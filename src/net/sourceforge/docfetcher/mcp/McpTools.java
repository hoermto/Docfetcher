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

import net.sourceforge.docfetcher.enums.ProgramConf;
import net.sourceforge.docfetcher.gui.FolderPanel;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.parse.PdfVisualText;
import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.collect.AlphanumComparator;

/**
 * The tools of the MCP server: read-only access to the indexed documents.
 * {@code search} finds documents like the search field, {@code read_document}
 * returns the text of a document (in pages of at most
 * {@link #MAX_CHARS_LIMIT} characters), {@code get_original_document} a copy
 * of the file itself (base64, up to {@code McpMaxOriginalMB}),
 * {@code list_folder} lists the indexed folders and their contents. Only files
 * inside the indexed folders (and the fixed move targets) can be read or
 * listed.
 */
final class McpTools {

	static final int DEFAULT_MAX_CHARS = 20000;
	static final int MAX_CHARS_LIMIT = 100000;
	private static final int DEFAULT_MAX_RESULTS = 20;
	private static final int MAX_RESULTS_LIMIT = 100;
	private static final int SNIPPET_RESULTS = 10;
	private static final int SNIPPET_CHARS = 300;
	/** All snippets of one search together may take at most this long. */
	private static final long SNIPPET_MILLIS = 5000;

	/** Images: their text comes from text recognition, which is too slow for snippets. */
	private static final java.util.Set<String> IMAGE_EXTENSIONS = new java.util.HashSet<String>(Arrays.asList(
		"jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff"));
	private static final int MAX_FOLDER_ENTRIES = 500;
	private static final int OVERVIEW_FOLDERS = 10;
	private static final int OVERVIEW_WORDS = 15;
	private static final int OVERVIEW_PERIODS = 24;
	/** Content words: the text of at most this many documents is read, within the time limit. */
	private static final int SAMPLE_SIZE = 20;
	private static final long SAMPLE_MILLIS = 5000;
	private static final int SAMPLE_CHARS_PER_DOC = 20000;

	/** Words that say nothing about the content of a document, in file and folder names. */
	private static final java.util.Set<String> NOISE_WORDS = new java.util.HashSet<String>(Arrays.asList(
		"pdf", "doc", "docx", "txt", "odt", "xls", "xlsx", "jpg", "png", "tif", "tiff", "scan", "scans",
		"img", "image", "bild", "dokument", "dokumente", "document", "documents", "seite", "page", "datei",
		"file", "ordner", "folder", "kopie", "copy", "neu", "new", "final", "und", "der", "die", "das",
		"den", "dem", "des", "ein", "eine", "fur", "für", "von", "mit", "and", "the", "for", "with", "from"));

	/** A wrong argument or a document that can't be read; the message goes to the client. */
	static final class ToolException extends Exception {
		ToolException(@NotNull String message) {
			super(message);
		}
	}

	private final IndexRegistry registry;
	/** DocFetcher's window for show_document; null without one (tests). */
	@Nullable volatile McpServer.DocumentViewer viewer;
	/** Asks the user before get_file_folder tells the folder; null: refused. */
	@Nullable volatile McpServer.FolderApprover folderApprover;
	/** The ids of documents and folders handed out instead of their paths. */
	final McpIds ids = new McpIds();

	McpTools(@NotNull IndexRegistry registry) {
		this.registry = registry;
	}

	// ---- Tool definitions

