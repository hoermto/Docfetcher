/*******************************************************************************
 * Copyright (c) 2011 Tran Nam Quang.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tran Nam Quang - initial API and implementation
 *******************************************************************************/

package net.sourceforge.docfetcher.model;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * @author Tran Nam Quang
 */
public enum DocumentType {
	
	FILE (false),
	OUTLOOK (true),
	;

	// Lucene's maximum term length in bytes (UTF-8 encoded)
	private static final int MAX_TERM_LENGTH = 32766;

	private final String prefix = name().toLowerCase() + "://";
	private final boolean isEmail;

	private DocumentType(boolean isEmail) {
		this.isEmail = isEmail;
	}

	@NotNull
	public String createUniqueId(@NotNull Path path) {
		String uid = prefix + path.getPath();

		// Check if UID exceeds Lucene's maximum term length
		try {
			byte[] uidBytes = uid.getBytes("UTF-8");
			if (uidBytes.length > MAX_TERM_LENGTH) {
				// Truncate the UID to fit within the limit
				// Keep as much of the path as possible and append a hash for uniqueness
				uid = truncateUid(uid, path.getPath());
			}
		} catch (UnsupportedEncodingException e) {
			// UTF-8 should always be supported, but if not, return the original UID
			// and let Lucene handle the error
		}

		return uid;
	}

	@NotNull
	private String truncateUid(@NotNull String originalUid, @NotNull String pathStr) {
		try {
			// Create a hash of the full path for uniqueness
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] hashBytes = md.digest(pathStr.getBytes("UTF-8"));
			StringBuilder hashHex = new StringBuilder();
			for (byte b : hashBytes) {
				hashHex.append(String.format("%02x", b));
			}

			// Calculate how much of the original path we can keep
			// Reserve space for: prefix + hash + separator
			String separator = "...#";
			String suffix = separator + hashHex.toString();
			int maxPrefixLength = MAX_TERM_LENGTH - suffix.getBytes("UTF-8").length;

			// Truncate the UID to fit
			String truncatedUid = originalUid;
			while (truncatedUid.getBytes("UTF-8").length > maxPrefixLength) {
				// Remove one character at a time from the end
				truncatedUid = truncatedUid.substring(0, truncatedUid.length() - 1);
			}

			return truncatedUid + suffix;
		} catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
			// Fallback: simple truncation without hash
			String truncated = originalUid;
			try {
				while (truncated.getBytes("UTF-8").length > MAX_TERM_LENGTH) {
					truncated = truncated.substring(0, truncated.length() - 1);
				}
			} catch (UnsupportedEncodingException e2) {
				// Should never happen
			}
			return truncated;
		}
	}
	
	@NotNull
	public static Path extractPath(@NotNull String uid) {
		Util.checkNotNull(uid);
		for (DocumentType type : values())
			if (uid.startsWith(type.prefix))
				return new Path(uid.substring(type.prefix.length()));
		throw new IllegalArgumentException();
	}
	
	public static boolean isEmailType(@NotNull String uid) {
		Util.checkNotNull(uid);
		for (DocumentType type : values())
			if (type.isEmail && uid.startsWith(type.prefix))
				return true;
		return false;
	}
	
}