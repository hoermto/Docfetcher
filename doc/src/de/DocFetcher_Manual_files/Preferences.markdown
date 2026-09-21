Programm-Einstellungen
======================
Der Einstellungen-Dialog hat zwei Reiter: `Allgemein` und `Indizes`.

Reiter „Allgemein“
------------------
Einstellung  |  Anmerkungen
-------------|-------------
Benutzerhandbuch bei Programmstart anzeigen  |  Stellt ein, ob das Benutzerhandbuch nach Programmstart angezeigt werden soll.
In Suchanfragen OR-Operator als Standard verwenden (anstelle von AND)  |  Stellt ein, ob DocFetcher zwei aufeinanderfolgende Suchbegriffe als mittels OR- oder als mittels AND-Operator verknüpft auffassen soll. Siehe die [Suchanfrage-Syntax](Query_Syntax.html) für weitere Informationen.
Automatisch zur ersten Fundstelle im Vorschau-Fenster scrollen  |  Stellt ein, ob DocFetcher automatisch zur ersten Fundstelle im Vorschau-Fenster scrollen soll, nachdem Sie auf eine Datei in der Ergebnis-Tabelle geklickt haben und die Datei in das Vorschau-Fenster geladen wurde.
Programm nach Öffnen von Dateien in den System Tray minimieren  |  Stellt ein, ob DocFetcher automatisch in den System Tray minimiert werden soll, nachdem Sie eine Datei in der Ergebnis-Tabelle oder einen Ordner im `Suchbereich` öffnen.
DocFetcher zusammen mit Windows starten  |  (Nur Windows) Stellt ein, ob DocFetcher automatisch gestartet wird, wenn Sie sich bei Windows anmelden. Dazu wird ein Autostart-Eintrag für den aktuellen Benutzer angelegt; das Deaktivieren entfernt ihn wieder.
Beim Schließen des Programm-Fensters  |  Legt fest, was beim Klick auf den Schließen-Button des Haupt-Programmfensters geschieht: `Beenden` beendet das Programm, `In die Taskleiste minimieren` lässt es als minimiertes Fenster weiterlaufen, und `Im System Tray verbergen` versteckt es im System Tray. In den beiden letzten Fällen beenden Sie das Programm über den Button `DocFetcher beenden` in der Werkzeugleiste (oder `Strg+Q`, oder das System-Tray-Menü).
Suchanfragen beim Beenden nicht speichern  |  Stellt ein, ob DocFetcher eingegebene Suchanfragen bei Programmende speichern soll oder nicht.
Indizes beim Programmstart aktualisieren  |  Stellt ein, ob alle Indizes, deren Ordner auf Änderungen überwacht werden (siehe die Indizierungs-Option `Automatische Detektion von Datei-Änderungen`), beim Programmstart im Hintergrund aktualisiert werden. Dadurch werden Dateien erfasst, die hinzugefügt, geändert, umbenannt oder gelöscht wurden, während DocFetcher nicht lief – auch ohne den DocFetcher-Daemon.
Position der Vorschau  |  Legt fest, ob das Vorschau-Fenster rechts neben oder unterhalb der Ergebnis-Tabelle angezeigt wird.
Angezeigte Pfadbestandteile für vorgeschlagene Ordner  |  Wie viele der letzten Bestandteile eines Ordnerpfads in der Spalte `Ordner` des Dialogs `In Ordner verschieben` angezeigt werden, z.&nbsp;B. zeigt 2 `Projekte/Berichte`; der vordere Teil des Pfads steht in der Spalte `Speicherort`.
Wort-Segmentierung  |  Der Wort-Segmentierungs-Algorithmus, der während der Indizierung verwendet werden soll. Der Standard-Algorithmus ist geeignet für westliche Sprachen wie Englisch und Deutsch. Der Quellcode- und der Chinesisch-Algorithmus sind ihrem Namen entsprechend für Quellcode bzw. Chinesische Texte gedacht. Eine Änderung des Wort-Segmentierungs-Algorithmus erfordert ein Neuerstellen aller existierenden Indizes.
Hervorhebungsfarbe  |  Die Farbe, die im Vorschau-Fenster zur Hervorhebung von Fundstellen verwendet wird.
Schriftart für Vorschau (normal)  |  Die Schriftart, die im Vorschau-Fenster für alle Dateien außer Textdateien verwendet werden soll.
Schriftart für Vorschau (fixe Breite)  |  Die Schriftart, die im Vorschau-Fenster für Textdateien verwendet werden soll. Sie müssen hier nicht unbedingt eine Schriftart mit fixer Breite einstellen, wobei dies jedoch in der Regel Sinn macht.
Schnellzugriffstaste  |  Mit dieser Tastenkombination können Sie das DocFetcher-Programmfenster nach oben befördern, nachdem es den Fokus an andere Programme verloren hat.

Reiter „Indizes“
----------------
Der Reiter `Indizes` listet alle vorhandenen Indizes auf und bietet Buttons zu deren Verwaltung:

Button  |  Anmerkungen
--------|-------------
Index erstellen...  |  Erstellt einen neuen Index aus einem Ordner, einem Archiv, einer Outlook-PST-Datei oder einem Ordnerpfad in der Zwischenablage.
Aktualisieren  |  Aktualisiert die selektierten Indizes, damit sie Änderungen an den indizierten Dokumenten widerspiegeln.
Neu erstellen  |  Erstellt die selektierten Indizes von Grund auf neu. Dies ist nötig, nachdem die Indizierungs-Optionen eines Index oder der Wort-Segmentierungs-Algorithmus geändert wurden.
Umbenennen...  |  Ändert den angezeigten Namen des selektierten Index.
Entfernen  |  Entfernt die selektierten Indizes.
Verwaiste entfernen  |  Entfernt alle Indizes, deren Dokument-Ordner nicht mehr existieren.
Index-Ordner  |  Der Ordner, in dem DocFetcher seine Index-Dateien ablegt. Leer lassen, um den Standardordner zu verwenden. Eine Änderung wird erst nach einem Neustart von DocFetcher wirksam; Sie werden gefragt, ob die vorhandenen Indizes beim nächsten Start in den neuen Ordner verschoben werden sollen.
