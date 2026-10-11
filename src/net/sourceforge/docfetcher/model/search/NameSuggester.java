/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.search;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.docfetcher.model.parse.ParseException;
import net.sourceforge.docfetcher.util.CheckedOutOfMemoryError;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Suggests file names of the form {@code <date>_<word>-<word>-...} for a
 * document: The date is the first date found in the document text (or the
 * file's modification date), the words are the terms that characterize the
 * document best compared to all other indexed documents.
 */
public final class NameSuggester {

	/** Maximum number of keywords kept in a suggestion. */
	public static final int MAX_WORDS = 5;

	/** Number of keywords used when nothing else is specified. */
	public static final int DEFAULT_WORDS = 3;

	private static final SimpleDateFormat ISO_DATE = new SimpleDateFormat("yyyy-MM-dd");

	/** Characters not allowed in file names on Windows (a superset of other systems). */
	private static final Pattern ILLEGAL_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]+");

	private static final String[] MONTHS_DE = {
		"januar", "februar", "märz", "april", "mai", "juni", "juli", "august",
		"september", "oktober", "november", "dezember" };
	private static final String[] MONTHS_EN = {
		"january", "february", "march", "april", "may", "june", "july", "august",
		"september", "october", "november", "december" };

	// Common German and English words that make poor file name keywords
	static final Set<String> STOPWORDS = new HashSet<String>(Arrays.asList(
		"der", "die", "das", "und", "oder", "ein", "eine", "einer", "eines",
		"einem", "einen", "ist", "sind", "war", "waren", "wird", "werden",
		"wurde", "wurden", "nicht", "mit", "von", "zu", "zum", "zur", "für",
		"auf", "im", "in", "am", "an", "den", "dem", "des", "als", "auch",
		"bei", "nach", "über", "unter", "aus", "bis", "durch", "gegen",
		"ohne", "um", "wir", "sie", "er", "es", "ich", "ihr", "ihre", "ihren",
		"ihrem", "ihrer", "sein", "seine", "seinen", "seinem", "seiner",
		"uns", "unser", "unsere", "unseren", "unserer", "dieser", "diese",
		"dieses", "diesem", "diesen", "dass", "daß", "wenn", "wie", "was",
		"wer", "wo", "noch", "nur", "schon", "sehr", "mehr", "kann", "können",
		"muss", "müssen", "soll", "sollen", "haben", "hat", "hatte", "hatten",
		"sehr", "geehrte", "geehrter", "geehrten", "sehr", "herr", "frau",
		"herrn", "damen", "herren", "freundlichen", "grüßen", "gruß",
		"mfg", "bitte", "danke", "vielen", "dank", "sowie", "bzw", "etc",
		"the", "and", "or", "a", "an", "of", "to", "in", "on", "at", "for",
		"with", "by", "from", "is", "are", "was", "were", "be", "been",
		"being", "this", "that", "these", "those", "it", "its", "as", "not",
		"but", "if", "then", "than", "so", "we", "you", "your", "our", "they",
		"their", "he", "she", "his", "her", "him", "them", "us", "i", "my",
		"me", "will", "would", "can", "could", "should", "may", "might",
		"have", "has", "had", "do", "does", "did", "all", "any", "each",
		"dear", "sincerely", "regards", "please", "thank", "thanks", "date",
		"datum", "seite", "page", "tel", "fax", "email", "mail", "www", "http",
		"https", "com", "de", "org", "net", "gmbh", "ag", "inc", "ltd",
		// Currencies, units and invoice boilerplate
		"eur", "euro", "usd", "chf", "gbp", "cent", "kwh", "betrag",
		"gesamtbetrag", "summe", "gesamt", "nummer", "kundennummer",
		"rechnungsnummer", "zahlung", "konto", "iban", "bic", "mwst", "ust",
		"brutto", "netto", "amount", "total", "number", "account", "payment",
		"customer", "invoice number", "telefon", "internet"));

	/** A computed suggestion: the date part and the ranked keywords. */
	public static final class Suggestion {
		private final String date;
		private final List<String> words;

		Suggestion(@NotNull String date, @NotNull List<String> words) {
			this.date = date;
			this.words = words;
		}

		@NotNull
		public String getDate() {
			return date;
		}

		/** The keywords, best first, in their original casing. */
		@NotNull
		public List<String> getWords() {
			return Collections.unmodifiableList(words);
		}

		/**
		 * Builds a file name using the first {@code wordCount} keywords and
		 * the given extension (without dot; may be empty).
		 */
		@NotNull
		public String buildName(int wordCount, @NotNull String extension) {
			StringBuilder sb = new StringBuilder(date);
			List<String> used = words.subList(0, Math.min(wordCount, words.size()));
			if (!used.isEmpty())
				sb.append('_').append(Util.join("-", used));
			if (!extension.isEmpty())
				sb.append('.').append(extension);
			return sanitize(sb.toString());
		}
	}

	private NameSuggester() {}

	/**
	 * Computes a name suggestion for the given document. This parses the
	 * document and queries the indexes, so it should be run in a non-GUI
	 * thread.
	 */
	@NotNull
	public static Suggestion suggest(	@NotNull ResultDocument doc,
										@NotNull Searcher searcher)
			throws ParseException, FileNotFoundException, SearchException,
			CheckedOutOfMemoryError {
		return suggest(doc, searcher, doc.getText());
	}
	
	/**
	 * Like {@link #suggest(ResultDocument, Searcher)}, but uses the given,
	 * already extracted document text.
	 */
	@NotNull
	public static Suggestion suggest(	@NotNull ResultDocument doc,
										@NotNull Searcher searcher,
										@NotNull String text)
			throws SearchException, CheckedOutOfMemoryError {
		return suggest(text, doc.getLastModified(), searcher);
	}

	/**
	 * Computes a name suggestion from the given text, e.g. of a part of a
	 * split document that is not indexed yet. {@code fallbackDate} is used if
	 * the text contains no date; if it is null, today is used.
	 */
	@NotNull
	public static Suggestion suggest(	@NotNull String text,
										@Nullable Date fallbackDate,
										@NotNull Searcher searcher)
			throws SearchException, CheckedOutOfMemoryError {
		String date = findDate(text);
		if (date == null)
			date = ISO_DATE.format(fallbackDate == null ? new Date() : fallbackDate);

		List<String> candidates = searcher.extractKeywords(text, 40);

		/*
		 * Rank the usable candidates by their TF-IDF rank, with a strong bonus
		 * for words in the first few lines (letterhead, subject line - these
		 * usually name the sender and the matter) and a small bonus for words
		 * elsewhere on the first page.
		 */
		int headEnd = endOfLeadingLines(text, 5);
		final Map<String, Double> scores = new HashMap<String, Double>();
		final Map<String, String> originals = new HashMap<String, String>();
		int rank = 0;
		for (String term : candidates) {
			rank++;
			if (!isUsableWord(term))
				continue;
			int pos = findOriginalCasing(text, term);
			if (pos < 0)
				continue;
			String original = text.substring(pos, pos + term.length());
			String key = original.toLowerCase(Locale.ROOT);
			if (scores.containsKey(key))
				continue;
			double score = 1.0 / rank;
			if (pos < headEnd)
				score += 1.0;
			else if (pos < 1500)
				score += 0.3;
			scores.put(key, score);
			originals.put(key, capitalize(original));
		}
		List<String> keys = new ArrayList<String>(scores.keySet());
		Collections.sort(keys, new Comparator<String>() {
			public int compare(String k1, String k2) {
				return Double.compare(scores.get(k2), scores.get(k1));
			}
		});
		List<String> words = new ArrayList<String>();
		for (String key : keys) {
			if (words.size() >= MAX_WORDS)
				break;
			words.add(originals.get(key));
		}
		return new Suggestion(date, words);
	}

	/**
	 * Returns the offset at which the given number of non-empty lines ends.
	 */
	private static int endOfLeadingLines(@NotNull String text, int lines) {
		int pos = 0;
		int count = 0;
		while (pos < text.length() && count < lines) {
			int nl = text.indexOf('\n', pos);
			if (nl < 0)
				return text.length();
			if (!text.substring(pos, nl).trim().isEmpty())
				count++;
			pos = nl + 1;
		}
		return pos;
	}

	private static boolean isUsableWord(@NotNull String term) {
		if (term.length() < 3 || term.length() > 30)
			return false;
		if (STOPWORDS.contains(term.toLowerCase(Locale.ROOT)))
			return false;
		for (int i = 0; i < term.length(); i++)
			if (!Character.isLetter(term.charAt(i)))
				return false;
		return true;
	}

	/**
	 * Finds the term as a whole word in the text and returns the position of
	 * its first occurrence, or -1 if not found (e.g. the analyzer changed it).
	 */
	private static int findOriginalCasing(@NotNull String text, @NotNull String term) {
		Pattern p = Pattern.compile(
			"(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		Matcher m = p.matcher(text);
		return m.find() ? m.start() : -1;
	}

	@NotNull
	private static String capitalize(@NotNull String word) {
		if (word.isEmpty())
			return word;
		return word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
	}

	private static final Pattern DATE_ISO = Pattern.compile(
		"(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)");
	private static final Pattern DATE_DMY = Pattern.compile(
		"(?<!\\d)(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})(?!\\d)");
	private static final Pattern DATE_D_MONTH_Y = Pattern.compile(
		"(?<!\\d)(\\d{1,2})\\.?\\s+(\\p{L}+)\\.?\\s+(\\d{4})(?!\\d)",
		Pattern.UNICODE_CASE);
	private static final Pattern DATE_MONTH_D_Y = Pattern.compile(
		"(\\p{L}+)\\.?\\s+(\\d{1,2}),?\\s+(\\d{4})(?!\\d)",
		Pattern.UNICODE_CASE);

	/**
	 * Returns the document date as ISO date, or null if none is found. A date
	 * preceded by a "Datum"/"Date" label is preferred; otherwise the first
	 * plausible date in the text is used. Supported: 2026-03-12, 12.03.2026,
	 * 12. März 2026, March 12, 2026.
	 */
	@Nullable
	static String findDate(@NotNull String text) {
		// Documents usually state their date near the top
		String head = text.length() > 20000 ? text.substring(0, 20000) : text;
		String firstLabeled = null;
		String first = null;
		int firstPos = Integer.MAX_VALUE;

		List<int[]> found = new ArrayList<int[]>(); // {position, index into isoDates}
		List<String> isoDates = new ArrayList<String>();

		Matcher m = DATE_ISO.matcher(head);
		while (m.find())
			collect(found, isoDates, m.start(), toIso(m.group(1), m.group(2), m.group(3)));
		m = DATE_DMY.matcher(head);
		while (m.find())
			collect(found, isoDates, m.start(), toIso(m.group(3), m.group(2), m.group(1)));
		m = DATE_D_MONTH_Y.matcher(head);
		while (m.find()) {
			int month = monthOf(m.group(2));
			if (month > 0)
				collect(found, isoDates, m.start(), toIso(m.group(3), String.valueOf(month), m.group(1)));
		}
		m = DATE_MONTH_D_Y.matcher(head);
		while (m.find()) {
			int month = monthOf(m.group(1));
			if (month > 0)
				collect(found, isoDates, m.start(), toIso(m.group(3), String.valueOf(month), m.group(2)));
		}

		int firstLabeledPos = Integer.MAX_VALUE;
		for (int[] entry : found) {
			int pos = entry[0];
			String iso = isoDates.get(entry[1]);
			if (pos < firstPos) {
				firstPos = pos;
				first = iso;
			}
			String before = head.substring(Math.max(0, pos - 16), pos).toLowerCase(Locale.ROOT);
			if ((before.contains("datum") || before.contains("date")) && pos < firstLabeledPos) {
				firstLabeledPos = pos;
				firstLabeled = iso;
			}
		}
		return firstLabeled != null ? firstLabeled : first;
	}

	private static void collect(@NotNull List<int[]> found,
								@NotNull List<String> isoDates,
								int pos,
								@Nullable String iso) {
		if (iso == null)
			return;
		isoDates.add(iso);
		found.add(new int[] { pos, isoDates.size() - 1 });
	}

	private static int monthOf(@NotNull String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		for (int i = 0; i < 12; i++) {
			if (MONTHS_DE[i].startsWith(lower) && lower.length() >= 3)
				return i + 1;
			if (MONTHS_EN[i].startsWith(lower) && lower.length() >= 3)
				return i + 1;
		}
		return -1;
	}

	@Nullable
	private static String toIso(String year, String month, String day) {
		try {
			int y = Integer.parseInt(year);
			int mo = Integer.parseInt(month);
			int d = Integer.parseInt(day);
			if (y < 1900 || y > 2100 || mo < 1 || mo > 12 || d < 1 || d > 31)
				return null;
			return String.format("%04d-%02d-%02d", y, mo, d);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Removes characters that are not allowed in file names and collapses
	 * whitespace.
	 */
	@NotNull
	public static String sanitize(@NotNull String name) {
		String result = ILLEGAL_CHARS.matcher(name).replaceAll("-");
		result = result.replaceAll("\\s+", "-").replaceAll("-{2,}", "-");
		result = result.replaceAll("^[-. ]+", "").replaceAll("[-. ]+$", "");
		return result;
	}

}
