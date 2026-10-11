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
	private static final int MAX_MOVED = 100;

	private McpFileChanges() {
	}

	static boolean renameAllowed() {
		return SettingsConf.Bool.McpRenameAllowed.get();
	}

	static boolean moveAllowed() {
		return SettingsConf.Bool.McpMoveAllowed.get();
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
				"Proposes a new name for a document" + scope() + ". DocFetcher shows the "
				+ "document and its rename dialog prefilled with the name; the user may change it, "
				+ "confirm or cancel - the result says what happened. The extension stays. Follow "
				+ "the user's naming rules: " + namingRules() + " read_document returns DocFetcher's "
				+ "own suggestion as suggested_name (date and characteristic words), a good start.",
				McpTools.props(
					"id", McpTools.prop("string", "Id of the document, as returned by search or list_folder"),
					"name", McpTools.prop("string", "The proposed file name, with or without the extension")),
				"id", "name"));
		}
		if (moveAllowed()) {
			Map<String, Object> idList = McpTools.prop("array", "Ids of the documents, as returned by "
				+ "search or list_folder");
			idList.put("items", McpTools.props("type", "string"));
			tools.add(McpTools.changingTool(MOVE,
				"Proposes moving documents" + scope() + " into another folder, e.g. to file "
				+ "a new letter where similar documents are. DocFetcher shows the documents and its "
				+ "move dialog with the folder preselected (next to its own suggestions); the user "
				+ "may choose another folder, confirm or cancel - the result says what happened. To "
				+ "find the most suiting folder, read_document returns suggested_folders (the user's "
				+ "fixed targets and folders with similar documents); list_folder shows the others. "
				+ "The target must be an indexed folder.",
				McpTools.props(
					"ids", idList,
					"folder_id", McpTools.prop("string", "Id of the target folder, as returned by "
						+ "read_document's suggested_folders or list_folder")),
				"ids", "folder_id"));
		}
		return tools;
	}

	/** The part of the server instructions about changes, empty without any. */
	@NotNull
	static String rule() {
		List<String> names = new ArrayList<String>(2);
		if (renameAllowed())
			names.add(RENAME);
		if (moveAllowed())
			names.add(MOVE);
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
		List<File> moved = organizer.move(docs, folder);
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
		File renamed = organizer.rename(doc, name);
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
