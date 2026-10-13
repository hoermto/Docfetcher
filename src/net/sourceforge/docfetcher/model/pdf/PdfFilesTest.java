/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.pdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.Test;

import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilFile;

/**
 * Tests that DocFetcher doesn't lock the files it reads: while a PDF is
 * loaded (e.g. during text recognition) or a document is being parsed, the
 * file can be moved - on Windows, a file opened with java.io can't.
 */
public final class PdfFilesTest {

	@Test
	public void filesCanBeMovedWhileTheyAreRead() throws Exception {
		File dir = Files.createTempDirectory("docfetcher-lock").toFile();
		try {
			File pdf = PdfPagesTest.createTextPdf(new File(dir, "scan.pdf"), "Erste Seite", "Zweite Seite");
			File moved = new File(dir, "verschoben.pdf");

			PDDocument doc = PdfFiles.load(pdf);
			try {
				assertTrue("Movable while loaded", pdf.renameTo(moved));
				assertEquals(2, doc.getNumberOfPages());
				assertTrue(new PDFTextStripper().getText(doc).contains("Zweite Seite"));
			}
			finally {
				doc.close();
			}

			InputStream in = UtilFile.openForReading(moved);
			try {
				assertTrue("Movable while open for parsing", moved.renameTo(pdf));
				assertTrue(in.read() >= 0);
			}
			finally {
				in.close();
			}

			// What PDFBox does by itself: the file stays locked on Windows
			PDDocument direct = PDDocument.load(pdf);
			try {
				if (Util.IS_WINDOWS)
					assertFalse(pdf.renameTo(moved));
			}
			finally {
				direct.close();
			}
		}
		finally {
			UtilFile.deletePermanently(dir);
		}
	}

}
