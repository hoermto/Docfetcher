/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.parse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;

import com.google.common.io.Closeables;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.model.ocr.OcrEngine;
import net.sourceforge.docfetcher.model.ocr.OcrPreprocessor;
import net.sourceforge.docfetcher.model.ocr.OcrTextEmbedding;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.UtilFile;

/**
 * Parser for image files, e.g. scanned documents. The text is the text
 * recognized by OCR: either the text embedded in the file by an earlier
 * recognition (see {@link OcrTextEmbedding}) or freshly recognized. JPEG
 * files additionally contribute their Exif metadata.
 */
public final class ImageParser extends FileParser {

	private static final Collection<String> extensions = Arrays.asList(
		"png", "jpg", "jpeg", "gif", "bmp", "tif", "tiff");

	private static final Collection<String> types = Arrays.asList(
		"image/png", "image/jpeg", "image/jpg", "image/gif", "image/bmp",
		"image/tiff");

	private final ExifParser exifParser = new ExifParser();

	ImageParser() {}

	@Override
	protected ParseResult parse(@NotNull File file, @NotNull ParseContext context)
			throws ParseException {
		StringBuilder sb = new StringBuilder();
		String ocrText = getOcrText(file, context.getCancelable(), context.isOcrAllowed());
		if (ocrText != null)
			sb.append(ocrText);
		else if (!context.isOcrAllowed() && OcrPreprocessor.isEnabled())
			context.setOcrSkipped(true); // Recognized later in the deferred pass
		if (isJpeg(context.getFilename())) {
			String exif = getExifText(file, context);
			if (exif != null && !exif.isEmpty())
				sb.append("\n\n").append(exif);
		}
		return new ParseResult(sb.toString());
	}

	@Override
	protected String renderText(@NotNull File file, @NotNull String filename)
			throws ParseException {
		String ocrText = getOcrText(file, Cancelable.nullCancelable, true);
		return ocrText == null ? "" : ocrText;
	}

	/**
	 * Returns the embedded text, or recognizes the text if recognition is
	 * enabled. Returns null if no text is available.
	 */
	@Nullable
	private static String getOcrText(	@NotNull File file,
										@NotNull Cancelable cancelable,
										boolean ocrAllowed) throws ParseException {
		file = new File(file.getPath()); // TrueZIP files don't support java.nio
		try {
			if (OcrTextEmbedding.supports(Util.getExtension(file.getName()))) {
				String embedded = OcrTextEmbedding.read(file);
				if (embedded != null)
					return embedded;
			}
			if (!ocrAllowed || !OcrPreprocessor.isEnabled())
				return null;
			return OcrPreprocessor.recognize(file, OcrEngine.getInstance(), cancelable);
		}
		catch (IOException e) {
			throw new ParseException(e);
		}
		catch (OutOfMemoryError e) {
			throw new ParseException(new IOException(e.toString()));
		}
		catch (RuntimeException e) {
			// Decoder bugs etc. must not abort the indexing of other files
			throw new ParseException(new IOException(Msg.ocr_failed.format(e.toString())));
		}
		catch (LinkageError e) {
			throw new ParseException(new IOException(Msg.ocr_failed.format(e.toString())));
		}
	}

	@Nullable
	private String getExifText(@NotNull File file, @NotNull ParseContext context) {
		InputStream in = null;
		try {
			in = UtilFile.openForReading(file);
			return exifParser.parse(in, context).getContent().toString();
		}
		catch (Exception e) {
			return null; // Exif data is optional
		}
		finally {
			Closeables.closeQuietly(in);
		}
	}

	private static boolean isJpeg(@NotNull String filename) {
		String ext = Util.getExtension(filename).toLowerCase();
		return ext.equals("jpg") || ext.equals("jpeg");
	}

	@Override
	protected Collection<String> getExtensions() {
		return extensions;
	}

	@Override
	protected Collection<String> getTypes() {
		return types;
	}

	@Override
	public String getTypeLabel() {
		return Msg.filetype_image.get();
	}

}
