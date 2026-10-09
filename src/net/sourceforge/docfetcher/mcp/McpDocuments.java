/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.mcp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.ss.formula.FormulaParseException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import net.sourceforge.docfetcher.mcp.McpTools.ToolException;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Creates Office and PDF files from content an AI assistant sends for
 * add_to_inbox: an assistant can't type a binary file, and base64 of even a
 * small workbook is tens of thousands of characters it has to repeat without
 * a single error. So it sends the rows of a table or a text, and DocFetcher
 * writes the file.
 * <p>
 * The text of Word and PDF files is plain text with a few markers: lines
 * starting with '# ', '## ' or '### ' are headings, lines starting with '- '
 * or '* ' bullet points, and empty lines separate paragraphs.
 */
final class McpDocuments {

	private static final int MAX_SHEETS = 50;
	private static final int MAX_COLUMN_WIDTH = 60 * 256; // Characters * 256
	private static final String DATE_FORMAT = "dd.mm.yyyy";

	private McpDocuments() {
	}

	// ---- Excel

	/**
	 * A workbook from sheets like {"name": "Buchungen", "rows": [["Datum",
	 * "Betrag"], ["2026-10-01", 850.5], ["Summe", "=SUM(B2:B2)"]],
	 * "column_formats": {"B": "#,##0.00 €"}, "header": true}. Numbers and
	 * booleans keep their type, '=...' becomes a formula, 'yyyy-mm-dd' a date.
	 */
	@NotNull
	static byte[] xlsx(@NotNull Object sheetsArg) throws ToolException {
		if (!(sheetsArg instanceof List) || ((List<?>) sheetsArg).isEmpty())
			throw new ToolException("'sheets' must be a list of sheets, each with 'rows' (a list of rows, each a list of values).");
		List<?> sheets = (List<?>) sheetsArg;
		if (sheets.size() > MAX_SHEETS)
			throw new ToolException("At most " + MAX_SHEETS + " sheets.");
		XSSFWorkbook workbook = new XSSFWorkbook();
		try {
			Font bold = workbook.createFont();
			bold.setBold(true);
			CellStyle headerStyle = workbook.createCellStyle();
			headerStyle.setFont(bold);
			Set<String> sheetNames = new HashSet<String>();
			for (int i = 0; i < sheets.size(); i++) {
				if (!(sheets.get(i) instanceof Map))
					throw new ToolException("Sheet " + (i + 1) + " must be an object with 'rows'.");
				Map<?, ?> sheetArg = (Map<?, ?>) sheets.get(i);
				String name = uniqueSheetName(sheetArg.get("name"), i, sheetNames);
				writeSheet(workbook, workbook.createSheet(name), name, sheetArg, headerStyle);
			}
			/*
			 * Store the results of the formulas, so that the index and the
			 * preview show them; Excel calculates them again on opening, also
			 * those POI can't calculate.
			 */
			try {
				XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);
			}
			catch (RuntimeException e) {
				// E.g. a function POI doesn't know: no stored results
			}
			workbook.setForceFormulaRecalculation(true);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			workbook.write(out);
			return out.toByteArray();
		}
		catch (IOException e) {
			throw new ToolException("The workbook could not be created: " + e.getMessage());
		}
		finally {
			try {
				workbook.close();
			}
			catch (IOException e) {
				// Nothing was opened
			}
		}
	}

	@NotNull
	private static String uniqueSheetName(@Nullable Object wanted, int index, @NotNull Set<String> taken) {
		String base = WorkbookUtil.createSafeSheetName(
			wanted == null || wanted.toString().trim().isEmpty() ? "Sheet" + (index + 1) : wanted.toString().trim());
		String name = base;
		for (int n = 2; taken.contains(name.toLowerCase()); n++) {
			String suffix = " (" + n + ")";
			name = base.substring(0, Math.min(base.length(), 31 - suffix.length())) + suffix;
		}
		taken.add(name.toLowerCase());
		return name;
	}

	private static void writeSheet(@NotNull XSSFWorkbook workbook, @NotNull Sheet sheet, @NotNull String name,
			@NotNull Map<?, ?> sheetArg, @NotNull CellStyle headerStyle) throws ToolException {
		if (!(sheetArg.get("rows") instanceof List))
			throw new ToolException("Sheet '" + name + "' has no 'rows' (a list of rows, each a list of values).");
		List<?> rows = (List<?>) sheetArg.get("rows");
		boolean header = !Boolean.FALSE.equals(sheetArg.get("header")) && !rows.isEmpty();

		// Number formats by column letter; dates get a date format unless set
		Map<Integer, CellStyle> columnStyles = new HashMap<Integer, CellStyle>();
		if (sheetArg.get("column_formats") instanceof Map) {
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) sheetArg.get("column_formats")).entrySet()) {
				String column = entry.getKey().toString().trim().toUpperCase();
				if (!column.matches("[A-Z]{1,3}"))
					throw new ToolException("'column_formats' of sheet '" + name + "': '" + column
						+ "' is not a column letter like 'B'.");
				CellStyle style = workbook.createCellStyle();
				style.setDataFormat(workbook.createDataFormat().getFormat(entry.getValue().toString()));
				columnStyles.put(CellReference.convertColStringToIndex(column), style);
			}
		}
		CellStyle dateStyle = workbook.createCellStyle();
		dateStyle.setDataFormat(workbook.createDataFormat().getFormat(DATE_FORMAT));

		int columns = 0;
		for (int r = 0; r < rows.size(); r++) {
			if (!(rows.get(r) instanceof List))
				throw new ToolException("Row " + (r + 1) + " of sheet '" + name + "' must be a list of values.");
			List<?> values = (List<?>) rows.get(r);
			columns = Math.max(columns, values.size());
			Row row = sheet.createRow(r);
			for (int c = 0; c < values.size(); c++) {
				Object value = values.get(c);
				if (value == null || "".equals(value))
					continue;
				Cell cell = row.createCell(c);
				boolean isDate = setValue(cell, value, name);
				if (r == 0 && header)
					cell.setCellStyle(headerStyle);
				else if (columnStyles.containsKey(c))
					cell.setCellStyle(columnStyles.get(c));
				else if (isDate)
					cell.setCellStyle(dateStyle);
			}
		}
		if (header)
			sheet.createFreezePane(0, 1);
		for (int c = 0; c < columns; c++) {
			sheet.autoSizeColumn(c);
			sheet.setColumnWidth(c, Math.min(MAX_COLUMN_WIDTH, sheet.getColumnWidth(c) + 512));
		}
	}

	/** Sets the value with its type; returns whether it is a date. */
	private static boolean setValue(@NotNull Cell cell, @NotNull Object value, @NotNull String sheetName)
			throws ToolException {
		if (value instanceof Number) {
			cell.setCellValue(((Number) value).doubleValue());
			return false;
		}
		if (value instanceof Boolean) {
			cell.setCellValue((Boolean) value);
			return false;
		}
		String text = value.toString();
		if (text.length() > 1 && text.startsWith("=")) {
			try {
				cell.setCellFormula(text.substring(1));
				return false;
			}
			catch (FormulaParseException e) {
				throw new ToolException("Invalid formula in sheet '" + sheetName + "', cell "
					+ new CellReference(cell).formatAsString() + ": " + text + " (" + e.getMessage() + ")");
			}
		}
		if (text.matches("\\d{4}-\\d{2}-\\d{2}")) {
			try {
				SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
				format.setLenient(false);
				cell.setCellValue(format.parse(text));
				return true;
			}
			catch (ParseException e) {
				// Not a real date: keep the text
			}
		}
		cell.setCellValue(text);
		return false;
	}

	// ---- Text with headings and bullet points

	/** One line of the text: a heading (level 1-3), a bullet point, text or an empty line (null). */
	private static final class Line {
		final int heading;
		final boolean bullet;
		@Nullable final String text;

		Line(int heading, boolean bullet, @Nullable String text) {
			this.heading = heading;
			this.bullet = bullet;
			this.text = text;
		}
	}

	@NotNull
	private static List<Line> lines(@NotNull String text) {
		List<Line> lines = new ArrayList<Line>();
		for (String line : text.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ").split("\n", -1)) {
			String trimmed = line.trim();
			if (trimmed.isEmpty())
				lines.add(new Line(0, false, null));
			else if (trimmed.startsWith("### "))
				lines.add(new Line(3, false, trimmed.substring(4).trim()));
			else if (trimmed.startsWith("## "))
				lines.add(new Line(2, false, trimmed.substring(3).trim()));
			else if (trimmed.startsWith("# "))
				lines.add(new Line(1, false, trimmed.substring(2).trim()));
			else if (trimmed.startsWith("- ") || trimmed.startsWith("* "))
				lines.add(new Line(0, true, trimmed.substring(2).trim()));
			else
				lines.add(new Line(0, false, line.replaceAll("\\s+$", "")));
		}
		return lines;
	}

	private static final int[] HEADING_SIZES = {11, 16, 14, 12};

	// ---- Word

	/** A Word document from the text: headings, bullet points, paragraphs. */
	@NotNull
	static byte[] docx(@NotNull String text) throws ToolException {
		XWPFDocument doc = new XWPFDocument();
		try {
			XWPFParagraph paragraph = null; // The paragraph that following text lines continue
			for (Line line : lines(text)) {
				if (line.text == null) {
					paragraph = null;
					continue;
				}
				if (line.heading > 0 || line.bullet || paragraph == null) {
					XWPFParagraph p = doc.createParagraph();
					p.setSpacingAfter(line.heading > 0 ? 120 : 80);
					if (line.heading > 0)
						p.setSpacingBefore(240);
					if (line.bullet) {
						p.setIndentationLeft(360);
						p.setIndentationHanging(240);
					}
					XWPFRun run = p.createRun();
					run.setFontFamily("Calibri");
					run.setFontSize(HEADING_SIZES[line.heading]);
					run.setBold(line.heading > 0);
					run.setText(line.bullet ? "•\t" + line.text : line.text);
					// Text lines after a heading or bullet point start a new paragraph
					paragraph = line.heading > 0 || line.bullet ? null : p;
				}
				else {
					XWPFRun run = paragraph.getRuns().get(paragraph.getRuns().size() - 1);
					run.addBreak();
					run.setText(line.text);
				}
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			doc.write(out);
			return out.toByteArray();
		}
		catch (IOException e) {
			throw new ToolException("The Word document could not be created: " + e.getMessage());
		}
		finally {
			try {
				doc.close();
			}
			catch (IOException e) {
				// Nothing was opened
			}
		}
	}

	// ---- PDF

	private static final float MARGIN = 56; // About 2 cm
	private static final float FONT_SIZE = 11;

	/**
	 * A PDF document (A4, Helvetica) from the text: headings, bullet points,
	 * paragraphs, lines wrapped to the page width. Characters Helvetica
	 * doesn't have become '?'.
	 */
	@NotNull
	static byte[] pdf(@NotNull String text) throws ToolException {
		PDDocument doc = new PDDocument();
		try {
			PdfWriter writer = new PdfWriter(doc);
			boolean afterText = false;
			for (Line line : lines(text)) {
				if (line.text == null) {
					writer.space(FONT_SIZE * 0.6f);
					afterText = false;
					continue;
				}
				if (line.heading > 0) {
					writer.space(afterText ? FONT_SIZE : FONT_SIZE * 0.5f);
					writer.paragraph(line.text, PDType1Font.HELVETICA_BOLD, HEADING_SIZES[line.heading], 0, null);
					writer.space(FONT_SIZE * 0.3f);
				}
				else if (line.bullet) {
					writer.paragraph(line.text, PDType1Font.HELVETICA, FONT_SIZE, 14, "•");
				}
				else {
					writer.paragraph(line.text, PDType1Font.HELVETICA, FONT_SIZE, 0, null);
				}
				afterText = line.heading == 0;
			}
			writer.close();
			if (doc.getNumberOfPages() == 0)
				doc.addPage(new PDPage(PDRectangle.A4)); // No text: an empty page
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			doc.save(out);
			return out.toByteArray();
		}
		catch (IOException e) {
			throw new ToolException("The PDF document could not be created: " + e.getMessage());
		}
		finally {
			try {
				doc.close();
			}
			catch (IOException e) {
				// Nothing to free
			}
		}
	}

	/** Writes wrapped lines top-down, starting a new page when one is full. */
	private static final class PdfWriter {
		private final PDDocument doc;
		@Nullable private PDPageContentStream stream;
		private float y;

		PdfWriter(@NotNull PDDocument doc) {
			this.doc = doc;
		}

		void space(float height) {
			y -= height;
		}

		/** A paragraph, indented and with a bullet if given. */
		void paragraph(@NotNull String text, @NotNull PDFont font, float size, float indent, @Nullable String bullet)
				throws IOException {
			float width = PDRectangle.A4.getWidth() - 2 * MARGIN - indent;
			boolean first = true;
			for (String line : wrap(printable(text, font), font, size, width)) {
				float leading = size * 1.35f;
				if (stream == null || y - leading < MARGIN)
					newPage();
				y -= leading;
				if (first && bullet != null)
					show(bullet, font, size, MARGIN + indent - 10);
				show(line, font, size, MARGIN + indent);
				first = false;
			}
		}

		private void show(@NotNull String text, @NotNull PDFont font, float size, float x) throws IOException {
			stream.beginText();
			stream.setFont(font, size);
			stream.newLineAtOffset(x, y);
			stream.showText(text);
			stream.endText();
		}

		private void newPage() throws IOException {
			close();
			PDPage page = new PDPage(PDRectangle.A4);
			doc.addPage(page);
			stream = new PDPageContentStream(doc, page);
			y = PDRectangle.A4.getHeight() - MARGIN;
		}

		void close() throws IOException {
			if (stream != null)
				stream.close();
			stream = null;
		}
	}

	/** The text with the characters the font can't show replaced by '?'. */
	@NotNull
	static String printable(@NotNull String text, @NotNull PDFont font) {
		StringBuilder sb = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			try {
				font.encode(String.valueOf(c));
				sb.append(c);
			}
			catch (Exception e) {
				sb.append('?');
			}
		}
		return sb.toString();
	}

	/** Splits the text into lines no wider than the width, at spaces where possible. */
	@NotNull
	private static List<String> wrap(@NotNull String text, @NotNull PDFont font, float size, float width)
			throws IOException {
		List<String> lines = new ArrayList<String>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ", -1)) {
			String candidate = line.length() == 0 ? word : line + " " + word;
			if (width(candidate, font, size) <= width) {
				line.setLength(0);
				line.append(candidate);
				continue;
			}
			if (line.length() > 0) {
				lines.add(line.toString());
				line.setLength(0);
			}
			// A word wider than the line: split it
			while (width(word, font, size) > width) {
				int end = word.length() - 1;
				while (end > 1 && width(word.substring(0, end), font, size) > width)
					end--;
				lines.add(word.substring(0, end));
				word = word.substring(end);
			}
			line.append(word);
		}
		lines.add(line.toString());
		return lines;
	}

	private static float width(@NotNull String text, @NotNull PDFont font, float size) throws IOException {
		return font.getStringWidth(text) / 1000 * size;
	}

}
