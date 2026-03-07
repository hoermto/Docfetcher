如何提高資料夾監控上限 (Linux) 
===========================================

在 Linux 上，處理程序預設最多只能監控 8192 個資料夾。如果您為非常深的資料夾階層建立索引，就可能會達到這個上限。如果發生這種情況，DocFetcher 可能會顯示 "No space left on device" 錯誤訊息。您可以透過提高監控上限來解決這個問題。例如，此指令會暫時將監控上限提高到 32000：

```
sudo echo 32000 > /proc/sys/fs/inotify/max_user_watches

```

若要永久變更監控上限，請（以 root 身分）開啟 `/etc/sysctl.conf` 檔案，並加入下列這一行：

```
fs.inotify.max_user_watches=32000

```