/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.model.pdf.DocumentStarts;
import net.sourceforge.docfetcher.model.pdf.DocumentStarts.Reason;
import net.sourceforge.docfetcher.model.pdf.DocumentStarts.Suggestion;
import net.sourceforge.docfetcher.model.pdf.PdfPages;
import net.sourceforge.docfetcher.model.pdf.PdfPages.PageInfo;
import net.sourceforge.docfetcher.model.pdf.PdfPages.Range;
import net.sourceforge.docfetcher.model.pdf.PdfPages.Role;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * Asks where a PDF file is split. All pages are listed with the beginning of
 * their text; a checkbox marks the pages where a new document starts, and
 * separator pages (blank pages, separator sheets) are left out. The buttons at
 * the top fill in a suggestion - at the separator pages, from the text of the
 * pages, every page, or none - which the user can correct page by page.
 */
final class SplitDialog {

	/** The suggestions the buttons at the top fill in. */
	enum Choice {
		SEPARATORS, TEXT, EACH_PAGE, NONE
	}

	private static final int PREVIEW_CHARS = 70;

	private final Shell shell;
	private final File file;
	private final List<PageInfo> pages;
	private final Role[] roles;
	private final String[] reasons;
	private final Table table;
	private final Label resultLabel;
	private final Button okBt;
	private final Map<Choice, Button> choiceButtons = new EnumMap<Choice, Button>(Choice.class);
	@Nullable private List<Range> answer;

