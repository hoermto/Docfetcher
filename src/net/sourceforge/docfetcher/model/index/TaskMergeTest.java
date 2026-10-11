/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;

import org.junit.Test;

import net.sourceforge.docfetcher.model.index.Task.IndexAction;
import net.sourceforge.docfetcher.model.search.SearchTestSupport;

/**
 * Tests how update requests for the same index join a waiting update: the
 * folders add up, and an update of the whole index includes them.
 */
public final class TaskMergeTest {

	@Test
	public void foldersAddUpAndTheWholeIndexWins() throws Exception {
		SearchTestSupport support = new SearchTestSupport("a.txt", "eins");
		try {
			File a = new File(support.root, "A");
			File b = new File(support.root, "B");
			Task task = new Task(support.registry.getQueue(), support.registry.getIndexes().get(0),
				IndexAction.UPDATE, Arrays.asList(a));
			task.mergeFolders(new LinkedHashSet<File>(Arrays.asList(b, a)));
			assertEquals(new LinkedHashSet<File>(Arrays.asList(a, b)), task.getFolders());
			task.mergeFolders(null);
			assertNull("The whole index", task.getFolders());
			task.mergeFolders(new LinkedHashSet<File>(Arrays.asList(a)));
			assertNull("Stays complete", task.getFolders());
			assertNull(new Task(support.registry.getQueue(), support.registry.getIndexes().get(0),
				IndexAction.UPDATE).getFolders());
		}
		finally {
			support.dispose();
		}
	}

}
