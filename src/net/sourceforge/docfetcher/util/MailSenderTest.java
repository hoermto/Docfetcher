/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assume;
import org.junit.Test;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;

/**
 * Tests the Simple MAPI message built for the email program. Calling MAPI
 * itself would open a message window, so that is left to the GUI test.
 */
public final class MailSenderTest {

	@Test
	public void buildsTheMapiMessageWithAllAttachments() {
		Assume.assumeTrue(Pointer.SIZE == 8); // The layout is the one of 64-bit Windows
		File first = new File("C:/scans/Rechnung März.pdf");
		File second = new File("C:/scans/letter.pdf");
		List<Memory> keepAlive = new ArrayList<Memory>();
		Memory message = MailSender.createMessage("Rechnung März.pdf, letter.pdf",
			Arrays.asList(first, second), keepAlive);

		assertEquals("Rechnung März.pdf, letter.pdf",
			message.getPointer(MailSender.SUBJECT_OFFSET).getString(0, true));
		assertEquals(2, message.getInt(MailSender.FILE_COUNT_OFFSET));

		Pointer files = message.getPointer(MailSender.FILES_OFFSET);
		File[] expected = { first, second };
		for (int i = 0; i < expected.length; i++) {
			long base = (long) i * MailSender.FILE_DESC_SIZE;
			assertEquals("nPosition", -1, files.getInt(base + 8));
			assertEquals(expected[i].getAbsolutePath(),
				files.getPointer(base + MailSender.FILE_PATH_OFFSET).getString(0, true));
			assertEquals(expected[i].getName(),
				files.getPointer(base + MailSender.FILE_NAME_OFFSET).getString(0, true));
		}
		// Subject, the list and two strings per attachment must stay allocated
		assertTrue(keepAlive.size() >= 6);
	}

}