	@NotNull
	static List<Object> definitions() {
		List<Object> tools = new ArrayList<Object>();
		tools.add(tool("search",
			"Full-text search in the user's documents indexed by DocFetcher (letters, invoices, "
			+ "contracts, scans, office files, emails; often German). Google-style query: all words "
			+ "must occur, -word excludes, \"exact phrase\", OR for alternatives, wildcards like "
			+ "steuer*. German compound words need a wildcard in front: *steuer* also finds "
			+ "Einkommensteuer, *miete* finds Kaltmiete. Umlauts and OCR errors are tolerated "
			+ "(Muller finds Müller). Returns the "
			+ "id, name, folder, date and a text snippet of each document (no file paths: use the "
			+ "id with the other tools). For 'latest' or 'newest' "
			+ "questions use sort=newest; for a period use modified_after/modified_before. Words "
			+ "also match file and folder names ('Rosenweg' finds documents in the folder "
			+ "'Rosenweg 12'). If more documents are found than returned, the newest come first "
			+ "and an overview (time range, counts per year or month, top folders, characteristic "
			+ "words of the texts and names with the share of documents containing them) helps to "
			+ "narrow the search by adding or excluding (-word) words.",
			props(
				"query", prop("string", "The search words, e.g. 'Steuer Januar' or '\"Rosenweg 5\" Miete'"),
				"max_results", prop("integer", "Maximum number of documents (default 20, at most 100)"),
				"sort", enumProp("Order of the results (default: relevance)", "relevance", "newest", "oldest"),
				"modified_after", prop("string", "Only documents modified on or after this date (YYYY-MM-DD)"),
				"modified_before", prop("string", "Only documents modified before this date (YYYY-MM-DD)"),
				"folder_id", prop("string", "Only documents in this folder or its subfolders: the id of a "
					+ "folder from list_folder or from the overview's top_folders")),
			"query"));
		tools.add(tool("read_document",
			"Returns the text of a document found with search or list_folder, to answer questions "
			+ "about its content. Long documents are returned in parts: continue with the returned "
			+ "next_offset. This is the way to learn what a document says. If the user wants to "
			+ "see the document itself, use show_document.",
			props(
				"id", prop("string", "Id of the document, as returned by search or list_folder"),
				"offset", prop("integer", "Character offset to start at (default 0)"),
				"max_chars", prop("integer", "Maximum number of characters (default " + DEFAULT_MAX_CHARS
					+ ", at most " + MAX_CHARS_LIMIT + ")"),
				"order", enumProp("PDF files only: 'visual' (default) gives the text line by line as it "
					+ "appears on the page, which keeps labels and values of forms and tables together "
					+ "('Grundmiete 620,00 Euro'); 'stream' gives the order in which the text was drawn - "
					+ "use it if columns of a multi-column page appear mixed", "visual", "stream")),
			"id"));
		tools.add(tool("get_original_document",
			"Returns the original file of a document (base64, with its MIME type; images that can "
			+ "be shown come as an image). Only when the original itself is needed: the user "
			+ "explicitly asks for the original file, or its layout matters, e.g. as a template for "
			+ "a new document in the same style, or to look at a scan. For anything about the "
			+ "content - questions, summaries, figures - always use read_document instead. Files "
			+ "of at most " + maxOriginalMB() + " MB.",
			props("id", prop("string", "Id of the document, as returned by search or list_folder")),
			"id"));
		tools.add(tool("list_folder",
			"Lists a folder: subfolders first, then the files, newest first, with dates and ids. "
			+ "Without an id, lists the indexed folders (e.g. the inbox and the archive folders).",
			props("id", prop("string", "Id of a folder, as returned by list_folder or search; omit for "
				+ "the indexed folders")),
			null));
		Map<String, Object> idList = prop("array", "Ids of the documents, as returned by search or "
			+ "list_folder; the first one is selected");
		idList.put("items", props("type", "string"));
		tools.add(tool("show_document",
			"Shows documents to the user in DocFetcher's window, e.g. when the user wants to see, open "
			+ "or find a document ('show me the rental contract'): DocFetcher comes to the front and "
			+ "lists them on its Search tab, the first one selected with its preview, and the folder "
			+ "browser shows its folder. Changes nothing but the view.",
			props("ids", idList),
			"ids"));
		Map<String, Object> fileFolder = McpFileFolder.definition();
		if (fileFolder != null)
			tools.add(fileFolder);
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
		if (name.equals("get_original_document"))
			return originalDocument(args);
		if (name.equals("list_folder"))
			return listFolder(args);
		if (name.equals("show_document"))
			return showDocuments(args);
		if (name.equals(McpFileFolder.TOOL))
			return McpFileFolder.call(folderApprover);
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
		File folder = string(args, "folder_id") == null ? null : target(args, "folder_id");

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
			if (folder != null && !Util.contains(folder, resolve(doc.getPathStr())))
				continue;
			docs.add(doc);
		}
		// Too many: the newest first (unless another order was asked for), plus an overview
		boolean tooMany = docs.size() > maxResults;
		if (tooMany && sort == null)
			sort = "newest";
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
		long snippetDeadline = System.currentTimeMillis() + SNIPPET_MILLIS;
		java.util.Set<String> queryWords = wordsOf(query);
		for (int i = 0; i < docs.size() && i < maxResults; i++) {
			Map<String, Object> described = describe(docs.get(i));
			if (i < SNIPPET_RESULTS && System.currentTimeMillis() < snippetDeadline) {
				String snippet = snippet(docs.get(i), queryWords);
				if (snippet != null)
					described.put("snippet", snippet);
			}
			results.add(described);
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("query", query);
		result.put("total_found", docs.size());
		result.put("returned", results.size());
		if (tooMany)
			result.put("overview", overview(docs, query, results.size(), sort, contentWords(docs, query)));
		result.put("results", results);
		return result;
	}

