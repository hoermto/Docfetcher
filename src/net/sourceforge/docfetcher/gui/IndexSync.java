/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.model.LuceneIndex;
import net.sourceforge.docfetcher.util.UtilFile;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * How each index is kept up to date: by folder watching, or - for folders
 * on a network drive, whose changes the operating system doesn't report
 * reliably, and for indexes without folder watching - by the daily sync at a
 * fixed time (preferences, General tab), which is caught up at the next
 * start if DocFetcher wasn't running then. The sync can also cover all
 * indexes.
 */
public final class IndexSync {

	/** Which indexes the daily sync updates (SettingsConf.Int.DailySyncScope). */
	public static final int SCOPE_NEEDED = 0;
	public static final int SCOPE_ALL = 1;

	/** The time of the sync if the setting can't be read: 03:00. */
	static final int DEFAULT_MINUTE = 3 * 60;

	private static final Pattern TIME = Pattern.compile("\\s*(\\d{1,2})[:.](\\d{2})\\s*");

	private IndexSync() {}

	/**
	 * Returns true if folder watching can't be relied on for the index: its
	 * folders aren't watched, or they are on a network drive.
	 */
	public static boolean needsSync(@NotNull LuceneIndex index) {
		return !index.isWatchFolders() || isNetwork(index);
	}

	private static boolean isNetwork(@NotNull LuceneIndex index) {
		return !index.isEmailIndex() && UtilFile.isNetworkDrive(index.getCanonicalRootFile());
	}

	/** Returns true if the daily sync updates the index. */
	public static boolean isSynced(@NotNull LuceneIndex index) {
		return SettingsConf.Bool.DailyIndexUpdate.get()
			&& (SettingsConf.Int.DailySyncScope.get() == SCOPE_ALL || needsSync(index));
	}

	/** How the index is kept up to date, for the list of indexes. */
	@NotNull
	public static String describe(@NotNull LuceneIndex index) {
		boolean daily = SettingsConf.Bool.DailyIndexUpdate.get();
		String time = format(getMinute());
		if (!needsSync(index)) {
			return daily && SettingsConf.Int.DailySyncScope.get() == SCOPE_ALL
				? Msg.index_sync_watched_daily.format(time)
				: Msg.index_sync_watched.get();
		}
		String reason = isNetwork(index) ? Msg.index_sync_network.get() : Msg.index_sync_unwatched.get();
		return daily ? Msg.index_sync_daily.format(reason, time) : Msg.index_sync_manual.format(reason);
	}

	/** The time of the daily sync in minutes after midnight. */
	public static int getMinute() {
		Integer minute = parseTime(SettingsConf.Str.DailySyncTime.get());
		return minute == null ? DEFAULT_MINUTE : minute;
	}

	/** The time 'HH:MM' (also 'H.MM') in minutes after midnight, or null if it isn't one. */
	@Nullable
	public static Integer parseTime(@NotNull String text) {
		Matcher matcher = TIME.matcher(text);
		if (!matcher.matches())
			return null;
		int hours = Integer.parseInt(matcher.group(1));
		int minutes = Integer.parseInt(matcher.group(2));
		if (hours > 23 || minutes > 59)
			return null;
		return hours * 60 + minutes;
	}

	@NotNull
	public static String format(int minute) {
		return String.format("%02d:%02d", minute / 60, minute % 60);
	}

	/** The last moment at the given time of day that isn't after {@code now} (local time). */
	static long lastScheduled(long now, int minute) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(now);
		calendar.set(Calendar.HOUR_OF_DAY, minute / 60);
		calendar.set(Calendar.MINUTE, minute % 60);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		if (calendar.getTimeInMillis() > now)
			calendar.add(Calendar.DAY_OF_MONTH, -1);
		return calendar.getTimeInMillis();
	}

	/**
	 * Returns true if a sync is due: the last one ran before the last time
	 * the sync was scheduled - at that time, or later if DocFetcher wasn't
	 * running then.
	 */
	static boolean isDue(long now, long lastSync, int minute) {
		return lastSync < lastScheduled(now, minute);
	}

}
