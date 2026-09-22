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

package net.sourceforge.docfetcher.gui.preview;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Collection;
import java.net.URI;

import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.gui.CustomBorderComposite;
import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.gui.BrowserPopupBlocker;
import net.sourceforge.docfetcher.util.gui.Col;
import net.sourceforge.docfetcher.util.gui.ToolItemFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

/**
 * @author Tran Nam Quang
 */
abstract class HtmlPreview extends ToolBarForm {
	
	public final Event<Void> evtHtmlToTextBt = new Event<Void>();
	public final Event<Void> evtHideInSystemTray = new Event<Void>();
	
	@NotNull private ToolItem backBt;
	@NotNull private ToolItem forwardBt;
	@NotNull private Text locationBar;
	@NotNull private ToolItem htmlBt;
	@NotNull private Browser browser;
	
	/*
	 * When a rendered document (e.g. a temporary HTML file created from a Word
	 * document) is displayed, the location bar shows the path of the original
	 * document rather than the browser URL, and the 'launch external' button
	 * opens the original document.
	 */
	@Nullable private String displayPathOverride;
	@Nullable private File externalLaunchFile;
	
	// This will throw an SWTError if no embedded browser is available
	public HtmlPreview(@NotNull Composite parent) {
		super(parent);
	}
	
	protected abstract void saveSettings();
	
