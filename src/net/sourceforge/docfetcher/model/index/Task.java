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

package net.sourceforge.docfetcher.model.index;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.model.index.file.FileIndex;
import net.sourceforge.docfetcher.model.LuceneIndex;
import net.sourceforge.docfetcher.model.PendingDeletion;
import net.sourceforge.docfetcher.model.TreeIndex.IndexingResult;
import net.sourceforge.docfetcher.model.index.DelegatingReporter.ExistingMessagesHandler;
import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.annotations.ThreadSafe;

import com.google.common.base.Objects;

/**
 * @author Tran Nam Quang
 */
public final class Task {

	public interface CancelHandler {
		/**
		 * Returns the type of cancelation. May be null to indicate that the
		 * indexing should not be canceled. Warning: This method is called under
		 * lock.
		 */
		@Nullable
		public CancelAction cancel();
	}

	public enum IndexAction {
		CREATE, UPDATE, REBUILD
	}

	public enum CancelAction {
		DISCARD, KEEP
	}

	public enum TaskState {
		NOT_READY, READY, INDEXING, FINISHED
	}
	
	// Event data: True if there were errors
	// Fired without lock, possibly from non-GUI thread
	public final Event<Boolean> evtFinished = new Event<Boolean>();

	private final IndexingQueue queue;
	private final LuceneIndex index;
	private final IndexAction indexAction;
	@NotNull private volatile TaskState state;
	@Nullable private volatile PendingDeletion deletion;
	private final DelegatingReporter reporter;
	@Nullable volatile CancelAction cancelAction;
	/**
	 * For an update: only these folders (see FileIndex.updateFolders); null:
	 * the whole index. Changed under the lock of the queue while the task
	 * is ready.
	 */
	@Nullable private volatile Set<File> folders;

	Task(	@NotNull IndexingQueue queue,
			@NotNull LuceneIndex index,
			@NotNull IndexAction indexAction) {
		this(queue, index, indexAction, null);
	}

	Task(	@NotNull IndexingQueue queue,
			@NotNull LuceneIndex index,
			@NotNull IndexAction indexAction,
			@Nullable Collection<File> folders) {
		Util.checkNotNull(queue, index, indexAction);
		this.folders = folders == null ? null : new LinkedHashSet<File>(folders);
		this.queue = queue;
		this.index = index;
		this.indexAction = indexAction;
		state = is(IndexAction.UPDATE) ? TaskState.READY : TaskState.NOT_READY;
		reporter = new DelegatingReporter(queue.reporterCapacity);
		reporter.setProgressListener(new DelegatingReporter.ProgressListener() {
			public void progressChanged(int visited, int total) {
				// Notify listeners only when the displayed value changes
				int percent = computePercent(visited, total);
				if (percent != lastPercent) {
					lastPercent = percent;
					queue.evtProgress.fire(Task.this);
				}
			}
		});
	}
	
	private volatile int lastPercent = -1;
	
	/**
	 * Returns the progress of this task in percent (0-99 while running), or
	 * -1 if unknown, e.g. because the task hasn't started yet.
	 */
	public int getProgressPercent() {
		return computePercent(reporter.getProgressVisited(), reporter.getProgressTotal());
	}
	
	private static int computePercent(int visited, int total) {
		if (total <= 0)
			return -1;
		return Math.min(99, (int) (visited * 100L / total));
	}

	// If the task is not in indexing state, the task is simply removed.
	// If it is in indexing state, the handler is called for 'Create' and
	// 'Rebuild' jobs to determine whether to discard or keep the index. In case
	// of 'Update' tasks, the index is always kept. If in indexing state, only
	// a cancel flag is set, but the task may be removed later.
	// See IndexingQueue.removeAll(CancelHandler)
	// Warning: Cancel handler is called under lock, so caller must take possible
	// lock-ordering deadlocks into account.
	@ThreadSafe
	public void remove(@NotNull CancelHandler handler) {
		queue.remove(this, handler);
	}

	// for index updates, the ready-state is set automatically
	@ThreadSafe
	public void setReady() {
		queue.setReady(this);
	}

	@NotNull
	IndexingResult update() {
		Cancelable cancelable = new Cancelable() {
			public boolean isCanceled() {
				return cancelAction != null;
			}
		};
		Set<File> only = folders;
		if (only != null && index instanceof FileIndex)
			return ((FileIndex) index).updateFolders(only, reporter, cancelable);
		return index.update(reporter, cancelable);
	}

	/**
	 * Adds the folders of another update request for the same index; null
	 * (the whole index) makes this a complete update.
	 */
	void mergeFolders(@Nullable Set<File> more) {
		Set<File> current = folders;
		if (current == null)
			return; // Complete already
		if (more == null) {
			folders = null;
			return;
		}
		Set<File> merged = new LinkedHashSet<File>(current);
		merged.addAll(more);
		folders = merged;
	}

	/** The folders of a partial update, or null for the whole index. */
	@Nullable
	Set<File> getFolders() {
		return folders;
	}

	@NotNull
	public LuceneIndex getLuceneIndex() {
		return index;
	}

	// delegate and handler are called under lock of DelegatingReporter instance, so beware of lock-ordering deadlocks!
	public void attachReporter(	@NotNull IndexingReporter delegate,
								@NotNull ExistingMessagesHandler handler) {
		reporter.attachDelegate(delegate, handler);
	}

	public void detachReporter(	@NotNull IndexingReporter delegate) {
		reporter.detachDelegate(delegate);
	}

	@ThreadSafe
	public boolean is(@Nullable IndexAction indexAction) {
		// No lock needed because indexAction is immutable
		return Objects.equal(this.indexAction, indexAction);
	}

	@ThreadSafe
	boolean is(@Nullable CancelAction cancelAction) {
		queue.readLock.lock();
		try {
			return Objects.equal(this.cancelAction, cancelAction);
		}
		finally {
			queue.readLock.unlock();
		}
	}

	@ThreadSafe
	public boolean is(@Nullable TaskState state) {
		queue.readLock.lock();
		try {
			return Objects.equal(this.state, state);
		}
		finally {
			queue.readLock.unlock();
		}
	}

	@ThreadSafe
	void set(@NotNull TaskState state) {
		queue.writeLock.lock();
		try {
			this.state = Util.checkNotNull(state);
		}
		finally {
			queue.writeLock.unlock();
		}
	}
	
	@ThreadSafe
	void setDeletion(@NotNull PendingDeletion deletion) {
		queue.writeLock.lock();
		try {
			this.deletion = Util.checkNotNull(deletion);
		}
		finally {
			queue.writeLock.unlock();
		}
	}
	
	@Nullable
	@ThreadSafe
	PendingDeletion getDeletion() {
		queue.readLock.lock();
		try {
			return deletion;
		}
		finally {
			queue.readLock.unlock();
		}
	}
	
	public String toString() {
		return String.format("Task [%s %s] %s", indexAction.name(), state
				.name(), index.getCanonicalRootFile().toString());
	}

}