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

import java.util.ArrayList;
import java.util.List;

import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.MutableCopy;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.collect.BoundedList;

/**
 * @author Tran Nam Quang
 */
public final class DelegatingReporter extends IndexingReporter {

	public interface ExistingMessagesHandler {
		public void handleMessages(	@MutableCopy @NotNull List<IndexingInfo> infos,
									@MutableCopy @NotNull List<IndexingError> errors);
	}

	@Nullable private IndexingReporter delegate;
	private int progressVisited = 0;
	private int progressTotal = -1;
	@Nullable private ProgressListener progressListener;
	private final BoundedList<IndexingInfo> infos;
	private final List<IndexingError> errors;
	@Nullable private Long start;
	@Nullable private Long end;

	DelegatingReporter(int infoCapacity) {
		infos = new BoundedList<IndexingInfo>(infoCapacity);
		errors = new ArrayList<IndexingError>();
	}

	// delegate and handler are called under lock of receiver, so beware of lock-ordering deadlocks!
	public synchronized void attachDelegate(@NotNull IndexingReporter delegate,
											@NotNull ExistingMessagesHandler handler) {
		Util.checkNotNull(delegate, handler);
		Util.checkThat(this.delegate == null);
		
		this.delegate = delegate;
		if (start != null)
			delegate.setStartTime(start);
		if (end != null)
			delegate.setEndTime(end);
		
		if (progressVisited > 0 || progressTotal >= 0)
			delegate.progress(progressVisited, progressTotal);
		
		List<IndexingInfo> infoCopy = new ArrayList<IndexingInfo>(infos);
		List<IndexingError> errorCopy = new ArrayList<IndexingError>(errors);
		handler.handleMessages(infoCopy, errorCopy);
	}

	public synchronized void detachDelegate(@NotNull IndexingReporter delegate) {
		Util.checkNotNull(delegate);
		Util.checkThat(this.delegate == delegate);
		this.delegate = null;
	}

	public synchronized void setStartTime(long time) {
		start = time;
		if (delegate != null)
			delegate.setStartTime(time);
	}

	public synchronized void setEndTime(long time) {
		end = time;
		if (delegate != null)
			delegate.setEndTime(time);
	}

	public synchronized void info(@NotNull IndexingInfo info) {
		infos.add(info);
		if (delegate != null)
			delegate.info(info);
	}
	
	public synchronized void progress(int visited, int total) {
		progressVisited = visited;
		progressTotal = total;
		if (delegate != null)
			delegate.progress(visited, total);
		if (progressListener != null)
			progressListener.progressChanged(visited, total);
	}
	
	/** Receives all progress reports, whether a delegate is attached or not. */
	public interface ProgressListener {
		public void progressChanged(int visited, int total);
	}
	
	public synchronized void setProgressListener(@Nullable ProgressListener listener) {
		progressListener = listener;
	}
	
	/** Number of files visited so far by the indexing task. */
	public synchronized int getProgressVisited() {
		return progressVisited;
	}
	
	/** Total number of files of the indexing task, or -1 if unknown. */
	public synchronized int getProgressTotal() {
		return progressTotal;
	}
	
	public synchronized void subInfo(int current, int total) {
		Util.checkThat(!infos.isEmpty());
		infos.getLast().setPercentage(current, total);
		if (delegate != null)
			delegate.subInfo(current, total);
	}

	public synchronized void fail(@NotNull IndexingError error) {
		errors.add(error);
		if (delegate != null)
			delegate.fail(error);
	}

}