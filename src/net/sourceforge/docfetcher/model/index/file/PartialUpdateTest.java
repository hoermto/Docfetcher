/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.index.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.Test;

import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.model.index.IndexingReporter;
import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.model.search.SearchTestSupport;
import net.sourceforge.docfetcher.util.Util;

/**
 * Tests the update of only some folders of an index, as after DocFetcher
 * moved or renamed files: the folders' files and new subfolders are read
 * again, other folders are left as they are (only a complete update notices
 * changes there).
 */
public final class PartialUpdateTest {

	private static List<String> found(SearchTestSupport support, String query) throws Exception {
		List<String> paths = new ArrayList<String>();
		for (ResultDocument doc : support.registry.getSearcher().search(query))
			paths.add(Util.getCanonicalFile(doc.getPath().getCanonicalFile()).getPath()
				.substring(Util.getCanonicalFile(support.root).getPath().length() + 1).replace('\\', '/'));
		return paths;
	}

	private static void write(File file, String text) throws Exception {
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), text.getBytes("UTF-8"));
	}

	@Test
	public void onlyTheGivenFolders() throws Exception {
		SearchTestSupport support = new SearchTestSupport(
			"Eingang/brief.txt", "Zitronenfalter im Eingang",
			"Archiv/2026/alt.txt", "Schwalbenschwanz im Archiv",
			"Archiv/2026/Tief/tief.txt", "Kleiner Fuchs ganz unten",
			"Andere/notiz.txt", "Admiral woanders");
		try {
			FileIndex index = (FileIndex) support.registry.getIndexes().get(0);
			File root = support.root;
			// What DocFetcher does: a letter moved into the archive, a folder moved in, a file renamed
			Files.move(new File(root, "Eingang/brief.txt").toPath(), new File(root, "Archiv/2026/brief.txt").toPath());
			write(new File(root, "Archiv/Neu/Unter/neu.txt"), "Distelfalter neu");
			Files.move(new File(root, "Archiv/2026/alt.txt").toPath(), new File(root, "Archiv/2026/2025-alt.txt").toPath());
			// Changed by someone else, unnoticed on a network drive
			write(new File(root, "Andere/zusatz.txt"), "Trauermantel unbemerkt");
			write(new File(root, "Archiv/2026/Tief/tief2.txt"), "Tagpfauenauge tief");

			index.updateFolders(Arrays.asList(new File(root, "Eingang"), new File(root, "Archiv/2026"),
				new File(root, "Archiv")), new IndexingReporter(), Cancelable.nullCancelable);
			support.registry.getSearcher().replaceLuceneSearcher();
			assertEquals(Arrays.asList("Archiv/2026/brief.txt"), found(support, "Zitronenfalter"));
			assertEquals(Arrays.asList("Archiv/2026/2025-alt.txt"), found(support, "Schwalbenschwanz"));
			assertEquals("A new folder completely", Arrays.asList("Archiv/Neu/Unter/neu.txt"), found(support, "Distelfalter"));
			assertEquals("Kept", Arrays.asList("Archiv/2026/Tief/tief.txt"), found(support, "Fuchs"));
			assertTrue("Other folders untouched", found(support, "Trauermantel").isEmpty());
			assertTrue("Existing subfolders untouched", found(support, "Tagpfauenauge").isEmpty());

			// A complete update notices everything
			index.update(new IndexingReporter(), Cancelable.nullCancelable);
			support.registry.getSearcher().replaceLuceneSearcher();
			assertEquals(Arrays.asList("Andere/zusatz.txt"), found(support, "Trauermantel"));
			assertEquals(Arrays.asList("Archiv/2026/Tief/tief2.txt"), found(support, "Tagpfauenauge"));
		}
		finally {
			support.dispose();
		}
	}

	@Test
	public void foldersNotInTheTreeYet() throws Exception {
		SearchTestSupport support = new SearchTestSupport("Archiv/alt.txt", "Schwalbenschwanz");
		try {
			FileIndex index = (FileIndex) support.registry.getIndexes().get(0);
			File root = Util.getCanonicalFile(support.root);
			// A new folder is visited through its nearest folder in the tree; folders elsewhere don't count
			List<File> visited = index.getVisitedFolders(new LinkedHashSet<File>(Arrays.asList(
				new File(root, "Archiv/Neu/Tiefer"), new File(root, "Archiv"),
				root.getParentFile())));
			assertEquals(Arrays.asList(new File(root, "Archiv")), visited);
			assertEquals(Arrays.asList(root), index.getVisitedFolders(Arrays.asList(new File(root, "Neu"))));
		}
		finally {
			support.dispose();
		}
	}

}
