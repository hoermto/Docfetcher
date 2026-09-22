/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/** Tests the search parameter passed to the browser's PDF viewer. */
public final class PdfViewerParamsTest {

	@Test
	public void joinsTheTerms() {
		assertEquals("#search=rechnung+stadtwerke",
			PdfViewerParams.createSearchFragment(
				Arrays.asList("rechnung", "stadtwerke")));
	}

	@Test
	public void encodesNonAsciiTerms() {
		assertEquals("#search=gr%C3%BC%C3%9Fe",
			PdfViewerParams.createSearchFragment(Arrays.asList("grüße")));
	}

	@Test
	public void skipsTermsTheViewerCannotSearchFor() {
		// Wildcards, phrases and field queries would be searched literally
		assertEquals("#search=invoice", PdfViewerParams.createSearchFragment(
			Arrays.asList("invoice", "inv*", "a b", "filename:x", "")));
	}

	@Test
	public void returnsNothingWithoutUsableTerms() {
		assertEquals("", PdfViewerParams.createSearchFragment(
			Collections.<String> emptyList()));
		assertEquals("", PdfViewerParams.createSearchFragment(
			Arrays.asList("*", "  ")));
	}

	@Test
	public void keepsTheFragmentShort() {
		List<String> terms = new ArrayList<String>();
		for (int i = 0; i < 50; i++)
			terms.add("term" + i);
		String fragment = PdfViewerParams.createSearchFragment(terms);
		assertTrue(fragment, fragment.length() < 140);
		assertTrue(fragment, fragment.startsWith("#search=term0+term1+"));
	}

}
