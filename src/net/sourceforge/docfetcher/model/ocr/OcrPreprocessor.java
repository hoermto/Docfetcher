/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.ocr;

import java.io.File;
import java.util.List;

import de.schlichtherle.truezip.file.TFile;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Runs text recognition on files before they are indexed, and (unless
 * {@link SettingsConf.Bool#StoreOcrTextInFiles} is off) stores the result in
 * the files so that it doesn't have to be repeated:
 * <ul>
 * <li>PDF files without text get an invisible text layer,</li>
 * <li>TIFF files are converted into PDF files with a text layer if enabled
 * in the settings (the TIFF file is deleted or renamed to '.ocr'),</li>
 * <li>PNG and JPEG files get the text embedded in their metadata.</li>
 * </ul>
 * Files inside archives are left alone. Any failure is logged and leaves
 * the file unchanged; the parsers then work with what the file contains.
 */
public final class OcrPreprocessor {

	private OcrPreprocessor() {}

	/** Returns true if recognition is switched on and the engine works. */
	public static boolean isEnabled() {
		return SettingsConf.Bool.OcrEnabled.get()
			&& OcrEngine.getInstance().isAvailable();
	}

	/**
	 * Prepares the given file for indexing (see the class comment) and
	 * returns the file to index: the PDF created from a TIFF file, otherwise
	 * the given file itself.
	 */
	@NotNull
	public static File prepare(@NotNull File file, @NotNull Cancelable cancelable) {
		Util.checkNotNull(file, cancelable);
		KNOWN_TEXT.remove();
		if (!isEnabled() || !file.isFile())
			return file;
		// Without this option the files are left untouched; the parsers
		// recognize the text on the fly and it goes into the index only
		if (!SettingsConf.Bool.StoreOcrTextInFiles.get())
			return file;
		if (file instanceof TFile && ((TFile) file).isEntry())
			return file;
		// TrueZIP files don't support java.nio, so work with a plain file
		File plain = new File(file.getPath());
		String ext = Util.getExtension(file.getName()).toLowerCase();
		OcrEngine engine = OcrEngine.getInstance();
		try {
			if (ext.equals("pdf")) {
				// A copy recognized earlier replaces the file once it is free
				if (PendingOcr.has(plain))
					PendingOcr.apply(plain);
				else
					SearchablePdf.ocr(plain, engine, cancelable);
				// Still open in another program: the index gets the text of the copy
				if (PendingOcr.has(plain))
					KNOWN_TEXT.set(PendingOcr.getText(plain));
			}
			else if (ext.equals("tif") || ext.equals("tiff")) {
				// Without conversion, TIFF files are recognized at parse time
				if (SettingsConf.Bool.ConvertTiffToPdf.get()) {
					File pdf = SearchablePdf.convertTiff(plain, engine, cancelable,
						SettingsConf.Bool.KeepTiffAfterConversion.get());
					if (pdf != null)
						return pdf;
				}
			}
			else if (OcrTextEmbedding.supports(ext)) {
				// The dates before reading the file, which changes the access date
				FileDates dates = FileDates.of(plain);
				if (OcrTextEmbedding.read(plain) == null) {
					String text = recognize(plain, engine, cancelable);
					if (text != null)
						OcrTextEmbedding.write(plain, text, dates);
				}
			}
		}
		catch (Throwable e) {
			// IOException, OutOfMemoryError for huge images, etc.
			Util.printErr("Text recognition failed for " + file + ": " + e);
		}
		return file;
	}

	/**
	 * Recognizes the text of a PDF document that contains no text, without
	 * modifying it. Returns an empty string if recognition is switched off,
	 * null if it was cancelled.
	 */
	@Nullable
	public static String recognizePdf(	@NotNull org.apache.pdfbox.pdmodel.PDDocument doc,
										@NotNull Cancelable cancelable) throws java.io.IOException {
		if (!isEnabled())
			return "";
		String known = KNOWN_TEXT.get();
		if (known != null) {
			KNOWN_TEXT.remove();
			return known;
		}
		return SearchablePdf.recognizeText(doc, OcrEngine.getInstance(), cancelable);
	}

	/**
	 * The text of a recognized copy waiting for its file (see PendingOcr),
	 * set by prepare for the parsing of the same file that follows in the
	 * same thread, so that the text isn't recognized again.
	 */
	private static final ThreadLocal<String> KNOWN_TEXT = new ThreadLocal<String>();

	/**
	 * Recognizes the text of all pages of an image file. Returns null if
	 * cancelled.
	 */
	@Nullable
	public static String recognize(	@NotNull File imageFile,
									@NotNull OcrEngine engine,
									@NotNull Cancelable cancelable) throws java.io.IOException {
		List<ImagePages.Page> pages = ImagePages.read(imageFile);
		StringBuilder sb = new StringBuilder();
		for (ImagePages.Page page : pages) {
			if (cancelable.isCanceled())
				return null;
			if (sb.length() > 0)
				sb.append("\n\n");
			sb.append(engine.recognize(page.image, page.dpi).text.trim());
		}
		return sb.toString();
	}

}
