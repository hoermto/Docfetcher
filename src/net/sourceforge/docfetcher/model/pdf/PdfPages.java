/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.pdf;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * Merging and splitting of PDF files, e.g. of a stack of documents that was
 * scanned in one go. The pages are copied as they are, including the text
 * layer added by the text recognition.
 */
public final class PdfPages {

	/**
	 * The pages of one document of a split, 0-based and in ascending order.
	 * Usually a range from {@code start} to {@code end} (exclusive), but it
	 * may have gaps where pages are left out, e.g. a blank back side.
	 */
	public static final class Range {
		public final int start;
		public final int end;
		private final List<Integer> pages;

		public Range(int start, int end) {
			Util.checkThat(start >= 0 && end > start);
			List<Integer> list = new ArrayList<Integer>(end - start);
			for (int i = start; i < end; i++)
				list.add(i);
			this.start = start;
			this.end = end;
			this.pages = Collections.unmodifiableList(list);
		}

		/** A document made of the given pages (ascending, not empty). */
		public Range(@NotNull List<Integer> pages) {
			Util.checkThat(!pages.isEmpty());
			this.pages = Collections.unmodifiableList(new ArrayList<Integer>(pages));
			this.start = pages.get(0);
			this.end = pages.get(pages.size() - 1) + 1;
		}

		/** The pages of the document, 0-based. */
		@NotNull
		public List<Integer> getPages() {
			return pages;
		}

		public int size() {
			return pages.size();
		}

