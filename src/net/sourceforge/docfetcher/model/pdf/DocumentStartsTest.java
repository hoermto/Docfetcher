/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.pdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import net.sourceforge.docfetcher.model.pdf.DocumentStarts.Reason;
import net.sourceforge.docfetcher.model.pdf.DocumentStarts.Suggestion;
import net.sourceforge.docfetcher.model.pdf.PdfPages.PageInfo;
import net.sourceforge.docfetcher.model.pdf.PdfPages.Range;
import net.sourceforge.docfetcher.model.pdf.PdfPages.Role;

/** Tests the suggestion of document starts from the text of scanned pages. */
public final class DocumentStartsTest {

	private static PageInfo page(String text) {
		return new PageInfo(text, false, SeparatorSheet.isSeparator(text));
	}

	private static PageInfo blank() {
		return new PageInfo("", true, false);
	}

	private static Role[] roles(List<Suggestion> suggestions) {
		Role[] roles = new Role[suggestions.size()];
		for (int i = 0; i < roles.length; i++)
			roles[i] = suggestions.get(i).role;
		return roles;
	}

	private static final String INVOICE_1 =
		"Stadtwerke Musterstadt GmbH\nHerrn Torsten Beispiel\nRechnung Nr. RE-2026-4711\n" +
		"Kundennummer 5566778 Rechnungsdatum 03.09.2026\nStrom Abrechnungszeitraum Verbrauch " +
		"Arbeitspreis Grundpreis Nettobetrag Umsatzsteuer\nSeite 1 von 2";
	private static final String INVOICE_2 =
		"Stadtwerke Musterstadt GmbH\nRechnung Nr. RE-2026-4711\nZählerstand alt neu Differenz " +
		"Zählernummer Messstelle Abschlag Zahlungsplan\nSeite 2 von 2";
	private static final String LETTER_1 =
		"Versicherung AG\nHerrn Torsten Beispiel\nIhre Hausratversicherung\nSehr geehrter Herr Beispiel,\n" +
		"vielen Dank für Ihre Nachricht. Wir haben den Vertrag angepasst und senden Ihnen den neuen " +
		"Versicherungsschein. Die Beiträge ändern sich ab dem nächsten Monat wie folgt.";
	private static final String LETTER_2 =
		"Versicherungsschein Hausrat Versicherungssumme Wohnfläche Beitrag jährlich Selbstbeteiligung " +
		"Fahrraddiebstahl Glasbruch Überspannung Deckung Versicherungsnehmer Beispiel Beitrag Hausrat";
	private static final String RECIPE =
		"Apfelkuchen mit Streuseln\nZutaten: Mehl, Butter, Zucker, Äpfel, Zimt, Eier, Backpulver. " +
		"Den Teig kneten, die Äpfel schälen und in Spalten schneiden, im Ofen goldbraun backen.";

	private static final String CONTRACT_1 =
		"Mietvertrag zwischen Vermieter Hans Muster und Mieter Torsten Beispiel über die Wohnung " +
		"im zweiten Obergeschoss. Die Miete beträgt monatlich. Nebenkosten werden vorausgezahlt.";
	private static final String CONTRACT_2 =
		"Die Kaution beträgt drei Monatsmieten. Der Mieter darf die Wohnung nur mit Zustimmung " +
		"des Vermieters untervermieten. Schönheitsreparaturen trägt der Mieter.";

	@Test
	public void similarTextWithoutOtherHints() {
		List<Suggestion> s = DocumentStarts.suggest(Arrays.asList(
			page(RECIPE), page(CONTRACT_1), page(CONTRACT_2)));
		assertEquals(Reason.DIFFERENT_TEXT, s.get(1).reason);
		assertEquals(Reason.SIMILAR_TEXT, s.get(2).reason);
	}

	@Test
	public void findsPageNumbers() {
		assertEquals(Arrays.toString(new int[] { 2, 3 }), Arrays.toString(DocumentStarts.findPageNumber("x\nSeite 2 von 3")));
		assertEquals(Arrays.toString(new int[] { 1, 4 }), Arrays.toString(DocumentStarts.findPageNumber("Page 1 of 4")));
		assertEquals(Arrays.toString(new int[] { 3, 0 }), Arrays.toString(DocumentStarts.findPageNumber("text\n - 3 - \nmore")));
		assertNull(DocumentStarts.findPageNumber("Seite 5 von 3")); // Not a valid page number
		assertNull(DocumentStarts.findPageNumber("Rechnung vom 1.2.2026"));
	}

	@Test
	public void findsNumbersButNoDatesOrYears() {
		assertEquals("[RE-2026-4711, 5566778]",
			DocumentStarts.findNumbers("Rechnung RE-2026-4711, Kunde 5566778, vom 03.09.2026 im Jahr 2026").toString());
	}

