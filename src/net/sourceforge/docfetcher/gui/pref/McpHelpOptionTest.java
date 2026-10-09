/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui.pref;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Properties;

import org.junit.Test;

import net.sourceforge.docfetcher.enums.Msg;

/**
 * Tests the help on connecting AI assistants: the configurations and the
 * English and German help texts with all of them filled in.
 */
public final class McpHelpOptionTest {

	@Test
	public void configurations() {
		assertEquals("claude mcp add --transport http docfetcher http://localhost:4711/mcp",
			McpHelpOption.claudeCodeCommand("4711"));
		// Exactly what 'codex mcp add docfetcher --url ...' writes into config.toml (Codex 0.162)
		assertEquals("[mcp_servers.docfetcher]\nurl = \"http://localhost:4711/mcp\"", McpHelpOption.chatGptConfig("4711"));
		assertEquals("codex mcp add docfetcher --url http://localhost:4711/mcp", McpHelpOption.codexCommand("4711"));
		assertTrue(McpHelpOption.claudeDesktopConfig("4711").contains("\"-y\", \"mcp-remote\", \"http://localhost:4711/mcp\""));
	}

	@Test
	public void englishHelp() {
		checkHelp(McpHelpOption.helpText("4711"));
		assertTrue(McpHelpOption.helpText("4711").contains(".codex\\config.toml"));
	}

	@Test
	public void germanHelp() throws Exception {
		Properties de = new Properties();
		InputStream in = new FileInputStream("dist/lang/Resource_de.properties");
		try {
			de.load(in); // Latin-1, as the program reads it
		}
		finally {
			in.close();
		}
		String port = "4711";
		// As Msg.format does it
		String text = MessageFormat.format(de.getProperty(Msg.mcp_help_text.name()).replace("'", "''"),
			McpHelpOption.claudeCodeCommand(port), McpHelpOption.claudeDesktopConfig(port),
			McpHelpOption.chatGptConfig(port), McpHelpOption.codexCommand(port), McpHelpOption.url(port));
		checkHelp(text);
		assertTrue(text, text.contains("'MCP-Server für KI-Assistenten'") && text.contains(".codex\\config.toml"));
		assertTrue(de.getProperty(Msg.mcp_copy_chatgpt_config.name()).contains("ChatGPT"));
	}

	private static void checkHelp(String text) {
		assertTrue(text, text.contains("claude mcp add --transport http docfetcher http://localhost:4711/mcp"));
		assertTrue(text, text.contains("[mcp_servers.docfetcher]\nurl = \"http://localhost:4711/mcp\""));
		assertTrue(text, text.contains("codex mcp add docfetcher --url http://localhost:4711/mcp"));
		assertTrue(text, text.contains("\"mcpServers\""));
		assertTrue(text, text.contains("chatgpt.com"));
		for (int i = 0; i <= 4; i++)
			assertFalse(text, text.contains("{" + i + "}"));
	}

}
