/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.parse;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * The text of an Excel workbook (.xls and .xlsx) for the index and the
 * preview: per sheet its name, then the cells row by row, separated by tabs.
 * A formula cell gives its result - the value Excel stored when the file was
 * last saved, formatted like in Excel - followed by the formula, e.g.
 * '2.091,52 [=SUM(J7:J18)]', so that both the numbers and the formulas can
 * be found. Cell comments are included.
 */
public final class ExcelText {

	/**
	 * Version of this text format. Indexes with an older version parse their
	 * Excel files once again on the next update (see TreeIndex); version 1
	 * added the results of formula cells.
	 */
	public static final int VERSION = 1;

	/** The extensions of the Excel files handled here. */
	public static final Collection<String> EXTENSIONS = Arrays.asList(
		"xls", "xlsx", "xlsm", "xltx", "xltm");

	private ExcelText() {}

	/** Returns true if the file is an Excel file by its name. */
	public static boolean isExcelFile(@NotNull String filename) {
		return EXTENSIONS.contains(Util.getExtension(filename).toLowerCase(Locale.ROOT));
	}

	/**
	 * Returns the text of the workbook; with {@code withFormulas}, formula
	 * cells give their result and their formula, otherwise only the result.
	 */
	@NotNull
	public static String extract(@NotNull Workbook workbook, boolean withFormulas) {
		DataFormatter formatter = new DataFormatter();
		StringBuilder sb = new StringBuilder();
		for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
			Sheet sheet = workbook.getSheetAt(s);
			sb.append(sheet.getSheetName()).append('\n');
			for (Row row : sheet) {
				boolean first = true;
				for (Cell cell : row) {
					String text = cellText(cell, formatter, withFormulas);
					Comment comment = cell.getCellComment();
					if (comment != null && comment.getString() != null)
						text += " [" + comment.getString().getString().trim() + "]";
					if (text.isEmpty())
						continue;
					if (!first)
						sb.append('\t');
					sb.append(text);
					first = false;
				}
				if (!first)
					sb.append('\n');
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	@NotNull
	private static String cellText(@NotNull Cell cell, @NotNull DataFormatter formatter, boolean withFormulas) {
		try {
			if (cell.getCellTypeEnum() != CellType.FORMULA)
				return formatter.formatCellValue(cell);
			String result = cachedResult(cell, formatter);
			if (!withFormulas)
				return result;
			String formula = "[=" + cell.getCellFormula() + "]";
			return result.isEmpty() ? formula : result + " " + formula;
		}
		catch (RuntimeException e) {
			return ""; // An odd cell must not spoil the whole workbook
		}
	}

	/** The result Excel stored for the formula cell, formatted like the cell. */
	@NotNull
	private static String cachedResult(@NotNull Cell cell, @NotNull DataFormatter formatter) {
		switch (cell.getCachedFormulaResultTypeEnum()) {
		case NUMERIC:
			return formatter.formatRawCellContents(cell.getNumericCellValue(),
				cell.getCellStyle().getDataFormat(), cell.getCellStyle().getDataFormatString());
		case STRING:
			return cell.getRichStringCellValue().getString();
		case BOOLEAN:
			return cell.getBooleanCellValue() ? "TRUE" : "FALSE";
		case ERROR:
			return FormulaError.forInt(cell.getErrorCellValue()).getString();
		default:
			return "";
		}
	}

}
