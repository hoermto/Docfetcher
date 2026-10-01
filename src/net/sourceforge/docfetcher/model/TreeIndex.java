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

package net.sourceforge.docfetcher.model;

import java.io.File;
import java.io.IOException;
import java.util.List;

import net.sourceforge.docfetcher.model.index.IndexingConfig;
import net.sourceforge.docfetcher.model.index.IndexingReporter;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.model.parse.ExcelText;
import net.sourceforge.docfetcher.model.parse.PdfForms;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.ImmutableCopy;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.annotations.VisibleForPackageGroup;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAFDirectory;
import org.apache.lucene.store.RAMDirectory;

@VisibleForPackageGroup
public abstract class TreeIndex <
	D extends Document<D, F>,
	F extends Folder<D, F>> implements LuceneIndex {
	
	public enum IndexingResult {
		SUCCESS_CHANGED,
		SUCCESS_UNCHANGED,
		FAILURE,
	}
	
	/*
	 * Note: It is important to always serialize Path objects instead of
	 * java.io.File objects to avoid certain portability issues caused by
	 * non-normalized file paths.
	 */
	
	private static final long serialVersionUID = 1L;
	
	/**
	 * Format version of indexes created or rebuilt by this program version.
	 * Version 1 (DocFetcher 2.0) added page counts, version 2 (2.4) the text
	 * recognized in images and scanned PDFs. Version 3 (2.7) added the tolerant
	 * forms of the words; they aren't needed anymore since the tolerant search
	 * works at search time, so indexes of version 2 and 3 are both current.
	 * Deserialized indexes from older versions have version 0.
	 */
	public static final int CURRENT_FORMAT_VERSION = 2;
	
	private final IndexingConfig config;
	private final F rootFolder;
	private final long created;
	private int formatVersion = CURRENT_FORMAT_VERSION;

	/**
	 * Version of the Excel text in the index (see ExcelText.VERSION).
	 * Deserialized indexes from older versions have version 0; their Excel
	 * files are parsed again on the next update, and only those.
	 */
	private int excelTextVersion = ExcelText.VERSION;

	/**
	 * Version of the PDF form text in the index (see PdfForms.VERSION); 0 in
	 * older indexes, whose PDF files with filled form fields are then parsed
	 * again on the next update, and only those.
	 */
	private int pdfFormsVersion = PdfForms.VERSION;
/** Time of the last successful update or creation, 0 if unknown (older indexes). */
	private long lastUpdated = 0;
	@Nullable private final Path fileIndexDirPath;
	@Nullable private transient RAMDirectory ramIndexDir;
	@Nullable private transient File indexParentDir;
	
	// if indexDir is null, all content is written to a RAM index, which
	// can be retrieved via getLuceneDir
	protected TreeIndex(@Nullable File indexParentDir,
	                    @NotNull File rootFile) {
		Util.checkNotNull(rootFile);
		this.indexParentDir = indexParentDir;
		
		// Create config
		this.config = new IndexingConfig() {
			@Override
			protected void onStoreRelativePathsChanged() {
				File oldFile = rootFolder.getPath().getCanonicalFile();
				Path newPath = config.getStorablePath(oldFile);
				rootFolder.setPath(newPath);
			}
			protected void onWatchFoldersChanged() {
				LuceneIndex.evtWatchFoldersChanged.fire(TreeIndex.this);
			}
		};
		
		// Create root folder
		rootFile = Util.getCanonicalFile(rootFile);
		Path newPath = config.getStorablePath(rootFile);
		rootFolder = createRootFolder(newPath);
		Util.checkNotNull(rootFolder);
		
		// Create index directory or RAM directory
		created = Util.getTimestamp();
		if (indexParentDir == null) {
			fileIndexDirPath = null;
			ramIndexDir = new RAMDirectory();
		}
		else {
			String indexDirName = getIndexDirName(rootFile) + "_" + created;
			File indexDir = new File(indexParentDir, indexDirName);
			if (AppUtil.isPortable())
				fileIndexDirPath = new Path(UtilModel.getRelativePathIfPossible(indexDir));
			else
				fileIndexDirPath = new Path(indexDir);
		}
	}
	
	public final long getCreated() {
		return created;
	}
	
	public final int getFormatVersion() {
		return formatVersion;
	}

	/** Returns true if the Excel files must be parsed again (older text format). */
	public final boolean isExcelTextOutdated() {
		return excelTextVersion < ExcelText.VERSION;
	}

	/** To be called after all Excel files were parsed with the current text format. */
	public final void setExcelTextCurrent() {
		excelTextVersion = ExcelText.VERSION;
	}

	/** Returns true if the PDF files with form fields must be parsed again. */
	public final boolean isPdfFormsOutdated() {
		return pdfFormsVersion < PdfForms.VERSION;
	}

	/** To be called after all PDF files with form fields were parsed again. */
	public final void setPdfFormsCurrent() {
		pdfFormsVersion = PdfForms.VERSION;
	}

	@NotNull
	public final File getCanonicalRootFile() {
		return rootFolder.getPath().getCanonicalFile();
	}
	
	@NotNull
	protected abstract String getIndexDirName(@NotNull File rootFile);
	
	@NotNull
	protected abstract F createRootFolder(@NotNull Path path);

	@NotNull
	public final IndexingConfig getConfig() {
		return config;
	}
	
	@Nullable
	public final Path getIndexDirPath() {
		if (fileIndexDirPath == null)
			return null;
		if (IndexRegistry.indexPathOverride != null) {
			String name = new File(fileIndexDirPath.getPath()).getName();
			return new Path(new File(IndexRegistry.indexPathOverride, name));
		}
		return fileIndexDirPath;
	}
	
	@Nullable
	protected final File getIndexParentDir() {
		if (fileIndexDirPath == null)
			return null;
		if (indexParentDir == null) // is null after deserialization
			indexParentDir = Util.getParentFile(getIndexDirPath().getCanonicalFile());
		return indexParentDir;
	}
	
	@NotNull
	public final IndexingResult update(	@Nullable IndexingReporter reporter,
										@Nullable Cancelable cancelable) {
		if (reporter == null)
			reporter = IndexingReporter.nullReporter;
		if (cancelable == null)
			cancelable = Cancelable.nullCancelable;
		if (cancelable.isCanceled())
			return IndexingResult.SUCCESS_UNCHANGED;
		IndexingResult result = doUpdate(reporter, cancelable);
		if (result != IndexingResult.FAILURE && !cancelable.isCanceled())
			lastUpdated = System.currentTimeMillis();
		return result;
	}
	
	/**
	 * Returns the time of the last completed update or creation of this
	 * index, or 0 if unknown.
	 */
	public final long getLastUpdated() {
		return lastUpdated;
	}
	
	@NotNull
	protected abstract IndexingResult doUpdate(	@NotNull IndexingReporter reporter,
												@NotNull Cancelable cancelable);
	
	@NotNull
	public final Directory getLuceneDir() throws IOException {
		if (fileIndexDirPath != null) {
			assert ramIndexDir == null;
			/*
			 * Use RAFDirectory on Windows to avoid EXCEPTION_IN_PAGE_ERROR crashes
			 * that can occur with MMapDirectory in long-running processes. MMapDirectory
			 * (the default on Windows 64-bit) uses memory-mapped files which become
			 * unstable when combined with thread interruption (used in DocFetcher's
			 * cancellation mechanism), causing JVM crashes after many hours of operation.
			 * RAFDirectory is the official Lucene recommendation for applications using
			 * thread interruption, offering better performance than SimpleFSDirectory
			 * while remaining thread-interruption safe. See bug #2242.
			 */
			if (Util.IS_WINDOWS)
				return new RAFDirectory(getIndexDirPath().getCanonicalFile().toPath());
			return FSDirectory.open(getIndexDirPath().getCanonicalFile().toPath());
		}
		if (ramIndexDir == null) // may be null after deserialization
			ramIndexDir = new RAMDirectory();
		return ramIndexDir;
	}
	
	@NotNull
	public final F getRootFolder() {
		return rootFolder;
	}
	
	@NotNull
	public final String getDisplayName() {
		return rootFolder.getDisplayName();
	}
	
	@NotNull
	public final Iterable<ViewNode> getChildren() {
		return rootFolder.getChildren();
	}
	
	public final void clear() {
		clear(false);
	}
	
	public final void delete() {
		clear(true);
	}
	
	private void clear(boolean removeTopLevel) {
		// A rebuild re-parses everything, so the index gets the current format
		if (!removeTopLevel) {
			formatVersion = CURRENT_FORMAT_VERSION;
			excelTextVersion = ExcelText.VERSION;
			pdfFormsVersion = PdfForms.VERSION;
		}
		if (fileIndexDirPath != null) {
			File fileIndexDir = getIndexDirPath().getCanonicalFile();
			if (fileIndexDir.exists()) {
				try {
					if (removeTopLevel)
						Util.deleteRecursively(fileIndexDir);
					else
						Util.deleteContents(fileIndexDir);
				}
				catch (IOException e) {
					Util.printErr(e);
				}
			}
		}
		else {
			assert ramIndexDir != null;
			ramIndexDir = new RAMDirectory();
		}
		
		/*
		 * The last-modified field of the root folder must be cleared so that
		 * the next index update will detect the root folder as modified.
		 */
		rootFolder.setLastModified(null);
		rootFolder.removeChildren();
	}
	
	public final boolean isChecked() {
		return rootFolder.isChecked();
	}
	
	public final void setChecked(boolean isChecked) {
		rootFolder.setChecked(isChecked);
	}
	
	@NotNull
	public final TreeCheckState getTreeCheckState() {
		return rootFolder.getTreeCheckState();
	}
	
	public final boolean isIndex() {
		return true;
	}
	
	@ImmutableCopy
	@NotNull
	public final List<String> getDocumentIds() {
		return rootFolder.getDocumentIds();
	}
	
	public final boolean isWatchFolders() {
		return config.isWatchFolders();
	}
	
	public final boolean hasErrorsDeep() {
		return rootFolder.hasErrorsDeep();
	}
	
}
