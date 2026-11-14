/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.render;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.rtf.RTFParser;
import org.apache.tika.sax.ToHTMLContentHandler;

import com.google.common.io.Closeables;

import net.sourceforge.docfetcher.model.parse.ParseService;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.UtilFile;

/**
 * Converts an RTF document into HTML using Tika's RTF parser, which emits
 * structured XHTML (paragraphs, bold/italic/underline, lists, hyperlinks).
 */
final class RtfHtmlRenderer {

	private RtfHtmlRenderer() {}

	/**
	 * Renders the given RTF file and returns the HTML body content.
	 */
	@NotNull
	public static String render(@NotNull File file) throws IOException {
		InputStream in = new BufferedInputStream(UtilFile.openForReading(file));
		try {
			/*
			 * Note: Wrapping the handler in a BodyContentHandler would drop
			 * the namespace declarations, causing the XHTML serializer to
			 * fail, so the whole document is serialized and the body content
			 * is extracted afterwards.
			 */
			ToHTMLContentHandler htmlHandler = new ToHTMLContentHandler();
			Metadata metadata = new Metadata();

			/*
			 * Bug #1230: If the RTF file contains an image, the RTF parser will
			 * look for Tika parsers that can parse the image. This involves
			 * reading the tika-mimetypes.xml file, which cannot be found
			 * without the following custom class loader, due to the fact that
			 * we're running Tika outside its expected environment.
			 */
			org.apache.tika.parser.ParseContext tikaContext = ParseService.tikaContext();
			TikaConfig tikaConfig = new TikaConfig(new ClassLoader() {
				protected URL findResource(String name) {
					if ("tika-mimetypes.xml".equals(name)) {
						return getResource("org/apache/tika/mime/tika-mimetypes.xml");
					}
			        return null;
			    }
			});
			tikaContext.set(TikaConfig.class, tikaConfig);

			new RTFParser().parse(in, htmlHandler, metadata, tikaContext);
			return extractBody(htmlHandler.toString());
		}
		catch (IOException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IOException(e.getMessage(), e);
		}
		catch (AssertionError e) {
			// See RtfParser: Tika's RTF parser may throw AssertionErrors
			throw new IOException(e.getMessage(), e);
		}
		finally {
			Closeables.closeQuietly(in);
		}
	}
	
	/**
	 * Returns the content between the <code>body</code> tags of the given
	 * HTML document, or the whole document if no body tags are found.
	 */
	@NotNull
	static String extractBody(@NotNull String html) {
		int start = html.indexOf("<body");
		if (start < 0)
			return html;
		start = html.indexOf('>', start);
		if (start < 0)
			return html;
		int end = html.lastIndexOf("</body>");
		if (end < start)
			return html.substring(start + 1);
		return html.substring(start + 1, end);
	}

}
