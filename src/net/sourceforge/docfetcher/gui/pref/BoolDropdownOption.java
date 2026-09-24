/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui.pref;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.gui.pref.PrefDialog.PrefOption;
import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * A preferences option that presents a boolean setting as a dropdown with two
 * human-readable choices, e.g. "Right of results" / "Below results".
 */
final class BoolDropdownOption extends PrefOption {

	private final SettingsConf.Bool enumOption;
	private final String trueChoice;
	private final String falseChoice;
	@NotNull private CCombo dropdown;

	public BoolDropdownOption(	@NotNull String labelText,
								@NotNull SettingsConf.Bool enumOption,
								@NotNull String trueChoice,
								@NotNull String falseChoice) {
		super(labelText);
		this.enumOption = enumOption;
		this.trueChoice = trueChoice;
		this.falseChoice = falseChoice;
	}

	@Override
	protected void createControls(@NotNull Composite parent) {
		Label label = new Label(parent, SWT.NONE);
		label.setText(labelText);
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

		int style = SWT.BORDER | SWT.READ_ONLY | SWT.FLAT;
		dropdown = new CCombo(parent, style);
		dropdown.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		dropdown.setCursor(dropdown.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
		dropdown.setItems(new String[] { falseChoice, trueChoice });
		select(enumOption.get());
	}

	private void select(boolean value) {
		dropdown.select(value ? 1 : 0);
	}

	@Override
	protected void restoreDefault() {
		select(enumOption.defaultValue);
	}

	@Override
	protected void save() {
		enumOption.set(dropdown.getSelectionIndex() == 1);
	}

}
