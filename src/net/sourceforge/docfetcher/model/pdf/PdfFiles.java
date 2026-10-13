/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.pdf;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;

import net.sourceforge.docfetcher.util.UtilFile;
import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * Loads PDF files without keeping them open. PDFBox keeps a file it loads
 * directly open until the document is closed - during text recognition for
 * minutes -, and on Windows such a file can't be moved, renamed or deleted
 * meanwhile, neither by the user nor by DocFetcher itself. Here the file is
 * read into a buffer (in memory, large files in a temporary file) and closed
 * right away; the document works on the copy.
 */
public final class PdfFiles {

	/** Larger PDF files are buffered in a temporary file. */
	private static final long MAX_MEMORY = 32L * 1024 * 1024;

	private PdfFiles() {}

	@NotNull
	public static PDDocument load(@NotNull File file) throws IOException {
		InputStream in = UtilFile.openForReading(file);
		try {
			return PDDocument.load(in, MemoryUsageSetting.setupMixed(MAX_MEMORY));
		}
		finally {
			in.close();
		}
	}

}
