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

## features/stories
[] as user, i want to send every document or multiple seelcted documents as email
[] as user I scan a staple of document as one, and want to merge and split the documents easily
  [] S3a merge the selected PDFs into one (order adjustable); after a successful merge offer to move the originals to the recycle bin (ask, with "remember"); undo with Ctrl+Z
  [] S3b split a PDF: each page, or at page numbers x; parts are numbered (name_1.pdf, ...); same handling of the original as after a merge
  [] S3c split automatically at blank separator pages
[] as user, i want to have a symbol bar for print, mail, rename, move to folder, split and merge



## collection, wait until confirmd
[] v2.4 UI style follow W11 on Windows (keep open)
