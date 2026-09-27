Preferences
===========
The preferences dialog is opened via `File > Preferences...` in the menu bar and has three tabs: `General`, `Indexes` and `Folders`.

General tab
-----------
Option  |  Comment
--------|---------
Show manual on startup  |  Whether to open the manual when DocFetcher starts up.
Use OR operator as default in queries (instead of AND)  |  If checked, DocFetcher will treat consecutive words in a query as if they were concatenated with the `OR` operator. Otherwise, the `AND` operator will be used. See the [query syntax](Query_Syntax.html) for more.
Automatically scroll to first match in preview pane  |  If checked, DocFetcher will automatically scroll to the first match in the preview pane after you click on a file in the result table and the file is loaded into the preview pane.
Hide program in System Tray after opening files  |  If checked, DocFetcher will automatically hide in the System Tray whenever you double-click a file on the result pane, or open a folder from the `Search Scope` area.
Start DocFetcher with Windows  |  (Windows only) If checked, DocFetcher is started automatically when you log on to Windows. This creates a startup entry for the current user; unchecking removes it again.
When closing the program window  |  What happens when you click on the close button of the main program window: `Exit` closes the program, `Minimize to taskbar` keeps it running as a minimized window, and `Hide in system tray` hides it in the System Tray. In the latter two cases, use the `Exit DocFetcher` toolbar button (or `Ctrl+Q`, or the System Tray menu) to actually exit.
Clear search history on exit  |  If checked, DocFetcher will wipe out its history of entered queries on exit.
Update indexes on startup  |  If checked, all indexes whose folders are watched for changes (see the `Watch folders for file changes` indexing option) are updated in the background when DocFetcher starts. This picks up files that were added, changed, renamed or removed while DocFetcher was not running, even if the DocFetcher daemon is not installed.
Icon size  |  Size of the icons in the program window. `Automatic` derives the size from the resolution of the screen, which makes the icons bigger on a high resolution display. The default is 200 %. The size can also be chosen under `View > Icon size`; a new size takes effect after a restart.
Show the folder of a document as a column in the result tables  |  If unchecked, the folder column is hidden; the full path is still shown as a tooltip and in the status bar.
Update all indexes once a day in the background  |  If checked, all indexes are updated once a day while DocFetcher runs, so that deleted or changed files are picked up even in folders that aren't watched. Independently of this option, search results whose files no longer exist are dropped and the affected index is updated.
Recognize text in images and scanned PDFs (OCR)  |  If checked, the text of image files and of PDF files without text is recognized while indexing and stored in the files (see the manual's section on text recognition). Uncheck it if files must not be modified or recognition takes too long.
Store the recognized text in the files  |  If unchecked, recognized text is only put into the index and no file is modified.
Convert TIFF files to PDF files with a text layer  |  If checked (off by default), TIFF files are replaced while indexing by PDF files containing the scanned pages and the recognized text.
Keep the original TIFF file, renamed to '.tif.ocr'  |  If checked, a converted TIFF file is renamed instead of deleted, so that it can be checked later.
Ask to rebuild indexes created by older versions  |  If checked, DocFetcher offers to rebuild indexes that were created by an older version and lack some information. Turned off by `Don't Ask Again` in that question.
MCP server for AI assistants  |  If checked (off by default), AI assistants like Claude can search the indexed documents and read their text via the Model Context Protocol, on `http://localhost:<port>/mcp` - only programs on this computer can connect, and they can't change anything. See 'AI assistants' in the manual.
MCP server port  |  The port of the MCP server (default 28835); change it if another program uses this port. `How to connect Claude...` below it (also shown when the server is switched on) shows how to add the server to Claude Code or Claude Desktop, with buttons to copy the command and the configuration.
Reset Remembered Answers  |  Questions that were answered with `Remember my choice` or `Don't Ask Again` (e.g. what to do with the originals after merging or splitting PDFs, rebuilding outdated indexes) are asked again, and hints that are shown only once appear again. The options above are set back to asking; the change takes effect with `OK`.
Preview position  |  Whether the preview pane is shown to the right of or below the result table.
Path segments shown for suggested folders  |  How many trailing parts of a folder path are shown in the `Folder` column of the `Move to Folder` dialog, e.g. 2 shows `Projects/Reports`; the leading part of the path is shown in the `Location` column.
Word segmentation  |  The word segmentation algorithm to use during indexing. The standard algorithm works well with Western languages like English and German. The source code and Chinese algorithm are intended for indexing source code and Chinese text, respectively. Changing the word segmentation algorithm requires rebuilding all existing indexes.
Highlight color  |  The color to use for highlighting matches in the preview pane.
Preview font (normal)  |  The font to use in the text preview; applies to all document formats except plain text files.
Preview font (fixed width)  |  The font to use for the text preview; applies only to plain text files. You are not required to pick a fixed-width font here, although it's usually a sensible choice.
Global hotkey  |  The keyboard shortcut specified here can be used to move the DocFetcher window to the top after it has lost focus.
Key for the inbox tabs / the search field / the folder browser  |  Keys inside the program window: they show the inbox tabs (pressed again: the next inbox), the `Search` tab with the cursor in the search field, and the folder browser (the `Folders` tab, or the middle pane with three panes). Defaults are `F8`, `F7` and `F5`, shown in the tab titles; function keys work without `Ctrl`, `Alt` or `Shift`.

Indexes tab
-----------
The `Indexes` tab lists all existing indexes with their status (`Queued`, `Creating`/`Updating`/`Rebuilding` with the progress in percent, or the time of the last update) and provides buttons for managing them. While indexing runs, the bottom right of the main window shows a progress bar for all queued tasks; clicking it (or double-clicking an index with a running task in the list) shows the indexing window with the queue, the configuration of new indexes and any errors. A new index stays in the state `Not started` until you press `Run` in that window.

Button  |  Comment
--------|---------
Create Index...  |  Creates a new index from a folder, an archive, an Outlook PST file or a folder path in the clipboard.
Update  |  Updates the selected indexes so that they reflect changes made to the indexed documents.
Rebuild  |  Rebuilds the selected indexes from scratch. This is necessary after changing the indexing options of an index or the word segmentation algorithm.
Rename...  |  Changes the display name of the selected index.
Remove  |  Removes the selected indexes.
Remove Orphaned  |  Removes all indexes whose document folders no longer exist.
Index folder  |  The folder where DocFetcher stores its index files. Leave it empty to use the default location. Changing the folder takes effect after DocFetcher is restarted; you will be asked whether the existing indexes should be moved to the new folder on the next start.

Folders tab
-----------
Option  |  Comment
--------|---------
Inbox folders  |  Folders each of which is listed on its own tab of the main window, e.g. where a scanner saves new documents. `Add...` adds a folder, `Rename...` sets the name of its tab (empty: the last part of the folder path). The folders are indexed automatically if no index covers them yet.
Fixed move targets  |  Folders that are always listed first in the `Move To` context menu and in the move dialog, above the folders suggested by content, e.g. your usual filing folders.
