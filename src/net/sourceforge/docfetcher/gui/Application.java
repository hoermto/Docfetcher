/*******************************************************************************
 * Copyright (c) 2010, 2011 Tran Nam Quang.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *	Tran Nam Quang - initial API and implementation
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.index.MergePolicy;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;

import com.drew.lang.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.io.LineReader;
import com.google.common.io.Resources;

import net.sourceforge.docfetcher.Main;
import net.sourceforge.docfetcher.Py4jHandler;
import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.ProgramConf;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.enums.SystemConf;
import net.sourceforge.docfetcher.gui.StatusBar.StatusBarPart;
import net.sourceforge.docfetcher.gui.filter.FileTypePanel;
import net.sourceforge.docfetcher.gui.filter.IndexPanel;
import net.sourceforge.docfetcher.gui.filter.SearchSettingsDialog;
import net.sourceforge.docfetcher.gui.pref.PrefDialog;
import net.sourceforge.docfetcher.gui.preview.PreviewPanel;
import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.model.Daemon;
import net.sourceforge.docfetcher.model.FolderWatcher;
import net.sourceforge.docfetcher.model.IndexLoadingProblems;
import net.sourceforge.docfetcher.model.IndexLoadingProblems.CorruptedIndex;
import net.sourceforge.docfetcher.model.IndexLoadingProblems.OverflowIndex;
import net.sourceforge.docfetcher.mcp.McpServer;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.LuceneIndex;
import net.sourceforge.docfetcher.model.TreeIndex;
import net.sourceforge.docfetcher.model.index.IndexingQueue;
import net.sourceforge.docfetcher.model.index.Task;
import net.sourceforge.docfetcher.model.index.Task.CancelAction;
import net.sourceforge.docfetcher.model.index.Task.CancelHandler;
import net.sourceforge.docfetcher.model.index.Task.IndexAction;
import net.sourceforge.docfetcher.model.index.Task.TaskState;
import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.CharsetDetectorHelper;
import net.sourceforge.docfetcher.util.ConfLoader;
import net.sourceforge.docfetcher.util.ConfLoader.Loadable;
import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.SettingsLocation;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.collect.AlphanumComparator;
import net.sourceforge.docfetcher.util.gui.CocoaUIEnhancer;
import net.sourceforge.docfetcher.util.gui.FormDataFactory;
import net.sourceforge.docfetcher.util.gui.LazyImageCache;
import net.sourceforge.docfetcher.util.gui.dialog.InfoDialog;
import net.sourceforge.docfetcher.util.gui.dialog.ListConfirmDialog;
import net.sourceforge.docfetcher.util.gui.dialog.MultipleChoiceDialog;

public final class Application {

	// TODO post-release-1.1: review visibility of all DocFetcher classes

	/**
	 * The width of the sashes between the panels, in pixels. Wide enough to
	 * be grabbed easily with the mouse.
	 */
	private static final int sashWidth = 9;

	private static volatile IndexRegistry indexRegistry;
	@Nullable private static McpServer mcpServer;
	private static volatile FolderWatcher folderWatcher;
	@Nullable private static HotkeyHandler hotkeyHandler;
	private static File programConfFile;

	private static SearchSettingsDialog searchSettingsDialog;
	private static FileTypePanel fileTypePanel;
	private static IndexPanel indexPanel;

	private static volatile Shell shell;
	private static TwoPanelForm twoPanelForm;
	private static SearchBar searchBar;
	private static ResultPanel resultPanel;
	private static DocToolBar docToolBar;
	@Nullable private static FolderSync folderSync;
	private static TabFolder mainTabFolder;
	private static final List<InboxTab> inboxTabs = new ArrayList<InboxTab>();
	private static final Set<File> inboxIndexRequests = new HashSet<File>();
	@Nullable private static FolderPanel folderPanel; // Next to the tabs
	@Nullable private static FolderPanel folderTabPanel; // On the 'Folders' tab
	@Nullable private static TabItem foldersTab;
	@NotNull private static TabItem searchTab;
