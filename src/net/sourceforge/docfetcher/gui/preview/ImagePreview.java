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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
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
import net.sourceforge.docfetcher.model.ocr.ImagePages;
import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.gui.ToolItemFactory;

/**
 * Preview for image files (PNG, JPEG, GIF, BMP, TIFF), e.g. scanned
 * documents. The image is fitted to the width of the pane initially and can
 * be zoomed in and out.
 */
final class ImagePreview extends ToolBarForm {

	public final Event<Void> evtHideInSystemTray = new Event<Void>();

	private static final double ZOOM_STEP = 1.25;
	private static final double MIN_SCALE = 0.05;
	private static final double MAX_SCALE = 8.0;

	@NotNull private ToolItem prevBt;
	@NotNull private ToolItem nextBt;
	@NotNull private ToolItem zoomInBt;
	@NotNull private ToolItem zoomOutBt;
	@NotNull private ToolItem launchBt;
	@NotNull private Label infoLabel;
	@NotNull private ScrolledComposite scrollComp;
	@NotNull private Canvas canvas;

	// GUI thread only
	@Nullable private File imageFile;
	@Nullable private Image image;
	// The loaded pages (converted to SWT image data on demand)
	private List<BufferedImage> pages = Collections.emptyList();
	@Nullable private ImageData swtPage; // Single page loaded by SWT itself
	private int pageIndex = 0;
	private double scale = 0; // 0 = fit to width
	private long loadRequest = 0;

