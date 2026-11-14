/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.util;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * File operations that need platform support, such as moving a file to the
 * recycle bin.
 */
public final class UtilFile {

	private UtilFile() {}
	
	private static volatile boolean trashDisabled = false;
	
	/** Lets tests delete for good, so that they don't fill the recycle bin. */
	public static void setTrashDisabled(boolean trashDisabled) {
		UtilFile.trashDisabled = trashDisabled;
	}

	/**
	 * Returns true if deleted files can be moved to the recycle bin (or to
	 * the desktop trash on Linux) instead of being deleted for good.
	 */
	public static boolean isTrashSupported() {
		if (trashDisabled)
			return false;
		try {
			return Desktop.isDesktopSupported()
				&& Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
		}
		catch (Throwable e) {
			// Headless environment, missing AWT libraries, etc.
			return false;
		}
	}

	/**
	 * Moves the given file or folder to the recycle bin, or deletes it for
	 * good if the system has no recycle bin. Folders are deleted with their
	 * contents. Returns true on success.
	 */
	public static boolean delete(@NotNull File file) {
		Util.checkNotNull(file);
		if (isTrashSupported()) {
			try {
				if (Desktop.getDesktop().moveToTrash(file))
					return true;
			}
			catch (Throwable e) {
				Util.printErr("Moving " + file + " to the trash failed: " + e);
			}
		}
		return deletePermanently(file);
	}

	/**
	 * Moves the given file or directory to the given target. If a simple
	 * rename is not possible (e.g. because source and target are on different
	 * drives), the source is copied recursively and then deleted.
	 */
	public static void move(@NotNull File source, @NotNull File target)
			throws IOException {
		Path srcPath = source.toPath();
		final Path dstPath = target.toPath();
		try {
			Files.move(srcPath, dstPath, StandardCopyOption.ATOMIC_MOVE);
			return;
		}
		catch (IOException e) {
			// Fall through to copy + delete
		}
		if (source.isFile()) {
			Files.copy(srcPath, dstPath);
		}
		else {
			final Path srcRoot = srcPath;
			Files.walkFileTree(srcRoot, new SimpleFileVisitor<Path>() {
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
						throws IOException {
					Files.createDirectories(dstPath.resolve(srcRoot.relativize(dir)));
					return FileVisitResult.CONTINUE;
				}
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
						throws IOException {
					Files.copy(file, dstPath.resolve(srcRoot.relativize(file)));
					return FileVisitResult.CONTINUE;
				}
			});
		}
		Util.deleteRecursively(source);
	}

	/**
	 * Opens the file for reading in a way that doesn't lock it: on Windows,
	 * a file opened with java.io can't be moved, renamed or deleted until it
	 * is closed, which java.nio allows. Use this instead of FileInputStream
	 * for the user's documents.
	 */
	@NotNull
	public static java.io.InputStream openForReading(@NotNull File file) throws java.io.FileNotFoundException {
		try {
			// A plain path: TrueZIP files (TFile) don't support java.nio
			return new java.io.BufferedInputStream(Files.newInputStream(new File(file.getPath()).toPath()));
		}
		catch (IOException e) {
			java.io.FileNotFoundException notFound = new java.io.FileNotFoundException(file.getPath() + ": " + e);
			notFound.initCause(e);
			throw notFound;
		}
		catch (java.nio.file.InvalidPathException e) {
			throw new java.io.FileNotFoundException(file.getPath() + ": " + e.getMessage());
		}
	}

	/** File system types of network shares outside Windows (FileStore.type()). */
	private static final java.util.Set<String> NETWORK_TYPES = new java.util.HashSet<String>(java.util.Arrays.asList(
		"nfs", "nfs4", "cifs", "smb", "smbfs", "smb2", "smb3", "afpfs", "webdav", "davfs", "fuse.sshfs", "sshfs", "9p"));

