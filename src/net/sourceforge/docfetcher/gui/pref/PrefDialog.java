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

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import net.sourceforge.docfetcher.enums.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

import net.sourceforge.docfetcher.gui.ManualLocator;
import net.sourceforge.docfetcher.gui.filter.IndexPanel;
import net.sourceforge.docfetcher.model.ocr.OcrEngine;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.WindowsAutostart;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.VisibleForPackageGroup;
import net.sourceforge.docfetcher.util.gui.ConfigComposite;
import net.sourceforge.docfetcher.util.gui.FormDataFactory;

/**
 * @author Tran Nam Quang
 */
@VisibleForPackageGroup
public final class PrefDialog {

	public final Event<Void> evtOKClicked = new Event<Void> ();
	
	private final Shell shell;
	@NotNull private Button okBt;
	private final List<PrefOption> checkOptions = new LinkedList<PrefOption>();
	private final List<PrefOption> fieldOptions = new LinkedList<PrefOption>();
	private final File programConfFile;
	private final IndexPanel indexPanel;
	private final File defaultIndexDir;
	private final DropdownOption analyzerOption;
	private final int oldAnalyzer;
	@NotNull private TabFolder tabFolder;
	@NotNull private IndexManagementPanel indexManagementPanel;
	@NotNull private FoldersPanel foldersPanel;

