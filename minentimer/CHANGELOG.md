# Changelog

## 1.8.1

- **Neues Overlay-Design** passend zur Aktivitäten-Mod: dunkle Fläche mit feinem goldenem Rahmen, Titel mit Anzahl bereiter Minen und Trennlinie. Bereite Minen sind grün hinterlegt und haben einen pulsierenden Punkt.
- Das Minen-Overlay verschwindet, solange der Chat offen ist.
- **Keine Beispiel-Minen mehr:** Eine neue Installation startet mit leerer Liste, du trägst deine eigenen Minen mit „+ Mine hier“ ein. Wer die Mod schon hat, behält seine Liste, auch übrig gebliebene Beispiel-Minen. Die kannst du in der Liste löschen.

## 1.8.0

- **Mitgliederliste:** Fährst du im Teilen-Fenster mit der Maus über ein Lehen, siehst du, wer dessen Code benutzt und wann zuletzt, z. B. „Kamerad – vor 3 Std.“. Die Zahl hinter dem Lehen zeigt, wie viele es sind.
- Die Namen meldet jede Mod selbst. Wer ein Lehen mit ✕ verlässt oder 30 Tage nicht da war, fällt aus der Liste.
- Dafür schickt die Mod deinen Minecraft-Namen an jedes Lehen, dessen Code du hast, auch wenn du nur zuschaust.
- Braucht das Server-Update dieser Version, sonst bleibt die Liste leer. Alles andere funktioniert auch ohne.
- **Anmeldung beim Serverbeitritt:** Der Abgleich wartet jetzt, bis du in der Welt bist, und danach noch 5 Sekunden. Vorher lief er schon im Hauptmenü und beim Verbinden mit vielen Verbindungen gleichzeitig. Das konnte die Anmeldung bei Mojang stören („Anmeldung fehlgeschlagen … versuche es erneut“). Außerdem fragt die Mod jetzt höchstens zwei Lehen gleichzeitig ab.

## 1.7.0

- **Nur noch Lehen-Codes:** Jedes Lehen wird über einen **eigenen Code** geteilt, einen Gruppen-Code gibt es nicht mehr. Haben Spieler aus verschiedenen Gruppen Rechte auf dasselbe Lehen, geben sie sich den Code dieses Lehens. Dann sehen alle dessen Minen und Timer, aber nichts von deinen anderen Lehen.
- **Minen ohne Lehen bleiben bei dir.** Willst du z. B. die Eisenmine teilen, gib ihr ein Lehen.
- Im Teilen-Fenster steht jedes Lehen mit seinem Code und einem Kopieren-Knopf. Einen Code, den dir jemand gibt, trägst du unten ein, das Lehen taucht dann von selbst auf.
- **Code ändern:** Mit „Neu“ bekommt ein Lehen einen neuen Code, z. B. wenn der alte bei den Falschen gelandet ist. Wer nur den alten hat, sieht das Lehen nicht mehr. Gib den neuen allen, die weiter mitmachen sollen.
- Mit ✕ verlässt du ein Lehen. Seine Minen verschwinden nur bei dir.
- **Bestehende Gruppen laufen weiter:** Wer bisher denselben Gruppen-Code hatte, bekommt automatisch dieselben Lehen-Codes. Alle sollten aber auf diese Version updaten.
- Die Server-Adresse steht jetzt hinter dem Knopf „Server …“.

## 1.6.1

- **Auto-Markieren startet neu, wenn das Erz früher da ist:** Läuft der Timer einer Mine nur noch **höchstens 10 Minuten** und du baust dort schon wieder Erz ab, startet der Timer neu. Unten im Bild steht dann „neu gestartet (Erz war schon da)“. Bei mehr Restzeit bleibt der Timer wie bisher unberührt.
- Die 10 Minuten stehen in `config/minentimer/config.json` unter `autoMarkRestartMinutes`.
- **Kein 3-Minuten-Zuschlag mehr:** Auto-Markieren startet den Timer jetzt mit der normalen Respawn-Zeit, genau wie die #-Taste.

## 1.6.0

- **Auto-Markieren:** Ist standardmäßig an und lässt sich unter Einstellungen → „Auto-Markieren“ ausschalten. Baust du einen Rohstoff ab, startet der Timer der nächsten Mine dieser Art von selbst, wenn sie höchstens 50 Blöcke entfernt ist. Erz, das du anderswo abbaust, löst nichts aus. Beispiele: Eisenerz startet die nächste Eisenmine, Kohle das nächste Kohlevorkommen. Andere Blöcke wie Stein lösen nichts aus, und ein laufender Timer wird nicht neu gestartet.
- Beim Auto-Markieren kommen **3 Minuten** auf die Respawn-Zeit drauf, weil das Abbauen einer Mine etwa so lange dauert. Beim Markieren per Taste ändert sich nichts.
- Diese Blöcke zählen wie auf Ottonien: Eisenerz → Eisen, Steinkohleerz → Steinkohle, Lapislazuli- und Kupfererz → Salzkristall, Tuff → Torf, Ton → Tonklumpen. Tiefenschiefer-Varianten zählen auch.
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
