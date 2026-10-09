/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.model.search.Searcher;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Finds documents with identical or nearly identical text in the indexes
 * (menu File > Find Duplicates...) and shows them in groups, the oldest
 * document of a group first. Copies can be checked and moved to the recycle
 * bin; at least one document of each group always stays. The search runs in
 * the background and stops when the dialog is closed.
 */
final class DuplicatesDialog {

	/** Shows documents in the main window: the Search tab, the preview, the folder browsers. */
	final Event<List<ResultDocument>> evtShow = new Event<List<ResultDocument>>();

	private final IndexRegistry registry;
	private final Shell shell;
	private final Label status;
	private final Tree tree;
	private final Button showBt;
	private final Button folderBt;
	private final Button deleteBt;
	private volatile boolean canceled = false;

	DuplicatesDialog(@NotNull Shell parent, @NotNull IndexRegistry registry) {
		this.registry = registry;
		shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE);
		shell.setText(Msg.dup_title.get());
		shell.setLayout(new GridLayout(1, false));

		status = new Label(shell, SWT.WRAP);
		GridData statusData = new GridData(SWT.FILL, SWT.TOP, true, false);
		statusData.widthHint = 400;
		status.setLayoutData(statusData);
		status.setText(Msg.dup_progress.format(0));

		tree = new Tree(shell, SWT.CHECK | SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
		tree.setHeaderVisible(true);
		tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		int dpi = shell.getDisplay().getDPI().x;
		column(Msg.filename.get(), dpi * 3);
		column(Msg.path.get(), dpi * 3);
		column(Msg.last_modified.get(), dpi * 3 / 2);
		column(Msg.size.get(), dpi);
		tree.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (e.detail == SWT.CHECK)
					onChecked((TreeItem) e.item);
				updateButtons();
			}
			public void widgetDefaultSelected(SelectionEvent e) {
				show();
			}
		});

		Composite buttons = new Composite(shell, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
		buttons.setLayout(UtilGui.createGridLayout(4, false, 0, 5));
		showBt = button(buttons, Msg.dup_show.get(), new Runnable() {
			public void run() {
				show();
			}
		});
		folderBt = button(buttons, Util.IS_WINDOWS ? Msg.open_in_explorer.get() : Msg.open_in_file_manager.get(),
			new Runnable() {
				public void run() {
					openFolder();
				}
			});
		deleteBt = button(buttons, Msg.dup_delete.get(), new Runnable() {
			public void run() {
				deleteChecked();
			}
		});
		Button close = button(buttons, Msg.close.get(), new Runnable() {
			public void run() {
				shell.close();
			}
		});
		close.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
		updateButtons();

		shell.setSize(dpi * 9, dpi * 6);
		shell.setLocation(parent.getLocation().x + 40, parent.getLocation().y + 40);
		shell.addListener(SWT.Dispose, new org.eclipse.swt.widgets.Listener() {
			public void handleEvent(org.eclipse.swt.widgets.Event event) {
				canceled = true; // Stops the search
			}
		});
	}

	private void column(@NotNull String label, int width) {
		TreeColumn column = new TreeColumn(tree, SWT.LEFT);
		column.setText(label);
		column.setWidth(width);
	}

	@NotNull
	private static Button button(@NotNull Composite parent, @NotNull String label, @NotNull final Runnable action) {
		return UtilGui.createPushButton(parent, label, new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				action.run();
			}
		});
	}

	/** Opens the dialog and starts the search in the background. */
	void open() {
		shell.open();
		Thread thread = new Thread(DuplicatesDialog.class.getName()) {
			public void run() {
				find();
			}
		};
		thread.setDaemon(true);
		thread.start();
	}

	private void find() {
		final List<Searcher.DuplicateGroup> groups;
		try {
			groups = registry.getSearcher().findDuplicates(new Cancelable() {
				public boolean isCanceled() {
					return canceled;
				}
			}, new Searcher.DuplicateProgress() {
				public void update(final int done, final int total) {
					UtilGui.runAsyncExec(status, new Runnable() {
						public void run() {
							status.setText(Msg.dup_progress.format(total == 0 ? 100 : (int) (100L * done / total)));
						}
					});
				}
			});
		}
		catch (final Exception e) {
			UtilGui.runAsyncExec(status, new Runnable() {
				public void run() {
					status.setText(Msg.dup_failed.format(e.getMessage()));
				}
			});
			return;
		}
		UtilGui.runAsyncExec(tree, new Runnable() {
			public void run() {
				fill(groups);
			}
		});
	}

	private void fill(@NotNull List<Searcher.DuplicateGroup> groups) {
		DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
		for (Searcher.DuplicateGroup group : groups) {
			TreeItem groupItem = new TreeItem(tree, SWT.NONE);
			groupItem.setData(group);
			for (ResultDocument doc : group.docs) {
				TreeItem item = new TreeItem(groupItem, SWT.NONE);
				item.setData(doc);
				File file = new File(doc.getPathStr());
				Date date = doc.getLastModified();
				item.setText(new String[] {
					doc.getFilename(),
					file.getParent() == null ? "" : file.getParent(),
					date == null ? "" : dateFormat.format(date),
					doc.getSizeInKB() + " KB" });
			}
			label(groupItem);
			groupItem.setExpanded(true);
		}
		updateStatus();
	}

	/** The group's line: identical or nearly identical, and how many documents. */
	private static void label(@NotNull TreeItem groupItem) {
		Searcher.DuplicateGroup group = (Searcher.DuplicateGroup) groupItem.getData();
		groupItem.setText(0, (group.identical ? Msg.dup_identical : Msg.dup_near).format(groupItem.getItemCount()));
	}

	private void updateStatus() {
		status.setText(tree.getItemCount() == 0 ? Msg.dup_none.get() : Msg.dup_found.format(tree.getItemCount()));
		shell.layout(true, true);
	}

	/** Checking a group checks all its documents but the oldest (the first). */
	private void onChecked(@NotNull TreeItem item) {
		if (item.getParentItem() == null) {
			TreeItem[] docs = item.getItems();
			for (int i = 0; i < docs.length; i++)
				docs[i].setChecked(i > 0 && item.getChecked());
		}
	}

	@Nullable
	private TreeItem selectedDocItem() {
		TreeItem[] selection = tree.getSelection();
		if (selection.length == 0)
			return null;
		TreeItem item = selection[0];
		return item.getParentItem() == null ? (item.getItemCount() > 0 ? item.getItem(0) : null) : item;
	}

	private void updateButtons() {
		boolean selected = selectedDocItem() != null;
		showBt.setEnabled(selected);
		folderBt.setEnabled(selected);
		deleteBt.setEnabled(!checkedDocs().isEmpty());
	}

	/** Shows the group of the selected document in the main window, the selected one first. */
	private void show() {
		TreeItem item = selectedDocItem();
		if (item == null)
			return;
		List<ResultDocument> docs = new ArrayList<ResultDocument>();
		docs.add((ResultDocument) item.getData());
		for (TreeItem other : item.getParentItem().getItems())
			if (other != item)
				docs.add((ResultDocument) other.getData());
		evtShow.fire(docs);
	}

	private void openFolder() {
		TreeItem item = selectedDocItem();
		if (item == null)
			return;
		File file = new File(((ResultDocument) item.getData()).getPathStr());
		if (!Util.winOpenDir(file.getPath()) && file.getParentFile() != null)
			UtilGui.launch(file.getParent());
	}

	@NotNull
	private List<TreeItem> checkedDocs() {
		List<TreeItem> checked = new ArrayList<TreeItem>();
		for (TreeItem groupItem : tree.getItems())
			for (TreeItem item : groupItem.getItems())
				if (item.getChecked())
					checked.add(item);
		return checked;
	}

	/**
	 * Moves the checked documents to the recycle bin (after the usual
	 * confirmation) - but only if each group keeps at least one document.
	 */
	private void deleteChecked() {
		List<TreeItem> checked = checkedDocs();
		if (checked.isEmpty())
			return;
		for (TreeItem groupItem : tree.getItems()) {
			boolean allChecked = true;
			for (TreeItem item : groupItem.getItems())
				allChecked &= item.getChecked();
			if (allChecked) {
				AppUtil.showError(Msg.dup_keep_one.format(groupItem.getItem(0).getText(0)), true, true);
				return;
			}
		}
		List<ResultDocument> docs = new ArrayList<ResultDocument>();
		for (TreeItem item : checked)
			docs.add((ResultDocument) item.getData());
		List<ResultDocument> deleted = ResultPanel.deleteDocuments(docs, registry);
		for (TreeItem item : checked) {
			if (item.isDisposed() || !deleted.contains(item.getData()))
				continue;
			TreeItem groupItem = item.getParentItem();
			item.dispose();
			if (groupItem.getItemCount() < 2)
				groupItem.dispose();
			else
				label(groupItem);
		}
		updateStatus();
		updateButtons();
	}

}