	public SplitDialog(@NotNull Shell parent, @NotNull File file, @NotNull List<PageInfo> pages) {
		Util.checkNotNull(parent, file, pages);
		this.file = file;
		this.pages = pages;
		roles = new Role[pages.size()];
		reasons = new String[pages.size()];

		shell = new Shell(parent, SWT.PRIMARY_MODAL | SWT.DIALOG_TRIM | SWT.RESIZE);
		shell.setText(Msg.split_title.get());
		shell.setImage(Img.TB_SPLIT.get());
		shell.setLayout(UtilGui.createGridLayout(1, false, 10, 6));

		Label intro = new Label(shell, SWT.WRAP);
		intro.setText(Msg.split_intro.format(file.getName(), pages.size()));
		intro.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		// Separator pages are the most reliable hint: offered first, and chosen if there are any
		boolean hasSeparators = false;
		for (PageInfo page : pages)
			hasSeparators |= page.isSeparator();
		Composite choiceComp = new Composite(shell, SWT.NONE);
		choiceComp.setLayout(UtilGui.createGridLayout(6, false, 0, 5));
		choiceComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		new Label(choiceComp, SWT.NONE).setText(Msg.split_suggestion.get());
		createChoiceButton(choiceComp, Msg.split_suggest_separators.get(), Choice.SEPARATORS)
			.setEnabled(hasSeparators);
		createChoiceButton(choiceComp, Msg.split_suggest_text.get(), Choice.TEXT);
		createChoiceButton(choiceComp, Msg.split_suggest_each_page.get(), Choice.EACH_PAGE);
		createChoiceButton(choiceComp, Msg.split_suggest_none.get(), Choice.NONE);
		Label separatorNote = new Label(choiceComp, SWT.NONE);
		separatorNote.setText(hasSeparators ? "" : Msg.split_no_separators.get());

		Label hint = new Label(shell, SWT.WRAP);
		hint.setText(Msg.split_hint.get());
		GridData hintData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		hintData.widthHint = 820;
		hint.setLayoutData(hintData);

		table = new Table(shell, SWT.CHECK | SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL);
		table.setHeaderVisible(true);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
		tableData.heightHint = 260;
		table.setLayoutData(tableData);
		addColumn(Msg.split_col_page.get(), 130);
		addColumn(Msg.split_col_text.get(), 330);
		addColumn(Msg.split_col_reason.get(), 340);
		for (int i = 0; i < pages.size(); i++)
			new TableItem(table, SWT.NONE);
		table.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (e.detail != SWT.CHECK)
					return;
				int index = table.indexOf((TableItem) e.item);
				// A ticked separator is kept as the start of a document
				setRole(index, ((TableItem) e.item).getChecked() || roles[index] == Role.SEPARATOR
					? Role.START : Role.CONTINUE);
			}
		});

		Composite rowComp = new Composite(shell, SWT.NONE);
		rowComp.setLayout(UtilGui.createGridLayout(2, false, 0, 10));
		rowComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button leaveOutBt = new Button(rowComp, SWT.PUSH);
		leaveOutBt.setText(Msg.split_leave_out.get());
		leaveOutBt.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				for (int index : table.getSelectionIndices())
					setRole(index, roles[index] == Role.SEPARATOR ? Role.CONTINUE : Role.SEPARATOR);
			}
		});
		int withoutText = 0;
		for (PageInfo page : pages)
			if (page.text.isEmpty() && !page.blank)
				withoutText++;
		Label noTextLabel = new Label(rowComp, SWT.WRAP);
		noTextLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		if (withoutText > 0)
			noTextLabel.setText(Msg.split_pages_without_text.format(withoutText));

		final Button autoRenameBt = new Button(shell, SWT.CHECK);
		autoRenameBt.setText(Msg.split_auto_rename.get());
		autoRenameBt.setSelection(SettingsConf.Bool.SplitAutoRename.get());
		autoRenameBt.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				SettingsConf.Bool.SplitAutoRename.set(autoRenameBt.getSelection());
				update();
			}
		});

		Label separator1 = new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL);
		separator1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		resultLabel = new Label(shell, SWT.WRAP);
		GridData resultData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		resultData.heightHint = 40;
		resultLabel.setLayoutData(resultData);

		Composite bottomComp = new Composite(shell, SWT.NONE);
		bottomComp.setLayout(UtilGui.createGridLayout(2, false, 0, 5));
		bottomComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Link printLink = new Link(bottomComp, SWT.NONE);
		printLink.setText("<a>" + Msg.print_separator_sheet.get() + "</a>");
		printLink.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		printLink.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				DocumentPrinter.printSeparatorSheet(shell);
			}
		});
		Composite buttonComp = new Composite(bottomComp, SWT.NONE);
		buttonComp.setLayout(UtilGui.createGridLayout(2, true, 0, 5));
		buttonComp.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		okBt = UtilGui.createPushButton(buttonComp, Msg.ok.get(), new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				List<Range> ranges = getRanges();
				if (ranges.size() < 2)
					return;
				answer = ranges;
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

		apply(hasSeparators ? Choice.SEPARATORS : Choice.TEXT);

		shell.pack();
		Point size = shell.getSize();
		UtilGui.setCenteredBounds(shell, size.x, size.y);
	}

	private void addColumn(@NotNull String label, int width) {
		TableColumn column = new TableColumn(table, SWT.LEFT);
		column.setText(label);
		column.setWidth(width);
	}

	/** A button for a suggestion; it stays pressed while its suggestion is shown. */
	@NotNull
	private Button createChoiceButton(	@NotNull Composite parent,
										@NotNull String label,
										@NotNull final Choice choice) {
		Button button = new Button(parent, SWT.TOGGLE);
		button.setText(label);
		button.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				apply(choice);
			}
		});
		choiceButtons.put(choice, button);
		return button;
	}

	/** Fills in the given suggestion. */
	void apply(@NotNull Choice choice) {
		for (Map.Entry<Choice, Button> entry : choiceButtons.entrySet())
			entry.getValue().setSelection(entry.getKey() == choice);
		List<Suggestion> suggestions;
		if (choice == Choice.TEXT)
			suggestions = DocumentStarts.suggest(pages);
		else
			suggestions = DocumentStarts.atSeparators(pages);
		boolean first = true;
		for (int i = 0; i < roles.length; i++) {
			Suggestion suggestion = suggestions.get(i);
			Role role = suggestion.role;
			if (choice == Choice.EACH_PAGE && role != Role.SEPARATOR)
				role = Role.START;
			else if (choice == Choice.NONE)
				role = first ? Role.START : Role.CONTINUE;
			roles[i] = role;
			reasons[i] = choice == Choice.TEXT || choice == Choice.SEPARATORS || role == Role.SEPARATOR
				? getReasonText(suggestion)
				: "";
			first = false;
		}
		update();
	}

	/** Changes the role of one page, e.g. after a click on its checkbox. */
	void setRole(int index, @NotNull Role role) {
		roles[index] = role;
		reasons[index] = Msg.split_reason_manual.get();
		update();
	}

	@NotNull
	private static String getReasonText(@NotNull Suggestion suggestion) {
		Reason reason = suggestion.reason;
		String detail = suggestion.detail == null ? "" : suggestion.detail;
		switch (reason) {
		case FIRST_PAGE: return Msg.split_reason_first_page.get();
		case SEPARATOR: return Msg.split_reason_separator.get();
		case AFTER_SEPARATOR: return Msg.split_reason_after_separator.get();
		case PAGE_NUMBER_ONE: return Msg.split_reason_page_one.format(detail);
		case PAGE_NUMBER_CONTINUES: return Msg.split_reason_page_continues.format(detail);
		case AFTER_LAST_PAGE: return Msg.split_reason_after_last_page.format(detail);
		case SALUTATION: return Msg.split_reason_salutation.get();
		case SHARED_NUMBER: return Msg.split_reason_shared_number.format(detail);
		case LITTLE_TEXT: return Msg.split_reason_little_text.get();
		case DIFFERENT_TEXT: return Msg.split_reason_different_text.format(detail);
		case SIMILAR_TEXT: return Msg.split_reason_similar_text.format(detail);
		default: return "";
		}
	}

	/** Shows the roles in the table and the resulting documents below it. */
	private void update() {
		// The first page that is kept always starts a document
		for (int i = 0; i < roles.length; i++) {
			if (roles[i] == Role.SEPARATOR)
				continue;
			roles[i] = Role.START;
			break;
		}
		Color gray = shell.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY);
		for (int i = 0; i < roles.length; i++) {
			TableItem item = table.getItem(i);
			boolean separator = roles[i] == Role.SEPARATOR;
			item.setChecked(roles[i] == Role.START || separator);
			item.setGrayed(separator); // A filled box: this page is left out
			item.setForeground(separator ? gray : null);
			item.setText(0, separator ? Msg.split_page_left_out.format(i + 1) : String.valueOf(i + 1));
			item.setText(1, preview(pages.get(i)));
			item.setText(2, reasons[i]);
		}
		List<Range> ranges = getRanges();
		int leftOut = 0;
		for (Role role : roles)
			if (role == Role.SEPARATOR)
				leftOut++;
		if (ranges.size() < 2) {
			resultLabel.setText(Msg.split_nothing.get());
		}
		else {
			StringBuilder pagesText = new StringBuilder();
			for (Range range : ranges) {
				if (pagesText.length() > 0)
					pagesText.append(", ");
				pagesText.append(range);
			}
			String firstName = SettingsConf.Bool.SplitAutoRename.get()
				? Msg.split_names_by_content.get()
				: PdfPages.getPartFiles(file, ranges.size()).get(0).getName();
			String text = Msg.split_result.format(ranges.size(), pagesText, firstName);
			if (leftOut > 0)
				text += " " + Msg.split_left_out.format(leftOut);
			resultLabel.setText(text);
		}
		okBt.setEnabled(ranges.size() >= 2);
	}

	/** The beginning of the text of the page, on one line. */
	@NotNull
	private static String preview(@NotNull PageInfo page) {
		if (page.blank)
			return Msg.split_blank_page.get();
		String text = page.text.replaceAll("\\s+", " ").trim();
		if (text.isEmpty())
			return Msg.split_no_text.get();
		return text.length() > PREVIEW_CHARS ? text.substring(0, PREVIEW_CHARS) + "..." : text;
	}

	/** The documents for the current roles of the pages. */
	@NotNull
	List<Range> getRanges() {
		return PdfPages.fromRoles(roles);
	}

	/** The suggestion whose button is pressed, or null; for tests. */
	@Nullable
	Choice getChoice() {
		for (Map.Entry<Choice, Button> entry : choiceButtons.entrySet())
			if (entry.getValue().getSelection())
				return entry.getKey();
		return null;
	}

	/** Whether a suggestion can be chosen; for tests. */
	boolean isChoiceEnabled(@NotNull Choice choice) {
		return choiceButtons.get(choice).getEnabled();
	}

	/** The current roles; for tests. */
	@NotNull
	List<Role> getRoles() {
		List<Role> list = new ArrayList<Role>();
		Collections.addAll(list, roles);
		return list;
	}

	/** The text below the page list; for tests. */
	@NotNull
	String getResultText() {
		return resultLabel.getText();
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
