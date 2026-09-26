/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.parse;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

import net.sourceforge.docfetcher.enums.ProgramConf;
import net.sourceforge.docfetcher.model.index.IndexingConfig;
import net.sourceforge.docfetcher.util.AppUtil;

/**
 * Tests that Excel files give the results of formula cells and the formulas:
 * the search and an AI assistant see the totals, not only 'SUM(B2:B3)'.
 */
public final class ExcelTextTest {

	/**
	 * A small statement of operating costs: two costs, their sum as a
	 * formula, a text formula and a formula with an error. The results are
	 * calculated and stored like Excel does when saving.
	 */
	static void writeWorkbook(Workbook workbook, File file) throws Exception {
		Sheet sheet = workbook.createSheet("Abrechnung");
		Row header = sheet.createRow(0);
		header.createCell(0).setCellValue("Kosten");
		header.createCell(1).setCellValue("Betrag");
		Row water = sheet.createRow(1);
		water.createCell(0).setCellValue("Wasser");
		water.createCell(1).setCellValue(211.64);
		Row waste = sheet.createRow(2);
		waste.createCell(0).setCellValue("Müllgebühr");
		waste.createCell(1).setCellValue(115.32);
		Row sum = sheet.createRow(3);
		sum.createCell(0).setCellValue("Summe");
		sum.createCell(1).setCellFormula("SUM(B2:B3)");
		Row text = sheet.createRow(4);
		text.createCell(0).setCellFormula("CONCATENATE(\"Guthaben \",\"Kruse\")");
		Row error = sheet.createRow(5);
		error.createCell(0).setCellFormula("1/0");
		workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
		FileOutputStream out = new FileOutputStream(file);
		try {
			workbook.write(out);
		}
		finally {
			out.close();
			workbook.close();
		}
	}

	private static String render(FileParser parser, File file) throws Exception {
		return parser.renderText(file, file.getName());
	}

	@Test
	public void xlsxGivesResultsAndFormulas() throws Exception {
		AppUtil.Const.autoInit();
		File file = File.createTempFile("docfetcher-excel", ".xlsx");
		try {
			writeWorkbook(new XSSFWorkbook(), file);
			String text = render(new MSOffice2007Parser.MSExcel2007Parser(), file);
			assertContents(text);
		}
		finally {
			file.delete();
		}
	}

	@Test
	public void xlsGivesResultsAndFormulas() throws Exception {
		AppUtil.Const.autoInit();
		File file = File.createTempFile("docfetcher-excel", ".xls");
		try {
			writeWorkbook(new HSSFWorkbook(), file);
			String text = render(new MSExcelParser(), file);
			assertContents(text);
		}
		finally {
			file.delete();
		}
	}

	private static void assertContents(String text) {
		assertTrue(text, text.startsWith("Abrechnung\n"));
		assertTrue(text, text.contains("Wasser\t211"));
		assertTrue("Result and formula: " + text, text.matches("(?s).*Summe\t326[.,]96 \\[=SUM\\(B2:B3\\)\\].*"));
		assertTrue(text, text.contains("Guthaben Kruse [=CONCATENATE("));
		assertTrue(text, text.contains("#DIV/0! [=1/0]"));
	}

	@Test
	public void onlyResultsIfFormulasAreSwitchedOff() throws Exception {
		AppUtil.Const.autoInit();
		File file = File.createTempFile("docfetcher-excel", ".xlsx");
		boolean saved = ProgramConf.Bool.IndexExcelFormulas.get();
		try {
			writeWorkbook(new XSSFWorkbook(), file);
			ProgramConf.Bool.IndexExcelFormulas.load("false");
			String text = render(new MSOffice2007Parser.MSExcel2007Parser(), file);
			assertTrue(text, text.matches("(?s).*Summe\t326[.,]96\n.*"));
			assertFalse(text, text.contains("SUM("));
		}
		finally {
			ProgramConf.Bool.IndexExcelFormulas.load(String.valueOf(saved));
			file.delete();
		}
	}

	@Test
	public void excelFilesByName() {
		assertTrue(ExcelText.isExcelFile("Abrechnung.XLSX"));
		assertTrue(ExcelText.isExcelFile("alt.xls"));
		assertFalse(ExcelText.isExcelFile("brief.docx"));
		new IndexingConfig(); // The parsers need an indexing configuration class loaded
	}

}
