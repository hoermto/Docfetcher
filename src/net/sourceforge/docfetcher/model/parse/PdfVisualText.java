/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.parse;

import java.io.File;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import com.google.common.io.Closeables;

import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * The text of a PDF file in visual order: line by line as it appears on the
 * page, instead of the order in which the text was drawn. Generated forms
 * (e.g. online rental contracts) draw the printed form first and the filled
 * in values afterwards, so in drawing order the values end up far from their
 * labels; in visual order 'Grundmiete 620,00 Euro' stays together. Pages
 * with several columns may get their columns mixed, though.
 */
public final class PdfVisualText {

	private PdfVisualText() {}

	/**
	 * Returns the text of the PDF file in visual order, followed by the values
	 * of filled form fields; null if the file has no text (e.g. a scan whose
	 * text wasn't stored) or can't be read.
	 */
	@Nullable
	public static String of(@NotNull File file) {
		PDDocument doc = null;
		try {
			doc = PDDocument.load(file);
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			String text = stripper.getText(doc);
			if (text.trim().isEmpty())
				return null;
			String formText = PdfForms.text(doc);
			return formText.isEmpty() ? text : text + "\n" + formText;
		}
		catch (Throwable e) {
			return null; // Encrypted, broken, ...: the caller uses the normal text
		}
		finally {
			Closeables.closeQuietly(doc);
		}
	}

}
