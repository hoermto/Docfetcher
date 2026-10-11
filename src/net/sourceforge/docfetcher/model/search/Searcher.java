/*******************************************************************************
 * Copyright (c) 2011 Tran Nam Quang.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tran Nam Quang - initial API and implementation
 *******************************************************************************/

package net.sourceforge.docfetcher.model.search;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.ProgramConf;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.model.Fields;
import net.sourceforge.docfetcher.model.IndexLoadingProblems.CorruptedIndex;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.DocumentType;
import net.sourceforge.docfetcher.model.IndexRegistry.ExistingIndexesHandler;
import net.sourceforge.docfetcher.model.LuceneIndex;
import net.sourceforge.docfetcher.model.Path;
import net.sourceforge.docfetcher.model.PendingDeletion;
import net.sourceforge.docfetcher.model.index.DecoratedMultiReader;
import net.sourceforge.docfetcher.model.index.IndexingConfig;
import net.sourceforge.docfetcher.model.parse.ParseService;
import net.sourceforge.docfetcher.model.index.file.FileFactory;
import net.sourceforge.docfetcher.model.index.outlook.OutlookMailFactory;
import net.sourceforge.docfetcher.model.parse.Parser;
import net.sourceforge.docfetcher.util.CheckedOutOfMemoryError;
import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.ImmutableCopy;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.NotThreadSafe;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.annotations.ThreadSafe;
import net.sourceforge.docfetcher.util.annotations.VisibleForPackageGroup;
import net.sourceforge.docfetcher.util.collect.AlphanumComparator;
import net.sourceforge.docfetcher.util.collect.LazyList;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.TermsQuery;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.MultiTermQuery.RewriteMethod;
import org.apache.lucene.util.automaton.TooComplexToDeterminizeException;

import com.google.common.io.Closeables;

/**
 * A search API on top of the index registry. This class is completely
 * thread-safe, so usually only one instance of it is needed for handling
 * concurrent search requests.
 * <p>
 * <b>Important</b>: Instances of this class must be disposed after usage by
 * calling {@link #shutdown()}.
 * 
 * @author Tran Nam Quang
 */
@ThreadSafe
public final class Searcher {
	
	private static boolean isExcluded(	@NotNull File folder,
										@NotNull Collection<File> excludeFolders) {
		for (File excluded : excludeFolders)
			if (Util.isCanonicallyEqual(folder, excluded))
				return true;
		return false;
	}

	/**
	 * A single page of results.
	 */
	public static final class ResultPage {
		/** The result documents for this page. */
		@ImmutableCopy
		public final List<ResultDocument> resultDocuments;
		
		/** The zero-based index of this page. */
		public final int pageIndex;
		
		/** The total number of pages. */
		public final int pageCount;
		
		/** The total number of result documents across all pages. */
		public final int hitCount;
		
		private ResultPage(	@NotNull List<ResultDocument> resultDocuments,
							int pageIndex,
							int pageCount,
							int hitCount) {
			this.resultDocuments = Util.checkNotNull(resultDocuments);
			this.pageIndex = pageIndex;
			this.pageCount = pageCount;
			this.hitCount = hitCount;
		}
	}
	
	private static final int PAGE_SIZE = 50;
	public static final int MAX_RESULTS = ProgramConf.Int.MaxResultsTotal.get();
	
	private final IndexRegistry indexRegistry;
	private final FileFactory fileFactory;
	private final OutlookMailFactory outlookMailFactory;
	private final Event.Listener<LuceneIndex> addedListener;
	
	private final BlockingQueue<List<PendingDeletion>> deletionQueue = new LinkedBlockingQueue<List<PendingDeletion>>(); // guarded by 'this' lock
	private final Thread deletionThread; // guarded by 'this' lock
	
	@NotNull private IndexSearcher luceneSearcher; // guarded by read-write lock
	@NotNull private List<LuceneIndex> indexes; // guarded by read-write lock
	@NotNull private TolerantSearch tolerantSearch; // guarded by read-write lock
	@Nullable private volatile IOException ioException;
	
	private final Lock readLock;
	private final Lock writeLock;

	volatile Boolean stopped;

	/**
	 * This method should not be called by clients. Use
	 * {@link IndexRegistry#getSearcher()} instead.
	 * 
	 * @param corruptedIndexes
	 *            A list that will be filled by this constructor with indexes
	 *            that couldn't be loaded.
	 */
	@VisibleForPackageGroup
	public Searcher(@NotNull IndexRegistry indexRegistry,
					@NotNull FileFactory fileFactory,
					@NotNull OutlookMailFactory outlookMailFactory,
					@NotNull final List<CorruptedIndex> corruptedIndexes)
			throws IOException {
		Util.checkNotNull(indexRegistry, fileFactory, outlookMailFactory);
		this.indexRegistry = indexRegistry;
		this.fileFactory = fileFactory;
		this.outlookMailFactory = outlookMailFactory;
		
		readLock = indexRegistry.getReadLock();
		writeLock = indexRegistry.getWriteLock();
		
		// Handler for index additions
		addedListener = new Event.Listener<LuceneIndex>() {
			public void update(LuceneIndex eventData) {
				replaceLuceneSearcher();
			}
		};
		
		/*
		 * This lock could be moved into the indexes handler, but we'll put it
		 * here to avoid releasing and reacquiring it.
		 */
		writeLock.lock();
		try {
			indexRegistry.addListeners(new ExistingIndexesHandler() {
				// Handle existing indexes
				public void handleExistingIndexes(List<LuceneIndex> indexes) {
					try {
						corruptedIndexes.addAll(setLuceneSearcher(indexes));
					}
					catch (IOException e) {
						ioException = e;
					}
				}
			}, addedListener, null); // removedListener is null, see deletion thread below
		}
		finally {
			writeLock.unlock();
		}
		
		if (ioException != null)
			throw ioException;
		
		// Handler for index removals
		deletionThread = new Thread(Searcher.class.getName() + " (Approve pending deletions)") {
			public void run() {
				while (true) {
					try {
						List<PendingDeletion> deletions = deletionQueue.take();
						replaceLuceneSearcher();
						for (PendingDeletion deletion : deletions)
							deletion.setApprovedBySearcher();
					}
					catch (InterruptedException e) {
						break;
					}
				}
			}
		};
		deletionThread.start();

		this.stopped = true;
	}
	
