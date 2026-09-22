Introduction
============

DocFetcher is an Open Source desktop search application: It allows you to search the contents of files on your computer. - You can think of it as Google for your local files.

**Index-based search**: Since searching in the documents directly would be impractically slow for a larger number of documents, DocFetcher requires that you create *indexes* for the folders you want to search in. These indexes allow DocFetcher to quickly look up files by keyword, similar to how you would use the index in the back of a book. Creating an index might take some time, depending on the number and sizes of the indexed files. However, this needs to be done only once for each folder; afterwards you can search in the indexed folders as many times as you want.

**Creating an index**: To create an index, choose `File > Indexes...` from the menu bar (this opens the preferences on the `Indexes` tab) and click on `Create Index... > Folder`. (You can also get there by right-clicking on the `Search Scope` area of the search settings dialog and selecting `Manage Indexes...`.) Now choose a folder to be indexed. For starters, this should be a small one with not too many files in it, like, say, 50? After selecting the folder, a configuration window pops up. The default configuration should suffice, so just click on the `Run` button and wait until DocFetcher has finished indexing the documents. (An alternative way of creating an index is to paste a directory from the clipboard into the `Search Scope` area by pressing `Ctrl+V`. However, this only works for ordinary directories, not for PST files.)

**Searching**: Enter one or more words to search for in the text field above the result pane (the table with the column headers) and press the `Enter` key. The search results will be displayed in the result pane, sorted by modification date with the newest documents on top. Click on a column header to sort by that column instead.

*If you are reading this manual inside DocFetcher, following the instructions in the next paragraph will make the manual disappear. To restore it, click on the `'?'` button on the top right. You can also open the manual in your default web browser by clicking on the `Open in external browser` button directly above this pane.*

**Result pane and preview pane**: Below the result pane (or to the right of it, depending on the current GUI layout), you can find the preview pane. If you select a file on the result pane, the preview pane will show a text-only preview of the file's contents. Notable features:

* ***Highlighting***: By default, the search terms you've entered will be highlighted, and you can jump from one occurrence to the previous or next using the up and down buttons.
* ***Built-in web browser***: In case of HTML files, you can switch between the text-only view and a simple built-in web browser. (Note: The latter is not available on some Linux variants.)
* ***Images***: PNG, JPEG, GIF, BMP and TIFF files (e.g. scans) are shown as pictures, fitted to the width of the pane; use the zoom buttons to enlarge or shrink them.

One shortcut you might find useful: Press `Ctrl+F` or `Alt+F` to move the focus back to the search field. To open a file in an external program, double-click on it in the result pane.

The `Folder` column shows the folder a document is in (the file name has its own column); the full path is shown as a tooltip and, for the selected document, in the status bar. The column can be turned off in the preferences.

**Sorting**: You can change the sorting of the results by clicking on any of the result pane's column headers. For example, to sort the results by filename, click on the `Filename` header. Clicking the same header twice will sort by the corresponding criterion in reversed order. You can also change the order of the columns via drag and drop: For example, if you want `Filename` to be the first column, just drag the `Filename` column header to the left.

**Filtering**: Click on the `Search Settings...` button next to the `Search` button to open the search filters: (1) The `Document Types` list allows you to filter the results by type. (2) By unchecking items in the `Search Scope` area, you can filter the results by location. (3) With `Also match file names` checked (the default), the search terms also match any part of the file names, so that e.g. "invoice" finds `myinvoice_2024.pdf` even if the word doesn't occur in the document. Filter changes take effect immediately.

**Renaming and moving documents**: Renaming and moving files can be undone with `Ctrl+Z` or `File > Undo file operation` (the last 20 operations of the running program); deleted files are in the recycle bin and are restored from there.

