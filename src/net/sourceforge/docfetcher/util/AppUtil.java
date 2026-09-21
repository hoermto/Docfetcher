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

package net.sourceforge.docfetcher.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.sun.jna.platform.win32.Shell32Util;

import net.sourceforge.docfetcher.enums.ProgramConf;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.enums.SystemConf;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.gui.dialog.StackTraceWindow;

/**
 * A container for various utility methods. These aren't members of the
 * {@link Util} package because they depend on some enum constants defined in
 * {@link Const} and {@link Messages}. The <code>Const</code> constants must have
 * been set before any method of this class can be called, otherwise an
 * <code>Exception</code> will be thrown. Setting the <code>Msg</code>
 * constants, on the other hand, is optional.
 * 
 * @author Tran Nam Quang
 */
public final class AppUtil {
	
	public static enum Const {
		/** This is the internal program name, not the name that can be changed by the user! */
		PROGRAM_NAME,
		PROGRAM_VERSION,
		PROGRAM_BUILD_DATE,
		USER_DIR_PATH,
		IS_PORTABLE,
		IS_DEVELOPMENT_VERSION,
		;
		
		private String value;
		
		@Nullable public String get() {
			return value;
		}
		
		public void set(@NotNull String value) {
			Util.checkNotNull(value);
			if (this.value != null)
				throw new UnsupportedOperationException("Constant cannot be set twice: " + this.name());
			if (this == IS_PORTABLE)
				this.value = String.valueOf(Boolean.parseBoolean(value));
			else
				this.value = value;
		}
		
		public void set(boolean b) {
			set(String.valueOf(b));
		}
		
		private boolean asBoolean() {
			return Boolean.parseBoolean(value);
		}
		
		/**
		 * Automatically initializes all constants. Should only be used for
		 * debugging. Calling this method repeatedly or after the constants have
		 * already been set manually will have no effect.
		 */
		public static void autoInit() {
			if (initialized)
				return;
			
			PROGRAM_NAME.set("DocFetcher");
			PROGRAM_VERSION.set("Unspecified");
			PROGRAM_BUILD_DATE.set("Unspecified");
			USER_DIR_PATH.set(Util.USER_DIR_PATH);
			IS_PORTABLE.set("true");
			IS_DEVELOPMENT_VERSION.set("true");
			
			for (Const c : Const.values())
				if (c.value == null)
					throw new IllegalStateException();
			
			initialized = true;
		}
		
		public static void clear() {
			for (Const c : Const.values())
				c.value = null;
			initialized = false;
		}
	}
	
	public static enum Messages {
		system_error ("System Error"),
		confirm_operation ("Confirm Operation"),
		invalid_operation ("Invalid Operation"),
		program_died_stacktrace_written (
				"This program just died! " +
				"The stacktrace below has been written to {0}."),
		program_running_launch_another (
				"It seems an instance of {0} was not terminated cleanly or is still running. " +
				"Do you want to launch another instance?"),
		ok ("&OK"),
		cancel ("&Cancel"),
		;
		
		private static int setCount = 0;
		private String value;
		
		Messages(String defaultValue) {
			this.value = defaultValue;
		}
		
		@NotNull public String get() {
			return value;
		}
		
		public void set(@NotNull String value) {
			this.value = Util.checkNotNull(value);
			setCount++;
		}
		
		/**
		 * Returns a string created from a <tt>java.text.MessageFormat</tt>
		 * with the given argument(s).
		 */
		private String format(Object... args) {
			return MessageFormat.format(value, args);
		}
		
		public static void checkInitialized() {
			Util.checkThat(values().length == setCount);
		}
	}
	
	private AppUtil() {}
	
	private static File appDataDir;
	private static Display display;
	private static boolean initialized = false;
	
	public static void setDisplay(@NotNull Display display) {
		Util.checkNotNull(display);
		Util.checkThat(AppUtil.display == null);
		AppUtil.display = display;
		
		display.disposeExec(new Runnable() {
			public void run() {
				AppUtil.display = null;
			}
		});
	}
	
