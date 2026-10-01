/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.ocr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * The dates of a file - created, modified, accessed - taken before the
 * recognized text is stored in it and set back afterwards, so that storing
 * the text doesn't change any of them and sorting by date keeps working, in
 * DocFetcher and in the file manager.
 */
final class FileDates {

	private final FileTime created;
	private final FileTime modified;
	private final FileTime accessed;

	private FileDates(@NotNull BasicFileAttributes attributes) {
		created = attributes.creationTime();
		modified = attributes.lastModifiedTime();
		accessed = attributes.lastAccessTime();
	}

	/** Returns the dates of the file, or null if they can't be read. */
	@Nullable
	static FileDates of(@NotNull File file) {
		try {
			return new FileDates(Files.readAttributes(file.toPath(), BasicFileAttributes.class));
		}
		catch (IOException e) {
			Util.printErr(e);
			return null;
		}
	}

	/** Gives the file these dates; file systems without creation dates keep theirs. */
	void applyTo(@NotNull File file) throws IOException {
		Files.getFileAttributeView(file.toPath(), BasicFileAttributeView.class)
			.setTimes(modified, accessed, created);
	}

}
