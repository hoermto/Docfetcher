/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.render;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;

import com.google.common.io.Closeables;

import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Converts an Excel workbook (xlsx, xlsm, xls) into HTML using Apache POI:
 * One table per sheet, with column letters and row numbers as headers, merged
 * cells, formatted cell values and basic font styling.
 */
final class XlsxHtmlRenderer {

	/*
	 * Limits protecting against huge sheets blowing up the HTML. When a limit
	 * is exceeded, a note is added below the table.
	 */
	private static final int MAX_ROWS_PER_SHEET = 5000;
	private static final int MAX_COLS_PER_SHEET = 256;

	private static final String EXTRA_CSS =
		"table.sheet { font-size: 12px; }\n" +
		"table.sheet td, table.sheet th { white-space: pre; max-width: 400px; " +
			"overflow: hidden; text-overflow: ellipsis; }\n" +
		"table.sheet td.wrap { white-space: pre-wrap; }\n" +
		"table.sheet th.row { text-align: right; }\n";

	private final StringBuilder sb = new StringBuilder(16 * 1024);
	private final DataFormatter formatter = new DataFormatter();
	@Nullable private final FormulaEvaluator evaluator;

	private XlsxHtmlRenderer(@NotNull Workbook workbook) {
		FormulaEvaluator eval = null;
		try {
			eval = workbook.getCreationHelper().createFormulaEvaluator();
		}
		catch (RuntimeException e) {
			// Formulas will be shown as cached values or as-is
		}
		evaluator = eval;
	}

	@NotNull
	static String getExtraCss() {
		return EXTRA_CSS;
	}

	/**
	 * Renders the given workbook file and returns the HTML body content.
	 */
	@NotNull
	public static String render(@NotNull File file) throws IOException {
		Workbook workbook = null;
		try {
			workbook = WorkbookFactory.create(file, null, true);
			XlsxHtmlRenderer renderer = new XlsxHtmlRenderer(workbook);
			int sheetCount = workbook.getNumberOfSheets();
			for (int i = 0; i < sheetCount; i++) {
				Sheet sheet = workbook.getSheetAt(i);
				if (workbook.isSheetHidden(i) || workbook.isSheetVeryHidden(i))
					continue;
				renderer.renderSheet(sheet, sheetCount > 1);
			}
			return renderer.sb.toString();
		}
		catch (InvalidFormatException e) {
			throw new IOException(e.getMessage(), e);
		}
		catch (EncryptedDocumentException e) {
			throw new IOException(e.getMessage(), e);
		}
		finally {
			if (workbook != null)
				Closeables.closeQuietly(workbook);
		}
	}

	private void renderSheet(@NotNull Sheet sheet, boolean showTitle) {
		if (showTitle) {
			sb.append("<h2 class=\"sheet-title\">");
			sb.append(HtmlPage.escape(sheet.getSheetName()));
			sb.append("</h2>\n");
		}

		int lastRow = sheet.getLastRowNum();
		if (lastRow < 0 || sheet.getPhysicalNumberOfRows() == 0) {
			sb.append("<p class=\"note\">(empty)</p>\n");
			return;
		}

		// Determine the number of columns
		int lastCol = -1;
		for (int r = 0; r <= lastRow; r++) {
			Row row = sheet.getRow(r);
			if (row != null)
				lastCol = Math.max(lastCol, row.getLastCellNum() - 1);
		}
		if (lastCol < 0) {
			sb.append("<p class=\"note\">(empty)</p>\n");
			return;
		}

		boolean rowsTruncated = lastRow + 1 > MAX_ROWS_PER_SHEET;
		boolean colsTruncated = lastCol + 1 > MAX_COLS_PER_SHEET;
		if (rowsTruncated)
			lastRow = MAX_ROWS_PER_SHEET - 1;
		if (colsTruncated)
			lastCol = MAX_COLS_PER_SHEET - 1;

		// Merged regions: first cell -> region; other cells are skipped
		Map<Long, CellRangeAddress> mergedStarts = new HashMap<Long, CellRangeAddress>();
		Set<Long> mergedCovered = new HashSet<Long>();
		for (CellRangeAddress region : sheet.getMergedRegions()) {
			mergedStarts.put(key(region.getFirstRow(), region.getFirstColumn()), region);
			for (int r = region.getFirstRow(); r <= region.getLastRow(); r++)
				for (int c = region.getFirstColumn(); c <= region.getLastColumn(); c++)
					if (r != region.getFirstRow() || c != region.getFirstColumn())
						mergedCovered.add(key(r, c));
		}

		sb.append("<table class=\"sheet\">\n<tr><th></th>");
		for (int c = 0; c <= lastCol; c++) {
			if (sheet.isColumnHidden(c))
				continue;
			sb.append("<th>").append(CellReference.convertNumToColString(c)).append("</th>");
		}
		sb.append("</tr>\n");

		for (int r = 0; r <= lastRow; r++) {
			Row row = sheet.getRow(r);
			if (row != null && row.getZeroHeight())
				continue;
			sb.append("<tr><th class=\"row\">").append(r + 1).append("</th>");
			for (int c = 0; c <= lastCol; c++) {
				if (sheet.isColumnHidden(c))
					continue;
				long key = key(r, c);
				if (mergedCovered.contains(key))
					continue;
				Cell cell = row == null ? null : row.getCell(c);
				renderCell(cell, mergedStarts.get(key));
			}
			sb.append("</tr>\n");
		}
		sb.append("</table>\n");

		if (rowsTruncated || colsTruncated) {
			sb.append("<p class=\"note\">(Sheet truncated for preview: at most ");
			sb.append(MAX_ROWS_PER_SHEET).append(" rows and ");
			sb.append(MAX_COLS_PER_SHEET).append(" columns are shown.)</p>\n");
		}
	}

