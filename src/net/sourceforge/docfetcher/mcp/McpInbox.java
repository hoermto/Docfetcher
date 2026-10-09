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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.gui.InboxFolders;
import net.sourceforge.docfetcher.mcp.McpTools.ToolException;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * The tool {@code add_to_inbox}: AI assistants may place new files in an inbox
 * folder, if the user allows it in the preferences. Only new files are
 * created: an existing file is never changed, replaced or deleted (a taken
 * name gets a number, 'Offer (2).docx'), and only directly in the inbox
 * folder. Program files and Office files with macros are refused. By
 * default the assistant never learns where the inbox is: it names an inbox by
 * its tab name, and neither the result nor the errors contain a path. Only if
 * the user allows it (McpInboxPathVisible), assistants with their own file
 * access get the folders, with the rule to only add new files there.
 */
final class McpInbox {

	static final int MAX_MB = 20;

	/** Files that run code when opened. */
	private static final Set<String> BLOCKED_EXTENSIONS = new HashSet<String>(Arrays.asList(
		"exe", "com", "bat", "cmd", "msi", "msp", "msix", "appx", "ps1", "psm1", "psd1", "vbs", "vbe", "js",
		"jse", "mjs", "wsf", "wsh", "ws", "scr", "pif", "cpl", "lnk", "url", "scf", "reg", "hta", "jar", "dll",
		"sys", "drv", "inf", "application", "appref-ms", "gadget", "msc", "sh", "bash", "py", "pyw", "pl",
		"rb", "iso", "img", "vhd", "vhdx", "library-ms", "settingcontent-ms", "chm", "xll",
		// Office files with macros
		"docm", "dotm", "xlsm", "xltm", "xlam", "pptm", "potm", "ppsm", "ppam", "sldm"));

	private static final Set<String> RESERVED_NAMES = new HashSet<String>(Arrays.asList(
		"con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
		"lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"));

	private McpInbox() {
	}

	static boolean isEnabled() {
		return SettingsConf.Bool.McpInboxWrite.get();
	}

	/**
	 * Whether the user also allows assistants with their own file access
	 * (e.g. Claude Code) to save new files directly in the inbox folders:
	 * then they learn the folders' paths. Off by default.
	 */
	static boolean isPathVisible() {
		return isEnabled() && SettingsConf.Bool.McpInboxPathVisible.get();
	}

	/** Whether the user allows downloads from the internet into the inbox (each one asks first). */
	static boolean isDownloadEnabled() {
		return isEnabled() && SettingsConf.Bool.McpInboxDownload.get();
	}

	/** How the assistant may give the user files, for the instructions and the tool. */
	@NotNull
	static String fileRule() {
		if (!isEnabled())
			return "Never write into the user's folders yourself.";
		if (!isPathVisible())
			return "To give the user a new file, use 'add_to_inbox' - never write into the user's "
				+ "folders yourself; DocFetcher stores the file, and you don't need its location.";
		return "To give the user a new file, use 'add_to_inbox', or - if you have your own file "
			+ "access, e.g. for a picture - save it directly into an inbox folder (" + inboxNames()
			+ "). The user allows only this: add new files there. Never change, overwrite, rename, "
			+ "move or delete a file in these folders, and never write into the user's other folders.";
	}

	/** Writes the file and returns its description. */
	@NotNull
	static Map<String, Object> add(@NotNull Map<String, Object> args, @Nullable McpServer.DownloadApprover approver)
			throws ToolException {
		if (!isEnabled())
			throw new ToolException("Adding files to the inbox is switched off in DocFetcher's preferences.");
		String name = checkName(McpTools.string(args, "name"));
		String text = McpTools.string(args, "text");
		String base64 = McpTools.string(args, "content_base64");
		String url = McpTools.string(args, "url");
		Object sheets = args.get("sheets");
		int given = (text != null ? 1 : 0) + (base64 != null ? 1 : 0) + (sheets != null ? 1 : 0) + (url != null ? 1 : 0);
		if (given != 1)
			throw new ToolException("Give the content as one of 'sheets' (.xlsx), 'text' (.docx, .pdf, "
				+ "text files), " + (isDownloadEnabled() ? "'url' (a file on the internet) " : "")
				+ "or 'content_base64' (other files).");
		InboxFolders.Entry inbox = findInbox(McpTools.string(args, "inbox"));
		File folder = inbox.getFolder();
		if (!folder.isDirectory())
			throw new ToolException("The inbox '" + inbox.getTabName() + "' is not available (its folder is missing).");
		String extension = Util.getExtension(name).toLowerCase(Locale.ROOT);
		byte[] data;
		if (url != null) {
			if (!isDownloadEnabled())
				throw new ToolException("Downloading files from the internet is switched off in DocFetcher's preferences.");
			McpDownload.checkAddress(url);
			// The user decides before DocFetcher contacts the address
			if (approver == null || !approver.approve(name, folder, url))
				throw new ToolException("The user did not allow storing this file.");
			data = McpDownload.fetch(url, name);
		}
		else if (sheets != null) {
			if (!extension.equals("xlsx"))
				throw new ToolException("'sheets' is for .xlsx files only.");
			data = McpDocuments.xlsx(sheets);
		}
		else if (text != null) {
			// DocFetcher creates Word and PDF files from the text
			if (extension.equals("docx"))
				data = McpDocuments.docx(text);
			else if (extension.equals("pdf"))
				data = McpDocuments.pdf(text);
			else if (extension.equals("xlsx") || extension.equals("xls"))
				throw new ToolException("For .xlsx files give 'sheets' (the rows of each sheet), not 'text'.");
			else
				data = text.getBytes(StandardCharsets.UTF_8);
		}
		else {
			try {
				data = Base64.getDecoder().decode(base64.replaceAll("\\s", "")); // Line breaks are fine
			}
			catch (IllegalArgumentException e) {
				throw new ToolException("'content_base64' is not valid base64: " + e.getMessage());
			}
		}
		if (data.length > MAX_MB * 1024L * 1024L)
			throw new ToolException("The file is too large (at most " + MAX_MB + " MB).");

		File file = writeNew(folder, name, data);
		if (url != null)
			McpDownload.markFromInternet(file, url);

		// The path only if the user allows direct access to the inbox folders
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		if (isPathVisible())
			result.put("path", file.getPath().replace(File.separatorChar, '/'));
		result.put("name", file.getName());
		result.put("renamed", !file.getName().equals(name));
		result.put("size_bytes", data.length);
		result.put("inbox", inbox.getTabName());
		return result;
	}

