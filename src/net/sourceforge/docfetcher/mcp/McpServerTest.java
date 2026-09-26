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
import org.junit.BeforeClass;
import org.junit.Test;

import net.sourceforge.docfetcher.model.search.SearchTestSupport;

/**
 * Tests the MCP server: the protocol (initialize, tools/list, tools/call,
 * errors), the tools with questions like 'which letters about tax arrived
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

	@SuppressWarnings("unchecked")
	private static String path(Map<String, Object> searchResult, int index) {
		List<Object> results = (List<Object>) searchResult.get("results");
		return (String) ((Map<String, Object>) results.get(index)).get("path");
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
		assertEquals(McpServer.LATEST_PROTOCOL, other.get("protocolVersion"));

		assertNull("Notifications get no answer", server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
		assertEquals(0, result("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}").size());

		List<Object> tools = (List<Object>) result("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/list\"}").get("tools");
		assertEquals(3, tools.size());
		assertEquals("search", ((Map<String, Object>) tools.get(0)).get("name"));
		assertEquals("read_document", ((Map<String, Object>) tools.get(1)).get("name"));
		assertEquals("list_folder", ((Map<String, Object>) tools.get(2)).get("name"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void protocolErrors() {
		assertEquals(-32700L, ((Map<String, Object>) rpc("{nope").get("error")).get("code"));
		assertEquals(-32601L, ((Map<String, Object>) rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/list\"}").get("error")).get("code"));
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
		assertTrue(path(found, 0), path(found, 0).endsWith("finanzamt-neu.txt"));
		assertTrue(path(found, 1).endsWith("finanzamt-alt.txt"));
		assertTrue(Json.write(found), Json.write(found).contains("snippet"));

		// Only the last month
		String lastMonth = new java.text.SimpleDateFormat("yyyy-MM-dd").format(
			new java.util.Date(System.currentTimeMillis() - 30 * DAY));
		Map<String, Object> recent = json(call("search",
			"{\"query\":\"Renate *steuer* Januar\",\"modified_after\":\"" + lastMonth + "\"}", false), 0);
		assertEquals(1L, recent.get("total_found"));
		assertTrue(path(recent, 0).endsWith("finanzamt-neu.txt"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void rentOfTheTenantInRosenweg() {
		// 'What rent does my client in Rosenweg have?'
		Map<String, Object> found = json(call("search", "{\"query\":\"Rosenweg *miete*\"}", false), 0);
		assertEquals(1L, found.get("total_found"));
		String path = path(found, 0);
		List<Object> content = call("read_document", "{\"path\":" + Json.write(path) + "}", false);
		Map<String, Object> meta = json(content, 0);
		assertEquals(path, meta.get("path"));
		assertFalse("Short document, no more parts", meta.containsKey("next_offset"));
		assertTrue(text(content, 1), text(content, 1).contains("Kaltmiete beträgt 850,00 EUR"));
	}

	@Test
	public void longDocumentsComeInParts() {
		String path = new File(support.root, "Mieter/lang.txt").getPath();
		List<Object> first = call("read_document", "{\"path\":" + Json.write(path) + ",\"max_chars\":1000}", false);
		Map<String, Object> meta = json(first, 0);
		assertEquals(1000L, meta.get("returned_chars"));
		assertEquals(1000L, meta.get("next_offset"));
		assertEquals(1000, text(first, 1).length());
		List<Object> second = call("read_document", "{\"path\":" + Json.write(path) + ",\"offset\":1000}", false);
		assertEquals(1000L, json(second, 0).get("offset"));
		// The default part size
		List<Object> part = call("read_document", "{\"path\":" + Json.write(path) + "}", false);
		assertEquals((long) McpTools.DEFAULT_MAX_CHARS, json(part, 0).get("returned_chars"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void listFolders() {
		Map<String, Object> roots = json(call("list_folder", "{}", false), 0);
		assertTrue(Json.write(roots), Json.write(roots).contains("\"name\":\"" + support.root.getName() + "\""));
		String inbox = new File(support.root, "Posteingang").getPath();
		Map<String, Object> listing = json(call("list_folder", "{\"path\":" + Json.write(inbox) + "}", false), 0);
		List<Object> entries = (List<Object>) listing.get("entries");
		assertEquals(3, entries.size());
		assertTrue("Newest first", ((String) ((Map<String, Object>) entries.get(0)).get("path")).endsWith("stadtwerke.txt"));
	}

	@Test
	public void onlyIndexedFoldersAreAccessible() throws Exception {
		File outside = File.createTempFile("docfetcher-secret", ".txt");
		try {
			assertTrue(text(call("read_document", "{\"path\":" + Json.write(outside.getPath()) + "}", true), 0)
				.startsWith("Access denied"));
			assertTrue(text(call("list_folder", "{\"path\":" + Json.write(outside.getParent()) + "}", true), 0)
				.startsWith("Access denied"));
			String sneaky = new File(support.root, "../" + outside.getName()).getPath();
			assertTrue(text(call("read_document", "{\"path\":" + Json.write(sneaky) + "}", true), 0)
				.startsWith("Access denied"));
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

	private static int freePort() throws Exception {
		// Only on this computer: a socket on all interfaces makes the Windows firewall ask
		ServerSocket socket = new ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress());
		int port = socket.getLocalPort();
		socket.close();
		return port;
	}

	/** Sends a request; returns the status code and the body. HttpURLConnection would drop the Origin header. */
	private static java.net.http.HttpResponse<String> send(String url, String method, String origin, String body)
			throws Exception {
		java.net.http.HttpRequest.Builder request = java.net.http.HttpRequest.newBuilder(new java.net.URI(url))
			.header("Accept", "application/json, text/event-stream")
			.method(method, body == null ? java.net.http.HttpRequest.BodyPublishers.noBody()
				: java.net.http.HttpRequest.BodyPublishers.ofString(body));
		if (origin != null)
			request.header("Origin", origin);
		if (body != null)
			request.header("Content-Type", "application/json");
		return java.net.http.HttpClient.newHttpClient().send(request.build(),
			java.net.http.HttpResponse.BodyHandlers.ofString());
	}

}