	public ImagePreview(@NotNull Composite parent) {
		super(parent);
		addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				clear();
			}
		});
	}

	@NotNull
	protected Control createToolBar(@NotNull Composite parent) {
		CustomBorderComposite comp = new CustomBorderComposite(parent);
		int margin = Util.IS_WINDOWS ? 2 : 0;
		comp.setLayout(UtilGui.createGridLayout(2, false, margin, 0));

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
						if (imageFile == null)
							return;
						UtilGui.launch(imageFile);
						if (SettingsConf.Bool.HideOnOpen.get())
							evtHideInSystemTray.fire(null);
					}
				}).create();

		infoLabel = new Label(comp, SWT.NONE);
		infoLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));

		return comp;
	}

	@NotNull
	protected Control createContents(@NotNull Composite parent) {
		scrollComp = new ScrolledComposite(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		scrollComp.setExpandHorizontal(true);
		scrollComp.setExpandVertical(true);
		scrollComp.getVerticalBar().setIncrement(40);
		scrollComp.getHorizontalBar().setIncrement(40);
		scrollComp.addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent e) {
				// Keep the image fitted to the width while not zoomed
				if (scale == 0)
					updateSize();
			}
		});

		canvas = new Canvas(scrollComp, SWT.DOUBLE_BUFFERED);
		canvas.setBackground(getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		canvas.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				if (image == null)
					return;
				Rectangle b = image.getBounds();
				Point size = getScaledSize();
				Rectangle area = canvas.getClientArea();
				// Center the image horizontally if it's narrower than the view
				int x = Math.max(0, (area.width - size.x) / 2);
				e.gc.setInterpolation(SWT.HIGH);
				e.gc.drawImage(image, 0, 0, b.width, b.height, x, 0, size.x, size.y);
			}
		});
		scrollComp.setContent(canvas);
		return scrollComp;
	}

	/**
	 * Loads and shows the given image file. Must be called from the GUI
	 * thread; the actual loading happens in a background thread. Errors are
	 * reported via the given handler.
	 */
	public void setFile(@NotNull final File file, @NotNull final ErrorHandler errorHandler) {
		Util.checkNotNull(file, errorHandler);
		UtilGui.assertSwtThread();
		clear();
		imageFile = file;
		launchBt.setEnabled(true);
		final long request = ++loadRequest;

		Thread thread = new Thread(ImagePreview.class.getName() + " (loader)") {
			public void run() {
				final Loaded data;
				try {
					data = load(file);
				}
				catch (Throwable e) {
					if (e instanceof OutOfMemoryError)
						errorHandler.handleError(Msg.out_of_memory.get());
					else
						errorHandler.handleError(e.toString());
					return;
				}
				UtilGui.runSwtSafe(ImagePreview.this, new Runnable() {
					public void run() {
						if (request != loadRequest)
							return;
						pages = data.pages;
						swtPage = data.swtPage;
						showPage(0);
					}
				});
			}
		};
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	public void clear() {
		UtilGui.assertSwtThread();
		loadRequest++;
		imageFile = null;
		scale = 0;
		pages = Collections.emptyList();
		swtPage = null;
		pageIndex = 0;
		if (image != null) {
			image.dispose();
			image = null;
		}
		if (!isDisposed()) {
			infoLabel.setText("");
			updateButtons();
			scrollComp.setMinSize(0, 0);
			canvas.redraw();
		}
	}

	private int getPageCount() {
		return swtPage != null ? 1 : pages.size();
	}

	/** Shows the given page of the loaded file, fitted to the width. */
	private void showPage(int index) {
		if (index < 0 || index >= getPageCount())
			return;
		pageIndex = index;
		if (image != null)
			image.dispose();
		ImageData data = swtPage != null ? swtPage : toImageData(pages.get(index));
		image = new Image(getDisplay(), data);
		scale = 0;
		String info = Msg.image_dimensions.format(
			String.valueOf(data.width), String.valueOf(data.height));
		if (getPageCount() > 1)
			info += "   " + Msg.page_of.format(pageIndex + 1, getPageCount());
		infoLabel.setText(info);
		updateButtons();
		updateSize();
	}

	private void zoom(double factor) {
		if (image == null)
			return;
		if (scale == 0)
			scale = computeFitToWidthScale();
		scale = Util.clamp(scale * factor, MIN_SCALE, MAX_SCALE);
		updateSize();
	}

	private double computeFitToWidthScale() {
		if (image == null)
			return 1.0;
		int imageWidth = image.getBounds().width;
		int viewWidth = scrollComp.getClientArea().width - 4;
		if (viewWidth <= 0 || imageWidth <= 0)
			return 1.0;
		return Util.clamp((double) viewWidth / imageWidth, MIN_SCALE, MAX_SCALE);
	}

	@NotNull
	private Point getScaledSize() {
		if (image == null)
			return new Point(0, 0);
		Rectangle b = image.getBounds();
		double s = scale == 0 ? computeFitToWidthScale() : scale;
		return new Point(Math.max(1, (int) Math.round(b.width * s)),
			Math.max(1, (int) Math.round(b.height * s)));
	}

	private void updateSize() {
		if (image == null)
			return;
		Point size = getScaledSize();
		scrollComp.setMinSize(size.x, size.y);
		canvas.redraw();
	}

	private void updateButtons() {
		boolean hasImage = image != null;
		zoomInBt.setEnabled(hasImage);
		zoomOutBt.setEnabled(hasImage);
		prevBt.setEnabled(hasImage && pageIndex > 0);
		nextBt.setEnabled(hasImage && pageIndex < getPageCount() - 1);
		launchBt.setEnabled(imageFile != null);
	}

	/**
	 * Loads the pages of the given file. TIFF files go through the shared
	 * image reader (ImageIO with Leptonica as fallback, all pages); the other
	 * formats through SWT's image loader, which keeps colors and
	 * transparency, with the shared reader as fallback.
	 */
	/** The result of loading a file: one SWT image, or pages to convert. */
	private static final class Loaded {
		@Nullable final ImageData swtPage;
		final List<BufferedImage> pages;

		Loaded(@Nullable ImageData swtPage, @NotNull List<BufferedImage> pages) {
			this.swtPage = swtPage;
			this.pages = pages;
		}
	}

	@NotNull
	private static Loaded load(@NotNull File file) throws IOException {
		String ext = Util.getExtension(file.getName()).toLowerCase();
		boolean tiff = ext.equals("tif") || ext.equals("tiff");
		if (!tiff) {
			try {
				ImageData[] data = new ImageLoader().load(file.getPath());
				if (data.length > 0)
					return new Loaded(data[0], Collections.<BufferedImage> emptyList());
			}
			catch (SWTException e) {
				// Try the shared reader below
			}
		}
		List<BufferedImage> pages = new ArrayList<BufferedImage>();
		for (ImagePages.Page page : ImagePages.read(file))
			pages.add(page.image);
		if (pages.isEmpty())
			throw new IOException(Msg.unsupported_image_format.get());
		return new Loaded(null, pages);
	}

	/**
	 * Converts a BufferedImage into SWT image data (without transparency).
	 */
	@NotNull
	static ImageData toImageData(@NotNull BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		if (image.getType() == BufferedImage.TYPE_BYTE_GRAY) {
			// Copy the raster directly with a gray palette
			RGB[] grays = new RGB[256];
			for (int i = 0; i < 256; i++)
				grays[i] = new RGB(i, i, i);
			ImageData data = new ImageData(width, height, 8, new PaletteData(grays));
			byte[] row = new byte[width];
			for (int y = 0; y < height; y++) {
				image.getRaster().getDataElements(0, y, width, 1, row);
				data.setPixels(0, y, width, toInts(row), 0);
			}
			return data;
		}
		PaletteData palette = new PaletteData(0xFF0000, 0x00FF00, 0x0000FF);
		ImageData data = new ImageData(width, height, 24, palette);
		int[] row = new int[width];
		for (int y = 0; y < height; y++) {
			image.getRGB(0, y, width, 1, row, 0, width);
			for (int x = 0; x < width; x++)
				row[x] &= 0xFFFFFF;
			data.setPixels(0, y, width, row, 0);
		}
		return data;
	}

	@NotNull
	private static int[] toInts(@NotNull byte[] bytes) {
		int[] ints = new int[bytes.length];
		for (int i = 0; i < bytes.length; i++)
			ints[i] = bytes[i] & 0xFF;
		return ints;
	}

	/** Receives error messages from the background loading thread. */
	interface ErrorHandler {
		void handleError(@NotNull String message);
	}

}
