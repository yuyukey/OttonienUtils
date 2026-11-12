# Changelog – Schreibstube

## 0.1.1

- Erhaltene Briefe in den Editor holen: Beim Lesen eines Briefs gibt es oben links den Knopf „✒ Im Editor öffnen“. Text, Farben und Seiten landen in der Schreibstube; dein bisheriger Entwurf wird vorher unter „Entwürfe“ gesichert
- Erhaltene Briefe ohne „Zu …“ werden am Absender erkannt und nicht mehr für den eigenen angefangenen Brief gehalten (kein „Weiterschreiben“/„Verschicken“ mehr bei fremden Briefen)

## 0.1.0

Erste Version.

- Rechtsklick mit „Pergament und Feder“ öffnet die Schreibstube, alternativ `/schreibstube` oder eine eigene Taste
- Brief auf Pergament schreiben: markieren mit Maus oder Umschalt, Doppelklick markiert ein Wort, Strg+A/C/X/V, Strg+Z/Y
- Markierten Text formatieren: 16 Farben, fett, kursiv, unterstrichen, durchgestrichen, Geheimschrift, Formatierung entfernen (Strg+B/I/U)
- Einfügen mit &-Codes (z. B. aus Discord) wird zu Formatierung, Kopieren nimmt die Formatierung als &-Codes mit (Strg+Umschalt+C ohne)
- Buchvorschau daneben, genau so wie der Server die Seiten füllt, mit Hinweis, wenn eine Seite im Buch abgeschnitten würde
- Seitenwechsel des Servers erscheinen schon beim Tippen als Linie „Seite 2“, „Seite 3“ … im Text, Absätze werden dort geteilt wie im Buch
- Seitenumbruch an jeder Stelle (✂ oder Strg+Enter)
- Formatierung läuft über Seitengrenzen weiter
- Schreiben mit `/letter` in passenden Stücken, Fortschritt in der Aktionsleiste, hält an, wenn man das Pergament weglegt
- Landet der Brief nach dem ersten Befehl woanders im Inventar oder am Mauszeiger, holt die Schreibstube ihn zurück in die Hand; das Nachfüllen von Mouse Wheelie ist während des Schreibens pausiert
- Warnung, wenn weiter links in der Hotbar noch ein Pergament liegt (der Server beschreibt immer das erste)
- Angefangene Briefe werden fortgesetzt, die Vorschau beginnt bei den schon geschriebenen Seiten
- Danach Empfänger auswählen (Spieler online und zuletzt angeschriebene) und mit `/post` verschicken
- Entwürfe speichern und laden, als Textdateien in `config/schreibstube/briefe`; geschriebene Briefe werden dort archiviert
- Signatur mit Gruß und Namen per Knopfdruck einfügen
- Text aus einem Buch oder Brief in der Hand übernehmen
- Im geöffneten Brief Knöpfe „Weiterschreiben“ und „Verschicken …“
