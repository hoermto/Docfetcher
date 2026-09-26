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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;

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
	
	/** The parts of the query that were in quotes: searched exactly, like Google. */
	private final Set<Query> exactQueries = Collections.newSetFromMap(new IdentityHashMap<Query, Boolean>());
	
	public PhraseDetectingQueryParser(  String defaultField,
                                        Analyzer analyzer) {
		super(defaultField, analyzer);
	}
	
	public boolean isPhraseQuery() {
		return isPhraseQuery;
	}

	/** Returns the parts of the parsed query that were in quotes (by identity). */
	public Set<Query> getExactQueries() {
		return exactQueries;
	}

	protected Query getFieldQuery(String field, String queryText, boolean quoted)
			throws ParseException {
		Query query = super.getFieldQuery(field, queryText, quoted);
		if (quoted && query != null)
			exactQueries.add(query);
		return query;
	}

	// Quoted text, with or without a proximity like "dog cat"~5
	protected Query getFieldQuery(String field, String queryText, int slop)
			throws ParseException {
		Query query = super.getFieldQuery(field, queryText, slop);
		if (query != null)
			exactQueries.add(query);
		return query;
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
		return super.newPrefixQuery(prefix);
	}

	protected Query newWildcardQuery(Term t) {
		isPhraseQuery = false;
		return super.newWildcardQuery(t);
	}
	
}