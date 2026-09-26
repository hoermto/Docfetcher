/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.search;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.index.IndexingReporter;
import net.sourceforge.docfetcher.model.index.Task.CancelAction;
import net.sourceforge.docfetcher.model.index.Task.CancelHandler;
import net.sourceforge.docfetcher.model.index.file.FileIndex;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.Util;

/**
 * For search tests: text files in a temporary folder, indexed and searchable
 * through a registry. Call {@link #dispose()} afterwards.
 */
public final class SearchTestSupport {

	public final File root;
	private final File indexDir;
	public final IndexRegistry registry;

	/** Creates the files (name, text, name, text, ...) and indexes them. */
	public SearchTestSupport(String... namesAndTexts) throws Exception {
		this(new java.util.HashMap<String, Long>(), namesAndTexts);
	}

	/**
	 * Creates the files (name, text, name, text, ...), gives them the
	 * modification times of the map (file name to milliseconds), if any, and
	 * indexes them.
	 */
	public SearchTestSupport(java.util.Map<String, Long> modified, String... namesAndTexts) throws Exception {
		AppUtil.Const.autoInit();
		root = createDir("docfetcher-search");
		indexDir = createDir("docfetcher-search-index");
		for (int i = 0; i + 1 < namesAndTexts.length; i += 2) {
			File file = new File(root, namesAndTexts[i]); // May be in a subfolder: "a/b.txt"
			file.getParentFile().mkdirs();
			java.nio.file.Files.write(file.toPath(), namesAndTexts[i + 1].getBytes("UTF-8"));
		}
		for (java.util.Map.Entry<String, Long> entry : modified.entrySet())
			assertTrue(new File(root, entry.getKey()).setLastModified(entry.getValue()));
		registry = new IndexRegistry(indexDir, 10, 100);
		registry.load(Cancelable.nullCancelable);
		FileIndex index = new FileIndex(indexDir, root);
		index.update(new IndexingReporter(), Cancelable.nullCancelable);
		registry.addIndex(index);
	}

	/** Returns the names of the files found by the query, sorted. */
	public Set<String> search(String query) throws Exception {
		Set<String> names = new TreeSet<String>();
		for (ResultDocument doc : registry.getSearcher().search(query))
			names.add(doc.getFilename());
		return names;
	}

	public void dispose() {
		registry.getQueue().shutdown(new CancelHandler() {
			public CancelAction cancel() {
				return CancelAction.DISCARD;
			}
		});
		for (File dir : new File[] { root, indexDir }) {
			try {
				Util.deleteRecursively(dir);
			}
			catch (Exception e) {
				dir.deleteOnExit(); // Index files may still be locked on Windows
			}
		}
	}

	private static File createDir(String prefix) throws Exception {
		File dir = File.createTempFile(prefix, "");
		assertTrue(dir.delete() && dir.mkdir());
		return dir;
	}

}
