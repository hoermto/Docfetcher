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
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;

import com.sun.net.httpserver.Headers;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sourceforge.docfetcher.enums.SettingsConf;

import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.model.search.SearchTestSupport;

/**
 * Tests the MCP server: the protocol of both eras (initialize, tools/list,
 * tools/call, errors; revision 2026-07-28 with server/discover, _meta and
 * headers), the tools with questions like 'which letters about tax arrived
 * latest?' and 'what rent does the tenant in Rosenweg pay?', the access
 * limits and the HTTP transport.
 */
public final class McpServerTest {

	private static final long DAY = 24 * 3600 * 1000L;
	private static SearchTestSupport support;
	private static McpServer server;

	@BeforeClass
	public static void setUpClass() throws Exception {
		long now = System.currentTimeMillis();
		Map<String, Long> modified = new HashMap<String, Long>();
		modified.put("Posteingang/finanzamt-alt.txt", now - 400 * DAY);
		modified.put("Posteingang/finanzamt-neu.txt", now - 3 * DAY);
		modified.put("Posteingang/stadtwerke.txt", now - 1 * DAY);
		support = new SearchTestSupport(modified,
			"Posteingang/finanzamt-alt.txt", "Finanzamt Bonn. Frau Renate Schmidt. Einkommensteuer Januar, Bescheid.",
			"Posteingang/finanzamt-neu.txt", "Finanzamt Bonn. Frau Renate Schmidt. Umsatzsteuer Voranmeldung Januar, Erinnerung.",
			"Posteingang/stadtwerke.txt", "Stadtwerke Bonn. Rechnung Strom Januar.",
			"Mieter/mietvertrag-rosenweg.txt", "Mietvertrag. Mieter: Herr Klaus Meier, Rosenweg 12, 53111 Bonn. "
				+ "Die Kaltmiete beträgt 850,00 EUR monatlich, Nebenkosten 210,00 EUR.",
			"Mieter/lang.txt", repeat("Langer Text. ", 3000));
		server = new McpServer(support.registry);
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		server.stop();
		support.dispose();
	}

