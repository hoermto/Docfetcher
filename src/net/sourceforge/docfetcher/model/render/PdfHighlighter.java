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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Marks the search terms in a PDF file: the matches are written into a copy
 * of the file as highlight annotations, which every PDF viewer displays. The
 * copy is shown in the preview pane, the original file is left untouched.
 */
public final class PdfHighlighter {

	/** Documents with more pages than this are not searched through. */
	private static final int MAX_PAGES = 500;

	/** Upper limit for the marks, so that a huge document stays manageable. */
	private static final int MAX_MARKS = 2000;

	/** The result of marking a document. */
	public static final class Result {
		private final File file;
		private final int firstMatchPage;

		Result(@NotNull File file, int firstMatchPage) {
			this.file = file;
			this.firstMatchPage = firstMatchPage;
		}

		/** The marked copy of the document. */
		@NotNull
		public File getFile() {
			return file;
		}

		/** The page with the first match, counted from 1; 0 if there is none. */
		public int getFirstMatchPage() {
			return firstMatchPage;
		}
	}

	private PdfHighlighter() {}

	/**
	 * Writes a copy of the given PDF file with the given terms marked to the
	 * given target file. Returns null if the document has no match, is
	 * encrypted or can't be processed; in that case the original file should
	 * be displayed.
	 */
	@Nullable
	public static Result highlight(	@NotNull File pdfFile,
									@NotNull File targetFile,
									@NotNull Collection<String> terms,
									@NotNull int[] rgb) throws IOException {
		Util.checkNotNull(pdfFile, targetFile, terms, rgb);
		List<String> searchTerms = new ArrayList<String>();
		for (String term : terms) {
			String trimmed = term == null ? "" : term.trim().toLowerCase();
			if (trimmed.length() >= 2)
				searchTerms.add(trimmed);
		}
		if (searchTerms.isEmpty())
			return null;

		PDDocument doc = PDDocument.load(pdfFile);
		try {
			if (doc.isEncrypted())
				return null;
			int pageCount = doc.getNumberOfPages();
			if (pageCount == 0 || pageCount > MAX_PAGES)
				return null;

			PDColor color = new PDColor(new float[] {
				rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f}, PDDeviceRGB.INSTANCE);
			int firstMatchPage = 0;
			int markCount = 0;
			for (int i = 0; i < pageCount && markCount < MAX_MARKS; i++) {
				PDPage page = doc.getPage(i);
				List<PDRectangle> boxes = findMatches(doc, i + 1, page, searchTerms);
				for (PDRectangle box : boxes) {
					PDAnnotation mark = createHighlight(box, color);
					/*
					 * Viewers usually draw markup annotations themselves, but
					 * some (including the PDF renderer used for the preview
					 * without a browser) only draw them if they come with an
					 * appearance stream.
					 */
					try {
						mark.constructAppearances(doc);
					}
					catch (RuntimeException e) {
						Util.printErr("Appearance of the marking failed: " + e);
					}
					page.getAnnotations().add(mark);
					markCount++;
					if (firstMatchPage == 0)
						firstMatchPage = i + 1;
					if (markCount >= MAX_MARKS)
						break;
				}
			}
			if (firstMatchPage == 0)
				return null; // Nothing found, show the original file
			doc.save(targetFile);
			return new Result(targetFile, firstMatchPage);
		}
		catch (RuntimeException e) {
			// Malformed documents, unusual fonts etc.
			throw new IOException(e.toString(), e);
		}
		finally {
			doc.close();
		}
	}

	@NotNull
	private static PDAnnotation createHighlight(@NotNull PDRectangle box,
												@NotNull PDColor color) {
		PDAnnotationTextMarkup mark = new PDAnnotationTextMarkup(
			PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT);
		mark.setRectangle(box);
		mark.setQuadPoints(new float[] {
			box.getLowerLeftX(), box.getUpperRightY(),
			box.getUpperRightX(), box.getUpperRightY(),
			box.getLowerLeftX(), box.getLowerLeftY(),
			box.getUpperRightX(), box.getLowerLeftY()});
		mark.setColor(color);
		mark.setConstantOpacity(0.4f);
		mark.setPrinted(true);
		return mark;
	}