	/**
	 * Describes a result set that is too big to return: the time range, the
	 * number of documents per year (or month), the folders with the most
	 * documents and the significant words of the file and folder names, so
	 * that the assistant can ask a more specific question.
	 */
	@NotNull
	private Map<String, Object> overview(@NotNull List<ResultDocument> docs, @NotNull String query,
			int returned, @Nullable String sort, @NotNull Map<String, Object> contentWords) {
		Date oldest = null;
		Date newest = null;
		for (ResultDocument doc : docs) {
			Date date = dateOf(doc);
			if (date == null)
				continue;
			if (oldest == null || date.before(oldest))
				oldest = date;
			if (newest == null || date.after(newest))
				newest = date;
		}
		// Per year for long periods, else per month; newest first
		boolean perYear = oldest != null && newest.getTime() - oldest.getTime() > 2 * 365 * 24 * 3600 * 1000L;
		SimpleDateFormat periodFormat = new SimpleDateFormat(perYear ? "yyyy" : "yyyy-MM");
		java.util.TreeMap<String, Integer> periods = new java.util.TreeMap<String, Integer>(Collections.reverseOrder());
		Map<String, Integer> folders = new java.util.HashMap<String, Integer>();
		Map<String, Integer> words = new java.util.HashMap<String, Integer>();
		java.util.Set<String> queryWords = wordsOf(query);
		for (ResultDocument doc : docs) {
			Date date = dateOf(doc);
			if (date != null)
				increment(periods, periodFormat.format(date));
			File file = resolve(doc.getPathStr());
			File parent = file.getParentFile();
			if (parent != null)
				increment(folders, parent.getPath().replace('\\', '/')); // Like the paths of the results
			// The words of the name, the title and the two folders above the document
			StringBuilder names = new StringBuilder(doc.getFilename()).append(' ').append(doc.getTitle());
			for (int i = 0; i < 2 && parent != null; i++, parent = parent.getParentFile())
				names.append(' ').append(parent.getName());
			for (String word : wordsOf(names.toString()))
				if (!queryWords.contains(word) && !NOISE_WORDS.contains(word))
					increment(words, word);
		}
		// Words in (nearly) all documents don't help to narrow the search
		List<String> significant = new ArrayList<String>();
		for (Map.Entry<String, Integer> entry : sortByCount(words))
			if (entry.getValue() >= 2 && (docs.size() < 5 || entry.getValue() <= docs.size() * 9 / 10)
					&& significant.size() < OVERVIEW_WORDS)
				significant.add(entry.getKey() + " (" + entry.getValue() + ")");
		List<Object> topFolders = new ArrayList<Object>();
		for (Map.Entry<String, Integer> entry : sortByCount(folders)) {
			if (topFolders.size() >= OVERVIEW_FOLDERS)
				break;
			File folderFile = new File(entry.getKey());
			Map<String, Object> folder = new LinkedHashMap<String, Object>();
			folder.put("id", ids.idOf(folderFile));
			folder.put("folder", location(folderFile));
			folder.put("count", entry.getValue());
			topFolders.add(folder);
		}
		Map<String, Object> periodCounts = new LinkedHashMap<String, Object>();
		for (Map.Entry<String, Integer> entry : periods.entrySet())
			if (periodCounts.size() < OVERVIEW_PERIODS)
				periodCounts.put(entry.getKey(), entry.getValue());

		Map<String, Object> overview = new LinkedHashMap<String, Object>();
		if (oldest != null) {
			overview.put("oldest", format(oldest));
			overview.put("newest", format(newest));
		}
		overview.put(perYear ? "by_year" : "by_month", periodCounts);
		overview.put("top_folders", topFolders);
		overview.put("name_words", significant);
		overview.putAll(contentWords);
		String order = "oldest".equals(sort) ? "oldest" : "newest".equals(sort) ? "newest" : "best matching";
		overview.put("hint", docs.size() + " documents found"
			+ (oldest != null ? " (" + format(oldest).substring(0, 10) + " to " + format(newest).substring(0, 10) + ")" : "")
			+ ", only the " + returned + " " + order + " are listed. To narrow the search, add a word "
			+ "(see content_words and name_words, with the share of documents containing it) or exclude "
			+ "one with -word, add a period (modified_after/modified_before, see "
			+ (perYear ? "by_year" : "by_month") + ") or a folder (see top_folders).");
		return overview;
	}

