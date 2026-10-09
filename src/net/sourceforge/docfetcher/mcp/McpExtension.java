/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.mcp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.sourceforge.docfetcher.enums.SystemConf;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Writes a Claude Desktop extension (.mcpb, a zip file with a manifest) that
 * connects Claude Desktop to the MCP server. Claude Desktop only starts local
 * programs and talks to them on stdio; the extension contains a small bridge
 * that passes each message to the server on localhost and the answer back.
 * Claude Desktop brings its own Node.js for it, so the user needs nothing else.
 */
public final class McpExtension {

	public static final String FILE_NAME = "docfetcher.mcpb";

	/** The bridge: stdio (one JSON message per line) to HTTP POST and back. */
	static final String BRIDGE = """
		// DocFetcher MCP bridge: passes the messages of Claude Desktop (stdio) to the
		// MCP server of the running DocFetcher (HTTP on localhost) and the answers back.
		const http = require('http');
		const readline = require('readline');
		const url = new URL(process.env.DOCFETCHER_MCP_URL || 'http://localhost:28835/mcp');
		const VERSION = 'io.modelcontextprotocol/protocolVersion';

		// Revision 2026-07-28 repeats the version, method and tool name of the body in headers.
		function headersOf(message, length) {
		  const headers = { 'Content-Type': 'application/json', 'Accept': 'application/json, text/event-stream',
		    'Content-Length': length };
		  const params = message && message.params;
		  const version = params && params._meta && params._meta[VERSION];
		  if (version) {
		    headers['MCP-Protocol-Version'] = version;
		    headers['Mcp-Method'] = message.method;
		    if (message.method === 'tools/call' && params.name)
		      headers['Mcp-Name'] = '=?base64?' + Buffer.from(String(params.name)).toString('base64') + '?=';
		  }
		  return headers;
		}

		function answer(message) {
		  process.stdout.write(JSON.stringify(message) + '\\n');
		}

		function fail(message, text) {
		  if (message && message.id !== undefined)
		    answer({ jsonrpc: '2.0', id: message.id, error: { code: -32603, message: text } });
		}

		readline.createInterface({ input: process.stdin }).on('line', line => {
		  if (!line.trim()) return;
		  let message;
		  try { message = JSON.parse(line); } catch (e) { message = null; }
		  const body = Buffer.from(line, 'utf8');
		  const request = http.request(url, { method: 'POST', headers: headersOf(message, body.length) }, response => {
		    const chunks = [];
		    response.on('data', chunk => chunks.push(chunk));
		    response.on('end', () => {
		      const text = Buffer.concat(chunks).toString('utf8').trim();
		      if (!text) return; // Notifications get no answer
		      try { answer(JSON.parse(text)); }
		      catch (e) { fail(message, 'DocFetcher answered with HTTP ' + response.statusCode + ': ' + text.slice(0, 200)); }
		    });
		  });
		  request.on('error', () => fail(message, 'DocFetcher is not reachable at ' + url + '. Start DocFetcher '
		    + 'and switch on the MCP server in its preferences (Advanced).'));
		  request.end(body);
		});
		""";

	private McpExtension() {
	}

	/**
	 * Writes the extension for the server on the given port; the icon is
	 * optional.
	 */
	public static void write(@NotNull File target, @NotNull String port, @Nullable File icon)
			throws IOException {
		ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(target));
		try {
			put(zip, "manifest.json", Json.write(manifest(port, icon != null && icon.isFile())));
			put(zip, "server/index.js", BRIDGE);
			if (icon != null && icon.isFile()) {
				zip.putNextEntry(new ZipEntry("icon.png"));
				zip.write(Files.readAllBytes(icon.toPath()));
				zip.closeEntry();
			}
		}
		finally {
			zip.close();
		}
	}

	private static void put(@NotNull ZipOutputStream zip, @NotNull String name, @NotNull String text)
			throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(text.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	@NotNull
	static Map<String, Object> manifest(@NotNull String port, boolean withIcon) {
		Map<String, Object> env = new LinkedHashMap<String, Object>();
		env.put("DOCFETCHER_MCP_URL", "http://localhost:" + port + McpServer.PATH);
		Map<String, Object> config = new LinkedHashMap<String, Object>();
		config.put("command", "node");
		config.put("args", Arrays.asList("${__dirname}/server/index.js"));
		config.put("env", env);
		Map<String, Object> server = new LinkedHashMap<String, Object>();
		server.put("type", "node");
		server.put("entry_point", "server/index.js");
		server.put("mcp_config", config);

		List<Object> tools = new ArrayList<Object>();
		for (Object definition : McpTools.definitions()) {
			Map<?, ?> tool = (Map<?, ?>) definition;
			Map<String, Object> entry = new LinkedHashMap<String, Object>();
			entry.put("name", tool.get("name"));
			entry.put("description", firstSentence((String) tool.get("description")));
			tools.add(entry);
		}
		Map<String, Object> runtimes = new LinkedHashMap<String, Object>();
		runtimes.put("node", ">=16.0.0");
		Map<String, Object> compatibility = new LinkedHashMap<String, Object>();
		compatibility.put("platforms", Arrays.asList("win32", "darwin", "linux"));
		compatibility.put("runtimes", runtimes);
		Map<String, Object> author = new LinkedHashMap<String, Object>();
		author.put("name", "DocFetcher contributors");

		Map<String, Object> manifest = new LinkedHashMap<String, Object>();
		manifest.put("manifest_version", "0.3");
		manifest.put("name", "docfetcher");
		manifest.put("display_name", "DocFetcher");
		manifest.put("version", semanticVersion(SystemConf.Str.ProgramVersion.get()));
		manifest.put("description", "Search and read the documents indexed by DocFetcher on this computer.");
		manifest.put("long_description", "Connects Claude to the MCP server of DocFetcher on "
			+ "http://localhost:" + port + McpServer.PATH + ". DocFetcher must be running with the "
			+ "MCP server switched on (preferences, Advanced).");
		manifest.put("author", author);
		manifest.put("homepage", "https://docfetcher.sourceforge.io");
		if (withIcon)
			manifest.put("icon", "icon.png");
		manifest.put("server", server);
		manifest.put("tools", tools);
		manifest.put("compatibility", compatibility);
		manifest.put("license", "EPL-1.0");
		return manifest;
	}

	/** "2.10" gives "2.10.0"; anything else (a development build) "0.0.0". */
	@NotNull
	static String semanticVersion(@NotNull String version) {
		if (!version.matches("\\d+(\\.\\d+){0,2}"))
			return "0.0.0";
		int parts = version.split("\\.").length;
		return parts == 3 ? version : parts == 2 ? version + ".0" : version + ".0.0";
	}

	@NotNull
	private static String firstSentence(@NotNull String text) {
		int end = text.indexOf(". ");
		return end < 0 ? text : text.substring(0, end + 1);
	}

}
