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
 * The fixed move targets: folders the user always wants to see at the top
 * of the 'Move To' menu and of the move dialog, regardless of the content
 * based suggestions. Stored in {@link SettingsConf.StrList#MoveTargets}.
 */
public final class MoveTargets {

	private MoveTargets() {}

	/** Returns the configured folders as entered by the user. */
	@NotNull
	public static List<String> loadPaths() {
		List<String> paths = new ArrayList<String>();
		for (String value : SettingsConf.StrList.MoveTargets.get())
			if (!value.trim().isEmpty())
				paths.add(value.trim());
		return paths;
	}

	/** Returns the configured folders as canonical files. */
	@NotNull
	public static List<File> loadFolders() {
		List<File> folders = new ArrayList<File>();
		for (String path : loadPaths())
			folders.add(Util.getCanonicalFile(path));
		return folders;
	}

	public static void save(@NotNull List<String> paths) {
		SettingsConf.StrList.MoveTargets.set(new ArrayList<String>(paths));
	}

	/** How many recently used target folders are remembered. */
	public static final int MAX_RECENT = 5;

	/**
	 * Returns the folders the user moved files into most recently, newest
	 * first. Folders that no longer exist are left out.
	 */
	@NotNull
	public static List<File> loadRecent() {
		List<File> folders = new ArrayList<File>();
		for (String path : SettingsConf.StrList.RecentMoveTargets.get()) {
			if (path.trim().isEmpty())
				continue;
			File folder = Util.getCanonicalFile(path.trim());
			if (folder.isDirectory())
				folders.add(folder);
		}
		return folders;
	}

	/**
	 * Remembers the given folder as the most recently used move target.
	 * Fixed targets are not remembered, since they are listed anyway.
	 */
	public static void addRecent(@NotNull File folder) {
		Util.checkNotNull(folder);
		for (File fixed : loadFolders())
			if (Util.isCanonicallyEqual(fixed, folder))
				return;
		List<String> paths = new ArrayList<String>();
		paths.add(Util.getSystemAbsPath(folder));
		for (File recent : loadRecent()) {
			if (Util.isCanonicallyEqual(recent, folder))
				continue;
			if (paths.size() >= MAX_RECENT)
				break;
			paths.add(Util.getSystemAbsPath(recent));
		}
		SettingsConf.StrList.RecentMoveTargets.set(paths);
	}

}
