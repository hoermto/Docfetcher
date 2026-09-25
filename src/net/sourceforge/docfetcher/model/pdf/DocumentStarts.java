/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.docfetcher.model.pdf.PdfPages.PageInfo;
import net.sourceforge.docfetcher.model.pdf.PdfPages.Role;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Suggests where the documents of a scanned stack begin, from the text of the
 * pages. Each page after the first gets a role and the reason for it; the
 * signals are checked in this order, the first one that applies decides:
 * <ol>
 * <li>A blank page or separator sheet is left out, the next page starts.
 * <li>Page numbers: "Seite 1 von 3" starts, "Seite 2 von 3" continues; after
 * the last page ("3 von 3") a new document starts.
 * <li>A salutation near the top ("Sehr geehrte", "Dear", ...) starts.
 * <li>A number shared with the page before (invoice number, customer number,
 * IBAN, ...) continues.
 * <li>With little text, there is nothing to decide on: continues.
 * <li>Otherwise the words are compared with the page before (in parts of
 * four letters, weighted by how rare they are): little in common starts,
 * similar text continues.
 * </ol>
 * The suggestion is only a starting point; the split dialog lets the user
 * correct it page by page.
 */
public final class DocumentStarts {

	/** Why a page got its role. */
	public enum Reason {
		FIRST_PAGE,
		SEPARATOR,
		AFTER_SEPARATOR,
		PAGE_NUMBER_ONE,
		PAGE_NUMBER_CONTINUES,
		AFTER_LAST_PAGE,
		SALUTATION,
		SHARED_NUMBER,
		LITTLE_TEXT,
		DIFFERENT_TEXT,
		SIMILAR_TEXT,
		SAME_DOCUMENT,
	}

	/** The suggestion for one page. */
	public static final class Suggestion {
		public final Role role;
		public final Reason reason;
		/** The page number ("2 von 3") or the shared number, or null. */
		@Nullable public final String detail;

		Suggestion(@NotNull Role role, @NotNull Reason reason, @Nullable String detail) {
			this.role = role;
			this.reason = reason;
			this.detail = detail;
		}

		public String toString() {
			return role + " " + reason + (detail == null ? "" : " (" + detail + ")");
		}
	}

	/** Pages with fewer parts of words than this can't be compared. */
	static final int MIN_WORDS = 12;

	/** Length of the parts of words that are compared. */
	private static final int GRAM = 4;

	/** Below this similarity (0..1) of the words, a new document starts. */
	static final double SIMILARITY_THRESHOLD = 0.12;

	// "Seite 2 von 3", "Page 2 of 3", "Blatt 2/3", "S. 2 von 3"
	private static final Pattern PAGE_OF = Pattern.compile(
		"(?i)\\b(?:seite|page|blatt|s\\.)\\s*(\\d{1,3})\\s*(?:von|of|aus|/)\\s*(\\d{1,3})\\b");
	// "- 2 -" on a line of its own
	private static final Pattern PAGE_DASHES = Pattern.compile("(?m)^\\s*-\\s*(\\d{1,3})\\s*-\\s*$");
	private static final Pattern SALUTATION = Pattern.compile(
		"(?i)\\b(sehr geehrte[rs]?|liebe[rs]?|hallo|guten tag|dear|hello)\\b");
	// Tokens with at least 4 digits, e.g. RE-2026-4711, 1234567, DE89370400440532013000
	private static final Pattern NUMBER = Pattern.compile("[\\p{L}\\d][\\p{L}\\d/-]{3,}");
	private static final Pattern DATE = Pattern.compile("\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}|\\d{4}-\\d{2}-\\d{2}");
	private static final Pattern YEAR = Pattern.compile("(19|20)\\d\\d");
	private static final Pattern WORD = Pattern.compile("\\p{L}{3,}");

	private DocumentStarts() {}

