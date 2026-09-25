/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.AnalyzerWrapper;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * Makes the search tolerant: words are also found without their umlauts and
 * accents ('Muller' finds 'Müller' and the other way round, 'strasse' finds
 * 'Straße'), and despite typical recognition errors of the OCR in words with
 * letters and digits ('Bonn' finds 'B0nn', 'Hallo' finds 'Ha11o').
 * <p>
 * Each word whose folded form differs is indexed twice, as it is and folded,
 * at the same position; queries are analyzed the same way. Since the original
 * form is kept, exact searches also work in indexes built before, which only
 * contain the original forms.
 */
public final class TolerantAnalyzer extends AnalyzerWrapper {

	private final Analyzer delegate;

	TolerantAnalyzer(@NotNull Analyzer delegate) {
		super(delegate.getReuseStrategy());
		this.delegate = delegate;
	}

	@Override
	protected Analyzer getWrappedAnalyzer(String fieldName) {
		return delegate;
	}

	@Override
	protected TokenStreamComponents wrapComponents(String fieldName, TokenStreamComponents components) {
		TokenStream filtered = new FoldingFilter(components.getTokenStream());
		return new TokenStreamComponents(components.getTokenizer(), filtered);
	}

	/** Wildcard and prefix terms ('Mül*') are folded, too. */
	@Override
	protected TokenStream wrapTokenStreamForNormalization(String fieldName, TokenStream in) {
		return new TokenFilter(in) {
			private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

			public boolean incrementToken() throws IOException {
				if (!input.incrementToken())
					return false;
				String folded = fold(termAtt.toString());
				termAtt.setEmpty().append(folded);
				return true;
			}
		};
	}

	/**
	 * Returns the tolerant form of a (lower case) word: umlauts and accents
	 * replaced by the plain letters (ß by ss), and, in words with letters and
	 * digits, 0, 1 and 5 replaced by o, l and s. Pure numbers stay as they are.
	 */
	@NotNull
	public static String fold(@NotNull String term) {
		char[] input = term.toCharArray();
		char[] output = new char[input.length * 4];
		int length = ASCIIFoldingFilter.foldToASCII(input, 0, output, 0, input.length);
		boolean letters = false;
		boolean digits = false;
		for (int i = 0; i < length; i++) {
			letters |= Character.isLetter(output[i]);
			digits |= Character.isDigit(output[i]);
		}
		if (letters && digits) {
			for (int i = 0; i < length; i++) {
				switch (output[i]) {
				case '0': output[i] = 'o'; break;
				case '1': output[i] = 'l'; break;
				case '5': output[i] = 's'; break;
				default: break;
				}
			}
		}
		return new String(output, 0, length);
	}

	/** Adds the folded form of a word at the same position, if it differs. */
	private static final class FoldingFilter extends TokenFilter {
		private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
		private final PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);
		private State pendingState;
		private String pendingTerm;

		FoldingFilter(@NotNull TokenStream input) {
			super(input);
		}

		@Override
		public boolean incrementToken() throws IOException {
			if (pendingTerm != null) {
				restoreState(pendingState);
				termAtt.setEmpty().append(pendingTerm);
				posIncAtt.setPositionIncrement(0);
				pendingTerm = null;
				pendingState = null;
				return true;
			}
			if (!input.incrementToken())
				return false;
			String term = termAtt.toString();
			String folded = fold(term);
			if (!folded.equals(term)) {
				pendingState = captureState();
				pendingTerm = folded;
			}
			return true;
		}

		@Override
		public void reset() throws IOException {
			super.reset();
			pendingTerm = null;
			pendingState = null;
		}
	}

}
