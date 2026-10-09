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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import net.sourceforge.docfetcher.model.Cancelable;

/**
 * Tests finding duplicates by their text: identical texts whatever the
 * spelling of punctuation and case, nearly identical texts (the same letter
 * scanned twice, with recognition errors), but not different letters of the
 * same kind or texts too short to compare; through the index, oldest first.
 */
public final class DuplicatesTest {

	private static final String LETTER = "Sehr geehrter Herr Meier, hiermit bestätigen wir den Eingang Ihrer "
		+ "Zahlung für die Wohnung im Rosenweg 12. Die Kaltmiete für den Monat Oktober beträgt 850,00 Euro, "
		+ "die Nebenkosten betragen 210,00 Euro. Bitte überweisen Sie künftige Beträge bis zum dritten Werktag "
		+ "eines Monats auf das bekannte Konto. Bei Fragen erreichen Sie uns montags bis freitags von neun bis "
		+ "zwölf Uhr unter der bekannten Rufnummer. Mit freundlichen Grüßen, Ihre Hausverwaltung Schmidt und Partner.";

	/** The letter with a few recognition errors, as from a second scan. */
	private static final String SCANNED = LETTER.replace("Eingang", "Eingarg").replace("Oktober", "0ktober")
		.replace("künftige", "kunftige");

	/** A letter of the same kind to another tenant: similar, but not a duplicate. */
	private static final String OTHER = "Sehr geehrte Frau Schulz, wir haben Ihre Kündigung der Wohnung in der "
		+ "Lindenallee 7 zum Jahresende erhalten. Für die Übergabe schlagen wir den zweiten Januar um zehn Uhr "
		+ "vor. Bitte bringen Sie alle Schlüssel mit, auch die für Keller und Briefkasten. Die Kaution erstatten "
		+ "wir nach der Abrechnung der Nebenkosten. Mit freundlichen Grüßen, Ihre Hausverwaltung Schmidt und Partner.";

	@Test
	public void identicalAndNearlyIdenticalTexts() {
		DuplicateFinder finder = new DuplicateFinder(7);
		finder.add(1, LETTER);
		finder.add(2, LETTER.toUpperCase().replace(",", " ;").replace(".", ""));
		finder.add(3, SCANNED);
		finder.add(4, OTHER);
		// Too short to compare
		finder.add(5, "Rechnung 12,50 Euro");
		finder.add(6, "Rechnung 12,50 Euro");
		List<DuplicateFinder.Group> groups = finder.groups();
		assertEquals(1, groups.size());
		assertEquals(Arrays.asList(1, 2, 3), groups.get(0).keys);
		assertFalse("Nearly identical: the scan differs", groups.get(0).identical);

		// Without the scan: identical
		DuplicateFinder exact = new DuplicateFinder(5);
		exact.add(1, LETTER);
		exact.add(2, LETTER + "  ");
		exact.add(4, OTHER);
		assertEquals(1, exact.groups().size());
		assertTrue(exact.groups().get(0).identical);
	}

	@Test
	public void groupCountInTheTexts() throws Exception {
		assertTrue(net.sourceforge.docfetcher.enums.Msg.dup_found.format(1).startsWith("One group of duplicates"));
		assertTrue(net.sourceforge.docfetcher.enums.Msg.dup_found.format(3).startsWith("3 groups of duplicates"));
		java.util.Properties de = new java.util.Properties();
		java.io.InputStream in = new java.io.FileInputStream("dist/lang/Resource_de.properties");
		try {
			de.load(in);
		}
		finally {
			in.close();
		}
		String pattern = de.getProperty("dup_found").replace("'", "''"); // As Msg.format does it
		assertTrue(java.text.MessageFormat.format(pattern, 1).startsWith("Eine Gruppe von Duplikaten"));
		assertTrue(java.text.MessageFormat.format(pattern, 4).startsWith("4 Gruppen von Duplikaten"));
	}

