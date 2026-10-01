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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

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
 * pages (Origin header of another host). See {@link McpTools} for the tools.
 */
public final class McpServer {

	static final String PATH = "/mcp";
	static final String LATEST_PROTOCOL = "2025-06-18";
	private static final List<String> PROTOCOLS = Arrays.asList(
		"2025-11-25", LATEST_PROTOCOL, "2025-03-26", "2024-11-05");
	private static final int MAX_REQUEST_BYTES = 1024 * 1024;

	private static final String INSTRUCTIONS =
		"DocFetcher indexes the user's documents (letters, invoices, contracts, scanned mail, "
		+ "office files, emails - often in German). To answer questions about them, use "
		+ "'search' with a few key words (try German words too, e.g. '*steuer*', '*miete*', "
		+ "'Rechnung' - the stars also find compound words like 'Einkommensteuer'), then "
		+ "'read_document' on the best results to read their content. "
		+ "Use sort=newest for the latest documents and modified_after/modified_before for "
		+ "periods. When the user asks for a document itself, answer with its path.";

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
		String response = handle(body);
		if (response == null) {
			send(exchange, 202, null); // Notifications and responses need no answer
			return;
		}
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		send(exchange, 200, response);
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

	/**
	 * Handles one JSON-RPC message (or a batch) and returns the JSON answer,
	 * or null if there is nothing to answer (notifications).
	 */
	@Nullable
	String handle(@NotNull String body) {
		Object message;
		try {
			message = Json.parse(body);
		}
		catch (IllegalArgumentException e) {
			return Json.write(error(null, -32700, "Parse error: " + e.getMessage()));
		}
		if (message instanceof List) {
			List<Object> responses = new ArrayList<Object>();
			for (Object item : (List<?>) message) {
				Map<String, Object> response = handleMessage(item);
				if (response != null)
					responses.add(response);
			}
			return responses.isEmpty() ? null : Json.write(responses);
		}
		Map<String, Object> response = handleMessage(message);
		return response == null ? null : Json.write(response);
	}

	@Nullable
	@SuppressWarnings("unchecked")
	private Map<String, Object> handleMessage(@Nullable Object message) {
		if (!(message instanceof Map))
			return error(null, -32600, "Invalid request");
		Map<String, Object> request = (Map<String, Object>) message;
		Object id = request.get("id");
		Object methodValue = request.get("method");
		if (!(methodValue instanceof String))
			return null; // A response from the client, or garbage without an id
		String method = (String) methodValue;
		boolean notification = !request.containsKey("id");
		Map<String, Object> params = request.get("params") instanceof Map
			? (Map<String, Object>) request.get("params")
			: Collections.<String, Object> emptyMap();
		if (notification)
			return null; // notifications/initialized, notifications/cancelled, ...

		if (method.equals("initialize"))
			return result(id, initialize(params));
		if (method.equals("ping"))
			return result(id, new LinkedHashMap<String, Object>());
		if (method.equals("tools/list")) {
			Map<String, Object> result = new LinkedHashMap<String, Object>();
			result.put("tools", tools.definitions());
			return result(id, result);
		}
		if (method.equals("tools/call"))
			return callTool(id, params);
		return error(id, -32601, "Method not found: " + method);
	}

	@NotNull
	private Map<String, Object> initialize(@NotNull Map<String, Object> params) {
		Object requested = params.get("protocolVersion");
		String version = requested instanceof String && PROTOCOLS.contains(requested)
			? (String) requested : LATEST_PROTOCOL;
		Map<String, Object> toolsCapability = new LinkedHashMap<String, Object>();
		toolsCapability.put("listChanged", Boolean.FALSE);
		Map<String, Object> capabilities = new LinkedHashMap<String, Object>();
		capabilities.put("tools", toolsCapability);
		Map<String, Object> serverInfo = new LinkedHashMap<String, Object>();
		serverInfo.put("name", "DocFetcher");
		serverInfo.put("version", SystemConf.Str.ProgramVersion.get());
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("protocolVersion", version);
		result.put("capabilities", capabilities);
		result.put("serverInfo", serverInfo);
		result.put("instructions", INSTRUCTIONS);
		return result;
	}

	@NotNull
	@SuppressWarnings("unchecked")
	private Map<String, Object> callTool(@Nullable Object id, @NotNull Map<String, Object> params) {
		Object name = params.get("name");
		if (!(name instanceof String))
			return error(id, -32602, "Missing tool name");
		Map<String, Object> args = params.get("arguments") instanceof Map
			? (Map<String, Object>) params.get("arguments")
			: Collections.<String, Object> emptyMap();
		List<Object> content = new ArrayList<Object>();
		boolean isError = false;
		try {
			Object value = tools.call((String) name, args);
			if (value instanceof List) {
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
		return result(id, result);
	}

	@NotNull
	private static Map<String, Object> text(@NotNull String text) {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("type", "text");
		map.put("text", text);
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
	private static Map<String, Object> error(@Nullable Object id, int code, @NotNull String message) {
		Map<String, Object> error = new LinkedHashMap<String, Object>();
		error.put("code", code);
		error.put("message", message);
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("jsonrpc", "2.0");
		map.put("id", id);
		map.put("error", error);
		return map;
	}

}
