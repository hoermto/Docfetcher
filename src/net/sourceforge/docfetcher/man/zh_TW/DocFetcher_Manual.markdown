簡介 
============

DocFetcher 是一款開放原始碼的桌面搜尋應用程式：它能讓您搜尋電腦上的檔案內容。 - 您可以把它當作是本機檔案的 Google。

**建立索引搜尋**：由於直接在大量文件中搜尋會非常緩慢，DocFetcher 需要您為想要搜尋的資料夾建立*索引*。這些索引讓 DocFetcher 能透過關鍵字快速尋找檔案，就像您使用書本後面的索引一樣。建立索引可能需要一些時間，具體取決於已建立索引的檔案數量和大小。然而，每個資料夾只需要執行一次此操作；之後您就可以在已建立索引的資料夾中進行無限次搜尋。

**建立索引**：若要建立索引，請在左側的 `Search Scope`（搜尋範圍）區域按一下右鍵，然後選擇 `Create Index From > Folder`（從資料夾建立索引）。現在選擇要建立索引的資料夾。對於初學者，建議選擇一個檔案不多的較小資料夾，例如 50 個檔案左右？選擇資料夾後，會彈出一個設定視窗。預設的設定應該就足夠了，因此只需點擊 `Run`（執行）按鈕，等待 DocFetcher 完成文件的索引建立。（建立索引的另一種方式是將剪貼簿中的目錄貼到 `Search Scope` 區域。不過，這僅適用於一般目錄，不適用於 PST 檔案。）

**搜尋**：在結果窗格（帶有欄位標題的表格）上方的文字欄位中輸入一個或多個要搜尋的單字，然後按下 `Enter` 鍵。搜尋結果將顯示在結果窗格中，並依分數遞減排序。

*如果您是在 DocFetcher 內部閱讀本手冊，按照下一段的指示操作將會使手冊消失。若要還原，請點擊右上角的 `'?'` 按鈕。您也可以透過點擊此窗格正上方的 `Open in external browser`（在外部瀏覽器中開啟）按鈕，在您的預設網頁瀏覽器中開啟本手冊。

**結果窗格與預覽窗格**：在結果窗格的下方（或右方，取決於目前的 GUI 版面配置），您可以找到預覽窗格。如果您在結果窗格選擇一個檔案，預覽窗格將會顯示該檔案內容的純文字預覽。值得注意的功能：

* ***螢光筆標示***：預設情況下，您輸入的搜尋字詞會被標示出來，而且您可以使用上下按鈕跳至上一個或下一個出現的位置。
* ***內建網頁瀏覽器***：如果是 HTML 檔案，您可以在純文字檢視與簡單的內建網頁瀏覽器之間進行切換。（注意：後者在某些 Linux 版本上無法使用。）

您可能會覺得實用的一個快速鍵：按下 `Ctrl+F` 或 `Alt+F` 可將焦點移回搜尋欄位。若要在外部程式中開啟檔案，請在結果窗格中連按兩下該檔案。

**排序**：您可以透過點擊結果窗格的任何欄位標題來變更結果的排序。例如，若要依檔名對結果進行排序，請點擊 `Filename` 標題。連按兩下同一個標題，將會依相應的準則以反向順序排序。您也可以透過拖放來變更欄位的順序：例如，如果您希望 `Filename` 成為第一個欄位，只需將 `Filename` 欄位標題向左拖曳即可。

**篩選**：在 GUI 的左側，您可以看到用於篩選結果的各種控制項：(1) 您可以在 `Minimum / Maximum Filesize` 控制項中指定檔案大小的下限和/或上限。(2) `Document Types` 清單能讓您依類型篩選結果。(3) 透過取消勾選 `Search Scope` 區域中的項目，您可以依位置篩選結果。

**更新索引**：如果已建立索引的資料夾中的檔案被新增、移除或修改，則必須更新相應的索引，否則您的搜尋結果可能會過時。幸運的是，更新索引實際上總是比從頭建立索引快得多，因為只需處理變更的部分。此外，DocFetcher 可以透過兩種方式自動更新其索引：

1. ***DocFetcher 程式本身***：如果 DocFetcher 正在執行，且已對修改過的資料夾啟用*資料夾監控*，DocFetcher 會偵測到變更並立即更新其索引。
2. ***DocFetcher 守護程式(Daemon)***：如果 DocFetcher 沒有在執行，這些變更會由一個在背景執行的小型守護程式記錄下來；接著受影響的索引將會在下次啟動 DocFetcher 時更新。（注意：遺憾的是，該守護程式目前無法在 Mac OS X 上使用。）

