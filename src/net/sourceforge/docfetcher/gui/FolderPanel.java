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
import java.util.Collections;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.gui.ResultPanel.HeaderMode;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.LuceneIndex;
import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.model.search.Searcher;
import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.collect.AlphanumComparator;
import net.sourceforge.docfetcher.util.gui.ToolItemFactory;

/**
 * The 'Folders' tab: a simple file browser over the indexed folders and the
 * fixed move targets. The root level lists those folders; from there the
 * user can walk into the subfolders. Each folder shows its subfolders and
 * all its files (indexed or not) in the same table as the search results,
 * so that files can be previewed, renamed and moved.
 */
public final class FolderPanel {

	/** Fired when another folder is shown, so that the preview can be cleared. */
	public final Event<Void> evtFolderChanged = new Event<Void>();

	private final Composite comp;
	private final Label pathLabel;
	private final ToolItem upBt;
	private final ResultPanel resultPanel;
	private final IndexRegistry indexRegistry;

	/** The folder being shown, or null for the root list. */
	@Nullable private volatile File folder;
	private volatile int refreshCount = 0;

	public FolderPanel(@NotNull Composite parent, @NotNull IndexRegistry indexRegistry) {
		Util.checkNotNull(parent, indexRegistry);
		this.indexRegistry = indexRegistry;

		comp = new Composite(parent, SWT.NONE);
		comp.setLayout(UtilGui.createGridLayout(1, false, 0, 3));

		Composite header = new Composite(comp, SWT.NONE);
		header.setLayout(UtilGui.createGridLayout(2, false, 3, 5));
		header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		ToolBar toolBar = new ToolBar(header, SWT.FLAT);
		toolBar.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		ToolItemFactory tif = new ToolItemFactory(toolBar);
		upBt = tif.image(Img.ARROW_UP.get()).toolTip(Msg.folder_up.get())
				.listener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						goUp();
					}
				}).create();
		tif.enabled(true).image(Img.REFRESH.get()).toolTip(Msg.refresh.get())
				.listener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						refresh();
					}
				}).create();

		pathLabel = new Label(header, SWT.NONE);
		pathLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		resultPanel = new ResultPanel(comp, indexRegistry, true);
		resultPanel.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		resultPanel.evtFolderOpened.add(new Event.Listener<File>() {
			public void update(File eventData) {
				setFolder(eventData);
			}
		});
		resultPanel.evtDocumentsChanged.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				refresh(); // Files may have been renamed or moved away
			}
		});

		setFolder(getStartFolder());
	}

	/** Returns the folder shown at startup: the last one, if it still exists. */
	@Nullable
	private static File getStartFolder() {
		String path = SettingsConf.Str.LastBrowsedFolder.get().trim();
		if (path.isEmpty())
			return null;
		File file = Util.getCanonicalFile(path);
		return file.isDirectory() ? file : null;
	}

	@NotNull
	public Control getControl() {
		return comp;
	}

	@NotNull
	public ResultPanel getResultPanel() {
		return resultPanel;
	}

	@Nullable
	public File getFolder() {
		return folder;
	}

	/**
	 * Shows the contents of the given folder, or the root list if it is
	 * null. Must be called from the GUI thread.
	 */
	public void setFolder(@Nullable File folder) {
		this.folder = folder;
		SettingsConf.Str.LastBrowsedFolder.set(
			folder == null ? "" : Util.getSystemAbsPath(folder));
		pathLabel.setText(folder == null
			? Msg.folder_roots.get()
			: Util.getSystemAbsPath(folder));
		pathLabel.getParent().layout();
		upBt.setEnabled(folder != null);
		evtFolderChanged.fire(null);
		refresh();
	}

	/**
	 * Goes to the parent folder, or back to the root list if the current
	 * folder is one of the root folders.
	 */
	public void goUp() {
		File current = folder;
		if (current == null)
			return;
		for (File root : getRootFolders()) {
			if (Util.isCanonicallyEqual(root, current)) {
				setFolder(null);
				return;
			}
		}
		File parent = current.getParentFile();
		setFolder(parent == null ? null : parent);
	}

	/** The folders listed at the root level: indexes and fixed move targets. */
	@NotNull
	private List<File> getRootFolders() {
		List<File> roots = new ArrayList<File>();
		for (LuceneIndex index : indexRegistry.getIndexes()) {
			if (index.isEmailIndex())
				continue;
			File root = index.getCanonicalRootFile();
			if (root.isDirectory())
				roots.add(root);
		}
		for (File target : MoveTargets.loadFolders()) {
			boolean known = false;
			for (File root : roots)
				if (Util.isCanonicallyEqual(root, target))
					known = true;
			if (!known)
				roots.add(target);
		}
		Collections.sort(roots, new java.util.Comparator<File>() {
			public int compare(File f1, File f2) {
				return AlphanumComparator.ignoreCaseInstance.compare(
					f1.getPath(), f2.getPath());
			}
		});
		return roots;
	}

	/**
	 * Reloads the current folder in the background. Safe to call from any
	 * thread.
	 */
	public void refresh() {
		final File folder = this.folder;
		final int count = ++refreshCount;
		Thread thread = new Thread(FolderPanel.class.getName() + " (refresh)") {
			public void run() {
				List<ResultDocument> docs;
				try {
					docs = folder == null ? listRoots() : listFolder(folder);
				}
				catch (Exception e) {
					docs = Collections.emptyList();
					Util.printErr(e);
				}
				catch (OutOfMemoryError e) {
					docs = Collections.emptyList();
					Util.printErr(e);
				}
				final List<ResultDocument> result = docs;
				UtilGui.runSwtSafe(comp, new Runnable() {
					public void run() {
						if (count != refreshCount)
							return; // A newer refresh is on its way
						resultPanel.setResults(
							new ArrayList<ResultDocument>(result), HeaderMode.FILES);
						resultPanel.applyNameSorting();
					}
				});
			}
		};
		thread.setDaemon(true);
		thread.start();
	}

	/** Returns the root folders as folder rows. */
	@NotNull
	private List<ResultDocument> listRoots() throws Exception {
		Searcher searcher = indexRegistry.getSearcher();
		List<ResultDocument> docs = new ArrayList<ResultDocument>();
		if (searcher == null)
			return docs;
		for (File root : getRootFolders())
			docs.add(searcher.createFolderRow(findIndexFor(root), root));
		return docs;
	}

	/** Returns the subfolders and files of the given folder. */
	@NotNull
	private List<ResultDocument> listFolder(@NotNull File folder) throws Exception {
		Searcher searcher = indexRegistry.getSearcher();
		if (searcher == null)
			return Collections.emptyList();
		return searcher.listFolderEntries(findIndexFor(folder), folder);
	}

	/** Returns the index covering the given folder, or null. */
	@Nullable
	private LuceneIndex findIndexFor(@NotNull File folder) {
		for (LuceneIndex index : indexRegistry.getIndexes()) {
			File root = index.getCanonicalRootFile();
			if (Util.isCanonicallyEqual(root, folder) || Util.contains(root, folder))
				return index;
		}
		return null;
	}

}