	public static void checkConstInitialized() {
		if (! initialized) {
			for (Const value : Const.values()) {
				if (value.value == null)
					throw new IllegalStateException("Uninitialized constant: " + value.name());
			}
			initialized = true;
		}
	}
	
	public static void ensureNoDisplay() {
		if (display != null)
			throw new IllegalStateException("Display has already been initialized.");
	}
	
	private static void ensureDisplay() {
		if (display == null)
			throw new IllegalStateException("Display has not been initialized.");
	}
	
	/**
	 * Checks whether an instance of this program is already running. The check
	 * relies on a lockfile created in the temporary directory. The argument is
	 * the program name to be displayed in the confirmation dialog (see below).
	 * <p>
	 * The boolean return value should be interpreted as
	 * "proceed with running this instance?". More specifically:
	 * <ul>
	 * <li>If there is no other instance, true is returned.
	 * <li>If there is another instance, a confirmation dialog is shown, which
	 * asks the user whether this instance should be launched. If the user
	 * confirms, true is returned, otherwise false.
	 * </ul>
	 * The filename of the lockfile includes the username and the working
	 * directory, which means:
	 * <ul>
	 * <li>The same instance may be launched multiple times by different users.
	 * <li>Multiple instances from different locations can be run simultaneously
	 * by the same user.
	 * </ul>
	 * <p>
	 * <b>Note</b>: The message container must be loaded before calling this
	 * method, otherwise the confirmation dialog will show untranslated strings.
	 */
	// TODO doc: before calling this method, set USER_DIR_PATH, PROGRAM_NAME and all Msg enums
	/**
	 * Fired (from a non-GUI thread) when another instance of the program was
	 * launched and asks this instance to show its main window.
	 */
	public static final Event<Void> evtActivateRequested = new Event<Void>();
	
	private static final String ACTIVATE_COMMAND = "ACTIVATE";
	
	/**
	 * Ensures that only one instance of the program runs at a time. Returns
	 * true if this instance may continue; returns false if another instance
	 * is already running, in which case that instance has been asked to bring
	 * its main window to the front and this instance should exit silently.
	 * <p>
	 * Implementation: The running instance listens on a loopback socket and
	 * stores the port in a lockfile. A newly launched instance connects to
	 * that port; if nobody answers, the lockfile is stale (e.g. after a crash)
	 * and is taken over.
	 */
	public static boolean checkSingleInstance() {
		checkConstInitialized();
		ensureNoDisplay();

		/*
		 * The lockfile is created in the system's temporary directory to make
		 * sure it gets deleted after OS shutdown - neither File.deleteOnExit()
		 * nor a JVM shutdown hook can guarantee this.
		 * 
		 * The name of the lockfile includes the base64-encoded working
		 * directory, thus avoiding filename collisions if the same user runs
		 * multiple program instances from different locations. We'll also put
		 * in a SHA-1 digest of the working directory, just in case the
		 * base64-encoded working directory exceeds the system's allowed
		 * filename length limit, which we'll assume to be 255 characters.
		 * 
		 * Note that the included program name is also encoded as base64 since a
		 * developer might have (accidentally?) set a program name that contains
		 * special characters such as '/'.
		 */
		
		String dirPath = Const.USER_DIR_PATH.value;
		String shaDirPath = DigestUtils.shaHex(dirPath);
		String programName64 = Util.encodeBase64(Const.PROGRAM_NAME.value);
		String username64 = Util.encodeBase64(System.getProperty("user.name"));
		String dirPath64 = Util.encodeBase64(dirPath);
		
		String lockname = String.format(
				".lock-%s-%s-%s-%s.", // dot at the end is intentional
				shaDirPath, programName64, username64, dirPath64
		);
		// Usual filename length limit is 255 characters
		lockname = lockname.substring(0, Math.min(lockname.length(), 250));
		File lockfile = new File(Util.TEMP_DIR, lockname);
		
		if (lockfile.exists() && activateRunningInstance(lockfile))
			return false;
		
		// No instance is running (or the lockfile is stale): take over
		ServerSocket server;
		try {
			server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
		}
		catch (IOException e) {
			// Can't listen; still allow the program to run
			Util.printErr(e);
			return true;
		}
		try {
			Files.write(String.valueOf(server.getLocalPort()), lockfile, Charsets.UTF_8);
		}
		catch (IOException e) {
			Util.printErr(e);
		}
		lockfile.deleteOnExit();
		startActivationServer(server);
		return true;
	}
	
