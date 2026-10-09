/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui.pref;

import java.io.File;
import java.io.IOException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.gui.pref.PrefDialog.PrefOption;
import net.sourceforge.docfetcher.mcp.McpExtension;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * A link below the MCP server options that opens a short help on connecting
 * Claude (Claude Code and Claude Desktop), with buttons to copy the command,
 * the configuration and to save the Claude Desktop extension. The port is
 * taken from the port field, so the help matches what is about to be saved.
 */
final class McpHelpOption extends PrefOption {

	@NotNull private final IntFieldOption portOption;

	public McpHelpOption(@NotNull IntFieldOption portOption) {
		super(Msg.mcp_help_link.get());
		this.portOption = portOption;
	}

	/** The command that adds the server to Claude Code. */
	@NotNull
	static String claudeCodeCommand(@NotNull String port) {
		return "claude mcp add --transport http docfetcher http://localhost:" + port + "/mcp";
	}

	/**
	 * The entry for claude_desktop_config.json; Claude Desktop starts local
	 * servers, hence the bridge. With -y, npx installs it without asking.
	 */
	@NotNull
	static String claudeDesktopConfig(@NotNull String port) {
		return "{\n  \"mcpServers\": {\n    \"docfetcher\": {\n      \"command\": \"npx\",\n"
			+ "      \"args\": [\"-y\", \"mcp-remote\", \"http://localhost:" + port + "/mcp\"]\n    }\n  }\n}";
	}

	@Override
	protected void createControls(Composite parent) {
		new Label(parent, SWT.NONE); // First column stays empty
		Link link = new Link(parent, SWT.NONE);
		link.setText("<a>" + labelText + "</a>");
		link.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		link.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				openHelp(((Link) e.widget).getShell(), portOption.getText());
			}
		});
	}

	/** Opens the help on connecting Claude, for the given port. */
	static void openHelp(@NotNull Shell parent, @NotNull final String port) {
		final Shell shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
		shell.setText(Msg.mcp_help_title.get());
		shell.setLayout(new GridLayout(4, false));

		Text text = new Text(shell, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL | SWT.BORDER);
		text.setText(Msg.mcp_help_text.format(claudeCodeCommand(port), claudeDesktopConfig(port)));
		GridData textData = new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1);
		int dpi = shell.getDisplay().getDPI().x;
		textData.widthHint = dpi * 6;
		textData.heightHint = dpi * 4;
		text.setLayoutData(textData);

		Button copyCommand = UtilGui.createPushButton(shell, Msg.mcp_copy_command.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				UtilGui.setClipboard(claudeCodeCommand(port));
			}
		});
		copyCommand.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		Button copyConfig = UtilGui.createPushButton(shell, Msg.mcp_copy_config.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				UtilGui.setClipboard(claudeDesktopConfig(port));
			}
		});
		copyConfig.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		Button extension = UtilGui.createPushButton(shell, Msg.mcp_create_extension.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				saveExtension(shell, port);
			}
		});
		extension.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		Button close = UtilGui.createPushButton(shell, Msg.close.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				shell.close();
			}
		});
		close.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		shell.setDefaultButton(close);

		shell.pack();
		shell.setLocation(parent.getLocation().x + 40, parent.getLocation().y + 40);
		shell.open();
	}

	/**
	 * Asks where to save the Claude Desktop extension (default: Downloads),
	 * writes it and shows it in the file manager, to be dragged into Claude
	 * Desktop.
	 */
	private static void saveExtension(@NotNull Shell shell, @NotNull String port) {
		FileDialog dialog = new FileDialog(shell, SWT.SAVE);
		dialog.setFileName(McpExtension.FILE_NAME);
		dialog.setFilterExtensions(new String[] {"*.mcpb"});
		dialog.setOverwrite(true);
		File downloads = new File(Util.USER_HOME_PATH, "Downloads");
		dialog.setFilterPath(downloads.isDirectory() ? downloads.getPath() : Util.USER_HOME_PATH);
		String path = dialog.open();
		if (path == null)
			return;
		try {
			McpExtension.write(new File(path), port, new File(AppUtil.getImageDir(), "docfetcher128.png"));
		}
		catch (IOException e) {
			AppUtil.showError(e.getMessage(), true, false);
			return;
		}
		if (!Util.winOpenDir(path))
			UtilGui.launch(new File(path).getParent());
	}

	@Override
	protected void restoreDefault() {
	}

	@Override
	protected void save() {
	}

}
