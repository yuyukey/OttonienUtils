#!/usr/bin/env python3
"""Sync-Server für die Minen-Timer-Mod.

Spricht dieselbe kleine REST-Schnittstelle wie eine Firebase Realtime Database:
  GET   /minentimer/<gruppe>.json   -> alle Minen und Timer der Gruppe
  PATCH /minentimer/<gruppe>.json   -> {"mines/<id>": {...}, "timers/<id>": {...}}
Jede Anfrage braucht den Header X-MinenTimer-Key mit dem geheimen Schlüssel.

Weil der Schlüssel in der Mod steckt, prüft der Server jeden Eintrag selbst:
nur gültige Minen und Timer, begrenzte Größe, keine Zeitstempel aus der Zukunft,
keine Löschungen und eine Obergrenze für die gesamte Datenmenge.

Einstellungen über Umgebungsvariablen:
  MINENTIMER_KEY   geheimer Schlüssel (Pflicht)
  MINENTIMER_DATA  Datei für die Daten (Standard: ./minentimer-data.json)
  MINENTIMER_HOST  Adresse zum Lauschen (Standard: 127.0.0.1, also nur hinter Caddy/nginx)
  MINENTIMER_PORT  Port (Standard: 8765)
"""
import hmac
import json
import os
import re
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

KEY = os.environ.get("MINENTIMER_KEY", "")
DATA_FILE = os.environ.get("MINENTIMER_DATA", "minentimer-data.json")
HOST = os.environ.get("MINENTIMER_HOST", "127.0.0.1")
PORT = int(os.environ.get("MINENTIMER_PORT", "8765"))

MAX_BODY = 256 * 1024
MAX_GROUPS = 100
MAX_ENTRIES = 2000
MAX_ENTRY_BYTES = 1024
MAX_DB_BYTES = 10 * 1024 * 1024
# Erlaubte Abweichung der Uhr eines Spielers nach vorne
MAX_CLOCK_AHEAD_MS = 60 * 60 * 1000
MAX_COORD = 30_000_000
MAX_TIMER_MS = 100 * 24 * 60 * 60 * 1000

# Bekannte Felder und ihre Prüfung. Unbekannte kleine Felder bleiben erlaubt,
# damit neuere Mod-Versionen weiter Felder ergänzen können.
def _int(lo, hi):
    return lambda v: isinstance(v, int) and not isinstance(v, bool) and lo <= v <= hi

def _str(n):
    return lambda v: isinstance(v, str) and len(v) <= n

def _bool(v):
    return isinstance(v, bool)

MINE_FIELDS = {
    "id": _str(100),
    "name": _str(64),
    "x": _int(-MAX_COORD, MAX_COORD),
    "y": _int(-4096, 4096),
    "z": _int(-MAX_COORD, MAX_COORD),
    "type": _str(32),
    "size": _str(32),
    "area": _str(64),
    "customMinutes": _int(0, 100_000),
    "updatedAt": _int(0, 2**53),
    "deleted": _bool,
}
TIMER_FIELDS = {
    "readyAt": _int(0, 2**53),
    "startedAt": _int(0, 2**53),
    "updatedAt": _int(0, 2**53),
    "by": _str(32),
}
GROUP_PATH = re.compile(r"^/minentimer/([A-Za-z0-9_\-]{1,64})\.json$")
ENTRY_KEY = re.compile(r"^(mines|timers)/([A-Za-z0-9_\-]{1,100})$")

lock = threading.Lock()
db = {}


def load():
    global db
    try:
        with open(DATA_FILE, encoding="utf-8") as f:
            db = json.load(f)
        if not isinstance(db, dict):
            db = {}
    except FileNotFoundError:
        db = {}


def save(text):
    tmp = DATA_FILE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        f.write(text)
    os.replace(tmp, DATA_FILE)


