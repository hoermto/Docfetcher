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

