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
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField;

import com.google.common.io.Closeables;

import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.model.pdf.PdfFiles;

/**
 * The values filled into the form fields of a PDF file, e.g. the rent in a
 * rental contract form. The page text of such forms only contains the empty
 * lines ('beträgt monatlich ......'); the values are stored in the fields and
 * are added to the text here, one field per line: 'label: value'. The label
 * is the field's description (tooltip) if it has one, else its name.
 */
public final class PdfForms {

	/**
	 * Version of the form text. Indexes with an older version parse their
	 * PDF files with filled form fields once again on the next update (see
	 * TreeIndex); version 1 added the form fields.
	 */
	public static final int VERSION = 1;

	private PdfForms() {}

	/** Returns the filled form fields of the document as text, or an empty string. */
	@NotNull
	public static String text(@NotNull PDDocument doc) {
		StringBuilder sb = new StringBuilder();
		try {
			PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
			if (form == null)
				return "";
			for (PDField field : form.getFieldTree()) {
				try {
					String value = valueOf(field);
					if (value == null)
						continue;
					String label = field.getAlternateFieldName();
					if (label == null || label.trim().isEmpty())
						label = field.getPartialName();
					sb.append(label == null ? "" : label.trim() + ": ").append(value).append('\n');
				}
				catch (RuntimeException e) {
					// One odd field must not hide the others
				}
			}
		}
		catch (RuntimeException e) {
			// A broken form must not spoil the text of the pages
		}
		return sb.toString();
	}

	/** Returns true if the PDF file has at least one filled form field. */
	public static boolean hasFilledFields(@NotNull File file) {
		PDDocument doc = null;
		try {
			doc = PdfFiles.load(file);
			PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
			if (form == null)
				return false;
			for (PDField field : form.getFieldTree()) {
				try {
					if (valueOf(field) != null)
						return true;
				}
				catch (RuntimeException e) {
					// Try the next field
				}
			}
			return false;
		}
		catch (Throwable e) {
			return false; // Encrypted, broken, inside an archive, ...
		}
		finally {
			Closeables.closeQuietly(doc);
		}
	}

	/** The value of a filled field, '[x]' for a checked box; null if empty or not a value field. */
	private static String valueOf(@NotNull PDField field) {
		if (!(field instanceof PDTerminalField))
			return null;
		if (field instanceof PDCheckBox)
			return ((PDCheckBox) field).isChecked() ? "[x]" : null;
		String value = field.getValueAsString();
		if (value == null)
			return null;
		value = value.trim();
		if (value.isEmpty() || (field instanceof PDButton && value.equals("Off")))
			return null;
		return value;
	}

}
