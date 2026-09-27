Quick Start
===========
DocFetcher searches the full text of your documents - PDF files, scans, Office files, emails and more - and helps you to file them: rename, move, merge, split, print and email them. This page shows the everyday steps; the [full manual](../DocFetcher_Manual.html) explains everything in detail.

1. Index your folders
---------------------
DocFetcher searches in *indexes*. Choose `File > Indexes...`, then `Create Index... > Folder`, select a folder and click `Run`. Indexes stay up to date automatically while DocFetcher runs. Scanned documents are recognized (OCR) while indexing, so that they can be searched too.

2. Search
---------
Type words into the search field and press `Enter` - it works like Google:

Query          | Finds documents with...
---------------|---------------------------------
`invoice 2025` | both words
`invoice -gas` | `invoice`, but not `gas`
`gas OR water` | one of the words
`"Stadtwerke Bonn"` | exactly this phrase
`insur*`       | words starting with `insur`

Umlauts and typical OCR errors don't matter: `Muller` also finds `Müller`, `Bonn` also finds `B0nn`. The `?` button next to the search field opens the [search help](Query_Syntax.html). Click a result to see the document on the right, with the words highlighted; the filters on the left side of the results narrow them down by file type, size or folder.

3. Inbox: file new documents
----------------------------
Add your scanner's folder as an `Inbox folder` (`File > Preferences`, tab `Folders`); it gets its own tab with the newest documents on top. For each document:

* **Rename**: click the pen (or `F2`) - DocFetcher suggests a name like `2025-03-12_Stadtwerke-Invoice.pdf` from the date and the key words of the text.
* **Move**: click the folder icon - DocFetcher suggests the folders where similar documents are, or drag the document onto a folder (hold `Ctrl` to copy).
* **Split / merge**: `Split PDF` divides a scanned stack into single documents (at separator sheets or where the text changes); `Merge PDFs` staples several PDF files into one.
* **Print / email**: `Print` (`Ctrl+P`) and `Send by Email` work on all selected documents.

Everything can be undone with `Ctrl+Z`; removed files go to the recycle bin.

4. Browse folders
-----------------
The toolbar buttons with the columns choose one, two or three panes next to the document view (`F3` switches): the tabs only, the tabs plus a folder browser, or two folder browsers side by side for sorting documents into folders. With the sync button the browsers move together, and a click on a search result shows it in its folder. `F7` jumps to the search field, `F8` to the inbox (again: the next one), `F5` to the folder browser.

5. Good to know
---------------
* The toolbar works on the list you clicked last; hover over a button to see what it does.
* `View > Icon size` makes the toolbar icons bigger or smaller.
* DocFetcher never changes documents that contain text. Only scans without text get their recognized text stored in the file, once (can be switched off in the preferences).
* AI assistants like Claude can search your documents and read them (read-only, this computer only): switch on `MCP server for AI assistants` in the preferences - see 'AI assistants' in the [full manual](../DocFetcher_Manual.html).
* More: [preferences](Preferences.html), [search syntax](Query_Syntax.html), [full manual](../DocFetcher_Manual.html).
