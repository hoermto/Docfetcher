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
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Remembers the file operations of the running program, so that they can be
 * undone (Ctrl+Z). Renames and moves are undone by moving the files back;
 * deleted files are in the recycle bin and can only be restored from there,
 * which the undo reports. Merges and splits of PDF files are undone by
 * removing the created files and, if the originals were moved to the recycle
 * bin, restoring them from backup copies, which are kept until the entry is
 * dropped or the program exits.
 */
public final class FileOperationHistory {

	/** How many operations are remembered. */
	public static final int MAX_ENTRIES = 20;

	/** What was done with the files. */
	public enum Kind {
		RENAME, MOVE, DELETE, MERGE, SPLIT, COPY
	}

	/** One file operation, with the files it affected. */
	public static final class Entry {
		private final Kind kind;
		private final List<File> sources;
		private final List<File> targets; // Empty for deletions
		private final List<File> backups; // Copies of the removed originals

		Entry(	@NotNull Kind kind,
				@NotNull List<File> sources,
				@NotNull List<File> targets,
				@NotNull List<File> backups) {
			this.kind = kind;
			this.sources = Collections.unmodifiableList(new ArrayList<File>(sources));
			this.targets = Collections.unmodifiableList(new ArrayList<File>(targets));
			this.backups = Collections.unmodifiableList(new ArrayList<File>(backups));
		}

		@NotNull
		public Kind getKind() {
			return kind;
		}

		/** Where the files were before the operation. */
		@NotNull
		public List<File> getSources() {
			return sources;
		}

		/** Where the files are now; empty for a deletion. */
		@NotNull
		public List<File> getTargets() {
			return targets;
		}

		/**
		 * Copies of the sources, in the same order, if the sources were
		 * removed by a merge or split; otherwise empty.
		 */
		@NotNull
		public List<File> getBackups() {
			return backups;
		}

		public int getFileCount() {
			return sources.size();
		}

		/** The name of the first file, for the undo message. */
		@NotNull
		public String getFirstName() {
			return sources.isEmpty() ? "" : sources.get(0).getName();
		}
	}

	private static final FileOperationHistory instance = new FileOperationHistory();

	private final LinkedList<Entry> entries = new LinkedList<Entry>();
	@Nullable private File backupDir;
	private int backupCount = 0;

	private FileOperationHistory() {}

	/**
	 * Sets the folder for the backup copies and deletes what an earlier run
	 * of the program may have left there.
	 */
	public synchronized void setBackupDir(@NotNull File backupDir) {
		this.backupDir = backupDir;
		deleteQuietly(backupDir);
	}

	/**
	 * Copies the given files into a new subfolder of the backup folder and
	 * returns the copies, in the same order.
	 */
	@NotNull
	public List<File> backup(@NotNull List<File> files) throws IOException {
		File dir;
		synchronized (this) {
			File parent = backupDir != null
				? backupDir
				: new File(System.getProperty("java.io.tmpdir"), "docfetcher-undo");
			dir = new File(parent, String.valueOf(++backupCount));
		}
		deleteQuietly(dir);
		if (!dir.mkdirs())
			throw new IOException(dir.getPath());
		List<File> copies = new ArrayList<File>(files.size());
		try {
			for (int i = 0; i < files.size(); i++) {
				// Numbered, since files from different folders may have the same name
				File copy = new File(dir, i + "_" + files.get(i).getName());
				Files.copy(files.get(i).toPath(), copy.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
				copies.add(copy);
			}
		}
		catch (IOException e) {
			deleteQuietly(dir);
			throw e;
		}
		return copies;
	}

	/** Deletes the backup copies of the given entry, if it has any. */
	public static void deleteBackups(@NotNull Entry entry) {
		if (!entry.backups.isEmpty())
			deleteQuietly(entry.backups.get(0).getParentFile());
	}

	private static void deleteQuietly(@Nullable File dir) {
		if (dir == null || !dir.exists())
			return;
		try {
			Util.deleteRecursively(dir);
		}
		catch (IOException e) {
			// Left for the next start, which cleans up the backup folder
		}
	}

	@NotNull
	public static FileOperationHistory getInstance() {
		return instance;
	}

	/**
	 * Remembers an operation. {@code sources} are the original files,
	 * {@code targets} the files after the operation, in the same order; for a
	 * deletion the targets are empty.
	 */
	public synchronized void add(	@NotNull Kind kind,
									@NotNull List<File> sources,
									@NotNull List<File> targets) {
		add(kind, sources, targets, Collections.<File> emptyList());
	}

	/**
	 * Remembers a merge or split: {@code targets} are the created files,
	 * {@code backups} the copies of the removed sources (empty if the sources
	 * were kept).
	 */
	public synchronized void add(	@NotNull Kind kind,
									@NotNull List<File> sources,
									@NotNull List<File> targets,
									@NotNull List<File> backups) {
		Util.checkNotNull(kind, sources, targets, backups);
		if (sources.isEmpty())
			return;
		entries.addLast(new Entry(kind, sources, targets, backups));
		while (entries.size() > MAX_ENTRIES)
			deleteBackups(entries.removeFirst());
	}

	/** Returns the operation that would be undone next, or null. */
	@Nullable
	public synchronized Entry peek() {
		return entries.isEmpty() ? null : entries.getLast();
	}

	/** Removes and returns the operation to undo, or null. */
	@Nullable
	public synchronized Entry pop() {
		return entries.isEmpty() ? null : entries.removeLast();
	}

	/** Forgets all operations and deletes the backup copies. */
	public synchronized void clear() {
		for (Entry entry : entries)
			deleteBackups(entry);
		entries.clear();
	}

}
