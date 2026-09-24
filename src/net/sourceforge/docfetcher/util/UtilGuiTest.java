/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.util;

import static org.junit.Assert.assertTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.Shell;
import org.junit.Assume;
import org.junit.Test;

/** Tests the grip drawn in the middle of the sashes between the panels. */
public final class UtilGuiTest {

	/** Returns a display, or null if the test runs without a screen. */
	private static Display createDisplay() {
		try {
			return new Display();
		}
		catch (Throwable e) {
			return null; // SWTError without a screen, missing GTK libraries, ...
		}
	}

	@Test
	public void picksUpTheSashesOfASashForm() {
		Display display = createDisplay();
		Assume.assumeTrue(display != null);
		try {
			Shell shell = new Shell(display);
			shell.setLayout(new FillLayout());
			SashForm sashForm = new SashForm(shell, SWT.HORIZONTAL);
			sashForm.setSashWidth(9);
			UtilGui.paintSashHandles(sashForm);

			// The sash is only created when the form is laid out
			new Composite(sashForm, SWT.NONE);
			new Composite(sashForm, SWT.NONE);
			shell.setSize(400, 300);
			shell.layout();
			while (display.readAndDispatch()) {} // Run the pending asyncExec

			int sashes = 0;
			for (Control child : sashForm.getChildren()) {
				if (!(child instanceof Sash))
					continue;
				sashes++;
				assertTrue("Sash draws a grip",
					((Sash) child).getData("sash-grip") != null);
				assertTrue("Sash is wide enough to grab",
					((Sash) child).getSize().x >= 8);
			}
			assertTrue("Sash form has a sash", sashes > 0);
			shell.dispose();
		}
		finally {
			display.dispose();
		}
	}

}