	/**
	 * Updates the cached indexes and replaces the current Lucene searcher with
	 * a new one.
	 */
	@ThreadSafe
	@VisibleForPackageGroup
	public void replaceLuceneSearcher() {
		writeLock.lock();
		try {
			Closeables.close(luceneSearcher.getIndexReader(), false);
			setLuceneSearcher(indexRegistry.getIndexes());
		}
		catch (IOException e) {
			ioException = e; // Will be thrown later
		}
		finally {
			writeLock.unlock();
		}
	}
	
	// Caller must close returned searcher
	@NotNull
	@NotThreadSafe
	private List<CorruptedIndex> setLuceneSearcher(@NotNull List<LuceneIndex> indexes)
			throws IOException {
		this.indexes = Util.checkNotNull(indexes);
		ArrayList<IndexReader> readers = new ArrayList<IndexReader>(indexes.size());
        LazyList<CorruptedIndex> corrupted = new LazyList<CorruptedIndex>();
		for (int i = 0; i < indexes.size(); i++) {
			LuceneIndex index = indexes.get(i);
            try {
				readers.add(DirectoryReader.open(index.getLuceneDir()));
            }
            catch (IOException e) {
            	Util.printErr(e);
                corrupted.add(new CorruptedIndex(index, e));
            }
            catch (RuntimeException e) {
            	// Catch IllegalStateException and other runtime exceptions that
            	// can occur when opening corrupted or incompatible indexes.
            	// For example: "missing attribute: PerFieldPostingsFormat.suffix"
            	Util.printErr(e);
            	IOException wrappedException = new IOException(
            		"Failed to open index due to corruption or incompatibility: " +
            		e.getMessage(), e);
                corrupted.add(new CorruptedIndex(index, wrappedException));
            }
        }
        luceneSearcher = new IndexSearcher(new DecoratedMultiReader(readers.toArray(new IndexReader[readers.size()])));
        tolerantSearch = new TolerantSearch(luceneSearcher.getIndexReader());
        return corrupted;
	}

	private class StoppedSearcherException extends RuntimeException{
	}

	@ImmutableCopy
	@NotNull
	@ThreadSafe
	public List<ResultDocument> search(@NotNull String queryString)
			throws SearchException, CheckedOutOfMemoryError {
		/*
		 * Note: For the desktop interface, we'll always search in all available
		 * indexes, even those which are unchecked on the filter panel. This
		 * allows the user to re-check the unchecked indexes and see previously
		 * hidden results without starting another search.
		 */
		stopped = false;
		
		// Create Lucene query
		QueryWrapper queryWrapper = createQuery(queryString);
		Query query = queryWrapper.query;
		boolean isPhraseQuery = queryWrapper.isPhraseQuery;
		
		/*
		 * Notes regarding the following code:
		 * 
		 * 1) Lucene will throw an IOException if the user deletes one or more
		 * indexes while a search is running over the affected indexes. This can
		 * happen when two DocFetcher instances are running.
		 * 
		 * 2) All the information needed for displaying the results must be
		 * loaded and returned immediately rather than lazily, because after the
		 * search the user might delete one or more indexes. This also means the
		 * result documents must not access the indexes later on.
		 */

		readLock.lock();
		try {
			checkIndexesExist();
			query = tolerant(matchNames(query), queryWrapper.exactQueries);

			// Perform search; might throw OutOfMemoryError
			DelegatingCollector collector = new DelegatingCollector(){
				@Override
				public void collect(int doc) throws IOException {
					leafDelegate.collect(doc);
					if(stopped){
						throw new StoppedSearcherException();
					}
				}
			};
			int maxResults = Math.max(1, MAX_RESULTS);
			collector.setDelegate(TopScoreDocCollector.create(maxResults, null));
			try{
				luceneSearcher.search(query, collector);
			}
			catch (StoppedSearcherException e) {}
			ScoreDoc[] scoreDocs = ((TopScoreDocCollector)collector.getDelegate()).topDocs().scoreDocs;

			// Create result documents
			ResultDocument[] results = new ResultDocument[scoreDocs.length];
			for (int i = 0; i < scoreDocs.length; i++) {
				Document doc = luceneSearcher.doc(scoreDocs[i].doc);
				float score = scoreDocs[i].score;
				LuceneIndex index = indexes.get(((DecoratedMultiReader) luceneSearcher.getIndexReader()).decoratedReaderIndex(i));
				IndexingConfig config = index.getConfig();
				results[i] = new ResultDocument(
					doc, score, query, isPhraseQuery, config, fileFactory,
					outlookMailFactory);
			}
			return Arrays.asList(results);
		}
		catch (IOException e) {
			throw new SearchException(e.getMessage()); // TODO i18n
		}
		catch (OutOfMemoryError e) {
			throw new CheckedOutOfMemoryError(e);
		}
		finally {
			readLock.unlock();
		}
	}

	@ThreadSafe
	public void stopSearch(){
		stopped =true;
	}
	