一些注意事項：如果您使用的是免安裝版的 DocFetcher，並希望執行守護程式，您必須透過將守護程式執行檔新增至作業系統的啟動程式清單中來手動安裝它。此外，DocFetcher 和守護程式都無法偵測網路共用上的變更。<!-- this line should end with two spaces -->  
因此，在無法自動更新索引的情況下，您必須自行手動更新：在 `Search Scope` 區域中，選擇一個或多個要更新的索引，然後在 `Search Scope` 右鍵選單中點擊 `Update Index`，或按下 `F5` 鍵。

* * * 

<a name="Advanced_Usage"></a> <!-- Do not translate this line, just copy it verbatim. -->

進階用法
==============

**查詢語法**：透過 DocFetcher，您能做的遠不止簡單的單字查詢。例如，您可以使用萬用字元來搜尋具有相同開頭的單字，像這樣：`wiki*`。若要搜尋特定的片語（即以特定順序排列的單字序列），請用引號將該片語括起來：`"the quick brown fox"`。但這僅僅是個開始。有關所有支援語法的概述，請參閱[查詢語法章節](DocFetcher_Manual_files/Query_Syntax.html)。

**偏好設定**：在使用者介面的右上角，您會找到一個描繪兩個齒輪的圖示。點擊它以開啟「偏好設定」對話方塊。可透過「偏好設定」對話方塊左下角的「進階設定」連結存取更多進階用法的額外設定。

**可攜式文件庫**：免安裝版的 DocFetcher 允許您建立一個包含 DocFetcher、您的文件和相關索引的組合包，然後自由地移動這個組合包 —甚至從一個作業系統移動到另一個作業系統，例如從 Windows 移動到 Linux，反之亦然。使用免安裝版時，需要記住的一件重要事情是，索引必須使用*相對路徑*建立。點擊[此處](DocFetcher_Manual_files/Portable_Repositories.html) 以獲取有關可攜式文件庫的更多資訊。順帶一提，如果您一直使用的是 DocFetcher 1.0.3 及更早版本，請注意您不再需要將文件放入 DocFetcher 資料夾中。

**索引設定選項**：有關索引設定視窗上所有選項的詳細討論，請點擊[此處](DocFetcher_Manual_files/Indexing_Options.html)。您也可以在設定視窗中點擊底部的 `Help` 按鈕，直接前往本手冊頁面。最有趣的設定選項也許是：

* ***自訂副檔名***：純文字檔案和 zip 壓縮檔的副檔名是完全可自訂的。這對於建立原始碼檔案的索引特別有用。
* ***檔案排除***：您可以根據正規表示式將特定檔案從索引建立中排除。
* ***MIME 類型偵測***：如果沒有 MIME 類型偵測，DocFetcher 將只會查看檔案的副檔名（例如 `'.doc'`）來決定其檔案類型。若啟用 MIME 類型偵測，DocFetcher 還會查看檔案的內容，看看是否能找到更好的類型資訊。這比只檢查副檔名還要慢，但對於副檔名錯誤的檔案很有用。
* ***HTML 配對***：預設情況下，DocFetcher 會將一個 HTML 檔案及其相關聯的資料夾（例如，檔案 `foo.html` 和資料夾 `foo_files`）視為單一文件。這項功能的主要目的是讓 HTML 資料夾內所有的「雜亂檔案」從搜尋結果中消失。

**正規表示式**：檔案排除與 MIME 類型偵測兩者都依賴於所謂的*正規表示式*。這些是使用者定義的模式，DocFetcher 會將其與檔名或檔案路徑進行比對。例如，若要排除所有以「journal」單字開頭的檔案，您可以使用此正規表示式：`journal.*`。請注意，這與 DocFetcher 的查詢語法略有不同，後者會省略點號：`journal*`。如果您想了解更多關於正規表示式的資訊，請閱讀這篇[簡短介紹](DocFetcher_Manual_files/Regular_Expressions.html)。

**發佈通知**：DocFetcher 不會（也不應該？）自動檢查更新。如果您*確實*希望在發佈新版本時收到通知，可以透過[幾種方式](DocFetcher_Manual_files/Release_Notification.html)來進行設定。

* * * 

<a name="Caveats"></a> <!-- Do not translate this line, just copy it verbatim. -->

注意事項與常見陷阱
==========================

