/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.mcp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import net.sourceforge.docfetcher.enums.SystemConf;
import net.sourceforge.docfetcher.mcp.McpTools.ToolException;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * An MCP server (Model Context Protocol, 'Streamable HTTP' transport) that
 * gives AI assistants like Claude read-only access to the indexes: clients
 * POST JSON-RPC messages to {@code http://localhost:<port>/mcp} and get the
 * answer as JSON. The server only listens on the loopback interface, so only
 * programs on this computer can connect, and it rejects requests from web
 * pages (Origin header of another host). See {@link McpTools} for the tools;
 * the only one that writes, {@link McpInbox}, must be allowed by the user.
 * <p>
 * It serves both protocol eras: the stateless revision 2026-07-28 (version,
 * client capabilities and HTTP headers on every request, {@code
 * server/discover}), and the earlier revisions 2025-03-26 to 2025-11-25 that
 * start with {@code initialize}. A request is modern if it carries the
 * version in its {@code _meta} or a modern {@code MCP-Protocol-Version}
 * header.
 */
public final class McpServer {

	static final String PATH = "/mcp";
	/** The stateless revision: no initialize, the version on every request. */
	static final String MODERN_PROTOCOL = "2026-07-28";
	/** The newest revision with initialize; the answer to unknown versions there. */
	static final String LATEST_LEGACY_PROTOCOL = "2025-11-25";
	private static final List<String> LEGACY_PROTOCOLS = Arrays.asList(
		LATEST_LEGACY_PROTOCOL, "2025-06-18", "2025-03-26", "2024-11-05");
	/** All supported versions, newest first. */
	static final List<String> PROTOCOLS = Arrays.asList(
		MODERN_PROTOCOL, LATEST_LEGACY_PROTOCOL, "2025-06-18", "2025-03-26", "2024-11-05");

	private static final String META_VERSION = "io.modelcontextprotocol/protocolVersion";
	private static final String META_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities";
	private static final String META_SERVER_INFO = "io.modelcontextprotocol/serverInfo";
	private static final int HEADER_MISMATCH = -32020;
	private static final int UNSUPPORTED_VERSION = -32022;
	/**
	 * How long modern clients may cache the tool list and discovery: short,
	 * as add_to_inbox comes and goes with the preferences.
	 */
	private static final long CACHE_TTL_MS = 60 * 1000;
	/** Enough for a file of McpInbox.MAX_MB as base64 (a third larger). */
	private static final int MAX_REQUEST_BYTES = (McpInbox.MAX_MB * 4 / 3 + 2) * 1024 * 1024;
	/** The image types AI assistants can look at; other images come as files. */
	private static final List<String> IMAGE_TYPES = Arrays.asList("image/png", "image/jpeg", "image/gif", "image/webp");

	private static final String INSTRUCTIONS =
		"DocFetcher indexes the user's documents (letters, invoices, contracts, scanned mail, "
		+ "office files, emails - often in German). To answer questions about them, use "
		+ "'search' with a few key words (try German words too, e.g. '*steuer*', '*miete*', "
		+ "'Rechnung' - the stars also find compound words like 'Einkommensteuer'), then "
		+ "'read_document' on the best results to read their content. "
		+ "Use sort=newest for the latest documents and modified_after/modified_before for "
		+ "periods. When the user asks for a document itself, answer with its path; "
		+ "'get_original_document' returns the file itself when its original form is needed, "
		+ "e.g. as a template for a new document.";

	private final McpTools tools;
	@Nullable private HttpServer server;
	@Nullable private ExecutorService executor;
	private int port = -1;

	public McpServer(@NotNull IndexRegistry registry) {
		tools = new McpTools(registry);
	}

	/**
	 * Starts the server on the given port of the loopback interface, or
	 * restarts it if it runs on another port. Throws an IOException if the
	 * port is in use.
	 */
	public synchronized void start(int port) throws IOException {
		if (server != null && this.port == port)
			return;
		stop();
		HttpServer newServer = HttpServer.create(
			new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
		newServer.createContext("/", new HttpHandler() {
			public void handle(HttpExchange exchange) throws IOException {
				try {
					handleExchange(exchange);
				}
				catch (Throwable e) {
					Util.printErr(e);
					send(exchange, 500, null);
				}
				finally {
					exchange.close();
				}
			}
		});
		executor = Executors.newFixedThreadPool(4, new ThreadFactory() {
			public Thread newThread(Runnable r) {
				Thread thread = new Thread(r, "MCP server");
				thread.setDaemon(true);
				return thread;
			}
		});
		newServer.setExecutor(executor);
		newServer.start();
		server = newServer;
		this.port = port;
	}

	/** Stops the server, if it is running. */
	public synchronized void stop() {
		if (server != null) {
			server.stop(0);
			server = null;
		}
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
		port = -1;
	}

	public synchronized boolean isRunning() {
		return server != null;
	}

	/** The port the server listens on, or -1 if it isn't running. */
	public synchronized int getPort() {
		return port;
	}

	// ---- HTTP

	private void handleExchange(@NotNull HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		if (!PATH.equals(path) && !(PATH + "/").equals(path)) {
			send(exchange, 404, null);
			return;
		}
		if (!isAllowedOrigin(exchange.getRequestHeaders().getFirst("Origin"))) {
			send(exchange, 403, null); // A web page trying to reach the server
			return;
		}
		if (!"POST".equals(exchange.getRequestMethod())) {
			// No server-initiated messages (GET) and no sessions (DELETE)
			exchange.getResponseHeaders().set("Allow", "POST");
			send(exchange, 405, null);
			return;
		}
		String body = readBody(exchange.getRequestBody());
		if (body == null) {
			send(exchange, 413, null);
			return;
		}
		Reply reply = handle(body, exchange.getRequestHeaders());
		if (reply.body != null)
			exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		send(exchange, reply.status, reply.body);
	}

	/** Requests from web pages carry an Origin header; only local ones are accepted. */
	static boolean isAllowedOrigin(@Nullable String origin) {
		if (origin == null || origin.isEmpty() || origin.equals("null"))
			return true;
		try {
			String host = new URI(origin).getHost();
			return host != null && (host.equalsIgnoreCase("localhost")
				|| host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1"));
		}
		catch (Exception e) {
			return false;
		}
	}

	@Nullable
	private static String readBody(@NotNull InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = in.read(buffer)) != -1) {
			out.write(buffer, 0, read);
			if (out.size() > MAX_REQUEST_BYTES)
				return null;
		}
		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}

	private static void send(@NotNull HttpExchange exchange, int status, @Nullable String body)
			throws IOException {
		if (body == null) {
			exchange.sendResponseHeaders(status, -1);
			return;
		}
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length);
		OutputStream out = exchange.getResponseBody();
		out.write(bytes);
		out.close();
	}

	// ---- JSON-RPC

	/** The answer to an HTTP request: status code and JSON body (null: none). */
	static final class Reply {
		final int status;
		@Nullable final String body;

		Reply(int status, @Nullable Map<String, Object> message) {
			this.status = status;
			this.body = message == null ? null : Json.write(message);
		}

		Reply(int status, @Nullable String body) {
			this.status = status;
			this.body = body;
		}
	}

	/** An error that ends a request: HTTP status plus JSON-RPC error. */
	private static final class RpcError extends Exception {
		final int status;
		final int code;
		@Nullable final Object data;

		RpcError(int status, int code, @NotNull String message, @Nullable Object data) {
			super(message);
			this.status = status;
			this.code = code;
			this.data = data;
		}
	}

	/**
	 * Handles a message without HTTP headers (an earlier-era client) and
	 * returns the JSON answer, or null if there is nothing to answer.
	 */
	@Nullable
	String handle(@NotNull String body) {
		return handle(body, new Headers()).body;
	}

	/** Handles one JSON-RPC message (or a batch of the earlier era) with its HTTP headers. */
	@NotNull
	@SuppressWarnings("unchecked")
	Reply handle(@NotNull String body, @NotNull Headers headers) {
		Object message;
		try {
			message = Json.parse(body);
		}
		catch (IllegalArgumentException e) {
			return new Reply(400, error(null, -32700, "Parse error: " + e.getMessage(), null));
		}
		String headerVersion = headers.getFirst("MCP-Protocol-Version");
		boolean modernHeader = headerVersion != null && headerVersion.compareTo(MODERN_PROTOCOL) >= 0;
		if (message instanceof Map && (modernHeader || isModern((Map<String, Object>) message)))
			return handleModern((Map<String, Object>) message, headers);
		if (modernHeader)
			return new Reply(400, error(null, -32600, "Batches are not allowed in protocol revision " + MODERN_PROTOCOL, null));
		if (headerVersion != null && !PROTOCOLS.contains(headerVersion))
			return new Reply(400, unsupportedVersion(null, headerVersion));

		// Earlier era: initialize first, batches allowed (2025-03-26)
		if (message instanceof List) {
			List<Object> responses = new ArrayList<Object>();
			for (Object item : (List<?>) message) {
				Map<String, Object> response = handleLegacy(item);
				if (response != null)
					responses.add(response);
			}
			return responses.isEmpty() ? new Reply(202, (String) null) : new Reply(200, Json.write(responses));
		}
		Map<String, Object> response = handleLegacy(message);
		return response == null ? new Reply(202, (String) null) : new Reply(200, response);
	}

	/** A modern request carries the version in its _meta; server/discover is modern only. */
	private static boolean isModern(@NotNull Map<String, Object> message) {
		return meta(message).containsKey(META_VERSION) || "server/discover".equals(message.get("method"));
	}

	@NotNull
	@SuppressWarnings("unchecked")
	private static Map<String, Object> params(@NotNull Map<String, Object> message) {
		return message.get("params") instanceof Map
			? (Map<String, Object>) message.get("params")
			: Collections.<String, Object> emptyMap();
	}

	@NotNull
	@SuppressWarnings("unchecked")
	private static Map<String, Object> meta(@NotNull Map<String, Object> message) {
		Object meta = params(message).get("_meta");
		return meta instanceof Map ? (Map<String, Object>) meta : Collections.<String, Object> emptyMap();
	}

	/** Revision 2026-07-28: every request stands on its own. */
	@NotNull
	private Reply handleModern(@NotNull Map<String, Object> message, @NotNull Headers headers) {
		Object id = message.get("id");
		Object methodValue = message.get("method");
		if (!(methodValue instanceof String))
			return new Reply(400, error(id, -32600, "Invalid request", null));
		if (!message.containsKey("id"))
			return new Reply(202, (String) null); // Notifications need no answer
		String method = (String) methodValue;
		Map<String, Object> params = params(message);
		try {
			Map<String, Object> meta = meta(message);
			Object version = meta.get(META_VERSION);
			if (!(version instanceof String) || !(meta.get(META_CAPABILITIES) instanceof Map))
				throw new RpcError(400, -32602, "Invalid params: _meta needs " + META_VERSION
					+ " and " + META_CAPABILITIES, null);
			if (!version.equals(MODERN_PROTOCOL))
				return new Reply(400, unsupportedVersion(id, (String) version));
			checkHeader(headers, "MCP-Protocol-Version", (String) version);
			checkHeader(headers, "Mcp-Method", method);
			if (method.equals("tools/call") || method.equals("prompts/get") || method.equals("resources/read")) {
				Object name = method.equals("resources/read") ? params.get("uri") : params.get("name");
				checkHeader(headers, "Mcp-Name", name instanceof String ? (String) name : "");
			}

			Map<String, Object> result;
			if (method.equals("server/discover")) {
				result = new LinkedHashMap<String, Object>();
				result.put("supportedVersions", PROTOCOLS);
				result.put("capabilities", capabilities());
				result.put("instructions", INSTRUCTIONS);
			}
			else {
				result = call(method, params);
				if (result == null)
					throw new RpcError(404, -32601, "Method not found: " + method, null);
			}
			if (!method.equals("tools/call")) {
				// Discovery and lists never change while the server runs
				result.put("ttlMs", CACHE_TTL_MS);
				result.put("cacheScope", "private");
			}
			Map<String, Object> resultMeta = new LinkedHashMap<String, Object>();
			resultMeta.put(META_SERVER_INFO, serverInfo());
			Map<String, Object> complete = new LinkedHashMap<String, Object>();
			complete.put("resultType", "complete");
			complete.putAll(result);
			complete.put("_meta", resultMeta);
			return new Reply(200, result(id, complete));
		}
		catch (RpcError e) {
			return new Reply(e.status, error(id, e.code, e.getMessage(), e.data));
		}
	}

	/**
	 * Header and body must agree (2026-07-28), so that proxies routing on the
	 * headers see what the server executes. Names may be Base64-encoded.
	 */
	private static void checkHeader(@NotNull Headers headers, @NotNull String name, @NotNull String bodyValue)
			throws RpcError {
		String value = headers.getFirst(name);
		if (value != null && value.startsWith("=?base64?") && value.endsWith("?=") && !name.equals("Mcp-Method")) {
			try {
				value = new String(Base64.getDecoder().decode(
					value.substring("=?base64?".length(), value.length() - 2)), StandardCharsets.UTF_8);
			}
			catch (IllegalArgumentException e) {
				value = null;
			}
		}
		if (value == null)
			throw new RpcError(400, HEADER_MISMATCH, "Header mismatch: " + name + " header missing", null);
		if (!value.equals(bodyValue))
			throw new RpcError(400, HEADER_MISMATCH, "Header mismatch: " + name + " header value '"
				+ value + "' does not match body value '" + bodyValue + "'", null);
	}

	@NotNull
	private static Map<String, Object> unsupportedVersion(@Nullable Object id, @NotNull String requested) {
		Map<String, Object> data = new LinkedHashMap<String, Object>();
		data.put("supported", PROTOCOLS);
		data.put("requested", requested);
		return error(id, UNSUPPORTED_VERSION, "Unsupported protocol version", data);
	}

	/** Revisions 2025-03-26 to 2025-11-25 (and 2024-11-05 messages): initialize first. */
	@Nullable
	@SuppressWarnings("unchecked")
	private Map<String, Object> handleLegacy(@Nullable Object message) {
		if (!(message instanceof Map))
			return error(null, -32600, "Invalid request", null);
		Map<String, Object> request = (Map<String, Object>) message;
		Object id = request.get("id");
		Object methodValue = request.get("method");
		if (!(methodValue instanceof String))
			return null; // A response from the client, or garbage without an id
		if (!request.containsKey("id"))
			return null; // notifications/initialized, notifications/cancelled, ...
		String method = (String) methodValue;
		Map<String, Object> params = params(request);
		if (method.equals("initialize"))
			return result(id, initialize(params));
		if (method.equals("ping"))
			return result(id, new LinkedHashMap<String, Object>());
		try {
			Map<String, Object> result = call(method, params);
			return result == null ? error(id, -32601, "Method not found: " + method, null) : result(id, result);
		}
		catch (RpcError e) {
			return error(id, e.code, e.getMessage(), e.data);
		}
	}

	/** The methods of both eras; null for an unknown method. */
	@Nullable
	private Map<String, Object> call(@NotNull String method, @NotNull Map<String, Object> params)
			throws RpcError {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		if (method.equals("tools/list"))
			result.put("tools", McpTools.definitions());
		else if (method.equals("tools/call"))
			return callTool(params);
		// No resources and prompts; some clients ask anyway, an empty list saves them an error
		else if (method.equals("resources/list"))
			result.put("resources", Collections.emptyList());
		else if (method.equals("resources/templates/list"))
			result.put("resourceTemplates", Collections.emptyList());
		else if (method.equals("prompts/list"))
			result.put("prompts", Collections.emptyList());
		else
			return null;
		return result;
	}

	@NotNull
	private Map<String, Object> initialize(@NotNull Map<String, Object> params) {
		Object requested = params.get("protocolVersion");
		String version = requested instanceof String && LEGACY_PROTOCOLS.contains(requested)
			? (String) requested : LATEST_LEGACY_PROTOCOL;
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("protocolVersion", version);
		result.put("capabilities", capabilities());
		result.put("serverInfo", serverInfo());
		result.put("instructions", INSTRUCTIONS);
		return result;
	}

	@NotNull
	private static Map<String, Object> capabilities() {
		Map<String, Object> toolsCapability = new LinkedHashMap<String, Object>();
		toolsCapability.put("listChanged", Boolean.FALSE);
		Map<String, Object> capabilities = new LinkedHashMap<String, Object>();
		capabilities.put("tools", toolsCapability);
		return capabilities;
	}

	@NotNull
	private static Map<String, Object> serverInfo() {
		Map<String, Object> serverInfo = new LinkedHashMap<String, Object>();
		serverInfo.put("name", "DocFetcher");
		serverInfo.put("version", SystemConf.Str.ProgramVersion.get());
		return serverInfo;
	}

	@NotNull
	@SuppressWarnings("unchecked")
	private Map<String, Object> callTool(@NotNull Map<String, Object> params) throws RpcError {
		Object name = params.get("name");
		if (!(name instanceof String))
			throw new RpcError(400, -32602, "Missing tool name", null);
		Map<String, Object> args = params.get("arguments") instanceof Map
			? (Map<String, Object>) params.get("arguments")
			: Collections.<String, Object> emptyMap();
		List<Object> content = new ArrayList<Object>();
		boolean isError = false;
		try {
			Object value = tools.call((String) name, args);
			if (value instanceof McpTools.FileContent) {
				// get_original_document: the description, then the file
				McpTools.FileContent file = (McpTools.FileContent) value;
				content.add(text(Json.write(file.meta)));
				content.add(fileContent(file));
			}
			else if (value instanceof List) {
				// read_document: metadata, then the text itself
				for (Object part : (List<?>) value)
					content.add(text(part instanceof String ? (String) part : Json.write(part)));
			}
			else {
				content.add(text(Json.write(value)));
			}
		}
		catch (ToolException e) {
			content.add(text(e.getMessage()));
			isError = true;
		}
		catch (Throwable e) {
			Util.printErr(e);
			content.add(text("Internal error: " + e));
			isError = true;
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("content", content);
		result.put("isError", isError);
		return result;
	}

	@NotNull
	private static Map<String, Object> text(@NotNull String text) {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("type", "text");
		map.put("text", text);
		return map;
	}

	/** Images the assistant can look at as an image, all other files as an embedded resource. */
	@NotNull
	static Map<String, Object> fileContent(@NotNull McpTools.FileContent file) {
		String data = Base64.getEncoder().encodeToString(file.data);
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		if (IMAGE_TYPES.contains(file.mimeType())) {
			map.put("type", "image");
			map.put("data", data);
			map.put("mimeType", file.mimeType());
			return map;
		}
		Map<String, Object> resource = new LinkedHashMap<String, Object>();
		resource.put("uri", file.uri());
		resource.put("mimeType", file.mimeType());
		resource.put("blob", data);
		map.put("type", "resource");
		map.put("resource", resource);
		return map;
	}

	@NotNull
	private static Map<String, Object> result(@Nullable Object id, @NotNull Object result) {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("jsonrpc", "2.0");
		map.put("id", id);
		map.put("result", result);
		return map;
	}

	@NotNull
	private static Map<String, Object> error(@Nullable Object id, int code, @NotNull String message,
			@Nullable Object data) {
		Map<String, Object> error = new LinkedHashMap<String, Object>();
		error.put("code", code);
		error.put("message", message);
		if (data != null)
			error.put("data", data);
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("jsonrpc", "2.0");
		map.put("id", id);
		map.put("error", error);
		return map;
	}

}
