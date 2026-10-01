/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.index.IndexingReporter;
import net.sourceforge.docfetcher.model.index.Task.CancelAction;
import net.sourceforge.docfetcher.model.index.Task.CancelHandler;
import net.sourceforge.docfetcher.model.index.file.FileIndex;
import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.gui.LazyImageCache;

/**
 * Helpers for tests of the GUI classes: a display (null without a screen), a
 * temporary folder with an index over it, the folder listing as result rows,
 * and screenshots of a shell for looking at the result.
 */
final class GuiTestSupport {

	final File root;
	final File indexDir;
	final IndexRegistry registry;

	private GuiTestSupport(File root, File indexDir, IndexRegistry registry) {
		this.root = root;
		this.indexDir = indexDir;
		this.registry = registry;
	}

	/** Returns a display with the program's images, or null without a screen. */
	static Display createDisplay() {
		Display display;
		try {
			display = new Display();
		}
		catch (Throwable e) {
			return null; // SWTError without a screen, missing GTK libraries, ...
		}
		Img.initialize(new LazyImageCache(display, "dist/img"));
		return display;
	}

	/**
	 * Creates a temporary folder, lets {@code filler} put files into it and
	 * indexes it.
	 */
	static GuiTestSupport createIndexedFolder(String prefix, FolderFiller filler) throws Exception {
		AppUtil.Const.autoInit();
		File root = createDir(prefix);
		File indexDir = createDir(prefix + "-index");
		filler.fill(root);
		IndexRegistry registry = new IndexRegistry(indexDir, 10, 100);
		registry.load(Cancelable.nullCancelable); // Creates the searcher
		FileIndex index = new FileIndex(indexDir, root);
		index.update(new IndexingReporter(), Cancelable.nullCancelable);
		registry.addIndex(index);
		return new GuiTestSupport(root, indexDir, registry);
	}

	interface FolderFiller {
		void fill(File root) throws Exception;
	}

	/** The rows of the given folder, as the folder browser lists them. */
	List<ResultDocument> listFolder(File folder) throws Exception {
		return registry.getSearcher().listFolderEntries(registry.getIndexes().get(0), folder);
	}

	/** Returns the rows with the given file names, in the given order. */
	static List<ResultDocument> find(Collection<ResultDocument> rows, String... names) {
		List<ResultDocument> found = new ArrayList<ResultDocument>();
		for (String name : names)
			for (ResultDocument row : rows)
				if (row.getFilename().equals(name))
					found.add(row);
		return found;
	}

	void dispose() throws Exception {
		registry.getQueue().shutdown(new CancelHandler() {
			public CancelAction cancel() {
				return CancelAction.DISCARD;
			}
		});
		// On Windows the index files may still be locked by the searcher
		for (File dir : new File[] { root, indexDir }) {
			try {
				Util.deleteRecursively(dir);
			}
			catch (java.io.IOException e) {
				dir.deleteOnExit();
			}
		}
	}

	static File createDir(String prefix) throws Exception {
		File dir = File.createTempFile(prefix, "");
		if (!dir.delete() || !dir.mkdir())
			throw new IllegalStateException(dir.getPath());
		return dir;
	}

	static void write(File file, String text) throws Exception {
		java.nio.file.Files.write(file.toPath(), text.getBytes("UTF-8"));
	}

	/** Runs the pending events of the display. */
	static void flush(Display display) {
		while (display.readAndDispatch()) {}
	}

	/**
	 * Saves a picture of the shell as PNG, if the system property
	 * 'docfetcher.test.shots' names a folder; for looking at a test's result.
	 */
	static void screenshot(Shell shell, String name) {
		String dir = System.getProperty("docfetcher.test.shots");
		if (dir == null)
			return;
		flush(shell.getDisplay());
		Point size = shell.getSize();
		Image image = new Image(shell.getDisplay(), size.x, size.y);
		GC gc = new GC(shell);
		try {
			gc.copyArea(image, 0, 0);
			ImageLoader loader = new ImageLoader();
			loader.data = new ImageData[] { image.getImageData() };
			loader.save(new File(dir, name + ".png").getPath(), org.eclipse.swt.SWT.IMAGE_PNG);
		}
		finally {
			gc.dispose();
			image.dispose();
		}
	}

}
