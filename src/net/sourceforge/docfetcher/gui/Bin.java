/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilFile;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * The DocFetcher bin: a folder (preferences, tab Folders) into which deleted
 * files and folders are moved, side by side under their names (with -2, -3
 * if a name is taken). A hidden list in the bin remembers where each one came
 * from, so that it can be restored there; what has no entry (e.g. put there
 * by hand) is restored into an inbox folder. Removing something from the bin
 * moves it to the recycle bin of the system, a second safety net. Without a
 * bin folder, deleted files go to the recycle bin of the system as before.
 */
public final class Bin {

	/** The list of the original locations, in the bin folder. */
	static final String ORIGINS = ".docfetcher-bin.properties";

	private Bin() {}

	/** Returns the configured bin folder, or null if there is none. */
	@Nullable
	public static File getFolder() {
		String path = SettingsConf.Str.BinFolder.get().trim();
		return path.isEmpty() ? null : Util.getCanonicalFile(path);
	}

	/** Returns true if the file is in the bin, or is the bin folder itself. */
	public static boolean contains(@NotNull File file) {
		return contains(getFolder(), file);
	}

	public static boolean contains(@Nullable File bin, @NotNull File file) {
		return bin != null && (Util.isCanonicallyEqual(bin, file) || Util.contains(bin, file));
	}

	/**
	 * Deletes the file or folder: into the bin if there is one; what is in
	 * the bin already, and everything without a bin, goes to the recycle bin
	 * of the system (or is deleted for good on a system without one). Returns
	 * where the file is now - in the bin, or the given file if it went to the
	 * recycle bin - or null on failure.
	 */
	@Nullable
	public static File delete(@NotNull File file) {
		File bin = getFolder();
		if (bin == null || contains(bin, file)) {
			if (!UtilFile.delete(file))
				return null;
			if (bin != null)
				removeMissing(bin);
			return file;
		}
		try {
			return moveIn(bin, file);
		}
		catch (IOException e) {
			Util.printErr("Moving " + file + " to the bin failed: " + e);
			return null;
		}
	}

	/**
	 * Moves the file or folder into the bin under a free name, remembers where
	 * it came from and returns its new place.
	 */
	@NotNull
	static synchronized File moveIn(@NotNull File bin, @NotNull File file) throws IOException {
		if (!bin.isDirectory() && !bin.mkdirs())
			throw new IOException("The bin folder can't be created: " + bin);
		File origin = Util.getCanonicalFile(file);
		File target = UtilFile.getFreeFile(new File(bin, origin.getName()));
		UtilFile.move(origin, target);
		Properties origins = load(bin);
		origins.setProperty(target.getName(), origin.getPath());
		save(bin, origins);
		return target;
	}

	/**
	 * Returns where the file in the bin came from, or null if that isn't
	 * known. For a file inside a deleted folder, that is the place inside
	 * the original folder.
	 */
	@Nullable
	public static synchronized File getOrigin(@NotNull File bin, @NotNull File file) {
		File entry = getEntry(bin, file);
		if (entry == null)
			return null;
		String origin = load(bin).getProperty(entry.getName());
		if (origin == null)
			return null;
		String rest = Util.getCanonicalFile(file).getPath().substring(entry.getPath().length());
		return rest.isEmpty() ? new File(origin) : new File(origin + rest);
	}

	/**
	 * Returns the file in the bin that came from the given place, or null if
	 * there is none, e.g. for an original removed after a merge.
	 */
	@Nullable
	public static synchronized File find(@NotNull File bin, @NotNull File origin) {
		String path = Util.getCanonicalFile(origin).getPath();
		Properties origins = load(bin);
		for (String name : origins.stringPropertyNames()) {
			File entry = new File(bin, name);
			if (path.equals(origins.getProperty(name)) && entry.exists())
				return entry;
		}
		return null;
	}

	/**
	 * Moves the file or folder from the bin to where it came from, or into the
	 * given folder if it isn't null, under a free name; returns its new
	 * place. Missing parent folders are created again.
	 */
	@NotNull
	public static synchronized File restore(@NotNull File bin,
											@NotNull File file,
											@Nullable File folder) throws IOException {
		File target = folder != null ? new File(folder, file.getName()) : getOrigin(bin, file);
		if (target == null)
			throw new IOException("Unknown origin: " + file);
		target = UtilFile.getFreeFile(target);
		File parent = target.getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs())
			throw new IOException("The folder can't be created: " + parent);
		UtilFile.move(file, target);
		removeMissing(bin);
		return target;
	}

	/**
	 * Moves everything in the bin to the recycle bin of the system and
	 * returns what couldn't be removed.
	 */
	@NotNull
	public static synchronized List<File> empty(@NotNull File bin) {
		List<File> failed = new ArrayList<File>();
		for (File entry : getEntries(bin))
			if (!UtilFile.delete(entry))
				failed.add(entry);
		removeMissing(bin);
		return failed;
	}

	/** Returns the files and folders in the bin, without the list of origins. */
	@NotNull
	public static List<File> getEntries(@NotNull File bin) {
		List<File> entries = new ArrayList<File>();
		File[] children = bin.listFiles();
		if (children != null)
			for (File child : children)
				if (!child.getName().equals(ORIGINS))
					entries.add(child);
		return entries;
	}

	/** Returns the number of bytes of all files in the bin. */
	public static long getSize(@NotNull File bin) {
		long size = 0;
		for (File entry : getEntries(bin))
			size += sizeOf(entry);
		return size;
	}

	private static long sizeOf(@NotNull File file) {
		File[] children = file.listFiles();
		if (children == null)
			return file.length();
		long size = 0;
		for (File child : children)
			size += sizeOf(child);
		return size;
	}

	/** The file or folder directly in the bin that holds the given file. */
	@Nullable
	private static File getEntry(@NotNull File bin, @NotNull File file) {
		File canonicalBin = Util.getCanonicalFile(bin);
		for (File f = Util.getCanonicalFile(file); f != null; f = f.getParentFile())
			if (canonicalBin.equals(f.getParentFile()))
				return f;
		return null;
	}

	/** Forgets the origins of files that aren't in the bin any more. */
	private static synchronized void removeMissing(@NotNull File bin) {
		Properties origins = load(bin);
		boolean changed = false;
		for (String name : origins.stringPropertyNames()) {
			if (!new File(bin, name).exists()) {
				origins.remove(name);
				changed = true;
			}
		}
		if (changed) {
			try {
				save(bin, origins);
			}
			catch (IOException e) {
				Util.printErr(e);
			}
		}
	}

	@NotNull
	private static Properties load(@NotNull File bin) {
		Properties origins = new Properties();
		File file = new File(bin, ORIGINS);
		if (!file.isFile())
			return origins;
		try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			origins.load(reader);
		}
		catch (IOException e) {
			Util.printErr(e);
		}
		return origins;
	}

	private static void save(@NotNull File bin, @NotNull Properties origins) throws IOException {
		File file = new File(bin, ORIGINS);
		// Opens an existing file without replacing it, which Windows refuses for a hidden file
		try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
			origins.store(writer, "Where the files in the DocFetcher bin came from");
		}
		try {
			Files.setAttribute(file.toPath(), "dos:hidden", true);
		}
		catch (Exception e) {
			// Not on Windows: the leading dot hides the file
		}
	}

}
