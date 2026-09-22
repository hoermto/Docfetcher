/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui.pref;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.ProgramConf;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.enums.SystemConf;
import net.sourceforge.docfetcher.gui.Application;
import net.sourceforge.docfetcher.gui.filter.IndexPanel;
import net.sourceforge.docfetcher.model.IndexRegistry.ExistingIndexesHandler;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.LuceneIndex;
import net.sourceforge.docfetcher.model.index.IndexingQueue;
import net.sourceforge.docfetcher.model.index.Task;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.collect.AlphanumComparator;

/**
 * Contents of the 'Indexes' tab on the preferences dialog: A table of all
 * indexes with buttons for creating, updating, rebuilding, renaming and
 * removing indexes, plus a field for choosing the folder where the index files
 * are stored.
 * <p>
 * The index management operations are delegated to the {@link IndexPanel},
 * which owns the indexing dialog.
 */
final class IndexManagementPanel {

	private final Composite comp;
	private final IndexPanel indexPanel;
	private final IndexRegistry indexRegistry;
	private final File defaultIndexDir;
	private final Table table;
	private final Text folderField;
	private final List<Button> selectionButtons = new ArrayList<Button>();
	@Nullable private Button renameBt;

	private final Event.Listener<LuceneIndex> addedListener = new Event.Listener<LuceneIndex>() {
		public void update(LuceneIndex eventData) {
			UtilGui.runAsyncExec(table, new Runnable() {
				public void run() {
					refreshTable();
				}
			});
		}
	};

	// Indexing tasks by index, for the status column
	private final Map<LuceneIndex, Task> tasksByIndex = new HashMap<LuceneIndex, Task>();
	// Indexes listed in the table that aren't registered yet (being created)
	private final Set<LuceneIndex> pendingIndexes = new HashSet<LuceneIndex>();
	
	private final Event.Listener<Task> taskAddedListener = new Event.Listener<Task>() {
		public void update(Task task) {
			synchronized (tasksByIndex) {
				tasksByIndex.put(task.getLuceneIndex(), task);
			}
			scheduleTableRefresh(); // Indexes being created get a row
		}
	};
	
	private final Event.Listener<Task> taskRemovedListener = new Event.Listener<Task>() {
		public void update(Task task) {
			synchronized (tasksByIndex) {
				if (tasksByIndex.get(task.getLuceneIndex()) == task)
					tasksByIndex.remove(task.getLuceneIndex());
			}
			scheduleTableRefresh();
		}
	};
	
	private void scheduleTableRefresh() {
		UtilGui.runAsyncExec(table, new Runnable() {
			public void run() {
				refreshTable();
			}
		});
	}
	
	private final Event.Listener<Task> taskProgressListener = new Event.Listener<Task>() {
		public void update(Task task) {
			scheduleStatusRefresh();
		}
	};
	
	private final Event.Listener<List<LuceneIndex>> removedListener = new Event.Listener<List<LuceneIndex>>() {
		public void update(List<LuceneIndex> eventData) {
			UtilGui.runAsyncExec(table, new Runnable() {
				public void run() {
					refreshTable();
				}
			});
		}
	};

