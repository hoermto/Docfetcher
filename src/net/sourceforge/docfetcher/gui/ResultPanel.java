/*******************************************************************************
 * Copyright (c) 2011 Tran Nam Quang.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tran Nam Quang - initial API and implementation
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.ProgramConf;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.model.FileResource;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.LuceneIndex;
import net.sourceforge.docfetcher.model.index.Task.IndexAction;
import net.sourceforge.docfetcher.model.Path;
import net.sourceforge.docfetcher.model.Path.PathParts;
import net.sourceforge.docfetcher.model.parse.ParseException;
import net.sourceforge.docfetcher.model.search.FolderSuggestion;
import net.sourceforge.docfetcher.model.search.NameSuggester;
import net.sourceforge.docfetcher.model.search.NameSuggester.Suggestion;
import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.model.search.Searcher;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.MutableCopy;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.collect.AlphanumComparator;
import net.sourceforge.docfetcher.util.gui.ContextMenuManager;
import net.sourceforge.docfetcher.util.gui.MenuAction;
import net.sourceforge.docfetcher.util.gui.dialog.MultipleChoiceDialog;
import net.sourceforge.docfetcher.util.gui.viewer.VirtualTableViewer;
import net.sourceforge.docfetcher.util.gui.viewer.VirtualTableViewer.Column;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

/**
 * @author Tran Nam Quang
 */
public final class ResultPanel {
	
	// TODO post-release-1.1: show an additional icon if an email has attachments
	// TODO post-release-1.1: show some helpful overlay message if a search yielded no results
	// TODO post-release-1.1: implement context menu: copy paths to clipboard, including Mod1+C shortcut (also: Mod1+A for selecting all items)
	
	public enum HeaderMode {
		FILES { protected void setLabel(VariableHeaderColumn<?> column) {
			column.setLabel(column.fileHeader);
		} },
		EMAILS { protected void setLabel(VariableHeaderColumn<?> column) {
			column.setLabel(column.emailHeader);
		} },
		FILES_AND_EMAILS { protected void setLabel(VariableHeaderColumn<?> column) {
			column.setLabel(column.combinedHeader);
		} },
		;
		
		protected abstract void setLabel(@NotNull VariableHeaderColumn<?> column);
		
		@NotNull
		public static HeaderMode getInstance(boolean filesFound, boolean emailsFound) {
			final HeaderMode mode;
			if (filesFound)
				mode = emailsFound ? HeaderMode.FILES_AND_EMAILS : HeaderMode.FILES;
			else
				mode = HeaderMode.EMAILS;
			return mode;
		}
	}
	
	private static final DateFormat dateFormat = new SimpleDateFormat();
	
	/** Columns are never made narrower than this when fitting them to the table width. */
	private static final int MIN_COLUMN_WIDTH = 30;
	
	public final Event<List<ResultDocument>> evtSelection = new Event<List<ResultDocument>> ();
	public final Event<Void> evtHideInSystemTray = new Event<Void>();
	
	/** Fired after documents were renamed or moved, so that the preview can be refreshed. */
	public final Event<Void> evtDocumentsChanged = new Event<Void>();
	
	private final VirtualTableViewer<ResultDocument> viewer;
	private final IndexRegistry indexRegistry;
	@Nullable private DragSource dragSource;
	private int dragOperations = 0;
	private boolean lastDragWasCopy = false;
	
	/*
	 * Name suggestions are computed in the background when a document is
	 * selected, so that they're usually ready when the context menu or the
	 * rename dialog is opened. Both maps are accessed under their own lock.
	 */
	/** Name and folder suggestions for one document. */
	private static final class Suggestions {
		@Nullable final Suggestion name;
		final List<FolderSuggestion> folders;
		Suggestions(@Nullable Suggestion name, @NotNull List<FolderSuggestion> folders) {
			this.name = name;
			this.folders = folders;
		}
	}
	
	private static final int MAX_FOLDER_SUGGESTIONS = 5;
	
	private final Map<ResultDocument, Suggestions> suggestionCache = new LinkedHashMap<ResultDocument, Suggestions>(16, 0.75f, true) {
		protected boolean removeEldestEntry(Map.Entry<ResultDocument, Suggestions> eldest) {
			return size() > 50;
		}
	};
	private final Set<ResultDocument> suggestionsInProgress = new HashSet<ResultDocument>();
	private final Set<ResultDocument> suggestionsFailed = new HashSet<ResultDocument>();
	private HeaderMode presetHeaderMode = HeaderMode.FILES; // externally suggested header mode
	private HeaderMode actualHeaderMode = HeaderMode.FILES; // header mode after examining each visible element
	private final Column<ResultDocument> dateColumn;

	/** Number of leading columns with clickable action icons (rename, move). */
	private final int actionColumnCount;
	
	public ResultPanel(@NotNull Composite parent, @NotNull IndexRegistry indexRegistry) {
		this(parent, indexRegistry, false);
	}
	