	/**
	 * Suggests folders into which a document with the given text could be
	 * moved: The indexed documents most similar to the text (by keyword
	 * usage) are looked up, and their folders are ranked by the accumulated
	 * similarity. Folders equal to the given one are excluded, as are folders
	 * that don't exist on disk (e.g. archive contents). Returns at most
	 * {@code maxFolders} suggestions, best first, with relevances normalized
	 * to 0..1.
	 * <p>
	 * This may take a moment and should be run in a non-GUI thread.
	 */
	@ImmutableCopy
	@NotNull
	@ThreadSafe
	public List<FolderSuggestion> suggestFolders(	@NotNull String text,
													@NotNull Collection<File> excludeFolders,
													int maxFolders)
			throws SearchException, CheckedOutOfMemoryError {
		Util.checkNotNull(text, excludeFolders);
		readLock.lock();
		try {
			checkIndexesExist();
			
			IndexReader reader = luceneSearcher.getIndexReader();
			MoreLikeThis mlt = new MoreLikeThis(reader);
			mlt.setAnalyzer(IndexRegistry.getAnalyzer());
			mlt.setFieldNames(new String[] { Fields.CONTENT.key() });
			mlt.setMinTermFreq(1);
			mlt.setMinDocFreq(1);
			mlt.setMaxQueryTerms(40);
			Query query = mlt.like(Fields.CONTENT.key(), new StringReader(text));
			
			int maxDocs = 200;
			ScoreDoc[] scoreDocs = luceneSearcher.search(query, maxDocs).scoreDocs;
			
			// Accumulate the scores of the similar documents per folder
			Map<File, Double> folderScores = new HashMap<File, Double>();
			for (ScoreDoc scoreDoc : scoreDocs) {
				Document doc = luceneSearcher.doc(scoreDoc.doc);
				String uid = doc.get(Fields.UID.key());
				if (uid == null || DocumentType.isEmailType(uid))
					continue;
				File folder;
				try {
					File file = DocumentType.extractPath(uid).getCanonicalFile();
					folder = Util.getParentFile(file);
				}
				catch (RuntimeException e) {
					continue;
				}
				if (folder == null || !folder.isDirectory())
					continue; // e.g. archive entries
				if (isExcluded(folder, excludeFolders))
					continue;
				Double sum = folderScores.get(folder);
				folderScores.put(folder, (sum == null ? 0 : sum) + scoreDoc.score);
			}
			
			List<Map.Entry<File, Double>> entries = new ArrayList<Map.Entry<File, Double>>(folderScores.entrySet());
			Collections.sort(entries, new Comparator<Map.Entry<File, Double>>() {
				public int compare(Map.Entry<File, Double> e1, Map.Entry<File, Double> e2) {
					return Double.compare(e2.getValue(), e1.getValue());
				}
			});
			
			List<FolderSuggestion> suggestions = new ArrayList<FolderSuggestion>();
			double max = entries.isEmpty() ? 1 : entries.get(0).getValue();
			for (Map.Entry<File, Double> entry : entries) {
				if (suggestions.size() >= maxFolders)
					break;
				double relevance = max > 0 ? entry.getValue() / max : 0;
				suggestions.add(new FolderSuggestion(entry.getKey(), relevance));
			}
			return suggestions;
		}
		catch (IOException e) {
			throw new SearchException(e.getMessage());
		}
		catch (OutOfMemoryError e) {
			throw new CheckedOutOfMemoryError(e);
		}
		finally {
			readLock.unlock();
		}
	}
	
	/**
	 * Lists the files located directly in the given folder (not in its
	 * subfolders), whether they are indexed or not. Indexed files are
	 * returned with their index data (title, page count, etc.); for the other
	 * files, the data is derived from the file itself. {@code index} is the
	 * index covering the folder, or null if there is none. The results carry
	 * a match-all query, i.e. nothing is highlighted in the preview.
	 */
	@ImmutableCopy
	@NotNull
	@ThreadSafe
	public List<ResultDocument> listFolder(	@Nullable LuceneIndex index,
											@NotNull File folder)
			throws SearchException, CheckedOutOfMemoryError {
		Util.checkNotNull(folder);
		IndexingConfig config = index != null ? index.getConfig() : new IndexingConfig();
		
		// Indexed documents by canonical file path (Unicode-normalized, since
		// the index stores composed names while the disk may use decomposed ones)
		Map<String, ResultDocument> indexed = new HashMap<String, ResultDocument>();
		if (index != null) {
			for (ResultDocument doc : listIndexedFolder(index, folder)) {
				File file = Util.getCanonicalFile(doc.getPath().getCanonicalFile());
				indexed.put(normalize(file.getPath()), doc);
			}
		}
		
		List<ResultDocument> results = new ArrayList<ResultDocument>();
		File[] files = folder.listFiles();
		if (files == null)
			return results;
		for (File file : files) {
			if (!file.isFile() || file.isHidden())
				continue;
			ResultDocument doc = indexed.get(normalize(Util.getCanonicalFile(file).getPath()));
			results.add(doc != null ? doc : createFileDocument(config, file));
		}
		return results;
	}
	
	/**
	 * Lists the subfolders and the files of the given folder, for the folder
	 * browser: the subfolders come first (as folder rows), then the files as
	 * returned by {@link #listFolder}. {@code index} is the index covering
	 * the folder, or null if there is none.
	 */
	@ImmutableCopy
	@NotNull
	@ThreadSafe
	public List<ResultDocument> listFolderEntries(	@Nullable LuceneIndex index,
													@NotNull File folder)
			throws SearchException, CheckedOutOfMemoryError {
		Util.checkNotNull(folder);
		IndexingConfig config = index != null ? index.getConfig() : new IndexingConfig();
		List<ResultDocument> entries = new ArrayList<ResultDocument>();
		File[] children = folder.listFiles();
		if (children != null) {
			for (File child : children)
				if (child.isDirectory() && !child.isHidden())
					entries.add(createFolderDocument(config, child));
		}
		entries.addAll(listFolder(index, folder));
		return entries;
	}
	
	/**
	 * Creates a folder row for the folder browser. {@code index} is the
	 * index covering the folder, or null if there is none.
	 */
	@NotNull
	@ThreadSafe
	public ResultDocument createFolderRow(	@Nullable LuceneIndex index,
											@NotNull File folder) {
		return createFolderRow(index, folder, null);
	}
	
	/**
	 * Creates a folder row for the folder browser, optionally with a display
	 * name of its own (used for the '..' entry).
	 */
	@NotNull
	@ThreadSafe
	public ResultDocument createFolderRow(	@Nullable LuceneIndex index,
											@NotNull File folder,
											@Nullable String displayName) {
		IndexingConfig config = index != null ? index.getConfig() : new IndexingConfig();
		return createFolderDocument(config, folder, displayName);
	}
	
	/** Creates a folder row for the folder browser. */
	@NotNull
	private ResultDocument createFolderDocument(@NotNull IndexingConfig config,
												@NotNull File folder) {
		return createFolderDocument(config, folder, null);
	}
	
