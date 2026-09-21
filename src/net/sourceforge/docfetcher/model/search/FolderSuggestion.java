/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.search;

import java.io.File;

import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * A folder suggested as target for moving a document, together with a
 * relevance in the range 0..1 that expresses how similar the documents in that
 * folder are to the document to be moved (1 = best suggestion).
 */
public final class FolderSuggestion {

	private final File folder;
	private final double relevance;

	public FolderSuggestion(@NotNull File folder, double relevance) {
		this.folder = Util.checkNotNull(folder);
		this.relevance = relevance;
	}

	@NotNull
	public File getFolder() {
		return folder;
	}

	public double getRelevance() {
		return relevance;
	}

}
