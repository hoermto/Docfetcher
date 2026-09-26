/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.mcp;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sourceforge.docfetcher.gui.FolderPanel;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.search.HighlightedString;
import net.sourceforge.docfetcher.model.search.Range;
import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.collect.AlphanumComparator;

/**
 * The tools of the MCP server: read-only access to the indexed documents.
 * {@code search} finds documents like the search field, {@code read_document}
 * returns the text of a document (in pages of at most
 * {@link #MAX_CHARS_LIMIT} characters), {@code list_folder} lists the indexed
 * folders and their contents. Only files inside the indexed folders (and the
 * fixed move targets) can be read or listed.
 */
final class McpTools {

	static final int DEFAULT_MAX_CHARS = 20000;
	static final int MAX_CHARS_LIMIT = 100000;
	private static final int DEFAULT_MAX_RESULTS = 20;
	private static final int MAX_RESULTS_LIMIT = 100;
	private static final int SNIPPET_RESULTS = 10;
	private static final int SNIPPET_CHARS = 300;
	private static final int MAX_FOLDER_ENTRIES = 500;

	/** A wrong argument or a document that can't be read; the message goes to the client. */
	static final class ToolException extends Exception {
		ToolException(@NotNull String message) {
			super(message);
		}
	}

	private final IndexRegistry registry;

	McpTools(@NotNull IndexRegistry registry) {
		this.registry = registry;
	}

	// ---- Tool definitions

	@NotNull
	List<Object> definitions() {
		List<Object> tools = new ArrayList<Object>();
		tools.add(tool("search",
			"Full-text search in the user's documents indexed by DocFetcher (letters, invoices, "
			+ "contracts, scans, office files, emails; often German). Google-style query: all words "
			+ "must occur, -word excludes, \"exact phrase\", OR for alternatives, wildcards like "
			+ "steuer*. German compound words need a wildcard in front: *steuer* also finds "
			+ "Einkommensteuer, *miete* finds Kaltmiete. Umlauts and OCR errors are tolerated "
			+ "(Muller finds Müller). Returns the "
			+ "path, name, date and a text snippet of each document. For 'latest' or 'newest' "
			+ "questions use sort=newest; for a period use modified_after/modified_before.",
			props(
				"query", prop("string", "The search words, e.g. 'Steuer Januar' or '\"Rosenweg 5\" Miete'"),
				"max_results", prop("integer", "Maximum number of documents (default 20, at most 100)"),
				"sort", enumProp("Order of the results (default: relevance)", "relevance", "newest", "oldest"),
				"modified_after", prop("string", "Only documents modified on or after this date (YYYY-MM-DD)"),
				"modified_before", prop("string", "Only documents modified before this date (YYYY-MM-DD)"),
				"folder", prop("string", "Only documents in this folder or its subfolders (absolute path)")),
			"query"));
		tools.add(tool("read_document",
			"Returns the text of a document found with search or list_folder, to answer questions "
			+ "about its content. Long documents are returned in parts: continue with the returned "
			+ "next_offset. If the user asks for the document itself, give them its path instead.",
			props(
				"path", prop("string", "Absolute path of the document, as returned by search or list_folder"),
				"offset", prop("integer", "Character offset to start at (default 0)"),
				"max_chars", prop("integer", "Maximum number of characters (default " + DEFAULT_MAX_CHARS
					+ ", at most " + MAX_CHARS_LIMIT + ")")),
			"path"));
		tools.add(tool("list_folder",
			"Lists a folder: subfolders first, then the files, newest first, with dates. Without a "
			+ "path, lists the indexed folders (e.g. the inbox and the archive folders).",
			props("path", prop("string", "Absolute path of a folder; omit for the indexed folders")),
			null));
		return tools;
	}

	@NotNull
	private static Map<String, Object> tool(@NotNull String name, @NotNull String description,
			@NotNull Map<String, Object> properties, @Nullable String required) {
		Map<String, Object> schema = new LinkedHashMap<String, Object>();
		schema.put("type", "object");
		schema.put("properties", properties);
		if (required != null)
			schema.put("required", Arrays.asList(required));
		Map<String, Object> tool = new LinkedHashMap<String, Object>();
		tool.put("name", name);
		tool.put("description", description);
		tool.put("inputSchema", schema);
		Map<String, Object> annotations = new LinkedHashMap<String, Object>();
		annotations.put("readOnlyHint", Boolean.TRUE);
		tool.put("annotations", annotations);
		return tool;
	}

	@NotNull
	private static Map<String, Object> props(@NotNull Object... namesAndProps) {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		for (int i = 0; i + 1 < namesAndProps.length; i += 2)
			map.put((String) namesAndProps[i], namesAndProps[i + 1]);
		return map;
	}

	@NotNull
	private static Map<String, Object> prop(@NotNull String type, @NotNull String description) {
		return props("type", type, "description", description);
	}

	@NotNull
	private static Map<String, Object> enumProp(@NotNull String description, @NotNull String... values) {
		Map<String, Object> map = prop("string", description);
		map.put("enum", Arrays.asList(values));
		return map;
	}

	// ---- Tool calls