	@NotNull
	private ResultDocument createFolderDocument(@NotNull IndexingConfig config,
												@NotNull File folder,
												@Nullable String displayName) {
		String name = displayName != null
			? displayName
			: (folder.getName().isEmpty() ? folder.getPath() : folder.getName());
		Document doc = new Document();
		doc.add(Fields.UID.create(DocumentType.FILE.createUniqueId(config.getStorablePath(folder))));
		doc.add(Fields.FILENAME.create(name));
		doc.add(Fields.TYPE.create(""));
		doc.add(Fields.PARSER.create(""));
		doc.add(Fields.TITLE.create(name));
		doc.add(Fields.SIZE.create(0L));
		doc.add(Fields.LAST_MODIFIED.create(String.valueOf(folder.lastModified())));
		ResultDocument result = new ResultDocument(
			doc, 0, new MatchAllDocsQuery(), false, config, fileFactory,
			outlookMailFactory);
		result.setFolder(true);
		return result;
	}
	
	@NotNull
	private static String normalize(@NotNull String path) {
		return Normalizer.normalize(path, Normalizer.Form.NFC);
	}
	
	/**
	 * Creates a result document for a file that is not (yet) in an index,
	 * with the data that can be read from the file system.
	 */
	@NotNull
	private ResultDocument createFileDocument(	@NotNull IndexingConfig config,
												@NotNull File file) {
		String filename = file.getName();
		Document doc = new Document();
		doc.add(Fields.UID.create(DocumentType.FILE.createUniqueId(config.getStorablePath(file))));
		doc.add(Fields.FILENAME.create(filename));
		doc.add(Fields.TYPE.create(Util.getExtension(filename)));
		doc.add(Fields.PARSER.create(ParseService.getParserName(config, filename)));
		doc.add(Fields.TITLE.create(Util.splitFilename(filename)[0]));
		doc.add(Fields.SIZE.create(file.length()));
		doc.add(Fields.LAST_MODIFIED.create(String.valueOf(file.lastModified())));
		return new ResultDocument(
			doc, 0, new MatchAllDocsQuery(), false, config, fileFactory,
			outlookMailFactory);
	}
	
	/**
	 * Lists the indexed documents located directly in the given folder (not
	 * in its subfolders). The folder must be covered by the given index.
	 */
	@NotNull
	private List<ResultDocument> listIndexedFolder(	@NotNull LuceneIndex index,
													@NotNull File folder)
			throws SearchException, CheckedOutOfMemoryError {
		// The UIDs contain the path in the form the index stores it
		Path folderPath = Util.isCanonicallyEqual(index.getCanonicalRootFile(), folder)
			? index.getRootFolder().getPath()
			: index.getConfig().getStorablePath(folder);
		String prefix = DocumentType.FILE.createUniqueId(folderPath) + "/";
		
		readLock.lock();
		try {
			checkIndexesExist();
			Query query = new MatchAllDocsQuery();
			BooleanQuery.Builder builder = new BooleanQuery.Builder();
			builder.add(new PrefixQuery(new Term(Fields.UID.key(), prefix)), BooleanClause.Occur.FILTER);
			builder.add(query, BooleanClause.Occur.MUST);
			int maxResults = Math.max(1, MAX_RESULTS);
			ScoreDoc[] scoreDocs = luceneSearcher.search(builder.build(), maxResults).scoreDocs;
			
			List<ResultDocument> results = new ArrayList<ResultDocument>();
			for (ScoreDoc scoreDoc : scoreDocs) {
				Document doc = luceneSearcher.doc(scoreDoc.doc);
				String uid = doc.get(Fields.UID.key());
				if (uid == null)
					continue;
				// Exclude documents in subfolders
				Path path = DocumentType.extractPath(uid);
				if (!path.splitAtLastSeparator().getLeft().equals(folderPath))
					continue;
				LuceneIndex docIndex = indexes.get(((DecoratedMultiReader) luceneSearcher.getIndexReader()).decoratedReaderIndex(scoreDoc.doc));
				results.add(new ResultDocument(
					doc, scoreDoc.score, query, false, docIndex.getConfig(),
					fileFactory, outlookMailFactory));
			}
			return results;
		}
		catch (IOException e) {
			throw new SearchException(e.getMessage());
		}
		catch (OutOfMemoryError e) {
			throw new CheckedOutOfMemoryError(e);
		}
		finally {
			readLock.unlock();
		}
	}
	
	/**
	 * Returns the terms of the given text that characterize it best, i.e.
	 * terms that are frequent in the text but rare across all indexed
	 * documents (TF-IDF), best first, lowercased as indexed. Returns at most
	 * {@code maxTerms} terms.
	 */
	@ImmutableCopy
	@NotNull
	@ThreadSafe
	public List<String> extractKeywords(@NotNull String text, int maxTerms)
			throws SearchException, CheckedOutOfMemoryError {
		Util.checkNotNull(text);
		readLock.lock();
		try {
			checkIndexesExist();
			MoreLikeThis mlt = new MoreLikeThis(luceneSearcher.getIndexReader());
			mlt.setAnalyzer(IndexRegistry.getAnalyzer());
			mlt.setFieldNames(new String[] { Fields.CONTENT.key() });
			mlt.setMinTermFreq(1);
			mlt.setMinDocFreq(1);
			mlt.setMinWordLen(3);
			/*
			 * MoreLikeThis pops its priority queue lowest score first, so the
			 * returned array is in ascending order and a limit would cut off
			 * the best terms. Retrieve all terms and reverse them instead.
			 */
			mlt.setMaxQueryTerms(5000);
			String[] terms = mlt.retrieveInterestingTerms(new StringReader(text), Fields.CONTENT.key());
			List<String> ranked = new ArrayList<String>(Arrays.asList(terms));
			Collections.reverse(ranked);
			if (ranked.size() > maxTerms)
				ranked = new ArrayList<String>(ranked.subList(0, maxTerms));
			return ranked;
		}
		catch (IOException e) {
			throw new SearchException(e.getMessage());
		}
		catch (OutOfMemoryError e) {
			throw new CheckedOutOfMemoryError(e);
		}
		finally {
			readLock.unlock();
		}
	}
	
