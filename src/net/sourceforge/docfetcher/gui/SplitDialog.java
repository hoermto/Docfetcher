/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.model.pdf.PdfPages;
import net.sourceforge.docfetcher.model.pdf.PdfPages.Range;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Asks how a PDF file is split: into single pages, at given page numbers or
 * automatically at blank separator pages. Below the choices, the dialog
 * shows which documents will be created.
 */
final class SplitDialog {

	private final Shell shell;
	private final File file;
	private final int pageCount;
	private final Button eachPageBt;
	private final Button atPagesBt;
	private final Button blankPagesBt;
	private final Text pagesField;
	private final Label resultLabel;
	private final Button okBt;
	@Nullable private boolean[] blankPages; // Computed on demand
	@Nullable private String blankPagesError;
	@Nullable private List<Range> ranges;
	@Nullable private List<Range> answer;

	public SplitDialog(@NotNull Shell parent, @NotNull File file, int pageCount) {
		Util.checkNotNull(parent, file);
		this.file = file;
		this.pageCount = pageCount;

		shell = new Shell(parent, SWT.PRIMARY_MODAL | SWT.DIALOG_TRIM | SWT.RESIZE);
		shell.setText(Msg.split_title.get());
		shell.setImage(Img.TB_SPLIT.get());
		shell.setLayout(UtilGui.createGridLayout(1, false, 10, 6));

		Label intro = new Label(shell, SWT.WRAP);
		intro.setText(Msg.split_intro.format(file.getName(), pageCount));
		intro.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		SelectionAdapter modeListener = new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (((Button) e.widget).getSelection())
					update();
			}
		};
		eachPageBt = new Button(shell, SWT.RADIO);
		eachPageBt.setText(Msg.split_each_page.get());
		eachPageBt.addSelectionListener(modeListener);

		atPagesBt = new Button(shell, SWT.RADIO);
		atPagesBt.setText(Msg.split_at_pages.get());
		atPagesBt.addSelectionListener(modeListener);
		Composite pagesComp = new Composite(shell, SWT.NONE);
		pagesComp.setLayout(UtilGui.createGridLayout(2, false, 0, 5));
		GridData pagesData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		pagesData.horizontalIndent = 20;
		pagesComp.setLayoutData(pagesData);
		pagesField = new Text(pagesComp, SWT.BORDER | SWT.SINGLE);
		GridData fieldData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		fieldData.widthHint = 150;
		pagesField.setLayoutData(fieldData);
		new Label(pagesComp, SWT.NONE).setText(Msg.split_at_pages_hint.get());
		pagesField.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				if (!atPagesBt.getSelection()) {
					setMode(SettingsConf.SPLIT_AT_PAGES);
					return;
				}
				update();
			}
		});

		blankPagesBt = new Button(shell, SWT.RADIO);
		blankPagesBt.setText(Msg.split_at_blank_pages.get());
		blankPagesBt.addSelectionListener(modeListener);
		Label explanation = new Label(shell, SWT.WRAP);
		explanation.setText(Msg.split_at_blank_pages_info.get());
		GridData explanationData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		explanationData.horizontalIndent = 20;
		explanationData.widthHint = 460;
		explanation.setLayoutData(explanationData);

		Label separator1 = new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL);
		separator1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		resultLabel = new Label(shell, SWT.WRAP);
		GridData resultData = new GridData(SWT.FILL, SWT.FILL, true, true);
		resultData.widthHint = 480;
		resultData.heightHint = 60;
		resultLabel.setLayoutData(resultData);

		Label separator2 = new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL);
		separator2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Composite buttonComp = new Composite(shell, SWT.NONE);
		buttonComp.setLayout(UtilGui.createGridLayout(2, true, 0, 5));
		buttonComp.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		okBt = UtilGui.createPushButton(buttonComp, Msg.ok.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (ranges == null || ranges.size() < 2)
					return;
				answer = ranges;
				SettingsConf.Int.SplitMode.set(getMode());
				shell.close();
			}
		});
		Button cancelBt = UtilGui.createPushButton(buttonComp, Msg.cancel.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				shell.close();
			}
		});
		for (Button bt : UtilGui.maybeSwapButtons(okBt, cancelBt)) {
			GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
			data.minimumWidth = UtilGui.BTW;
			bt.setLayoutData(data);
		}
		shell.setDefaultButton(okBt);

		shell.pack();
		Point size = shell.getSize();
		UtilGui.setCenteredBounds(shell, size.x, size.y);
		setMode(SettingsConf.Int.SplitMode.get());
	}

	private int getMode() {
		if (eachPageBt.getSelection())
			return SettingsConf.SPLIT_EACH_PAGE;
		if (atPagesBt.getSelection())
			return SettingsConf.SPLIT_AT_PAGES;
		return SettingsConf.SPLIT_AT_BLANK_PAGES;
	}

	/** Selects the given mode (SPLIT_* constant) and updates the result. */
	void setMode(int mode) {
		eachPageBt.setSelection(mode == SettingsConf.SPLIT_EACH_PAGE);
		atPagesBt.setSelection(mode == SettingsConf.SPLIT_AT_PAGES);
		blankPagesBt.setSelection(mode != SettingsConf.SPLIT_EACH_PAGE
			&& mode != SettingsConf.SPLIT_AT_PAGES);
		update();
	}

	/** Enters page numbers for 'split at pages'; for tests. */
	void setPages(@NotNull String pages) {
		pagesField.setText(pages);
	}

	/** Computes the documents for the chosen mode and shows them. */
	private void update() {
		String error = null;
		ranges = null;
		if (eachPageBt.getSelection()) {
			ranges = PdfPages.eachPage(pageCount);
		}
		else if (atPagesBt.getSelection()) {
			List<Integer> pages = PdfPages.parsePageNumbers(pagesField.getText());
			if (pages == null)
				error = Msg.split_invalid_pages.get();
			else
				ranges = PdfPages.atPages(pageCount, pages);
		}
		else {
			findBlankPages();
			if (blankPagesError != null)
				error = blankPagesError;
			else
				ranges = PdfPages.atBlankPages(blankPages);
		}

		String text;
		if (error != null)
			text = error;
		else if (ranges.size() < 2)
			text = Msg.split_nothing.get();
		else
			text = Msg.split_result.format(ranges.size(), join(ranges),
				PdfPages.getPartFiles(file, ranges.size()).get(0).getName());
		resultLabel.setText(text);
		okBt.setEnabled(error == null && ranges.size() >= 2);
	}

	private void findBlankPages() {
		if (blankPages != null || blankPagesError != null)
			return;
		BusyIndicator.showWhile(shell.getDisplay(), new Runnable() {
			public void run() {
				try {
					blankPages = PdfPages.findBlankPages(file);
				}
				catch (IOException e) {
					blankPagesError = Msg.split_failed.format(e.getMessage());
				}
			}
		});
	}

	@NotNull
	private static String join(@NotNull List<Range> ranges) {
		StringBuilder sb = new StringBuilder();
		for (Range range : ranges) {
			if (sb.length() > 0)
				sb.append(", ");
			sb.append(range);
		}
		return sb.toString();
	}

	/** The documents for the current choice, or null; for tests. */
	@Nullable
	List<Range> getRanges() {
		return ranges == null ? null : Collections.unmodifiableList(ranges);
	}

	boolean isOkEnabled() {
		return okBt.getEnabled();
	}

	/** Returns the documents to create, or null if cancelled. */
	@Nullable
	public List<Range> open() {
		shell.open();
		while (!shell.isDisposed()) {
			if (!shell.getDisplay().readAndDispatch())
				shell.getDisplay().sleep();
		}
		return answer;
	}

	/** Closes the dialog; for tests. */
	void close() {
		shell.close();
	}

}
