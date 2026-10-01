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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Shell;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.model.pdf.PdfPages;
import net.sourceforge.docfetcher.model.pdf.PdfPages.PageInfo;
import net.sourceforge.docfetcher.model.pdf.PdfPages.Range;
import net.sourceforge.docfetcher.model.search.NameSuggester;
import net.sourceforge.docfetcher.model.search.NameSuggester.Suggestion;
import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.model.search.Searcher;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilFile;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.gui.dialog.MultipleChoiceDialog;

/**
 * Merging and splitting of the PDF files selected in a result table. After a
 * successful merge or split, the originals can be moved to the recycle bin
 * (the user is asked, unless the answer was remembered); the whole operation
 * can be undone with Ctrl+Z.
 */
final class PdfOperations {

	/** What the operations need from the result table they work for. */
	interface Host {
		@NotNull Shell getShell();

		/** Removes the rows of documents whose files are gone. */
		void removeRows(@NotNull List<ResultDocument> docs);

		/** Updates the indexes and views after the given files changed. */
		void filesChanged(@NotNull List<File> files);

		/** The name suggestion for the document, computed if necessary, or null. */
		@Nullable Suggestion getNameSuggestion(@NotNull ResultDocument doc);

		/** The searcher for name suggestions of new files, or null. */
		@Nullable Searcher getSearcher();
	}

	/** Answers the question for the originals is answered with. */
	enum Answer {
		REMOVE, KEEP
	}

	private PdfOperations() {}

	static boolean isPdf(@NotNull ResultDocument doc) {
		return doc.isPlainFileOnDisk()
			&& Util.getExtension(doc.getFilename()).equalsIgnoreCase("pdf");
	}

	/** Returns true if at least two documents are selected, all PDF files. */
	static boolean canMerge(@NotNull List<ResultDocument> docs) {
		if (docs.size() < 2)
			return false;
		for (ResultDocument doc : docs)
			if (!isPdf(doc))
				return false;
		return true;
	}

	/** Returns true if one PDF file is selected that may have several pages. */
	static boolean canSplit(@NotNull List<ResultDocument> docs) {
		return docs.size() == 1 && isPdf(docs.get(0)) && docs.get(0).getPageCount() != 1;
	}

	@NotNull
	private static List<File> filesOf(@NotNull List<ResultDocument> docs) {
		List<File> files = new ArrayList<File>(docs.size());
		for (ResultDocument doc : docs)
			files.add(doc.getPath().getCanonicalFile());
		return files;
	}

	/** Asks for the order and name, then merges the documents. */
	static void merge(@NotNull final Host host, @NotNull final List<ResultDocument> docs) {
		if (!canMerge(docs))
			return;
		final List<File> files = filesOf(docs);
		MergeDialog.SuggestionSource suggestions = new MergeDialog.SuggestionSource() {
			public Suggestion get(File file) {
				int index = files.indexOf(file);
				return index < 0 ? null : host.getNameSuggestion(docs.get(index));
			}
		};
		MergeDialog.Result choice = new MergeDialog(host.getShell(), files, suggestions).open();
		if (choice == null)
			return;
		File target = new File(choice.files.get(0).getParentFile(), choice.fileName);
		merge(host, docs, choice.files, target, null);
	}

	/**
	 * Merges the files into {@code target}; {@code answer} is the answer
	 * for the originals, or null to ask (or use the remembered answer).
	 * Returns true on success.
	 */
	static boolean merge(	@NotNull Host host,
							@NotNull List<ResultDocument> docs,
							@NotNull final List<File> files,
							@NotNull final File target,
							@Nullable Answer answer) {
		/*
		 * The proposed name is the one of the first file. The merged file is
		 * written under a temporary name first; after the question what to do
		 * with the originals it gets the name if that is free then, or else
		 * the name with a number.
		 */
		final File written = target.exists()
			? new File(target.getParentFile(), "~" + target.getName() + ".docfetcher-merge")
			: target;
		final IOException[] error = new IOException[1];
		BusyIndicator.showWhile(host.getShell().getDisplay(), new Runnable() {
			public void run() {
				try {
					PdfPages.merge(files, written);
				}
				catch (IOException e) {
					error[0] = e;
				}
			}
		});
		if (error[0] != null) {
			written.delete();
			AppUtil.showError(Msg.merge_failed.format(error[0].getMessage()), true, true);
			return false;
		}
		String question = Msg.merge_done_question.format(files.size());
		List<File> backups = handleOriginals(host, docs, question, answer);
		File merged = target;
		if (written != target) {
			merged = UtilFile.getFreeFile(target);
			try {
				java.nio.file.Files.move(written.toPath(), merged.toPath());
			}
			catch (IOException e) {
				merged = written; // Keep the result under the temporary name
				AppUtil.showError(Msg.file_operation_failed.format(e.toString()), true, true);
			}
		}
		FileOperationHistory.getInstance().add(FileOperationHistory.Kind.MERGE,
			files, Collections.singletonList(merged), backups);
		List<File> changed = new ArrayList<File>(files);
		changed.add(merged);
		host.filesChanged(changed);
		return true;
	}