def valid_entry(section, entry, value, now):
    """Prüft eine Mine bzw. einen Timer. Liefert True, wenn der Eintrag gespeichert werden darf."""
    if not isinstance(value, dict):
        return False
    if len(json.dumps(value, separators=(",", ":"))) > MAX_ENTRY_BYTES:
        return False
    fields = MINE_FIELDS if section == "mines" else TIMER_FIELDS
    for field, check in fields.items():
        if field in value and not check(value[field]):
            return False
    for field, v in value.items():
        if field not in fields and not (v is None or _bool(v) or _int(-2**53, 2**53)(v) or _str(64)(v)):
            return False
    updated = value.get("updatedAt")
    if updated is None or updated > now + MAX_CLOCK_AHEAD_MS:
        return False
    if section == "mines":
        # Die ID muss zum Eintrag passen, sonst könnte man fremde Minen überschreiben
        return value.get("id") == entry and isinstance(value.get("name"), str)
    ready = value.get("readyAt", 0)
    return ready == 0 or ready <= now + MAX_TIMER_MS


class Handler(BaseHTTPRequestHandler):
    server_version = "MinenTimer/1.0"

    def log_message(self, fmt, *args):
        sys.stderr.write("%s %s\n" % (self.command, fmt % args))

    def reply(self, code, body=b"null"):
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def group(self):
        """Prüft Schlüssel und Pfad, liefert den Gruppennamen oder None (Antwort schon gesendet)."""
        if not hmac.compare_digest(self.headers.get("X-MinenTimer-Key", ""), KEY):
            self.reply(401, b'{"error":"Falscher Schluessel"}')
            return None
        m = GROUP_PATH.match(self.path.split("?")[0])
        if not m:
            self.reply(404, b'{"error":"Unbekannter Pfad"}')
            return None
        return m.group(1)

    def do_GET(self):
        if self.path == "/health":
            return self.reply(200, b'"ok"')
        name = self.group()
        if name is None:
            return
        with lock:
            body = json.dumps(db.get(name), separators=(",", ":")).encode()
        self.reply(200, body)

    def do_PATCH(self):
        name = self.group()
        if name is None:
            return
        try:
            length = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            return self.reply(400, b'{"error":"Laenge fehlt"}')
        if length <= 0 or length > MAX_BODY:
            return self.reply(413, b'{"error":"Zu gross"}')
        try:
            data = json.loads(self.rfile.read(length))
        except ValueError:
            return self.reply(400, b'{"error":"Kein JSON"}')
        if not isinstance(data, dict):
            return self.reply(400, b'{"error":"Objekt erwartet"}')
        now = int(time.time() * 1000)
        updates = []
        for key, value in data.items():
            m = ENTRY_KEY.match(key)
            # Ungültige Einträge überspringen, sonst würde die Mod denselben Stapel endlos wiederholen
            if not m or not valid_entry(m.group(1), m.group(2), value, now):
                continue
            updates.append((m.group(1), m.group(2), value))
        if not updates:
            return self.reply(200, b"{}")
        with lock:
            if name not in db and len(db) >= MAX_GROUPS:
                return self.reply(507, b'{"error":"Zu viele Gruppen"}')
            old = db.get(name)
            group = json.loads(json.dumps(old)) if old else {}
            for section, entry, value in updates:
                part = group.setdefault(section, {})
                if entry in part or len(part) < MAX_ENTRIES:
                    part[entry] = value
            db[name] = group
            text = json.dumps(db, separators=(",", ":"))
            if len(text) > MAX_DB_BYTES:
                # Zu viele Daten: Änderung verwerfen
                if old is None:
                    del db[name]
                else:
                    db[name] = old
                return self.reply(507, b'{"error":"Speicher voll"}')
            save(text)
        self.reply(200, b"{}")


def main():
    if len(KEY) < 16:
        sys.exit("MINENTIMER_KEY fehlt oder ist zu kurz (mindestens 16 Zeichen).")
    load()
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print("Minen-Timer-Server läuft auf %s:%d, Daten in %s" % (HOST, PORT, DATA_FILE), flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
