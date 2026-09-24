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

package net.sourceforge.docfetcher.gui;

import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import com.google.common.base.Joiner;
import com.google.common.primitives.Ints;

/**
 * A two-panel layout: The second panel can be placed either below or to the
 * right of the first panel, and it can be hidden. Both is controlled from the
 * outside (the 'View' menu and the preferences).
 * <p>
 * The two panels are to be created by subclassers by implementing
 * {@link #createFirstSubControl} and {@link #createSecondSubControl}.
 * 
 * @author Tran Nam Quang
 */
public abstract class TwoPanelForm extends Composite {
	
	public final Event<Boolean> evtSecondSubControlShown = new Event<Boolean>();
	public final Event<Boolean> evtSubOrientationChanging = new Event<Boolean>();
	public final Event<Boolean> evtSubOrientationChanged = new Event<Boolean>();
	
	@NotNull private SashForm sash;
	@NotNull private Composite secondControl;
	@NotNull private Control firstSubControl;
	@NotNull private Control secondSubControl;
	@NotNull private Composite firstSubControlWrapper;
	
	public TwoPanelForm(@NotNull Composite parent) {
		super(parent, SWT.NONE);
		setLayout(new FillLayout());
		createContents(this);
	}
	
	/**
	 * Creates and returns the first panel with the given parent.
	 */
	@NotNull
	protected abstract Control createFirstSubControl(Composite parent);
	
	/**
	 * Creates and returns the second panel with the given parent.
	 */
	@NotNull
	protected abstract Control createSecondSubControl(Composite parent);
	
	private void createContents(Composite parent) {
		secondControl = new Composite(parent, SWT.NONE);
		secondControl.setLayout(UtilGui.createGridLayout(1, false, 0, 0));
		
		sash = new SashForm(secondControl, SWT.VERTICAL);
		sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		firstSubControlWrapper = new Composite(sash, SWT.NONE);
		firstSubControlWrapper.setLayout(UtilGui.createGridLayout(1, false, 0, 0));

		firstSubControl = createFirstSubControl(firstSubControlWrapper);
		firstSubControl.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		secondSubControl = createSecondSubControl(sash);
	}
	
	/**
	 * Returns the width of the secondary sash.
	 */
	public final int getSubSashWidth() {
		return sash.getSashWidth();
	}
	
	/**
	 * Sets the width of the secondary sash.
	 */
	public final void setSubSashWidth(int width) {
		sash.setSashWidth(width);
	}
	
	/**
	 * Returns whether the second subcontrol is visible.
	 */
	public final boolean isSecondSubControlVisible() {
		return sash.getMaximizedControl() == null;
	}
	
	/**
	 * Sets the visibility of the second subcontrol.
	 */
	public final void setSecondSubControlVisible(boolean isVisible) {
		if (isVisible)
			sash.setMaximizedControl(null);
		else
			sash.setMaximizedControl(firstSubControlWrapper);
		evtSecondSubControlShown.fire(isVisible);
	}
	
	/**
	 * Returns true if the orientation of the sash separating the two
	 * subcontrols is SWT.VERTICAL, otherwise false.
	 */
	public final boolean isVertical() {
		return sash.getOrientation() == SWT.VERTICAL;
	}

	/**
	 * Sets the orientation of the sash separating the two subcontrols.
	 */
	public final void setVertical(boolean isVertical) {
		evtSubOrientationChanging.fire(isVertical);
		sash.setOrientation(isVertical ? SWT.VERTICAL : SWT.HORIZONTAL);
		evtSubOrientationChanged.fire(isVertical);
	}
	
	@NotNull
	public final int[] getSubSashWeights() {
		return sash.getWeights();
	}
	
	public final void setSubSashWeights(@NotNull int[] weights) {
		try {
			sash.setWeights(weights);
		} catch (IllegalArgumentException e) {
			String s = Joiner.on(", ").join(Ints.asList(weights));
			throw new IllegalArgumentException("Invalid sash weights: " + s);
		}
	}
	
	@NotNull
	public final Control getFirstSubControl() {
		return firstSubControl;
	}
	
	@NotNull
	public final Control getSecondSubControl() {
		return secondSubControl;
	}

}
