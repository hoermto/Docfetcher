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
import java.util.List;

import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * The configured inbox folders, each of which is shown on its own tab in the
 * main window. Stored in {@link SettingsConf.StrList#InboxFolders} as entries
 * of the form {@code path|name}, where the name is optional.
 */
public final class InboxFolders {

	private static final char SEPARATOR = '|';

	private InboxFolders() {}

	/** One inbox folder with an optional user-defined tab name. */
	public static final class Entry {
		private final String path;
		private final String name;

		public Entry(@NotNull String path, @NotNull String name) {
			Util.checkNotNull(path, name);
			this.path = path.trim();
			this.name = name.trim();
		}

		/** The folder as entered by the user. */
		@NotNull
		public String getPath() {
			return path;
		}

		@NotNull
		public File getFolder() {
			return Util.getCanonicalFile(path);
		}

		/** The user-defined name, may be empty. */
		@NotNull
		public String getName() {
			return name;
		}

		/**
		 * The name shown on the tab: the user-defined name, or the last part
		 * of the folder path if none was set.
		 */
		@NotNull
		public String getTabName() {
			if (!name.isEmpty())
				return name;
			String fileName = new File(path).getName();
			return fileName.isEmpty() ? path : fileName;
		}

		public boolean equals(Object obj) {
			if (!(obj instanceof Entry))
				return false;
			Entry other = (Entry) obj;
			return path.equals(other.path) && name.equals(other.name);
		}

		public int hashCode() {
			return path.hashCode() * 31 + name.hashCode();
		}
	}

	/** Returns the configured inbox folders. */
	@NotNull
	public static List<Entry> load() {
		List<Entry> entries = new ArrayList<Entry>();
		for (String value : SettingsConf.StrList.InboxFolders.get()) {
			int i = value.indexOf(SEPARATOR);
			String path = i < 0 ? value : value.substring(0, i);
			String name = i < 0 ? "" : value.substring(i + 1);
			if (!path.trim().isEmpty())
				entries.add(new Entry(path, name));
		}
		return entries;
	}

	/**
	 * Moves the single inbox folder configured with version 2.3 to the list of
	 * inbox folders. Called once at startup.
	 */
	public static void migrateLegacySetting() {
		String legacy = SettingsConf.Str.InboxFolder.get().trim();
		if (legacy.isEmpty())
			return;
		List<Entry> entries = load();
		if (entries.isEmpty()) {
			entries.add(new Entry(legacy, ""));
			save(entries);
		}
		SettingsConf.Str.InboxFolder.set("");
	}

	public static void save(@NotNull List<Entry> entries) {
		List<String> values = new ArrayList<String>(entries.size());
		for (Entry entry : entries) {
			values.add(entry.getName().isEmpty()
				? entry.getPath()
				: entry.getPath() + SEPARATOR + entry.getName());
		}
		SettingsConf.StrList.InboxFolders.set(values);
	}

	/** Returns the canonical folders of all configured inbox entries. */
	@NotNull
	public static List<File> getFolders() {
		List<File> folders = new ArrayList<File>();
		for (Entry entry : load())
			folders.add(entry.getFolder());
		return folders;
	}

}
