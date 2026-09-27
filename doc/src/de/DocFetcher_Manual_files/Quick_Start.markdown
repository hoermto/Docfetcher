Schnellstart
============
DocFetcher durchsucht den vollständigen Text Ihrer Dokumente – PDF-Dateien, Scans, Office-Dateien, E-Mails und mehr – und hilft beim Ablegen: umbenennen, verschieben, zusammenfügen, aufteilen, drucken und per E-Mail senden. Diese Seite zeigt die alltäglichen Schritte; das [vollständige Handbuch](../DocFetcher_Manual.html) erklärt alles im Detail.

1. Ordner indizieren
--------------------
DocFetcher sucht in *Indizes*. Wählen Sie `Datei > Indizes...`, dann `Index erstellen... > Ordner...`, wählen Sie einen Ordner und klicken Sie auf `Start`. Die Indizes bleiben automatisch aktuell, solange DocFetcher läuft. Gescannte Dokumente werden beim Indizieren erkannt (OCR), damit auch sie durchsucht werden können.

2. Suchen
---------
Geben Sie Wörter in das Suchfeld ein und drücken Sie `Enter` – es funktioniert wie Google:

Suchanfrage         | Findet Dokumente mit...
--------------------|---------------------------------
`Rechnung 2025`     | beiden Wörtern
`Rechnung -Gas`     | `Rechnung`, aber nicht `Gas`
`Gas OR Wasser`     | einem der Wörter
`"Stadtwerke Bonn"` | genau dieser Phrase
`Versich*`          | Wörtern, die mit `Versich` beginnen

Umlaute und typische OCR-Fehler spielen keine Rolle: `Muller` findet auch `Müller`, `Bonn` auch `B0nn`. Der Button `?` neben dem Suchfeld öffnet die [Suchhilfe](Query_Syntax.html). Ein Klick auf ein Ergebnis zeigt das Dokument rechts an, mit hervorgehobenen Wörtern; die Filter links neben den Ergebnissen grenzen sie nach Dateityp, Größe oder Ordner ein.

3. Postbox: neue Dokumente ablegen
----------------------------------
Fügen Sie den Ordner Ihres Scanners als `Postbox-Ordner` hinzu (`Datei > Einstellungen`, Reiter `Ordner`); er bekommt einen eigenen Reiter mit den neuesten Dokumenten oben. Für jedes Dokument:

* **Umbenennen**: Klick auf den Stift (oder `F2`) – DocFetcher schlägt einen Namen wie `2025-03-12_Stadtwerke-Rechnung.pdf` aus dem Datum und den Schlüsselwörtern des Textes vor.
* **Verschieben**: Klick auf das Ordner-Symbol – DocFetcher schlägt die Ordner vor, in denen ähnliche Dokumente liegen; oder ziehen Sie das Dokument auf einen Ordner (mit `Strg` wird kopiert).
* **Aufteilen / Zusammenfügen**: `PDF aufteilen` teilt einen gescannten Stapel in einzelne Dokumente (an Trennblättern oder wo der Text wechselt); `PDFs zusammenfügen` heftet mehrere PDF-Dateien zu einer zusammen.
* **Drucken / E-Mail**: `Drucken` (`Strg+P`) und `Per E-Mail senden` wirken auf alle ausgewählten Dokumente.

Alles lässt sich mit `Strg+Z` rückgängig machen; entfernte Dateien landen im Papierkorb.

4. Ordner durchblättern
-----------------------
Die Buttons mit den Spalten in der Symbolleiste wählen ein, zwei oder drei Fenster neben der Dokumentansicht (`F3` wechselt): nur die Reiter, die Reiter und einen Ordner-Browser, oder zwei Ordner-Browser nebeneinander zum Einsortieren von Dokumenten. Mit dem Synchronisieren-Button bewegen sich die Browser gemeinsam, und ein Klick auf ein Suchergebnis oder ein Postbox-Dokument zeigt es in seinem Ordner. `F5` springt zur Postbox (erneut: zur nächsten), `F6` ins Suchfeld, `F7` in den Ordner-Browser.

5. Gut zu wissen
----------------
* Die Symbolleiste wirkt auf die zuletzt angeklickte Liste; wenn Sie mit der Maus über einem Button verweilen, wird angezeigt, was er tut.
* `Ansicht > Symbolgröße` macht die Symbole der Symbolleiste größer oder kleiner.
* DocFetcher verändert nie Dokumente, die Text enthalten. Nur bei Scans ohne Text wird der erkannte Text einmalig in der Datei gespeichert (in den Einstellungen abschaltbar).
* KI-Assistenten wie Claude können Ihre Dokumente durchsuchen und lesen (nur lesend, nur dieser Computer): `MCP-Server für KI-Assistenten` in den Einstellungen einschalten – siehe 'KI-Assistenten' im [vollständigen Handbuch](../DocFetcher_Manual.html).
* Mehr: [Einstellungen](Preferences.html), [Suchanfrage-Syntax](Query_Syntax.html), [vollständiges Handbuch](../DocFetcher_Manual.html).
