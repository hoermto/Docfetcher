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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.gui.ResultPanel.DocAction;
import net.sourceforge.docfetcher.gui.ResultPanel.HeaderMode;
import net.sourceforge.docfetcher.model.search.ResultDocument;

/**
 * Tests that the buttons of the main toolbar follow the selection of the
 * active result panel, and the navigation buttons the active folder browser.
 */
public final class DocToolBarTest {

	private static GuiTestSupport support;

	@BeforeClass
	public static void setUpClass() throws Exception {
		support = GuiTestSupport.createIndexedFolder("docfetcher-toolbar", new GuiTestSupport.FolderFiller() {
			public void fill(File root) throws Exception {
				new File(root, "sub").mkdir();
				GuiTestSupport.write(new File(root, "a.txt"), "alpha document");
				GuiTestSupport.write(new File(root, "b.txt"), "beta document");
			}
		});
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		support.dispose();
	}

	private static void select(ResultPanel panel, List<ResultDocument> docs) {
		Set<String> uids = new HashSet<String>();
		for (ResultDocument doc : docs)
			uids.add(doc.getUniqueId());
		panel.selectByUids(uids);
	}

	private static boolean enabled(DocToolBar toolBar, DocAction action) {
		return toolBar.getItem(action).getEnabled();
	}

	@Test
	public void followsTheSelectionOfTheActivePanel() throws Exception {
		Display display = GuiTestSupport.createDisplay();
		Assume.assumeTrue(display != null);
		try {
			Shell shell = new Shell(display);
			shell.setLayout(new GridLayout(1, false));
			DocToolBar toolBar = new DocToolBar(shell);
			ResultPanel panel = new ResultPanel(shell, support.registry);
			panel.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			shell.setSize(600, 300);
			shell.open();

			for (DocAction action : DocAction.values())
				assertFalse("No active panel: " + action, enabled(toolBar, action));

			List<ResultDocument> rows = support.listFolder(support.root);
			panel.setResults(rows, HeaderMode.FILES);
			panel.activate();
			assertFalse(enabled(toolBar, DocAction.RENAME));
			assertFalse(enabled(toolBar, DocAction.DELETE));

			select(panel, GuiTestSupport.find(rows, "a.txt"));
			assertTrue(enabled(toolBar, DocAction.RENAME));
			assertTrue(enabled(toolBar, DocAction.MOVE));
			assertTrue(enabled(toolBar, DocAction.DELETE));
			// The search results have no folder navigation
			assertFalse(enabled(toolBar, DocAction.FOLDER_UP));

			select(panel, GuiTestSupport.find(rows, "a.txt", "b.txt"));
			assertFalse("Only one file at a time", enabled(toolBar, DocAction.RENAME));
			assertTrue(enabled(toolBar, DocAction.MOVE));
			assertTrue(enabled(toolBar, DocAction.DELETE));

			select(panel, GuiTestSupport.find(rows, "sub"));
			assertTrue("Folders can be renamed", enabled(toolBar, DocAction.RENAME));
			assertFalse("Folders are not moved", enabled(toolBar, DocAction.MOVE));
			assertTrue("Folders can be deleted", enabled(toolBar, DocAction.DELETE));
			GuiTestSupport.screenshot(shell, "toolbar-folder-selected");

			panel.getControl().dispose();
			assertFalse("Disposed panel", enabled(toolBar, DocAction.DELETE));
		}
		finally {
			display.dispose();
		}
	}

	@Test
	public void paneModeButtonsSetTheMode() throws Exception {
		Display display = GuiTestSupport.createDisplay();
		Assume.assumeTrue(display != null);
		int saved = SettingsConf.Int.PaneMode.get();
		try {
			Shell shell = new Shell(display);
			shell.setLayout(new GridLayout(1, false));
			DocToolBar toolBar = new DocToolBar(shell);
			DocToolBar.selectPaneMode(toolBar, 2);
			assertTrue(toolBar.getPaneItem(2).getSelection());
			assertFalse(toolBar.getPaneItem(3).getSelection());
			// A click on the button of mode 3 sets the mode
			toolBar.getPaneItem(3).setSelection(true);
			toolBar.getPaneItem(3).notifyListeners(SWT.Selection, new org.eclipse.swt.widgets.Event());
			assertEquals(3, SettingsConf.Int.PaneMode.get());
			assertEquals(3, Application.getPaneMode());
			// Settings of older versions: from 'folder panel shown'
			SettingsConf.Int.PaneMode.set(0);
			SettingsConf.Bool.ShowFolderPanel.set(false);
			assertEquals(1, Application.getPaneMode());
			SettingsConf.Bool.ShowFolderPanel.set(true);
			assertEquals(2, Application.getPaneMode());
		}
		finally {
			SettingsConf.Int.PaneMode.set(saved);
			display.dispose();
		}
	}

	@Test
	public void navigatesTheActiveFolderBrowser() throws Exception {
		Display display = GuiTestSupport.createDisplay();
		Assume.assumeTrue(display != null);
		try {
			Shell shell = new Shell(display);
			shell.setLayout(new GridLayout(1, false));
			DocToolBar toolBar = new DocToolBar(shell);
			FolderPanel folderPanel = new FolderPanel(shell, support.registry,
				SettingsConf.Str.LastBrowsedFolder);
			folderPanel.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			shell.setSize(600, 300);
			shell.open();

			folderPanel.setFolder(null);
			folderPanel.getResultPanel().activate();
			assertTrue(enabled(toolBar, DocAction.FOLDER_REFRESH));
			assertFalse("Root list has no parent", enabled(toolBar, DocAction.FOLDER_UP));
			assertFalse(enabled(toolBar, DocAction.NEW_FOLDER));

			folderPanel.setFolder(new File(support.root, "sub"));
			assertTrue(enabled(toolBar, DocAction.FOLDER_UP));
			assertTrue(enabled(toolBar, DocAction.FOLDER_ROOTS));
			assertTrue(enabled(toolBar, DocAction.NEW_FOLDER));

			// The toolbar button runs the browser's action
			toolBar.getItem(DocAction.FOLDER_ROOTS).notifyListeners(SWT.Selection, new org.eclipse.swt.widgets.Event());
			assertEquals(null, folderPanel.getFolder());
			assertFalse(enabled(toolBar, DocAction.FOLDER_UP));
		}
		finally {
			display.dispose();
		}
	}

}
