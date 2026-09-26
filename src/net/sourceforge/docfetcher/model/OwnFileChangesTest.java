/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import net.sourceforge.docfetcher.model.search.SearchTestSupport;
import net.sourceforge.docfetcher.util.Event;

/**
 * Tests that the changes DocFetcher makes itself (recognized text stored in
 * scans) don't start another index update, while other changes still do.
 */
public final class OwnFileChangesTest {

	private static void append(File file, String text) throws Exception {
		Files.write(file.toPath(), text.getBytes("UTF-8"), StandardOpenOption.APPEND);
	}

	/**
	 * Changes the file like the text recognition does: the new content is
	 * written to a temporary file, which then replaces the file, and the
	 * date is set back. The watcher sees the replacement as a new file.
	 */
	private static void changeAsDocFetcher(File file) throws Exception {
		long lastModified = file.lastModified();
		File temp = new File(file.getParentFile(), file.getName() + OwnFileChanges.TEMP_SUFFIX);
		byte[] content = Files.readAllBytes(file.toPath());
		Files.write(temp.toPath(), content);
		append(temp, " recognized text");
		OwnFileChanges.begin(file);
		try {
			Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			assertTrue("While writing", OwnFileChanges.isOwnChange(file));
			assertTrue(file.setLastModified(lastModified));
		}
		finally {
			OwnFileChanges.end(file);
		}
	}

	@Test
	public void recognizesOwnChangesOnly() throws Exception {
		File dir = Files.createTempDirectory("docfetcher-own").toFile();
		try {
			File scan = new File(dir, "scan.txt");
			File other = new File(dir, "other.txt");
			Files.write(scan.toPath(), "scan".getBytes("UTF-8"));
			Files.write(other.toPath(), "other".getBytes("UTF-8"));

			changeAsDocFetcher(scan);
			assertTrue(OwnFileChanges.isOwnChange(scan));
			assertTrue("Same file, other spelling", OwnFileChanges.isOwnChange(new File(dir, "./SCAN.txt")));
			assertFalse("Other files are not affected", OwnFileChanges.isOwnChange(other));
			assertTrue("Temporary files", OwnFileChanges.isOwnChange(new File(dir, "x.pdf" + OwnFileChanges.TEMP_SUFFIX)));

			// Changed by someone else afterwards: a real change again
			Thread.sleep(20);
			append(scan, " edited by the user");
			assertFalse(OwnFileChanges.isOwnChange(scan));

			// Deleted or renamed by DocFetcher, e.g. a converted TIFF file
			File tiff = new File(dir, "fax.tif");
			OwnFileChanges.removed(tiff);
			assertTrue(OwnFileChanges.isOwnChange(tiff));
			Files.write(tiff.toPath(), "new".getBytes("UTF-8"));
			assertFalse("Created again by someone else", OwnFileChanges.isOwnChange(tiff));
		}
		finally {
			for (File file : dir.listFiles())
				file.delete();
			dir.delete();
		}
	}

	/**
	 * With the real folder watcher: storing recognized text starts no index
	 * update, but a change of another file in the meantime does.
	 */
	@Test
	public void folderWatcherIgnoresOwnChanges() throws Exception {
		SearchTestSupport support = new SearchTestSupport(
			"scan.txt", "a scanned letter", "other.txt", "another letter");
		FolderWatcher watcher = new FolderWatcher(support.registry);
		final AtomicInteger updates = new AtomicInteger();
		support.registry.getQueue().evtQueueEmpty.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				updates.incrementAndGet();
			}
		});
		try {
			Thread.sleep(1500); // Let the watcher set up its watches

			changeAsDocFetcher(new File(support.root, "scan.txt"));
			Thread.sleep(4000); // The watcher waits a second before it starts an update
			assertEquals("No update for our own change", 0, updates.get());

			append(new File(support.root, "other.txt"), " changed");
			for (int i = 0; i < 100 && updates.get() == 0; i++)
				Thread.sleep(100);
			assertTrue("Other changes still start an update", updates.get() > 0);
		}
		finally {
			watcher.shutdown();
			support.dispose();
		}
	}

}
