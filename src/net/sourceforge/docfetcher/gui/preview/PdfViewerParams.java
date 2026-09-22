/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui.preview;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collection;

import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * Parameters for the PDF viewers built into browsers (Edge, Chrome) and
 * Acrobat, which are passed as a URL fragment, e.g.
 * {@code file:///C:/doc.pdf#search=invoice}.
 */
final class PdfViewerParams {

	/** Terms longer than this are cut off, to keep the URL short. */
	private static final int MAX_LENGTH = 100;

	private PdfViewerParams() {}

	/**
	 * Returns the URL fragment that makes the viewer search for the given
	 * terms and jump to the first match, or an empty string if none of the
	 * terms can be searched for. Terms with wildcards or other special
	 * characters are left out, since the viewer would search for them
	 * literally.
	 */
	@NotNull
	public static String createSearchFragment(@NotNull Collection<String> searchTerms) {
		StringBuilder sb = new StringBuilder();
		for (String term : searchTerms) {
			String trimmed = term == null ? "" : term.trim();
			if (trimmed.isEmpty() || !trimmed.matches("[\\p{L}\\p{N}_'-]+"))
				continue;
			if (sb.length() + trimmed.length() + 1 > MAX_LENGTH)
				break;
			if (sb.length() > 0)
				sb.append(' ');
			sb.append(trimmed);
		}
		if (sb.length() == 0)
			return "";
		try {
			return "#search=" + URLEncoder.encode(sb.toString(), "UTF-8");
		}
		catch (UnsupportedEncodingException e) {
			return ""; // UTF-8 is always available
		}
	}

}