	/**
	 * The words that characterize the found documents: the text of a sample
	 * of them (spread over the whole result list, within a time limit) is
	 * read, and its words that are frequent there but rare in the other
	 * indexed documents (TF-IDF) are returned with the number of sampled
	 * documents containing them - words in some of the documents help to
	 * narrow the search by adding or excluding them. Returns
	 * {"content_words": [...], "sampled_documents": n}, or nothing if too
	 * few documents could be read.
	 */
	@NotNull
	private Map<String, Object> contentWords(@NotNull List<ResultDocument> docs, @NotNull String query) {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		long deadline = System.currentTimeMillis() + SAMPLE_MILLIS;
		int sampleSize = Math.min(SAMPLE_SIZE, docs.size());
		List<java.util.Set<String>> texts = new ArrayList<java.util.Set<String>>(); // The words of each document
		StringBuilder combined = new StringBuilder();
		for (int i = 0; i < sampleSize && System.currentTimeMillis() < deadline; i++) {
			ResultDocument doc = docs.get((int) ((long) i * docs.size() / sampleSize));
			String text = quickText(doc); // No text recognition: too slow here
			if (text == null)
				continue;
			if (text.length() > SAMPLE_CHARS_PER_DOC)
				text = text.substring(0, SAMPLE_CHARS_PER_DOC);
			text = text.toLowerCase(java.util.Locale.ROOT);
			texts.add(wordsOf(text));
			combined.append(text).append('\n');
		}
		if (texts.size() < 2)
			return result;
		List<String> candidates;
		try {
			candidates = registry.getSearcher().extractKeywords(combined.toString(), 100);
		}
		catch (Exception e) {
			return result;
		}
		java.util.Set<String> queryWords = new java.util.HashSet<String>();
		for (String word : wordsOf(query))
			queryWords.add(plain(word));
		List<String> words = new ArrayList<String>();
		for (String candidate : candidates) {
			if (words.size() >= OVERVIEW_WORDS)
				break;
			if (!wordsOf(candidate).contains(candidate) || candidate.matches(".*\\d.*")
					|| NOISE_WORDS.contains(candidate) || queryWords.contains(plain(candidate)))
				continue; // Numbers, codes and OCR noise, short words, noise words, the query itself
			int count = 0;
			for (java.util.Set<String> docWords : texts)
				if (docWords.contains(candidate))
					count++;
			// Words in one document or in all of them don't split the results
			if (count < 2 || (texts.size() >= 5 && count == texts.size()))
				continue;
			words.add(candidate + " (" + count + "/" + texts.size() + ")");
		}
		result.put("content_words", words);
		result.put("sampled_documents", texts.size());
		return result;
	}

	/** The word without umlauts and accents, for comparing it with the query words. */
	@NotNull
	private static String plain(@NotNull String word) {
		String decomposed = java.text.Normalizer.normalize(word.replace("ß", "ss"), java.text.Normalizer.Form.NFD);
		return decomposed.replaceAll("\\p{M}", "");
	}

	/** The lower case words of a text with at least 3 characters and a letter. */
	@NotNull
	private static java.util.Set<String> wordsOf(@NotNull String text) {
		java.util.Set<String> words = new java.util.LinkedHashSet<String>();
		for (String word : text.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
			if (word.length() < 3)
				continue;
			for (int i = 0; i < word.length(); i++) {
				if (Character.isLetter(word.charAt(i))) {
					words.add(word);
					break;
				}
			}
		}
		return words;
	}

	private static void increment(@NotNull Map<String, Integer> counts, @NotNull String key) {
		Integer count = counts.get(key);
		counts.put(key, count == null ? 1 : count + 1);
	}

