package de.lehenskarte.gui;

import de.lehenskarte.data.LehenGeometry;
import de.lehenskarte.data.LehenGroups;
import de.lehenskarte.data.LehenState;
import de.lehenskarte.data.OttonienData;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

/**
 * Zeichnet die Lehensgrenzen als glatte Linien über Xaero's World Map, in echten Bildschirmpixeln statt in Blöcken.
 * Grenzen zwischen Machtbereichen: kräftige helle Linie mit dunklem Rand. Grenzen innerhalb eines
 * Machtbereichs: dünne helle Linie.
 */
final class BorderRenderer {
	/** Eine Kante eines Lehens in Weltkoordinaten; neighbour = Lehen auf der anderen Seite oder -1, nx/nz zeigt ins Lehen. */
	private record Edge(int shape, int neighbour, double ax, double az, double bx, double bz, double nx, double nz, boolean realm) {
	}

	private static LehenGeometry cachedGeo;
	private static OttonienData.Snapshot cachedData;
	private static List<Edge> edges = List.of();

	private BorderRenderer() {
	}

	/** Kanten nur neu einteilen, wenn sich Grenzen oder Besitzverhältnisse geändert haben. */
	private static List<Edge> edges(LehenState.Style st) {
		if (st.geometry() == cachedGeo && st.data() == cachedData) return edges;
		LehenGeometry geo = st.geometry();
		List<Edge> list = new ArrayList<>();
		for (LehenGeometry.Shape s : geo.shapes()) {
			int[] xs = s.xs(), zs = s.zs();
			for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
				double ax = xs[j], az = zs[j], bx = xs[i], bz = zs[i];
				double dx = bx - ax, dz = bz - az, len = Math.hypot(dx, dz);
				if (len < 0.5) continue;
				double nx = -dz / len, nz = dx / len;
				double mx = (ax + bx) / 2, mz = (az + bz) / 2;
				if (!s.contains(mx + nx * 3, mz + nz * 3)) {
					nx = -nx;
					nz = -nz;
				}
				int neighbour = neighbourAt(geo, s, mx - nx * 4, mz - nz * 4);
				boolean realm = neighbour < 0 || st.realm()[neighbour] != st.realm()[s.index()];
				list.add(new Edge(s.index(), neighbour, ax, az, bx, bz, nx, nz, realm));
			}
		}
		cachedGeo = geo;
		cachedData = st.data();
		edges = list;
		return list;
	}

	private static int neighbourAt(LehenGeometry geo, LehenGeometry.Shape self, double x, double z) {
		for (LehenGeometry.Shape o : geo.shapes()) {
			if (o == self || x < o.minX() || x > o.maxX() || z < o.minZ() || z > o.maxZ()) continue;
			if (o.contains(x, z)) return o.index();
		}
		return -1;
	}

	/** Bildschirm in echten Pixeln: pro = Pixel pro Block, cx/cy = Bildschirmmitte, camX/camZ = Kartenmitte. */
	record View(double camX, double camZ, double pxPerBlock, double halfW, double halfH, double w, double h) {
		double x(double worldX) {
			return (worldX - camX) * pxPerBlock + halfW;
		}

		double y(double worldZ) {
			return (worldZ - camZ) * pxPerBlock + halfH;
		}
	}

	/**
	 * Zeichnet jede Grenze genau einmal (gemeinsame Kanten nur vom Lehen mit der kleineren Nummer), als
	 * durchgehende Linienzüge mit sauberen Ecken statt einzelner, sich überlappender Striche.
	 * Grenzen zwischen Machtbereichen immer, Grenzen innerhalb nur dort, wo auf dieser Zoomstufe zwei
	 * verschiedene Gruppen aneinanderstoßen.
	 */
	static void drawAll(GuiGraphics g, LehenState.Style st, LehenGroups groups, float[] weights, View v, double guiScale) {
		List<Edge> all = edges(st);
		float realmW = (float) Math.max(2, guiScale * 1.2);
		float strip = (float) Math.max(1.5, guiScale);
		Quads bands = new Quads(g, guiScale), halos = new Quads(g, guiScale), lines = new Quads(g, guiScale);

		// Farbstreifen: jedes Lehen an seinen Machtbereichsgrenzen nach innen, in seiner eigenen Farbe
		forRuns(all, e -> e.realm ? 1 : 0, (run, key) -> {
			int rgb = st.baseRgb()[run.get(0).shape];
			if (rgb == LehenState.FREE_RGB || rgb == LehenState.LOCKED_RGB) return;
			for (int i = 0; i < BAND_ALPHA.length; i++) {
				float o = realmW / 2 + 1 + strip * i;
				bands.strip(run, v, o, o + strip, true, (BAND_ALPHA[i] << 24) | rgb);
			}
		});

		// Linien
		forRuns(all, e -> {
			if (e.neighbour >= 0 && e.neighbour < e.shape) return 0;
			if (e.realm) return 1;
			boolean diff1 = groups.root(1, e.shape) != groups.root(1, e.neighbour);
			boolean diff2 = groups.root(2, e.shape) != groups.root(2, e.neighbour);
			float alpha = diff2 ? 1f : diff1 ? weights[0] + weights[1] : weights[0];
			int a = Math.round(0x70 * alpha);
			return a < 6 ? 0 : 2 + a;
		}, (run, key) -> {
			if (key == 1) {
				halos.strip(run, v, -realmW / 2 - 1, realmW / 2 + 1, false, 0x50000000);
				lines.strip(run, v, -realmW / 2, realmW / 2, false, 0xD8F4EEDC);
			} else {
				lines.strip(run, v, -0.5f, 0.5f, false, ((key - 2) << 24) | 0xF4EEDC);
			}
		});
		bands.submit();
		halos.submit();
		lines.submit();
	}

	private static final int[] BAND_ALPHA = {0x70, 0x40, 0x1C};

	/** Außenumriss einer Gruppe von Lehen (Auswahl, Maus beim Rauszoomen). */
	static void groupOutline(GuiGraphics g, LehenState.Style st, LehenGroups.Group group, View v, double guiScale, float width, int color) {
		Quads q = new Quads(g, guiScale);
		forRuns(edges(st), e -> contains(group, e.shape) && (e.neighbour < 0 || !contains(group, e.neighbour)) ? 1 : 0,
				(run, key) -> q.strip(run, v, -width / 2, width / 2, false, color));
		q.submit();
	}

	private static boolean contains(LehenGroups.Group group, int index) {
		for (int m : group.members()) if (m == index) return true;
		return false;
	}

	/**
	 * Fasst aufeinanderfolgende Kanten desselben Lehens mit gleichem Schlüssel zu Linienzügen zusammen
	 * (Schlüssel 0 = nicht zeichnen). Ein ganz umlaufender Zug wird geschlossen.
	 */
	private static void forRuns(List<Edge> all, ToIntFunction<Edge> key, BiConsumer<List<Edge>, Integer> out) {
		int i = 0;
		while (i < all.size()) {
			int shape = all.get(i).shape, end = i;
			while (end < all.size() && all.get(end).shape == shape) end++;
			List<Edge> ring = all.subList(i, end);
			int n = ring.size();
			int[] keys = new int[n];
			for (int k = 0; k < n; k++) keys[k] = key.applyAsInt(ring.get(k));
			// an einer Stelle beginnen, wo der Schlüssel wechselt, damit kein Zug über den Anfang hinweg zerteilt wird
			int start = 0;
			for (int k = 0; k < n; k++) {
				if (keys[k] != keys[(k + n - 1) % n] || !connected(ring.get((k + n - 1) % n), ring.get(k))) {
					start = k;
					break;
				}
			}
			List<Edge> run = new ArrayList<>();
			int runKey = 0;
			for (int c = 0; c < n; c++) {
				int k = (start + c) % n;
				Edge e = ring.get(k);
				if (!run.isEmpty() && (keys[k] != runKey || !connected(run.get(run.size() - 1), e))) {
					if (runKey != 0) out.accept(run, runKey);
					run = new ArrayList<>();
				}
				run.add(e);
				runKey = keys[k];
			}
			if (!run.isEmpty() && runKey != 0) out.accept(run, runKey);
			i = end;
		}
	}

	private static boolean connected(Edge a, Edge b) {
		return Math.abs(a.bx - b.ax) < 0.01 && Math.abs(a.bz - b.az) < 0.01;
	}

	/** Sammelt Vierecke in echten Bildschirmpixeln und reicht sie als ein Element an Minecrafts GUI-Zeichnung weiter. */
	private static final class Quads implements GuiElementRenderState {
		private final GuiGraphics g;
		private final Matrix3x2f pose;
		private float[] xy = new float[256];
		private int[] colors = new int[32];
		private int count;
		private float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

		Quads(GuiGraphics g, double guiScale) {
			this.g = g;
			this.pose = new Matrix3x2f(g.pose()).scale((float) (1 / guiScale), (float) (1 / guiScale));
		}

		/**
		 * Band entlang eines Linienzugs zwischen den Abständen o1 und o2 (in Pixeln). inward: positiv ins Lehen,
		 * sonst positiv nach links. Ecken werden auf Gehrung geschnitten, so dass nichts doppelt gezeichnet wird.
		 */
		void strip(List<Edge> run, View v, float o1, float o2, boolean inward, int color) {
			int n = run.size();
			boolean closed = n > 2 && connected(run.get(n - 1), run.get(0));
			int pts = closed ? n : n + 1;
			double[] px = new double[pts], py = new double[pts];
			for (int k = 0; k < n; k++) {
				px[k] = v.x(run.get(k).ax);
				py[k] = v.y(run.get(k).az);
			}
			if (!closed) {
				px[n] = v.x(run.get(n - 1).bx);
				py[n] = v.y(run.get(n - 1).bz);
			}
			// Normalen der Teilstücke (links der Laufrichtung), bei inward ins Lehen gedreht
			double[] nx = new double[n], ny = new double[n];
			for (int k = 0; k < n; k++) {
				int k2 = (k + 1) % pts;
				double dx = px[k2] - px[k], dy = py[k2] - py[k], len = Math.max(1e-6, Math.hypot(dx, dy));
				nx[k] = -dy / len;
				ny[k] = dx / len;
				if (inward && nx[k] * run.get(k).nx + ny[k] * run.get(k).nz < 0) {
					nx[k] = -nx[k];
					ny[k] = -ny[k];
				}
			}
			// Gehrung an jedem Punkt
			double[] mx = new double[pts], my = new double[pts];
			for (int k = 0; k < pts; k++) {
				int next = closed ? k % n : Math.min(k, n - 1);
				int prev = closed ? (k + n - 1) % n : Math.max(k - 1, 0);
				double ax = nx[prev] + nx[next], ay = ny[prev] + ny[next];
				double len = Math.hypot(ax, ay);
				if (len < 1e-6) {
					ax = nx[next];
					ay = ny[next];
					len = 1;
				}
				ax /= len;
				ay /= len;
				double scale = 1 / Math.max(0.35, ax * nx[next] + ay * ny[next]);
				mx[k] = ax * scale;
				my[k] = ay * scale;
			}
			for (int k = 0; k < n; k++) {
				int k2 = (k + 1) % pts;
				quad((float) (px[k] + mx[k] * o1), (float) (py[k] + my[k] * o1),
						(float) (px[k] + mx[k] * o2), (float) (py[k] + my[k] * o2),
						(float) (px[k2] + mx[k2] * o2), (float) (py[k2] + my[k2] * o2),
						(float) (px[k2] + mx[k2] * o1), (float) (py[k2] + my[k2] * o1), color);
			}
		}

		private void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
			if (count * 8 + 8 > xy.length) xy = Arrays.copyOf(xy, xy.length * 2);
			if (count + 1 > colors.length) colors = Arrays.copyOf(colors, colors.length * 2);
			float[] q = {x0, y0, x1, y1, x2, y2, x3, y3};
			System.arraycopy(q, 0, xy, count * 8, 8);
			colors[count++] = color;
			for (int i = 0; i < 8; i += 2) {
				minX = Math.min(minX, q[i]);
				maxX = Math.max(maxX, q[i]);
				minY = Math.min(minY, q[i + 1]);
				maxY = Math.max(maxY, q[i + 1]);
			}
		}

		void submit() {
			if (count > 0) GuiAccess.state(g).submitGuiElement(this);
		}

		@Override
		public void buildVertices(VertexConsumer vc) {
			for (int q = 0; q < count; q++) {
				int c = colors[q];
				for (int i = 0; i < 4; i++) vc.addVertexWith2DPose(pose, xy[q * 8 + i * 2], xy[q * 8 + i * 2 + 1]).setColor(c);
			}
		}

		@Override
		public RenderPipeline pipeline() {
			return RenderPipelines.GUI;
		}

		@Override
		public TextureSetup textureSetup() {
			return TextureSetup.noTexture();
		}

		@Override
		public ScreenRectangle scissorArea() {
			return null;
		}

		@Override
		public ScreenRectangle bounds() {
			Vector2f a = pose.transformPosition(minX, minY, new Vector2f());
			Vector2f b = pose.transformPosition(maxX, maxY, new Vector2f());
			int x0 = (int) Math.floor(Math.max(-10000, Math.min(a.x, b.x))), y0 = (int) Math.floor(Math.max(-10000, Math.min(a.y, b.y)));
			int x1 = (int) Math.ceil(Math.min(20000, Math.max(a.x, b.x))), y1 = (int) Math.ceil(Math.min(20000, Math.max(a.y, b.y)));
			return new ScreenRectangle(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
		}
	}

	/** Füllt ein Lehen zeilenweise in echten Pixeln (Scanline). */
	static void fill(GuiGraphics g, LehenGeometry.Shape s, View v, double guiScale, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.scale((float) (1 / guiScale), (float) (1 / guiScale));
		int[] xs = s.xs(), zs = s.zs();
		int from = (int) Math.max(0, Math.floor(v.y(s.minZ())));
		int to = (int) Math.min(v.h - 1, Math.ceil(v.y(s.maxZ())));
		double[] cross = new double[xs.length];
		for (int y = from; y <= to; y++) {
			double wz = v.camZ + (y + 0.5 - v.halfH) / v.pxPerBlock;
			int n = 0;
			for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
				if ((zs[i] > wz) != (zs[j] > wz)) cross[n++] = xs[i] + (wz - zs[i]) * (xs[j] - xs[i]) / (double) (zs[j] - zs[i]);
			}
			java.util.Arrays.sort(cross, 0, n);
			for (int k = 0; k + 1 < n; k += 2) {
				int x0 = (int) Math.max(0, Math.round(v.x(cross[k])));
				int x1 = (int) Math.min(v.w, Math.round(v.x(cross[k + 1])));
				if (x1 > x0) g.fill(x0, y, x1, y + 1, color);
			}
		}
		pose.popMatrix();
	}

	/** Linie als gedrehtes Rechteck; die Enden stehen um die halbe Breite über, damit Ecken geschlossen sind. */
	private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float width, int color) {
		float dx = x1 - x0, dy = y1 - y0;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 0.01f) return;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x0, y0);
		pose.rotate((float) Math.atan2(dy, dx));
		pose.translate(-width / 2, -width / 2);
		pose.scale(len + width, width);
		g.fill(0, 0, 1, 1, color);
		pose.popMatrix();
	}

	/** Liang-Barsky: schneidet eine Strecke auf ein Rechteck zu, null wenn ganz außerhalb. */
	private static double[] clip(double x0, double y0, double x1, double y1, double minX, double minY, double maxX, double maxY) {
		double t0 = 0, t1 = 1, dx = x1 - x0, dy = y1 - y0;
		double[] p = {-dx, dx, -dy, dy}, q = {x0 - minX, maxX - x0, y0 - minY, maxY - y0};
		for (int i = 0; i < 4; i++) {
			if (p[i] == 0) {
				if (q[i] < 0) return null;
			} else {
				double r = q[i] / p[i];
				if (p[i] < 0) t0 = Math.max(t0, r);
				else t1 = Math.min(t1, r);
				if (t0 > t1) return null;
			}
		}
		return new double[]{x0 + t0 * dx, y0 + t0 * dy, x0 + t1 * dx, y0 + t1 * dy};
	}
}
