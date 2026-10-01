Query Syntax
============
DocFetcher's search field works like Google: type a few words, and DocFetcher finds the documents that contain all of them. This page starts with the Google-style basics and then goes on to the advanced syntax of Apache Lucene, DocFetcher's underlying search engine.

Quick Reference
---------------
Query                   | Resulting documents contain...
------------------------|---------------------------------------------
`dog cat`               | both `dog` and `cat`, anywhere in the document
`dog OR cat`            | `dog`, or `cat`, or both
`dog -cat`              | `dog`, but not `cat`
`+dog cat`              | `dog` and `cat` (`+` marks a word that must occur)
`"black dog"`           | exactly the phrase `black dog`
`"Müller"`              | exactly `Müller`, not `Muller` (quotes switch off the tolerant search)
`Muller`                | `Muller`, `Müller`, `MULLER`, ... (tolerant search, see below)
`luc*` / `luc?`         | words starting with `luc` / `luc` plus one character
`title:dog`             | `dog` in the title (fields: see below)

Tips:

* Upper and lower case don't matter. Only the operators `OR`, `AND` and `NOT` must be written in upper case.
* Characters like `+`, `:`, `/` or parentheses inside words are no problem: `C++`, `Nr: 4711` and `01/2025` are searched as words.
* Tolerant search (on by default, see the [preferences](Preferences.html)): words are also found without umlauts and accents (`Muller` finds `Müller`, `strasse` finds `Straße`) and despite typical recognition errors in scanned documents (`Bonn` finds `B0nn`). Put a word in quotes to find exactly that spelling.
* If there are too many results, add a word; if there are none, remove one or use `OR`.
* By default the words also match file and folder names: `invoice` finds `myinvoice_2024.pdf`, and `Rosenweg rent` finds a contract in the folder `Tenants/Rosenweg 12` even if its text doesn't mention `Rosenweg` (search settings: `Also match file and folder names`).


Boolean Operators
-----------------
DocFetcher supports the boolean operators `OR`, `AND` and `NOT`. If words are concatenated *without* boolean operators, DocFetcher treats them as if they were concatenated with `AND`, like Google. If you prefer `OR` as the default, you can change this in the [preferences](Preferences.html).

Instead of `OR`, `AND` and `NOT`, you can also use `||`, `&&` and `'-'` (minus symbol), respectively. You can use *parentheses* to group certain expressions. Here are some examples:

Query                    | Resulting documents contain...
-------------------------|---------------------------------------------
`dog OR cat`             | either `dog`, or `cat`, or both
`dog AND cat`            | both `dog` and `cat`
`dog cat`                | (by default equivalent to the query `dog AND cat`)
`dog NOT cat`            | `dog`, but not `cat`
`-dog cat`               | `cat`, but not `dog`
`(dog OR cat) AND mouse` | `mouse`, and either `dog` or `cat`, or both


Searching is case-insensitive
-----------------------------
DocFetcher does not distinguish between lowercase and uppercase characters, so it doesn't matter if the words you enter are completely lowercase, or completely uppercase, or a mix of both. The only exceptions are the keywords `OR`, `AND`, `NOT` and `TO`, which must always be entered in uppercase. (For the `TO` keyword, see the 'range searches' section below.)


Phrase Searches and Required Terms
----------------------------------
To search for a phrase (i.e. a sequence of words), put the phrase in double quotes; words in quotes are searched with exactly that spelling. To indicate that the documents to search for must contain a particular word, put a `'+'` in front of the word - with the default `AND` all words are required anyway, so this matters if you chose `OR` as the default. Of course you can combine these constructs with boolean operators and parentheses. Again, some examples:

Query                 | Resulting documents contain...
----------------------|-------------------------------------
`"dog cat mouse"`     | the words `dog`, `cat` and `mouse`, in that particular order
`+dog cat`            | definitely `dog`, and also `cat` (with `OR` as the default: maybe also `cat`)
`"dog cat" AND mouse` | the phrase `dog cat`, and the word `mouse`
`+dog +cat`           | (equivalent to the query `dog AND cat`)


Wildcards
---------
Question marks (`'?'`) and asterisks (`'*'`) can be used to indicate that some characters are unknown. The question mark stands for exactly one unknown character, while the asterisk stands for zero or more unknown characters. Examples:

Query        | Resulting documents contain...
-------------|-------------------------------------
`luc?`       | `lucy`, `luca`, ...
`luc*`       | `luc`, `lucy`, `luck`, `lucene`, ...
`*ene*`      | `lucene`, `energy`, `generator`, ...

Note: If wildcards are used as the first character of a word, the search tends to be slower on average. This is due to how the index is structured: It's as if you tried to look up someone's phone number, and you only know that person's first name. So, in the example above, searching for `*ene*` will probably be slower than the other searches because `*ene*` starts with a wildcard.


Fuzzy Searches
--------------
Fuzzy searches allow you to search for words *similar* to a given word. For example, if you search for `roam~`, DocFetcher will find documents containing words like `foam` and `roams`.

Additionally, you can append a similarity threshold between 0 and 1, like so: `roam~0.8`. The higher the threshold, the higher the similarity of the returned matches. Leaving out the threshold is the same as using the default value of 0.5.


Proximity Searches
------------------
Proximity searches allow you to find words that are within a specific distance away from each other. To do a proximity search, put a tilde (`'~'`) at the end of a phrase, followed by a distance value. &mdash; Note that this is syntactically similar to fuzzy searches. For example, to search for documents containing `wikipedia` and `lucene` within 10 words of each other, type in: `"wikipedia lucene"~10`


Boosting Terms
--------------
You can influence the relevance sorting of the results by assigning custom weights to words. Example: If you enter `dog^4 cat` instead of just `dog cat`, documents containing `dog` will receive a higher score and therefore move closer to the top of the results.

Although the boost factor must be positive, it can be less than 1 (e.g. 0.2). If no boost factor is specified, the default value 1 is used.


Field Searches
--------------
By default, DocFetcher will search in all the textual data it is capable of extracting, i.e. the contents, the filenames and the metadata of the documents. However, you can also restrict your searches to the filename and/or certain metadata fields. For example, to search for documents whose titles contain `wikipedia`, enter: `title:wikipedia`.

Field search can be combined with other search constructs, e.g.:

* Phrase search using quotes, e.g. `title:"dog cat"`, or using parentheses, e.g. `title:(dog cat)`. If you omit the quotes and parentheses, only `dog` will be matched against the title, not `cat`.
* Wildcards: `title:dog*` will, for instance, match occurrences of `doggy` in the title.

Which fields are available generally depends on the document format, but you can use this as a rule of thumb:

<!-- Do not translate the following field names (filename, title, etc.) -->
* *Files*: filename, title, author
* *Emails*: subject, sender, recipients


Range Searches
--------------
DocFetcher allows searching for words that are lexicographically *between* two other words. For example, the word `beta` lies between `alpha` and `gamma`. So, if you want to list all documents that contain words between `alpha` and `gamma`, type in: `[alpha TO gamma]`.

When using the square brackets, the range query is *inclusive*, i.e. `alpha` and `gamma` are included in the results. To do an *exclusive* range search, use curly brackets instead: `{alpha TO gamma}`

You may combine range searches and field searches as follows: `title:{alpha TO gamma}`. This will restrict the range search to the title field.
