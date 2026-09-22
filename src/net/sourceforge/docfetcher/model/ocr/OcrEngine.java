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
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Text recognition with the bundled Tesseract library (German and English).
 * The native library is loaded lazily on first use; if it can't be loaded,
 * {@link #isAvailable()} returns false and {@link #getUnavailableReason()}
 * says why. Recognition calls are serialized, since a Tesseract handle is not
 * thread-safe.
 */
public final class OcrEngine {

	/** Languages passed to Tesseract; the trained data must be bundled. */
	public static final String LANGUAGES = "deu+eng";

	/** Resolution used when rendering PDF pages for recognition. */
	public static final int RENDER_DPI = 300;

	/** A recognized word with its bounding box in image pixels. */
	public static final class Word {
		public final String text;
		public final int left, top, width, height;
		public final int confidence;

		Word(@NotNull String text, int left, int top, int width, int height, int confidence) {
			this.text = text;
			this.left = left;
			this.top = top;
			this.width = width;
			this.height = height;
			this.confidence = confidence;
		}
	}

	/** The result of recognizing one image. */
	public static final class Result {
		public final String text;
		public final List<Word> words;
		public final int meanConfidence;

		Result(@NotNull String text, @NotNull List<Word> words, int meanConfidence) {
			this.text = text;
			this.words = Collections.unmodifiableList(words);
			this.meanConfidence = meanConfidence;
		}
	}

	/** The subset of the Tesseract C API that is used. */
	interface TessAPI extends Library {
		Pointer TessBaseAPICreate();
		int TessBaseAPIInit3(Pointer handle, String datapath, String language);
		void TessBaseAPISetImage(Pointer handle, ByteBuffer imagedata, int width, int height, int bytesPerPixel, int bytesPerLine);
		void TessBaseAPISetSourceResolution(Pointer handle, int ppi);
		Pointer TessBaseAPIGetUTF8Text(Pointer handle);
		Pointer TessBaseAPIGetTsvText(Pointer handle, int pageNumber);
		int TessBaseAPIMeanTextConf(Pointer handle);
		void TessBaseAPIClear(Pointer handle);
		void TessBaseAPIEnd(Pointer handle);
		void TessBaseAPIDelete(Pointer handle);
		void TessDeleteText(Pointer text);
		String TessVersion();
	}

	private static final OcrEngine instance = new OcrEngine();

	@Nullable private TessAPI api;
	@Nullable private Pointer handle;
	@Nullable private String unavailableReason;
	private boolean initialized = false;

	private OcrEngine() {}

	@NotNull
	public static OcrEngine getInstance() {
		return instance;
	}

	/** Returns true if the native library and the trained data could be loaded. */
	public synchronized boolean isAvailable() {
		init();
		return handle != null;
	}

	@Nullable
	public synchronized String getUnavailableReason() {
		init();
		return unavailableReason;
	}

	private void init() {
		if (initialized)
			return;
		initialized = true;
		try {
			File tessdata = findTessdataDir();
			if (tessdata == null)
				throw new IllegalStateException("Trained data (tessdata folder) not found.");
			TessAPI loaded = loadLibrary();
			Pointer h = loaded.TessBaseAPICreate();
			if (h == null)
				throw new IllegalStateException("TessBaseAPICreate failed.");
			int rc = loaded.TessBaseAPIInit3(h, tessdata.getAbsolutePath(), LANGUAGES);
			if (rc != 0) {
				loaded.TessBaseAPIDelete(h);
				throw new IllegalStateException("Tesseract initialization failed for languages '"
					+ LANGUAGES + "' in " + tessdata.getAbsolutePath());
			}
			api = loaded;
			handle = h;
		}
		catch (Throwable e) {
			// UnsatisfiedLinkError, missing MSVC runtime, etc.
			unavailableReason = e.toString();
			Util.printErr("OCR is not available: " + e);
		}
	}

	/**
	 * Loads the Tesseract library. The bundled libraries are looked up in the
	 * 'lib' folder (release layout) and in 'lib/tesseract/<platform>'
	 * (development layout); if neither exists, the system library is tried.
	 */
	@NotNull
	private static TessAPI loadLibrary() {
		String platformDir;
		String[] tesseractNames;
		String[] dependencyNames;
		if (Util.IS_WINDOWS) {
			platformDir = "win32-x86-64";
			tesseractNames = new String[] {"libtesseract541.dll"};
			dependencyNames = new String[] {"libleptonica1841.dll"};
		}
		else if (Util.IS_LINUX) {
			platformDir = "linux-x86-64";
			tesseractNames = new String[] {"libtesseract-ea2dd323.so.5.5.1", "libtesseract.so.5"};
			dependencyNames = new String[] {
				"libjbig-2504a0c3.so.2.1", "libjpeg-e87d2658.so.62.2.0",
				"libpng16-ebd1fa42.so.16.34.0", "libwebp-78a6964e.so.7.0.2",
				"libwebpmux-489e612a.so.3.0.2", "libtiff-9308b5f3.so.5.3.0",
				"libleptonica-37b8efd2.so.6.0.0"};
		}
		else {
			platformDir = "";
			tesseractNames = new String[] {};
			dependencyNames = new String[] {};
		}
		List<File> dirs = new ArrayList<File>();
		dirs.add(new File("lib"));
		if (!platformDir.isEmpty())
			dirs.add(new File("lib/tesseract", platformDir));
		for (File dir : dirs) {
			for (String name : tesseractNames) {
				File lib = new File(dir, name);
				if (!lib.isFile())
					continue;
				// Dependencies must be loaded first so that the loader finds them
				for (String depName : dependencyNames) {
					File dep = new File(dir, depName);
					if (dep.isFile())
						System.load(dep.getAbsolutePath());
				}
				return (TessAPI) Native.loadLibrary(lib.getAbsolutePath(), TessAPI.class);
			}
		}
		// Fall back to a system-wide installation
		return (TessAPI) Native.loadLibrary("tesseract", TessAPI.class);
	}

	/** Returns the bundled Leptonica library, or null if it isn't there. */
	@Nullable
	static File findLeptonicaLibrary() {
		String platformDir;
		String name;
		if (Util.IS_WINDOWS) {
			platformDir = "win32-x86-64";
			name = "libleptonica1841.dll";
		}
		else if (Util.IS_LINUX) {
			platformDir = "linux-x86-64";
			name = "libleptonica-37b8efd2.so.6.0.0";
		}
		else {
			return null;
		}
		for (File dir : Arrays.asList(new File("lib"), new File("lib/tesseract", platformDir))) {
			File lib = new File(dir, name);
			if (lib.isFile())
				return lib;
		}
		return null;
	}

	/** Returns the folder containing the trained data, or null. */
	@Nullable
	public static File findTessdataDir() {
		for (File dir : Arrays.asList(new File("lib/tessdata"), new File("lib/tesseract/tessdata"))) {
			if (new File(dir, "eng.traineddata").isFile())
				return dir;
		}
		return null;
	}

	/** Returns the Tesseract version, or null if the library isn't available. */
	@Nullable
	public synchronized String getVersion() {
		return isAvailable() ? api.TessVersion() : null;
	}

	/**
	 * Recognizes the text in the given image. {@code dpi} is the resolution
	 * of the image (0 if unknown), which helps Tesseract estimate the font
	 * size. May take a few seconds per page.
	 *
	 * @throws IllegalStateException if the engine is not available
	 */
	@NotNull
	public synchronized Result recognize(@NotNull BufferedImage image, int dpi) {
		Util.checkNotNull(image);
		if (!isAvailable())
			throw new IllegalStateException(unavailableReason);
		int width = image.getWidth();
		int height = image.getHeight();
		ByteBuffer pixels = toGrayscale(image);
		try {
			api.TessBaseAPISetImage(handle, pixels, width, height, 1, width);
			api.TessBaseAPISetSourceResolution(handle, dpi > 0 ? dpi : RENDER_DPI);
			String text = readText(api.TessBaseAPIGetUTF8Text(handle));
			String tsv = readText(api.TessBaseAPIGetTsvText(handle, 0));
			int confidence = api.TessBaseAPIMeanTextConf(handle);
			return new Result(text, parseTsv(tsv), confidence);
		}
		finally {
			api.TessBaseAPIClear(handle);
		}
	}

	/**
	 * Converts the image into 8-bit grayscale pixels in a direct buffer, as
	 * expected by Tesseract.
	 */
	@NotNull
	private static ByteBuffer toGrayscale(@NotNull BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		ByteBuffer buffer = ByteBuffer.allocateDirect(width * height);
		int[] row = new int[width];
		byte[] gray = new byte[width];
		for (int y = 0; y < height; y++) {
			image.getRGB(0, y, width, 1, row, 0, width);
			for (int x = 0; x < width; x++) {
				int rgb = row[x];
				int r = (rgb >> 16) & 0xFF;
				int g = (rgb >> 8) & 0xFF;
				int b = rgb & 0xFF;
				gray[x] = (byte) ((r * 299 + g * 587 + b * 114) / 1000);
			}
			buffer.put(gray);
		}
		buffer.rewind();
		return buffer;
	}

	/** Reads a NUL-terminated UTF-8 string returned by Tesseract and frees it. */
	@NotNull
	private String readText(@Nullable Pointer text) {
		if (text == null)
			return "";
		try {
			long length = text.indexOf(0, (byte) 0);
			if (length <= 0)
				return "";
			byte[] bytes = text.getByteArray(0, (int) length);
			try {
				return new String(bytes, "UTF-8");
			}
			catch (UnsupportedEncodingException e) {
				throw new IllegalStateException(e);
			}
		}
		finally {
			api.TessDeleteText(text);
		}
	}

	/**
	 * Parses Tesseract's TSV output into words. Columns: level, page_num,
	 * block_num, par_num, line_num, word_num, left, top, width, height, conf,
	 * text. Level 5 rows are words.
	 */
	@NotNull
	private static List<Word> parseTsv(@NotNull String tsv) {
		List<Word> words = new ArrayList<Word>();
		for (String line : tsv.split("\n")) {
			String[] cols = line.split("\t", -1);
			if (cols.length < 12 || !cols[0].equals("5"))
				continue;
			String text = cols[11].trim();
			if (text.isEmpty())
				continue;
			try {
				words.add(new Word(text,
					Integer.parseInt(cols[6]), Integer.parseInt(cols[7]),
					Integer.parseInt(cols[8]), Integer.parseInt(cols[9]),
					parseConfidence(cols[10])));
			}
			catch (NumberFormatException e) {
				// Skip malformed row
			}
		}
		return words;
	}

	/**
	 * Parses a confidence value such as "93.301003". Tesseract formats it
	 * with the process locale, so the decimal separator may be a comma.
	 */
	private static int parseConfidence(@NotNull String value) {
		try {
			return (int) Float.parseFloat(value.replace(',', '.'));
		}
		catch (NumberFormatException e) {
			return -1;
		}
	}

	/** Releases the native resources. */
	public synchronized void dispose() {
		if (handle != null) {
			api.TessBaseAPIEnd(handle);
			api.TessBaseAPIDelete(handle);
			handle = null;
		}
	}

}
