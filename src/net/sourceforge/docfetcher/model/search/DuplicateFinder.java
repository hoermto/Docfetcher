/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.sourceforge.docfetcher.util.annotations.NotNull;

/**
 * Finds documents with identical or nearly identical text from their words
 * as the index knows them (each word with its count) - no document is opened,
 * and names, folders and formats don't matter (a PDF and its Word file with
 * the same text are duplicates).
 * <p>
 * Identical: the same words with the same counts. Nearly identical: MinHash
 * signatures of the word sets agree in at least {@link #NEAR} of their
 * values, e.g. the same letter scanned twice with a few recognition errors;
 * candidates are found with locality-sensitive hashing (bands of the
 * signature), so not every pair is compared. Documents of fewer than
 * {@link #MIN_TERMS} different words (empty scans, images) are ignored.
 */
public final class DuplicateFinder {

	public static final int MIN_TERMS = 20;
	/** High, as letters of the same kind (monthly invoices) share most words. */
	static final double NEAR = 0.9;
	private static final int HASHES = 64;
	private static final int BANDS = 16;
	private static final int ROWS = HASHES / BANDS;

	/** A group of documents (by their keys) with identical or nearly identical text. */
	public static final class Group {
		public final List<Integer> keys;
		public final boolean identical;

		Group(@NotNull List<Integer> keys, boolean identical) {
			this.keys = keys;
			this.identical = identical;
		}
	}

	private final long[] exact;
	private final int[] termCounts;
	private final int[][] signatures;

	/** For documents with keys from 0 to size - 1. */
	public DuplicateFinder(int size) {
		exact = new long[size];
		termCounts = new int[size];
		signatures = new int[size][];
	}

	/** Adds a word of a document, given by its hash, with its count in the document. */
	public void addTerm(int key, int termHash, int count) {
		exact[key] += mix64(termHash) * count;
		termCounts[key]++;
		int[] signature = signatures[key];
		if (signature == null) {
			signature = signatures[key] = new int[HASHES];
			Arrays.fill(signature, Integer.MAX_VALUE);
		}
		for (int h = 0; h < HASHES; h++) {
			int value = mix(termHash, h);
			if (value < signature[h])
				signature[h] = value;
		}
	}

	/** Adds the words of a text (lower case, without punctuation). */
	public void add(int key, @NotNull String text) {
		Map<String, Integer> counts = new HashMap<String, Integer>();
		String clean = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
		if (!clean.isEmpty())
			for (String word : clean.split(" "))
				counts.put(word, counts.containsKey(word) ? counts.get(word) + 1 : 1);
		for (Map.Entry<String, Integer> entry : counts.entrySet())
			addTerm(key, entry.getKey().hashCode(), entry.getValue());
	}

	/** The groups of two or more documents, the identical ones first. */
	@NotNull
	public List<Group> groups() {
		int n = exact.length;
		int[] parent = new int[n];
		for (int i = 0; i < n; i++)
			parent[i] = i;

		// Identical texts
		Map<String, Integer> firstOf = new HashMap<String, Integer>();
		for (int i = 0; i < n; i++) {
			if (termCounts[i] < MIN_TERMS)
				continue;
			String key = exact[i] + "/" + termCounts[i];
			Integer first = firstOf.get(key);
			if (first == null)
				firstOf.put(key, i);
			else
				union(parent, first, i);
		}

		// Nearly identical texts: candidates share a band of their signatures
		Set<Long> compared = new HashSet<Long>();
		for (int band = 0; band < BANDS; band++) {
			Map<String, List<Integer>> buckets = new HashMap<String, List<Integer>>();
			for (int i = 0; i < n; i++) {
				if (termCounts[i] < MIN_TERMS)
					continue;
				StringBuilder bucket = new StringBuilder();
				for (int r = 0; r < ROWS; r++)
					bucket.append(signatures[i][band * ROWS + r]).append(',');
				List<Integer> members = buckets.get(bucket.toString());
				if (members == null)
					buckets.put(bucket.toString(), members = new ArrayList<Integer>());
				members.add(i);
			}
			for (List<Integer> members : buckets.values()) {
				for (int a = 0; a < members.size(); a++) {
					for (int b = a + 1; b < members.size(); b++) {
						int i = members.get(a), j = members.get(b);
						if (find(parent, i) == find(parent, j) || !compared.add((long) i * n + j))
							continue;
						if (similarity(signatures[i], signatures[j]) >= NEAR)
							union(parent, i, j);
					}
				}
			}
		}

		// The groups, in the order of the keys
		Map<Integer, List<Integer>> members = new LinkedHashMap<Integer, List<Integer>>();
		for (int i = 0; i < n; i++) {
			if (termCounts[i] < MIN_TERMS)
				continue;
			int root = find(parent, i);
			List<Integer> list = members.get(root);
			if (list == null)
				members.put(root, list = new ArrayList<Integer>());
			list.add(i);
		}
		List<Group> identical = new ArrayList<Group>();
		List<Group> near = new ArrayList<Group>();
		for (List<Integer> list : members.values()) {
			if (list.size() < 2)
				continue;
			boolean same = true;
			for (int i : list)
				same &= exact[i] == exact[list.get(0)] && termCounts[i] == termCounts[list.get(0)];
			(same ? identical : near).add(new Group(list, same));
		}
		identical.addAll(near);
		return identical;
	}

	/** One of the hash functions of the signature: a seeded integer mix (MurmurHash3 finalizer). */
	private static int mix(int x, int seed) {
		int h = x ^ (seed * 0x9E3779B9);
		h ^= h >>> 16;
		h *= 0x85EBCA6B;
		h ^= h >>> 13;
		h *= 0xC2B2AE35;
		h ^= h >>> 16;
		return h & 0x7FFFFFFF;
	}

	/** A 64-bit mix for the identity of the word counts. */
	private static long mix64(long x) {
		x ^= x >>> 33;
		x *= 0xFF51AFD7ED558CCDL;
		x ^= x >>> 33;
		x *= 0xC4CEB9FE1A85EC53L;
		x ^= x >>> 33;
		return x;
	}

	/** The share of equal values: an estimate of the share of common words. */
	static double similarity(@NotNull int[] s1, @NotNull int[] s2) {
		int equal = 0;
		for (int i = 0; i < s1.length; i++)
			if (s1[i] == s2[i])
				equal++;
		return (double) equal / s1.length;
	}

	private static int find(@NotNull int[] parent, int i) {
		while (parent[i] != i)
			i = parent[i] = parent[parent[i]];
		return i;
	}

	private static void union(@NotNull int[] parent, int i, int j) {
		parent[find(parent, i)] = find(parent, j);
	}

}