	/** A group of documents with identical or nearly identical text (see {@link DuplicateFinder}). */
	public static final class DuplicateGroup {
		/** Why the documents are duplicates. */
		public enum Kind { IDENTICAL_TEXT, NEARLY_IDENTICAL_TEXT, SAME_NAME_AND_SIZE }

		public final List<ResultDocument> docs;
		public final Kind kind;

		DuplicateGroup(@NotNull List<ResultDocument> docs, @NotNull Kind kind) {
			this.docs = docs;
			this.kind = kind;
		}
	}

	/** Reports how many documents were compared, of how many. */
	public interface DuplicateProgress {
		void update(int done, int total);
	}

	/**
	 * Finds the documents with identical or nearly identical text, from the
	 * words in the indexes - no document is opened - among the documents in
	 * the given folder and its subfolders (null: all). Unless only the content
	 * counts, documents with the same file name and size are duplicates too,
	 * also without text (e.g. images). The documents of a group are sorted
	 * oldest first; groups of one file that is in two indexes are left out.
	 * Returns nothing if canceled.
	 */
	@NotNull
	@ThreadSafe
	public List<DuplicateGroup> findDuplicates(	@NotNull Cancelable cancelable,
												@NotNull DuplicateProgress progress,
												@Nullable java.io.File folder,
												@Nullable java.io.File excluded,
												boolean contentOnly)
			throws SearchException, CheckedOutOfMemoryError {
		readLock.lock();
		try {
			checkIndexesExist();
			IndexReader reader = luceneSearcher.getIndexReader();
			DuplicateFinder finder = new DuplicateFinder(reader.maxDoc());
			String field = Fields.CONTENT.key();

			/*
			 * The text isn't stored in the index, only its words: the words
			 * of each document with their counts. The words of the file name,
			 * which the content field contains too, are subtracted, so that
			 * copies under another name are identical.
			 */
			List<Map<String, Integer>> nameWords = new ArrayList<Map<String, Integer>>(reader.maxDoc());
			java.util.Set<String> storedFields = new java.util.HashSet<String>(Arrays.asList(
				Fields.FILENAME.key(), Fields.UID.key(), Fields.SIZE.key()));
			java.io.File scope = folder == null ? null : normalize(folder);
			java.io.File outside = excluded == null ? null : normalize(excluded);
			boolean[] inScope = new boolean[reader.maxDoc()];
			// Same file name and size: the documents of each name and size, in the scope
			Map<String, List<Integer>> byNameAndSize = new java.util.LinkedHashMap<String, List<Integer>>();
			org.apache.lucene.util.Bits allLive = org.apache.lucene.index.MultiFields.getLiveDocs(reader);
			for (int i = 0; i < reader.maxDoc(); i++) {
				Document stored = reader.document(i, storedFields);
				String filename = stored.get(Fields.FILENAME.key());
				String uid = stored.get(Fields.UID.key());
				java.io.File path = uid == null ? null : normalize(new java.io.File(DocumentType.extractPath(uid).getPath()));
				inScope[i] = (allLive == null || allLive.get(i)) && path != null
					&& (scope == null || Util.contains(scope, path))
					&& (outside == null || !Util.contains(outside, path));
				nameWords.add(filename == null || !inScope[i] ? Collections.<String, Integer> emptyMap() : nameWords(filename, field));
				if (!contentOnly && inScope[i] && filename != null && stored.get(Fields.SIZE.key()) != null) {
					String key = filename.toLowerCase(java.util.Locale.ROOT) + "/" + stored.get(Fields.SIZE.key());
					List<Integer> same = byNameAndSize.get(key);
					if (same == null)
						byNameAndSize.put(key, same = new ArrayList<Integer>(2));
					same.add(i);
				}
			}

			long totalTerms = 0;
			for (org.apache.lucene.index.LeafReaderContext leaf : reader.leaves()) {
				org.apache.lucene.index.Terms terms = leaf.reader().terms(field);
				if (terms != null)
					totalTerms += Math.max(0, terms.size());
			}
			long doneTerms = 0;
			for (org.apache.lucene.index.LeafReaderContext leaf : reader.leaves()) {
				org.apache.lucene.index.Terms terms = leaf.reader().terms(field);
				if (terms == null)
					continue;
				org.apache.lucene.util.Bits live = leaf.reader().getLiveDocs();
				org.apache.lucene.index.TermsEnum termsEnum = terms.iterator();
				org.apache.lucene.index.PostingsEnum postings = null;
				for (org.apache.lucene.util.BytesRef term; (term = termsEnum.next()) != null;) {
					if (cancelable.isCanceled())
						return Collections.emptyList();
					if (++doneTerms % 5000 == 0)
						progress.update((int) Math.min(Integer.MAX_VALUE, doneTerms), (int) Math.min(Integer.MAX_VALUE, totalTerms));
					String word = term.utf8ToString();
					int hash = word.hashCode();
					postings = termsEnum.postings(postings, org.apache.lucene.index.PostingsEnum.FREQS);
					for (int doc; (doc = postings.nextDoc()) != org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;) {
						if (live != null && !live.get(doc))
							continue;
						int key = leaf.docBase + doc;
						if (!inScope[key])
							continue;
						Integer inName = nameWords.get(key).get(word);
						int count = postings.freq() - (inName == null ? 0 : inName);
						if (count > 0)
							finder.addTerm(key, hash, count);
					}
				}
			}
			progress.update((int) Math.min(Integer.MAX_VALUE, totalTerms), (int) Math.min(Integer.MAX_VALUE, totalTerms));

			List<DuplicateGroup> groups = new ArrayList<DuplicateGroup>();
			Map<Integer, Integer> textGroupOf = new HashMap<Integer, Integer>();
			for (DuplicateFinder.Group group : finder.groups()) {
				for (int key : group.keys)
					textGroupOf.put(key, groups.size());
				addGroup(groups, reader, group.keys, group.identical
					? DuplicateGroup.Kind.IDENTICAL_TEXT
					: DuplicateGroup.Kind.NEARLY_IDENTICAL_TEXT);
			}
			// Same name and size, unless a text group has them all already
			for (List<Integer> same : byNameAndSize.values()) {
				if (same.size() < 2)
					continue;
				Integer textGroup = textGroupOf.get(same.get(0));
				boolean covered = textGroup != null;
				for (int key : same)
					covered &= textGroup != null && textGroup.equals(textGroupOf.get(key));
				if (!covered)
					addGroup(groups, reader, same, DuplicateGroup.Kind.SAME_NAME_AND_SIZE);
			}
			return groups;
		}
		catch (IOException e) {
			throw new SearchException(e.getMessage());
		}
		catch (OutOfMemoryError e) {
			throw new CheckedOutOfMemoryError(e);
		}
		finally {
			readLock.unlock();
		}
	}

