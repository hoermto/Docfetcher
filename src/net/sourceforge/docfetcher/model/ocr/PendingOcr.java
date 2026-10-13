/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.ocr;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Properties;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import net.sourceforge.docfetcher.model.pdf.PdfFiles;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Recognized PDF files whose text layer couldn't be written into the file
 * yet, because another program had the file open (e.g. a PDF viewer): the
 * finished copy with the text layer waits in a folder of its own and
 * replaces the file on a later index update, once the file is free.
 * Meanwhile the index gets the text of the copy, so the text isn't
 * recognized a second time. If the file is changed or deleted meanwhile,
 * the copy is dropped.
 */
public final class PendingOcr {

	/** How often, and how long apart, replacing a file is tried before the copy waits. */
	static int attempts = 10;
	static long pauseMillis = 300;

	private static volatile File dir = new File(System.getProperty("java.io.tmpdir"), "docfetcher-ocr-pending");

	private PendingOcr() {}

	/** Sets the folder for the waiting copies (in the app data) and drops copies of files that are gone. */
	public static synchronized void setDir(@NotNull File folder) {
		dir = folder;
		File[] entries = folder.listFiles();
		if (entries == null)
			return;
		for (File entry : entries) {
			if (!entry.getName().endsWith(".properties"))
				continue;
			Properties props = load(entry);
			String path = props.getProperty("path");
			if (path == null || !isUnchanged(new File(path), props))
				remove(entry.getName().replace(".properties", ""));
		}
	}

	/**
	 * Replaces the file with the recognized copy, keeping its dates; tries
	 * again for a few seconds while the file is open in another program.
	 * Returns false (and the copy is kept for later) if it stays open.
	 */
	public static boolean replace(@NotNull File recognized, @NotNull File original) throws IOException {
		return replace(recognized, original, FileDates.of(original));
	}

	/** Like {@link #replace(File, File)}, with the dates the file gets (taken before it was read). */
	static boolean replace(	@NotNull File recognized,
							@NotNull File original,
							@Nullable FileDates dates) throws IOException {
		IOException last = null;
		for (int i = 0; i < attempts; i++) {
			try {
				Files.move(recognized.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING);
				if (dates != null)
					dates.applyTo(original);
				return true;
			}
			catch (java.nio.file.FileSystemException e) {
				last = e; // Open in another program
			}
			try {
				Thread.sleep(pauseMillis);
			}
			catch (InterruptedException e) {
				break;
			}
		}
		Util.printErr("The text layer can't be saved yet, the file is open in another program: "
			+ original + (last == null ? "" : " (" + last + ")"));
		return false;
	}

	/** Parks the recognized copy of the original until the original can be replaced. */
	public static synchronized void add(@NotNull File original, @NotNull File recognized) throws IOException {
		String key = key(original);
		dir.mkdirs();
		Files.move(recognized.toPath(), pdfOf(key).toPath(), StandardCopyOption.REPLACE_EXISTING);
		Properties props = new Properties();
		props.setProperty("path", canonical(original).getPath());
		props.setProperty("lastModified", String.valueOf(original.lastModified()));
		props.setProperty("length", String.valueOf(original.length()));
		try (Writer writer = Files.newBufferedWriter(propsOf(key).toPath(), StandardCharsets.UTF_8)) {
			props.store(writer, "A recognized copy waits until the file is free");
		}
	}

	/** Returns true if a recognized copy waits for the file (which is unchanged since). */
	public static synchronized boolean has(@NotNull File original) {
		String key = key(original);
		File propsFile = propsOf(key);
		if (!propsFile.isFile())
			return false;
		if (isUnchanged(canonical(original), load(propsFile)) && pdfOf(key).isFile())
			return true;
		remove(key); // Changed or deleted meanwhile: the copy is outdated
		return false;
	}

	/**
	 * Puts the waiting copy in place of the file if it is free now; returns
	 * true if it was replaced (or there was nothing to do).
	 */
	public static synchronized boolean apply(@NotNull File original) throws IOException {
		if (!has(original))
			return true;
		String key = key(original);
		if (!replace(pdfOf(key), canonical(original)))
			return false;
		remove(key);
		return true;
	}

	/** The text of the waiting copy, for the index; null without one. */
	@Nullable
	public static synchronized String getText(@NotNull File original) {
		if (!has(original))
			return null;
		try (PDDocument doc = PdfFiles.load(pdfOf(key(original)))) {
			return new PDFTextStripper().getText(doc);
		}
		catch (IOException e) {
			return null;
		}
	}

	private static boolean isUnchanged(@NotNull File original, @NotNull Properties props) {
		return original.isFile()
			&& String.valueOf(original.lastModified()).equals(props.getProperty("lastModified"))
			&& String.valueOf(original.length()).equals(props.getProperty("length"));
	}

	private static void remove(@NotNull String key) {
		pdfOf(key).delete();
		propsOf(key).delete();
	}

	@NotNull
	private static Properties load(@NotNull File propsFile) {
		Properties props = new Properties();
		try (Reader reader = Files.newBufferedReader(propsFile.toPath(), StandardCharsets.UTF_8)) {
			props.load(reader);
		}
		catch (IOException e) {
			// An unreadable entry counts as outdated
		}
		return props;
	}

	@NotNull
	private static File pdfOf(@NotNull String key) {
		return new File(dir, key + ".pdf");
	}

	@NotNull
	private static File propsOf(@NotNull String key) {
		return new File(dir, key + ".properties");
	}

	@NotNull
	private static File canonical(@NotNull File file) {
		return Util.getCanonicalFile(new File(file.getPath())); // Also for TrueZIP files
	}

	/** A name for the copy of the file: a hash of its path. */
	@NotNull
	private static String key(@NotNull File original) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(
				canonical(original).getPath().getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 12; i++)
				sb.append(String.format("%02x", hash[i]));
			return sb.toString();
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
