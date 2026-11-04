# Lehenskarte

Zeigt die Lehen von Ottonien direkt in **Xaero's World Map** und **Xaero's Minimap**:

- Flächen in der Farbe des Machtbereichs: Alle Lehen unter demselben obersten Lehnsherrn haben dieselbe Farbe, wie auf [karte.ottonien.com](https://karte.ottonien.com/)
- Kräftige Grenzen zwischen Machtbereichen, feinere Grenzen zwischen Lehen innerhalb eines Machtbereichs
- Freie Lehen hellgrau, gesperrte Lehen dunkelgrau
- Banner der Gefolge in der World Map, live aus dem Ottonien-Ressourcenpaket
- Beim Überfahren erscheint der Name des Lehens. Ein Linksklick wählt das Lehen aus und zeigt ein Infofeld mit Gefolge, Rang, Anführer, Mitgliedern, Lehnsherr, Vasallen und Beschreibung. Ein Klick auf das Infofeld oder ein zweiter Klick schließt es.
- Oben in der Mitte steht, in welchem Lehen man gerade ist

Gefolge, Besitzer und Banner kommen alle 5 Minuten neu von der öffentlichen API (`mcp-api.ottonien.com`). Die Grenzen stammen aus der Karte auf karte.ottonien.com und sind in der Mod mitgeliefert.

## Befehle

| Befehl | Wirkung |
|---|---|
| `/lehenskarte` | Status |
| `/lehenskarte debug` | Position, erkanntes Lehen, Abstand zur Grenze (für die Kalibrierung) |
| `/lehenskarte verschieben <x> <z>` | Alle Grenzen um x/z Blöcke verschieben (addiert sich) |
| `/lehenskarte zuruecksetzen` | Verschiebung auf 0 |
| `/lehenskarte neuladen` | Grenzen und Daten neu laden |
| `/lehenskarte an` / `aus` | Karte ein- oder ausschalten (auch per Taste, standardmäßig ohne Belegung) |

Einstellungen stehen in `config/lehenskarte.json`. Mit `"onlyOnOttonien": false` läuft die Mod auch im Einzelspieler.
Eigene Grenzen kann man als `config/lehenskarte/lehen.json` ablegen (gleiches Format wie die mitgelieferte Datei).

## Bauen

Xaeros JARs werden nur zum Kompilieren gebraucht und liegen in `lehenskarte/libs/` (nicht im Repository):
`xaeroworldmap-fabric-1.21.11-1.41.0.jar`, `xaerominimap-fabric-1.21.11-26.1.0.jar` und `xaerolib-fabric-1.21.11-1.1.15.jar` (steckt in der World-Map-JAR unter `META-INF/jars`).

```bash
./gradlew :lehenskarte:build
```

Getestet mit Xaero's World Map 1.41.0 und Xaero's Minimap 26.1.0.