	/** Entries by descending count, then by key. */
	@NotNull
	private static List<Map.Entry<String, Integer>> sortByCount(@NotNull Map<String, Integer> counts) {
		List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());
		Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
			public int compare(Map.Entry<String, Integer> e1, Map.Entry<String, Integer> e2) {
				int result = e2.getValue().compareTo(e1.getValue());
				return result != 0 ? result : e1.getKey().compareTo(e2.getKey());
			}
		});
		return entries;
	}

	@NotNull
	private Map<String, Object> describe(@NotNull ResultDocument doc) {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		putReference(map, resolve(doc.getPathStr()));
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
		return map;
	}

	/**
	 * Text around the first query word, or the beginning of the text; null if
	 * the text can't be read quickly (see {@link #quickText}).
	 */
	@Nullable
	private static String snippet(@NotNull ResultDocument doc, @NotNull java.util.Set<String> queryWords) {
		String text = quickText(doc);
		if (text == null)
			return null;
		String lower = text.toLowerCase(java.util.Locale.ROOT);
		int first = -1;
		for (String word : queryWords) {
			int i = lower.indexOf(word);
			if (i >= 0 && (first < 0 || i < first))
				first = i;
		}
		int start = Math.max(0, first - SNIPPET_CHARS / 3);
		int end = Math.min(text.length(), start + SNIPPET_CHARS);
		String snippet = text.substring(start, end).replaceAll("\\s+", " ").trim();
		return (start > 0 ? "..." : "") + snippet + (end < text.length() ? "..." : "");
	}

	/**
	 * The text of a document for snippets and the overview, without the slow
	 * cases: PDF files give their page text (scans without stored text give
	 * null instead of being recognized), images and emails give null, other
	 * documents their normal text. Null also if the file can't be read.
	 */
	@Nullable
	private static String quickText(@NotNull ResultDocument doc) {
		if (doc.isEmail())
			return null;
		File file = resolve(doc.getPathStr());
		String ext = Util.getExtension(file.getName()).toLowerCase(java.util.Locale.ROOT);
		if (IMAGE_EXTENSIONS.contains(ext))
			return null;
		if (ext.equals("pdf"))
			return PdfVisualText.of(file);
		try {
			return doc.getText();
		}
		catch (Throwable e) {
			return null; // E.g. file moved since indexing, or unreadable
		}
	}

	@NotNull
	private List<Object> readDocument(@NotNull Map<String, Object> args) throws ToolException {
		File file = target(args, "id");
		if (!file.isFile())
			throw new ToolException("Not a file (or not found): " + file.getName());
		int offset = Math.max(0, integer(args, "offset", 0));
		int maxChars = Util.clamp(integer(args, "max_chars", DEFAULT_MAX_CHARS), 1, MAX_CHARS_LIMIT);

		ResultDocument doc = findDocument(file);
		String text = null;
		// PDF files in visual order, unless asked otherwise (see the tool description)
		if (!"stream".equals(string(args, "order")) && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"))
			text = PdfVisualText.of(file);
		try {
			if (text == null)
				text = doc.getText(); // Also recognizes scans whose text wasn't stored
		}
		catch (Throwable e) {
			throw new ToolException("The text of the document could not be read: " + e.getMessage());
		}
		int start = Math.min(offset, text.length());
		int end = Math.min(text.length(), start + maxChars);

		Map<String, Object> meta = describe(doc);
		meta.put("total_chars", text.length());
		meta.put("offset", start);
		meta.put("returned_chars", end - start);
		if (end < text.length())
			meta.put("next_offset", end);
		return Arrays.<Object> asList(meta, text.substring(start, end));
	}

	/**
	 * The original file: a copy of its bytes for the client, which never gets
	 * access to the file itself.
	 */
	@NotNull
	private FileContent originalDocument(@NotNull Map<String, Object> args) throws ToolException {
		File file = target(args, "id");
		if (!file.isFile())
			throw new ToolException("Not a file (or not found): " + file.getName()
				+ ". Documents inside archives and emails have no original file of their own.");
		long maxBytes = maxOriginalMB() * 1024L * 1024L;
		if (file.length() > maxBytes)
			throw new ToolException("The file is too large (" + (file.length() / (1024 * 1024)) + " MB, at most "
				+ maxOriginalMB() + " MB). Use read_document for its text.");
		byte[] data;
		try {
			data = java.nio.file.Files.readAllBytes(file.toPath());
		}
		catch (Exception e) {
			throw new ToolException("The file could not be read: " + e.getMessage());
		}
		Map<String, Object> meta = new LinkedHashMap<String, Object>();
		putReference(meta, file);
		meta.put("name", file.getName());
		meta.put("mime_type", mimeType(file.getName()));
		meta.put("size_bytes", data.length);
		meta.put("modified", format(new Date(file.lastModified())));
		return new FileContent(meta, data);
	}

	/** The original file of a document: its description and its bytes. */
	static final class FileContent {
		@NotNull final Map<String, Object> meta;
		@NotNull final byte[] data;

		FileContent(@NotNull Map<String, Object> meta, @NotNull byte[] data) {
			this.meta = meta;
			this.data = data;
		}

		@NotNull
		String mimeType() {
			return (String) meta.get("mime_type");
		}

		/** An identifier, not a link: the client gets the bytes, not the file. */
		@NotNull
		String uri() {
			try {
				return new java.net.URI("docfetcher", null, "/" + meta.get("id") + "/" + meta.get("name"), null)
					.toASCIIString();
			}
			catch (java.net.URISyntaxException e) {
				return "docfetcher:/" + meta.get("name");
			}
		}
	}

	private static int maxOriginalMB() {
		return ProgramConf.Int.McpMaxOriginalMB.get();
	}

	private static final Map<String, String> MIME_TYPES = new java.util.HashMap<String, String>();
	static {
		String[] types = {
			"pdf", "application/pdf",
			"doc", "application/msword",
			"docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			"dotx", "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
			"xls", "application/vnd.ms-excel",
			"xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
			"ppt", "application/vnd.ms-powerpoint",
			"pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
			"odt", "application/vnd.oasis.opendocument.text",
			"ods", "application/vnd.oasis.opendocument.spreadsheet",
			"odp", "application/vnd.oasis.opendocument.presentation",
			"rtf", "application/rtf",
			"txt", "text/plain", "csv", "text/csv", "md", "text/markdown",
			"html", "text/html", "htm", "text/html", "xml", "application/xml", "json", "application/json",
			"eml", "message/rfc822", "msg", "application/vnd.ms-outlook",
			"png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg", "gif", "image/gif",
			"webp", "image/webp", "bmp", "image/bmp", "tif", "image/tiff", "tiff", "image/tiff",
			"zip", "application/zip"};
		for (int i = 0; i < types.length; i += 2)
			MIME_TYPES.put(types[i], types[i + 1]);
	}

	/** The MIME type from the file extension; unknown ones are 'application/octet-stream'. */
	@NotNull
	static String mimeType(@NotNull String filename) {
		String type = MIME_TYPES.get(Util.getExtension(filename).toLowerCase(java.util.Locale.ROOT));
		return type == null ? "application/octet-stream" : type;
	}

	private static final int MAX_SHOWN = 100;

	/**
	 * Shows the found documents in DocFetcher's window; paths that aren't
	 * indexed documents are reported, an inaccessible one is refused.
	 */
	@NotNull
	private Map<String, Object> showDocuments(@NotNull Map<String, Object> args) throws ToolException {
		List<String> wanted = new ArrayList<String>();
		Object value = args.get("ids");
		if (value instanceof List) {
			for (Object id : (List<?>) value)
				if (id != null && !id.toString().trim().isEmpty())
					wanted.add(id.toString());
		}
		else if (value != null && !value.toString().trim().isEmpty()) {
			wanted.add(value.toString()); // A single id instead of a list
		}
		if (wanted.isEmpty())
			throw new ToolException("The argument 'ids' is missing.");
		if (wanted.size() > MAX_SHOWN)
			throw new ToolException("At most " + MAX_SHOWN + " documents can be shown at once.");
		McpServer.DocumentViewer viewer = this.viewer;
		if (viewer == null)
			throw new ToolException("DocFetcher's window is not available.");

		List<ResultDocument> docs = new ArrayList<ResultDocument>();
		List<String> shown = new ArrayList<String>();
		List<String> notFound = new ArrayList<String>();
		for (String id : wanted) {
			File file = ids.fileOf(id);
			checkAllowed(file);
			try {
				ResultDocument doc = findDocument(file);
				docs.add(doc);
				shown.add(file.getName());
			}
			catch (ToolException e) {
				notFound.add(file.getName());
			}
		}
		if (docs.isEmpty())
			throw new ToolException("Not found among the indexed documents: " + notFound);
		viewer.show(docs);

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("shown", shown);
		if (!notFound.isEmpty())
			result.put("not_found", notFound);
		return result;
	}

	/** The document of a file, with its index data if it is indexed. */
	@NotNull
	private ResultDocument findDocument(@NotNull File file) throws ToolException {
		File folder = file.getParentFile();
		try {
			for (ResultDocument doc : registry.getSearcher().listFolder(registry.findIndexFor(file), folder))
				if (resolve(doc.getPathStr()).equals(file))
					return doc;
		}
		catch (Exception e) {
			throw new ToolException("The document could not be read: " + e.getMessage());
		}
		throw new ToolException("Not found: " + file.getName());
	}

	@NotNull
	private Map<String, Object> listFolder(@NotNull Map<String, Object> args) throws ToolException {
		String id = string(args, "id");
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		List<Object> entries = new ArrayList<Object>();
		if (id == null || id.trim().isEmpty()) {
			for (File root : FolderPanel.getRootFolders(registry))
				entries.add(folderEntry(root));
			result.put("folders", entries);
			return result;
		}
		File folder = target(args, "id");
		if (!folder.isDirectory())
			throw new ToolException("Not a folder (or not found): " + folder.getName());
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
				entries.add(folderEntry(resolve(doc.getPathStr())));
			else
				entries.add(describe(doc));
		}
		result.put("id", ids.idOf(folder));
		result.put("folder", location(folder));
		result.put("total_entries", docs.size());
		result.put("entries", entries);
		return result;
	}

	@NotNull
	private Map<String, Object> folderEntry(@NotNull File folder) {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		File resolved = resolve(folder.getPath());
		map.put("id", ids.idOf(resolved));
		map.put("name", location(resolved).replaceAll(".*/", ""));
		map.put("type", "folder");
		return map;
	}

	/** Only the indexed folders (and the fixed move targets) are accessible. */
	private void checkAllowed(@NotNull File file) throws ToolException {
		for (File root : FolderPanel.getRootFolders(registry)) {
			/*
			 * Indexes with relative paths (portable repositories) have roots
			 * like 'C:/DocFetcher/../Documents', so compare normalized paths
			 */
			File normalized = resolve(root.getPath());
			if (normalized.equals(file) || Util.contains(normalized, file))
				return;
		}
		throw new ToolException("Access denied: " + file.getName()
			+ " is not inside an indexed folder. Use list_folder without an id to see the indexed folders.");
	}

	// ---- References: ids instead of paths

	/** The id of a document or folder and the folder it is in, inside the indexed folders. */
	private void putReference(@NotNull Map<String, Object> map, @NotNull File file) {
		map.put("id", ids.idOf(file));
		map.put("folder", location(file.getParentFile()));
	}

	/**
	 * Where a folder is, inside the indexed folders: the name of the indexed
	 * folder and the subfolders, e.g. '05 Torsten/Immobilien' - no drive path.
	 */
	@NotNull
	private String location(@Nullable File folder) {
		if (folder == null)
			return "";
		for (File root : FolderPanel.getRootFolders(registry)) {
			File normalized = resolve(root.getPath());
			if (normalized.equals(folder) || Util.contains(normalized, folder)) {
				// A whole drive has no name: its letter, not its path
				String name = normalized.getName().isEmpty()
					? "Drive " + normalized.getPath().replaceAll("[^A-Za-z0-9]", "")
					: normalized.getName();
				String relative = normalized.toPath().relativize(folder.toPath()).toString();
				return relative.isEmpty() ? name : name + "/" + relative.replace(File.separatorChar, '/');
			}
		}
		return folder.getName();
	}

	/** The document or folder whose id is given in the argument; only inside the indexed folders. */
	@NotNull
	private File target(@NotNull Map<String, Object> args, @NotNull String key) throws ToolException {
		String id = string(args, key);
		if (id == null || id.trim().isEmpty())
			throw new ToolException("The argument '" + key + "' is missing.");
		File file = ids.fileOf(id);
		checkAllowed(file);
		return file;
	}

	// ---- Helpers

	/**
	 * The absolute path with '..' and '.' resolved, so that a path can't
	 * leave the indexed folders ('C:/Docs/../secret.txt').
	 */
	@NotNull
	static File resolve(@NotNull String path) {
		return new File(path.trim()).toPath().toAbsolutePath().normalize().toFile();
	}

	/** The path as shown to the client: absolute, with '/' as in DocFetcher's result tables. */
	@NotNull
	private static String display(@NotNull File file) {
		return file.getPath().replace(File.separatorChar, '/');
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
	static String string(@NotNull Map<String, Object> args, @NotNull String name) {
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
