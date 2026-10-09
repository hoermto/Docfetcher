/*******************************************************************************
 * Copyright (c) 2011 Tran Nam Quang.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tran Nam Quang - initial API and implementation
 *******************************************************************************/

package net.sourceforge.docfetcher.gui.pref;

import java.util.ArrayList;
import java.util.List;

import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.gui.pref.PrefDialog.PrefOption;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

/**
 * A checkbox for a boolean setting. It may depend on another checkbox (see
 * {@link #CheckOption(String, SettingsConf.Bool, CheckOption)}): then it is
 * indented below it and disabled while that one is off or disabled - its
 * value stays, the setting only takes effect together with the other one.
 *
 * @author Tran Nam Quang
 */
final class CheckOption extends PrefOption {

	private final SettingsConf.Bool enumOption;
	@Nullable private final CheckOption parent;
	private final List<CheckOption> children = new ArrayList<CheckOption>(0);
	@NotNull private Button bt;

	public CheckOption(	@NotNull String labelText,
						@NotNull SettingsConf.Bool enumOption) {
		this(labelText, enumOption, null);
	}

	/** A checkbox that only applies while the given one is checked. */
	public CheckOption(	@NotNull String labelText,
						@NotNull SettingsConf.Bool enumOption,
						@Nullable CheckOption parent) {
		super(labelText);
		this.enumOption = enumOption;
		this.parent = parent;
		if (parent != null)
			parent.children.add(this);
	}

	public void createControls(@NotNull Composite parentComp) {
		bt = UtilGui.createCheckButton(parentComp, labelText);
		GridData data = new GridData(SWT.LEFT, SWT.FILL, false, false, 2, 1);
		data.horizontalIndent = 20 * depth();
		bt.setLayoutData(data);
		bt.setSelection(enumOption.get());
		bt.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				updateChildren();
			}
		});
		if (parent != null)
			bt.setEnabled(parent.isActive());
	}

	private int depth() {
		return parent == null ? 0 : parent.depth() + 1;
	}

	/** Checked and not disabled by the checkbox it depends on. */
	boolean isActive() {
		return bt.getSelection() && bt.getEnabled();
	}

	private void updateChildren() {
		for (CheckOption child : children) {
			child.bt.setEnabled(isActive());
			child.updateChildren();
		}
	}

	protected void restoreDefault() {
		bt.setSelection(enumOption.defaultValue);
		updateChildren();
	}

	protected void save() {
		enumOption.set(bt.getSelection());
	}

}
