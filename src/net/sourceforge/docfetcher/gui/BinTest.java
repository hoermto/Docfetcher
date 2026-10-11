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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilFile;

/**
 * Tests the DocFetcher bin: deleted files and folders lie side by side in the
 * bin, a hidden list knows where they came from, they can be restored there
 * or into another folder, and removing them from the bin deletes them (the
 * recycle bin of the system is switched off for the test).
 */
public final class BinTest {

	private File root;
	private File bin;

	@Before
	public void setUp() throws IOException {
		UtilFile.setTrashDisabled(true);
		root = Util.getCanonicalFile(Files.createTempDirectory("docfetcher-bin").toFile());
		bin = new File(root, "Papierkorb");
		SettingsConf.Str.BinFolder.set(bin.getPath());
	}

	@After
	public void tearDown() {
		SettingsConf.Str.BinFolder.set("");
		UtilFile.setTrashDisabled(false);
		UtilFile.deletePermanently(root);
	}

	private File file(String path, String text) throws IOException {
		File file = new File(root, path);
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
		return file;
	}

	@Test
	public void deletedFilesLieSideBySideAndKnowTheirOrigin() throws IOException {
		File letter = file("Steuer/brief.txt", "Steuer");
		File other = file("Miete/brief.txt", "Miete");
		File first = Bin.delete(letter);
		File second = Bin.delete(other);
		assertEquals(new File(bin, "brief.txt"), first);
		assertEquals(new File(bin, "brief-2.txt"), second);
		assertFalse(letter.exists());
		assertEquals(letter, Bin.getOrigin(bin, first));
		assertEquals(other, Bin.getOrigin(bin, second));
		assertEquals(second, Bin.find(bin, other));
		// The list of origins is hidden and isn't an entry of the bin
		File origins = new File(bin, Bin.ORIGINS);
		assertTrue(origins.isFile());
		if (Util.IS_WINDOWS)
			assertTrue(origins.isHidden());
		assertEquals(2, Bin.getEntries(bin).size());
		assertEquals(11, Bin.getSize(bin));
		assertTrue(Bin.contains(second));
		assertFalse(Bin.contains(letter));
	}

	@Test
	public void foldersAndFilesInThem() throws IOException {
		File folder = new File(root, "Steuer");
		file("Steuer/2025/bescheid.txt", "2025");
		File inBin = Bin.delete(folder);
		assertEquals(new File(bin, "Steuer"), inBin);
		File nested = new File(inBin, "2025/bescheid.txt");
		assertTrue(nested.isFile());
		assertEquals(new File(folder, "2025/bescheid.txt"), Bin.getOrigin(bin, nested));

		// A file from the deleted folder alone: back into the folder, which is created again
		File restored = Bin.restore(bin, nested, null);
		assertEquals(new File(folder, "2025/bescheid.txt"), restored);
		assertTrue(restored.isFile());
		// The rest of the folder stays in the bin, with its origin
		assertEquals(folder, Bin.getOrigin(bin, inBin));
	}

	@Test
	public void restoreToTheOriginOrIntoAFolder() throws IOException {
		File letter = file("Steuer/brief.txt", "Steuer");
		File inBin = Bin.delete(letter);
		// Meanwhile a new file has the name: the restored one gets a free name
		file("Steuer/brief.txt", "neu");
		File restored = Bin.restore(bin, inBin, null);
		assertEquals(new File(root, "Steuer/brief-2.txt"), restored);
		assertTrue(Bin.getEntries(bin).isEmpty());
		assertNull(Bin.find(bin, letter));

		// Into another folder (the inbox)
		File inbox = new File(root, "Eingang");
		inbox.mkdirs();
		inBin = Bin.delete(restored);
		assertEquals(new File(inbox, "brief-2.txt"), Bin.restore(bin, inBin, inbox));

		// Put into the bin by hand: no origin
		File byHand = new File(bin, "notiz.txt");
		Files.write(byHand.toPath(), "x".getBytes(StandardCharsets.UTF_8));
		assertNull(Bin.getOrigin(bin, byHand));
		try {
			Bin.restore(bin, byHand, null);
			fail();
		}
		catch (IOException e) {
			// Expected: the caller asks for a folder or uses the inbox
		}
	}

	@Test
	public void removingFromTheBinAndEmptyingIt() throws IOException {
		File a = Bin.delete(file("a.txt", "a"));
		File b = Bin.delete(file("b.txt", "b"));
		Bin.delete(file("Ordner/c.txt", "c").getParentFile());
		// What is in the bin already goes to the recycle bin of the system
		assertEquals(a, Bin.delete(a));
		assertFalse(a.exists());
		assertNull(Bin.getOrigin(bin, a));
		assertEquals(new File(root, "b.txt"), Bin.getOrigin(bin, b));

		assertTrue(Bin.empty(bin).isEmpty());
		assertTrue(Bin.getEntries(bin).isEmpty());
		assertEquals(Arrays.asList(bin.list()), Arrays.asList(Bin.ORIGINS));
	}

	@Test
	public void texts() throws IOException {
		assertEquals("Empty", Msg.bin_count.format(0, "0 KB"));
		assertEquals("One entry, 1 KB", Msg.bin_count.format(1, "1 KB"));
		assertEquals("3 entries, 2 MB", Msg.bin_count.format(3, "2 MB"));
		assertTrue(Msg.restore_question.format(1, "Eingang").startsWith("Where should the selected entry go?"));
		assertTrue(Msg.restore_question.format(2, "Eingang").startsWith("Where should the 2 selected entries go?"));
		Properties de = new Properties();
		try (InputStream in = new FileInputStream("dist/lang/Resource_de.properties")) {
			de.load(in);
		}
		assertEquals("Ein Eintrag, 1 KB", MessageFormat.format(de.getProperty("bin_count").replace("'", "''"), 1, "1 KB"));
		assertEquals("Leer", MessageFormat.format(de.getProperty("bin_count").replace("'", "''"), 0, "0 KB"));
		assertTrue(MessageFormat.format(de.getProperty("restore_question").replace("'", "''"), 1, "x")
			.startsWith("Wohin soll der ausgewählte Eintrag?"));

		assertEquals("1 KB", UtilFile.formatSize(1));
		assertEquals("512 KB", UtilFile.formatSize(512 * 1024));
		assertEquals(String.format("%.1f MB", 5.0), UtilFile.formatSize(5 * 1024 * 1024));
		assertEquals(String.format("%.1f GB", 2.0), UtilFile.formatSize(2L * 1024 * 1024 * 1024));
	}

	@Test
	public void withoutABinFolder() throws IOException {
		SettingsConf.Str.BinFolder.set("");
		File letter = file("brief.txt", "x");
		assertNull(Bin.getFolder());
		assertEquals(letter, Bin.delete(letter));
		assertFalse(letter.exists());
		assertFalse(bin.exists());
	}

}
