# tasks done

- auto size the columns in result table based on available size, after window resize
  - Result table columns are scaled proportionally to fill the visible width on every table resize (window, preview or filter panel resize) and after new results. Stored widths serve as initial proportions; dragging a column border updates the proportions.
- right pane is default, also apply after pane was hidden (when done, it reappears bottom).
  - Default preview position is now right of the results. While the preview is hidden, only the arrow that restores it to its last position is shown, so it always reappears where it was; switching the position is done while it is visible or via the settings dialog.
- document types and search area as modal, reachable via search setting option (right from search button)
  - New modal 'Search Settings' dialog (button right of 'Search') holding the Document Types list and the Search Scope tree; filter changes apply immediately. The left panel is removed from the main window; the file-size filter was dropped as agreed. 'Manage Indexes...' and 'List Documents' close the dialog.
- add in settings per folder flag to autorefresh (if not generally available): for these folders check for changes and refresh index. File might have been renamed, added, removed or changes (e.g. office docs). Update index accordingly
  - Already available per index as 'Watch folders for file changes' (enabled by default): file create/delete/rename/modify events trigger a background index update while DocFetcher runs; the daemon covers changes while it is closed.
  - New: 'Update indexes on startup' option (General tab, on by default) updates all watched indexes in the background at startup, so offline changes are picked up even without the daemon.
  - Fix: the daemon's indexes file is always written to the location the daemon expects, so the hand-off keeps working with a custom index folder.
- add option: start with windows, close to minimize to task bar. Add option to exit app
  - 'Start DocFetcher with Windows' checkbox (General tab, Windows only): creates/removes a per-user startup entry (HKCU Run key) for DocFetcher.exe; the program starts as a normal window.
  - 'When closing the program window' dropdown: Exit / Minimize to taskbar / Hide in system tray (replaces the tray checkbox; old setting migrated).
  - Exit: new toolbar button (power icon) and Ctrl+Q; both exit regardless of the close setting. The system tray menu keeps its Exit entry.
- for result table, move date to 2nd column, remove author, remove size, remove "treffer (%)", add pages as col if known after date
  - Result table columns are now Title, Last Modified, Pages, Filename, Type, Path. The page count is recorded while indexing for PDF (page count), Word/OpenDocument text (page count from document metadata) and PowerPoint (slide count); other formats and indexes created before this change show an empty cell until the index is rebuilt.
- at least provide complete german translation
  - All 38 previously untranslated UI strings translated in Resource_de.properties; the German manual (main page and preferences page) updated for the new preferences dialog, search settings dialog and index management.
