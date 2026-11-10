# OttonienUtils

Kleine Client-Mods für den Minecraft-Rollenspiel-Server **Ottonien**, damit der Alltag im Spiel etwas leichter wird.

> **Inoffiziell:** OttonienUtils ist ein Projekt von Spielern für Spieler. Es ist kein offizielles Projekt des Ottonien-Teams und wird von ihm nicht betreut. Die Mods sind nur für Ottonien gedacht und auf anderen Servern wenig nützlich.

## Mods

| Mod | Wofür | Download | Version |
|---|---|---|---|
| [Minen-Timer](minentimer/README.md) | Respawn-Timer für Minen und Vorkommen mit Overlay, Lehen-Gruppen und Timern, die du pro Lehen mit anderen teilst | [⬇️ 1.8.3](https://github.com/yuyukey/OttonienUtils/releases/download/minentimer-v1.8.3/minentimer-1.8.3.jar) | [Changelog](minentimer/CHANGELOG.md) · [alle Releases](https://github.com/yuyukey/OttonienUtils/releases?q=minentimer) |
| [Lehenskarte](lehenskarte/README.md) | Alle Lehen mit Grenzen, Farben der Machtbereiche und aktuellen Wappen in Xaero's World Map und Minimap | [⬇️ 0.1.5](https://github.com/yuyukey/OttonienUtils/releases/download/lehenskarte-v0.1.5/lehenskarte-0.1.5.jar) | [Changelog](lehenskarte/CHANGELOG.md) · [alle Releases](https://github.com/yuyukey/OttonienUtils/releases?q=lehenskarte) |
| [Aktivitäten](aktivitaeten/README.md) | Liste im Spiel, bei welchen Gefolgen gerade Aktivitäten sind, mit Wappen, orange oder rot nach Stärke | [⬇️ 0.1.2](https://github.com/yuyukey/OttonienUtils/releases/download/aktivitaeten-v0.1.2/aktivitaeten-0.1.2.jar) | [Changelog](aktivitaeten/CHANGELOG.md) · [alle Releases](https://github.com/yuyukey/OttonienUtils/releases?q=aktivitaeten) |
| [Schreibstube](schreibstube/README.md) | Briefe mit Pergament und Feder bequem schreiben: Editor mit Markieren, Kopieren, Einfügen und Formatierung, Buchvorschau genau wie der Server die Seiten füllt, Entwürfe und Versand | [⬇️ 0.1.0](https://github.com/yuyukey/OttonienUtils/releases/download/schreibstube-v0.1.0/schreibstube-0.1.0.jar) | [Changelog](schreibstube/CHANGELOG.md) · [alle Releases](https://github.com/yuyukey/OttonienUtils/releases?q=schreibstube) |
| [Viehzucht](viehzucht/README.md) | Tafel beim Angucken von Tieren: paarungsbereit oder nicht, Geschlecht, Mast als Balken, Melken, Scheren und Wartezeiten, die live weiterlaufen | [⬇️ 0.1.0](https://github.com/yuyukey/OttonienUtils/releases/download/viehzucht-v0.1.0/viehzucht-0.1.0.jar) | [Changelog](viehzucht/CHANGELOG.md) · [alle Releases](https://github.com/yuyukey/OttonienUtils/releases?q=viehzucht) |


Weitere Mods folgen.

## Installation

1. Installiere **Fabric** für **Minecraft 1.21.11**, zum Beispiel über die Modrinth-App, dazu die **Fabric API**.
2. Lade die gewünschte Mod herunter: Der Link in der Spalte **Download** oben führt direkt zur JAR der neuesten Version. Ältere Versionen findest du unter [Releases](https://github.com/yuyukey/OttonienUtils/releases).
3. Leg die JAR in den `mods`-Ordner deines Profils und starte das Spiel.

Wie die einzelnen Mods funktionieren, steht in ihrer eigenen Anleitung.

## Aufbau

```
OttonienUtils/
├── minentimer/        Minen-Timer (Mod, Anleitung, Changelog, Sync-Server)
├── lehenskarte/       Lehenskarte (Mod, Anleitung, Changelog)
├── aktivitaeten/      Aktivitäten (Mod, Anleitung, Changelog)
├── schreibstube/      Schreibstube (Mod, Anleitung, Changelog)
├── viehzucht/         Viehzucht (Mod, Anleitung, Changelog)
├── gradle/            Gradle-Wrapper, gemeinsam für alle Mods
├── gradle.properties  gemeinsame Versionen (Minecraft, Fabric)
└── settings.gradle    Liste aller Mods
```

Neue Mods bekommen einen eigenen Ordner und eine Zeile `include '<ordner>'` in `settings.gradle`.

## Selbst bauen

Du brauchst ein JDK 25, damit Gradle läuft. Im Hauptordner:

```bash
./gradlew build
```

Die JARs liegen danach in `<mod>/build/libs/`.

## Fehler und Ideen

Fehler meldest du und Ideen schlägst du vor unter [Issues](https://github.com/yuyukey/OttonienUtils/issues).

## Lizenz

[MIT](LICENSE): Du darfst den Code frei nutzen, ändern und weitergeben, solange der Lizenzhinweis erhalten bleibt.
