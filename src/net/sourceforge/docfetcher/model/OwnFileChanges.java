/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * Remembers the files DocFetcher changes itself while indexing - scans that
 * get their recognized text - so that the folder watcher doesn't answer
 * these changes with another index update. A change counts as our own only
 * while the file is being written, or while it still has exactly the size
 * and modification time DocFetcher left it with; any other change of the
 * file, and any change of other files, triggers the update as usual. The
 * temporary files DocFetcher writes next to them (suffix
 * {@link #TEMP_SUFFIX}) are always ignored.
 */
public final class OwnFileChanges {

	/** Suffix of the temporary files written next to the files that are replaced. */
	public static final String TEMP_SUFFIX = ".docfetcher-tmp";

	/** How long a change is remembered; the watcher reports it within seconds. */
	static final long EXPIRY_MILLIS = 60 * 1000L;

	private static final class Change {
		final long expires;
		boolean inProgress = true;
		boolean removed = false;
		long length;
		long lastModified;

		Change(long now) {
			expires = now + EXPIRY_MILLIS;
		}
	}

	private static final Map<String, Change> changes = new HashMap<String, Change>();

	private OwnFileChanges() {}

	/** To be called before DocFetcher writes, replaces or creates the file. */
	public static synchronized void begin(@NotNull File file) {
		cleanUp();
		changes.put(key(file), new Change(System.currentTimeMillis()));
	}

	/** To be called after the file was written and its modification time set. */
	public static synchronized void end(@NotNull File file) {
		Change change = changes.get(key(file));
		if (change == null)
			change = new Change(System.currentTimeMillis());
		change.inProgress = false;
		change.length = file.length();
		change.lastModified = file.lastModified();
		changes.put(key(file), change);
	}

	/** To be called when DocFetcher deleted or renamed the file (e.g. a converted TIFF file). */
	public static synchronized void removed(@NotNull File file) {
		Change change = new Change(System.currentTimeMillis());
		change.inProgress = false;
		change.removed = true;
		changes.put(key(file), change);
	}

	/** Returns true if the last change of the file was made by DocFetcher itself. */
	public static synchronized boolean isOwnChange(@NotNull File file) {
		if (file.getName().endsWith(TEMP_SUFFIX))
			return true;
		cleanUp();
		Change change = changes.get(key(file));
		if (change == null)
			return false;
		if (change.inProgress)
			return true;
		if (change.removed)
			return !file.exists(); // Unless someone created it again
		return file.exists() && file.length() == change.length
			&& file.lastModified() == change.lastModified;
	}

	private static void cleanUp() {
		long now = System.currentTimeMillis();
		for (Iterator<Change> it = changes.values().iterator(); it.hasNext();)
			if (it.next().expires < now)
				it.remove();
	}

	@NotNull
	private static String key(@NotNull File file) {
		return file.getAbsoluteFile().toPath().normalize().toString().toLowerCase(java.util.Locale.ROOT);
	}

}
