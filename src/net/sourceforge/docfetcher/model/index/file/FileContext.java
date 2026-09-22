/*******************************************************************************
 * Copyright (c) 2010, 2011 Tran Nam Quang.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tran Nam Quang - initial API and implementation
 *******************************************************************************/

package net.sourceforge.docfetcher.model.index.file;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.model.Path;
import net.sourceforge.docfetcher.model.TreeNode;
import net.sourceforge.docfetcher.model.UtilModel;
import net.sourceforge.docfetcher.model.index.IndexingConfig;
import net.sourceforge.docfetcher.model.index.IndexingError.ErrorType;
import net.sourceforge.docfetcher.model.index.IndexingException;
import net.sourceforge.docfetcher.model.index.IndexingInfo;
import net.sourceforge.docfetcher.model.index.IndexingInfo.InfoType;
import net.sourceforge.docfetcher.model.index.IndexingReporter;
import net.sourceforge.docfetcher.model.index.MutableInt;
import net.sourceforge.docfetcher.model.index.PatternAction;
import net.sourceforge.docfetcher.model.ocr.OcrPreprocessor;
import net.sourceforge.docfetcher.model.parse.ParseException;
import net.sourceforge.docfetcher.model.parse.ParseResult;
import net.sourceforge.docfetcher.model.parse.ParseService;
import net.sourceforge.docfetcher.util.CheckedOutOfMemoryError;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import de.schlichtherle.truezip.file.TArchiveDetector;
import de.schlichtherle.truezip.file.TFile;

/**
 * @author Tran Nam Quang
 */
class FileContext {
	
	private final IndexingConfig config;
	private final TArchiveDetector zipDetector;
	private final LuceneDocWriter writer;
	@NotNull private IndexingReporter reporter;
	@Nullable private final Path originalPath;
	private final Cancelable cancelable;
	private final MutableInt fileCount;
	private Progress progress = new Progress(); // shared with sub-contexts
	
	/*
	 * Text recognition is deferred: in the first pass, files needing it are
	 * indexed without recognized text and collected; the indexer recognizes
	 * them at the end, so that all other documents become searchable first.
	 */
	private boolean deferOcr = false;
	private List<DeferredFile> deferredFiles = new ArrayList<DeferredFile>(); // shared
	
	/** A file whose text recognition was deferred to the end of the task. */
	public static final class DeferredFile {
		public final FileDocument doc;
		public final File file;
		
		DeferredFile(@NotNull FileDocument doc, @NotNull File file) {
			this.doc = doc;
			this.file = file;
		}
	}
	@Nullable private final File indexParentDir; // null if index only exists in RAM

	protected FileContext(	@NotNull IndexingConfig config,
							@NotNull TArchiveDetector zipDetector,
							@NotNull LuceneDocWriter writer,
							@Nullable IndexingReporter reporter,
							@Nullable Path originalPath,
							@NotNull Cancelable cancelable,
							@NotNull MutableInt fileCount,
							@Nullable File indexParentDir) {
		Util.checkNotNull(config, zipDetector, writer, cancelable, fileCount);
		this.config = config;
		this.zipDetector = zipDetector;
		this.writer = writer;
		this.originalPath = originalPath;
		this.cancelable = cancelable;
		this.fileCount = fileCount;
		this.indexParentDir = indexParentDir;
		setReporter(reporter);
	}
	
	protected FileContext(	@NotNull FileContext superContext,
							@NotNull Path originalPath) {
		this(
				superContext.config,
				superContext.zipDetector,
				superContext.writer,
				superContext.reporter,
				originalPath,
				superContext.cancelable,
				superContext.fileCount,
				superContext.indexParentDir
		);
		progress = superContext.progress;
		deferOcr = superContext.deferOcr;
		deferredFiles = superContext.deferredFiles;
	}
	
	/** Enables deferring text recognition to the end of the task. */
	public final void setDeferOcr(boolean deferOcr) {
		this.deferOcr = deferOcr;
	}
	
	public final boolean isDeferOcr() {
		return deferOcr;
	}
	
	/** The files whose text recognition was deferred, in indexing order. */
	@NotNull
	public final List<DeferredFile> getDeferredFiles() {
		return deferredFiles;
	}
	
	/** Progress of the whole indexing task: files visited so far and total. */
	private static final class Progress {
		int visited = 0;
		int total = -1; // -1 = unknown
	}
	
	/** Sets the total number of files of the task (-1 if unknown) and reports it. */
	public final void setProgressTotal(int total) {
		progress.total = total;
		reporter.progress(progress.visited, total);
	}
	
