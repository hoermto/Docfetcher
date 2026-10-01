/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import java.util.EnumMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.gui.ResultPanel.DocAction;
import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.gui.MenuAction;
import net.sourceforge.docfetcher.util.gui.ToolItemFactory;

/**
 * The toolbar of the main window. Its buttons run the actions of the result
 * panel the user worked with last on the documents selected there, the same
 * actions the context menu of that panel offers; a button is enabled when the
 * action is possible for the current selection.
 */
final class DocToolBar {

	/** One button: the action, its icon and its tooltip. */
	private static final class Button {
		final DocAction action;
		final Img image;
		final Msg toolTip;

		Button(@NotNull DocAction action, @NotNull Img image, @NotNull Msg toolTip) {
			this.action = action;
			this.image = image;
			this.toolTip = toolTip;
		}
	}

	/** The buttons in groups; the groups are divided by separators. */
	private static final Button[][] GROUPS = {
		{
			new Button(DocAction.FOLDER_ROOTS, Img.TB_ROOTS, Msg.folder_roots_bt),
			new Button(DocAction.FOLDER_UP, Img.TB_UP, Msg.folder_up),
			new Button(DocAction.FOLDER_REFRESH, Img.TB_REFRESH, Msg.refresh),
			new Button(DocAction.NEW_FOLDER, Img.TB_NEW_FOLDER, Msg.new_folder),
		},
		{
			new Button(DocAction.PRINT, Img.TB_PRINT, Msg.toolbar_print),
			new Button(DocAction.MAIL, Img.TB_MAIL, Msg.toolbar_mail),
		},
		{
			new Button(DocAction.RENAME, Img.TB_RENAME, Msg.toolbar_rename),
			new Button(DocAction.MOVE, Img.TB_MOVE, Msg.toolbar_move),
		},
		{
			new Button(DocAction.MERGE, Img.TB_MERGE, Msg.toolbar_merge),
			new Button(DocAction.SPLIT, Img.TB_SPLIT, Msg.toolbar_split),
		},
		{
			new Button(DocAction.DELETE, Img.TB_DELETE, Msg.toolbar_delete),
		},
	};

	private final ToolBar toolBar;
	private final Map<DocAction, ToolItem> items = new EnumMap<DocAction, ToolItem>(DocAction.class);

	public DocToolBar(@NotNull Composite parent) {
		toolBar = new ToolBar(parent, SWT.FLAT | SWT.HORIZONTAL);
		ToolItemFactory factory = new ToolItemFactory(toolBar).enabled(false);
		for (int i = 0; i < GROUPS.length; i++) {
			if (i > 0)
				new ToolItem(toolBar, SWT.SEPARATOR);
			for (final Button button : GROUPS[i]) {
				ToolItem item = factory
					.image(button.image.get())
					.toolTip(button.toolTip.get())
					.listener(new SelectionAdapter() {
						public void widgetSelected(SelectionEvent e) {
							run(button.action);
						}
					})
					.create();
				items.put(button.action, item);
			}
			if (i == 0) {
				createSyncItem(); // After the folder navigation
				new ToolItem(toolBar, SWT.SEPARATOR);
				createPaneModeItems();
			}
		}

		final Event.Listener<Void> stateListener = new Event.Listener<Void>() {
			public void update(Void eventData) {
				updateEnabledState();
			}
		};
		ResultPanel.evtActiveStateChanged.add(stateListener);
		toolBar.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				ResultPanel.evtActiveStateChanged.remove(stateListener);
			}
		});
		updateEnabledState();
	}

	@Nullable private ToolItem syncItem;

	/**
	 * A toggle for the synced folder view: the 'Folders' tab shows the parent
	 * of the folder browser next to it, and the rightmost folder browser
	 * follows the document selected in the search results or an inbox (with
	 * one pane: the Folders tab). Always enabled, since it works in all modes.
	 */
	private void createSyncItem() {
		final ToolItem item = new ToolItemFactory(toolBar)
			.style(SWT.CHECK)
			.image(Img.TB_SYNC.get())
			.toolTip(Msg.toolbar_sync.get())
			.create();
		item.setSelection(SettingsConf.Bool.SyncFolderPanels.get());
		item.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				SettingsConf.Bool.SyncFolderPanels.set(item.getSelection());
			}
		});
		final Event.Listener<Boolean> syncListener = new Event.Listener<Boolean>() {
			public void update(Boolean enabled) {
				if (!item.isDisposed())
					item.setSelection(enabled);
			}
		};
		SettingsConf.Bool.SyncFolderPanels.evtChanged.add(syncListener);
		item.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				SettingsConf.Bool.SyncFolderPanels.evtChanged.remove(syncListener);
			}
		});
		syncItem = item;
	}

	private final ToolItem[] paneItems = new ToolItem[3];

	/**
	 * Three buttons for the number of panes next to the document view (see
	 * Application.getPaneMode); the pressed one shows the current mode.
	 */
	private void createPaneModeItems() {
		Img[] images = { Img.TB_PANES1, Img.TB_PANES2, Img.TB_PANES3 };
		Msg[] toolTips = { Msg.toolbar_one_pane, Msg.toolbar_two_panes, Msg.toolbar_three_panes };
		for (int i = 0; i < 3; i++) {
			final int mode = i + 1;
			paneItems[i] = new ToolItemFactory(toolBar)
				.style(SWT.RADIO)
				.image(images[i].get())
				.toolTip(toolTips[i].get())
				.listener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						if (((ToolItem) e.widget).getSelection())
							SettingsConf.Int.PaneMode.set(mode);
					}
				})
				.create();
		}
	}

	/** Presses the button of the given pane mode; the tool bar may be null (at startup). */
	static void selectPaneMode(@Nullable DocToolBar toolBar, int mode) {
		if (toolBar == null)
			return;
		for (int i = 0; i < toolBar.paneItems.length; i++)
			if (toolBar.paneItems[i] != null && !toolBar.paneItems[i].isDisposed())
				toolBar.paneItems[i].setSelection(i + 1 == mode);
	}

	/** The button of a pane mode (1-3); for tests. */
	@NotNull
	ToolItem getPaneItem(int mode) {
		return paneItems[mode - 1];
	}

	/** The sync toggle; for tests. */
	@Nullable
	ToolItem getSyncItem() {
		return syncItem;
	}

	@NotNull
	public ToolBar getControl() {
		return toolBar;
	}

	/** Returns the action of the active panel, or null if there is none. */
	@Nullable
	private static MenuAction getAction(@NotNull DocAction key) {
		ResultPanel panel = ResultPanel.getActivePanel();
		return panel == null ? null : panel.getDocAction(key);
	}

	private void run(@NotNull DocAction key) {
		MenuAction action = getAction(key);
		if (action != null && action.isEnabled())
			action.run();
		if (!toolBar.isDisposed())
			updateEnabledState();
	}

	/** Enables the buttons whose action is possible for the current selection. */
	public void updateEnabledState() {
		for (Map.Entry<DocAction, ToolItem> entry : items.entrySet()) {
			MenuAction action = getAction(entry.getKey());
			boolean enabled = action != null && action.isEnabled();
			if (entry.getValue().getEnabled() != enabled)
				entry.getValue().setEnabled(enabled);
		}
	}

	/** Returns the button of the given action, for tests. */
	@NotNull
	ToolItem getItem(@NotNull DocAction key) {
		return items.get(key);
	}

}