- add page count per document. on indexing, store page per text extracted and add to index metadata, then show in table. currently is always empty. Maybe is other reason
  - Cause: page counts are only recorded when a document is parsed by 2.0; an index Update skips unchanged files. Indexes now carry a format version, and indexes created before 2.0 trigger a one-time prompt at startup (Rebuild Now / Later / Don't Ask Again); the rebuild runs in the background.
  - Additional sources: RTF (\nofpages from Word's info group) and a fallback for Word documents without page metadata (counts rendered/explicit page breaks). Verified: PDF 3, DOCX with page breaks 4, RTF 6.
- add check to run the app only once at a time
  - The running instance listens on a loopback socket (port stored in the lock file). Launching a second instance brings the running one to the front (restoring it from tray/minimized) and exits silently; a stale lock file (after a crash) no longer causes the 'launch another instance?' dialog. CheckSingleInstance in program-conf.txt still disables the check.
- story: as user, i want to move a rename a document in result list (F2, right click).
  - F2 / context menu 'Rename...' opens a rename dialog (name preselected without extension). The file is renamed on disk, the row and preview update immediately, the containing index is updated in the background. Not available for emails and archive entries.
- story: as user, I want to move a document into another folder on the system. The system therefore should assist by making suggestions, based on statistical use of keywords use in the current doc and the documents per folder. Also drag and drop into explorer should be supported (windows)
  - Context menu 'Move to Folder...' opens a dialog with up to 5 suggested folders (Lucene 'more like this' over the indexed documents: folders of the most similar documents, ranked and shown with a match percentage) plus Browse. Multi-selection supported; source/target indexes are updated afterwards.
  - Drag & drop from the result table into other programs (Explorer): move by default; Ctrl+drag copies and DocFetcher then asks whether to keep the original. Rows of moved-away files are removed and the indexes updated. Explorer interaction itself could only be implemented, not tested, on Linux.
- improvements (chat, 2026-09-21): move dialog resizable with remembered size and auto-fit columns; setting for path segments shown for suggested folders (default 2); file name matching checkbox in search settings
  - Move dialog: size/position persisted (ShellBounds), match column fixed, folder column fills the rest. New preference 'Path segments shown for suggested folders' (1-5, default 2) renders e.g. 'testdocs/nature  (/rest/of/path)'.
  - Search Settings: 'Also match file names (any part of the name)' (default on): search terms additionally match file names as case-insensitive substrings; toggling re-runs the current search.
  - Fixed on the way: the 'Indexing...' status indicator could stay visible forever when all startup update tasks were rejected as redundant; hiding a modal dialog left the keyboard focus dangling on GTK.
- move modal: split the first col into 2: first col with the last x parts of path, 2nd with the initial parts
  - 'Move to Folder' dialog now has Folder (last N segments), Location (leading part) and Match columns; the folder column is sized to its contents, the location column takes the remaining width.
- add folder list view (inbox): select a folder and list the files; rename, move, preview; inbox folder setting; search view as second tab; rename pattern <date>_<content> with 1-4 most relevant words
  - 'Inbox folder' setting (Indexes tab). When set, an 'Inbox' tab (first tab, German 'Postbox') lists the files directly in that folder using the result table (preview, F2 rename, Move to Folder, drag & drop); the folder is indexed automatically, the list refreshes after indexing and via a Refresh button; the selection survives refreshes.
  - Name suggestions: 'Rename To' submenu with names of the form <ISO date>_<Word-Word-...> using 2 to 5 keywords; 'Rename...' dialog prefilled with the 3-word suggestion plus clickable keywords. Date = labelled ('Datum'/'Date') or first date in the text (dd.mm.yyyy, yyyy-mm-dd, d. Monat yyyy, Month d, yyyy), else file date. Keywords = TF-IDF terms (Lucene MoreLikeThis) against all indexes, stopwords/boilerplate removed, strong preference for the first lines (sender/subject), original casing, capitalized. Suggestions are precomputed in the background on selection.
- postbox: add as initial 2 columns an icon for rename (pen) and one for move (arrow)
  - Inbox table starts with two fixed-width icon columns; clicking the pen opens the rename dialog (suggestion computed on demand if not cached yet), clicking the folder/arrow icon opens the Move to Folder dialog.
- remove the type icon (doc type is seen in extension); rename must not change the extension
  - Title column shows no file-type/email icon anymore. The rename dialog edits only the base name and shows the extension as a fixed label; all rename paths keep the original extension.
- similar to "Rename To": add a "Move To" submenu with the top 5 target folders and an entry that opens the manual folder selection, in search and inbox
  - 'Move To' submenu in the result table context menu (both tabs): up to five suggested folders (same keyword similarity as the dialog, computed in the background together with the name suggestions) that move the selected files with one click, plus 'Other Folder...' opening the dialog.
- remove the "try docfetcher pro" from UI
  - Removed the 'Try DocFetcher Pro' status bar link and the one-time hint overlay (plus the related setting and strings).
- support multiple inbox folders in settings, and add them if set as additional tab. let user select a name, if empty use the last part of folder name
  - Preferences > Indexes: list of inbox folders (Add.../Remove/Rename...); each folder gets its own tab before the Search tab, named after the last path segment unless a name is set. Setting InboxFolders (path|name entries) replaces InboxFolder, which is migrated at startup. Inbox folders are excluded from the folder suggestions. The preferences dialog grows to its preferred size if the stored size is too small.
- add preview for png/jpg; in inbox show all files, regardless of being indexed (allows manual rename and move)
  - Image preview (PNG, JPEG, GIF, BMP, TIFF; SWT ImageLoader with ImageIO fallback) fitted to the pane width with zoom buttons and external launch. Inbox tabs list every regular file in the folder; files without index entry get a result document built from the file system (title, date, size, type), so rename/move/preview work before indexing; the list refreshes after file operations.
- move the controls for settings and app close/minimize to a usual place in the app where they are always visible (were only on the search tab)
  - Native menu bar: File (Preferences..., Indexes..., Minimize To System Tray, Exit Ctrl+Q) and Help (Manual F1, About). The help/preferences/tray/exit icons were removed from the search bar; Ctrl+Q and F1 keep working via the global key filter.
- v2.4 OCR (bundled Tesseract, de+en) for images and PDFs without text; TIFF and text-less PDFs are converted to PDFs with a text layer, JPG/PNG get the recognized text embedded so it is not OCRed again
  - model/ocr: OcrEngine (JNA binding of the Tesseract C API, bundled DLLs + tessdata_fast deu/eng), SearchablePdf (invisible text layer via PDFBox, TIFF->PDF), OcrTextEmbedding (PNG iTXt / JPEG COM segments, mtime preserved), OcrPreprocessor (runs in FileIndex for new/modified files). ImageParser replaces ExifParser for images and reads the embedded text. Preference 'Recognize text...' (default on) with reason shown if the engine can't load. Index format version 2 triggers the one-time rebuild prompt. build.py packages lib/tessdata. Version bumped to 2.4.
- in inbox and search table, remove the title and use the filename instead; drop the filename column (duplicated, eats up space)
  - First column is now 'Filename' (subject for e-mails); the separate Filename/Sender column was removed. Column width defaults adjusted; stored widths/orders with the old column count fall back to the defaults.
- bugfix: document reported as "not found" although it exists (name with unusual chars)
  - Cause: accented characters stored in decomposed Unicode form (NFD) on disk while the index stores composed names; Path now resolves to the equivalent name on disk (recursively for folders), and the inbox listing matches files by normalized paths.
- bugfix: docx preview failed with XmlValueOutOfRangeException ('start' is not a valid value for ST_Jc)
  - POI 3.17 doesn't know the 'start'/'end' alignments of newer Word files; the renderer now reads them from the raw XML, tolerates unknown underline/vertical-alignment values, and falls back to plain text for a paragraph POI can't handle.
- bug: the scan settings modal can be opened from menu, from search tab and bottom right -> only via menu bar
  - Removed 'Manage Indexes...' from the search-scope context menu and the click on the status bar indicator; index management/settings only via File > Preferences... / Indexes....
- show index status (in %) per folder in index settings; general progress bar bottom right
  - Indexing tasks report visited/total files (files are counted before the walk); Task.getProgressPercent() and IndexingQueue.evtProgress. Status bar shows a progress bar with the overall percentage of the current batch (finished tasks + running fractions) and a per-index tooltip. Preferences > Indexes has a Status column (Waiting/Queued/Creating|Updating|Rebuilding n %/Updated <time>); indexes being created are listed while their task runs. TreeIndex stores lastUpdated.
- garbage collection of vanished documents
  - After each search, results whose files no longer exist (and whose index root is available) are dropped and an update of the affected index is queued (first 2000 results checked). New option 'Update all indexes once a day in the background' (default on). Also fixed Path's file predicate accepting folders.
- ocr for png/jpg and add text to metadata
  - Covered by the 2.4 OCR feature: PNG iTXt chunk / JPEG COM segment keyed DocFetcherOCR. Confirmed: no need to expose the text in Windows Explorer (EXIF/XMP), so nothing more to do.
- bug: new index shows "waiting for config" and the indexing window can't be reached anymore from bottom right
  - The state means the 'Run' button of the indexing window hasn't been pressed (window was minimized). Status text now says so; the status-bar progress indicator is clickable again and shows the indexing window (queue/config/errors, not settings), and double-clicking an index with a task on the Indexes tab does the same.
- add an option to enable the TIFF to PDF conversion (disabled by default); when enabled, option to rename the TIFF to .tif.ocr instead of deleting it
  - Settings ConvertTiffToPdf (off) and KeepTiffAfterConversion (on) with two General-tab checkboxes; without conversion, TIFFs are OCR-read at parse time like other images.
- bug: tiff ocr crashes app (ArrayIndexOutOfBoundsException on an archive TIFF), does not continue
  - ImageIO's TIFF decoder fails on some fax-compressed archive files. ImagePages now falls back to the bundled Leptonica library (JNA: pixReadTiff/pixRead, pixels unpacked to 8-bit gray), and ImageParser turns any decoder/OCR RuntimeException into a ParseException, so the file lands in the indexing error list instead of a stack-trace window and indexing continues.
- if OCR is enabled, reorder the processing to queue these at the end of the process to import the others first
  - Indexing runs in two passes: the walk indexes everything with recognition disabled (ParseContext.ocrAllowed=false; images without embedded text and PDFs without text are collected as deferred files), then FileIndex.processDeferredOcr recognizes/writes back and re-indexes them (TIFF->PDF replacement included). Archive entries are recognized inline. Progress counts deferred files in the second pass.
- add configuration to define fixed move targets, which always show in move and context menus on top
  - New preferences tab 'Folders' (inbox folders moved there from the Indexes tab, plus 'Fixed move targets' with Add/Remove; setting StrList.MoveTargets). The 'Move To' submenu lists them first (separator, then suggestions minus duplicates, then 'Other Folder...'); the move dialog lists them on top with 'fixed' in the Match column and preselects the best content match.
- bug: tiff file viewer not available
  - The image preview used SWT's limited TIFF loader and then ImageIO (which fails on archive TIFFs). TIFFs now go through the shared ImagePages reader (ImageIO, Leptonica fallback), all pages are loaded with Prev/Next page buttons and "Page x of y"; pages are converted on demand with a fast path for gray images. Unit tests: ImagePagesTest, OcrTextEmbeddingTest.
- remember last 5 folders used to move as offer
  - MoveTargets.addRecent/loadRecent (setting StrList.RecentMoveTargets, newest first, max 5, fixed targets and vanished folders skipped); recorded on every successful move. Menu and dialog list fixed > recent > content suggestions without duplicates ('recently used' in the Match column). Unit test MoveTargetsTest.
- in search and folder list table, remove the path as col, add hover info and show it in the app frame bottom when a line is selected
  - The column shows the folder only (header 'Folder'/'Ordner'), full path as table tooltip and in the status bar (search and inbox tabs). New option 'Show the folder of a document as a column' (default on) hides it live via a zero column weight; stored column widths now tolerate a changed column count.
- if "ocr enabled", will it write the OCRd text into the original PDF, or only to index? add option
  - It did always write into the files. New option 'Store the recognized text in the files' (default on); with it off, OcrPreprocessor leaves files untouched and the parsers recognize on the fly (PdfParser now OCRs text-less PDFs itself), so the text only goes into the index. Verified: files stay byte-identical, search still finds the scans.
- when tiff convert is enabled, make sure the PDF is not larger than the tiff
  - Black and white pages are embedded with their original CCITT (fax) compression via CCITTFactory instead of being re-encoded (Flate); other pages use JPEG at 0.7. Test SearchablePdfTest checks text and PDF size against the TIFF size.
- v2.5: add tab "Folders" (norton commander style browser over the index folders)
  - FolderPanel with path bar, Up/Refresh and the shared ResultPanel; root level lists index roots + fixed move targets; Searcher.listFolderEntries returns folder rows (ResultDocument.isFolder) followed by all files of the folder. Folders sort above files via a new group comparator in VirtualTableViewer, double-click enters a folder, rename/move are disabled for folder rows, last folder is remembered (SettingsConf.Str.LastBrowsedFolder). Test FolderBrowsingTest.
- v2.5: move a doc into another folder by drag and drop
  - Drop target on the result table: files dropped on a folder row are moved there (Ctrl copies), including files dragged in from other programs; the target becomes the newest recent move target. Verified in the GUI by dragging a file onto a folder row.
- in folder view: add top ".." row to go up, support keyboard "arrow left" to go back
  - The listing starts with a '..' row (ResultDocument.isParentRow, sorted above the folders); opening it, the Up button, the left arrow key and backspace go to the parent folder, and from a root folder back to the list of root folders.
- drag/drop use case: move a file from the inbox into another folder, with the folder view visible in the same window
  - 'View > Folder panel' (F9, setting ShowFolderPanel) moves the folder browser out of its tab into a panel of its own between the tabs and the preview (SashForm, weights remembered in FolderSashWeights); the Folders tab disappears while it is shown. Dragging a document from an inbox tab onto a folder row of the panel moves it there (verified in the GUI), and the preview follows the last click in either panel. The folder column is hidden in the browser, since the path bar shows the folder.
- when disabling the path column, consider this in the auto-size of the remaining columns
  - Root cause: the automatic column fitting wrote its computed widths back into the stored column widths (SettingsConf.ColumnWidths.bind), so the remembered widths collapsed to the minimum while the column was hidden. The result tables now restore the stored values themselves and save the column *weights* (only changed when the user drags a border); hidden columns keep their weight but are left out of the width distribution, so the freed space goes to the other columns.
- in folder view, add "delete" and "add folder"; delete also in the inbox (with confirmation)
  - 'Delete...' (Del key) in the context menu of every result table: confirmation naming the files, then UtilFile.delete moves them to the recycle bin (Desktop.moveToTrash) or, without one, deletes them permanently, which the confirmation says. 'New Folder...' button in the folder browser creates a subfolder; names are taken as typed and only rejected if they contain characters that are invalid in file names. Test UtilFileTest.
- in search table, context menu "show in folder" and a setting for folder sync
  - 'Show in Folder View' opens the document's folder in the folder browser and selects it there (switching to the Folders tab if the browser isn't shown as a panel); 'Open Parent Folder' still opens the system file manager. New option SyncFolderView (on by default) makes the browser follow the selected search result.
- detached view to be default
  - SettingsConf.Bool.ShowFolderPanel now defaults to true.
- when drag/drop, allow dropping inside the current folder
  - The drop target of the folder browser falls back to the folder currently shown, so files dropped on a file row or on the free space below are moved into it.
- in folder pane, add icon to jump to root
  - Toolbar button (folder icon) that returns to the list of indexed folders and fixed move targets.
- change confirmation to buttons yes / no; add Ctrl+Z (undo) for disaster recovery
  - AppUtil.showConfirmation uses SWT.YES | SWT.NO. New FileOperationHistory (max 20 operations of the running program) records renames, moves (menu, drag and drop, files dragged in from other programs) and deletions; Ctrl+Z or 'File > Undo file operation' moves the files back after a confirmation and refreshes the inbox and folder views. Deletions can't be undone automatically (the files are in the recycle bin), so the undo says so and drops the entry. Tests: FileOperationHistoryTest; verified in the GUI (move undone, delete message, Yes/No buttons).
- folder view: default sort "date desc", remember the chosen sort across folders
  - The folder view sorts by 'Last Modified' descending by default; sorting by a column header is stored (SettingsConf.Int.FolderSortColumn, Bool.FolderSortAscending) and re-applied on every folder change and after a restart. VirtualTableViewer got evtSorted plus getters for the sort state.
- highlight the search terms in the preview pane for docx, pdf etc.
  - Word, Excel and RTF previews already highlighted the terms. PDFs are shown by the browser's own viewer, which now receives the terms as a '#search=' open parameter (Edge/Chrome/Acrobat), so it marks them and jumps to the first match; terms with wildcards or spaces are left out. Test PdfViewerParamsTest. Only testable on Windows, since Linux uses the PDFBox fallback viewer.
- bugfix: the search term was not highlighted in the PDF preview on Windows
  - Edge's PDF viewer ignores the '#search=' open parameter. The matches are now marked in a temporary copy of the PDF as highlight annotations (PdfHighlighter, with appearance streams so every viewer draws them), which the preview displays instead of the original; '#page=' resp. the image viewer's start page opens the page of the first match. Whole-word matching, at most 2000 marks, original file untouched, temp copy deleted with the next preview. Also fixed: showing a search hit in the folder view replaced the preview with the folder entry (which has no search terms), so the marks disappeared - the folder view now selects without firing a selection event. Tests: PdfHighlighterTest, PdfViewerParamsTest.
- the folder panel next to the tabs is a second browser; the 'Folders' tab remains
  - Both browsers exist at the same time and work identically but independently (own folder, own drag and drop), so files can be dragged from one folder into another. Each remembers its own folder (Str.LastBrowsedFolder / LastBrowsedFolderPanel); the sort order and everything else is shared behaviour. The folder sync of the search results only moves the panel, not the tab.
- change the shortcut to F3
  - F9 replaced by F3 for showing/hiding the folder panel, in the global keys and in the View menu.
- make the icons larger on HiDPI screens
  - New option 'Icon size' (Int.IconScalePercent): automatic (from the display DPI: 125/150/200 % above 120/132/168 dpi) or fixed 100/125/150/200 %. LazyImageCache scales the loaded images smoothly (antialiasing + high interpolation); the action icon columns follow the icon width. Takes effect after a restart. Test LazyImageCacheTest (skips without a display).
- add support to rename a folder (F2, context menu); currently an error shows up
  - The rename in the result table now handles folder rows: `F2` and `Rename...` open a dialog with the folder
    name, the name is taken as typed and only checked (`UtilFile.isValidName`: no empty name, no `. / \ : * ? " < > |`
    or control characters), the folder is renamed on disk, the affected indexes are updated and the operation
    goes into the undo history. The root folder of an index is refused with an explanatory message, since its
    index would have to be created again. Bugfix on the way: the undo only moved files back (`isFile()`), so
    renamed folders were skipped, and the undo never updated the indexes. Tests: `UtilFileTest`
    (`checksNamesForFilesAndFolders`), `FileOperationHistoryTest`; in the GUI: index root refused, rename via
    F2 and via the context menu (with a space in the name), invalid name rejected, undo through
    `File > Undo file operation` (Ctrl+Z can't be tested on Xephyr, see `skill.md`).
- folders need to be renamed and deleted like files (currently an error)
  - Renaming was done in 2.5. Deleting now works for folder rows as well: `Delete...` in the context menu and
    the `Del` key move a single selected folder with its contents to the recycle bin after a confirmation that
    names the folder and the number of files inside (`UtilFile.countFiles`); without a recycle bin the folder is
    deleted permanently, which the confirmation says, and the new `UtilFile.deletePermanently` removes the
    contents first. The root folder of an index is refused with an explanation, as when renaming (shared
    `isIndexRoot`). Also fixed: German messages containing a file or folder name printed the placeholder `{0}`,
    because MessageFormat treats a single quote as an escape - the German file now uses `''{0}''` everywhere.
    Tests: `UtilFileTest` (7 tests); in the GUI: folder with subfolder and 2 files deleted (confirmation counted
    2 files), index root refused.
- search also should list folders
  - New `FolderSearch` (model/search): `extractTerms` reduces a query to its plain words (operators, field
    names, wildcards, quotes and single characters dropped), `find` walks the root folders of the folder
    browser breadth-first and returns the folders whose name contains all the terms, capped at 50 hits and
    20000 visited folders. `SearchQueue` puts those folder rows in front of the document hits, where the
    group comparator of the result table keeps them; the type and location filters don't apply to them.
    `FolderPanel.getRootFolders(IndexRegistry)` is now static, so the search reuses the browser's root list.
    A double-click on a folder hit opens it in the visible folder browser (`evtFolderOpened`). Tests:
    `FolderSearchTest`; in the GUI: search for "rechnung" listed "Rechnungen 2026" above the documents and
    the double-click opened it.
- remove the arrows that make the doc view pane invisible
  - The three thin arrow buttons of `TwoPanelForm` (hide the preview pane, move it to the right or to the
    bottom) and the `ThinArrowButton` class are gone; the layout code got simpler with them. The preview pane
    is now shown and hidden with `View > Preview panel` (F4, a check item that follows the stored setting),
    its position stays in the preferences. Application test: arrows gone, menu entry hides and shows the pane
    and its check mark follows.
- add larger handles in the split between the panes to easily grab and change the size
  - The sashes of the main window are 9 pixels wide instead of 5, and `UtilGui.paintSashHandles` draws three
    dots in the middle of each one, so the separator can be seen and grabbed. The sashes of a `SashForm` are
    created while it is laid out, so the painter is attached on resize (and once more via asyncExec, after the
    layout). Test `UtilGuiTest` (skips without a display); in the GUI: grip visible on both sashes, dragging
    the separator still resizes the panels.
- on folder also support "open in explorer"
  - New context menu entry for folder rows, labelled `Open in Explorer` on Windows and `Open in File Manager`
    elsewhere (like the existing `Copy` / `Copy (Mac OS X)` pair). It hands the selected folders to
    `MultiFileLauncher`, the same launcher `Open Parent Folder` uses, and is only enabled when every selected
    row is a real folder (not the `..` row). Application test: entry shown and enabled on a folder row,
    launching it reported no error.
- by default disable archive (zip, jar) search
  - `IndexingConfig.defaultZipExtensions` is empty now, so a new index treats zip and jar files as ordinary
    files; the extensions can be entered in the indexing dialog, and existing indexes keep the extensions
    stored with them (the config is serialized with the index). An index whose root is an archive file still
    adds that extension by itself (`FileIndex`). Test: `IndexingConfigTest.doesNotLookIntoArchivesByDefault`;
    in the GUI: the 'Zip archives' field of a new index was empty, and after indexing a folder with a zip, a
    word inside the archive found nothing while a word in the file next to it was found.

- as user, i want to print a document (v2.6)
  - `Print` in the new toolbar, `Print...` in the context menu of every result table and Ctrl+P print the selected files. `DocumentPrinter`: PDFs and images (png, jpg, gif, bmp, tif) get one SWT print dialog (printer, page range, copies) and one print job; pages are rendered with PDFBox (at most 300 dpi) and printed in their real size, shrunk to the printable area if bigger. Other files go to the program registered for printing (`Desktop.print`, default printer). `UtilGui.toImageData` is now shared by the previews and the printer.
    Tests: `DocumentPrinterTest` (page placement, file types, printing 2 PDF pages + 1 image and a page range into a PDF with 'Microsoft Print to PDF'); in the GUI: toolbar button opened the Windows print dialog, printing to 'Microsoft Print to PDF' produced the 2-page invoice.
- as user, i want to send every document or multiple seelcted documents as email (v2.6)
  - `Send by Email` in the toolbar and in the context menu of every result table opens a new message in the default email program with all selected files attached and their names as subject. `MailSender` uses Simple MAPI (`MAPISendMailW` from mapi32.dll via JNA, always with MAPI_DIALOG, so nothing is sent automatically) in a background thread; Linux uses `xdg-email --attach`, macOS the Mail app.
    Tests: `MailSenderTest` (layout of the MAPI message with two attachments and umlauts); in the GUI: Ctrl+A + toolbar button opened a Thunderbird compose window with the 4 inbox files attached.
- as user I scan a staple of document as one, and want to merge and split the documents easily (v2.6; S3a merge, S3b split, S3c automatic split)
  - `PdfPages` (model/pdf): merge (PDFMergerUtility), split into page ranges (importPage, like PDFBox's Splitter), ranges for each page / at page numbers / at blank pages, numbered part names (`name_1.pdf`, or `name-2_1.pdf` if taken). Blank pages: no text (text layer incl. OCR) and less than 0.03 % dark pixels outside a 6 % margin at 40 dpi, so dust and scanner shadows don't count but one short line does.
  - `MergeDialog` (order with Up/Down, file name following the first file), `SplitDialog` (the three modes, explanation of the automatic one, live preview of the parts), `PdfOperations` (runs them for a result table via a small Host interface). Toolbar buttons and context menu entries `Merge PDFs...` / `Split PDF...`.
  - Originals: after success the question 'Move to Recycle Bin' / 'Keep' with 'Remember my choice' (`MultipleChoiceDialog.setCheckBox`), stored in `SettingsConf.Int.RemoveOriginals` and changeable in the preferences. Undo (Ctrl+Z): new history kinds MERGE/SPLIT; removed originals are backed up in `<app data>/undo` (deleted when the entry is dropped and at exit), the created files go to the recycle bin.
    Tests: `PdfPagesTest` (ranges, page numbers, merge order, split, part names, blank scans with dust/shadow and a sparse page), `PdfOperationsTest` (merge/split with removed or kept originals, undo of both, remembered answer, dialog logic); in the GUI: automatic split of a 7-page scan with 2 blank pages into 3 parts, original to the recycle bin, undo restored it; merge of 2 PDFs in changed order, 'Keep' remembered and shown in the preferences, undo removed the merged file.
- as user, i want to have a symbol bar for print, mail, rename, move to folder, split and merge (v2.6); also: folder navigation and 'add folder' icons in the same style, in the general toolbar too
  - `DocToolBar` at the top of the main window with flat icons (own drawings, `dev/icons/make_toolbar_icons.py`, 16 px + @2x for high-DPI screens, which `LazyImageCache` now prefers when enlarging) and tooltips: root folders, up, refresh, new folder | print, email | rename, move | merge, split | delete. It works on the result table used last (`ResultPanel.getActivePanel()`: focus, click, or the tab brought to front) and runs the same `MenuAction`s as the context menu (`ResultPanel.DocAction`, `addDocAction`); the folder browser registers its navigation actions, which its own buttons use as well. A button is enabled when its action is possible for the selection (e.g. rename: one file or folder; move: files only). The inbox/folder rows use the new rename and move icons.
    Tests: `DocToolBarTest` (buttons follow selection and active panel, folder navigation of the active browser); in the GUI: all groups shown, enabled states for file, several files, '..' row and folder browser, move dialog and refresh from the toolbar.
- View > Icon size: Automatic, 100, 125, 150, 200 %; default 200 %; all icons; takes effect after a restart (v2.6)
  - New submenu `View > Icon size` with radio entries for the existing setting `IconScalePercent` (the preferences dropdown changes the same value, and the menu follows it); choosing a size says that it takes effect after a restart. The default is 200 % instead of Automatic; settings files that already contain a value keep it.
    Tests: `LazyImageCacheTest` (32 px variant used at 150/200 %, default 200 %); in the GUI: new settings start with 200 % icons, View > Icon size > 100 % showed the hint, after the restart the icons were at 100 %.
- bug (found while testing v2.6): on Windows the OCR text layer was never written into scanned PDFs
  - `SearchablePdf.ocr` replaced the PDF while the PDFBox document loaded from it was still open; Windows refuses that ('used by another process'), so the text only went into the index and the files stayed without text layer. Now the document is saved to a temporary file and closed before the file is replaced.
    Test: `SearchablePdfTest.addsTheTextLayerToAScannedPdfInPlace` (failed on Windows before the fix).
- auto-split without blank pages (the scanner removes them): a) separator sheet, b) text-based suggestion, c) editable page list (v2.6)
  - a) `SeparatorSheet` (model/pdf) writes an A4 sheet with a thick frame, the marker 'DOCFETCHER SEPARATOR' upright at the top and turned by 180 degrees at the bottom (found however the sheet lies in the scanner; Tesseract doesn't turn pages) and a localized title and instruction; `isSeparator` finds the marker in the page text with up to 3 OCR errors. Printed via `File > Print Separator Sheet...` and a link in the split dialog (`DocumentPrinter.printSeparatorSheet`).
  - b) `DocumentStarts` suggests a role per page with a reason, first signal wins: separator page (left out, next page starts), page numbers ('Seite 1 von 2' starts, '2 von 2' continues, after the last page a new one starts), salutation in the upper half, a number with >= 4 digits shared with the page before (no dates/years; numbers on more than half of 4+ pages ignored), too little text (continues), else idf-weighted cosine similarity of the 4-letter parts of the words (< 12 % starts; 4-grams handle German compounds and OCR errors - calibrated: same document >= 24 %, different <= 2 %).
  - c) `SplitDialog` rewritten: page table with checkbox 'new document starts here', beginning of the text and reason; buttons At Separator Pages / From the Text / Every Page / Clear; 'Leave Out / Keep'; note for pages without text; live result. `PdfPages.analyzePages` (text, blank, separator sheet per page) and `fromRoles` replace the blank-page-only split; the old modes and the SplitMode setting are gone.
    Tests: `DocumentStartsTest` (mixed stack, salutation/shared number, separators, OCR errors in the marker, little text, similar text), `PdfPagesTest` (sheet recognized in its text layer), `SeparatorSheetOcrTest` (Tesseract finds the marker on the rendered sheet, upright and upside down), `PdfOperationsTest` (dialog suggestions and manual changes); in the GUI: image-only stack (invoice 2 pages, separator sheet, letter 2 pages) was OCR'd by the inbox indexing, the dialog suggested 1-2, 4-5 with the sheet left out, 'From the Text' gave the same with reasons, a ticked page added a document, the separator sheet printed via File menu to 'Microsoft Print to PDF' was recognized after OCR, and the split created the 2 documents with their text layer.
