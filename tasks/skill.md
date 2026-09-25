# Skill: building, testing and verifying DocFetcher changes

## Build
- `./mill compile > /tmp/build.txt 2>&1; grep "\[error\]" /tmp/build.txt` (output is long, never read it directly).
- Mill needs JDK 21; the system `java` may be older. A JDK 21 is in the coursier cache:
  `ls ~/.cache/coursier/arc/https/cdn.azul.com/zulu/bin/*/zulu21*/bin/java`. Use that path as `$JAVA` below.
- If Mill fails with `NoSuchFileException: out/mill-daemon/socketPort`, just run the command again (daemon restart).
- Windows package: `python3 build.py windows` (needs `build-jre.txt`), then `git checkout -- dist/` (build.py rewrites line endings).

## Build on Windows
- `./mill` is the Linux/macOS launcher; on Windows use `mill.bat` (from PowerShell:
  `cmd /c ".\mill.bat compile > <scratch>\build.txt 2>&1"`). It downloads Mill and a Zulu JDK 21 into
  `%LOCALAPPDATA%\Coursier\cache\arc\https\cdn.azul.com\zulu\bin\zulu21*\...\bin\java.exe`; use that as `$JAVA`.
- `dev/guitest/run-tests.sh` works in Git Bash too (it switches to `;` as classpath separator and the win32 SWT).
- Windows Python needs `C:/...` paths, not `/c/...`.
- Package on Windows: `build-jre.txt` has `jre_windows=D:/daten/Torsten/Projects/docfetcher-build-jre/windows`, a Java 21
  runtime made with `jlink` from the Zulu JDK (`--add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki,
  jdk.localedata,jdk.charsets,jdk.zipfs,jdk.management,jdk.net,jdk.accessibility --strip-debug --no-header-files
  --no-man-pages`). The manual build needs the Python package Markdown (no `uv` here): use a venv with
  `pip install markdown` and run `PATH="$JDK/bin:$PATH" <venv>/Scripts/python.exe build.py windows` (javac/jar
  come from the PATH). Output: `build/DocFetcher-<version>-Windows-64bit-Portable.zip` and the non-portable folder;
  the installer needs NSIS (`build-win-installer.nsi`), which is not installed. Afterwards `git checkout -- dist/`.

## Unit tests
- JUnit 4 tests live next to the sources (`src/**/*Test.java`) and are compiled by `./mill compile`.
- Run: `dev/guitest/run-tests.sh $JAVA net.sourceforge.docfetcher.model.ocr.ImagePagesTest net.sourceforge.docfetcher.model.ocr.OcrTextEmbeddingTest`
- Model code that needs `AppUtil.Const` (e.g. `FileIndex`) must call `AppUtil.Const.autoInit()` first.
- Existing tests worth running after touching parsers/OCR: `ImagePagesTest`, `OcrTextEmbeddingTest`, `PdfParserTest`, `MSOffice2007ParserTest`.

## Application (GUI) tests
- Never use the user's display (`:0`). `dev/guitest/run-dev.sh $JAVA` starts Xephyr on `:2` and the dev build
  (cwd = project root; settings in `bin/settings-conf.txt`, indexes in `bin/indexes`). German UI: add
  `-Duser.language=de -Duser.country=DE` and put `dist/lang:lang` on the classpath.
- Drive it with `DISPLAY=:2 python3 dev/guitest/xin.py move X Y | click | rclick | dclick | key KEYSYM |
  ckey KEYSYM (with Ctrl) | type TEXT` (python-xlib XTEST; keyboard-layout aware, so `/` and `_` work on a
  German layout).
- The global shortcuts (F1, F3, Ctrl+F, Ctrl+Q, Ctrl+Z) do not fire on `:2`, because Xephyr runs without a
  window manager and the shell is never the active shell. Test them through the equivalent menu item
  (e.g. `File > Undo file operation` for Ctrl+Z); shortcuts of a widget, such as F2 in a result table, work.
