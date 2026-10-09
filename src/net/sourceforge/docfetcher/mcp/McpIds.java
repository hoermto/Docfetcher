/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.mcp;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

import net.sourceforge.docfetcher.mcp.McpTools.ToolException;
import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * Short IDs for the documents and folders the MCP server tells assistants
 * about, instead of their paths: an assistant with its own file access would
 * otherwise go for the files themselves (and its app asks the user for
 * permission to every path). An ID is a hash of the path, so a document keeps
 * its ID while DocFetcher runs and after a restart; only DocFetcher knows
 * which path belongs to an ID - IDs it has handed out (in this run) can be
 * used in the tools.
 */
final class McpIds {

	private static final int MAX_ENTRIES = 200000;

	/** The IDs handed out, oldest first; old ones are dropped beyond the limit. */
	private final Map<String, File> files = new LinkedHashMap<String, File>(1024, 0.75f, true) {
		private static final long serialVersionUID = 1L;
		protected boolean removeEldestEntry(Map.Entry<String, File> eldest) {
			return size() > MAX_ENTRIES;
		}
	};

	/** The ID of the file or folder, remembered so it can be used later. */
	@NotNull
	synchronized String idOf(@NotNull File file) {
		String id = hash(file.getPath().replace(File.separatorChar, '/'));
		files.put(id, file);
		return id;
	}

	/** The file or folder of an ID handed out before. */
	@NotNull
	synchronized File fileOf(@NotNull String id) throws ToolException {
		File file = files.get(id.trim());
		if (file == null)
			throw new ToolException("Unknown id '" + id + "'. Use an id from the results of search or "
				+ "list_folder (after a restart of DocFetcher, search again).");
		return file;
	}

	/** 12 hex digits of the SHA-256 of the path: short, and no path can be read from it. */
	@NotNull
	static String hash(@NotNull String path) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(path.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(12);
			for (int i = 0; i < 6; i++)
				sb.append(String.format("%02x", digest[i]));
			return sb.toString();
		}
		catch (Exception e) {
			throw new IllegalStateException(e); // SHA-256 is part of every Java runtime
		}
	}

}