	/**
	 * @param indexPanel
	 *            The search scope panel of the main window, which provides
	 *            the index management operations for the 'Indexes' tab.
	 * @param defaultIndexDir
	 *            The index folder used when no custom index folder is set.
	 */
	public PrefDialog(	@NotNull Shell parent,
						@NotNull File programConfFile,
						@NotNull IndexPanel indexPanel,
						@NotNull File defaultIndexDir) {
		Util.checkNotNull(parent, indexPanel, defaultIndexDir);
		this.programConfFile = programConfFile;
		this.indexPanel = indexPanel;
		this.defaultIndexDir = defaultIndexDir;
		
		shell = new Shell(parent, SWT.PRIMARY_MODAL | SWT.SHELL_TRIM);
		shell.setLayout(UtilGui.createFillLayout(10));
		shell.setText(Msg.preferences.get());
		shell.setImage(Img.PREFERENCES.get());
		SettingsConf.ShellBounds.PreferencesDialog.bind(shell);

		checkOptions.addAll(Arrays.<PrefOption> asList(
			new CheckOption(
				Msg.pref_manual_on_startup.get(),
				SettingsConf.Bool.ShowManualOnStartup),

			new CheckOption(
				Msg.pref_use_or_operator.get(),
				SettingsConf.Bool.UseOrOperator),

			new CheckOption(
				Msg.pref_tolerant_search.get(),
				SettingsConf.Bool.TolerantSearch),

			new CheckOption(
				Msg.pref_scroll_to_first_match.get(),
				SettingsConf.Bool.AutoScrollToFirstMatch),

            new CheckOption(
            	Msg.pref_use_type_ahead_search.get(),
            	SettingsConf.Bool.UseTypeAheadSearch)
		));

		if (!Util.IS_UBUNTU_UNITY) {
			checkOptions.add(new CheckOption(
				Msg.pref_hide_in_systray.get(),
				SettingsConf.Bool.HideOnOpen));
		}
		
		if (WindowsAutostart.isSupported()) {
			checkOptions.add(new AutostartOption(
				Msg.pref_start_with_windows.get(),
				ProgramConf.Str.AppName.get()));
		}

		checkOptions.addAll(Arrays.<PrefOption> asList(
			new CheckOption(
				Msg.pref_clear_search_history_on_exit.get(),
				SettingsConf.Bool.ClearSearchHistoryOnExit),

			new CheckOption(
				Msg.pref_update_indexes_on_startup.get(),
				SettingsConf.Bool.UpdateIndexesOnStartup),

			new CheckOption(
				Msg.pref_daily_index_update.get(),
				SettingsConf.Bool.DailyIndexUpdate),

			new CheckOption(
				Msg.pref_show_path_column.get(),
				SettingsConf.Bool.ShowPathColumn),

			new CheckOption(
				Msg.pref_sync_folder_view.get(),
				SettingsConf.Bool.SyncFolderView),

			new CheckOption(
				Msg.pref_ocr.get(),
				SettingsConf.Bool.OcrEnabled),

			new CheckOption(
				Msg.pref_store_ocr_text.get(),
				SettingsConf.Bool.StoreOcrTextInFiles),

			new CheckOption(
				Msg.pref_convert_tiff.get(),
				SettingsConf.Bool.ConvertTiffToPdf),

			new CheckOption(
				Msg.pref_keep_tiff.get(),
				SettingsConf.Bool.KeepTiffAfterConversion)

			// TODO post-release-1.1: Implement this; requires saving and restoring the tree expansion state
			//	new CheckOption(
			//		"Reset location filter on exit",
			//		SettingsConf.Bool.ResetLocationFilterOnExit),
		));
		
		oldAnalyzer = SettingsConf.Int.LuceneAnalyzer.get();
		
		String[] choices = new String[] {
			Msg.pref_word_seg_standard.get(),
			Msg.pref_word_seg_source_code.get(),
			Msg.pref_word_seg_whitespace.get(),
			Msg.pref_word_seg_chinese.get()
		};
		int[] choiceIndices = new int[] {
			0, 1, 3, 2 // for backward compatibility with versions <= 1.1.22
		};
		
		analyzerOption = new DropdownOption(
			Msg.pref_word_segmentation.get(),
			SettingsConf.Int.LuceneAnalyzer,
			choices, choiceIndices);
		
		// The system tray is not supported on Ubuntu Unity
		String[] closeChoices;
		int[] closeIndices;
		if (Util.IS_UBUNTU_UNITY) {
			closeChoices = new String[] {
				Msg.close_action_exit.get(),
				Msg.close_action_minimize.get()
			};
			closeIndices = new int[] {
				SettingsConf.CLOSE_ACTION_EXIT,
				SettingsConf.CLOSE_ACTION_MINIMIZE
			};
		}
		else {
			closeChoices = new String[] {
				Msg.close_action_exit.get(),
				Msg.close_action_minimize.get(),
				Msg.close_action_tray.get()
			};
			closeIndices = new int[] {
				SettingsConf.CLOSE_ACTION_EXIT,
				SettingsConf.CLOSE_ACTION_MINIMIZE,
				SettingsConf.CLOSE_ACTION_TRAY
			};
		}
		
		fieldOptions.addAll(Arrays.asList(
			new DropdownOption(
				Msg.pref_close_action.get(),
				SettingsConf.Int.CloseAction,
				closeChoices, closeIndices),
			
			new BoolDropdownOption(
				Msg.pref_preview_position.get(),
				SettingsConf.Bool.ShowPreviewPanelAtBottom,
				Msg.pref_preview_bottom.get(),
				Msg.pref_preview_right.get()),
			
			new DropdownOption(
				Msg.pref_remove_originals.get(),
				SettingsConf.Int.RemoveOriginals,
				new String[] { Msg.pref_originals_ask.get(),
					Msg.pref_originals_remove.get(), Msg.pref_originals_keep.get() },
				new int[] { SettingsConf.ORIGINALS_ASK, SettingsConf.ORIGINALS_REMOVE,
					SettingsConf.ORIGINALS_KEEP }),
			
			new DropdownOption(
				Msg.pref_folder_path_segments.get(),
				SettingsConf.Int.FolderPathSegments,
				new String[] { "1", "2", "3", "4", "5" },
				new int[] { 1, 2, 3, 4, 5 }),
			
			new DropdownOption(
				Msg.pref_icon_size.get(),
				SettingsConf.Int.IconScalePercent,
				new String[] { Msg.pref_icon_size_auto.get(), "100 %", "125 %",
					"150 %", "200 %" },
				new int[] { 0, 100, 125, 150, 200 }),
			
			analyzerOption,

			new ColorOption(
				Msg.pref_highlight_color.get(),
				SettingsConf.IntArray.PreviewHighlighting),

			new FontOption(
				Msg.pref_font_normal.get(),
				UtilGui.getPreviewFontNormal()),

			new FontOption(
				Msg.pref_font_fixed_width.get(),
				UtilGui.getPreviewFontMono())
		));

		boolean hotkeyEnabled = true;
		if (!ProgramConf.Bool.HotkeyEnabled.get()) {
			hotkeyEnabled = false;
		}
		else if (Util.IS_MAC_OS_X) {
			hotkeyEnabled = false;
		}
		if (hotkeyEnabled) {
			fieldOptions.add(new HotkeyOption(Msg.pref_hotkey.get()));
		}

		new ConfigComposite(shell, SWT.H_SCROLL | SWT.V_SCROLL) {
			protected Control createContents(Composite parent) {
				return PrefDialog.this.createContents(parent);
			}
			protected Control createButtonArea(Composite parent) {
				return PrefDialog.this.createButtonArea(parent);
			}
		};
	}

