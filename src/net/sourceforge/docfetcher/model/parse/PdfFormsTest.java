/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.parse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.junit.Test;

import net.sourceforge.docfetcher.model.index.IndexingConfig;
import net.sourceforge.docfetcher.util.AppUtil;

/**
 * Tests that the values of filled PDF form fields are part of the text, like
 * the rent in a rental contract form whose page only shows empty lines.
 */
public final class PdfFormsTest {

	/**
	 * A contract form: the page says 'Die Grundmiete beträgt monatlich ......',
	 * the rent is in a field. Fields: filled rent (with a description), filled
	 * name (without), an empty field and a checked box.
	 */
	public static File writeForm(File file, String rent) throws Exception {
		PDDocument pdf = new PDDocument();
		try {
			PDPage page = new PDPage(PDRectangle.A4);
			pdf.addPage(page);
			PDPageContentStream content = new PDPageContentStream(pdf, page);
			content.beginText();
			content.setFont(PDType1Font.HELVETICA, 12);
			content.newLineAtOffset(72, 700);
			content.showText("Wohnraum-Mietvertrag. Die Grundmiete betraegt monatlich ......");
			content.endText();
			content.close();

			PDAcroForm form = new PDAcroForm(pdf);
			pdf.getDocumentCatalog().setAcroForm(form);
			addField(form, page, new PDTextField(form), "Text1", "Grundmiete monatlich", rent, 650);
			addField(form, page, new PDTextField(form), "Mieter", null, "Henker, Anna", 620);
			addField(form, page, new PDTextField(form), "Text3", "Kaution", null, 590);
			PDCheckBox box = new PDCheckBox(form);
			addField(form, page, box, "Kündigungsausschluss", null, null, 560);
			COSDictionary normal = new COSDictionary();
			normal.setItem(COSName.getPDFName("Yes"), pdf.getDocument().createCOSStream());
			normal.setItem(COSName.Off, pdf.getDocument().createCOSStream());
			COSDictionary appearance = new COSDictionary();
			appearance.setItem(COSName.N, normal);
			box.getWidgets().get(0).getCOSObject().setItem(COSName.AP, appearance);
			box.getCOSObject().setItem(COSName.V, COSName.getPDFName("Yes"));
			pdf.save(file);
		}
		finally {
			pdf.close();
		}
		return file;
	}

	private static void addField(PDAcroForm form, PDPage page, PDField field, String name,
			String description, String value, float y) throws Exception {
		field.setPartialName(name);
		if (description != null)
			field.setAlternateFieldName(description);
		PDAnnotationWidget widget = new PDAnnotationWidget();
		widget.setRectangle(new PDRectangle(300, y, 150, 18));
		widget.setPage(page);
		widget.getCOSObject().setItem(COSName.PARENT, field);
		page.getAnnotations().add(widget);
		List<PDAnnotationWidget> widgets = new ArrayList<PDAnnotationWidget>();
		widgets.add(widget);
		((org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField) field).setWidgets(widgets);
		form.getFields().add(field);
		// Set the value directly, without generating an appearance
		if (value != null)
			field.getCOSObject().setString(COSName.V, value);
	}

	@Test
	public void filledFieldsArePartOfTheText() throws Exception {
		AppUtil.Const.autoInit();
		File file = writeForm(File.createTempFile("docfetcher-form", ".pdf"), "850,00 EUR");
		try {
			java.io.InputStream in = new java.io.FileInputStream(file);
			String text;
			try {
				text = new PdfParser().renderText(in, file.getName());
			}
			finally {
				in.close();
			}
			assertTrue(text, text.contains("Grundmiete betraegt monatlich"));
			assertTrue("With the description: " + text, text.contains("Grundmiete monatlich: 850,00 EUR"));
			assertTrue("Without one, the name: " + text, text.contains("Mieter: Henker, Anna"));
			assertTrue("Checked box: " + text, text.contains("Kündigungsausschluss: [x]"));
			assertFalse("Empty fields are left out: " + text, text.contains("Kaution"));
			assertTrue(PdfForms.hasFilledFields(file));
		}
		finally {
			file.delete();
		}
	}

	@Test
	public void thePreviewShowsTheFieldsOnAnExtraPage() throws Exception {
		AppUtil.Const.autoInit();
		File file = writeForm(File.createTempFile("docfetcher-form", ".pdf"), "850,00 EUR");
		try {
			final List<String> pages = new ArrayList<String>();
			new PagingPdfParser(file, new PageHandler() {
				public boolean handlePage(String pageText) {
					pages.add(pageText);
					return false;
				}
			}).run();
			assertEquals(pages.toString(), 2, pages.size());
			assertTrue(pages.get(1), pages.get(1).contains("Grundmiete monatlich: 850,00 EUR"));
		}
		finally {
			file.delete();
		}
	}

	@Test
	public void pdfsWithoutFormsHaveNoFields() throws Exception {
		File file = File.createTempFile("docfetcher-form", ".pdf");
		try {
			PDDocument pdf = new PDDocument();
			pdf.addPage(new PDPage());
			pdf.save(file);
			pdf.close();
			assertFalse(PdfForms.hasFilledFields(file));
			assertFalse("Not a PDF", PdfForms.hasFilledFields(new File(file.getPath() + ".missing")));
		}
		finally {
			file.delete();
		}
		new IndexingConfig(); // Loaded like in the other parser tests
	}

}