	@Test
	public void suggestsStartsForAMixedStack() {
		List<PageInfo> pages = Arrays.asList(
			page(INVOICE_1), page(INVOICE_2), // Page numbers
			page(LETTER_1), page(LETTER_2),   // Salutation, then similar words
			page(RECIPE));                    // Different words
		List<Suggestion> s = DocumentStarts.suggest(pages);
		assertEquals(Reason.FIRST_PAGE, s.get(0).reason);
		assertEquals(Reason.PAGE_NUMBER_CONTINUES, s.get(1).reason);
		assertEquals("2/2", s.get(1).detail);
		assertEquals(Role.START, s.get(2).role);
		assertEquals(Reason.AFTER_LAST_PAGE, s.get(2).reason); // Page 2 of 2 was the last one
		assertEquals(Role.CONTINUE, s.get(3).role);
		assertEquals(Role.START, s.get(4).role);
		assertEquals(Reason.DIFFERENT_TEXT, s.get(4).reason);
		assertEquals(Arrays.asList(new Range(0, 2), new Range(2, 4), new Range(4, 5)),
			PdfPages.fromRoles(roles(s)));
	}

	@Test
	public void salutationAndSharedNumbers() {
		String reminder = "Stadtwerke Musterstadt GmbH\nSehr geehrter Herr Beispiel, leider konnten wir " +
			"zur Rechnung RE-2026-4711 noch keinen Zahlungseingang feststellen.";
		String attachment = "Anlage zur Rechnung RE-2026-4711: Zählerstände";
		List<Suggestion> s = DocumentStarts.suggest(Arrays.asList(
			page(LETTER_2), page(reminder), page(attachment)));
		assertEquals(Reason.SALUTATION, s.get(1).reason);
		assertEquals(Reason.SHARED_NUMBER, s.get(2).reason);
		assertEquals("RE-2026-4711", s.get(2).detail);
	}

	private static final String SHEET = "DOCFETCHER SEPARATOR\nTrennblatt\nDOCFETCHER SEPARATOR";

	@Test
	public void withSeparatorSheetsBlankPagesAreOnlyLeftOut() {
		// A letter with a blank back side, a separator sheet, an invoice with a blank back side
		List<PageInfo> pages = Arrays.asList(page(LETTER_1), blank(), page(LETTER_2),
			page(SHEET), page(INVOICE_1), blank(), page(INVOICE_2));
		List<Suggestion> s = DocumentStarts.atSeparators(pages);
		assertEquals(Reason.BLANK_LEFT_OUT, s.get(1).reason);
		assertEquals(Role.OMIT, s.get(1).role);
		assertEquals(Reason.SEPARATOR, s.get(3).reason);
		assertEquals(Reason.AFTER_SEPARATOR, s.get(4).reason);
		assertEquals(Role.CONTINUE, s.get(6).role);
		assertEquals(Arrays.asList(new Range(Arrays.asList(0, 2)), new Range(Arrays.asList(4, 6))),
			PdfPages.fromRoles(roles(s)));
		assertEquals("1, 3", new Range(Arrays.asList(0, 2)).toString());
		// The suggestion from the text treats the blank pages the same way
		assertEquals(PdfPages.fromRoles(roles(s)), PdfPages.fromRoles(roles(DocumentStarts.suggest(pages))));
	}

	@Test
	public void withoutSeparatorSheetsBlankPagesSeparate() {
		List<PageInfo> pages = Arrays.asList(page(LETTER_1), page(LETTER_2), blank(), blank(), page(RECIPE));
		List<Suggestion> s = DocumentStarts.atSeparators(pages);
		assertEquals(Reason.SEPARATOR, s.get(2).reason);
		assertEquals(Reason.AFTER_SEPARATOR, s.get(4).reason);
		assertEquals(Arrays.asList(new Range(0, 2), new Range(4, 5)), PdfPages.fromRoles(roles(s)));
	}

	@Test
	public void recognizesTheSeparatorSheetDespiteOcrErrors() {
		assertTrue(SeparatorSheet.isSeparator("xx DOCFETCHER SEPARATOR yy"));
		assertTrue(SeparatorSheet.isSeparator("D0CFETCHER SEPARAT0R")); // 0 instead of O
		assertTrue(SeparatorSheet.isSeparator("DocFetcher Sepa-\nrator"));
		assertTrue(SeparatorSheet.isSeparator("DOCFETCHER SEPERATOR"));
		assertFalse(SeparatorSheet.isSeparator("DocFetcher ist ein Suchprogramm"));
		assertFalse(SeparatorSheet.isSeparator(LETTER_1));
	}

	@Test
	public void littleTextContinues() {
		List<Suggestion> s = DocumentStarts.suggest(Arrays.asList(page(LETTER_1), page("Anlage")));
		assertEquals(Reason.LITTLE_TEXT, s.get(1).reason);
		assertEquals(Role.CONTINUE, s.get(1).role);
	}

	@Test
	public void rolesToRanges() {
		Role S = Role.START, C = Role.CONTINUE, X = Role.OMIT;
		// Leaving out doesn't end a document, only START does
		assertEquals(Arrays.asList(new Range(1, 3), new Range(Arrays.asList(3, 6))),
			PdfPages.fromRoles(new Role[] { X, S, C, S, X, X, C }));
		assertEquals(Arrays.asList(new Range(1, 3), new Range(3, 4), new Range(6, 7)),
			PdfPages.fromRoles(new Role[] { X, S, C, S, X, X, S }));
		assertEquals(new ArrayList<Range>(), PdfPages.fromRoles(new Role[] { X, X }));
	}

}
