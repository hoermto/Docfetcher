/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.mcp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.sourceforge.docfetcher.enums.SystemConf;
import net.sourceforge.docfetcher.mcp.McpTools.ToolException;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * Downloads a file from a public internet address for add_to_inbox, after
 * the user agreed. Only https on the standard port and only public addresses
 * - not this computer, not the home network, also not after a redirect;
 * https also defeats DNS tricks, as devices in the home network have no valid
 * certificate for a foreign name. At most {@link McpInbox#MAX_MB}, 30
 * seconds. The content must not be a program and must match the extension of
 * the file name. Nothing is opened or run: the bytes are only stored, with
 * the Windows mark of files from the internet.
 */
final class McpDownload {

	private static final Duration TIMEOUT = Duration.ofSeconds(30);
	private static final int MAX_REDIRECTS = 5;

	/** Starts of programs and scripts, which are refused whatever the name. */
	private static final List<byte[]> PROGRAM_MAGIC = Arrays.asList(
		new byte[] {'M', 'Z'}, // Windows programs and libraries
		new byte[] {0x7f, 'E', 'L', 'F'}, // Linux programs
		new byte[] {(byte) 0xfe, (byte) 0xed, (byte) 0xfa, (byte) 0xce}, // macOS programs
		new byte[] {(byte) 0xfe, (byte) 0xed, (byte) 0xfa, (byte) 0xcf},
		new byte[] {(byte) 0xcf, (byte) 0xfa, (byte) 0xed, (byte) 0xfe},
		new byte[] {(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe},
		new byte[] {'#', '!'}, // Scripts
		new byte[] {0x4c, 0x00, 0x00, 0x00}); // Windows shortcuts

	/** What files of these types start with; other types aren't checked. */
	private static final Map<String, List<byte[]>> TYPE_MAGIC = new HashMap<String, List<byte[]>>();
	static {
		List<byte[]> zip = Arrays.asList(new byte[] {'P', 'K'});
		for (String ext : new String[] {"docx", "xlsx", "pptx", "odt", "ods", "odp", "zip", "epub"})
			TYPE_MAGIC.put(ext, zip);
		List<byte[]> ole = Arrays.asList(new byte[] {(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0});
		for (String ext : new String[] {"doc", "xls", "ppt", "msg"})
			TYPE_MAGIC.put(ext, ole);
		TYPE_MAGIC.put("pdf", Arrays.asList(new byte[] {'%', 'P', 'D', 'F'}));
		TYPE_MAGIC.put("png", Arrays.asList(new byte[] {(byte) 0x89, 'P', 'N', 'G'}));
		List<byte[]> jpeg = Arrays.asList(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
		TYPE_MAGIC.put("jpg", jpeg);
		TYPE_MAGIC.put("jpeg", jpeg);
		TYPE_MAGIC.put("gif", Arrays.asList(new byte[] {'G', 'I', 'F', '8'}));
		List<byte[]> tiff = Arrays.asList(new byte[] {'I', 'I', 42, 0}, new byte[] {'M', 'M', 0, 42});
		TYPE_MAGIC.put("tif", tiff);
		TYPE_MAGIC.put("tiff", tiff);
		TYPE_MAGIC.put("rtf", Arrays.asList(new byte[] {'{', '\\', 'r', 't', 'f'}));
	}

	private McpDownload() {
	}

	/**
	 * Checks that the address may be downloaded from: https, standard port,
	 * a host whose addresses are all public. Returns the normalized address.
	 */
	@NotNull
	static URI checkAddress(@NotNull String url) throws ToolException {
		URI uri;
		try {
			uri = new URI(url.trim()).normalize();
		}
		catch (Exception e) {
			throw new ToolException("Not a valid internet address: " + url);
		}
		if (!"https".equalsIgnoreCase(uri.getScheme()))
			throw new ToolException("Only https addresses can be downloaded: " + url);
		if (uri.getPort() != -1 && uri.getPort() != 443)
			throw new ToolException("Only the standard https port can be used: " + url);
		String host = uri.getHost();
		if (host == null || host.isEmpty() || uri.getUserInfo() != null)
			throw new ToolException("Not a valid internet address: " + url);
		InetAddress[] addresses;
		try {
			addresses = InetAddress.getAllByName(host);
		}
		catch (UnknownHostException e) {
			throw new ToolException("Unknown host: " + host);
		}
		for (InetAddress address : addresses)
			if (!isPublic(address))
				throw new ToolException("Only public internet addresses can be downloaded from, not this "
					+ "computer or the local network: " + host);
		return uri;
	}

	/** Not this computer, not a private, link-local, shared or multicast network. */
	static boolean isPublic(@NotNull InetAddress address) {
		if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress())
			return false;
		byte[] b = address.getAddress();
		if (address instanceof Inet6Address) {
			if ((b[0] & 0xfe) == 0xfc)
				return false; // Unique local fc00::/7
			boolean mapped = true; // ::ffff:a.b.c.d
			for (int i = 0; i < 10; i++)
				mapped &= b[i] == 0;
			mapped &= b[10] == (byte) 0xff && b[11] == (byte) 0xff;
			if (mapped) {
				try {
					return isPublic(InetAddress.getByAddress(Arrays.copyOfRange(b, 12, 16)));
				}
				catch (UnknownHostException e) {
					return false;
				}
			}
			return true;
		}
		int first = b[0] & 0xff;
		int second = b[1] & 0xff;
		if (first == 0 || first >= 240)
			return false; // 'This network' and reserved
		if (first == 100 && second >= 64 && second <= 127)
			return false; // Shared address space of providers (100.64.0.0/10)
		return true;
	}

	/** Downloads the file, following redirects to public https addresses only. */
	@NotNull
	static byte[] fetch(@NotNull String url, @NotNull String fileName) throws ToolException {
		HttpClient client = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.connectTimeout(TIMEOUT)
			.build();
		URI uri = checkAddress(url);
		long deadline = System.currentTimeMillis() + TIMEOUT.toMillis();
		for (int redirects = 0; ; redirects++) {
			HttpResponse<InputStream> response;
			try {
				response = client.send(HttpRequest.newBuilder(uri)
					.timeout(TIMEOUT)
					.header("User-Agent", "DocFetcher/" + SystemConf.Str.ProgramVersion.get())
					.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new ToolException("The download was interrupted.");
			}
			catch (Exception e) {
				throw new ToolException("The download failed: " + e.getMessage());
			}
			int status = response.statusCode();
			if (status >= 300 && status < 400) {
				closeQuietly(response.body());
				String location = response.headers().firstValue("Location").orElse(null);
				if (location == null || redirects >= MAX_REDIRECTS)
					throw new ToolException("Too many or broken redirects at " + uri);
				uri = checkAddress(uri.resolve(location).toString());
				continue;
			}
			if (status != 200) {
				closeQuietly(response.body());
				throw new ToolException("The server answered with HTTP " + status + ": " + uri);
			}
			long maxBytes = McpInbox.MAX_MB * 1024L * 1024L;
			if (response.headers().firstValueAsLong("Content-Length").orElse(0) > maxBytes) {
				closeQuietly(response.body());
				throw new ToolException("The file is too large (at most " + McpInbox.MAX_MB + " MB).");
			}
			byte[] data = read(response.body(), maxBytes, deadline);
			checkContent(data, fileName);
			return data;
		}
	}

	@NotNull
	private static byte[] read(@NotNull InputStream in, long maxBytes, long deadline) throws ToolException {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[64 * 1024];
			for (int n; (n = in.read(buffer)) != -1;) {
				out.write(buffer, 0, n);
				if (out.size() > maxBytes)
					throw new ToolException("The file is too large (at most " + McpInbox.MAX_MB + " MB).");
				if (System.currentTimeMillis() > deadline)
					throw new ToolException("The download took too long (more than 30 seconds).");
			}
			return out.toByteArray();
		}
		catch (ToolException e) {
			throw e;
		}
		catch (Exception e) {
			throw new ToolException("The download failed: " + e.getMessage());
		}
		finally {
			closeQuietly(in);
		}
	}

	/** Refuses programs, and contents that don't match the extension. */
	static void checkContent(@NotNull byte[] data, @NotNull String fileName) throws ToolException {
		for (byte[] magic : PROGRAM_MAGIC)
			if (startsWith(data, magic))
				throw new ToolException("The downloaded file is a program or script; it was not stored.");
		String extension = Util.getExtension(fileName).toLowerCase(Locale.ROOT);
		List<byte[]> expected = TYPE_MAGIC.get(extension);
		if (expected == null)
			return;
		for (byte[] magic : expected)
			if (startsWith(data, magic))
				return;
		String start = new String(data, 0, Math.min(data.length, 200), StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
		throw new ToolException("The downloaded content is not a ." + extension + " file"
			+ (start.contains("<html") || start.contains("<!doctype") ? " but a web page" : "")
			+ "; it was not stored.");
	}

	private static boolean startsWith(@NotNull byte[] data, @NotNull byte[] magic) {
		if (data.length < magic.length)
			return false;
		for (int i = 0; i < magic.length; i++)
			if (data[i] != magic[i])
				return false;
		return true;
	}

	/**
	 * Marks the file as downloaded from the internet (Windows, NTFS): Windows
	 * and Office then warn before running it or open it in protected view.
	 */
	static void markFromInternet(@NotNull File file, @NotNull String url) {
		if (!Util.IS_WINDOWS)
			return;
		try {
			FileOutputStream out = new FileOutputStream(file.getPath() + ":Zone.Identifier");
			try {
				out.write(("[ZoneTransfer]\r\nZoneId=3\r\nHostUrl=" + url + "\r\n").getBytes(StandardCharsets.UTF_8));
			}
			finally {
				out.close();
			}
		}
		catch (Exception e) {
			// E.g. not on NTFS: no mark
		}
	}

	private static void closeQuietly(@NotNull InputStream in) {
		try {
			in.close();
		}
		catch (Exception e) {
			// Nothing to do
		}
	}

}
