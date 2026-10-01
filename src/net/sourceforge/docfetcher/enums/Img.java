/*******************************************************************************
 * Copyright (c) 2010, 2011 Tran Nam Quang.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tran Nam Quang - initial API and implementation
 *******************************************************************************/

package net.sourceforge.docfetcher.enums;

import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.gui.LazyImageCache;
import net.sourceforge.docfetcher.util.gui.LazyImageCache.FilenameProvider;

import org.eclipse.swt.graphics.Image;

/**
 * An application-wide image container. The images are allocated on demand and
 * automatically disposed of after program termination. Usage:
 * <ul>
 * <li>The available images are defined by the constants in this enumeration. A
 * brief name and the filename (with file extension) must be specified.
 * <li>Before accessing the images, {@link #initialize(LazyImageCache)} must be
 * called once. This is usually done after the SWT
 * {@link org.eclipse.swt.widgets.Display Display} has been created.
 * <li>Then, images can be retrieved through the enumeration constants and the
 * {@link #get()} method, e.g. <tt>Img.NAME_OF_IMAGE.get()</tt>.
 * </ul>
 * 
 * @author Tran Nam Quang
 */
public enum Img implements FilenameProvider {
	
	ADD ("add.gif"),
	ARROW_DOWN ("arrow_down.gif"),
	ARROW_LEFT ("arrow_left.gif"),
	ARROW_RIGHT ("arrow_right.gif"),
	ARROW_UP ("arrow_up.gif"),
	BROWSER ("browser.gif"), // currently unused
	BUILDING_BLOCKS ("building_blocks.gif"),
	CHECK ("check.gif"),
	CLIPBOARD ("clipboard.gif"),
	DOCFETCHER_16 ("docfetcher16.png"),
	DOCFETCHER_24 ("docfetcher24.png"),
	DOCFETCHER_32 ("docfetcher32.png"),
	DOCFETCHER_48 ("docfetcher48.png"),
	DOCFETCHER_64 ("docfetcher64.png"),
	DOCFETCHER_128 ("docfetcher128.png"),
	EMAIL ("email.gif"),
	FILE ("file.gif"),
	FOLDER ("folder.gif"),
	HELP ("help.gif"),
	HIDE ("hide.gif"),
	HIGHLIGHT ("highlight.gif"),
	INDEXING_DIALOG ("indexing_dialog.gif"),
	INDEXING ("indexing.gif"),
	INFO ("info.gif"),
	LETTERS ("letters.gif"),
	LIST ("list.gif"), // TODO pre-release: Currently not used
	MAXIMIZE ("maximize.gif"),
	MINIMIZE ("minimize.gif"),
	PACKAGE ("package.gif"),
	PREFERENCES ("preferences.gif"),
	EXIT ("exit.png"),
	PEN ("pen.png"),
	MOVE ("move.png"),
	REFRESH ("refresh.gif"),
	REMOVE ("remove.gif"),
	RESTORE ("restore.png"),
	STAR ("star.gif"), // currently unused
	STOP ("stop.gif"),
	TREE ("tree.gif"),
	WARNING_BIG ("warning_big.gif"),
	WARNING ("warning.gif"), // currently unused
	WINDOW ("program.gif"),

	// Flat icons of the main toolbar (see dev/icons/make_toolbar_icons.py)
	TB_PRINT ("tb_print.png"),
	TB_MAIL ("tb_mail.png"),
	TB_RENAME ("tb_rename.png"),
	TB_MOVE ("tb_move.png"),
	TB_MERGE ("tb_merge.png"),
	TB_SPLIT ("tb_split.png"),
	TB_DELETE ("tb_delete.png"),
	TB_ROOTS ("tb_roots.png"),
	TB_UP ("tb_up.png"),
	TB_REFRESH ("tb_refresh.png"),
	TB_NEW_FOLDER ("tb_new_folder.png"),
	TB_SYNC ("tb_sync.png"),
	TB_PANES1 ("tb_panes1.png"),
	TB_PANES2 ("tb_panes2.png"),
	TB_PANES3 ("tb_panes3.png"),
	;
	
	private static LazyImageCache lazyImageCache;

	public static void initialize(@NotNull LazyImageCache lazyImageCache) {
		Img.lazyImageCache = lazyImageCache;
	}
	
	@NotNull private final String filename;
	
	private Img(@NotNull String filename) {
		this.filename = filename;
	}
	
	/**
	 * Returns the <tt>Image</tt> object corresponding to this enumeration
	 * constant. It is disposed of automatically after program termination. If
	 * the image cannot be loaded, null is returned.
	 */
	@Nullable
	public Image get() {
		return lazyImageCache.getImage(filename);
	}

	@NotNull
	public String getFilename() {
		return filename;
	}

}

