/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import net.sourceforge.docfetcher.enums.Msg;

/**
 * Tests the version in the window title and the About box: a build without a
 * version (the development version) shows a label instead of 'Unspecified'.
 */
public final class VersionTest {

	@Test
	public void developmentVersionHasALabel() {
		assertEquals("DocFetcher " + Msg.development_version.get(), Application.withVersion("DocFetcher"));
		assertEquals("DocFetcher (development version)", Application.withVersion("DocFetcher"));
	}

}
