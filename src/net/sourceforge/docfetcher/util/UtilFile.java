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

import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * File operations that need platform support, such as moving a file to the
 * recycle bin.
 */
public final class UtilFile {

	private UtilFile() {}

	/**
	 * Returns true if deleted files can be moved to the recycle bin (or to
	 * the desktop trash on Linux) instead of being deleted for good.
	 */
	public static boolean isTrashSupported() {
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
	 * Moves the given file to the recycle bin, or deletes it for good if the
	 * system has no recycle bin. Returns true on success.
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
		return file.delete();
	}

	/** Characters that are not allowed in file names on Windows. */
	private static final String INVALID_NAME_CHARS = "\\/:*?\"<>|";

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
		if (name.isEmpty() || name.equals(".") || name.equals(".."))
			return null;
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (INVALID_NAME_CHARS.indexOf(c) >= 0 || c < ' ')
				return null;
		}
		File folder = new File(parent, name);
		if (folder.isDirectory())
			return folder;
		return folder.mkdir() ? folder : null;
	}

}
