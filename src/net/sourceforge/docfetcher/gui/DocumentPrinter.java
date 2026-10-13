/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.printing.PrintDialog;
import org.eclipse.swt.printing.Printer;
import org.eclipse.swt.printing.PrinterData;
import org.eclipse.swt.widgets.Shell;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.model.ocr.ImagePages;
import net.sourceforge.docfetcher.model.pdf.SeparatorSheet;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.model.pdf.PdfFiles;

/**
 * Prints documents. PDF and image files are printed by DocFetcher itself,
 * with one print dialog (printer, page range, copies) for all of them: the
 * pages are rendered like in the preview and printed in their real size, or
 * shrunk to the printable area if they are bigger. Other files are handed to
 * the program that is registered for printing them, e.g. the word processor.
 */
final class DocumentPrinter {

	private static final Set<String> IMAGE_EXTENSIONS = new HashSet<String>(Arrays.asList(
		"png", "jpg", "jpeg", "gif", "bmp", "tif", "tiff"));

	/** Pages are rendered with at most this resolution, which is plenty for print. */
	private static final int MAX_RENDER_DPI = 300;

	private DocumentPrinter() {}

	/** Returns true if DocFetcher prints the file itself (PDF and images). */
	static boolean isPrintedByDocFetcher(@NotNull File file) {
		String ext = Util.getExtension(file.getName()).toLowerCase();
		return ext.equals("pdf") || IMAGE_EXTENSIONS.contains(ext);
	}