	/**
	 * Runs the tool and returns its result as a JSON-able value (maps and
	 * lists); for read_document a list of [metadata, text].
	 */
	@NotNull
	Object call(@NotNull String name, @NotNull Map<String, Object> args) throws ToolException {
		if (name.equals("search"))
			return search(args);
		if (name.equals("read_document"))
			return readDocument(args);
		if (name.equals("list_folder"))
			return listFolder(args);
		throw new ToolException("Unknown tool: " + name);
	}

	@NotNull
	private Map<String, Object> search(@NotNull Map<String, Object> args) throws ToolException {
		String query = string(args, "query");
		if (query == null || query.trim().isEmpty())
			throw new ToolException("The argument 'query' is missing.");
		int maxResults = Util.clamp(integer(args, "max_results", DEFAULT_MAX_RESULTS), 1, MAX_RESULTS_LIMIT);
		String sort = string(args, "sort");
		Date after = date(args, "modified_after");
		Date before = date(args, "modified_before");
		String folderArg = string(args, "folder");
		File folder = folderArg == null ? null : resolve(folderArg);

		List<ResultDocument> found;
		try {
			found = registry.getSearcher().search(query.trim());
		}
		catch (Exception e) {
			throw new ToolException("Search failed: " + e.getMessage());
		}
		List<ResultDocument> docs = new ArrayList<ResultDocument>();
		for (ResultDocument doc : found) {
			Date date = dateOf(doc);
			if (after != null && (date == null || date.before(after)))
				continue;
			if (before != null && (date == null || !date.before(before)))
				continue;
			if (folder != null && !Util.contains(folder, new File(doc.getPathStr())))
				continue;
			docs.add(doc);
		}
		if ("newest".equals(sort) || "oldest".equals(sort)) {
			final boolean newest = "newest".equals(sort);
			Collections.sort(docs, new Comparator<ResultDocument>() {
				public int compare(ResultDocument d1, ResultDocument d2) {
					long t1 = time(dateOf(d1));
					long t2 = time(dateOf(d2));
					return newest ? Long.compare(t2, t1) : Long.compare(t1, t2);
				}
			});
		}

		List<Object> results = new ArrayList<Object>();
		for (int i = 0; i < docs.size() && i < maxResults; i++)
			results.add(describe(docs.get(i), i < SNIPPET_RESULTS));
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("query", query);
		result.put("total_found", docs.size());
		result.put("returned", results.size());
		result.put("results", results);
		return result;
	}

	@NotNull
	private Map<String, Object> describe(@NotNull ResultDocument doc, boolean withSnippet) {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("path", doc.getPathStr());
		map.put("filename", doc.getFilename());
		String title = doc.getTitle();
		if (!title.isEmpty() && !title.equals(doc.getFilename()))
			map.put("title", title);
		map.put("type", doc.getType());
		Date date = dateOf(doc);
		if (date != null)
			map.put(doc.isEmail() ? "sent" : "modified", format(date));
		if (doc.isEmail()) {
			map.put("sender", doc.getSender());
		}
		else {
			map.put("size_kb", doc.getSizeInKB());
			if (!doc.getAuthors().isEmpty())
				map.put("author", doc.getAuthors());
		}
		if (doc.getPageCount() > 0)
			map.put("pages", doc.getPageCount());
		if (withSnippet && !doc.isEmail()) {
			String snippet = snippet(doc);
			if (snippet != null)
				map.put("snippet", snippet);
		}
		return map;
	}

	/** Text around the first match, or the beginning of the text; null if it can't be read. */
	@Nullable
	private static String snippet(@NotNull ResultDocument doc) {
		try {
			HighlightedString text = doc.getHighlightedText();
			String string = text.getString();
			int start = 0;
			if (!text.getRanges().isEmpty()) {
				Range first = text.getRanges().get(0);
				start = Math.max(0, first.start - SNIPPET_CHARS / 3);
			}
			int end = Math.min(string.length(), start + SNIPPET_CHARS);
			String snippet = string.substring(start, end).replaceAll("\\s+", " ").trim();
			return (start > 0 ? "..." : "") + snippet + (end < string.length() ? "..." : "");
		}
		catch (Throwable e) {
			return null; // E.g. file moved since indexing, or unreadable
		}
	}

	@NotNull
	private List<Object> readDocument(@NotNull Map<String, Object> args) throws ToolException {
		String path = string(args, "path");
		if (path == null || path.trim().isEmpty())
			throw new ToolException("The argument 'path' is missing.");
		File file = resolve(path);
		checkAllowed(file);
		if (!file.isFile())
			throw new ToolException("Not a file (or not found): " + file.getPath());
		int offset = Math.max(0, integer(args, "offset", 0));
		int maxChars = Util.clamp(integer(args, "max_chars", DEFAULT_MAX_CHARS), 1, MAX_CHARS_LIMIT);

		ResultDocument doc = findDocument(file);
		String text;
		try {
			text = doc.getText();
		}
		catch (Throwable e) {
			throw new ToolException("The text of the document could not be read: " + e.getMessage());
		}
		int start = Math.min(offset, text.length());
		int end = Math.min(text.length(), start + maxChars);

		Map<String, Object> meta = describe(doc, false);
		meta.put("total_chars", text.length());
		meta.put("offset", start);
		meta.put("returned_chars", end - start);
		if (end < text.length())
			meta.put("next_offset", end);
		return Arrays.<Object> asList(meta, text.substring(start, end));
	}

