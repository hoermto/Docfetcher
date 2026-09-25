/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.util.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.junit.Assume;
import org.junit.Test;

import net.sourceforge.docfetcher.enums.SettingsConf;

/** Tests that the icons are loaded in the configured size. */
public final class LazyImageCacheTest {

	private static final String IMAGE_DIR = "dist/img";
	private static final String IMAGE = "folder.gif";

	/** Returns a display, or null if the test runs without a screen. */
	private static Display createDisplay() {
		try {
			return new Display();
		}
		catch (Throwable e) {
			return null; // SWTError without a screen, missing GTK libraries, ...
		}
	}

	@Test
	public void scalesTheIcons() {
		Assume.assumeTrue(new File(IMAGE_DIR, IMAGE).isFile());
		Display display = createDisplay();
		Assume.assumeTrue(display != null);
		try {
			LazyImageCache normal = new LazyImageCache(display, IMAGE_DIR);
			Image image = normal.getImage(IMAGE);
			assertNotNull(image);
			Rectangle bounds = image.getBounds();

			LazyImageCache large = new LazyImageCache(display, IMAGE_DIR);
			large.setScalePercent(200);
			Image scaled = large.getImage(IMAGE);
			assertNotNull(scaled);
			assertEquals(bounds.width * 2, scaled.getBounds().width);
			assertEquals(bounds.height * 2, scaled.getBounds().height);

			// Values outside the supported range are clamped
			LazyImageCache tiny = new LazyImageCache(display, IMAGE_DIR);
			tiny.setScalePercent(10);
			assertEquals(bounds.width, tiny.getImage(IMAGE).getBounds().width);
		}
		finally {
			display.dispose();
		}
	}

	private static int widthOf(Display display, int percent, String filename) {
		LazyImageCache cache = new LazyImageCache(display, IMAGE_DIR);
		cache.setScalePercent(percent);
		return cache.getImage(filename).getBounds().width;
	}

	@Test
	public void usesTheDoubleSizeVariantWhenEnlarging() {
		Display display = createDisplay();
		Assume.assumeTrue(display != null);
		try {
			// Toolbar icons have a 32 px variant (tb_*@2x.png)
			assertEquals(16, widthOf(display, 100, "tb_print.png"));
			assertEquals(32, widthOf(display, 200, "tb_print.png"));
			assertEquals(24, widthOf(display, 150, "tb_print.png"));
		}
		finally {
			display.dispose();
		}
	}

	@Test
	public void defaultIconSizeIs200Percent() {
		assertEquals(200, SettingsConf.Int.IconScalePercent.defaultValue);
	}

}
