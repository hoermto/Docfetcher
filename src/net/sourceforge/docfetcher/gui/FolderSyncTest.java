/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.model.search.ResultDocument;

/**
 * Tests the synced folder view: the left browser always shows the parent of
 * the folder in the middle one.
 */
public final class FolderSyncTest {

	private static GuiTestSupport support;

	@BeforeClass
	public static void setUpClass() throws Exception {
		support = GuiTestSupport.createIndexedFolder("docfetcher-sync", new GuiTestSupport.FolderFiller() {
			public void fill(File root) throws Exception {
				new File(root, "a/a2").mkdirs();
				new File(root, "a/a1").mkdirs();
				new File(root, "b").mkdirs();
				GuiTestSupport.write(new File(root, "b/doc.txt"), "a document");
			}
		});
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		support.dispose();
	}

	private File dir(String path) {
		return new File(support.root, path);
	}

	private static void assertFolder(File expected, FolderPanel panel) {
		assertEquals(expected == null ? null : expected.getAbsoluteFile(),
			panel.getFolder() == null ? null : panel.getFolder().getAbsoluteFile());
	}

	@Test
	public void theLeftShowsTheParentOfTheMiddle() throws Exception {
		Display display = GuiTestSupport.createDisplay();
		Assume.assumeTrue(display != null);
		boolean saved = SettingsConf.Bool.SyncFolderPanels.get();
		try {
			SettingsConf.Bool.SyncFolderPanels.set(true);
			Shell shell = new Shell(display);
			shell.setLayout(new FillLayout());
			FolderPanel left = new FolderPanel(shell, support.registry, SettingsConf.Str.LastBrowsedFolder);
			FolderPanel middle = new FolderPanel(shell, support.registry, SettingsConf.Str.LastBrowsedFolderPanel);
			left.setFolder(support.root);
			middle.setFolder(null);
			FolderSync sync = new FolderSync(left, middle);

			// Entering a folder in the middle moves the left one along
			middle.setFolder(dir("a"));
			assertFolder(support.root, left);
			middle.setFolder(dir("a/a1"));
			assertFolder(dir("a"), left);

			// Up on the left: the middle shows the folder that was left
			left.setFolder(support.root);
			assertFolder(dir("a"), middle);

			// Entering a folder on the left: the middle shows its first subfolder
			left.setFolder(dir("a"));
			assertFolder(dir("a/a1"), middle);
			// ... or nothing if there is none
			left.setFolder(dir("b"));
			assertTrue(middle.isShowingNothing());

			// A folder clicked on the left is shown in the middle
			left.setFolder(support.root);
			List<ResultDocument> rows = support.listFolder(support.root);
			left.getResultPanel().evtSelection.fire(GuiTestSupport.find(rows, "b"));
			assertFolder(dir("b"), middle);
			assertFalse(middle.isShowingNothing());

			// Switched off: the browsers are independent
			SettingsConf.Bool.SyncFolderPanels.set(false);
			middle.setFolder(dir("a/a2"));
			assertFolder(support.root, left);
			// Switched on again: the left is brought in line
			SettingsConf.Bool.SyncFolderPanels.set(true);
			assertFolder(dir("a"), left);

			sync.dispose();
			middle.setFolder(dir("b"));
			assertFolder(dir("a"), left);
		}
		finally {
			SettingsConf.Bool.SyncFolderPanels.set(saved);
			display.dispose();
		}
	}

	@Test
	public void firstSubfolderByName() {
		assertEquals("a1", FolderSync.getFirstSubfolder(dir("a")).getName());
		assertEquals(null, FolderSync.getFirstSubfolder(dir("a/a1")));
	}

}
