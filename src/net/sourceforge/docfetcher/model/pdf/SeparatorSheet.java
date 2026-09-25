/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.pdf;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.util.Matrix;

import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * A page that is put between the documents of a stack before scanning, so
 * that the scan can be split there. Scanners that skip blank pages keep it,
 * since it is printed. It is recognized by the text on it, which the text
 * recognition (OCR) reads: a marker that is printed twice, once upside down,
 * so that it is found however the sheet was put into the scanner.
 */
public final class SeparatorSheet {

	/** The text the sheet is recognized by; the same in all languages. */
	public static final String MARKER = "DOCFETCHER SEPARATOR";

	private static final String NORMALIZED_MARKER = normalize(MARKER);

	/** OCR errors tolerated in the marker (wrong, missing or extra letters). */
	private static final int MAX_ERRORS = 3;

	private SeparatorSheet() {}

	/**
	 * Writes a PDF with one separator sheet. {@code title} and
	 * {@code instruction} are shown in the user's language.
	 */
	public static void create(	@NotNull File output,
								@NotNull String title,
								@NotNull String instruction) throws IOException {
		PDDocument pdf = new PDDocument();
		try {
			PDRectangle a4 = PDRectangle.A4;
			PDPage page = new PDPage(a4);
			pdf.addPage(page);
			PDFont bold = PDType1Font.HELVETICA_BOLD;
			PDFont plain = PDType1Font.HELVETICA;
			float width = a4.getWidth();
			float height = a4.getHeight();
			PDPageContentStream content = new PDPageContentStream(pdf, page);
			try {
				// A thick frame, so that no scanner takes the page for blank
				content.setLineWidth(6);
				content.addRect(36, 36, width - 72, height - 72);
				content.stroke();

				float markerSize = 30; // Fits into the frame
				float markerWidth = bold.getStringWidth(MARKER) / 1000 * markerSize;
				// Upright at the top ...
				showText(content, bold, markerSize, (width - markerWidth) / 2, height - 130, MARKER);
				// ... and upside down at the bottom
				content.saveGraphicsState();
				content.transform(Matrix.getRotateInstance(Math.PI, width, height));
				showText(content, bold, markerSize, (width - markerWidth) / 2, height - 130, MARKER);
				content.restoreGraphicsState();

				float titleSize = 56;
				float titleWidth = bold.getStringWidth(title) / 1000 * titleSize;
				showText(content, bold, titleSize, (width - titleWidth) / 2, height / 2 + 40, title);
				float y = height / 2 - 20;
				for (String line : wrap(instruction, plain, 16, width - 160)) {
					float lineWidth = plain.getStringWidth(line) / 1000 * 16;
					showText(content, plain, 16, (width - lineWidth) / 2, y, line);
					y -= 24;
				}
			}
			finally {
				content.close();
			}
			pdf.save(output);
		}
		finally {
			pdf.close();
		}
	}

	private static void showText(	@NotNull PDPageContentStream content,
									@NotNull PDFont font,
									float size,
									float x,
									float y,
									@NotNull String text) throws IOException {
		content.beginText();
		content.setFont(font, size);
		content.newLineAtOffset(x, y);
		content.showText(text);
		content.endText();
	}

	@NotNull
	private static List<String> wrap(	@NotNull String text,
										@NotNull PDFont font,
										float size,
										float maxWidth) throws IOException {
		List<String> lines = new ArrayList<String>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split("\\s+")) {
			String candidate = line.length() == 0 ? word : line + " " + word;
			if (line.length() > 0 && font.getStringWidth(candidate) / 1000 * size > maxWidth) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			}
			else {
				line = new StringBuilder(candidate);
			}
		}
		if (line.length() > 0)
			lines.add(line.toString());
		return lines;
	}

	/**
	 * Returns true if the text of a page, e.g. from the text recognition,
	 * contains the marker, allowing for a few recognition errors.
	 */
	public static boolean isSeparator(@NotNull String pageText) {
		String text = normalize(pageText);
		int n = NORMALIZED_MARKER.length();
		if (text.contains(NORMALIZED_MARKER))
			return true;
		for (int start = 0; start + n - MAX_ERRORS <= text.length(); start++) {
			String window = text.substring(start, Math.min(text.length(), start + n + MAX_ERRORS));
			if (bestMatchDistance(window) <= MAX_ERRORS)
				return true;
		}
		return false;
	}

	/** Upper case letters only, since OCR often gets spaces and punctuation wrong. */
	@NotNull
	private static String normalize(@NotNull String text) {
		StringBuilder sb = new StringBuilder(text.length());
		for (char c : text.toUpperCase().toCharArray())
			if (c >= 'A' && c <= 'Z')
				sb.append(c);
		return sb.toString();
	}

	/**
	 * The smallest edit distance between the marker and a prefix of the
	 * window, so that trailing letters of the window don't count.
	 */
	private static int bestMatchDistance(@NotNull String window) {
		String marker = NORMALIZED_MARKER;
		int[] previous = new int[window.length() + 1];
		int[] current = new int[window.length() + 1];
		for (int j = 0; j <= window.length(); j++)
			previous[j] = j;
		for (int i = 1; i <= marker.length(); i++) {
			current[0] = i;
			for (int j = 1; j <= window.length(); j++) {
				int cost = marker.charAt(i - 1) == window.charAt(j - 1) ? 0 : 1;
				current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
					previous[j - 1] + cost);
			}
			int[] swap = previous;
			previous = current;
			current = swap;
		}
		int best = Integer.MAX_VALUE;
		for (int j = 0; j <= window.length(); j++)
			best = Math.min(best, previous[j]);
		return best;
	}

}
