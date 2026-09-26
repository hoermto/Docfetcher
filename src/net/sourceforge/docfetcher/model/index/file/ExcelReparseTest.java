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
 * Tests that an index with an older Excel text format parses its Excel files
 * again on the next update - only those, and only once - without a rebuild.
 */
public final class ExcelReparseTest {

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
