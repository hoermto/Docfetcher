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

package net.sourceforge.docfetcher.gui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.ProgramConf;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.collect.MemoryList;

/**
 * @author Tran Nam Quang
 */
public final class SearchBar {

	private static final int MARGIN = Util.IS_WINDOWS ? 1 : 0;
	private static final int SPACING = 1;

	public final Event<String> evtSearch = new Event<String>();
	public final Event<String> evtSearchTypeAhead = new Event<String>();
	public final Event<Void> evtOpenSearchSettings = new Event<Void>();

	private final Composite comp;
	private final Combo searchBox;
	private final Button searchBt;
	private final Button searchSettingsBt;
	private final MemoryList<String> searchHistory;

	public SearchBar(@NotNull Composite parent) {
		comp = new CustomBorderComposite(parent) {
			public Point computeSize(int wHint, int hHint, boolean changed) {
				return SearchBar.this.computeSize(wHint, hHint);
			}
			@Override
			protected boolean isBorderVisible(int side) {
				return side != SWT.BOTTOM;
			}
		};
		searchBox = new Combo(comp, SWT.BORDER);
		searchBox.setVisibleItemCount(ProgramConf.Int.SearchHistorySize.get());
		UtilGui.selectAllOnFocus(searchBox);

		searchBox.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				if (UtilGui.isEnterKey(e.keyCode)) {
					String query = searchBox.getText().trim();
					if (!query.isEmpty()) {
						evtSearch.fire(query);
					}
				}
			}
		});
		
		searchBox.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (SettingsConf.Bool.UseTypeAheadSearch.get()) {
					evtSearchTypeAhead.fire(searchBox.getText().trim());
				}
			}
		});

		// Load search history
		searchHistory = new MemoryList<String>(ProgramConf.Int.SearchHistorySize.get());
		searchHistory.addAll(SettingsConf.StrList.SearchHistory.get()); // may discard items
		searchBox.setItems(getHistoryArray());

		searchBt = new Button(comp, SWT.PUSH);
		searchBt.setText(Msg.search.get());
		searchBt.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				String query = searchBox.getText().trim();
				if (!query.isEmpty())
					evtSearch.fire(query);
			}
		});

		// Opens the dialog with the search filters (document types, search scope)
		searchSettingsBt = new Button(comp, SWT.PUSH);
		searchSettingsBt.setText(Msg.search_settings_bt.get());
		searchSettingsBt.setToolTipText(Msg.document_types.get() + ", " + Msg.search_scope.get());
		searchSettingsBt.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				evtOpenSearchSettings.fire(null);
			}
		});

		// Make the search box smaller when there's not enough space left
		comp.addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent e) {
				Point searchBoxSize = computeSize(searchBox);
				Point searchBtSize = computeSize(searchBt);
				Point settingsBtSize = computeSize(searchSettingsBt);

				Rectangle clientArea = comp.getClientArea();
				int caWidth = clientArea.width;
				int caHeight = clientArea.height;

				int spaceLeft = caWidth - MARGIN * 2;
				spaceLeft -= searchBtSize.x;
				spaceLeft -= settingsBtSize.x + SPACING;

				int searchBoxMaxWidth = ProgramConf.Int.SearchBoxMaxWidth.get();
				int searchBoxWidth = Util.clamp(spaceLeft, 0, searchBoxMaxWidth);
				setBounds(searchBox, MARGIN, searchBoxWidth, caHeight, searchBoxSize.y);

				int searchBtX = MARGIN + searchBox.getSize().x + SPACING;
				setBounds(searchBt, searchBtX, searchBtSize.x, caHeight, searchBtSize.y);

				int settingsBtX = searchBtX + searchBtSize.x + SPACING;
				setBounds(searchSettingsBt, settingsBtX, settingsBtSize.x, caHeight, settingsBtSize.y);
			}
		});
	}

	private void setBounds(	@NotNull Control control,
							int x,
							int width,
							int compHeight,
							int controlHeight) {
		int y = Math.max(0, (compHeight - controlHeight) / 2);
		control.setLocation(x, y);
		control.setSize(width, Math.min(compHeight, controlHeight));
	}

	@NotNull
	private Point computeSize(int wHint, int hHint) {
		Point searchBoxSize = computeSize(searchBox);
		Point searchBtSize = computeSize(searchBt);
		Point settingsBtSize = computeSize(searchSettingsBt);

		int width = 0;
		width += searchBoxSize.x;
		width += searchBtSize.x;
		width += settingsBtSize.x;
		width += MARGIN * 2 + SPACING * 2;

		int height = Math.max(searchBoxSize.y, searchBtSize.y);
		height = Math.max(height, settingsBtSize.y);
		height += MARGIN * 2;

		if (wHint != SWT.DEFAULT)
			width = wHint;
		if (hHint != SWT.DEFAULT)
			height = hHint;
		return new Point(width, height);
	}

	@NotNull
	private Point computeSize(@NotNull Control control) {
		return control.computeSize(SWT.DEFAULT, SWT.DEFAULT, false);
	}

	public void addToSearchHistory(@NotNull String query) {
		Util.checkNotNull(query);
		searchHistory.add(query);
		String[] historyArray = getHistoryArray();
		SettingsConf.StrList.SearchHistory.set(historyArray);
		searchBox.setItems(historyArray);
		searchBox.setText(query);
	}

	@NotNull
	private String[] getHistoryArray() {
		return searchHistory.toArray(new String[searchHistory.size()]);
	}

	@NotNull
	public Control getControl() {
		return comp;
	}

	public boolean isEnabled() {
		return searchBox.isEnabled();
	}

	public void setEnabled(boolean enabled) {
		searchBox.setEnabled(enabled);
		searchBt.setEnabled(enabled);
	}

	public boolean setFocus() {
		return searchBox.setFocus();
	}

}