	/** Suggests the role of each page; the first page with text starts. */
	@NotNull
	public static List<Suggestion> suggest(@NotNull List<PageInfo> pages) {
		int n = pages.size();
		List<Map<String, Integer>> words = new ArrayList<Map<String, Integer>>(n);
		Map<String, Integer> pageFrequency = new HashMap<String, Integer>();
		List<Set<String>> numbers = new ArrayList<Set<String>>(n);
		Map<String, Integer> numberFrequency = new HashMap<String, Integer>();
		for (PageInfo page : pages) {
			Map<String, Integer> pageWords = countWords(page.text);
			words.add(pageWords);
			for (String word : pageWords.keySet())
				increment(pageFrequency, word);
			Set<String> pageNumbers = findNumbers(page.text);
			numbers.add(pageNumbers);
			for (String number : pageNumbers)
				increment(numberFrequency, number);
		}

		List<Suggestion> result = new ArrayList<Suggestion>(n);
		int previous = -1; // The last page that is not a separator
		boolean separatorBefore = false;
		for (int i = 0; i < n; i++) {
			PageInfo page = pages.get(i);
			if (page.isSeparator()) {
				result.add(new Suggestion(Role.SEPARATOR, Reason.SEPARATOR, null));
				separatorBefore = true;
				continue;
			}
			Suggestion suggestion;
			if (previous < 0)
				suggestion = new Suggestion(Role.START, Reason.FIRST_PAGE, null);
			else if (separatorBefore)
				suggestion = new Suggestion(Role.START, Reason.AFTER_SEPARATOR, null);
			else
				suggestion = decide(pages.get(previous), page, words.get(previous), words.get(i),
					numbers.get(previous), numbers.get(i), numberFrequency, pageFrequency, n);
			result.add(suggestion);
			previous = i;
			separatorBefore = false;
		}
		return Collections.unmodifiableList(result);
	}

	@NotNull
	private static Suggestion decide(	@NotNull PageInfo before,
										@NotNull PageInfo page,
										@NotNull Map<String, Integer> wordsBefore,
										@NotNull Map<String, Integer> pageWords,
										@NotNull Set<String> numbersBefore,
										@NotNull Set<String> pageNumbers,
										@NotNull Map<String, Integer> numberFrequency,
										@NotNull Map<String, Integer> pageFrequency,
										int pageCount) {
		int[] number = findPageNumber(page.text);
		if (number != null) {
			String detail = number[1] > 0 ? number[0] + "/" + number[1] : String.valueOf(number[0]);
			if (number[0] == 1)
				return new Suggestion(Role.START, Reason.PAGE_NUMBER_ONE, detail);
			return new Suggestion(Role.CONTINUE, Reason.PAGE_NUMBER_CONTINUES, detail);
		}
		int[] numberBefore = findPageNumber(before.text);
		if (numberBefore != null && numberBefore[1] > 0 && numberBefore[0] == numberBefore[1])
			return new Suggestion(Role.START, Reason.AFTER_LAST_PAGE,
				numberBefore[0] + "/" + numberBefore[1]);
		if (hasSalutation(page.text))
			return new Suggestion(Role.START, Reason.SALUTATION, null);
		for (String candidate : pageNumbers) {
			// Numbers on (almost) every page, e.g. the own customer number, don't tell anything
			if (pageCount >= 4 && numberFrequency.get(candidate) > pageCount / 2)
				continue;
			if (numbersBefore.contains(candidate))
				return new Suggestion(Role.CONTINUE, Reason.SHARED_NUMBER, candidate);
		}
		if (total(pageWords) < MIN_WORDS || total(wordsBefore) < MIN_WORDS)
			return new Suggestion(Role.CONTINUE, Reason.LITTLE_TEXT, null);
		double similarity = similarity(wordsBefore, pageWords, pageFrequency, pageCount);
		String detail = Math.round(similarity * 100) + " %";
		if (similarity < SIMILARITY_THRESHOLD)
			return new Suggestion(Role.START, Reason.DIFFERENT_TEXT, detail);
		return new Suggestion(Role.CONTINUE, Reason.SIMILAR_TEXT, detail);
	}

	/**
	 * Returns the page number and the page count ("2 von 3" -> {2, 3}), or
	 * {page, 0} if only the page number is given ("- 2 -"), or null.
	 */
	@Nullable
	static int[] findPageNumber(@NotNull String text) {
		Matcher m = PAGE_OF.matcher(text);
		while (m.find()) {
			int page = Integer.parseInt(m.group(1));
			int count = Integer.parseInt(m.group(2));
			if (page >= 1 && page <= count)
				return new int[] { page, count };
		}
		m = PAGE_DASHES.matcher(text);
		if (m.find())
			return new int[] { Integer.parseInt(m.group(1)), 0 };
		return null;
	}

