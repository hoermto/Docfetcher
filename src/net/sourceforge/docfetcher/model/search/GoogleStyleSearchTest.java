/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.search;

import static org.junit.Assert.assertEquals;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sourceforge.docfetcher.enums.SettingsConf;

/**
 * Tests that the search field works like Google: all words must occur, '+'
 * requires and '-' excludes a word, quotes search the exact words, OR
 * allows alternatives, and characters like ':' or '+' in words are no errors.
 */
public final class GoogleStyleSearchTest {

	private static SearchTestSupport support;

	@BeforeClass
	public static void setUpClass() throws Exception {
		assertEquals("AND is the default", false, SettingsConf.Bool.UseOrOperator.defaultValue);
		SettingsConf.Bool.UseOrOperator.set(false);
		SettingsConf.Bool.MatchFilenames.set(true);
		support = new SearchTestSupport(
			"a.txt", "dog cat mouse",
			"b.txt", "dog only",
			"c.txt", "a cat writes C++ code, Nr: 5, due 01/2025",
			"bill-2025.txt", "unrelated words");
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		support.dispose();
	}

	private static String search(String query) throws Exception {
		return support.search(query).toString();
	}

	@Test
	public void allWordsMustOccur() throws Exception {
		assertEquals("dog cat", "[a.txt]", search("dog cat"));
		assertEquals("+dog cat", "[a.txt]", search("+dog cat"));
		assertEquals("dog OR cat", "[a.txt, b.txt, c.txt]", search("dog OR cat"));
	}

	@Test
	public void minusExcludesAWord() throws Exception {
		assertEquals("dog -cat", "[b.txt]", search("dog -cat"));
		assertEquals("cat -mouse", "[c.txt]", search("cat -mouse"));
	}

	@Test
	public void quotesSearchThePhrase() throws Exception {
		assertEquals("[a.txt]", search("\"dog cat\""));
		assertEquals("[]", search("\"cat dog\""));
		assertEquals("Unbalanced quote", "[a.txt]", search("\"dog cat"));
	}

	@Test
	public void specialCharactersAreNoErrors() throws Exception {
		assertEquals("C++", "[c.txt]", search("C++"));
		assertEquals("Nr: 5", "[c.txt]", search("Nr: 5"));
		assertEquals("01/2025", "[c.txt]", search("01/2025"));
		assertEquals("(cat code", "[c.txt]", search("(cat code"));
		assertEquals("cat - code", "[c.txt]", search("cat - code"));
	}

	@Test
	public void fileNamesMatchButExcludedWordsExcludeThem() throws Exception {
		assertEquals("bill", "[bill-2025.txt]", search("bill"));
		assertEquals("words", "[bill-2025.txt]", search("words"));
		assertEquals("words -bill", "[]", search("words -bill"));
		assertEquals("dog -cat -bill", "[b.txt]", search("dog -cat -bill"));
	}

	@Test
	public void plainWordsKeepTheGoogleSyntax() {
		assertEquals("C\\+\\+ -dog +cat \"a b\" x\\:y lu?y* \\(z\\)", Searcher.asPlainWords("C++ -dog +cat \"a b\" x:y lu?y* (z)"));
		assertEquals("Odd quotes are dropped", " a b", Searcher.asPlainWords("\"a b"));
		assertEquals("A lone minus is a character", "a \\- b", Searcher.asPlainWords("a - b"));
	}

	@Test
	public void onlyRealFieldsAreFieldSearches() {
		assertEquals("title:dog Nr\\: 5 filename:x", Searcher.escapeUnknownFields("Title:dog Nr: 5 filename:x"));
	}

}
