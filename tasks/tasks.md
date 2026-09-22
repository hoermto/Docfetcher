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
[] if "ocr enabled", will it write the OCRd text into the original PDF, or only to index? In case, add option to add OCRd text into files (pdf, png, jpg).
[] when tiff convert is enabled, make sure the PDF uses compression etc, so the PDF is not really larger than the tiff. found it ~1.5 times larger than PDF

## features

[] add tab "Folders", which has top level the configured index folders, and a click jumps into this folder, listing all sub folders on top and then files (norton commander style). A file selected shows in right panel, the current path in a status line. jump forward into any sub folder, also possible to go up in parent folder. Per file offer same "rename, move" options like in scan box
[] as user, I want to move a doc from a folder (the preconfigured or from folders view) into another folder. somethink like on left side I see my search/folder and the doc, and on the right side I have the target folder I can move by drag and drop. Suggest a user friendly way


## collection, wait until confirmd
[] v2.4 UI style follow W11 on Windows (keep open)