	/** A salutation in the upper part of the page, where letters start. */
	static boolean hasSalutation(@NotNull String text) {
		String top = text.substring(0, Math.max(0, (int) (text.length() * 0.5)));
		return SALUTATION.matcher(top).find();
	}

	/** Tokens with at least four digits that are not dates or years. */
	@NotNull
	static Set<String> findNumbers(@NotNull String text) {
		Set<String> numbers = new LinkedHashSet<String>();
		Matcher m = NUMBER.matcher(text);
		while (m.find()) {
			String token = m.group().replaceAll("[/-]+$", "");
			int digits = 0;
			for (char c : token.toCharArray())
				if (Character.isDigit(c))
					digits++;
			if (digits < 4 || DATE.matcher(token).matches() || YEAR.matcher(token).matches())
				continue;
			numbers.add(token.toUpperCase());
		}
		return numbers;
	}

	/**
	 * Counts the parts of four letters of the words ('hausrat' -> 'haus',
	 * 'ausr', ...), so that compounds ('Hausratversicherung' and 'Hausrat')
	 * and words with a recognition error still have something in common.
	 */
	@NotNull
	private static Map<String, Integer> countWords(@NotNull String text) {
		Map<String, Integer> counts = new HashMap<String, Integer>();
		Matcher m = WORD.matcher(text.toLowerCase());
		while (m.find()) {
			String word = m.group();
			if (word.length() <= GRAM)
				increment(counts, word);
			for (int i = 0; i + GRAM <= word.length(); i++)
				increment(counts, word.substring(i, i + GRAM));
		}
		return counts;
	}

	/**
	 * Cosine similarity of the word counts, each word weighted by how rare it
	 * is among the pages, so that words on every page (e.g. the letterhead of
	 * the own company, or 'und') count little.
	 */
	static double similarity(	@NotNull Map<String, Integer> a,
								@NotNull Map<String, Integer> b,
								@NotNull Map<String, Integer> pageFrequency,
								int pageCount) {
		double dot = 0, normA = 0, normB = 0;
		Set<String> all = new HashSet<String>(a.keySet());
		all.addAll(b.keySet());
		for (String word : all) {
			Integer df = pageFrequency.get(word);
			double idf = Math.log(1.0 + pageCount / (double) (df == null ? 1 : df));
			double wa = a.containsKey(word) ? (1 + Math.log(a.get(word))) * idf : 0;
			double wb = b.containsKey(word) ? (1 + Math.log(b.get(word))) * idf : 0;
			dot += wa * wb;
			normA += wa * wa;
			normB += wb * wb;
		}
		if (normA == 0 || normB == 0)
			return 0;
		return dot / Math.sqrt(normA * normB);
	}

	private static int total(@NotNull Map<String, Integer> counts) {
		int sum = 0;
		for (int count : counts.values())
			sum += count;
		return sum;
	}

	private static void increment(@NotNull Map<String, Integer> counts, @NotNull String key) {
		Integer count = counts.get(key);
		counts.put(key, count == null ? 1 : count + 1);
	}

	/** Suggestion that only starts after separators; for the 'separator pages' choice. */
	@NotNull
	public static List<Suggestion> atSeparators(@NotNull List<PageInfo> pages) {
		List<Suggestion> result = new ArrayList<Suggestion>(pages.size());
		boolean start = true;
		for (PageInfo page : pages) {
			if (page.isSeparator()) {
				result.add(new Suggestion(Role.SEPARATOR, Reason.SEPARATOR, null));
				start = true;
			}
			else {
				result.add(start
					? new Suggestion(Role.START, result.isEmpty() || allSeparators(result)
						? Reason.FIRST_PAGE : Reason.AFTER_SEPARATOR, null)
					: new Suggestion(Role.CONTINUE, Reason.SAME_DOCUMENT, null));
				start = false;
			}
		}
		return Collections.unmodifiableList(result);
	}

	private static boolean allSeparators(@NotNull List<Suggestion> suggestions) {
		for (Suggestion suggestion : suggestions)
			if (suggestion.role != Role.SEPARATOR)
				return false;
		return true;
	}

}
