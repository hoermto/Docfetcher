/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.gui.PdfOperations.Answer;
import net.sourceforge.docfetcher.model.pdf.PdfPages;
import net.sourceforge.docfetcher.model.pdf.PdfPages.PageInfo;
import net.sourceforge.docfetcher.model.pdf.PdfPages.Range;
import net.sourceforge.docfetcher.model.pdf.PdfPages.Role;
import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.util.UtilFile;

/**
 * Tests merging and splitting of PDF files in a result table, what happens
 * to the originals, and the undo of both.
 */
public final class PdfOperationsTest {

	private static Display display;
	private GuiTestSupport support;
	private Shell shell;
	private final List<ResultDocument> removedRows = new ArrayList<ResultDocument>();
	private final List<File> changedFiles = new ArrayList<File>();

	private final PdfOperations.Host host = new PdfOperations.Host() {
		public Shell getShell() {
			return shell;
		}
		public void removeRows(List<ResultDocument> docs) {
			removedRows.addAll(docs);
		}
		public void filesChanged(List<File> files) {
			changedFiles.addAll(files);
		}
	};

	@BeforeClass
	public static void setUpClass() {
		display = GuiTestSupport.createDisplay();
		UtilFile.setTrashDisabled(true);
	}

	@AfterClass
	public static void tearDownClass() {
		UtilFile.setTrashDisabled(false);
		if (display != null)
			display.dispose();
	}

	@Before
	public void setUp() throws Exception {
		Assume.assumeTrue(display != null);
		shell = new Shell(display);
		support = GuiTestSupport.createIndexedFolder("docfetcher-pdfops", new GuiTestSupport.FolderFiller() {
			public void fill(File root) throws Exception {
				DocumentPrinterTest.createPdf(root, "a.pdf", 2);
				DocumentPrinterTest.createPdf(root, "b.pdf", 1);
				DocumentPrinterTest.createPdf(root, "stack.pdf", 5);
				GuiTestSupport.write(new File(root, "note.txt"), "no pdf");
			}
		});
		FileOperationHistory.getInstance().clear();
		FileOperationHistory.getInstance().setBackupDir(new File(support.root, ".undo"));
	}

	@After
	public void tearDown() throws Exception {
		if (support == null)
			return;
		FileOperationHistory.getInstance().clear();
		shell.dispose();
		support.dispose();
	}

	private List<ResultDocument> rows(String... names) throws Exception {
		return GuiTestSupport.find(support.listFolder(support.root), names);
	}

	private File file(String name) {
		return new File(support.root, name);
	}

	@Test
	public void offersMergeAndSplitOnlyForPdfs() throws Exception {
		assertTrue(PdfOperations.canMerge(rows("a.pdf", "b.pdf")));
		assertFalse("One file", PdfOperations.canMerge(rows("a.pdf")));
		assertFalse("Not a PDF", PdfOperations.canMerge(rows("a.pdf", "note.txt")));
		assertTrue(PdfOperations.canSplit(rows("stack.pdf")));
		assertFalse("Several files", PdfOperations.canSplit(rows("a.pdf", "stack.pdf")));
		assertFalse("Not a PDF", PdfOperations.canSplit(rows("note.txt")));
	}

	@Test
	public void mergeRemovesTheOriginalsAndUndoRestoresThem() throws Exception {
		List<ResultDocument> docs = rows("b.pdf", "a.pdf");
		File merged = file("all.pdf");
		assertTrue(PdfOperations.merge(host, docs, Arrays.asList(file("b.pdf"), file("a.pdf")),
			merged, Answer.REMOVE));
		assertEquals(3, PdfPages.getPageCount(merged));
		assertFalse(file("a.pdf").exists());
		assertFalse(file("b.pdf").exists());
		assertEquals("Rows of the removed files", 2, removedRows.size());
		assertTrue(changedFiles.contains(merged));

		assertTrue(ResultPanel.undoMergeOrSplitWithoutAsking(support.registry));
		assertFalse("Merged file removed", merged.exists());
		assertEquals(2, PdfPages.getPageCount(file("a.pdf")));
		assertEquals(1, PdfPages.getPageCount(file("b.pdf")));
	}

