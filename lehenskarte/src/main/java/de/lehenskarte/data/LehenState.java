package de.lehenskarte.data;

import de.lehenskarte.Config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Zentraler Zustand: Geometrie, Live-Daten und die daraus berechneten Farben je Lehen.
 * Xaero fragt die Farben aus eigenen Threads ab, darum ist alles unveränderlich und wird nur als Ganzes ersetzt.
 */
public final class LehenState {
	/** Grau für Lehen ohne Gefolge und gesperrte Lehen. */
	public static final int FREE_RGB = 0xA3A3A3;
	public static final int LOCKED_RGB = 0x5E5E5E;

	public record Style(LehenGeometry geometry, OttonienData.Snapshot data, boolean active, int selected,
						int[] baseRgb, int[] realm, int[] fill, int[] lehenBorder, int[] realmBorder,
						int dataHash) {
	}

	private static volatile Style style = build(LehenGeometry.load(0, 0), OttonienData.Snapshot.EMPTY, false, -1);
	private static volatile boolean active;
	private static volatile int selected = -1;

	private LehenState() {
	}

	public static Style style() {
		return style;
	}

	public static LehenGeometry geometry() {
		return style.geometry;
	}

	public static boolean isActive() {
		return active;
	}

	public static int selected() {
		return selected;
	}

	public static synchronized void reloadGeometry() {
		Config c = Config.get();
		style = build(LehenGeometry.load(c.offsetX, c.offsetZ), OttonienData.get(), active, selected);
	}

	public static synchronized void rebuild() {
		style = build(style.geometry, OttonienData.get(), active, selected);
	}

	/** Gibt true zurück, wenn sich etwas geändert hat. */
	public static synchronized boolean setActive(boolean a) {
		if (a == active) return false;
		active = a;
		rebuild();
		return true;
	}

	public static synchronized boolean select(int index) {
		if (index == selected) return false;
		selected = index;
		Style s = style;
		style = new Style(s.geometry, s.data, s.active, index, s.baseRgb, s.realm, s.fill, s.lehenBorder, s.realmBorder,
				s.dataHash);
		return true;
	}

	private static Style build(LehenGeometry geo, OttonienData.Snapshot data, boolean active, int selected) {
		int n = geo.shapes().size();
		int[] base = new int[n], realm = new int[n], fill = new int[n], lb = new int[n], rb = new int[n];
		Map<String, Integer> realmIds = new HashMap<>();
		Config cfg = Config.get();
		int fillA = clampAlpha(cfg.fillOpacity), borderA = clampAlpha(cfg.borderOpacity);
		for (LehenGeometry.Shape s : geo.shapes()) {
			int i = s.index();
			OttonienData.Region region = data.region(s.id());
			OttonienData.Faction owner = data.owner(s.id());
			int rgb;
			String realmKey;
			if (owner != null) {
				LehenGeometry.Shape realmShape = geo.byId(owner.realmRegion());
				rgb = realmShape != null ? realmShape.websiteRgb() : s.websiteRgb();
				realmKey = owner.realmRegion();
			} else if (region != null && !region.enabled()) {
				rgb = LOCKED_RGB;
				realmKey = "#gesperrt";
			} else {
				rgb = FREE_RGB;
				realmKey = "#frei";
			}
			base[i] = rgb;
			realm[i] = realmIds.computeIfAbsent(realmKey, k -> realmIds.size());
			fill[i] = pack(rgb, fillA);
			lb[i] = pack(shade(rgb, 0.85), borderA * 2 / 3);
			rb[i] = pack(shade(rgb, 0.60), borderA);
		}
		int hash = Arrays.hashCode(fill) * 31 + Arrays.hashCode(rb) * 17 + Arrays.hashCode(realm) + (active ? 1 : 0)
				+ geo.offsetX * 7919 + geo.offsetZ * 104729;
		return new Style(geo, data, active, selected, base, realm, fill, lb, rb, hash);
	}

