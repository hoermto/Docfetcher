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

import javax.imageio.ImageIO;

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
 * Preview for image files (PNG, JPEG, GIF, BMP, TIFF), e.g. scanned
 * documents. The image is fitted to the width of the pane initially and can
 * be zoomed in and out.
 */
final class ImagePreview extends ToolBarForm {

	public final Event<Void> evtHideInSystemTray = new Event<Void>();

	private static final double ZOOM_STEP = 1.25;
	private static final double MIN_SCALE = 0.05;
	private static final double MAX_SCALE = 8.0;

	@NotNull private ToolItem zoomInBt;
	@NotNull private ToolItem zoomOutBt;
	@NotNull private ToolItem launchBt;
	@NotNull private Label infoLabel;
	@NotNull private ScrolledComposite scrollComp;
	@NotNull private Canvas canvas;

	// GUI thread only
	@Nullable private File imageFile;
	@Nullable private Image image;
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
				final ImageData data;
				try {
					data = loadImageData(file);
				}
				catch (Throwable e) {
					errorHandler.handleError(e.toString());
					return;
				}
				UtilGui.runSwtSafe(ImagePreview.this, new Runnable() {
					public void run() {
						if (request != loadRequest)
							return;
						image = new Image(getDisplay(), data);
						scale = 0;
						infoLabel.setText(Msg.image_dimensions.format(
							String.valueOf(data.width), String.valueOf(data.height)));
						updateButtons();
						updateSize();
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
		launchBt.setEnabled(imageFile != null);
	}

	/**
	 * Loads the image data of the given file with SWT's image loader, falling
	 * back to ImageIO for formats the former doesn't understand.
	 */
	@NotNull
	private static ImageData loadImageData(@NotNull File file) throws IOException {
		try {
			ImageData[] data = new ImageLoader().load(file.getPath());
			if (data.length > 0)
				return data[0];
		}
		catch (SWTException e) {
			// Try ImageIO below
		}
		BufferedImage image = ImageIO.read(file);
		if (image == null)
			throw new IOException(Msg.unsupported_image_format.get());
		return toImageData(image);
	}

	/**
	 * Converts a BufferedImage into SWT image data (without transparency).
	 */
	@NotNull
	static ImageData toImageData(@NotNull BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
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

	/** Receives error messages from the background loading thread. */
	interface ErrorHandler {
		void handleError(@NotNull String message);
	}

}
