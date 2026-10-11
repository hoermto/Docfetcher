/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Calendar;

import org.junit.Test;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.model.LuceneIndex;
import net.sourceforge.docfetcher.model.search.SearchTestSupport;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilFile;

/**
 * Tests how indexes are kept up to date: which need the daily sync
 * (network drives, folders not watched), its time, and when a missed sync is
 * caught up.
 */
public final class IndexSyncTest {

	private static long at(int day, int hour, int minute) {
		Calendar calendar = Calendar.getInstance();
		calendar.clear();
		calendar.set(2026, Calendar.OCTOBER, day, hour, minute);
		return calendar.getTimeInMillis();
	}

	@Test
	public void timeAndCatchingUp() {
		assertEquals(Integer.valueOf(3 * 60), IndexSync.parseTime("03:00"));
		assertEquals(Integer.valueOf(22 * 60 + 30), IndexSync.parseTime(" 22.30 "));
		assertNull(IndexSync.parseTime("24:00"));
		assertNull(IndexSync.parseTime("drei"));
		assertEquals("03:05", IndexSync.format(3 * 60 + 5));

		int three = 3 * 60;
		assertEquals(at(11, 3, 0), IndexSync.lastScheduled(at(11, 8, 0), three));
		assertEquals("Before the time: yesterday's", at(10, 3, 0), IndexSync.lastScheduled(at(11, 2, 0), three));
		// Ran yesterday night: due again from 03:00, not before
		assertFalse(IndexSync.isDue(at(11, 2, 59), at(10, 3, 1), three));
		assertTrue(IndexSync.isDue(at(11, 3, 0), at(10, 3, 1), three));
		// DocFetcher wasn't running at night: caught up at the start in the morning, once
		assertTrue(IndexSync.isDue(at(11, 8, 0), at(9, 22, 0), three));
		assertFalse(IndexSync.isDue(at(11, 8, 5), at(11, 8, 0), three));
	}

	@Test
	public void whichIndexesNeedTheSync() throws Exception {
		assertFalse(UtilFile.isNetworkDrive(new File(System.getProperty("java.io.tmpdir"))));
		if (Util.IS_WINDOWS)
			assertTrue(UtilFile.isNetworkDrive(new File("\\\\server\\share\\Dokumente")));

		SearchTestSupport support = new SearchTestSupport("a.txt", "eins");
		try {
			LuceneIndex index = support.registry.getIndexes().get(0);
			assertTrue(index.isWatchFolders());
			assertFalse("Watched on a local disk", IndexSync.needsSync(index));
			assertEquals(Msg.index_sync_watched.get(), IndexSync.describe(index));
			assertFalse(IndexSync.isSynced(index));
			SettingsConf.Int.DailySyncScope.set(IndexSync.SCOPE_ALL);
			assertTrue(IndexSync.isSynced(index));
			SettingsConf.Int.DailySyncScope.set(IndexSync.SCOPE_NEEDED);

			index.getConfig().setWatchFolders(false);
			assertTrue(IndexSync.needsSync(index));
			assertTrue(IndexSync.isSynced(index));
			assertEquals("Not watched: daily sync at 03:00", IndexSync.describe(index));
			SettingsConf.Bool.DailyIndexUpdate.set(false);
			assertFalse(IndexSync.isSynced(index));
			assertTrue(IndexSync.describe(index).startsWith("Not watched: no daily sync"));
		}
		finally {
			SettingsConf.Bool.DailyIndexUpdate.set(true);
			SettingsConf.Int.DailySyncScope.set(IndexSync.SCOPE_NEEDED);
			support.dispose();
		}
	}

}