- settings were stored in the portable folder and lost when a new version was extracted: store them in the user folder of the OS, docfetcher/settings.conf (v2.6)
  - `SettingsLocation` (util): Windows `%APPDATA%\docfetcher`, macOS `~/Library/Application Support/docfetcher`, Linux `$XDG_CONFIG_HOME` or `~/.config/docfetcher`; DOCFETCHER_HOME and `settings=` in paths.txt still take precedence (there the file is `settings.conf` too). On the first start the old `conf/settings-conf.txt` (portable folder, `%APPDATA%\DocFetcher\conf`, `~/.docfetcher/conf` or the paths.txt folder) is copied once and left in place. Indexes of the portable version stay in its `indexes` folder (portable document repositories). The dev build keeps `bin/settings-conf.txt`.
    Tests: `SettingsLocationTest` (folders per OS, DOCFETCHER_HOME, XDG, copy once, never overwrite); with the portable package and APPDATA pointing to a scratch folder: old portable settings copied to docfetcher/settings.conf, a change saved there and not in the program folder, after deleting the program's conf folder (like unpacking a new version) the change was still there.
- bug (reported for v2.6): split parts of d:/tmp/testeingang/scan.pdf (22 pages, a PDF form) were as big as the original (5 MB each for parts 2 and 12-15), all parts 5-6x the original
  - The form fields (widgets) and links of the pages refer to their original page (/P) and to the field tree of the whole form (/Parent); importPage copied them as they are, so saving a part wrote the whole source document along. `PdfPages.detachFromSource` gives each part page copies of its annotations with /P set to the new page, without /Parent, /Popup and /IRT, links without destinations into the original (web links stay), and removes /B (article threads). Form fields keep their appearance.
    Tests: `PdfPagesTest.partsDontCarryTheOtherPagesAlong` (form with a big image on page 1, a link to it on page 2 and a field with widgets on all pages: the part with pages 2-3 was as big as the original before, now < 10 %); with a copy of the reported file split into single pages: 5131k in total for 5035k original, largest part 511k, page 2 renders pixel-identical.
