# DocFetcher - extended edition

![Version](https://img.shields.io/badge/version-3.2-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![License](https://img.shields.io/badge/license-EPL--1.0-green)

This is a fork of **[DocFetcher](https://docfetcher.sourceforge.io/)** by Tran Nam Quang and contributors - the
open source desktop search application that indexes your folders and finds documents by their content in a
fraction of a second. All credit for the search engine, the parsers and the original user interface goes to the
original project; its source is at [github.com/docfetcher/DocFetcher](https://github.com/docfetcher/DocFetcher).

The fork is based on the upstream state of July 2026 (DocFetcher 1.1.x) and continues with its own version
numbers from 2.0 on. It turns DocFetcher from a pure search tool into a tool for **filing paper and digital
documents**: scans are recognized, named, split and moved into folders, and AI assistants can search and read
your documents. The previous README of this repository is kept as [README.md.original](README.md.original).

## Why this fork

All documents live in one large, grown folder structure on a Windows PC - documents of every kind, but mostly
correspondence: letters, invoices and contracts, scanned when they come in on paper or saved when they go out.
What was missing is a **full-text search engine on top of these folders** and a little help with the daily
paperwork, without giving up control over the files.

The goals:

- **An add-on, not a new home for the documents.** The folder structure stays exactly as it is, and it remains
  the one place where the documents live. DocFetcher indexes it and helps to file new documents into it, but never
  imposes its own structure.
- **No lock-in.** The documents stay plain files in plain folders, with readable names. Stop using DocFetcher, and
  nothing is lost: there is no database you have to export from, and no file renamed into an internal ID.
- **Native on the PC, no server.** DocFetcher is a desktop program that works directly on the local (or network)
  folders - no server runtime, no Docker, no web application to install and keep running, unlike document
  management systems such as Paperless-ngx.
- **Simple workflow for paper mail:** scan into an inbox folder, split the scanned stack into single documents,
  rename them (with a suggested name), and move them into the right folder (with a suggested folder).
- **Simple search and preview:** type a few words and see the matching documents, newest first, with a preview of
  each one.

Photos and media collections are not the focus; the tool is made for documents.

## What is different from the original DocFetcher

The sections below describe the changes from the user's point of view. Every detail is in the
[changelog](website/content/extra/changelog.txt) (entries 2.0 to 3.2).

### Searching

- **Google-style search**: all words must occur, `-word` excludes, `+word` requires, `"exact phrase"`, `OR` for
  alternatives. A `?` button next to the search field explains the syntax.
- **Tolerant search**: `Muller` finds `Müller`, `strasse` finds `Straße`, and typical OCR errors are tolerated
  (`Bonn` finds `B0nn`). This works at search time - indexes don't have to be rebuilt.
- **File and folder names count**: `Rosenweg rent` finds the contract in `Tenants/Rosenweg 12` even if the text
  never mentions Rosenweg. Matching folders are listed above the documents.
- **More content is searchable**: values of filled PDF forms, Excel formula results (`286,52 €`), CSV files, and
  the text of scanned documents (see OCR below).
- **Results newest first**, with file name, folder, date and page count; deleted or moved files disappear from
  the results by themselves.
- **Find Duplicates**: finds documents with identical or nearly identical text, even with different names or
  formats (e.g. a letter scanned twice).
- A message explains what to try when a search finds nothing (can be switched off).

### Preview

- **Rich preview**: PDFs in the PDF viewer of the embedded browser, Word, Excel and RTF as formatted documents,
  images with zoom, multi-page TIFFs page by page.
- Search terms are **highlighted in the PDF preview**, which opens on the page with the first match.
- The preview sits right of the results by default and can be shown or hidden with `F4`.

### Scanner inbox and filing

- **Inbox tabs**: choose one or more inbox folders (e.g. where the scanner saves). Each gets its own tab listing
  all its files, ready to be handled right away.
- **Name suggestions**: DocFetcher proposes names like `2025-03-12_Stadtwerke-Invoice.pdf` from the date and
  keywords of the document; the rename dialog lets you combine the keywords freely.
- **Folder suggestions**: "Move To" offers the folders whose documents are most similar, plus your fixed targets
  and the last folders used.
- **Folder browsers**: one, two or three panes next to the preview (`F3`), synced with each other and with the
  selected search result, for filing by drag and drop. `Ctrl` + drag copies.
- **File operations everywhere**: rename (`F2`), move, copy, delete, create and rename folders, open in Explorer -
  from the result tables, the inbox tabs and the folder browsers. **Everything can be undone with `Ctrl+Z`.**
- **DocFetcher bin** (optional): deleted files go to a bin folder with a "Bin" tab, from where they can be
  restored to where they were; otherwise they go to the recycle bin of the system.

### Scans: OCR, split and merge

- **Built-in text recognition** (Tesseract, German and English): image files and PDFs without text become
  searchable. The recognized text can be stored in the PDF itself, so it is recognized only once - documents
  that already contain text are never changed.
- **Split PDF**: a scanned stack is split into single documents at separator sheets, at blank pages or where the
  text changes; you see every page and tick where a new document starts. Blank pages are left out.
- **Merge PDFs** in any order, with a suggested name.

### Print and email

- **Print** the selected documents (`Ctrl+P`) - PDFs and images with one print dialog in their real size.
- **Send by Email** opens a new message in your email program with the selected documents attached.

### AI assistants (MCP server)

An optional, built-in [MCP](https://modelcontextprotocol.io/) server (off by default, only reachable from your
own computer at `http://localhost:28835/mcp`) lets assistants like Claude, ChatGPT or Codex work with your
documents:

- **Search and read**: *"Which letters about tax arrived last?"*, *"What rent does my tenant in Rosenweg pay?"*
  Large result sets come with an overview (time range, folders, typical words) so the assistant can narrow them.
- **Show** documents in DocFetcher's window (*"show me the rental contract"*).
- **Propose** - never perform - renaming, moving and splitting documents (each off by default): DocFetcher opens
  its own dialog prefilled with the proposal, and you decide.
- Assistants get short ids instead of file paths, and cannot change or delete your files.
- The preferences explain how to connect each assistant; for Claude Desktop there is a one-click extension
  (`docfetcher.mcpb`).

### Program and window

- Tabbed **preferences** with index management, index folder, inbox folders, fixed move targets and the
  assistant options; a progress bar shows running indexing tasks.
- Indexes are **updated at startup** and synced daily at a fixed time - also on network drives, whose changes
  aren't reported by the system.
- New **toolbar** with flat icons, a menu bar, keyboard shortcuts for the tabs (`F7` search, `F8` inbox, `F5`
  folders), selectable icon size for high-resolution screens.
- Only **one instance** runs at a time; optional start with Windows and close to the taskbar or system tray.
- Files are no longer locked while DocFetcher reads them, so they can be moved or deleted at any time.
- Complete **German translation** of the interface and an updated German manual; a short quick start guide is
  shown on the first start.
- Windows **MSI installer** (installs for all users) next to the portable package; settings are kept in the
  user's settings folder, so they survive replacing the portable package.
- Removed: the "DocFetcher Pro" hints, the file-size filter and the Score, Size and Authors columns.

## Getting started

1. Install `DocFetcher-<version>-Windows-64bit.msi`, or unpack the portable package and start `DocFetcher.exe`.
2. `File > Indexes... > Create Index... > Folder`, choose a folder and click `Run`.
3. Optionally set your scanner folder as inbox folder: `File > Preferences > Folders`.
4. Type words into the search field and press `Enter`; the `?` button explains the search syntax.

## Documentation

| Topic | Where |
|-------|-------|
| Quick start guide (shown in the app) | [English](doc/src/en/DocFetcher_Manual_files/Quick_Start.markdown), [German](doc/src/de/DocFetcher_Manual_files/Quick_Start.markdown) |
| User manual | [English](doc/src/en/DocFetcher_Manual.markdown), [German](doc/src/de/DocFetcher_Manual.markdown) |
| Changelog | [website/content/extra/changelog.txt](website/content/extra/changelog.txt) |
| Building, tests, packaging, project layout | [README.md.original](README.md.original), [tasks/skill.md](tasks/skill.md) |

## Building from source

### Development build

```bash
./mill compile                  # on Windows: mill.bat compile
```

### Windows packages and MSI installer

Requirements:

- **JDK 21 on the `PATH`**: `build.py` compiles the sources itself with `javac` and `jar` (Mill is not used).
- **Python 3** with the package `markdown` (for the manual): `pip install markdown`.
- **`build-jre.txt`** (git-ignored) with `jre_windows=<folder>`: a Java 21 runtime made with `jlink` that is
  bundled with the packages (module list in [tasks/skill.md](tasks/skill.md)).
- **For the MSI: Windows and [WiX Toolset 3](https://github.com/wixtoolset/wix3/releases)** (3.14). Name its folder
  (with `heat.exe`, `candle.exe`, `light.exe`) in the git-ignored `build-wix.txt` as `wix=<folder>`, or put WiX on
  the `PATH`. Without WiX the MSI is skipped with a note.

```bash
python build.py windows         # on Linux/macOS: python3 build.py windows (no MSI there)
git checkout -- dist/           # build.py rewrites the line endings under dist/
```

Output under `build/`:

- `DocFetcher-<version>-Windows-64bit.msi` - the installer (Windows only)
- `DocFetcher-<version>-Windows-64bit-Portable.zip` - the portable package
- `DocFetcher-<version>-Windows-64bit-NonPortable/` - the installed version, the content of the MSI

The same build runs automatically on every push to `master` (GitHub Actions; Gitea Actions with a Windows
runner): [dev/ci/build-windows.ps1](dev/ci/build-windows.ps1) sets up the runtime, WiX and the configuration files, and
the MSI and the portable zip are attached to the run.

`build.py` empties the whole `build/` folder first, so never run a DocFetcher you use from there. Code signing is
optional (`code-signing-windows.txt` with a certificate thumbprint, Windows SDK). On Linux/macOS,
`python3 build.py` without `windows` builds the packages of that system (on Linux also the macOS packages).

## License

Like the original DocFetcher, this fork is licensed under the [Eclipse Public License 1.0](dist/epl-v10.html).
The bundled libraries are under their own licenses.
