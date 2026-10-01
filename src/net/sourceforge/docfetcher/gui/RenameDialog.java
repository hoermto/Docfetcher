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
import org.eclipse.swt.widgets.Shell;

import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.enums.Msg;
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
	private final NameEditor nameEditor;
	@Nullable private String answer;

	public RenameDialog(@NotNull Shell parent,
						@NotNull String currentName,
						@Nullable Suggestion suggestion) {
		Util.checkNotNull(parent, currentName);
		String[] parts = Util.splitFilename(currentName);
		final String extension = parts.length > 1 ? parts[1] : "";

		shell = new Shell(parent, SWT.PRIMARY_MODAL | SWT.DIALOG_TRIM | SWT.RESIZE);
		shell.setText(Msg.rename_document_title.get());
		shell.setImage(Img.FILE.get());
		shell.setLayout(UtilGui.createGridLayout(1, false, 10, 5));

		Label label = new Label(shell, SWT.NONE);
		label.setText(Msg.rename_document_msg.get());
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		nameEditor = new NameEditor(shell, extension, Msg.use_current_name.get());
		nameEditor.getControl().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		nameEditor.setCurrentName(parts[0]);
		nameEditor.setSuggestion(suggestion);
		String suggested = nameEditor.getSuggestedName();
		nameEditor.setName(suggested != null ? suggested : parts[0]);

		Label separator = new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL);
		separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Composite buttonComp = new Composite(shell, SWT.NONE);
		buttonComp.setLayout(UtilGui.createGridLayout(2, true, 0, 5));
		buttonComp.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		Button okBt = UtilGui.createPushButton(buttonComp, Msg.ok.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				String base = nameEditor.getName();
				if (base.isEmpty())
					return;
				answer = extension.isEmpty() ? base : base + "." + extension;
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
