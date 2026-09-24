/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.ocr;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Reads image files with the bundled Leptonica library (which comes with
 * Tesseract). Used as a fallback when Java's ImageIO can't decode a file,
 * e.g. some fax-compressed TIFF files from document archives. The pages
 * are returned as 8-bit grayscale images.
 */
final class LeptonicaImages {

	/** The subset of the Leptonica C API that is used. */
	interface LeptAPI extends Library {
		Pointer pixRead(String filename);
		Pointer pixReadTiff(String filename, int pageIndex);
		Pointer pixConvertTo8(Pointer pix, int cmapflag);
		int pixGetWidth(Pointer pix);
		int pixGetHeight(Pointer pix);
		int pixGetDepth(Pointer pix);
		int pixGetWpl(Pointer pix);
		int pixGetXRes(Pointer pix);
		Pointer pixGetData(Pointer pix);
		void pixDestroy(PointerByReference ppix);
	}

	private static final int MAX_PAGES = 500;

	@Nullable private static LeptAPI api;
	private static boolean loadAttempted = false;

	private LeptonicaImages() {}

	@Nullable
	private static synchronized LeptAPI getApi() {
		if (loadAttempted)
			return api;
		loadAttempted = true;
		try {
			// The engine loads Leptonica's dependencies (Linux) first
			OcrEngine.getInstance().isAvailable();
			File lib = OcrEngine.findLeptonicaLibrary();
			if (lib != null)
				api = (LeptAPI) Native.loadLibrary(lib.getAbsolutePath(), LeptAPI.class);
			else
				api = (LeptAPI) Native.loadLibrary("leptonica", LeptAPI.class);
		}
		catch (Throwable e) {
			Util.printErr("Leptonica image reader is not available: " + e);
		}
		return api;
	}

	/** Returns true if the library could be loaded. */
	public static boolean isAvailable() {
		return getApi() != null;
	}

	/**
	 * Reads all pages of the given image file. Throws an IOException if the
	 * library is unavailable or can't decode the file.
	 */
	@NotNull
	public static List<ImagePages.Page> read(@NotNull File file) throws IOException {
		LeptAPI lept = getApi();
		if (lept == null)
			throw new IOException("Leptonica image reader is not available.");
		String path = file.getAbsolutePath();
		String ext = Util.getExtension(file.getName()).toLowerCase();
		boolean tiff = ext.equals("tif") || ext.equals("tiff");
		List<ImagePages.Page> pages = new ArrayList<ImagePages.Page>();
		synchronized (LeptonicaImages.class) {
			for (int i = 0; i < MAX_PAGES; i++) {
				Pointer pix = tiff ? lept.pixReadTiff(path, i) : lept.pixRead(path);
				if (pix == null)
					break;
				try {
					pages.add(toPage(lept, pix));
				}
				finally {
					lept.pixDestroy(new PointerByReference(pix));
				}
				if (!tiff)
					break;
			}
		}
		if (pages.isEmpty())
			throw new IOException("Leptonica can't decode " + file);
		return pages;
	}

	/**
	 * Converts a PIX into an 8-bit grayscale BufferedImage. Leptonica packs
	 * pixels into 32-bit words with the leftmost pixel in the most
	 * significant byte.
	 */
	@NotNull
	private static ImagePages.Page toPage(@NotNull LeptAPI lept, @NotNull Pointer pix) throws IOException {
		int dpi = lept.pixGetXRes(pix);
		Pointer gray = lept.pixGetDepth(pix) == 8 ? null : lept.pixConvertTo8(pix, 0);
		Pointer source = gray != null ? gray : pix;
		try {
			int width = lept.pixGetWidth(source);
			int height = lept.pixGetHeight(source);
			int wpl = lept.pixGetWpl(source);
			Pointer data = lept.pixGetData(source);
			if (width <= 0 || height <= 0 || data == null)
				throw new IOException("Empty image");
			BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
			byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
			int[] row = new int[wpl];
			for (int y = 0; y < height; y++) {
				data.read((long) y * wpl * 4, row, 0, wpl);
				int offset = y * width;
				for (int x = 0; x < width; x++) {
					int word = row[x >> 2];
					int shift = 24 - (x & 3) * 8;
					pixels[offset + x] = (byte) (word >>> shift);
				}
			}
			return new ImagePages.Page(image, dpi >= 50 && dpi <= 2400 ? dpi : OcrEngine.RENDER_DPI);
		}
		finally {
			if (gray != null)
				lept.pixDestroy(new PointerByReference(gray));
		}
	}

}
