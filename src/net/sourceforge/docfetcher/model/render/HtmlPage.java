/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.render;

import java.util.Collection;
import java.util.regex.Pattern;

import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Assembles a complete, self-contained HTML page around a rendered document
 * body: A common stylesheet, optional highlighting of search terms and
 * optional scrolling to the first match.
 */
public final class HtmlPage {

	/** Options controlling the highlighting of search terms in the page. */
	public static final class Highlighting {
		private final Collection<String> terms;
		private final String colorHex; // e.g. "#ffff00"
		private final boolean scrollToFirstMatch;

		public Highlighting(@NotNull Collection<String> terms,
							@NotNull String colorHex,
							boolean scrollToFirstMatch) {
			this.terms = Util.checkNotNull(terms);
			this.colorHex = Util.checkNotNull(colorHex);
			this.scrollToFirstMatch = scrollToFirstMatch;
		}
	}

	private static final String CSS =
		"html, body { margin: 0; padding: 0; }\n" +
		"body { font-family: 'Segoe UI', Arial, Helvetica, sans-serif; font-size: 13px; " +
			"color: #222; background: #fff; padding: 12px 16px; line-height: 1.4; }\n" +
		"p { margin: 0 0 0.6em 0; }\n" +
		"h1, h2, h3, h4, h5, h6 { margin: 1em 0 0.4em 0; line-height: 1.2; }\n" +
		"h1 { font-size: 1.8em; } h2 { font-size: 1.5em; } h3 { font-size: 1.25em; }\n" +
		"h4 { font-size: 1.1em; } h5, h6 { font-size: 1em; }\n" +
		"table { border-collapse: collapse; margin: 0.5em 0 1em 0; }\n" +
		"td, th { border: 1px solid #c8c8c8; padding: 2px 6px; vertical-align: top; }\n" +
		"th { background: #f0f0f0; font-weight: normal; color: #555; text-align: center; }\n" +
		"td.num { text-align: right; }\n" +
		"img { max-width: 100%; height: auto; }\n" +
		"ul, ol { margin: 0 0 0.6em 0; }\n" +
		".list-item { margin-bottom: 0.2em; }\n" +
		".sheet-title { margin-top: 1.2em; }\n" +
		".note { color: #777; font-style: italic; }\n" +
		"mark { color: inherit; padding: 0; }\n";

	private HtmlPage() {}

	/**
	 * Wraps the given HTML body content (without the surrounding
	 * <code>body</code> tag) into a complete HTML document. Additional CSS may
	 * be supplied by the renderer.
	 */
	@NotNull
	public static String wrap(	@NotNull String bodyContent,
								@Nullable String extraCss,
								@Nullable Highlighting highlighting) {
		StringBuilder sb = new StringBuilder(bodyContent.length() + 4096);
		sb.append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n");
		sb.append("<meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\n");
		sb.append("<style>\n").append(CSS);
		if (highlighting != null)
			sb.append("mark { background: ").append(highlighting.colorHex).append("; }\n");
		if (extraCss != null)
			sb.append(extraCss).append('\n');
		sb.append("</style>\n</head>\n<body>\n");
		sb.append(bodyContent);
		sb.append("\n");
		if (highlighting != null && !highlighting.terms.isEmpty())
			appendHighlightScript(sb, highlighting);
		sb.append("</body>\n</html>\n");
		return sb.toString();
	}

