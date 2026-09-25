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

## Checks before committing
- Every `Msg` key needs a German entry in `dist/lang/Resource_de.properties` (Latin-1, `\t` escapes):
  compare `^\t(key) \(` in Msg.java with `^key=` in the properties file.
- One commit per task item (`git -c user.name=Torsten -c user.email=claude@thoermann.net commit`), no push.
- Move finished items from `tasks/tasks.md` to `tasks/tasks_done.md`; add a changelog line in
  `website/content/extra/changelog.txt`; update the EN/DE manuals under `doc/src` when the UI changes.
