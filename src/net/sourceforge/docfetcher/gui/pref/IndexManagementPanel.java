/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui.pref;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
import org.eclipse.swt.widgets.Listener;
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
import net.sourceforge.docfetcher.gui.InboxFolders;
import net.sourceforge.docfetcher.gui.filter.IndexPanel;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.IndexRegistry.ExistingIndexesHandler;
import net.sourceforge.docfetcher.model.LuceneIndex;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.collect.AlphanumComparator;
import net.sourceforge.docfetcher.util.gui.dialog.InputDialog;

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
	private final Table inboxTable;
	private final List<InboxFolders.Entry> inboxEntries;
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
		locationCol.setWidth(350);

		table.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				updateButtonStates();
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
		
		// Inbox folders
		Label inboxLabel = new Label(comp, SWT.NONE);
		inboxLabel.setText(Msg.pref_inbox_folders.get());
		inboxLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));
		
		inboxEntries = InboxFolders.load();
		inboxTable = new Table(comp, SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
		inboxTable.setHeaderVisible(true);
		inboxTable.setLinesVisible(false);
		GridData inboxTableData = new GridData(SWT.FILL, SWT.FILL, true, false);
		inboxTableData.heightHint = 80;
		inboxTable.setLayoutData(inboxTableData);
		TableColumn inboxFolderCol = new TableColumn(inboxTable, SWT.LEFT);
		inboxFolderCol.setText(Msg.folder_col.get());
		inboxFolderCol.setWidth(350);
		TableColumn inboxNameCol = new TableColumn(inboxTable, SWT.LEFT);
		inboxNameCol.setText(Msg.inbox_col_tab_name.get());
		inboxNameCol.setWidth(180);
		
		Composite inboxButtons = new Composite(comp, SWT.NONE);
		inboxButtons.setLayout(UtilGui.createGridLayout(1, false, 0, 5));
		inboxButtons.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
		createButton(inboxButtons, Msg.add_inbox_folder_bt.get(), new Runnable() {
			public void run() {
				addInboxFolder();
			}
		});
		final Button removeInboxBt = createButton(inboxButtons, Msg.remove_index_bt.get(), new Runnable() {
			public void run() {
				int i = inboxTable.getSelectionIndex();
				if (i < 0)
					return;
				inboxEntries.remove(i);
				fillInboxTable();
				inboxTable.setSelection(Math.min(i, inboxEntries.size() - 1));
				inboxTable.notifyListeners(SWT.Selection, null);
			}
		});
		final Button renameInboxBt = createButton(inboxButtons, Msg.rename_index_bt.get(), new Runnable() {
			public void run() {
				renameInboxFolder();
			}
		});
		inboxTable.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				boolean selected = inboxTable.getSelectionIndex() >= 0;
				removeInboxBt.setEnabled(selected);
				renameInboxBt.setEnabled(selected);
			}
		});
		inboxTable.addListener(SWT.MouseDoubleClick, new Listener() {
			public void handleEvent(org.eclipse.swt.widgets.Event e) {
				renameInboxFolder();
			}
		});
		removeInboxBt.setEnabled(false);
		renameInboxBt.setEnabled(false);
		fillInboxTable();
		
		Label inboxHint = new Label(comp, SWT.WRAP);
		inboxHint.setText(Msg.pref_inbox_folders_hint.get());
		inboxHint.setForeground(comp.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND));
		GridData inboxHintData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		inboxHintData.widthHint = 400;
		inboxHint.setLayoutData(inboxHintData);

		// Fill table and keep it in sync with the index registry
		indexRegistry.addListeners(new ExistingIndexesHandler() {
			public void handleExistingIndexes(List<LuceneIndex> indexes) {
				fillTable(indexes);
			}
		}, addedListener, removedListener);

		comp.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				indexRegistry.removeListeners(addedListener, removedListener);
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
		table.removeAll();
		for (LuceneIndex index : indexes) {
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(0, index.getRootFolder().getDisplayName());
			item.setText(1, index.getRootFolder().getPath().getPath());
			item.setImage(0, (index.isEmailIndex() ? Img.EMAIL : Img.FOLDER).get());
			item.setData(index);
		}
		updateButtonStates();
	}

	private void refreshTable() {
		if (table.isDisposed())
			return;
		fillTable(indexRegistry.getIndexes());
	}

	private void updateButtonStates() {
		int selCount = table.getSelectionCount();
		for (Button bt : selectionButtons)
			bt.setEnabled(selCount > 0);
		if (renameBt != null)
			renameBt.setEnabled(selCount == 1);
	}

	@NotNull
	private List<LuceneIndex> getSelectedIndexes() {
		TableItem[] items = table.getSelection();
		List<LuceneIndex> indexes = new ArrayList<LuceneIndex>(items.length);
		for (TableItem item : items)
			indexes.add((LuceneIndex) item.getData());
		return indexes;
	}

	@NotNull
	public Control getControl() {
		return comp;
	}

	public void restoreDefaultFolder() {
		folderField.setText(SettingsConf.Str.IndexFolder.defaultValue);
		inboxEntries.clear();
		fillInboxTable();
	}
	
	private void fillInboxTable() {
		inboxTable.removeAll();
		for (InboxFolders.Entry entry : inboxEntries) {
			TableItem item = new TableItem(inboxTable, SWT.NONE);
			item.setText(0, entry.getPath());
			item.setText(1, entry.getTabName());
			item.setImage(0, Img.FOLDER.get());
		}
	}
	
	/** Lets the user choose a folder and adds it to the inbox folders. */
	private void addInboxFolder() {
		DirectoryDialog dialog = new DirectoryDialog(comp.getShell());
		dialog.setText(Msg.add_inbox_folder_bt.get());
		dialog.setFilterPath(inboxEntries.isEmpty()
			? SettingsConf.Str.LastIndexedFolder.get()
			: inboxEntries.get(inboxEntries.size() - 1).getPath());
		String path = dialog.open();
		if (path == null)
			return;
		path = Util.getSystemAbsPath(path);
		for (InboxFolders.Entry entry : inboxEntries)
			if (entry.getPath().equals(path))
				return; // Already in the list
		inboxEntries.add(new InboxFolders.Entry(path, ""));
		fillInboxTable();
		inboxTable.setSelection(inboxEntries.size() - 1);
		inboxTable.notifyListeners(SWT.Selection, null);
	}
	
	/** Asks the user for the tab name of the selected inbox folder. */
	private void renameInboxFolder() {
		int i = inboxTable.getSelectionIndex();
		if (i < 0)
			return;
		InboxFolders.Entry entry = inboxEntries.get(i);
		InputDialog dialog = new InputDialog(comp.getShell(),
			Msg.rename_inbox_folder_title.get(), Msg.rename_inbox_folder_msg.get(),
			entry.getName());
		String name = dialog.open();
		if (name == null)
			return;
		inboxEntries.set(i, new InboxFolders.Entry(entry.getPath(), name));
		fillInboxTable();
		inboxTable.setSelection(i);
	}
	
	/**
	 * Validates and saves the inbox folders. Returns false (after showing an
	 * error) if one of the folders doesn't exist.
	 */
	public boolean saveInboxFolders() {
		for (InboxFolders.Entry entry : inboxEntries) {
			if (!entry.getFolder().isDirectory()) {
				AppUtil.showError(Msg.inbox_folder_missing.format(entry.getPath()), true, true);
				return false;
			}
		}
		InboxFolders.save(inboxEntries);
		return true;
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