	private static String repeat(String s, int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++)
			sb.append(s);
		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> rpc(String json) {
		String response = server.handle(json);
		return (Map<String, Object>) Json.parse(response);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> result(String json) {
		Map<String, Object> response = rpc(json);
		assertNull("Error: " + response.get("error"), response.get("error"));
		return (Map<String, Object>) response.get("result");
	}

	/** Calls a tool and returns the text parts of the result. */
	@SuppressWarnings("unchecked")
	private static List<Object> call(String tool, String arguments, boolean expectError) {
		Map<String, Object> result = result("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
			+ "\"params\":{\"name\":\"" + tool + "\",\"arguments\":" + arguments + "}}");
		assertEquals(Json.write(result.get("content")), expectError, result.get("isError"));
		return (List<Object>) result.get("content");
	}

	@SuppressWarnings("unchecked")
	private static String text(List<Object> content, int index) {
		return (String) ((Map<String, Object>) content.get(index)).get("text");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> json(List<Object> content, int index) {
		return (Map<String, Object>) Json.parse(text(content, index));
	}

	/** A value of the search result at the index: its id, filename, folder... */
	@SuppressWarnings("unchecked")
	private static String result(Map<String, Object> searchResult, int index, String key) {
		List<Object> results = (List<Object>) searchResult.get("results");
		return (String) ((Map<String, Object>) results.get(index)).get(key);
	}

	/** The id of a file or folder, as a search or listing gives it to the client. */
	static String id(McpServer server, File file) {
		return server.tools.ids.idOf(McpTools.resolve(file.getPath()));
	}

	private static String id(File file) {
		return id(server, file);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void initializeAndListTools() {
		Map<String, Object> init = result("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
			+ "{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}");
		assertEquals("2025-03-26", init.get("protocolVersion"));
		assertEquals("DocFetcher", ((Map<String, Object>) init.get("serverInfo")).get("name"));
		assertTrue(((Map<String, Object>) init.get("capabilities")).containsKey("tools"));
		// Unknown versions get the latest one
		Map<String, Object> other = result("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"1999-01-01\"}}");
		assertEquals(McpServer.LATEST_LEGACY_PROTOCOL, other.get("protocolVersion"));

		// Newer versions too: the client decides whether it can work with the newest earlier one
		Map<String, Object> modern = result("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2026-07-28\"}}");
		assertEquals("2025-11-25", modern.get("protocolVersion"));
		// Clients that ask without the capability get empty lists instead of an error
		assertEquals(0, ((List<Object>) result("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"resources/list\"}").get("resources")).size());
		assertEquals(0, ((List<Object>) result("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"prompts/list\"}").get("prompts")).size());

		assertNull("Notifications get no answer", server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
		assertEquals(0, result("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}").size());

		List<Object> tools = (List<Object>) result("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/list\"}").get("tools");
		assertEquals(5, tools.size());
		assertEquals("search", ((Map<String, Object>) tools.get(0)).get("name"));
		assertEquals("read_document", ((Map<String, Object>) tools.get(1)).get("name"));
		assertEquals("get_original_document", ((Map<String, Object>) tools.get(2)).get("name"));
		assertEquals("list_folder", ((Map<String, Object>) tools.get(3)).get("name"));
		assertEquals("show_document", ((Map<String, Object>) tools.get(4)).get("name"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void pathsOnlyWhenAllowed() throws Exception {
		File contract = new File(support.root, "Mieter/mietvertrag-rosenweg.txt");
		String contractPath = McpTools.resolve(contract.getPath()).getPath().replace(File.separatorChar, '/');
		// Default: ids only, a path isn't accepted
		Map<String, Object> found = json(call("search", "{\"query\":\"Rosenweg\"}", false), 0);
		assertFalse(Json.write(found), Json.write(found).contains(contractPath));
		assertTrue(text(call("read_document", "{\"path\":" + Json.write(contract.getPath()) + "}", true), 0)
			.contains("'id' is missing"));
		assertFalse(Json.write(rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}")).contains("instead of the id"));

		net.sourceforge.docfetcher.enums.SettingsConf.Bool.McpPathsVisible.set(true);
		try {
			found = json(call("search", "{\"query\":\"Rosenweg\"}", false), 0);
			assertEquals(contractPath, result(found, 0, "path"));
			assertTrue(result(found, 0, "id") != null);
			// Paths work as arguments, ids still do
			assertTrue(text(call("read_document", "{\"path\":" + Json.write(contract.getPath()) + "}", false), 1).contains("Kaltmiete"));
			assertTrue(text(call("read_document", "{\"id\":" + Json.write(result(found, 0, "id")) + "}", false), 1).contains("Kaltmiete"));
			Map<String, Object> listing = json(call("list_folder", "{\"path\":" + Json.write(contract.getParent()) + "}", false), 0);
			assertTrue(Json.write(listing), ((String) listing.get("path")).endsWith("/Mieter"));
			assertTrue(Json.write(rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}")).contains("instead of the id"));
			assertTrue((String) result("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}")
				.get("instructions"), ((String) result("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
				+ "{\"protocolVersion\":\"2025-06-18\"}}").get("instructions")).contains("The user allows file paths"));
			// Still only inside the indexed folders
			File outside = File.createTempFile("docfetcher-secret", ".txt");
			try {
				assertTrue(text(call("read_document", "{\"path\":" + Json.write(outside.getPath()) + "}", true), 0)
					.startsWith("Access denied"));
			}
			finally {
				outside.delete();
			}
		}
		finally {
			net.sourceforge.docfetcher.enums.SettingsConf.Bool.McpPathsVisible.set(false);
		}
	}

	@Test
	public void renameOnlyWhenAllowedAndConfirmed() throws Exception {
		File inbox = new File(support.root, "Posteingang");
		File scan = new File(inbox, "scan_0815.txt");
		java.nio.file.Files.write(scan.toPath(), "Stadtwerke Bonn. Rechnung Strom Oktober 2026, Abschlag.".getBytes("UTF-8"));
		File elsewhere = new File(support.root, "Mieter/notiz-umbenennen.txt");
		java.nio.file.Files.write(elsewhere.toPath(), "Notiz zum Mieter.".getBytes("UTF-8"));
		final List<String> proposed = new java.util.ArrayList<String>();
		final boolean[] accept = {false};
		server.setOrganizer(new McpServer.FileOrganizer() {
			public File rename(ResultDocument doc, String name) {
				proposed.add(name);
				if (!accept[0])
					return null; // The user canceled
				File file = doc.getPath().getCanonicalFile();
				File target = new File(file.getParentFile(), name);
				return file.renameTo(target) ? target : null;
			}
		});
		List<String> saved = SettingsConf.StrList.InboxFolders.get();
		SettingsConf.StrList.InboxFolders.set(java.util.Arrays.asList(inbox.getPath() + "|Scans"));
		try {
			// Off by default: no tool, no suggestion
			String tools = Json.write(rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"));
			assertFalse(tools.contains("rename_document"));
			assertTrue(text(call("rename_document", "{\"id\":" + Json.write(id(scan)) + ",\"name\":\"x\"}", true), 0)
				.contains("switched off"));

			SettingsConf.Bool.McpRenameAllowed.set(true);
			tools = Json.write(rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"));
			assertTrue(tools.contains("rename_document"));
			assertTrue(tools, tools.contains("\"readOnlyHint\":false"));
			assertTrue(tools, tools.contains("JJJJ-MM-TT") || tools.contains("YYYY-MM-DD"));
			assertTrue(McpServer.instructions().contains("never delete"));
			Map<String, Object> read = json(call("read_document", "{\"id\":" + Json.write(id(scan)) + "}", false), 0);
			assertTrue(Json.write(read), ((String) read.get("suggested_name")).endsWith(".txt"));

			// Only in the inbox; a name, not a path
			assertTrue(text(call("rename_document", "{\"id\":" + Json.write(id(elsewhere)) + ",\"name\":\"neu\"}", true), 0)
				.startsWith("Only documents in the user's inbox folders"));
			assertTrue(text(call("rename_document", "{\"id\":" + Json.write(id(scan)) + ",\"name\":\"..\\\\x\"}", true), 0)
				.startsWith("A name, not a path"));
			assertTrue(proposed.isEmpty());

			// Canceled: nothing happens; confirmed: renamed, the extension kept
			Map<String, Object> result = json(call("rename_document", "{\"id\":" + Json.write(id(scan))
				+ ",\"name\":\"2026-10-01_Stadtwerke-Stromrechnung\"}", false), 0);
			assertEquals(Boolean.FALSE, result.get("renamed"));
			assertTrue(scan.isFile());
			assertEquals("2026-10-01_Stadtwerke-Stromrechnung.txt", proposed.get(0));
			accept[0] = true;
			result = json(call("rename_document", "{\"id\":" + Json.write(id(scan))
				+ ",\"name\":\"2026-10-01_Stadtwerke-Stromrechnung.txt\"}", false), 0);
			assertEquals(Boolean.TRUE, result.get("renamed"));
			assertEquals("2026-10-01_Stadtwerke-Stromrechnung.txt", result.get("name"));
			File renamed = new File(inbox, "2026-10-01_Stadtwerke-Stromrechnung.txt");
			assertTrue(renamed.isFile());
			assertEquals(id(renamed), result.get("id"));
			scan = renamed;

			// Outside the inbox only if the user allows that too
			SettingsConf.Bool.McpOutsideInbox.set(true);
			result = json(call("rename_document", "{\"id\":" + Json.write(id(elsewhere)) + ",\"name\":\"notiz-neu\"}", false), 0);
			assertEquals(Boolean.TRUE, result.get("renamed"));
			elsewhere = new File(elsewhere.getParentFile(), "notiz-neu.txt");
		}
		finally {
			SettingsConf.Bool.McpRenameAllowed.set(false);
			SettingsConf.Bool.McpOutsideInbox.set(false);
			SettingsConf.StrList.InboxFolders.set(saved);
			server.setOrganizer(null);
			scan.delete();
			elsewhere.delete();
		}
	}

	@Test
	public void theBinIsGoneForAssistants() throws Exception {
		File contract = new File(support.root, "Mieter/mietvertrag-rosenweg.txt");
		String contractId = id(contract);
		// The folder 'Mieter' plays the DocFetcher bin
		net.sourceforge.docfetcher.enums.SettingsConf.Str.BinFolder.set(contract.getParent());
		try {
			Map<String, Object> found = json(call("search", "{\"query\":\"Rosenweg Kaltmiete\"}", false), 0);
			assertFalse(Json.write(found), Json.write(found).contains("rosenweg"));
			assertTrue(text(call("read_document", "{\"id\":" + Json.write(contractId) + "}", true), 0)
				.startsWith("Not found (deleted)"));
			Map<String, Object> listing = json(call("list_folder", "{\"id\":" + Json.write(id(support.root)) + "}", false), 0);
			assertFalse(Json.write(listing), Json.write(listing).contains("Mieter"));
			assertTrue(Json.write(listing), Json.write(listing).contains("Posteingang"));
		}
		finally {
			net.sourceforge.docfetcher.enums.SettingsConf.Str.BinFolder.set("");
		}
		assertTrue(text(call("read_document", "{\"id\":" + Json.write(contractId) + "}", false), 1).contains("Kaltmiete"));
	}

	@Test
	public void showDocuments() throws Exception {
		File contract = new File(support.root, "Mieter/mietvertrag-rosenweg.txt");
		File letter = new File(support.root, "Posteingang/stadtwerke.txt");
		assertTrue(text(call("show_document", "{\"ids\":[" + Json.write(id(contract)) + "]}", true), 0)
			.contains("not available"));

		final List<String> shown = new java.util.ArrayList<String>();
		server.setViewer(new McpServer.DocumentViewer() {
			public void show(List<ResultDocument> docs) {
				for (ResultDocument doc : docs)
					shown.add(doc.getFilename());
			}
		});
		try {
			// Several documents, the order kept; a missing one is reported
			File missing = new File(support.root, "Mieter/gibt-es-nicht.txt");
			Map<String, Object> result = json(call("show_document", "{\"ids\":[" + Json.write(id(letter)) + ","
				+ Json.write(id(contract)) + "," + Json.write(id(missing)) + "]}", false), 0);
			assertEquals(java.util.Arrays.asList("stadtwerke.txt", "mietvertrag-rosenweg.txt"), shown);
			assertEquals(2, ((List<?>) result.get("shown")).size());
			assertTrue(result.get("not_found").toString().contains("gibt-es-nicht.txt"));

			// A single path instead of a list
			shown.clear();
			call("show_document", "{\"ids\":" + Json.write(id(contract)) + "}", false);
			assertEquals(java.util.Arrays.asList("mietvertrag-rosenweg.txt"), shown);

			// Nothing found, nothing given, not accessible: errors, nothing shown
			shown.clear();
			assertTrue(text(call("show_document", "{\"ids\":[" + Json.write(id(missing)) + "]}", true), 0)
				.startsWith("Not found"));
			assertTrue(text(call("show_document", "{\"ids\":[]}", true), 0).contains("missing"));
			File outside = File.createTempFile("docfetcher-secret", ".txt");
			try {
				assertTrue(text(call("show_document", "{\"ids\":[" + Json.write(id(outside)) + "]}", true), 0)
					.startsWith("Access denied"));
			}
			finally {
				outside.delete();
			}
			assertTrue(shown.isEmpty());
		}
		finally {
			server.setViewer(null);
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void originalDocuments() throws Exception {
		// A text file: its description, then the bytes as an embedded resource
		File contract = new File(support.root, "Mieter/mietvertrag-rosenweg.txt");
		List<Object> content = call("get_original_document", "{\"id\":" + Json.write(id(contract)) + "}", false);
		assertEquals(2, content.size());
		Map<String, Object> meta = json(content, 0);
		assertEquals("mietvertrag-rosenweg.txt", meta.get("name"));
		assertEquals("text/plain", meta.get("mime_type"));
		assertEquals(contract.length(), ((Number) meta.get("size_bytes")).longValue());
		Map<String, Object> part = (Map<String, Object>) content.get(1);
		assertEquals("resource", part.get("type"));
		Map<String, Object> resource = (Map<String, Object>) part.get("resource");
		assertEquals("text/plain", resource.get("mimeType"));
		assertTrue(resource.get("uri").toString(), resource.get("uri").toString().startsWith("docfetcher:/"));
		assertTrue("Not a link to the file", !resource.get("uri").toString().startsWith("file:"));
		assertTrue(java.util.Arrays.equals(java.nio.file.Files.readAllBytes(contract.toPath()),
			java.util.Base64.getDecoder().decode((String) resource.get("blob"))));

		// An image the assistant can look at; a TIFF comes as a file
		File png = new File(support.root, "Posteingang/scan.png");
		File tiff = new File(support.root, "Posteingang/scan.tif");
		byte[] bytes = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
		java.nio.file.Files.write(png.toPath(), bytes);
		java.nio.file.Files.write(tiff.toPath(), bytes);
		try {
			Map<String, Object> image = (Map<String, Object>) call("get_original_document",
				"{\"id\":" + Json.write(id(png)) + "}", false).get(1);
			assertEquals("image", image.get("type"));
			assertEquals("image/png", image.get("mimeType"));
			assertTrue(java.util.Arrays.equals(bytes, java.util.Base64.getDecoder().decode((String) image.get("data"))));
			Map<String, Object> file = (Map<String, Object>) call("get_original_document",
				"{\"id\":" + Json.write(id(tiff)) + "}", false).get(1);
			assertEquals("resource", file.get("type"));
			assertEquals("image/tiff", ((Map<String, Object>) file.get("resource")).get("mimeType"));
		}
		finally {
			png.delete();
			tiff.delete();
		}

		// Too large (limit lowered to 1 MB), missing, outside the indexed folders
		File large = new File(support.root, "Posteingang/large.pdf");
		java.nio.file.Files.write(large.toPath(), new byte[1024 * 1024 + 1]);
		net.sourceforge.docfetcher.enums.ProgramConf.Int.McpMaxOriginalMB.load("1");
		try {
			String error = text(call("get_original_document", "{\"id\":" + Json.write(id(large)) + "}", true), 0);
			assertTrue(error, error.contains("too large") && error.contains("read_document"));
		}
		finally {
			net.sourceforge.docfetcher.enums.ProgramConf.Int.McpMaxOriginalMB.load("50");
			large.delete();
		}
		assertTrue(text(call("get_original_document", "{\"id\":" + Json.write(id(large)) + "}", true), 0)
			.startsWith("Not a file"));
		File outside = File.createTempFile("docfetcher-secret", ".pdf");
		try {
			assertTrue(text(call("get_original_document", "{\"id\":" + Json.write(id(outside)) + "}", true), 0)
				.startsWith("Access denied"));
		}
		finally {
			outside.delete();
		}

		assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", McpTools.mimeType("Brief.DOCX"));
		assertEquals("application/octet-stream", McpTools.mimeType("notes.xyz"));
		assertEquals("application/octet-stream", McpTools.mimeType("README"));
	}

	/** A request of revision 2026-07-28: _meta in the body, the same values in the headers. */
	private static McpServer.Reply modern(String method, String params, String nameHeader, String version) {
		Headers headers = new Headers();
		headers.add("MCP-Protocol-Version", version);
		headers.add("Mcp-Method", method);
		if (nameHeader != null)
			headers.add("Mcp-Name", nameHeader);
		String meta = "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"" + version + "\","
			+ "\"io.modelcontextprotocol/clientInfo\":{\"name\":\"test\",\"version\":\"1\"},"
			+ "\"io.modelcontextprotocol/clientCapabilities\":{}}";
		return server.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":{"
			+ (params.isEmpty() ? meta : params + "," + meta) + "}}", headers);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> modernResult(McpServer.Reply reply) {
		assertEquals(reply.body, 200, reply.status);
		Map<String, Object> result = (Map<String, Object>) ((Map<String, Object>) Json.parse(reply.body)).get("result");
		assertEquals("complete", result.get("resultType"));
		Map<String, Object> meta = (Map<String, Object>) result.get("_meta");
		assertEquals("DocFetcher", ((Map<String, Object>) meta.get("io.modelcontextprotocol/serverInfo")).get("name"));
		return result;
	}

	@SuppressWarnings("unchecked")
	private static long errorCode(McpServer.Reply reply, int status) {
		assertEquals(reply.body, status, reply.status);
		return (Long) ((Map<String, Object>) ((Map<String, Object>) Json.parse(reply.body)).get("error")).get("code");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void modernRevision() {
		String v = McpServer.MODERN_PROTOCOL;
		Map<String, Object> discover = modernResult(modern("server/discover", "", null, v));
		List<Object> versions = (List<Object>) discover.get("supportedVersions");
		assertTrue(versions.contains("2026-07-28"));
		assertTrue("Earlier clients of the last year", versions.containsAll(java.util.Arrays.asList("2025-11-25", "2025-06-18", "2025-03-26")));
		assertTrue(((Map<String, Object>) discover.get("capabilities")).containsKey("tools"));
		assertTrue(((String) discover.get("instructions")).contains("search"));
		assertEquals("private", discover.get("cacheScope"));

		Map<String, Object> list = modernResult(modern("tools/list", "", null, v));
		assertEquals(5, ((List<Object>) list.get("tools")).size());
		assertTrue(((Number) list.get("ttlMs")).longValue() > 0);

		String search = "\"name\":\"search\",\"arguments\":{\"query\":\"Rosenweg\"}";
		Map<String, Object> call = modernResult(modern("tools/call", search, "search", v));
		assertEquals(Boolean.FALSE, call.get("isError"));
		assertTrue(Json.write(call.get("content")).contains("mietvertrag-rosenweg.txt"));
		assertNull("Tool results aren't cached", call.get("ttlMs"));
		// Names may come Base64-encoded
		modernResult(modern("tools/call", search, "=?base64?c2VhcmNo?=", v));

		// Headers must match the body
		assertEquals(-32020L, errorCode(modern("tools/call", search, "read_document", v), 400));
		assertEquals(-32020L, errorCode(modern("tools/call", search, null, v), 400));
		Headers wrongMethod = new Headers();
		wrongMethod.add("MCP-Protocol-Version", v);
		wrongMethod.add("Mcp-Method", "tools/call");
		assertEquals(-32020L, errorCode(server.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{\"_meta\":"
			+ "{\"io.modelcontextprotocol/protocolVersion\":\"" + v + "\",\"io.modelcontextprotocol/clientCapabilities\":{}}}}", wrongMethod), 400));

		// A version from the future: the error lists the supported ones
		McpServer.Reply future = modern("tools/list", "", null, "2099-01-01");
		assertEquals(-32022L, errorCode(future, 400));
		assertTrue(future.body.contains("\"supported\":[\"2026-07-28\""));
		assertTrue(future.body.contains("\"requested\":\"2099-01-01\""));

		// The modern header without _meta, or _meta without capabilities: invalid params
		Headers onlyHeader = new Headers();
		onlyHeader.add("MCP-Protocol-Version", v);
		onlyHeader.add("Mcp-Method", "tools/list");
		assertEquals(-32602L, errorCode(server.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}", onlyHeader), 400));
		assertEquals(-32602L, errorCode(server.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{\"_meta\":"
			+ "{\"io.modelcontextprotocol/protocolVersion\":\"" + v + "\"}}}", onlyHeader), 400));

		// Methods of the earlier era are gone; unknown methods give 404
		assertEquals(-32601L, errorCode(modern("ping", "", null, v), 404));
		assertEquals(-32601L, errorCode(modern("initialize", "", null, v), 404));
		modernResult(modern("resources/list", "", null, v));

		// An earlier-era request with an unknown version header
		Headers oldHeader = new Headers();
		oldHeader.add("MCP-Protocol-Version", "1999-01-01");
		assertEquals(-32022L, errorCode(server.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}", oldHeader), 400));
		Headers knownHeader = new Headers();
		knownHeader.add("MCP-Protocol-Version", "2025-06-18");
		assertEquals(200, server.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}", knownHeader).status);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void protocolErrors() {
		assertEquals(-32700L, ((Map<String, Object>) rpc("{nope").get("error")).get("code"));
		assertEquals(-32601L, ((Map<String, Object>) rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/read\"}").get("error")).get("code"));
		List<Object> content = call("delete_everything", "{}", true);
		assertTrue(text(content, 0).contains("Unknown tool"));
		assertTrue(text(call("search", "{}", true), 0).contains("query"));
	}

	@Test
	public void latestTaxLettersForRenate() {
		// 'I am Renate: what letters did arrive latest regarding tax for Jan?'
		Map<String, Object> found = json(call("search",
			"{\"query\":\"Renate *steuer* Januar\",\"sort\":\"newest\"}", false), 0);
		assertEquals(2L, found.get("total_found"));
		assertTrue(result(found, 0, "filename"), result(found, 0, "filename").equals("finanzamt-neu.txt"));
		assertTrue(result(found, 1, "filename").equals("finanzamt-alt.txt"));
		assertTrue(Json.write(found), Json.write(found).contains("snippet"));

		// Only the last month
		String lastMonth = new java.text.SimpleDateFormat("yyyy-MM-dd").format(
			new java.util.Date(System.currentTimeMillis() - 30 * DAY));
		Map<String, Object> recent = json(call("search",
			"{\"query\":\"Renate *steuer* Januar\",\"modified_after\":\"" + lastMonth + "\"}", false), 0);
		assertEquals(1L, recent.get("total_found"));
		assertTrue(result(recent, 0, "filename").equals("finanzamt-neu.txt"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void tooManyResultsComeWithAnOverview() {
		// Four documents mention Bonn, only one is returned: the newest (the lease,
		// written just now), plus an overview
		Map<String, Object> found = json(call("search", "{\"query\":\"Bonn\",\"max_results\":1}", false), 0);
		assertEquals(4L, found.get("total_found"));
		assertEquals(1L, found.get("returned"));
		assertTrue("Newest first", result(found, 0, "filename").equals("mietvertrag-rosenweg.txt"));
		Map<String, Object> overview = (Map<String, Object>) found.get("overview");
		assertTrue(Json.write(overview), overview.containsKey("oldest") && overview.containsKey("newest"));
		assertTrue(Json.write(overview), overview.containsKey("by_month"));
		List<Object> folders = (List<Object>) overview.get("top_folders");
		Map<String, Object> top = (Map<String, Object>) folders.get(0);
		assertEquals(support.root.getName() + "/Posteingang", top.get("folder"));
		assertEquals(3L, top.get("count"));
		assertEquals("The id works as a search filter", id(new File(support.root, "Posteingang")), top.get("id"));
		assertFalse("No paths", Json.write(found).contains(support.root.getParent().replace('\\', '/')));
		String words = Json.write(overview.get("name_words"));
		assertTrue(words, words.contains("finanzamt (2)"));
		assertFalse("Query words are no hints", words.contains("bonn"));
		// Words of the texts, with the share of the sampled documents containing them
		assertEquals(4L, overview.get("sampled_documents"));
		String content = Json.write(overview.get("content_words"));
		assertTrue(content, content.contains("renate (2/4)"));
		assertTrue(content, content.contains("januar (3/4)"));
		assertFalse(content, content.contains("bonn"));
		assertTrue((String) overview.get("hint"), ((String) overview.get("hint")).startsWith("4 documents found"));

		// Everything returned: no overview
		Map<String, Object> all = json(call("search", "{\"query\":\"Bonn\"}", false), 0);
		assertFalse(all.containsKey("overview"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void rentOfTheTenantInRosenweg() {
		// 'What rent does my client in Rosenweg have?'
		Map<String, Object> found = json(call("search", "{\"query\":\"Rosenweg *miete*\"}", false), 0);
		assertEquals(1L, found.get("total_found"));
		String id = result(found, 0, "id");
		assertEquals(support.root.getName() + "/Mieter", result(found, 0, "folder"));
		List<Object> content = call("read_document", "{\"id\":" + Json.write(id) + "}", false);
		Map<String, Object> meta = json(content, 0);
		assertEquals(id, meta.get("id"));
		assertFalse("No path", meta.containsKey("path"));
		assertFalse("Short document, no more parts", meta.containsKey("next_offset"));
		assertTrue(text(content, 1), text(content, 1).contains("Kaltmiete beträgt 850,00 EUR"));
	}

	/**
	 * Like generated contract forms: the printed form (labels) is drawn
	 * first, the filled in values afterwards, on the same lines.
	 */
	private static void writeGeneratedForm(File file) throws Exception {
		org.apache.pdfbox.pdmodel.PDDocument pdf = new org.apache.pdfbox.pdmodel.PDDocument();
		try {
			org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
			pdf.addPage(page);
			org.apache.pdfbox.pdmodel.PDPageContentStream content = new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf, page);
			String[][] lines = { { "Grundmiete", "620,00 Euro" }, { "Betriebskostenvorauszahlung", "70,00 Euro" },
				{ "Gesamtbetrag", "790,00 Euro" } };
			for (int pass = 0; pass < 2; pass++) {
				for (int i = 0; i < lines.length; i++) {
					content.beginText();
					content.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12);
					content.newLineAtOffset(pass == 0 ? 72 : 300, 700 - i * 20);
					content.showText(lines[i][pass]);
					content.endText();
				}
			}
			content.close();
			pdf.save(file);
		}
		finally {
			pdf.close();
		}
	}

	@Test
	public void pdfsAreReadInVisualOrder() throws Exception {
		File file = new File(support.root, "Mieter/vertrag-generiert.pdf");
		writeGeneratedForm(file);
		try {
			String visual = text(call("read_document", "{\"id\":" + Json.write(id(file)) + "}", false), 1);
			assertTrue(visual, visual.contains("Grundmiete 620,00 Euro"));
			assertTrue(visual, visual.contains("Gesamtbetrag 790,00 Euro"));
			String stream = text(call("read_document", "{\"id\":" + Json.write(id(file)) + ",\"order\":\"stream\"}", false), 1);
			assertTrue(stream, stream.contains("620,00 Euro"));
			assertFalse("Drawing order separates labels and values: " + stream, stream.contains("Grundmiete 620,00"));
		}
		finally {
			file.delete();
		}
	}

	/**
	 * Snippets must not recognize scans on the fly (minutes per document): a
	 * scan without stored text, found by its file name, gets no snippet.
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void snippetsDontRecognizeScans() throws Exception {
		File scan = new File(support.root, "Posteingang/scan-zebra.pdf");
		org.apache.pdfbox.pdmodel.PDDocument pdf = new org.apache.pdfbox.pdmodel.PDDocument();
		try {
			org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
			pdf.addPage(page);
			java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(1240, 400, java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
			java.awt.Graphics2D g = image.createGraphics();
			g.setColor(java.awt.Color.WHITE);
			g.fillRect(0, 0, image.getWidth(), image.getHeight());
			g.setColor(java.awt.Color.BLACK);
			g.setFont(new java.awt.Font(java.awt.Font.SERIF, java.awt.Font.PLAIN, 60));
			g.drawString("Stadtwerke Bonn Rechnung", 100, 200);
			g.dispose();
			org.apache.pdfbox.pdmodel.PDPageContentStream content = new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf, page);
			content.drawImage(org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(pdf, image), 36, 500, 540, 174);
			content.close();
			pdf.save(scan);
		}
		finally {
			pdf.close();
		}
		updateIndexWithoutOcr(); // Indexed as it is: its text isn't recognized and stored now
		try {
			long start = System.currentTimeMillis();
			Map<String, Object> found = json(call("search", "{\"query\":\"zebra\"}", false), 0);
			long millis = System.currentTimeMillis() - start;
			boolean seen = false;
			for (Object result : (List<Object>) found.get("results")) {
				Map<String, Object> map = (Map<String, Object>) result;
				if ("scan-zebra.pdf".equals(map.get("filename"))) {
					seen = true;
					assertFalse("No text recognition for the snippet", map.containsKey("snippet"));
				}
			}
			assertTrue("Found by its file name: " + Json.write(found), seen);
			assertTrue("Search took " + millis + " ms", millis < 10000);
		}
		finally {
			scan.delete();
			updateIndexWithoutOcr(); // Out of the index again, for the other tests
		}
	}

	private static void updateIndexWithoutOcr() throws Exception {
		boolean ocr = net.sourceforge.docfetcher.enums.SettingsConf.Bool.OcrEnabled.get();
		net.sourceforge.docfetcher.enums.SettingsConf.Bool.OcrEnabled.set(false);
		try {
			((net.sourceforge.docfetcher.model.index.file.FileIndex) support.registry.getIndexes().get(0))
				.update(new net.sourceforge.docfetcher.model.index.IndexingReporter(),
					net.sourceforge.docfetcher.model.Cancelable.nullCancelable);
			support.registry.getSearcher().replaceLuceneSearcher();
		}
		finally {
			net.sourceforge.docfetcher.enums.SettingsConf.Bool.OcrEnabled.set(ocr);
		}
	}

	@Test
	public void longDocumentsComeInParts() {
		String path = id(new File(support.root, "Mieter/lang.txt"));
		List<Object> first = call("read_document", "{\"id\":" + Json.write(path) + ",\"max_chars\":1000}", false);
		Map<String, Object> meta = json(first, 0);
		assertEquals(1000L, meta.get("returned_chars"));
		assertEquals(1000L, meta.get("next_offset"));
		assertEquals(1000, text(first, 1).length());
		List<Object> second = call("read_document", "{\"id\":" + Json.write(path) + ",\"offset\":1000}", false);
		assertEquals(1000L, json(second, 0).get("offset"));
		// The default part size
		List<Object> part = call("read_document", "{\"id\":" + Json.write(path) + "}", false);
		assertEquals((long) McpTools.DEFAULT_MAX_CHARS, json(part, 0).get("returned_chars"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void listFolders() {
		Map<String, Object> roots = json(call("list_folder", "{}", false), 0);
		assertTrue(Json.write(roots), Json.write(roots).contains("\"name\":\"" + support.root.getName() + "\""));
		Map<String, Object> listing = json(call("list_folder", "{\"id\":" + Json.write(id(new File(support.root, "Posteingang"))) + "}", false), 0);
		assertEquals(support.root.getName() + "/Posteingang", listing.get("folder"));
		List<Object> entries = (List<Object>) listing.get("entries");
		assertEquals(3, entries.size());
		assertEquals("Newest first", "stadtwerke.txt", ((Map<String, Object>) entries.get(0)).get("filename"));
		// The ids of the listing work in the other tools
		String entryId = (String) ((Map<String, Object>) entries.get(0)).get("id");
		assertTrue(text(call("read_document", "{\"id\":" + Json.write(entryId) + "}", false), 1).contains("Stadtwerke"));
		assertFalse("No paths", Json.write(listing).contains(support.root.getParent().replace('\\', '/')));
		// A folder id as search filter
		Map<String, Object> filtered = json(call("search", "{\"query\":\"Bonn\",\"folder_id\":"
			+ Json.write(id(new File(support.root, "Mieter"))) + "}", false), 0);
		assertEquals(1L, filtered.get("total_found"));
	}

	/**
	 * Indexes with relative paths report their root like 'C:/DocFetcher/../Documents';
	 * reading and listing must work with the normalized paths all the same, and
	 * the results show normalized paths.
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void indexRootWithDetour() throws Exception {
		SearchTestSupport detour = new SearchTestSupport(true, new HashMap<String, Long>(),
			"Mieter/brief.txt", "Mahnung wegen Mietrückstand an Herrn Nöthen");
		McpServer detourServer = new McpServer(detour.registry);
		try {
			String found = detourServer.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":"
				+ "{\"name\":\"search\",\"arguments\":{\"query\":\"Nöthen Mahnung\"}}}");
			Map<String, Object> result = (Map<String, Object>) ((Map<String, Object>) Json.parse(found)).get("result");
			String text = (String) ((Map<String, Object>) ((List<Object>) result.get("content")).get(0)).get("text");
			Map<String, Object> search = (Map<String, Object>) Json.parse(text);
			assertEquals(1L, search.get("total_found"));
			String path = result(search, 0, "id");
			assertFalse("Normalized: " + result(search, 0, "folder"), result(search, 0, "folder").contains(".."));

			String read = detourServer.handle("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":"
				+ "{\"name\":\"read_document\",\"arguments\":{\"id\":" + Json.write(path) + "}}}");
			assertTrue(read, read.contains("\"isError\":false"));
			assertTrue(read, read.contains("Mahnung wegen Mietrückstand"));

			String list = detourServer.handle("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":"
				+ "{\"name\":\"list_folder\",\"arguments\":{\"id\":" + Json.write(id(detourServer, new File(detour.root, "Mieter"))) + "}}}");
			assertTrue(list, list.contains("\"isError\":false") && list.contains("brief.txt"));
		}
		finally {
			detourServer.stop();
			detour.dispose();
		}
	}

	@Test
	public void onlyIndexedFoldersAreAccessible() throws Exception {
		File outside = File.createTempFile("docfetcher-secret", ".txt");
		try {
			assertTrue(text(call("read_document", "{\"id\":" + Json.write(id(outside)) + "}", true), 0)
				.startsWith("Access denied"));
			assertTrue(text(call("list_folder", "{\"id\":" + Json.write(id(outside.getParentFile())) + "}", true), 0)
				.startsWith("Access denied"));
			File sneaky = new File(support.root, "../" + outside.getName());
			assertTrue(text(call("read_document", "{\"id\":" + Json.write(id(sneaky)) + "}", true), 0)
				.startsWith("Access denied"));
			// Ids nobody handed out
			assertTrue(text(call("read_document", "{\"id\":\"0123456789ab\"}", true), 0).startsWith("Unknown id"));
		}
		finally {
			outside.delete();
		}
	}

	@Test
	public void httpTransport() throws Exception {
		int port = freePort();
		server.start(port);
		try {
			assertTrue(server.isRunning());
			String url = "http://127.0.0.1:" + port + "/mcp";
			String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
			java.net.http.HttpResponse<String> post = send(url, "POST", null, body);
			assertEquals(200, post.statusCode());
			assertTrue(post.headers().firstValue("Content-Type").get().startsWith("application/json"));
			assertTrue(post.body().contains("read_document"));

			assertEquals(202, send(url, "POST", null, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}").statusCode());

			// Revision 2026-07-28: headers mirror the body; unknown methods give 404
			String meta = "{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
				+ "\"io.modelcontextprotocol/clientCapabilities\":{}}}";
			java.net.http.HttpResponse<String> discover = send(url, "POST", null,
				"{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\",\"params\":" + meta + "}",
				"MCP-Protocol-Version", "2026-07-28", "Mcp-Method", "server/discover");
			assertEquals(200, discover.statusCode());
			assertTrue(discover.body().contains("\"resultType\":\"complete\""));
			assertEquals(404, send(url, "POST", null,
				"{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\",\"params\":" + meta + "}",
				"MCP-Protocol-Version", "2026-07-28", "Mcp-Method", "ping").statusCode());
			assertEquals(405, send(url, "GET", null, null).statusCode());
			assertEquals(404, send("http://127.0.0.1:" + port + "/other", "POST", null, body).statusCode());
			assertEquals("Web pages may not connect", 403,
				send(url, "POST", "https://evil.example.com", body).statusCode());
			assertEquals(200, send(url, "POST", "http://localhost:3000", body).statusCode());
		}
		finally {
			server.stop();
		}
		assertFalse(server.isRunning());
	}

	@Test
	public void originCheck() {
		assertTrue(McpServer.isAllowedOrigin(null));
		assertTrue(McpServer.isAllowedOrigin("http://localhost:6274"));
		assertTrue(McpServer.isAllowedOrigin("http://127.0.0.1"));
		assertFalse(McpServer.isAllowedOrigin("http://attacker.example"));
		assertFalse(McpServer.isAllowedOrigin("http://localhost.attacker.example"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void jsonRoundTrip() {
		String text = "{\"a\":[1,2.5,true,false,null,\"x\\\"\\n\\u00fc\"],\"b\":{}}";
		Map<String, Object> map = (Map<String, Object>) Json.parse(text);
		List<Object> a = (List<Object>) map.get("a");
		assertEquals(1L, a.get(0));
		assertEquals(2.5, a.get(1));
		assertEquals("x\"\nü", a.get(5));
		assertEquals("{\"a\":[1,2.5,true,false,null,\"x\\\"\\nü\"],\"b\":{}}", Json.write(map));
	}

	static int freePort() throws Exception {
		// Only on this computer: a socket on all interfaces makes the Windows firewall ask
		ServerSocket socket = new ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress());
		int port = socket.getLocalPort();
		socket.close();
		return port;
	}

	/** Sends a request; returns the status code and the body. HttpURLConnection would drop the Origin header. */
	private static java.net.http.HttpResponse<String> send(String url, String method, String origin, String body,
			String... headers) throws Exception {
		java.net.http.HttpRequest.Builder request = java.net.http.HttpRequest.newBuilder(new java.net.URI(url))
			.header("Accept", "application/json, text/event-stream")
			.method(method, body == null ? java.net.http.HttpRequest.BodyPublishers.noBody()
				: java.net.http.HttpRequest.BodyPublishers.ofString(body));
		if (origin != null)
			request.header("Origin", origin);
		if (body != null)
			request.header("Content-Type", "application/json");
		for (int i = 0; i < headers.length; i += 2)
			request.header(headers[i], headers[i + 1]);
		return java.net.http.HttpClient.newHttpClient().send(request.build(),
			java.net.http.HttpResponse.BodyHandlers.ofString());
	}

}
