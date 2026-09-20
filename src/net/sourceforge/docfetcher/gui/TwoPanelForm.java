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
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import com.google.common.base.Joiner;
import com.google.common.primitives.Ints;

/**
 * A two-panel layout with layout toggle buttons: The second panel can be
 * placed either below or to the right of the first panel, and it can be
 * hidden.
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
	@NotNull private ThinArrowButton bottomBt;
	@NotNull private ThinArrowButton outerRightBt;
	@NotNull private ThinArrowButton innerRightBt;
	
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
		secondControl.setLayout(UtilGui.createGridLayout(2, false, 0, 0));
		
		sash = new SashForm(secondControl, SWT.VERTICAL);
		sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		outerRightBt = new ThinArrowButton(secondControl, SWT.LEFT);
		outerRightBt.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 2));
		
		firstSubControlWrapper = new Composite(sash, SWT.NONE);
		firstSubControlWrapper.setLayout(UtilGui.createGridLayout(2, false, 0, 0));

		firstSubControl = createFirstSubControl(firstSubControlWrapper);
		firstSubControl.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		innerRightBt = new ThinArrowButton(firstSubControlWrapper, SWT.RIGHT);
		innerRightBt.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 2));

		bottomBt = new ThinArrowButton(firstSubControlWrapper, SWT.UP);
		bottomBt.setLayoutData(new GridData(SWT.CENTER, SWT.BOTTOM, false, false));

		secondSubControl = createSecondSubControl(sash);
		
		MouseAdapter rightBtHandler = new MouseAdapter() {
			public void mouseUp(MouseEvent e) {
				if (isSecondSubControlVisible() && ! isVertical()) {
					setSecondSubControlVisible(false);
				} else {
					setSecondSubControlVisible(true);
					setVertical(false);
				}
			}
		};
		outerRightBt.addMouseListener(rightBtHandler);
		innerRightBt.addMouseListener(rightBtHandler);
		
		bottomBt.addMouseListener(new MouseAdapter() {
			public void mouseUp(MouseEvent e) {
				if (isSecondSubControlVisible() && isVertical()) {
					setSecondSubControlVisible(false);
				} else {
					setSecondSubControlVisible(true);
					setVertical(true);
				}
			}
		});
		
		updateRightButtons();
	}
	
	private void updateRightButtons() {
		boolean isVisible = isSecondSubControlVisible();
		boolean isVertical = isVertical();
		boolean isBottomVisible = isVisible && isVertical;
		boolean isRightVisible = isVisible && ! isVertical;
		
		outerRightBt.setOrientation(isRightVisible ? SWT.RIGHT : SWT.LEFT);
		innerRightBt.setOrientation(isRightVisible ? SWT.RIGHT : SWT.LEFT);
		bottomBt.setOrientation(isBottomVisible ? SWT.DOWN : SWT.UP);
		
		/*
		 * While the second subcontrol is visible, the buttons allow hiding it
		 * and moving it to the other position. While it is hidden, only the
		 * button that restores it to its last position is shown, so that it
		 * always reappears where it was.
		 */
		boolean showOuterRight = isBottomVisible;
		boolean showInnerRight = isRightVisible || (! isVisible && ! isVertical);
		boolean showBottom = isVisible || isVertical;
		
		outerRightBt.setVisible(showOuterRight);
		((GridData) outerRightBt.getLayoutData()).exclude = ! showOuterRight;
		((GridLayout) secondControl.getLayout()).numColumns = showOuterRight ? 2 : 1;

		innerRightBt.setVisible(showInnerRight);
		((GridData) innerRightBt.getLayoutData()).exclude = ! showInnerRight;
		((GridLayout) firstSubControlWrapper.getLayout()).numColumns = showInnerRight ? 2 : 1;
		
		bottomBt.setVisible(showBottom);
		((GridData) bottomBt.getLayoutData()).exclude = ! showBottom;
		
		firstSubControlWrapper.layout();
		secondControl.layout();
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
		updateRightButtons();
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
		updateRightButtons();
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
