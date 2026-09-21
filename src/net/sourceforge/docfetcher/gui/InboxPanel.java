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
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.gui.ResultPanel.HeaderMode;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.LuceneIndex;
import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.model.search.Searcher;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * The 'Inbox' tab: lists the indexed files located directly in the inbox
 * folder (e.g. new documents from a scanner) in a result table, so that they
 * can be previewed, renamed and moved like search results.
 */
public final class InboxPanel {

	private final Composite comp;
	private final Label folderLabel;
	private final Label countLabel;
	private final Label messageLabel;
	private final ResultPanel resultPanel;
	private final IndexRegistry indexRegistry;
	@Nullable private volatile File folder;
	private volatile int refreshCount = 0;

	public InboxPanel(@NotNull Composite parent, @NotNull IndexRegistry indexRegistry) {
		Util.checkNotNull(parent, indexRegistry);
		this.indexRegistry = indexRegistry;

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

		resultPanel = new ResultPanel(comp, indexRegistry);
		resultPanel.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
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
	 * Sets the inbox folder and reloads the file list.
	 */
	public void setFolder(@NotNull File folder) {
		Util.checkNotNull(folder);
		this.folder = folder;
		folderLabel.setText(Msg.inbox_folder_label.format(Util.getSystemAbsPath(folder)));
		folderLabel.getParent().layout();
		refresh();
	}

	@Nullable
	public File getFolder() {
		return folder;
	}

	/**
	 * Returns the index covering the given folder, or null.
	 */
	@Nullable
	private LuceneIndex findIndexFor(@NotNull File folder) {
		for (LuceneIndex index : indexRegistry.getIndexes()) {
			File root = index.getCanonicalRootFile();
			if (Util.isCanonicallyEqual(root, folder) || Util.contains(root, folder))
				return index;
		}
		return null;
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
				if (!folder.isDirectory()) {
					message = Msg.inbox_folder_missing.format(Util.getSystemAbsPath(folder));
				}
				else {
					LuceneIndex index = findIndexFor(folder);
					if (index == null) {
						message = Msg.inbox_not_indexed.get();
					}
					else {
						try {
							Searcher searcher = indexRegistry.getSearcher();
							if (searcher != null)
								docs = searcher.listFolder(index, folder);
						}
						catch (Exception e) {
							message = e.getMessage();
						}
						catch (OutOfMemoryError e) {
							message = e.toString();
						}
					}
				}
				final List<ResultDocument> result = docs;
				final String msg = message;
				UtilGui.runSwtSafe(comp, new Runnable() {
					public void run() {
						if (count != refreshCount)
							return; // A newer refresh is on its way
						showResults(result, msg);
					}
				});
			}
		};
		thread.setDaemon(true);
		thread.start();
	}

	private void showResults(@NotNull List<ResultDocument> docs, @Nullable String message) {
		// Keep the selection across refreshes
		Set<String> selectedUids = new HashSet<String>();
		for (ResultDocument doc : resultPanel.getSelection())
			selectedUids.add(doc.getUniqueId());
		
		GridData data = (GridData) messageLabel.getLayoutData();
		boolean show = message != null;
		messageLabel.setText(show ? message : "");
		messageLabel.setVisible(show);
		data.exclude = !show;
		countLabel.setText(Msg.inbox_files_count.format(docs.size()));
		resultPanel.setResults(new ArrayList<ResultDocument>(docs), HeaderMode.FILES);
		resultPanel.applyDefaultSorting();
		if (!selectedUids.isEmpty())
			resultPanel.selectByUids(selectedUids);
		comp.layout(true, true);
	}

}
