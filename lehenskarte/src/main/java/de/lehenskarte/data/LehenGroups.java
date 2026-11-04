package de.lehenskarte.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Fasst Lehen beim Rauszoomen zusammen, entlang der Lehnskette aus der API.
 * Stufe 0: jedes Lehen einzeln. Stufe 1: alles unter einem direkten Vasallen des obersten Lehnsherrn
 * (z. B. eine Freiherrschaft mit ihren Vasallen). Stufe 2: der ganze Machtbereich (z. B. Priol).
 * Freie und gesperrte Lehen bleiben immer einzeln.
 */
public final class LehenGroups {
	public static final int STAGES = 3;

	/** Ein zusammengefasster Bereich: root = Lehen des Anführers, members = alle Lehen darin. */
	public record Group(int root, int[] members, double labelX, double labelZ) {
	}

	private final int[][] rootOf;
	private final Group[][] groupOf;

	private LehenGroups(int[][] rootOf, Group[][] groupOf) {
		this.rootOf = rootOf;
		this.groupOf = groupOf;
	}

	/** Lehen des Anführers der Gruppe, in der das Lehen auf dieser Stufe liegt. */
	public int root(int stage, int index) {
		return rootOf[stage][index];
	}

	public Group group(int stage, int index) {
		return groupOf[stage][index];
	}

	private static LehenGeometry cachedGeo;
	private static OttonienData.Snapshot cachedData;
	private static LehenGroups cached;

	public static synchronized LehenGroups of(LehenGeometry geo, OttonienData.Snapshot data) {
		if (geo == cachedGeo && data == cachedData && cached != null) return cached;
		cached = build(geo, data);
		cachedGeo = geo;
		cachedData = data;
		return cached;
	}

	private static LehenGroups build(LehenGeometry geo, OttonienData.Snapshot data) {
		List<LehenGeometry.Shape> shapes = geo.shapes();
		int n = shapes.size();
		int[][] root = new int[STAGES][n];
		for (LehenGeometry.Shape s : shapes) {
			List<Integer> chain = chain(geo, data, s);
			int i = s.index();
			root[0][i] = i;
			root[1][i] = chain.size() >= 2 ? chain.get(chain.size() - 2) : i;
			root[2][i] = chain.get(chain.size() - 1);
		}
		Group[][] groups = new Group[STAGES][n];
		for (int stage = 0; stage < STAGES; stage++) {
			for (int r = 0; r < n; r++) {
				List<Integer> members = new ArrayList<>();
				for (int i = 0; i < n; i++) if (root[stage][i] == r) members.add(i);
				if (members.isEmpty()) continue;
				int[] m = members.stream().mapToInt(Integer::intValue).toArray();
				double[] c = label(geo, m, r);
				Group g = new Group(r, m, c[0], c[1]);
				for (int i : m) groups[stage][i] = g;
			}
		}
		return new LehenGroups(root, groups);
	}

	/** Lehnskette von unten nach oben: [eigenes Lehen, Lehnsherr, dessen Lehnsherr, …]. */
	private static List<Integer> chain(LehenGeometry geo, OttonienData.Snapshot data, LehenGeometry.Shape s) {
		List<Integer> chain = new ArrayList<>();
		chain.add(s.index());
		OttonienData.Faction f = data.owner(s.id());
		while (f != null && !f.lordRegion().isEmpty() && chain.size() < 12) {
			LehenGeometry.Shape lord = geo.byId(f.lordRegion());
			if (lord == null || chain.contains(lord.index())) break;
			chain.add(lord.index());
			f = data.owner(lord.id());
		}
		return chain;
	}

	/**
	 * Platz für das Wappen: der Punkt im Bereich, der am weitesten von dessen Außengrenze entfernt ist
	 * (wie „polylabel“), damit es nie auf einer Grenze oder am Rand sitzt.
	 */
	private static double[] label(LehenGeometry geo, int[] members, int root) {
		boolean[] in = new boolean[geo.shapes().size()];
		for (int m : members) in[m] = true;
		// Außenkanten: Kanten, hinter denen kein Lehen derselben Gruppe liegt
		List<double[]> outer = new ArrayList<>();
		double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (int m : members) {
			LehenGeometry.Shape s = geo.shape(m);
			minX = Math.min(minX, s.minX());
			minZ = Math.min(minZ, s.minZ());
			maxX = Math.max(maxX, s.maxX());
			maxZ = Math.max(maxZ, s.maxZ());
			int[] xs = s.xs(), zs = s.zs();
			for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
				double ax = xs[j], az = zs[j], bx = xs[i], bz = zs[i];
				double len = Math.hypot(bx - ax, bz - az);
				if (len < 0.5) continue;
				double nx = -(bz - az) / len, nz = (bx - ax) / len;
				double mx = (ax + bx) / 2, mz = (az + bz) / 2;
				if (s.contains(mx + nx * 3, mz + nz * 3)) {
					nx = -nx;
					nz = -nz;
				}
				LehenGeometry.Shape other = geo.at(mx + nx * 4, mz + nz * 4);
				if (other == null || !in[other.index()]) outer.add(new double[]{ax, az, bx, bz});
			}
		}
		double step = Math.max(4, Math.max(maxX - minX, maxZ - minZ) / 60);
		double[] best = {geo.shape(root).labelX(), geo.shape(root).labelZ()};
		double bestD = -1;
		for (double x = minX + step / 2; x < maxX; x += step) {
			for (double z = minZ + step / 2; z < maxZ; z += step) {
				double d = clearance(geo, in, outer, x, z, bestD);
				if (d > bestD) {
					bestD = d;
					best = new double[]{x, z};
				}
			}
		}
		// fein nachsuchen um den besten Punkt
		double cx = best[0], cz = best[1];
		for (double x = cx - step; x <= cx + step; x += step / 4) {
			for (double z = cz - step; z <= cz + step; z += step / 4) {
				double d = clearance(geo, in, outer, x, z, bestD);
				if (d > bestD) {
					bestD = d;
					best = new double[]{x, z};
				}
			}
		}
		return best;
	}

	/** Abstand zur nächsten Außenkante, -1 außerhalb der Gruppe. Bricht ab, sobald er nicht mehr über atLeast liegen kann. */
	private static double clearance(LehenGeometry geo, boolean[] in, List<double[]> outer, double x, double z, double atLeast) {
		LehenGeometry.Shape s = geo.at(x, z);
		if (s == null || !in[s.index()]) return -1;
		double min = Double.MAX_VALUE;
		for (double[] e : outer) {
			double d = segDist(x, z, e[0], e[1], e[2], e[3]);
			if (d < min) {
				min = d;
				if (min <= atLeast) return min;
			}
		}
		return min;
	}

	private static double segDist(double px, double pz, double ax, double az, double bx, double bz) {
		double dx = bx - ax, dz = bz - az;
		double t = ((px - ax) * dx + (pz - az) * dz) / (dx * dx + dz * dz);
		t = Math.max(0, Math.min(1, t));
		return Math.hypot(px - ax - t * dx, pz - az - t * dz);
	}
}