private static SashForm folderSash;
	private static Composite folderPanelHolder;
	private static Composite firstFolderHolder;
	private static PreviewPanel previewPanel;
	private static volatile StatusBarPart indexingStatus;
	// Tasks of the current batch of indexing jobs, for the overall progress
	private static final List<Task> queuedTasks = new ArrayList<Task>();
	private static int batchTaskCount = 0;
	private static int batchFinishedCount = 0;
	private static SystemTrayHider systemTrayHider;
	private static StatusBar statusBar;
	private static boolean systemTrayShutdown = false;
	private static boolean exitRequested = false; // set by the Exit button and Ctrl+Q
	private static File settingsConfFile;
	
	private static boolean indexRegistryLoaded = false; // should only be accessed from SWT thread
	
	/*
	 * True while background indexing tasks enqueued by the program itself
	 * (startup updates, rebuilds of outdated indexes) are pending, so that the
	 * indexing indicator in the status bar is only shown if the queue hasn't
	 * already been emptied in the meantime.
	 */
	
	/*
	 * The index folder that is used if the user hasn't set a custom index
	 * folder in the preferences, i.e. either the folder from paths.txt or the
	 * built-in default.
	 */
	private static File defaultIndexParentDir;

	private Application() {
		throw new UnsupportedOperationException();
	}

	public static void main(String[] args) {
		/*
		 * Bug #3553412: Starting with Java 7, calling Arrays.sort can cause an
		 * IllegalArgumentException with the error message
		 * "Comparison method violates its general contract!". In DocFetcher,
		 * this happened on a PDF file with PDFBox 1.7.0. For background, see
		 * http://stackoverflow.com/questions/7849539/comparison-method
		 * -violates-its-general-contract-java-7-only
		 */
		System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
		
		/*
		 * Load system constants; this should be the very first thing to do.
		 * We'll first try to load from the jar (normal use case), then from a
		 * file (we're inside the IDE).
		 */
		String systemConfName = "system-conf.txt";
		String systemConfPath = "dev/system-conf.txt";
		boolean success = ConfLoader.loadFromStreamOrFile(
			Main.class, SystemConf.class, systemConfName, systemConfPath);
		if (!success) {
			/*
			 * This is pretty bad, just give up here. Note that we cannot use
			 * the stacktrace utility methods at this point because we haven't
			 * loaded the constants in the utility class yet.
			 */
			Util.printErr("Couldn't find resource: " + systemConfName);
			System.exit(1);
		}

		AppUtil.Const.PROGRAM_NAME.set(SystemConf.Str.ProgramName.get());
		AppUtil.Const.PROGRAM_VERSION.set(SystemConf.Str.ProgramVersion.get());
		AppUtil.Const.PROGRAM_BUILD_DATE.set(SystemConf.Str.BuildDate.get());
		AppUtil.Const.USER_DIR_PATH.set(Util.USER_DIR_PATH);
		AppUtil.Const.IS_PORTABLE.set(SystemConf.Bool.IsPortable.get());
		AppUtil.Const.IS_DEVELOPMENT_VERSION.set(SystemConf.Bool.IsDevelopmentVersion.get());

		Msg.loadFromDisk();
		Msg.setCheckEnabled(false);
		AppUtil.Messages.system_error.set(Msg.system_error.get());
		AppUtil.Messages.confirm_operation.set(Msg.confirm_operation.get());
		AppUtil.Messages.invalid_operation.set(Msg.invalid_operation.get());
		AppUtil.Messages.program_died_stacktrace_written.set(Msg.report_bug.get());
		AppUtil.Messages.program_running_launch_another.set(Msg.program_running_launch_another.get());
		AppUtil.Messages.ok.set(Msg.ok.get());
		AppUtil.Messages.cancel.set(Msg.cancel.get());
		Msg.setCheckEnabled(true);
		AppUtil.Messages.checkInitialized();

		// Path overrides
		File confPathOverride = null;
		File swtPathOverride = null;
		try {
			File pathsFile;

			// For non-portable macOS version, use ~/.docfetcher/paths.txt
			// (inside app bundle is not writable due to code signing)
			if (Util.IS_MAC_OS_X && !AppUtil.isPortable()) {
				File appDataDir = AppUtil.getAppDataDir();
				pathsFile = new File(appDataDir, "paths.txt");

				// Copy from app bundle if it doesn't exist yet
				if (!pathsFile.exists()) {
					File bundlePathsFile = new File("../Resources/misc", "paths.txt");
					if (bundlePathsFile.exists()) {
						Files.copy(bundlePathsFile, pathsFile);
					}
				}
			} else {
				pathsFile = new File("misc", "paths.txt");
			}

			Properties pathProps = CharsetDetectorHelper.load(pathsFile);
			confPathOverride = toFile(pathProps, "settings");
			IndexRegistry.indexPathOverride = toFile(pathProps, "indexes");
			swtPathOverride = toFile(pathProps, "swt");
		}
		catch (IOException e1) {
			// Ignore
			if (!SystemConf.Bool.IsDevelopmentVersion.get()) {
				e1.printStackTrace();
			}
		}

		/* This method call must be placed *before* any SWT access. This means
		 * it must be placed *before* the loadSettingsConf call further below,
		 * as the latter will access SWT via the SWT.CTRL and SWT.F8 constants
		 * to set the default value for the global hotkey. */
		configureSwtLibraryPath(swtPathOverride);

		// Load program configuration and preferences
		programConfFile = loadProgramConf(confPathOverride);
		settingsConfFile = loadSettingsConf(confPathOverride);
		
		// Migrate the legacy 'close to tray' checkbox to the close action setting
		if (SettingsConf.Bool.CloseToTray.get()) {
			if (SettingsConf.Int.CloseAction.get() == SettingsConf.CLOSE_ACTION_EXIT)
				SettingsConf.Int.CloseAction.set(SettingsConf.CLOSE_ACTION_TRAY);
			SettingsConf.Bool.CloseToTray.set(false);
		}
		
		// Google-style search: version 2.8 switches the old OR default to AND once
		if (!SettingsConf.Bool.AndOperatorApplied.get()) {
			SettingsConf.Bool.UseOrOperator.set(false);
			SettingsConf.Bool.AndOperatorApplied.set(true);
		}
		
		/*
		 * Determine the index folder. A custom index folder set in the
		 * preferences dialog takes precedence over the one in paths.txt. If the
		 * user changed the index folder and asked for the existing indexes to
		 * be moved, this is done now, before any index files are opened.
		 */
		defaultIndexParentDir = getIndexParentDir(IndexRegistry.indexPathOverride);
		String customIndexFolder = SettingsConf.Str.IndexFolder.get().trim();
		if (!customIndexFolder.isEmpty())
			IndexRegistry.indexPathOverride = Util.getCanonicalFile(customIndexFolder);
		File indexParentDir = getIndexParentDir(IndexRegistry.indexPathOverride);
		performPendingIndexMove(indexParentDir);
		
		// Update indexes in headless mode
		if (args.length >= 1 && args[0].equals("--update-indexes")) {
			loadIndexRegistryHeadless(indexParentDir);
			return;
		}

		// Check single instance
		if (ProgramConf.Bool.CheckSingleInstance.get() && !AppUtil.checkSingleInstance())
			return;
		
		checkMultipleDocFetcherJars();

		// Determine shell title
		String shellTitle;
		if (SystemConf.Bool.IsDevelopmentVersion.get())
			shellTitle = SystemConf.Str.ProgramName.get();
		else
			shellTitle = ProgramConf.Str.AppName.get();

		// Load index registry; create display and shell
		Display.setAppName(shellTitle); // must be called *before* the display is created
		Display display = new Display();
		AppUtil.setDisplay(display);
		shell = new Shell(display);
		loadIndexRegistry(shell, indexParentDir);

		// Load images
		LazyImageCache lazyImageCache = new LazyImageCache(
			display, AppUtil.getImageDir());
		lazyImageCache.setScalePercent(getIconScalePercent(display));
		Img.initialize(lazyImageCache);
		lazyImageCache.reportMissingFiles(
			shell, Img.class, Msg.missing_image_files.get());

		// Set shell icons, must be done *after* loading the images
		shell.setImages(new Image[] {
			Img.DOCFETCHER_16.get(),
			Img.DOCFETCHER_24.get(),
			Img.DOCFETCHER_32.get(),
			Img.DOCFETCHER_48.get(),
			Img.DOCFETCHER_64.get(),
			Img.DOCFETCHER_128.get()});

		// Set default uncaught exception handler
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			public void uncaughtException(Thread t, final Throwable e) {
				handleCrash(e);
			}
		});

		SettingsConf.ShellBounds.MainWindow.bind(shell);
		SettingsConf.Bool.MainShellMaximized.bindMaximized(shell);
		shell.setLayout(new FormLayout());
		shell.setText(shellTitle);

		configureWebView2DataDir();
		initCocoaMenu(display);
		initSystemTrayHider();
		initMenuBar();
		initSearchSettingsDialog();
		// Backups of PDF files removed after a merge or split, for the undo
		FileOperationHistory.getInstance().setBackupDir(new File(AppUtil.getAppDataDir(), "undo"));
		docToolBar = new DocToolBar(shell);
		initTwoPanelForm();
		InboxFolders.migrateLegacySetting();
		applyInboxFolders();
		initStatusBar();
		initHotkey();
		initGlobalKeys(display);
		
		// Show the window when another instance is launched
		AppUtil.evtActivateRequested.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				UtilGui.runAsyncExec(shell, new Runnable() {
					public void run() {
						activateMainWindow();
					}
				});
			}
		});
		
		final SearchQueue searchQueue = new SearchQueue(
			searchBar, fileTypePanel, indexPanel, resultPanel, statusBar);
		searchSettingsDialog.evtMatchFilenamesChanged.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				searchQueue.rerunLastSearch();
			}
		});

		FormDataFactory fdf = FormDataFactory.getInstance();
		fdf.bottom().left().right().applyTo(statusBar);
		fdf.reset().top().left().right().applyTo(docToolBar.getControl());
		fdf.top(docToolBar.getControl()).bottom(statusBar).applyTo(twoPanelForm);

		// Move focus to search text field
		searchBar.setFocus();

		// Try to show the manual in the embedded browser
		boolean showManualHint = true;
		if (SettingsConf.Bool.ShowManualOnStartup.get()
				&& SettingsConf.Bool.ShowPreviewPanel.get()) {
			File file = ManualLocator.getStartPage();
			if (file == null) {
				showManualHint = false;
				/*
				 * In the development version, don't show the following warning
				 * message, as the manual under dist/help may not have been
				 * generated yet.
				 */
				if (!SystemConf.Bool.IsDevelopmentVersion.get()) {
					String msg = Msg.file_not_found.get() + "\n" + SystemConf.Str.ProgramName.get() + "_Manual.html";
					AppUtil.showError(msg, true, true);
				}
			}
			else if (previewPanel.setHtmlFile(file)) {
				showManualHint = false;
			}
		}
		if (showManualHint) {
			String msg = Msg.press_f1_for_help.get();
			statusBar.getLeftPart().setContents(Img.HELP.get(), msg);
		}

		// Open Py4j Gateway Server
		if(ProgramConf.Bool.PythonApiEnabled.get())
            Py4jHandler.openGatewayServer();

		// MCP server for AI assistants, if enabled in the preferences
		updateMcpServer();
		SettingsConf.Bool.McpServerEnabled.evtChanged.add(new Event.Listener<Boolean>() {
			public void update(Boolean eventData) {
				updateMcpServer();
			}
		});
		SettingsConf.Int.McpServerPort.evtChanged.add(new Event.Listener<Integer>() {
			public void update(Integer eventData) {
				updateMcpServer();
			}
		});

		shell.addShellListener(new ShellAdapter() {
			public void shellClosed(final ShellEvent e) {
				handleShellClosed(e);
			}
		});

		shell.open();
		while (!shell.isDisposed()) {
			try {
				if (!display.readAndDispatch())
					display.sleep();
			}
			catch (Throwable t) {
				handleCrash(t);
			}
		}

		// Close Py4j Gateway Server
		if(ProgramConf.Bool.PythonApiEnabled.get())
			Py4jHandler.shutdownGatewayServer();
		if (mcpServer != null)
			mcpServer.stop();

		/*
		 * Do not set this to null; the index registry loading thread must be
		 * able to see that the display was disposed.
		 */
		display.dispose();
		saveSettingsConfFile();
		FileOperationHistory.getInstance().clear(); // Deletes the undo backups
	}
	
	@Nullable
	private static File toFile(@NotNull Properties props, @NotNull String key) {
		String value = props.getProperty(key);
		if (value == null)
			return null;
		Pattern homePattern = Pattern.compile("\\$\\{user\\.home}(?:[\\\\/](.*))?");
		Matcher m = homePattern.matcher(value);
		if (m.matches()) {
			if (m.group(1) == null || m.group(1).trim().isEmpty())
				return new File(Util.USER_HOME_PATH);
			return new File(Util.USER_HOME_PATH, m.group(1));
		}
		return Util.getCanonicalFile(value);
	}
	
	private static void handleCrash(@NotNull Throwable t) {
		// Filter out benign SWTException caused by race condition between widget
		// disposal and Windows accessibility queries. This can happen when
		// accessibility features (e.g. screen readers) try to query widget
		// properties after the widget has been disposed. Only filter if the
		// exception is coming from the accessibility callback path.
		// See bug #2395.
		if (t instanceof SWTException) {
			SWTException swtEx = (SWTException) t;
			if (swtEx.code == SWT.ERROR_WIDGET_DISPOSED ||
				(swtEx.getMessage() != null && swtEx.getMessage().contains("Widget is disposed"))) {
				// Check if this is from accessibility callbacks
				String stackTrace = Throwables.getStackTraceAsString(t);
				if (stackTrace.contains("org.eclipse.swt.accessibility.Accessible")) {
					// Silently ignore - this is a benign accessibility race condition
					return;
				}
			}

			// Filter out benign SWTException caused by GTK/SWT library bug during
			// widget disposal. This manifests as "g_object_get_qdata returned unexpected
			// index value" error on Linux systems with certain GTK themes. This is a
			// known issue in Eclipse SWT's GTK implementation that occurs during
			// shell/widget disposal and is outside DocFetcher's control.
			// See bug #2369.
			if (swtEx.getMessage() != null && swtEx.getMessage().contains("g_object_get_qdata")) {
				String stackTrace = Throwables.getStackTraceAsString(t);
				// Only filter if this is happening during widget disposal/deregistration
				if (stackTrace.contains("Display.removeWidget") ||
					stackTrace.contains("Widget.deregister") ||
					stackTrace.contains("Widget.releaseWidget")) {
					// Silently ignore - this is a benign GTK/SWT library bug
					return;
				}
			}
		}

		// Filter out benign NullPointerException caused by SWT bug on Windows 7 where
		// MenuItem.getMenu() returns null during popup menu rendering. This occurs in
		// newer SWT versions that added per-monitor DPI scaling (getMonitorZoom), which
		// fails on Windows 7 because getMenu() unexpectedly returns null for certain menu
		// items. The app functions normally despite this error. See bug #2450.
		if (t instanceof NullPointerException) {
			StackTraceElement[] frames = t.getStackTrace();
			if (frames.length > 0
					&& frames[0].getClassName().equals("org.eclipse.swt.widgets.MenuItem")
					&& frames[0].getMethodName().equals("getMonitorZoom")) {
				return;
			}
		}

		for (OutOfMemoryError e : Iterables.filter(Throwables.getCausalChain(t), OutOfMemoryError.class)) {
			UtilGui.showOutOfMemoryMessage(shell, e);
			return;
		}
		if (t instanceof MergePolicy.MergeException) {
			String msg = t.getMessage();
			msg += "\n\nPlease ensure no other programs are accessing or locking the index files.";
			AppUtil.showError(msg, true, false);
			return;
		}
		AppUtil.showStackTrace(t);
	}

	private static void saveSettingsConfFile() {
		/*
		 * Try to save the settings. This may not be possible, for example when
		 * the user has burned the program onto a CD-ROM.
		 */
		if (ProgramConf.Bool.SaveSettings.get() && settingsConfFile.canWrite()) {
			try {
				String comment = SettingsConf.loadHeaderComment();
				ConfLoader.save(settingsConfFile, SettingsConf.class, comment);
			}
			catch (IOException e) {
				boolean displayExists = (Display.getCurrent() != null);
				handleConfigurationIOException(e, settingsConfFile, displayExists);
			}
		}
	}
	
	/**
	 * Checks for multiple loaded DocFetcher jars. This should be called before
	 * creating the display.
	 */
	private static void checkMultipleDocFetcherJars() {
		if (SystemConf.Bool.IsDevelopmentVersion.get())
			return;
		if (!(AppUtil.isPortable() || Util.IS_WINDOWS))
			return;
		Pattern p = Pattern.compile("net\\.sourceforge\\.docfetcher.*\\.jar");
		List<File> dfJars = new LinkedList<File>();
		for (File jarFile : Util.listFiles(new File("lib")))
			if (p.matcher(jarFile.getName()).matches())
				dfJars.add(jarFile);
		if (dfJars.size() == 1)
			return;
		assert !dfJars.isEmpty();
		String msg = Msg.multiple_docfetcher_jars.format(Util.join("\n", dfJars));
		AppUtil.showErrorOnStart(msg, false);
	}

	private static void initGlobalKeys(@NotNull Display display) {
		/*
		 * This filter must be added to SWT.KeyDown rather than SWT.KeyUp,
		 * otherwise we won't be able to prevent the events from propagating
		 * further.
		 */
		display.addFilter(SWT.KeyDown, new Listener() {
			public void handleEvent(org.eclipse.swt.widgets.Event e) {
				// Disable global keys when the main shell is inactive
				if (Display.getCurrent().getActiveShell() != shell)
					return;

				e.doit = false;
				int m = e.stateMask;
				int k = e.keyCode;

				if (k == SWT.F1) {
					showManual();

					// Clear "Press F1" help message from status bar
					String msg = Msg.press_f1_for_help.get();
					StatusBarPart statusBarPart = statusBar.getLeftPart();
					if (msg.equals(statusBarPart.getText()))
						statusBarPart.setContents(null, "");
				}
				/* Do not swallow the Shift + F shortcut, otherwise it will be
				 * impossible to type a capital F in the search field. */
				else if (m == SWT.MOD1 && k == 'f') {
					searchBar.setFocus();
				}
				else if (m == SWT.MOD1 && k == 'q') {
					requestExit();
				}
				else if (k == SWT.F3) {
					cyclePaneMode();
				}
				else if (k == SWT.F4) {
					togglePreviewPanel();
				}
				else if (m == SWT.MOD1 && k == 'z') {
					undoFileOperation();
				}
				// Keys set in the preferences
				else if (isKey(SettingsConf.IntArray.InboxKey, m, k)) {
					goToInbox();
				}
				else if (isKey(SettingsConf.IntArray.SearchKey, m, k)) {
					goToSearch();
				}
				else if (isKey(SettingsConf.IntArray.FoldersKey, m, k)) {
					goToFolders();
				}
				else {
					e.doit = true;
				}
			}
		});
	}
	
	@NotNull
	private static File getIndexParentDir(@Nullable File pathOverride) {
		File indexParentDir;
		if (pathOverride != null && !pathOverride.isFile()) {
			pathOverride.mkdirs();
			indexParentDir = pathOverride;
		}
		else if (SystemConf.Bool.IsDevelopmentVersion.get()) {
			indexParentDir = new File("bin/indexes");
		}
		else {
			File appDataDir = AppUtil.getAppDataDir();
			if (SystemConf.Bool.IsPortable.get())
				indexParentDir = new File(appDataDir, "indexes");
			else
				indexParentDir = appDataDir;
		}
		indexParentDir.mkdirs();
		return indexParentDir;
	}

	/**
	 * If the user changed the index folder in the preferences dialog and opted
	 * to move the existing indexes, this moves all index folders from the old
	 * index folder into the given new one. Must be called before the index
	 * registry is loaded and before the display is created, since the moving
	 * will fail on Windows if any index files are open.
	 */
	private static void performPendingIndexMove(@NotNull File newIndexParentDir) {
		String from = SettingsConf.Str.PendingIndexMoveFrom.get().trim();
		if (from.isEmpty())
			return;
		SettingsConf.Str.PendingIndexMoveFrom.set(""); // Only try once
		
		File oldIndexParentDir = new File(from);
		if (!oldIndexParentDir.isDirectory())
			return;
		if (Util.isCanonicallyEqual(oldIndexParentDir, newIndexParentDir))
			return;
		newIndexParentDir.mkdirs();
		
		List<File> toMove = new ArrayList<File>();
		for (File file : Util.listFiles(oldIndexParentDir)) {
			// Index folders are recognized by their serialized index tree
			if (file.isDirectory() && new File(file, IndexRegistry.SER_FILENAME).isFile())
				toMove.add(file);
		}
		
		List<String> failed = new ArrayList<String>();
		for (File source : toMove) {
			File target = new File(newIndexParentDir, source.getName());
			try {
				if (target.exists())
					throw new IOException("Target already exists: " + target);
				moveFileOrDir(source, target);
			}
			catch (IOException e) {
				Util.printErr(e);
				failed.add(source.getPath());
			}
		}
		
		if (!failed.isEmpty()) {
			String msg = Msg.index_move_failed.format(Util.join("\n", failed));
			AppUtil.showErrorOnStart(msg, false);
		}
	}
	
	/**
	 * Moves the given file or directory to the given target. If a simple
	 * rename is not possible (e.g. because source and target are on different
	 * drives), the source is copied recursively and then deleted.
	 */
	private static void moveFileOrDir(@NotNull File source, @NotNull File target)
			throws IOException {
		java.nio.file.Path srcPath = source.toPath();
		final java.nio.file.Path dstPath = target.toPath();
		try {
			java.nio.file.Files.move(srcPath, dstPath, StandardCopyOption.ATOMIC_MOVE);
			return;
		}
		catch (IOException e) {
			// Fall through to copy + delete
		}
		if (source.isFile()) {
			java.nio.file.Files.copy(srcPath, dstPath);
		}
		else {
			final java.nio.file.Path srcRoot = srcPath;
			java.nio.file.Files.walkFileTree(srcRoot, new SimpleFileVisitor<java.nio.file.Path>() {
				public FileVisitResult preVisitDirectory(java.nio.file.Path dir, BasicFileAttributes attrs)
						throws IOException {
					java.nio.file.Files.createDirectories(dstPath.resolve(srcRoot.relativize(dir)));
					return FileVisitResult.CONTINUE;
				}
				public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attrs)
						throws IOException {
					java.nio.file.Files.copy(file, dstPath.resolve(srcRoot.relativize(file)));
					return FileVisitResult.CONTINUE;
				}
			});
		}
		Util.deleteRecursively(source);
	}

	private static void loadIndexRegistry(@NotNull final Shell mainShell, @NotNull File indexParentDir) {
		UtilGui.assertSwtThread();
		final Display display = mainShell.getDisplay();

		int cacheCapacity = ProgramConf.Int.UnpackCacheCapacity.get();
		int reporterCapacity = ProgramConf.Int.MaxLinesInProgressPanel.get();
		indexRegistry = new IndexRegistry(
			indexParentDir, cacheCapacity, reporterCapacity);
		final Daemon daemon = new Daemon(indexRegistry, getDaemonDir());
		IndexingQueue queue = indexRegistry.getQueue();

		queue.evtWorkerThreadTerminated.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				daemon.writeIndexesToFile();
			}
		});

		/*
		 * The indexing progress in the status bar follows the task queue:
		 * visible while tasks exist, with the overall progress of the current
		 * batch of tasks.
		 */
		queue.addListeners(new IndexingQueue.ExistingTasksHandler() {
			public void handleExistingTasks(List<Task> tasks) {
				synchronized (queuedTasks) {
					queuedTasks.addAll(tasks);
					batchTaskCount += tasks.size();
				}
			}
		}, new Event.Listener<Task>() {
			public void update(Task task) {
				synchronized (queuedTasks) {
					queuedTasks.add(task);
					batchTaskCount++;
				}
				scheduleIndexingStatusUpdate();
			}
		}, new Event.Listener<Task>() {
			public void update(Task task) {
				synchronized (queuedTasks) {
					if (queuedTasks.remove(task))
						batchFinishedCount++;
				}
				scheduleIndexingStatusUpdate();
			}
		});
		queue.evtProgress.add(new Event.Listener<Task>() {
			public void update(Task task) {
				scheduleIndexingStatusUpdate();
			}
		});
		queue.evtQueueEmpty.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				synchronized (queuedTasks) {
					queuedTasks.clear();
					batchTaskCount = 0;
					batchFinishedCount = 0;
				}
				refreshInboxTabs();
				scheduleIndexingStatusUpdate();
			}
		});

		new Thread(Application.class.getName() + " (load index registry)") {
			public void run() {
				try {
					final IndexLoadingProblems loadingProblems = indexRegistry.load(new Cancelable() {
						public boolean isCanceled() {
							return display.isDisposed();
						}
					});
					
					// Program may have been shut down while it was loading the indexes
					if (display.isDisposed())
						return;

					/*
					 * Install folder watches on the user's document folders.
					 *
					 * This should be done *after* the index registry is loaded:
					 * The index registry will try to install its own folder
					 * watch during loading, and if we set up this folder
					 * watcher before loading the registry, we might take up all
					 * the allowed watches, so that there's none left for the
					 * registry.
					 */
					folderWatcher = new FolderWatcher(indexRegistry);

					// Show error message when watch limit is reached
					folderWatcher.evtWatchLimitError.add(new Event.Listener<String>() {
						public void update(final String eventData) {
							UtilGui.runAsyncExec(mainShell, new Runnable() {
								public void run() {
									InfoDialog dialog = new InfoDialog(mainShell);
									dialog.setTitle(Msg.system_error.get());
									dialog.setImage(SWT.ICON_ERROR);
									dialog.setText(eventData);
									dialog.open();
								}
							});
						}
					});

					// Must be called *after* the indexes have been loaded
					daemon.enqueueUpdateTasks();
					
					// Offer to rebuild indexes that lack page counts
					if (SettingsConf.Bool.AskRebuildOutdatedIndexes.get())
						offerRebuildOfOutdatedIndexes(mainShell);
					
					/*
					 * Pick up changes made while the program was closed, even
					 * if the daemon isn't running. The updates run in the
					 * background via the indexing queue.
					 */
					if (SettingsConf.Bool.UpdateIndexesOnStartup.get())
						enqueueIndexUpdates(true);
					startDailyIndexUpdates();
					
					// Confirm deletion of obsolete files inside the index
					// folder
					if (ProgramConf.Bool.ReportObsoleteIndexFiles.get()
							&& !loadingProblems.getObsoleteFiles().isEmpty()) {
						UtilGui.runSyncExec(mainShell, new Runnable() {
							public void run() {
								reportObsoleteIndexFiles(
									mainShell,
									indexRegistry.getIndexParentDir(),
									loadingProblems.getObsoleteFiles());
							}
						});
					}

					// Show error messages if some indexes couldn't be loaded
					if (!loadingProblems.getCorruptedIndexes().isEmpty()) {
						StringBuilder msg = new StringBuilder(Msg.corrupted_indexes.get());
						for (CorruptedIndex index : loadingProblems.getCorruptedIndexes()) {
							msg.append("\n\n");
							String indexName = index.index.getRootFolder().getDisplayName();
							String errorMsg = index.ioException.getMessage();
							msg.append(Msg.index.format(indexName));
							msg.append("\n");
							msg.append(Msg.error.format(errorMsg));
						}
						AppUtil.showError(msg.toString(), true, false);
					}
					if (!loadingProblems.getOverflowIndexes().isEmpty()) {
						StringBuilder msg = new StringBuilder(Msg.folder_hierarchy_too_deep_on_loading.get());
						msg.append("\n");
						for (OverflowIndex index : loadingProblems.getOverflowIndexes()) {
							msg.append("\n");
							msg.append(index.file.getName());
						}
						AppUtil.showError(msg.toString(), true, false);
					}
				}
				catch (IOException e) {
					if (display.isDisposed())
						AppUtil.showStackTraceInOwnDisplay(e);
					else
						AppUtil.showStackTrace(e);
				}
				finally {
					UtilGui.runAsyncExec(mainShell, new Runnable() {
						public void run() {
							indexRegistryLoaded = true;
							if (searchSettingsDialog != null)
								searchSettingsDialog.setIndexRegistryLoaded();
						}
					});
				}
			}
		}.start();
	}
	
	/**
	 * Returns the folder in which the DocFetcher daemon looks for the file
	 * listing the indexed folders. The daemon ignores any custom index folder,
	 * so this is always the default location.
	 */
	@NotNull
	private static File getDaemonDir() {
		if (SystemConf.Bool.IsDevelopmentVersion.get())
			return new File("bin/indexes");
		File appDataDir = AppUtil.getAppDataDir();
		if (SystemConf.Bool.IsPortable.get())
			return new File(appDataDir, "indexes");
		return appDataDir;
	}
	
	/**
	 * Asks the user once per startup whether indexes created by an older
	 * program version (which lack page counts) should be rebuilt, and enqueues
	 * the rebuilds in the background if so. Must be called after the indexes
	 * have been loaded, from a non-GUI thread.
	 */
	private static void offerRebuildOfOutdatedIndexes(@NotNull final Shell mainShell) {
		final List<LuceneIndex> outdated = new ArrayList<LuceneIndex>();
		for (LuceneIndex index : indexRegistry.getIndexes()) {
			if (index.getFormatVersion() < TreeIndex.CURRENT_FORMAT_VERSION
					&& index.getCanonicalRootFile().exists())
				outdated.add(index);
		}
		if (outdated.isEmpty())
			return;
		
		final boolean[] rebuild = { false };
		UtilGui.runSyncExec(mainShell, new Runnable() {
			public void run() {
				List<String> names = new ArrayList<String>(outdated.size());
				for (LuceneIndex index : outdated)
					names.add(index.getRootFolder().getDisplayName());
				String msg = Msg.rebuild_outdated_indexes_msg.format(
					SystemConf.Str.ProgramName.get(), Util.join("\n", names));
				
				MultipleChoiceDialog<Integer> dialog = new MultipleChoiceDialog<Integer>(mainShell);
				dialog.setTitle(Msg.rebuild_outdated_indexes_title.get());
				dialog.setText(msg);
				dialog.addButton(Msg.rebuild_now.get(), 1);
				dialog.addButton(Msg.later.get(), 0);
				dialog.addButton(Msg.dont_ask_again.get(), -1);
				Integer answer = dialog.open();
				if (answer == null)
					return; // Dialog closed, ask again next time
				if (answer < 0)
					SettingsConf.Bool.AskRebuildOutdatedIndexes.set(false);
				rebuild[0] = answer > 0;
			}
		});
		
		if (!rebuild[0])
			return;
		IndexingQueue queue = indexRegistry.getQueue();
		for (LuceneIndex index : outdated)
			queue.addTask(index, IndexAction.REBUILD, true);
	}
	
	/** Updates the indexing progress in the status bar from the GUI thread. */
	private static void scheduleIndexingStatusUpdate() {
		/*
		 * Bug #3485598: The indexing status widget can be null at this point,
		 * e.g. when an indexing update issued by the daemon finishes before
		 * the GUI is fully initialized.
		 */
		if (indexingStatus == null)
			return;
		UtilGui.runAsyncExec(indexingStatus.getControl(), new Runnable() {
			public void run() {
				updateIndexingStatus();
			}
		});
	}
	
	/**
	 * Shows the overall progress of the queued indexing tasks in the status
	 * bar: finished tasks of the current batch count fully, running tasks
	 * with their percentage. Hides the indicator when the queue is empty.
	 */
	private static void updateIndexingStatus() {
		List<Task> tasks;
		int total;
		double done;
		synchronized (queuedTasks) {
			tasks = new ArrayList<Task>(queuedTasks);
			total = batchTaskCount;
			done = batchFinishedCount;
		}
		if (tasks.isEmpty()) {
			indexingStatus.setVisible(false);
			return;
		}
		StringBuilder tooltip = new StringBuilder();
		for (Task task : tasks) {
			int percent = task.getProgressPercent();
			if (percent > 0)
				done += percent / 100.0;
			if (tooltip.length() > 0)
				tooltip.append('\n');
			tooltip.append(task.getLuceneIndex().getRootFolder().getDisplayName());
			tooltip.append(": ").append(getTaskStatusText(task));
		}
		int percent = total > 0 ? (int) Math.min(99, done * 100 / total) : -1;
		String text = percent >= 0
			? Msg.indexing_progress.format(percent)
			: Msg.indexing.get();
		indexingStatus.setContents(Img.INDEXING.get(), text);
		indexingStatus.setProgress(percent);
		tooltip.append("\n\n").append(Msg.indexing_status_tooltip.get());
		indexingStatus.setToolTipText(tooltip.toString());
		indexingStatus.setVisible(true);
	}
	
	/** Returns a short description of the state of an indexing task. */
	@NotNull
	public static String getTaskStatusText(@NotNull Task task) {
		if (task.is(TaskState.NOT_READY))
			return Msg.index_status_waiting.get();
		if (task.is(TaskState.READY))
			return Msg.index_status_queued.get();
		int percent = task.getProgressPercent();
		String percentStr = percent >= 0 ? percent + " %" : "";
		if (task.is(IndexAction.REBUILD))
			return Msg.index_status_rebuilding.format(percentStr).trim();
		if (task.is(IndexAction.CREATE))
			return Msg.index_status_creating.format(percentStr).trim();
		return Msg.index_status_updating.format(percentStr).trim();
	}
	
	/**
	 * Enqueues update tasks for all indexes whose root folder is available;
	 * with {@code watchedOnly}, only for indexes whose folders are watched
	 * for changes. Must be called after the indexes have been loaded, from a
	 * non-GUI thread.
	 */
	private static void enqueueIndexUpdates(boolean watchedOnly) {
		IndexingQueue queue = indexRegistry.getQueue();
		for (LuceneIndex index : indexRegistry.getIndexes()) {
			if (watchedOnly && !index.isWatchFolders())
				continue;
			if (!index.getCanonicalRootFile().exists())
				continue;
			queue.addTask(index, IndexAction.UPDATE); // Rejected if already queued
		}
	}
	
	/**
	 * Updates all indexes once a day while the program is running, so that
	 * files deleted or changed outside the watched folders don't linger in
	 * the indexes. The startup update counts as the first run.
	 */
	private static void startDailyIndexUpdates() {
		Thread thread = new Thread(Application.class.getName() + " (daily index updates)") {
			public void run() {
				long lastUpdate = System.currentTimeMillis();
				while (true) {
					try {
						Thread.sleep(60 * 60 * 1000); // Check hourly
					}
					catch (InterruptedException e) {
						return;
					}
					if (!SettingsConf.Bool.DailyIndexUpdate.get())
						continue;
					long now = System.currentTimeMillis();
					if (now - lastUpdate < 24L * 60 * 60 * 1000)
						continue;
					lastUpdate = now;
					enqueueIndexUpdates(false);
				}
			}
		};
		thread.setDaemon(true);
		thread.start();
	}
	
	private static void loadIndexRegistryHeadless(@NotNull File indexParentDir) {
		int cacheCapacity = ProgramConf.Int.UnpackCacheCapacity.get();
		int reporterCapacity = ProgramConf.Int.MaxLinesInProgressPanel.get();
		indexRegistry = new IndexRegistry(
			indexParentDir, cacheCapacity, reporterCapacity);
		
		try {
			indexRegistry.load(Cancelable.nullCancelable);
			final IndexingQueue queue = indexRegistry.getQueue();
			
			queue.evtQueueEmpty.add(new Event.Listener<Void>() {
				public void update(Void eventData) {
					indexRegistry.getSearcher().shutdown();
					queue.shutdown(new CancelHandler() {
						public CancelAction cancel() {
							return CancelAction.KEEP;
						}
					});
				}
			});
			
			for (LuceneIndex index : indexRegistry.getIndexes())
				queue.addTask(index, IndexAction.UPDATE);
		}
		catch (IOException e) {
			Util.printErr(e);
		}
	}
	
	private static void reportObsoleteIndexFiles(	@NotNull Shell mainShell,
	                                             	@NotNull File indexDir,
													@NotNull List<File> filesToDelete) {
		ListConfirmDialog dialog = new ListConfirmDialog(mainShell, SWT.ICON_INFORMATION);
		dialog.setTitle(Msg.confirm_operation.get());
		dialog.setText(Msg.delete_obsolete_index_files.format(indexDir.getPath()));
		dialog.setButtonLabels(Msg.delete_bt.get(), Msg.keep.get());
		
		filesToDelete = new ArrayList<File>(filesToDelete);
		Collections.sort(filesToDelete, new Comparator<File>() {
			public int compare(File o1, File o2) {
				boolean d1 = o1.isDirectory();
				boolean d2 = o2.isDirectory();
				if (d1 && !d2)
					return -1;
				if (!d1 && d2)
					return 1;
				return AlphanumComparator.ignoreCaseInstance.compare(o1.getName(), o2.getName());
			}
		});
		
		for (File file : filesToDelete) {
			Image img = (file.isDirectory() ? Img.FOLDER : Img.FILE).get();
			dialog.addItem(img, file.getName());
		}
		
		dialog.evtLinkClicked.add(new Event.Listener<String>() {
			public void update(String eventData) {
				UtilGui.launch(eventData);
			}
		});
		
		if (dialog.open()) {
			for (File file : filesToDelete) {
				try {
					Util.deleteRecursively(file);
				}
				catch (IOException e) {
					Util.printErr(e);
				}
			}
		}
	}

	/**
	 * Handles IOException during configuration file loading/creation with.
	 */
	private static void handleConfigurationIOException(@NotNull IOException e, @NotNull File confFile, boolean displayExists) {
		// Check for permission errors by testing if the directory exists but is
		// not writable. Use a reliable write test instead of canWrite() which
		// is unreliable on Windows with UAC-protected directories.
		File parentDir = Util.getParentFile(confFile);
		boolean isPermissionError = parentDir != null &&
			parentDir.exists() &&
			!AppUtil.canWriteToDirectory(parentDir);

		if (isPermissionError && SystemConf.Bool.IsPortable.get()) {
			String programDir = new File(Util.USER_DIR_PATH).getAbsolutePath();
			StringBuilder msg = new StringBuilder();
			msg.append("The portable version of " + SystemConf.Str.ProgramName.get() + " cannot write to the following location:\n\n");
			msg.append(confFile.getAbsolutePath() + "\n\n");
			msg.append("This is likely because the program is installed in a protected system directory:\n\n");
			msg.append(programDir + "\n\n");
			msg.append("Possible solutions:\n\n");
			msg.append("1. Use the official installer instead of the portable version. The installer will place files in the appropriate user directories. (Recommended)\n\n");
			msg.append("2. Move the portable version to a writable location such as your Desktop, Documents folder, or a folder in your user directory.\n\n");
			msg.append("3. Run the program as Administrator. (Not recommended for security reasons.)\n\n");
			msg.append("Technical details: " + e.getClass().getSimpleName() + ": " + e.getMessage());

			AppUtil.showErrorOnStart(msg.toString(), false);
		}
		else {
			// Bug #2410: Use the Display state passed from caller to choose appropriate error display method
			if (displayExists) {
				// Display is active, use it to show the error
				AppUtil.showStackTrace(e);
			} else {
				// No display (called after dispose), create own display for error
				AppUtil.showStackTraceInOwnDisplay(e);
			}
		}
	}

	private static File loadProgramConf(@Nullable File pathOverride) {
		AppUtil.checkConstInitialized();
		AppUtil.ensureNoDisplay();

		File confFile;
		if (SystemConf.Bool.IsDevelopmentVersion.get()) {
			confFile = new File("dist/program-conf.txt");
		}
		else if (pathOverride != null && !pathOverride.isFile()) {
			pathOverride.mkdirs();
			confFile = new File(pathOverride, "program-conf.txt");
		}
		else {
			File appDataDir = AppUtil.getAppDataDir();
			confFile = new File(appDataDir, "conf/program-conf.txt");
		}

		try {
			List<Loadable> notLoaded = ConfLoader.load(
				confFile, ProgramConf.class, false);
			if (!notLoaded.isEmpty()) {
				if (SystemConf.Bool.IsDevelopmentVersion.get()) {
					List<String> entryNames = new ArrayList<>(notLoaded.size());
					for (Loadable entry : notLoaded)
						entryNames.add("  " + entry.name());
					String msg = Msg.entries_missing.format(confFile.getName());
					msg += "\n" + Joiner.on("\n").join(entryNames);
					AppUtil.showErrorOnStart(msg, false);
				}
				else {
					regenerateConfFile(confFile, ProgramConf.class);
				}
			}
		}
		catch (FileNotFoundException e) {
			regenerateConfFile(confFile, ProgramConf.class);
		}
		catch (IOException e) {
			handleConfigurationIOException(e, confFile, false); // No display yet
		}
		return confFile;
	}

	/**
	 * Regenerates the given conf file from an internal template, replacing
	 * values in the template with values loaded from the given container class
	 * if possible.
	 * <p>
	 * Note: In case of the non-portable version, the program-conf.txt file will
	 * be missing when the program is started for the first time.
	 */
	private static void regenerateConfFile(	File confFile,
											Class<?> containerClass) {
		Pattern linePat = Pattern.compile("(\\w+)\\s*=.*");
		try {
			URL url = Resources.getResource(Main.class, confFile.getName());
			LineReader lineReader = new LineReader(
				new StringReader(Resources.toString(url, Charsets.UTF_8))
			);
			List<String> outLines = new ArrayList<>();
			while (true) {
				String line = lineReader.readLine();
				if (line == null) {
					break;
				}
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					outLines.add(line);
					continue;
				}
				Matcher mat = linePat.matcher(line);
				if (!mat.matches()) {
					outLines.add(line);
					continue;
				}
				String key = mat.group(1);
				String value = ConfLoader.getRawValue(key, containerClass);
				if (value != null) {
					if (value.trim().isEmpty()) {
						outLines.add(String.format("%s =", key));
					}
					else {
						outLines.add(String.format("%s = %s", key, value));
					}
				}
				else {
					outLines.add(line);
				}
			}
			String outStr = Util.join(Util.LS, outLines);
			Util.getParentFile(confFile).mkdirs();
			Files.write(outStr, confFile, Charsets.UTF_8);
		}
		catch (IOException e1) {
			handleConfigurationIOException(e1, confFile, false);
		}
		catch (Exception e1) {
			AppUtil.showStackTraceInOwnDisplay(e1);
		}
	}
	
	private static File loadSettingsConf(@Nullable File pathOverride) {
		AppUtil.checkConstInitialized();
		AppUtil.ensureNoDisplay();

		/*
		 * The settings are kept in the folder the system has for the settings
		 * of the user, also for the portable version, so that they survive when
		 * a new version is unpacked over the program folder. Settings of
		 * earlier versions are copied from their old place once.
		 */
		File confFile;
		if (SystemConf.Bool.IsDevelopmentVersion.get()) {
			confFile = new File("bin/settings-conf.txt");
		}
		else if (pathOverride != null && !pathOverride.isFile()) {
			pathOverride.mkdirs();
			confFile = SettingsLocation.getFile(pathOverride,
				new File(pathOverride, SettingsLocation.OLD_FILE_NAME));
		}
		else {
			File oldFile = new File(AppUtil.getAppDataDir(), "conf/" + SettingsLocation.OLD_FILE_NAME);
			confFile = SettingsLocation.getFile(SettingsLocation.getDefaultDir(), oldFile);
			confFile.getParentFile().mkdirs();
		}

		try {
			ConfLoader.load(confFile, SettingsConf.class, true);
		}
		catch (IOException e) {
			handleConfigurationIOException(e, confFile, false); // No display yet
		}
		return confFile;
	}

	/**
	 * Creates the dialog holding the search filters (document types, search
	 * scope). The dialog is created at startup, but only shown on demand, since
	 * the filter panels must exist for the search queue to work.
	 */
	private static void initSearchSettingsDialog() {
		searchSettingsDialog = new SearchSettingsDialog(
			shell, indexRegistry, indexRegistryLoaded);
		fileTypePanel = searchSettingsDialog.getFileTypePanel();
		indexPanel = searchSettingsDialog.getIndexPanel();
		
		indexPanel.evtIndexingDialogMinimized.add(new Event.Listener<Rectangle>() {
			public void update(Rectangle eventData) {
				moveIndexingDialogToStatusBar(eventData);
			}
		});
		indexPanel.evtListDocuments.add(new Event.Listener<Set<String>>() {
			public void update(Set<String> eventData) {
				// Close the dialog so that the listed documents are visible
				searchSettingsDialog.close();
			}
		});
	}

	private static Control createRightTopPanel(Composite parent) {
		/*
		 * The folder browser is either a tab of the main tab folder or a
		 * panel of its own next to it, so that documents can be dragged from
		 * another tab into a folder.
		 */
		folderSash = new SashForm(parent, SWT.HORIZONTAL);
		folderSash.setSashWidth(sashWidth);
		UtilGui.paintSashHandles(folderSash);
		mainTabFolder = new TabFolder(folderSash, SWT.TOP);
		initInboxTabDrop();
		// Place of the Folders tab's browser in the three-pane mode
		firstFolderHolder = new Composite(folderSash, SWT.NONE);
		firstFolderHolder.setLayout(new FillLayout());
		folderPanelHolder = new Composite(folderSash, SWT.NONE);
		folderPanelHolder.setLayout(new FillLayout());
		
		searchTab = new TabItem(mainTabFolder, SWT.NONE);
		// The tab titles show the keys of the preferences
		Event.Listener<int[]> keyListener = new Event.Listener<int[]>() {
			public void update(int[] eventData) {
				if (!mainTabFolder.isDisposed())
					updateTabTitles();
			}
		};
		SettingsConf.IntArray.SearchKey.evtChanged.add(keyListener);
		SettingsConf.IntArray.InboxKey.evtChanged.add(keyListener);
		SettingsConf.IntArray.FoldersKey.evtChanged.add(keyListener);
		searchTab.setText(withKey(Msg.search_tab.get(), SettingsConf.IntArray.SearchKey));
		searchTab.setControl(createSearchPanel(mainTabFolder));
		mainTabFolder.setSelection(searchTab);
		mainTabFolder.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				onTabSelected();
			}
		});
		applyPaneMode();
		folderSash.addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent e) {
				saveFolderSashWeights();
			}
		});
		// The toolbar buttons and the View menu change the setting
		SettingsConf.Int.PaneMode.evtChanged.add(new Event.Listener<Integer>() {
			public void update(Integer eventData) {
				UtilGui.runAsyncExec(folderSash, new Runnable() {
					public void run() {
						applyPaneMode();
					}
				});
			}
		});
		return folderSash;
	}

	/**
	 * The number of panes next to the document view: 1 = the tabs (Folders,
	 * inboxes, Search), 2 = plus a folder browser, 3 = the inbox and search
	 * tabs, the Folders tab's browser as a pane of its own and the other
	 * folder browser. Settings of older versions only knew whether the folder
	 * panel was shown.
	 */
	static int getPaneMode() {
		int mode = SettingsConf.Int.PaneMode.get();
		if (mode < 1 || mode > 3)
			mode = SettingsConf.Bool.ShowFolderPanel.get() ? 2 : 1;
		return mode;
	}

	/** Switches to the next pane mode: 1 -> 2 -> 3 -> 1 (F3). */
	private static void cyclePaneMode() {
		SettingsConf.Int.PaneMode.set(getPaneMode() % 3 + 1);
	}

	/** Stores the widths of the panes of the current mode. */
	private static void saveFolderSashWeights() {
		if (folderSash.getMaximizedControl() != null || appliedPaneMode < 2)
			return;
		int[] weights = folderSash.getWeights(); // Tabs, first folder place, folder panel
		if (appliedPaneMode == 3)
			SettingsConf.IntArray.FolderSashWeights3.set(weights);
		else
			SettingsConf.IntArray.FolderSashWeights.set(new int[] { weights[0], weights[2] });
	}

	private static int appliedPaneMode = 0;
	
	/**
	 * Arranges the panes for the pane mode (see {@link #getPaneMode()}). The
	 * browser of the Folders tab moves between the tab and its own pane, so
	 * it keeps its folder and its sync with the folder panel.
	 */
	private static void applyPaneMode() {
		saveFolderSashWeights(); // Of the mode that is left
		int mode = getPaneMode();
		appliedPaneMode = mode;
		SettingsConf.Bool.ShowFolderPanel.set(mode >= 2);
		if (folderTabPanel == null)
			folderTabPanel = createFolderPanel(mainTabFolder, SettingsConf.Str.LastBrowsedFolder);
		if (mode == 3) {
			if (foldersTab != null) {
				foldersTab.dispose(); // Doesn't dispose the browser
				foldersTab = null;
			}
			moveFolderTabPanel(firstFolderHolder);
			// The tab folder hides the controls of the tabs that aren't in front
			folderTabPanel.getControl().setVisible(true);
			folderTabPanel.setShowLabel(true);
		}
		else {
			// The 'Folders' tab comes after the inbox tabs
			if (foldersTab != null && mainTabFolder.indexOf(foldersTab) != inboxTabs.size()) {
				foldersTab.dispose();
				foldersTab = null;
			}
			if (foldersTab == null) {
				moveFolderTabPanel(mainTabFolder);
				foldersTab = new TabItem(mainTabFolder, SWT.NONE, inboxTabs.size());
				foldersTab.setText(withKey(Msg.folders_tab.get(), SettingsConf.IntArray.FoldersKey));
				foldersTab.setControl(folderTabPanel.getControl());
			}
			folderTabPanel.setShowLabel(false);
		}
		applyFolderPanelMode(mode >= 2);
		firstFolderHolder.setVisible(mode == 3);
		folderSash.setMaximizedControl(mode == 1 ? mainTabFolder : null);
		if (mode == 3) {
			folderSash.setWeights(SettingsConf.IntArray.FolderSashWeights3.get());
		}
		else if (mode == 2) {
			int[] w = SettingsConf.IntArray.FolderSashWeights.get();
			folderSash.setWeights(new int[] { w[0], 1, w[1] });
		}
		firstFolderHolder.layout();
		folderSash.layout(true, true);
		DocToolBar.selectPaneMode(docToolBar, mode);
	}

	/**
	 * Puts the browser of the Folders tab into the given parent. Where
	 * controls can't be moved, it is created anew there, with a new sync.
	 */
	private static void moveFolderTabPanel(@NotNull Composite parent) {
		Control control = folderTabPanel.getControl();
		if (control.getParent() == parent)
			return;
		if (control.isReparentable()) {
			control.setParent(parent);
			return;
		}
		File folder = folderTabPanel.getFolder();
		if (folderSync != null)
			folderSync.dispose();
		control.dispose();
		folderTabPanel = createFolderPanel(parent, SettingsConf.Str.LastBrowsedFolder);
		folderTabPanel.setFolder(folder);
		if (folderPanel != null)
			folderSync = new FolderSync(folderTabPanel, folderPanel);
	}

	/**
	 * Shows or hides the folder browser next to the tabs. Both browsers work
	 * the same way, so that documents can be dragged from one folder to
	 * another; the sync couples them.
	 */
	private static void applyFolderPanelMode(boolean showPanel) {
		if (showPanel && folderPanel == null) {
			folderPanel = createFolderPanel(
				folderPanelHolder, SettingsConf.Str.LastBrowsedFolderPanel);
			folderPanel.setShowLabel(true);
			folderPanelHolder.layout();
			// The 'Folders' tab shows the parent of this browser (toolbar toggle)
			folderSync = new FolderSync(folderTabPanel, folderPanel);
		}
		else if (!showPanel && folderPanel != null) {
			if (folderSync != null)
				folderSync.dispose();
			folderSync = null;
			folderPanel.getControl().dispose();
			folderPanel = null;
		}
	}
	
	/**
	 * Files dropped on the tab of an inbox are moved into its folder, e.g.
	 * from the folder browser, without bringing the tab to the front first.
	 */
	private static void initInboxTabDrop() {
		DropTarget dropTarget = new DropTarget(mainTabFolder, DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_DEFAULT);
		dropTarget.setTransfer(new Transfer[] { FileTransfer.getInstance() });
		dropTarget.addDropListener(new DropTargetAdapter() {
			public void dragEnter(DropTargetEvent event) {
				updateFeedback(event);
			}
			public void dragOver(DropTargetEvent event) {
				updateFeedback(event);
			}
			public void dragOperationChanged(DropTargetEvent event) {
				updateFeedback(event);
			}
			public void drop(DropTargetEvent event) {
				InboxTab inboxTab = getInboxTabAt(event);
				if (inboxTab == null || !(event.data instanceof String[]))
					return;
				List<File> files = new ArrayList<File>();
				for (String path : (String[]) event.data)
					files.add(new File(path));
				inboxTab.panel.getResultPanel().dropFiles(files, inboxTab.entry.getFolder(),
					event.detail == DND.DROP_COPY);
				inboxTab.panel.refresh();
			}
			private void updateFeedback(DropTargetEvent event) {
				if (getInboxTabAt(event) == null)
					event.detail = DND.DROP_NONE;
				else if (event.detail == DND.DROP_NONE || event.detail == DND.DROP_DEFAULT)
					event.detail = DND.DROP_MOVE;
			}
		});
	}

	/** The inbox tab under the cursor of a drag, or null. */
	@Nullable
	private static InboxTab getInboxTabAt(@NotNull DropTargetEvent event) {
		TabItem item = mainTabFolder.getItem(mainTabFolder.toControl(event.x, event.y));
		for (InboxTab inboxTab : inboxTabs)
			if (inboxTab.tab == item)
				return inboxTab;
		return null;
	}


	/** Shows or hides the preview pane, which has no toggle of its own. */
	private static void togglePreviewPanel() {
		if (twoPanelForm == null)
			return;
		twoPanelForm.setSecondSubControlVisible(
			!twoPanelForm.isSecondSubControlVisible());
	}
	
	/** The folder browser a document should be shown in. */
	@Nullable
	private static FolderPanel getVisibleFolderPanel() {
		return folderPanel != null ? folderPanel : folderTabPanel;
	}
	
	/** An inbox tab of the main window. */
	private static final class InboxTab {
		final InboxFolders.Entry entry;
		final TabItem tab;
		final InboxPanel panel;

		InboxTab(@NotNull InboxFolders.Entry entry, @NotNull TabItem tab, @NotNull InboxPanel panel) {
			this.entry = entry;
			this.tab = tab;
			this.panel = panel;
		}
	}

	@Nullable
	private static InboxTab getSelectedInboxTab() {
		TabItem[] selection = mainTabFolder.getSelection();
		if (selection.length == 0)
			return null;
		for (InboxTab inboxTab : inboxTabs)
			if (inboxTab.tab == selection[0])
				return inboxTab;
		return null;
	}

	/**
	 * Shows the path of the selected document in the status bar, or the
	 * number of selected documents if there are several.
	 */
	private static void showSelectedPath(@NotNull List<ResultDocument> selection) {
		if (statusBar == null)
			return;
		String msg;
		if (selection.isEmpty())
			msg = "";
		else if (selection.size() > 1)
			msg = Msg.num_sel_results.format(selection.size());
		else
			msg = selection.get(0).getPath().getPath();
		statusBar.getLeftPart().setContents(msg.isEmpty() ? null : Img.INFO.get(), msg);
	}
	
	/** Reloads the file lists of all inbox tabs. Safe to call from any thread. */
	private static void refreshInboxTabs() {
		for (InboxTab inboxTab : inboxTabs)
			inboxTab.panel.refresh();
	}

	/**
	 * Creates, updates or removes the inbox tabs according to the configured
	 * inbox folders, and makes sure the folders are indexed. The inbox tabs
	 * are placed before the search tab, in the configured order.
	 */
	private static void applyInboxFolders() {
		List<InboxFolders.Entry> entries = InboxFolders.load();
		List<InboxFolders.Entry> current = new ArrayList<InboxFolders.Entry>(inboxTabs.size());
		for (InboxTab inboxTab : inboxTabs)
			current.add(inboxTab.entry);
		if (!entries.equals(current)) {
			/*
			 * Rebuild all tabs. Select the tab of the previously selected
			 * folder if it still exists, otherwise the first inbox tab if an
			 * inbox tab was selected before (or on startup).
			 */
			InboxTab selected = getSelectedInboxTab();
			boolean selectInbox = inboxTabs.isEmpty() || selected != null;
			File selectedFolder = selected == null ? null : selected.entry.getFolder();
			for (InboxTab inboxTab : inboxTabs) {
				inboxTab.tab.dispose();
				inboxTab.panel.getControl().dispose();
			}
			inboxTabs.clear();
			InboxTab toSelect = null;
			for (InboxFolders.Entry entry : entries) {
				InboxTab inboxTab = createInboxTab(entry, inboxTabs.size());
				inboxTabs.add(inboxTab);
				if (toSelect == null && (selectedFolder == null
						|| Util.isCanonicallyEqual(selectedFolder, entry.getFolder())))
					toSelect = inboxTab;
			}
			if (toSelect == null && !inboxTabs.isEmpty())
				toSelect = inboxTabs.get(0);
			if (selectInbox && toSelect != null)
				mainTabFolder.setSelection(toSelect.tab);
		}
		for (InboxTab inboxTab : inboxTabs) {
			inboxTab.panel.setFolder(inboxTab.entry.getFolder());
			ensureIndexed(inboxTab.entry.getFolder());
		}
		
		// The folders tab belongs after the inbox tabs
		if (foldersTab != null && mainTabFolder.indexOf(foldersTab) != inboxTabs.size())
			applyPaneMode();
	}

	@NotNull
	private static InboxTab createInboxTab(	@NotNull InboxFolders.Entry entry,
											int index) {
		InboxPanel inboxPanel = new InboxPanel(mainTabFolder, indexRegistry);
		TabItem tab = new TabItem(mainTabFolder, SWT.NONE, index);
		tab.setText(withKey(entry.getTabName(), SettingsConf.IntArray.InboxKey));
		tab.setControl(inboxPanel.getControl());

		final ResultPanel inboxResults = inboxPanel.getResultPanel();
		inboxResults.evtSelection.add(new Event.Listener<List<ResultDocument>>() {
			public void update(List<ResultDocument> eventData) {
				if (!eventData.isEmpty())
					previewPanel.setPreview(eventData.get(0));
				showSelectedPath(eventData);
			}
		});
		inboxResults.evtHideInSystemTray.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				systemTrayHider.hide();
			}
		});
		inboxResults.evtDocumentsChanged.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				if (inboxResults.getSelection().isEmpty())
					previewPanel.clear();
				else
					previewPanel.refresh();
			}
		});
		return new InboxTab(entry, tab, inboxPanel);
	}

	/**
	 * Creates an index for the given folder if no index covers it yet. Folders
	 * whose indexing has already been requested are skipped, since the index
	 * appears in the registry only after indexing has finished.
	 */
	private static void ensureIndexed(@NotNull File folder) {
		for (LuceneIndex index : indexRegistry.getIndexes()) {
			File root = index.getCanonicalRootFile();
			if (Util.isCanonicallyEqual(root, folder) || Util.contains(root, folder))
				return;
		}
		if (!folder.isDirectory() || inboxIndexRequests.contains(folder))
			return;
		if (indexPanel.createIndexForFolder(folder))
			inboxIndexRequests.add(folder);
	}
	
	/**
	 * Undoes the last renaming or moving of files and refreshes the views
	 * that may show them.
	 */
	private static void undoFileOperation() {
		if (!ResultPanel.undoLastFileOperation(indexRegistry))
			return;
		refreshInboxTabs();
		refreshFolderPanels();
	}
	
	/**
	 * Returns the size of the icons in percent: the configured value, or, if
	 * that is 0, a value derived from the resolution of the screen, so that
	 * the icons don't look tiny on a high resolution display.
	 */
	private static int getIconScalePercent(@NotNull Display display) {
		int configured = SettingsConf.Int.IconScalePercent.get();
		if (configured > 0)
			return configured;
		int dpi;
		try {
			dpi = display.getDPI().x;
		}
		catch (RuntimeException e) {
			return 100;
		}
		if (dpi >= 168)
			return 200;
		if (dpi >= 132)
			return 150;
		if (dpi >= 120)
			return 125;
		return 100;
	}
	
	/** Reloads the listings of both folder browsers. */
	private static void refreshFolderPanels() {
		if (folderPanel != null)
			folderPanel.refresh();
		if (folderTabPanel != null)
			folderTabPanel.refresh();
	}
	
	/**
	 * Shows the given file in the folder browser: brings the browser up (as
	 * a panel, or by switching to its tab) and selects the file there.
	 */
	private static void showInFolderView(@NotNull File file) {
		FolderPanel panel = getVisibleFolderPanel();
		if (panel == null)
			return;
		if (panel == folderTabPanel && foldersTab != null) {
			// Only the tab shows a browser; bring it to the front
			mainTabFolder.setSelection(foldersTab);
		}
		panel.showFile(file);
	}
	
	/**
	 * With the sync button on, shows the document selected in the search
	 * results in the rightmost folder browser: the folder panel, whose left
	 * neighbour follows through the sync; with one pane the Folders tab, in
	 * the background (the tab in front stays). Not for the inboxes: they are
	 * the source for filing documents, and the browsers keep the target.
	 */
	private static void followInFolderPanel(@NotNull List<ResultDocument> selection) {
		if (SettingsConf.Bool.SyncFolderPanels.get() && selection.size() == 1)
			showInRightmostFolderPanel(selection.get(0));
	}

	/** The sync of the sync button, for one document. */
	private static void showInRightmostFolderPanel(@NotNull ResultDocument doc) {
		if (!doc.isPlainFileOnDisk())
			return;
		FolderPanel rightmost = folderPanel != null ? folderPanel : folderTabPanel;
		if (rightmost != null)
			rightmost.showFile(doc.getPath().getCanonicalFile());
	}

	/** The result table of the tab in front becomes the toolbar's; the Search tab focuses its field. */
	private static void onTabSelected() {
		InboxTab selected = getSelectedInboxTab();
		if (selected != null) {
			selected.panel.refresh();
			selected.panel.getResultPanel().activate();
		}
		else if (folderTabPanel != null
				&& mainTabFolder.getSelection().length > 0
				&& mainTabFolder.getSelection()[0] == foldersTab) {
			folderTabPanel.refresh();
			folderTabPanel.getResultPanel().activate();
		}
		else {
			resultPanel.activate();
			// Ready for typing a query
			if (mainTabFolder.getSelection().length > 0 && mainTabFolder.getSelection()[0] == searchTab)
				searchBar.setFocus();
		}
	}

	/** Returns the tab title with the key of the setting, e.g. "Search (F7)". */
	static String withKey(@NotNull String title, @NotNull SettingsConf.IntArray key) {
		int[] k = key.get();
		if (k.length != 2 || k[1] == 0)
			return title;
		return title + " (" + UtilGui.toString(k) + ")";
	}

	/** Shows the keys of the preferences in the tab titles. */
	private static void updateTabTitles() {
		searchTab.setText(withKey(Msg.search_tab.get(), SettingsConf.IntArray.SearchKey));
		if (foldersTab != null && !foldersTab.isDisposed())
			foldersTab.setText(withKey(Msg.folders_tab.get(), SettingsConf.IntArray.FoldersKey));
		for (InboxTab inboxTab : inboxTabs)
			inboxTab.tab.setText(withKey(inboxTab.entry.getTabName(), SettingsConf.IntArray.InboxKey));
	}

	/** Returns true if the key event matches the key combination of the setting. */
	static boolean isKey(@NotNull SettingsConf.IntArray setting, int stateMask, int keyCode) {
		int[] key = setting.get();
		if (key.length != 2 || key[1] == 0)
			return false;
		int modifiers = stateMask & (SWT.CTRL | SWT.ALT | SWT.SHIFT | SWT.COMMAND);
		return modifiers == key[0] && (keyCode == key[1] || Character.toUpperCase(keyCode) == key[1]);
	}

	/** Shows the first inbox tab, or the next one if an inbox tab is in front. */
	private static void goToInbox() {
		if (inboxTabs.isEmpty())
			return;
		InboxTab selected = getSelectedInboxTab();
		int next = selected == null ? 0 : (inboxTabs.indexOf(selected) + 1) % inboxTabs.size();
		InboxTab tab = inboxTabs.get(next);
		mainTabFolder.setSelection(tab.tab);
		onTabSelected();
		tab.panel.getResultPanel().getControl().setFocus();
	}

	/** Shows the Search tab with the cursor in the search field. */
	private static void goToSearch() {
		mainTabFolder.setSelection(searchTab);
		onTabSelected();
		searchBar.setFocus();
	}

	/** Focuses the folder browser: the Folders tab, or the middle pane with three panes. */
	private static void goToFolders() {
		if (folderTabPanel == null)
			return;
		if (foldersTab != null && !foldersTab.isDisposed()) {
			mainTabFolder.setSelection(foldersTab);
			onTabSelected();
		}
		else {
			folderTabPanel.getResultPanel().activate();
		}
		folderTabPanel.getResultPanel().getControl().setFocus();
	}

	/** Creates the 'Folders' tab, which browses the indexed folders. */
	@NotNull
	private static FolderPanel createFolderPanel(	@NotNull Composite parent,
													@NotNull SettingsConf.Str lastFolderSetting) {
		final FolderPanel panel = new FolderPanel(parent, indexRegistry, lastFolderSetting);
		final ResultPanel folderResults = panel.getResultPanel();
		folderResults.evtSelection.add(new Event.Listener<List<ResultDocument>>() {
			public void update(List<ResultDocument> eventData) {
				// Folder rows have nothing to preview
				if (!eventData.isEmpty() && !eventData.get(0).isFolder())
					previewPanel.setPreview(eventData.get(0));
				else
					previewPanel.clear();
				showSelectedPath(eventData);
			}
		});
		folderResults.evtHideInSystemTray.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				systemTrayHider.hide();
			}
		});
		panel.evtFolderChanged.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				// The synced folder view may change folders before the preview exists
				if (previewPanel != null)
					previewPanel.clear();
				showSelectedPath(Collections.<ResultDocument> emptyList());
			}
		});
		return panel;
	}
	
	private static Control createSearchPanel(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		searchBar = new SearchBar(comp);
		resultPanel = new ResultPanel(comp, indexRegistry);
		resultPanel.evtShowInFolder.add(new Event.Listener<File>() {
			public void update(File eventData) {
				showInFolderView(eventData);
			}
		});
		// Double-click on a folder among the search results opens it
		resultPanel.evtFolderOpened.add(new Event.Listener<File>() {
			public void update(File eventData) {
				showInFolderView(eventData);
			}
		});

		comp.setLayout(new FormLayout());
		FormDataFactory fdf = FormDataFactory.getInstance();
		fdf.margin(0).top().left().right().applyTo(searchBar.getControl());
		fdf.top(searchBar.getControl()).bottom().applyTo(resultPanel.getControl());

		searchBar.evtOpenSearchSettings.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				searchSettingsDialog.open();
			}
		});
		searchBar.evtOpenSearchHelp.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				showSearchHelp();
			}
		});

		// Optionally let the folder view follow the selected result
		resultPanel.evtSelection.add(new Event.Listener<List<ResultDocument>>() {
			public void update(List<ResultDocument> eventData) {
				followInFolderPanel(eventData);
			}
		});
		resultPanel.evtSelection.add(new Event.Listener<List<ResultDocument>>() {
			public void update(List<ResultDocument> eventData) {
				if (!eventData.isEmpty())
					previewPanel.setPreview(eventData.get(0));
			}
		});

		resultPanel.evtHideInSystemTray.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				systemTrayHider.hide();
			}
		});

		resultPanel.evtDocumentsChanged.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				// Rows of moved-away documents are removed, clearing the selection
				if (resultPanel.getSelection().isEmpty())
					previewPanel.clear();
				else
					previewPanel.refresh();
			}
		});

		return comp;
	}

	private static void moveIndexingDialogToStatusBar(@NotNull Rectangle src) {
		indexingStatus.setVisible(true);
		Rectangle dest = indexingStatus.getBounds();
		dest = shell.getDisplay().map(shell, null, dest);
		MovingBox movingBox = new MovingBox(shell, src, dest, 0.2, 40);
		movingBox.start();
	}

	/*
	 * Sets up system tray hiding.
	 */
	private static void initSystemTrayHider() {
		systemTrayHider = new SystemTrayHider(shell);

		final ResultDocument[] lastDoc = new ResultDocument[1];

		systemTrayHider.evtHiding.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				/*
				 * If DocFetcher is sent to the system tray while being
				 * maximized and showing a big file on the preview panel, one
				 * would experience an annoying delay once the program returns
				 * from the system tray. The workaround is to clear the preview
				 * panel before going to the system tray and reset it when we
				 * come back.
				 */
				lastDoc[0] = previewPanel.clear();
			}
		});

		systemTrayHider.evtRestored.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				if (lastDoc[0] != null) {
					previewPanel.setPreview(lastDoc[0]);
					lastDoc[0] = null;
				}
				searchBar.setFocus();
			}
		});

		systemTrayHider.evtShutdown.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				systemTrayShutdown = true;
				shell.close();
			}
		});
	}

	private static void initCocoaMenu(@NotNull Display display) {
		if (!Util.IS_MAC_OS_X)
			return;

		CocoaUIEnhancer cocoaUIEnhancer = new CocoaUIEnhancer(ProgramConf.Str.AppName.get());
		cocoaUIEnhancer.hookApplicationMenu(display, new Listener() {
			public void handleEvent(org.eclipse.swt.widgets.Event event) {
				shell.close(); // Bug #942
			}
		}, new Runnable() {
			public void run() {
				// TODO post-release-1.1: Show an about dialog? Or maybe open a manual page?
				String name = SystemConf.Str.ProgramName.get();
				String version = SystemConf.Str.ProgramVersion.get();
				AppUtil.showInfo(name + " " + version);
			}
		}, new Runnable() {
			public void run() {
				openPrefDialog(false);
			}
		});
	}
	
	/**
	 * Creates the menu bar with the program-wide actions (preferences,
	 * indexes, system tray, exit, manual), so that they are reachable from
	 * every tab. The keyboard shortcuts shown are handled by the global key
	 * filter.
	 */
	private static void initMenuBar() {
		Menu menuBar = new Menu(shell, SWT.BAR);
		shell.setMenuBar(menuBar);

		Menu fileMenu = createMenu(menuBar, Msg.menu_file.get());
		createMenuItem(fileMenu, Msg.menu_undo.get(), Img.ARROW_LEFT.get(), new Runnable() {
			public void run() {
				undoFileOperation();
			}
		});
		new MenuItem(fileMenu, SWT.SEPARATOR);
		createMenuItem(fileMenu, Msg.menu_preferences.get(), Img.PREFERENCES.get(), new Runnable() {
			public void run() {
				openPrefDialog(false);
			}
		});
		createMenuItem(fileMenu, Msg.print_separator_sheet.get(), Img.TB_SPLIT.get(), new Runnable() {
			public void run() {
				DocumentPrinter.printSeparatorSheet(shell);
			}
		});
		createMenuItem(fileMenu, Msg.menu_find_duplicates.get(), null, new Runnable() {
			public void run() {
				// The folder the folder browser shows (the rightmost one); none: all indexes
				FolderPanel browser = folderPanel != null ? folderPanel : folderTabPanel;
				DuplicatesDialog dialog = new DuplicatesDialog(shell, indexRegistry,
					browser == null ? null : browser.getFolder());
				dialog.evtShow.add(new Event.Listener<List<ResultDocument>>() {
					public void update(List<ResultDocument> docs) {
						showDocuments(docs, false); // The dialog stays in front
					}
				});
				dialog.open();
			}
		});
		createMenuItem(fileMenu, Msg.menu_indexes.get(), Img.FOLDER.get(), new Runnable() {
			public void run() {
				openPrefDialog(true);
			}
		});
		new MenuItem(fileMenu, SWT.SEPARATOR);
		/*
		 * On Ubuntu Unity, disable hiding in system tray. See bug #3457028 and
		 * follow-up bug #3457035.
		 */
		if (!Util.IS_UBUNTU_UNITY) {
			createMenuItem(fileMenu, Msg.to_systray.get(), Img.HIDE.get(), new Runnable() {
				public void run() {
					systemTrayHider.hide();
				}
			});
		}
		createMenuItem(fileMenu, Msg.exit_app.get(), Img.EXIT.get(), new Runnable() {
			public void run() {
				requestExit();
			}
		});

		Menu viewMenu = createMenu(menuBar, Msg.menu_view.get());
		final Msg[] paneLabels = { Msg.menu_one_pane, Msg.menu_two_panes, Msg.menu_three_panes };
		final MenuItem[] paneItems = new MenuItem[3];
		for (int i = 0; i < 3; i++) {
			final int mode = i + 1;
			paneItems[i] = new MenuItem(viewMenu, SWT.RADIO);
			paneItems[i].setText(paneLabels[i].get());
			paneItems[i].setSelection(getPaneMode() == mode);
			paneItems[i].addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					if (((MenuItem) e.widget).getSelection())
						SettingsConf.Int.PaneMode.set(mode);
				}
			});
		}
		SettingsConf.Int.PaneMode.evtChanged.add(new Event.Listener<Integer>() {
			public void update(Integer eventData) {
				for (int i = 0; i < 3; i++)
					if (!paneItems[i].isDisposed())
						paneItems[i].setSelection(eventData == i + 1);
			}
		});
		new MenuItem(viewMenu, SWT.SEPARATOR);
		
		final MenuItem previewPanelItem = new MenuItem(viewMenu, SWT.CHECK);
		previewPanelItem.setText(Msg.menu_preview_panel.get());
		previewPanelItem.setSelection(SettingsConf.Bool.ShowPreviewPanel.get());
		previewPanelItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				togglePreviewPanel();
				previewPanelItem.setSelection(SettingsConf.Bool.ShowPreviewPanel.get());
			}
		});
		SettingsConf.Bool.ShowPreviewPanel.evtChanged.add(new Event.Listener<Boolean>() {
			public void update(Boolean eventData) {
				if (!previewPanelItem.isDisposed())
					previewPanelItem.setSelection(eventData);
			}
		});
		
		initIconSizeMenu(viewMenu);
		
		Menu helpMenu = createMenu(menuBar, Msg.menu_help.get());
		createMenuItem(helpMenu, Msg.menu_manual.get(), Img.HELP.get(), new Runnable() {
			public void run() {
				showManual();
			}
		});
		final String appName = SystemConf.Str.ProgramName.get();
		createMenuItem(helpMenu, Msg.menu_about.format(appName), null, new Runnable() {
			public void run() {
				AppUtil.showInfo(appName + " " + SystemConf.Str.ProgramVersion.get());
			}
		});
	}

	@NotNull
	private static Menu createMenu(@NotNull Menu menuBar, @NotNull String label) {
		MenuItem item = new MenuItem(menuBar, SWT.CASCADE);
		item.setText(label);
		Menu menu = new Menu(shell, SWT.DROP_DOWN);
		item.setMenu(menu);
		return menu;
	}

	@NotNull
	/** The choices of 'View > Icon size', the same as in the preferences. */
	private static final int[] ICON_SIZES = { 0, 100, 125, 150, 200 };
	
	/**
	 * Adds the 'Icon size' submenu. The icons are scaled when the program
	 * starts, so a new size takes effect after a restart, which is said.
	 */
	private static void initIconSizeMenu(@NotNull Menu viewMenu) {
		MenuItem iconSizeItem = new MenuItem(viewMenu, SWT.CASCADE);
		iconSizeItem.setText(Msg.menu_icon_size.get());
		Menu iconSizeMenu = new Menu(viewMenu);
		iconSizeItem.setMenu(iconSizeMenu);
		final MenuItem[] items = new MenuItem[ICON_SIZES.length];
		for (int i = 0; i < ICON_SIZES.length; i++) {
			final int size = ICON_SIZES[i];
			items[i] = new MenuItem(iconSizeMenu, SWT.RADIO);
			items[i].setText(size == 0 ? Msg.pref_icon_size_auto.get() : size + " %");
			items[i].setSelection(SettingsConf.Int.IconScalePercent.get() == size);
			items[i].addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					// Radio items also report being deselected
					if (!((MenuItem) e.widget).getSelection()
							|| SettingsConf.Int.IconScalePercent.get() == size)
						return;
					SettingsConf.Int.IconScalePercent.set(size);
					AppUtil.showInfo(Msg.pref_icon_size_hint.format(
						ProgramConf.Str.AppName.get()));
				}
			});
		}
		// The preferences change the same setting
		SettingsConf.Int.IconScalePercent.evtChanged.add(new Event.Listener<Integer>() {
			public void update(Integer eventData) {
				for (int i = 0; i < items.length; i++)
					if (!items[i].isDisposed())
						items[i].setSelection(ICON_SIZES[i] == eventData);
			}
		});
	}
	
	private static MenuItem createMenuItem(	@NotNull Menu menu,
											@NotNull String label,
											@Nullable Image image,
											@NotNull final Runnable action) {
		MenuItem item = new MenuItem(menu, SWT.PUSH);
		item.setText(label);
		if (image != null)
			item.setImage(image);
		item.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				action.run();
			}
		});
		return item;
	}

	/**
	 * Opens the preferences dialog. If {@code showIndexesTab} is true, the
	 * 'Indexes' tab is selected initially.
	 */
	private static void openPrefDialog(boolean showIndexesTab) {
		PrefDialog prefDialog = new PrefDialog(
			shell, programConfFile, indexPanel, defaultIndexParentDir);
		prefDialog.evtOKClicked.add(new Event.Listener<Void> () {
		    public void update(Void eventData) {
		    	saveSettingsConfFile();
		    	applyInboxFolders();
		    }
		});
		prefDialog.open(showIndexesTab);
	}

	@NotNull
	private static void initTwoPanelForm() {
		twoPanelForm = new TwoPanelForm(shell) {
			protected Control createFirstSubControl(Composite parent) {
				return createRightTopPanel(parent);
			}
			protected Control createSecondSubControl(Composite parent) {
				previewPanel = new PreviewPanel(parent);
				previewPanel.evtHideInSystemTray.add(new Event.Listener<Void>() {
					public void update(Void eventData) {
						systemTrayHider.hide();
					}
				});
				previewPanel.evtSaveSettings.add(new Event.Listener<Void>() {
					@Override
					public void update(Void eventData) {
						saveSettingsConfFile();
					}
				});
				return previewPanel;
			}
		};

		twoPanelForm.setSubSashWidth(sashWidth);

		// Restore visibility of preview panel
		twoPanelForm.setSecondSubControlVisible(SettingsConf.Bool.ShowPreviewPanel.get());

		// Restore orientation and weights of right sash
		boolean isVertical = SettingsConf.Bool.ShowPreviewPanelAtBottom.get();
		twoPanelForm.setVertical(isVertical);
		twoPanelForm.setSubSashWeights(getRightSashWeights(isVertical));

		final boolean[] ignoreControlResize = { false };

		// Store weights of right sash
		Control topRightControl = twoPanelForm.getFirstSubControl();
		ControlListener rightControlListener = new ControlAdapter() {
			public void controlResized(ControlEvent e) {
				if (!previewPanel.isVisible() || ignoreControlResize[0])
					return;
				int[] weights = twoPanelForm.getSubSashWeights();
				if (twoPanelForm.isVertical())
					SettingsConf.IntArray.RightSashVertical.set(weights);
				else
					SettingsConf.IntArray.RightSashHorizontal.set(weights);
			}
		};
		topRightControl.addControlListener(rightControlListener);
		previewPanel.addControlListener(rightControlListener);

		// Store visibility of preview panel
		twoPanelForm.evtSecondSubControlShown.add(new Event.Listener<Boolean>() {
			public void update(Boolean eventData) {
				SettingsConf.Bool.ShowPreviewPanel.set(eventData);
			}
		});

		/*
		 * Temporarily deactivate storing the sash weights during sash
		 * orientation changes. Without this, we'd set the same sash weights for
		 * both orientations.
		 */
		twoPanelForm.evtSubOrientationChanging.add(new Event.Listener<Boolean>() {
			public void update(Boolean isVertical) {
				ignoreControlResize[0] = true;
			}
		});

		// Store orientation of right sash; update weights
		twoPanelForm.evtSubOrientationChanged.add(new Event.Listener<Boolean>() {
			public void update(Boolean isVertical) {
				SettingsConf.Bool.ShowPreviewPanelAtBottom.set(isVertical);
				twoPanelForm.setSubSashWeights(getRightSashWeights(isVertical));
				ignoreControlResize[0] = false;
			}
		});
		
		/*
		 * Apply preview position changes made on the preferences dialog. The
		 * listener above sets the same value, in which case the setter doesn't
		 * fire, so there's no feedback loop.
		 */
		SettingsConf.Bool.ShowPreviewPanelAtBottom.evtChanged.add(new Event.Listener<Boolean>() {
			public void update(Boolean isVertical) {
				if (twoPanelForm.isVertical() == isVertical)
					return;
				twoPanelForm.setVertical(isVertical);
				if (!twoPanelForm.isSecondSubControlVisible())
					twoPanelForm.setSecondSubControlVisible(true);
			}
		});
	}

	@NotNull
	private static int[] getRightSashWeights(boolean isVertical) {
		return isVertical
			? SettingsConf.IntArray.RightSashVertical.get()
			: SettingsConf.IntArray.RightSashHorizontal.get();
	}

	private static void initStatusBar() {
		statusBar = new StatusBar(shell) {
			public List<StatusBarPart> createRightParts(StatusBar statusBar) {
				// Clicking the progress indicator shows the indexing window
				// (queue, configuration of new indexes, errors)
				indexingStatus = new StatusBarPart(statusBar, true);
				indexingStatus.setContents(Img.INDEXING.get(), Msg.indexing.get());
				indexingStatus.setVisible(false);
				indexingStatus.evtClicked.add(new Event.Listener<Void>() {
					public void update(Void eventData) {
						indexPanel.openIndexingDialog();
					}
				});
				
				List<StatusBarPart> parts = new ArrayList<StatusBarPart>(1);
				parts.add(indexingStatus);
				return parts;
			}
		};
	}

	/**
	 * Exits the program, regardless of the configured close action. The usual
	 * shutdown sequence applies, e.g. the user is asked what to do with
	 * running indexing tasks.
	 */
	private static void requestExit() {
		exitRequested = true;
		shell.close();
		/*
		 * If the close was vetoed, e.g. because the user chose not to abort
		 * the indexing, reset the flag so that the next click on the close
		 * button honors the configured close action again.
		 */
		if (!shell.isDisposed())
			exitRequested = false;
	}

	private static void handleShellClosed(@NotNull ShellEvent e) {
		int closeAction = SettingsConf.Int.CloseAction.get();
		boolean forceExit = systemTrayShutdown || exitRequested;
		if (!forceExit && closeAction == SettingsConf.CLOSE_ACTION_TRAY && !Util.IS_UBUNTU_UNITY) {
			e.doit = false;
			systemTrayHider.hide();
		} else if (!forceExit && closeAction == SettingsConf.CLOSE_ACTION_MINIMIZE) {
			e.doit = false;
			shell.setMinimized(true);
		} else {
			e.doit = indexRegistry.getQueue().shutdown(new CancelHandler() {
				public CancelAction cancel() {
					return confirmExit();
				}
			});
			if (!e.doit)
				return;

			// Clear search history
			if (SettingsConf.Bool.ClearSearchHistoryOnExit.get())
				SettingsConf.StrList.SearchHistory.set();

			/*
			 * Note: The getSearcher() call below will block until the searcher
			 * is available. If we run this inside the GUI thread, we won't let
			 * go of the GUI lock, causing the program to deadlock when the user
			 * tries to close the program before all indexes have been loaded.
			 */
			new Thread() {
				public void run() {
					/*
					 * The folder watcher will be null if the program is shut
					 * down while loading the indexes
					 */
					if (folderWatcher != null)
						folderWatcher.shutdown();

					indexRegistry.getSearcher().shutdown();
				}
			}.start();

			/*
			 * Hotkey teardown goes through native code that has been known to
			 * hang. It doesn't touch the index, so it must run independently
			 * of (and must not delay) the searcher shutdown above, which needs
			 * to interrupt the index deletion thread. Daemonized so a hang
			 * here can't keep the JVM from exiting.
			 */
			if (hotkeyHandler != null) {
				Thread hotkeyShutdownThread = new Thread() {
					public void run() {
						hotkeyHandler.shutdown();
					}
				};
				hotkeyShutdownThread.setDaemon(true);
				hotkeyShutdownThread.start();
			}
		}
	}

	private static void initHotkey() {
		/*
		 * On Windows, the hotkey is disabled by default due to various known
		 * issues:
		 * 
		 * Thread (in German) about the program refusing to launch, with no
		 * error messages and running on Java 64-bit:
		 * https://sourceforge.net/p/docfetcher/discussion/702424/thread/458c45e0cf/
		 * 
		 * One of many bug reports where the program crashes with Java 32-bit:
		 * https://sourceforge.net/p/docfetcher/bugs/1514/
		 */
		if (!ProgramConf.Bool.HotkeyEnabled.get()) {
			return;
		}
		
		/*
		 * As registering the hotkey is prone to crashing the VM, we'll
		 * temporarily disable the hotkey before attempting to register it, so
		 * that if the VM does crash, the hotkey will be disabled the next time.
		 */
		
		boolean wasHotkeyEnabled = SettingsConf.Bool.HotkeyEnabled.get();
		SettingsConf.Bool.HotkeyEnabled.set(false);
		saveSettingsConfFile();
		
		try {
			hotkeyHandler = new HotkeyHandler();
		}
		catch (UnsupportedOperationException e) {
			return; // Hotkey not supported on OS X
		}
		catch (Throwable e) {
			Util.printErr(e);
			return;
		}
		
		if (wasHotkeyEnabled) {
			int[] hotkey = SettingsConf.IntArray.Hotkey.get();
			boolean success = hotkeyHandler.registerHotkey(hotkey[0], hotkey[1]); // might crash the VM
			if (!success) {
				handleHotkeyConflict(hotkey);
			}
		}
		
		/*
		 * This used to be in a finally clause, but apparently even if the VM
		 * aborts due to failed hotkey registration, the finally clause is still
		 * executed, which is not what we want. Relevant forum thread (in
		 * German):
		 * https://sourceforge.net/p/docfetcher/discussion/702424/thread/458c45e0cf/?limit=25#2055
		 */
		SettingsConf.Bool.HotkeyEnabled.set(wasHotkeyEnabled);
//		saveSettingsConfFile();
		
		SettingsConf.IntArray.Hotkey.evtChanged.add(new Event.Listener<int[]>() {
			public void update(int[] eventData) {
				hotkeyHandler.unregisterHotkey();
				
				if (SettingsConf.Bool.HotkeyEnabled.get()) {
					boolean success = hotkeyHandler.registerHotkey(eventData[0], eventData[1]);
					if (!success) {
						handleHotkeyConflict(eventData);
					}
				}
			}
		});
		
		SettingsConf.Bool.HotkeyEnabled.evtChanged.add(new Event.Listener<Boolean> () {
			public void update(Boolean eventData) {
				if (eventData) {
					// Ignore hotkey conflict
					int[] hotkey = SettingsConf.IntArray.Hotkey.get();
					hotkeyHandler.registerHotkey(hotkey[0], hotkey[1]);
				}
				else {
					hotkeyHandler.unregisterHotkey();
				}
			}
		});

		hotkeyHandler.evtHotkeyPressed.add(new Event.Listener<Void> () {
			public void update(Void eventData) {
				UtilGui.runSyncExec(shell, new Runnable() {
					public void run() {
						activateMainWindow();
					}
				});
			}
		});
	}
	
	/**
	 * Brings the main window to the front, restoring it from the system tray
	 * or from its minimized state if necessary. Must be called from the GUI
	 * thread.
	 */
	private static void activateMainWindow() {
		if (systemTrayHider.isHidden()) {
			systemTrayHider.restore();
		}
		else {
			shell.setMinimized(false);
			shell.setVisible(true);
			shell.forceActive();
			searchBar.setFocus();
		}
	}
	
	private static void handleHotkeyConflict(int[] hotkey) {
		String key = UtilGui.toString(hotkey);
		AppUtil.showError(Msg.hotkey_in_use.format(key), false, true);

		/*
		 * Don't open preferences dialog when the hotkey conflict occurs
		 * at startup.
		 */
		if (shell.isVisible()) {
			openPrefDialog(false);
		}
	}

	@Nullable
	private static CancelAction confirmExit() {
		MultipleChoiceDialog<CancelAction> dialog = new MultipleChoiceDialog<CancelAction>(shell);
		dialog.setTitle(Msg.abort_indexing.get());
		dialog.setText(Msg.keep_partial_index_on_exit.get());
		if (shell.getDisplay().getDismissalAlignment() == SWT.LEFT) {
			dialog.addButton(Msg.keep.get(), CancelAction.KEEP);
			dialog.addButton(Msg.discard.get(), CancelAction.DISCARD);
			dialog.addButton(Msg.dont_exit.get(), null);
		} else {
			dialog.addButton(Msg.dont_exit.get(), null);
			dialog.addButton(Msg.discard.get(), CancelAction.DISCARD);
			dialog.addButton(Msg.keep.get(), CancelAction.KEEP);
		}
		return dialog.open();
	}

	/**
	 * Starts, restarts or stops the MCP server according to the settings;
	 * shows an error if the port is in use.
	 */
	private static void updateMcpServer() {
		if (!SettingsConf.Bool.McpServerEnabled.get()) {
			if (mcpServer != null)
				mcpServer.stop();
			return;
		}
		int port = SettingsConf.Int.McpServerPort.get();
		try {
			if (mcpServer == null) {
				mcpServer = new McpServer(indexRegistry);
				mcpServer.setViewer(new McpServer.DocumentViewer() {
					public void show(final List<ResultDocument> docs) {
						UtilGui.runAsyncExec(shell, new Runnable() {
							public void run() {
								showDocuments(docs, true);
							}
						});
					}
				});
				mcpServer.setFolderApprover(new McpServer.FolderApprover() {
					public boolean approve(final File folder) {
						final boolean[] allowed = {false};
						UtilGui.runSyncExec(shell, new Runnable() {
							public void run() {
								allowed[0] = askFileFolder(folder);
							}
						});
						return allowed[0];
					}
				});
}
			mcpServer.start(port);
		}
		catch (Throwable e) {
			// Port in use, or e.g. a Java runtime without the HTTP server module
			Util.printErr(e);
			AppUtil.showError(Msg.mcp_server_failed.format(port, e.toString()), true, true);
		}
	}

	/**
	 * Shows documents for an AI assistant (MCP show_document) or from the
	 * duplicates dialog: the window comes to the front if wanted, the Search
	 * tab lists them, the first one is previewed and the folder browsers sync
	 * to it, even with the sync button off - the user asked for this document.
	 */
	private static void showDocuments(@NotNull List<ResultDocument> docs, boolean activate) {
		if (activate)
			activateMainWindow();
		mainTabFolder.setSelection(searchTab);
		onTabSelected();
		resultPanel.showResults(docs);
		// Sync first: a folder browser changing its folder clears the preview
		showInRightmostFolderPanel(docs.get(0));
		previewPanel.setPreview(docs.get(0));
	}

	/**
	 * Asks whether an AI assistant may learn the folder for the files it
	 * created (MCP get_file_folder); the answer can be remembered, and 'Reset
	 * Remembered Answers' in the preferences brings the question back. The
	 * window comes to the front first, as it may be in the tray.
	 */
	private static boolean askFileFolder(@NotNull File folder) {
		activateMainWindow();
		MultipleChoiceDialog<Boolean> dialog = new MultipleChoiceDialog<Boolean>(shell);
		dialog.setTitle(Msg.mcp_folder_title.get());
		dialog.setImage(SWT.ICON_QUESTION);
		dialog.setText(Msg.mcp_folder_question.format(folder.getPath()));
		dialog.setCheckBox(Msg.remember_choice.get());
		dialog.addButton(Msg.mcp_folder_allow.get(), Boolean.TRUE);
		dialog.addButton(Msg.mcp_folder_deny.get(), Boolean.FALSE);
		Boolean answer = dialog.open();
		if (answer == null)
			return false; // Closed: not allowed
		if (dialog.isChecked())
			SettingsConf.Int.McpFileFolderAnswer.set(answer
				? SettingsConf.MCP_FOLDER_ALLOW
				: SettingsConf.MCP_FOLDER_DENY);
		return answer;
	}

	private static void showManual() {
		showHelpPage(ManualLocator.getStartPage());
	}

	/** Shows the search help (query syntax) next to the results. */
	private static void showSearchHelp() {
		File file = ManualLocator.getManualSubpageFile("Query_Syntax.html");
		showHelpPage(file != null && file.isFile() ? file : ManualLocator.getStartPage());
	}

	private static void showHelpPage(@Nullable File file) {
		if (file != null) {
			if (previewPanel.setHtmlFile(file))
				twoPanelForm.setSecondSubControlVisible(true);
			else
				UtilGui.launch(file);
		}
		else {
			String msg = Msg.file_not_found.get() + "\n" + SystemConf.Str.ProgramName.get() + "_Manual.html";
			AppUtil.showError(msg, true, true);
		}
	}

	/**
	 * For the portable version on Windows, keeps the user data folder of the
	 * embedded Edge (WebView2) browser inside the application folder, next to
	 * the unpacked SWT libraries, instead of the default location under
	 * %LOCALAPPDATA%. Must be called before the first browser widget is
	 * created.
	 */
	private static void configureWebView2DataDir() {
		if (!Util.IS_WINDOWS || !AppUtil.isPortable())
			return;
		String prop = "org.eclipse.swt.browser.EdgeDataDir";
		if (System.getProperty(prop) != null)
			return;
		File dataDir = new File(AppUtil.getAppDataDir(), "lib/webview2");
		dataDir.mkdirs();
		if (dataDir.isDirectory() && AppUtil.canWriteToDirectory(dataDir))
			System.setProperty(prop, dataDir.getAbsolutePath());
	}

	/**
	 * Configures the SWT library path to unpack native libraries into a
	 * specific location. This must be called before any Display/SWT classes are
	 * loaded to ensure portable versions don't leave native libraries in the
	 * system.
	 *
	 * @param pathOverride Optional override directory from paths.txt. If null,
	 *                     uses default location based on portable/non-portable
	 *                     mode.
	 */
	private static void configureSwtLibraryPath(@Nullable File pathOverride) {
		final File swtLibDir;
		if (pathOverride != null) {
			swtLibDir = pathOverride;
		} else if (AppUtil.isPortable()) {
            swtLibDir = new File(AppUtil.getAppDataDir(), "lib/swt");
		} else {
            swtLibDir = new File(AppUtil.getAppDataDir(), "swt");
		}

		// Create directory if it doesn't exist - SWT won't recognize the path
		// otherwise
		swtLibDir.mkdirs();

		// Determine target directory: use swtLibDir if writable, otherwise fall
		// back to temp
		File targetDir;
		if (swtLibDir.isDirectory() && swtLibDir.canWrite()) {
			targetDir = swtLibDir;
		} else {
			targetDir = new File(System.getProperty("java.io.tmpdir"));
		}

		unpackSwtNativeLibs(targetDir);
		try {
			System.setProperty("swt.library.path", targetDir.getCanonicalPath());
		} catch (IOException e) {
			System.setProperty("swt.library.path", targetDir.getAbsolutePath());
		}
	}

	/**
	 * Unpacks SWT native libraries from the SWT jar to the target directory.
	 *
	 * @param targetDir The directory where native libraries should be unpacked
	 */
	private static void unpackSwtNativeLibs(@NotNull File targetDir) {
		File libDir = Util.IS_MAC_OS_X && !AppUtil.isPortable() ?
				new File("../Resources/lib") :
				new File("lib");
		if (!libDir.isDirectory()) {
			return;
		}
		
		// Find the SWT jar
		File swtJar = null;
		File[] jarFiles = libDir.listFiles();
		if (jarFiles != null) {
			for (File jar : jarFiles) {
				String name = jar.getName();
				if (name.startsWith("swt-") && name.endsWith(".jar")) {
					swtJar = jar;
					break;
				}
			}
		}

		if (swtJar == null) {
			return;
		}

		// Determine the native library extension based on platform
		String nativeExt;
		if (Util.IS_WINDOWS) {
			nativeExt = ".dll";
		} else if (Util.IS_LINUX) {
			nativeExt = ".so";
		} else if (Util.IS_MAC_OS_X) {
			nativeExt = ".jnilib";
		} else {
			return; // Unsupported platform
		}

		// Unpack native libraries from the jar
		JarFile jar = null;
		try {
			jar = new JarFile(swtJar);
			java.util.Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				if (entry.isDirectory()) {
					continue;
				}
				String name = entry.getName();
				// Only extract files at the top level that have the correct extension
				if (!name.contains("/") && name.endsWith(nativeExt)) {
					File targetFile = new File(targetDir, name);
					if (!targetFile.exists() || targetFile.length() != entry.getSize()) {
						unpackJarEntry(jar, entry, targetFile);
					}
				}
			}
		} catch (IOException e) {
			// Silently ignore errors during unpacking
			if (SystemConf.Bool.IsDevelopmentVersion.get()) {
				e.printStackTrace();
			}
		} finally {
			if (jar != null) {
				try {
					jar.close();
				} catch (IOException e) {
					// Ignore
				}
			}
		}
	}

	/**
	 * Unpacks a single entry from a JAR file to a target file.
	 *
	 * @param jar The JAR file
	 * @param entry The entry to unpack
	 * @param targetFile The target file
	 */
	private static void unpackJarEntry(
		@NotNull JarFile jar,
		@NotNull JarEntry entry,
		@NotNull File targetFile
	) throws IOException {
        try (
			InputStream in = jar.getInputStream(entry);
			FileOutputStream out = new FileOutputStream(targetFile)
		) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            // Make the library executable on Unix systems (like SWT does)
            if (!Util.IS_WINDOWS) {
                targetFile.setExecutable(true);
            }
        }
    }

	// Public method for Python API
	@NotNull
	public static IndexRegistry getIndexRegistry() {
		return indexRegistry;
	}

}
