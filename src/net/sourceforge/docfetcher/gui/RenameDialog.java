/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.model.search.NameSuggester;
import net.sourceforge.docfetcher.model.search.NameSuggester.Suggestion;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Dialog for renaming a file. If a name suggestion is available, the name
 * field is prefilled with it and the suggested keywords can be added to the
 * name with a click.
 */
final class RenameDialog {

	private final Shell shell;
	private final Text nameField;
	private final String currentName;
	private final String extension;
	@Nullable private final Suggestion suggestion;
	@Nullable private String answer;

	public RenameDialog(@NotNull Shell parent,
						@NotNull String currentName,
						@Nullable Suggestion suggestion) {
		Util.checkNotNull(parent, currentName);
		this.currentName = currentName;
		this.suggestion = suggestion;
		String[] parts = Util.splitFilename(currentName);
		extension = parts.length > 1 ? parts[1] : "";

		shell = new Shell(parent, SWT.PRIMARY_MODAL | SWT.DIALOG_TRIM | SWT.RESIZE);
		shell.setText(Msg.rename_document_title.get());
		shell.setImage(Img.FILE.get());
		shell.setLayout(UtilGui.createGridLayout(1, false, 10, 5));

		Label label = new Label(shell, SWT.NONE);
		label.setText(Msg.rename_document_msg.get());
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		nameField = new Text(shell, SWT.BORDER | SWT.SINGLE);
		GridData nameData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		nameData.widthHint = 450;
		nameField.setLayoutData(nameData);

		String initial = suggestion != null && !suggestion.getWords().isEmpty()
			? suggestion.buildName(NameSuggester.DEFAULT_WORDS, extension)
			: currentName;
		setName(initial);

		// Links for switching between the current and the suggested name
		Composite linkComp = new Composite(shell, SWT.NONE);
		linkComp.setLayout(UtilGui.createGridLayout(2, false, 0, 15));
		linkComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Link currentLink = new Link(linkComp, SWT.NONE);
		currentLink.setText("<a>" + Msg.use_current_name.get() + "</a>");
		currentLink.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				setName(RenameDialog.this.currentName);
			}
		});
		if (suggestion != null && !suggestion.getWords().isEmpty()) {
			Link suggestedLink = new Link(linkComp, SWT.NONE);
			suggestedLink.setText("<a>" + Msg.use_suggested_name.get() + "</a>");
			suggestedLink.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					setName(RenameDialog.this.suggestion.buildName(
						NameSuggester.DEFAULT_WORDS, extension));
				}
			});

			Label keywordsLabel = new Label(shell, SWT.NONE);
			keywordsLabel.setText(Msg.suggested_keywords.get());
			keywordsLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			Composite keywordComp = new Composite(shell, SWT.NONE);
			keywordComp.setLayout(UtilGui.createGridLayout(6, false, 0, 10));
			keywordComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			for (final String word : suggestion.getWords()) {
				Link wordLink = new Link(keywordComp, SWT.NONE);
				wordLink.setText("<a>" + word.replace("&", "&&").replace("<", "&lt;") + "</a>");
				wordLink.addSelectionListener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						appendWord(word);
					}
				});
			}
		}

		Label separator = new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL);
		separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Composite buttonComp = new Composite(shell, SWT.NONE);
		buttonComp.setLayout(UtilGui.createGridLayout(2, true, 0, 5));
		buttonComp.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		Button okBt = UtilGui.createPushButton(buttonComp, Msg.ok.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				answer = nameField.getText().trim();
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

		shell.pack();
		Point size = shell.getSize();
		shell.setSize(Math.max(size.x, 500), size.y);
		UtilGui.setCenteredBounds(shell, shell.getSize().x, shell.getSize().y);
	}

	/** Sets the name and selects the part before the extension. */
	private void setName(@NotNull String name) {
		nameField.setText(name);
		int dot = name.lastIndexOf('.');
		int end = dot > 0 ? dot : name.length();
		nameField.setSelection(0, end);
		nameField.setFocus();
	}

	/** Appends a keyword to the name, before the extension. */
	private void appendWord(@NotNull String word) {
		String name = nameField.getText().trim();
		String base = name;
		String ext = "";
		int dot = name.lastIndexOf('.');
		if (dot > 0) {
			base = name.substring(0, dot);
			ext = name.substring(dot);
		}
		if (!base.isEmpty() && !base.endsWith("_") && !base.endsWith("-"))
			base += base.contains("_") ? "-" : "_";
		nameField.setText(NameSuggester.sanitize(base + word) + ext);
		nameField.setSelection(nameField.getText().length());
		nameField.setFocus();
	}

	/**
	 * Opens the dialog and returns the entered name, or null if cancelled.
	 */
	@Nullable
	public String open() {
		shell.open();
		while (!shell.isDisposed()) {
			if (!shell.getDisplay().readAndDispatch())
				shell.getDisplay().sleep();
		}
		return answer;
	}

}
