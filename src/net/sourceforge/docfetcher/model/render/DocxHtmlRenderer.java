/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.render;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.IRunElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.VerticalAlign;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlink;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;

import com.google.common.io.Closeables;

import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Converts a Word 2007+ document (docx, docm, dotx, dotm) into HTML using
 * Apache POI's XWPF API. The conversion preserves the document structure
 * (headings, paragraphs, lists, tables, inline images) and basic character
 * formatting (bold, italic, underline, strikethrough, color, size), but not
 * the page layout.
 */
final class DocxHtmlRenderer {

	private static final Pattern HEADING_PATTERN = Pattern.compile(
		"(?i)^(?:heading|überschrift|titre|título|titolo|kop)\\s*(\\d)$");

	/** Maximum size of an embedded image in bytes; larger images are skipped. */
	private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

	private static final Pattern JC_VAL_PATTERN = Pattern.compile("val=\"([^\"]*)\"");

	private final XWPFDocument doc;
	private final StringBuilder sb = new StringBuilder(16 * 1024);

	private DocxHtmlRenderer(@NotNull XWPFDocument doc) {
		this.doc = doc;
	}

	/**
	 * Renders the given docx file and returns the HTML body content.
	 */
	@NotNull
	public static String render(@NotNull File file) throws IOException {
		InputStream in = new FileInputStream(file);
		XWPFDocument doc = null;
		try {
			doc = new XWPFDocument(in);
			DocxHtmlRenderer renderer = new DocxHtmlRenderer(doc);
			renderer.renderBody(doc.getBodyElements());
			return renderer.sb.toString();
		}
		finally {
			if (doc != null)
				Closeables.closeQuietly(doc);
			Closeables.closeQuietly(in);
		}
	}

	private void renderBody(@NotNull List<IBodyElement> elements) {
		for (IBodyElement element : elements) {
			if (element instanceof XWPFParagraph)
				renderParagraphSafely((XWPFParagraph) element);
			else if (element instanceof XWPFTable)
				renderTable((XWPFTable) element);
			// Content controls (SDTs) and other elements are ignored
		}
	}

	/**
	 * Renders a paragraph; if POI chokes on it (e.g. on attribute values of
	 * newer Word versions it doesn't know, such as the 'start' alignment),
	 * the paragraph's plain text is emitted instead, so that one odd
	 * paragraph doesn't spoil the whole preview.
	 */
	private void renderParagraphSafely(@NotNull XWPFParagraph p) {
		int start = sb.length();
		try {
			renderParagraph(p);
		}
		catch (RuntimeException e) {
			sb.setLength(start);
			String text;
			try {
				text = p.getText();
			}
			catch (RuntimeException e2) {
				text = "";
			}
			if (text != null && !text.isEmpty())
				sb.append("<p>").append(HtmlPage.escape(text)).append("</p>\n");
		}
	}

	/**
	 * Returns the paragraph alignment, mapping the values 'start' and 'end'
	 * of newer documents (unknown to this POI version) to left and right.
	 */
	@Nullable
	private static ParagraphAlignment getAlignment(@NotNull XWPFParagraph p) {
		try {
			return p.getAlignment();
		}
		catch (RuntimeException e) {
			try {
				CTPPr ppr = p.getCTP().getPPr();
				if (ppr == null || !ppr.isSetJc())
					return null;
				// Typed access validates and throws again, so read the XML
				Matcher m = JC_VAL_PATTERN.matcher(ppr.getJc().xmlText());
				String raw = m.find() ? m.group(1) : "";
				if (raw.equals("start"))
					return ParagraphAlignment.LEFT;
				if (raw.equals("end"))
					return ParagraphAlignment.RIGHT;
				return null;
			}
			catch (RuntimeException e2) {
				return null;
			}
		}
	}

