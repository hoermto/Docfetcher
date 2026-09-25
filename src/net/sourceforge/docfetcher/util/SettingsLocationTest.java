/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/** Tests where the settings file is kept and the copying of old settings. */
public final class SettingsLocationTest {

	private static Map<String, String> env(String... keysAndValues) {
		Map<String, String> env = new HashMap<String, String>();
		for (int i = 0; i < keysAndValues.length; i += 2)
			env.put(keysAndValues[i], keysAndValues[i + 1]);
		return env;
	}

	@Test
	public void usesTheFolderOfTheSystemForUserSettings() {
		assertEquals(new File("C:/Users/tho/AppData/Roaming", "docfetcher"),
			SettingsLocation.getDefaultDir(true, false,
				env("APPDATA", "C:/Users/tho/AppData/Roaming"), "C:/Users/tho"));
		assertEquals("Without APPDATA", new File("C:/Users/tho/AppData/Roaming", "docfetcher"),
			SettingsLocation.getDefaultDir(true, false, env(), "C:/Users/tho"));
		assertEquals(new File("/Users/tho/Library/Application Support/docfetcher"),
			SettingsLocation.getDefaultDir(false, true, env(), "/Users/tho"));
		assertEquals(new File("/home/tho/.config/docfetcher"),
			SettingsLocation.getDefaultDir(false, false, env(), "/home/tho"));
		assertEquals(new File("/home/tho/conf/docfetcher"),
			SettingsLocation.getDefaultDir(false, false, env("XDG_CONFIG_HOME", "/home/tho/conf"), "/home/tho"));
		assertEquals("DOCFETCHER_HOME wins", new File("D:/df"),
			SettingsLocation.getDefaultDir(true, false,
				env("DOCFETCHER_HOME", "D:/df", "APPDATA", "C:/x"), "C:/Users/tho"));
	}

	@Test
	public void copiesTheOldSettingsOnce() throws Exception {
		File dir = File.createTempFile("docfetcher-settings", "");
		assertTrue(dir.delete() && dir.mkdir());
		try {
			File portable = new File(dir, "portable/conf/settings-conf.txt");
			portable.getParentFile().mkdirs();
			Files.write(portable.toPath(), "IconScalePercent = 150\n".getBytes("UTF-8"));
			File userDir = new File(dir, "user/docfetcher");

			File settings = SettingsLocation.getFile(userDir, null, portable);
			assertEquals(new File(userDir, "settings.conf"), settings);
			assertEquals("IconScalePercent = 150\n", new String(Files.readAllBytes(settings.toPath()), "UTF-8"));
			assertTrue("The old file stays", portable.isFile());

			// An existing settings file is never overwritten
			Files.write(settings.toPath(), "IconScalePercent = 200\n".getBytes("UTF-8"));
			SettingsLocation.getFile(userDir, portable);
			assertEquals("IconScalePercent = 200\n", new String(Files.readAllBytes(settings.toPath()), "UTF-8"));

			// Without old settings nothing is created; the program writes the file on exit
			File fresh = SettingsLocation.getFile(new File(dir, "fresh"), new File(dir, "missing.txt"));
			assertFalse(fresh.exists());
		}
		finally {
			Util.deleteRecursively(dir);
		}
	}

}
