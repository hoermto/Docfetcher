/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import java.io.File;
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
 * which the undo reports.
 */
public final class FileOperationHistory {

	/** How many operations are remembered. */
	public static final int MAX_ENTRIES = 20;

	/** What was done with the files. */
	public enum Kind {
		RENAME, MOVE, DELETE
	}

	/** One file operation, with the files it affected. */
	public static final class Entry {
		private final Kind kind;
		private final List<File> sources;
		private final List<File> targets; // Empty for deletions

		Entry(@NotNull Kind kind, @NotNull List<File> sources, @NotNull List<File> targets) {
			this.kind = kind;
			this.sources = Collections.unmodifiableList(new ArrayList<File>(sources));
			this.targets = Collections.unmodifiableList(new ArrayList<File>(targets));
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

	private FileOperationHistory() {}

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
		Util.checkNotNull(kind, sources, targets);
		if (sources.isEmpty())
			return;
		entries.addLast(new Entry(kind, sources, targets));
		while (entries.size() > MAX_ENTRIES)
			entries.removeFirst();
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

	public synchronized void clear() {
		entries.clear();
	}

}
