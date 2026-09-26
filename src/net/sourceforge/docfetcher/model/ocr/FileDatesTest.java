/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.Assume;
import org.junit.Test;

import net.sourceforge.docfetcher.model.Cancelable;

/**
 * Tests that storing the recognized text in a file changes none of its dates
 * (created, modified, accessed): sorting by date must keep working.
 */
public final class FileDatesTest {

	private static final FileTime CREATED = FileTime.fromMillis(1514764800000L); // 2018-01-01
	private static final FileTime MODIFIED = FileTime.fromMillis(1546300800000L); // 2019-01-01
	private static final FileTime ACCESSED = FileTime.fromMillis(1577836800000L); // 2020-01-01

	private static void setOldDates(File file) throws Exception {
		Files.getFileAttributeView(file.toPath(), BasicFileAttributeView.class)
			.setTimes(MODIFIED, ACCESSED, CREATED);
	}

	private static void assertOldDates(File file) throws Exception {
		BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
		assertEquals("modified", MODIFIED, attributes.lastModifiedTime());
		assertEquals("accessed", ACCESSED, attributes.lastAccessTime());
		assertEquals("created", CREATED, attributes.creationTime());
	}

	private static BufferedImage page() {
		BufferedImage image = new BufferedImage(1240, 400, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		g.setColor(Color.BLACK);
		g.setFont(new Font(Font.SERIF, Font.PLAIN, 60));
		g.drawString("Rechnung Nummer 4711", 100, 200);
		g.dispose();
		return image;
	}

	@Test
	public void textInImagesKeepsTheDates() throws Exception {
		File png = File.createTempFile("docfetcher-dates", ".png");
		try {
			ImageIO.write(page(), "png", png);
			setOldDates(png);
			assertTrue(OcrTextEmbedding.write(png, "Rechnung Nummer 4711"));
			assertOldDates(png);
		}
		finally {
			png.delete();
		}
	}

	/** The whole indexing step for an image: read, recognize, store the text. */
	@Test
	public void recognizingAnImageWhileIndexingKeepsTheDates() throws Exception {
		Assume.assumeTrue(OcrPreprocessor.isEnabled());
		File png = File.createTempFile("docfetcher-dates", ".png");
		try {
			ImageIO.write(page(), "png", png);
			setOldDates(png);
			OcrPreprocessor.prepare(png, Cancelable.nullCancelable);
			assertOldDates(png); // Before the check below reads the file
			assertNotNull("Text stored", OcrTextEmbedding.read(png));
		}
		finally {
			png.delete();
		}
	}

	@Test
	public void textLayerInScansKeepsTheDates() throws Exception {
		Assume.assumeTrue(OcrEngine.getInstance().isAvailable());
		File pdf = File.createTempFile("docfetcher-dates", ".pdf");
		try {
			PDDocument doc = new PDDocument();
			try {
				PDPage page = new PDPage();
				doc.addPage(page);
				PDPageContentStream cs = new PDPageContentStream(doc, page);
				cs.drawImage(LosslessFactory.createFromImage(doc, page()), 36, 500, 540, 174);
				cs.close();
				doc.save(pdf);
			}
			finally {
				doc.close();
			}
			setOldDates(pdf);
			assertTrue(SearchablePdf.ocr(pdf, OcrEngine.getInstance(), Cancelable.nullCancelable));
			assertOldDates(pdf);
		}
		finally {
			pdf.delete();
		}
	}

	@Test
	public void convertedTiffGivesItsDatesToThePdf() throws Exception {
		Assume.assumeTrue(OcrEngine.getInstance().isAvailable());
		File dir = Files.createTempDirectory("docfetcher-dates").toFile();
		File tiff = new File(dir, "fax.tif");
		File pdf = null;
		try {
			assertTrue(ImageIO.write(page(), "tiff", tiff));
			setOldDates(tiff);
			pdf = SearchablePdf.convertTiff(tiff, OcrEngine.getInstance(), Cancelable.nullCancelable, false);
			assertNotNull(pdf);
			assertOldDates(pdf);
		}
		finally {
			for (File file : dir.listFiles())
				file.delete();
			dir.delete();
		}
	}

}
