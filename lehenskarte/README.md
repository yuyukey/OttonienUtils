# Lehenskarte

Zeigt die Lehen von Ottonien direkt in **Xaero's World Map** und **Xaero's Minimap**:

- Flächen in der Farbe des Machtbereichs: Alle Lehen unter demselben obersten Lehnsherrn haben dieselbe Farbe, wie auf [karte.ottonien.com](https://karte.ottonien.com/)
- Kräftige Grenzen zwischen Machtbereichen, feinere Grenzen zwischen Lehen innerhalb eines Machtbereichs
- Freie Lehen hellgrau, gesperrte Lehen dunkelgrau, beide mit dem leeren Wappen „kein Wappen“
- Banner der Gefolge in der World Map, live aus dem Ottonien-Ressourcenpaket
- Beim Überfahren erscheint der Name des Lehens. Ein Linksklick wählt das Lehen aus und zeigt ein Infofeld mit Gefolge, Rang, Anführer, Mitgliedern, Lehnsherr, Vasallen und Beschreibung. Ein Klick auf das Infofeld oder ein zweiter Klick schließt es.
- Oben in der Mitte steht, in welchem Lehen man gerade ist
- Beim Rauszoomen werden Lehen zusammengefasst: erst Freiherrschaften o. Ä. mit ihren Vasallen, dann ganze Machtbereiche, jeweils mit einem Wappen (`"groupOnZoomOut"`)
- Wappen und Namen wachsen mit dem Zoom und blenden zwischen den Stufen weich über; Namen haben einen dunklen Rand
- Größe der Wappen auf der Karte einstellbar (50–200 %, `"bannerScale"`)
- Grenzen zwischen Machtbereichen mit einem Farbstreifen, der nach innen ausläuft
- Im Infofeld eines Lehens auch Bergbau und Bonus
- Meldung im Chat, wenn ein Lehen den Besitzer wechselt, frei wird oder ein Gefolge den Lehnsherrn wechselt (`"notifyChanges"`)
- Daten und Wappen werden zwischengespeichert und sind beim Start sofort da; aktualisiert wird im Hintergrund
- Einstellungen im Spiel über Mod Menu oder `/lehenskarte einstellungen`
- NPC-Dörfer als Glocke mit Namen (`"npcVillages"`); eigene Liste als `config/lehenskarte/npc_doerfer.json` möglich
- Noch nicht erkundete, schwarze Bereiche der World Map zeigen die gemalte Karte von karte.ottonien.com, so dass man die Insel schon erkennt (`"paintedMap"`)

Gefolge, Besitzer und Banner kommen alle 5 Minuten neu von der öffentlichen API (`mcp-api.ottonien.com`). Die Grenzen sind in der Mod mitgeliefert (siehe Quellen).

## Quellen

- **Lehensgrenzen:** Polygone aus [OttoExtra](https://github.com/Skyfuller1303/OttoExtra) von Skyfuller1303, verwendet mit seiner schriftlichen Erlaubnis. Vielen Dank!
- **Farben der Machtbereiche, Lehen- und Gefolgedaten:** [karte.ottonien.com](https://karte.ottonien.com/) bzw. die öffentliche Ottonien-API
- **Gemalte Karte für unerkundete Bereiche:** Kartenbild von [karte.ottonien.com](https://karte.ottonien.com/) (Ottonien)
- **Wappen:** Ottonien-Ressourcenpaket ([Ottonien/ottonien-reformed](https://github.com/Ottonien/ottonien-reformed))

Die MIT-Lizenz des Repositorys gilt für den Code. Die Lehensgrenzen (`lehen.json`) und das Kartenbild (`textures/map/karte.png`) gehören ihren jeweiligen Urhebern und fallen nicht darunter.

## Befehle

| Befehl | Wirkung |
|---|---|
| `/lehenskarte` | Status |
| `/lehenskarte einstellungen` | Einstellungen öffnen |
| `/lehenskarte debug` | Position, erkanntes Lehen, Abstand zur Grenze (für die Kalibrierung) |
| `/lehenskarte verschieben <x> <z>` | Alle Grenzen um x/z Blöcke verschieben (addiert sich) |
| `/lehenskarte zuruecksetzen` | Verschiebung auf 0 |
| `/lehenskarte neuladen` | Grenzen und Daten neu laden |
| `/lehenskarte an` / `aus` | Karte ein- oder ausschalten (auch per Taste, standardmäßig ohne Belegung) |

Einstellungen stehen in `config/lehenskarte.json` (auch im Spiel über Mod Menu oder `/lehenskarte einstellungen`). Die gemalte Karte lässt sich dort mit `paintedMapOffsetX`/`paintedMapOffsetZ` verschieben. Mit `"onlyOnOttonien": false` läuft die Mod auch im Einzelspieler.
Eigene Grenzen kann man als `config/lehenskarte/lehen.json` ablegen (gleiches Format wie die mitgelieferte Datei).

## Bauen

Xaeros JARs werden nur zum Kompilieren gebraucht und liegen in `lehenskarte/libs/` (nicht im Repository):
`xaeroworldmap-fabric-1.21.11-1.41.0.jar`, `xaerominimap-fabric-1.21.11-26.1.0.jar` und `xaerolib-fabric-1.21.11-1.1.15.jar` (steckt in der World-Map-JAR unter `META-INF/jars`), dazu `modmenu-17.0.0.jar` für den Einstellungs-Knopf.

```bash
./gradlew :lehenskarte:build
```

Getestet mit Xaero's World Map 1.41.0 und Xaero's Minimap 26.1.0.
