/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * Opens a new message in the user's email program with the given files
 * attached. The message is never sent automatically: the user sees it, adds
 * the recipients and sends it.
 * <p>
 * On Windows this uses Simple MAPI, which works with the default email
 * program (Outlook, Thunderbird, ...); elsewhere 'xdg-email' (Linux) or the
 * Mail app (macOS).
 */
public final class MailSender {

	/** Thrown if the email program could not be started. */
	public static final class MailException extends Exception {
		private static final long serialVersionUID = 1L;
		private final int mapiError;

		MailException(int mapiError, @NotNull String message) {
			super(message);
			this.mapiError = mapiError;
		}

		/** The MAPI error code, or 0 if the error didn't come from MAPI. */
		public int getMapiError() {
			return mapiError;
		}
	}

	// Simple MAPI constants (mapi.h)
	private static final int MAPI_LOGON_UI = 0x1;
	private static final int MAPI_DIALOG = 0x8;
	static final int SUCCESS_SUCCESS = 0;
	static final int MAPI_USER_ABORT = 1;
	private static final int MAPI_E_NOT_SUPPORTED = 26;

	/*
	 * Layout of MapiFileDescW and MapiMessageW on 64-bit Windows: 32-bit
	 * integers, pointers aligned to 8 bytes.
	 */
	static final int FILE_DESC_SIZE = 40;
	static final int FILE_PATH_OFFSET = 16;
	static final int FILE_NAME_OFFSET = 24;
	static final int MESSAGE_SIZE = 96;
	static final int SUBJECT_OFFSET = 8;
	static final int FILE_COUNT_OFFSET = 80;
	static final int FILES_OFFSET = 88;

	private MailSender() {}

	/**
	 * Opens a new message with the given subject and attachments. Blocks
	 * until the email program has taken over the message, which, depending
	 * on the program, may be when the message window is closed; call it
	 * from a background thread. Returns false if the user cancelled.
	 */
	public static boolean compose(@NotNull String subject, @NotNull List<File> attachments)
			throws MailException {
		Util.checkNotNull(subject, attachments);
		if (Util.IS_WINDOWS)
			return composeWithMapi(subject, attachments);
		List<String> command = new ArrayList<String>();
		if (Util.IS_MAC_OS_X) {
			command.add("open");
			command.add("-a");
			command.add("Mail");
			for (File file : attachments)
				command.add(file.getPath());
		}
		else {
			command.add("xdg-email");
			command.add("--subject");
			command.add(subject);
			for (File file : attachments) {
				command.add("--attach");
				command.add(file.getPath());
			}
		}
		try {
			new ProcessBuilder(command).start();
			return true;
		}
		catch (IOException e) {
			throw new MailException(0, e.getMessage());
		}
	}

	private static boolean composeWithMapi(	@NotNull String subject,
											@NotNull List<File> attachments)
			throws MailException {
		Function sendMail;
		try {
			sendMail = NativeLibrary.getInstance("mapi32").getFunction("MAPISendMailW");
		}
		catch (UnsatisfiedLinkError e) {
			throw new MailException(MAPI_E_NOT_SUPPORTED, e.getMessage());
		}
		List<Memory> keepAlive = new ArrayList<Memory>();
		Memory message = createMessage(subject, attachments, keepAlive);
		int result = sendMail.invokeInt(new Object[] {
			Pointer.NULL, Pointer.NULL, message, MAPI_LOGON_UI | MAPI_DIALOG, 0 });
		keepAlive.clear(); // The memory may be freed only after the call
		if (result == SUCCESS_SUCCESS)
			return true;
		if (result == MAPI_USER_ABORT)
			return false;
		throw new MailException(result, "MAPI error " + result);
	}

	/**
	 * Builds the MapiMessageW structure; the strings and the attachment
	 * list are added to {@code keepAlive}, which must be kept until the
	 * MAPI call has returned.
	 */
	@NotNull
	static Memory createMessage(@NotNull String subject,
								@NotNull List<File> attachments,
								@NotNull List<Memory> keepAlive) {
		Memory message = new Memory(MESSAGE_SIZE);
		message.clear();
		message.setPointer(SUBJECT_OFFSET, wideString(subject, keepAlive));
		if (!attachments.isEmpty()) {
			Memory files = new Memory(FILE_DESC_SIZE * attachments.size());
			files.clear();
			keepAlive.add(files);
			for (int i = 0; i < attachments.size(); i++) {
				File file = attachments.get(i);
				long base = (long) i * FILE_DESC_SIZE;
				files.setInt(base + 8, -1); // nPosition: attachment, not in the text
				files.setPointer(base + FILE_PATH_OFFSET, wideString(file.getAbsolutePath(), keepAlive));
				files.setPointer(base + FILE_NAME_OFFSET, wideString(file.getName(), keepAlive));
			}
			message.setInt(FILE_COUNT_OFFSET, attachments.size());
			message.setPointer(FILES_OFFSET, files);
		}
		return message;
	}

	@NotNull
	private static Memory wideString(@NotNull String value, @NotNull List<Memory> keepAlive) {
		Memory memory = new Memory((value.length() + 1) * 2L);
		memory.setString(0, value, true);
		keepAlive.add(memory);
		return memory;
	}

}
