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
import java.nio.file.attribute.FileTime;
import java.util.Arrays;

import org.junit.Test;

import net.sourceforge.docfetcher.model.index.Task.CancelAction;
import net.sourceforge.docfetcher.model.index.Task.CancelHandler;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.Util;

/**
 * Tests the index folders reported as obsolete on loading. Reported: a
 * folder of DocFetcher 1.0.3 (a name ending with a number, index files, no
 * tree-index.ser). Not reported: an empty leftover of a deleted index (seen
 * after a rebuild of the package: Windows couldn't remove the folder at once),
 * which is removed quietly, and an index being built right now, which has no
 * tree-index.ser yet.
 */
public final class ObsoleteIndexFilesTest {

	private static final FileTime OLD = FileTime.fromMillis(1262304000000L); // 2010

	@Test
	public void onlyOldIndexesAreReported() throws Exception {
		AppUtil.Const.autoInit();
		File indexDir = Files.createTempDirectory("docfetcher-obsolete").toFile();
		File building = new File(indexDir, "inbox_1791544589022");
		File leftover = new File(indexDir, "inbox_1791542179954");
		File old = new File(indexDir, "Dokumente_1234567890");
		assertTrue(building.mkdir() && leftover.mkdir() && old.mkdir());
		Files.write(new File(old, "segments_1").toPath(), new byte[] {1});
		Files.setAttribute(old.toPath(), "creationTime", OLD);
		Files.setAttribute(leftover.toPath(), "creationTime", OLD);

		assertTrue(IndexRegistry.isCreatedInThisRun(building));
		assertFalse(IndexRegistry.isCreatedInThisRun(old));

		IndexRegistry registry = new IndexRegistry(indexDir, 10, 100);
		try {
			IndexLoadingProblems problems = registry.load(Cancelable.nullCancelable);
			assertEquals(Arrays.asList(old), problems.getObsoleteFiles());
			assertFalse("Empty leftover removed", leftover.exists());
			assertTrue("Index being built kept, although still empty", building.isDirectory());
		}
		finally {
			registry.getQueue().shutdown(new CancelHandler() {
				public CancelAction cancel() {
					return CancelAction.DISCARD;
				}
			});
			Util.deleteRecursively(indexDir);
		}
	}

}
