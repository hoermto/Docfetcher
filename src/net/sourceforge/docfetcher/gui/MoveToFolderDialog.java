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
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.model.search.FolderSuggestion;
import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.model.search.Searcher;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Dialog for choosing the folder into which one or more documents are to be
 * moved. It suggests folders whose documents are similar to the documents to
 * be moved (computed in the background) and allows browsing for any other
 * folder.
 */
final class MoveToFolderDialog {

	private static final int MAX_SUGGESTIONS = 5;

	private final Shell shell;
	private final Table table;
	private final Label statusLabel;
	private final Text targetField;
	private final Button moveBt;
	@Nullable private File answer;
	private volatile boolean disposed = false;

	/**
	 * @param docs
	 *            The documents to be moved; the suggestions are based on all
	 *            of them.
	 */
	public MoveToFolderDialog(	@NotNull Shell parent,
								@NotNull final Searcher searcher,
								@NotNull final List<ResultDocument> docs) {
		Util.checkNotNull(parent, searcher, docs);
		Util.checkThat(!docs.isEmpty());

		shell = new Shell(parent, SWT.PRIMARY_MODAL | SWT.DIALOG_TRIM | SWT.RESIZE);
		shell.setText(Msg.move_to_folder_title.get());
		shell.setImage(Img.FOLDER.get());
		shell.setLayout(UtilGui.createGridLayout(1, false, 10, 5));
		SettingsConf.ShellBounds.MoveToFolderDialog.bind(shell);

		Label label = new Label(shell, SWT.NONE);
		label.setText(Msg.move_suggestions_label.get());
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		table = new Table(shell, SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
		table.setHeaderVisible(true);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
		tableData.heightHint = 140;
		table.setLayoutData(tableData);
		TableColumn folderCol = new TableColumn(table, SWT.LEFT);
		folderCol.setText(Msg.folder_col.get());
		folderCol.setWidth(200);
		TableColumn locationCol = new TableColumn(table, SWT.LEFT);
		locationCol.setText(Msg.index_col_location.get());
		locationCol.setWidth(260);
		TableColumn matchCol = new TableColumn(table, SWT.RIGHT);
		matchCol.setText(Msg.match_col.get());
		matchCol.setWidth(90);
		table.addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent e) {
				// Deferred: on GTK the client area is stale during the event
				UtilGui.runAsyncExec(table, new Runnable() {
					public void run() {
						fitColumns();
					}
				});
			}
		});
		table.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				TableItem[] sel = table.getSelection();
				if (sel.length > 0)
					setTarget((File) sel[0].getData());
			}
			public void widgetDefaultSelected(SelectionEvent e) {
				TableItem[] sel = table.getSelection();
				if (sel.length > 0) {
					setTarget((File) sel[0].getData());
					accept();
				}
			}
		});

		statusLabel = new Label(shell, SWT.WRAP);
		statusLabel.setText(Msg.computing_suggestions.get());
		statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		// Target folder field with browse button
		Composite targetComp = new Composite(shell, SWT.NONE);
		targetComp.setLayout(UtilGui.createGridLayout(3, false, 0, 5));
		targetComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Label targetLabel = new Label(targetComp, SWT.NONE);
		targetLabel.setText(Msg.target_folder_label.get());
		targetField = new Text(targetComp, SWT.BORDER | SWT.SINGLE | SWT.READ_ONLY);
		targetField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button browseBt = UtilGui.createPushButton(targetComp, Msg.browse.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				DirectoryDialog dialog = new DirectoryDialog(shell);
				dialog.setText(Msg.move_to_folder_title.get());
				File current = getTarget();
				if (current != null)
					dialog.setFilterPath(current.getPath());
				else
					dialog.setFilterPath(SettingsConf.Str.LastIndexedFolder.get());
				String path = dialog.open();
				if (path != null) {
					table.deselectAll();
					setTarget(new File(path));
				}
			}
		});
		browseBt.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

		// Button bar
		Composite buttonComp = new Composite(shell, SWT.NONE);
		buttonComp.setLayout(UtilGui.createGridLayout(2, true, 0, 5));
		buttonComp.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		moveBt = UtilGui.createPushButton(buttonComp, Msg.move_bt.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				accept();
			}
		});
		Button cancelBt = UtilGui.createPushButton(buttonComp, Msg.cancel.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				shell.close();
			}
		});
		Button[] bts = UtilGui.maybeSwapButtons(moveBt, cancelBt);
		for (Button bt : bts) {
			GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
			data.minimumWidth = UtilGui.BTW;
			bt.setLayoutData(data);
		}
		moveBt.setEnabled(false);
		shell.setDefaultButton(moveBt);

		computeSuggestions(searcher, docs);
	}

	private void computeSuggestions(@NotNull final Searcher searcher,
									@NotNull final List<ResultDocument> docs) {
		Thread thread = new Thread(MoveToFolderDialog.class.getName() + " (suggestions)") {
			public void run() {
				List<FolderSuggestion> suggestions = new ArrayList<FolderSuggestion>();
				String error = null;
				try {
					// Combine the text of all documents to be moved
					StringBuilder text = new StringBuilder();
					for (ResultDocument doc : docs) {
						if (disposed)
							return;
						try {
							text.append(doc.getText()).append('\n');
						}
						catch (Exception e) {
							// Documents that can't be parsed contribute nothing
						}
					}
					// Also include the filenames, they often carry keywords
					for (ResultDocument doc : docs)
						text.append(doc.getFilename()).append('\n');

					// Never suggest the current folder or an inbox folder
					List<File> exclude = InboxFolders.getFolders();
					if (docs.size() == 1)
						exclude.add(Util.getParentFile(docs.get(0).getPath().getCanonicalFile()));
					suggestions = searcher.suggestFolders(text.toString(), exclude, MAX_SUGGESTIONS);
				}
				catch (Exception e) {
					error = e.getMessage();
				}
				catch (OutOfMemoryError e) {
					error = e.toString();
				}
				final List<FolderSuggestion> result = suggestions;
				final String errorMsg = error;
				UtilGui.runSwtSafe(shell, new Runnable() {
					public void run() {
						showSuggestions(result, errorMsg);
					}
				});
			}
		};
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	private void showSuggestions(	@NotNull List<FolderSuggestion> suggestions,
									@Nullable String error) {
		table.removeAll();
		// Fixed targets first, then the folders suggested by content
		List<File> fixedTargets = MoveTargets.loadFolders();
		for (File target : fixedTargets)
			addRow(target, Msg.move_target_fixed.get());
		for (FolderSuggestion suggestion : suggestions) {
			boolean fixed = false;
			for (File target : fixedTargets)
				if (Util.isCanonicallyEqual(target, suggestion.getFolder()))
					fixed = true;
			if (!fixed)
				addRow(suggestion.getFolder(), Math.round(suggestion.getRelevance() * 100) + " %");
		}
		if (error != null) {
			statusLabel.setText(error);
		}
		else if (suggestions.isEmpty() && fixedTargets.isEmpty()) {
			statusLabel.setText(Msg.no_suggestions.get());
		}
		else {
			statusLabel.setText("");
			// Preselect the best match (or the first fixed target), unless
			// the user already browsed
			if (getTarget() == null) {
				File best = suggestions.isEmpty()
					? fixedTargets.get(0)
					: suggestions.get(0).getFolder();
				for (int i = 0; i < table.getItemCount(); i++) {
					if (Util.isCanonicallyEqual((File) table.getItem(i).getData(), best)) {
						table.setSelection(i);
						break;
					}
				}
				setTarget(best);
			}
		}
		// Size the folder column to its contents, the location column gets the rest
		table.getColumn(0).pack();
		folderPreferredWidth = table.getColumn(0).getWidth();
		fitColumns();
		shell.layout();
	}

	private void addRow(@NotNull File folder, @NotNull String match) {
		TableItem item = new TableItem(table, SWT.NONE);
		String[] parts = splitFolder(folder);
		item.setText(0, parts[0]);
		item.setText(1, parts[1]);
		item.setText(2, match);
		item.setImage(0, Img.FOLDER.get());
		item.setData(folder);
	}

	private int folderPreferredWidth = 200;

	/**
	 * Splits a folder path for display into the trailing part (the last few
	 * segments, as configured in the preferences) and the leading part, e.g.
	 * "Projects/Reports" and "C:\\Users\\me\\Documents". The leading part is
	 * empty if the whole path fits into the configured number of segments.
	 */
	@NotNull
	static String[] splitFolder(@NotNull File folder) {
		int segments = Math.max(1, SettingsConf.Int.FolderPathSegments.get());
		List<String> names = new ArrayList<String>();
		File current = folder;
		while (current != null && names.size() < segments && current.getName().length() > 0) {
			names.add(0, current.getName());
			current = current.getParentFile();
		}
		if (names.isEmpty())
			return new String[] { Util.getSystemAbsPath(folder), "" };
		String tail = Util.join(File.separator, names);
		String head = current == null ? "" : Util.getSystemAbsPath(current);
		return new String[] { tail, head };
	}

	/**
	 * Gives the match column a fixed width, the folder column the width of
	 * its contents (but at most about half of the table) and the location
	 * column the rest, so that long paths get as much room as possible.
	 */
	private void fitColumns() {
		if (table.isDisposed())
			return;
		int available = table.getClientArea().width;
		if (available <= 0)
			return;
		TableColumn[] columns = table.getColumns();
		int matchWidth = 90;
		int remaining = Math.max(200, available - matchWidth);
		int folderWidth = Math.min(folderPreferredWidth, remaining / 2);
		folderWidth = Math.max(folderWidth, 100);
		columns[0].setWidth(folderWidth);
		columns[1].setWidth(remaining - folderWidth);
		columns[2].setWidth(matchWidth);
	}

	private void setTarget(@Nullable File folder) {
		targetField.setText(folder == null ? "" : Util.getSystemAbsPath(folder));
		moveBt.setEnabled(folder != null);
	}

	@Nullable
	private File getTarget() {
		String path = targetField.getText().trim();
		return path.isEmpty() ? null : new File(path);
	}

	private void accept() {
		File target = getTarget();
		if (target == null)
			return;
		answer = target;
		shell.close();
	}

	/**
	 * Opens the dialog and returns the chosen target folder, or null if the
	 * dialog was cancelled.
	 */
	@Nullable
	public File open() {
		shell.open();
		while (!shell.isDisposed()) {
			if (!shell.getDisplay().readAndDispatch())
				shell.getDisplay().sleep();
		}
		disposed = true;
		return answer;
	}

}
