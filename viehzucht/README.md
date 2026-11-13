# Viehzucht

Zeigt auf Ottonien eine kleine Tafel neben dem Fadenkreuz, sobald du ein Tier anguckst. Darauf siehst du sofort, ob das Tier sich paaren kann.

- **Farbiger Streifen und Statuszeile:**
  - **grün** ❤ paarungsbereit
  - **rot** ✖ braucht noch Mast (z. B. „braucht Mast 65/80“)
  - **orange** ⌛ wartet noch (z. B. nach einer Paarung)
  - **türkis** ✿ Jungtier, wächst noch
  - **rosa** ❤ verliebt (Herzchen gerade gesehen)
  - **grau**: noch keine Infos
- Geschlecht mit ♂ / ♀ und dem Wort des Servers (Bulle, Kuh, Bock, Schaf, Hengst …)
- Mast als Balken, grün, sobald genug Mast für die Paarung da ist
- Mastverlust, Melkbar, Scherbar und bei Pferden Leben, Sprunghöhe und Geschwindigkeit
- Alle Zeiten laufen live weiter herunter und bleiben auch nach einem Neustart erhalten
- Ohne Infos zeigt die Tafel die Werte aus Ottoniens Tabelle, z. B. „Paarung ab Mast 80 · Wartezeit 24h“

- ♂ / ♀ auch hinter dem Namen über dem Tier

## So funktioniert es

Ottonien schickt die Infos zu einem Tier nur in den Chat, wenn man es mit **Shift + Rechtsklick** anklickt. Das erledigt die Mod für dich: Visierst du ein Zuchttier kurz an, fragt sie die Info automatisch ab und blendet die Antwort im Chat aus. Sie merkt sich die Infos für genau dieses Tier und zählt die Zeiten selbst weiter. Ein Tier fragt sie höchstens einmal pro Minute neu ab, nach dem Füttern sofort.

Damit dabei nichts passiert, fragt die Mod nicht, wenn du ein passendes Futter, eine Leine, eine Schere, einen Eimer, ein Namensschild oder einen Sattel in der Hand hast. Sie fragt auch nicht, wenn du reitest, schleichst, sprintest, gerade etwas benutzt oder eben selbst geklickt hast. Bei Pferden sendet sie kurz „Schleichen“ mit, damit du nicht aufsteigst. Der Ablauf ist von [OttoExtra](https://github.com/Skyfuller1303/OttoExtra) (Skyfuller1303, MIT-Lizenz) übernommen. Mit `/viehzucht auto aus` schaltest du die automatische Abfrage ab, dann geht es wie bisher per Shift + Rechtsklick.

Sitzt du auf einem Pferd, zeigt die Tafel dein eigenes Reittier nicht an.

„Stand: vor 5 Min“ zeigt, wie alt die Infos sind.

Siehst du Herzchen über zwei Tieren und erscheint daneben ein Jungtier, merkt sich die Mod die Paarung. Sie zeigt dann die Wartezeit aus Ottoniens Tabelle als Schätzung an, bis du das Tier wieder anklickst.

Gepaarte Kühe sind rosa umrandet, bis sie sich wieder paaren können. Selbst geschorene Schafe sind golden umrandet, bis sie wieder scherbar sind.

## Befehle

| Befehl | Wirkung |
|---|---|
| `/viehzucht` | Kurze Hilfe und wie viele Tiere gemerkt sind |
| `/viehzucht an` / `aus` | Tafel ein- oder ausblenden (auch per Taste, in den Steuerungseinstellungen unter „Viehzucht“) |
| `/viehzucht anpassen` | Fenster, in dem du die Tafel mit der Maus verschiebst und mit dem Mausrad größer oder kleiner machst |
| `/viehzucht groesse <Prozent>` | Größe der Tafel, 50 bis 200 |
| `/viehzucht verschieben <x> <y>` | Tafel um so viele Pixel verschieben, z. B. `verschieben 0 -40` schiebt sie nach oben |
| `/viehzucht zuruecksetzen` | Tafel wieder neben dem Fadenkreuz in normaler Größe |
| `/viehzucht auto an` / `aus` | Automatische Abfrage beim Anvisieren ein- oder ausschalten |
| `/viehzucht leuchten an` / `aus` | Umrandung für gepaarte Kühe und geschorene Schafe ein- oder ausschalten |
| `/viehzucht chat aus` / `an` | Die Tier-Infos des Servers im Chat ausblenden, weil sie auf der Tafel stehen, oder wieder anzeigen |
| `/viehzucht vergessen` | Alle gemerkten Tiere auf diesem Server löschen |
| `/viehzucht analyse an` / `aus` / `leeren` | Zur Fehlersuche aufzeichnen, was der Server über Tiere schickt (`viehzucht-analyse.txt` im Spielordner) |

## Einstellungen

`config/viehzucht.json`:

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `overlayEnabled` | `true` | Tafel zeigen |
| `autoQuery` | `true` | Tier-Info beim Anvisieren automatisch abfragen |
| `hideAutoChat` | `true` | Automatisch angeforderte Antworten im Chat ausblenden (eigene Klicks bleiben sichtbar) |
| `hoverDelayMs` | `150` | So lange anvisieren, bevor gefragt wird |
| `autoRefreshSeconds` | `60` | Infos, die älter sind, beim Anvisieren neu abfragen |
| `nametagGender` | `true` | ♂ / ♀ hinter den Namen über dem Tier |
| `glowSheared` | `true` | Selbst geschorene Schafe umranden |
| `glowBred` | `true` | Gepaarte Kühe umranden |
| `hideChatInfo` | `false` | Auch die Antworten auf eigene Klicks im Chat ausblenden |
| `analysis` | `false` | Analyse-Datei schreiben |
| `onlyOnOttonien` | `true` | Nur auf Servern mit „ottonien“ in der Adresse |

Die gemerkten Tiere stehen in `config/viehzucht/tiere-<server>.json`. Tiere, die du 14 Tage nicht gesehen hast, werden vergessen.
