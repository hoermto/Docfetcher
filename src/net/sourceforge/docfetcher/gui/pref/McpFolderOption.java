/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui.pref;

import java.io.File;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.gui.InboxFolders;
import net.sourceforge.docfetcher.gui.pref.PrefDialog.PrefOption;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * The inbox folder in which AI assistants with their own file access may
 * save new files ({@link SettingsConf.Str#McpFileFolder}): a dropdown with
 * '(none)' and the inbox folders, indented below the MCP server checkbox and
 * disabled while that is off. Inbox folders added on the Folders tab appear
 * the next time the preferences are opened.
 */
final class McpFolderOption extends PrefOption {

	@NotNull private final List<InboxFolders.Entry> inboxes = InboxFolders.load();
	@NotNull private Label label;
	@NotNull private Combo combo;

	public McpFolderOption(@NotNull CheckOption mcpServerOption) {
		super(Msg.pref_mcp_file_folder.get());
		mcpServerOption.addDependent(this);
	}

	@Override
	protected void createControls(@NotNull Composite parent) {
		label = new Label(parent, SWT.NONE);
		label.setText(labelText);
		GridData labelData = new GridData(SWT.FILL, SWT.CENTER, false, false);
		labelData.horizontalIndent = 20;
		label.setLayoutData(labelData);
		combo = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
		combo.add(Msg.pref_mcp_file_folder_none.get());
		for (InboxFolders.Entry inbox : inboxes)
			combo.add(inbox.getTabName() + "  (" + inbox.getFolder().getPath() + ")");
		// Long folder paths would widen the dialog; the open list shows them in full
		GridData comboData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		comboData.widthHint = parent.getDisplay().getDPI().x * 3;
		combo.setLayoutData(comboData);
		combo.select(indexOf(SettingsConf.Str.McpFileFolder.get()));
	}

	/** The position of the folder in the dropdown; 0 = none or no longer an inbox. */
	private int indexOf(@NotNull String path) {
		if (path.trim().isEmpty())
			return 0;
		File folder = Util.getCanonicalFile(path);
		for (int i = 0; i < inboxes.size(); i++)
			if (inboxes.get(i).getFolder().equals(folder))
				return i + 1;
		return 0;
	}

	@Override
	protected void setActive(boolean active) {
		label.setEnabled(active);
		combo.setEnabled(active);
	}

	@Override
	protected void restoreDefault() {
		combo.select(0);
	}

	@Override
	protected void save() {
		int index = combo.getSelectionIndex();
		SettingsConf.Str.McpFileFolder.set(index <= 0 ? "" : inboxes.get(index - 1).getFolder().getPath());
	}

}
