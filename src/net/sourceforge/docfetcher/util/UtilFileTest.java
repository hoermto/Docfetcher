/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

/** Tests deleting files and creating folders. */
public final class UtilFileTest {

	private static File createDir() throws Exception {
		File dir = File.createTempFile("docfetcher-test", "");
		assertTrue(dir.delete() && dir.mkdir());
		dir.deleteOnExit();
		return dir;
	}

	@Test
	public void deletesAFile() throws Exception {
		File dir = createDir();
		File file = new File(dir, "doc.txt");
		java.nio.file.Files.write(file.toPath(), "content".getBytes("UTF-8"));
		assertTrue(file.isFile());
		assertTrue(UtilFile.delete(file));
		assertFalse("File is gone from its folder", file.exists());
		dir.delete();
	}

	@Test
	public void createsAFolder() throws Exception {
		File dir = createDir();
		File created = UtilFile.createFolder(dir, "Invoices 2026");
		assertNotNull(created);
		assertTrue(created.isDirectory());
		assertEquals("Invoices 2026", created.getName());
		// Creating it again returns the existing folder instead of failing
		assertEquals(created, UtilFile.createFolder(dir, "Invoices 2026"));
		created.delete();
		dir.delete();
	}

	@Test
	public void keepsSpacesInFolderNames() throws Exception {
		File dir = createDir();
		File created = UtilFile.createFolder(dir, "  Invoices 2026  ");
		assertNotNull(created);
		assertEquals("Invoices 2026", created.getName());
		created.delete();
		dir.delete();
	}

	@Test
	public void checksNamesForFilesAndFolders() {
		assertTrue(UtilFile.isValidName("Invoices 2026"));
		assertTrue(UtilFile.isValidName("  Rechnungen  "));
		assertTrue(UtilFile.isValidName("Ärger & Co."));
		assertFalse(UtilFile.isValidName(""));
		assertFalse(UtilFile.isValidName("   "));
		assertFalse(UtilFile.isValidName("."));
		assertFalse(UtilFile.isValidName(".."));
		assertFalse(UtilFile.isValidName("a/b"));
		assertFalse(UtilFile.isValidName("a\\b"));
		assertFalse(UtilFile.isValidName("a:b"));
		assertFalse(UtilFile.isValidName("a?b"));
		assertFalse(UtilFile.isValidName("a*b"));
		assertFalse(UtilFile.isValidName("a\"b"));
		assertFalse(UtilFile.isValidName("a<b"));
		assertFalse(UtilFile.isValidName("a|b"));
	}

	@Test
	public void rejectsImpossibleFolderNames() throws Exception {
		File dir = createDir();
		assertNull(UtilFile.createFolder(dir, ""));
		assertNull(UtilFile.createFolder(dir, "   "));
		assertNull(UtilFile.createFolder(dir, ".."));
		assertNull(UtilFile.createFolder(dir, "a/b"));
		assertNull(UtilFile.createFolder(dir, "a:b"));
		assertNull(UtilFile.createFolder(dir, "a?b"));
		File missing = new File(dir, "nope");
		assertNull(UtilFile.createFolder(new File(missing, "deeper"), "child"));
		dir.delete();
	}

}
