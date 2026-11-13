package de.viehzucht.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Was die Mod über ein Tier weiß: die letzte Info-Nachricht des Servers (Shift + Rechtsklick)
 * und selbst beobachtete Ereignisse (Herzchen, Jungtier geboren, gefüttert).
 */
public final class AnimalInfo {
	/** „1d 16h“, „22m 3s“, „5h“ … wie der Server Zeiten schreibt. */
	private static final Pattern DURATION = Pattern.compile("\\b\\d+[dhms]\\b(?:\\s+\\d+[dhms]\\b)*");
	private static final Pattern PART = Pattern.compile("(\\d+)([dhms])");
	private static final Pattern FRACTION = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
	/** So lange gilt ein Tier nach Herzchen als verliebt. */
	public static final long LOVE_MS = 30_000L;

	public static final class Line {
		public String key;
		public String value;

		public Line(String key, String value) {
			this.key = key;
			this.value = value;
		}
	}

	public String name;
	/** "m", "w" oder null, wenn unbekannt. */
	public String gender;
	/** Wort des Servers, z.B. „Bulle“, „Kuh“, „Bock“. */
	public String genderWord;
	public int mast = -1;
	public int mastMax = -1;
	/** Wann die Info-Nachricht kam (0 = nie). Zeiten in den Zeilen zählen ab hier. */
	public long capturedAt;
	public List<Line> lines = new ArrayList<>();
	/** Herzchen gesehen. */
	public long lovedAt;
	/** Paarung beobachtet (Jungtier daneben geboren). */
	public long bredAt;
	/** Als Jungtier geboren gesehen. */
	public long bornAt;
	/** Ohne Shift angeklickt (z.B. gefüttert), nachdem die Info kam. */
	public long touchedAt;
	/** Selbst geschoren (Schere benutzt und das Schaf war danach geschoren). */
	public long shearedAt;
	/** Zuletzt gesehen, zum Aufräumen alter Einträge. */
	public long seenAt;

	public boolean hasCapture() {
		return capturedAt > 0;
	}

	public Line line(String... keys) {
		for (Line l : lines)
			for (String k : keys)
				if (l.key.equalsIgnoreCase(k)) return l;
		return null;
	}

	// ---------------------------------------------------------------- Einlesen

	/**
	 * Liest die Zeilen zwischen den beiden Strichlinien der Server-Nachricht.
	 * Beispiel: „- Rotvieh Rind“, „⚥  Geschlecht: Kuh (Weiblich)“, „🍽 Mast: 65/80“, „❤ Paarungsbereit: Mast 65/80“.
	 */
	public static AnimalInfo parse(List<String> raw, long now) {
		AnimalInfo info = new AnimalInfo();
		info.capturedAt = now;
		for (String r : raw) {
			String s = r.strip();
			if (s.startsWith("- ") && info.name == null && !s.contains(": ")) {
				info.name = s.substring(2).strip();
				continue;
			}
			int colon = s.indexOf(": ");
			if (colon < 0) continue;
			String key = s.substring(0, colon);
			int start = 0;
			while (start < key.length() && !Character.isLetter(key.charAt(start))) start++;
			key = key.substring(start).strip();
			String value = s.substring(colon + 2).strip();
			if (key.isEmpty()) continue;
			info.lines.add(new Line(key, value));
			String k = key.toLowerCase(Locale.ROOT);
			if (k.equals("geschlecht")) {
				String v = value.toLowerCase(Locale.ROOT);
				info.gender = v.contains("männlich") ? "m" : v.contains("weiblich") ? "w" : null;
				int paren = value.indexOf('(');
				info.genderWord = (paren > 0 ? value.substring(0, paren) : value).strip();
			} else if (k.equals("mast")) {
				Matcher m = FRACTION.matcher(value);
				if (m.find()) {
					info.mast = Integer.parseInt(m.group(1));
					info.mastMax = Integer.parseInt(m.group(2));
				}
			}
		}
		return info.lines.isEmpty() ? null : info;
	}

	/** Beobachtete Werte vom alten Eintrag übernehmen, wenn eine neue Info kommt. */
	public void keepObserved(AnimalInfo old) {
		if (old == null) return;
		lovedAt = old.lovedAt;
		bredAt = old.bredAt;
		bornAt = old.bornAt;
		shearedAt = old.shearedAt;
	}

	/** Auf Ottonien ist ein Schaf 6 Stunden nach dem Scheren wieder scherbar. */
	public static final long SHEAR_MS = 6 * 3_600_000L;

	/**
	 * Wann das selbst geschorene Schaf wieder scherbar ist, 0 wenn es nicht wartet. Nach dem Scheren zählt die Zeit
	 * aus der nächsten Info des Servers („Scherbar: in 5h 59m“), „Bereit“ beendet das Warten, sonst gilt die Tabelle.
	 */
	public long shearReadyAt() {
		if (shearedAt <= 0) return 0;
		Line l = capturedAt > shearedAt ? line("Scherbar") : null;
		if (l != null) {
			long d = parseDuration(l.value);
			if (d >= 0) return capturedAt + d;
			if (l.value.toLowerCase(Locale.ROOT).startsWith("bereit")) return 0;
		}
		return shearedAt + SHEAR_MS;
	}

