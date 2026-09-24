/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import net.sourceforge.docfetcher.gui.FileOperationHistory.Entry;
import net.sourceforge.docfetcher.gui.FileOperationHistory.Kind;

/** Tests the undo history of file operations. */
public final class FileOperationHistoryTest {

	private final FileOperationHistory history = FileOperationHistory.getInstance();

	@Before
	public void setUp() {
		history.clear();
	}

	@Test
	public void returnsTheOperationsInReverseOrder() {
		history.add(Kind.RENAME, Arrays.asList(new File("/a/one.txt")),
			Arrays.asList(new File("/a/first.txt")));
		history.add(Kind.MOVE, Arrays.asList(new File("/a/two.txt")),
			Arrays.asList(new File("/b/two.txt")));

		Entry last = history.peek();
		assertNotNull(last);
		assertEquals(Kind.MOVE, last.getKind());
		assertEquals("two.txt", last.getFirstName());
		assertEquals(last, history.pop());

		Entry first = history.pop();
		assertNotNull(first);
		assertEquals(Kind.RENAME, first.getKind());
		assertEquals(new File("/a/first.txt"), first.getTargets().get(0));
		assertNull(history.pop());
	}

	@Test
	public void keepsOnlyTheNewestOperations() {
		for (int i = 0; i < FileOperationHistory.MAX_ENTRIES + 5; i++) {
			history.add(Kind.MOVE, Arrays.asList(new File("/a/file" + i + ".txt")),
				Arrays.asList(new File("/b/file" + i + ".txt")));
		}
		int count = 0;
		Entry entry;
		while ((entry = history.pop()) != null) {
			count++;
			// The newest operation comes first
			if (count == 1)
				assertEquals("file" + (FileOperationHistory.MAX_ENTRIES + 4) + ".txt",
					entry.getFirstName());
		}
		assertEquals(FileOperationHistory.MAX_ENTRIES, count);
	}

	@Test
	public void deletionsHaveNoTargets() {
		history.add(Kind.DELETE, Arrays.asList(new File("/a/gone.txt")),
			Collections.<File> emptyList());
		Entry entry = history.peek();
		assertNotNull(entry);
		assertEquals(Kind.DELETE, entry.getKind());
		assertTrue(entry.getTargets().isEmpty());
		assertEquals(1, entry.getFileCount());
	}

	@Test
	public void ignoresEmptyOperations() {
		history.add(Kind.MOVE, Collections.<File> emptyList(),
			Collections.<File> emptyList());
		assertNull(history.peek());
	}

}
