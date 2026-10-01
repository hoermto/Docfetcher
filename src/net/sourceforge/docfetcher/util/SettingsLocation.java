/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Where the settings file is kept: in the folder the system has for the
 * settings of the user, not in the program folder, so that the settings of
 * the portable version survive when a new version is unpacked over it.
 * <ul>
 * <li>Windows: {@code %APPDATA%\docfetcher\settings.conf}
 * <li>macOS: {@code ~/Library/Application Support/docfetcher/settings.conf}
 * <li>Linux: {@code $XDG_CONFIG_HOME/docfetcher/settings.conf}, by default
 * {@code ~/.config/docfetcher/settings.conf}
 * </ul>
 * The environment variable DOCFETCHER_HOME and the 'settings' entry of
 * paths.txt still take precedence. Settings files of earlier versions
 * ({@code conf/settings-conf.txt}) are copied once.
 */
public final class SettingsLocation {

	public static final String FILE_NAME = "settings.conf";

	/** The name of the settings file up to version 2.5. */
	public static final String OLD_FILE_NAME = "settings-conf.txt";

	private static final String FOLDER_NAME = "docfetcher";

	private SettingsLocation() {}

	/** The folder of the settings file on this system. */
	@NotNull
	public static File getDefaultDir() {
		return getDefaultDir(Util.IS_WINDOWS, Util.IS_MAC_OS_X, System.getenv(), Util.USER_HOME_PATH);
	}

	/** The folder of the settings file for the given system; for tests. */
	@NotNull
	static File getDefaultDir(	boolean windows,
								boolean mac,
								@NotNull Map<String, String> env,
								@NotNull String userHome) {
		String home = env.get("DOCFETCHER_HOME");
		if (home != null && !home.trim().isEmpty())
			return new File(home);
		if (windows) {
			String appData = env.get("APPDATA");
			File base = appData != null && !appData.trim().isEmpty()
				? new File(appData)
				: new File(userHome, "AppData/Roaming");
			return new File(base, FOLDER_NAME);
		}
		if (mac)
			return new File(userHome, "Library/Application Support/" + FOLDER_NAME);
		String configHome = env.get("XDG_CONFIG_HOME");
		File base = configHome != null && !configHome.trim().isEmpty()
			? new File(configHome)
			: new File(userHome, ".config");
		return new File(base, FOLDER_NAME);
	}

	/**
	 * Returns the settings file in the given folder. If it doesn't exist yet,
	 * the first existing of the old files is copied to it; the old file is
	 * left in place, e.g. for an older version of the program.
	 */
	@NotNull
	public static File getFile(@NotNull File dir, @Nullable File... oldFiles) {
		File file = new File(dir, FILE_NAME);
		if (file.exists() || oldFiles == null)
			return file;
		for (File oldFile : oldFiles) {
			if (oldFile == null || !oldFile.isFile())
				continue;
			try {
				dir.mkdirs();
				Files.copy(oldFile.toPath(), file.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
			}
			catch (IOException e) {
				Util.printErr("Copying the settings from " + oldFile + " failed: " + e);
			}
			break;
		}
		return file;
	}

}