- Screenshots: `DISPLAY=:2 python3 dev/guitest/shot.py out.png [x y w h]`, then look at the PNG.
- GTK folder dialogs: type the absolute path and press Return.
- Stop everything afterwards: `dev/guitest/stop-dev.sh` (rule: no processes left running after a task).
- Test data: create scans with PIL (see `ImagePagesTest` for a programmatic TIFF); the OCR test image is a
  300-dpi rendering of an invoice text; drop files into an inbox folder to see them in the inbox tab.

## Application (GUI) tests on Windows
- `dev/guitest/run-dev.sh $JAVA` starts the dev build on the desktop (settings `bin/settings-conf.txt`, a Java
  properties file; write it while the program is not running, e.g. `InboxFolders = C:/.../inbox|Inbox` and
  `ShowManualOnStartup = false` - an inbox folder is indexed automatically, which gives test documents).
- Drive it with `python dev/guitest/win.py list | shot OUT.png [TITLE] | click X Y [TITLE] | dclick | rclick |
  key NAME [TITLE] | ckey NAME | type TEXT | close TITLE`: PrintWindow screenshots and posted window messages,
  so the user's mouse and keyboard are not touched. Coordinates are window-relative, as in the screenshot.
- In-process SWT tests: `GuiTestSupport` (gui package) creates a display, an indexed temp folder and
  screenshots (`-Ddocfetcher.test.shots=<dir>`). On Windows the index files stay locked, so cleanup is best-effort;
  remove leftover `%TEMP%\docfetcher-*` test folders afterwards.
- `win.py ckey A` works (Ctrl is set in the key state shared with the program's thread via AttachThreadInput);
  use it for Ctrl+A to select several rows. SWT's virtual tables expose no rows to UI Automation.
- Email: the default MAPI client is in HKCU\Software\Clients\Mail (here Thunderbird). The compose window
  'Verfassen: <subject> - Thunderbird' can be checked with `win.py shot` and closed with `win.py close` (no prompt).
- Message boxes have no title: `win.py press OK` (WM_COMMAND to the program's own dialogs; never FindWindow('#32770'),
  which may hit another program's dialog). Submenus: `win.py menu View "Icon size>100"`.
- A second `run-dev.sh` while the program still runs only brings the first instance to the front; check
  `tasklist` for the Windows PID and exit with `win.py menu File Exit` (saves the settings).
- `ct.sh`-style scripts that start the Mill daemon must write to a file, not a pipe (the daemon keeps the pipe open).
- Menu entries: `win.py menu File Undo` (WM_COMMAND with the item id); global shortcuts like Ctrl+Z don't fire
  while the window is in the background. Modifier keys: `win.py key SHIFT+DOWN`, `key CTRL+A`.
- After `stop-dev.sh` (hard kill) wait until `win.py list` shows the main window before clicking: a question
  about another running instance may come first, and a blind click answers it.
- Merge/split tests move test files to the real recycle bin; in unit tests call `UtilFile.setTrashDisabled(true)`.
- The Windows 11 print dialog belongs to explorer.exe and ignores posted clicks: `win.py invoke "Drucken" Drucken`
  (UI Automation). With 'Microsoft Print to PDF' a classic save dialog follows: `win.py save "speichern unter" C:/x.pdf`.
  Never print to a real printer; in unit tests use `DocumentPrinter.findPrinter("Microsoft Print to PDF")` with
  `printToFile`. Documents that are not PDF/images go to the default printer without a dialog - don't test those.
- Stop with `dev/guitest/stop-dev.sh`; stop the Mill daemon at the end: `cmd /c ".\mill.bat shutdown"`.

## Checks before committing
- Every `Msg` key needs a German entry in `dist/lang/Resource_de.properties` (Latin-1, `\t` escapes):
  compare `^\t(key) \(` in Msg.java with `^key=` in the properties file.
- One commit per task item (`git -c user.name=Torsten -c user.email=claude@thoermann.net commit`), no push.
- Move finished items from `tasks/tasks.md` to `tasks/tasks_done.md`; add a changelog line in
  `website/content/extra/changelog.txt`; update the EN/DE manuals under `doc/src` when the UI changes.
