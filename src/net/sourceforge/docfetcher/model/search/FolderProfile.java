/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.search;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;

import net.sourceforge.docfetcher.model.DocumentType;
import net.sourceforge.docfetcher.model.Fields;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * What a folder contains, from the index - no document is opened: the number
 * of documents in it and its subfolders, their period, the names of the
 * newest ones, and the words that characterize them: frequent in the
 * folder's documents, rare in the other indexed documents. Gives an AI
 * assistant an idea of what the folder is about, to decide where a document
 * belongs.
 */
public final class FolderProfile {

	/** Keywords per folder. */
	static final int KEYWORDS = 12;
	/** Names of the newest documents per folder. */
	static final int EXAMPLES = 3;
	/** File extensions, which the index has with the file names: they say nothing about the content. */
	private static final Set<String> EXTENSIONS = new HashSet<String>(java.util.Arrays.asList(
		"pdf", "txt", "doc", "docx", "xls", "xlsx", "xlsm", "ppt", "pptx", "odt", "ods", "odp", "rtf", "csv",
		"htm", "html", "xml", "eml", "msg", "jpg", "jpeg", "png", "gif", "tif", "tiff", "bmp", "svg", "zip"));

	/** Words checked against all documents per folder, the most frequent in the folder. */
	private static final int CANDIDATES = 300;

	public final File folder;
	private int documents = 0;
	private long oldest = Long.MAX_VALUE;
	private long newest = Long.MIN_VALUE;
	private final List<String> keywords = new ArrayList<String>(KEYWORDS);
	/** The newest documents: [time, name] */
	private final List<Object[]> examples = new ArrayList<Object[]>(EXAMPLES + 1);
	private final Map<String, Integer> wordCounts = new HashMap<String, Integer>();

	FolderProfile(@NotNull File folder) {
		this.folder = folder;
	}

	/** Documents in the folder and its subfolders. */
	public int getDocuments() {
		return documents;
	}

	/** Modification time of the oldest document, or 0 without documents. */
	public long getOldest() {
		return documents == 0 ? 0 : oldest;
	}

	/** Modification time of the newest document, or 0 without documents. */
	public long getNewest() {
		return documents == 0 ? 0 : newest;
	}

	/** The characterizing words with the number of documents containing them, e.g. "stadtwerke (12)". */
	@NotNull
	public List<String> getKeywords() {
		return keywords;
	}

	/** The names of the newest documents, newest first. */
	@NotNull
	public List<String> getExamples() {
		List<String> names = new ArrayList<String>(examples.size());
		for (Object[] example : examples)
			names.add((String) example[1]);
		return names;
	}

	private void addDocument(long time, @NotNull String name) {
		documents++;
		if (time > 0) {
			oldest = Math.min(oldest, time);
			newest = Math.max(newest, time);
		}
		examples.add(new Object[] {time, name});
		Collections.sort(examples, new Comparator<Object[]>() {
			public int compare(Object[] e1, Object[] e2) {
				return Long.compare((Long) e2[0], (Long) e1[0]);
			}
		});
		if (examples.size() > EXAMPLES)
			examples.remove(EXAMPLES);
	}

