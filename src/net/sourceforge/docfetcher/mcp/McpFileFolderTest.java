/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.mcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.model.search.SearchTestSupport;

/**
 * Tests the folder for files from AI assistants: no folder by default, then
 * the tool get_file_folder and the instructions name the chosen inbox folder
 * with the rule to only add new files; a folder that is no longer an inbox
 * counts as none. DocFetcher itself writes nothing.
 */
public final class McpFileFolderTest {

	private static SearchTestSupport support;
	private static McpServer server;
	private static List<String> savedInboxes;
	private static String savedFolder;
	private File inbox;

	@BeforeClass
	public static void setUpClass() throws Exception {
		support = new SearchTestSupport("Archiv/brief.txt", "Ein Brief.");
		server = new McpServer(support.registry);
		savedInboxes = SettingsConf.StrList.InboxFolders.get();
		savedFolder = SettingsConf.Str.McpFileFolder.get();
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		SettingsConf.StrList.InboxFolders.set(savedInboxes);
		SettingsConf.Str.McpFileFolder.set(savedFolder);
		server.stop();
		support.dispose();
	}

	@Before
	public void setUp() throws Exception {
		inbox = Files.createTempDirectory("mcp-files").toFile();
		SettingsConf.StrList.InboxFolders.set(Arrays.asList(inbox.getPath() + "|Scans"));
		SettingsConf.Str.McpFileFolder.set("");
	}

	@After
	public void tearDown() {
		inbox.delete();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> result(String json) {
		return (Map<String, Object>) ((Map<String, Object>) Json.parse(server.handle(json))).get("result");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> listedTool() {
		for (Object tool : (List<Object>) result("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}").get("tools"))
			if (McpFileFolder.TOOL.equals(((Map<String, Object>) tool).get("name")))
				return (Map<String, Object>) tool;
		return null;
	}

	private static String instructions() {
		return (String) result("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
			+ "{\"protocolVersion\":\"2025-06-18\"}}").get("instructions");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> call() {
		Map<String, Object> result = result("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":"
			+ "{\"name\":\"get_file_folder\",\"arguments\":{}}}");
		return result;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> answer(Map<String, Object> result) {
		String text = (String) ((Map<String, Object>) ((List<Object>) result.get("content")).get(0)).get("text");
		return (Map<String, Object>) Json.parse(text);
	}

	@Test
	public void folderOnlyWhenChosenAndAllowed() {
		String folder = inbox.getPath().replace(File.separatorChar, '/');
		// Default: no folder, no tool, never write
		assertNull(listedTool());
		assertTrue(instructions(), instructions().contains("Never write into the user's folders yourself"));
		assertEquals(Boolean.TRUE, call().get("isError"));

		// An inbox folder chosen: the tool, but no folder in advance (an app may ask for permission to any path it sees)
		SettingsConf.Str.McpFileFolder.set(inbox.getPath());
		Map<String, Object> tool = listedTool();
		String description = (String) tool.get("description");
		assertTrue(description, description.contains("DocFetcher asks the user first"));
		assertFalse(description, description.contains(folder));
		assertFalse(instructions().contains(folder));
		assertTrue(instructions(), instructions().contains("get_file_folder"));

		// Asked: no one to ask, the user says no, the user says yes
		final List<String> asked = new java.util.ArrayList<String>();
		final boolean[] allow = {false};
		assertEquals(Boolean.TRUE, call().get("isError"));
		server.setFolderApprover(new McpServer.FolderApprover() {
			public boolean approve(File asking) {
				asked.add(asking.getPath());
				return allow[0];
			}
		});
		try {
			Map<String, Object> refused = call();
			assertEquals(Boolean.TRUE, refused.get("isError"));
			assertEquals(1, asked.size());
			allow[0] = true;
			Map<String, Object> result = call();
			assertEquals(Boolean.FALSE, result.get("isError"));
			Map<String, Object> answer = answer(result);
			assertEquals(folder, answer.get("folder"));
			assertEquals(Boolean.TRUE, answer.get("exists"));
			String rule = (String) answer.get("rule");
			assertTrue(rule, rule.contains("never change, overwrite, rename, move or delete") && rule.contains("/add-dir " + folder));

			// Remembered answers: no question
			asked.clear();
			SettingsConf.Int.McpFileFolderAnswer.set(SettingsConf.MCP_FOLDER_DENY);
			assertEquals(Boolean.TRUE, call().get("isError"));
			SettingsConf.Int.McpFileFolderAnswer.set(SettingsConf.MCP_FOLDER_ALLOW);
			assertEquals(Boolean.FALSE, call().get("isError"));
			assertTrue(asked.isEmpty());
		}
		finally {
			server.setFolderApprover(null);
			SettingsConf.Int.McpFileFolderAnswer.set(SettingsConf.MCP_FOLDER_ASK);
		}

		// No longer an inbox folder: none
		SettingsConf.StrList.InboxFolders.set(Arrays.<String> asList());
		assertNull(listedTool());
		assertFalse(instructions().contains("get_file_folder"));
		assertEquals("DocFetcher writes nothing", 0, inbox.listFiles().length);
	}

}
