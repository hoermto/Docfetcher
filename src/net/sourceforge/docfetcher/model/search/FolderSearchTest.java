/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.sourceforge.docfetcher.util.UtilFile;

import org.junit.Test;

/** Tests the lookup of folders whose name matches the search terms. */
public final class FolderSearchTest {

	@Test
	public void takesThePlainWordsOfAQuery() {
		assertEquals(Arrays.asList("rechnung"), FolderSearch.extractTerms("Rechnung"));
		assertEquals(Arrays.asList("rechnung", "2026"),
			FolderSearch.extractTerms("rechnung AND 2026"));
		// Wildcards, quotes and parentheses are part of the syntax
		assertEquals(Arrays.asList("rechnung"), FolderSearch.extractTerms("\"rechnung*\""));
		assertEquals(Arrays.asList("rechnung", "telekom"),
			FolderSearch.extractTerms("(rechnung OR telekom)"));
		// A field name is not a search term
		assertEquals(Arrays.asList("brief"), FolderSearch.extractTerms("filename:brief"));
		// Single characters would match almost any folder
		assertEquals(Collections.emptyList(), FolderSearch.extractTerms("a b"));
		assertEquals(Collections.emptyList(), FolderSearch.extractTerms("   "));
		// Each term is listed once
		assertEquals(Arrays.asList("akte"), FolderSearch.extractTerms("akte akte"));
	}

	@Test
	public void matchesFolderNamesCaseInsensitively() {
		List<String> terms = Arrays.asList("rechnung");
		assertTrue(FolderSearch.matches("Rechnungen 2026", terms));
		assertTrue(FolderSearch.matches("RECHNUNG", terms));
		assertFalse(FolderSearch.matches("Briefe", terms));
		// All terms must be in the name
		List<String> two = Arrays.asList("rechnung", "2026");
		assertTrue(FolderSearch.matches("Rechnungen 2026", two));
		assertFalse(FolderSearch.matches("Rechnungen 2025", two));
		// Without terms nothing matches, so a search can't list every folder
		assertFalse(FolderSearch.matches("Rechnungen", Collections.<String> emptyList()));
	}

	@Test
	public void findsMatchingFoldersInTheTree() throws Exception {
		File root = File.createTempFile("docfetcher-test", "");
		assertTrue(root.delete() && root.mkdir());
		try {
			File sub = new File(root, "Briefe");
			File deep = new File(sub, "Rechnungen 2026");
			File other = new File(root, "Bilder");
			assertTrue(deep.mkdirs() && other.mkdir());

			List<File> hits = FolderSearch.find(
				Collections.singletonList(root), Arrays.asList("rechnung"));
			assertEquals(Arrays.asList(deep), hits);

			// The root folders themselves are matched too
			hits = FolderSearch.find(
				Collections.singletonList(sub), Arrays.asList("brief"));
			assertEquals(Arrays.asList(sub), hits);

			// Without a matching name nothing is found
			assertTrue(FolderSearch.find(
				Collections.singletonList(root), Arrays.asList("vertrag")).isEmpty());
			// An empty term list never walks the tree
			assertTrue(FolderSearch.find(
				Collections.singletonList(root), Collections.<String> emptyList()).isEmpty());
		}
		finally {
			UtilFile.deletePermanently(root);
		}
	}

}
