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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import org.junit.Test;

/**
 * Tests that recognized text survives a round trip through PNG and JPEG
 * files, and that the image data and modification time stay intact.
 */
public final class OcrTextEmbeddingTest {

	private static final String TEXT = "Rechnung Nr. 4711\nBetrag: 142,03 € – Straße";

	private static File writeImage(String format) throws Exception {
		File file = File.createTempFile("docfetcher-test", "." + format);
		file.deleteOnExit();
		BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
		image.setRGB(5, 5, 0xFF0000);
		ImageIO.write(image, format, file);
		return file;
	}

	private static void roundTrip(String format) throws Exception {
		File file = writeImage(format);
		long mtime = file.lastModified() - 100000;
		assertTrue(file.setLastModified(mtime));
		assertNull(OcrTextEmbedding.read(file));

		assertTrue(OcrTextEmbedding.write(file, TEXT));
		assertEquals(TEXT, OcrTextEmbedding.read(file));
		assertEquals(mtime, file.lastModified());
		BufferedImage image = ImageIO.read(file);
		assertNotNull(image);
		assertEquals(40, image.getWidth());

		// Writing again replaces the text instead of adding a second copy
		assertTrue(OcrTextEmbedding.write(file, "second"));
		assertEquals("second", OcrTextEmbedding.read(file));
	}

	@Test
	public void pngRoundTrip() throws Exception {
		roundTrip("png");
	}

	@Test
	public void jpegRoundTrip() throws Exception {
		roundTrip("jpg");
	}

	@Test
	public void longTextSplitsIntoSeveralJpegSegments() throws Exception {
		File file = writeImage("jpg");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 20000; i++)
			sb.append("word").append(i).append(' ');
		String text = sb.toString();
		assertTrue(text.length() > 70000);
		assertTrue(OcrTextEmbedding.write(file, text));
		assertEquals(text, OcrTextEmbedding.read(file));
		assertNotNull(ImageIO.read(file));
	}

}
