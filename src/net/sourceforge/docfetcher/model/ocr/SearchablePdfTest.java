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

	/**
	 * Adds the text layer to a scanned PDF in place. The document is read
	 * from the file it replaces, which Windows only allows after it was
	 * closed.
	 */
	@Test
	public void addsTheTextLayerToAScannedPdfInPlace() throws Exception {
		Assume.assumeTrue(OcrEngine.getInstance().isAvailable());
		File scan = writeScan();
		assertTrue(SearchablePdf.ocr(scan, OcrEngine.getInstance(), Cancelable.nullCancelable));
		PDDocument result = PDDocument.load(scan);
		try {
			String text = new PDFTextStripper().getText(result);
			assertTrue(text, text.contains("4711"));
		}
		finally {
			result.close();
		}
		assertFalse("No temporary file left", new File(scan.getPath() + ".docfetcher-tmp").exists());
	}

	private static String textOf(File pdf) throws Exception {
		PDDocument doc = PDDocument.load(pdf);
		try {
			return new PDFTextStripper().getText(doc);
		}
		finally {
			doc.close();
		}
	}

	/**
	 * Open in another program (here: java.io, which keeps others from
	 * replacing the file on Windows), the recognized copy waits; the index
	 * gets its text without a second recognition, and the copy replaces the
	 * file once it is free - unless the file was changed meanwhile.
	 */
	@Test
	public void theTextLayerWaitsWhileTheFileIsOpenElsewhere() throws Exception {
		Assume.assumeTrue(OcrEngine.getInstance().isAvailable());
		Assume.assumeTrue(net.sourceforge.docfetcher.util.Util.IS_WINDOWS);
		File pending = java.nio.file.Files.createTempDirectory("docfetcher-pending").toFile();
		PendingOcr.setDir(pending);
		int attempts = PendingOcr.attempts;
		PendingOcr.attempts = 2;
		PendingOcr.pauseMillis = 10;
		File scan = writeScan();
		try {
			java.io.FileInputStream viewer = new java.io.FileInputStream(scan);
			try {
				assertEquals(scan, OcrPreprocessor.prepare(scan, Cancelable.nullCancelable));
				assertTrue("The copy waits", PendingOcr.has(scan));
				assertFalse("The file is unchanged", textOf(scan).contains("4711"));
				// The parsing that follows gets the text of the copy, no second recognition
				PDDocument doc = net.sourceforge.docfetcher.model.pdf.PdfFiles.load(scan);
				try {
					long start = System.currentTimeMillis();
					String text = OcrPreprocessor.recognizePdf(doc, Cancelable.nullCancelable);
					assertTrue(text, text.contains("4711"));
					assertTrue("Not recognized again", System.currentTimeMillis() - start < 500);
				}
				finally {
					doc.close();
				}
				// Still open on the next update: it keeps waiting
				assertFalse(PendingOcr.apply(scan));
			}
			finally {
				viewer.close();
			}
			// Free again: the next update puts the copy in place
			assertEquals(scan, OcrPreprocessor.prepare(scan, Cancelable.nullCancelable));
			assertFalse(PendingOcr.has(scan));
			assertTrue(textOf(scan).contains("4711"));
			assertEquals(0, pending.listFiles().length);

			// Changed meanwhile: the copy is outdated and dropped
			File other = writeScan();
			java.io.FileInputStream viewer2 = new java.io.FileInputStream(other);
			try {
				SearchablePdf.ocr(other, OcrEngine.getInstance(), Cancelable.nullCancelable);
			}
			finally {
				viewer2.close();
			}
			assertTrue(PendingOcr.has(other));
			assertTrue(other.setLastModified(other.lastModified() - 60000));
			assertFalse(PendingOcr.has(other));
			assertEquals(0, pending.listFiles().length);
		}
		finally {
			PendingOcr.attempts = attempts;
			PendingOcr.pauseMillis = 300;
			net.sourceforge.docfetcher.util.UtilFile.deletePermanently(pending);
		}
	}

	/**
	 * The file isn't locked during the recognition: if the user moves it
	 * meanwhile, the result is dropped instead of creating the file again at
	 * its old place.
	 */
	@Test
	public void aFileMovedDuringTheRecognitionStaysMoved() throws Exception {
		Assume.assumeTrue(OcrEngine.getInstance().isAvailable());
		final File scan = writeScan();
		final File moved = new File(scan.getPath() + "-moved.pdf");
		moved.deleteOnExit();
		Cancelable userMovesIt = new Cancelable() {
			public boolean isCanceled() {
				if (scan.exists())
					assertTrue("Movable during the recognition", scan.renameTo(moved));
				return false;
			}
		};
		assertFalse(SearchablePdf.ocr(scan, OcrEngine.getInstance(), userMovesIt));
		assertFalse("Not created again", scan.exists());
		assertTrue(moved.isFile());
		assertFalse("No temporary file left", new File(scan.getPath() + ".docfetcher-tmp").exists());
	}

	/** A scanned PDF without text layer: the invoice image as the only content of the page. */
	private static File writeScan() throws Exception {
		File tiff = writeFaxTiff();
		File pdf = SearchablePdf.convertTiff(
			tiff, OcrEngine.getInstance(), Cancelable.nullCancelable, false);
		assertNotNull(pdf);
		pdf.deleteOnExit();
		File scan = File.createTempFile("docfetcher-scan", ".pdf");
		scan.deleteOnExit();
		PDDocument doc = PDDocument.load(pdf);
		try {
			PDDocument copy = new PDDocument();
			try {
				org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(
					doc.getPage(0).getMediaBox());
				copy.addPage(page);
				org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject image =
					org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(copy,
						new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0, 150));
				org.apache.pdfbox.pdmodel.PDPageContentStream cs =
					new org.apache.pdfbox.pdmodel.PDPageContentStream(copy, page);
				cs.drawImage(image, 0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
				cs.close();
				copy.save(scan);
			}
			finally {
				copy.close();
			}
		}
		finally {
			doc.close();
		}
		pdf.delete();
		return scan;
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
