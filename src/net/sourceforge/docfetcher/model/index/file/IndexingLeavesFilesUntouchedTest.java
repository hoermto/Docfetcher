/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.index.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.model.index.IndexingReporter;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.Util;

/**
 * Indexing only reads the documents: documents with text are never written,
 * and a rebuild of an index doesn't change any file - neither content nor
 * modification time nor the archive attribute that backup programs look at.
 * Only scans without text get their recognized text once (if the option
 * 'Store the recognized text in the files' is on).
 */
public final class IndexingLeavesFilesUntouchedTest {

	private static final String[] SAMPLES = {
		"multi-page.pdf", "testWORD-Tika.doc", "testWORD-Tika.docx",
		"file-requiring-read-write-mode.xlsx", "missing-meta.xml-entry.odt",
		"rtf-with-image.rtf", "test.html", "zip.zip" };

	private File root;
	private File indexDir1;
	private File indexDir2;

	@Before
	public void setUp() throws Exception {
		AppUtil.Const.autoInit();
		root = createDir("docfetcher-untouched");
		indexDir1 = createDir("docfetcher-index");
		indexDir2 = createDir("docfetcher-index");
		for (String name : SAMPLES)
			Files.copy(new File("dev/test-files", name).toPath(),
				new File(root, name).toPath(), StandardCopyOption.COPY_ATTRIBUTES);
		Files.write(new File(root, "note.txt").toPath(), "a note about Müller".getBytes("UTF-8"));
		writeTextPdf(new File(root, "letter.pdf"));
		writeScanPdf(new File(root, "scan.pdf"));
		ImageIO.write(textImage(), "png", new File(root, "photo.png"));
		// Older files, as usual for documents
		long lastWeek = System.currentTimeMillis() - 7 * 24 * 3600 * 1000L;
		for (File file : root.listFiles())
			assertTrue(file.setLastModified(lastWeek));
	}

	@After
	public void tearDown() {
		// On Windows the index files may still be locked by the searcher
		for (File dir : new File[] { root, indexDir1, indexDir2 }) {
			try {
				Util.deleteRecursively(dir);
			}
			catch (java.io.IOException e) {
				dir.deleteOnExit();
			}
		}
	}

	@Test
	public void documentsWithTextAreNeverWritten() throws Exception {
		Map<String, String> before = snapshot();
		index(indexDir1);
		Map<String, String> after = snapshot();
		for (String name : before.keySet()) {
			if (name.equals("scan.pdf") || name.equals("photo.png"))
				continue; // May get their recognized text once
			assertEquals(name, before.get(name), after.get(name));
		}
		assertEquals("No files added or left behind", before.keySet(), after.keySet());
	}

	@Test
	public void rebuildingAnIndexChangesNoFile() throws Exception {
		index(indexDir1);
		Map<String, String> before = snapshot();
		clearArchiveAttributes();
		index(indexDir2); // A new index of the same folder, as in a rebuild
		assertEquals(before, snapshot());
		if (Util.IS_WINDOWS)
			for (File file : root.listFiles())
				assertEquals("Archive attribute of " + file.getName(), false,
					Files.getAttribute(file.toPath(), "dos:archive"));
	}

	private void index(File indexDir) throws Exception {
		FileIndex index = new FileIndex(indexDir, root);
		index.update(new IndexingReporter(), Cancelable.nullCancelable);
	}

	/** File name -> size, modification time and content hash. */
	private Map<String, String> snapshot() throws Exception {
		Map<String, String> result = new TreeMap<String, String>();
		for (File file : root.listFiles()) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(Files.readAllBytes(file.toPath()));
			StringBuilder sb = new StringBuilder();
			for (byte b : hash)
				sb.append(String.format("%02x", b));
			result.put(file.getName(), file.length() + " " + file.lastModified() + " " + sb);
		}
		return result;
	}

	private void clearArchiveAttributes() throws Exception {
		if (!Util.IS_WINDOWS)
			return;
		for (File file : root.listFiles())
			Files.setAttribute(file.toPath(), "dos:archive", false);
	}

	private static File createDir(String prefix) throws Exception {
		File dir = File.createTempFile(prefix, "");
		assertTrue(dir.delete() && dir.mkdir());
		return dir;
	}

	private static BufferedImage textImage() {
		BufferedImage image = new BufferedImage(1240, 400, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		g.setColor(Color.BLACK);
		g.setFont(new Font(Font.SERIF, Font.PLAIN, 60));
		g.drawString("Rechnung Nummer 4711", 100, 200);
		g.dispose();
		return image;
	}

	private static void writeTextPdf(File file) throws Exception {
		PDDocument doc = new PDDocument();
		try {
			PDPage page = new PDPage();
			doc.addPage(page);
			PDPageContentStream cs = new PDPageContentStream(doc, page);
			cs.beginText();
			cs.setFont(PDType1Font.HELVETICA, 12);
			cs.newLineAtOffset(72, 700);
			cs.showText("A letter with a text layer, written by an office program.");
			cs.endText();
			cs.close();
			doc.save(file);
		}
		finally {
			doc.close();
		}
	}

	/** A PDF with only an image of the page, like from a scanner without OCR. */
	private static void writeScanPdf(File file) throws Exception {
		PDDocument doc = new PDDocument();
		try {
			PDPage page = new PDPage();
			doc.addPage(page);
			PDImageXObject image = LosslessFactory.createFromImage(doc, textImage());
			PDPageContentStream cs = new PDPageContentStream(doc, page);
			cs.drawImage(image, 36, 500, 540, 174);
			cs.close();
			doc.save(file);
		}
		finally {
			doc.close();
		}
	}

}
