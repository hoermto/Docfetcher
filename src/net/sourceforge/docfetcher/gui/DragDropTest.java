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
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sourceforge.docfetcher.gui.ResultPanel.HeaderMode;
import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.util.UtilFile;

/**
 * Tests that dragging files never deletes them: a drop that reports a move
 * but didn't move the files (a DocFetcher table showing the same folder, a
 * failed move, a program that copied) leaves the files in place.
 */
public final class DragDropTest {

	private static GuiTestSupport support;

	@BeforeClass
	public static void setUpClass() throws Exception {
		support = GuiTestSupport.createIndexedFolder("docfetcher-dnd", new GuiTestSupport.FolderFiller() {
			public void fill(File root) throws Exception {
				GuiTestSupport.write(new File(root, "a.txt"), "alpha document");
				GuiTestSupport.write(new File(root, "b.txt"), "beta document");
				new File(root, "inbox").mkdir();
				new File(root, "archive").mkdir();
				GuiTestSupport.write(new File(root, "archive/c.txt"), "gamma document");
			}
		});
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		support.dispose();
	}

	@Test
	public void filesDroppedOnAnInboxAreMovedIntoItsFolder() throws Exception {
		Display display = GuiTestSupport.createDisplay();
		Assume.assumeTrue(display != null);
		try {
			Shell shell = new Shell(display);
			shell.setLayout(new FillLayout());
			File inbox = new File(support.root, "inbox");
			InboxPanel inboxPanel = new InboxPanel(shell, support.registry);
			inboxPanel.setFolder(inbox);
			assertEquals("Drops on the inbox list go into the inbox folder",
				inbox, inboxPanel.getResultPanel().getCurrentFolder());

			// E.g. dragged from the folder browser onto the inbox tab
			File file = new File(support.root, "archive/c.txt");
			inboxPanel.getResultPanel().dropFiles(Arrays.asList(file), inbox, false);
			assertTrue(new File(inbox, "c.txt").isFile());
			assertTrue(!file.exists());
			// And back, to leave the folder as it was
			inboxPanel.getResultPanel().dropFiles(Arrays.asList(new File(inbox, "c.txt")), file.getParentFile(), false);
			assertTrue(file.isFile());
		}
		finally {
			display.dispose();
		}
	}

	@Test
	public void ctrlDropCopiesAndNeverOverwrites() throws Exception {
		Display display = GuiTestSupport.createDisplay();
		Assume.assumeTrue(display != null);
		try {
			Shell shell = new Shell(display);
			shell.setLayout(new FillLayout());
			File inbox = new File(support.root, "inbox");
			InboxPanel inboxPanel = new InboxPanel(shell, support.registry);
			inboxPanel.setFolder(inbox);
			File original = new File(support.root, "a.txt");
			FileOperationHistory.getInstance().clear();

			inboxPanel.getResultPanel().dropFiles(Arrays.asList(original), inbox, true);
			File copy = new File(inbox, "a.txt");
			assertTrue("Copied", copy.isFile());
			assertTrue("Original stays", original.isFile());
			// Copying again (or into the same folder) adds a number
			inboxPanel.getResultPanel().dropFiles(Arrays.asList(original), inbox, true);
			File second = new File(inbox, "a-2.txt");
			assertTrue(second.isFile());
			assertEquals("alpha document", new String(java.nio.file.Files.readAllBytes(copy.toPath()), "UTF-8"));

			// Undo removes the last copy, the original stays
			UtilFile.setTrashDisabled(true);
			assertTrue(ResultPanel.undoMergeOrSplitWithoutAsking(support.registry));
			assertTrue(!second.exists());
			assertTrue(copy.isFile() && original.isFile());
			assertTrue(ResultPanel.undoMergeOrSplitWithoutAsking(support.registry));
			assertTrue(!copy.exists());
			assertTrue(original.isFile());
		}
		finally {
			UtilFile.setTrashDisabled(false);
			display.dispose();
		}
	}

	@Test
	public void aMoveThatDidntMoveKeepsTheFiles() throws Exception {
		Display display = GuiTestSupport.createDisplay();
		Assume.assumeTrue(display != null);
		try {
			Shell shell = new Shell(display);
			shell.setLayout(new FillLayout());
			ResultPanel panel = new ResultPanel(shell, support.registry);
			List<ResultDocument> rows = support.listFolder(support.root);
			panel.setResults(rows, HeaderMode.FILES);
			List<ResultDocument> docs = GuiTestSupport.find(rows, "a.txt", "b.txt");
			assertEquals("Rows: " + rows, 2, docs.size());
			int count = panel.getItemCount();
			List<File> files = Arrays.asList(new File(support.root, "a.txt"), new File(support.root, "b.txt"));

			// Dropped on a DocFetcher table that didn't move them (e.g. the same folder)
			panel.handleDragFinished(DND.DROP_MOVE, true, files, docs);
			// Dropped on a program that reports a move but only copied
			panel.handleDragFinished(DND.DROP_MOVE, false, files, docs);
			for (File file : files)
				assertTrue("Still there: " + file, file.isFile());
			assertEquals(count, panel.getItemCount());

			// A file the target really moved away loses its row
			File moved = new File(support.root, "moved-away.txt");
			assertTrue(files.get(0).renameTo(moved));
			panel.handleDragFinished(DND.DROP_MOVE, false, files, docs);
			assertEquals(count - 1, panel.getItemCount());
			assertTrue(moved.renameTo(files.get(0)));
		}
		finally {
			display.dispose();
		}
	}

}