	/**
	 * Computes the profiles of the given folders; each counts the documents
	 * in it and its subfolders, except those in {@code excluded} (the bin).
	 * One pass over the words of the content field.
	 */
	@NotNull
	static List<FolderProfile> compute(	@NotNull IndexReader reader,
										@NotNull List<File> folders,
										@Nullable File excluded) throws IOException {
		List<FolderProfile> profiles = new ArrayList<FolderProfile>(folders.size());
		List<File> normalized = new ArrayList<File>(folders.size());
		for (File folder : folders) {
			profiles.add(new FolderProfile(folder));
			normalized.add(normalize(folder));
		}
		File outside = excluded == null ? null : normalize(excluded);

		// The folders each document counts for
		int[][] memberOf = new int[reader.maxDoc()][];
		Bits allLive = org.apache.lucene.index.MultiFields.getLiveDocs(reader);
		Set<String> fields = new HashSet<String>(java.util.Arrays.asList(
			Fields.UID.key(), Fields.FILENAME.key(), Fields.LAST_MODIFIED.key()));
		int[] buffer = new int[folders.size()];
		for (int i = 0; i < reader.maxDoc(); i++) {
			if (allLive != null && !allLive.get(i))
				continue;
			Document stored = reader.document(i, fields);
			String uid = stored.get(Fields.UID.key());
			if (uid == null)
				continue;
			File path = normalize(new File(DocumentType.extractPath(uid).getPath()));
			if (outside != null && Util.contains(outside, path))
				continue;
			int n = 0;
			for (int f = 0; f < normalized.size(); f++)
				if (Util.contains(normalized.get(f), path))
					buffer[n++] = f;
			if (n == 0)
				continue;
			memberOf[i] = java.util.Arrays.copyOf(buffer, n);
			long time = 0;
			try {
				time = Long.parseLong(stored.get(Fields.LAST_MODIFIED.key()));
			}
			catch (Exception e) {
				// Emails etc.: no period
			}
			String name = stored.get(Fields.FILENAME.key());
			for (int f : memberOf[i])
				profiles.get(f).addDocument(time, name == null ? path.getName() : name);
		}

		// In how many documents of each folder each word occurs
		String field = Fields.CONTENT.key();
		for (LeafReaderContext leaf : reader.leaves()) {
			Terms terms = leaf.reader().terms(field);
			if (terms == null)
				continue;
			Bits live = leaf.reader().getLiveDocs();
			TermsEnum termsEnum = terms.iterator();
			PostingsEnum postings = null;
			for (BytesRef term; (term = termsEnum.next()) != null;) {
				String word = null;
				postings = termsEnum.postings(postings, PostingsEnum.NONE);
				for (int doc; (doc = postings.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS;) {
					if (live != null && !live.get(doc))
						continue;
					int[] member = memberOf[leaf.docBase + doc];
					if (member == null)
						continue;
					if (word == null) {
						word = term.utf8ToString();
						if (!isWord(word))
							break;
					}
					for (int f : member) {
						Map<String, Integer> counts = profiles.get(f).wordCounts;
						Integer count = counts.get(word);
						counts.put(word, count == null ? 1 : count + 1);
					}
				}
			}
		}

		// Frequent in the folder, rare elsewhere
		int total = Math.max(1, reader.numDocs());
		Map<String, Integer> docFreqs = new HashMap<String, Integer>();
		for (FolderProfile profile : profiles) {
			List<Map.Entry<String, Integer>> candidates = new ArrayList<Map.Entry<String, Integer>>(
				profile.wordCounts.entrySet());
			Collections.sort(candidates, new Comparator<Map.Entry<String, Integer>>() {
				public int compare(Map.Entry<String, Integer> e1, Map.Entry<String, Integer> e2) {
					return e2.getValue().compareTo(e1.getValue());
				}
			});
			final Map<String, Double> scores = new HashMap<String, Double>();
			int minCount = profile.documents >= 3 ? 2 : 1; // A word of one document says little about many
			for (int i = 0; i < candidates.size() && i < CANDIDATES; i++) {
				String word = candidates.get(i).getKey();
				int count = candidates.get(i).getValue();
				if (count < minCount)
					break;
				Integer docFreq = docFreqs.get(word);
				if (docFreq == null)
					docFreqs.put(word, docFreq = reader.docFreq(new Term(field, word)));
				// Words in more than half of all indexed documents characterize no folder
				double idf = Math.log((double) total / Math.max(docFreq, count));
				if (idf > Math.log(2))
					scores.put(word, count * idf);
			}
			List<String> best = new ArrayList<String>(scores.keySet());
			Collections.sort(best, new Comparator<String>() {
				public int compare(String w1, String w2) {
					return Double.compare(scores.get(w2), scores.get(w1));
				}
			});
			for (int i = 0; i < best.size() && i < KEYWORDS; i++)
				profile.keywords.add(best.get(i) + " (" + profile.wordCounts.get(best.get(i)) + ")");
			profile.wordCounts.clear();
		}
		return profiles;
	}

	/**
	 * Words of at least 3 letters, without digits (numbers, codes and dates
	 * say little about a folder), file extensions and filler words.
	 */
	private static boolean isWord(@NotNull String word) {
		if (word.length() < 3 || EXTENSIONS.contains(word) || NameSuggester.STOPWORDS.contains(word))
			return false;
		for (int i = 0; i < word.length(); i++)
			if (!Character.isLetter(word.charAt(i)))
				return false;
		return true;
	}

	@NotNull
	private static File normalize(@NotNull File file) {
		return file.getAbsoluteFile().toPath().normalize().toFile();
	}

}
