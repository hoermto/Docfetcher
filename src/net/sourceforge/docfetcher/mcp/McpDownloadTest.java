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
import static org.junit.Assert.fail;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import net.sourceforge.docfetcher.mcp.McpTools.ToolException;

/**
 * Tests the restrictions of downloads into the inbox: only public https
 * addresses, no programs, contents that match the file name. The download
 * itself (with the user's approval) is tested in McpInboxTest and in the app.
 */
public final class McpDownloadTest {

	private static String refused(String url) {
		try {
			McpDownload.checkAddress(url);
		}
		catch (ToolException e) {
			return e.getMessage();
		}
		fail("Not refused: " + url);
		return null;
	}

	@Test
	public void onlyPublicHttpsAddresses() throws Exception {
		assertTrue(refused("http://93.184.216.34/form.pdf").contains("Only https"));
		assertTrue(refused("ftp://93.184.216.34/form.pdf").contains("Only https"));
		assertTrue(refused("file:///C:/Windows/win.ini").contains("Only https"));
		assertTrue(refused("https://93.184.216.34:8443/form.pdf").contains("standard https port"));
		assertTrue(refused("https://user@93.184.216.34/form.pdf").contains("Not a valid"));
		for (String local : new String[] {"https://localhost/", "https://127.0.0.1/", "https://[::1]/",
				"https://192.168.178.1/", "https://10.0.0.1/", "https://172.16.5.4/", "https://169.254.1.1/",
				"https://100.64.0.1/", "https://0.0.0.0/", "https://[fd00::1]/", "https://[::ffff:192.168.1.1]/"})
			assertTrue(local, refused(local).contains("Only public internet addresses"));
		assertEquals("https://93.184.216.34/forms/a.pdf",
			McpDownload.checkAddress(" https://93.184.216.34/forms/../forms/a.pdf ").toString());
		assertTrue(McpDownload.isPublic(InetAddress.getByName("8.8.8.8")));
		assertTrue(McpDownload.isPublic(InetAddress.getByName("2a00:1450:4001::1")));
		assertFalse(McpDownload.isPublic(InetAddress.getByName("::ffff:10.1.2.3")));
	}

	private static String contentRefused(String content, String name) {
		try {
			McpDownload.checkContent(content.getBytes(StandardCharsets.ISO_8859_1), name);
		}
		catch (ToolException e) {
			return e.getMessage();
		}
		return null;
	}

	@Test
	public void noProgramsAndMatchingContent() {
		assertTrue(contentRefused("MZ\u0090\u0000program", "invoice.pdf").contains("program"));
		assertTrue(contentRefused("\u007fELF....", "data.txt").contains("program"));
		assertTrue(contentRefused("#!/bin/sh\nrm -rf ~", "notes.txt").contains("program"));
		assertTrue(contentRefused("<!DOCTYPE html><html>Login</html>", "form.pdf").contains("web page"));
		assertTrue(contentRefused("PK\u0003\u0004", "form.pdf").contains("not a .pdf"));
		assertEquals(null, contentRefused("%PDF-1.7 ...", "form.pdf"));
		assertEquals(null, contentRefused("PK\u0003\u0004...", "Vorlage.DOCX"));
		assertEquals(null, contentRefused("\u0089PNG\r\n", "bild.png"));
		assertEquals("Other types aren't checked", null, contentRefused("a;b;c", "tabelle.csv"));
	}

}
