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

	private McpFileChanges() {
	}

	static boolean renameAllowed() {
		return SettingsConf.Bool.McpRenameAllowed.get();
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
		return tools;
	}

	/** The part of the server instructions about changes, empty without any. */
	@NotNull
	static String rule() {
		if (!renameAllowed())
			return "";
		return "The user lets you propose changes to files (" + RENAME + "): DocFetcher asks the user "
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
	 * May an assistant propose changing this file: a plain file in an inbox
	 * folder - or anywhere in the indexed folders if the user allows that
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
		for (File inbox : InboxFolders.getFolders())
			if (Util.contains(inbox, file))
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
