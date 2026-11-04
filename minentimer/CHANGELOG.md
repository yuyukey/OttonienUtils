# Changelog

## 1.6.0 (Testversion, noch nicht veröffentlicht)

- **Auto-Markieren:** Neuer Schalter unter Einstellungen → „Auto-Markieren“, standardmäßig aus. Ist er an und du baust in der Nähe einer Mine ihren Rohstoff ab, startet ihr Timer von selbst, z. B. Eisenerz an einer Eisenmine oder Kohle an einem Kohlevorkommen. Es zählt der Markier-Radius (Standard 40 Blöcke). Andere Blöcke wie Stein lösen nichts aus, und ein laufender Timer wird nicht neu gestartet.
- Welche Blöcke zu welchem Rohstoff gehören, steht in `config/minentimer/config.json` unter `autoMarkBlocks` und lässt sich dort ändern.

## 1.5.1

- Der Knopf **„Standard-Server“** im Teilen-Fenster ist weg, seit jeder einen eigenen Gruppen-Code hat, wird er nicht mehr gebraucht.
- Wer eine andere Server-Adresse eingetragen hatte, leert einfach das Feld „Server-URL“. Beim Schließen nutzt die Mod dann wieder den eingebauten Server, der Gruppen-Code bleibt.

## 1.5.0

- **Eigener Gruppen-Code für neue Spieler:** Wer die Mod neu installiert, bekommt statt „standard“ einen zufälligen Code wie `K7QX-M2PA-9TZD`. So landet niemand mehr aus Versehen in derselben Gruppe wie Fremde.
- **Teilen-Fenster:** Unter dem Gruppen-Code steht jetzt, dass du ihn mit deinen Freunden teilen musst. Mit dem neuen Knopf **Kopieren** landet er in der Zwischenablage.
- Wer schon einen Gruppen-Code hat, behält ihn. Auch „Standard-Server“ lässt den Code jetzt in Ruhe.

## 1.4.0

- **Neue Standardtasten:** **#** markiert die nächste Mine (bisher O), **Numpad 3** öffnet das Menü (bisher P), **Numpad 4** schaltet das Overlay ein und aus (bisher K).
- Die neuen Tasten gelten auch, wenn du die alten schon einmal gespeichert hattest. Eigene Belegungen musst du nach dem Update einmal neu einstellen, unter Optionen → Steuerung → Tastenbelegung → Minen-Timer.
- Im Einstellungsmenü heißt „Taste O Radius“ jetzt „Markier-Radius“.

## 1.3.0

- **Overlay nach Lehen gruppiert:** Bei „Alle Lehen“ zeigt das Overlay die Minen jetzt nach Lehen geordnet, jedes mit eigener Überschrift. Minen ohne Lehen stehen unten unter „Ohne Lehen“.
- **Overlay folgt der Sortierung im Menü:** Stellst du im Menü (P) „Sortierung: Entfernung“ oder „Status“ ein, sortiert auch das Overlay so, innerhalb jedes Lehens. Die Sortierung gilt nur für dich.

## Server-Update (3.11.2026, nur für den Server-Betreiber)

- Der Sync-Server prüft jetzt jeden Eintrag. Kaputte, zu große oder gefälschte Minen und Timer, Zeitstempel aus der Zukunft und Löschungen werden abgelehnt. Die Daten sind auf insgesamt 10 MB begrenzt.
- nginx bremst zu viele Anfragen pro IP.
- Die Daten werden täglich gesichert, 14 Tage lang.
- An der Mod ändert sich nichts. Die Anleitung steht in `server/EINRICHTEN.md` unter „Absicherung nachrüsten“.

## 1.2.2

- **Fehler behoben:** Wer die Minen noch mit einer älteren Version bekommen hatte, sah nach dem Update keine Lehen (nur „Alle Lehen“ und „Ohne Lehen“). Die Lehen werden jetzt beim nächsten Abgleich automatisch nachgeladen.

## 1.2.1

- **Lehen-Gruppen:** Jede Mine kann einem Lehen zugeordnet werden (beim Bearbeiten, Feld „Lehen“, z. B. *Sturmhöhe* oder *Rosenwacht*). Der Name wird mit allen geteilt.
- **Lehen umschalten:** Mit **Pfeil rechts / Pfeil links** (änderbar unter Steuerung) schaltest du durch: Alle Lehen → jedes Lehen → Ohne Lehen. Liste und Overlay zeigen dann nur die Minen dieses Lehens. Im Menü geht das auch mit ◀ / ▶ oben.
- Das Overlay zeigt oben den Namen des gewählten Lehens.
- „+ Mine hier“ trägt das gerade gewählte Lehen gleich ein.
- **Overlay „Zeigen“:** „Nur im Cooldown“ heißt jetzt **„Nicht im Cooldown“** und blendet die Minen im Cooldown aus, sodass nur bereite übrig bleiben. „Nur bereite“ aus 1.2.0 ist darin aufgegangen.

## 1.2.0

- Overlay-Option „Nur bereite“: Das Overlay zeigt nur Minen, die bereit sind.

## 1.1.0

- Minen-Liste umsortieren: ▲ / ▼ neben dem ✎ schieben eine Mine nach oben oder unten. Die Reihenfolge gilt nur für dich und auch fürs Overlay.

## 1.0.0

- Erste Version: Timer pro Mine, Overlay, Ton, eigene Minen, Vorkommen/Mine, geteilte Timer über den eigenen Server, Overlay verschiebbar, Taste K für das Overlay.
