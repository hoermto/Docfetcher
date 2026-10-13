/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.enums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.junit.Test;

/**
 * Tests the texts: Msg.format doubles the apostrophes itself, so a text with
 * two of them in a row shows two ('Unterordner' as ''Unterordner'').
 */
public final class MsgTest {

	@Test
	public void apostrophesAreSingle() throws Exception {
		Msg.setCheckEnabled(false); // get() of texts with {0}
		for (Msg msg : Msg.values())
			assertFalse(msg.name(), msg.get().contains("''"));
		Properties de = new Properties();
		try (InputStream in = new FileInputStream("dist/lang/Resource_de.properties")) {
			de.load(in);
		}
		for (String key : de.stringPropertyNames())
			assertFalse(key, de.getProperty(key).contains("''"));
		assertEquals("Move the folder 'Unterordner' with 1 file(s) to the DocFetcher bin?",
			Msg.confirm_delete_folder_bin.format("Unterordner", 1));
		assertTrue(Msg.no_results_msg.format("Steuer*").startsWith("The search for 'Steuer*' found nothing."));
		assertTrue(java.text.MessageFormat.format(de.getProperty("no_results_msg").replace("'", "''"), "Steuer*")
			.startsWith("Die Suche nach 'Steuer*' hat nichts gefunden."));
	}

}