	@NotNull
	private Control createContents(@NotNull Composite parent) {
		tabFolder = new TabFolder(parent, SWT.TOP);
		
		TabItem generalTab = new TabItem(tabFolder, SWT.NONE);
		generalTab.setText(Msg.pref_tab_general.get());
		generalTab.setControl(createGeneralTab(tabFolder));
		
		TabItem indexesTab = new TabItem(tabFolder, SWT.NONE);
		indexesTab.setText(Msg.pref_tab_indexes.get());
		indexManagementPanel = new IndexManagementPanel(
			tabFolder, indexPanel, defaultIndexDir);
		indexesTab.setControl(indexManagementPanel.getControl());
		
		TabItem foldersTab = new TabItem(tabFolder, SWT.NONE);
		foldersTab.setText(Msg.pref_tab_folders.get());
		foldersPanel = new FoldersPanel(tabFolder);
		foldersTab.setControl(foldersPanel.getControl());
		
		return tabFolder;
	}
	
	@NotNull
	private Control createGeneralTab(@NotNull Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(UtilGui.createGridLayout(2, false, 5, 5));

		for (PrefOption checkOption : checkOptions)
			checkOption.createControls(comp);

		// Explain why text recognition can't be used, e.g. missing runtime
		String ocrProblem = OcrEngine.getInstance().getUnavailableReason();
		if (ocrProblem != null) {
			Label ocrLabel = new Label(comp, SWT.WRAP);
			ocrLabel.setText(Msg.ocr_unavailable.format(ocrProblem));
			ocrLabel.setForeground(comp.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND));
			GridData ocrData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
			ocrData.widthHint = 500;
			ocrLabel.setLayoutData(ocrData);
		}

