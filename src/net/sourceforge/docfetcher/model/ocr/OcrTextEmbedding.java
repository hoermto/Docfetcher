/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.ocr;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import net.sourceforge.docfetcher.model.OwnFileChanges;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Stores recognized text inside PNG and JPEG files, so that the text stays
 * with the file and recognition doesn't have to be repeated: PNG files get
 * an iTXt chunk, JPEG files one or more COM segments, both marked with the
 * keyword {@link #KEYWORD}. The image data itself is left untouched, and the
 * file's dates (created, modified, accessed) are preserved.
 */
public final class OcrTextEmbedding {

	/** Keyword identifying the embedded text. */
	public static final String KEYWORD = "DocFetcherOCR";

	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final byte[] PNG_SIGNATURE = {
		(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
	private static final int JPEG_MAX_SEGMENT_DATA = 65533 - 2; // minus length field
	private static final byte[] JPEG_PREFIX = (KEYWORD + "\0").getBytes(UTF8);
	private static final byte[] JPEG_CONT_PREFIX = (KEYWORD + "+\0").getBytes(UTF8);

	private OcrTextEmbedding() {}

	/** Returns true if text can be embedded in files with the given extension. */
	public static boolean supports(@NotNull String extension) {
		String ext = extension.toLowerCase();
		return ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg");
	}

	/**
	 * Returns the text embedded in the given PNG or JPEG file, or null if
	 * there is none (or the file is not a PNG/JPEG file).
	 */
	@Nullable
	public static String read(@NotNull File file) throws IOException {
		byte[] data = Files.readAllBytes(file.toPath());
		if (isPng(data))
			return readPng(data);
		if (isJpeg(data))
			return readJpeg(data);
		return null;
	}

	/**
	 * Embeds the given text in the given PNG or JPEG file, replacing any
	 * previously embedded text. The dates of the file are kept.
	 * Returns false if the file is neither a PNG nor a JPEG file.
	 */
	public static boolean write(@NotNull File file, @NotNull String text)
			throws IOException {
		// Before reading, which changes the access date
		return write(file, text, FileDates.of(file));
	}

	/**
	 * Like {@link #write(File, String)}, with the dates the file is to keep,
	 * taken before the file was first read (the text recognition reads it).
	 */
	static boolean write(@NotNull File file, @NotNull String text, @Nullable FileDates dates)
			throws IOException {
		byte[] data = Files.readAllBytes(file.toPath());
		byte[] out;
		if (isPng(data))
			out = writePng(data, text);
		else if (isJpeg(data))
			out = writeJpeg(data, text);
		else
			return false;
		replaceFile(file, out, dates);
		return true;
	}

	/** Writes the bytes to the file via a temporary file, then gives it the dates. */
	static void replaceFile(@NotNull File file, @NotNull byte[] data, @Nullable FileDates dates)
			throws IOException {
		File temp = new File(file.getParentFile(), file.getName() + OwnFileChanges.TEMP_SUFFIX);
		Files.write(temp.toPath(), data);
		// Our own change: the folder watcher shouldn't start another update for it
		OwnFileChanges.begin(file);
		try {
			Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			if (dates != null)
				dates.applyTo(file);
		}
		finally {
			temp.delete();
			OwnFileChanges.end(file);
		}
	}

	private static boolean isPng(@NotNull byte[] data) {
		if (data.length < PNG_SIGNATURE.length)
			return false;
		for (int i = 0; i < PNG_SIGNATURE.length; i++)
			if (data[i] != PNG_SIGNATURE[i])
				return false;
		return true;
	}

	private static boolean isJpeg(@NotNull byte[] data) {
		return data.length > 4 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8;
	}

	// ---- PNG: chunks of length(4) type(4) data crc(4) after the signature

	@Nullable
	private static String readPng(@NotNull byte[] data) {
		int pos = PNG_SIGNATURE.length;
		while (pos + 8 <= data.length) {
			int length = readInt(data, pos);
			String type = new String(data, pos + 4, 4, UTF8);
			int dataStart = pos + 8;
			if (length < 0 || dataStart + length + 4 > data.length)
				break;
			if (type.equals("iTXt") || type.equals("tEXt")) {
				String text = decodePngText(data, dataStart, length, type.equals("iTXt"));
				if (text != null)
					return text;
			}
			if (type.equals("IEND"))
				break;
			pos = dataStart + length + 4;
		}
		return null;
	}

	/** Returns the text of a text chunk if it carries our keyword, else null. */
	@Nullable
	private static String decodePngText(@NotNull byte[] data, int start, int length, boolean international) {
		int end = start + length;
		int nul = indexOf(data, (byte) 0, start, end);
		if (nul < 0)
			return null;
		String keyword = new String(data, start, nul - start, Charset.forName("ISO-8859-1"));
		if (!keyword.equals(KEYWORD))
			return null;
		if (!international)
			return new String(data, nul + 1, end - nul - 1, Charset.forName("ISO-8859-1"));
		// iTXt: keyword\0 compression flag(1) method(1) language\0 translated\0 text
		int p = nul + 1;
		if (p + 2 > end)
			return null;
		boolean compressed = data[p] != 0;
		p += 2;
		int langEnd = indexOf(data, (byte) 0, p, end);
		if (langEnd < 0)
			return null;
		int transEnd = indexOf(data, (byte) 0, langEnd + 1, end);
		if (transEnd < 0)
			return null;
		if (compressed)
			return null; // We never write compressed chunks
		return new String(data, transEnd + 1, end - transEnd - 1, UTF8);
	}

	@NotNull
	private static byte[] writePng(@NotNull byte[] data, @NotNull String text) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + text.length() + 64);
		out.write(data, 0, PNG_SIGNATURE.length);
		int pos = PNG_SIGNATURE.length;
		boolean inserted = false;
		while (pos + 8 <= data.length) {
			int length = readInt(data, pos);
			String type = new String(data, pos + 4, 4, UTF8);
			int dataStart = pos + 8;
			if (length < 0 || dataStart + length + 4 > data.length)
				throw new IOException("Malformed PNG file");
			int chunkEnd = dataStart + length + 4;
			boolean ours = (type.equals("iTXt") || type.equals("tEXt"))
				&& decodePngText(data, dataStart, length, type.equals("iTXt")) != null;
			if (!ours)
				out.write(data, pos, chunkEnd - pos);
			if (type.equals("IHDR") && !inserted) {
				writePngTextChunk(out, text);
				inserted = true;
			}
			pos = chunkEnd;
			if (type.equals("IEND"))
				break;
		}
		if (!inserted)
			throw new IOException("Malformed PNG file: no IHDR chunk");
		return out.toByteArray();
	}

	private static void writePngTextChunk(@NotNull ByteArrayOutputStream out, @NotNull String text) throws IOException {
		ByteArrayOutputStream chunk = new ByteArrayOutputStream();
		chunk.write("iTXt".getBytes(UTF8));
		chunk.write(KEYWORD.getBytes(UTF8));
		chunk.write(0); // keyword terminator
		chunk.write(0); // compression flag: uncompressed
		chunk.write(0); // compression method
		chunk.write(0); // language tag (empty) terminator
		chunk.write(0); // translated keyword (empty) terminator
		chunk.write(text.getBytes(UTF8));
		byte[] typeAndData = chunk.toByteArray();
		CRC32 crc = new CRC32();
		crc.update(typeAndData);
		writeInt(out, typeAndData.length - 4);
		out.write(typeAndData);
		writeInt(out, (int) crc.getValue());
	}

	// ---- JPEG: marker segments FFxx length(2) data, up to SOS (FFDA)

	@Nullable
	private static String readJpeg(@NotNull byte[] data) {
		List<byte[]> parts = new ArrayList<byte[]>();
		int pos = 2;
		while (pos + 4 <= data.length) {
			if ((data[pos] & 0xFF) != 0xFF)
				break;
			int marker = data[pos + 1] & 0xFF;
			if (marker == 0xD8 || (marker >= 0xD0 && marker <= 0xD7) || marker == 0x01) {
				pos += 2; // Standalone marker
				continue;
			}
			if (marker == 0xDA || marker == 0xD9)
				break; // Start of scan / end of image
			int length = ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
			int dataStart = pos + 4;
			int dataEnd = pos + 2 + length;
			if (length < 2 || dataEnd > data.length)
				break;
			if (marker == 0xFE) {
				if (startsWith(data, dataStart, dataEnd, JPEG_PREFIX)) {
					parts.clear();
					parts.add(slice(data, dataStart + JPEG_PREFIX.length, dataEnd));
				}
				else if (startsWith(data, dataStart, dataEnd, JPEG_CONT_PREFIX) && !parts.isEmpty()) {
					parts.add(slice(data, dataStart + JPEG_CONT_PREFIX.length, dataEnd));
				}
			}
			pos = dataEnd;
		}
		if (parts.isEmpty())
			return null;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] part : parts)
			out.write(part, 0, part.length);
		return new String(out.toByteArray(), UTF8);
	}

	@NotNull
	private static byte[] writeJpeg(@NotNull byte[] data, @NotNull String text) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + text.length() + 64);
		out.write(data, 0, 2); // SOI
		int pos = 2;
		boolean inserted = false;
		while (pos + 4 <= data.length) {
			if ((data[pos] & 0xFF) != 0xFF)
				throw new IOException("Malformed JPEG file");
			int marker = data[pos + 1] & 0xFF;
			if (marker == 0xD8 || (marker >= 0xD0 && marker <= 0xD7) || marker == 0x01) {
				out.write(data, pos, 2);
				pos += 2;
				continue;
			}
			boolean app = marker >= 0xE0 && marker <= 0xEF;
			if (!app && !inserted) {
				// After the APPn segments (JFIF/Exif), before everything else
				writeJpegComments(out, text);
				inserted = true;
			}
			if (marker == 0xDA || marker == 0xD9) {
				// Copy the rest of the file unchanged
				out.write(data, pos, data.length - pos);
				return out.toByteArray();
			}
			int length = ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
			int dataStart = pos + 4;
			int dataEnd = pos + 2 + length;
			if (length < 2 || dataEnd > data.length)
				throw new IOException("Malformed JPEG file");
			boolean ours = marker == 0xFE
				&& (startsWith(data, dataStart, dataEnd, JPEG_PREFIX)
					|| startsWith(data, dataStart, dataEnd, JPEG_CONT_PREFIX));
			if (!ours)
				out.write(data, pos, dataEnd - pos);
			pos = dataEnd;
		}
		throw new IOException("Malformed JPEG file: no start of scan");
	}

	private static void writeJpegComments(@NotNull ByteArrayOutputStream out, @NotNull String text) throws IOException {
		byte[] bytes = text.getBytes(UTF8);
		int offset = 0;
		boolean first = true;
		do {
			byte[] prefix = first ? JPEG_PREFIX : JPEG_CONT_PREFIX;
			int chunk = Math.min(bytes.length - offset, JPEG_MAX_SEGMENT_DATA - prefix.length);
			int length = 2 + prefix.length + chunk;
			out.write(0xFF);
			out.write(0xFE);
			out.write((length >> 8) & 0xFF);
			out.write(length & 0xFF);
			out.write(prefix);
			out.write(bytes, offset, chunk);
			offset += chunk;
			first = false;
		} while (offset < bytes.length);
	}

	// ---- Helpers

	private static int readInt(@NotNull byte[] data, int pos) {
		return ((data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
			| ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
	}

	private static void writeInt(@NotNull ByteArrayOutputStream out, int value) {
		out.write((value >> 24) & 0xFF);
		out.write((value >> 16) & 0xFF);
		out.write((value >> 8) & 0xFF);
		out.write(value & 0xFF);
	}

	private static int indexOf(@NotNull byte[] data, byte value, int from, int to) {
		for (int i = from; i < to; i++)
			if (data[i] == value)
				return i;
		return -1;
	}

	private static boolean startsWith(@NotNull byte[] data, int from, int to, @NotNull byte[] prefix) {
		if (to - from < prefix.length)
			return false;
		for (int i = 0; i < prefix.length; i++)
			if (data[from + i] != prefix[i])
				return false;
		return true;
	}

	@NotNull
	private static byte[] slice(@NotNull byte[] data, int from, int to) {
		byte[] result = new byte[Math.max(0, to - from)];
		System.arraycopy(data, from, result, 0, result.length);
		return result;
	}

}
