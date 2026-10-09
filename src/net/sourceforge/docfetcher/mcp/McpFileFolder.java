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
 * itself; it tells the assistant the folder and the rule to only add new
 * files there - in the tool {@code get_file_folder}, whose description the
 * assistant always sees, and in the server instructions. As an inbox folder,
 * DocFetcher indexes the new files, and the user files them from there.
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

	/** How the assistant may give the user files, for the instructions and the tool. */
	@NotNull
	static String rule() {
		File folder = get();
		if (folder == null)
			return "Never write into the user's folders yourself.";
		return "If you have your own file access (e.g. Claude Code, or a file system tool), you may save "
			+ "new files for the user - e.g. a letter, a table or a picture you created - into the folder "
			+ path(folder) + ". DocFetcher indexes them there, and the user files them. Only add new files "
			+ "to this folder: never change, overwrite, rename, move or delete a file in it, and never "
			+ "write into the user's other folders. Use a new name if a file with the name exists.";
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
		tool.put("description", "Tells where to save files you create for the user. " + rule());
		tool.put("inputSchema", schema);
		tool.put("annotations", annotations);
		return tool;
	}

	/** The result of the tool: the folder and the rule. */
	@NotNull
	static Map<String, Object> call() throws McpTools.ToolException {
		File folder = get();
		if (folder == null)
			throw new McpTools.ToolException("The user has not chosen a folder for files from AI assistants. "
				+ "Never write into the user's folders yourself.");
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("folder", path(folder));
		result.put("exists", folder.isDirectory());
		result.put("rule", rule());
		return result;
	}

	@NotNull
	private static String path(@NotNull File folder) {
		return folder.getPath().replace(File.separatorChar, '/');
	}

}
