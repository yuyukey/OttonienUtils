package de.lehenskarte.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lehenskarte.LehenskarteClient;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Grenzen der Lehen in Weltkoordinaten. Die Polygone stammen aus der Karte auf karte.ottonien.com und sind
 * auf die Mittelpunkte der API umgerechnet. Für schnelle Abfragen liegt alles zusätzlich als Raster
 * mit {@link #CELL} Blöcken Kantenlänge vor.
 */
public final class LehenGeometry {
	public static final int CELL = 2;

	public record Shape(String id, int index, int websiteRgb, int labelX, int labelZ, int[] xs, int[] zs,
						int minX, int minZ, int maxX, int maxZ) {
		public boolean contains(double x, double z) {
			boolean in = false;
			for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
				if ((zs[i] > z) != (zs[j] > z) && x < (double) (xs[j] - xs[i]) * (z - zs[i]) / (zs[j] - zs[i]) + xs[i]) in = !in;
			}
			return in;
		}

		/** Abstand zur nächsten Polygonkante in Blöcken. */
		public double edgeDistance(double x, double z) {
			double best = Double.MAX_VALUE;
			for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
				double ax = xs[j], az = zs[j], bx = xs[i], bz = zs[i];
				double dx = bx - ax, dz = bz - az;
				double len = dx * dx + dz * dz;
				double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / len));
				double px = ax + t * dx - x, pz = az + t * dz - z;
				best = Math.min(best, Math.sqrt(px * px + pz * pz));
			}
			return best;
		}
	}

	private final List<Shape> shapes;
	private final Map<String, Shape> byId;
	private final int gridMinX, gridMinZ, gridW, gridH;
	/** Index+1 des Lehens je Zelle, 0 = kein Lehen. */
	private final byte[] grid;
	private final LongOpenHashSet chunks = new LongOpenHashSet();
	private final Long2ObjectOpenHashMap<int[]> regionShapes = new Long2ObjectOpenHashMap<>();
	public final int offsetX, offsetZ;
	public final String source;

	private LehenGeometry(List<Shape> shapes, int offsetX, int offsetZ, String source) {
		this.shapes = Collections.unmodifiableList(shapes);
		this.offsetX = offsetX;
		this.offsetZ = offsetZ;
		this.source = source;
		Map<String, Shape> map = new HashMap<>();
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (Shape s : shapes) {
			map.put(s.id, s);
			minX = Math.min(minX, s.minX);
			minZ = Math.min(minZ, s.minZ);
			maxX = Math.max(maxX, s.maxX);
			maxZ = Math.max(maxZ, s.maxZ);
		}
		this.byId = map;
		if (shapes.isEmpty()) {
			gridMinX = gridMinZ = gridW = gridH = 0;
			grid = new byte[0];
			return;
		}
		gridMinX = Math.floorDiv(minX, CELL) * CELL - CELL;
		gridMinZ = Math.floorDiv(minZ, CELL) * CELL - CELL;
		gridW = (maxX - gridMinX) / CELL + 3;
		gridH = (maxZ - gridMinZ) / CELL + 3;
		grid = new byte[gridW * gridH];
		rasterize();
	}

	/** Scanline-Füllung aller Polygone. Bei Überschneidungen gewinnt das zuerst gezeichnete Lehen. */
	private void rasterize() {
		double[] cross = new double[64];
		for (Shape s : shapes) {
			int rowFrom = Math.max(0, (s.minZ - gridMinZ) / CELL - 1);
			int rowTo = Math.min(gridH - 1, (s.maxZ - gridMinZ) / CELL + 1);
			for (int row = rowFrom; row <= rowTo; row++) {
				double zc = gridMinZ + row * CELL + CELL / 2.0;
				int n = 0;
				for (int i = 0, j = s.xs.length - 1; i < s.xs.length; j = i++) {
					if ((s.zs[i] > zc) != (s.zs[j] > zc)) {
						if (n == cross.length) cross = Arrays.copyOf(cross, n * 2);
						cross[n++] = s.xs[i] + (zc - s.zs[i]) * (s.xs[j] - s.xs[i]) / (double) (s.zs[j] - s.zs[i]);
					}
				}
				Arrays.sort(cross, 0, n);
				for (int k = 0; k + 1 < n; k += 2) {
					int c0 = (int) Math.ceil((cross[k] - gridMinX) / CELL - 0.5);
					int c1 = (int) Math.floor((cross[k + 1] - gridMinX) / CELL - 0.5);
					for (int c = Math.max(0, c0); c <= Math.min(gridW - 1, c1); c++) {
						int at = row * gridW + c;
						if (grid[at] == 0) {
							grid[at] = (byte) (s.index + 1);
							int bx = gridMinX + c * CELL, bz = gridMinZ + row * CELL;
							chunks.add(key(bx >> 4, bz >> 4));
							long rk = key(bx >> 9, bz >> 9);
							int[] list = regionShapes.get(rk);
							if (list == null) regionShapes.put(rk, new int[]{s.index});
							else if (Arrays.stream(list).noneMatch(v -> v == s.index)) {
								int[] more = Arrays.copyOf(list, list.length + 1);
								more[list.length] = s.index;
								regionShapes.put(rk, more);
							}
						}
					}
				}
			}
		}
	}

	public static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	public List<Shape> shapes() {
		return shapes;
	}

	public Shape byId(String id) {
		return byId.get(id);
	}

	public Shape shape(int index) {
		return index >= 0 && index < shapes.size() ? shapes.get(index) : null;
	}

	/** Index des Lehens an dieser Blockposition oder -1. */
	public int indexAt(int x, int z) {
		return cell(Math.floorDiv(x - gridMinX, CELL), Math.floorDiv(z - gridMinZ, CELL));
	}

	/** Index des Lehens in dieser Rasterzelle oder -1. */
	public int cell(int cx, int cz) {
		if (cx < 0 || cz < 0 || cx >= gridW || cz >= gridH) return -1;
		return (grid[cz * gridW + cx] & 0xFF) - 1;
	}

	public int cellX(int x) {
		return Math.floorDiv(x - gridMinX, CELL);
	}

	public int cellZ(int z) {
		return Math.floorDiv(z - gridMinZ, CELL);
	}

	public Shape at(double x, double z) {
		return shape(indexAt((int) Math.floor(x), (int) Math.floor(z)));
	}

	public boolean chunkHasLehen(int chunkX, int chunkZ) {
		return chunks.contains(key(chunkX, chunkZ));
	}

	/** Lehen, die eine 512er-Region von Xaero berühren, oder null. */
	public int[] regionShapes(int regionX, int regionZ) {
		return regionShapes.get(key(regionX, regionZ));
	}

	// ---------------------------------------------------------------- Laden

	/** Lädt config/lehenskarte/lehen.json, falls vorhanden, sonst die mitgelieferten Grenzen. */
	public static LehenGeometry load(int offsetX, int offsetZ) {
		Path override = FabricLoader.getInstance().getConfigDir().resolve("lehenskarte").resolve("lehen.json");
		if (Files.exists(override)) {
			try (Reader r = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
				return parse(JsonParser.parseReader(r).getAsJsonObject(), offsetX, offsetZ, override.toString());
			} catch (Exception e) {
				LehenskarteClient.LOGGER.warn("Eigene Grenzen in {} fehlerhaft, nehme die mitgelieferten", override, e);
			}
		}
		try (InputStream in = LehenGeometry.class.getResourceAsStream("/assets/lehenskarte/lehen.json")) {
			if (in == null) throw new IllegalStateException("lehen.json fehlt in der Mod");
			try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return parse(JsonParser.parseReader(r).getAsJsonObject(), offsetX, offsetZ, "mitgeliefert");
			}
		} catch (Exception e) {
			LehenskarteClient.LOGGER.error("Konnte Lehensgrenzen nicht laden", e);
			return new LehenGeometry(new ArrayList<>(), offsetX, offsetZ, "leer");
		}
	}

	private static LehenGeometry parse(JsonObject root, int offsetX, int offsetZ, String source) {
		List<Shape> shapes = new ArrayList<>();
		for (JsonElement el : root.getAsJsonArray("regions")) {
			JsonObject o = el.getAsJsonObject();
			JsonArray poly = o.getAsJsonArray("polygon");
			if (poly.size() < 3) continue;
			int[] xs = new int[poly.size()], zs = new int[poly.size()];
			int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
			for (int i = 0; i < poly.size(); i++) {
				JsonArray p = poly.get(i).getAsJsonArray();
				xs[i] = p.get(0).getAsInt() + offsetX;
				zs[i] = p.get(1).getAsInt() + offsetZ;
				minX = Math.min(minX, xs[i]);
				minZ = Math.min(minZ, zs[i]);
				maxX = Math.max(maxX, xs[i]);
				maxZ = Math.max(maxZ, zs[i]);
			}
			int rgb = 0x9A9A9A;
			if (o.has("color")) rgb = Integer.parseInt(o.get("color").getAsString().replace("#", ""), 16);
			int lx = (minX + maxX) / 2, lz = (minZ + maxZ) / 2;
			if (o.has("label")) {
				JsonArray l = o.getAsJsonArray("label");
				lx = l.get(0).getAsInt() + offsetX;
				lz = l.get(1).getAsInt() + offsetZ;
			}
			shapes.add(new Shape(o.get("id").getAsString(), shapes.size(), rgb, lx, lz, xs, zs, minX, minZ, maxX, maxZ));
		}
		return new LehenGeometry(snapNeighbours(shapes), offsetX, offsetZ, source);
	}

	/** Eckpunkte, die bis zu so viele Blöcke neben der Kante eines Nachbarlehens liegen, werden daraufgesetzt. */
	private static final double SNAP = 15;

	/**
	 * Die Polygone der Website teilen sich fast alle Eckpunkte; einige liegen aber ein paar Blöcke daneben.
	 * Das gäbe doppelte Linien und schmale Lücken, darum werden solche Punkte auf die Nachbarkante gezogen.
	 */
	private static List<Shape> snapNeighbours(List<Shape> in) {
		List<int[]> origX = new ArrayList<>(), origZ = new ArrayList<>();
		for (Shape s : in) {
			origX.add(s.xs.clone());
			origZ.add(s.zs.clone());
		}
		List<Shape> out = new ArrayList<>(in.size());
		for (Shape s : in) {
			int[] xs = s.xs.clone(), zs = s.zs.clone();
			for (int v = 0; v < xs.length; v++) {
				double best = SNAP, bx = 0, bz = 0;
				boolean found = false, onEdge = false;
				for (int o = 0; o < in.size(); o++) {
					if (o == s.index) continue;
					Shape other = in.get(o);
					if (xs[v] < other.minX - SNAP || xs[v] > other.maxX + SNAP || zs[v] < other.minZ - SNAP || zs[v] > other.maxZ + SNAP) continue;
					int[] ox = origX.get(o), oz = origZ.get(o);
					for (int i = 0, j = ox.length - 1; i < ox.length; j = i++) {
						double ax = ox[j], az = oz[j], dx = ox[i] - ax, dz = oz[i] - az;
						double len = dx * dx + dz * dz;
						double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((xs[v] - ax) * dx + (zs[v] - az) * dz) / len));
						double px = ax + t * dx, pz = az + t * dz;
						double d = Math.hypot(px - xs[v], pz - zs[v]);
						if (d < 0.5) onEdge = true;
						else if (d < best) {
							best = d;
							bx = px;
							bz = pz;
							found = true;
						}
					}
				}
				// Liegt der Punkt schon auf einer Nachbarkante, bleibt er, wo er ist
				if (found && !onEdge) {
					xs[v] = (int) Math.round(bx);
					zs[v] = (int) Math.round(bz);
				}
			}
			int minX = Arrays.stream(xs).min().orElse(0), maxX = Arrays.stream(xs).max().orElse(0);
			int minZ = Arrays.stream(zs).min().orElse(0), maxZ = Arrays.stream(zs).max().orElse(0);
			out.add(new Shape(s.id, s.index, s.websiteRgb, s.labelX, s.labelZ, xs, zs, minX, minZ, maxX, maxZ));
		}
		return out;
	}
}
