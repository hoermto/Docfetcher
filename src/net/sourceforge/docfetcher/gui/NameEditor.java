/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.model.search.NameSuggester;
import net.sourceforge.docfetcher.model.search.NameSuggester.Suggestion;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * The part of a dialog where a file name is entered, used by the rename and
 * the merge dialog: a field for the name without extension (the extension is
 * shown next to it and can't be changed), links for switching between the
 * current and the suggested name, and the suggested keywords, which are added
 * to the name with a click.
 */
final class NameEditor {

	private final Composite comp;
	private final Text nameField;
	private final Composite suggestionComp;
	private String currentName = "";
	@Nullable private Suggestion suggestion;
	private boolean settingName = false;
	private boolean edited = false;

	/**
	 * @param currentLabel
	 *            The label of the link that brings back the current name,
	 *            e.g. 'Use current name'.
	 */
	public NameEditor(	@NotNull Composite parent,
						@NotNull String extension,
						@NotNull String currentLabel) {
		comp = new Composite(parent, SWT.NONE);
		comp.setLayout(UtilGui.createGridLayout(1, false, 0, 5));

		Composite nameComp = new Composite(comp, SWT.NONE);
		nameComp.setLayout(UtilGui.createGridLayout(2, false, 0, 3));
		nameComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		nameField = new Text(nameComp, SWT.BORDER | SWT.SINGLE);
		GridData nameData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		nameData.widthHint = 430;
		nameField.setLayoutData(nameData);
		Label extLabel = new Label(nameComp, SWT.NONE);
		extLabel.setText(extension.isEmpty() ? "" : "." + extension);
		extLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		nameField.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				if (!settingName)
					edited = true;
			}
		});

		Composite linkComp = new Composite(comp, SWT.NONE);
		linkComp.setLayout(UtilGui.createGridLayout(1, false, 0, 15));
		linkComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Link currentLink = new Link(linkComp, SWT.NONE);
		currentLink.setText("<a>" + currentLabel + "</a>");
		currentLink.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				setName(currentName);
				edited = true;
			}
		});

		suggestionComp = new Composite(comp, SWT.NONE);
		suggestionComp.setLayout(UtilGui.createGridLayout(1, false, 0, 5));
		suggestionComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
	}

	@NotNull
	public Control getControl() {
		return comp;
	}

	/** Sets the name the 'current name' link brings back (without extension). */
	public void setCurrentName(@NotNull String base) {
		currentName = Util.checkNotNull(base);
	}

	/**
	 * Shows the links for the given suggestion (none if it is null or has no
	 * keywords). The dialog should be laid out again afterwards.
	 */
	public void setSuggestion(@Nullable Suggestion suggestion) {
		for (Control child : suggestionComp.getChildren())
			child.dispose();
		this.suggestion = hasWords(suggestion) ? suggestion : null;
		if (this.suggestion == null) {
			((GridData) suggestionComp.getLayoutData()).exclude = true;
			suggestionComp.setVisible(false);
			return;
		}
		((GridData) suggestionComp.getLayoutData()).exclude = false;
		suggestionComp.setVisible(true);

		Link suggestedLink = new Link(suggestionComp, SWT.NONE);
		suggestedLink.setText("<a>" + Msg.use_suggested_name.get() + "</a>");
		suggestedLink.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				setName(getSuggestedName());
				edited = true;
			}
		});
		Label keywordsLabel = new Label(suggestionComp, SWT.NONE);
		keywordsLabel.setText(Msg.suggested_keywords.get());
		Composite keywordComp = new Composite(suggestionComp, SWT.NONE);
		keywordComp.setLayout(UtilGui.createGridLayout(6, false, 0, 10));
		keywordComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		for (final String word : this.suggestion.getWords()) {
			Link wordLink = new Link(keywordComp, SWT.NONE);
			wordLink.setText("<a>" + word.replace("&", "&&").replace("<", "&lt;") + "</a>");
			wordLink.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					appendWord(word);
				}
			});
		}
	}

	private static boolean hasWords(@Nullable Suggestion suggestion) {
		return suggestion != null && !suggestion.getWords().isEmpty();
	}

	/** The suggested name without extension, or null if there is none. */
	@Nullable
	public String getSuggestedName() {
		return suggestion == null ? null : suggestion.buildName(NameSuggester.DEFAULT_WORDS, "");
	}

	/** Sets the name (without extension) and selects it; doesn't count as an edit. */
	public void setName(@Nullable String base) {
		if (base == null)
			return;
		settingName = true;
		try {
			nameField.setText(base);
		}
		finally {
			settingName = false;
		}
		nameField.setSelection(0, base.length());
		nameField.setFocus();
	}

	/** Appends a keyword to the name. */
	private void appendWord(@NotNull String word) {
		String base = nameField.getText().trim();
		if (!base.isEmpty() && !base.endsWith("_") && !base.endsWith("-"))
			base += base.contains("_") ? "-" : "_";
		nameField.setText(NameSuggester.sanitize(base + word));
		nameField.setSelection(nameField.getText().length());
		nameField.setFocus();
	}

	/** True if the user changed the name: typed, or clicked one of the links. */
	public boolean isEdited() {
		return edited;
	}

	/** The entered name without extension, cleaned up for use as file name. */
	@NotNull
	public String getName() {
		return NameSuggester.sanitize(nameField.getText().trim());
	}

}
