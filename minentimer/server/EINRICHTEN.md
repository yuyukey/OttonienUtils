# Sync-Server einrichten (Ubuntu)

Die Mod ist ab Werk mit `https://minen.vprondzinski.de` verbunden. Damit das funktioniert, muss dieser kleine Server einmal auf deinem Root-Server eingerichtet werden. Er braucht nur Python 3, das auf Ubuntu schon installiert ist.

**Dateien in diesem Ordner:**

| Datei | Wofür |
|---|---|
| `minentimer-server.py` | der Server selbst |
| `minentimer.env.example` | Vorlage für die Einstellungen. Als `minentimer.env` kopieren und einen eigenen **geheimen Schlüssel** eintragen (nie hochladen!) |
| `minentimer.service` | startet den Server automatisch (systemd) |
| `Caddyfile` | HTTPS mit automatischem Zertifikat, falls noch kein Webserver läuft |
| `nginx-minentimer.conf` | stattdessen, falls schon nginx läuft |
| `nginx-ratelimit.conf` | bremst zu viele Anfragen pro IP (für nginx) |
| `minentimer-backup.cron` | tägliche Sicherung der Daten |

## 1. Subdomain anlegen

Beim Anbieter deiner Domain `vprondzinski.de` einen DNS-Eintrag anlegen:

- Typ **A**, Name **minen**, Wert: **IPv4-Adresse deines Servers**
- (falls der Server IPv6 hat: zusätzlich Typ **AAAA** mit der IPv6-Adresse)

Prüfen (kann ein paar Minuten dauern):

```bash
ping minen.vprondzinski.de
```

## 2. Dateien hochladen

Zuerst die Einstellungen anlegen: `minentimer.env.example` nach `minentimer.env` kopieren und bei `MINENTIMER_KEY` einen langen Zufallswert eintragen. Derselbe Wert kommt beim Bauen der Mod in `minentimer/sync-key.txt`.

Dann auf deinem PC im Ordner `OttonienUtils/minentimer` (Server-Adresse anpassen):

```bash
ssh root@dein-server mkdir -p /root/minentimer-server
scp -r server/. root@dein-server:/root/minentimer-server/
```

## 3. Server installieren und starten

Per SSH auf dem Server:

```bash
cd /root/minentimer-server
mkdir -p /opt/minentimer
cp minentimer-server.py /opt/minentimer/
install -m 600 minentimer.env /etc/minentimer.env
cp minentimer.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now minentimer
systemctl status minentimer
```

Bei `status` sollte **active (running)** stehen. Der Server lauscht nur intern auf `127.0.0.1:8765`; nach außen geht es über HTTPS (Schritt 4). Die Daten liegen in `/var/lib/minentimer/data.json`.

## 4. HTTPS einrichten

Erst nachsehen, ob schon ein Webserver läuft:

```bash
ss -tlnp | grep -E ':(80|443) '
```

**a) Nichts läuft auf 80/443 → Caddy (holt das Zertifikat automatisch):**

```bash
apt install -y caddy
cp Caddyfile /etc/caddy/Caddyfile
systemctl reload caddy
```

Läuft Caddy schon für andere Seiten, die Datei **nicht** überschreiben, sondern den Inhalt von `Caddyfile` unten an `/etc/caddy/Caddyfile` anhängen.

**b) nginx läuft schon:**

```bash
cp nginx-ratelimit.conf /etc/nginx/conf.d/minentimer-ratelimit.conf
cp nginx-minentimer.conf /etc/nginx/sites-available/minentimer
ln -s /etc/nginx/sites-available/minentimer /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
apt install -y certbot python3-certbot-nginx
certbot --nginx -d minen.vprondzinski.de
```

**c) Apache oder etwas anderes:** Weiterleitung von `minen.vprondzinski.de` auf `http://127.0.0.1:8765` einrichten.

Falls die Firewall `ufw` aktiv ist:

```bash
ufw allow 80,443/tcp
```

## 5. Testen

```bash
curl https://minen.vprondzinski.de/health
```

Die Antwort muss `"ok"` sein. Danach im Spiel unter **P → Einstellungen → Mit anderen teilen…** muss **Status: Verbunden** stehen.

## Nützliches

```bash
journalctl -u minentimer -f          # Protokoll live ansehen
systemctl restart minentimer         # neu starten
cp /var/lib/minentimer/data.json ~/  # Sicherung der Timer
```

**Schlüssel ändern:** In `/etc/minentimer.env` und beim Bauen der Mod in `minentimer/sync-key.txt` muss derselbe Schlüssel stehen. Der Schlüssel steckt in jeder fertigen JAR, ist also nicht wirklich geheim. Den eigentlichen Schutz liefern die Prüfungen des Servers und die Lehen-Codes.

## Absicherung nachrüsten (Server läuft schon)

Der Schlüssel steckt in der Mod, also kann ihn jeder mit der JAR auslesen. Ab dieser Server-Version prüft der Server deshalb jeden Eintrag selbst. Gespeichert werden nur gültige Minen und Timer. Abgelehnt werden zu große Einträge, Zeitstempel aus der Zukunft, Löschungen und Daten über 10 MB insgesamt. Dazu kommen eine Bremse in nginx und eine tägliche Sicherung.

Auf deinem PC im Ordner `OttonienUtils/minentimer`:

```bash
scp -r server/. root@dein-server:/root/minentimer-server/
```

(Mit `server/.` landen die Dateien direkt in `/root/minentimer-server` und nicht in einem Unterordner.)

Per SSH auf dem Server:

```bash
cd /root/minentimer-server
cp /var/lib/minentimer/data.json ~/minentimer-data-vorher.json
cp minentimer-server.py /opt/minentimer/
systemctl restart minentimer
cp nginx-ratelimit.conf /etc/nginx/conf.d/minentimer-ratelimit.conf
sed -i 's|^\(\s*\)proxy_pass http://127.0.0.1:8765;|\1limit_req zone=minentimer burst=30 nodelay;\n\1proxy_pass http://127.0.0.1:8765;|' /etc/nginx/sites-available/minentimer
nginx -t && systemctl reload nginx
cp minentimer-backup.cron /etc/cron.d/minentimer-backup
chmod 644 /etc/cron.d/minentimer-backup
```

Den `sed`-Befehl nur **einmal** ausführen, sonst steht die Zeile doppelt drin.

Prüfen:

```bash
systemctl status minentimer
grep -c limit_req /etc/nginx/sites-available/minentimer
curl https://minen.vprondzinski.de/health
```

`active (running)`, eine Zahl ab `1` und `"ok"` heißen: alles läuft.

**Nach Unfug zurückspringen:** Die Sicherungen liegen in `/var/backups/minentimer/` (eine pro Tag, 14 Tage lang). Das Datum im Befehl musst du anpassen:

```bash
systemctl stop minentimer
cp /var/backups/minentimer/data-2026-11-03.json /var/lib/minentimer/data.json
systemctl start minentimer
```