	/**
	 * Prints the given files; must be called from the GUI thread. The print
	 * dialog is only shown if there are PDF or image files among them; the
	 * printing itself runs in the background.
	 */
	public static void print(@NotNull Shell shell, @NotNull List<File> files) {
		final List<File> own = new ArrayList<File>();
		final List<File> external = new ArrayList<File>();
		for (File file : files)
			(isPrintedByDocFetcher(file) ? own : external).add(file);

		PrinterData printerData = null;
		if (!own.isEmpty()) {
			PrintDialog dialog = new PrintDialog(shell, SWT.NONE);
			dialog.setText(Msg.print_title.get());
			dialog.setScope(PrinterData.ALL_PAGES);
			printerData = dialog.open();
			if (printerData == null)
				return; // Cancelled; don't print the other files either
		}

		final PrinterData finalData = printerData;
		Thread thread = new Thread(DocumentPrinter.class.getName()) {
			public void run() {
				if (finalData != null)
					printFiles(finalData, own);
				for (File file : external)
					printWithProgram(file);
			}
		};
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * Prints the given PDF and image files as one print job and returns true
	 * on success; errors are shown to the user. May be called from any thread.
	 */
	static boolean printFiles(@NotNull PrinterData data, @NotNull List<File> files) {
		Printer printer = new Printer(data);
		try {
			String jobName = files.size() == 1
				? files.get(0).getName()
				: Msg.print_job_name.format(files.size());
			if (!printer.startJob(jobName)) {
				AppUtil.showError(Msg.print_failed.format(jobName), true, true);
				return false;
			}
			try {
				for (File file : files) {
					try {
						if (Util.getExtension(file.getName()).equalsIgnoreCase("pdf"))
							printPdf(printer, data, file);
						else
							printImage(printer, data, file);
					}
					catch (IOException e) {
						AppUtil.showError(Msg.print_failed.format(
							file.getName() + ": " + e.getMessage()), true, true);
						return false;
					}
				}
			}
			finally {
				printer.endJob();
			}
			return true;
		}
		finally {
			printer.dispose();
		}
	}

	/** Returns true if the 1-based page is in the page range of the dialog. */
	private static boolean isInRange(@NotNull PrinterData data, int page) {
		if (data.scope != PrinterData.PAGE_RANGE)
			return true;
		return page >= data.startPage && page <= data.endPage;
	}

	private static void printPdf(	@NotNull Printer printer,
									@NotNull PrinterData data,
									@NotNull File file) throws IOException {
		int dpi = getRenderDpi(printer);
		PDDocument pdf = PdfFiles.load(file);
		try {
			PDFRenderer renderer = new PDFRenderer(pdf);
			for (int i = 0; i < pdf.getNumberOfPages(); i++) {
				if (!isInRange(data, i + 1))
					continue;
				PDRectangle box = pdf.getPage(i).getCropBox();
				int rotation = pdf.getPage(i).getRotation();
				boolean turned = rotation == 90 || rotation == 270;
				double width = (turned ? box.getHeight() : box.getWidth()) / 72.0;
				double height = (turned ? box.getWidth() : box.getHeight()) / 72.0;
				BufferedImage image = renderer.renderImage(i, dpi / 72f, ImageType.RGB);
				printPage(printer, image, width, height);
			}
		}
		finally {
			pdf.close();
		}
	}

	private static void printImage(	@NotNull Printer printer,
									@NotNull PrinterData data,
									@NotNull File file) throws IOException {
		List<ImagePages.Page> pages = ImagePages.read(file);
		for (int i = 0; i < pages.size(); i++) {
			if (!isInRange(data, i + 1))
				continue;
			ImagePages.Page page = pages.get(i);
			int dpi = Math.max(1, page.dpi);
			printPage(printer, page.image,
				page.image.getWidth() / (double) dpi,
				page.image.getHeight() / (double) dpi);
		}
	}

	private static int getRenderDpi(@NotNull Printer printer) {
		return Math.min(MAX_RENDER_DPI, printer.getDPI().y);
	}

	/** Prints one page whose real size is given in inches. */
	private static void printPage(	@NotNull Printer printer,
									@NotNull BufferedImage image,
									double widthInch,
									double heightInch) {
		Point dpi = printer.getDPI();
		Rectangle bounds = computeBounds(
			widthInch * dpi.x, heightInch * dpi.y, printer.getClientArea());
		Image swtImage = new Image(printer, UtilGui.toImageData(image));
		try {
			printer.startPage();
			GC gc = new GC(printer);
			try {
				gc.setInterpolation(SWT.HIGH);
				gc.drawImage(swtImage, 0, 0, image.getWidth(), image.getHeight(),
					bounds.x, bounds.y, bounds.width, bounds.height);
			}
			finally {
				gc.dispose();
			}
			printer.endPage();
		}
		finally {
			swtImage.dispose();
		}
	}

	/**
	 * Returns where a page of the given size (in printer pixels) is printed
	 * in the printable area: in its real size, or shrunk to fit if it is
	 * bigger, keeping the proportions, and centered.
	 */
	@NotNull
	static Rectangle computeBounds(double width, double height, @NotNull Rectangle area) {
		double scale = Math.min(1, Math.min(area.width / width, area.height / height));
		int w = (int) Math.round(width * scale);
		int h = (int) Math.round(height * scale);
		return new Rectangle(area.x + (area.width - w) / 2, area.y + (area.height - h) / 2, w, h);
	}

	/**
	 * Lets the program registered for the file print it on the default
	 * printer. An error is shown if there is no such program.
	 */
	private static void printWithProgram(@NotNull File file) {
		try {
			if (!Desktop.isDesktopSupported()
					|| !Desktop.getDesktop().isSupported(Desktop.Action.PRINT))
				throw new UnsupportedOperationException();
			Desktop.getDesktop().print(file);
		}
		catch (Exception e) {
			AppUtil.showError(Msg.print_no_program.format(file.getName()), true, true);
		}
	}

	/**
	 * Prints separator sheets, which are put between the documents of a
	 * stack before scanning (see {@link SeparatorSheet}). The print dialog
	 * asks how many.
	 */
	public static void printSeparatorSheet(@NotNull Shell shell) {
		try {
			File file = File.createTempFile("docfetcher-separator-sheet", ".pdf");
			file.deleteOnExit();
			SeparatorSheet.create(file, Msg.separator_sheet_title.get(),
				Msg.separator_sheet_instruction.get());
			print(shell, Collections.singletonList(file));
		}
		catch (IOException e) {
			AppUtil.showError(Msg.print_failed.format(e.getMessage()), true, true);
		}
	}

	/** Returns the printer with the given name, or null; for tests. */
	@Nullable
	static PrinterData findPrinter(@NotNull String name) {
		for (PrinterData data : Printer.getPrinterList())
			if (data.name.equals(name))
				return data;
		return null;
	}

}
