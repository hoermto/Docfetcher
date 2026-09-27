/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.swt.SWT;
import org.junit.Test;

import net.sourceforge.docfetcher.enums.SettingsConf;

/**
 * Tests the keys of the main window that are set in the preferences: the
 * defaults F5 (inbox), F6 (search), F7 (folders), and matching key events.
 */
public final class KeysTest {

	@Test
	public void defaultsAreF5F6F7() {
		assertArrayEquals(new int[] { SWT.NONE, SWT.F5 }, SettingsConf.IntArray.KeyInbox.defaultValue);
		assertArrayEquals(new int[] { SWT.NONE, SWT.F6 }, SettingsConf.IntArray.KeySearch.defaultValue);
		assertArrayEquals(new int[] { SWT.NONE, SWT.F7 }, SettingsConf.IntArray.KeyFolders.defaultValue);
	}

	@Test
	public void keyEventsMatchTheSetting() {
		int[] saved = SettingsConf.IntArray.KeySearch.get();
		try {
			SettingsConf.IntArray.KeySearch.set(new int[] { SWT.NONE, SWT.F6 });
			assertTrue(Application.isKey(SettingsConf.IntArray.KeySearch, SWT.NONE, SWT.F6));
			assertFalse("With Ctrl it's another key", Application.isKey(SettingsConf.IntArray.KeySearch, SWT.CTRL, SWT.F6));
			assertFalse(Application.isKey(SettingsConf.IntArray.KeySearch, SWT.NONE, SWT.F7));

			// Letters are saved in upper case, key events come in lower case
			SettingsConf.IntArray.KeySearch.set(new int[] { SWT.CTRL | SWT.SHIFT, 'S' });
			assertTrue(Application.isKey(SettingsConf.IntArray.KeySearch, SWT.CTRL | SWT.SHIFT, 's'));
			assertFalse(Application.isKey(SettingsConf.IntArray.KeySearch, SWT.CTRL, 's'));
			// Mouse buttons in the state mask don't matter
			assertTrue(Application.isKey(SettingsConf.IntArray.KeySearch, SWT.CTRL | SWT.SHIFT | SWT.BUTTON1, 's'));
		}
		finally {
			SettingsConf.IntArray.KeySearch.set(saved);
		}
	}

}
