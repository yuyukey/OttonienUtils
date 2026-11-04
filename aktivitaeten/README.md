# Aktivitäten

Zeigt im Spiel eine kleine Liste, bei welchen Gefolgen bzw. in welchen Lehen von Ottonien gerade **Aktivitäten** sind, also die Lehen, die auf [karte.ottonien.com](https://karte.ottonien.com/) pulsieren.

- Eine Zeile pro Lehen: Wappen und Name des Gefolges, dem das Lehen gehört (bei freien Lehen der Name des Lehens)
- Farblich nach Stärke: kleine Aktivität **orange**, sehr hohe Aktivität **rot** mit roter Fläche; rote stehen immer oben
- Die Liste bleibt auch bei offenem Chat sichtbar
- Beginnt während du spielst eine neue Aktivität, steht rechts daneben „neu“ bzw. wie viele Minuten sie schon läuft, und es kommt eine Meldung im Chat (`"notifyNew"`)
- Frei verschiebbar und skalierbar: `/aktivitaeten verschieben` oder „Verschieben und Größe …“ in den Einstellungen, dann die Liste mit der Maus ziehen und mit dem Mausrad oder − / + die Größe ändern (50–200 %)
- Alternativ feste Positionen (oben/Mitte, links/rechts) und die Anzahl der Einträge in den Einstellungen
- Taste zum Ein- und Ausblenden (in den Steuerungseinstellungen unter „Aktivitäten“, standardmäßig nicht belegt)
- Einstellungen im Spiel über Mod Menu oder `/aktivitaeten einstellungen`

Die Daten kommen jede Minute neu von der öffentlichen API hinter karte.ottonien.com (`mcp-api.ottonien.com`, Feld `player_gathering` der Lehen). Die Wappen kommen aus dem Ottonien-Ressourcenpaket, wie bei der Lehenskarte.

## Befehle

| Befehl | Wirkung |
|---|---|
| `/aktivitaeten` | Alle Aktivitäten im Chat, mit Lehen und Koordinaten |
| `/aktivitaeten einstellungen` | Einstellungen öffnen |
| `/aktivitaeten verschieben` | Liste mit der Maus verschieben, Größe mit dem Mausrad |
| `/aktivitaeten chat an` / `aus` | Chat-Meldungen bei neuen Aktivitäten ein- oder ausschalten (auch in den Einstellungen) |
| `/aktivitaeten neuladen` | Sofort neu abrufen |
| `/aktivitaeten an` / `aus` | Liste ein- oder ausblenden |

## Einstellungen

`config/aktivitaeten.json`:

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `hudEnabled` | `true` | Liste zeigen |
| `position` | `MIDDLE_RIGHT` | `TOP_LEFT`, `TOP_RIGHT`, `MIDDLE_LEFT`, `MIDDLE_RIGHT` oder `CUSTOM` (frei verschoben) |
| `overlayX`, `overlayY` | `1`, `0.5` | Bei `CUSTOM`: Lage als Anteil des freien Platzes (0 = links/oben, 1 = rechts/unten) |
| `maxEntries` | `8` | Höchstens so viele Lehen |
| `scale` | `100` | Größe in Prozent (50–200) |
| `showWhenEmpty` | `true` | Liste auch zeigen, wenn nirgends etwas los ist |
| `notifyNew` | `true` | Chat-Meldung bei neuer Aktivität |
| `onlyOnOttonien` | `true` | Nur auf Servern mit „ottonien“ in der Adresse |

## Quellen

- **Wappen:** Ottonien-Ressourcenpaket ([Ottonien/ottonien-reformed](https://github.com/Ottonien/ottonien-reformed))
- **Aktivitäten, Lehen und Gefolge:** [karte.ottonien.com](https://karte.ottonien.com/) bzw. die öffentliche Ottonien-API
