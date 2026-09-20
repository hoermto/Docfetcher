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
