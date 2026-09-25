/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.pdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.sourceforge.docfetcher.model.pdf.PdfPages.Range;
import net.sourceforge.docfetcher.util.Util;

public final class PdfPagesTest {

	private File dir;

	@Before
	public void setUp() throws Exception {
		dir = File.createTempFile("docfetcher-pdfpages", "");
		assertTrue(dir.delete() && dir.mkdir());
	}

	@After
	public void tearDown() throws Exception {
		Util.deleteRecursively(dir);
	}

	/** A PDF whose pages carry the given texts as text. */
	static File createTextPdf(File file, String... pageTexts) throws Exception {
		PDDocument pdf = new PDDocument();
		try {
			for (String text : pageTexts) {
				PDPage page = new PDPage(PDRectangle.A4);
				pdf.addPage(page);
				PDPageContentStream content = new PDPageContentStream(pdf, page);
				content.beginText();
				content.setFont(PDType1Font.HELVETICA, 20);
				content.newLineAtOffset(72, 700);
				content.showText(text);
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

	/**
	 * A scanned page without text layer: white paper with a little noise and
	 * a dark scanner edge, and optionally some printed lines.
	 */
	private static BufferedImage scan(int lines) {
		BufferedImage image = new BufferedImage(620, 877, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 620, 877);
		g.setColor(Color.DARK_GRAY);
		g.fillRect(0, 0, 620, 12); // Shadow at the edge
		Random random = new Random(1);
		for (int i = 0; i < 40; i++) // Dust
			image.setRGB(40 + random.nextInt(540), 40 + random.nextInt(797), 0x303030);
		g.setColor(Color.BLACK);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
		for (int line = 0; line < lines; line++)
			g.drawString(lines == 1 ? "Doc A page 1" : "Rechnung Nr. 4711 vom 25.09.2026 - Position " + line,
				60, 120 + line * 40);
		g.dispose();
		return image;
	}

	/** A PDF of scanned pages with the given numbers of text lines (0 = blank). */
	private File createScanPdf(File file, int... linesPerPage) throws Exception {
		PDDocument pdf = new PDDocument();
		try {
			for (int lines : linesPerPage) {
				PDPage page = new PDPage(PDRectangle.A4);
				pdf.addPage(page);
				PDImageXObject image = LosslessFactory.createFromImage(pdf, scan(lines));
				PDPageContentStream content = new PDPageContentStream(pdf, page);
				content.drawImage(image, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
				content.close();
			}
			pdf.save(file);
		}
		finally {
			pdf.close();
		}
		return file;
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

	@Test
	public void computesTheRanges() {
		assertEquals(Arrays.asList(new Range(0, 1), new Range(1, 2), new Range(2, 3)),
			PdfPages.eachPage(3));
		// A new document starts at pages 3 and 5; out-of-range numbers are ignored
		assertEquals(Arrays.asList(new Range(0, 2), new Range(2, 4), new Range(4, 6)),
			PdfPages.atPages(6, Arrays.asList(5, 3, 1, 99, 3)));
		assertEquals(Arrays.asList(new Range(0, 6)), PdfPages.atPages(6, Arrays.<Integer> asList()));
		assertEquals("3-5", new Range(2, 5).toString());
		assertEquals("7", new Range(6, 7).toString());
	}

	@Test
	public void mergesInTheGivenOrder() throws Exception {
		File a = createTextPdf(new File(dir, "a.pdf"), "alpha one", "alpha two");
		File b = createTextPdf(new File(dir, "b.pdf"), "beta one");
		File merged = new File(dir, "merged.pdf");
		PdfPages.merge(Arrays.asList(b, a), merged);
		assertEquals(3, PdfPages.getPageCount(merged));
		String text = textOf(merged);
		assertTrue(text, text.indexOf("beta one") < text.indexOf("alpha one"));
		assertTrue(text, text.indexOf("alpha one") < text.indexOf("alpha two"));
	}

	@Test
	public void splitsIntoNumberedParts() throws Exception {
		File stack = createTextPdf(new File(dir, "stack.pdf"), "page A", "page B", "page C");
		List<File> parts = PdfPages.getPartFiles(stack, 2);
		assertEquals(new File(dir, "stack_1.pdf"), parts.get(0));
		assertEquals(new File(dir, "stack_2.pdf"), parts.get(1));
		PdfPages.split(stack, PdfPages.atPages(3, Arrays.asList(3)), parts);
		assertEquals(2, PdfPages.getPageCount(parts.get(0)));
		assertEquals(1, PdfPages.getPageCount(parts.get(1)));
		assertTrue(textOf(parts.get(1)).contains("page C"));
		assertFalse(textOf(parts.get(0)).contains("page C"));

		// Existing parts are not overwritten; the next split gets other names
		List<File> next = PdfPages.getPartFiles(stack, 2);
		assertEquals(new File(dir, "stack-2_1.pdf"), next.get(0));
	}

	@Test
	public void findsBlankScannedPages() throws Exception {
		// Page 4 has only one short line, which must not count as blank
		File scans = createScanPdf(new File(dir, "scans.pdf"), 12, 0, 12, 1, 0, 12);
		assertEquals("[false, true, false, false, true, false]", blankPages(scans));

		// A page with text is never blank, however little ink it has
		File text = createTextPdf(new File(dir, "text.pdf"), "Hello", " ");
		assertEquals("[false, true]", blankPages(text));
	}

	private static String blankPages(File pdf) throws Exception {
		List<Boolean> blank = new java.util.ArrayList<Boolean>();
		for (PdfPages.PageInfo page : PdfPages.analyzePages(pdf))
			blank.add(page.blank);
		return blank.toString();
	}

	@Test
	public void recognizesThePrintedSeparatorSheet() throws Exception {
		File sheet = new File(dir, "sheet.pdf");
		SeparatorSheet.create(sheet, "Trennblatt", "Zwischen die Dokumente legen und mitscannen.");
		PdfPages.PageInfo page = PdfPages.analyzePages(sheet).get(0);
		assertTrue(page.text, page.separatorSheet);
		assertFalse("Printed, so not blank", page.blank);
	}

}
