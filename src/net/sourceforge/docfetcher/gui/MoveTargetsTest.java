/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.util.Util;

/** Tests the fixed and the recently used move targets. */
public final class MoveTargetsTest {

	private final List<File> folders = new ArrayList<File>();

	@Before
	public void setUp() throws Exception {
		SettingsConf.StrList.MoveTargets.set(Collections.<String> emptyList());
		SettingsConf.StrList.RecentMoveTargets.set(Collections.<String> emptyList());
		for (int i = 0; i < 7; i++) {
			File dir = File.createTempFile("docfetcher-test", "");
			assertTrue(dir.delete() && dir.mkdir());
			folders.add(dir);
		}
	}

	@After
	public void tearDown() {
		for (File dir : folders)
			dir.delete();
		SettingsConf.StrList.MoveTargets.set(Collections.<String> emptyList());
		SettingsConf.StrList.RecentMoveTargets.set(Collections.<String> emptyList());
	}

	@Test
	public void keepsTheFiveNewestTargetsInOrder() {
		for (int i = 0; i < 7; i++)
			MoveTargets.addRecent(folders.get(i));
		List<File> recent = MoveTargets.loadRecent();
		assertEquals(MoveTargets.MAX_RECENT, recent.size());
		// Newest first: 6, 5, 4, 3, 2
		for (int i = 0; i < recent.size(); i++)
			assertTrue(Util.isCanonicallyEqual(folders.get(6 - i), recent.get(i)));
	}

	@Test
	public void movesAKnownTargetToTheFront() {
		MoveTargets.addRecent(folders.get(0));
		MoveTargets.addRecent(folders.get(1));
		MoveTargets.addRecent(folders.get(0));
		List<File> recent = MoveTargets.loadRecent();
		assertEquals(2, recent.size());
		assertTrue(Util.isCanonicallyEqual(folders.get(0), recent.get(0)));
		assertTrue(Util.isCanonicallyEqual(folders.get(1), recent.get(1)));
	}

	@Test
	public void doesNotRememberFixedTargets() {
		MoveTargets.save(Arrays.asList(Util.getSystemAbsPath(folders.get(0))));
		MoveTargets.addRecent(folders.get(0));
		assertTrue(MoveTargets.loadRecent().isEmpty());
		assertEquals(1, MoveTargets.loadFolders().size());
	}

	@Test
	public void skipsDeletedFolders() {
		MoveTargets.addRecent(folders.get(0));
		MoveTargets.addRecent(folders.get(1));
		assertTrue(folders.get(1).delete());
		List<File> recent = MoveTargets.loadRecent();
		assertEquals(1, recent.size());
		assertTrue(Util.isCanonicallyEqual(folders.get(0), recent.get(0)));
	}

}