**提高記憶體上限**：就像所有的 Java 程式一樣，DocFetcher 允許使用的記憶體也有固定的上限，稱為 *Java 堆積大小 (Java heap size)*。此記憶體上限必須在啟動時設定，而 DocFetcher 目前選擇的預設值為 256 MB。如果您嘗試為數量非常龐大的檔案建立索引，和/或其中有些已建立索引的檔案非常巨大（這在 PDF 檔案中並不罕見），那麼 DocFetcher 很有可能會達到該記憶體上限。如果發生這種情況，您可能會想要[提高記憶體上限](DocFetcher_Manual_files/Memory_Limit.html)。

**請勿對系統資料夾建立索引**：與其他桌面搜尋應用程式不同，DocFetcher 的設計並非用於為系統資料夾（例如 `C:` 或 `C:\Windows`）建立索引。不建議這樣做有以下原因：

1. ***系統變慢***：系統資料夾中的檔案通常會非常頻繁地被修改。如果開啟了資料夾監控，這將會導致 DocFetcher 不斷地更新其索引，進而拖慢您的電腦速度。
2. ***記憶體問題***：DocFetcher 需要在記憶體中保留您檔案的微小表示形式。正因為如此，再加上系統資料夾通常包含大量的檔案，如果您對系統資料夾建立索引，DocFetcher 將更有可能耗盡記憶體。
3. ***浪費資源，降低搜尋結果品質***：除了這些技術原因之外，對系統資料夾建立索引很可能會浪費建立索引的時間和磁碟空間，而且還會讓不需要的系統檔案污染您的搜尋結果。因此，為了在最短時間內獲得最佳結果，只需對您需要的內容建立索引即可。

**Unicode 支援**：DocFetcher 對所有文件格式都有完整的 Unicode 支援。就純文字檔案而言，DocFetcher 必須使用[某些啟發式方法](http://www-archive.mozilla.org/projects/intl/UniversalCharsetDetection.html)來猜測正確的編碼，因為純文字檔案不包含任何明確的編碼資訊。

**支援壓縮檔**：DocFetcher 目前支援以下壓縮檔格式：zip 及其衍生格式、7z、rar 以及整個 tar.* 家族。此外，也支援可執行的 zip 和 7z 壓縮檔，但不支援可執行的 rar 壓縮檔。DocFetcher 會將所有壓縮檔視為一般資料夾處理，並且也能處理任意深度的巢狀壓縮檔（例如一個 zip 壓縮檔包含一個 7z 壓縮檔，而該 7z 壓縮檔又包含一個 rar 壓縮檔...）。<!-- this line should end with two spaces -->  
話雖如此，必須注意的是，在穩定性和速度方面，對 zip 和 7z 壓縮檔的支援是最好的。另一方面，對 tar.gz、tar.bz2 及類似格式建立索引的效率通常較低。這是因為這些格式沒有壓縮檔內容的內部「摘要」，這迫使 DocFetcher 解開整個壓縮檔，而不僅僅是個別的壓縮檔項目。總結：如果您可以選擇，請將您的檔案壓縮為 zip 或 7z 壓縮檔，以達到與 DocFetcher 最大的相容性。

**DocFetcher 守護程式是無辜的**：如果您懷疑 DocFetcher 守護程式正在拖慢您的電腦或導致當機，您可能錯了。事實上，該守護程式是一個非常簡單的程式，記憶體佔用量和 CPU 使用率都很低，除了監控資料夾之外，它並沒有做太多其他事情。如果您仍不相信，只需重新命名守護程式的執行檔，使其不會自動啟動，或是嘗試使用預設停用該守護程式的免安裝版 DocFetcher 即可。



* * * 

<a name="Subpages"></a> <!-- Do not translate this line, just copy it verbatim. -->

手冊子頁面
===============
* [查詢語法](DocFetcher_Manual_files/Query_Syntax.html)
* [可攜式文件庫](DocFetcher_Manual_files/Portable_Repositories.html)
* [索引設定選項](DocFetcher_Manual_files/Indexing_Options.html)
* [正規表示式](DocFetcher_Manual_files/Regular_Expressions.html)
* [發佈通知](DocFetcher_Manual_files/Release_Notification.html)
* [如何提高記憶體上限](DocFetcher_Manual_files/Memory_Limit.html)
* [如何提高資料夾監控上限 (Linux)](DocFetcher_Manual_files/Watch_Limit.html)
* [偏好設定](DocFetcher_Manual_files/Preferences.html)

進階資訊
===============
如需更多資訊，請查看我們的 [wiki](http://docfetcher.sourceforge.net/wiki/doku.php)。如果您有任何問題，歡迎造訪我們的[論壇](http://sourceforge.net/projects/docfetcher/forums/forum/702424)。錯誤回報可以提交到我們的[錯誤追蹤系統](http://sourceforge.net/tracker/?group_id=197779&atid=962834)。