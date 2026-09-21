Preferences
===========
The preferences dialog has two tabs: `General` and `Indexes`.

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
Preview position  |  Whether the preview pane is shown to the right of or below the result table.
Path segments shown for suggested folders  |  How many trailing parts of a folder path are shown in the first column of the `Move to Folder` dialog, e.g. 2 shows `Projects/Reports`; the rest of the path follows in parentheses.
Word segmentation  |  The word segmentation algorithm to use during indexing. The standard algorithm works well with Western languages like English and German. The source code and Chinese algorithm are intended for indexing source code and Chinese text, respectively. Changing the word segmentation algorithm requires rebuilding all existing indexes.
Highlight color  |  The color to use for highlighting matches in the preview pane.
Preview font (normal)  |  The font to use in the text preview; applies to all document formats except plain text files.
Preview font (fixed width)  |  The font to use for the text preview; applies only to plain text files. You are not required to pick a fixed-width font here, although it's usually a sensible choice.
Global hotkey  |  The keyboard shortcut specified here can be used to move the DocFetcher window to the top after it has lost focus.

Indexes tab
-----------
The `Indexes` tab lists all existing indexes and provides buttons for managing them:

Button  |  Comment
--------|---------
Create Index...  |  Creates a new index from a folder, an archive, an Outlook PST file or a folder path in the clipboard.
Update  |  Updates the selected indexes so that they reflect changes made to the indexed documents.
Rebuild  |  Rebuilds the selected indexes from scratch. This is necessary after changing the indexing options of an index or the word segmentation algorithm.
Rename...  |  Changes the display name of the selected index.
Remove  |  Removes the selected indexes.
Remove Orphaned  |  Removes all indexes whose document folders no longer exist.
Index folder  |  The folder where DocFetcher stores its index files. Leave it empty to use the default location. Changing the folder takes effect after DocFetcher is restarted; you will be asked whether the existing indexes should be moved to the new folder on the next start.
