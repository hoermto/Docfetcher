/*******************************************************************************
 * Copyright (c) 2010, 2011 Tran Nam Quang.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tran Nam Quang - initial API and implementation
 *******************************************************************************/

package net.sourceforge.docfetcher.model.parse;

import java.util.ArrayList;
import java.util.List;

import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.MutableCopy;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

/**
 * TODO doc: simplifies creation of Lucene Document objects for the parsers.
 * allows method chaining
 * 
 * @author Tran Nam Quang
 */
public final class ParseResult {
	
	@NotNull private CharSequence content;
	@Nullable private String title;
	@Nullable private List<String> authors;
	@Nullable private List<String> miscMetadata;
	@NotNull private String parserName;
	private int pageCount = -1;
	
	public ParseResult(@Nullable CharSequence content) {
		setContent(content);
	}
	
	@NotNull
	public CharSequence getContent() {
		return content;
	}
	
	@NotNull
	public ParseResult setContent(@Nullable CharSequence content) {
		this.content = content == null ? "" : content;
		return this;
	}
	
	@Nullable
	public String getTitle() {
		return title;
	}
	
	@NotNull
	public ParseResult setTitle(@Nullable String title) {
		this.title = title;
		return this;
	}
	
	@MutableCopy
	@Nullable
	public List<String> getAuthors() {
		return authors;
	}
	
	@NotNull
	public ParseResult addAuthor(@Nullable String author) {
		if (author == null)
			return this;
		if (authors == null)
			authors = new ArrayList<String> (3);
		authors.add(author);
		return this;
	}
	
	@MutableCopy
	@Nullable
	public List<String> getMiscMetadata() {
		return miscMetadata;
	}
	
	@NotNull
	public ParseResult addMiscMetadata(@Nullable String metadata) {
		if (metadata == null) // PDFBox may return null on some metadata fields
			return this;
		if (miscMetadata == null)
			miscMetadata = new ArrayList<String> (5);
		miscMetadata.add(metadata);
		return this;
	}
	
	// Returned char sequence has an empty string appended at the end
	@NotNull
	public StringBuilder getMetadata() {
		StringBuilder metadata = new StringBuilder();
		if (title != null)
			metadata.append(title).append(" ");
		if (authors != null)
			for (String author : authors)
				metadata.append(author).append(" ");
		if (miscMetadata != null)
			for (String metadata0 : miscMetadata)
				metadata.append(metadata0).append(" ");
		return metadata;
	}
	
	/**
	 * Returns the number of pages of the document, or -1 if unknown. For
	 * presentations, this is the number of slides.
	 */
	public int getPageCount() {
		return pageCount;
	}
	
	/**
	 * Sets the number of pages; values below 1 mean "unknown".
	 */
	@NotNull
	public ParseResult setPageCount(int pageCount) {
		this.pageCount = pageCount > 0 ? pageCount : -1;
		return this;
	}
	
	// Should not be called by Parser implementations
	@Nullable
	public String getParserName() {
		return parserName;
	}
	
	// Should not be called by Parser implementations
	@NotNull
	public ParseResult setParserName(@NotNull String parserName) {
		this.parserName = Util.checkNotNull(parserName);
		return this;
	}
	
	private boolean ocrSkipped = false;
	
	/** True if the parser would have run text recognition but wasn't allowed to. */
	public boolean isOcrSkipped() {
		return ocrSkipped;
	}
	
	public void setOcrSkipped(boolean ocrSkipped) {
		this.ocrSkipped = ocrSkipped;
	}

}
