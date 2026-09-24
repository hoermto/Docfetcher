/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui.pref;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.gui.pref.PrefDialog.PrefOption;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.WindowsAutostart;
import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * Checkbox for starting the program automatically with Windows. Unlike the
 * other options, its state is not stored in the settings file but read from
 * and written to the Windows registry.
 */
final class AutostartOption extends PrefOption {

	private final String valueName;
	@NotNull private Button bt;
	private boolean initialState;

	/**
	 * @param valueName
	 *            The name of the registry value, usually the program name.
	 */
	public AutostartOption(@NotNull String labelText, @NotNull String valueName) {
		super(labelText);
		this.valueName = valueName;
	}

	protected void createControls(@NotNull Composite parent) {
		bt = UtilGui.createCheckButton(parent, labelText);
		bt.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, false, 2, 1));
		initialState = WindowsAutostart.isEnabled(valueName);
		bt.setSelection(initialState);
	}

	protected void restoreDefault() {
		bt.setSelection(false);
	}

	protected void save() {
		boolean enabled = bt.getSelection();
		if (enabled == initialState)
			return;
		try {
			WindowsAutostart.setEnabled(valueName, enabled);
			initialState = enabled;
		}
		catch (Throwable e) {
			AppUtil.showError(Msg.autostart_failed.format(e.toString()), true, true);
		}
	}

}
