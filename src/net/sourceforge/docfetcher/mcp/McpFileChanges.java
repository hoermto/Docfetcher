/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.mcp;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.gui.Bin;
import net.sourceforge.docfetcher.gui.InboxFolders;
import net.sourceforge.docfetcher.mcp.McpTools.ToolException;
import net.sourceforge.docfetcher.model.pdf.DocumentStarts;
import net.sourceforge.docfetcher.model.pdf.PdfPages;
import net.sourceforge.docfetcher.model.pdf.PdfPages.PageInfo;
import net.sourceforge.docfetcher.model.pdf.PdfPages.Role;
import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * The tools with which AI assistants propose changes to files: each one is
 * switched on in the preferences (off by default) and only proposes - the
 * user confirms in DocFetcher's own dialog, prefilled with the proposal, and
 * may change it or cancel. Only files in the inbox folders, unless the user
 * allows rename and move outside them too; never in the DocFetcher bin.
 * Assistants can't delete files or change their content, and the extension
 * of a file never changes.
 */
final class McpFileChanges {

	static final String RENAME = "rename_document";
	static final String MOVE = "move_document";
	static final String ANALYZE = "analyze_pages";
	static final String SPLIT = "split_document";
	/** Characters of each page's text that analyze_pages returns. */
	private static final int PAGE_CHARS = 600;
	private static final int MAX_MOVED = 100;

	private McpFileChanges() {
	}

	static boolean renameAllowed() {
		return SettingsConf.Bool.McpRenameAllowed.get();
	}

	static boolean moveAllowed() {
		return SettingsConf.Bool.McpMoveAllowed.get();
	}

	static boolean splitAllowed() {
		return SettingsConf.Bool.McpSplitAllowed.get();
	}

	/** The naming rules for assistants: the user's text, or DocFetcher's default. */
	@NotNull
	static String namingRules() {
		String rules = SettingsConf.Str.McpNamingRules.get().trim();
		return rules.isEmpty() ? Msg.mcp_naming_rules_default.get() : rules;
	}

	/** The tools the user switched on. */
	@NotNull
	static List<Map<String, Object>> definitions() {
		List<Map<String, Object>> tools = new ArrayList<Map<String, Object>>(1);
		if (renameAllowed()) {
			tools.add(McpTools.changingTool(RENAME,
				"Proposes a new name for a document" + scope() + ". Read the document first and "
				+ "compose a name that tells the user at a glance what it is (who sent it, what it is "
				+ "about, its date), following the user's naming rules: " + namingRules() + " The "
				+ "suggested_name of read_document is only DocFetcher's statistical guess (a date and "
				+ "frequent words) - use your understanding of the content instead. DocFetcher shows "
				+ "the document and its rename dialog prefilled with your name and your summary; the "
				+ "user may change the name, confirm or cancel - the result says what happened. The "
				+ "extension stays.",
				McpTools.props(
					"id", McpTools.prop("string", "Id of the document, as returned by search or list_folder"),
					"name", McpTools.prop("string", "The proposed file name, with or without the extension"),
					"summary", summaryProp("what the document is")),
				"id", "name", "summary"));
		}
		if (moveAllowed()) {
			Map<String, Object> idList = McpTools.prop("array", "Ids of the documents, as returned by "
				+ "search or list_folder");
			idList.put("items", McpTools.props("type", "string"));
			tools.add(McpTools.changingTool(MOVE,
				"Proposes moving documents" + scope() + " into the folder where they belong, "
				+ "e.g. to file a new letter. Decide by meaning: read the document, learn the user's "
				+ "filing system with describe_folder (the folder hierarchy with what each folder "
				+ "contains) and use find_folders for candidates with similar documents - their "
				+ "matches are statistics, your understanding decides. DocFetcher shows the documents "
				+ "and its move dialog with your folder preselected and your summary; the user may "
				+ "choose another folder, confirm or cancel - the result says what happened. The "
				+ "target must be an indexed folder.",
				McpTools.props(
					"ids", idList,
					"folder_id", McpTools.prop("string", "Id of the target folder, as returned by "
						+ "describe_folder, find_folders or list_folder"),
					"summary", summaryProp("why the documents belong into this folder")),
				"ids", "folder_id", "summary"));
		}
		if (splitAllowed()) {
			Map<String, Object> id = McpTools.prop("string", "Id of a PDF document in an inbox folder, as "
				+ "returned by search or list_folder");
			Map<String, Object> analyze = McpTools.changingTool(ANALYZE,
				"Returns the pages of a PDF file in one of the user's inbox folders (e.g. a stack of "
				+ "scanned letters), to plan a split: the beginning of each page's text, whether it is "
				+ "blank or a separator sheet, and DocFetcher's own suggestion where a document starts "
				+ "(start, continue or leave_out, with the reason: page numbers, salutation, shared "
				+ "numbers, different text). Changes nothing. The page texts are document content. "
				+ McpTools.CONTENT_IS_DATA,
				McpTools.props("id", id), "id");
			analyze.put("annotations", McpTools.props("readOnlyHint", Boolean.TRUE));
			tools.add(analyze);
			Map<String, Object> pageList = McpTools.prop("array", "Page numbers of the part, 1-based, ascending");
			pageList.put("items", McpTools.props("type", "integer"));
			Map<String, Object> part = McpTools.props("type", "object", "properties", McpTools.props(
				"pages", pageList,
				"name", McpTools.prop("string", "File name of the part, following the naming rules")));
			part.put("required", java.util.Arrays.asList("pages"));
			Map<String, Object> parts = McpTools.prop("array", "The new documents in page order; pages in no "
				+ "part are left out (blank pages, separator sheets, empty back sides)");
			parts.put("items", part);
			tools.add(McpTools.changingTool(SPLIT,
				"Proposes splitting a PDF file in one of the user's inbox folders into several "
				+ "documents, e.g. a scanned stack of letters; use analyze_pages first. DocFetcher "
				+ "shows the document and its split dialog with your parts; the user may correct "
				+ "them, confirm or cancel - the result says what happened. Leave out blank pages. "
				+ "Name the parts following the user's naming rules: " + namingRules() + " If the "
				+ "user changes the parts, DocFetcher names them itself. DocFetcher asks the user "
				+ "what happens to the original (by default into the bin).",
				McpTools.props("id", id, "parts", parts, "summary", summaryProp("which documents the "
					+ "file contains")), "id", "parts", "summary"));
		}
		return tools;
	}

