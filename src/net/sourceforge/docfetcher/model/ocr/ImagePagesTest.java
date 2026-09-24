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
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.Test;

import net.sourceforge.docfetcher.util.Util;

/**
 * Tests the image reader used for text recognition and the image preview:
 * ImageIO first, the bundled Leptonica library as fallback.
 */
public final class ImagePagesTest {

	/** A small bilevel page with a black bar, so that decoding can be checked. */
	private static BufferedImage createPage() {
		BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_BYTE_BINARY);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 200, 100);
		g.setColor(Color.BLACK);
		g.fillRect(20, 40, 160, 20);
		g.dispose();
		return image;
	}

	/** Writes a multi-page TIFF with the given compression type. */
	private static File writeTiff(String compression, int pages) throws Exception {
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
			param.setCompressionType(compression);
			writer.prepareWriteSequence(null);
			for (int i = 0; i < pages; i++)
				writer.writeToSequence(new IIOImage(createPage(), null, null), param);
			writer.endWriteSequence();
		}
		finally {
			writer.dispose();
			out.close();
		}
		return file;
	}

	private static void assertPageDecoded(BufferedImage image) {
		assertEquals(200, image.getWidth());
		assertEquals(100, image.getHeight());
		// Inside the bar: black; outside: white
		assertEquals(0, image.getRGB(100, 50) & 0xFFFFFF);
		assertEquals(0xFFFFFF, image.getRGB(100, 10) & 0xFFFFFF);
	}

	@Test
	public void readsFaxCompressedMultiPageTiff() throws Exception {
		File file = writeTiff("CCITT T.6", 3);
		List<ImagePages.Page> pages = ImagePages.read(file);
		assertEquals(3, pages.size());
		for (ImagePages.Page page : pages)
			assertPageDecoded(page.image);
	}

	@Test
	public void readsLzwTiff() throws Exception {
		List<ImagePages.Page> pages = ImagePages.read(writeTiff("LZW", 1));
		assertEquals(1, pages.size());
		assertPageDecoded(pages.get(0).image);
	}

	@Test
	public void leptonicaReadsTiffPages() throws Exception {
		if (!Util.IS_WINDOWS && !Util.IS_LINUX)
			return; // No bundled library
		assertTrue("Leptonica library not found", LeptonicaImages.isAvailable());
		List<ImagePages.Page> pages = LeptonicaImages.read(writeTiff("CCITT T.6", 2));
		assertEquals(2, pages.size());
		for (ImagePages.Page page : pages) {
			assertNotNull(page.image);
			assertPageDecoded(page.image);
		}
	}

	@Test
	public void readsPng() throws Exception {
		File file = File.createTempFile("docfetcher-test", ".png");
		file.deleteOnExit();
		ImageIO.write(createPage(), "png", file);
		List<ImagePages.Page> pages = ImagePages.read(file);
		assertEquals(1, pages.size());
		assertPageDecoded(pages.get(0).image);
	}

}
