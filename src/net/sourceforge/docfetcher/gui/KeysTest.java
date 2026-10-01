/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.swt.SWT;
import org.junit.Test;

import net.sourceforge.docfetcher.enums.SettingsConf;

/**
 * Tests the keys of the main window that are set in the preferences: the
 * defaults F7 (search), F8 (inbox), F5 (folders), the tab titles with the key,
 * and matching key events.
 */
public final class KeysTest {

	@Test
	public void defaults() {
		assertArrayEquals(new int[] { SWT.NONE, SWT.F7 }, SettingsConf.IntArray.SearchKey.defaultValue);
		assertArrayEquals(new int[] { SWT.NONE, SWT.F8 }, SettingsConf.IntArray.InboxKey.defaultValue);
		assertArrayEquals(new int[] { SWT.NONE, SWT.F5 }, SettingsConf.IntArray.FoldersKey.defaultValue);
	}

	@Test
	public void tabTitlesShowTheKey() {
		int[] saved = SettingsConf.IntArray.SearchKey.get();
		try {
			SettingsConf.IntArray.SearchKey.set(new int[] { SWT.NONE, SWT.F7 });
			assertEquals("Search (F7)", Application.withKey("Search", SettingsConf.IntArray.SearchKey));
			SettingsConf.IntArray.SearchKey.set(new int[] { SWT.CTRL, SWT.F9 });
			assertTrue(Application.withKey("Search", SettingsConf.IntArray.SearchKey).matches("Search \\(.+\\+ ?F9\\)"));
			// No key, no parentheses
			SettingsConf.IntArray.SearchKey.set(new int[] { SWT.NONE, 0 });
			assertEquals("Search", Application.withKey("Search", SettingsConf.IntArray.SearchKey));
		}
		finally {
			SettingsConf.IntArray.SearchKey.set(saved);
		}
	}

	@Test
	public void keyEventsMatchTheSetting() {
		int[] saved = SettingsConf.IntArray.SearchKey.get();
		try {
			SettingsConf.IntArray.SearchKey.set(new int[] { SWT.NONE, SWT.F7 });
			assertTrue(Application.isKey(SettingsConf.IntArray.SearchKey, SWT.NONE, SWT.F7));
			assertFalse("With Ctrl it's another key", Application.isKey(SettingsConf.IntArray.SearchKey, SWT.CTRL, SWT.F7));
			assertFalse(Application.isKey(SettingsConf.IntArray.SearchKey, SWT.NONE, SWT.F8));

			// Letters are saved in upper case, key events come in lower case
			SettingsConf.IntArray.SearchKey.set(new int[] { SWT.CTRL | SWT.SHIFT, 'S' });
			assertTrue(Application.isKey(SettingsConf.IntArray.SearchKey, SWT.CTRL | SWT.SHIFT, 's'));
			assertFalse(Application.isKey(SettingsConf.IntArray.SearchKey, SWT.CTRL, 's'));
			// Mouse buttons in the state mask don't matter
			assertTrue(Application.isKey(SettingsConf.IntArray.SearchKey, SWT.CTRL | SWT.SHIFT | SWT.BUTTON1, 's'));
		}
		finally {
			SettingsConf.IntArray.SearchKey.set(saved);
		}
	}

}
