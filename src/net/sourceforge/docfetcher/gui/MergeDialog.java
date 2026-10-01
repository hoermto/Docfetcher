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
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.model.search.NameSuggester.Suggestion;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Asks in which order the selected PDF files are merged and how the merged
 * file is named. The name is entered like in the rename dialog; it starts
 * with the name of the first file and follows the order until the user
 * changes it. The merged file is saved in the folder of the first file.
 */
final class MergeDialog {

	/** Provides the name suggestion for a file, or null. */
	interface SuggestionSource {
		@Nullable Suggestion get(@NotNull File file);
	}

	/** The user's choice: the files in the order to merge, and the new file name. */
	static final class Result {
		final List<File> files;
		final String fileName;

		Result(@NotNull List<File> files, @NotNull String fileName) {
			this.files = files;
			this.fileName = fileName;
		}
	}

	private final Shell shell;
	private final org.eclipse.swt.widgets.List list;
	private final NameEditor nameEditor;
	private final List<File> files;
	private final SuggestionSource suggestions;
	@Nullable private File suggestionFor;
	@Nullable private Result result;

	public MergeDialog(	@NotNull Shell parent,
						@NotNull List<File> files,
						@NotNull SuggestionSource suggestions) {
		Util.checkNotNull(parent, files, suggestions);
		this.files = new ArrayList<File>(files);
		this.suggestions = suggestions;

		shell = new Shell(parent, SWT.PRIMARY_MODAL | SWT.DIALOG_TRIM | SWT.RESIZE);
		shell.setText(Msg.merge_title.get());
		shell.setImage(Img.TB_MERGE.get());
		shell.setLayout(UtilGui.createGridLayout(2, false, 10, 5));

		Label orderLabel = new Label(shell, SWT.NONE);
		orderLabel.setText(Msg.merge_order.get());
		orderLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		list = new org.eclipse.swt.widgets.List(shell, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
		GridData listData = new GridData(SWT.FILL, SWT.FILL, true, true);
		listData.heightHint = 150;
		listData.widthHint = 380;
		list.setLayoutData(listData);

		Composite moveComp = new Composite(shell, SWT.NONE);
		moveComp.setLayout(UtilGui.createGridLayout(1, true, 0, 5));
		moveComp.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
		createButton(moveComp, Msg.move_up.get(), new Runnable() {
			public void run() {
				move(-1);
			}
		});
		createButton(moveComp, Msg.move_down.get(), new Runnable() {
			public void run() {
				move(1);
			}
		});
		createButton(moveComp, Msg.merge_reverse.get(), new Runnable() {
			public void run() {
				reverse();
			}
		});

		Label nameLabel = new Label(shell, SWT.WRAP);
		nameLabel.setText(Msg.merge_file_name.get());
		GridData nameLabelData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		nameLabelData.widthHint = 480; // Wraps instead of widening the dialog
		nameLabel.setLayoutData(nameLabelData);
		nameEditor = new NameEditor(shell, "pdf", Msg.merge_use_first_name.get());
		nameEditor.getControl().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		Label separator = new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL);
		separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		Composite buttonComp = new Composite(shell, SWT.NONE);
		buttonComp.setLayout(UtilGui.createGridLayout(2, true, 0, 5));
		buttonComp.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));
		Button okBt = UtilGui.createPushButton(buttonComp, Msg.ok.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				String name = getFileName();
				if (name.isEmpty())
					return;
				result = new Result(Collections.unmodifiableList(MergeDialog.this.files), name + ".pdf");
				shell.close();
			}
		});
		Button cancelBt = UtilGui.createPushButton(buttonComp, Msg.cancel.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				shell.close();
			}
		});
		for (Button bt : UtilGui.maybeSwapButtons(okBt, cancelBt)) {
			GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
			data.minimumWidth = UtilGui.BTW;
			bt.setLayoutData(data);
		}
		shell.setDefaultButton(okBt);

		fillList(0);
		shell.pack();
		Point size = shell.getSize();
		UtilGui.setCenteredBounds(shell, Math.max(size.x, 520), size.y);
	}

	private static void createButton(	@NotNull Composite parent,
										@NotNull String label,
										@NotNull final Runnable action) {
		Button button = new Button(parent, SWT.PUSH);
		button.setText(label);
		button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		button.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				action.run();
			}
		});
	}

	/** Moves the selected file up (-1) or down (1) in the order. */
	void move(int step) {
		int index = list.getSelectionIndex();
		int target = index + step;
		if (index < 0 || target < 0 || target >= files.size())
			return;
		Collections.swap(files, index, target);
		fillList(target);
	}

	/** Reverses the order, e.g. for a stack that was scanned from the back. */
	void reverse() {
		int index = list.getSelectionIndex();
		Collections.reverse(files);
		fillList(index < 0 ? 0 : files.size() - 1 - index);
	}

	private void fillList(int selection) {
		list.removeAll();
		for (int i = 0; i < files.size(); i++)
			list.add((i + 1) + ".  " + files.get(i).getName());
		list.select(selection);

		// The proposed name follows the first file, unless the user changed it
		File first = files.get(0);
		if (first.equals(suggestionFor))
			return;
		suggestionFor = first;
		String base = Util.splitFilename(first.getName())[0];
		nameEditor.setCurrentName(base);
		nameEditor.setSuggestion(suggestions.get(first));
		if (!nameEditor.isEdited())
			nameEditor.setName(base);
		shell.layout(true, true);
	}

	/** The file name in the dialog, without extension; for tests. */
	@NotNull
	String getFileName() {
		String name = nameEditor.getName();
		if (name.toLowerCase().endsWith(".pdf"))
			name = name.substring(0, name.length() - 4);
		return name;
	}

	/** Returns the files in the current order; for tests. */
	@NotNull
	List<File> getOrder() {
		return Collections.unmodifiableList(files);
	}

	/** Selects the file at the given position; for tests. */
	void select(int index) {
		list.select(index);
	}

	/** Opens the dialog and returns the user's choice, or null if cancelled. */
	@Nullable
	public Result open() {
		shell.open();
		while (!shell.isDisposed()) {
			if (!shell.getDisplay().readAndDispatch())
				shell.getDisplay().sleep();
		}
		return result;
	}

	/** Closes the dialog; for tests. */
	void close() {
		shell.close();
	}

}
