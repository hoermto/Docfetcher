/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.Assume;
import org.junit.Test;

import net.sourceforge.docfetcher.model.Cancelable;

/**
 * Tests the conversion of TIFF files into searchable PDF files: the text
 * must be found in the PDF and the PDF must not be much larger than the
 * TIFF file it came from.
 */
public final class SearchablePdfTest {

	private static final String TEXT = "Rechnung Nummer 4711";

	/** A 300 dpi black and white page with a line of text on it. */
	private static File writeFaxTiff() throws Exception {
		BufferedImage image = new BufferedImage(2480, 3508, BufferedImage.TYPE_BYTE_BINARY);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		g.setColor(Color.BLACK);
		g.setFont(new Font(Font.SERIF, Font.PLAIN, 60));
		g.drawString(TEXT, 200, 400);
		g.dispose();

		File file = File.createTempFile("docfetcher-test", ".tif");
		file.deleteOnExit();
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("tiff");
		assertTrue(writers.hasNext());
		ImageWriter writer = writers.next();
		ImageOutputStream out = ImageIO.createImageOutputStream(file);
		try {
			writer.setOutput(out);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionType("CCITT T.6");
			writer.write(null, new IIOImage(image, null, null), param);
		}
		finally {
			writer.dispose();
			out.close();
		}
		return file;
	}

	@Test
	public void convertsFaxTiffIntoASearchablePdfOfSimilarSize() throws Exception {
		Assume.assumeTrue(OcrEngine.getInstance().isAvailable());
		File tiff = writeFaxTiff();
		long tiffSize = tiff.length();

		File pdf = SearchablePdf.convertTiff(
			tiff, OcrEngine.getInstance(), Cancelable.nullCancelable, true);
		assertNotNull(pdf);
		pdf.deleteOnExit();
		new File(tiff.getPath() + SearchablePdf.CONVERTED_SUFFIX).deleteOnExit();

		// The scanned page keeps its fax compression, so the PDF stays small
		assertTrue("PDF is " + pdf.length() + " bytes, TIFF was " + tiffSize,
			pdf.length() < tiffSize * 1.3 + 10000);

		PDDocument doc = PDDocument.load(pdf);
		try {
			assertEquals(1, doc.getNumberOfPages());
			assertNotNull(doc.getDocumentInformation().getCustomMetadataValue(
				SearchablePdf.INFO_KEY));
			String text = new PDFTextStripper().getText(doc);
			assertTrue("Recognized text: " + text, text.contains("Rechnung"));
			assertTrue("Recognized text: " + text, text.contains("4711"));
			// Marked as recognized, so it isn't recognized again
			assertFalse(SearchablePdf.needsOcr(doc));
		}
		finally {
			doc.close();
		}
	}

	@Test
	public void recognizesTextWithoutChangingTheDocument() throws Exception {
		Assume.assumeTrue(OcrEngine.getInstance().isAvailable());
		File tiff = writeFaxTiff();
		File pdf = SearchablePdf.convertTiff(
			tiff, OcrEngine.getInstance(), Cancelable.nullCancelable, false);
		assertNotNull(pdf);
		pdf.deleteOnExit();
		PDDocument doc = PDDocument.load(pdf);
		try {
			String text = SearchablePdf.recognizeText(
				doc, OcrEngine.getInstance(), Cancelable.nullCancelable);
			assertNotNull(text);
			assertTrue("Recognized text: " + text, text.contains("4711"));
		}
		finally {
			doc.close();
		}
	}

}
