/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui.pref;

import java.io.File;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.gui.Bin;
import net.sourceforge.docfetcher.gui.InboxFolders;
import net.sourceforge.docfetcher.gui.MoveTargets;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.gui.dialog.InputDialog;

/**
 * The 'Folders' tab of the preferences dialog: the inbox folders (each
 * shown on its own tab in the main window), the fixed move targets and the
 * folder of the DocFetcher bin.
 */
final class FoldersPanel {

	private final Composite comp;
	private final Table inboxTable;
	private final List<InboxFolders.Entry> inboxEntries;
	private final Table targetTable;
	private final List<String> targetPaths;
	private final Text binText;

	public FoldersPanel(@NotNull Composite parent) {
		Util.checkNotNull(parent);
		comp = new Composite(parent, SWT.NONE);
		comp.setLayout(UtilGui.createGridLayout(2, false, 5, 5));

		// Inbox folders
		Label inboxLabel = new Label(comp, SWT.NONE);
		inboxLabel.setText(Msg.pref_inbox_folders.get());
		inboxLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));

		inboxEntries = InboxFolders.load();
		inboxTable = createTable(comp, 100);
		TableColumn inboxFolderCol = new TableColumn(inboxTable, SWT.LEFT);
		inboxFolderCol.setText(Msg.folder_col.get());
		inboxFolderCol.setWidth(350);
		TableColumn inboxNameCol = new TableColumn(inboxTable, SWT.LEFT);
		inboxNameCol.setText(Msg.inbox_col_tab_name.get());
		inboxNameCol.setWidth(180);

		Composite inboxButtons = createButtonColumn(comp);
		createButton(inboxButtons, Msg.add_inbox_folder_bt.get(), new Runnable() {
			public void run() {
				String path = chooseFolder(Msg.add_inbox_folder_bt.get(), inboxEntries.isEmpty()
					? null : inboxEntries.get(inboxEntries.size() - 1).getPath());
				if (path == null)
					return;
				for (InboxFolders.Entry entry : inboxEntries)
					if (entry.getPath().equals(path))
						return; // Already in the list
				inboxEntries.add(new InboxFolders.Entry(path, ""));
				fillInboxTable();
				selectRow(inboxTable, inboxEntries.size() - 1);
			}
		});
		final Button removeInboxBt = createButton(inboxButtons, Msg.remove_index_bt.get(), new Runnable() {
			public void run() {
				int i = inboxTable.getSelectionIndex();
				if (i < 0)
					return;
				inboxEntries.remove(i);
				fillInboxTable();
				selectRow(inboxTable, Math.min(i, inboxEntries.size() - 1));
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
		createHint(comp, Msg.pref_inbox_folders_hint.get());

		// Fixed move targets
		Label targetLabel = new Label(comp, SWT.NONE);
		targetLabel.setText(Msg.pref_move_targets.get());
		GridData targetLabelData = new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1);
		targetLabelData.verticalIndent = 10;
		targetLabel.setLayoutData(targetLabelData);

		targetPaths = MoveTargets.loadPaths();
		targetTable = createTable(comp, 100);
		TableColumn targetFolderCol = new TableColumn(targetTable, SWT.LEFT);
		targetFolderCol.setText(Msg.folder_col.get());
		targetFolderCol.setWidth(530);

		Composite targetButtons = createButtonColumn(comp);
		createButton(targetButtons, Msg.add_inbox_folder_bt.get(), new Runnable() {
			public void run() {
				String path = chooseFolder(Msg.pref_move_targets.get(), targetPaths.isEmpty()
					? null : targetPaths.get(targetPaths.size() - 1));
				if (path == null || targetPaths.contains(path))
					return;
				targetPaths.add(path);
				fillTargetTable();
				selectRow(targetTable, targetPaths.size() - 1);
			}
		});
		final Button removeTargetBt = createButton(targetButtons, Msg.remove_index_bt.get(), new Runnable() {
			public void run() {
				int i = targetTable.getSelectionIndex();
				if (i < 0)
					return;
				targetPaths.remove(i);
				fillTargetTable();
				selectRow(targetTable, Math.min(i, targetPaths.size() - 1));
			}
		});
		targetTable.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				removeTargetBt.setEnabled(targetTable.getSelectionIndex() >= 0);
			}
		});
		removeTargetBt.setEnabled(false);
		fillTargetTable();
		createHint(comp, Msg.pref_move_targets_hint.get());

		// DocFetcher bin
		Label binLabel = new Label(comp, SWT.NONE);
		binLabel.setText(Msg.pref_bin_folder.get());
		GridData binLabelData = new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1);
		binLabelData.verticalIndent = 10;
		binLabel.setLayoutData(binLabelData);

		binText = new Text(comp, SWT.BORDER | SWT.READ_ONLY);
		binText.setText(SettingsConf.Str.BinFolder.get());
		binText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		Composite binButtons = createButtonColumn(comp);
		createButton(binButtons, Msg.pref_bin_choose.get(), new Runnable() {
			public void run() {
				String current = binText.getText();
				String path = chooseFolder(Msg.pref_bin_folder.get(), current.isEmpty() ? null : current);
				if (path != null)
					binText.setText(path);
			}
		});
		createButton(binButtons, Msg.remove_index_bt.get(), new Runnable() {
			public void run() {
				binText.setText("");
			}
		});
		createHint(comp, Msg.pref_bin_hint.get());
	}

	@NotNull
	private static Table createTable(@NotNull Composite parent, int heightHint) {
		Table table = new Table(parent, SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
		table.setHeaderVisible(true);
		table.setLinesVisible(false);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, false);
		data.heightHint = heightHint;
		table.setLayoutData(data);
		return table;
	}

	@NotNull
	private static Composite createButtonColumn(@NotNull Composite parent) {
		Composite buttons = new Composite(parent, SWT.NONE);
		buttons.setLayout(UtilGui.createGridLayout(1, false, 0, 5));
		buttons.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
		return buttons;
	}

	@NotNull
	private static Button createButton(	@NotNull Composite parent,
										@NotNull String label,
										@NotNull final Runnable action) {
		Button bt = new Button(parent, SWT.PUSH);
		bt.setText(label);
		GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		data.minimumWidth = UtilGui.BTW;
		bt.setLayoutData(data);
		bt.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				action.run();
			}
		});
		return bt;
	}

	private static void createHint(@NotNull Composite parent, @NotNull String text) {
		Label hint = new Label(parent, SWT.WRAP);
		hint.setText(text);
		hint.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND));
		GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		data.widthHint = 400;
		hint.setLayoutData(data);
	}

	private static void selectRow(@NotNull Table table, int index) {
		if (index >= 0)
			table.setSelection(index);
		table.notifyListeners(SWT.Selection, null);
	}

	/** Opens a folder chooser and returns the chosen path, or null. */
	@Nullable
	private String chooseFolder(@NotNull String title, @Nullable String startPath) {
		DirectoryDialog dialog = new DirectoryDialog(comp.getShell());
		dialog.setText(title);
		dialog.setFilterPath(startPath != null
			? startPath
			: SettingsConf.Str.LastIndexedFolder.get());
		String path = dialog.open();
		return path == null ? null : Util.getSystemAbsPath(path);
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

	private void fillTargetTable() {
		targetTable.removeAll();
		for (String path : targetPaths) {
			TableItem item = new TableItem(targetTable, SWT.NONE);
			item.setText(0, path);
			item.setImage(0, Img.FOLDER.get());
		}
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

	@NotNull
	public Control getControl() {
		return comp;
	}

	public void restoreDefaults() {
		inboxEntries.clear();
		fillInboxTable();
		targetPaths.clear();
		fillTargetTable();
		binText.setText("");
	}

	/**
	 * Validates and saves the folders. Returns false (after showing an
	 * error) if one of the folders doesn't exist.
	 */
	public boolean save() {
		for (InboxFolders.Entry entry : inboxEntries) {
			if (!entry.getFolder().isDirectory()) {
				AppUtil.showError(Msg.inbox_folder_missing.format(entry.getPath()), true, true);
				return false;
			}
		}
		for (String path : targetPaths) {
			if (!Util.getCanonicalFile(path).isDirectory()) {
				AppUtil.showError(Msg.folder_not_found.get() + "\n" + path, true, true);
				return false;
			}
		}
		String binPath = binText.getText().trim();
		if (!binPath.isEmpty()) {
			File bin = Util.getCanonicalFile(binPath);
			for (InboxFolders.Entry entry : inboxEntries) {
				File inbox = entry.getFolder();
				if (Bin.contains(bin, inbox) || Util.contains(inbox, bin)) {
					AppUtil.showError(Msg.pref_bin_conflict.format(entry.getPath()), true, true);
					return false;
				}
			}
			if (bin.getParentFile() == null || (!bin.isDirectory() && !bin.mkdirs())) {
				AppUtil.showError(Msg.folder_not_found.get() + "\n" + binPath, true, true);
				return false;
			}
		}
		InboxFolders.save(inboxEntries);
		MoveTargets.save(targetPaths);
		SettingsConf.Str.BinFolder.set(binPath);
		return true;
	}

}
