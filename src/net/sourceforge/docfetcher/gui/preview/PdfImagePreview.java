/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui.preview;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.gui.CustomBorderComposite;
import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.gui.ToolItemFactory;

/**
 * A page-based PDF viewer that renders pages to images using PDFBox. This is
 * the fallback used when the embedded browser has no built-in PDF viewer.
 */
final class PdfImagePreview extends ToolBarForm {

	public final Event<Void> evtHideInSystemTray = new Event<Void>();

	private static final double ZOOM_STEP = 1.25;
	private static final double MIN_SCALE = 0.25;
	private static final double MAX_SCALE = 6.0;

	@NotNull private ToolItem prevBt;
	@NotNull private ToolItem nextBt;
	@NotNull private ToolItem zoomInBt;
	@NotNull private ToolItem zoomOutBt;
	@NotNull private ToolItem launchBt;
	@NotNull private Label pageLabel;
	@NotNull private ScrolledComposite scrollComp;
	@NotNull private Canvas canvas;

	// GUI thread only
	@Nullable private PDDocument document;
	@Nullable private File documentFile;
	@Nullable private Image pageImage;
	private int pageCount = 0;
	private int pageIndex = 0;
	private double scale = 0; // 0 = fit to width on next render
	private long renderRequest = 0;

	private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, PdfImagePreview.class.getName() + " (renderer)");
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY);
			return t;
		}
	});

	public PdfImagePreview(@NotNull Composite parent) {
		super(parent);
		addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				clear();
				renderExecutor.shutdownNow();
			}
		});
	}

	@NotNull
	protected Control createToolBar(@NotNull Composite parent) {
		CustomBorderComposite comp = new CustomBorderComposite(parent);
		int margin = Util.IS_WINDOWS ? 2 : 0;
		comp.setLayout(UtilGui.createGridLayout(3, false, margin, 0));

		ToolBar toolBar = new ToolBar(comp, SWT.FLAT);
		toolBar.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
		ToolItemFactory tif = new ToolItemFactory(toolBar);
		tif.enabled(false);

		prevBt = tif.image(Img.ARROW_LEFT.get()).toolTip(Msg.prev_page.get())
				.listener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						showPage(pageIndex - 1);
					}
				}).create();
		nextBt = tif.image(Img.ARROW_RIGHT.get()).toolTip(Msg.next_page.get())
				.listener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						showPage(pageIndex + 1);
					}
				}).create();
		zoomInBt = tif.image(Img.MAXIMIZE.get()).toolTip(Msg.zoom_in.get())
				.listener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						zoom(ZOOM_STEP);
					}
				}).create();
		zoomOutBt = tif.image(Img.MINIMIZE.get()).toolTip(Msg.zoom_out.get())
				.listener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						zoom(1 / ZOOM_STEP);
					}
				}).create();
		launchBt = tif.image(Img.WINDOW.get()).toolTip(Msg.browser_launch_external.get())
				.listener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						if (documentFile == null)
							return;
						UtilGui.launch(documentFile);
						if (SettingsConf.Bool.HideOnOpen.get())
							evtHideInSystemTray.fire(null);
					}
				}).create();

		pageLabel = new Label(comp, SWT.NONE);
		pageLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));

		return comp;
	}

	@NotNull
	protected Control createContents(@NotNull Composite parent) {
		scrollComp = new ScrolledComposite(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		scrollComp.setExpandHorizontal(true);
		scrollComp.setExpandVertical(true);
		scrollComp.getVerticalBar().setIncrement(40);
		scrollComp.getHorizontalBar().setIncrement(40);

		canvas = new Canvas(scrollComp, SWT.DOUBLE_BUFFERED);
		canvas.setBackground(getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		canvas.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				if (pageImage == null)
					return;
				Rectangle imgBounds = pageImage.getBounds();
				Rectangle area = canvas.getClientArea();
				// Center the page horizontally if it's narrower than the view
				int x = Math.max(0, (area.width - imgBounds.width) / 2);
				e.gc.drawImage(pageImage, x, 0);
			}
		});
		scrollComp.setContent(canvas);
		return scrollComp;
	}

	/**
	 * Opens the given PDF file and shows its first page. Must be called from
	 * the GUI thread; the actual loading and rendering happens in a background
	 * thread. Errors are reported via the given handler.
	 */
	public void setFile(@NotNull final File file, @NotNull final ErrorHandler errorHandler) {
		Util.checkNotNull(file, errorHandler);
		UtilGui.assertSwtThread();
		clear();
		documentFile = file;
		launchBt.setEnabled(true);
		final long request = ++renderRequest;

		renderExecutor.execute(new Runnable() {
			public void run() {
				final PDDocument doc;
				try {
					doc = PDDocument.load(file);
				}
				catch (IOException e) {
					errorHandler.handleError(e.getMessage());
					return;
				}
				catch (RuntimeException e) {
					errorHandler.handleError(e.toString());
					return;
				}
				boolean accepted = UtilGui.runSwtSafe(PdfImagePreview.this, new Runnable() {
					public void run() {
						if (request != renderRequest) {
							closeQuietly(doc);
							return;
						}
						document = doc;
						pageCount = doc.getNumberOfPages();
						pageIndex = 0;
						scale = 0;
						showPage(0);
					}
				});
				if (!accepted)
					closeQuietly(doc);
			}
		});
	}

	public void clear() {
		UtilGui.assertSwtThread();
		renderRequest++;
		if (document != null) {
			final PDDocument doc = document;
			document = null;
			// Close in the render thread to avoid racing with a running render
			renderExecutor.execute(new Runnable() {
				public void run() {
					closeQuietly(doc);
				}
			});
		}
		documentFile = null;
		pageCount = 0;
		pageIndex = 0;
		scale = 0;
		disposePageImage();
		if (!isDisposed()) {
			pageLabel.setText("");
			updateButtons();
			scrollComp.setMinSize(0, 0);
			canvas.redraw();
		}
	}

	private void showPage(int index) {
		if (document == null || index < 0 || index >= pageCount)
			return;
		pageIndex = index;
		pageLabel.setText(Msg.page_of.format(pageIndex + 1, pageCount));
		updateButtons();
		render();
	}

	private void zoom(double factor) {
		if (document == null)
			return;
		if (scale == 0)
			scale = computeFitToWidthScale();
		scale = Util.clamp(scale * factor, MIN_SCALE, MAX_SCALE);
		render();
	}

	private double computeFitToWidthScale() {
		if (document == null)
			return 1.0;
		try {
			PDPage page = document.getPage(pageIndex);
			PDRectangle box = page.getCropBox();
			float widthPt = box.getWidth();
			int rotation = page.getRotation();
			if (rotation == 90 || rotation == 270)
				widthPt = box.getHeight();
			int viewWidth = scrollComp.getClientArea().width - 4;
			if (viewWidth <= 0 || widthPt <= 0)
				return 1.0;
			return Util.clamp(viewWidth / widthPt, MIN_SCALE, MAX_SCALE);
		}
		catch (RuntimeException e) {
			return 1.0;
		}
	}

	private void render() {
		if (document == null)
			return;
		if (scale == 0)
			scale = computeFitToWidthScale();
		final PDDocument doc = document;
		final int index = pageIndex;
		final float renderScale = (float) scale;
		final long request = ++renderRequest;

		renderExecutor.execute(new Runnable() {
			public void run() {
				final ImageData imageData;
				try {
					PDFRenderer renderer = new PDFRenderer(doc);
					BufferedImage image = renderer.renderImage(index, renderScale, ImageType.RGB);
					imageData = ImagePreview.toImageData(image);
				}
				catch (Throwable e) {
					// Rendering errors for single pages are not fatal; leave
					// the previous page image in place
					Util.printErr(e);
					return;
				}
				UtilGui.runSwtSafe(PdfImagePreview.this, new Runnable() {
					public void run() {
						if (request != renderRequest)
							return;
						disposePageImage();
						pageImage = new Image(getDisplay(), imageData);
						Rectangle b = pageImage.getBounds();
						scrollComp.setMinSize(b.width, b.height);
						scrollComp.setOrigin(new Point(0, 0));
						canvas.redraw();
					}
				});
			}
		});
	}

	private void updateButtons() {
		boolean hasDoc = document != null && pageCount > 0;
		prevBt.setEnabled(hasDoc && pageIndex > 0);
		nextBt.setEnabled(hasDoc && pageIndex < pageCount - 1);
		zoomInBt.setEnabled(hasDoc);
		zoomOutBt.setEnabled(hasDoc);
		launchBt.setEnabled(documentFile != null);
	}

	private void disposePageImage() {
		if (pageImage != null) {
			pageImage.dispose();
			pageImage = null;
		}
	}

	private static void closeQuietly(@Nullable PDDocument doc) {
		if (doc == null)
			return;
		try {
			doc.close();
		}
		catch (IOException e) {
			// Ignore
		}
	}

	/** Receives error messages from the background loading thread. */
	interface ErrorHandler {
		void handleError(@NotNull String message);
	}

}
