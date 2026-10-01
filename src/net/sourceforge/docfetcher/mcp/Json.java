/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * A minimal JSON reader and writer for the MCP server. JSON objects become
 * {@code Map<String, Object>} (in document order), arrays {@code List<Object>},
 * numbers {@code Long} or {@code Double}, and true/false/null the Java
 * equivalents.
 */
final class Json {

	private final String text;
	private int pos;

	private Json(@NotNull String text) {
		this.text = text;
	}

	/** Parses the given JSON text; throws IllegalArgumentException if it is invalid. */
	@Nullable
	static Object parse(@NotNull String text) {
		Json json = new Json(text);
		Object value = json.readValue();
		json.skipWhitespace();
		if (json.pos < text.length())
			throw json.error("Unexpected text after the value");
		return value;
	}

	/** Returns the JSON text of the given value (maps, lists, strings, numbers, booleans, null). */
	@NotNull
	static String write(@Nullable Object value) {
		StringBuilder sb = new StringBuilder();
		write(sb, value);
		return sb.toString();
	}

	// ---- Reading

	private IllegalArgumentException error(String message) {
		return new IllegalArgumentException(message + " at position " + pos);
	}

	private void skipWhitespace() {
		while (pos < text.length() && Character.isWhitespace(text.charAt(pos)))
			pos++;
	}

	private char next() {
		if (pos >= text.length())
			throw error("Unexpected end");
		return text.charAt(pos++);
	}

	private void expect(String word) {
		if (!text.startsWith(word, pos))
			throw error("Expected '" + word + "'");
		pos += word.length();
	}

	@Nullable
	private Object readValue() {
		skipWhitespace();
		if (pos >= text.length())
			throw error("Unexpected end");
		char c = text.charAt(pos);
		switch (c) {
		case '{': return readObject();
		case '[': return readArray();
		case '"': return readString();
		case 't': expect("true"); return Boolean.TRUE;
		case 'f': expect("false"); return Boolean.FALSE;
		case 'n': expect("null"); return null;
		default:
			if (c == '-' || (c >= '0' && c <= '9'))
				return readNumber();
			throw error("Unexpected character '" + c + "'");
		}
	}

	@NotNull
	private Map<String, Object> readObject() {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		pos++; // {
		skipWhitespace();
		if (pos < text.length() && text.charAt(pos) == '}') {
			pos++;
			return map;
		}
		while (true) {
			skipWhitespace();
			if (pos >= text.length() || text.charAt(pos) != '"')
				throw error("Expected a name");
			String key = readString();
			skipWhitespace();
			if (next() != ':')
				throw error("Expected ':'");
			map.put(key, readValue());
			skipWhitespace();
			char c = next();
			if (c == '}')
				return map;
			if (c != ',')
				throw error("Expected ',' or '}'");
		}
	}

	@NotNull
	private List<Object> readArray() {
		List<Object> list = new ArrayList<Object>();
		pos++; // [
		skipWhitespace();
		if (pos < text.length() && text.charAt(pos) == ']') {
			pos++;
			return list;
		}
		while (true) {
			list.add(readValue());
			skipWhitespace();
			char c = next();
			if (c == ']')
				return list;
			if (c != ',')
				throw error("Expected ',' or ']'");
		}
	}

	@NotNull
	private String readString() {
		pos++; // "
		StringBuilder sb = new StringBuilder();
		while (true) {
			char c = next();
			if (c == '"')
				return sb.toString();
			if (c != '\\') {
				sb.append(c);
				continue;
			}
			char e = next();
			switch (e) {
			case '"': sb.append('"'); break;
			case '\\': sb.append('\\'); break;
			case '/': sb.append('/'); break;
			case 'b': sb.append('\b'); break;
			case 'f': sb.append('\f'); break;
			case 'n': sb.append('\n'); break;
			case 'r': sb.append('\r'); break;
			case 't': sb.append('\t'); break;
			case 'u':
				if (pos + 4 > text.length())
					throw error("Invalid unicode escape");
				try {
					sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
				}
				catch (NumberFormatException ex) {
					throw error("Invalid unicode escape");
				}
				pos += 4;
				break;
			default:
				throw error("Invalid escape '\\" + e + "'");
			}
		}
	}

	@NotNull
	private Object readNumber() {
		int start = pos;
		boolean decimal = false;
		while (pos < text.length()) {
			char c = text.charAt(pos);
			if (c == '.' || c == 'e' || c == 'E')
				decimal = true;
			else if (!(c == '-' || c == '+' || (c >= '0' && c <= '9')))
				break;
			pos++;
		}
		String number = text.substring(start, pos);
		try {
			if (!decimal)
				return Long.valueOf(number);
			return Double.valueOf(number);
		}
		catch (NumberFormatException e) {
			try {
				return Double.valueOf(number);
			}
			catch (NumberFormatException e2) {
				throw error("Invalid number");
			}
		}
	}

	// ---- Writing

	private static void write(@NotNull StringBuilder sb, @Nullable Object value) {
		if (value == null) {
			sb.append("null");
		}
		else if (value instanceof String) {
			writeString(sb, (String) value);
		}
		else if (value instanceof Boolean || value instanceof Long || value instanceof Integer) {
			sb.append(value);
		}
		else if (value instanceof Number) {
			double d = ((Number) value).doubleValue();
			if (Double.isNaN(d) || Double.isInfinite(d))
				sb.append("null");
			else if (d == Math.rint(d) && Math.abs(d) < 1e15)
				sb.append((long) d);
			else
				sb.append(d);
		}
		else if (value instanceof Map) {
			sb.append('{');
			boolean first = true;
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
				if (!first)
					sb.append(',');
				first = false;
				writeString(sb, String.valueOf(entry.getKey()));
				sb.append(':');
				write(sb, entry.getValue());
			}
			sb.append('}');
		}
		else if (value instanceof Iterable) {
			sb.append('[');
			boolean first = true;
			for (Object item : (Iterable<?>) value) {
				if (!first)
					sb.append(',');
				first = false;
				write(sb, item);
			}
			sb.append(']');
		}
		else {
			writeString(sb, value.toString());
		}
	}

	private static void writeString(@NotNull StringBuilder sb, @NotNull String s) {
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '"': sb.append("\\\""); break;
			case '\\': sb.append("\\\\"); break;
			case '\n': sb.append("\\n"); break;
			case '\r': sb.append("\\r"); break;
			case '\t': sb.append("\\t"); break;
			case '\b': sb.append("\\b"); break;
			case '\f': sb.append("\\f"); break;
			default:
				if (c < 0x20)
					sb.append(String.format("\\u%04x", (int) c));
				else
					sb.append(c);
			}
		}
		sb.append('"');
	}

}