	@Test
	public void mergeKeepsTheOriginalsIfWanted() throws Exception {
		File merged = file("all.pdf");
		assertTrue(PdfOperations.merge(host, rows("a.pdf", "b.pdf"),
			Arrays.asList(file("a.pdf"), file("b.pdf")), merged, Answer.KEEP));
		assertTrue(file("a.pdf").exists() && file("b.pdf").exists() && merged.exists());
		assertTrue(removedRows.isEmpty());

		// Undo only removes the merged file
		assertTrue(ResultPanel.undoMergeOrSplitWithoutAsking(support.registry));
		assertFalse(merged.exists());
		assertTrue(file("a.pdf").exists() && file("b.pdf").exists());
	}

	@Test
	public void splitCreatesNumberedPartsAndUndoRestoresTheOriginal() throws Exception {
		List<ResultDocument> docs = rows("stack.pdf");
		List<File> parts = PdfOperations.split(host, docs.get(0),
			PdfPages.atPages(5, Arrays.asList(3, 5)), Answer.REMOVE);
		assertNotNull(parts);
		assertEquals(Arrays.asList(file("stack_1.pdf"), file("stack_2.pdf"), file("stack_3.pdf")), parts);
		assertEquals(2, PdfPages.getPageCount(parts.get(0)));
		assertEquals(2, PdfPages.getPageCount(parts.get(1)));
		assertEquals(1, PdfPages.getPageCount(parts.get(2)));
		assertFalse(file("stack.pdf").exists());

		assertTrue(ResultPanel.undoMergeOrSplitWithoutAsking(support.registry));
		assertEquals(5, PdfPages.getPageCount(file("stack.pdf")));
		for (File part : parts)
			assertFalse(part.exists());
	}

	@Test
	public void theRememberedAnswerIsUsed() throws Exception {
		int saved = SettingsConf.Int.RemoveOriginals.get();
		try {
			SettingsConf.Int.RemoveOriginals.set(SettingsConf.ORIGINALS_KEEP);
			// No answer given: the remembered one applies, no question is asked
			assertNotNull(PdfOperations.split(host, rows("stack.pdf").get(0),
				PdfPages.eachPage(5), null));
			assertTrue(file("stack.pdf").exists());
			assertTrue(file("stack_5.pdf").exists());
		}
		finally {
			SettingsConf.Int.RemoveOriginals.set(saved);
		}
	}

	@Test
	public void dialogsComputeOrderAndParts() throws Exception {
		MergeDialog merge = new MergeDialog(shell, Arrays.asList(file("a.pdf"), file("b.pdf")));
		assertEquals("a_merged", merge.getFileName());
		merge.select(1);
		merge.move(-1);
		assertEquals(Arrays.asList(file("b.pdf"), file("a.pdf")), merge.getOrder());
		assertEquals("The name follows the first file", "b_merged", merge.getFileName());

		List<PageInfo> pages = Arrays.asList(
			new PageInfo("Rechnung RE-4711 Seite 1 von 2", false, false),
			new PageInfo("Rechnung RE-4711 Seite 2 von 2", false, false),
			new PageInfo("", false, true), // Separator sheet
			new PageInfo("Sehr geehrter Herr Beispiel, anbei der Vertrag", false, false),
			new PageInfo("Vertrag Unterschrift Seite 2 von 2", false, false));
		SplitDialog split = new SplitDialog(shell, file("stack.pdf"), pages);
		// With a separator sheet, the dialog starts with the separators
		assertEquals(Arrays.asList(new Range(0, 2), new Range(3, 5)), split.getRanges());
		split.apply(SplitDialog.Choice.TEXT);
		assertEquals(Arrays.asList(Role.START, Role.CONTINUE, Role.SEPARATOR, Role.START, Role.CONTINUE),
			split.getRoles());
		split.apply(SplitDialog.Choice.EACH_PAGE);
		assertEquals("The separator stays out", 4, split.getRanges().size());
		split.apply(SplitDialog.Choice.NONE);
		assertFalse("One document is no split", split.isOkEnabled());
		split.setRole(4, Role.START); // Ticked by the user
		assertEquals(Arrays.asList(new Range(0, 4), new Range(4, 5)), split.getRanges());
		assertTrue(split.isOkEnabled());
		split.setRole(0, Role.CONTINUE); // The first page always starts
		assertEquals(Role.START, split.getRoles().get(0));
		split.close();
	}

}