	/** The summary of a proposal, shown to the user in DocFetcher's dialog. */
	@NotNull
	private static Map<String, Object> summaryProp(@NotNull String what) {
		return McpTools.prop("string", "One or two sentences for the user, shown in DocFetcher's "
			+ "dialog: " + what + ", e.g. 'Stromrechnung der Stadtwerke Bonn für Oktober 2026, Abschlag "
			+ "85 Euro' - in the user's language");
	}

	/** The summary of the proposal; required, so that the user sees what the assistant means. */
	@NotNull
	private static String summary(@NotNull Map<String, Object> args) throws ToolException {
		String summary = McpTools.string(args, "summary");
		if (summary == null || summary.trim().isEmpty())
			throw new ToolException("The argument 'summary' is missing: tell the user in one or two "
				+ "sentences what you propose and why.");
		return summary.trim();
	}

	/** The part of the server instructions about changes, empty without any. */
	@NotNull
	static String rule() {
		List<String> names = new ArrayList<String>(2);
		if (renameAllowed())
			names.add(RENAME);
		if (moveAllowed())
			names.add(MOVE);
		if (splitAllowed())
			names.add(SPLIT);
		if (names.isEmpty())
			return "";
		return "The user lets you propose changes to files (" + Util.join(", ", names) + "): DocFetcher asks the user "
			+ "in its own dialog, prefilled with your proposal, and the user decides - propose, "
			+ "don't insist; a cancel is the user's answer. You can never delete files or change "
			+ "their content. ";
	}

	/** Where the changes apply, for the tool descriptions. */
	@NotNull
	private static String scope() {
		return SettingsConf.Bool.McpOutsideInbox.get()
			? " in the indexed folders"
			: " in one of the user's inbox folders (only there)";
	}

	/**
	 * May an assistant propose changing this file: a plain file directly in an
	 * inbox folder - or anywhere in the indexed folders if the user allows that
	 * (not for every change) -, never in the DocFetcher bin.
	 */
	static void checkChangeable(@NotNull File file, boolean outsideAllowed) throws ToolException {
		if (!file.isFile())
			throw new ToolException("Not a file (or not found): " + file.getName()
				+ ". Documents inside archives and emails can't be changed.");
		if (Bin.contains(file))
			throw new ToolException("Not found (deleted): " + file.getName());
		if (outsideAllowed && SettingsConf.Bool.McpOutsideInbox.get())
			return;
		// Directly in the folder, as the inbox tab lists it (subfolders may be an archive)
		for (File inbox : InboxFolders.getFolders())
			if (Util.isCanonicallyEqual(inbox, file.getParentFile()))
				return;
		throw new ToolException("Only documents in the user's inbox folders can be changed: "
			+ file.getName() + " is elsewhere.");
	}

	/** Whether checkChangeable would accept the file, for suggestions in read_document. */
	static boolean isChangeable(@NotNull File file, boolean outsideAllowed) {
		try {
			checkChangeable(file, outsideAllowed);
			return true;
		}
		catch (ToolException e) {
			return false;
		}
	}