	private void renderCell(@Nullable Cell cell, @Nullable CellRangeAddress merged) {
		sb.append("<td");
		if (merged != null) {
			int rowspan = merged.getLastRow() - merged.getFirstRow() + 1;
			int colspan = merged.getLastColumn() - merged.getFirstColumn() + 1;
			if (rowspan > 1)
				sb.append(" rowspan=\"").append(rowspan).append('"');
			if (colspan > 1)
				sb.append(" colspan=\"").append(colspan).append('"');
		}

		if (cell == null) {
			sb.append("></td>");
			return;
		}

		String text = getCellText(cell);
		StringBuilder classes = new StringBuilder();
		StringBuilder style = new StringBuilder();

		CellType type = getEffectiveType(cell);
		boolean numeric = type == CellType.NUMERIC;

		CellStyle cellStyle = null;
		try {
			cellStyle = cell.getCellStyle();
		}
		catch (RuntimeException e) {
			// Ignore
		}
		if (cellStyle != null) {
			HorizontalAlignment align = null;
			try {
				align = cellStyle.getAlignmentEnum();
			}
			catch (RuntimeException e) {
				// Ignore
			}
			if (align == HorizontalAlignment.CENTER)
				style.append("text-align:center;");
			else if (align == HorizontalAlignment.RIGHT)
				style.append("text-align:right;");
			else if (align == HorizontalAlignment.LEFT)
				style.append("text-align:left;");
			else if (numeric)
				classes.append("num");

			if (cellStyle.getWrapText())
				classes.append(classes.length() > 0 ? " wrap" : "wrap");

			try {
				Font font = cell.getSheet().getWorkbook().getFontAt(cellStyle.getFontIndex());
				if (font != null) {
					if (font.getBold())
						style.append("font-weight:bold;");
					if (font.getItalic())
						style.append("font-style:italic;");
					if (font.getUnderline() != Font.U_NONE)
						style.append("text-decoration:underline;");
				}
			}
			catch (RuntimeException e) {
				// Ignore
			}
		}
		else if (numeric) {
			classes.append("num");
		}

		if (classes.length() > 0)
			sb.append(" class=\"").append(classes).append('"');
		if (style.length() > 0)
			sb.append(" style=\"").append(style).append('"');
		sb.append('>');
		sb.append(HtmlPage.escape(text));
		sb.append("</td>");
	}

	@NotNull
	private CellType getEffectiveType(@NotNull Cell cell) {
		try {
			CellType type = cell.getCellTypeEnum();
			if (type == CellType.FORMULA)
				type = cell.getCachedFormulaResultTypeEnum();
			return type;
		}
		catch (RuntimeException e) {
			return CellType.STRING;
		}
	}

	@NotNull
	private String getCellText(@NotNull Cell cell) {
		try {
			if (evaluator != null)
				return formatter.formatCellValue(cell, evaluator);
			return formatter.formatCellValue(cell);
		}
		catch (RuntimeException e) {
			// Formula evaluation may fail for unsupported functions; fall
			// back to the cached value
			try {
				return formatter.formatCellValue(cell);
			}
			catch (RuntimeException e2) {
				return cell.toString();
			}
		}
	}

	private static long key(int row, int col) {
		return ((long) row << 32) | (col & 0xFFFFFFFFL);
	}

}