	/**
	 * The words the indexing adds to the content field for the file name:
	 * the name and the name without extension (see LuceneDocWriter), as the
	 * analyzer splits them, with their counts.
	 */
	@NotNull
	private static Map<String, Integer> nameWords(@NotNull String filename, @NotNull String field)
			throws IOException {
		String basename = Util.splitFilename(filename)[0];
		String metadata = basename.equals(filename) ? filename : filename + " " + basename;
		Map<String, Integer> counts = new HashMap<String, Integer>();
		org.apache.lucene.analysis.TokenStream stream = IndexRegistry.getAnalyzer().tokenStream(field, metadata);
		try {
			org.apache.lucene.analysis.tokenattributes.CharTermAttribute term =
				stream.addAttribute(org.apache.lucene.analysis.tokenattributes.CharTermAttribute.class);
			stream.reset();
			while (stream.incrementToken()) {
				String word = term.toString();
				counts.put(word, counts.containsKey(word) ? counts.get(word) + 1 : 1);
			}
			stream.end();
		}
		finally {
			stream.close();
		}
		return counts;
	}

	/** Adds the group, oldest first, unless it is only one file that is in two indexes. */
	private void addGroup(	@NotNull List<DuplicateGroup> groups,
							@NotNull IndexReader reader,
							@NotNull List<Integer> keys,
							@NotNull DuplicateGroup.Kind kind) throws IOException {
		Query query = new MatchAllDocsQuery();
		Map<String, ResultDocument> byPath = new java.util.LinkedHashMap<String, ResultDocument>();
		for (int key : keys) {
			LuceneIndex index = indexes.get(((DecoratedMultiReader) reader).decoratedReaderIndex(key));
			ResultDocument doc = new ResultDocument(reader.document(key), 0, query, true,
				index.getConfig(), fileFactory, outlookMailFactory);
			if (!byPath.containsKey(doc.getPathStr()))
				byPath.put(doc.getPathStr(), doc);
		}
		if (byPath.size() < 2)
			return;
		List<ResultDocument> docs = new ArrayList<ResultDocument>(byPath.values());
		Collections.sort(docs, new Comparator<ResultDocument>() {
			public int compare(ResultDocument d1, ResultDocument d2) {
				return Long.compare(time(d1), time(d2));
			}
		});
		groups.add(new DuplicateGroup(docs, kind));
	}

	/** The absolute path without '.' and '..', for comparing folders. */
	@NotNull
	private static java.io.File normalize(@NotNull java.io.File file) {
		return file.getAbsoluteFile().toPath().normalize().toFile();
	}

	private static long time(@NotNull ResultDocument doc) {
		try {
			java.util.Date date = doc.isEmail() ? doc.getDate() : doc.getLastModified();
			return date == null ? 0 : date.getTime();
		}
		catch (Exception e) {
			return 0;
		}
	}

	@ImmutableCopy
	@NotNull
	@ThreadSafe
	public List<ResultDocument> list(@NotNull Set<String> uids)
			throws SearchException, CheckedOutOfMemoryError {
		// Construct a filter that only matches documents with the given UIDs
		BooleanQuery.Builder builder=new BooleanQuery.Builder();
		ArrayList<Term> terms=new ArrayList<Term>(uids.size());
		String fieldName = Fields.UID.key();
		for (String uid : uids)
			terms.add(new Term(fieldName, uid));
		TermsQuery uidQuery = new TermsQuery(terms);
		builder.add(uidQuery,BooleanClause.Occur.FILTER);
		
		Query query = new MatchAllDocsQuery();
		
		readLock.lock();
		try {
			checkIndexesExist();

			// Perform search; might throw OutOfMemoryError
			int maxResults = Math.max(1, MAX_RESULTS);
			builder.add(query,BooleanClause.Occur.MUST);
			ScoreDoc[] scoreDocs = luceneSearcher.search(builder.build(), maxResults).scoreDocs;
			
			// Create result documents
			ResultDocument[] results = new ResultDocument[scoreDocs.length];
			for (int i = 0; i < results.length; i++) {
				Document doc = luceneSearcher.doc(scoreDocs[i].doc);
				float score = scoreDocs[i].score;
				LuceneIndex index = indexes.get(((DecoratedMultiReader) luceneSearcher.getIndexReader()).decoratedReaderIndex(i));
				IndexingConfig config = index.getConfig();
				results[i] = new ResultDocument(
					doc, score, query, true, config, fileFactory,
					outlookMailFactory);
			}
			
			// Sort results by title
			Arrays.sort(results, new Comparator<ResultDocument>() {
				public int compare(ResultDocument o1, ResultDocument o2) {
					return AlphanumComparator.ignoreCaseInstance.compare(
						o1.getTitle(), o2.getTitle());
				}
			});
			
			return Arrays.asList(results);
		}
		catch (IOException e) {
			throw new SearchException(e.getMessage()); // TODO i18n
		}
		catch (OutOfMemoryError e) {
			throw new CheckedOutOfMemoryError(e);
		}
		finally {
			readLock.unlock();
		}
	}
	