	private void renderParagraph(@NotNull XWPFParagraph p) {
		if (p.isPageBreak())
			sb.append("<hr>\n");

		int headingLevel = getHeadingLevel(p);
		String tag = headingLevel > 0 ? "h" + headingLevel : "p";

		StringBuilder style = new StringBuilder();
		ParagraphAlignment alignment = getAlignment(p);
		if (alignment == ParagraphAlignment.CENTER)
			style.append("text-align:center;");
		else if (alignment == ParagraphAlignment.RIGHT)
			style.append("text-align:right;");
		else if (alignment == ParagraphAlignment.BOTH || alignment == ParagraphAlignment.DISTRIBUTE)
			style.append("text-align:justify;");

		boolean isListItem = p.getNumID() != null;
		int indentLevel = 0;
		if (isListItem) {
			BigInteger ilvl = p.getNumIlvl();
			if (ilvl != null)
				indentLevel = ilvl.intValue();
			style.append("margin-left:").append(20 + indentLevel * 20).append("px;");
		}
		else {
			int indent = p.getIndentationLeft(); // in twentieths of a point
			if (indent > 0)
				style.append("margin-left:").append(indent / 20).append("pt;");
		}

		sb.append('<').append(tag);
		if (isListItem)
			sb.append(" class=\"list-item\"");
		if (style.length() > 0)
			sb.append(" style=\"").append(style).append('"');
		sb.append('>');

		if (isListItem)
			sb.append(getListMarker(p)).append(' ');

		renderRuns(p);
		sb.append("</").append(tag).append(">\n");
	}

	@NotNull
	private String getListMarker(@NotNull XWPFParagraph p) {
		String fmt = null;
		try {
			fmt = p.getNumFmt();
		}
		catch (RuntimeException e) {
			// Numbering definition may be missing or malformed
		}
		if (fmt == null || fmt.equals("bullet"))
			return "•";
		// Numbered lists: We don't track counters, so use a neutral marker
		return "–";
	}

	private int getHeadingLevel(@NotNull XWPFParagraph p) {
		String styleId = p.getStyleID();
		if (styleId == null)
			return 0;
		String name = styleId;
		try {
			XWPFStyles styles = doc.getStyles();
			if (styles != null) {
				XWPFStyle style = styles.getStyle(styleId);
				if (style != null && style.getName() != null)
					name = style.getName();
			}
		}
		catch (RuntimeException e) {
			// Fall back to the style ID
		}
		name = name.trim();
		if (name.equalsIgnoreCase("Title") || name.equalsIgnoreCase("Titel"))
			return 1;
		Matcher m = HEADING_PATTERN.matcher(name);
		if (m.matches())
			return Math.min(6, Math.max(1, Integer.parseInt(m.group(1))));
		// Style IDs like "Heading1" or "berschrift1" (umlaut stripped)
		m = Pattern.compile("(?i)^[a-zäöü]+(\\d)$").matcher(styleId);
		if (m.matches() && styleId.toLowerCase().contains("heading"))
			return Math.min(6, Math.max(1, Integer.parseInt(m.group(1))));
		return 0;
	}

	private void renderRuns(@NotNull XWPFParagraph p) {
		for (IRunElement runElement : p.getIRuns()) {
			if (runElement instanceof XWPFHyperlinkRun) {
				XWPFHyperlinkRun run = (XWPFHyperlinkRun) runElement;
				String url = null;
				try {
					XWPFHyperlink link = run.getHyperlink(doc);
					if (link != null)
						url = link.getURL();
				}
				catch (RuntimeException e) {
					// Ignore broken hyperlinks
				}
				if (url != null)
					sb.append("<a href=\"").append(HtmlPage.escapeAttr(url)).append("\">");
				renderRun(run);
				if (url != null)
					sb.append("</a>");
			}
			else if (runElement instanceof XWPFRun) {
				renderRun((XWPFRun) runElement);
			}
			else {
				// e.g. XWPFSDT (content control): render its text only
				String text = runElement.toString();
				if (text != null && !text.isEmpty())
					sb.append(HtmlPage.escape(text));
			}
		}
	}

	private static boolean isUnderlined(@NotNull XWPFRun run) {
		try {
			return run.getUnderline() != UnderlinePatterns.NONE;
		}
		catch (RuntimeException e) {
			return false; // Unknown underline value
		}
	}

	@Nullable
	private static VerticalAlign getVerticalAlign(@NotNull XWPFRun run) {
		try {
			return run.getSubscript();
		}
		catch (RuntimeException e) {
			return null; // Unknown vertical alignment value
		}
	}

