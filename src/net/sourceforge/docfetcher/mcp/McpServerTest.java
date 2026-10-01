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
	public void tooManyResultsComeWithAnOverview() {
		// Four documents mention Bonn, only one is returned: the newest (the lease,
		// written just now), plus an overview
		Map<String, Object> found = json(call("search", "{\"query\":\"Bonn\",\"max_results\":1}", false), 0);
		assertEquals(4L, found.get("total_found"));
		assertEquals(1L, found.get("returned"));
		assertTrue("Newest first", path(found, 0).endsWith("mietvertrag-rosenweg.txt"));
		Map<String, Object> overview = (Map<String, Object>) found.get("overview");
		assertTrue(Json.write(overview), overview.containsKey("oldest") && overview.containsKey("newest"));
		assertTrue(Json.write(overview), overview.containsKey("by_month"));
		List<Object> folders = (List<Object>) overview.get("top_folders");
		assertTrue(Json.write(folders), ((String) ((Map<String, Object>) folders.get(0)).get("path")).endsWith("Posteingang"));
		assertEquals(3L, ((Map<String, Object>) folders.get(0)).get("count"));
		assertFalse("Same path format as the results", ((String) ((Map<String, Object>) folders.get(0)).get("path")).contains("\\"));
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
		String path = path(found, 0);
		List<Object> content = call("read_document", "{\"path\":" + Json.write(path) + "}", false);
		Map<String, Object> meta = json(content, 0);
		assertEquals(path, meta.get("path"));
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
			String visual = text(call("read_document", "{\"path\":" + Json.write(file.getPath()) + "}", false), 1);
			assertTrue(visual, visual.contains("Grundmiete 620,00 Euro"));
			assertTrue(visual, visual.contains("Gesamtbetrag 790,00 Euro"));
			String stream = text(call("read_document", "{\"path\":" + Json.write(file.getPath()) + ",\"order\":\"stream\"}", false), 1);
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
				if (((String) map.get("path")).endsWith("scan-zebra.pdf")) {
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
			String path = path(search, 0);
			assertFalse("Normalized: " + path, path.contains(".."));

			String read = detourServer.handle("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":"
				+ "{\"name\":\"read_document\",\"arguments\":{\"path\":" + Json.write(path) + "}}}");
			assertTrue(read, read.contains("\"isError\":false"));
			assertTrue(read, read.contains("Mahnung wegen Mietrückstand"));

			String list = detourServer.handle("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":"
				+ "{\"name\":\"list_folder\",\"arguments\":{\"path\":" + Json.write(new File(detour.root, "Mieter").getPath()) + "}}}");
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
