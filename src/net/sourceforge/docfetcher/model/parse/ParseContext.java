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

package net.sourceforge.docfetcher.model.parse;

import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.model.index.IndexingReporter;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * @author Tran Nam Quang
 */
final class ParseContext {
	
	private final String filename;
	private final IndexingReporter reporter;
	private final Cancelable cancelable;
	private boolean ocrAllowed = true;
	private boolean ocrSkipped = false;

	public ParseContext(@NotNull String filename) {
		this(filename, IndexingReporter.nullReporter, Cancelable.nullCancelable);
	}
	
	public ParseContext(@NotNull String filename,
	                    @NotNull IndexingReporter reporter,
						@NotNull Cancelable cancelable) {
		Util.checkNotNull(filename, reporter, cancelable);
		this.filename = filename;
		this.reporter = reporter;
		this.cancelable = cancelable;
	}
	
	@NotNull
	public String getFilename() {
		return filename;
	}

	@NotNull
	public IndexingReporter getReporter() {
		return reporter;
	}

	@NotNull
	public Cancelable getCancelable() {
		return cancelable;
	}
	
	/**
	 * Whether parsers may run text recognition now. The indexer disables it
	 * in its first pass and recognizes the deferred files at the end.
	 */
	public boolean isOcrAllowed() {
		return ocrAllowed;
	}
	
	public void setOcrAllowed(boolean ocrAllowed) {
		this.ocrAllowed = ocrAllowed;
	}
	
	/** Set by a parser that would have run text recognition if allowed. */
	public boolean isOcrSkipped() {
		return ocrSkipped;
	}
	
	public void setOcrSkipped(boolean ocrSkipped) {
		this.ocrSkipped = ocrSkipped;
	}

}