	/** Asks how to split the document, then splits it. */
	static void split(@NotNull Host host, @NotNull ResultDocument doc) {
		final File file = doc.getPath().getCanonicalFile();
		// Reads the text of the pages and looks for blank pages and separator sheets
		final List<PageInfo> pages = new ArrayList<PageInfo>();
		final IOException[] error = new IOException[1];
		BusyIndicator.showWhile(host.getShell().getDisplay(), new Runnable() {
			public void run() {
				try {
					pages.addAll(PdfPages.analyzePages(file));
				}
				catch (IOException e) {
					error[0] = e;
				}
			}
		});
		if (error[0] != null) {
			AppUtil.showError(Msg.split_failed.format(error[0].getMessage()), true, true);
			return;
		}
		if (pages.size() < 2) {
			AppUtil.showInfo(Msg.split_one_page.format(file.getName()));
			return;
		}
		List<Range> ranges = new SplitDialog(host.getShell(), file, pages).open();
		if (ranges == null)
			return;
		List<File> parts = PdfPages.getPartFiles(file, ranges.size());
		if (SettingsConf.Bool.SplitAutoRename.get())
			parts = getSuggestedPartFiles(host, file, pages, ranges, parts);
		split(host, doc, ranges, parts, null);
	}

	/**
	 * Names the parts of a split by their text, like 'Rename To' does:
	 * {@code <date>_<word>-<word>-<word>.pdf}, with a number if the name is
	 * taken. A part without a suggestion keeps its numbered name.
	 */
	@NotNull
	static List<File> getSuggestedPartFiles(@NotNull Host host,
											@NotNull final File file,
											@NotNull final List<PageInfo> pages,
											@NotNull final List<Range> ranges,
											@NotNull final List<File> numbered) {
		final Searcher searcher = host.getSearcher();
		if (searcher == null)
			return numbered;
		final List<File> result = new ArrayList<File>(numbered);
		BusyIndicator.showWhile(host.getShell().getDisplay(), new Runnable() {
			public void run() {
				Date fallbackDate = new Date(file.lastModified());
				Set<String> used = new HashSet<String>();
				for (int i = 0; i < ranges.size(); i++) {
					StringBuilder text = new StringBuilder();
					for (int page : ranges.get(i).getPages())
						text.append(pages.get(page).text).append('\n');
					try {
						Suggestion suggestion = NameSuggester.suggest(text.toString(), fallbackDate, searcher);
						if (suggestion.getWords().isEmpty())
							continue;
						String base = suggestion.buildName(NameSuggester.DEFAULT_WORDS, "");
						File part = UtilFile.getFreeFile(new File(file.getParentFile(), base + ".pdf"));
						// Parts with the same suggestion get numbers among themselves, too
						for (int n = 2; used.contains(part.getName().toLowerCase()); n++)
							part = UtilFile.getFreeFile(new File(file.getParentFile(), base + "-" + n + ".pdf"));
						used.add(part.getName().toLowerCase());
						result.set(i, part);
					}
					catch (Exception e) {
						// No suggestion: the part keeps its numbered name
					}
				}
			}
		});
		return result;
	}

