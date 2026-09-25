/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.List;

import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.collect.AlphanumComparator;

/**
 * Keeps the two folder browsers in step: the left one (on the 'Folders' tab)
 * always shows the parent of the folder shown in the middle one, with that
 * folder selected.
 * <ul>
 * <li>A folder clicked on the left is shown in the middle.
 * <li>Entering a folder on the left shows its first subfolder in the middle,
 * or nothing if it has none; going up on the left shows the folder that was
 * left in the middle.
 * <li>Entering a folder in the middle (or going up there) moves the left
 * one along, so that it shows the parent.
 * </ul>
 * Switched on and off with {@link SettingsConf.Bool#SyncFolderPanels}.
 */
final class FolderSync {

	private final FolderPanel left;
	private final FolderPanel middle;
	private boolean syncing = false;
	@Nullable private File lastLeftFolder;

	private final Event.Listener<List<ResultDocument>> leftSelection = new Event.Listener<List<ResultDocument>>() {
		public void update(List<ResultDocument> selection) {
			if (!isActive() || selection.size() != 1)
				return;
			ResultDocument doc = selection.get(0);
			if (!doc.isFolder() || doc.isParentRow())
				return;
			File folder = doc.getPath().getCanonicalFile();
			if (folder.isDirectory() && !Util.isCanonicallyEqual(folder, middle.getFolder()))
				sync(new Runnable() {
					public void run() {
						middle.setFolder(folder(doc));
					}
				});
		}
	};

	private final Event.Listener<Void> leftFolderChanged = new Event.Listener<Void>() {
		public void update(Void eventData) {
			File previous = lastLeftFolder;
			lastLeftFolder = left.getFolder();
			if (isActive())
				leftChanged(previous, left.getFolder());
		}
	};

	private final Event.Listener<Void> middleFolderChanged = new Event.Listener<Void>() {
		public void update(Void eventData) {
			if (isActive() && !middle.isShowingNothing())
				middleChanged(middle.getFolder());
		}
	};

	private final Event.Listener<Boolean> settingChanged = new Event.Listener<Boolean>() {
		public void update(Boolean enabled) {
			if (enabled && !middle.isShowingNothing())
				middleChanged(middle.getFolder()); // Bring the left one in line
		}
	};

	public FolderSync(@NotNull FolderPanel left, @NotNull FolderPanel middle) {
		this.left = Util.checkNotNull(left);
		this.middle = Util.checkNotNull(middle);
		lastLeftFolder = left.getFolder();
		left.getResultPanel().evtSelection.add(leftSelection);
		left.evtFolderChanged.add(leftFolderChanged);
		middle.evtFolderChanged.add(middleFolderChanged);
		SettingsConf.Bool.SyncFolderPanels.evtChanged.add(settingChanged);
		if (SettingsConf.Bool.SyncFolderPanels.get())
			settingChanged.update(true);
	}

	/** Stops syncing, e.g. when the middle browser is closed. */
	public void dispose() {
		left.getResultPanel().evtSelection.remove(leftSelection);
		left.evtFolderChanged.remove(leftFolderChanged);
		middle.evtFolderChanged.remove(middleFolderChanged);
		SettingsConf.Bool.SyncFolderPanels.evtChanged.remove(settingChanged);
	}

	private boolean isActive() {
		return !syncing && SettingsConf.Bool.SyncFolderPanels.get();
	}

	/** Runs a change of the other browser without syncing back. */
	private void sync(@NotNull Runnable change) {
		syncing = true;
		try {
			change.run();
		}
		finally {
			syncing = false;
		}
		lastLeftFolder = left.getFolder();
	}

	@NotNull
	private static File folder(@NotNull ResultDocument doc) {
		return doc.getPath().getCanonicalFile();
	}

	/**
	 * The left browser went from {@code previous} to {@code current}: after
	 * going up, the middle shows the folder that was left; otherwise the first
	 * subfolder, or nothing.
	 */
	private void leftChanged(@Nullable File previous, @Nullable final File current) {
		File child = null;
		if (previous != null && Util.isCanonicallyEqual(getParent(previous), current))
			child = previous;
		else if (current != null)
			child = getFirstSubfolder(current);
		else if (!left.getRootFolders().isEmpty())
			child = left.getRootFolders().get(0);
		final File shown = child;
		sync(new Runnable() {
			public void run() {
				if (shown == null) {
					middle.showNothing();
				}
				else {
					middle.setFolder(shown);
					left.showFolder(current, shown); // Select it once the list is there
				}
			}
		});
	}

	/** The middle browser now shows {@code current}: the left shows its parent. */
	private void middleChanged(@Nullable final File current) {
		sync(new Runnable() {
			public void run() {
				if (current == null)
					left.setFolder(null);
				else
					left.showFolder(getParent(current), current);
			}
		});
	}

	/** The parent in the browsers: null (the root list) for a root folder. */
	@Nullable
	private File getParent(@NotNull File folder) {
		for (File root : left.getRootFolders())
			if (Util.isCanonicallyEqual(root, folder))
				return null;
		return folder.getParentFile();
	}

	/** The first subfolder by name, hidden ones left out, or null. */
	@Nullable
	static File getFirstSubfolder(@NotNull File folder) {
		File[] subfolders = folder.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return file.isDirectory() && !file.isHidden();
			}
		});
		if (subfolders == null || subfolders.length == 0)
			return null;
		Arrays.sort(subfolders, new java.util.Comparator<File>() {
			public int compare(File f1, File f2) {
				return AlphanumComparator.ignoreCaseInstance.compare(f1.getName(), f2.getName());
			}
		});
		return subfolders[0];
	}

}
