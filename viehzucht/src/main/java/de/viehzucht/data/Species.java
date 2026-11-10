package de.viehzucht.data;

import java.util.Locale;
import java.util.Map;

/**
 * Ottoniens Zuchtwerte je Tierart (aus der Tabelle des Servers). Damit kann die Mod schon vor dem
 * ersten Shift-Rechtsklick sagen, was ein Tier braucht, und nach einer Paarung die Wartezeit schätzen.
 */
public record Species(String name, long cooldownMs, long growMs, int breedMast) {
	private static final long M = 60_000L, H = 60 * M;

	private static final Map<String, Species> ALL = Map.ofEntries(
			e(new Species("Brakel", 6 * H, 12 * H, 20)),
			e(new Species("Graue Bergziege", 12 * H, 24 * H, 30)),
			e(new Species("Zaupel", 12 * H, 24 * H, 30)),
			e(new Species("Hausschwein", 18 * H, 36 * H, 50)),
			e(new Species("Rotvieh Rind", 24 * H, 48 * H, 80)),
			e(new Species("Ackergaul", 24 * H, 48 * H, 100)),
			e(new Species("Reitpferd", 24 * H, 48 * H, 100)),
			e(new Species("Streitross", 24 * H, 48 * H, 100)),
			e(new Species("Packesel", 24 * H, 48 * H, 100)),
			e(new Species("Merciapferd", 2 * H, 6 * H, 30)),
			e(new Species("Standardpferd", 40 * M, 20 * M, 7)));

	private static Map.Entry<String, Species> e(Species s) {
		return Map.entry(key(s.name), s);
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT).replace('-', ' ').replaceAll("\\s+", " ").trim();
	}

	/** Art nach dem Namen über dem Tier („Rotvieh Rind“, „Rotvieh-Rind“ …), sonst null. */
	public static Species byName(String name) {
		if (name == null) return null;
		return ALL.get(key(name));
	}
}
