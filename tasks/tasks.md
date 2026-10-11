# tasks

# general rules

- prio: 1- Bugs, 2- changes, 3- features. 
- analyse all changes befgore using INVEST method
- ask questions before action
- always look for code reuse (avoid code redundancy / replication, find generalizations) and follow KISS principle
- After all questions are answered, continue to process without any further user
- always apply unit test and application test for the changes function
- always remove all started processes after task completition for clean up 
- no git push are allowed
- add git commit after each item completed with brief single line commit message
- after task complete, move them here in task_done.md file to keep this clean
- if relevant changes are here, remember them in a memory file. for special actions like test execution, maintain and use skill.md
- always use localization for any UI visible strings and maintain language filesd

## bugs


## changes


[] add support for encrypted containers like cryptomator (follow you 1a) 1b) suggestions)


## new version
situation: documents are on a network drive; file changes are most likely not notified to DocFetcher.
[] whenever a file rename or move happens within DocFetcher, make sure the affected indexes are updated in any case
[] for network drives, consider a nightly refresh (option setting)
[] determine per index whether the source path/drive supports fs based notifications or needs manual syncs
  Answers (v3.1): only the affected folders are read again after DocFetcher's own operations; automatic
  detection (network drive / UNC = sync needed, watching stays on too), shown per index; daily sync at a fixed
  time (default 03:00, on, caught up at the next start), merged with 'update all indexes once a day' into one
  option with 'only indexes that need it' / 'all indexes'.


## future features/stories



[] any ideas about document encryption?
  Analysis (v2.7, answer: encrypted folder + protect the index, write-up only for now):
  - What leaks today: the documents themselves, and in the app data folder the index (full text of every
    document and all paths, Lucene files in plain text), the settings (inbox folders, move targets, search
    history), the undo backups (copies of PDFs removed after merge/split, until exit) and temporary files
    (preview copies of archive entries, separator sheet). The OCR text layer is written into the scanned PDFs.
  - a) Encrypted folder via a container - recommended. Put the documents in a VeraCrypt container or a
    BitLocker drive/VHDX (Windows Pro); EFS (NTFS folder encryption, tied to the user account) is the
    zero-effort variant, but the key must be backed up. DocFetcher indexes the folder while it is unlocked.
    Needed in DocFetcher (effort S): 1) an index whose folder is missing (container locked) must not be
    emptied by an update or the daily update - to check: FileIndex.update treats a missing root like a
    single file; it should skip with a clear message 'folder not available'; 2) keep the index and the undo
    backups in the container too: `indexes=` in misc/paths.txt already works, add the same for undo/temp.
    Risk: low; no own cryptography.
  - b) Protect the index - via a) as well: put the indexes into the container (`indexes=` in paths.txt), or
    run the portable DocFetcher completely from inside the container (settings via `settings=`, search
    history off with the existing option). Own index encryption (a Lucene Directory that encrypts every
    read and write, key from a password at start) would be effort L: slower search, own key handling, a
    lost password means a lost index, and the plain text still appears in the preview and temp files -
    not recommended.
  - c) DocFetcher's own vault (files encrypted with AES-GCM in an own format): effort XL, everything
    that opens a file (preview, print, mail, external programs) would need decrypted temp copies, which
    defeats the purpose; not recommended.
  - Suggested next step when wanted: story 'DocFetcher works with an encrypted container' = a)1 + a)2 +
    a manual section; about 1-2 days including tests.