		Label spacing = new Label(comp, SWT.NONE);
		GridData spacingGridData = new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1);
		spacingGridData.heightHint = 3;
		spacing.setLayoutData(spacingGridData);

		for (PrefOption fieldOption : fieldOptions)
			fieldOption.createControls(comp);

		Label spacing2 = new Label(comp, SWT.NONE);
		spacing2.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		
		Link link = new Link(comp, SWT.NONE);
		link.setText("<a>" + Msg.advanced_settings_link.get() + "</a>");
		link.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, true, false));
		link.setVisible(ProgramConf.Bool.ShowAdvancedSettingsLink.get());
		link.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				UtilGui.launch(programConfFile);
			}
		});

		return comp;
	}

	@NotNull
	private Control createButtonArea(@NotNull Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);

		Button helpBt = UtilGui.createPushButton(comp, Msg.help.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				File file = ManualLocator.getManualSubpageFile("Preferences.html");
				if (file == null) {
					AppUtil.showError(Msg.file_not_found.get() + "\n" +
							"Preferences.html", true, false);
				} else {
					UtilGui.launch(file);
				}
			}
		});

		Button resetBt = UtilGui.createPushButton(comp, Msg.restore_defaults.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				for (PrefOption checkOption : checkOptions)
					checkOption.restoreDefault();
				for (PrefOption fieldOption : fieldOptions)
					fieldOption.restoreDefault();
				indexManagementPanel.restoreDefaultFolder();
				foldersPanel.restoreDefaults();
			}
		});
		
		// Show app name, app version and maximum heap size
		Composite infoComp = new Composite(comp, SWT.NONE);
		GridLayout infoCompLayout = new GridLayout(2, false);
		infoCompLayout.marginWidth = 0;
		infoCompLayout.marginHeight = 0;
		infoCompLayout.horizontalSpacing = 15;
		infoComp.setLayout(infoCompLayout);
		Label appLabel = new Label(infoComp, SWT.NONE);
		appLabel.setText(getAppNameAndVersion());
		Label memLimitLabel = new Label(infoComp, SWT.NONE);
		memLimitLabel.setText(getMemLimitString());
		for (Label label : Arrays.asList(appLabel, memLimitLabel)) {
			label.setForeground(
				label.getDisplay().getSystemColor(
					SWT.COLOR_WIDGET_DISABLED_FOREGROUND
				)
			);
			label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, true));
		}

		okBt = UtilGui.createPushButton(comp, Msg.ok.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (!indexManagementPanel.validateFolder()) {
					tabFolder.setSelection(1);
					return;
				}
				if (!foldersPanel.save()) {
					tabFolder.setSelection(2);
					return;
				}
				boolean oldTolerant = SettingsConf.Bool.TolerantSearch.get();
				for (PrefOption checkOption : checkOptions)
					checkOption.save();
				if (oldTolerant != SettingsConf.Bool.TolerantSearch.get()) {
					// Like a new word segmentation, this needs rebuilt indexes
					AppUtil.showInfo(Msg.rebuild_indexes.get());
					IndexRegistry.resetAnalyzer();
				}
				for (PrefOption fieldOption : fieldOptions) {
					fieldOption.save();
					
					if (fieldOption == analyzerOption) {
						int newAnalyzer = SettingsConf.Int.LuceneAnalyzer.get();
						if (oldAnalyzer != newAnalyzer) {
							AppUtil.showInfo(Msg.rebuild_indexes.get());
							IndexRegistry.resetAnalyzer();
						}
					}
				}
				indexManagementPanel.saveFolder();
				evtOKClicked.fire(null);
				shell.close();
			}
		});

		Button cancelBt = UtilGui.createPushButton(comp, Msg.cancel.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				shell.close();
			}
		});

		Button[] okCancelBts = UtilGui.maybeSwapButtons(okBt, cancelBt);

		comp.setLayout(new FormLayout());
		FormDataFactory fdf = FormDataFactory.getInstance();
		fdf.margin(0).top().bottom().left().minWidth(UtilGui.BTW).applyTo(helpBt);
		fdf.left(helpBt, 5).applyTo(resetBt);
		fdf.unleft().right().applyTo(okCancelBts[1]);
		fdf.right(okCancelBts[1], -5).applyTo(okCancelBts[0]);
		fdf.left(resetBt, 15).right(okCancelBts[0], -15).applyTo(infoComp);

		return comp;
	}

	private static String getAppNameAndVersion() {
		return SystemConf.Str.ProgramName.get() + " " +
				SystemConf.Str.ProgramVersion.get();
	}

	private static String getMemLimitString() {
		long bytesPerMB = 1024L * 1024L;
		long bytesPerGB = bytesPerMB * 1024L;

		/* Try to get the memory limit from the -Xmx argument first, and use
		 * maxMemory as fallback. We're preferring the former because the latter
		 * will be slightly lower due to subtraction of overhead, which is not
		 * what the user expects. */
		long memoryBytes = Runtime.getRuntime().maxMemory();
		RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
		for (String arg : bean.getInputArguments()) {
			if (arg.startsWith("-Xmx")) {
				String value = arg.substring(4); // Remove "-Xmx" prefix
				long multiplier = 1L;
				char lastChar = value.charAt(value.length() - 1);
				if (lastChar == 'k' || lastChar == 'K') {
					multiplier = 1024L;
					value = value.substring(0, value.length() - 1);
				} else if (lastChar == 'm' || lastChar == 'M') {
					multiplier = 1024L * 1024L;
					value = value.substring(0, value.length() - 1);
				} else if (lastChar == 'g' || lastChar == 'G') {
					multiplier = 1024L * 1024L * 1024L;
					value = value.substring(0, value.length() - 1);
				}
				try {
					memoryBytes = Long.parseLong(value) * multiplier;
					System.out.println(value);
					break;
				} catch (NumberFormatException e) {
					// Fall back to maxMemory
				}
			}
		}

		if (memoryBytes < bytesPerGB) {
			double memoryMB = ((double) memoryBytes) / bytesPerMB;
			return Math.round(memoryMB) + " MB";
		} else {
			double memoryGB = ((double) memoryBytes) / bytesPerGB;
			return Math.round(memoryGB) + " GB";
		}
	}

	public void open() {
		open(false);
	}
	
	/**
	 * Opens the dialog. If {@code showIndexesTab} is true, the 'Indexes' tab is
	 * selected initially, otherwise the 'General' tab.
	 */
	public void open(boolean showIndexesTab) {
		tabFolder.setSelection(showIndexesTab ? 1 : 0);
		okBt.setFocus();
		growToPreferredSize();
		shell.open();
		while (!shell.isDisposed()) {
			if (!shell.getDisplay().readAndDispatch())
				shell.getDisplay().sleep();
		}
	}

	/**
	 * Enlarges the dialog if the stored size is too small to show all options
	 * without scrolling, e.g. after an update that added options.
	 */
	private void growToPreferredSize() {
		Point preferred = shell.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		Rectangle bounds = shell.getBounds();
		Rectangle client = shell.getMonitor().getClientArea();
		int width = Math.min(Math.max(bounds.width, preferred.x), client.width);
		int height = Math.min(Math.max(bounds.height, preferred.y), client.height);
		if (width == bounds.width && height == bounds.height)
			return;
		int x = Math.max(client.x, Math.min(bounds.x, client.x + client.width - width));
		int y = Math.max(client.y, Math.min(bounds.y, client.y + client.height - height));
		shell.setBounds(x, y, width, height);
	}

	@NotNull
	static StyledLabel createLabeledStyledLabel(@NotNull Composite parent,
												@NotNull String labelText) {
		Label label = new Label(parent, SWT.NONE);
		label.setText(labelText);
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		StyledLabel text = new StyledLabel(parent, SWT.BORDER);
		text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		return text;
	}

	static abstract class PrefOption {
		protected final String labelText;

		public PrefOption(@NotNull String labelText) {
			this.labelText = labelText;
		}
		// Subclassers must set grid datas on the created controls, assuming
		// a two-column grid layout
		protected abstract void createControls(@NotNull Composite parent);
		protected abstract void restoreDefault();
		protected abstract void save();
	}

}
