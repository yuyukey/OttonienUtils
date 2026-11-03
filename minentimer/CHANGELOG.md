# Changelog

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
