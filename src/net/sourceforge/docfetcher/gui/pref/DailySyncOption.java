/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui.pref;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.gui.IndexSync;
import net.sourceforge.docfetcher.gui.pref.PrefDialog.PrefOption;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * The daily sync (see IndexSync): a checkbox, the time and whether it
 * updates the indexes that need it (network drives, folders not watched) or
 * all of them.
 */
final class DailySyncOption extends PrefOption {

	@NotNull private Button checkBt;
	@NotNull private Text timeField;
	@NotNull private Label forLabel;
	@NotNull private Combo scopeCombo;

	public DailySyncOption() {
		super(Msg.pref_daily_sync.get());
	}

	@Override
	protected void createControls(@NotNull Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(UtilGui.createGridLayout(4, false, 0, 5));
		comp.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
		checkBt = UtilGui.createCheckButton(comp, labelText);
		checkBt.setSelection(SettingsConf.Bool.DailyIndexUpdate.get());
		checkBt.setToolTipText(Msg.pref_daily_sync_hint.get());
		timeField = new Text(comp, SWT.BORDER);
		timeField.setText(IndexSync.format(IndexSync.getMinute()));
		timeField.setTextLimit(5);
		GridData timeData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		timeData.widthHint = timeField.getDisplay().getDPI().x / 2;
		timeField.setLayoutData(timeData);
		forLabel = new Label(comp, SWT.NONE);
		forLabel.setText(Msg.pref_daily_sync_for.get());
		scopeCombo = new Combo(comp, SWT.DROP_DOWN | SWT.READ_ONLY);
		scopeCombo.add(Msg.pref_daily_sync_needed.get());
		scopeCombo.add(Msg.pref_daily_sync_all.get());
		scopeCombo.select(SettingsConf.Int.DailySyncScope.get() == IndexSync.SCOPE_ALL ? 1 : 0);
		checkBt.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				updateEnabled();
			}
		});
		updateEnabled();
	}

	private void updateEnabled() {
		boolean on = checkBt.getSelection();
		timeField.setEnabled(on);
		forLabel.setEnabled(on);
		scopeCombo.setEnabled(on);
	}

	@Override
	protected void restoreDefault() {
		checkBt.setSelection(SettingsConf.Bool.DailyIndexUpdate.defaultValue);
		timeField.setText(SettingsConf.Str.DailySyncTime.defaultValue);
		scopeCombo.select(0);
		updateEnabled();
	}

	@Override
	protected void save() {
		SettingsConf.Bool.DailyIndexUpdate.set(checkBt.getSelection());
		Integer minute = IndexSync.parseTime(timeField.getText());
		if (minute != null) // Something else keeps the time it had
			SettingsConf.Str.DailySyncTime.set(IndexSync.format(minute));
		SettingsConf.Int.DailySyncScope.set(scopeCombo.getSelectionIndex() == 1
			? IndexSync.SCOPE_ALL : IndexSync.SCOPE_NEEDED);
	}

}
