/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui.filter;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.gui.filter.TwoFormExpander.MaximizedControl;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.parse.ParseService;
import net.sourceforge.docfetcher.model.parse.Parser;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.collect.ListMap;

/**
 * A modal dialog holding the search filters: the document types list and the
 * search scope tree. The dialog is created once and only hidden when closed,
 * so the filter panels (and everything wired to them, such as the search
 * queue) stay alive for the lifetime of the program. Filter changes take
 * effect immediately, while the dialog is still open.
 */
public final class SearchSettingsDialog {

	/** Fired when the 'match file names' option was toggled. */
	public final net.sourceforge.docfetcher.util.Event<Void> evtMatchFilenamesChanged = new net.sourceforge.docfetcher.util.Event<Void>();
	
	private final Shell shell;
	private final Shell mainShell;
	private final FileTypePanel fileTypePanel;
	private final IndexPanel indexPanel;
	private final TwoFormExpander expander;

	/**
	 * @param mainShell
	 *            The main shell. It is also used as parent for the dialogs
	 *            opened by the index panel, e.g. the indexing dialog.
	 * @param indexRegistryLoaded
	 *            Whether the index registry has finished loading. If not, the
	 *            search scope header shows a loading hint until
	 *            {@link #setIndexRegistryLoaded()} is called.
	 */
	public SearchSettingsDialog(	@NotNull final Shell mainShell,
									@NotNull final IndexRegistry indexRegistry,
									boolean indexRegistryLoaded) {
		Util.checkNotNull(mainShell, indexRegistry);
		this.mainShell = mainShell;

		shell = new Shell(mainShell, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.PRIMARY_MODAL);
		shell.setText(Msg.search_settings_title.get());
		shell.setImage(Img.PREFERENCES.get());
		shell.setLayout(UtilGui.createGridLayout(1, false, 10, 5));
		SettingsConf.ShellBounds.SearchSettingsDialog.bind(shell);

		// Hide instead of disposing when closed, e.g. via the close button or Escape
		shell.addListener(SWT.Close, new Listener() {
			public void handleEvent(Event e) {
				e.doit = false;
				close();
			}
		});

		final Button matchFilenamesBt = UtilGui.createCheckButton(shell, Msg.match_filenames.get());
		matchFilenamesBt.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		matchFilenamesBt.setSelection(SettingsConf.Bool.MatchFilenames.get());
		matchFilenamesBt.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				SettingsConf.Bool.MatchFilenames.set(matchFilenamesBt.getSelection());
				evtMatchFilenamesChanged.fire(null);
			}
		});
		
		final FileTypePanel[] fileTypePanelHolder = new FileTypePanel[1];
		final IndexPanel[] indexPanelHolder = new IndexPanel[1];

		expander = new TwoFormExpander(shell) {
			protected Control createFirstContents(Composite parent) {
				// TODO websearch: Load parser states from file, save parser states to file?
				List<Parser> parsers = ParseService.getParsers();
				ListMap<Parser, Boolean> map = ListMap.create(parsers.size());
				for (Parser parser : parsers)
					map.add(parser, true);
				fileTypePanelHolder[0] = new FileTypePanel(parent, map);
				return fileTypePanelHolder[0].getControl();
			}
			protected Control createSecondContents(Composite parent) {
				indexPanelHolder[0] = new IndexPanel(parent, indexRegistry, mainShell);
				return indexPanelHolder[0].getControl();
			}
			protected void onMaximizationChanged() {
				// Save maximization states
				MaximizedControl maxControl = getMaximizedControl();
				boolean topMax = maxControl == MaximizedControl.TOP;
				boolean bottomMax = maxControl == MaximizedControl.BOTTOM;
				SettingsConf.Bool.TypesFilterMaximized.set(topMax);
				SettingsConf.Bool.LocationFilterMaximized.set(bottomMax);
			}
		};
		fileTypePanel = fileTypePanelHolder[0];
		indexPanel = indexPanelHolder[0];
		expander.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		expander.setTopText(Msg.document_types.get());
		expander.setSashWidth(5);
		if (indexRegistryLoaded)
			setIndexRegistryLoaded();
		else
			expander.setBottomText(Msg.search_scope.get() + " (" + Msg.loading.get() + ")");

		// Restore sash weights and maximization states
		expander.setSashWeights(SettingsConf.IntArray.FilterSash.get());
		if (SettingsConf.Bool.TypesFilterMaximized.get())
			expander.setMaximizedControl(MaximizedControl.TOP);
		if (SettingsConf.Bool.LocationFilterMaximized.get())
			expander.setMaximizedControl(MaximizedControl.BOTTOM);

		// Save sash weights
		expander.getFirstControl().addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent e) {
				// Run in asyncExec to make sure both controls have been resized
				UtilGui.runAsyncExec(expander, new Runnable() {
					public void run() {
						MaximizedControl maxControl = expander.getMaximizedControl();
						if (maxControl != MaximizedControl.NONE)
							return;
						int[] weights = expander.getSashWeights();
						SettingsConf.IntArray.FilterSash.set(weights);
					}
				});
			}
		});

		Label separator = new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL);
		separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Button closeBt = UtilGui.createPushButton(shell, Msg.close.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				close();
			}
		});
		GridData closeData = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
		closeData.minimumWidth = UtilGui.BTW;
		closeBt.setLayoutData(closeData);
		shell.setDefaultButton(closeBt);
	}

	/**
	 * Removes the loading hint from the search scope header.
	 */
	public void setIndexRegistryLoaded() {
		UtilGui.assertSwtThread();
		if (!shell.isDisposed())
			expander.setBottomText(Msg.search_scope.get());
	}

	/**
	 * Shows the dialog, or brings it to the front if it is already visible.
	 */
	public void open() {
		UtilGui.assertSwtThread();
		if (shell.isDisposed())
			return;
		if (shell.isVisible())
			shell.setActive();
		else
			shell.open();
	}

	/**
	 * Hides the dialog. The filter panels keep their state.
	 */
	public void close() {
		if (shell.isDisposed())
			return;
		shell.setVisible(false);
		/*
		 * Hiding (rather than disposing) a modal shell doesn't hand the
		 * keyboard focus back to the parent on all platforms (observed on
		 * GTK), so activate the main window explicitly.
		 */
		if (!mainShell.isDisposed())
			mainShell.setActive();
	}

	public boolean isOpen() {
		return !shell.isDisposed() && shell.isVisible();
	}

	@NotNull
	public FileTypePanel getFileTypePanel() {
		return fileTypePanel;
	}

	@NotNull
	public IndexPanel getIndexPanel() {
		return indexPanel;
	}

}
