/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.printing.PrinterData;
import org.eclipse.swt.widgets.Display;
import org.junit.Assume;
import org.junit.Test;

import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.Util;

/** Tests printing of PDF and image files. */
public final class DocumentPrinterTest {

	@Test
	public void printsInRealSizeAndShrinksBigPages() {
		Rectangle area = new Rectangle(10, 20, 1000, 1400);
		// Smaller than the area: real size, centered
		Rectangle small = DocumentPrinter.computeBounds(500, 700, area);
		assertEquals(new Rectangle(260, 370, 500, 700), small);
		// Too wide: shrunk to the width, proportions kept
		Rectangle wide = DocumentPrinter.computeBounds(2000, 1000, area);
		assertEquals(1000, wide.width);
		assertEquals(500, wide.height);
		assertEquals(10, wide.x);
	}

	@Test
	public void printsPdfsAndImagesItself() {
		assertTrue(DocumentPrinter.isPrintedByDocFetcher(new File("a.PDF")));
		assertTrue(DocumentPrinter.isPrintedByDocFetcher(new File("scan.tiff")));
		assertTrue(DocumentPrinter.isPrintedByDocFetcher(new File("photo.jpg")));
		assertFalse(DocumentPrinter.isPrintedByDocFetcher(new File("letter.docx")));
		assertFalse(DocumentPrinter.isPrintedByDocFetcher(new File("notes.txt")));
	}

	/** Creates a PDF with the given number of pages, each showing its number. */
	static File createPdf(File dir, String name, int pages) throws Exception {
		File file = new File(dir, name);
		PDDocument pdf = new PDDocument();
		try {
			for (int i = 1; i <= pages; i++) {
				PDPage page = new PDPage(PDRectangle.A4);
				pdf.addPage(page);
				PDPageContentStream content = new PDPageContentStream(pdf, page);
				content.beginText();
				content.setFont(PDType1Font.HELVETICA, 24);
				content.newLineAtOffset(72, 700);
				content.showText(name + " page " + i);
				content.endText();
				content.close();
			}
			pdf.save(file);
		}
		finally {
			pdf.close();
		}
		return file;
	}

	private static int countPages(File pdf) throws Exception {
		PDDocument doc = PDDocument.load(pdf);
		try {
			return doc.getNumberOfPages();
		}
		finally {
			doc.close();
		}
	}

	/**
	 * Prints into a PDF file with the 'Microsoft Print to PDF' printer of
	 * Windows, if it is there; neither a dialog nor paper is involved.
	 */
	@Test
	public void printsAllPagesOrThePageRange() throws Exception {
		Display display = GuiTestSupport.createDisplay();
		Assume.assumeTrue(display != null);
		File dir = GuiTestSupport.createDir("docfetcher-print");
		try {
			AppUtil.Const.autoInit();
			PrinterData printer = DocumentPrinter.findPrinter("Microsoft Print to PDF");
			Assume.assumeTrue(printer != null);

			File pdf = createPdf(dir, "two-pages.pdf", 2);
			File png = new File(dir, "scan.png");
			BufferedImage image = new BufferedImage(850, 1100, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = image.createGraphics();
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, 850, 1100);
			g.setColor(Color.BLACK);
			g.drawString("scanned page", 100, 100);
			g.dispose();
			ImageIO.write(image, "png", png);

			File out = new File(dir, "all.pdf");
			printer.printToFile = true;
			printer.fileName = out.getPath();
			assertTrue(DocumentPrinter.printFiles(printer, Arrays.asList(pdf, png)));
			waitFor(out);
			assertEquals("2 PDF pages + 1 image", 3, countPages(out));

			File range = new File(dir, "range.pdf");
			printer.fileName = range.getPath();
			printer.scope = PrinterData.PAGE_RANGE;
			printer.startPage = 2;
			printer.endPage = 2;
			assertTrue(DocumentPrinter.printFiles(printer, Arrays.asList(pdf, png)));
			waitFor(range);
			assertEquals("Only page 2 of the PDF", 1, countPages(range));
		}
		finally {
			display.dispose();
			Util.deleteRecursively(dir);
		}
	}

	/** The spooler writes the file after the job has ended. */
	private static void waitFor(File file) throws Exception {
		long size = -1;
		for (int i = 0; i < 100; i++) {
			if (file.isFile() && file.length() > 0 && file.length() == size)
				return;
			size = file.isFile() ? file.length() : -1;
			Thread.sleep(200);
		}
		throw new AssertionError("Not printed: " + file);
	}

}
