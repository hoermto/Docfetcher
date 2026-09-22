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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Reads the pages of an image file (TIFF files may have several) together
 * with their resolution, using ImageIO.
 */
public final class ImagePages {

	/** One page of an image file. */
	public static final class Page {
		public final BufferedImage image;
		/** Resolution in dots per inch; a default if the file doesn't say. */
		public final int dpi;

		Page(@NotNull BufferedImage image, int dpi) {
			this.image = image;
			this.dpi = dpi;
		}
	}

	private ImagePages() {}

	/**
	 * Reads all pages of the given image file, with ImageIO or, if that
	 * fails, with Leptonica. Throws an IOException if the format is not
	 * supported or the file is damaged.
	 */
	@NotNull
	public static List<Page> read(@NotNull File file) throws IOException {
		try {
			return readWithImageIO(file);
		}
		catch (Throwable e) {
			// ImageIO's TIFF decoder throws on some archive files (e.g.
			// ArrayIndexOutOfBoundsException); Leptonica may still read them
			if (e instanceof OutOfMemoryError)
				throw (OutOfMemoryError) e;
			if (!LeptonicaImages.isAvailable())
				throw e instanceof IOException ? (IOException) e : new IOException(e.toString(), e);
			try {
				return LeptonicaImages.read(file);
			}
			catch (IOException e2) {
				throw new IOException(e.toString() + " / " + e2.getMessage(), e);
			}
		}
	}

	@NotNull
	private static List<Page> readWithImageIO(@NotNull File file) throws IOException {
		List<Page> pages = new ArrayList<Page>();
		ImageInputStream in = ImageIO.createImageInputStream(file);
		if (in == null)
			throw new IOException("Can't read " + file);
		try {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext())
				throw new IOException("Unsupported image format: " + file);
			ImageReader reader = readers.next();
			try {
				reader.setInput(in);
				int count;
				try {
					count = reader.getNumImages(true);
				}
				catch (IllegalStateException e) {
					count = 1;
				}
				for (int i = 0; i < Math.max(1, count); i++) {
					BufferedImage image = reader.read(i);
					int dpi = OcrEngine.RENDER_DPI;
					try {
						dpi = readDpi(reader.getImageMetadata(i), dpi);
					}
					catch (IOException e) {
						// Keep the default
					}
					pages.add(new Page(image, dpi));
				}
			}
			finally {
				reader.dispose();
			}
		}
		finally {
			in.close();
		}
		return pages;
	}

	/**
	 * Reads the horizontal resolution from the standard image metadata
	 * (pixel size in millimeters), or returns the default.
	 */
	private static int readDpi(@Nullable IIOMetadata metadata, int defaultDpi) {
		if (metadata == null)
			return defaultDpi;
		try {
			Node root = metadata.getAsTree("javax_imageio_1.0");
			NodeList dims = ((Element) root).getElementsByTagName("HorizontalPixelSize");
			if (dims.getLength() == 0)
				return defaultDpi;
			Node value = dims.item(0).getAttributes().getNamedItem("value");
			if (value == null)
				return defaultDpi;
			double mmPerPixel = Double.parseDouble(value.getNodeValue());
			if (mmPerPixel <= 0)
				return defaultDpi;
			int dpi = (int) Math.round(25.4 / mmPerPixel);
			return dpi >= 50 && dpi <= 2400 ? dpi : defaultDpi;
		}
		catch (RuntimeException e) {
			return defaultDpi;
		}
	}

}
