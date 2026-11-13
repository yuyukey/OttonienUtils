# Changelog – Viehzucht

## 0.1.5

- Nicht nur Kühe: Alle Zuchttiere (Schafe, Ziegen, Schweine, Pferde, Esel …) leuchten nach einer Paarung rosa, bis sie sich wieder paaren können. Geschorene Schafe bleiben golden

## 0.1.4

- Gepaarte Kühe leuchten jetzt auch, wenn die Mod die Paarung nicht selbst gesehen hat: Nennt der Server unter „Paarungsbereit“ eine Wartezeit, ist die Kuh rosa umrandet
- Herzchen, die Ottonien als Partikel schickt, werden jetzt erkannt (Status „verliebt“ und Paarungserkennung)

## 0.1.3

- Gepaarte Kühe leuchten: Sieht die Mod eine Paarung, ist die Kuh rosa umrandet, bis sie sich wieder paaren kann (laut Server-Info „Paarungsbereit: in …“, sonst nach der Wartezeit aus der Tabelle). `/viehzucht leuchten aus` schaltet auch das ab

## 0.1.2

- Geschorene Schafe leuchten: Wer ein Schaf mit der Schere schert, sieht es golden umrandet, bis es wieder scherbar ist (laut Server-Info „Scherbar: in …“, sonst nach 6 Stunden). Die Tafel zeigt die Wartezeit unter „Scherbar“. Abschalten mit `/viehzucht leuchten aus`

## 0.1.1

- Keine Tafel mehr für das eigene Reittier, solange du draufsitzt
- Tafel skalierbar und verschiebbar: `/viehzucht anpassen` (mit der Maus ziehen, Mausrad für die Größe), `groesse`, `verschieben`, `zuruecksetzen`

## 0.1.0

Erste Version.

- Tafel neben dem Fadenkreuz beim Angucken von Tieren: Paarungsstatus in Farbe, Geschlecht, Mast als Balken, Mastverlust, Melkbar, Scherbar
- Fragt die Tier-Info beim kurzen Anvisieren automatisch ab (wie Shift + Rechtsklick, Ablauf nach OttoExtra) und blendet die Antwort im Chat aus
- Merkt sich die Infos pro Tier und zählt die Zeiten live weiter
- ♂ / ♀ hinter dem Namen über dem Tier
- Erkennt Paarungen (Herzchen und Jungtier) und schätzt die Wartezeit nach Ottoniens Tabelle
- Server-Infos im Chat auf Wunsch ausblendbar
- Die Tafel bleibt verborgen, solange du TAB (Spielerliste) gedrückt hältst
