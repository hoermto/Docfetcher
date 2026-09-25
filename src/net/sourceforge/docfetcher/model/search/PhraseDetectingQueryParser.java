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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.model.TolerantAnalyzer;

import net.sourceforge.docfetcher.util.annotations.VisibleForPackageGroup;

/**
 * @author Tran Nam Quang
 */
@VisibleForPackageGroup
public final class PhraseDetectingQueryParser extends QueryParser {
	
	/*
	 * This class is used for determining whether the parsed query is supported
	 * by the fast-vector highlighter. The latter only supports queries that are
	 * a combination of TermQuery, PhraseQuery and/or BooleanQuery.
	 */
	
	private boolean isPhraseQuery = true;
	
	public PhraseDetectingQueryParser(  String defaultField,
                                        Analyzer analyzer) {
		super(defaultField, analyzer);
	}
	
	public boolean isPhraseQuery() {
		return isPhraseQuery;
	}

	protected Query newFuzzyQuery(	Term term,
									float minimumSimilarity,
									int prefixLength) {
		isPhraseQuery = false;
		return super.newFuzzyQuery(term, minimumSimilarity, prefixLength);
	}

	protected Query newMatchAllDocsQuery() {
		isPhraseQuery = false;
		return super.newMatchAllDocsQuery();
	}

	protected Query newPrefixQuery(Term prefix) {
		isPhraseQuery = false;
		Query query = super.newPrefixQuery(prefix);
		Term folded = foldedTerm(prefix);
		return folded == null ? query : either(query, super.newPrefixQuery(folded));
	}

	protected Query newWildcardQuery(Term t) {
		isPhraseQuery = false;
		Query query = super.newWildcardQuery(t);
		Term folded = foldedTerm(t);
		return folded == null ? query : either(query, super.newWildcardQuery(folded));
	}

	/*
	 * Wildcard and prefix terms don't go through the analyzer, so with the
	 * tolerant search they are folded here ('Mül*' also finds 'Muller'). The
	 * original term is searched as well, for indexes built without the
	 * tolerant forms.
	 */
	private static Term foldedTerm(Term term) {
		if (!SettingsConf.Bool.TolerantSearch.get())
			return null;
		String folded = TolerantAnalyzer.fold(term.text());
		return folded.equals(term.text()) ? null : new Term(term.field(), folded);
	}

	private static Query either(Query first, Query second) {
		BooleanQuery.Builder builder = new BooleanQuery.Builder();
		builder.add(first, BooleanClause.Occur.SHOULD);
		builder.add(second, BooleanClause.Occur.SHOULD);
		return builder.build();
	}
	
}