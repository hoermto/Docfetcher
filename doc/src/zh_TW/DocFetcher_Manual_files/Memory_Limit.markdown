如何提高記憶體上限 
=============================
DocFetcher 的預設記憶體上限為 256 MB，此限制會在啟動時設定。 您可以透過調整各平台專用的啟動器來提高此上限： 

Windows
-------
Windows 版的 DocFetcher 內建了現成的替代啟動器，可設定不同的堆積大小。 請依照以下步驟使用這些啟動器： 

* 開啟 DocFetcher 資料夾。 如果您使用的是免安裝版的 DocFetcher，這就是您下載並解壓縮的資料夾。 如果您使用的是安裝版，DocFetcher 資料夾將位於 `C:\Program Files` 或 `C:\Program Files (x86)`，或類似的路徑。 
* 替代啟動器位於 `DocFetcher\misc` 資料夾中。它們的命名方式為 `DocFetcher-XXX.exe`，其中的 `XXX` 代表該啟動器所設定的堆積大小。 例如，啟動器 `DocFetcher-512.exe` 會將堆積大小設定為 512 MB。 
* 在使用這些啟動器之前，**您必須先將其移動或複製到 DocFetcher 資料夾中**。 您不需要刪除預設啟動器或重新命名替代啟動器。

另一種更改記憶體上限的方法是將 `misc\DocFetcher.bat` 檔案複製到 DocFetcher 資料夾，並修改檔案最後一行的 `-Xmx256m` 參數，例如改為 `-Xmx512m`。 

Linux 
-----
使用文字編輯器開啟啟動器指令碼 `DocFetcher/DocFetcher.sh`，並根據需求修改最後一行的 `-Xmx256m` 參數，例如改為 `-Xmx512m`。 

Mac OS X 
-------------
在 Mac OS X 上，記憶體上限是透過名為 `memory-limit.txt` 的檔案來設定。 此檔案的位置取決於您使用的版本： 

* **免安裝版**：在 DocFetcher 資料夾中，使用文字編輯器開啟 `conf/memory-limit.txt` 檔案。 
* **安裝版**：在家目錄中，使用文字編輯器開啟 `.docfetcher/conf/memory-limit.txt` 檔案。 請注意，以點開頭的資料夾在 Finder 中預設為隱藏。 若要顯示隱藏的檔案，請在 Finder 中按下 `Cmd+Shift+.` (Command+Shift+句號)，或使用終端機瀏覽至該檔案。

如果該檔案尚未存在，請啟動 DocFetcher 一次，系統便會自動建立。 開啟檔案後： 

* 您會在幾行註解下方看到類似 `4g` 的數值。 將其變更為所需的記憶體上限，例如將其改為 `8g` 以使用 8 GB 的 RAM。 
* 儲存並關閉該檔案，如果程式仍在執行中，請將其關閉，然後重新啟動程式。