	/** Returns the boxes of all term occurrences on the given page. */
	@NotNull
	private static List<PDRectangle> findMatches(	@NotNull PDDocument doc,
													int pageNumber,
													@NotNull PDPage page,
													@NotNull List<String> terms)
			throws IOException {
		final StringBuilder text = new StringBuilder();
		final List<TextPosition> positions = new ArrayList<TextPosition>();
		PDFTextStripper stripper = new PDFTextStripper() {
			protected void writeString(String string, List<TextPosition> textPositions) {
				for (TextPosition position : textPositions) {
					String unicode = position.getUnicode();
					if (unicode == null || unicode.isEmpty())
						continue;
					/*
					 * A text position can stand for several characters (e.g.
					 * a ligature); each of them points back to it, so that
					 * the character indexes and the positions stay in sync.
					 */
					for (int i = 0; i < unicode.length(); i++) {
						text.append(unicode.charAt(i));
						positions.add(position);
					}
				}
			}
		};
		stripper.setStartPage(pageNumber);
		stripper.setEndPage(pageNumber);
		stripper.setSortByPosition(true);
		stripper.getText(doc);

		String lowerText = text.toString().toLowerCase();
		List<PDRectangle> boxes = new ArrayList<PDRectangle>();
		for (String term : terms) {
			int from = 0;
			while (true) {
				int index = lowerText.indexOf(term, from);
				if (index < 0)
					break;
				from = index + term.length();
				if (!isWholeWord(lowerText, index, from))
					continue;
				PDRectangle box = toBox(page, positions, index, from);
				if (box != null)
					boxes.add(box);
			}
		}
		return boxes;
	}

	/**
	 * Returns true if the match isn't part of a longer word, so that
	 * searching for 'oak' doesn't mark 'cloak'.
	 */
	private static boolean isWholeWord(@NotNull String text, int start, int end) {
		if (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1)))
			return false;
		return end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
	}

	/**
	 * Returns the box of the characters in the given range in the page's
	 * coordinate system, or null if the characters are spread over several
	 * lines.
	 */
	@Nullable
	private static PDRectangle toBox(	@NotNull PDPage page,
										@NotNull List<TextPosition> positions,
										int start,
										int end) {
		if (start < 0 || end > positions.size() || start >= end)
			return null;
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		for (int i = start; i < end; i++) {
			TextPosition position = positions.get(i);
			float x = position.getXDirAdj();
			float y = position.getYDirAdj(); // Distance from the top
			float width = position.getWidthDirAdj();
			float height = position.getHeightDir();
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x + width);
			minY = Math.min(minY, y - height);
			maxY = Math.max(maxY, y);
		}
		if (maxY - minY > 3 * (maxX - minX) || maxX <= minX)
			return null; // Spread over several lines
		
		PDRectangle box = page.getCropBox();
		int rotation = ((page.getRotation() % 360) + 360) % 360;
		float[] lower = toPageSpace(minX, maxY, box, rotation);
		float[] upper = toPageSpace(maxX, minY, box, rotation);
		return new PDRectangle(
			Math.min(lower[0], upper[0]), Math.min(lower[1], upper[1]),
			Math.abs(upper[0] - lower[0]), Math.abs(upper[1] - lower[1]));
	}

	/**
	 * Maps a point of the displayed page (origin top left, as reported by the
	 * text stripper) to the page's coordinate system (origin bottom left).
	 */
	@NotNull
	private static float[] toPageSpace(	float x,
										float y,
										@NotNull PDRectangle box,
										int rotation) {
		float llx = box.getLowerLeftX();
		float lly = box.getLowerLeftY();
		float urx = box.getUpperRightX();
		float ury = box.getUpperRightY();
		switch (rotation) {
		case 90:
			return new float[] {llx + y, lly + x};
		case 180:
			return new float[] {urx - x, lly + y};
		case 270:
			return new float[] {urx - y, ury - x};
		default:
			return new float[] {llx + x, ury - y};
		}
	}

}
