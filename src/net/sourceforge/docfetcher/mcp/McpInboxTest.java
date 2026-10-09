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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
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
 * Tests add_to_inbox: only when allowed, only new files directly in an inbox
 * folder, never replacing one, no program files, and over HTTP with a file
 * larger than the former request limit.
 */
public final class McpInboxTest {

	private static SearchTestSupport support;
	private static McpServer server;
	private static List<String> savedInboxes;
	private static boolean savedWrite;
	private static boolean savedPathVisible;
	private static boolean savedDownload;
	private File inbox;
	private File second;

	@BeforeClass
	public static void setUpClass() throws Exception {
		support = new SearchTestSupport("Archiv/brief.txt", "Ein Brief.");
		server = new McpServer(support.registry);
		savedInboxes = SettingsConf.StrList.InboxFolders.get();
		savedWrite = SettingsConf.Bool.McpInboxWrite.get();
		savedPathVisible = SettingsConf.Bool.McpInboxPathVisible.get();
		savedDownload = SettingsConf.Bool.McpInboxDownload.get();
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		SettingsConf.StrList.InboxFolders.set(savedInboxes);
		SettingsConf.Bool.McpInboxWrite.set(savedWrite);
		SettingsConf.Bool.McpInboxPathVisible.set(savedPathVisible);
		SettingsConf.Bool.McpInboxDownload.set(savedDownload);
		server.stop();
		support.dispose();
	}

	@Before
	public void setUp() throws Exception {
		inbox = Files.createTempDirectory("mcp-inbox").toFile();
		second = Files.createTempDirectory("mcp-inbox2").toFile();
		SettingsConf.StrList.InboxFolders.set(Arrays.asList(inbox.getPath() + "|Scans", second.getPath() + "|Belege"));
		SettingsConf.Bool.McpInboxWrite.set(true);
		SettingsConf.Bool.McpInboxPathVisible.set(false);
		SettingsConf.Bool.McpInboxDownload.set(false);
	}

