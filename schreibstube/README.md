# Schreibstube

Briefe für Ottonien schreiben, bequem wie in einem Textprogramm. Statt jede Zeile mit `/letter TEXT` in den Chat zu tippen, öffnet sich eine Schreibstube mit Pergament und Buchvorschau.

- **Öffnen:** Rechtsklick mit „Pergament und Feder“ (Phantomhaut) in der Hand, `/schreibstube` oder eine eigene Taste (Steuerung → „Schreibstube“, standardmäßig nicht belegt)
- **Schreiben wie gewohnt:** Text markieren mit der Maus oder mit Umschalt + Pfeiltasten, Doppelklick markiert ein Wort, Strg+A, Strg+C/X/V, Strg+Z/Y, Strg+Pfeil springt wortweise
- **Formatieren:** Text markieren und oben eine Farbe oder **B** / *I* / U / S / ? (Geheimschrift) anklicken, oder Strg+B, Strg+I, Strg+U. Ohne Markierung gilt die Formatierung für das, was du als Nächstes tippst. ✕ entfernt die Formatierung.
- **Einfügen und Kopieren mit Formatierung:** eingefügte `&6`/`&l`-Codes werden zu Farbe und Stil; Strg+C nimmt die Formatierung als &-Codes mit, Strg+Umschalt+C kopiert nur den Text
- **Buchvorschau:** rechts siehst du jede Buchseite so, wie der Server sie füllt (blättern mit den Pfeilen oder dem Mausrad). Würde eine Seite im Buch unten abgeschnitten, gibt es einen Hinweis.
- **Seitenwechsel im Text:** schon beim Tippen erscheint links eine Linie „Seite 2“, „Seite 3“ … genau dort, wo der Server eine neue Buchseite beginnt
- **Neue Seite:** ✂ oder Strg+Enter beginnt an dieser Stelle eine neue Buchseite
- **Schreiben:** „✒ Schreiben“ schickt den Brief in passenden `/letter`-Stücken an den Server, mit Fortschritt in der Aktionsleiste. Legst du das Pergament weg, hält es an und lässt sich mit [Weiter] fortsetzen.
- **Brief bleibt in der Hand:** halte genau ein Pergament in der Hand und keins weiter links in der Hotbar (der Server beschreibt immer das erste). Landet der Brief trotzdem woanders im Inventar oder am Mauszeiger, holt die Schreibstube ihn zurück. Das automatische Nachfüllen von Mouse Wheelie ist während des Schreibens pausiert.
- **Verschicken:** danach öffnet sich die Empfänger-Auswahl (Spieler online, zuletzt angeschriebene oben) und der Brief geht mit `/post` raus
- **Angefangene Briefe:** hältst du einen schon beschriebenen Brief, schreibt die Schreibstube dort weiter; im geöffneten Brief gibt es die Knöpfe „Weiterschreiben“ und „Verschicken …“
- **Entwürfe:** speichern und laden unter „Entwürfe …“. Sie liegen als Textdateien in `config/schreibstube/briefe` und lassen sich dort auch mit einem Texteditor bearbeiten. Fertig geschriebene Briefe werden dort automatisch archiviert. Der aktuelle Brief wird laufend gesichert.
- **Signatur:** fügt Gruß und Namen ein. Umschalt-Klick merkt sich den markierten Text als neue Signatur.
- **Übernehmen:** holt den Text aus einem Buch, Buch und Feder oder Brief in deiner Hand in die Schreibstube

## Wie der Server Briefe füllt

Das Brief-Plugin auf Ottonien hängt jeden `/letter`-Befehl als neue Zeile an. Mehrere Leerzeichen werden zu einem, `&`-Codes werden zu Formatierung, `\n` zu einer neuen Zeile. Eine Buchseite gilt als voll bei etwa 13 Zeilen (geschätzt mit 18 Zeichen je Zeile) oder 255 Zeichen. Die Schreibstube rechnet das genau nach, teilt lange Absätze dort, wo der Server die Seite wechselt, und setzt die Formatierung auf der neuen Seite fort. Leere Zeilen und Seitenumbrüche werden mit unsichtbaren `&r` geschrieben, damit der Server sie nicht weglässt.

Die Regeln stammen aus der Brief-Funktion von [OttoExtra](https://github.com/Skyfuller1303/OttoExtra) (Skyfuller1303, MIT-Lizenz).

## Befehle

| Befehl | Wirkung |
|---|---|
| `/schreibstube` | Schreibstube öffnen |
| `/schreibstube verschicken [Spieler]` | Empfänger-Auswahl öffnen |
| `/schreibstube entwuerfe` | Entwürfe öffnen |
| `/schreibstube einstellungen` | Einstellungen öffnen |
| `/schreibstube weiter` | Angehaltenes Schreiben fortsetzen |
| `/schreibstube abbrechen` | Schreiben abbrechen |

## Einstellungen

Im Spiel über das Zahnrad, Mod Menu oder `/schreibstube einstellungen`; gespeichert in `config/schreibstube.json`:

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `openOnUse` | `true` | Rechtsklick mit Pergament und Feder öffnet die Schreibstube |
| `itemName` | `Pergament und Feder` | Name des Schreibzeugs (Teil reicht) |
| `delayMs` | `1500` | Pause zwischen zwei `/letter`-Befehlen (600–5000 ms) |
| `hideServerEcho` | `true` | „Du hast den Brief bearbeitet“ beim Schreiben ausblenden |
| `copyWithCodes` | `true` | Strg+C nimmt die Formatierung als &-Codes mit |
| `pasteCodes` | `true` | &-Codes beim Einfügen als Formatierung lesen |
| `askRecipientAfterWrite` | `true` | Nach dem Schreiben die Empfänger-Auswahl öffnen |
| `signature` | `&oHochachtungsvoll,⏎&l%spieler%` | Signatur, `%spieler%` wird dein Name |
| `onlyOnOttonien` | `true` | Rechtsklick-Öffnen nur auf Servern mit „ottonien“ in der Adresse |