	/**
	 * The tab names of the inboxes, for the tool description; with their
	 * folders if the user allows direct access.
	 */
	@NotNull
	static String inboxNames() {
		boolean withPath = isPathVisible();
		StringBuilder names = new StringBuilder();
		for (InboxFolders.Entry inbox : InboxFolders.load()) {
			names.append(names.length() == 0 ? "" : ", ").append("'").append(inbox.getTabName()).append("'");
			if (withPath)
				names.append(": ").append(inbox.getFolder().getPath().replace(File.separatorChar, '/'));
		}
		return names.toString();
	}

	/** The first inbox, or the one with the given tab name (not a path). */
	@NotNull
	private static InboxFolders.Entry findInbox(String wanted) throws ToolException {
		List<InboxFolders.Entry> inboxes = InboxFolders.load();
		if (inboxes.isEmpty())
			throw new ToolException("No inbox folder is set up in DocFetcher.");
		if (wanted == null || wanted.trim().isEmpty())
			return inboxes.get(0);
		StringBuilder names = new StringBuilder();
		for (InboxFolders.Entry inbox : inboxes) {
			if (inbox.getTabName().equalsIgnoreCase(wanted.trim()))
				return inbox;
		}
		throw new ToolException("Unknown inbox '" + wanted + "'. The inboxes are: " + inboxNames());
	}

	/** A plain file name: no folders, nothing Windows refuses or runs. */
	@NotNull
	static String checkName(String name) throws ToolException {
		if (name == null || name.trim().isEmpty())
			throw new ToolException("The argument 'name' is missing.");
		name = name.trim();
		if (name.length() > 200)
			throw new ToolException("The name is too long (at most 200 characters).");
		for (char c : name.toCharArray())
			if (c < 32 || "/\\:*?\"<>|".indexOf(c) >= 0)
				throw new ToolException("The name may not contain folders or any of / \\ : * ? \" < > | - only a file name.");
		if (name.startsWith(".") || name.endsWith("."))
			throw new ToolException("The name may not start or end with a dot.");
		String lower = name.toLowerCase(Locale.ROOT);
		String extension = Util.getExtension(lower);
		String base = lower.contains(".") ? lower.substring(0, lower.indexOf('.')) : lower;
		if (RESERVED_NAMES.contains(base.trim()))
			throw new ToolException("'" + name + "' is a reserved name on Windows.");
		if (BLOCKED_EXTENSIONS.contains(extension))
			throw new ToolException("Files of type ." + extension + " (programs, scripts, shortcuts, Office "
				+ "files with macros) may not be placed in the inbox.");
		return name;
	}

	/**
	 * Creates the file under the given name, or under 'name (2).ext' and so
	 * on if it is taken; never replaces a file.
	 */
	@NotNull
	static File writeNew(@NotNull File folder, @NotNull String name, @NotNull byte[] data) throws ToolException {
		String[] parts = Util.splitFilename(name);
		String suffix = parts[1].isEmpty() ? "" : "." + parts[1];
		for (int i = 1; i <= 1000; i++) {
			File file = new File(folder, i == 1 ? name : parts[0] + " (" + i + ")" + suffix);
			try {
				Files.write(file.toPath(), data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
				return file;
			}
			catch (FileAlreadyExistsException e) {
				continue; // Taken: the next number
			}
			catch (Exception e) {
				// The message would contain the path
				Util.printErr(e);
				throw new ToolException("The file could not be written (" + e.getClass().getSimpleName()
					+ ", e.g. the disk is full or the folder is write-protected).");
			}
		}
		throw new ToolException("Too many files named '" + name + "' in the inbox.");
	}

}
