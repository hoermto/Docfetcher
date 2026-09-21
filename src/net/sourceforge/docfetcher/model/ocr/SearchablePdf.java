/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.ocr;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.util.Matrix;

import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.model.ocr.OcrEngine.Result;
import net.sourceforge.docfetcher.model.ocr.OcrEngine.Word;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Creates "searchable" PDF files: an invisible text layer with the
 * recognized words is placed over the scanned page images, so that the text
 * can be extracted, searched and selected by any PDF viewer.
 */
public final class SearchablePdf {

	/** Document information key marking PDFs that went through recognition. */
	public static final String INFO_KEY = "DocFetcherOCR";

	/** PDFs with more pages are left alone (recognition would take too long). */
	private static final int MAX_PAGES = 500;

	/** Longest side of a rendered page in pixels. */
	private static final int MAX_RENDER_PIXELS = 5000;

	/** Pages with more text than this are considered to have a text layer. */
	private static final int MIN_TEXT_CHARS = 20;

	private static final PDFont FONT = PDType1Font.HELVETICA;

	private SearchablePdf() {}

	/**
	 * Returns true if the given PDF should be recognized: it is not
	 * encrypted, has not been recognized before and contains no text.
	 */
	public static boolean needsOcr(@NotNull PDDocument doc) throws IOException {
		if (doc.isEncrypted())
			return false;
		int pageCount = doc.getNumberOfPages();
		if (pageCount == 0 || pageCount > MAX_PAGES)
			return false;
		if (doc.getDocumentInformation().getCustomMetadataValue(INFO_KEY) != null)
			return false;
		return !hasText(doc);
	}

	/** Returns true if any page of the document has extractable text. */
	public static boolean hasText(@NotNull PDDocument doc) throws IOException {
		PDFTextStripper stripper = new PDFTextStripper();
		int pageCount = doc.getNumberOfPages();
		for (int i = 1; i <= pageCount; i++) {
			stripper.setStartPage(i);
			stripper.setEndPage(i);
			String text = stripper.getText(doc);
			if (text.replaceAll("\\s+", "").length() >= MIN_TEXT_CHARS)
				return true;
		}
		return false;
	}

	/**
	 * Recognizes the text of all pages of the given PDF file and writes it
	 * back into the file as an invisible text layer, keeping the file's
	 * modification time. Returns false if the file was left unchanged (it
	 * already has text, is encrypted, or recognition was cancelled).
	 */
	public static boolean ocr(	@NotNull File pdfFile,
								@NotNull OcrEngine engine,
								@NotNull Cancelable cancelable) throws IOException {
		Util.checkNotNull(pdfFile, engine, cancelable);
		PDDocument doc = PDDocument.load(pdfFile);
		try {
			if (!needsOcr(doc))
				return false;
			PDFRenderer renderer = new PDFRenderer(doc);
			int pageCount = doc.getNumberOfPages();
			for (int i = 0; i < pageCount; i++) {
				if (cancelable.isCanceled())
					return false;
				PDPage page = doc.getPage(i);
				int dpi = renderDpi(page);
				BufferedImage image = renderer.renderImageWithDPI(i, dpi, ImageType.GRAY);
				Result result = engine.recognize(image, dpi);
				addTextLayer(doc, page, result, image.getWidth(), image.getHeight());
			}
			markRecognized(doc);
			saveReplacing(doc, pdfFile);
			return true;
		}
		finally {
			doc.close();
		}
	}