	private static int clampAlpha(int percent) {
		return Math.max(0, Math.min(255, percent * 255 / 100));
	}

	/** Farbformat von Xaeros Highlightern (wie bei Open Parties and Claims): B,G,R,A von oben nach unten. */
	public static int pack(int rgb, int alpha) {
		return ((rgb & 255) << 24) | (((rgb >> 8) & 255) << 16) | (((rgb >> 16) & 255) << 8) | (alpha & 255);
	}

	public static int shade(int rgb, double f) {
		int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
		return (r << 16) | (g << 8) | b;
	}

	public static int mix(int a, int b, double t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}

	// ---------------------------------------------------------------- Farben für Xaero

	/**
	 * Hash einer 512er-Region, ändert sich nur bei neuen Daten. Die Auswahl zeichnet {@code MapOverlay} direkt
	 * auf den Bildschirm, damit Xaero beim Klicken keine Kacheln neu berechnen muss.
	 */
	public static int regionHash(int regionX, int regionZ) {
		Style s = style;
		return s.active ? s.dataHash : 0;
	}

	public static boolean regionHasLehen(int regionX, int regionZ) {
		Style s = style;
		return s.active && s.geometry.regionShapes(regionX, regionZ) != null;
	}

	public static boolean chunkHasLehen(int chunkX, int chunkZ) {
		Style s = style;
		return s.active && s.geometry.chunkHasLehen(chunkX, chunkZ);
	}

	/**
	 * Füllt out (16×16, Index z*16+x) mit den Farben des Chunks. Gibt false zurück, wenn nichts zu zeichnen ist.
	 * edges: Grenzen als Blockstreifen einfärben (Minimap). Die World Map bekommt nur Flächen, ihre Grenzen
	 * zeichnet {@code MapOverlay} als glatte Linien.
	 */
	public static boolean chunkColors(int chunkX, int chunkZ, int[] out, boolean edges) {
		Style s = style;
		if (!s.active || !s.geometry.chunkHasLehen(chunkX, chunkZ)) return false;
		int bx = chunkX << 4, bz = chunkZ << 4;
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				out[(z << 4) | x] = edges ? blockColor(s, bx + x, bz + z) : fillColor(s, bx + x, bz + z);
			}
		}
		return true;
	}

	/** Nur die Fläche, an Grenzen blockgenau per Punkt-im-Polygon statt im 2er-Raster. */
	private static int fillColor(Style s, int x, int z) {
		LehenGeometry g = s.geometry;
		int cx = g.cellX(x), cz = g.cellZ(z);
		int i = g.cell(cx, cz);
		int a = g.cell(cx - 1, cz), b = g.cell(cx + 1, cz), c = g.cell(cx, cz - 1), d = g.cell(cx, cz + 1);
		if (a == i && b == i && c == i && d == i) return i < 0 ? 0 : s.fill[i];
		double px = x + 0.5, pz = z + 0.5;
		for (int n : new int[]{i, a, b, c, d}) {
			LehenGeometry.Shape shape = g.shape(n);
			if (shape != null && shape.contains(px, pz)) return s.fill[n];
		}
		return 0;
	}

	private static int blockColor(Style s, int x, int z) {
		LehenGeometry g = s.geometry;
		int cx = g.cellX(x), cz = g.cellZ(z);
		int i = g.cell(cx, cz);
		if (i < 0) return 0;
		boolean realmEdge = false, lehenEdge = false;
		search:
		for (int dz = -2; dz <= 2; dz++) {
			for (int dx = -2; dx <= 2; dx++) {
				if (dx == 0 && dz == 0) continue;
				int n = g.cell(cx + dx, cz + dz);
				if (n == i) continue;
				if (n < 0 || s.realm[n] != s.realm[i]) {
					realmEdge = true;
					break search;
				}
				if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) lehenEdge = true;
			}
		}
		if (realmEdge) return s.realmBorder[i];
		return lehenEdge ? s.lehenBorder[i] : s.fill[i];
	}
}
