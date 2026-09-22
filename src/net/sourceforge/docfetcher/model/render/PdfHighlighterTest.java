/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;
import org.junit.Test;

/** Tests marking the search terms in a PDF file. */
public final class PdfHighlighterTest {

	private static final int[] YELLOW = {255, 255, 0};

	/** A two-page PDF; the term to find is on the second page. */
	private static File createPdf() throws Exception {
		File file = File.createTempFile("docfetcher-test", ".pdf");
		file.deleteOnExit();
		PDDocument doc = new PDDocument();
		try {
			String[] lines = {"Cloakroom notes without the word", "Invoice for the squirrel"};
			for (String line : lines) {
				PDPage page = new PDPage(PDRectangle.A4);
				doc.addPage(page);
				PDPageContentStream cs = new PDPageContentStream(doc, page);
				cs.beginText();
				cs.setFont(PDType1Font.HELVETICA, 14);
				cs.newLineAtOffset(72, 700);
				cs.showText(line);
				cs.endText();
				cs.close();
			}
			doc.save(file);
		}
		finally {
			doc.close();
		}
		return file;
	}

	private static File targetFile() throws Exception {
		File file = File.createTempFile("docfetcher-test-marked", ".pdf");
		file.deleteOnExit();
		return file;
	}

	private static List<PDAnnotation> annotationsOf(File file, int pageIndex) throws Exception {
		PDDocument doc = PDDocument.load(file);
		try {
			return doc.getPage(pageIndex).getAnnotations();
		}
		finally {
			doc.close();
		}
	}

	@Test
	public void marksTheTermOnTheRightPage() throws Exception {
		File source = createPdf();
		File target = targetFile();
		PdfHighlighter.Result result = PdfHighlighter.highlight(
			source, target, Arrays.asList("squirrel"), YELLOW);
		assertNotNull(result);
		assertEquals(2, result.getFirstMatchPage());
		assertEquals(target, result.getFile());

		assertTrue(annotationsOf(target, 0).isEmpty());
		List<PDAnnotation> marks = annotationsOf(target, 1);
		assertEquals(1, marks.size());
		PDAnnotation mark = marks.get(0);
		assertTrue(mark instanceof PDAnnotationTextMarkup);
		assertEquals(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT, mark.getSubtype());

		// The mark sits on the text line, not somewhere off the page
		PDRectangle box = mark.getRectangle();
		assertTrue("x=" + box.getLowerLeftX(), box.getLowerLeftX() > 60);
		assertTrue("x=" + box.getUpperRightX(), box.getUpperRightX() < 400);
		assertTrue("y=" + box.getLowerLeftY(), box.getLowerLeftY() > 680);
		assertTrue("y=" + box.getUpperRightY(), box.getUpperRightY() < 730);
		assertTrue("width=" + box.getWidth(), box.getWidth() > 20);
		assertTrue("height=" + box.getHeight(), box.getHeight() > 5);

		// The original file stays as it was
		assertTrue(annotationsOf(source, 1).isEmpty());
	}

	@Test
	public void ignoresPartsOfLongerWords() throws Exception {
		// 'oak' also occurs inside 'Cloakroom', which must not be marked
		PdfHighlighter.Result result = PdfHighlighter.highlight(
			createPdf(), targetFile(), Arrays.asList("oak"), YELLOW);
		assertNull(result);
	}

	@Test
	public void findsTermsRegardlessOfCase() throws Exception {
		PdfHighlighter.Result result = PdfHighlighter.highlight(
			createPdf(), targetFile(), Arrays.asList("INVOICE"), YELLOW);
		assertNotNull(result);
		assertEquals(2, result.getFirstMatchPage());
	}

	@Test
	public void returnsNothingWithoutMatchesOrTerms() throws Exception {
		assertNull(PdfHighlighter.highlight(
			createPdf(), targetFile(), Arrays.asList("dolphin"), YELLOW));
		assertNull(PdfHighlighter.highlight(
			createPdf(), targetFile(), Collections.<String> emptyList(), YELLOW));
		// Single characters are not searched for
		assertNull(PdfHighlighter.highlight(
			createPdf(), targetFile(), Arrays.asList("a"), YELLOW));
	}

}