	/**
	 * Converts the given TIFF file into a PDF with a text layer, next to the
	 * TIFF file and with its modification time. Returns the PDF file, or null
	 * if the target file exists already or the conversion was cancelled. The
	 * TIFF file is deleted after a successful conversion.
	 */
	@Nullable
	public static File convertTiff(	@NotNull File tiffFile,
									@NotNull OcrEngine engine,
									@NotNull Cancelable cancelable) throws IOException {
		Util.checkNotNull(tiffFile, engine, cancelable);
		File pdfFile = new File(tiffFile.getParentFile(),
			Util.splitFilename(tiffFile.getName())[0] + ".pdf");
		if (pdfFile.exists())
			return null;
		long lastModified = tiffFile.lastModified();
		File temp = new File(pdfFile.getParentFile(), pdfFile.getName() + ".docfetcher-tmp");
		PDDocument doc = new PDDocument();
		boolean success = false;
		try {
			List<ImagePages.Page> pages = ImagePages.read(tiffFile);
			if (pages.isEmpty())
				return null;
			for (ImagePages.Page tiffPage : pages) {
				if (cancelable.isCanceled())
					return null;
				BufferedImage image = tiffPage.image;
				float widthPt = image.getWidth() * 72f / tiffPage.dpi;
				float heightPt = image.getHeight() * 72f / tiffPage.dpi;
				PDPage page = new PDPage(new PDRectangle(widthPt, heightPt));
				doc.addPage(page);
				PDImageXObject imageObject = isBilevel(image)
					? LosslessFactory.createFromImage(doc, image)
					: JPEGFactory.createFromImage(doc, image, 0.85f);
				PDPageContentStream cs = new PDPageContentStream(doc, page);
				try {
					cs.drawImage(imageObject, 0, 0, widthPt, heightPt);
				}
				finally {
					cs.close();
				}
				Result result = engine.recognize(image, tiffPage.dpi);
				addTextLayer(doc, page, result, image.getWidth(), image.getHeight());
			}
			markRecognized(doc);
			doc.save(temp);
			success = true;
		}
		finally {
			doc.close();
			if (!success)
				temp.delete();
		}
		Files.move(temp.toPath(), pdfFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		if (lastModified > 0)
			Files.setLastModifiedTime(pdfFile.toPath(), FileTime.fromMillis(lastModified));
		if (!tiffFile.delete())
			Util.printErr("Could not delete " + tiffFile + " after converting it to PDF.");
		return pdfFile;
	}

	private static void markRecognized(@NotNull PDDocument doc) {
		doc.getDocumentInformation().setCustomMetadataValue(
			INFO_KEY, "Tesseract " + OcrEngine.LANGUAGES);
	}

	/** Saves the document over the given file, keeping the modification time. */
	private static void saveReplacing(@NotNull PDDocument doc, @NotNull File file) throws IOException {
		long lastModified = file.lastModified();
		File temp = new File(file.getParentFile(), file.getName() + ".docfetcher-tmp");
		try {
			doc.save(temp);
			Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		finally {
			temp.delete();
		}
		if (lastModified > 0)
			Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(lastModified));
	}

	/** Chooses a rendering resolution that keeps large pages within limits. */
	private static int renderDpi(@NotNull PDPage page) {
		PDRectangle box = page.getCropBox();
		float longest = Math.max(box.getWidth(), box.getHeight());
		if (longest <= 0)
			return OcrEngine.RENDER_DPI;
		int dpi = OcrEngine.RENDER_DPI;
		while (longest * dpi / 72f > MAX_RENDER_PIXELS && dpi > 72)
			dpi -= 50;
		return Math.max(dpi, 72);
	}

	/**
	 * Appends the recognized words as invisible text to the page. The word
	 * boxes refer to the rendered page image of the given size, which shows
	 * the crop box with the page rotation applied.
	 */
	private static void addTextLayer(	@NotNull PDDocument doc,
										@NotNull PDPage page,
										@NotNull Result result,
										int imageWidth,
										int imageHeight) throws IOException {
		if (result.words.isEmpty())
			return;
		PDRectangle box = page.getCropBox();
		int rotation = ((page.getRotation() % 360) + 360) % 360;
		boolean swapped = rotation == 90 || rotation == 270;
		float scale = (swapped ? box.getHeight() : box.getWidth()) / imageWidth;
		if (scale <= 0 || Float.isNaN(scale) || Float.isInfinite(scale))
			return;

		PDPageContentStream cs = new PDPageContentStream(doc, page, AppendMode.APPEND, true, true);
		try {
			cs.beginText();
			cs.setRenderingMode(RenderingMode.NEITHER);
			for (Word word : result.words) {
				String text = encodable(word.text);
				if (text.isEmpty())
					continue;
				float heightPt = word.height * scale;
				float widthPt = word.width * scale;
				float fontSize = (float) Util.clamp(heightPt, 4, 72);
				// Baseline: bottom of the word box plus room for descenders
				float xi = word.left;
				float yi = word.top + word.height - 0.2f * word.height;
				float[] pt = toPageSpace(xi, yi, scale, box, rotation);
				cs.setFont(FONT, fontSize);
				float textWidth = FONT.getStringWidth(text) / 1000f * fontSize;
				cs.setHorizontalScaling(textWidth > 0
					? (float) Util.clamp(100.0 * widthPt / textWidth, 10, 1000)
					: 100f);
				cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(rotation), pt[0], pt[1]));
				cs.showText(text);
			}
			cs.endText();
		}
		finally {
			cs.close();
		}
	}

	/** Maps a point of the rendered image to page coordinates. */
	@NotNull
	private static float[] toPageSpace(	float xi,
										float yi,
										float scale,
										@NotNull PDRectangle box,
										int rotation) {
		float llx = box.getLowerLeftX();
		float lly = box.getLowerLeftY();
		float urx = box.getUpperRightX();
		float ury = box.getUpperRightY();
		switch (rotation) {
		case 90:
			return new float[] {llx + yi * scale, lly + xi * scale};
		case 180:
			return new float[] {urx - xi * scale, lly + yi * scale};
		case 270:
			return new float[] {urx - yi * scale, ury - xi * scale};
		default:
			return new float[] {llx + xi * scale, ury - yi * scale};
		}
	}

	/** Replaces characters the standard font can't encode. */
	@NotNull
	private static String encodable(@NotNull String text) {
		try {
			FONT.encode(text);
			return text;
		}
		catch (IllegalArgumentException e) {
			// Fall through
		}
		catch (IOException e) {
			return "";
		}
		StringBuilder sb = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			String c = text.substring(i, i + 1);
			try {
				FONT.encode(c);
				sb.append(c);
			}
			catch (IllegalArgumentException e) {
				sb.append('?');
			}
			catch (IOException e) {
				sb.append('?');
			}
		}
		return sb.toString();
	}

	private static boolean isBilevel(@NotNull BufferedImage image) {
		return image.getType() == BufferedImage.TYPE_BYTE_BINARY
			&& image.getColorModel().getPixelSize() == 1;
	}

}
