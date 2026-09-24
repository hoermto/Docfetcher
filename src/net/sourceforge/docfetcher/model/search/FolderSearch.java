/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.search;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * Finds folders whose name matches the words of a search query. The documents
 * are searched by Lucene, but folders aren't indexed, so they are looked up on
 * disk, starting at the folders the folder browser shows at its root level.
 */
public final class FolderSearch {

	private FolderSearch() {}

	/** At most this many folders are returned. */
	public static final int MAX_RESULTS = 50;

	/** At most this many folders are visited, so that huge trees can't stall the search. */
	private static final int MAX_VISITED = 20000;

	/** Words that are part of the query syntax rather than search terms. */
	private static final List<String> OPERATORS = java.util.Arrays.asList(
		"and", "or", "not", "to");

	/**
	 * Returns the search terms of the given query in lower case: the plain
	 * words, without the query syntax (operators, field names, wildcards,
	 * quotes, parentheses). Returns an empty list if the query has no word
	 * that is worth matching folder names against.
	 */
	@NotNull
	public static List<String> extractTerms(@NotNull String queryString) {
		List<String> terms = new ArrayList<String>();
		StringBuilder word = new StringBuilder();
		for (int i = 0; i <= queryString.length(); i++) {
			char c = i < queryString.length() ? queryString.charAt(i) : ' ';
			if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
				word.append(c);
				continue;
			}
			// A word followed by a colon is a field name, e.g. 'filename:'
			if (c == ':')
				word.setLength(0);
			if (word.length() >= 2) {
				String term = word.toString().toLowerCase(Locale.ENGLISH);
				if (!OPERATORS.contains(term) && !terms.contains(term))
					terms.add(term);
			}
			word.setLength(0);
		}
		return terms;
	}

	/**
	 * Returns true if the given folder name contains all the given terms,
	 * ignoring case. An empty term list matches nothing.
	 */
	public static boolean matches(@NotNull String folderName, @NotNull List<String> terms) {
		if (terms.isEmpty())
			return false;
		String name = folderName.toLowerCase(Locale.ENGLISH);
		for (String term : terms)
			if (!name.contains(term))
				return false;
		return true;
	}

	/**
	 * Returns the folders below the given root folders whose name matches all
	 * the given terms, breadth-first, at most {@link #MAX_RESULTS} of them.
	 * The root folders themselves are matched as well. Hidden folders are
	 * skipped.
	 */
	@NotNull
	public static List<File> find(	@NotNull Collection<File> roots,
									@NotNull List<String> terms) {
		List<File> hits = new ArrayList<File>();
		if (terms.isEmpty())
			return hits;
		Queue<File> queue = new LinkedList<File>();
		for (File root : roots)
			if (root.isDirectory())
				queue.add(root);
		int visited = 0;
		while (!queue.isEmpty() && hits.size() < MAX_RESULTS && visited < MAX_VISITED) {
			File folder = queue.remove();
			visited++;
			if (matches(folder.getName(), terms) && !hits.contains(folder))
				hits.add(folder);
			File[] children = folder.listFiles();
			if (children == null)
				continue;
			for (File child : children)
				if (child.isDirectory() && !child.isHidden())
					queue.add(child);
		}
		return hits;
	}

}