	private void renderRun(@NotNull XWPFRun run) {
		String text = run.text();
		boolean hasText = text != null && !text.isEmpty();

		List<XWPFPicture> pictures = null;
		try {
			pictures = run.getEmbeddedPictures();
		}
		catch (RuntimeException e) {
			// Ignore malformed picture references
		}

		if (!hasText && (pictures == null || pictures.isEmpty()))
			return;

		if (hasText) {
			StringBuilder style = new StringBuilder();
			String color = run.getColor();
			if (color != null && !color.equalsIgnoreCase("auto") && color.matches("[0-9A-Fa-f]{6}"))
				style.append("color:#").append(color).append(';');
			int fontSize = run.getFontSize();
			if (fontSize > 0)
				style.append("font-size:").append(fontSize).append("pt;");

			boolean bold = run.isBold();
			boolean italic = run.isItalic();
			boolean underline = isUnderlined(run);
			boolean strike = run.isStrikeThrough();
			VerticalAlign vAlign = getVerticalAlign(run);
			boolean sub = vAlign == VerticalAlign.SUBSCRIPT;
			boolean sup = vAlign == VerticalAlign.SUPERSCRIPT;

			if (style.length() > 0)
				sb.append("<span style=\"").append(style).append("\">");
			if (bold) sb.append("<b>");
			if (italic) sb.append("<i>");
			if (underline) sb.append("<u>");
			if (strike) sb.append("<s>");
			if (sub) sb.append("<sub>");
			if (sup) sb.append("<sup>");

			sb.append(HtmlPage.escape(text));

			if (sup) sb.append("</sup>");
			if (sub) sb.append("</sub>");
			if (strike) sb.append("</s>");
			if (underline) sb.append("</u>");
			if (italic) sb.append("</i>");
			if (bold) sb.append("</b>");
			if (style.length() > 0)
				sb.append("</span>");
		}

		if (pictures != null) {
			for (XWPFPicture picture : pictures)
				renderPicture(picture);
		}
	}

	private void renderPicture(@NotNull XWPFPicture picture) {
		XWPFPictureData data;
		try {
			data = picture.getPictureData();
		}
		catch (RuntimeException e) {
			return;
		}
		if (data == null)
			return;
		byte[] bytes = data.getData();
		if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES)
			return;
		String mimeType = getMimeType(data.suggestFileExtension());
		if (mimeType == null)
			return;
		sb.append("<img src=\"data:").append(mimeType).append(";base64,");
		sb.append(Base64.getEncoder().encodeToString(bytes));
		sb.append("\" alt=\"\">");
	}

	@Nullable
	static String getMimeType(@Nullable String extension) {
		if (extension == null)
			return null;
		extension = extension.toLowerCase();
		if (extension.equals("png")) return "image/png";
		if (extension.equals("jpg") || extension.equals("jpeg")) return "image/jpeg";
		if (extension.equals("gif")) return "image/gif";
		if (extension.equals("bmp")) return "image/bmp";
		if (extension.equals("tiff") || extension.equals("tif")) return "image/tiff";
		if (extension.equals("svg")) return "image/svg+xml";
		return null; // EMF, WMF, PICT etc. cannot be displayed by browsers
	}

	private void renderTable(@NotNull XWPFTable table) {
		sb.append("<table>\n");
		for (XWPFTableRow row : table.getRows()) {
			sb.append("<tr>");
			for (XWPFTableCell cell : row.getTableCells()) {
				int colspan = getGridSpan(cell);
				sb.append("<td");
				if (colspan > 1)
					sb.append(" colspan=\"").append(colspan).append('"');
				sb.append('>');
				renderBody(cell.getBodyElements());
				sb.append("</td>");
			}
			sb.append("</tr>\n");
		}
		sb.append("</table>\n");
	}

	private static int getGridSpan(@NotNull XWPFTableCell cell) {
		try {
			CTTcPr tcPr = cell.getCTTc().getTcPr();
			if (tcPr != null && tcPr.getGridSpan() != null) {
				BigInteger span = tcPr.getGridSpan().getVal();
				if (span != null)
					return span.intValue();
			}
		}
		catch (RuntimeException e) {
			// Ignore
		}
		return 1;
	}

}
