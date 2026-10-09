/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.mcp;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.gui.InboxFolders;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * The folder in which AI assistants with their own file access (e.g. Claude
 * Code) may save new files for the user: one of the inbox folders, chosen in
 * the preferences (none by default). DocFetcher doesn't write anything
 * itself. The folder isn't named in advance - an app that sees a path may ask
 * the user for permission in every conversation: only when the assistant
 * calls {@code get_file_folder} to save a file, and only after the user
 * agreed (the answer can be remembered), it gets the folder and the rule to
 * only add new files there. As an inbox folder, DocFetcher indexes the new
 * files, and the user files them from there.
 */
final class McpFileFolder {

	static final String TOOL = "get_file_folder";

	private McpFileFolder() {
	}

	/** The chosen folder, if it is still an inbox folder; null: none. */
	@Nullable
	static File get() {
		String path = SettingsConf.Str.McpFileFolder.get().trim();
		if (path.isEmpty())
			return null;
		File chosen = Util.getCanonicalFile(path);
		for (File inbox : InboxFolders.getFolders())
			if (inbox.equals(chosen))
				return inbox;
		return null;
	}

	/** The rule for the server instructions: no folder in advance. */
	@NotNull
	static String rule() {
		if (get() == null)
			return "Never write into the user's folders yourself.";
		return "Never write into the user's folders yourself - except when the user asks you to save "
			+ "a file you created: then call '" + TOOL + "' for the folder and the rules.";
	}

	/** The folder and the rule, told after the user agreed. */
	@NotNull
	private static String folderRule(@NotNull File folder) {
		return "If you have your own file access (e.g. Claude Code, or a file system tool), you may save "
			+ "new files for the user - e.g. a letter, a table or a picture you created - into the folder "
			+ path(folder) + ". DocFetcher indexes them there, and the user files them. Only add new files "
			+ "to this folder: never change, overwrite, rename, move or delete a file in it, and never "
			+ "write into the user's other folders. Use a new name if a file with the name exists. "
			+ "If you can't write into this folder (no permission yet), ask the user to give you access "
			+ "to exactly this folder - e.g. in Claude Code by confirming the permission prompt or with "
			+ "'/add-dir " + path(folder) + "' - and never save the file elsewhere instead.";
	}

	/** The tool, only while a folder is chosen. */
	@Nullable
	static Map<String, Object> definition() {
		if (get() == null)
			return null;
		Map<String, Object> schema = new LinkedHashMap<String, Object>();
		schema.put("type", "object");
		schema.put("properties", new LinkedHashMap<String, Object>());
		Map<String, Object> annotations = new LinkedHashMap<String, Object>();
		annotations.put("readOnlyHint", Boolean.TRUE);
		Map<String, Object> tool = new LinkedHashMap<String, Object>();
		tool.put("name", TOOL);
		tool.put("description", "Only when the user asks you to save a file you created and you have "
			+ "your own file access (e.g. Claude Code): gives the folder to save it in and the rules. "
			+ "DocFetcher asks the user first. Don't call it otherwise.");
		tool.put("inputSchema", schema);
		tool.put("annotations", annotations);
		return tool;
	}

	/**
	 * The result of the tool: the folder and the rule - if the user allows it,
	 * for good (remembered answer) or when asked now.
	 */
	@NotNull
	static Map<String, Object> call(@Nullable McpServer.FolderApprover approver) throws McpTools.ToolException {
		File folder = get();
		if (folder == null)
			throw new McpTools.ToolException("The user has not chosen a folder for files from AI assistants. "
				+ "Never write into the user's folders yourself.");
		int answer = SettingsConf.Int.McpFileFolderAnswer.get();
		boolean allowed = answer == SettingsConf.MCP_FOLDER_ALLOW
			|| (answer == SettingsConf.MCP_FOLDER_ASK && approver != null && approver.approve(folder));
		if (!allowed)
			throw new McpTools.ToolException("The user doesn't allow you to save files. Don't write into "
				+ "the user's folders; offer the content in the chat instead.");
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("folder", path(folder));
		result.put("exists", folder.isDirectory());
		result.put("rule", folderRule(folder));
		return result;
	}

	@NotNull
	private static String path(@NotNull File folder) {
		return folder.getPath().replace(File.separatorChar, '/');
	}

}
