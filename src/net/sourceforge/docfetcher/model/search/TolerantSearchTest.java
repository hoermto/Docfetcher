/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.search;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.model.IndexRegistry;

/**
 * Tests the tolerant search: umlauts and typical OCR errors, at search time
 * only - the index keeps the words as they are. Words in quotes are exact.
 */
public final class TolerantSearchTest {

	private static SearchTestSupport support;

	@BeforeClass
	public static void setUpClass() throws Exception {
		SettingsConf.Bool.TolerantSearch.set(true);
		support = new SearchTestSupport(
			"letter.txt", "Rechnung für Herrn Müller",
			"scan.txt", "Stadtwerke B0nn, Kundennummer 4711",
			"street.txt", "Wohnung in der Hauptstraße",
			"plain.txt", "Mueller and Muller are other names");
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		support.dispose();
	}

	private static String search(String query) throws Exception {
		return support.search(query).toString();
	}

	private static List<String> tokens(String text) throws Exception {
		List<String> tokens = new ArrayList<String>();
		TokenStream stream = IndexRegistry.getAnalyzer().tokenStream("content", text);
		CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
		stream.reset();
		while (stream.incrementToken())
			tokens.add(term.toString());
		stream.end();
		stream.close();
		return tokens;
	}

	@Test
	public void foldsUmlautsAndOcrErrors() {
		assertEquals("muller", TolerantSearch.fold("müller"));
		assertEquals("strasse", TolerantSearch.fold("straße"));
		assertEquals("cafe", TolerantSearch.fold("café"));
		assertEquals("bonn", TolerantSearch.fold("b0nn"));
		assertEquals("hallo", TolerantSearch.fold("ha11o"));
		assertEquals("rechnungsnummer", TolerantSearch.fold("rechnungsnummer"));
		assertEquals("Numbers stay numbers", "4711", TolerantSearch.fold("4711"));
		assertEquals("2025", TolerantSearch.fold("2025"));
	}

	@Test
	public void theIndexKeepsTheWordsAsTheyAre() throws Exception {
		assertEquals("[herrn, müller, b0nn, 4711]", tokens("Herrn Müller B0nn 4711").toString());
	}

	@Test
	public void findsWordsWithAndWithoutUmlauts() throws Exception {
		assertEquals("[letter.txt, plain.txt]", search("Muller"));
		assertEquals("[letter.txt, plain.txt]", search("Müller"));
		assertEquals("[street.txt]", search("hauptstrasse"));
		assertEquals("[street.txt]", search("Hauptstraße"));
		assertEquals("[letter.txt]", search("fur"));
	}

	@Test
	public void findsWordsDespiteOcrErrors() throws Exception {
		assertEquals("[scan.txt]", search("Bonn"));
		assertEquals("[scan.txt]", search("B0nn"));
		assertEquals("Exact numbers", "[scan.txt]", search("4711"));
	}

	@Test
	public void wildcardsAreTolerantToo() throws Exception {
		assertEquals("[letter.txt, plain.txt]", search("Mül*"));
		assertEquals("[letter.txt, plain.txt]", search("Mul*"));
		assertEquals("[street.txt]", search("haupt*strasse"));
	}

	@Test
	public void wordsInQuotesAreExact() throws Exception {
		assertEquals("[plain.txt]", search("\"Muller\""));
		assertEquals("[letter.txt]", search("\"Müller\""));
		assertEquals("[letter.txt]", search("\"Herrn Müller\""));
		assertEquals("[]", search("\"Herrn Muller\""));
		assertEquals("Tolerant outside the quotes", "[letter.txt]", search("\"Rechnung\" Muller"));
	}

	@Test
	public void switchedOffOnlyExactWordsAreFound() throws Exception {
		SettingsConf.Bool.TolerantSearch.set(false);
		try {
			assertEquals("[plain.txt]", search("Muller"));
			assertEquals("[]", search("Bonn"));
		}
		finally {
			SettingsConf.Bool.TolerantSearch.set(true);
		}
	}

	@Test
	public void theMatchesAreHighlighted() throws Exception {
		for (ResultDocument doc : support.registry.getSearcher().search("Muller")) {
			if (!doc.getFilename().equals("letter.txt"))
				continue;
			HighlightedString text = doc.getHighlightedText();
			assertEquals(1, text.getRangeCount());
			return;
		}
		throw new AssertionError("letter.txt not found");
	}

}
