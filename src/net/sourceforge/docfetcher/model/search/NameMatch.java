/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.search;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;

import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.model.DocumentType;
import net.sourceforge.docfetcher.model.Fields;
import net.sourceforge.docfetcher.model.Folder;
import net.sourceforge.docfetcher.model.LuceneIndex;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Lets each word of a query match the text, the file name or a folder name
 * in the path of a document: 'Rosenweg Miete' finds the lease in the folder
 * 'Tenants/Rosenweg 12', even if its text doesn't mention 'Rosenweg'. File
 * names are matched as substrings ('invoice' finds 'myinvoice_2024.pdf'),
 * and so are folder names, via the folder trees of the indexes - so this
 * happens at search time and needs no changes to the indexes. Excluded words
 * ('-invoice') exclude matching names as well. Phrases in quotes only match
 * the text.
 */
final class NameMatch {

	/** At most this many folders are matched per word, so that short words stay fast. */
	static final int MAX_FOLDERS_PER_WORD = 100;

	private final List<LuceneIndex> indexes;

	NameMatch(@NotNull List<LuceneIndex> indexes) {
		this.indexes = indexes;
	}

	/** Returns the query with each word also matching file and folder names. */
	@NotNull
	Query expand(@NotNull Query query) {
		if (query instanceof BooleanQuery) {
			BooleanQuery bool = (BooleanQuery) query;
			BooleanQuery.Builder builder = new BooleanQuery.Builder();
			builder.setMinimumNumberShouldMatch(bool.getMinimumNumberShouldMatch());
			for (BooleanClause clause : bool.clauses())
				builder.add(expand(clause.getQuery()), clause.getOccur());
			return builder.build();
		}
		if (query instanceof BoostQuery) {
			BoostQuery boost = (BoostQuery) query;
			return new BoostQuery(expand(boost.getQuery()), boost.getBoost());
		}
		Term term = null;
		if (query instanceof TermQuery)
			term = ((TermQuery) query).getTerm();
		else if (query instanceof PrefixQuery)
			term = ((PrefixQuery) query).getPrefix();
		else if (query instanceof WildcardQuery)
			term = ((WildcardQuery) query).getTerm();
		if (term == null || !term.field().equals(Fields.CONTENT.key()))
			return query;
		// Wildcard characters are query syntax, not part of the name
		String word = term.text().replace("*", "").replace("?", "").trim();
		if (word.isEmpty())
			return query;
		BooleanQuery.Builder builder = new BooleanQuery.Builder();
		builder.add(query, BooleanClause.Occur.SHOULD);
		WildcardQuery filename = new WildcardQuery(new Term(Fields.FILENAME.key(), "*" + word + "*"));
		/*
		 * Constant score rewriting avoids the boolean clause limit that
		 * scoring rewrites can hit when a substring matches many names.
		 */
		filename.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_REWRITE);
		builder.add(filename, BooleanClause.Occur.SHOULD);
		Query folders = folderQuery(word);
		if (folders != null)
			builder.add(folders, BooleanClause.Occur.SHOULD);
		return builder.build();
	}

	/** Matches the documents below the folders whose name contains the word, or null if there are none. */
	@Nullable
	private Query folderQuery(@NotNull String word) {
		if (word.length() < 2)
			return null;
		List<Folder<?, ?>> folders = foldersNamed(word);
		if (folders.isEmpty())
			return null;
		BooleanQuery.Builder builder = new BooleanQuery.Builder();
		for (Folder<?, ?> folder : folders) {
			// The UIDs of the documents start with the path of their folder
			String prefix = DocumentType.FILE.createUniqueId(folder.getPath()) + "/";
			PrefixQuery prefixQuery = new PrefixQuery(new Term(Fields.UID.key(), prefix));
			prefixQuery.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_REWRITE);
			builder.add(prefixQuery, BooleanClause.Occur.SHOULD);
		}
		return builder.build();
	}

	/**
	 * Returns the folders of the (non-email) indexes whose name contains the
	 * word, ignoring case - and umlauts and accents with the tolerant search.
	 */
	@NotNull
	List<Folder<?, ?>> foldersNamed(@NotNull String word) {
		boolean tolerant = SettingsConf.Bool.TolerantSearch.get();
		String needle = normalize(word, tolerant);
		List<Folder<?, ?>> hits = new ArrayList<Folder<?, ?>>();
		for (LuceneIndex index : indexes) {
			if (index.isEmailIndex())
				continue;
			Queue<Folder<?, ?>> queue = new LinkedList<Folder<?, ?>>();
			queue.add(index.getRootFolder());
			while (!queue.isEmpty() && hits.size() < MAX_FOLDERS_PER_WORD) {
				Folder<?, ?> folder = queue.remove();
				if (normalize(folder.getName(), tolerant).contains(needle))
					hits.add(folder);
				for (Object subFolder : folder.getSubFolders())
					queue.add((Folder<?, ?>) subFolder);
			}
		}
		return hits;
	}

	@NotNull
	private static String normalize(@NotNull String name, boolean tolerant) {
		String lower = name.toLowerCase(Locale.ROOT);
		return tolerant ? TolerantSearch.fold(lower) : lower;
	}

}