	/**
	 * For the given query, returns the requested page of results. This method
	 * should not be called anymore after {@link #shutdown()} has been called,
	 * otherwise an IOException will be thrown.
	 */
	@NotNull
	@ThreadSafe
	public ResultPage search(@NotNull WebQuery webQuery)
			throws IOException, SearchException, CheckedOutOfMemoryError {
		Util.checkNotNull(webQuery);
		
		if (ioException != null)
			throw ioException;
		
		BooleanQuery.Builder builder=new BooleanQuery.Builder();
		
		// Add size filter to filter chain
		if (webQuery.minSize != null || webQuery.maxSize != null) {
			builder.add(
                LegacyNumericRangeQuery.newLongRange(
						Fields.SIZE.key(), webQuery.minSize, webQuery.maxSize, true, true),
                BooleanClause.Occur.FILTER
			);
		}
		
		// Add type filter to filter chain
		if (webQuery.parsers != null) {
			ArrayList<Term> terms=new ArrayList<Term>(webQuery.parsers.size()+1);
			String fieldName = Fields.PARSER.key();
			terms.add(new Term(fieldName, Fields.EMAIL_PARSER));
			for (Parser parser : webQuery.parsers) {
				String parserName = parser.getClass().getSimpleName();
				terms.add(new Term(fieldName, parserName));
			}
			builder.add( new TermsQuery(terms), BooleanClause.Occur.FILTER );
		}
		
		// Add location filter to filter chain
		if (webQuery.indexes != null) {
			BooleanQuery.Builder locationQueryBuilder=new BooleanQuery.Builder();
			for (LuceneIndex index : webQuery.indexes) {
				Path path = index.getRootFolder().getPath();
				String uid = index.getDocumentType().createUniqueId(path);
				Term prefix = new Term(Fields.UID.key(), uid + "/");
				locationQueryBuilder.add(new PrefixQuery(prefix),BooleanClause.Occur.SHOULD);
			}
			builder.add( locationQueryBuilder.build(), BooleanClause.Occur.FILTER );
		}

		// Create query
		QueryWrapper queryWrapper = createQuery(webQuery.query);
		Query query = queryWrapper.query;
		boolean isPhraseQuery = queryWrapper.isPhraseQuery;
		
		readLock.lock();
		try {
			checkIndexesExist();
			query = tolerant(matchNames(query), queryWrapper.exactQueries);

			// Perform search; might throw OutOfMemoryError
			// Protect against integer overflow when calculating maxResults
			long maxResultsLong = ((long) webQuery.pageIndex + 1) * PAGE_SIZE;
			int maxResults = (int) Math.min(maxResultsLong, Integer.MAX_VALUE);
			maxResults = Math.max(1, maxResults);
			builder.add(query, BooleanClause.Occur.MUST);
			TopDocs topDocs = luceneSearcher.search(builder.build(), maxResults);
			ScoreDoc[] scoreDocs = topDocs.scoreDocs;
			
			// Compute start and end indices of returned page
			int start;
			int end = scoreDocs.length;
			if (end <= PAGE_SIZE) {
				start = 0;
			}
			else {
				int r = end % PAGE_SIZE;
				start = end - (r == 0 ? PAGE_SIZE : r);
			}

			// Create and fill list of result documents to return
			ResultDocument[] results = new ResultDocument[end - start];
			for (int i = start; i < end; i++) {
				Document doc = luceneSearcher.doc(scoreDocs[i].doc);
				float score = scoreDocs[i].score;
				LuceneIndex index = indexes.get(((DecoratedMultiReader) luceneSearcher.getIndexReader()).decoratedReaderIndex(i));
				IndexingConfig config = index.getConfig();
				results[i - start] = new ResultDocument(
					doc, score, query, isPhraseQuery, config, fileFactory,
					outlookMailFactory);
			}
			
			int hitCount = topDocs.totalHits;
			int newPageIndex = start / PAGE_SIZE;
			int pageCount = (int) Math.ceil((float) hitCount / PAGE_SIZE);
			
			return new ResultPage(
				Arrays.asList(results), newPageIndex, pageCount, hitCount);
		}
		catch (OutOfMemoryError e) {
			throw new CheckedOutOfMemoryError(e);
		}
		finally {
			readLock.unlock();
		}
	}
	
	@NotNull
	@ThreadSafe
	private static QueryWrapper createQuery(@NotNull String queryString)
			throws SearchException {
		// Normalize Unicode quotation marks to ASCII quotes to avoid parse errors
		// Bug #2226: Users might copy-paste text with curly quotes
		queryString = queryString
			.replace('\u201C', '"')  // Left double quotation mark
			.replace('\u201D', '"')  // Right double quotation mark
			.replace('\u2018', '\'') // Left single quotation mark
			.replace('\u2019', '\''); // Right single quotation mark

		try {
			PhraseDetectingQueryParser queryParser = createParser();
			Query query;
			try {
				// Like Google, there are no regular expressions between slashes
				query = queryParser.parse(escapeUnknownFields(queryString.replace("/", "\\/")));
			}
			catch (ParseException e) {
				/*
				 * Like Google, characters that are syntax for Lucene are taken
				 * as part of the words if the query doesn't parse otherwise,
				 * e.g. 'C++', 'Nr: 5', '(draft' or a single quote.
				 */
				queryParser = createParser();
				query = queryParser.parse(asPlainWords(queryString));
			}
			boolean isPhraseQuery = queryParser.isPhraseQuery();
			return new QueryWrapper(query, isPhraseQuery, queryParser.getExactQueries());
		}
		catch (TooComplexToDeterminizeException e) {
			/*
			 * This happens when wildcard patterns are too complex to convert
			 * into a deterministic finite automaton, e.g. "???????????L1*".
			 */
			String msg = Msg.invalid_query.get() + "\n\n" +
				"The search pattern is too complex. " +
				"Please simplify your query by using fewer wildcards.";
			throw new SearchException(msg);
		}
		catch (IllegalArgumentException e) {
			/*
			 * This happens for example when you enter a fuzzy search with
			 * similarity >= 1, e.g. "fuzzy~1".
			 */
			String msg = Msg.invalid_query.get() + "\n\n" + e.getMessage();
			throw new SearchException(msg);
		}
		catch (ParseException e) {
			String msg = Msg.invalid_query.get() + "\n\n" + e.getMessage();
			throw new SearchException(msg);
		}
	}
	
