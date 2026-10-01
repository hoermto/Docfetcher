/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui.pref;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.gui.pref.PrefDialog.PrefOption;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * A label and a text field for a whole number within a range, e.g. a port.
 * Values outside the range are corrected when saving.
 */
final class IntFieldOption extends PrefOption {

	@NotNull private final SettingsConf.Int option;
	private final int min;
	private final int max;
	@NotNull private Text field;

	public IntFieldOption(	@NotNull String labelText,
							@NotNull SettingsConf.Int option,
							int min,
							int max) {
		super(labelText);
		this.option = option;
		this.min = min;
		this.max = max;
	}

	@Override
	protected void createControls(Composite parent) {
		Label label = new Label(parent, SWT.NONE);
		label.setText(labelText);
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

		field = new Text(parent, SWT.BORDER);
		GridData gridData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		gridData.widthHint = field.getDisplay().getDPI().x; // About an inch
		field.setLayoutData(gridData);
		field.setText(String.valueOf(option.get()));
		field.addVerifyListener(new VerifyListener() {
			public void verifyText(VerifyEvent e) {
				for (int i = 0; i < e.text.length(); i++)
					if (!Character.isDigit(e.text.charAt(i)))
						e.doit = false;
			}
		});
	}

	/** The text currently in the field. */
	@NotNull
	String getText() {
		return field.getText().trim();
	}

	@Override
	protected void restoreDefault() {
		field.setText(String.valueOf(option.defaultValue));
	}

	@Override
	protected void save() {
		int value = Util.toInt(field.getText().trim(), option.get());
		option.set(Util.clamp(value, min, max));
	}

}
