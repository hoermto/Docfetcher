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
import net.sourceforge.docfetcher.gui.ResultPanel.DocAction;
import net.sourceforge.docfetcher.gui.ResultPanel.HeaderMode;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.LuceneIndex;
import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.model.search.Searcher;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.UtilFile;
import net.sourceforge.docfetcher.util.gui.dialog.InputDialog;
import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.collect.AlphanumComparator;
import net.sourceforge.docfetcher.util.gui.MenuAction;
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
	private final ToolItem rootBt;
	private final ToolItem newFolderBt;
	private final ResultPanel resultPanel;
	private final IndexRegistry indexRegistry;
	/** Where the folder of this browser is remembered between program runs. */
	private final SettingsConf.Str lastFolderSetting;

	/** The folder being shown, or null for the root list. */
	@Nullable private volatile File folder;
	private volatile int refreshCount = 0;

	public FolderPanel(	@NotNull Composite parent,
						@NotNull IndexRegistry indexRegistry,
						@NotNull SettingsConf.Str lastFolderSetting) {
		Util.checkNotNull(parent, indexRegistry, lastFolderSetting);
		this.indexRegistry = indexRegistry;
		this.lastFolderSetting = lastFolderSetting;

		comp = new Composite(parent, SWT.NONE);
		comp.setLayout(UtilGui.createGridLayout(1, false, 0, 3));

		Composite header = new Composite(comp, SWT.NONE);
		header.setLayout(UtilGui.createGridLayout(2, false, 3, 5));
		header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		ToolBar toolBar = new ToolBar(header, SWT.FLAT);
		toolBar.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		pathLabel = new Label(header, SWT.NONE);
		pathLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		resultPanel = new ResultPanel(comp, indexRegistry, true);
		
		/*
		 * The navigation actions are offered by the buttons above the listing
		 * and by the main toolbar, which works on the active browser.
		 */
		MenuAction rootsAction = resultPanel.addDocAction(DocAction.FOLDER_ROOTS, new MenuAction(Msg.folder_roots_bt.get()) {
			public boolean isEnabled() {
				return folder != null;
			}
			public void run() {
				setFolder(null); // Back to the list of root folders
			}
		});
		MenuAction upAction = resultPanel.addDocAction(DocAction.FOLDER_UP, new MenuAction(Msg.folder_up.get()) {
			public boolean isEnabled() {
				return folder != null;
			}
			public void run() {
				goUp();
			}
		});
		MenuAction refreshAction = resultPanel.addDocAction(DocAction.FOLDER_REFRESH, new MenuAction(Msg.refresh.get()) {
			public void run() {
				refresh();
			}
		});
		MenuAction newFolderAction = resultPanel.addDocAction(DocAction.NEW_FOLDER, new MenuAction(Msg.new_folder.get()) {
			public boolean isEnabled() {
				return folder != null; // Not in the root list
			}
			public void run() {
				createFolder();
			}
		});
		ToolItemFactory tif = new ToolItemFactory(toolBar);
		rootBt = createButton(tif, Img.TB_ROOTS, rootsAction);
		upBt = createButton(tif, Img.TB_UP, upAction);
		createButton(tif, Img.TB_REFRESH, refreshAction);
		newFolderBt = createButton(tif, Img.TB_NEW_FOLDER, newFolderAction);
		resultPanel.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		// The path bar already says which folder the files are in
		resultPanel.setPathColumnAllowed(false);

		// Keep the sort order when the user switches to another folder
		resultPanel.evtSorted.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				int column = resultPanel.getSortColumnIndex();
				if (column < 0)
					return;
				SettingsConf.Int.FolderSortColumn.set(column);
				SettingsConf.Bool.FolderSortAscending.set(resultPanel.isSortAscending());
			}
		});
		resultPanel.evtFolderOpened.add(new Event.Listener<File>() {
			public void update(File eventData) {
				setFolder(eventData);
			}
		});
		resultPanel.evtGoUp.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				goUp();
			}
		});
		resultPanel.evtDocumentsChanged.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				refresh(); // Files may have been renamed or moved away
			}
		});

		setFolder(getStartFolder(lastFolderSetting));
	}

	@NotNull
	private static ToolItem createButton(	@NotNull ToolItemFactory tif,
											@NotNull Img image,
											@NotNull final MenuAction action) {
		return tif.image(image.get()).toolTip(action.getLabel())
				.listener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						action.run();
					}
				}).create();
	}

	/**
	 * Sorts the listing the way the user last sorted it, by default by date
	 * with the newest documents on top.
	 */
	private void applySorting() {
		int column = SettingsConf.Int.FolderSortColumn.get();
		if (column < 0)
			column = resultPanel.getDateColumnIndex();
		resultPanel.sortBy(column, SettingsConf.Bool.FolderSortAscending.get());
	}
	
	/** Returns the folder shown at startup: the last one, if it still exists. */
	@Nullable
	private static File getStartFolder(@NotNull SettingsConf.Str lastFolderSetting) {
		String path = lastFolderSetting.get().trim();
		if (path.isEmpty())
			return null;
		File file = Util.getCanonicalFile(path);
		return file.isDirectory() ? file : null;
	}

	/**
	 * When the browser is a panel of its own, its purpose isn't obvious from
	 * a tab label, so the path bar carries the panel name as a prefix.
	 */
	public void setShowLabel(boolean showLabel) {
		this.showLabel = showLabel;
		updatePathLabel();
	}
	
	private boolean showLabel = false;
	
	private void updatePathLabel() {
		File current = folder;
		String path = current == null
			? Msg.folder_roots.get()
			: Util.getSystemAbsPath(current);
		pathLabel.setText(showLabel ? Msg.folders_tab.get() + ":  " + path : path);
		pathLabel.getParent().layout();
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
		showingNothing = false;
		this.folder = folder;
		lastFolderSetting.set(folder == null ? "" : Util.getSystemAbsPath(folder));
		updatePathLabel();
		resultPanel.setCurrentFolder(folder);
		upBt.setEnabled(folder != null);
		rootBt.setEnabled(folder != null);
		newFolderBt.setEnabled(folder != null); // Not in the root list
		resultPanel.fireStateChangedIfActive();
		evtFolderChanged.fire(null);
		refresh();
	}

	/**
	 * Shows the folder of the given file and selects the file in the list.
	 */
	public void showFile(@NotNull File file) {
		Util.checkNotNull(file);
		File parent = file.isDirectory() ? file : file.getParentFile();
		if (parent == null)
			return;
		fileToSelect = file;
		setFolder(parent);
	}
	
	@Nullable private File fileToSelect;

	/**
	 * Shows the given folder (null: the root list) and selects the given
	 * file or subfolder in it once the list is loaded.
	 */
	public void showFolder(@Nullable File folder, @Nullable File toSelect) {
		fileToSelect = toSelect;
		setFolder(folder);
	}

	private boolean showingNothing = false;

	/**
	 * Shows an empty list, e.g. in the synced folder view when the folder on
	 * the left has no subfolder to show.
	 */
	public void showNothing() {
		showingNothing = true;
		folder = null;
		fileToSelect = null;
		resultPanel.setCurrentFolder(null);
		upBt.setEnabled(false);
		rootBt.setEnabled(true);
		newFolderBt.setEnabled(false);
		pathLabel.setText(Msg.folder_nothing_to_show.get());
		pathLabel.getParent().layout();
		refreshCount++; // Drops the result of a refresh that is still running
		resultPanel.setResults(new ArrayList<ResultDocument>(), HeaderMode.FILES);
		resultPanel.fireStateChangedIfActive();
		evtFolderChanged.fire(null);
	}

	/** True while {@link #showNothing()} is in effect. */
	public boolean isShowingNothing() {
		return showingNothing;
	}
	
	/** Asks for a name and creates a subfolder in the current folder. */
	public void createFolder() {
		File current = folder;
		if (current == null)
			return;
		InputDialog dialog = new InputDialog(comp.getShell(),
			Msg.new_folder_title.get(), Msg.new_folder_msg.get(), "");
		String name = dialog.open();
		if (name == null || name.trim().isEmpty())
			return;
		File created = UtilFile.createFolder(current, name);
		if (created == null) {
			AppUtil.showError(Msg.new_folder_failed.format(
				new File(current, name.trim()).getPath()), true, true);
			return;
		}
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
	List<File> getRootFolders() {
		return getRootFolders(indexRegistry);
	}
	
	/**
	 * The folders the browser lists at its root level: the root folders of the
	 * document indexes and the fixed move targets, sorted by path.
	 */
	@NotNull
	public static List<File> getRootFolders(@NotNull IndexRegistry indexRegistry) {
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
		if (showingNothing)
			return;
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
						applySorting();
						if (fileToSelect != null) {
							resultPanel.selectByFile(fileToSelect);
							fileToSelect = null;
						}
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
			docs.add(searcher.createFolderRow(indexRegistry.findIndexFor(root), root));
		return docs;
	}

	/**
	 * Returns the '..' entry, the subfolders and the files of the given
	 * folder.
	 */
	@NotNull
	private List<ResultDocument> listFolder(@NotNull File folder) throws Exception {
		Searcher searcher = indexRegistry.getSearcher();
		if (searcher == null)
			return Collections.emptyList();
		List<ResultDocument> entries = new ArrayList<ResultDocument>();
		File up = getUpFolder(folder);
		if (up != null || isRootFolder(folder)) {
			// Leads to the parent folder, or back to the list of root folders
			ResultDocument parentRow = searcher.createFolderRow(
				indexRegistry.findIndexFor(up != null ? up : folder),
				up != null ? up : folder, PARENT_ROW_NAME);
			parentRow.setParentRow(true);
			entries.add(parentRow);
		}
		entries.addAll(searcher.listFolderEntries(indexRegistry.findIndexFor(folder), folder));
		return entries;
	}
	
	/** Name of the row that leads to the parent folder. */
	private static final String PARENT_ROW_NAME = "..";
	
	private boolean isRootFolder(@NotNull File folder) {
		for (File root : getRootFolders())
			if (Util.isCanonicallyEqual(root, folder))
				return true;
		return false;
	}
	
	/**
	 * Returns the folder the '..' entry leads to, or null if it leads back to
	 * the list of root folders.
	 */
	@Nullable
	private File getUpFolder(@NotNull File folder) {
		if (isRootFolder(folder))
			return null;
		return folder.getParentFile();
	}


}
