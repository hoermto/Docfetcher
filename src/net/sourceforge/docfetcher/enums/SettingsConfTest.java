/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.enums;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.ConfLoader;

/**
 * Saving the settings needs a description on every group of settings: a
 * constant put between the annotation and its enum (as it happened with the
 * MCP folder answers) made saving fail with a NullPointerException.
 */
public final class SettingsConfTest {

	@Test
	public void settingsCanBeSaved() throws Exception {
		AppUtil.Const.autoInit();
		File file = File.createTempFile("settings-conf", ".txt");
		try {
			ConfLoader.save(file, SettingsConf.class, "# test");
			String saved = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
			assertTrue(saved.contains("McpFileFolder = "));
			assertTrue(saved.contains("McpFileFolderAnswer = "));
		}
		finally {
			file.delete();
		}
	}

}