	@NotNull
	protected Control createToolBar(@NotNull Composite parent) {
		CustomBorderComposite comp = new CustomBorderComposite(parent);
		int margin = Util.IS_WINDOWS ? 2 : 0;
		comp.setLayout(UtilGui.createGridLayout(3, false, margin, 0));
		
		ToolBar leftToolBar = new ToolBar(comp, SWT.FLAT);
		leftToolBar.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
		
		ToolItemFactory tif = new ToolItemFactory(leftToolBar);
		tif.enabled(false);
		
		backBt = tif.image(Img.ARROW_LEFT.get()).toolTip(Msg.prev_page.get())
				.listener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						browser.back();
					}
				}).create();
		
		forwardBt = tif.image(Img.ARROW_RIGHT.get()).toolTip(Msg.next_page.get())
				.listener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						browser.forward();
					}
				}).create();
		
		tif.enabled(true);
		
		tif.image(Img.STOP.get()).toolTip(Msg.browser_stop.get())
				.listener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						browser.stop();
					}
				}).create();
		
		tif.image(Img.REFRESH.get()).toolTip(Msg.browser_refresh.get())
				.listener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						browser.refresh();
					}
				}).create();

		tif.image(Img.WINDOW.get()).toolTip(Msg.browser_launch_external.get())
				.listener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						if (externalLaunchFile != null) {
							UtilGui.launch(externalLaunchFile);
						}
						else {
							String url = browser.getUrl();
							if (url.equals(""))
								return;
							UtilGui.launch(url);
						}
						if (SettingsConf.Bool.HideOnOpen.get())
							evtHideInSystemTray.fire(null);
					}
				}).create();
		
		locationBar = new Text(comp, SWT.SINGLE | SWT.BORDER);
		locationBar.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		UtilGui.selectAllOnFocus(locationBar);
		
		locationBar.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				if (UtilGui.isEnterKey(e.keyCode))
					browser.setUrl(locationBar.getText());
			}
		});
		
		ToolBar rightToolBar = new ToolBar(comp, SWT.FLAT);
		rightToolBar.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
		
		tif.toolBar(rightToolBar);
		tif.style(SWT.CHECK);
		
		htmlBt = tif.image(Img.BUILDING_BLOCKS.get())
				.toolTip(Msg.use_embedded_html_viewer.get())
				.listener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						evtHtmlToTextBt.fire(null);
					}
				}).create();
		
		htmlBt.setSelection(true);
		
		return comp;
	}
	
	@NotNull
	protected Control createContents(@NotNull Composite parent) {
		boolean wasShowManual = SettingsConf.Bool.ShowManualOnStartup.get();
		SettingsConf.Bool.ShowManualOnStartup.set(false);
		saveSettings();
		
		try {
			browser = createBrowser(parent);
		}
		finally {
			/*
			 * Next time, only show the manual in the browser if the VM didn't
			 * crash while trying to initialize the browser.
			 */
			SettingsConf.Bool.ShowManualOnStartup.set(wasShowManual);
			saveSettings();
		}
		
		browser.addLocationListener(new LocationAdapter() {
			public void changing(LocationEvent event) {
				locationBar.setBackground(Col.WIDGET_BACKGROUND.get());
			}
			public void changed(LocationEvent event) {
				backBt.setEnabled(browser.isBackEnabled());
				forwardBt.setEnabled(browser.isForwardEnabled());
				String path = browser.getUrl();
				
				if (path.equals("about:blank")) {
					path = "";
				}
				else if (path.startsWith("file:///")) {
					try {
						path = Util.getSystemAbsPath(new File(new URI(path)));
					}
					catch (Exception e) {
						/*
						 * Ignoring URISyntaxException and
						 * IllegalArgumentException. The latter can happen if
						 * the URI contains a "fragment component", e.g.
						 * "myfile.htm#Section_1".
						 */
					}
				}
				if (displayPathOverride != null)
					path = displayPathOverride;
				locationBar.setText(path);
				
				/*
				 * The appropriate color is 'LIST_BACKGROUND', not 'WHITE',
				 * because the user might have chosen a dark theme.
				 */
	            locationBar.setBackground(Col.LIST_BACKGROUND.get());
			}
		});
		
		// Block popups
		BrowserPopupBlocker.initialize(parent.getDisplay(), browser);
		
		return browser;
	}
	
	/**
	 * Creates the browser widget. On Windows, the Edge (WebView2) backend is
	 * preferred since it renders modern HTML and has a built-in PDF viewer; if
	 * it isn't available (e.g. WebView2 runtime not installed), SWT falls back
	 * to the platform default (Internet Explorer). On other platforms, the
	 * SWT.EDGE flag is ignored. Throws an SWTError if no browser is available
	 * at all.
	 */
	@NotNull
	private static Browser createBrowser(@NotNull Composite parent) {
		Control[] childrenBefore = parent.getChildren();
		try {
			return new Browser(parent, SWT.BORDER | SWT.EDGE);
		}
		catch (SWTError e) {
			if (!Util.IS_WINDOWS)
				throw e;
			/*
			 * If the Edge backend fails during initialization, SWT may leave
			 * a half-constructed Browser widget behind. Remove it before
			 * creating the fallback browser.
			 */
			for (Control child : parent.getChildren()) {
				boolean isNew = true;
				for (Control old : childrenBefore)
					if (old == child)
						isNew = false;
				if (isNew)
					child.dispose();
			}
			return new Browser(parent, SWT.BORDER);
		}
	}
	
	/**
	 * Returns true if the embedded browser is the Edge (WebView2) backend,
	 * which has a built-in PDF viewer.
	 */
	public boolean isEdge() {
		try {
			return "edge".equalsIgnoreCase(browser.getBrowserType());
		}
		catch (RuntimeException e) {
			return false;
		}
	}
	
	/**
	 * Displays a rendered document: The given file (e.g. a PDF file or a
	 * temporary HTML file generated from a Word document) is loaded into the
	 * browser, while the location bar shows the given display path and the
	 * 'launch external' button opens the given original document.
	 */
	public void setDocument(@NotNull File fileToLoad,
							@NotNull File originalDocument,
							@NotNull String displayPath) {
		setDocument(fileToLoad, originalDocument, displayPath,
			Collections.<String> emptyList());
	}
	
	/**
	 * Shows the given document. The search terms, if any, are passed to the
	 * browser's built-in PDF viewer, which marks them in the document and
	 * jumps to the first one; viewers that don't know the parameter simply
	 * show the document.
	 */
	public void setDocument(@NotNull File fileToLoad,
							@NotNull File originalDocument,
							@NotNull String displayPath,
							@NotNull Collection<String> searchTerms) {
		setDocument(fileToLoad, originalDocument, displayPath, searchTerms, 0);
	}
	
	/**
	 * Shows the given document, scrolled to the given page (counted from 1;
	 * 0 for the first page). The search terms are passed to the browser's
	 * PDF viewer, which may search for them itself.
	 */
	public void setDocument(@NotNull File fileToLoad,
							@NotNull File originalDocument,
							@NotNull String displayPath,
							@NotNull Collection<String> searchTerms,
							int page) {
		Util.checkNotNull(fileToLoad, originalDocument, displayPath, searchTerms);
		displayPathOverride = displayPath;
		externalLaunchFile = originalDocument;
		try {
			// Path.toUri() yields "file:///C:/..." on Windows, which all
			// browser backends accept, whereas File.toURI() yields "file:/C:/..."
			browser.setUrl(fileToLoad.toPath().toUri().toString()
				+ PdfViewerParams.createFragment(searchTerms, page));
		}
		catch (SWTException e) {
			// Bug #2428: Ignore SWTException when setting URL
		}
		locationBar.setText(displayPath);
		htmlBt.setEnabled(false);
		htmlBt.setSelection(true);
	}
	
	// TODO post-release-1.1: maybe add HTML highlighting
	/**
	 * Sets the file to be displayed.
	 */
	public void setFile(@NotNull File file, boolean allowSwitchingToTextPreview) {
		displayPathOverride = null;
		externalLaunchFile = null;
		String path = Util.getSystemAbsPath(file);
		try {
			String url;
			if (path.startsWith("\\\\")) {
				/*
				 * Bug #1351: Extra weirdness on Windows: If the file is given
				 * by a UNC path, do not convert the path to a URL, otherwise
				 * the URL will be percent-encoded twice. This will cause spaces
				 * to be replaced with "%2520", rather than "%20", for instance.
				 */
				url = path;
			} else {
				url = file.toURI().toURL().toString();
			}
			browser.setUrl(url);
		}
		catch (MalformedURLException e) {
			try {
				browser.setUrl(path);
			} catch (SWTException e2) {
				// Bug #2428: Ignore SWTException when setting URL
				// This can occur when the IE OLE component is in an invalid state
			}
		}
		catch (SWTException e) {
			// Bug #2428: Ignore SWTException when setting URL
			// This can occur when the IE OLE component is in an invalid state
		}
		locationBar.setText(path);
		htmlBt.setEnabled(allowSwitchingToTextPreview);
		htmlBt.setSelection(true);
	}

	public void clear() {
		displayPathOverride = null;
		externalLaunchFile = null;
		try {
			browser.setText("");
		} catch (SWTException e) {
			// Bug #2428: Ignore SWTException when clearing browser
			// This can occur when the IE OLE component is in an invalid state
		}
		locationBar.setText("");
		htmlBt.setEnabled(false);
		htmlBt.setSelection(false);
	}

}
