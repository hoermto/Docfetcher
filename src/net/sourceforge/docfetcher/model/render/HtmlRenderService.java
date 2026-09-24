/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.render;

import java.io.File;
import java.io.IOException;

import net.sourceforge.docfetcher.model.render.HtmlPage.Highlighting;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Entry point for converting documents into self-contained HTML pages for
 * display in the embedded browser of the preview pane.
 */
public final class HtmlRenderService {

	/** The document types that can be rendered as HTML. */
	public enum Kind {
		WORD, EXCEL, RTF
	}

	private HtmlRenderService() {}

	/**
	 * Renders the given file of the given kind into a complete HTML page. The
	 * returned string is a full HTML document (including
	 * <code>html</code>, <code>head</code> and <code>body</code> tags) with all
	 * resources such as images embedded.
	 *
	 * @throws IOException
	 *             if the file could not be read or converted. The exception
	 *             message is suitable for display to the user.
	 */
	@NotNull
	public static String render(@NotNull File file,
								@NotNull Kind kind,
								@Nullable Highlighting highlighting)
			throws IOException {
		Util.checkNotNull(file, kind);
		String body;
		String extraCss = null;
		try {
			switch (kind) {
			case WORD:
				body = DocxHtmlRenderer.render(file);
				break;
			case EXCEL:
				body = XlsxHtmlRenderer.render(file);
				extraCss = XlsxHtmlRenderer.getExtraCss();
				break;
			case RTF:
				body = RtfHtmlRenderer.render(file);
				break;
			default:
				throw new IllegalArgumentException();
			}
		}
		catch (IOException e) {
			throw e;
		}
		catch (OutOfMemoryError e) {
			throw e;
		}
		catch (RuntimeException e) {
			// POI and Tika throw various unchecked exceptions on corrupt files
			throw new IOException(e.toString(), e);
		}
		catch (LinkageError e) {
			// e.g. NoClassDefFoundError for missing optional dependencies
			throw new IOException(e.toString(), e);
		}
		return HtmlPage.wrap(body, extraCss, highlighting);
	}

}