	/** The document of a file, with its index data if it is indexed. */
	@NotNull
	private ResultDocument findDocument(@NotNull File file) throws ToolException {
		File folder = file.getParentFile();
		try {
			for (ResultDocument doc : registry.getSearcher().listFolder(registry.findIndexFor(file), folder))
				if (Util.isCanonicallyEqual(doc.getPath().getCanonicalFile(), file))
					return doc;
		}
		catch (Exception e) {
			throw new ToolException("The document could not be read: " + e.getMessage());
		}
		throw new ToolException("Not found: " + file.getPath());
	}

	@NotNull
	private Map<String, Object> listFolder(@NotNull Map<String, Object> args) throws ToolException {
		String path = string(args, "path");
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		List<Object> entries = new ArrayList<Object>();
		if (path == null || path.trim().isEmpty()) {
			for (File root : FolderPanel.getRootFolders(registry))
				entries.add(folderEntry(root));
			result.put("folders", entries);
			return result;
		}
		File folder = resolve(path);
		checkAllowed(folder);
		if (!folder.isDirectory())
			throw new ToolException("Not a folder (or not found): " + folder.getPath());
		List<ResultDocument> docs;
		try {
			docs = new ArrayList<ResultDocument>(
				registry.getSearcher().listFolderEntries(registry.findIndexFor(folder), folder));
		}
		catch (Exception e) {
			throw new ToolException("The folder could not be listed: " + e.getMessage());
		}
		// Subfolders by name, then the files, newest first
		Collections.sort(docs, new Comparator<ResultDocument>() {
			public int compare(ResultDocument d1, ResultDocument d2) {
				if (d1.isFolder() != d2.isFolder())
					return d1.isFolder() ? -1 : 1;
				if (d1.isFolder())
					return AlphanumComparator.ignoreCaseInstance.compare(d1.getFilename(), d2.getFilename());
				return Long.compare(time(dateOf(d2)), time(dateOf(d1)));
			}
		});
		for (ResultDocument doc : docs) {
			if (entries.size() >= MAX_FOLDER_ENTRIES)
				break;
			if (doc.isFolder())
				entries.add(folderEntry(new File(doc.getPathStr())));
			else
				entries.add(describe(doc, false));
		}
		result.put("path", folder.getPath());
		result.put("total_entries", docs.size());
		result.put("entries", entries);
		return result;
	}

	@NotNull
	private static Map<String, Object> folderEntry(@NotNull File folder) {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("path", folder.getPath());
		map.put("name", folder.getName().isEmpty() ? folder.getPath() : folder.getName());
		map.put("folder", Boolean.TRUE);
		return map;
	}

	/** Only the indexed folders (and the fixed move targets) are accessible. */
	private void checkAllowed(@NotNull File file) throws ToolException {
		for (File root : FolderPanel.getRootFolders(registry))
			if (Util.isCanonicallyEqual(root, file) || Util.contains(root, file))
				return;
		throw new ToolException("Access denied: " + file.getPath()
			+ " is not inside an indexed folder. Use list_folder without a path to see the indexed folders.");
	}

	// ---- Helpers

	/**
	 * The absolute path with '..' and '.' resolved, so that a path can't
	 * leave the indexed folders ('C:/Docs/../secret.txt').
	 */
	@NotNull
	private static File resolve(@NotNull String path) {
		return new File(path.trim()).toPath().toAbsolutePath().normalize().toFile();
	}

	@Nullable
	private static Date dateOf(@NotNull ResultDocument doc) {
		try {
			return doc.isEmail() ? doc.getDate() : doc.getLastModified();
		}
		catch (Exception e) {
			return null;
		}
	}

	private static long time(@Nullable Date date) {
		return date == null ? 0 : date.getTime();
	}

	@NotNull
	private static String format(@NotNull Date date) {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(date);
	}

	@Nullable
	private static String string(@NotNull Map<String, Object> args, @NotNull String name) {
		Object value = args.get(name);
		return value == null ? null : value.toString();
	}

	private static int integer(@NotNull Map<String, Object> args, @NotNull String name, int defaultValue)
			throws ToolException {
		Object value = args.get(name);
		if (value == null)
			return defaultValue;
		if (value instanceof Number)
			return ((Number) value).intValue();
		try {
			return Integer.parseInt(value.toString().trim());
		}
		catch (NumberFormatException e) {
			throw new ToolException("The argument '" + name + "' must be a number.");
		}
	}

	@Nullable
	private static Date date(@NotNull Map<String, Object> args, @NotNull String name) throws ToolException {
		String value = string(args, name);
		if (value == null || value.trim().isEmpty())
			return null;
		try {
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
			format.setLenient(false);
			return format.parse(value.trim());
		}
		catch (ParseException e) {
			throw new ToolException("The argument '" + name + "' must be a date like 2025-01-31.");
		}
	}

}