	@Test
	public void copiesUnderOtherNamesAreIdentical() throws Exception {
		// The index adds the words of the file name to the text; they don't count
		SearchTestSupport support = new SearchTestSupport(
			"Mieter/Zahlungsbestaetigung Meier.txt", LETTER,
			"Posteingang/scan_2026-10-01 (2).txt", LETTER,
			"Mieter/kuendigung.txt", OTHER);
		try {
			List<Searcher.DuplicateGroup> groups = support.registry.getSearcher().findDuplicates(
				Cancelable.nullCancelable, new Searcher.DuplicateProgress() {
					public void update(int done, int total) {
					}
				}, null, true);
			assertEquals(1, groups.size());
			assertEquals(Searcher.DuplicateGroup.Kind.IDENTICAL_TEXT, groups.get(0).kind);
			assertEquals(2, groups.get(0).docs.size());
		}
		finally {
			support.dispose();
		}
	}

	private static List<Searcher.DuplicateGroup> find(SearchTestSupport support, java.io.File folder,
			boolean contentOnly) throws Exception {
		return support.registry.getSearcher().findDuplicates(Cancelable.nullCancelable,
			new Searcher.DuplicateProgress() {
				public void update(int done, int total) {
				}
			}, folder, contentOnly);
	}

	@Test
	public void folderAndNameAndSize() throws Exception {
		SearchTestSupport support = new SearchTestSupport(
			"Archiv/2025/brief.txt", LETTER,
			"Posteingang/brief.txt", LETTER,
			"Archiv/2026/notiz.txt", "kurz",
			"Posteingang/notiz.txt", "kurz");
		try {
			// Only within the folder (with subfolders): the copy in the inbox doesn't count
			assertEquals(0, find(support, new java.io.File(support.root, "Archiv"), true).size());
			assertEquals(0, find(support, new java.io.File(support.root, "Archiv/2025"), false).size());
			// The root folder, or all indexes ('Global'): the letter and its copy
			assertEquals(1, find(support, support.root, true).size());
			List<Searcher.DuplicateGroup> all = find(support, null, true);
			assertEquals(1, all.size());
			assertEquals(Searcher.DuplicateGroup.Kind.IDENTICAL_TEXT, all.get(0).kind);

			// Not only by content: same name and size too, also without text to compare;
			// the letters are in the text group already, not again
			List<Searcher.DuplicateGroup> withNames = find(support, null, false);
			assertEquals(2, withNames.size());
			assertEquals(Searcher.DuplicateGroup.Kind.IDENTICAL_TEXT, withNames.get(0).kind);
			assertEquals(Searcher.DuplicateGroup.Kind.SAME_NAME_AND_SIZE, withNames.get(1).kind);
			assertEquals("notiz.txt", withNames.get(1).docs.get(0).getFilename());
			assertEquals(2, withNames.get(1).docs.size());
		}
		finally {
			support.dispose();
		}
	}

	@Test
	public void throughTheIndex() throws Exception {
		long now = System.currentTimeMillis();
		Map<String, Long> modified = new HashMap<String, Long>();
		modified.put("Mieter/brief.txt", now - 30 * 24 * 3600 * 1000L);
		modified.put("Posteingang/brief-kopie.txt", now - 2 * 24 * 3600 * 1000L);
		modified.put("Posteingang/scan.txt", now - 1 * 24 * 3600 * 1000L);
		SearchTestSupport support = new SearchTestSupport(modified,
			"Mieter/brief.txt", LETTER,
			"Posteingang/brief-kopie.txt", LETTER,
			"Posteingang/scan.txt", SCANNED,
			"Mieter/kuendigung.txt", OTHER,
			"Mieter/kurz.txt", "kurz");
		try {
			final List<Integer> progress = new ArrayList<Integer>();
			List<Searcher.DuplicateGroup> groups = support.registry.getSearcher().findDuplicates(
				Cancelable.nullCancelable, new Searcher.DuplicateProgress() {
					public void update(int done, int total) {
						progress.add(done);
					}
				}, null, true);
			assertEquals(1, groups.size());
			List<String> names = new ArrayList<String>();
			for (ResultDocument doc : groups.get(0).docs)
				names.add(doc.getFilename());
			assertEquals("Oldest first", Arrays.asList("brief.txt", "brief-kopie.txt", "scan.txt"), names);
			assertFalse(progress.isEmpty());

			// Canceled at once: nothing compared, nothing found
			assertEquals(0, support.registry.getSearcher().findDuplicates(new Cancelable() {
				public boolean isCanceled() {
					return true;
				}
			}, new Searcher.DuplicateProgress() {
				public void update(int done, int total) {
				}
			}, null, true).size());
		}
		finally {
			support.dispose();
		}
	}

}