Files in the result table can be renamed (`F2` or `Rename...` in the context menu) and moved into another folder: the `Move To` submenu of the context menu lists your fixed move targets (preferences, `Folders` tab) first, then the last five folders you moved files into, then up to five suggested folders whose documents have similar content, based on the keywords in your indexes, plus `Other Folder...`, which opens a dialog with the same entries and a folder chooser. Dragging files from the result table into Windows Explorer moves them; hold `Ctrl` while dragging to copy them instead, in which case DocFetcher asks whether to keep the original. The affected indexes are updated automatically. These operations are not available for emails and for files inside archives.

**Inbox**: If you add one or more `Inbox folders` in the preferences (`Folders` tab) - typically the folder where your scanner saves new documents - each of them gets its own tab in front of the `Search` tab, named after the last part of the folder path unless you set a name. Such a tab lists all files in the folder - whether they are indexed or not - with the same table as the search results, so that you can preview, rename and move them; the folders are indexed automatically (which enables the name and folder suggestions) and are never suggested as move targets. The first two columns show a pen and a folder icon for each file: click the pen to rename it, the folder to move it. The `Rename To` entry of the context menu offers file names of the form `<date>_<keywords>`, e.g. `2026-03-12_Deutsche-Telekom-Rechnung.pdf`: the date is the first date found in the document (otherwise the file date), and the keywords are the words that distinguish the document most from your other documents, with a preference for the first lines. Variants with 2 to 5 keywords are offered; `Rename...` (`F2`) opens a dialog prefilled with the suggestion where the keywords can be combined freely.

**Folders**: The `Folders` tab is a simple file browser over your documents. Its root level lists the folders of your indexes and your fixed move targets; a double-click on a folder row enters it, and the `..` row at the top, the `Up` button or the left arrow key (or backspace) lead back to the parent folder - and from a root folder back to the list of root folders. Inside a folder you see its subfolders first and then all of its files, indexed or not, in the same table as the search results: selecting a file shows it in the preview pane, the full path appears in the status bar, and the context menu offers the same `Rename...`, `Rename To`, `Move To` entries as on the inbox tabs. Files can also be filed by dragging them onto a folder row - the row is highlighted while dragging, and dropping moves the files into that folder (hold `Ctrl` to copy); dropping them anywhere else in the list files them in the folder currently shown. The toolbar has buttons for the list of root folders, the parent folder, reloading and creating a subfolder, and `Delete` in the context menu (or the `Del` key) moves the selected files to the recycle bin, after a confirmation. `Show in Folder View` in the context menu of the search results opens a document's folder here, and with `Show the folder of the selected search result in the folder view` (preferences, on by default) the browser follows the selected search result on its own. With `View > Folder panel` (`F9`) the browser moves out of its tab and becomes a panel of its own between the tabs and the preview pane: that way you can drag documents from an inbox tab or from the search results straight into a folder, and the preview always shows the document you clicked last, in either panel. The folder you last browsed is restored on the next start.

**Text recognition (OCR)**: DocFetcher comes with the Tesseract engine (German and English). While indexing, the text of image files (PNG, JPEG, GIF, BMP, TIFF) and of PDF files that contain no text (typical scanner output) is recognized, so that scans can be searched like other documents and get name and folder suggestions. The recognized text is stored in the files so that recognition happens only once (this can be switched off with `Store the recognized text in the files`, then the text only goes into the index and has to be recognized again on every rebuild): PDF files get an invisible text layer and PNG/JPEG files carry the text in their metadata; the image data and the file dates stay unchanged. TIFF files are recognized on the fly; optionally (`Convert TIFF files to PDF files with a text layer` in the preferences, off by default) they are converted into such PDFs, with the original either deleted or kept as `name.tif.ocr`. Files inside archives are not modified. The option can be switched off in the preferences. On Windows the engine needs the Microsoft Visual C++ 2015-2022 Redistributable (x64); if it can't be loaded, the preferences dialog shows the reason.

**Index updates**: If files in the indexed folders are added, removed or modified, the corresponding indexes have to be updated, otherwise your search results might be out of date. Fortunately, updating an index is virtually always much faster than creating it from scratch, since only the changes have to be processed. Also, DocFetcher can update its indexes automatically in two ways:

1. ***DocFetcher itself***: If DocFetcher is running and the *folder watching* for the modified folder is enabled, DocFetcher detects the changes and updates its indexes immediately.
2. ***DocFetcher daemon***: If DocFetcher isn't running, the changes are recorded by a small daemon program that runs in the background; the affected indexes will then be updated the next time DocFetcher starts. (Note: The daemon is currently not available on Mac OS&nbsp;X, unfortunately.)

Some caveats: If you are using the portable version of DocFetcher and want the daemon to be run, you must install it manually by adding the daemon executable to your operating system's list of startup programs. Also, neither DocFetcher nor the daemon can detect changes on network shares.<!-- this line should end with two spaces -->  
So, in those cases where the indexes can't be updated automatically, you'll have to do it yourself: Open the preferences, switch to the `Indexes` tab, select one or more indexes to be updated and click on `Update`.

* * *

<a name="Advanced_Usage"></a> <!-- Do not translate this line, just copy it verbatim. -->

Advanced Usage
==============

**Query syntax**: With DocFetcher, you can do much more than simple word lookup. For example, you can use wildcards to search for words with a common start, like so: `wiki*`. To search for a certain phrase (i.e. a sequence of words in a specific order), surround the phrase with quotation marks: `"the quick brown fox"`. But that's barely the start. For an overview of all the supported contructs, see the [query syntax section](DocFetcher_Manual_files/Query_Syntax.html).

**Preferences**: On the top right of the user interface, you'll find an icon depicting two cogwheels. Click on it to open the Preferences dialog. Additional settings for more advanced usage can be accessed via the "Advanced Settings" link at the lower left of the Preferences dialog.

**Portable document repository**: The portable version of DocFetcher allows you create a bundle containing DocFetcher, your documents and the associated indexes, and then freely move this bundle around &mdash; even from one operating system to another, e.g. from Windows to Linux and vice versa. One important thing to keep in mind when using the portable version is that the indexes must be created with *relative paths*. Click [here](DocFetcher_Manual_files/Portable_Repositories.html) for more information about portable document repositories. By the way, if you've been using DocFetcher 1.0.3 and earlier, note that you're not required to put your documents into the DocFetcher folder anymore.

**Indexing configuration options**: For a detailed discussion of all those options on the indexing configuration window, click [here](DocFetcher_Manual_files/Indexing_Options.html). You can also reach this manual page directly from the configuration window by clicking on the `Help` button at the bottom of the window. Perhaps the most interesting configuration options are:

* ***Customizable file extensions***: The file extensions for plain text files and zip archives are fully customizable. This is particularly useful for indexing source code files.
* ***File exclusion***: You can exclude certain files from indexing based on regular expressions.
* ***Mime type detection***: Without mime type detection, DocFetcher will just look at a file's extension (e.g. `'.doc'`) to determine its file type. With mime type detection, DocFetcher will also peek into the file's contents to see if it can find any better type info. This is slower than just checking the file extension, but it's useful for files that have the wrong file extension.
* ***HTML pairing***: By default, DocFetcher treats an HTML file and its associated folder (e.g. a file `foo.html` and a folder `foo_files`) as a single document. The main purpose of this is to make all the "clutter" inside the HTML folders disappear from the search results.

**Regular expressions**: Both the file exclusion and the mime type detection rely on so-called *regular expressions*. These are user-defined patterns that DocFetcher will match against filenames or filepaths. For example, to exclude all files starting with the word "journal", you can use this regular expression: `journal.*`. Note that this is slightly different from DocFetcher's query syntax, where you would omit the dot: `journal*`. If you want to know more about regular expressions, read this [brief introduction](DocFetcher_Manual_files/Regular_Expressions.html).

**Release notification**: DocFetcher does not (and should not?) automatically check for updates. If you *do* wish to be notified of new releases, there are [a couple of ways](DocFetcher_Manual_files/Release_Notification.html) to set this up.

* * *

<a name="Caveats"></a> <!-- Do not translate this line, just copy it verbatim. -->

Caveats and Common Gotchas
==========================

