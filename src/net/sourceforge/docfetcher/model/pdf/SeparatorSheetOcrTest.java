/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.pdf;

import static org.junit.Assert.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.Assume;
import org.junit.Test;

import net.sourceforge.docfetcher.model.ocr.OcrEngine;
import net.sourceforge.docfetcher.util.AppUtil;

/**
 * Tests that the text recognition finds the marker on a scanned separator
 * sheet, also when the sheet was put into the scanner upside down.
 */
public final class SeparatorSheetOcrTest {

	private static BufferedImage rotate180(BufferedImage image) {
		BufferedImage rotated = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = rotated.createGraphics();
		g.rotate(Math.PI, image.getWidth() / 2.0, image.getHeight() / 2.0);
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return rotated;
	}

	@Test
	public void recognizesTheScannedSheet() throws Exception {
		AppUtil.Const.autoInit();
		OcrEngine ocr = OcrEngine.getInstance();
		Assume.assumeTrue(ocr.isAvailable());
		File sheet = File.createTempFile("docfetcher-separator", ".pdf");
		try {
			SeparatorSheet.create(sheet, "Trennblatt",
				"Legen Sie dieses Blatt vor dem Scannen zwischen zwei Dokumente eines Stapels.");
			BufferedImage scan;
			PDDocument pdf = PDDocument.load(sheet);
			try {
				// Like a scanner with 200 dpi, in gray
				scan = new PDFRenderer(pdf).renderImage(0, 200 / 72f, ImageType.RGB);
			}
			finally {
				pdf.close();
			}
			String upright = ocr.recognize(scan, 200).text;
			assertTrue(upright, SeparatorSheet.isSeparator(upright));
			String upsideDown = ocr.recognize(rotate180(scan), 200).text;
			assertTrue(upsideDown, SeparatorSheet.isSeparator(upsideDown));
		}
		finally {
			sheet.delete();
		}
	}

}