	/** Lets the words of the query also match file and folder names, if wanted (see NameMatch). */
	@NotNull
	@NotThreadSafe
	private Query matchNames(@NotNull Query query) {
		return SettingsConf.Bool.MatchFilenames.get() ? new NameMatch(indexes).expand(query) : query;
	}

	/** Expands the words of the query to their tolerant variants, if wanted (see TolerantSearch). */
	@NotNull
	@NotThreadSafe
	private Query tolerant(@NotNull Query query, @NotNull Set<Query> exactQueries) throws IOException {
		return SettingsConf.Bool.TolerantSearch.get() ? tolerantSearch.expand(query, exactQueries) : query;
	}
	
	// Checks that all indexes still exist
	@NotNull
	@NotThreadSafe
	private void checkIndexesExist() throws SearchException {
		if (indexes.isEmpty())
			throw new SearchException("Nothing to search in: No indexes have been created yet."); // TODO i18n
		for (LuceneIndex index : indexes) {
			File indexDir = index.getIndexDirPath().getCanonicalFile();
			if (indexDir != null && !indexDir.isDirectory()) {
				String msg = "Folders not found:"; // TODO i18n folders_not_found
				msg += "\n" + indexDir;
				throw new SearchException(msg);
			}
		}
	}
	
	// Given deletions should not be in the registry anymore, since the receiver
	// will retrieve a fresh set of indexes from the registry before approval
	@ThreadSafe
	@VisibleForPackageGroup
	public void approveDeletions(@NotNull List<PendingDeletion> deletions) {
		Util.checkNotNull(deletions);
		if (deletions.isEmpty())
			return;
		
		/*
		 * If the deletion thread is not available anymore, approve of deletions
		 * immediately. - Otherwise the given deletion objects would just hang
		 * around in the queue until program shutdown and never receive
		 * approval, thus the associated indexes would never get deleted.
		 */
		synchronized (this) {
			if (deletionThread.isInterrupted()) {
				for (PendingDeletion pendingDeletion : deletions)
					pendingDeletion.setApprovedBySearcher();
			}
			else {
				deletionQueue.add(deletions);
			}
		}
	}
	
	/**
	 * Disposes of the receiver. The caller should make sure that no more search
	 * requests are submitted to the receiver after this method is called.
	 */
	@ThreadSafe
	public void shutdown() {
		if (ioException != null)
			Util.printErr(ioException);
		
		writeLock.lock();
		try {
			indexRegistry.removeListeners(addedListener, null);
			Closeables.closeQuietly(luceneSearcher.getIndexReader());
		}
		finally {
			writeLock.unlock();
		}
		
		/*
		 * This should be done after closing the Lucene searcher in order to
		 * ensure that no indexes will be deleted outside the deletion queue
		 * while the Lucene searcher is still open.
		 */
		synchronized (this) {
			deletionThread.interrupt();
		}
	}
	
	/**
	 * The query parser for the search field: the words must all occur (unless
	 * the user chose OR in the preferences), as in Google.
	 */
	@NotNull
	private static PhraseDetectingQueryParser createParser() {
		PhraseDetectingQueryParser queryParser = new PhraseDetectingQueryParser(
			Fields.CONTENT.key(), IndexRegistry.getAnalyzer());
		queryParser.setAllowLeadingWildcard(true);
		RewriteMethod rewriteMethod = MultiTermQuery.SCORING_BOOLEAN_REWRITE;
		queryParser.setMultiTermRewriteMethod(rewriteMethod);
		if (!SettingsConf.Bool.UseOrOperator.get())
			queryParser.setDefaultOperator(QueryParser.AND_OPERATOR);
		return queryParser;
	}

	private static final Pattern FIELD_PREFIX = Pattern.compile("(?<![\\\\\\w])(\\w+):");

	/**
	 * Keeps searches in a field like 'title:dog' (any case), but takes other
	 * words before a colon as words, like Google: 'Nr: 5' searches for 'Nr'
	 * and '5' instead of '5' in a field named 'Nr'.
	 */
	@NotNull
	static String escapeUnknownFields(@NotNull String query) {
		Matcher matcher = FIELD_PREFIX.matcher(query);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String name = matcher.group(1);
			String replacement = name + "\\:";
			for (Fields field : Fields.values())
				if (field.key().equalsIgnoreCase(name))
					replacement = field.key() + ":";
			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * Returns the query with every character that is syntax for Lucene
	 * escaped, except for the Google-style syntax: quotes (if they come in
	 * pairs), '+' and '-' at the start of a word, and the wildcards '*' and
	 * '?'. The words OR, AND and NOT stay operators.
	 */
	@NotNull
	static String asPlainWords(@NotNull String query) {
		int quotes = 0;
		for (int i = 0; i < query.length(); i++)
			if (query.charAt(i) == '"')
				quotes++;
		boolean keepQuotes = quotes % 2 == 0;
		StringBuilder sb = new StringBuilder(query.length() * 2);
		boolean wordStart = true;
		for (int i = 0; i < query.length(); i++) {
			char c = query.charAt(i);
			char next = i + 1 < query.length() ? query.charAt(i + 1) : ' ';
			if (Character.isWhitespace(c)) {
				sb.append(c);
				wordStart = true;
			}
			else if (c == '"') {
				sb.append(keepQuotes ? c : ' ');
				wordStart = true;
			}
			else if ((c == '+' || c == '-') && wordStart
					&& (Character.isLetterOrDigit(next) || next == '"')) {
				sb.append(c); // Must or must not contain the following word
				wordStart = false;
			}
			else {
				if (!Character.isLetterOrDigit(c) && c != '*' && c != '?')
					sb.append('\\');
				sb.append(c);
				wordStart = false;
			}
		}
		return sb.toString();
	}

	private static final class QueryWrapper {
		public final Query query;
		public final boolean isPhraseQuery;
		public final Set<Query> exactQueries;

		private QueryWrapper(@NotNull Query query, boolean isPhraseQuery, @NotNull Set<Query> exactQueries) {
			this.query = Util.checkNotNull(query);
			this.isPhraseQuery = isPhraseQuery;
			this.exactQueries = exactQueries;
		}
	}

}