	/**
	 * @param defaultIndexDir
	 *            The index folder that is used when no custom index folder has
	 *            been set in the preferences.
	 */
	public IndexManagementPanel(	@NotNull Composite parent,
									@NotNull IndexPanel indexPanel,
									@NotNull File defaultIndexDir) {
		Util.checkNotNull(parent, indexPanel, defaultIndexDir);
		this.indexPanel = indexPanel;
		this.indexRegistry = indexPanel.getIndexRegistry();
		this.defaultIndexDir = defaultIndexDir;

		comp = new Composite(parent, SWT.NONE);
		comp.setLayout(UtilGui.createGridLayout(2, false, 5, 5));

		// Index table
		table = new Table(comp, SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
		table.setHeaderVisible(true);
		table.setLinesVisible(false);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
		tableData.heightHint = 120;
		table.setLayoutData(tableData);

		TableColumn nameCol = new TableColumn(table, SWT.LEFT);
		nameCol.setText(Msg.index_col_name.get());
		nameCol.setWidth(180);
		TableColumn locationCol = new TableColumn(table, SWT.LEFT);
		locationCol.setText(Msg.index_col_location.get());
		locationCol.setWidth(300);
		TableColumn statusCol = new TableColumn(table, SWT.LEFT);
		statusCol.setText(Msg.index_col_status.get());
		statusCol.setWidth(150);

		table.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				updateButtonStates();
			}
			public void widgetDefaultSelected(SelectionEvent e) {
				// Double-click on an index with a running or pending task
				// shows the indexing window, e.g. to press 'Run'
				TableItem[] sel = table.getSelection();
				if (sel.length == 0)
					return;
				LuceneIndex index = (LuceneIndex) sel[0].getData();
				boolean hasTask;
				synchronized (tasksByIndex) {
					hasTask = tasksByIndex.containsKey(index);
				}
				if (hasTask)
					indexPanel.openIndexingDialog();
			}
		});

		// Buttons
		Composite buttonComp = new Composite(comp, SWT.NONE);
		buttonComp.setLayout(UtilGui.createGridLayout(1, false, 0, 5));
		buttonComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
		createButtons(buttonComp);

		// Index folder
		Label folderLabel = new Label(comp, SWT.NONE);
		folderLabel.setText(Msg.pref_index_folder.get());
		folderLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));

		Composite folderComp = new Composite(comp, SWT.NONE);
		folderComp.setLayout(UtilGui.createGridLayout(2, false, 0, 5));
		folderComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		folderField = new Text(folderComp, SWT.BORDER | SWT.SINGLE);
		folderField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		folderField.setText(SettingsConf.Str.IndexFolder.get().trim());
		folderField.setMessage(Util.getSystemAbsPath(defaultIndexDir));

		Button browseBt = UtilGui.createPushButton(folderComp, Msg.browse.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				DirectoryDialog dialog = new DirectoryDialog(comp.getShell());
				dialog.setText(Msg.pref_index_folder.get());
				String current = folderField.getText().trim();
				if (current.isEmpty())
					current = Util.getSystemAbsPath(indexRegistry.getIndexParentDir());
				dialog.setFilterPath(current);
				String path = dialog.open();
				if (path != null)
					folderField.setText(Util.getSystemAbsPath(path));
			}
		});
		browseBt.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

		Label hintLabel = new Label(comp, SWT.WRAP);
		hintLabel.setText(Msg.pref_index_folder_hint.get());
		hintLabel.setForeground(comp.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND));
		GridData hintData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		hintData.widthHint = 400;
		hintLabel.setLayoutData(hintData);
		
		// Fill table and keep it in sync with the index registry
		indexRegistry.addListeners(new ExistingIndexesHandler() {
			public void handleExistingIndexes(List<LuceneIndex> indexes) {
				fillTable(indexes);
			}
		}, addedListener, removedListener);
		
		// Keep the status column in sync with the indexing queue
		final IndexingQueue queue = indexRegistry.getQueue();
		queue.addListeners(new IndexingQueue.ExistingTasksHandler() {
			public void handleExistingTasks(List<Task> tasks) {
				synchronized (tasksByIndex) {
					for (Task task : tasks)
						tasksByIndex.put(task.getLuceneIndex(), task);
				}
			}
		}, taskAddedListener, taskRemovedListener);
		queue.evtProgress.add(taskProgressListener);

		comp.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				indexRegistry.removeListeners(addedListener, removedListener);
				queue.removeListeners(taskAddedListener, taskRemovedListener);
				queue.evtProgress.remove(taskProgressListener);
			}
		});

		updateButtonStates();
	}

	private void createButtons(@NotNull final Composite parent) {
		if (ProgramConf.Bool.AllowIndexCreation.get()) {
			final Button createBt = createButton(parent, Msg.create_index_bt.get(), null);
			final Menu createMenu = new Menu(createBt);

			addMenuItem(createMenu, Img.FOLDER.get(), Msg.folder.get(), new Runnable() {
				public void run() {
					indexPanel.createIndexFromFolder(comp.getShell());
				}
			});
			addMenuItem(createMenu, Img.PACKAGE.get(), Msg.archive.get(), new Runnable() {
				public void run() {
					indexPanel.createIndexFromArchive(comp.getShell());
				}
			});
			addMenuItem(createMenu, Img.EMAIL.get(), Msg.outlook_pst.get(), new Runnable() {
				public void run() {
					indexPanel.createIndexFromOutlook(comp.getShell());
				}
			});
			String clipboardLabel = Util.IS_MAC_OS_X
				? Msg.clipboard_macosx.get()
				: Msg.clipboard.get();
			addMenuItem(createMenu, Img.CLIPBOARD.get(), stripAccelerator(clipboardLabel), new Runnable() {
				public void run() {
					indexPanel.createIndexFromClipboard(comp.getShell());
				}
			});

			createBt.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					// Show the menu directly below the button
					Rectangle bounds = createBt.getBounds();
					Point pt = createBt.getParent().toDisplay(bounds.x, bounds.y + bounds.height);
					createMenu.setLocation(pt);
					createMenu.setVisible(true);
				}
			});
		}

		if (ProgramConf.Bool.AllowIndexUpdate.get()) {
			selectionButtons.add(createButton(parent, Msg.update_index_bt.get(), new Runnable() {
				public void run() {
					indexPanel.updateIndexes(getSelectedIndexes());
				}
			}));
		}

		if (ProgramConf.Bool.AllowIndexRebuild.get()) {
			selectionButtons.add(createButton(parent, Msg.rebuild_index_bt.get(), new Runnable() {
				public void run() {
					indexPanel.rebuildIndexes(getSelectedIndexes());
				}
			}));
		}

		if (ProgramConf.Bool.AllowIndexRenaming.get()) {
			renameBt = createButton(parent, Msg.rename_index_bt.get(), new Runnable() {
				public void run() {
					List<LuceneIndex> sel = getSelectedIndexes();
					if (sel.size() != 1)
						return;
					if (indexPanel.renameIndex(comp.getShell(), sel.get(0)))
						refreshTable();
				}
			});
		}

		if (ProgramConf.Bool.AllowIndexDeletion.get()) {
			selectionButtons.add(createButton(parent, Msg.remove_index_bt.get(), new Runnable() {
				public void run() {
					indexPanel.removeIndexes(getSelectedIndexes());
				}
			}));
			createButton(parent, Msg.remove_orphaned_indexes_bt.get(), new Runnable() {
				public void run() {
					indexPanel.removeOrphanedIndexes();
				}
			});
		}
	}

	@NotNull
	private static Button createButton(	@NotNull Composite parent,
										@NotNull String label,
										@Nullable final Runnable action) {
		Button bt = new Button(parent, SWT.PUSH);
		bt.setText(label);
		GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		data.minimumWidth = UtilGui.BTW;
		bt.setLayoutData(data);
		if (action != null) {
			bt.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					action.run();
				}
			});
		}
		return bt;
	}

	private static void addMenuItem(@NotNull Menu menu,
									@Nullable org.eclipse.swt.graphics.Image image,
									@NotNull String label,
									@NotNull final Runnable action) {
		MenuItem item = new MenuItem(menu, SWT.PUSH);
		item.setText(label);
		item.setImage(image);
		item.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				action.run();
			}
		});
	}

	// Removes a keyboard shortcut suffix such as "\tCtrl+V" from a menu label
	@NotNull
	private static String stripAccelerator(@NotNull String label) {
		int i = label.indexOf('\t');
		return i < 0 ? label : label.substring(0, i);
	}

	private void fillTable(@NotNull List<LuceneIndex> indexes) {
		indexes = new ArrayList<LuceneIndex>(indexes);
		Collections.sort(indexes, new Comparator<LuceneIndex>() {
			public int compare(LuceneIndex o1, LuceneIndex o2) {
				return AlphanumComparator.ignoreCaseInstance.compare(
					o1.getRootFolder().getDisplayName(),
					o2.getRootFolder().getDisplayName());
			}
		});
		// Indexes that are being created aren't registered yet; list them too
		List<LuceneIndex> pending = new ArrayList<LuceneIndex>();
		synchronized (tasksByIndex) {
			for (LuceneIndex index : tasksByIndex.keySet())
				if (!indexes.contains(index))
					pending.add(index);
		}
		pendingIndexes.clear();
		pendingIndexes.addAll(pending);
		indexes.addAll(pending);
		
		table.removeAll();
		for (LuceneIndex index : indexes) {
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(0, index.getRootFolder().getDisplayName());
			item.setText(1, index.getRootFolder().getPath().getPath());
			item.setText(2, getStatusText(index));
			item.setImage(0, (index.isEmailIndex() ? Img.EMAIL : Img.FOLDER).get());
			item.setData(index);
		}
		updateButtonStates();
	}
	
	/** Refreshes the status column from the GUI thread. */
	private void scheduleStatusRefresh() {
		UtilGui.runAsyncExec(table, new Runnable() {
			public void run() {
				for (TableItem item : table.getItems())
					item.setText(2, getStatusText((LuceneIndex) item.getData()));
			}
		});
	}
	
	/**
	 * Returns the status of the index: the state of its indexing task, or
	 * the time of its last update.
	 */
	@NotNull
	private String getStatusText(@NotNull LuceneIndex index) {
		Task task;
		synchronized (tasksByIndex) {
			task = tasksByIndex.get(index);
		}
		if (task != null)
			return Application.getTaskStatusText(task);
		long lastUpdated = index.getLastUpdated();
		if (lastUpdated <= 0)
			return "";
		return Msg.index_status_updated.format(
			DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(lastUpdated)));
	}

	private void refreshTable() {
		if (table.isDisposed())
			return;
		fillTable(indexRegistry.getIndexes());
	}

	private void updateButtonStates() {
		int selCount = getSelectedIndexes().size();
		for (Button bt : selectionButtons)
			bt.setEnabled(selCount > 0);
		if (renameBt != null)
			renameBt.setEnabled(selCount == 1);
	}

	/** Returns the selected registered indexes (not the ones being created). */
	@NotNull
	private List<LuceneIndex> getSelectedIndexes() {
		TableItem[] items = table.getSelection();
		List<LuceneIndex> indexes = new ArrayList<LuceneIndex>(items.length);
		for (TableItem item : items) {
			LuceneIndex index = (LuceneIndex) item.getData();
			if (!pendingIndexes.contains(index))
				indexes.add(index);
		}
		return indexes;
	}

	@NotNull
	public Control getControl() {
		return comp;
	}

	public void restoreDefaultFolder() {
		folderField.setText(SettingsConf.Str.IndexFolder.defaultValue);
	}
	
	/**
	 * Validates the index folder entered by the user. Returns true if the value
	 * is acceptable, otherwise an error message is shown and false is returned.
	 */
	public boolean validateFolder() {
		String value = folderField.getText().trim();
		if (value.isEmpty())
			return true;
		File dir = new File(value);
		dir.mkdirs();
		if (!dir.isDirectory() || !AppUtil.canWriteToDirectory(dir)) {
			AppUtil.showError(Msg.invalid_index_folder.format(value), true, true);
			return false;
		}
		return true;
	}

	/**
	 * Saves the index folder setting. If the effective index folder changes,
	 * the user is asked whether the existing indexes should be moved to the new
	 * folder on the next program start, and is informed that a restart is
	 * required. Must be called after {@link #validateFolder()} returned true.
	 */
	public void saveFolder() {
		String newValue = folderField.getText().trim();
		String oldValue = SettingsConf.Str.IndexFolder.get().trim();
		if (newValue.equals(oldValue))
			return;

		File currentDir = indexRegistry.getIndexParentDir();
		File newDir = newValue.isEmpty()
			? defaultIndexDir
			: Util.getCanonicalFile(newValue);

		SettingsConf.Str.IndexFolder.set(newValue);

		if (Util.isCanonicallyEqual(currentDir, newDir)) {
			// Nothing changes effectively, e.g. the user entered the default
			// folder explicitly
			SettingsConf.Str.PendingIndexMoveFrom.set("");
			return;
		}

		String appName = SystemConf.Str.ProgramName.get();
		boolean move = false;
		if (!indexRegistry.getIndexes().isEmpty()) {
			String msg = Msg.move_indexes_to_new_folder.format(appName);
			move = AppUtil.showConfirmation(msg, false);
		}
		SettingsConf.Str.PendingIndexMoveFrom.set(
			move ? Util.getSystemAbsPath(currentDir) : "");

		AppUtil.showInfo(Msg.index_folder_changed_restart.format(appName));
	}

}
