# DocFetcher

**Desktop full-text search and document filing for your own files - PDFs, scans, Office documents and emails.**

![Version](https://img.shields.io/badge/version-2.9-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![UI](https://img.shields.io/badge/UI-SWT-lightgrey)
![License](https://img.shields.io/badge/license-EPL--1.0-green)

DocFetcher is an open source desktop search application: it indexes the folders you choose and then finds
documents by their content in a fraction of a second - like Google, but for the files on your computer.
This version extends the original [DocFetcher](https://docfetcher.sourceforge.io/) with a document inbox
workflow: scanned documents are recognized (OCR), named, split, merged and filed into folders.

[[_TOC_]]

## Main use cases

| Use case | What DocFetcher does |
|----------|----------------------|
| **Find a document by its content** | Full-text search over all indexed folders, Google-style syntax (`invoice 2025 -gas`, `"exact phrase"`, `OR`), tolerant to umlauts and OCR errors (`Muller` finds `Müller`); words also match file and folder names (`Rosenweg rent` finds the contract in `Tenants/Rosenweg 12`); highlighted matches in the preview. |
| **Process the scanner inbox** | Inbox tabs list new documents; DocFetcher suggests a file name (`2025-03-12_Stadtwerke-Invoice.pdf`) and a target folder from similar documents; rename and move with one click or by drag and drop. |
| **Split and merge scans** | Split a scanned stack into single documents at separator sheets or where the text changes; merge PDF files; blank pages are left out. |
| **Search scanned paper** | Built-in Tesseract OCR (German and English) makes image PDFs and pictures searchable; the recognized text is stored once in the file. |
| **Sort folders** | One, two or three panes with synced folder browsers next to the document view, for filing documents into a folder structure. |
| **Ask an AI assistant** | A built-in MCP server (off by default, localhost only, read-only unless you allow new files in the inbox) lets assistants like Claude search the indexes and read documents: *"Which letters about tax arrived last?"* - *"What rent does my tenant in Rosenweg pay?"* |
| **Print and send** | Print or email one or several selected documents directly from the result list. |

Everything is undoable (`Ctrl+Z`), removed files go to the recycle bin, and documents containing text are never modified.

## Features

- Fast search via [Apache Lucene](https://lucene.apache.org/) indexes, updated automatically while DocFetcher runs
  (and by the optional DocFetcher daemon while it doesn't).
- Many formats: PDF, Microsoft Office and OpenOffice/LibreOffice documents, RTF, HTML, plain text, EPUB, CHM,
  AbiWord, SVG, images (with OCR), MP3/FLAC metadata, Outlook PST emails, archives (ZIP, 7z, RAR, ...).
- Google-style query syntax, plus wildcards, fuzzy, proximity and field searches (`title:report`).
- Inbox folders, name and folder suggestions, rename, move, copy (`Ctrl` + drag), delete to recycle bin.
- PDF split and merge, OCR with Tesseract, print, email.
- MCP server (Model Context Protocol revisions 2026-07-28 and 2025-03-26 to 2025-11-25, Streamable HTTP on `http://localhost:28835/mcp`) with the read-only tools
  `search`, `read_document`, `get_original_document` (a base64 copy of the file, e.g. as a template) and `list_folder`,
  plus `add_to_inbox` when allowed in the preferences (new files only, no programs, at most 20 MB); e.g. `claude mcp add --transport http docfetcher http://localhost:28835/mcp`
  (the preferences show how to connect Claude; for Claude Desktop they create a one-click extension, `docfetcher.mcpb`). Large result sets come newest first with an overview (time range,
  counts per year/month, top folders, characteristic words of the texts and names with their share of documents)
  that helps the assistant to narrow the search by adding or excluding words.
- Portable version (settings and indexes next to the program) and installed version.
- User interface in several languages; manual in English and German for the features of this version.

## Getting started (users)

1. Download or build the package (see below) and start `DocFetcher.exe` (Windows) or the launcher of your platform.
2. `File > Indexes... > Create Index... > Folder`, choose a folder and click `Run`.
3. Type words into the search field and press `Enter`; the `?` button next to it explains the search syntax.

The quick start guide is shown in the preview pane on the first start and via the help button.

## Documentation

| Topic | Where |
|-------|-------|
| Quick start guide (shown in the app) | [doc/src/en/DocFetcher_Manual_files/Quick_Start.markdown](doc/src/en/DocFetcher_Manual_files/Quick_Start.markdown) ([German](doc/src/de/DocFetcher_Manual_files/Quick_Start.markdown)) |
| Full user manual | [doc/src/en/DocFetcher_Manual.markdown](doc/src/en/DocFetcher_Manual.markdown) ([German](doc/src/de/DocFetcher_Manual.markdown)) |
| Search syntax | [doc/src/en/DocFetcher_Manual_files/Query_Syntax.markdown](doc/src/en/DocFetcher_Manual_files/Query_Syntax.markdown) |
| Preferences, indexing options, portable repositories | [doc/src/en/DocFetcher_Manual_files/](doc/src/en/DocFetcher_Manual_files/) |
| Changelog | [website/content/extra/changelog.txt](website/content/extra/changelog.txt) |
| Installing and starting the packages | [dist/Readme.txt](dist/Readme.txt) |
| Developer setup (IntelliJ IDEA, SWT, run configuration) | [readme.txt](readme.txt) |
| Building, unit tests, GUI tests, packaging | [tasks/skill.md](tasks/skill.md) |
| Build and compile notes for the Mill build | [CLAUDE.md](CLAUDE.md), [build.mill](build.mill) |
| Tasks and completed work | [tasks/tasks.md](tasks/tasks.md), [tasks/tasks_done.md](tasks/tasks_done.md) |

## Building from source

Requirements: JDK 21, Python 3 (for the package and the manual, with the `markdown` package).

```bash
./mill compile                  # Linux/macOS; on Windows: mill.bat compile
python3 build.py windows        # or: linux, mac - creates the packages under build/
```

The Mill output is long; redirect it to a file and search for `[error]` / `[warn]`.
Details, including the Java runtime for the Windows package (`build-jre.txt`), are in [tasks/skill.md](tasks/skill.md).

## Tests

JUnit 4 tests live next to the sources (`src/**/*Test.java`):

```bash
dev/guitest/run-tests.sh <java> net.sourceforge.docfetcher.model.search.GoogleStyleSearchTest
```

GUI tests drive the running development build with `dev/guitest/win.py` (Windows) or `dev/guitest/xin.py`
(Linux, Xephyr); see [tasks/skill.md](tasks/skill.md).

## Project layout

| Path | Content |
|------|---------|
| `src/net/sourceforge/docfetcher/gui` | SWT user interface (main window, toolbar, panes, dialogs) |
| `src/net/sourceforge/docfetcher/model` | Indexing, parsing, search (Lucene), OCR, PDF operations |
| `src/net/sourceforge/docfetcher/mcp` | MCP server for AI assistants (HTTP, JSON-RPC, tools, Claude Desktop extension) |
| `src/net/sourceforge/docfetcher/enums` | Messages (`Msg`), settings (`SettingsConf`), images (`Img`) |
| `dist/` | Files shipped with the package: language files, images, launchers |
| `doc/` | Manual sources (Markdown) and the manual build |
| `dev/` | Icons, test files, GUI test drivers, installer resources |
| `src-daemon/` | The DocFetcher daemon (index updates while the program is closed) |

## License

DocFetcher is licensed under the [Eclipse Public License 1.0](dist/epl-v10.html). The bundled libraries are
under their own licenses (see `build/licenses.zip` after a build).