	@After
	public void tearDown() {
		for (File folder : new File[] {inbox, second}) {
			for (File file : folder.listFiles())
				file.delete();
			folder.delete();
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> call(String arguments, boolean expectError) {
		Map<String, Object> response = (Map<String, Object>) Json.parse(server.handle(
			"{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"add_to_inbox\",\"arguments\":"
				+ arguments + "}}"));
		Map<String, Object> result = (Map<String, Object>) response.get("result");
		String text = (String) ((Map<String, Object>) ((List<Object>) result.get("content")).get(0)).get("text");
		assertEquals(text, expectError, result.get("isError"));
		return expectError ? java.util.Collections.<String, Object> singletonMap("error", text)
			: (Map<String, Object>) Json.parse(text);
	}

	private static String error(String arguments) {
		return (String) call(arguments, true).get("error");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> listedTool() {
		Map<String, Object> response = (Map<String, Object>) Json.parse(server.handle(
			"{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"));
		for (Object tool : (List<Object>) ((Map<String, Object>) response.get("result")).get("tools"))
			if ("add_to_inbox".equals(((Map<String, Object>) tool).get("name")))
				return (Map<String, Object>) tool;
		return null;
	}

	@Test
	@SuppressWarnings("unchecked")
	public void onlyWhenAllowed() {
		Map<String, Object> tool = listedTool();
		assertEquals(Boolean.FALSE, ((Map<String, Object>) tool.get("annotations")).get("readOnlyHint"));
		assertEquals(Boolean.FALSE, ((Map<String, Object>) tool.get("annotations")).get("destructiveHint"));
		// The inboxes by name, never by path; the assistant is told not to write files itself
		String definition = Json.write(tool);
		assertTrue(definition, definition.contains("'Scans', 'Belege'") && definition.contains("never write into the user's folders"));
		assertFalse(definition, definition.contains(inbox.getName()) || definition.contains(second.getName()));

		SettingsConf.Bool.McpInboxWrite.set(false);
		assertEquals(null, listedTool());
		assertTrue(error("{\"name\":\"a.txt\",\"text\":\"x\"}").contains("switched off"));
		assertEquals(0, inbox.listFiles().length);
	}

	@SuppressWarnings("unchecked")
	private static String instructions() {
		Map<String, Object> response = (Map<String, Object>) Json.parse(server.handle("{\"jsonrpc\":\"2.0\",\"id\":1,"
			+ "\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}"));
		return (String) ((Map<String, Object>) response.get("result")).get("instructions");
	}

	@Test
	public void inboxPathOnlyWhenAllowed() throws Exception {
		String folder = inbox.getPath().replace(File.separatorChar, '/');
		// Default: only through DocFetcher
		assertTrue(instructions(), instructions().contains("never write into the user's folders yourself"));
		assertFalse(instructions().contains(folder));

		// Allowed: the folders with the rule to only add files, in the tool and the instructions
		SettingsConf.Bool.McpInboxPathVisible.set(true);
		String definition = Json.write(listedTool());
		assertTrue(definition, definition.contains("'Scans': " + folder) && definition.contains("Never change, overwrite, rename"));
		assertTrue(instructions(), instructions().contains(folder) && instructions().contains("add new files there"));
		Map<String, Object> result = call("{\"name\":\"Bild.svg\",\"text\":\"<svg/>\"}", false);
		assertEquals(folder + "/Bild.svg", result.get("path"));

		// Only together with adding files
		SettingsConf.Bool.McpInboxWrite.set(false);
		assertFalse(instructions(), instructions().contains(folder));
		assertTrue(instructions().contains("Never write into the user's folders"));
	}

	@Test
	public void newFilesOnly() throws Exception {
		Map<String, Object> first = call("{\"name\":\"Angebot Rosenweg.txt\",\"text\":\"Miete 850 €\"}", false);
		File file = new File(inbox, "Angebot Rosenweg.txt");
		// DocFetcher stores the file: no path for the assistant
		assertEquals("Angebot Rosenweg.txt", first.get("name"));
		assertFalse(first.containsKey("path"));
		assertFalse(Json.write(first), Json.write(first).contains(inbox.getName()));
		assertEquals(Boolean.FALSE, first.get("renamed"));
		assertEquals("Scans", first.get("inbox"));
		assertEquals("Miete 850 €", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));

		// The same name again: a new file with a number, the first one stays as it is
		Map<String, Object> again = call("{\"name\":\"Angebot Rosenweg.txt\",\"text\":\"Neu\"}", false);
		assertEquals("Angebot Rosenweg (2).txt", again.get("name"));
		assertEquals(Boolean.TRUE, again.get("renamed"));
		assertEquals("Miete 850 €", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
		// Windows ignores the case: also taken, the new name keeps the given spelling
		assertEquals("angebot rosenweg (3).txt", call("{\"name\":\"angebot rosenweg.txt\",\"text\":\"x\"}", false).get("name"));
		// Into the second inbox chosen by name
		Map<String, Object> other = call("{\"name\":\"Notiz.txt\",\"inbox\":\"belege\",\"text\":\"Zweite\"}", false);
		assertEquals("Belege", other.get("inbox"));
		assertEquals("Zweite", new String(Files.readAllBytes(new File(second, "Notiz.txt").toPath()), StandardCharsets.UTF_8));
		assertTrue(error("{\"name\":\"x.txt\",\"inbox\":\"Other\",\"text\":\"x\"}").contains("'Scans', 'Belege'"));
		// Not by path
		assertTrue(error("{\"name\":\"x.txt\",\"inbox\":" + Json.write(second.getPath()) + ",\"text\":\"x\"}").contains("Unknown inbox"));

		// A missing inbox folder: the error names the inbox, not the folder
		File moved = new File(second.getPath() + "-moved");
		assertTrue(second.renameTo(moved));
		try {
			String missing = error("{\"name\":\"x.txt\",\"inbox\":\"Belege\",\"text\":\"x\"}");
			assertTrue(missing, missing.contains("'Belege' is not available") && !missing.contains(second.getName()));
		}
		finally {
			assertTrue(moved.renameTo(second));
		}
	}

	@Test
	public void officeFilesFromContent() throws Exception {
		Map<String, Object> xlsx = call("{\"name\":\"Buchungen.xlsx\",\"sheets\":[{\"rows\":[[\"Betrag\"],[850],[\"=SUM(A2:A2)\"]]}]}", false);
		assertEquals("Buchungen.xlsx", xlsx.get("name"));
		org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
			new java.io.FileInputStream(new File(inbox, "Buchungen.xlsx")));
		assertEquals("SUM(A2:A2)", workbook.getSheetAt(0).getRow(2).getCell(0).getCellFormula());
		workbook.close();

		call("{\"name\":\"Brief.docx\",\"text\":\"# Brief\\nText\"}", false);
		byte[] docx = Files.readAllBytes(new File(inbox, "Brief.docx").toPath());
		assertTrue("A zip file, not the text", docx[0] == 'P' && docx[1] == 'K');
		call("{\"name\":\"Brief.pdf\",\"text\":\"# Brief\\nText\"}", false);
		assertTrue(new String(Files.readAllBytes(new File(inbox, "Brief.pdf").toPath()), StandardCharsets.ISO_8859_1).startsWith("%PDF"));

		assertTrue(error("{\"name\":\"a.xlsx\",\"text\":\"a;b\"}").contains("'sheets'"));
		assertTrue(error("{\"name\":\"a.txt\",\"sheets\":[{\"rows\":[]}]}").contains(".xlsx files only"));
		assertTrue(error("{\"name\":\"a.xlsx\",\"text\":\"x\",\"sheets\":[]}").contains("one of"));
		assertTrue(error("{\"name\":\"a.xlsx\",\"sheets\":[{\"rows\":[[\"=SUM(\"]]}]}").contains("Invalid formula"));
	}

	@Test
	public void downloadsOnlyWithTheUsersConsent() {
		String args = "{\"name\":\"Formular.pdf\",\"url\":\"https://93.184.216.34/formular.pdf\"}";
		// Switched off: no download, no question
		assertTrue(error(args).contains("switched off"));
		SettingsConf.Bool.McpInboxDownload.set(true);
		try {
			// No one to ask: refused
			assertTrue(error(args).contains("did not allow"));

			// The user says no: nothing is downloaded or stored
			final List<String> asked = new java.util.ArrayList<String>();
			server.setDownloadApprover(new McpServer.DownloadApprover() {
				public boolean approve(String fileName, File folder, String url) {
					asked.add(fileName + "|" + folder.getName() + "|" + url);
					return false;
				}
			});
			assertTrue(error(args).contains("did not allow"));
			assertEquals(Arrays.asList("Formular.pdf|" + inbox.getName() + "|https://93.184.216.34/formular.pdf"), asked);

			// Addresses of this computer or the home network are refused before asking
			asked.clear();
			assertTrue(error("{\"name\":\"x.pdf\",\"url\":\"https://192.168.178.1/x.pdf\"}").contains("Only public"));
			assertTrue(error("{\"name\":\"x.exe\",\"url\":\"https://93.184.216.34/x.exe\"}").contains(".exe"));
			assertTrue(asked.isEmpty());
			assertEquals(0, inbox.listFiles().length);

			// The tool tells about 'url' only when downloads are allowed
			assertTrue(Json.write(listedTool()).contains("give its https 'url'"));
		}
		finally {
			server.setDownloadApprover(null);
			SettingsConf.Bool.McpInboxDownload.set(false);
		}
		assertFalse(Json.write(listedTool()).contains("give its https 'url'"));
	}

	@Test
	public void refusedNamesAndContents() {
		for (String name : new String[] {"../x.txt", "sub/x.txt", "sub\\x.txt", "C:x.txt", "x.txt:stream",
				"CON.txt", "nul", ".hidden", "name.", "", "tool.exe", "Run.BAT", "macro.docm", "link.lnk",
				"script.ps1", "page.hta"})
			assertTrue(name, call("{\"name\":" + Json.write(name) + ",\"text\":\"x\"}", true).get("error") != null);
		assertTrue(error("{\"name\":\"a.txt\"}").contains("one of"));
		assertTrue(error("{\"name\":\"a.txt\",\"text\":\"x\",\"sheets\":[]}").contains("one of"));
		// Base64 isn't accepted any more
		assertTrue(error("{\"name\":\"a.pdf\",\"content_base64\":\"JVBERg==\"}").contains("one of"));
		assertEquals("Nothing was written", 0, inbox.listFiles().length + second.listFiles().length);

		SettingsConf.StrList.InboxFolders.set(Arrays.<String> asList());
		assertTrue(error("{\"name\":\"a.txt\",\"text\":\"x\"}").contains("No inbox"));
	}

	@Test
	public void sizeLimitAndHttp() throws Exception {
		char[] large = new char[McpInbox.MAX_MB * 1024 * 1024 + 1];
		Arrays.fill(large, 'x');
		String tooLarge = "{\"name\":\"big.txt\",\"text\":\"" + new String(large) + "\"}";
		assertTrue(error(tooLarge).contains("too large"));
		assertEquals(0, inbox.listFiles().length);

		// Over HTTP: 5 MB, more than the former limit of 1 MB per request
		char[] chars = new char[5 * 1024 * 1024];
		Arrays.fill(chars, 'z');
		String content = new String(chars);
		int port = McpServerTest.freePort();
		server.start(port);
		try {
			String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"add_to_inbox\","
				+ "\"arguments\":{\"name\":\"lang.txt\",\"text\":\"" + content + "\"}}}";
			java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
				java.net.http.HttpRequest.newBuilder(new java.net.URI("http://127.0.0.1:" + port + "/mcp"))
					.header("Content-Type", "application/json")
					.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),
				java.net.http.HttpResponse.BodyHandlers.ofString());
			assertEquals(200, response.statusCode());
			assertFalse(response.body(), response.body().contains("\"isError\":true"));
			assertEquals(content, new String(Files.readAllBytes(new File(inbox, "lang.txt").toPath()), StandardCharsets.UTF_8));
		}
		finally {
			server.stop();
		}
	}

}
