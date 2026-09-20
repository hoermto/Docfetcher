/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.util;

import java.io.File;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Registers or unregisters the program as a Windows startup program, using
 * the per-user "Run" registry key, which doesn't require administrator
 * privileges.
 */
public final class WindowsAutostart {

	private static final String RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
	private static final String LAUNCHER_NAME = "DocFetcher.exe";

	private WindowsAutostart() {}

	/**
	 * Returns the launcher executable in the program folder, or null if it
	 * doesn't exist (e.g. on other platforms or in the development version).
	 * Autostart is only supported if this returns a non-null value.
	 */
	@Nullable
	public static File getLauncher() {
		if (!Util.IS_WINDOWS)
			return null;
		File launcher = new File(LAUNCHER_NAME).getAbsoluteFile();
		return launcher.isFile() ? launcher : null;
	}

	public static boolean isSupported() {
		return getLauncher() != null;
	}

	/**
	 * Returns true if a startup entry for this program exists.
	 */
	public static boolean isEnabled(@NotNull String valueName) {
		if (!Util.IS_WINDOWS)
			return false;
		try {
			return Advapi32Util.registryValueExists(
				WinReg.HKEY_CURRENT_USER, RUN_KEY, valueName);
		}
		catch (Throwable e) {
			Util.printErr(e);
			return false;
		}
	}

	/**
	 * Creates or removes the startup entry. Throws an exception if the
	 * registry could not be modified.
	 */
	public static void setEnabled(@NotNull String valueName, boolean enabled) {
		Util.checkNotNull(valueName);
		if (enabled) {
			File launcher = getLauncher();
			if (launcher == null)
				throw new IllegalStateException(LAUNCHER_NAME + " not found");
			String command = "\"" + launcher.getPath() + "\"";
			Advapi32Util.registrySetStringValue(
				WinReg.HKEY_CURRENT_USER, RUN_KEY, valueName, command);
		}
		else if (isEnabled(valueName)) {
			Advapi32Util.registryDeleteValue(
				WinReg.HKEY_CURRENT_USER, RUN_KEY, valueName);
		}
	}

}