		/** The pages as the user sees them, e.g. "3-5", "7" or "1-2, 4" (1-based). */
		@NotNull
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < pages.size(); ) {
				int j = i;
				while (j + 1 < pages.size() && pages.get(j + 1) == pages.get(j) + 1)
					j++;
				if (sb.length() > 0)
					sb.append(", ");
				sb.append(pages.get(i) + 1);
				if (j > i)
					sb.append('-').append(pages.get(j) + 1);
				i = j + 1;
			}
			return sb.toString();
		}

		public boolean equals(Object obj) {
			return obj instanceof Range && ((Range) obj).pages.equals(pages);
		}

		public int hashCode() {
			return pages.hashCode();
		}
	}

	/** What a page is in a split. */
	public enum Role {
		/** A new document starts with this page. */
		START,
		/** The page belongs to the document of the page before. */
		CONTINUE,
		/**
		 * The page is left out, e.g. a separator sheet or a blank page. Leaving
		 * out doesn't end the document by itself; after a separator, the next
		 * page is marked as START.
		 */
		OMIT
	}

	/** What is known about a page for splitting. */
	public static final class PageInfo {
		/** The text of the page, e.g. from the text layer of the OCR. */
		public final String text;
		/** The page has no text and (almost) no ink. */
		public final boolean blank;
		/** The page is a separator sheet printed by DocFetcher. */
		public final boolean separatorSheet;

		public PageInfo(@NotNull String text, boolean blank, boolean separatorSheet) {
			this.text = text;
			this.blank = blank;
			this.separatorSheet = separatorSheet;
		}

		/** Blank pages and separator sheets are left out of the documents. */
		public boolean isSeparator() {
			return blank || separatorSheet;
		}
	}

	/*
	 * A page counts as blank if less than this share of its area (without
	 * the margins, where scanners leave shadows) is dark and it has no text.
	 * At the low resolution used for this, single specks of dust are too
	 * light to count, while one short line of text is well above the limit.
	 */
	private static final double BLANK_INK_RATIO = 0.0003;
	private static final double BLANK_MARGIN = 0.06;
	private static final int BLANK_DARK_LEVEL = 160;
	private static final float BLANK_RENDER_DPI = 40;

	private PdfPages() {}

	public static int getPageCount(@NotNull File pdf) throws IOException {
		PDDocument doc = PDDocument.load(pdf, MemoryUsageSetting.setupTempFileOnly());
		try {
			return doc.getNumberOfPages();
		}
		finally {
			doc.close();
		}
	}

	/** Writes the pages of the given PDF files, in this order, into {@code output}. */
	public static void merge(@NotNull List<File> inputs, @NotNull File output) throws IOException {
		Util.checkThat(!inputs.isEmpty());
		PDFMergerUtility merger = new PDFMergerUtility();
		for (File input : inputs)
			merger.addSource(input);
		merger.setDestinationFileName(output.getPath());
		try {
			merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly());
		}
		catch (IOException e) {
			output.delete(); // Don't leave a half-written file behind
			throw e;
		}
	}

	/**
	 * Writes each range of pages of {@code input} into the output file at
	 * the same position. On failure, the outputs written so far are deleted.
	 */
	public static void split(	@NotNull File input,
								@NotNull List<Range> ranges,
								@NotNull List<File> outputs) throws IOException {
		Util.checkThat(ranges.size() == outputs.size());
		PDDocument source = PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly());
		List<File> written = new ArrayList<File>();
		try {
			for (int i = 0; i < ranges.size(); i++) {
				Range range = ranges.get(i);
				if (range.end > source.getNumberOfPages())
					throw new IOException("Page " + range.end + " > " + source.getNumberOfPages());
				PDDocument part = new PDDocument();
				try {
					part.getDocument().setVersion(source.getVersion());
					part.setDocumentInformation(source.getDocumentInformation());
					for (int page : range.getPages()) {
						PDPage imported = part.importPage(source.getPage(page));
						// Inherited attributes are not copied by importPage
						imported.setCropBox(source.getPage(page).getCropBox());
						imported.setMediaBox(source.getPage(page).getMediaBox());
						imported.setResources(source.getPage(page).getResources());
						imported.setRotation(source.getPage(page).getRotation());
						detachFromSource(imported);
					}
					written.add(outputs.get(i));
					part.save(outputs.get(i));
				}
				finally {
					part.close();
				}
			}
		}
		catch (IOException e) {
			for (File file : written)
				file.delete();
			throw e;
		}
		finally {
			source.close();
		}
	}

	/**
	 * Cuts the references of an imported page to the rest of the source
	 * document. Annotations refer to their page (/P), form fields to the field
	 * tree of the whole form (/Parent), links to other pages, pop-ups and
	 * replies to other annotations; when the part is saved, PDFBox would
	 * follow these references and write the whole source document, with all
	 * its images, into each part. The annotations are copied, so the source
	 * stays as it is: form fields keep their appearance, web links keep
	 * working, links into the source are dropped.
	 */
	static void detachFromSource(@NotNull PDPage page) throws IOException {
		page.getCOSObject().removeItem(COSName.B); // Article threads across pages
		List<PDAnnotation> annotations = page.getAnnotations();
		if (annotations.isEmpty())
			return;
		List<PDAnnotation> copies = new ArrayList<PDAnnotation>(annotations.size());
		for (PDAnnotation annotation : annotations) {
			COSDictionary copy = new COSDictionary(annotation.getCOSObject());
			copy.setItem(COSName.P, page);
			copy.removeItem(COSName.PARENT);
			copy.removeItem(COSName.POPUP);
			copy.removeItem(COSName.getPDFName("IRT"));
			if (COSName.LINK.equals(copy.getCOSName(COSName.SUBTYPE))) {
				copy.removeItem(COSName.DEST);
				COSDictionary action = copy.getCOSDictionary(COSName.A);
				if (action != null && !COSName.URI.equals(action.getCOSName(COSName.S)))
					copy.removeItem(COSName.A);
			}
			copies.add(PDAnnotation.createAnnotation(copy));
		}
		page.setAnnotations(copies);
	}

	/** One document per page. */
	@NotNull
	public static List<Range> eachPage(int pageCount) {
		List<Range> ranges = new ArrayList<Range>(pageCount);
		for (int i = 0; i < pageCount; i++)
			ranges.add(new Range(i, i + 1));
		return ranges;
	}

	/**
	 * Splits before each of the given 1-based page numbers, i.e. a new
	 * document starts at each of them. Numbers outside 2..pageCount are
	 * ignored.
	 */
	@NotNull
	public static List<Range> atPages(int pageCount, @NotNull Iterable<Integer> startPages) {
		TreeSet<Integer> starts = new TreeSet<Integer>();
		starts.add(0);
		for (int page : startPages)
			if (page >= 2 && page <= pageCount)
				starts.add(page - 1);
		List<Range> ranges = new ArrayList<Range>();
		Integer start = starts.first();
		for (Integer next : starts.tailSet(start, false)) {
			ranges.add(new Range(start, next));
			start = next;
		}
		if (start < pageCount)
			ranges.add(new Range(start, pageCount));
		return ranges;
	}

	/**
	 * Returns the documents for the given roles of the pages: a document
	 * starts at each START page (and at the first page that is kept), pages
	 * that are left out are skipped without ending the document.
	 */
	@NotNull
	public static List<Range> fromRoles(@NotNull Role[] roles) {
		List<Range> ranges = new ArrayList<Range>();
		List<Integer> current = null;
		for (int i = 0; i < roles.length; i++) {
			if (roles[i] == Role.OMIT)
				continue;
			if (roles[i] == Role.START || current == null) {
				if (current != null)
					ranges.add(new Range(current));
				current = new ArrayList<Integer>();
			}
			current.add(i);
		}
		if (current != null)
			ranges.add(new Range(current));
		return ranges;
	}

	/**
	 * Reads the text of each page and finds the blank pages (no text and
	 * (almost) no ink outside the margins, for which pages without text are
	 * rendered at a low resolution) and the separator sheets.
	 */
	@NotNull
	public static List<PageInfo> analyzePages(@NotNull File pdf) throws IOException {
		PDDocument doc = PDDocument.load(pdf, MemoryUsageSetting.setupTempFileOnly());
		try {
			int count = doc.getNumberOfPages();
			List<PageInfo> pages = new ArrayList<PageInfo>(count);
			PDFRenderer renderer = new PDFRenderer(doc);
			PDFTextStripper stripper = new PDFTextStripper();
			for (int i = 0; i < count; i++) {
				stripper.setStartPage(i + 1);
				stripper.setEndPage(i + 1);
				String text = stripper.getText(doc).trim();
				boolean blank = false;
				if (text.length() <= 2) { // No text, e.g. from the text recognition
					BufferedImage image = renderer.renderImage(i, BLANK_RENDER_DPI / 72f, ImageType.GRAY);
					blank = isBlank(image);
				}
				pages.add(new PageInfo(text, blank, SeparatorSheet.isSeparator(text)));
			}
			return pages;
		}
		finally {
			doc.close();
		}
	}

	/** Returns true if the image has (almost) no dark pixels outside its margins. */
	static boolean isBlank(@NotNull BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		int x0 = (int) (width * BLANK_MARGIN);
		int y0 = (int) (height * BLANK_MARGIN);
		int x1 = width - x0;
		int y1 = height - y0;
		if (x1 <= x0 || y1 <= y0)
			return false;
		int dark = 0;
		for (int y = y0; y < y1; y++)
			for (int x = x0; x < x1; x++)
				if ((image.getRGB(x, y) & 0xFF) < BLANK_DARK_LEVEL)
					dark++;
		return dark < BLANK_INK_RATIO * (x1 - x0) * (y1 - y0);
	}

	/**
	 * Returns the files for the parts of a split: {@code name_1.pdf},
	 * {@code name_2.pdf}, ... next to the original. If one of them exists,
	 * a further number is added to all of them, e.g. {@code name-2_1.pdf}.
	 */
	@NotNull
	public static List<File> getPartFiles(@NotNull File original, int count) {
		File dir = original.getParentFile();
		String base = Util.splitFilename(original.getName())[0];
		for (int attempt = 1; ; attempt++) {
			String prefix = attempt == 1 ? base : base + "-" + attempt;
			List<File> files = new ArrayList<File>(count);
			boolean free = true;
			for (int i = 1; i <= count && free; i++) {
				File file = new File(dir, prefix + "_" + i + ".pdf");
				free = !file.exists();
				files.add(file);
			}
			if (free)
				return Collections.unmodifiableList(files);
		}
	}

}