	/** Die Wartezeit nach dem Scheren ist nur geschätzt, weil noch keine neue Info kam. */
	public boolean shearEstimated() {
		Line l = capturedAt > shearedAt ? line("Scherbar") : null;
		return l == null || parseDuration(l.value) < 0;
	}

	/**
	 * Wann das Tier nach der beobachteten Paarung wieder kann, 0 wenn es nicht wartet. Kam danach eine Info
	 * des Servers, zählt deren Zeit („Paarungsbereit: in 23h 59m | Mast 0/80“); ohne Zeit darin wartet es nicht
	 * mehr. Sonst gilt die Wartezeit aus der Tabelle.
	 */
	public long breedReadyAt(long cooldownMs) {
		if (bredAt <= 0) return 0;
		Line l = capturedAt > bredAt ? line("Paarungsbereit", "Zuchtbereit") : null;
		if (l == null) return bredAt + cooldownMs;
		for (String piece : l.value.split("\\s*\\|\\s*")) {
			if (piece.toLowerCase(Locale.ROOT).contains("wächst")) continue;
			long d = parseDuration(piece);
			if (d > 0) return capturedAt + d;
		}
		return 0;
	}

	// ---------------------------------------------------------------- Zeiten

	public static long parseDuration(String text) {
		Matcher m = DURATION.matcher(text);
		if (!m.find()) return -1;
		long ms = 0;
		Matcher p = PART.matcher(m.group());
		while (p.find()) {
			long n = Long.parseLong(p.group(1));
			ms += switch (p.group(2)) {
				case "d" -> n * 86_400_000L;
				case "h" -> n * 3_600_000L;
				case "m" -> n * 60_000L;
				default -> n * 1000L;
			};
		}
		return ms;
	}

	public static String formatDuration(long ms) {
		long s = Math.max(0, (ms + 999) / 1000);
		long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60, sec = s % 60;
		if (d > 0) return d + "d " + h + "h";
		if (h > 0) return h + "h " + m + "m";
		if (m > 0) return m + "m " + sec + "s";
		return sec + "s";
	}

	/** Ein Stück Text des Servers, mit live heruntergezählter Zeit. */
	public record Part(String text, boolean done, Kind kind) {
	}

	public enum Kind { READY, TIME, MAST, OTHER }

	/** Zerlegt z.B. „wächst noch 1d 16h | Mast 0/80“ und rechnet die Zeit ab der Nachricht weiter. */
	public List<Part> parts(String value, long now) {
		List<Part> out = new ArrayList<>();
		for (String piece : value.split("\\s*\\|\\s*")) {
			if (piece.isBlank()) continue;
			Matcher d = DURATION.matcher(piece);
			if (d.find()) {
				long left = capturedAt + parseDuration(piece) - now;
				String text = piece.substring(0, d.start()) + formatDuration(left) + piece.substring(d.end());
				out.add(new Part(text, left <= 0, Kind.TIME));
				continue;
			}
			Matcher f = FRACTION.matcher(piece);
			if (f.find() && piece.toLowerCase(Locale.ROOT).contains("mast")) {
				out.add(new Part(piece, Integer.parseInt(f.group(1)) >= Integer.parseInt(f.group(2)), Kind.MAST));
				continue;
			}
			String low = piece.toLowerCase(Locale.ROOT);
			boolean ready = low.startsWith("bereit") || low.equals("ja");
			out.add(new Part(piece, ready, ready ? Kind.READY : Kind.OTHER));
		}
		return out;
	}

	// ---------------------------------------------------------------- Zuchtstatus

	public enum State { READY, LOVE, WAIT, GROWING, NEED_MAST, BLOCKED, UNKNOWN }

	public record Status(State state, String text, boolean estimated) {
	}

	public Status breedStatus(long now, Species species, boolean baby) {
		if (lovedAt > 0 && now - lovedAt < LOVE_MS) return new Status(State.LOVE, "verliebt", false);
		if (species != null && bredAt > capturedAt && now - bredAt < species.cooldownMs())
			return new Status(State.WAIT, "wartet noch ~" + formatDuration(bredAt + species.cooldownMs() - now), true);
		if (species != null && baby && bornAt > capturedAt && now - bornAt < species.growMs())
			return new Status(State.GROWING, "wächst noch ~" + formatDuration(bornAt + species.growMs() - now), true);
		Line breed = line("Paarungsbereit", "Zuchtbereit");
		if (breed == null) {
			if (baby) return new Status(State.GROWING, "Jungtier", false);
			return new Status(State.UNKNOWN, "Shift + Rechtsklick für Infos", false);
		}
		List<Part> parts = parts(breed.value, now);
		for (Part p : parts) {
			if (p.done()) continue;
			if (p.kind() == Kind.TIME) {
				State st = p.text().toLowerCase(Locale.ROOT).contains("wächst") ? State.GROWING : State.WAIT;
				return new Status(st, p.text(), false);
			}
		}
		for (Part p : parts) {
			if (p.done()) continue;
			if (p.kind() == Kind.MAST) return new Status(State.NEED_MAST, "braucht " + p.text(), false);
			return new Status(State.BLOCKED, p.text(), false);
		}
		// Baby-Flag vom Server ist sicherer als eine alte Nachricht
		if (baby) return new Status(State.GROWING, "Jungtier", false);
		return new Status(State.READY, "paarungsbereit", false);
	}
}
