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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.gui.pref.PrefDialog.PrefOption;
import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * The naming rules AI assistants follow when they propose file names
 * ({@link SettingsConf.Str#McpNamingRules}): a text field with DocFetcher's
 * rule as default, indented below the rename checkbox and disabled while
 * that is off. The default is stored as empty, so that it follows the
 * language of the program.
 */
final class McpNamingRulesOption extends PrefOption {

	@NotNull private Label label;
	@NotNull private Text text;

	public McpNamingRulesOption(@NotNull CheckOption renameOption) {
		super(Msg.pref_mcp_naming_rules.get());
		renameOption.addDependent(this);
	}

	@Override
	protected void createControls(@NotNull Composite parent) {
		label = new Label(parent, SWT.NONE);
		label.setText(labelText);
		GridData labelData = new GridData(SWT.FILL, SWT.TOP, false, false);
		labelData.horizontalIndent = 40;
		label.setLayoutData(labelData);
		text = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
		String rules = SettingsConf.Str.McpNamingRules.get().trim();
		text.setText(rules.isEmpty() ? Msg.mcp_naming_rules_default.get() : rules);
		GridData textData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		textData.widthHint = parent.getDisplay().getDPI().x * 3;
		textData.heightHint = text.getLineHeight() * 4;
		text.setLayoutData(textData);
	}

	@Override
	protected void setActive(boolean active) {
		label.setEnabled(active);
		text.setEnabled(active);
	}

	@Override
	protected void restoreDefault() {
		text.setText(Msg.mcp_naming_rules_default.get());
	}

	@Override
	protected void save() {
		String rules = text.getText().trim();
		SettingsConf.Str.McpNamingRules.set(
			rules.equals(Msg.mcp_naming_rules_default.get()) ? "" : rules);
	}

}