**Raising the memory limit**: DocFetcher, like all Java programs, has a fixed limit on how much memory it's allowed to use, known as the *Java heap size*. This memory limit must be set on startup, and DocFetcher currently chooses a default value of 256&nbsp;MB. If you try to index a very, very large number of files, and/or if some of the indexed files are really huge (which is not uncommon with PDF files), then chances are DocFetcher will hit that memory limit. If this ever happens, you might want to [raise the memory limit](DocFetcher_Manual_files/Memory_Limit.html).

**Don't index system folders**: In contrast to other desktop search applications, DocFetcher was not designed for indexing system folders such as `C:` or `C:\Windows`. Doing so is discouraged for the following reasons:

1. ***Slowdown***: The files in system folders tend to be modified very frequently. If the folder watching is turned on, this will cause DocFetcher to update its indexes all the time, slowing down your computer.
2. ***Memory issues***: DocFetcher needs to keep tiny representations of your files in memory. Because of this, and because system folders usually contain a very large number of files, DocFetcher will be more likely to run out of memory if you index system folders.
3. ***Waste of resources, worse search results***: Apart from these technical reasons, indexing system folders is most likely a waste of indexing time and disk space, and it will also pollute your search results with unneeded system files. So, for the best results in the least amount of time, just index what you need.

**Unicode support**: DocFetcher has full Unicode support for all document formats. In case of plain text files, DocFetcher has to use [certain heuristics](http://www-archive.mozilla.org/projects/intl/UniversalCharsetDetection.html) to guess the correct encoding, since plain text files don't contain any explicit encoding information.

**Archive support**: DocFetcher currently supports the following archive formats: zip and derived formats, 7z, rar and the whole tar.* family. Additionally, executable zip and 7z archives are supported as well, but not executable rar archives. DocFetcher will treat all archives as if they were ordinary folders, and it can also handle an arbitrarily deep nesting of archives (e.g. a zip archive containing a 7z archive containing a rar archive...).<!-- this line should end with two spaces -->  
With that said, it should be noted that support for zip and 7z archives is best in terms of robustness and speed. On the other hand, indexing of tar.gz, tar.bz2 and similar formats tends to be less efficient. This is due to the fact that these formats don't have an internal "summary" of the archive contents, which forces DocFetcher to unpack the entire archive rather than only individual archive entries. Bottom line: If you have the choice, compress your files either as zip or 7z archives for maximum compatibility with DocFetcher.

**The DocFetcher daemon is innocent**: If you suspect that the DocFetcher daemon is slowing down your computer or causing crashes, you're probably wrong. As a matter of fact, the daemon is a very simple program with low memory footprint and CPU usage, and it doesn't do much besides watching folders. If you're still not convinced, just rename the daemon executables so they won't start automatically, or try the portable version of DocFetcher, where the daemon is deactivated by default.

* * *

<a name="Subpages"></a> <!-- Do not translate this line, just copy it verbatim. -->

Manual Subpages
===============
* [Query syntax](DocFetcher_Manual_files/Query_Syntax.html)
* [Portable document repositories](DocFetcher_Manual_files/Portable_Repositories.html)
* [Indexing options](DocFetcher_Manual_files/Indexing_Options.html)
* [Regular expressions](DocFetcher_Manual_files/Regular_Expressions.html)
* [Release notification](DocFetcher_Manual_files/Release_Notification.html)
* [How to raise the memory limit](DocFetcher_Manual_files/Memory_Limit.html)
* [How to raise the folder watch limit (Linux)](DocFetcher_Manual_files/Watch_Limit.html)
* [Preferences](DocFetcher_Manual_files/Preferences.html)

Further Information
===================
For more information, have a look at our [wiki](http://docfetcher.sourceforge.net/wiki/doku.php). If you have any questions, feel free to visit our [forum](http://sourceforge.net/projects/docfetcher/forums/forum/702424). Bug reports can be submitted on our [bug tracker](http://sourceforge.net/tracker/?group_id=197779&atid=962834).
