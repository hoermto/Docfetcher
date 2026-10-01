/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;

import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Makes the search tolerant: words are also found without their umlauts and
 * accents ('Muller' finds 'Müller' and the other way round, 'strasse' finds
 * 'Straße'), and despite typical recognition errors of the OCR in words with
 * letters and digits ('Bonn' finds 'B0nn', 'Hallo' finds 'Ha11o').
 * <p>
 * This happens only at search time: the index keeps the words as they are in
 * the documents, and each word of a query is expanded to the indexed words
 * with the same folded form ({@link #fold(String)}), e.g. 'Muller' to 'muller
 * OR müller'. So the indexes never need to be rebuilt for it, the documents
 * are not read again, and the rules can change without touching the indexes.
 * The indexed words that differ from their folded form are collected once per
 * opened index reader and field. Words and phrases in quotes are searched
 * exactly, as in Google.
 */
final class TolerantSearch {

	/** Limits for the variants added to one wildcard term or phrase. */
	private static final int MAX_WILDCARD_VARIANTS = 256;
	private static final int MAX_PHRASE_VARIANTS = 16;

	private final IndexReader reader;

	/** Field -> folded form -> the indexed words with this folded form that differ from it. */
	private final Map<String, Map<String, List<String>>> variants = new HashMap<String, Map<String, List<String>>>();

	TolerantSearch(@NotNull IndexReader reader) {
		this.reader = reader;
	}

	/**
	 * Returns the tolerant form of a (lower case) word: umlauts and accents
	 * replaced by the plain letters (ß by ss), and, in words with letters and
	 * digits, 0, 1 and 5 replaced by o, l and s. Pure numbers stay as they are.
	 */
	@NotNull
	static String fold(@NotNull String term) {
		char[] input = term.toCharArray();
		char[] output = new char[input.length * 4];
		int length = ASCIIFoldingFilter.foldToASCII(input, 0, output, 0, input.length);
		boolean letters = false;
		boolean digits = false;
		for (int i = 0; i < length; i++) {
			letters |= Character.isLetter(output[i]);
			digits |= Character.isDigit(output[i]);
		}
		if (letters && digits) {
			for (int i = 0; i < length; i++) {
				switch (output[i]) {
				case '0': output[i] = 'o'; break;
				case '1': output[i] = 'l'; break;
				case '5': output[i] = 's'; break;
				default: break;
				}
			}
		}
		return new String(output, 0, length);
	}

	/** Quick check before folding: only non-ASCII letters and 0, 1, 5 can change. */
	private static boolean mayFold(@NotNull String term) {
		for (int i = 0; i < term.length(); i++) {
			char c = term.charAt(i);
			if (c > 127 || c == '0' || c == '1' || c == '5')
				return true;
		}
		return false;
	}

	/**
	 * Returns the given query with every word expanded to its tolerant
	 * variants, except in the given (quoted) parts of the query.
	 */
	@NotNull
	Query expand(@NotNull Query query, @NotNull Set<Query> exact) throws IOException {
		if (exact.contains(query))
			return query;
		if (query instanceof BooleanQuery) {
			BooleanQuery bool = (BooleanQuery) query;
			BooleanQuery.Builder builder = new BooleanQuery.Builder();
			builder.setMinimumNumberShouldMatch(bool.getMinimumNumberShouldMatch());
			for (BooleanClause clause : bool.clauses())
				builder.add(expand(clause.getQuery(), exact), clause.getOccur());
			return builder.build();
		}
		if (query instanceof BoostQuery) {
			BoostQuery boost = (BoostQuery) query;
			return new BoostQuery(expand(boost.getQuery(), exact), boost.getBoost());
		}
		if (query instanceof TermQuery) {
			Term term = ((TermQuery) query).getTerm();
			Set<String> words = variantsOf(term);
			return words.size() == 1 ? query : anyOf(term.field(), words);
		}
		if (query instanceof PhraseQuery)
			return expandPhrase((PhraseQuery) query);
		if (query instanceof PrefixQuery) {
			Term prefix = ((PrefixQuery) query).getPrefix();
			String folded = fold(prefix.text());
			List<String> matches = new ArrayList<String>();
			for (Map.Entry<String, List<String>> entry : variants(prefix.field()).entrySet())
				if (entry.getKey().startsWith(folded))
					matches.addAll(entry.getValue());
			Query foldedQuery = folded.equals(prefix.text()) ? null
				: new PrefixQuery(new Term(prefix.field(), folded));
			return withVariants((MultiTermQuery) query, foldedQuery, prefix.field(), matches);
		}
		if (query instanceof WildcardQuery) {
			Term pattern = ((WildcardQuery) query).getTerm();
			Term folded = new Term(pattern.field(), fold(pattern.text()));
			CharacterRunAutomaton automaton = new CharacterRunAutomaton(WildcardQuery.toAutomaton(folded));
			List<String> matches = new ArrayList<String>();
			for (Map.Entry<String, List<String>> entry : variants(pattern.field()).entrySet())
				if (automaton.run(entry.getKey()))
					matches.addAll(entry.getValue());
			Query foldedQuery = folded.equals(pattern) ? null : new WildcardQuery(folded);
			return withVariants((MultiTermQuery) query, foldedQuery, pattern.field(), matches);
		}
		return query;
	}

	/** The word itself, its folded form and the indexed words with that folded form. */
	@NotNull
	private Set<String> variantsOf(@NotNull Term term) throws IOException {
		Set<String> words = new LinkedHashSet<String>();
		words.add(term.text());
		String folded = fold(term.text());
		words.add(folded);
		List<String> indexed = variants(term.field()).get(folded);
		if (indexed != null)
			words.addAll(indexed);
		return words;
	}

	/** A phrase becomes a choice of phrases, one per combination of variants. */
	@NotNull
	private Query expandPhrase(@NotNull PhraseQuery phrase) throws IOException {
		Term[] terms = phrase.getTerms();
		int[] positions = phrase.getPositions();
		List<List<String>> choices = new ArrayList<List<String>>();
		boolean any = false;
		for (Term term : terms) {
			List<String> words = new ArrayList<String>(variantsOf(term));
			any |= words.size() > 1;
			choices.add(words);
		}
		if (!any || terms.length == 0)
			return phrase;
		String field = terms[0].field();
		List<List<String>> combinations = new ArrayList<List<String>>();
		combinations.add(new ArrayList<String>());
		for (List<String> words : choices) {
			List<List<String>> next = new ArrayList<List<String>>();
			for (List<String> start : combinations) {
				for (String word : words) {
					if (next.size() >= MAX_PHRASE_VARIANTS)
						break;
					List<String> combination = new ArrayList<String>(start);
					combination.add(word);
					next.add(combination);
				}
			}
			combinations = next;
		}
		BooleanQuery.Builder builder = new BooleanQuery.Builder();
		for (List<String> combination : combinations) {
			PhraseQuery.Builder phraseBuilder = new PhraseQuery.Builder();
			phraseBuilder.setSlop(phrase.getSlop());
			for (int i = 0; i < combination.size(); i++)
				phraseBuilder.add(new Term(field, combination.get(i)), positions[i]);
			builder.add(phraseBuilder.build(), BooleanClause.Occur.SHOULD);
		}
		return builder.build();
	}

	@NotNull
	private Query withVariants(	@NotNull MultiTermQuery query,
								@Nullable Query foldedQuery,
								@NotNull String field,
								@NotNull List<String> words) {
		if (foldedQuery == null && words.isEmpty())
			return query;
		BooleanQuery.Builder builder = new BooleanQuery.Builder();
		builder.add(query, BooleanClause.Occur.SHOULD);
		if (foldedQuery != null) {
			((MultiTermQuery) foldedQuery).setRewriteMethod(query.getRewriteMethod());
			builder.add(foldedQuery, BooleanClause.Occur.SHOULD);
		}
		for (int i = 0; i < words.size() && i < MAX_WILDCARD_VARIANTS; i++)
			builder.add(new TermQuery(new Term(field, words.get(i))), BooleanClause.Occur.SHOULD);
		return builder.build();
	}

	@NotNull
	private static Query anyOf(@NotNull String field, @NotNull Set<String> words) {
		BooleanQuery.Builder builder = new BooleanQuery.Builder();
		for (String word : words)
			builder.add(new TermQuery(new Term(field, word)), BooleanClause.Occur.SHOULD);
		return builder.build();
	}

	/** Collects the indexed words of a field that differ from their folded form, once. */
	@NotNull
	private synchronized Map<String, List<String>> variants(@NotNull String field) throws IOException {
		Map<String, List<String>> map = variants.get(field);
		if (map != null)
			return map;
		map = new HashMap<String, List<String>>();
		Terms terms = MultiFields.getTerms(reader, field);
		if (terms != null) {
			TermsEnum termsEnum = terms.iterator();
			for (BytesRef ref = termsEnum.next(); ref != null; ref = termsEnum.next()) {
				String word = ref.utf8ToString();
				if (!mayFold(word))
					continue;
				String folded = fold(word);
				if (folded.equals(word))
					continue;
				List<String> words = map.get(folded);
				if (words == null) {
					words = new ArrayList<String>(1);
					map.put(folded, words);
				}
				words.add(word);
			}
		}
		variants.put(field, map);
		return map;
	}

}
