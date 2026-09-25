/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.index.IndexingReporter;
import net.sourceforge.docfetcher.model.index.Task.CancelAction;
import net.sourceforge.docfetcher.model.index.Task.CancelHandler;
import net.sourceforge.docfetcher.model.index.file.FileIndex;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.Util;

/**
 * Tests the tolerant search: umlauts and typical OCR errors, at search time
 * only - the index keeps the words as they are.
 */
public final class TolerantSearchTest {

	private static File root;
	private static File indexDir;
	private static IndexRegistry registry;

	@BeforeClass
	public static void setUpClass() throws Exception {
		AppUtil.Const.autoInit();
		SettingsConf.Bool.TolerantSearch.set(true);
		IndexRegistry.resetAnalyzer();
		root = createDir("docfetcher-tolerant");
		indexDir = createDir("docfetcher-tolerant-index");
		write(new File(root, "letter.txt"), "Rechnung für Herrn Müller");
		write(new File(root, "scan.txt"), "Stadtwerke B0nn, Kundennummer 4711");
		write(new File(root, "street.txt"), "Wohnung in der Hauptstraße");
		write(new File(root, "plain.txt"), "Mueller and Muller are other names");
		registry = new IndexRegistry(indexDir, 10, 100);
		registry.load(Cancelable.nullCancelable);
		FileIndex index = new FileIndex(indexDir, root);
		index.update(new IndexingReporter(), Cancelable.nullCancelable);
		registry.addIndex(index);
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		registry.getQueue().shutdown(new CancelHandler() {
			public CancelAction cancel() {
				return CancelAction.DISCARD;
			}
		});
		for (File dir : new File[] { root, indexDir }) {
			try {
				Util.deleteRecursively(dir);
			}
			catch (Exception e) {
				dir.deleteOnExit(); // Index files may still be locked on Windows
			}
		}
	}

	private static File createDir(String prefix) throws Exception {
		File dir = File.createTempFile(prefix, "");
		assertTrue(dir.delete() && dir.mkdir());
		return dir;
	}

	private static void write(File file, String text) throws Exception {
		java.nio.file.Files.write(file.toPath(), text.getBytes("UTF-8"));
	}

	private static Set<String> search(String query) throws Exception {
		Set<String> names = new TreeSet<String>();
		for (ResultDocument doc : registry.getSearcher().search(query))
			names.add(doc.getFilename());
		return names;
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
		assertEquals("[letter.txt, plain.txt]", search("Muller").toString());
		assertEquals("[letter.txt, plain.txt]", search("Müller").toString());
		assertEquals("[street.txt]", search("hauptstrasse").toString());
		assertEquals("[street.txt]", search("Hauptstraße").toString());
		assertEquals("[letter.txt]", search("fur").toString());
	}

	@Test
	public void findsWordsDespiteOcrErrors() throws Exception {
		assertEquals("[scan.txt]", search("Bonn").toString());
		assertEquals("[scan.txt]", search("B0nn").toString());
		assertEquals("Exact numbers", "[scan.txt]", search("4711").toString());
	}

	@Test
	public void wildcardsAreTolerantToo() throws Exception {
		assertEquals("[letter.txt, plain.txt]", search("Mül*").toString());
		assertEquals("[letter.txt, plain.txt]", search("Mul*").toString());
		assertEquals("[street.txt]", search("haupt*strasse").toString());
	}

	@Test
	public void phrasesAreTolerantToo() throws Exception {
		assertEquals("[letter.txt]", search("\"Herrn Muller\"").toString());
		assertEquals("[scan.txt]", search("\"Stadtwerke Bonn\"").toString());
		assertEquals("[]", search("\"Muller Herrn\"").toString());
	}

	@Test
	public void switchedOffOnlyExactWordsAreFound() throws Exception {
		SettingsConf.Bool.TolerantSearch.set(false);
		try {
			assertEquals("[plain.txt]", search("Muller").toString());
			assertEquals("[]", search("Bonn").toString());
		}
		finally {
			SettingsConf.Bool.TolerantSearch.set(true);
		}
	}

	@Test
	public void theMatchesAreHighlighted() throws Exception {
		for (ResultDocument doc : registry.getSearcher().search("Muller")) {
			if (!doc.getFilename().equals("letter.txt"))
				continue;
			HighlightedString text = doc.getHighlightedText();
			assertEquals(1, text.getRangeCount());
			return;
		}
		throw new AssertionError("letter.txt not found");
	}

}
