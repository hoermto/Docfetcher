/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.search;

import static org.junit.Assert.assertEquals;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sourceforge.docfetcher.enums.SettingsConf;

/**
 * Tests that the words of a query also match the file name and the folder
 * names of the path: a word counts as found if it is in the text, the file
 * name or a folder name.
 */
public final class NameMatchTest {

	private static SearchTestSupport support;

	@BeforeClass
	public static void setUpClass() throws Exception {
		SettingsConf.Bool.MatchFilenames.set(true);
		SettingsConf.Bool.TolerantSearch.set(true);
		support = new SearchTestSupport(
			"Mieter/Rosenweg 12/vertrag-a.txt", "Kaltmiete 900 EUR monatlich",
			"Mieter/Lindenallee 3/vertrag-b.txt", "Kaltmiete 700 EUR monatlich",
			"Archiv/Müllerstraße/brief.txt", "Hallo zusammen",
			"Archiv/notiz-rosenweg.txt", "Schlüssel abgeholt");
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		support.dispose();
	}

	private static String search(String query) throws Exception {
		return support.search(query).toString();
	}

	@Test
	public void wordsMatchFolderNames() throws Exception {
		assertEquals("[vertrag-a.txt]", search("Rosenweg Kaltmiete"));
		assertEquals("[vertrag-a.txt, vertrag-b.txt]", search("Mieter Kaltmiete"));
		assertEquals("Folder and file name", "[notiz-rosenweg.txt, vertrag-a.txt]", search("Rosenweg"));
		assertEquals("Part of a folder name", "[vertrag-b.txt]", search("linden* monatlich"));
	}

	@Test
	public void excludedWordsExcludeFolders() throws Exception {
		assertEquals("[vertrag-b.txt]", search("Kaltmiete -Rosenweg"));
		assertEquals("[brief.txt, notiz-rosenweg.txt]", search("Archiv -Mieter"));
	}

	@Test
	public void folderNamesAreTolerant() throws Exception {
		assertEquals("[brief.txt]", search("Mullerstrasse"));
		assertEquals("[brief.txt]", search("müllerstraße Hallo"));
	}

	@Test
	public void switchedOffOnlyTheTextCounts() throws Exception {
		SettingsConf.Bool.MatchFilenames.set(false);
		try {
			assertEquals("[]", search("Rosenweg Kaltmiete"));
			assertEquals("[vertrag-a.txt, vertrag-b.txt]", search("Kaltmiete"));
		}
		finally {
			SettingsConf.Bool.MatchFilenames.set(true);
		}
	}

}