	/**
	 * @param actionColumns
	 *            If true, the table starts with two narrow columns showing a
	 *            rename and a move icon for each row; clicking an icon runs
	 *            the respective action for that row.
	 */
	public ResultPanel(	@NotNull Composite parent,
						@NotNull IndexRegistry indexRegistry,
						boolean actionColumns) {
		Util.checkNotNull(indexRegistry);
		this.indexRegistry = indexRegistry;
		this.actionColumnCount = actionColumns ? 2 : 0;
		
		int treeStyle = SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER;
		viewer = new VirtualTableViewer<ResultDocument> (parent, treeStyle) {
			@SuppressWarnings("unchecked")
			protected List<ResultDocument> getElements(Object rootElement) {
				return (List<ResultDocument>) rootElement;
			}
		};
		
		// Open result document on double-click
		final Table table = viewer.getControl();
		table.addMouseListener(new MouseAdapter() {
			public void mouseDoubleClick(MouseEvent e) {
				launchSelection();
			}
		});
		
		table.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				if (UtilGui.isEnterKey(e.keyCode))
					launchSelection();
				else if (e.stateMask == SWT.MOD1 && e.keyCode == 'c')
					copyToClipboard();
				else if (e.stateMask == SWT.MOD1 && e.keyCode == 'a')
					table.selectAll();
				else if (e.keyCode == SWT.F2)
					renameSelection();
			}
		});
		
		initDragSource(table);
		
		viewer.setSortingEnabled(true);
		initContextMenu();
		
		table.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				List<ResultDocument> selection = viewer.getSelection();
				evtSelection.fire(selection);
				if (selection.size() == 1)
					requestSuggestion(selection.get(0));
			}
		});
		
		if (actionColumns) {
			viewer.addColumn(new Column<ResultDocument>("", SWT.CENTER) {
				protected String getLabel(ResultDocument element) {
					return "";
				}
				protected Image getImage(ResultDocument element) {
					return element.isEmail() ? null : Img.PEN.get();
				}
				protected String getToolTipText() {
					return Msg.rename_document_title.get();
				}
			});
			viewer.addColumn(new Column<ResultDocument>("", SWT.CENTER) {
				protected String getLabel(ResultDocument element) {
					return "";
				}
				protected Image getImage(ResultDocument element) {
					return element.isEmail() ? null : Img.MOVE.get();
				}
				protected String getToolTipText() {
					return Msg.move_to_folder_title.get();
				}
			});
			initActionColumnClicks(table);
		}
		
		// The file name identifies a document better than its title, which
		// often is just the file name without extension or some boilerplate
		viewer.addColumn(new VariableHeaderColumn<ResultDocument>(Msg.filename.get(), Msg.subject.get()) {
			protected String getLabel(ResultDocument element) {
				if (element.isEmail())
					return element.getTitle(); // Subject
				return element.getFilename();
			}
			protected int compare(ResultDocument e1, ResultDocument e2) {
				return compareAlphanum(getLabel(e1), getLabel(e2));
			}
		});
		
		dateColumn = new VariableHeaderColumn<ResultDocument>(Msg.last_modified.get(), Msg.send_date.get()) {
			protected String getLabel(ResultDocument element) {
				Date date = getDate(element);
				return date == null ? "" : dateFormat.format(date);
			}
			protected int compare(ResultDocument e1, ResultDocument e2) {
				Date date1 = getDate(e1);
				Date date2 = getDate(e2);
				if (date1 == null) // Place null dates before non-null dates
					return date2 == null ? 0 : -1;
				else if (date2 == null)
					return 1;
				return date1.compareTo(date2);
			}
			@Nullable
			private Date getDate(ResultDocument element) {
				if (element.isEmail())
					return element.getDate();
				return element.getLastModified();
			}
		};
		viewer.addColumn(dateColumn);
		
		viewer.addColumn(new Column<ResultDocument>(Msg.pages.get(), SWT.RIGHT) {
			protected String getLabel(ResultDocument element) {
				int pages = element.getPageCount();
				return pages > 0 ? String.valueOf(pages) : "";
			}
			protected int compare(ResultDocument e1, ResultDocument e2) {
				return Integer.compare(e1.getPageCount(), e2.getPageCount());
			}
		});
		
		viewer.addColumn(new Column<ResultDocument>(Msg.type.get()) {
			protected String getLabel(ResultDocument element) {
				return element.getType();
			}
			protected int compare(ResultDocument e1, ResultDocument e2) {
				return compareAlphanum(e1.getType(), e2.getType());
			}
		});
		
		// The folder is enough here; the file name has its own column, and
		// the full path is shown as tooltip and in the status bar
		viewer.addColumn(new Column<ResultDocument>(Msg.path.get()) {
			protected String getLabel(ResultDocument element) {
				return element.getParentPath().getPath();
			}
			protected int compare(ResultDocument e1, ResultDocument e2) {
				return compareAlphanum(getLabel(e1), getLabel(e2));
			}
		});
		
		
		
		if (actionColumns) {
			SettingsConf.ColumnWidths.InboxPanel.bind(table);
			SettingsConf.ColumnOrder.InboxPanelColumnOrder.bind(table);
		}
		else {
			SettingsConf.ColumnWidths.ResultPanel.bind(table);
			SettingsConf.ColumnOrder.ResultPanelColumnOrder.bind(table);
		}
		
		initColumnFitting(table);
		initPathTooltip(table);
		
		// The folder column can be turned off in the settings
		pathColumn = table.getColumn(table.getColumnCount() - 1);
		applyPathColumnVisibility();
		final Event.Listener<Boolean> pathColumnListener = new Event.Listener<Boolean>() {
			public void update(Boolean eventData) {
				UtilGui.runAsyncExec(table, new Runnable() {
					public void run() {
						applyPathColumnVisibility();
					}
				});
			}
		};
		SettingsConf.Bool.ShowPathColumn.evtChanged.add(pathColumnListener);
		table.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				SettingsConf.Bool.ShowPathColumn.evtChanged.remove(pathColumnListener);
			}
		});
	}
	
	@Nullable private TableColumn pathColumn;
	
	/** Hides or shows the folder column according to the settings. */
	private void applyPathColumnVisibility() {
		if (pathColumn == null || pathColumn.isDisposed())
			return;
		boolean visible = SettingsConf.Bool.ShowPathColumn.get();
		int index = pathColumn.getParent().indexOf(pathColumn);
		pathColumn.setResizable(visible);
		// A weight of 0 keeps the column out of the width distribution
		hiddenColumns[index] = !visible;
		if (!visible)
			columnWeights[index] = 0;
		else if (columnWeights[index] <= 0)
			columnWeights[index] = PATH_COLUMN_WEIGHT;
		scheduleColumnFit();
	}
	
	/** Shows the full path of the row under the mouse as a tooltip. */
	private void initPathTooltip(@NotNull final Table table) {
		table.addListener(SWT.MouseMove, new Listener() {
			public void handleEvent(org.eclipse.swt.widgets.Event event) {
				TableItem item = table.getItem(new Point(event.x, event.y));
				String path = "";
				if (item != null) {
					Object data = item.getData();
					if (data instanceof ResultDocument)
						path = ((ResultDocument) data).getPath().getPath();
				}
				if (!path.equals(table.getToolTipText()))
					table.setToolTipText(path);
			}
		});
	}
	
	/*
	 * The columns are scaled to fill the visible width of the table whenever
	 * the table is resized. The proportions are taken from the column
	 * weights, which are initialized from the stored column widths and
	 * updated when the user drags a column border. Reading the proportions
	 * back from the live column widths wouldn't work, because some platforms
	 * (GTK) automatically expand the last column to fill the table.
	 */
	private double[] columnWeights;
	private boolean[] hiddenColumns;
	private static final int PATH_COLUMN_WEIGHT = 350;
	private boolean fittingColumns = false; // true while the fit sets widths
	private boolean tableResizing = false; // true from table resize until fit
	private boolean columnFitPending = false;
	
	private void initColumnFitting(@NotNull final Table table) {
		final TableColumn[] columns = table.getColumns();
		columnWeights = new double[columns.length];
		hiddenColumns = new boolean[columns.length];
		for (int i = 0; i < columns.length; i++) {
			columnWeights[i] = Math.max(columns[i].getWidth(), MIN_COLUMN_WIDTH);
			final int index = i;
			columns[i].addControlListener(new ControlAdapter() {
				public void controlResized(ControlEvent e) {
					// Only record widths set by the user, not by the fit or
					// by the platform's automatic expansion during a resize
					if (fittingColumns || tableResizing || hiddenColumns[index])
						return;
					columnWeights[index] = Math.max(columns[index].getWidth(), MIN_COLUMN_WIDTH);
				}
			});
		}
		table.addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent e) {
				tableResizing = true;
				scheduleColumnFit();
			}
		});
	}
	
	/**
	 * Fits the columns to the table width asynchronously. The deferral is
	 * necessary because on some platforms (GTK) the client area reported
	 * during a resize event is still the old one; it also coalesces bursts
	 * of resize events.
	 */
	private void scheduleColumnFit() {
		if (columnFitPending)
			return;
		columnFitPending = true;
		UtilGui.runAsyncExec(viewer.getControl(), new Runnable() {
			public void run() {
				columnFitPending = false;
				fitColumnsToWidth();
			}
		});
	}
	
	/**
	 * Sets the column widths so that they exactly fill the visible width of
	 * the table, distributed according to the column weights.
	 */
	private void fitColumnsToWidth() {
		Table table = viewer.getControl();
		if (table.isDisposed())
			return;
		try {
			int available = table.getClientArea().width;
			if (available <= 0)
				return;
			TableColumn[] columns = table.getColumns();
			
			// The action icon columns keep a fixed width
			int actionWidth = 26;
			available -= actionColumnCount * actionWidth;
			if (available <= 0)
				return;
			
			double totalWeight = 0;
			for (int i = actionColumnCount; i < columnWeights.length; i++)
				totalWeight += columnWeights[i];
			if (totalWeight <= 0)
				return;
			fittingColumns = true;
			try {
				int lastVisible = columns.length - 1;
				while (lastVisible > actionColumnCount && hiddenColumns[lastVisible])
					lastVisible--;
				int assigned = 0;
				for (int i = 0; i < columns.length; i++) {
					int width;
					if (hiddenColumns[i])
						width = 0;
					else if (i < actionColumnCount)
						width = actionWidth;
					else if (i == lastVisible)
						width = available - assigned; // Absorb rounding errors
					else
						width = (int) Math.round(columnWeights[i] * available / totalWeight);
					if (i >= actionColumnCount && !hiddenColumns[i])
						width = Math.max(width, MIN_COLUMN_WIDTH);
					if (columns[i].getWidth() != width)
						columns[i].setWidth(width);
					if (i >= actionColumnCount)
						assigned += width;
				}
			}
			finally {
				fittingColumns = false;
			}
		}
		finally {
			tableResizing = false;
		}
	}
	
	private void launchSelection() {
		List<ResultDocument> selection = viewer.getSelection();
		if (selection.isEmpty())
			return;
		ResultDocument doc = selection.get(0);
		if (!doc.isEmail())
			launchFiles(Collections.singletonList(doc));
	}
	
	private static int compareAlphanum(@NotNull String s1, @NotNull String s2) {
		return AlphanumComparator.ignoreCaseInstance.compare(s1, s2);
	}

	private void initContextMenu() {
		ContextMenuManager menuManager = new ContextMenuManager(viewer.getControl());
		
		menuManager.add(new MenuAction(Msg.open.get()) {
			public boolean isEnabled() {
				List<ResultDocument> sel = viewer.getSelection();
				if (sel.isEmpty())
					return false;
				for (ResultDocument doc : sel)
					if (doc.isEmail())
						return false;
				return true;
			}
			public void run() {
				launchFiles(viewer.getSelection());
			}
			public boolean isDefaultItem() {
				return true;
			}
		});
		
		menuManager.add(new MenuAction(Msg.open_parent.get()) {
			public boolean isEnabled() {
				return !viewer.getSelection().isEmpty();
			}
			public void run() {
				MultiFileLauncher launcher = new MultiFileLauncher();
				for (ResultDocument doc : viewer.getSelection()) {
					Path path = doc.getPath();
					try {
						launcher.addFile(getParent(path));
					}
					catch (FileNotFoundException e) {
						launcher.addMissing(path.getCanonicalPath());
					}
				}
				if (launcher.launch() && SettingsConf.Bool.HideOnOpen.get())
					evtHideInSystemTray.fire(null);
			}
			@NotNull
			private File getParent(@NotNull Path path)
					throws FileNotFoundException {
				/*
				 * The possible cases:
				 * - Path points to an ordinary file
				 * - Path points to an archive entry
				 * - Path points to an item in a PST file
				 * 
				 * In each case, the target may or may not exist.
				 */
				PathParts pathParts = path.splitAtExistingFile();
				
				if (pathParts.getRight().isEmpty()) // Existing ordinary file
					return Util.getParentFile(path.getCanonicalFile());
				
				File leftFile = pathParts.getLeft().getCanonicalFile();
				if (leftFile.isDirectory())
					// File, archive entry or PST item does not exist
					throw new FileNotFoundException();
				
				// Existing PST item
				if (Util.hasExtension(pathParts.getLeft().getName(), "pst"))
					return Util.getParentFile(leftFile);
				
				// Existing archive entry -> return the archive
				return leftFile;
			}
		});
		
		menuManager.addSeparator();
		
		menuManager.add(new MenuAction(Msg.rename_document.get()) {
			public boolean isEnabled() {
				List<ResultDocument> sel = viewer.getSelection();
				return sel.size() == 1 && !sel.get(0).isEmail();
			}
			public void run() {
				renameSelection();
			}
		});
		
		final Menu renameToMenu = menuManager.addSubmenu(new MenuAction(Msg.rename_to_submenu.get()) {
			public boolean isEnabled() {
				List<ResultDocument> sel = viewer.getSelection();
				return sel.size() == 1 && !sel.get(0).isEmail();
			}
		});
		renameToMenu.addMenuListener(new MenuAdapter() {
			public void menuShown(MenuEvent e) {
				fillRenameToMenu(renameToMenu);
			}
		});
		
		final Menu moveToMenu = menuManager.addSubmenu(new MenuAction(Msg.move_to_submenu.get()) {
			public boolean isEnabled() {
				List<ResultDocument> sel = viewer.getSelection();
				if (sel.isEmpty())
					return false;
				for (ResultDocument doc : sel)
					if (doc.isEmail())
						return false;
				return true;
			}
		});
		moveToMenu.addMenuListener(new MenuAdapter() {
			public void menuShown(MenuEvent e) {
				fillMoveToMenu(moveToMenu);
			}
		});
		
		menuManager.addSeparator();
		
		String label = Util.IS_MAC_OS_X ? Msg.copy_macosx.get() : Msg.copy.get();
		menuManager.add(new MenuAction(label) {
			public boolean isEnabled() {
				return !viewer.getSelection().isEmpty();
			}
			public void run() {
				copyToClipboard();
			}
		});
	}
	
	/**
	 * Runs the rename or move action when the corresponding icon of a row is
	 * clicked. The row is selected first, so the actions work on it.
	 */
	private void initActionColumnClicks(@NotNull final Table table) {
		table.addMouseListener(new MouseAdapter() {
			public void mouseUp(MouseEvent e) {
				if (e.button != 1)
					return;
				Point pt = new Point(e.x, e.y);
				TableItem item = table.getItem(pt);
				if (item == null)
					return;
				for (int col = 0; col < actionColumnCount; col++) {
					if (!item.getBounds(col).contains(pt))
						continue;
					table.setSelection(item);
					evtSelection.fire(viewer.getSelection());
					if (col == 0)
						renameSelection();
					else
						moveSelection();
					return;
				}
			}
		});
	}
	
	/*
	 * Renaming and moving documents. Both operate on the files on disk,
	 * update the affected result documents in memory (so that the table and
	 * the preview show the new location immediately) and enqueue updates of
	 * the affected indexes.
	 */
	
	private void renameSelection() {
		List<ResultDocument> sel = viewer.getSelection();
		if (sel.size() != 1)
			return;
		ResultDocument doc = sel.get(0);
		if (!doc.isPlainFileOnDisk()) {
			AppUtil.showError(Msg.only_plain_files.get(), true, true);
			return;
		}
		File file = doc.getPath().getCanonicalFile();
		RenameDialog dialog = new RenameDialog(
			getControl().getShell(), file.getName(), waitForSuggestion(doc));
		String newName = dialog.open();
		if (newName != null)
			renameTo(doc, newName);
	}
	
	/**
	 * Renames the document's file to the given name (in the same folder).
	 */
	private void renameTo(@NotNull ResultDocument doc, @NotNull String newName) {
		File file = doc.getPath().getCanonicalFile();
		newName = newName.trim();
		// Renaming must never change the file extension
		String extension = Util.splitFilename(file.getName())[1];
		if (!extension.isEmpty()
				&& !Util.splitFilename(newName)[1].equalsIgnoreCase(extension))
			newName += "." + extension;
		if (newName.isEmpty() || newName.equals(file.getName()))
			return;
		if (newName.contains("/") || newName.contains("\\")) {
			AppUtil.showError(Msg.file_operation_failed.format(newName), true, true);
			return;
		}
		File target = new File(file.getParentFile(), newName);
		if (moveFile(file, target)) {
			synchronized (suggestionCache) {
				Suggestions suggestions = suggestionCache.remove(doc);
				doc.setPathAfterFileOperation(target);
				if (suggestions != null)
					suggestionCache.put(doc, suggestions);
			}
			viewer.refresh();
			updateIndexesFor(Arrays.asList(file, target));
			evtDocumentsChanged.fire(null);
		}
	}
	
	/*
	 * Name suggestions
	 */
	
	@Nullable
	private Suggestions getSuggestions(@NotNull ResultDocument doc) {
		synchronized (suggestionCache) {
			return suggestionCache.get(doc);
		}
	}
	
	@Nullable
	private Suggestion getSuggestion(@NotNull ResultDocument doc) {
		Suggestions suggestions = getSuggestions(doc);
		return suggestions == null ? null : suggestions.name;
	}
	
	/**
	 * Returns the name suggestion for the document, computing it first if
	 * necessary. While computing, the busy cursor is shown and events are
	 * dispatched, so the GUI stays responsive; gives up after a few seconds.
	 */
	@Nullable
	private Suggestion waitForSuggestion(@NotNull ResultDocument doc) {
		if (getSuggestions(doc) != null)
			return getSuggestion(doc);
		synchronized (suggestionCache) {
			if (suggestionsFailed.contains(doc))
				return null;
		}
		requestSuggestion(doc);
		Display display = getControl().getDisplay();
		Shell shell = getControl().getShell();
		Cursor waitCursor = display.getSystemCursor(SWT.CURSOR_WAIT);
		shell.setCursor(waitCursor);
		try {
			long deadline = System.currentTimeMillis() + 8000;
			while (System.currentTimeMillis() < deadline) {
				boolean pending;
				synchronized (suggestionCache) {
					pending = suggestionsInProgress.contains(doc);
				}
				if (!pending)
					break;
				if (!display.readAndDispatch())
					display.sleep();
			}
		}
		finally {
			shell.setCursor(null);
		}
		return getSuggestion(doc);
	}
	
	/**
	 * Computes the name suggestion for the given document in the background,
	 * unless it is already available or being computed.
	 */
	private void requestSuggestion(@NotNull final ResultDocument doc) {
		if (!doc.isPlainFileOnDisk())
			return;
		synchronized (suggestionCache) {
			if (suggestionCache.containsKey(doc) || suggestionsInProgress.contains(doc)
					|| suggestionsFailed.contains(doc))
				return;
			suggestionsInProgress.add(doc);
		}
		Thread thread = new Thread(ResultPanel.class.getName() + " (suggestions)") {
			public void run() {
				Suggestions suggestions = null;
				try {
					Searcher searcher = indexRegistry.getSearcher();
					String text = doc.getText();
					Suggestion name = null;
					try {
						name = NameSuggester.suggest(doc, searcher, text);
					}
					catch (Exception e) {
						// No name suggestion, folders may still work
					}
					// Never suggest the current folder or an inbox folder
					List<File> exclude = InboxFolders.getFolders();
					exclude.add(Util.getParentFile(doc.getPath().getCanonicalFile()));
					List<FolderSuggestion> folders = searcher.suggestFolders(
						text + "\n" + doc.getFilename(), exclude, MAX_FOLDER_SUGGESTIONS);
					suggestions = new Suggestions(name, folders);
				}
				catch (Exception e) {
					// Unparsable document etc.: no suggestions
				}
				catch (OutOfMemoryError e) {
					// Ditto
				}
				synchronized (suggestionCache) {
					suggestionsInProgress.remove(doc);
					if (suggestions != null)
						suggestionCache.put(doc, suggestions);
					else
						suggestionsFailed.add(doc);
				}
				// Wake up a GUI thread that may be waiting for this result
				UtilGui.runAsyncExec(getControl(), new Runnable() {
					public void run() {}
				});
			}
		};
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}
	
	/**
	 * Fills the 'Rename To' submenu with name variants using 2 to 5 of the
	 * suggested keywords for the selected document.
	 */
	private void fillRenameToMenu(@NotNull Menu menu) {
		for (MenuItem item : menu.getItems())
			item.dispose();
		List<ResultDocument> sel = viewer.getSelection();
		if (sel.size() != 1)
			return;
		final ResultDocument doc = sel.get(0);
		Suggestion suggestion = getSuggestion(doc);
		
		String infoText = null;
		boolean inProgress;
		synchronized (suggestionCache) {
			inProgress = suggestionsInProgress.contains(doc);
		}
		if (suggestion == null) {
			requestSuggestion(doc);
			infoText = inProgress || !suggestionsFailed.contains(doc)
				? Msg.suggestions_computing.get()
				: Msg.suggestions_unavailable.get();
		}
		else if (suggestion.getWords().size() < 2) {
			infoText = Msg.suggestions_unavailable.get();
		}
		if (infoText != null) {
			MenuItem item = new MenuItem(menu, SWT.PUSH);
			item.setText(infoText);
			item.setEnabled(false);
			item.setData(new MenuAction(infoText));
			return;
		}
		
		String extension = Util.splitFilename(doc.getFilename())[1];
		int maxWords = Math.min(NameSuggester.MAX_WORDS, suggestion.getWords().size());
		for (int words = 2; words <= maxWords; words++) {
			final String name = suggestion.buildName(words, extension);
			final MenuAction action = new MenuAction(name.replace("&", "&&")) {
				public void run() {
					renameTo(doc, name);
				}
			};
			MenuItem item = new MenuItem(menu, SWT.PUSH);
			item.setText(action.getLabel());
			item.setData(action);
			item.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					action.run();
				}
			});
		}
	}
	
	private void moveSelection() {
		List<ResultDocument> sel = viewer.getSelection();
		if (sel.isEmpty())
			return;
		for (ResultDocument doc : sel) {
			if (!doc.isPlainFileOnDisk()) {
				AppUtil.showError(Msg.only_plain_files.get(), true, true);
				return;
			}
		}
		MoveToFolderDialog dialog = new MoveToFolderDialog(
			getControl().getShell(), indexRegistry.getSearcher(), sel);
		File targetDir = dialog.open();
		if (targetDir == null)
			return;
		moveTo(sel, targetDir);
	}
	
	/**
	 * Moves the given documents' files into the target folder, updating the
	 * result rows and the affected indexes.
	 */
	private void moveTo(@NotNull List<ResultDocument> sel, @NotNull File targetDir) {
		for (ResultDocument doc : sel) {
			if (!doc.isPlainFileOnDisk()) {
				AppUtil.showError(Msg.only_plain_files.get(), true, true);
				return;
			}
		}
		if (!targetDir.isDirectory() && !targetDir.mkdirs()) {
			AppUtil.showError(Msg.file_operation_failed.format(targetDir.getPath()), true, true);
			return;
		}
		
		List<File> affected = new ArrayList<File>();
		List<ResultDocument> moved = new ArrayList<ResultDocument>();
		for (ResultDocument doc : sel) {
			File file = doc.getPath().getCanonicalFile();
			File target = new File(targetDir, file.getName());
			if (Util.isCanonicallyEqual(file, target))
				continue;
			if (!moveFile(file, target))
				break; // Error already shown
			affected.add(file);
			affected.add(target);
			doc.setPathAfterFileOperation(target);
			moved.add(doc);
		}
		if (moved.isEmpty())
			return;
		
		// Documents moved outside of all indexed folders disappear from the
		// results once the index is updated, so remove them right away
		List<ResultDocument> outside = new ArrayList<ResultDocument>();
		for (ResultDocument doc : moved)
			if (findIndexFor(doc.getPath().getCanonicalFile()) == null)
				outside.add(doc);
		viewer.removeElements(outside);
		viewer.refresh();
		updateIndexesFor(affected);
		evtDocumentsChanged.fire(null);
		MoveTargets.addRecent(targetDir);
	}
	
	/**
	 * Moves or renames the given file. Returns true on success; on failure an
	 * error message is shown and false is returned.
	 */
	private static boolean moveFile(@NotNull File source, @NotNull File target) {
		if (target.exists()) {
			AppUtil.showError(Msg.file_exists_error.format(target.getPath()), true, true);
			return false;
		}
		try {
			java.nio.file.Files.move(source.toPath(), target.toPath());
			return true;
		}
		catch (IOException e) {
			AppUtil.showError(Msg.file_operation_failed.format(e.toString()), true, true);
			return false;
		}
	}
	
	@Nullable
	private LuceneIndex findIndexFor(@NotNull File file) {
		for (LuceneIndex index : indexRegistry.getIndexes()) {
			File root = index.getCanonicalRootFile();
			if (Util.contains(root, file) || Util.isCanonicallyEqual(root, file))
				return index;
		}
		return null;
	}
	
	/**
	 * Enqueues updates for all indexes containing any of the given files.
	 * Folder watching would trigger these updates anyway, but only if it is
	 * enabled for the index.
	 */
	private void updateIndexesFor(@NotNull Collection<File> files) {
		Set<LuceneIndex> indexes = new HashSet<LuceneIndex>();
		for (File file : files) {
			LuceneIndex index = findIndexFor(file);
			if (index != null)
				indexes.add(index);
		}
		for (LuceneIndex index : indexes)
			indexRegistry.getQueue().addTask(index, IndexAction.UPDATE);
	}
	
	/*
	 * Drag and drop of result files into other programs, e.g. the file
	 * manager. Without a modifier key the files are moved (the drop target
	 * performs the move); with the Ctrl key held down they are copied, after
	 * which the user is asked whether to keep the original. The allowed
	 * operation is decided when the mouse button goes down, since the drag
	 * source's operations can't be changed while dragging.
	 */
	
	private void initDragSource(@NotNull final Table table) {
		table.addListener(SWT.MouseDown, new Listener() {
			public void handleEvent(org.eclipse.swt.widgets.Event e) {
				boolean copy = (e.stateMask & SWT.CTRL) != 0;
				int operations = copy ? DND.DROP_COPY : DND.DROP_MOVE;
				lastDragWasCopy = copy;
				if (dragSource == null || dragOperations != operations)
					createDragSource(table, operations);
			}
		});
		createDragSource(table, DND.DROP_MOVE);
	}
	
	private void createDragSource(@NotNull final Table table, int operations) {
		if (dragSource != null)
			dragSource.dispose();
		dragOperations = operations;
		dragSource = new DragSource(table, operations);
		dragSource.setTransfer(new Transfer[] { FileTransfer.getInstance() });
		dragSource.addDragListener(new DragSourceAdapter() {
			private List<ResultDocument> draggedDocs = Collections.emptyList();
			private List<File> draggedFiles = Collections.emptyList();
			
			public void dragStart(DragSourceEvent event) {
				draggedDocs = new ArrayList<ResultDocument>();
				draggedFiles = new ArrayList<File>();
				for (ResultDocument doc : viewer.getSelection()) {
					if (!doc.isPlainFileOnDisk())
						continue;
					draggedDocs.add(doc);
					draggedFiles.add(doc.getPath().getCanonicalFile());
				}
				event.doit = !draggedFiles.isEmpty();
			}
			
			public void dragSetData(DragSourceEvent event) {
				if (!FileTransfer.getInstance().isSupportedType(event.dataType))
					return;
				String[] paths = new String[draggedFiles.size()];
				for (int i = 0; i < paths.length; i++)
					paths[i] = draggedFiles.get(i).getPath();
				event.data = paths;
			}
			
			public void dragFinished(DragSourceEvent event) {
				if (draggedFiles.isEmpty())
					return;
				List<File> files = draggedFiles;
				List<ResultDocument> docs = draggedDocs;
				draggedFiles = Collections.emptyList();
				draggedDocs = Collections.emptyList();
				
				if (event.detail == DND.DROP_COPY && lastDragWasCopy) {
					askToRemoveOriginals(files);
				}
				else if (event.detail == DND.DROP_MOVE) {
					// A target that doesn't move the files itself expects
					// the source to delete them
					for (File file : files)
						if (file.exists())
							file.delete();
				}
				// Targets that move the files themselves report no
				// operation; in any case, drop rows of files that are gone
				List<ResultDocument> gone = new ArrayList<ResultDocument>();
				for (int i = 0; i < files.size(); i++)
					if (!files.get(i).exists())
						gone.add(docs.get(i));
				if (!gone.isEmpty()) {
					viewer.removeElements(gone);
					updateIndexesFor(files);
					evtDocumentsChanged.fire(null);
				}
			}
		});
	}
	
	/**
	 * After files were copied by dragging them with Ctrl into another
	 * program, asks whether the originals should be removed, to avoid
	 * duplicate documents.
	 */
	private void askToRemoveOriginals(@NotNull final List<File> files) {
		MultipleChoiceDialog<Boolean> dialog = new MultipleChoiceDialog<Boolean>(getControl().getShell());
		dialog.setTitle(Msg.confirm_operation.get());
		dialog.setText(Msg.copied_keep_original_msg.get());
		dialog.addButton(Msg.keep_both_bt.get(), Boolean.FALSE);
		dialog.addButton(Msg.remove_original_bt.get(), Boolean.TRUE);
		Boolean remove = dialog.open();
		if (remove == null || !remove)
			return;
		List<String> failed = new ArrayList<String>();
		for (File file : files) {
			// The copy may still be in progress; retry a few times
			boolean deleted = false;
			for (int attempt = 0; attempt < 10 && !deleted; attempt++) {
				deleted = !file.exists() || file.delete();
				if (!deleted) {
					try {
						Thread.sleep(300);
					}
					catch (InterruptedException e) {
						break;
					}
				}
			}
			if (!deleted)
				failed.add(file.getPath());
		}
		if (!failed.isEmpty())
			AppUtil.showError(Msg.remove_original_failed.format(Util.join("\n", failed)), true, true);
	}
	
	/**
	 * Fills the 'Move To' submenu with the suggested target folders for the
	 * first selected document, plus an entry for choosing any other folder.
	 */
	/**
	 * Adds menu items for the given folders, skipping those already listed,
	 * and returns the folders that were added.
	 */
	@NotNull
	private List<File> addMoveToItems(	@NotNull Menu menu,
										@NotNull List<File> folders,
										@NotNull List<File> listed) {
		List<File> added = new ArrayList<File>();
		for (File folder : folders) {
			boolean known = false;
			for (File other : listed)
				if (Util.isCanonicallyEqual(other, folder))
					known = true;
			if (known)
				continue;
			addMoveToItem(menu, folder);
			listed.add(folder);
			added.add(folder);
		}
		return added;
	}
	
	/** Adds a menu item that moves the selected documents into the folder. */
	private void addMoveToItem(@NotNull Menu menu, @NotNull final File folder) {
		String[] parts = MoveToFolderDialog.splitFolder(folder);
		String label = parts[1].isEmpty()
			? parts[0]
			: parts[0] + "  (" + parts[1] + ")";
		final MenuAction action = new MenuAction(Img.FOLDER.get(), label.replace("&", "&&")) {
			public void run() {
				moveTo(viewer.getSelection(), folder);
			}
		};
		MenuItem item = new MenuItem(menu, SWT.PUSH);
		item.setText(action.getLabel());
		item.setImage(action.getImage());
		item.setData(action);
		item.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				action.run();
			}
		});
	}
	
	private void fillMoveToMenu(@NotNull Menu menu) {
		for (MenuItem item : menu.getItems())
			item.dispose();
		List<ResultDocument> sel = viewer.getSelection();
		if (sel.isEmpty())
			return;
		final ResultDocument doc = sel.get(0);
		Suggestions suggestions = getSuggestions(doc);
		
		/*
		 * Fixed targets first, then the recently used folders, then the
		 * folders suggested by content; the folder the document is already
		 * in and duplicates are left out.
		 */
		File parent = Util.getParentFile(doc.getPath().getCanonicalFile());
		List<File> listed = new ArrayList<File>();
		listed.add(parent);
		List<File> fixedTargets = addMoveToItems(menu, MoveTargets.loadFolders(), listed);
		if (!fixedTargets.isEmpty())
			new MenuItem(menu, SWT.SEPARATOR);
		List<File> recentTargets = addMoveToItems(menu, MoveTargets.loadRecent(), listed);
		if (!recentTargets.isEmpty())
			new MenuItem(menu, SWT.SEPARATOR);
		
		if (suggestions == null) {
			boolean failed;
			synchronized (suggestionCache) {
				failed = suggestionsFailed.contains(doc);
			}
			if (!failed)
				requestSuggestion(doc);
			String infoText = failed
				? Msg.suggestions_unavailable.get()
				: Msg.suggestions_computing.get();
			MenuItem item = new MenuItem(menu, SWT.PUSH);
			item.setText(infoText);
			item.setEnabled(false);
			item.setData(new MenuAction(infoText));
		}
		else if (suggestions.folders.isEmpty()) {
			String infoText = Msg.suggestions_unavailable.get();
			MenuItem item = new MenuItem(menu, SWT.PUSH);
			item.setText(infoText);
			item.setEnabled(false);
			item.setData(new MenuAction(infoText));
		}
		else {
			List<File> folders = new ArrayList<File>();
			for (FolderSuggestion suggestion : suggestions.folders)
				folders.add(suggestion.getFolder());
			addMoveToItems(menu, folders, listed);
		}
		
		new MenuItem(menu, SWT.SEPARATOR);
		final MenuAction otherAction = new MenuAction(Msg.move_to_other_folder.get()) {
			public void run() {
				moveSelection();
			}
		};
		MenuItem otherItem = new MenuItem(menu, SWT.PUSH);
		otherItem.setText(otherAction.getLabel());
		otherItem.setData(otherAction);
		otherItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				otherAction.run();
			}
		});
	}
	
	private void copyToClipboard() {
		List<ResultDocument> docs = getSelection();
		if (docs.isEmpty())
			return;
		List<File> files = new ArrayList<File>(docs.size());
		for (ResultDocument doc : docs)
			files.add(doc.getPath().getCanonicalFile());
		UtilGui.setClipboard(files);
	}
	
	@NotNull
	public Table getControl() {
		return viewer.getControl();
	}
	
	/**
	 * Selects the documents with the given unique IDs, if present.
	 */
	public void selectByUids(@NotNull Set<String> uids) {
		List<ResultDocument> toSelect = new ArrayList<ResultDocument>();
		for (ResultDocument doc : viewer.getElements())
			if (uids.contains(doc.getUniqueId()))
				toSelect.add(doc);
		viewer.setSelection(toSelect);
	}
	
	public int getItemCount() {
		return viewer.getControl().getItemCount();
	}
	
	@MutableCopy
	@NotNull
	public List<ResultDocument> getSelection() {
		return viewer.getSelection();
	}
	
	// header mode: auto-detect for "files + emails", no auto-detect for files and emails mode
	public void setResults(	@NotNull List<ResultDocument> results,
							@NotNull HeaderMode headerMode) {
		Util.checkNotNull(results, headerMode);
		
		if (this.presetHeaderMode != headerMode) {
			if (headerMode != HeaderMode.FILES_AND_EMAILS)
				updateColumnHeaders(headerMode);
			this.presetHeaderMode = headerMode;
		}
		setActualHeaderMode(results); // TODO post-release-1.1: needs some refactoring
		
		viewer.setRoot(results);
		viewer.scrollToTop();
		
		/*
		 * Setting the results may show or hide the vertical scrollbar, which
		 * changes the available width without a resize event; refit once the
		 * table has been updated.
		 */
		scheduleColumnFit();
	}
	
	private void setActualHeaderMode(List<ResultDocument> elements) {
		if (presetHeaderMode != HeaderMode.FILES_AND_EMAILS) {
			actualHeaderMode = presetHeaderMode;
			return;
		}
		boolean filesFound = false;
		boolean emailsFound = false;
		for (ResultDocument element : elements) {
			if (element.isEmail())
				emailsFound = true;
			else
				filesFound = true;
		}
		actualHeaderMode = HeaderMode.getInstance(filesFound, emailsFound);
		updateColumnHeaders(actualHeaderMode);
	}

	private void updateColumnHeaders(HeaderMode headerMode) {
		for (Column<ResultDocument> column : viewer.getColumns()) {
			if (! (column instanceof VariableHeaderColumn)) continue;
			headerMode.setLabel((VariableHeaderColumn<?>) column);
		}
	}

	/**
	 * Applies the initial sorting to the results: By default, the results are
	 * sorted by last-modified date (or send date for emails), newest first.
	 * This can be overridden via {@link ProgramConf.Int#InitialSorting}.
	 */
	public void applyDefaultSorting() {
		int initialSorting = ProgramConf.Int.InitialSorting.get();
		if (initialSorting == 0)
			viewer.sortByColumn(dateColumn, false);
		else
			sortByColumn(initialSorting);
	}
	
	// sign of given index specifies direction of sorting
	// zero and out-of-range values will be ignored
	// column numbering starts at 1
	// the index points at the column in visual order, not in creation order
	public void sortByColumn(int columnIndex) {
		if (columnIndex == 0)
			return;
		/*
		 * Note: The column to sort by must be specified as an index, since the
		 * column names may change.
		 */
		try {
			int index = Math.abs(columnIndex) - 1;
			List<Column<ResultDocument>> columns = viewer.getColumnsVisualOrder();
			if (index >= columns.size())
				return;
			boolean up = Math.signum(columnIndex) > 0;
			viewer.sortByColumn(columns.get(index), up);
		}
		catch (NumberFormatException e) {
			return;
		}
	}
	
	// Should not be called with emails
	private void launchFiles(@NotNull List<ResultDocument> docs) {
		assert !docs.isEmpty();
		MultiFileLauncher launcher = new MultiFileLauncher();
		Set<FileResource> resources = new HashSet<FileResource>();
		try {
			for (ResultDocument doc : docs) {
				try {
					FileResource fileResource = doc.getFileResource();
					resources.add(fileResource);
					launcher.addFile(fileResource.getFile());
				}
				catch (FileNotFoundException e) {
					launcher.addMissing(doc.getPath().getCanonicalPath());
				}
				catch (ParseException e) {
					AppUtil.showError(e.getMessage(), true, false);
					return;
				}
			}
			if (launcher.launch() && SettingsConf.Bool.HideOnOpen.get())
				evtHideInSystemTray.fire(null);
		}
		finally {
			for (FileResource fileResource : resources)
				fileResource.dispose();
		}
	}

	private static abstract class VariableHeaderColumn<T> extends Column<T> {
		private final String fileHeader;
		private final String emailHeader;
		private final String combinedHeader;
		
		public VariableHeaderColumn(@NotNull String fileHeader,
									@NotNull String emailHeader) {
			super(fileHeader);
			Util.checkNotNull(fileHeader, emailHeader);
			this.fileHeader = fileHeader;
			this.emailHeader = emailHeader;
			combinedHeader = fileHeader + " / " + emailHeader;
		}
	}

}