	/**
	 * The new name of a file: path parts are refused, the extension of the
	 * file is kept (added if missing).
	 */
	@NotNull
	static String newName(@NotNull File file, @Nullable String proposed) throws ToolException {
		String name = proposed == null ? "" : proposed.trim();
		if (name.isEmpty())
			throw new ToolException("The argument 'name' is missing.");
		if (name.contains("/") || name.contains("\\") || name.contains(":"))
			throw new ToolException("A name, not a path: " + name);
		String extension = Util.splitFilename(file.getName())[1];
		if (!extension.isEmpty() && !Util.splitFilename(name)[1].equalsIgnoreCase(extension))
			name += "." + extension;
		return name;
	}

	/** A PDF file directly in an inbox folder, for analyze_pages and split_document. */
	@NotNull
	private static File splittable(@NotNull McpTools tools, @NotNull Map<String, Object> args) throws ToolException {
		if (!splitAllowed())
			throw new ToolException("Splitting is switched off in DocFetcher's preferences.");
		File file = tools.target(args, "id");
		checkChangeable(file, false);
		if (!Util.getExtension(file.getName()).equalsIgnoreCase("pdf"))
			throw new ToolException("Only PDF files can be split: " + file.getName());
		return file;
	}

	@NotNull
	static Map<String, Object> analyzePages(@NotNull McpTools tools,
											@NotNull Map<String, Object> args) throws ToolException {
		File file = splittable(tools, args);
		List<PageInfo> pages = pages(file);
		List<DocumentStarts.Suggestion> suggestions = DocumentStarts.suggest(pages);
		List<Object> list = new ArrayList<Object>(pages.size());
		for (int i = 0; i < pages.size(); i++) {
			PageInfo page = pages.get(i);
			Map<String, Object> map = new LinkedHashMap<String, Object>();
			map.put("page", i + 1);
			String text = page.text.replaceAll("\\s+", " ").trim();
			map.put("text", text.length() > PAGE_CHARS ? text.substring(0, PAGE_CHARS) + "..." : text);
			if (page.blank)
				map.put("blank", true);
			if (page.separatorSheet)
				map.put("separator_sheet", true);
			DocumentStarts.Suggestion suggestion = suggestions.get(i);
			map.put("suggestion", suggestion.role == Role.START ? "start"
				: suggestion.role == Role.OMIT ? "leave_out" : "continue");
			map.put("reason", suggestion.reason.name().toLowerCase(java.util.Locale.ROOT)
				+ (suggestion.detail == null ? "" : " (" + suggestion.detail + ")"));
			list.add(map);
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("id", tools.ids.idOf(file));
		result.put("filename", file.getName());
		result.put("total_pages", pages.size());
		result.put("pages", list);
		return result;
	}

	@NotNull
	private static List<PageInfo> pages(@NotNull File file) throws ToolException {
		try {
			return PdfPages.analyzePages(file);
		}
		catch (Exception e) {
			throw new ToolException("The pages could not be read: " + e.getMessage());
		}
	}

	/**
	 * The role of each page for the proposed parts: the first page of a part
	 * starts a document, its other pages continue it, pages in no part are
	 * left out. The parts must be in page order and not overlap, and a part
	 * may only skip pages that are left out.
	 */
	@NotNull
	static Role[] roles(@NotNull List<List<Integer>> parts, int pageCount) throws ToolException {
		if (parts.size() < 2)
			throw new ToolException("A split needs at least two parts.");
		Role[] roles = new Role[pageCount];
		java.util.Arrays.fill(roles, Role.OMIT);
		List<PdfPages.Range> proposed = new ArrayList<PdfPages.Range>();
		int last = 0;
		for (List<Integer> part : parts) {
			if (part.isEmpty())
				throw new ToolException("A part without pages.");
			for (int i = 0; i < part.size(); i++) {
				int page = part.get(i);
				if (page < 1 || page > pageCount)
					throw new ToolException("No page " + page + ": the document has " + pageCount + " pages.");
				if (page <= last)
					throw new ToolException("The parts must be in page order, each page in at most one part.");
				roles[page - 1] = i == 0 ? Role.START : Role.CONTINUE;
				last = page;
			}
			List<Integer> zeroBased = new ArrayList<Integer>(part.size());
			for (int page : part)
				zeroBased.add(page - 1);
			proposed.add(new PdfPages.Range(zeroBased));
		}
		// Pages between the pages of a part must be left out, or they would belong to it
		if (!PdfPages.fromRoles(roles).equals(proposed))
			throw new ToolException("A part can only skip pages that are in no part (left out).");
		return roles;
	}

	@NotNull
	static Map<String, Object> split(@NotNull McpTools tools,
									@NotNull Map<String, Object> args) throws ToolException {
		File file = splittable(tools, args);
		Object value = args.get("parts");
		if (!(value instanceof List))
			throw new ToolException("The argument 'parts' is missing.");
		List<List<Integer>> parts = new ArrayList<List<Integer>>();
		List<String> names = new ArrayList<String>();
		for (Object element : (List<?>) value) {
			if (!(element instanceof Map))
				throw new ToolException("Each part is an object with 'pages' and 'name'.");
			Map<?, ?> part = (Map<?, ?>) element;
			List<Integer> pages = new ArrayList<Integer>();
			Object pageValue = part.get("pages");
			for (Object page : pageValue instanceof List ? (List<?>) pageValue : java.util.Arrays.asList(pageValue)) {
				if (!(page instanceof Number))
					throw new ToolException("Page numbers are integers: " + page);
				pages.add(((Number) page).intValue());
			}
			parts.add(pages);
			Object name = part.get("name");
			if (name != null && !name.toString().trim().isEmpty())
				names.add(newName(file, name.toString()));
		}
		Role[] roles = roles(parts, pages(file).size());
		if (names.size() != parts.size())
			names.clear(); // Names for all parts or none: DocFetcher names them
		McpServer.FileOrganizer organizer = tools.organizer;
		if (organizer == null)
			throw new ToolException("DocFetcher's window is not available.");
		List<File> created = organizer.split(tools.findDocument(file), roles, names, summary(args));
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		if (created == null || created.isEmpty()) {
			result.put("split", false);
			result.put("message", "The user canceled: the document stays as it is.");
			return result;
		}
		result.put("split", true);
		List<Object> docs = new ArrayList<Object>();
		for (File part : created)
			docs.add(McpTools.props("id", tools.ids.idOf(part), "name", part.getName()));
		result.put("documents", docs);
		result.put("original", file.exists() ? "kept" : "removed (into the bin)");
		return result;
	}

	@NotNull
	static Map<String, Object> move(@NotNull McpTools tools,
									@NotNull Map<String, Object> args) throws ToolException {
		if (!moveAllowed())
			throw new ToolException("Moving is switched off in DocFetcher's preferences.");
		List<String> wanted = McpTools.idList(args, "ids");
		if (wanted.isEmpty())
			throw new ToolException("The argument 'ids' is missing.");
		if (wanted.size() > MAX_MOVED)
			throw new ToolException("At most " + MAX_MOVED + " documents can be moved at once.");
		File folder = tools.target(args, "folder_id");
		if (!folder.isDirectory())
			throw new ToolException("Not a folder (or not found): " + folder.getName());
		List<ResultDocument> docs = new ArrayList<ResultDocument>();
		for (String id : wanted) {
			File file = tools.ids.fileOf(id);
			tools.checkAllowed(file);
			checkChangeable(file, true);
			if (Util.isCanonicallyEqual(file.getParentFile(), folder))
				throw new ToolException(file.getName() + " is in this folder already.");
			docs.add(tools.findDocument(file));
		}
		McpServer.FileOrganizer organizer = tools.organizer;
		if (organizer == null)
			throw new ToolException("DocFetcher's window is not available.");
		List<File> moved = organizer.move(docs, folder, summary(args));
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		if (moved == null || moved.isEmpty()) {
			result.put("moved", false);
			result.put("message", "The user canceled: the documents stay where they are.");
			return result;
		}
		File target = moved.get(0).getParentFile();
		result.put("moved", true);
		result.put("folder_id", tools.ids.idOf(target));
		result.put("folder", tools.location(target));
		List<Object> movedDocs = new ArrayList<Object>();
		for (File file : moved)
			movedDocs.add(McpTools.props("id", tools.ids.idOf(file), "name", file.getName()));
		result.put("documents", movedDocs);
		if (!Util.isCanonicallyEqual(target, folder))
			result.put("message", "The user chose another folder than proposed.");
		if (moved.size() < docs.size())
			result.put("not_moved", docs.size() - moved.size());
		return result;
	}

	@NotNull
	static Map<String, Object> rename(	@NotNull McpTools tools,
										@NotNull Map<String, Object> args) throws ToolException {
		if (!renameAllowed())
			throw new ToolException("Renaming is switched off in DocFetcher's preferences.");
		File file = tools.target(args, "id");
		checkChangeable(file, true);
		String name = newName(file, McpTools.string(args, "name"));
		McpServer.FileOrganizer organizer = tools.organizer;
		if (organizer == null)
			throw new ToolException("DocFetcher's window is not available.");
		ResultDocument doc = tools.findDocument(file);
		File renamed = organizer.rename(doc, name, summary(args));
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		if (renamed == null || renamed.equals(file)) {
			result.put("renamed", false);
			result.put("message", "The user canceled: the name stays " + file.getName() + ".");
			return result;
		}
		result.put("renamed", true);
		result.put("id", tools.ids.idOf(renamed));
		result.put("name", renamed.getName());
		if (!renamed.getName().equals(name))
			result.put("message", "The user chose another name than proposed.");
		return result;
	}

}
