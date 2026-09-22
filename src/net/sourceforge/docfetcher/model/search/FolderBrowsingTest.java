/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.index.IndexingReporter;
import net.sourceforge.docfetcher.model.index.Task.CancelAction;
import net.sourceforge.docfetcher.model.index.Task.CancelHandler;
import net.sourceforge.docfetcher.model.index.file.FileIndex;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.Util;

/**
 * Tests the folder listing used by the 'Folders' tab: subfolders first, then
 * the files of the folder, whether they are indexed or not.
 */
public final class FolderBrowsingTest {

	private static File root;
	private static File indexDir;
	private static IndexRegistry registry;

	@BeforeClass
	public static void setUpClass() throws Exception {
		AppUtil.Const.autoInit();
		root = createDir("docfetcher-browse");
		indexDir = createDir("docfetcher-index");
		new File(root, "sub2").mkdir();
		new File(root, "sub1").mkdir();
		write(new File(root, "b.txt"), "beta document");
		write(new File(root, "a.txt"), "alpha document");
		write(new File(root, "sub1/inner.txt"), "inner document");

		registry = new IndexRegistry(indexDir, 10, 100);
		registry.load(Cancelable.nullCancelable); // Creates the searcher
		FileIndex index = new FileIndex(indexDir, root);
		index.update(new IndexingReporter(), Cancelable.nullCancelable);
		registry.addIndex(index);

		// A file added after indexing must still be listed
		write(new File(root, "c.txt"), "gamma document");
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		registry.getQueue().shutdown(new CancelHandler() {
			public CancelAction cancel() {
				return CancelAction.DISCARD;
			}
		});
		Util.deleteRecursively(root);
		Util.deleteRecursively(indexDir);
	}

	private static File createDir(String prefix) throws Exception {
		File dir = File.createTempFile(prefix, "");
		assertTrue(dir.delete() && dir.mkdir());
		return dir;
	}

	private static void write(File file, String text) throws Exception {
		java.nio.file.Files.write(file.toPath(), text.getBytes("UTF-8"));
	}

	private static List<String> namesOf(List<ResultDocument> docs) {
		List<String> names = new ArrayList<String>();
		for (ResultDocument doc : docs)
			names.add(doc.getFilename());
		return names;
	}

	@Test
	public void listsSubfoldersAndAllFiles() throws Exception {
		Searcher searcher = registry.getSearcher();
		List<ResultDocument> entries = searcher.listFolderEntries(
			registry.getIndexes().get(0), root);
		List<String> names = namesOf(entries);
		assertTrue(names.toString(), names.contains("sub1"));
		assertTrue(names.toString(), names.contains("sub2"));
		assertTrue(names.toString(), names.contains("a.txt"));
		assertTrue(names.toString(), names.contains("b.txt"));
		// Not indexed yet, but present on disk
		assertTrue(names.toString(), names.contains("c.txt"));
		// The subfolder's file belongs to the subfolder, not here
		assertFalse(names.toString(), names.contains("inner.txt"));

		// The folder rows come first and are marked as folders
		assertTrue(entries.get(0).isFolder());
		assertTrue(entries.get(1).isFolder());
		for (int i = 2; i < entries.size(); i++)
			assertFalse(entries.get(i).isFolder());
	}

	@Test
	public void folderRowsAreNotDocuments() throws Exception {
		Searcher searcher = registry.getSearcher();
		List<ResultDocument> entries = searcher.listFolderEntries(
			registry.getIndexes().get(0), root);
		ResultDocument folderRow = entries.get(0);
		assertTrue(folderRow.isFolder());
		// Folders must not be renamed, moved or previewed like documents
		assertFalse(folderRow.isPlainFileOnDisk());
		assertEquals(new File(root, folderRow.getFilename()).getPath(),
			folderRow.getPath().getCanonicalFile().getPath());
	}

	@Test
	public void listsSubfolderContents() throws Exception {
		Searcher searcher = registry.getSearcher();
		List<ResultDocument> entries = searcher.listFolderEntries(
			registry.getIndexes().get(0), new File(root, "sub1"));
		assertEquals(namesOf(entries).toString(), 1, entries.size());
		assertEquals("inner.txt", entries.get(0).getFilename());
	}

}