	/**
	 * Returns true if the file is on a network drive (a mapped drive or a UNC
	 * path on Windows, an NFS/SMB/AFP/... mount elsewhere), whose changes
	 * the operating system usually doesn't report reliably.
	 */
	public static boolean isNetworkDrive(@NotNull File file) {
		String path = file.getAbsolutePath();
		if (Util.IS_WINDOWS) {
			if (path.startsWith("\\\\"))
				return true; // UNC path, \\server\share
			if (path.length() < 2 || path.charAt(1) != ':')
				return false;
			try {
				int type = com.sun.jna.platform.win32.Kernel32.INSTANCE.GetDriveType(path.substring(0, 2) + "\\");
				return type == 4; // DRIVE_REMOTE
			}
			catch (Throwable e) {
				return false;
			}
		}
		try {
			File existing = file.getAbsoluteFile();
			while (existing != null && !existing.exists())
				existing = existing.getParentFile();
			if (existing == null)
				return false;
			String type = Files.getFileStore(existing.toPath()).type().toLowerCase(java.util.Locale.ROOT);
			return NETWORK_TYPES.contains(type);
		}
		catch (Throwable e) {
			return false;
		}
	}

	/** Returns the size for people: '512 KB', '12.3 MB', '1.2 GB'. */
	@NotNull
	public static String formatSize(long bytes) {
		double kb = bytes / 1024.0;
		if (kb < 1000)
			return Math.round(Math.ceil(kb)) + " KB";
		if (kb < 1000 * 1024)
			return String.format("%.1f MB", kb / 1024);
		return String.format("%.1f GB", kb / 1024 / 1024);
	}

	/**
	 * Returns the given file if it doesn't exist, otherwise the first free
	 * file with a number added to the name: {@code name-2.pdf},
	 * {@code name-3.pdf}, ...
	 */
	@NotNull
	public static File getFreeFile(@NotNull File file) {
		if (!file.exists())
			return file;
		String[] parts = Util.splitFilename(file.getName());
		String ext = parts[1].isEmpty() ? "" : "." + parts[1];
		for (int i = 2; ; i++) {
			File candidate = new File(file.getParentFile(), parts[0] + "-" + i + ext);
			if (!candidate.exists())
				return candidate;
		}
	}

	/**
	 * Deletes the given file or folder without using the recycle bin; a
	 * folder is deleted with its contents. Returns true on success.
	 */
	public static boolean deletePermanently(@NotNull File file) {
		Util.checkNotNull(file);
		File[] children = file.listFiles(); // null for files and on errors
		if (children != null)
			for (File child : children)
				deletePermanently(child);
		return file.delete();
	}

	/**
	 * Returns the number of files in the given folder, including the files
	 * in its subfolders. Returns 0 for anything that isn't a folder.
	 */
	public static int countFiles(@NotNull File folder) {
		Util.checkNotNull(folder);
		File[] children = folder.listFiles();
		if (children == null)
			return 0;
		int count = 0;
		for (File child : children)
			count += child.isDirectory() ? countFiles(child) : 1;
		return count;
	}

	/** Characters that are not allowed in file names on Windows. */
	private static final String INVALID_NAME_CHARS = "\\/:*?\"<>|";

	/**
	 * Returns true if the given name can be used for a file or folder, i.e.
	 * it isn't empty and contains no characters that file names can't have.
	 */
	public static boolean isValidName(@NotNull String name) {
		Util.checkNotNull(name);
		name = name.trim();
		if (name.isEmpty() || name.equals(".") || name.equals(".."))
			return false;
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (INVALID_NAME_CHARS.indexOf(c) >= 0 || c < ' ')
				return false;
		}
		return true;
	}

	/**
	 * Creates a subfolder with the given name in the given parent folder.
	 * The name is taken as typed; only characters that aren't allowed in
	 * file names are rejected. Returns the new folder, an existing folder of
	 * that name, or null if it couldn't be created.
	 */
	@Nullable
	public static File createFolder(@NotNull File parent, @NotNull String name) {
		Util.checkNotNull(parent, name);
		name = name.trim();
		if (!isValidName(name))
			return null;
		File folder = new File(parent, name);
		if (folder.isDirectory())
			return folder;
		return folder.mkdir() ? folder : null;
	}

}