	/**
	 * Splits the document into the given page ranges, saved as numbered
	 * files next to it; {@code answer} as for {@link #merge}. Returns the
	 * created files, or null on failure.
	 */
	@Nullable
	static List<File> split(@NotNull Host host,
							@NotNull ResultDocument doc,
							@NotNull final List<Range> ranges,
							@Nullable Answer answer) {
		File file = doc.getPath().getCanonicalFile();
		return split(host, doc, ranges, PdfPages.getPartFiles(file, ranges.size()), answer);
	}

	/** Like {@link #split(Host, ResultDocument, List, Answer)}, with the given part files. */
	@Nullable
	static List<File> split(@NotNull Host host,
							@NotNull ResultDocument doc,
							@NotNull final List<Range> ranges,
							@NotNull final List<File> parts,
							@Nullable Answer answer) {
		final File file = doc.getPath().getCanonicalFile();
		final IOException[] error = new IOException[1];
		BusyIndicator.showWhile(host.getShell().getDisplay(), new Runnable() {
			public void run() {
				try {
					PdfPages.split(file, ranges, parts);
				}
				catch (IOException e) {
					error[0] = e;
				}
			}
		});
		if (error[0] != null) {
			AppUtil.showError(Msg.split_failed.format(error[0].getMessage()), true, true);
			return null;
		}
		String question = Msg.split_done_question.format(parts.size(),
			parts.get(0).getName(), parts.get(parts.size() - 1).getName());
		List<File> backups = handleOriginals(host, Collections.singletonList(doc), question, answer);
		FileOperationHistory.getInstance().add(FileOperationHistory.Kind.SPLIT,
			Collections.singletonList(file), parts, backups);
		List<File> changed = new ArrayList<File>(parts);
		changed.add(file);
		host.filesChanged(changed);
		return parts;
	}

	/**
	 * Moves the originals to the recycle bin if the user wants that, after
	 * keeping backup copies for the undo. Returns the backups, or an empty
	 * list if the originals were kept.
	 */
	@NotNull
	private static List<File> handleOriginals(	@NotNull Host host,
												@NotNull List<ResultDocument> originals,
												@NotNull String question,
												@Nullable Answer answer) {
		if (answer == null)
			answer = askForOriginals(host.getShell(), question);
		if (answer != Answer.REMOVE)
			return Collections.emptyList();

		List<File> files = filesOf(originals);
		List<File> backups;
		try {
			backups = FileOperationHistory.getInstance().backup(files);
		}
		catch (IOException e) {
			// Without a backup the undo couldn't restore them, so keep them
			AppUtil.showError(Msg.file_operation_failed.format(e.toString()), true, true);
			return Collections.emptyList();
		}
		List<ResultDocument> removed = new ArrayList<ResultDocument>();
		for (int i = 0; i < files.size(); i++) {
			if (!UtilFile.delete(files.get(i))) {
				AppUtil.showError(Msg.file_operation_failed.format(files.get(i).getPath()), true, true);
				break;
			}
			removed.add(originals.get(i));
		}
		host.removeRows(removed);
		return backups;
	}

	/**
	 * Returns what to do with the originals: the remembered answer, or the
	 * user's answer to the question, which is remembered if the user wants.
	 */
	@NotNull
	private static Answer askForOriginals(@NotNull Shell shell, @NotNull String question) {
		int setting = SettingsConf.Int.RemoveOriginals.get();
		if (setting == SettingsConf.ORIGINALS_REMOVE)
			return Answer.REMOVE;
		if (setting == SettingsConf.ORIGINALS_KEEP)
			return Answer.KEEP;
		MultipleChoiceDialog<Answer> dialog = new MultipleChoiceDialog<Answer>(shell);
		dialog.setTitle(Msg.confirm_operation.get());
		dialog.setImage(SWT.ICON_QUESTION);
		dialog.setText(question);
		dialog.setCheckBox(Msg.remember_choice.get());
		dialog.addButton(Msg.originals_remove_bt.get(), Answer.REMOVE);
		dialog.addButton(Msg.originals_keep_bt.get(), Answer.KEEP);
		Answer answer = dialog.open();
		if (answer == null)
			return Answer.KEEP; // Dialog closed: nothing is removed
		if (dialog.isChecked())
			SettingsConf.Int.RemoveOriginals.set(answer == Answer.REMOVE
				? SettingsConf.ORIGINALS_REMOVE
				: SettingsConf.ORIGINALS_KEEP);
		return answer;
	}

}
