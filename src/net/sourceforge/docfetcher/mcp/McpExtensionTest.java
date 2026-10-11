/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.mcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sourceforge.docfetcher.model.search.SearchTestSupport;

/**
 * Tests the Claude Desktop extension: the contents of the .mcpb file, and the
 * bridge in it, run with Node.js (skipped without Node.js) against a running
 * server, as Claude Desktop would run it.
 */
public final class McpExtensionTest {

	private static SearchTestSupport support;
	private static McpServer server;
	private static File dir;

	@BeforeClass
	public static void setUpClass() throws Exception {
		support = new SearchTestSupport("Mieter/mietvertrag-rosenweg.txt",
			"Mietvertrag. Mieter: Herr Klaus Meier, Rosenweg 12. Die Kaltmiete beträgt 850,00 EUR.");
		server = new McpServer(support.registry);
		dir = Files.createTempDirectory("mcpb").toFile();
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		server.stop();
		support.dispose();
		for (File file : new File(dir, "server").listFiles())
			file.delete();
		new File(dir, "server").delete();
		for (File file : dir.listFiles())
			file.delete();
		dir.delete();
	}

	@Test
	public void semanticVersion() {
		assertEquals("2.10.0", McpExtension.semanticVersion("2.10"));
		assertEquals("3.0.0", McpExtension.semanticVersion("3"));
		assertEquals("2.10.1", McpExtension.semanticVersion("2.10.1"));
		assertEquals("0.0.0", McpExtension.semanticVersion("Unspecified"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void contents() throws Exception {
		File icon = new File(dir, "icon-source.png");
		Files.write(icon.toPath(), new byte[] {(byte) 0x89, 'P', 'N', 'G'});
		File mcpb = new File(dir, McpExtension.FILE_NAME);
		McpExtension.write(mcpb, "12345", icon);

		ZipFile zip = new ZipFile(mcpb);
		try {
			Map<String, Object> manifest = (Map<String, Object>) Json.parse(read(zip, "manifest.json"));
			assertEquals("0.3", manifest.get("manifest_version"));
			assertEquals("docfetcher", manifest.get("name"));
			assertEquals("icon.png", manifest.get("icon"));
			assertNotNull(zip.getEntry("icon.png"));
			Map<String, Object> server = (Map<String, Object>) manifest.get("server");
			assertEquals("node", server.get("type"));
			Map<String, Object> config = (Map<String, Object>) server.get("mcp_config");
			assertEquals("[${__dirname}/server/index.js]", config.get("args").toString());
			assertEquals("http://localhost:12345/mcp",
				((Map<String, Object>) config.get("env")).get("DOCFETCHER_MCP_URL"));
			String tools = Json.write(manifest.get("tools"));
			assertTrue(tools, tools.contains("\"name\":\"search\"") && tools.contains("\"name\":\"read_document\""));
			assertEquals(McpExtension.BRIDGE, read(zip, server.get("entry_point").toString()));
		}
		finally {
			zip.close();
		}

		// Without an icon file, no icon
		McpExtension.write(mcpb, "12345", new File(dir, "missing.png"));
		zip = new ZipFile(mcpb);
		try {
			assertEquals(null, ((Map<String, Object>) Json.parse(read(zip, "manifest.json"))).get("icon"));
		}
		finally {
			zip.close();
		}
	}

	private static String read(ZipFile zip, String name) throws Exception {
		ZipEntry entry = zip.getEntry(name);
		assertNotNull(name, entry);
		return new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
	}

	/** The bridge talks to the server like Claude Desktop: both protocol eras, and a stopped server. */
	@Test
	public void bridge() throws Exception {
		String node = findNode();
		Assume.assumeTrue(node != null); // Without Node.js: skipped
		File script = new File(dir, "server/index.js");
		script.getParentFile().mkdirs();
		Files.write(script.toPath(), McpExtension.BRIDGE.getBytes(StandardCharsets.UTF_8));

		int port = McpServerTest.freePort();
		server.start(port);
		try {
			String meta = "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
				+ "\"io.modelcontextprotocol/clientCapabilities\":{}}";
			List<String> lines = runBridge(node, script, port,
				"{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}",
				"{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
				"{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"search\",\"arguments\":{\"query\":\"Rosenweg\"}}}",
				"{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"search\",\"arguments\":{\"query\":\"Kaltmiete\"}," + meta + "}}");
			String all = String.join("\n", lines);
			assertEquals(all, 3, lines.size()); // No answer to the notification
			assertTrue(all, all.contains("\"protocolVersion\":\"2025-06-18\""));
			assertTrue(all, find(lines, 2).contains("mietvertrag-rosenweg.txt"));
			// Revision 2026-07-28: the headers made by the bridge match the body
			assertTrue(all, find(lines, 3).contains("\"resultType\":\"complete\""));
			assertTrue(all, find(lines, 3).contains("mietvertrag-rosenweg.txt"));

			// An original document beyond the 10 MiB of the clients: an error, and the connection stays open
			File large = new File(support.root, "Mieter/plan.pdf");
			Files.write(large.toPath(), new byte[9 * 1024 * 1024]);
			try {
				lines = runBridge(node, script, port,
					"{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"get_original_document\",\"arguments\":{\"id\":"
						+ Json.write(McpServerTest.id(server, large)) + "}}}",
					"{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/list\"}",
					"{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{\"name\":\"get_original_document\",\"arguments\":{\"id\":"
						+ Json.write(McpServerTest.id(server, large)) + "}," + meta + "}}");
			}
			finally {
				large.delete();
			}
			assertEquals(3, lines.size());
			String error = find(lines, 4);
			assertTrue(error, error.length() < 1000 && error.contains("\"isError\":true") && error.contains("too large for this connection"));
			assertTrue(error, !error.contains("resultType"));
			assertTrue(find(lines, 5).contains("get_original_document"));
			// Revision 2026-07-28 requires the result type
			String modernError = find(lines, 6);
			assertTrue(modernError, modernError.contains("too large") && modernError.contains("\"resultType\":\"complete\""));
		}
		finally {
			server.stop();
		}

		// DocFetcher not running: a clear error instead of no answer
		List<String> lines = runBridge(node, script, port,
			"{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/list\"}");
		assertEquals(1, lines.size());
		assertTrue(lines.get(0), lines.get(0).contains("\"id\":7") && lines.get(0).contains("Start DocFetcher"));
	}

	private static String find(List<String> lines, int id) {
		for (String line : lines)
			if (line.contains("\"id\":" + id + ","))
				return line;
		throw new AssertionError("No answer " + id + " in " + lines);
	}

	private static List<String> runBridge(String node, File script, int port, String... messages) throws Exception {
		ProcessBuilder builder = new ProcessBuilder(node, script.getPath());
		builder.environment().put("DOCFETCHER_MCP_URL", "http://localhost:" + port + "/mcp");
		builder.redirectErrorStream(true);
		Process process = builder.start();
		Writer in = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
		for (String message : messages)
			in.write(message + "\n");
		in.close(); // End of input: the bridge exits after the last answer
		List<String> lines = new ArrayList<String>();
		BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
		for (String line; (line = out.readLine()) != null;)
			lines.add(line);
		process.waitFor();
		return lines;
	}

	private static String findNode() {
		List<String> candidates = new ArrayList<String>();
		candidates.add("node");
		candidates.add("C:\\Program Files\\nodejs\\node.exe");
		for (String candidate : candidates) {
			try {
				Process process = new ProcessBuilder(candidate, "--version").redirectErrorStream(true).start();
				process.getInputStream().readAllBytes();
				if (process.waitFor() == 0)
					return candidate;
			}
			catch (Exception e) {
				// Next one
			}
		}
		return null;
	}

}
