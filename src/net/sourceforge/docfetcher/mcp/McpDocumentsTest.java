/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.mcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.Test;

import net.sourceforge.docfetcher.mcp.McpTools.ToolException;

/**
 * Tests the files DocFetcher creates for add_to_inbox, read back with POI
 * and PDFBox: a workbook with types, formulas, dates, formats and a header,
 * a Word document and a PDF document with headings and bullet points.
 */
public final class McpDocumentsTest {

	@Test
	public void workbook() throws Exception {
		StringBuilder rows = new StringBuilder("[[\"Datum\",\"Text\",\"Betrag\",\"Bezahlt\"]");
		for (int i = 1; i <= 300; i++)
			rows.append(",[\"2026-10-").append(String.format("%02d", 1 + i % 28)).append("\",\"Buchung ").append(i)
				.append("\",").append(i).append(".5,").append(i % 2 == 0).append("]");
		rows.append(",[\"Summe\",null,\"=SUM(C2:C301)\"]]");
		String sheets = "[{\"name\":\"Buchungen\",\"rows\":" + rows + ",\"column_formats\":{\"c\":\"#,##0.00 €\"}},"
			+ "{\"name\":\"Buchungen\",\"header\":false,\"rows\":[[\"x\",1],[\"2026-02-30\"]]},{\"rows\":[]}]";
		XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(McpDocuments.xlsx(Json.parse(sheets))));
		try {
			assertEquals(3, workbook.getNumberOfSheets());
			assertEquals("Buchungen", workbook.getSheetName(0));
			assertEquals("Buchungen (2)", workbook.getSheetName(1)); // Unique names
			assertEquals("Sheet3", workbook.getSheetName(2));

			Sheet sheet = workbook.getSheetAt(0);
			assertTrue("Bold header", workbook.getFontAt(sheet.getRow(0).getCell(0).getCellStyle().getFontIndex()).getBold());
			assertEquals("Header stays visible", 1, sheet.getPaneInformation().getHorizontalSplitPosition());
			Cell date = sheet.getRow(1).getCell(0);
			assertEquals(CellType.NUMERIC, date.getCellTypeEnum());
			assertTrue(DateUtil.isCellDateFormatted(date));
			assertEquals("dd.mm.yyyy", date.getCellStyle().getDataFormatString());
			assertEquals(1.5, sheet.getRow(1).getCell(2).getNumericCellValue(), 0);
			assertEquals("#,##0.00 €", sheet.getRow(1).getCell(2).getCellStyle().getDataFormatString());
			assertEquals(CellType.BOOLEAN, sheet.getRow(2).getCell(3).getCellTypeEnum());
			Cell sum = sheet.getRow(301).getCell(2);
			assertEquals("SUM(C2:C301)", sum.getCellFormula());
			FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
			assertEquals(300 * 301 / 2 + 150, evaluator.evaluate(sum).getNumberValue(), 0.001);
			// The result is stored, for the index and the preview
			assertEquals(300 * 301 / 2 + 150, sum.getNumericCellValue(), 0.001);
			assertEquals("Empty cell", null, sheet.getRow(301).getCell(1));

			Sheet second = workbook.getSheetAt(1);
			assertFalse(workbook.getFontAt(second.getRow(0).getCell(0).getCellStyle().getFontIndex()).getBold());
			assertEquals("No real date: text", "2026-02-30", second.getRow(1).getCell(0).getStringCellValue());
		}
		finally {
			workbook.close();
		}
	}

	@Test
	public void workbookErrors() {
		for (String sheets : new String[] {"[]", "{}", "[1]", "[{\"name\":\"x\"}]", "[{\"rows\":[1]}]",
				"[{\"rows\":[[\"=SUM(\"]]}]", "[{\"rows\":[[1]],\"column_formats\":{\"B2\":\"0\"}}]"}) {
			try {
				McpDocuments.xlsx(Json.parse(sheets));
				fail(sheets);
			}
			catch (ToolException e) {
				assertFalse(sheets, e.getMessage().isEmpty());
			}
		}
	}

	private static final String TEXT = "# Angebot Rosenweg 12\n\nSehr geehrter Herr Meier,\nwir bieten Ihnen an:\n\n"
		+ "## Leistungen\n- Kaltmiete 850,00 €\n* Nebenkosten 210,00 €\n\nMit freundlichen Grüßen\n\n### Anlage\nGrundriss";

	@Test
	public void wordDocument() throws Exception {
		XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(McpDocuments.docx(TEXT)));
		try {
			List<XWPFParagraph> paragraphs = doc.getParagraphs();
			assertEquals("Angebot Rosenweg 12", paragraphs.get(0).getText());
			assertTrue(paragraphs.get(0).getRuns().get(0).isBold());
			assertEquals(16, paragraphs.get(0).getRuns().get(0).getFontSize());
			// Lines of one paragraph stay together, with a line break
			assertEquals("Sehr geehrter Herr Meier,\nwir bieten Ihnen an:", paragraphs.get(1).getText());
			assertEquals("Leistungen", paragraphs.get(2).getText());
			assertEquals("•\tKaltmiete 850,00 €", paragraphs.get(3).getText());
			assertEquals("•\tNebenkosten 210,00 €", paragraphs.get(4).getText());
			assertEquals(360, paragraphs.get(4).getIndentationLeft());
			assertEquals("Mit freundlichen Grüßen", paragraphs.get(5).getText());
			assertEquals("Anlage", paragraphs.get(6).getText());
			assertEquals("Grundriss", paragraphs.get(7).getText());
			assertEquals(8, paragraphs.size());
		}
		finally {
			doc.close();
		}
	}

	@Test
	public void pdfDocument() throws Exception {
		StringBuilder longText = new StringBuilder(TEXT + "\n\n");
		for (int i = 0; i < 200; i++)
			longText.append("Zeile ").append(i).append(" mit einem längeren Text, der umbrochen werden muss, weil er nicht in eine Zeile passt. ");
		longText.append("\nZeichen außerhalb von Helvetica: 中文 → Ende");
		PDDocument doc = PDDocument.load(McpDocuments.pdf(longText.toString()));
		try {
			assertTrue("Page breaks", doc.getNumberOfPages() > 1);
			String text = new PDFTextStripper().getText(doc);
			assertTrue(text, text.contains("Angebot Rosenweg 12"));
			assertTrue(text, text.contains("Kaltmiete 850,00 €"));
			assertTrue(text, text.contains("Mit freundlichen Grüßen"));
			assertTrue(text, text.contains("Zeile 199"));
			assertTrue("Unknown characters become '?'", text.contains("?? ? Ende"));
		}
		finally {
			doc.close();
		}
		// No text: still a valid document with one page
		PDDocument empty = PDDocument.load(McpDocuments.pdf(""));
		assertEquals(1, empty.getNumberOfPages());
		empty.close();
	}

}
