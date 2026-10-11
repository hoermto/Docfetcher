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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.util.UtilFile;
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

/**
 * An inbox tab: lists all files located directly in the inbox folder (e.g.
 * new documents from a scanner), indexed or not, in a result table, so that
 * they can be previewed, renamed and moved like search results. The tab of
 * the DocFetcher bin is one too, which lists the deleted folders as well.
 */
public final class InboxPanel {

	private final Composite comp;
	private final Label folderLabel;
	private final Label countLabel;
	private final Label messageLabel;
	private final ResultPanel resultPanel;
	private final IndexRegistry indexRegistry;
	private final boolean bin;
	@Nullable private Composite buttonRow; // Created by addButton
	@Nullable private volatile File folder;
	private volatile int refreshCount = 0;

	public InboxPanel(@NotNull Composite parent, @NotNull IndexRegistry indexRegistry) {
		this(parent, indexRegistry, false);
	}

	/**
	 * @param bin
	 *            If true, the panel shows the DocFetcher bin: the folders in
	 *            it too, and the size of everything.
	 */
	public InboxPanel(@NotNull Composite parent, @NotNull IndexRegistry indexRegistry, boolean bin) {
		Util.checkNotNull(parent, indexRegistry);
		this.indexRegistry = indexRegistry;
		this.bin = bin;

		comp = new Composite(parent, SWT.NONE);
		comp.setLayout(UtilGui.createGridLayout(1, false, 0, 3));

		Composite header = new Composite(comp, SWT.NONE);
		header.setLayout(UtilGui.createGridLayout(3, false, 3, 8));
		header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		folderLabel = new Label(header, SWT.NONE);
		folderLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		countLabel = new Label(header, SWT.NONE);
		countLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

		Button refreshBt = UtilGui.createPushButton(header, Msg.refresh.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				refresh();
			}
		});
		refreshBt.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

		messageLabel = new Label(comp, SWT.WRAP);
		messageLabel.setForeground(comp.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND));
		GridData messageData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		messageData.exclude = true;
		messageLabel.setLayoutData(messageData);
		messageLabel.setVisible(false);

		resultPanel = new ResultPanel(comp, indexRegistry, true);
		resultPanel.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		// Files may have been moved away or renamed
		resultPanel.evtDocumentsChanged.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				refresh();
			}
		});
	}

	@NotNull
	public Control getControl() {
		return comp;
	}

	@NotNull
	public ResultPanel getResultPanel() {
		return resultPanel;
	}

	/**
	 * Adds a button to a row of its own below the header, right of the
	 * buttons added before (the header has no room for more in a narrow pane).
	 */
	@NotNull
	public Button addButton(@NotNull String label, @NotNull final Runnable action) {
		if (buttonRow == null) {
			buttonRow = new Composite(comp, SWT.NONE);
			buttonRow.setLayout(UtilGui.createGridLayout(0, false, 3, 8));
			buttonRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			buttonRow.moveBelow(countLabel.getParent());
		}
		((GridLayout) buttonRow.getLayout()).numColumns++;
		Button bt = UtilGui.createPushButton(buttonRow, label, new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				action.run();
			}
		});
		bt.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		comp.layout(true, true);
		return bt;
	}

	/**
	 * Sets the inbox folder and reloads the file list.
	 */
	public void setFolder(@NotNull File folder) {
		Util.checkNotNull(folder);
		this.folder = folder;
		resultPanel.setCurrentFolder(folder); // Files dropped on the list are moved here
		folderLabel.setText((bin ? Msg.bin_folder_label : Msg.inbox_folder_label)
			.format(Util.getSystemAbsPath(folder)));
		folderLabel.getParent().layout();
		refresh();
	}

	@Nullable
	public File getFolder() {
		return folder;
	}


	/**
	 * Reloads the file list from the index in the background. Safe to call
	 * from any thread.
	 */
	public void refresh() {
		final File folder = this.folder;
		if (folder == null)
			return;
		final int count = ++refreshCount;
		Thread thread = new Thread(InboxPanel.class.getName() + " (refresh)") {
			public void run() {
				List<ResultDocument> docs = Collections.emptyList();
				String message = null;
				long size = bin ? Bin.getSize(folder) : 0;
				if (!folder.isDirectory()) {
					message = Msg.inbox_folder_missing.format(Util.getSystemAbsPath(folder));
				}
				else {
					LuceneIndex index = indexRegistry.findIndexFor(folder);
					if (index == null)
						message = Msg.inbox_not_indexed.get();
					try {
						Searcher searcher = indexRegistry.getSearcher();
						if (searcher != null)
							docs = bin
								? searcher.listFolderEntries(index, folder)
								: searcher.listFolder(index, folder);
					}
					catch (Exception e) {
						message = e.getMessage();
					}
					catch (OutOfMemoryError e) {
						message = e.toString();
					}
				}
				final List<ResultDocument> result = docs;
				final String msg = message;
				final String countText = bin
					? Msg.bin_count.format(docs.size(), UtilFile.formatSize(size))
					: Msg.inbox_files_count.format(docs.size());
				UtilGui.runSwtSafe(comp, new Runnable() {
					public void run() {
						if (count != refreshCount)
							return; // A newer refresh is on its way
						showResults(result, msg, countText);
					}
				});
			}
		};
		thread.setDaemon(true);
		thread.start();
	}

	private void showResults(	@NotNull List<ResultDocument> docs,
								@Nullable String message,
								@NotNull String countText) {
		// Keep the selection across refreshes
		Set<String> selectedUids = new HashSet<String>();
		for (ResultDocument doc : resultPanel.getSelection())
			selectedUids.add(doc.getUniqueId());
		
		GridData data = (GridData) messageLabel.getLayoutData();
		boolean show = message != null;
		messageLabel.setText(show ? message : "");
		messageLabel.setVisible(show);
		data.exclude = !show;
		countLabel.setText(countText);
		resultPanel.setResults(new ArrayList<ResultDocument>(docs), HeaderMode.FILES);
		resultPanel.applyDefaultSorting();
		if (!selectedUids.isEmpty())
			resultPanel.selectByUids(selectedUids);
		comp.layout(true, true);
	}

}