	/**
	 * Tries to contact the instance that owns the given lockfile. Returns true
	 * if it answered, i.e. it is running and will show its window.
	 */
	private static boolean activateRunningInstance(@NotNull File lockfile) {
		int port;
		try {
			String content = Files.toString(lockfile, Charsets.UTF_8).trim();
			if (content.isEmpty())
				return false; // Lockfile from an older version, treat as stale
			port = Integer.parseInt(content);
		}
		catch (IOException e) {
			return false;
		}
		catch (NumberFormatException e) {
			return false;
		}
		
		Socket socket = new Socket();
		try {
			socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1000);
			socket.setSoTimeout(3000);
			Writer out = new OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8);
			out.write(ACTIVATE_COMMAND + "\n");
			out.flush();
			BufferedReader in = new BufferedReader(
				new InputStreamReader(socket.getInputStream(), Charsets.UTF_8));
			String reply = in.readLine();
			return reply != null && reply.startsWith("OK");
		}
		catch (IOException e) {
			return false; // Nobody listening: stale lockfile
		}
		finally {
			try {
				socket.close();
			}
			catch (IOException e) {
				// Ignore
			}
		}
	}
	
	/**
	 * Accepts activation requests from other instances in a daemon thread.
	 */
	private static void startActivationServer(@NotNull final ServerSocket server) {
		Thread thread = new Thread(AppUtil.class.getName() + " (single instance server)") {
			public void run() {
				while (!server.isClosed()) {
					Socket socket = null;
					try {
						socket = server.accept();
						socket.setSoTimeout(3000);
						BufferedReader in = new BufferedReader(
							new InputStreamReader(socket.getInputStream(), Charsets.UTF_8));
						String line = in.readLine();
						if (line != null && line.trim().equals(ACTIVATE_COMMAND)) {
							evtActivateRequested.fire(null);
							Writer out = new OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8);
							out.write("OK\n");
							out.flush();
						}
					}
					catch (IOException e) {
						if (server.isClosed())
							return;
					}
					finally {
						if (socket != null) {
							try {
								socket.close();
							}
							catch (IOException e) {
								// Ignore
							}
						}
					}
				}
			}
		};
		thread.setDaemon(true);
		thread.start();
	}
	
	/**
	 * Returns a shell associated with this program. This method will first try
	 * to return the currently active shell. If no shell is active, it will try
	 * to return the first inactive shell. If there are no shells at all, null
	 * is returned.
	 * <p>
	 * This method should not be called from a non-GUI thread, and it should not
	 * be called before the first display is created.
	 */
	private static Shell getActiveShell() {
		ensureDisplay();
		Shell shell = display.getActiveShell();
		if (shell != null) return shell;
		Shell[] shells = display.getShells();
		if (shells.length != 0) return shells[0];
		return null;
	}
	
	/**
	 * Shows the given message in an error message box, with "System Error" as
	 * the shell title. If <tt>isSevere</tt> is true, an error icon is shown,
	 * otherwise a warning icon.
	 * <p>
	 * This method can be used before any GUI components are created, because it
	 * creates its own display and shell. If there is already a GUI,
	 * {@link #showErrorMsg} should be used instead.
	 */
	public static void showErrorOnStart(String message, boolean isSevere) {
		int style = SWT.OK | (isSevere ? SWT.ICON_ERROR : SWT.ICON_WARNING);
		showErrorOnStart(message, style);
	}
	
	public static int showErrorOnStart(String message, int style) {
		checkConstInitialized();
		ensureNoDisplay();
		
		Display display = new Display();
		Shell shell = new Shell(display);
		MessageBox msgBox = new MessageBox(shell, style);
		msgBox.setText(Messages.system_error.value);
		msgBox.setMessage(message);
		int buttonID = msgBox.open();
		shell.dispose();
		display.dispose();
		return buttonID;
	}

	/**
	 * Shows the given message in a confirmation message box and returns the
	 * user's answer, either <tt>SWT.OK</tt> or <tt>SWT.CANCEL</tt>. If
	 * <tt>isSevere</tt> is true, a warning icon is shown, otherwise a question
	 * icon.
	 * <p>
	 * This method may be called from a non-GUI thread. It should not be called
	 * before the first shell is created.
	 */
	public static boolean showConfirmation(final String message, final boolean warningNotQuestion) {
		checkConstInitialized();
		ensureDisplay();
		class MyRunnable implements Runnable {
			private boolean answer;
			public void run() {
				int style = SWT.OK | SWT.CANCEL;
				style |= warningNotQuestion ? SWT.ICON_WARNING : SWT.ICON_QUESTION;
				MessageBox msgBox = new MessageBox(getActiveShell(), style);
				msgBox.setText(Messages.confirm_operation.value);
				msgBox.setMessage(message);
				answer = msgBox.open() == SWT.OK;
			}
		}
		MyRunnable myRunnable = new MyRunnable();
		UtilGui.runSwtSafe(display, myRunnable);
		return myRunnable.answer;
	}

	/**
	 * Shows the given message in a message box with an information icon. This
	 * method may be called from a non-GUI thread. It should not be called
	 * before the first shell is created.
	 */
	public static void showInfo(final String message) {
		checkConstInitialized();
		ensureDisplay();
		
		UtilGui.runSwtSafe(display, new Runnable() {
			public void run() {
				MessageBox msgBox = new MessageBox(getActiveShell(),
						SWT.ICON_INFORMATION | SWT.OK);
				msgBox.setMessage(message);
				msgBox.open();
			}
		});
	}

	/**
	 * Shows the given message in an error message box. If
	 * <tt>errorNotWarning</tt> is true, an error icon is shown, otherwise a
	 * warning icon. If <tt>isUserError</tt> is true, the shell title is set to
	 * "Invalid Operation", otherwise "System Error".
	 * <p>
	 * This method may be called from a non-GUI thread. It should not be called
	 * before the first shell is created.
	 */
	public static void showError(	@NotNull final String message,
									final boolean errorNotWarning,
									final boolean isUserError) {
		checkConstInitialized();
		ensureDisplay();
		
		UtilGui.runSwtSafe(display, new Runnable() {
			public void run() {
				int style = SWT.OK;
				style |= errorNotWarning ? SWT.ICON_ERROR : SWT.ICON_WARNING;
				MessageBox msgBox = new MessageBox(getActiveShell(), style);
				msgBox.setText(isUserError ? Messages.invalid_operation.value
						: Messages.system_error.value);
				msgBox.setMessage(message);
				msgBox.open();
			}
		});
	}

	/**
	 * Prints the stacktrace to {@link System.err} and to a stacktrace file. In
	 * addition to that, the stacktrace is displayed in an error window. The
	 * printouts for the file and the error window are prepended with some
	 * useful debug information about the program.
	 * <p>
	 * This method creates its own display, and should therefore be called
	 * either before the application's display has been created, or after the
	 * application's display has been disposed. In between,
	 * {@link #showStackTrace} should be used instead.
	 */
	public static void showStackTraceInOwnDisplay(Throwable throwable) {
		checkConstInitialized();
		ensureNoDisplay();
		Display display = new Display();
		showStackTrace(display, throwable, null);
		display.dispose();
	}

	/**
	 * Prints the stacktrace to {@link System.err} and to a stacktrace file. In
	 * addition to that, the stacktrace is displayed in an error window. The
	 * printouts for the file and the error window are prepended with some
	 * useful debug information about the program.
	 * <p>
	 * It is safe to call this method from a non-GUI thread. The method should
	 * not be called before the first display has been created. In the latter case
	 * {@link #showStackTraceOnStart} should be used instead.
	 */
	public static void showStackTrace(Throwable throwable) {
		checkConstInitialized();
		ensureDisplay();
		showStackTrace(Display.getDefault(), throwable, null);
	}
	
	/**
	 * Prints the stacktrace to {@link System.err} and to a stacktrace file. In
	 * addition to that, the stacktrace is displayed in an error window. The
	 * printouts for the file and the error window are prepended with some
	 * useful debug information about the program.
	 * <p>
	 * The file argument can be used to indicate that the crash was caused by a
	 * specific file.
	 * <p>
	 * It is safe to call this method from a non-GUI thread. The method should
	 * not be called before the first display has been created. In the latter case
	 * {@link #showStackTraceOnStart} should be used instead.
	 */
	public static void showStackTrace(Throwable throwable, @Nullable File file) {
		checkConstInitialized();
		ensureDisplay();
		showStackTrace(Display.getDefault(), throwable, file);
	}

	private static void showStackTrace(final Display display, final Throwable throwable, @Nullable File file) {
		// Print stacktrace to System.err
		throwable.printStackTrace();

		// Prepend useful program info to the stacktrace
		StringBuilder sb = new StringBuilder();
		sb.append("program.name=" + Const.PROGRAM_NAME.value + Util.LS);
		sb.append("program.version=" + Const.PROGRAM_VERSION.value + Util.LS);
		sb.append("program.build=" + Const.PROGRAM_BUILD_DATE.value + Util.LS);
		sb.append("program.portable=" + Const.IS_PORTABLE.asBoolean() + Util.LS);
		String[] keys = {
				"java.runtime.name",
				"java.runtime.version",
				"java.version",
				"sun.arch.data.model",
				"os.arch",
				"os.name",
				"os.version",
				"user.language"
		};
		for (String key : keys)
			sb.append(key + "=" + System.getProperty(key) + Util.LS);
		if (file != null) {
			sb.append("file=" + file.getPath() + Util.LS);
		}

		// Get stacktrace as string
		StringWriter writer = new StringWriter();
		throwable.printStackTrace(new PrintWriter(writer));
		sb.append(writer.toString());
		final String trace = sb.toString();

		// Write stacktrace to file
		String timestamp = new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date());
		String traceFilename = "stacktrace_" + timestamp + ".txt";
		final File traceFile = new File(getAppDataDir(), traceFilename);
		try {
			Files.write(trace, traceFile, Charsets.UTF_8);
		}
		catch (IOException e) {
			e.printStackTrace(); // We'll give up here
		}
		
		// Show stacktrace in error window
		UtilGui.runSwtSafe(display, new Runnable() {
			public void run() {
				/*
				 * We don't want to fill up the user's workspace with hundreds
				 * of crash windows. Note that this check only works if it's run
				 * inside the SWT thread.
				 */
				if (StackTraceWindow.windowCount >= 5) {
					return;
				}
				
				StackTraceWindow window = new StackTraceWindow(display);
				window.setTitle(throwable.getClass().getSimpleName());
				String path = Util.getSystemAbsPath(traceFile);
				String link = String.format("<a href=\"%s\">%s</a>", path, path);
				String msg = Messages.program_died_stacktrace_written.format(link);
				window.setText(msg);
				Image icon = display.getSystemImage(SWT.ICON_WARNING);
				window.setTitleImage(icon);
				
				/*
				 * It appears that when you paste a stracktrace with Windows
				 * newlines into the text field of a SourceForge.net bug report,
				 * the newlines will end up being duplicated. The workaround is
				 * to use Linux newlines in the stacktrace window.
				 */
				window.setStackTrace(Util.ensureLinuxLineSep(trace));
				
				window.open();
			}
		});
	}

	/**
	 * Returns a directory where the program may store data. The directory is
	 * created if necessary. The rules for choosing the directory are as
	 * follows:
	 * <p>
	 * <ul>
	 * <li>If the {@code portable} flag was set, the current working directory
	 * is returned.
	 * <li>If the {@code is development version} flag was set, the "bin"
	 * directory under the current working directory is returned.
	 * <li>Otherwise, the returned directory is platform-dependent: On Windows,
	 * the application data folder + program name is returned, on Linux the home
	 * folder + dot + lowercase program name.
	 * </ul>
	 */
	public static File getAppDataDir() {
		checkConstInitialized();
		
		if (appDataDir != null)
			return appDataDir; // Return cached value
		
		String appDataDirOverride = System.getenv("DOCFETCHER_HOME");
		if (appDataDirOverride != null) {
			File appDataDir = Util.getCanonicalFile(appDataDirOverride);
			if (!appDataDir.exists())
				appDataDir.mkdirs(); // may fail
			if (appDataDir.isDirectory()) {
				AppUtil.appDataDir = appDataDir; // Store value in cache
				return appDataDir;
			}
		}
		
		String programName = Const.PROGRAM_NAME.value;
		File appDataDir = null;
		if (Const.IS_DEVELOPMENT_VERSION.asBoolean()) {
			// The development flag has higher priority than the portable flag
			// TODO post-release-1.1: Remove this part of the if-clause
			appDataDir = new File("bin");
		}
		else if (Const.IS_PORTABLE.asBoolean()) {
			appDataDir = new File(Const.USER_DIR_PATH.value);
		}
		else if (Util.IS_WINDOWS) {
			// Windows 7/Vista: C:\Users\<UserName>\AppData\<ProgramName>
			// Windows XP/2000: C:\Documents and Settings\<UserName>\Application Data\<ProgramName>
			String winAppData = System.getenv("APPDATA");
			if (winAppData == null)
				/*
				 * Bug #2812637: The previous System.getenv("APPDATA") call
				 * returns null if DocFetcher is started as an alternative user
				 * via the executable's "Run as..." context menu entry. If this
				 * happens, we'll have to fall back to this JNA-based
				 * workaround.
				 */
				winAppData = Shell32Util.getFolderPath(0x001a); // CSIDL_APPDATA = 0x001a
			if (winAppData == null)
				throw new IllegalStateException("Cannot find application data folder.");
			appDataDir = new File(winAppData, programName);
		}
		else if (Util.IS_LINUX || Util.IS_MAC_OS_X) {
			// Linux: /home/<UserName>/.<LowerCaseProgramName>
			// Mac OS X: /Users/<UserName>/.<LowerCaseProgramName>
			appDataDir = new File(Util.USER_HOME_PATH, "." + programName.toLowerCase());
		}
		else {
			throw new IllegalStateException();
		}
		
		appDataDir.mkdirs();
		AppUtil.appDataDir = appDataDir; // Store value in cache
		return appDataDir;
	}
	
	public static boolean isPortable() {
		checkConstInitialized();
		return Const.IS_PORTABLE.asBoolean();
	}
	
	@NotNull
	public static String getImageDir() {
		checkConstInitialized();
		if (Const.IS_DEVELOPMENT_VERSION.asBoolean())
			return "dist/img";
		if (Util.IS_MAC_OS_X && !Const.IS_PORTABLE.asBoolean())
			return "../Resources/img";
		return "img";
	}
	
	@NotNull
	public static File getLangDir() {
		if (SystemConf.Bool.IsDevelopmentVersion.get()) {
			return new File("dist/lang");
		} else if (Util.IS_MAC_OS_X && !SystemConf.Bool.IsPortable.get()) {
			/*
			 * Note: Can't use AppUtil.isPortable here because this method might
			 * be called at a point where AppUtil hasn't been initialized yet.
			 */
			return new File("../Resources/lang");
		}
		return new File("lang");
	}

	/**
	 * Tests if the given directory is writable by attempting to create and
	 * delete a temporary file. This is more reliable than File.canWrite(),
	 * especially on Windows with UAC-protected directories.
	 *
	 * @param dir The directory to test
	 * @return true if the directory exists, is a directory, and is writable
	 */
	public static boolean canWriteToDirectory(@NotNull File dir) {
		if (!dir.exists() || !dir.isDirectory())
			return false;

		File testFile = new File(dir, ".write-test-" + System.currentTimeMillis());
		try {
			if (testFile.createNewFile()) {
				testFile.delete();
				return true;
			}
		} catch (IOException e) {
			// Cannot write to this directory
		}
		return false;
	}

}
