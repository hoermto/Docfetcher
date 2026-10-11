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
 * AI assistants (Claude Code, Claude Desktop, ChatGPT desktop/Codex, others),
 * with buttons to copy the commands and configurations and to save the Claude
 * Desktop extension. The port is
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

	/** The address for assistants that connect over HTTP themselves. */
	@NotNull
	static String url(@NotNull String port) {
		return "http://localhost:" + port + "/mcp";
	}

	/**
	 * The entry for ~/.codex/config.toml, which the ChatGPT desktop app shares
	 * with Codex; both connect over HTTP, no bridge needed.
	 */
	@NotNull
	static String chatGptConfig(@NotNull String port) {
		return "[mcp_servers.docfetcher]\nurl = \"" + url(port) + "\"";
	}

	/** The Codex command that writes {@link #chatGptConfig}. */
	@NotNull
	static String codexCommand(@NotNull String port) {
		return "codex mcp add docfetcher --url " + url(port);
	}

	/** The help text with the commands and configurations for the port. */
	@NotNull
	static String helpText(@NotNull String port) {
		return Msg.mcp_help_text.format(claudeCodeCommand(port), claudeDesktopConfig(port),
			chatGptConfig(port), codexCommand(port), url(port));
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

	/** Opens the help on connecting AI assistants, for the given port. */
	static void openHelp(@NotNull Shell parent, @NotNull final String port) {
		final Shell shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
		shell.setText(Msg.mcp_help_title.get());
		shell.setLayout(new GridLayout(3, false));

		Text text = new Text(shell, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL | SWT.BORDER);
		text.setText(helpText(port));
		GridData textData = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
		int dpi = shell.getDisplay().getDPI().x;
		textData.widthHint = dpi * 6;
		textData.heightHint = dpi * 4;
		text.setLayoutData(textData);

		// In the order of the steps: Claude Code, Claude Desktop, ChatGPT; then other apps
		copyButton(shell, Msg.mcp_copy_command.get(), claudeCodeCommand(port));
		Button extension = UtilGui.createPushButton(shell, Msg.mcp_create_extension.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				saveExtension(shell, port);
			}
		});
		extension.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		copyButton(shell, Msg.mcp_copy_chatgpt_config.get(), chatGptConfig(port));
		copyButton(shell, Msg.mcp_copy_config.get(), claudeDesktopConfig(port));
		new Label(shell, SWT.NONE);
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

	private static void copyButton(@NotNull Shell shell, @NotNull String label, @NotNull final String text) {
		Button button = UtilGui.createPushButton(shell, label, new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				UtilGui.setClipboard(text);
			}
		});
		button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
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