	/** Reports one more visited file. */
	public final void fileVisited() {
		progress.visited++;
		reporter.progress(progress.visited, progress.total);
	}
	
	@NotNull
	public final IndexingConfig getConfig() {
		return config;
	}
	
	@NotNull
	public final TArchiveDetector getZipDetector() {
		return zipDetector;
	}
	
	@NotNull
	public final LuceneDocWriter getWriter() {
		return writer;
	}
	
	// May return a default empty reporter
	@NotNull
	protected final IndexingReporter getReporter() {
		return reporter;
	}

	public final void setReporter(@Nullable IndexingReporter reporter) {
		this.reporter = reporter == null
			? IndexingReporter.nullReporter
			: reporter;
	}
	
	@Nullable
	public final Path getOriginalPath() {
		return originalPath;
	}
	
	public final boolean isStopped() {
		return cancelable.isCanceled();
	}
	
	@NotNull
	protected final Cancelable getStopper() {
		return cancelable;
	}
	
	@NotNull
	protected final MutableInt getFileCount() {
		return fileCount;
	}
	
	@Nullable
	protected final File getIndexParentDir() {
		return indexParentDir;
	}
	
	/*
	 * Lucene has a hard limit of 32766 bytes for indexed terms. To prevent crashes,
	 * we skip files whose UID (essentially the full path) would exceed this limit.
	 */
	private static final int MAX_LUCENE_TERM_LENGTH = 32766;

	// returns success
	// if the indexing is canceled before or during the execution of this method,
	// the last-modified value of the given document will be set to -1.
	public final boolean index(	@NotNull FileDocument doc,
								@NotNull File file,
								boolean isAdded) throws IndexingException {
		// Check if UID would exceed Lucene's term length limit
		String uid = doc.getUniqueId();
		int uidByteLength = uid.getBytes(StandardCharsets.UTF_8).length;
		if (uidByteLength > MAX_LUCENE_TERM_LENGTH) {
			String msg = String.format(
				"File path too long (%d bytes in UTF-8 encoding, maximum is %d bytes).",
				uidByteLength, MAX_LUCENE_TERM_LENGTH
			);
			fail(ErrorType.PARSING, doc, new IllegalArgumentException(msg));
			return false;
		}

		info(InfoType.EXTRACTING, doc);
		try {
			/*
			 * Text extraction; may throw OutOfMemoryErrors. Recognition is
			 * deferred for plain files on disk only; archive entries are
			 * recognized right away since they may be gone after the walk.
			 */
			boolean deferrable = deferOcr && OcrPreprocessor.isEnabled()
				&& !(file instanceof TFile && ((TFile) file).isEntry());
			ParseResult parseResult = ParseService.parse(
				config, file, doc.getName(), doc.getPath(), reporter, cancelable,
				!deferrable);
			/*
			 * The file is indexed without the recognized text now and again
			 * after recognition, so that it is in the index even if the
			 * program is closed before the deferred pass.
			 */
			if (deferrable && parseResult.isOcrSkipped()) {
				deferredFiles.add(new DeferredFile(doc, file));
				progress.visited--; // Counted again in the deferred pass
			}

			/*
			 * If we detect a cancel request at this point, the request probably
			 * came in during the parsing step above. In that case, we'll keep
			 * the partially extracted text and feed it to Lucene, but set the
			 * stored last-modified value to -1 so that the next index update
			 * will see the file as "modified" and therefore reindex it.
			 */
			if (cancelable.isCanceled())
				doc.setLastModified(-1);

			// Add to index or update in index; may also throw OutOfMemoryErrors
			if (isAdded)
				writer.add(doc, file, parseResult);
			else
				writer.update(doc, file, parseResult);

			// Clear errors from previous indexing operations
			doc.setError(null);

			return true;
		}
		catch (IOException e) {
			throw new IndexingException(e);
		}
		catch (ParseException e) {
			fail(ErrorType.PARSING, doc, e);
		}
		catch (CheckedOutOfMemoryError e) {
			fail(ErrorType.OUT_OF_MEMORY, doc, e.getCause());
		}
		catch (ArrayIndexOutOfBoundsException | AssertionError e) {
			/*
			 * Bug #2413: This is a known Lucene bug (LUCENE-8118, LUCENE-8614)
			 * that occurs when indexing documents with extremely large numbers
			 * of tokens, causing an integer overflow in Lucene's ByteBlockPool.
			 *
			 * Bug #2360: This also catches ArrayIndexOutOfBoundsException from
			 * Lucene's ByteSliceReader when buffer management issues occur during
			 * index flushing with complex documents.
			 *
			 * Bug #2409: This also catches ArrayIndexOutOfBoundsException from
			 * the ANSJ tokenizer library when processing certain text, likely
			 * due to edge cases in Chinese character processing (String.getChars
			 * in IndexAnalysis.java:86).
			 *
			 * AssertionError is also caught here because Lucene's ByteSliceReader
			 * can throw it during index flushing when buffer management state is
			 * invalid for certain documents (same code path as Bug #2360).
			 *
			 * We treat this as a parsing failure so the user gets an appropriate
			 * error message rather than being told to increase the Java heap size.
			 */
			fail(ErrorType.PARSING, doc, e);
		}
		catch (LinkageError e) {
			/*
			 * Bug #2083: Rare JVM bug in Java 8u261 that causes LinkageError
			 * with "not invoker: invokeBasic" during Lucene's AttributeFactory
			 * initialization via MethodHandle.invokeBasic. This appears to be
			 * a transient JVM issue with method handle invocation that occurs
			 * during the creation of Lucene token stream attributes.
			 *
			 * The error propagates from:
			 * MethodHandleNatives.linkMethod -> AttributeFactory.createInstance
			 * -> Field.tokenStream -> DefaultIndexingChain.processField
			 *
			 * We treat this as a parsing failure since the file cannot be
			 * indexed properly due to the JVM bug. The user can retry indexing
			 * the file, which may succeed on subsequent attempts.
			 */
			fail(ErrorType.PARSING, doc, e);
		}
		return false;
	}
	