	/**
	 * Appends a script that wraps all occurrences of the search terms in the
	 * page's text nodes into <code>mark</code> elements. Terms are matched
	 * case-insensitively and only as whole words.
	 */
	private static void appendHighlightScript(	@NotNull StringBuilder sb,
												@NotNull Highlighting hl) {
		StringBuilder terms = new StringBuilder();
		for (String term : hl.terms) {
			if (term == null || term.isEmpty())
				continue;
			if (terms.length() > 0)
				terms.append(',');
			terms.append('"').append(escapeJs(term)).append('"');
		}
		if (terms.length() == 0)
			return;

		sb.append("<script>\n(function() {\n");
		sb.append("var terms = [").append(terms).append("];\n");
		sb.append(
			"function esc(s) { return s.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&'); }\n" +
			"function bounded(t) {\n" +
			"  var s = esc(t);\n" +
			"  if (/^\\w/.test(t)) s = '\\\\b' + s;\n" +
			"  if (/\\w$/.test(t)) s = s + '\\\\b';\n" +
			"  return s;\n" +
			"}\n" +
			"var re;\n" +
			"try { re = new RegExp('(' + terms.map(bounded).join('|') + ')', 'gi'); }\n" +
			"catch (e) { return; }\n" +
			"function walk(node) {\n" +
			"  if (node.nodeType === 3) {\n" +
			"    var text = node.nodeValue;\n" +
			"    if (!re.test(text)) return;\n" +
			"    re.lastIndex = 0;\n" +
			"    var frag = document.createDocumentFragment();\n" +
			"    var last = 0, m;\n" +
			"    while ((m = re.exec(text)) !== null) {\n" +
			"      if (m.index > last) frag.appendChild(document.createTextNode(text.substring(last, m.index)));\n" +
			"      var mark = document.createElement('mark');\n" +
			"      mark.appendChild(document.createTextNode(m[0]));\n" +
			"      frag.appendChild(mark);\n" +
			"      last = m.index + m[0].length;\n" +
			"      if (m[0].length === 0) re.lastIndex++;\n" +
			"    }\n" +
			"    if (last < text.length) frag.appendChild(document.createTextNode(text.substring(last)));\n" +
			"    node.parentNode.replaceChild(frag, node);\n" +
			"  }\n" +
			"  else if (node.nodeType === 1 && node.nodeName !== 'SCRIPT' && node.nodeName !== 'STYLE') {\n" +
			"    var children = Array.prototype.slice.call(node.childNodes);\n" +
			"    for (var i = 0; i < children.length; i++) walk(children[i]);\n" +
			"  }\n" +
			"}\n" +
			"walk(document.body);\n");
		if (hl.scrollToFirstMatch) {
			sb.append(
				"var first = document.querySelector('mark');\n" +
				"if (first && first.scrollIntoView) first.scrollIntoView({block: 'center'});\n");
		}
		sb.append("})();\n</script>\n");
	}

	private static final Pattern JS_ESCAPE = Pattern.compile("[\"\\\\\\n\\r\\t</]");

	@NotNull
	private static String escapeJs(@NotNull String s) {
		if (!JS_ESCAPE.matcher(s).find())
			return s;
		StringBuilder sb = new StringBuilder(s.length() + 8);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '"': sb.append("\\\""); break;
			case '\\': sb.append("\\\\"); break;
			case '\n': sb.append("\\n"); break;
			case '\r': sb.append("\\r"); break;
			case '\t': sb.append("\\t"); break;
			case '<': sb.append("\\u003c"); break;
			case '/': sb.append("\\/"); break;
			default: sb.append(c);
			}
		}
		return sb.toString();
	}

	/**
	 * Escapes the given text for insertion into HTML. Line breaks are
	 * converted to <code>br</code> tags and tabs to a sequence of spaces.
	 */
	@NotNull
	public static String escape(@Nullable String text) {
		if (text == null || text.isEmpty())
			return "";
		StringBuilder sb = new StringBuilder(text.length() + 16);
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
			case '&': sb.append("&amp;"); break;
			case '<': sb.append("&lt;"); break;
			case '>': sb.append("&gt;"); break;
			case '"': sb.append("&quot;"); break;
			case '\n': sb.append("<br>"); break;
			case '\r': break;
			case '\t': sb.append("&nbsp;&nbsp;&nbsp;&nbsp;"); break;
			default: sb.append(c);
			}
		}
		return sb.toString();
	}

	/**
	 * Escapes the given text for insertion into an HTML attribute value.
	 */
	@NotNull
	public static String escapeAttr(@Nullable String text) {
		if (text == null)
			return "";
		return text.replace("&", "&amp;").replace("\"", "&quot;")
			.replace("<", "&lt;").replace(">", "&gt;");
	}

}
