/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.index.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

import net.sourceforge.docfetcher.model.LuceneIndex;
import net.sourceforge.docfetcher.model.TreeIndex;
import net.sourceforge.docfetcher.model.index.IndexingReporter;
import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.model.search.SearchTestSupport;

/**
 * Tests that an index with an older text format for Excel files or PDF forms
 * parses those files again on the next update - only those, and only once -
 * without a rebuild.
 */
public final class ReparseOnceTest {

	/** Writes a workbook with one cell; keeps the file's date if it exists. */
	private static void writeXlsx(File file, String word) throws Exception {
		long lastModified = file.exists() ? file.lastModified() : 0;
		XSSFWorkbook workbook = new XSSFWorkbook();
		workbook.createSheet("Tabelle1").createRow(0).createCell(0).setCellValue(word);
		FileOutputStream out = new FileOutputStream(file);
		try {
			workbook.write(out);
		}
		finally {
			out.close();
			workbook.close();
		}
		if (lastModified > 0)
			assertTrue(file.setLastModified(lastModified));
	}

	/** Changes the text file's content, keeping its date: an update doesn't notice. */
	private static void rewriteTxt(File file, String text) throws Exception {
		long lastModified = file.lastModified();
		Files.write(file.toPath(), text.getBytes("UTF-8"));
		assertTrue(file.setLastModified(lastModified));
	}

	private static void markExcelTextOutdated(LuceneIndex index) throws Exception {
		Field field = TreeIndex.class.getDeclaredField("excelTextVersion");
		field.setAccessible(true);
		field.setInt(index, 0);
	}

	private static void update(SearchTestSupport support) throws Exception {
		FileIndex index = (FileIndex) support.registry.getIndexes().get(0);
		index.update(new IndexingReporter(), Cancelable.nullCancelable);
		support.registry.getSearcher().replaceLuceneSearcher();
	}

	/** A PDF without form fields; keeps the file's date if it exists. */
	private static void writePlainPdf(File file, String word) throws Exception {
		long lastModified = file.exists() ? file.lastModified() : 0;
		org.apache.pdfbox.pdmodel.PDDocument pdf = new org.apache.pdfbox.pdmodel.PDDocument();
		try {
			org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
			pdf.addPage(page);
			org.apache.pdfbox.pdmodel.PDPageContentStream content =
				new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf, page);
			content.beginText();
			content.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12);
			content.newLineAtOffset(72, 700);
			content.showText(word);
			content.endText();
			content.close();
			pdf.save(file);
		}
		finally {
			pdf.close();
		}
		if (lastModified > 0)
			assertTrue(file.setLastModified(lastModified));
	}

	private static void writeForm(File file, String rent) throws Exception {
		long lastModified = file.exists() ? file.lastModified() : 0;
		net.sourceforge.docfetcher.model.parse.PdfFormsTest.writeForm(file, rent);
		if (lastModified > 0)
			assertTrue(file.setLastModified(lastModified));
	}

	@Test
	public void outdatedIndexesParseOnlyTheirPdfFormsOnce() throws Exception {
		SearchTestSupport support = new SearchTestSupport("note.txt", "alpha");
		try {
			File contract = new File(support.root, "contract.pdf");
			File letter = new File(support.root, "letter.pdf");
			writeForm(contract, "850,00 EUR");
			writePlainPdf(letter, "firstletter");
			update(support);
			assertEquals("[contract.pdf]", support.search("850,00").toString());

			// New contents, same dates: a normal update sees no change
			writeForm(contract, "912,34 EUR");
			writePlainPdf(letter, "secondletter");
			update(support);
			assertEquals("[]", support.search("912,34").toString());

			// Older form text: only PDFs with filled forms are parsed again
			TreeIndex<?, ?> index = (TreeIndex<?, ?>) support.registry.getIndexes().get(0);
			Field field = TreeIndex.class.getDeclaredField("pdfFormsVersion");
			field.setAccessible(true);
			field.setInt(index, 0);
			update(support);
			assertEquals("[contract.pdf]", support.search("912,34").toString());
			assertEquals("PDFs without forms are not read again", "[]", support.search("secondletter").toString());
			assertFalse(index.isPdfFormsOutdated());

			// Only once
			writeForm(contract, "777,77 EUR");
			update(support);
			assertEquals("[]", support.search("777,77").toString());
		}
		finally {
			support.dispose();
		}
	}

	@Test
	public void outdatedIndexesAddCsvFilesOnce() throws Exception {
		SearchTestSupport support = new SearchTestSupport("note.txt", "alpha");
		try {
			FileIndex index = (FileIndex) support.registry.getIndexes().get(0);
			assertTrue("New indexes have CSV files as text", index.getConfig().getTextExtensions().contains("csv"));

			// An index of an older version: no CSV among its text files
			index.getConfig().setTextExtensions(java.util.Arrays.asList("txt", "java", "cpp", "py"));
			File csv = new File(support.root, "kunden.csv");
			Files.write(csv.toPath(), "Name;Adresse;Miete\nMeier;Rosenweg 12;850\n".getBytes("UTF-8"));
			update(support);
			assertEquals("[]", support.search("Rosenweg").toString());

			// Its next update adds CSV and parses the CSV file, although unchanged
			Field field = TreeIndex.class.getDeclaredField("csvVersion");
			field.setAccessible(true);
			field.setInt(index, 0);
			assertTrue(index.isCsvOutdated());
			update(support);
			assertEquals("[kunden.csv]", support.search("Rosenweg").toString());
			assertTrue(index.getConfig().getTextExtensions().contains("csv"));
			assertFalse(index.isCsvOutdated());

			// Only once: removed by the user, it stays removed
			index.getConfig().setTextExtensions(java.util.Arrays.asList("txt"));
			update(support);
			assertFalse(index.getConfig().getTextExtensions().contains("csv"));
		}
		finally {
			support.dispose();
		}
	}

	@Test
	public void outdatedIndexesParseOnlyTheirExcelFilesOnce() throws Exception {
		SearchTestSupport support = new SearchTestSupport("note.txt", "alpha");
		try {
			File xlsx = new File(support.root, "table.xlsx");
			File txt = new File(support.root, "note.txt");
			writeXlsx(xlsx, "firstword");
			update(support);
			assertEquals("[table.xlsx]", support.search("firstword").toString());

			// New contents, same dates: a normal update sees no change
			writeXlsx(xlsx, "secondword");
			rewriteTxt(txt, "beta");
			update(support);
			assertEquals("[]", support.search("secondword").toString());

			// Older Excel text format: the Excel file is parsed again, the text file isn't
			LuceneIndex index = support.registry.getIndexes().get(0);
			markExcelTextOutdated(index);
			assertTrue(((TreeIndex<?, ?>) index).isExcelTextOutdated());
			update(support);
			assertEquals("[table.xlsx]", support.search("secondword").toString());
			assertEquals("Other files are not read again", "[]", support.search("beta").toString());
			assertFalse(((TreeIndex<?, ?>) index).isExcelTextOutdated());

			// Only once
			writeXlsx(xlsx, "thirdword");
			update(support);
			assertEquals("[]", support.search("thirdword").toString());
		}
		finally {
			support.dispose();
		}
	}

}
