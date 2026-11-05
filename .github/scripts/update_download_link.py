"""Setzt in der Mod-Tabelle der README den Download-Link einer Mod auf ein neues Release.

Aufruf: python update_download_link.py <tag> <jar-url> [README.md]
Der Tag hat die Form <ordner>-v<version>, z. B. aktivitaeten-v0.1.1. Die Zeile der Mod wird
am Link auf <ordner>/README.md erkannt. Gibt "changed" oder "unchanged" aus.
"""
import re
import sys


def main() -> int:
    tag, url = sys.argv[1], sys.argv[2]
    path = sys.argv[3] if len(sys.argv) > 3 else "README.md"
    m = re.fullmatch(r"([a-z0-9_-]+)-v(\d[\w.\-]*)", tag)
    if not m:
        print(f"Tag {tag!r} hat nicht die Form <mod>-v<version>", file=sys.stderr)
        return 1
    mod, version = m.groups()

    with open(path, "rb") as f:
        text = f.read().decode("utf-8")
    newline = "\r\n" if "\r\n" in text else "\n"
    lines = text.split(newline)

    row = next((i for i, l in enumerate(lines) if l.startswith("| ") and f"]({mod}/README.md)" in l), None)
    if row is None:
        print(f"Keine Tabellenzeile für {mod} gefunden", file=sys.stderr)
        return 1
    cell = f"[⬇️ {version}]({url})"
    new, n = re.subn(r"\[⬇️ [^\]]*\]\([^)]*\)", lambda _: cell, lines[row], count=1)
    if n == 0:
        print(f"Zeile für {mod} hat noch keinen Download-Link", file=sys.stderr)
        return 1
    if new == lines[row]:
        print("unchanged")
        return 0
    lines[row] = new
    with open(path, "wb") as f:
        f.write(newline.join(lines).encode("utf-8"))
    print("changed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