- merge: 'revert order' button; name part like the rename dialog, proposal = name of the first file (after reorder); split: 'auto rename after split' with the 3 part name; split dialog: separator pages first if the document has them, else the text suggestion; print: OS print dialog (v2.6)
  - `NameEditor` (gui): the name part of the rename dialog (field with fixed extension, 'use current/first name', 'use suggested name', keyword links), now used by `RenameDialog` and `MergeDialog`. `MergeDialog`: Up / Down / Reverse; the name follows the first file until the user edits it, with the suggestion of the first file (via `Host.getNameSuggestion`, i.e. the result table's suggestion cache). Name clash (answered: add a number): the merge is written under a temporary name; if the originals went to the recycle bin the file gets the exact name, otherwise `UtilFile.getFreeFile` adds -2, -3, ... The undo now removes the created files before restoring the originals, since the merged file may have the name of one of them.
  - Split: checkbox 'Name the new documents automatically by date and keywords' (`SettingsConf.Bool.SplitAutoRename`, default on); `PdfOperations.getSuggestedPartFiles` builds `<date>_<word>-<word>-<word>.pdf` from the text of each part (`NameSuggester.suggest(text, fallbackDate, searcher)`, new), same names get numbers, parts without a suggestion stay `name_n.pdf`. The suggestion buttons are toggle buttons showing the active suggestion; 'At Separator Pages' is disabled with a note when there are no separator pages.
  - Print: SWT already calls the classic PrintDlg; Windows 11 replaces it with its own dialog, which takes its title from the version info of javaw.exe ('Azul Zulu 21') and has no preview for Win32 programs. SetCurrentProcessExplicitAppUserModelID doesn't change the title (tried). Left as it is; see the notes to the user.
    Tests: `PdfOperationsTest` (exact name with removed originals and undo, number with kept originals, part names by date/keywords with numbers for equal ones, merge dialog name follows first file incl. Reverse, split dialog toggles/disabled separator choice and result text); in the GUI: merge dialog with Reverse (name and keywords followed invoice-a), Keep gave invoice-a-2.pdf with 3 pages; split with auto-rename gave 2026-09-25_Stadtwerke-Rechnung-Musterstadt.pdf and 2026-09-25_Beispiel-Fuer-Torsten.pdf.
- bug (v2.7): in no circumstance a file has to be deleted on a move action - a drag from the folder pane to the inbox pane made the document disappear, not even in the recycle bin
  - Cause: after a drop reported as 'move', the drag source deleted every dragged file that still existed (`file.delete()`, for good), meant for programs that copy and leave the deleting to the source. When a DocFetcher table accepted the drop but didn't move (same folder, move failed, name taken), the file was lost. Now the source never deletes after a move (answer: a program that copies leaves the original, which stays); DocFetcher's own drop targets mark the drop (`noteDropInDocFetcher`); after an explicit Ctrl copy into another program the question 'remove originals' moves them to the recycle bin instead of deleting.
    Test: `DragDropTest` (a move that didn't move keeps the files, for DocFetcher and foreign targets; a file really moved away loses its row). A real mouse drag can't be simulated by the test driver (OLE drag and drop needs real input).
- bug (v2.7): split pages - with separator sheets detected, don't split on empty pages (but keep them out); only without separator sheets split after blank pages
  - The page role SEPARATOR (left out and splits) became OMIT (only left out); a split is the START of the next page. `DocumentStarts` (both suggestions): separator sheets are left out and the next page starts; blank pages are left out and only split if the document has no separator sheets (new reason 'Blank page, left out (the separator sheets split)'). `Range` can have gaps (a document without its blank back side), `split` and the part names follow its page list. The result line separates documents with ';' (1, 3; 5, 7) and says 'page(s) are left out'.
    Tests: `DocumentStartsTest` (sheets + blank back sides -> 2 documents with gaps; without sheets blank pages split), `PdfPagesTest` (part with a gap), `PdfOperationsTest`; in the GUI: 7-page duplex stack (letter, blank, letter, sheet, invoice, blank, invoice) -> 2 documents, pages 1, 3; 5, 7.
- allow moving docs from a folder view into an inbox folder (v2.7)
  - The inbox list now knows its folder (`InboxPanel.setFolder` -> `ResultPanel.setCurrentFolder`), so the existing drop logic moves files dropped on it into the inbox folder. The tab headers have a drop target (`Application.initInboxTabDrop`): files dropped on an inbox tab move into its folder via `ResultPanel.dropFiles`, which also marks the drop for the drag source (never deletes).
    Test: `DragDropTest.filesDroppedOnAnInboxAreMovedIntoItsFolder`; the app starts with the new drop target (a mouse drag can't be simulated by the test driver).
- sync view: the folder browser on the left (Folders tab) always shows the parent of the middle folder view; toggle icon in the toolbar (v2.7)
  - `FolderSync` (gui) couples the two `FolderPanel`s: a folder clicked on the left is shown in the middle; entering a folder on the left (answer) shows its first subfolder by name in the middle or nothing (`FolderPanel.showNothing`, with a note); going up on the left shows the folder that was left; entering a folder or going up in the middle moves the left one to the parent and selects the folder (`FolderPanel.showFolder(folder, select)`, folder rows can be selected now). A guard prevents syncing back. Toolbar toggle with the new flat icon `tb_sync` (setting `SyncFolderPanels`, default on), only enabled while the folder panel is shown. Fixed on the way: the folder browser's listener cleared the preview before it existed (crash at startup with sync on).
    Tests: `FolderSyncTest` (all rules, switching off and on, dispose); in the GUI: click on inbox -> middle shows it, double-click Archiv in the middle -> left shows inbox with Archiv selected, double-click Belege (no subfolder) on the left -> middle empty with the note, '..' on the left -> middle shows Belege.
- change icon for merging docs to a stapler icon (v2.7)
  - `icon_merge` in `dev/icons/make_toolbar_icons.py` draws a flat stapler seen from the side (dark base with anvil, blue arm, hinge), 16 px and @2x; used by the toolbar button and the merge dialog. Checked in the app at 200 %.
- add ctrl + drag/drop to copy (v2.7)
  - The drag source already offered only 'copy' when Ctrl was held at mouse down, but the drop targets of DocFetcher always moved. Now a copy drop copies (`ResultPanel.copyFilesTo`, also for the inbox tabs via `dropFiles(files, folder, copy)`): never overwriting, `UtilFile.getFreeFile` adds -2, -3 (also when copying into the same folder); history kind COPY, undone like merge/split by removing the created files (recycle bin), message 'Remove the n copied file(s)?'.
    Test: `DragDropTest.ctrlDropCopiesAndNeverOverwrites` (copy, second copy numbered, both undone, original untouched). A real mouse drag can't be simulated by the test driver.
- search in German: find umlauts (u finds u and ü, and the opposite) and typical OCR issues (O and 0) (v2.7)
  - `TolerantAnalyzer` (model) wraps the chosen analyzer: every word whose folded form differs is indexed twice at the same position, as it is and folded (umlauts/accents via ASCIIFoldingFilter, ß -> ss; in words with letters and digits 0/1/5 -> o/l/s; pure numbers unchanged). Queries go through the same analyzer, wildcard and prefix terms are folded in `PhraseDetectingQueryParser` (original OR folded). Because the original forms stay, exact searches keep working in old indexes; index format version 3 makes DocFetcher offer a rebuild at startup (message mentions the tolerant search). Preference 'Tolerant search' (`SettingsConf.Bool.TolerantSearch`, default on); changing it asks to rebuild, like the word segmentation.
    Tests: `TolerantAnalyzerTest` (folding rules, tokens, searches Muller/Müller, hauptstrasse/Hauptstraße, fur, Bonn/B0nn, 4711, Mül*); in the GUI: the outdated-index question came, after the rebuild 'Muller' found 'Müller' and 'Bonn' found 'B0nn', both highlighted in the preview.
- UI: toolbar buttons to switch between 1, 2 and 3 panes, the document view always shown (v2.8)
  - Answers: the Folders tab is gone in mode 3; 3 toolbar buttons + View menu, F3 cycles; the sync button applies to all modes; search and inbox clicks follow. `SettingsConf.Int.PaneMode` (0 = from the old `ShowFolderPanel`: 2 if shown, else 1) and `FolderSashWeights3`. `Application.applyPaneMode`: the folder sash has three children (tabs, a holder for the Folders browser, the folder panel); in mode 3 the Folders tab is disposed and its `FolderPanel` is moved into the holder (`setParent`, recreated with a new `FolderSync` where reparenting isn't supported), in modes 1/2 the tab comes back before Search; mode 1 disposes the folder panel. `followInFolderPanel`: a single selected document in the search results or an inbox is shown in the folder panel (`showFile`), with sync the browser before it goes to the parent. Toolbar: 3 radio buttons with new flat icons `tb_panes1-3` (`panes(n)` in make_toolbar_icons.py); View menu radio items; the search bar keeps a minimum width and shortens 'Search Settings...' to '...' in narrow panes.
    Fixed on the way: the inbox list set the preview before following, the folder panel then cleared it; `FolderBrowsingTest` failed in teardown on Windows (index file still locked), now tolerant like `GuiTestSupport`.
    Tests: `DocToolBarTest.paneModeButtonsSetTheMode` (buttons, migration from ShowFolderPanel); full suite (the 4 FileFactory/OpenOffice failures need the native 7-Zip/RAR libraries and are unchanged); in the GUI: 2 -> 3 (inbox/search | Folders | panel), search hit Archiv/2026/note.txt -> right pane 2026 with note.txt, middle Archiv with 2026 selected, preview shown; 1 (Folders tab back between Inbox and Search), 2 with sync, View menu, F3 cycle 3 -> 1 -> 2, inbox click follows in mode 2 with the preview.
- bug/change (v2.8): the tolerant search made DocFetcher rebuild the indexes, which read (and to a file watcher 'touched') nearly every document; the full text must stay untouched, tuning belongs to the index/search logic
  - Findings: indexing never writes documents with text (only scans without text get their OCR text once, option 'Store the recognized text in the files'); a rebuild reads every document again, and Windows updates the last-access time on each read (DisableLastAccess = 2 here), which file watchers report. New test `IndexingLeavesFilesUntouchedTest`: documents with text keep content, size and date; a second full indexing changes no file, not even the archive attribute.
  - Change: `TolerantSearch` (model.search) replaces `TolerantAnalyzer`: the index keeps the words as they are; `Searcher` expands each query word to the indexed words with the same folded form (terms, phrases as a choice of phrases, prefix and wildcard terms), from a table built once per opened index reader and field. `CURRENT_FORMAT_VERSION` back to 2 (version 3 indexes are current as well), no rebuild prompt, switching the option needs no rebuild. Tests: `TolerantSearchTest` (index keeps the words, umlauts, OCR errors, wildcards, phrases, switched off, highlighting); full suite; in the GUI no rebuild prompt, 'Muller Bonn' found and highlighted 'Müller' and 'B0nn'.
- search like Google: AND for words, + must include, - must not, "" exact; search help button; quick start guide instead of the manual at startup; project README (v2.8)
  - Answers: '?' shows the quick guide with links to the manual; search help in the preview pane; quotes = exact (no tolerant variants); existing OR settings switched to AND once.
  - Search (`Searcher.createQuery`): `UseOrOperator` default false, `AndOperatorApplied` switches old settings once at startup. Parsing: '/' is always a character; words before ':' are fields only for real fields (`escapeUnknownFields`, 'Nr: 5' = words); if Lucene still can't parse, `asPlainWords` escapes everything except quotes in pairs, +/- at word start, * and ?. `PhraseDetectingQueryParser.getExactQueries` marks the quoted parts, `TolerantSearch.expand` skips them. Bug fixed on the way: the file name match took the words of `-word` too, so excluded words added documents with that name; now the file name query uses only the included words of the parsed query, and the excluded words also exclude by name.
  - Help: new manual subpage `Quick_Start` (EN/DE, `quick_start` title), `ManualLocator.getStartPage` (falls back to the manual in other languages) for the startup view and the help button; '?' button in the `SearchBar` (tooltip `Msg.search_help`) opens `Query_Syntax.html`, which starts with a Google-style quick reference now (EN/DE). Main manual 'Searching' paragraph updated.
  - `README.md` (GitLab style): purpose, use cases, features, docs table linking the manual, readme.txt, tasks/skill.md, CLAUDE.md, build and tests.
    Tests: `GoogleStyleSearchTest` (AND, +, -, OR, phrases, unbalanced quote, C++, 'Nr: 5', 01/2025, '(cat', lone '-', file names with excluded words, asPlainWords, fields), `TolerantSearchTest` (quotes exact) via the new `SearchTestSupport`; full suite (only the 4 known FileFactory/OpenOffice failures); in the GUI: quick start at startup, OR setting migrated to AND, 'printer Muller' 0 results, 'printer OR Muller' 3, 'Nr: 5 (x' no error, '?' opens the search help.
- dialog options with 'remember': allow to change / reset them (v2.9)
  - Preferences: new checkbox 'Ask to rebuild indexes created by older versions' (`AskRebuildOutdatedIndexes`, turned off by 'Don't Ask Again'); 'After merging or splitting PDFs' (`RemoveOriginals`) was already there; button 'Reset Remembered Answers' sets both back to asking and brings back one-time hints (`ShowRelativePathsMessage`) on OK.
    Test: in the GUI with AskRebuild off: the checkbox was unchecked, the reset button checked it, after OK the settings file had AskRebuildOutdatedIndexes = true and ShowRelativePathsMessage = true.
- MCP server (HTTP), enabled in the settings: access to the indexes, returns document paths and content (v2.9)
  - Answers: localhost only, off by default, port in the preferences; read only; read_document up to 20,000 characters per call, paged; asking for the document itself gives its path.
  - Package `mcp`: `McpServer` (JDK HttpServer on the loopback address, Streamable HTTP: POST JSON-RPC to /mcp, 202 for notifications, 405 for GET, 403 for web pages with a foreign Origin; initialize with protocol version negotiation, ping, tools/list, tools/call; instructions for the assistant), `McpTools` (search: Google-style query via the Searcher, sort newest/oldest/relevance, modified_after/before, folder, snippets; read_document: text via ResultDocument.getText, offset/max_chars; list_folder: indexed folders or folder contents, newest first; access only inside the indexed folders and fixed move targets, paths normalized so '..' can't escape), `Json` (own minimal reader/writer, no new library). Settings `McpServerEnabled` (false), `McpServerPort` (28835), `IntFieldOption` for the port; the server starts/stops/restarts when the settings change. Refactoring on the way: `IndexRegistry.findIndexFor` replaces four copies in FolderPanel, InboxPanel, ResultPanel and SearchQueue. German compound words: the tool description tells the assistant to use *steuer*.
    Tests: `McpServerTest` (initialize/version, tools/list, errors, 'latest tax letters for Renate' sorted newest and by date range, 'rent of the tenant in Rosenweg' via search + read_document, long documents in parts, list_folder, access denied outside and with '..', HTTP transport incl. 202/405/404/403, Origin check, JSON round trip; it found the '..' escape, fixed); `SearchTestSupport` made public with file dates and subfolders; full suite; in the GUI: enabled in the preferences, curl initialize + search 'Rechnung' newest first with snippets + read_document of a scanned PDF, disabled again -> connection refused. Docs: manual 'AI assistants (MCP server)' EN/DE, preferences page, quick start, README, changelog.
- settings: quick help next to the MCP option how to add it to Claude; MCP: too many results newest first with time range, amount and significant words; search also considers path/folder names (v2.9)
  - Answer: folder names count for the normal search and the MCP search (existing option 'Also match file and folder names').
  - `NameMatch` (model.search) replaces the separate file name query: each word of the parsed query matches the text OR the file name (substring) OR the name of a folder in the path; the folders come from the in-memory folder trees of the indexes (at most 100 per word, tolerant with umlauts), matched via PrefixQuery on the UID - no index change. Excluded words exclude matching names too (the earlier MUST_NOT copy is gone). Applied in `Searcher.search` and the web search before the tolerant expansion.
  - `McpTools.search`: if more documents are found than max_results, sorted newest first (unless a sort was given) and an `overview`: oldest/newest, counts by_year (over 2 years) or by_month, top_folders with counts, significant_words (words of file names, titles and the two folders above, without query words, noise words and words in nearly all documents) and a hint. Tool description mentions folder names and the overview.
  - Preferences: `McpHelpOption` below the port field: link 'How to connect Claude...' opens a dialog with the steps (Claude Code command, Claude Desktop claude_desktop_config.json via npx mcp-remote), buttons to copy both, port from the field.
  - Also: the MCP test's free-port probe now binds to loopback only; binding to all interfaces made the Windows firewall ask about the Zulu Java (user question).
    Tests: `NameMatchTest` (folder names, parts of names, exclusion, tolerant folder names, switched off), `McpServerTest.tooManyResultsComeWithAnOverview`; full suite (the 4 known failures; DragDropTest failed once in the full run - recycle bin delete - and passed alone); in the GUI: link and help dialog in the preferences, MCP search 'Stadtwerke' with max_results 2 gave the overview, search 'Archiv note' found only Archiv/2026/note.txt.
- MCP overview: also significant words of the content, to narrow down by adding or excluding terms (v2.9)
  - `McpTools.contentWords`: reads the text of up to 20 found documents spread over the result list (5 s limit, 20,000 characters each), ranks the words by TF-IDF against the whole index (reuses `Searcher.extractKeywords`) and counts per word how many sampled documents contain it (whole words); left out: query words (also without umlauts), numbers and letter/digit codes (OCR noise), noise words, words in only one or in all documents. Overview keys `content_words` ('versicherungsschein (2/3)') and `sampled_documents`; the name-based list is now `name_words`; the hint mentions adding and excluding (-word).
    Tests: `McpServerTest.tooManyResultsComeWithAnOverview` (renate 2/4, januar 3/4, no query word); in the app over HTTP: 'Seite' gave content words like 'versicherungsschein (2/3)', 'zaehlernummer (2/3)'. A dev instance started on a failed build hung without a window and caught the next starts - ended it.
- bug (v2.9): storing OCR text in a document triggers a re-index of the directory ('self-hack'); another change may have happened in the meantime
  - Cause: the text layer is written to a temporary file which replaces the scan; the folder watcher reports the replacement as a new file (its mtime check only covers plain modifications) and starts an UPDATE of the whole index. Change detection itself only compares the date, which is restored, so the file isn't parsed again - it's the extra update walk.
  - `OwnFileChanges` (model): `begin(file)` before replacing, `end(file)` after the date is restored (remembers size and date), `removed(file)` for a deleted/renamed TIFF; `isOwnChange` is true while writing, or while the file still has exactly that size and date (60 s), or for the temporary files (`TEMP_SUFFIX`, now shared by SearchablePdf and OcrTextEmbedding). `FolderWatcher.accept` ignores own changes; other files, or the same file changed again, still start an update. Used in SearchablePdf.ocr, convertTiff (PDF, renamed/deleted TIFF) and OcrTextEmbedding.replaceFile.
    Tests: `OwnFileChangesTest` (own change, other spelling, other file, temp file, changed afterwards, removed and created again; with the real FolderWatcher and JNotify: replacing a file like the OCR does starts no update, a change of another file does - verified that this test fails without the fix); full suite.
- bugs from the package test by the user (v2.9): enabling MCP -> NoClassDefFoundError com/sun/net/httpserver/HttpHandler; preferences take time to open; where is the help how to add it to the LLM tool
  - NoClassDefFoundError: the jlink runtime of the package had no jdk.httpserver module (the dev build runs on the full JDK). Runtime rebuilt with jdk.httpserver (skill.md list updated, package test now always enables MCP); `Application.updateMcpServer` creates the server inside the try and shows any Throwable as a message instead of crashing the preferences.
  - Slow preferences: `OcrEngine.getUnavailableReason()` is synchronized like `recognize()` and initializes the engine, so the dialog waited for the page being recognized in the background (and loaded Tesseract on first use). New `peekUnavailableReason()` (volatile, no init, no lock) for the dialog. Measured with the indexer running OCR on 3 synthetic 4-page scans: 0.50/0.53/0.48 s with the fix, 1.11/2.22 s with the old call (real scans take longer per page). Test `OcrEngineTest` (lock held like a recognition, peek returns at once).
  - Help: port field and 'How to connect Claude...' are the first field options, i.e. right below the MCP checkbox; after OK the help opens when the server was just switched on. Checked in the app (layout, help window after OK, server answers).
- bug (v2.9, found with the user's real indexes via MCP): read_document said 'Access denied' for documents of an index with relative paths (portable repository), and search results showed paths like '../../../../../../Dokumente/...'
  - The root of such an index is 'D:/.../DocFetcher-2.9-Windows-64bit-Portable/../../../../../../Dokumente/05 Torsten' (Util.getCanonicalFile doesn't normalize); the access check compared it with the normalized document path. McpTools now normalizes the roots, the result paths (absolute, '/'), the folder filter, the top folders, list_folder and the document lookup; IndexRegistry.findIndexFor also compares normalized paths if the plain comparison finds nothing.
    Test: `McpServerTest.indexRootWithDetour` (index root 'x-detour/../root': search shows a normalized path, read_document and list_folder work) - fails without the fix.
- Excel: store formulas and results (v2.9)
  - Answer: existing indexes parse only their Excel files once more (no rebuild).
  - `ExcelText` (model.parse) for .xls (HSSF) and .xlsx (XSSF): per sheet its name and the cells row by row (tabs); formula cells give the result Excel stored when saving, formatted like the cell, plus '[=formula]'; comments in brackets; IndexExcelFormulas = false gives only the results (before: formulas OR results). Used by MSExcelParser (old BIFF5 files still via JExcelAPI) and MSOffice2007Parser. `TreeIndex.excelTextVersion` (serialized, 0 in older indexes) makes `FileIndex.doUpdate` treat Excel files as modified once (`FileContext.reparseExcel`), then it is set to `ExcelText.VERSION`; a rebuild sets it too.
    Tests: `ExcelTextTest` (xlsx and xls: text, sum '326,96 [=SUM(B2:B3)]', text formula, #DIV/0!, results only), `ExcelReparseTest` (changed contents with the same dates: normal update ignores them; outdated Excel format: only the Excel file is parsed again, the text file not, and only once); full suite; in the app via MCP (port 28836): search '286,52' found the sheet, read_document gave 'Summe BK 286,52 € [=SUM(B1:B3)]'.
- OCR: don't change the access/modification date when storing the text, it breaks the sort by date (v2.9)
  - Findings: the modification date was always restored, and Windows keeps the creation date when a file is replaced under the same name (tunneling); only the access date changed. The 2,889 ELO PDFs with created = modified = 2026-09-24 12:01-12:35 were not changed by DocFetcher: no TIFF was converted (5,376 .tif, no .tif.ocr), OCR can't do 1.4 files per second, and the Windows write only worked from 25.09 on; they were created in bulk by the export - the document date is in their names.
  - `FileDates` (model.ocr): created, modified and accessed, taken before the file is first read and set back after writing (BasicFileAttributeView.setTimes); used by SearchablePdf.ocr, convertTiff (the PDF gets the TIFF's dates) and OcrTextEmbedding (OcrPreprocessor takes the dates before reading the image). Reading a file for indexing, preview or MCP still updates the access date, as with any program, while Windows has last-access updates on (fsutil behavior query disablelastaccess).
    Tests: `FileDatesTest` (PNG text, the whole indexing step for an image, scanned PDF text layer, converted TIFF: all three dates unchanged); full suite.
- PDF form fields: extract their values (v2.9)
  - Answer: existing indexes parse only their PDFs with filled forms once more.
  - `PdfForms` (model.parse): 'label: value' per filled terminal field (label = description/tooltip, else name; checked boxes '[x]'; empty fields left out; one odd field doesn't hide the others); `hasFilledFields(file)`. PdfParser appends the form text after the page text and after OCR (a scan with filled fields is still recognized); PagingPdfParser shows it as an extra last page. `TreeIndex.pdfFormsVersion` like the Excel version; `FileContext.needsReparse(file)` combines both checks (Excel by name, PDF by hasFilledFields). ReparseOnceTest (renamed from ExcelReparseTest) covers both.
  - Finding with the user's rental contracts (Haus & Grund online contracts): they have no form fields at all (PDFlib Personalization Server prints the values as page text). The amounts were in the index all along, but drawn in a separate pass, so they are far from their labels in the text; sorted by position they line up ('Grundmiete 620,00 Euro'). So the form support doesn't change these files; it helps real PDF forms.
    Tests: `PdfFormsTest` (filled text fields with and without description, checked box, empty field left out, extra preview page, PDF without form), `ReparseOnceTest.outdatedIndexesParseOnlyTheirPdfFormsOnce` (form PDF parsed again, PDF without form not, only once); full suite.
- try: index/read PDF text in visual order, so labels and values stay together (v2.9)
  - Decision: only for MCP read_document (the AI reads live), not for the index: the search finds the words in any order, and changing the index would mean parsing every PDF again. `PdfVisualText.of(file)`: PDFTextStripper with sortByPosition, plus the form fields; null for PDFs without text (scans whose text wasn't stored), then the normal text with on-the-fly OCR. read_document parameter `order` ('visual' default, 'stream').
    Tests: `McpServerTest.pdfsAreReadInVisualOrder` (labels drawn first, values afterwards like PDFlib: visual gives 'Grundmiete 620,00 Euro', stream doesn't); with the user's Henker contract: 'Grundmiete 620,00 Euro', 'Betriebskostenvorauszahlung 70,00 Euro', 'Gesamtbetrag 790,00 Euro' on one line each; full suite.
- bug (v2.9, found with the user's question about cars): MCP searches took minutes and timed out; MCP only - the GUI search reads only the index
  - Cause: snippets (first 10 results, via getHighlightedText) and the overview's content sample (up to 20 documents) parsed the documents fully, and scans without stored text (e.g. the 5,376 unconverted ELO TIFFs) were recognized on the fly; the 5 s limit of the sample was only checked between documents.
  - `McpTools.quickText`: PDF files via PdfVisualText (no OCR, null for scans without text), images and emails give null, other documents their normal text; used for snippets (cut around the first query word, 5 s budget for all) and the content sample. read_document unchanged (recognizes a scan when asked).
    Test: `McpServerTest.snippetsDontRecognizeScans` (scan without text found by its name gets no snippet, search < 10 s) - fails with the old code; full suite.
- change (v2.9): Search tab focuses its field; keys for inbox / search / folders; sync button also syncs search results
  - Keys `KeyInbox`/`KeySearch`/`KeyFolders` (SettingsConf.IntArray, defaults F5/F6/F7, saved in settings.conf), set in the preferences with the generalized `HotkeyOption`/`HotkeyDialog` (plain function keys allowed for in-app keys). F5 cycles through the inbox tabs, F6 shows the Search tab with the cursor in the field, F7 the Folders tab or (three panes) the middle browser. Clicking the Search tab also focuses the field (`Application.onTabSelected`).
  - Search/inbox sync is now the toolbar sync button (preference `SyncFolderView` dropped): the selected document goes to the rightmost browser (folder panel, or with one pane the Folders tab in the background), the browser before it shows the parent. The button is enabled in all pane modes.
    Tests: `KeysTest` (defaults, key matching with modifiers, letters, mouse mask), `DocToolBarTest`, `FolderSyncTest`; GUI: F5 cycles two inboxes, F6/tab click focus, sync in modes 1/2/3, sync off leaves the browsers, F7 middle pane, key changed to F9 via the dialog persists (plain letter rejected).
- change (v2.9, feedback): only search results sync the folder browsers; default keys F7 search / F8 inbox / F5 folders, shown in the tab titles
  - Inbox documents no longer call followInFolderPanel: the inbox is the source for filing, the browsers keep the target folder (with sync, the right pane jumped back to the inbox folder).
  - Keys renamed to SearchKey/InboxKey/FoldersKey, so settings files that saved the old defaults F5/F6/F7 get the new ones. Tab titles 'Search (F7)', 'Inbox (F8)', 'Folders (F5)' via Application.withKey, updated when a key changes in the preferences.
    Tests: KeysTest (new defaults, tab titles with key / without key); GUI: inbox click keeps the right browser, search result syncs it, F8 cycles the inboxes, F5 Folders tab, F7 search, key changed to F9 renames the tab at once.
- change (v2.9): MCP protocol versions for clients of the last year (Claude Desktop without version warnings)
  - Found: the server spoke 2025-03-26..2025-11-25 (initialize) but answered unknown versions with 2025-06-18; the current
    SDK (client 2.x, in mcp-remote@latest) already knows the stateless revision 2026-07-28. resources/list and prompts/list
    gave 'Method not found'. Claude Desktop had no DocFetcher entry yet, so no logged warning to compare.
  - McpServer is dual-era: _meta version or a modern MCP-Protocol-Version header -> 2026-07-28 (server/discover,
    resultType, serverInfo in _meta, ttlMs/cacheScope, header checks -32020, unsupported version -32022 with the list,
    missing _meta fields -32602, HTTP 400/404); otherwise initialize (unknown -> 2025-11-25), batches, unknown version
    header -> 400. Empty resources/prompts lists. Claude Desktop config with 'npx -y'.
    Tests: McpServerTest.modernRevision (discover, lists, call, Base64 Mcp-Name, header mismatches, future version,
    missing _meta, removed methods, legacy headers), initializeAndListTools, httpTransport (modern over HTTP: 200/404);
    real clients: SDK 2.3.1 legacy -> 2025-11-25, auto and pin -> 2026-07-28, SDK 1.32.1 -> 2025-11-25,
    mcp-remote with the help's configuration - all without warnings.
- change (v2.10): MCP: claude desktop wants per default https connection only - easier integration for a normal user
  - Found: https would not help - Claude Desktop connects custom connectors (https addresses) from Anthropic's servers,
    which can't reach localhost. Claude Desktop starts local servers on stdio; .mcpb files are not associated with it on
    Windows (MSIX package), they are installed under Settings > Extensions.
  - New: 'Create Claude Desktop Extension...' in the help on connecting Claude saves docfetcher.mcpb (default: Downloads)
    and shows it in the Explorer. Manifest 0.3 (McpExtension) with the port of the help, the tools and the icon; the
    bridge (server/index.js, no dependencies) passes stdio lines to http://localhost:<port>/mcp, adds the headers of
    revision 2026-07-28 and answers with a clear error when DocFetcher isn't running. Runs with Claude Desktop's own
    Node.js. The JSON configuration stays as '2c' for other apps. Util.winOpenDir works with spaces in paths.
    Tests: McpExtensionTest (version, zip contents, manifest, bridge with Node against a server in both eras, server
    stopped); mcpb validate (schema OK, unsigned); app: help dialog button -> save -> Explorer with the file selected,
    SDK 2.3.1 over stdio through the saved extension: legacy 2025-11-25 and auto 2026-07-28 with search results.
    Not installed in the user's Claude Desktop (no test entries in the user's Claude configuration).
- change (v2.10): MCP: add endpoint for returning the original document (not only the full text)
  - Answer to 'base64 - no native file access to source ever. 10MB limit why? can be 50?': 50 MB default
    (program-conf.txt McpMaxOriginalMB). The 10 MB came from the clients: the MCP SDK (1.x and 2.x) reads at most
    10 MiB per stdio message and closes the connection on a larger one - that is Claude Desktop's connection.
  - Tool get_original_document (read-only, same access check as read_document): a text part with path, name,
    MIME type, size, date, then the file - PNG/JPEG/GIF/WebP as 'image', all others as an embedded 'resource' with
    a base64 blob and the identifier docfetcher:/<path> (no file: link). The Claude Desktop bridge turns answers
    over 10 MiB into an error message (with resultType for 2026-07-28) and keeps the connection.
    Tests: McpServerTest.originalDocuments (text file, PNG as image, TIFF as resource, too large, missing, outside,
    MIME types), McpExtensionTest.bridge (9 MB file: error in both eras, next request answered); app: SDK 2.3.1 over
    stdio through the extension (PDF and PNG byte-identical, 30 MB file -> error, connection stays) and over HTTP
    (30 MB byte-identical in < 1 s), legacy and 2026-07-28.
- change (v2.10): MCP: add endpoint to place a file in ScanBox folder for LLM (new files only, no deletion, no modifications)
  - 'ScanBox' = DocFetcher's inbox (Postbox) folders. Opt-in 'AI assistants may add new files to the inbox' below the
    MCP option (SettingsConf McpInboxWrite, off). Tool add_to_inbox (McpInbox), only listed while allowed; name + text
    (UTF-8) or content_base64 (strict, line breaks allowed), optional inbox (tab name or path, default the first).
    Files are created with CREATE_NEW directly in the inbox folder: a taken name (also in another case) gets
    'name (2).ext'; nothing is ever replaced or deleted. Refused: folders and invalid characters, leading/trailing dot,
    Windows device names, programs/scripts/shortcuts/Office macro files, more than 20 MB. Request limit raised from
    1 MB to 28 MB for this; tool list cache (ttlMs) 1 min instead of 1 h.
    Tests: McpInboxTest (listing and annotations, switched off, new files and numbering, case, base64 with line breaks,
    second inbox by name, unknown inbox, refused names/contents, no inbox, size limit, 5 MB over HTTP); app: option in
    the preferences -> tool listed; SDK 2.3.1 through the Claude Desktop extension in both eras: text, same name
    (numbered), PDF as base64, .exe refused; the inbox tab shows the files, search finds the new offer.
- change (v2.10): documentation for non-Claude users (ChatGPT desktop app and others)
  - Help dialog 'Connect AI Assistants to DocFetcher' (link 'How to connect AI assistants (Claude, ChatGPT)...'):
    2c ChatGPT desktop / Codex (shared ~/.codex/config.toml, streamable HTTP, no bridge; or 'codex mcp add docfetcher
    --url ...'), 2d other assistants (HTTP address, or the JSON configuration for apps that start local programs), why
    web assistants can't connect; button 'Copy ChatGPT / Codex Configuration'; buttons in two rows. Manual EN/DE, README.
    The config entry is exactly what Codex CLI 0.162 writes with 'codex mcp add --url' (checked with a scratch
    CODEX_HOME); not tested in the ChatGPT app itself (not installed here, needs an OpenAI login).
    Tests: McpHelpOptionTest (configurations, English and German help with all parts); app: help dialog in the dev build.
- change (v2.10): MCP: add task to show a particular document in DocFetcher, including folder sync
  - Answers: Search tab + sync, window to the front, several documents at once.
  - Tool show_document (paths: list, or a single path): same access check as read_document; indexed documents only
    (others reported as not_found; none found -> error), at most 100. McpServer.DocumentViewer, set by Application;
    showDocuments on the GUI thread: activateMainWindow (minimized/tray), Search tab, ResultPanel.showResults (first
    selected), then the folder sync (showInRightmostFolderPanel, also with the sync button off), then the preview -
    in this order, as a folder browser changing folders clears the preview (found in the app test: blank preview).
    Tests: McpServerTest.showDocuments (no window, several in order, single path, not found, empty, outside);
    app: dev build minimized, sync button off -> curl show_document: window in front, Search tab with both documents,
    text preview of the first, folder browser on the inbox with the document selected.
- change (v2.10): to send files to the inbox, MCP routes them via DocFetcher - no direct file access for the LLM
  - The result of add_to_inbox had the host path of the new file (guiding the assistant to the inbox folder), and the
    inbox could be chosen by path. Now: result = name, renamed, size_bytes, inbox (tab name); inbox only by tab name,
    the names listed in the tool description; errors without paths (missing folder -> 'inbox X is not available',
    write errors only with the exception type). Description and server instructions: give the user files with
    add_to_inbox, never write into the user's folders yourself.
    Tests: McpInboxTest (no path in result/definition, inbox by path refused, missing folder error without path);
    app: SDK through the Claude Desktop extension - results without paths, description with the inbox names.
- change (v2.10): add_to_inbox for Office files (Claude: 'base64 at 69 KB is error-prone', cut 300 rows to 60)
  - Answers: DocFetcher creates xlsx, docx and pdf; no templates; base64 stays.
  - McpDocuments: xlsx (POI) from sheets [{name, rows, column_formats, header}] - numbers/booleans typed, '=...'
    formulas (results stored with XSSFFormulaEvaluator, recalculated by Excel on opening), ISO dates as dd.mm.yyyy
    dates, unique safe sheet names, bold frozen header, fitted columns; docx (POI) and pdf (PDFBox, A4, Helvetica,
    wrapping, page breaks, '?' for missing glyphs) from text with '#'/'##'/'###' headings and '- '/'* ' bullets.
    add_to_inbox takes exactly one of sheets / text / content_base64, chosen by the extension; the tool description
    steers assistants away from base64 for Office files.
    Tests: McpDocumentsTest (300-row workbook read back: types, formats, header, stored formula result, sheet
    names, errors; docx paragraphs/headings/bullets; pdf text, page breaks, '?'), McpInboxTest.officeFilesFromContent;
    app: SDK through the extension (dev build on port 28836) in both eras: 300-row xlsx (10 KB request), docx, pdf;
    read_document shows dates, amounts and the sum.
  - Test mishap: a sed edit left the extension on port 28835 (the user's running DocFetcher), four 139-byte test
    files landed in the user's inbox '00 Eingang'; moved to the recycle bin right away. skill.md: test on 28836 and
    check the target first.
- change (v2.10): base64 for pictures? + toggle a) only via MCP to the postbox (default) b) inbox path for the LLM
  - base64: no limit on DocFetcher's side (20 MB), but the assistant must type every byte - only small files work
    (a 100 KB PNG is ~100k tokens); a photo shown in the chat or a chart made in the cloud sandbox isn't available as
    bytes on this computer. SVG works as 'text'. Pictures: assistants with own file access, i.e. option b).
  - SettingsConf McpInboxPathVisible (off), checkbox below the inbox option, effective only together with it.
    McpInbox.fileRule() - one text for the server instructions (now built per request) and the tool: a) only
    add_to_inbox, never write yourself; b) inbox names with paths, 'only add new files there, never change,
    overwrite, rename, move or delete'; add_to_inbox then returns the path again.
    Tests: McpInboxTest.inboxPathOnlyWhenAllowed (default, allowed, only with adding files); app: dev build on port
    28836, checkbox in the preferences -> instructions and result with the path.
- change (v2.10): base64 effectively useless? -> download via URL into the inbox, with the user's consent
  - base64 only for small files. URLs of files an LLM made in its sandbox are not public (Claude: needs the browser
    session, ChatGPT: sandbox:/...), so the download helps for public files (forms, notices). No charts for now.
  - Answers: restricted, modal 'store file <> in folder <>', in no way allow execution.
  - McpDownload: https, port 443, all resolved addresses public (no loopback/private/link-local/CGNAT/ULA/mapped),
    redirects followed by hand and checked (max 5), 20 MB, 30 s; content: program magic (MZ, ELF, Mach-O, #!, .lnk)
    refused, known types must match their magic (web page as .pdf refused); Zone.Identifier ZoneId=3 + HostUrl.
    add_to_inbox 'url' only with SettingsConf McpInboxDownload (off, checkbox below the inbox option); address
    checked before asking; McpServer.DownloadApprover -> Application.askDownload (window to the front, question
    with file, folder, address; the request waits).
    Tests: McpDownloadTest (addresses, contents), McpInboxTest.downloadsOnlyWithTheUsersConsent (off, no approver,
    declined, private/blocked before asking, tool text); app (dev, port 28836): w3.org test PDF approved -> stored,
    %PDF, Zone.Identifier; declined -> nothing; web page as .pdf -> refused; 192.168.178.1 -> refused, no dialog.
- bug (v2.10): fresh package reported an inbox index folder as 'incompatible with this program version - delete?'
  - Cause (first guess 'index being built' was wrong): the folder inbox_1791542179954 was an empty leftover of the
    previous build's index - build.py emptied build/, Windows deleted the files but not yet the folder (an open
    handle). IndexRegistry.load takes a folder without tree-index.ser and with a name ending in a number for an
    index of DocFetcher 1.0.3.
  - Fix in IndexRegistry.load: folders created during this run (index being built) are skipped; empty folders are
    removed quietly; only folders with content are reported as before.
    Tests: ObsoleteIndexFilesTest (old index reported, empty leftover removed, new empty folder kept); app: dev build
    with an empty old inbox_<number> folder -> no question, folder gone.
- change (v2.10): MCP options in their own section/tab; what does 'allow placing files' do; remove base64
  - 'AI assistants may add new files to the inbox' was already the master switch of add_to_inbox (download and
    inbox path only work with it, McpInbox.isDownloadEnabled/isPathVisible), but the dialog didn't show it.
  - Preferences tab 'AI Assistants (MCP)' (intro text, server, add files, download, inbox path, port, help);
    CheckOption with an optional parent: indented and disabled while the parent is off or disabled, value kept.
  - content_base64 removed from add_to_inbox (user: not really useful); content is sheets, text or url.
    get_original_document (DocFetcher -> assistant) stays base64.
    Tests: McpInboxTest without base64 (base64 refused, 21 MB text too large, 5 MB text over HTTP); app: dev
    preferences - tab, indentation, greying on two levels, values kept, OK saves; schema without content_base64.
- change (v2.10): direct folder access is the only usable way - remove all others; the user chooses the folder
  - Answers: remove all (add_to_inbox with sheets/text/url, McpDocuments, McpDownload, download dialog, inbox-path
    option); folder = dropdown of the inbox folders; how the LLM learns it (my question was unclear): tool + rules.
  - McpFileFolder: SettingsConf.Str McpFileFolder (empty = none; must still be an inbox folder); rule() for the
    server instructions and the read-only tool get_file_folder (only listed with a folder; description = folder +
    'only add new files, never change, overwrite, rename, move or delete'). Request limit back to 1 MB.
    Preferences: McpFolderOption (dropdown '(none)' + inbox folders, fixed width) below the MCP checkbox, disabled
    while it is off (CheckOption passes its state to any dependent PrefOption).
    Caveat told to the user: Claude Desktop has no file access (the Filesystem extension writes text only), so it
    can't deliver Excel/Word files any more; Claude Code can.
    Tests: McpFileFolderTest (none, chosen, no longer an inbox); app: dev preferences - dropdown, greying, OK saves;
    tools/list with get_file_folder and the rule, add_to_inbox gone.
- change (v2.10): get_file_folder should make the LLM ask for permission to the folder
  - The assistant can't grant itself access; the rule now tells it to ask the user for access to exactly this folder
    (Claude Code: permission prompt or /add-dir <folder>; Claude Desktop: allowed folders of the Filesystem
    extension) and never to save elsewhere instead. Test: McpFileFolderTest.
- change (v2.10): MCP - LLMs went for the original documents (permission request per path in every conversation,
  rejecting it stopped Claude Desktop); full text by default, the original only when needed
  - Answers: ids + option for paths (next item); get_original_document only on explicit need; folder only on
    request with 'Remember my choice', covered by 'Reset Remembered Answers'.
  - McpIds: 12-hex SHA-256 ids of paths, map of the handed-out ids (LRU 200000); results carry id + folder inside
    the indexed folders (index folder name + subfolders; a drive root as 'Drive D'); read_document/
    get_original_document/list_folder take id, show_document ids, search folder_id; overview top folders with
    id + folder; no paths in errors. Instructions: ids, show_document to show, get_original_document only for
    the original itself.
  - get_file_folder: no folder in its description or the instructions; on a call DocFetcher asks (Application.
    askFileFolder, MultipleChoiceDialog with 'Remember my choice'), SettingsConf Int McpFileFolderAnswer (ask/
    allow/deny), reset by 'Reset Remembered Answers' and when another folder is chosen.
  - Found in the app test: the new constants between @Description and enum Str broke saving the settings (NPE);
    moved; SettingsConfTest saves the settings.
    Tests: McpServerTest (ids everywhere, no paths in results, unknown ids, folder_id filter), McpFileFolderTest
    (no folder in advance, asked, refused, remembered), SettingsConfTest; app (dev, port 28836): search without
    paths, dialog -> Allow + remember -> no second question, reset -> question again, Don't Allow respected.
- change (v2.10): MCP option to completely prevent returning document paths, except for the postbox
  - Paths are off by default (previous item); SettingsConf McpPathsVisible + checkbox below the MCP server: results
    also carry 'path' (documents, folders, listing, overview), the tools take path/folder/paths instead of
    id/folder_id/ids (schemas list them only then, ids then not required), the instructions mention it. The
    postbox folder (get_file_folder) is told either way after the user agreed.
    Tests: McpServerTest.pathsOnlyWhenAllowed (off: no paths, path refused; on: paths, path arguments, access
    check); app: dev preferences checkbox -> search result with path.
- change (v2.10): by default also add csv to index
  - Answer: new and existing indexes. IndexingConfig default text extensions txt csv java cpp py; TreeIndex
    csvVersion (0 in older indexes): FileIndex.update adds 'csv' to the text extensions once and parses the CSV
    files even if unchanged (FileContext.setReparseCsv), then setCsvCurrent; removed by hand it stays removed.
    Tests: ReparseOnceTest.outdatedIndexesAddCsvFilesOnce; app: dev build with its September inbox index - a CSV
    with an old date is found after the startup update.
- change (v2.10): menu tool to identify duplicate documents, by the document index (not the file name)
  - Answers: identical + nearly identical text; show + recycle bin. The index doesn't store the text (only for
    highlighting): DuplicateFinder works on the words of the index with their counts - identical: same words and
    counts (64-bit hash); nearly identical: MinHash over the word sets (64 values, LSH 16 bands x 4), >= 0.9
    (high, as invoices of the same kind share most words); fewer than 20 different words are ignored.
    Searcher.findDuplicates reads the postings of the content field per leaf, subtracts the words of the file name
    (the content field contains them; analyzer on 'name basename'), merges one file in two indexes, oldest first;
    cancelable, with progress.
  - DuplicatesDialog (File > Find Duplicates...): background search, tree of groups, group check = all but the
    oldest, Show in DocFetcher (Application.showDocuments without bringing the window up), Open in Explorer, Move
    Checked to Recycle Bin via ResultPanel.deleteDocuments (extracted from deleteSelection: confirmation, recycle
    bin, index update, undo history); every group keeps one document.
    Tests: DuplicatesTest (finder, through the index incl. copies under other names, cancel, singular/plural texts);
    app: dev build - groups (identical xlsx, letter + copy + second scan), group check, deletion to the recycle bin
    with confirmation, show in the main window, 'keep one' refused.
- change (v2.10): duplicates refer to the active folder; option 'only by content'
  - Answers: always the folder of the folder browser (rightmost; none: all indexes); only within that folder (with
    subfolders), 'Global' includes all; unchecked 'only by content' adds same file name + size (also without text);
    'only by content' on by default, remembered (SettingsConf DuplicatesContentOnly).
  - Searcher.findDuplicates(folder, contentOnly): documents outside the folder are skipped (UID -> path,
    normalized); name+size groups (lower-case name + stored size) unless a text group has them all; DuplicateGroup
    .Kind IDENTICAL_TEXT / NEARLY_IDENTICAL_TEXT / SAME_NAME_AND_SIZE. Dialog: checkboxes 'Only by content' and
    'Global (all indexes)' (disabled and on without a folder), status names the folder, changes restart the search.
    Tests: DuplicatesTest.folderAndNameAndSize; app: dev build - folder of the browser without duplicates, Global
    finds the workbooks, unchecked 'only by content' adds two same-named notes; the option is remembered.
- add version number to app in title / info so its visible to the user (v3.0)
  - The window title shows the version ('DocFetcher 3.0'); Help > About and the preferences already showed it. A build without a version (development) shows '(development version)' instead of 'Unspecified'.
- bin support (v3.0): all files deleted land in a bin folder defined in the settings; the bin appears in the left panel next to search; removal from the bin only after an explicit confirmation, into the OS bin (2nd safety net); toggle in the search panel to include the bin, default off
  - Answers: flat, with a list of origins (no entry: restore into the first inbox; with entry: ask original location or inbox); without a bin folder the OS recycle bin as before; checkbox right of the search field, off at every start; extras: restore, empty bin, bin hidden elsewhere.
  - Bin (gui): SettingsConf.Str.BinFolder; delete() moves into the bin under a free name (UtilFile.getFreeFile) and records the origin in the hidden .docfetcher-bin.properties (UTF-8, dos:hidden; written without replacing the file, which Windows refuses for hidden files); what is in the bin already goes to the OS recycle bin (UtilFile.delete). getOrigin (also for files inside a deleted folder), find (original after a merge), restore (to the origin or a folder, parents created, free name), empty, getEntries/getSize; stale entries are pruned.
  - All deletions go through Bin.delete: result tables and duplicates (ResultPanel.deleteDocuments: documents in the bin get their own confirmation), folders, merge/split originals, files created by an undone merge/split (the originals come back from the bin rather than the backup), the original after a drag copy. Into the bin the history gets a MOVE entry, so Ctrl+Z moves them back ('Move N file(s) back?'). UtilFile.move (from Application.moveFileOrDir) also moves folders to another drive; ResultPanel.moveFile uses it.
  - Bin tab: an InboxPanel in bin mode (listFolderEntries with folder rows, 'One entry, 1 KB', addButton for 'Restore...' and 'Empty Bin...') after the search tab, created/removed with the inbox folders (applyBin), indexed like an inbox (ensureIndexed). Restore: ResultPanel.restoreSelection/restoreFromBin, also in the context menu when all selected rows are in the bin.
  - Search: checkbox 'Bin' in the SearchBar (visible only with a bin, off at start); SearchQueue filters bin documents and folders out unless checked (as a location filter change). Duplicates: findDuplicates(..., excluded, ...) leaves the bin out unless the search is inside it. MCP: checkAllowed refuses bin files ('Not found (deleted)'), search and list_folder leave them out. The sync button doesn't follow into the bin.
  - Preferences, tab Folders: 'DocFetcher bin' with Choose.../Remove and a hint; the bin can't overlap an inbox folder; it is created on OK.
  - Fixes found on the way: the preview kept showing a deleted document (PreviewPanel.refresh(selected)); doubled apostrophes in 10 messages (Msg.format doubles them itself; MsgTest guards it).
  - Tests: BinTest (7), DuplicatesTest.theBinIsLeftOut, McpServerTest.theBinIsGoneForAssistants, MsgTest; full suite 220 tests, only the 4 known environment failures. App test (dev build): bin tab and checkbox, delete from search ('Move 1 file(s) to the DocFetcher bin?'), results 2 without / 3 with the checkbox, restore to the original location, Ctrl+Z via File > Undo, folder deleted from the folder browser into the bin, removed from the bin (OS recycle bin), Empty Bin, preferences section, removing the folder hides tab and checkbox.
- MCP story A (v3.0): settings for changes proposed by AI assistants + rename_document with naming rules
  - Settings (tab AI Assistants (MCP), below the server checkbox, off): McpRenameAllowed with the naming rules text
    (McpNamingRulesOption, Str.McpNamingRules, empty = localized default Msg.mcp_naming_rules_default), McpOutsideInbox
    ('Renaming and moving also outside the inbox folders'); McpMoveAllowed/McpSplitAllowed prepared for B and C.
  - McpFileChanges: tool definitions only when switched on (changingTool: readOnlyHint false, destructiveHint false),
    instructions rule, checkChangeable (plain file, inbox unless allowed outside, never the bin), newName (no paths,
    extension kept). McpServer.FileOrganizer: Application shows the document (showDocuments) and opens
    ResultPanel.renameProposed - the RenameDialog prefilled with the proposal and DocFetcher's keywords; the result
    (renamed + new id, or canceled) goes back to the assistant. read_document adds suggested_name (NameSuggester).
  - Tests: McpServerTest.renameOnlyWhenAllowedAndConfirmed (off: no tool; on: annotations, rules, suggested_name;
    outside the inbox refused unless allowed; paths refused; cancel; confirmed with the extension kept). App test (dev
    build, MCP on 28836, verified 'Unspecified'): search -> read_document suggested_name -> rename_document: DocFetcher
    showed the document and the prefilled dialog; OK renamed it (new id returned), Cancel reported 'canceled'.
- MCP story B (v3.0): move_document with DocFetcher's folder suggestions
  - McpMoveAllowed ('AI assistants may propose moving documents to another folder'); move_document(ids, folder_id):
    sources directly in an inbox folder (unless allowed outside; also for rename - subfolders of an inbox may be an
    archive), target an indexed folder (checkAllowed, not the bin), not the folder it is in. read_document adds
    suggested_folders (fixed move targets, then Searcher.suggestFolders like the move dialog, with folder location and
    match). FileOrganizer.move: Application shows the documents and opens ResultPanel.moveProposed - the move dialog
    with the proposal listed first, marked 'AI', preselected (MoveToFolderDialog.setProposal, wrapping message);
    the result has the new ids and the folder, or 'canceled', and says when the user chose another folder.
    McpTools.idList shared with show_document.
  - Tests: McpServerTest.moveOnlyWhenAllowedAndConfirmed (off; suggestions; refused outside the inbox, same folder,
    not a folder; cancel; confirmed with new ids). App test (dev build, MCP 28836): read_document suggested_folders
    (Archiv, Belege), move_document to Archiv: dialog with 'Archiv' preselected, Move -> moved with new id.