	public final boolean indexAndDeleteFile(@NotNull FileDocument doc,
											@NotNull File file,
											boolean added)
			throws IndexingException {
		try {
			return index(doc, file, added);
		}
		finally {
			file.delete();
		}
	}
	
	public final void deleteFromIndex(@NotNull String uid)
			throws IndexingException {
		try {
			writer.delete(uid);
		}
		catch (IOException e) {
			throw new IndexingException(e);
		}
	}
	
	public void info(@NotNull InfoType type, @NotNull TreeNode treeNode) {
		fileCount.increment();
		reporter.info(new IndexingInfo(type, treeNode, fileCount.get()));
	}
	
	// Reports the given error and saves it in the given tree node
	public final void fail(	@NotNull ErrorType type,
							@NotNull TreeNode treeNode,
							@Nullable Throwable cause) {
		UtilModel.fail(reporter, type, treeNode, cause);
	}
	
	/**
	 * Returns the original path for the given file, taking into account virtual
	 * files inside TrueZIP archives. The returned path depends on whether a
	 * non-null original path was set on the receiver.
	 */
	@NotNull
	public final Path getDirOrZipPath(@NotNull File file) {
		if (originalPath == null)
			return config.getStorablePath(file);
		TFile tzFile = (TFile) file;
		TFile topArchive = tzFile.getTopLevelArchive();
		String relativePath = UtilModel.getRelativePath(topArchive, tzFile);
		return originalPath.createSubPath(relativePath);
	}
	
	/**
	 * Returns whether the given TrueZIP file or directory should be skipped,
	 * given the various settings of the receiver.
	 */
	public final boolean skip(@NotNull TFile fileOrDir) {
		String filename = fileOrDir.getName();
		Path filepath = getDirOrZipPath(fileOrDir);
		
		boolean isFileOrSolidArchive = fileOrDir.isFile();
		boolean isZipArchiveOrFolder = !isFileOrSolidArchive;
		boolean isZipArchive = isZipArchiveOrFolder
			? UtilModel.isZipArchive(fileOrDir)
			: false;
		boolean isFileOrArchive = isFileOrSolidArchive || isZipArchive;
		boolean isFile = isFileOrSolidArchive
				&& !config.isSolidArchive(filename);
		
		for (PatternAction patternAction : config.getPatternActions()) {
			switch (patternAction.getAction()) {
			case EXCLUDE:
				if (patternAction.matches(filename, filepath, isFileOrArchive))
					return true;
				break;
			case DETECT_MIME:
				/*
				 * If the mime pattern matches, we'll check the mime pattern
				 * again later (right before parsing) in order to determine
				 * whether to detect the filetype by filename or by mimetype.
				 */
				if (isFile && patternAction.matches(filename, filepath, isFile))
					return false;
				break;
			default:
				throw new IllegalStateException();
			}
		}
		return isFile && !ParseService.canParseByName(config, filename);
	}

}