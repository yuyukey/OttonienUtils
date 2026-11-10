package de.lehenskarte.xaero;

import com.mojang.blaze3d.vertex.VertexConsumer;
import de.lehenskarte.Config;
import de.lehenskarte.data.LehenState;
import de.lehenskarte.gui.BorderRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import xaero.common.graphics.CustomRenderTypes;
import xaero.lib.XaeroLib;

/**
 * Zeichnet die Lehensgrenzen in Xaero's Minimap, an derselben Stelle wie Xaero sein Chunk-Gitter: in den noch
 * ungedrehten Framebuffer, in Blöcken relativ zur abgerundeten Spielerposition. Drehen und Zoomen übernimmt
 * Xaero. Nötig, weil die Minimap bei installierter World Map deren Kacheln zeigt, und dort gibt es nur die Flächen.
 * Die Striche sind Vierecke im Puffer für Chunk-Markierungen, Xaeros Linien-Shader zeichnet hier nichts.
 */
public final class MinimapBorders {
	private static final float R = 0xF4 / 255f, G = 0xEE / 255f, B = 0xDC / 255f;

	private MinimapBorders() {
	}

	public static void render(Matrix4f pose, Vec3 pos, double zoom) {
		Config c = Config.get();
		if (!c.overlayEnabled || !c.minimapBorders || !LehenState.isActive() || c.borderOpacity <= 0) return;
		// wie Xaero: bei Zoom 0,5 auf gerade Blöcke abrunden
		int px = Mth.floor(pos.x), pz = Mth.floor(pos.z);
		if (zoom == 0.5) {
			px = px >> 1 << 1;
			pz = pz >> 1 << 1;
		}
		// Framebuffer ist 512 Pixel breit, gedreht reicht die Karte bis in die Ecken
		double r = 256 / zoom * 1.5 + 16;
		float a = c.borderOpacity / 100f;
		// Breiten in Pixeln des Framebuffers, damit die Striche bei jedem Zoom gleich dick bleiben
		float px1 = (float) (1 / zoom);
		VertexConsumer buf = XaeroLib.INSTANCE.getClient().getBufferProvider().getBuffer(CustomRenderTypes.MAP_CHUNK_OVERLAY);
		LehenState.Style st = LehenState.style();
		int ox = px, oz = pz;
		// erst die dunklen Ränder, dann die hellen Linien darüber, damit sich Ecken sauber treffen
		BorderRenderer.forEachEdge(st, px - r, pz - r, px + r, pz + r, (ax, az, bx, bz, realm) -> {
			if (realm) quad(pose, buf, ax - ox, az - oz, bx - ox, bz - oz, 2.2f * px1, 0, 0, 0, 0.45f * a);
		});
		BorderRenderer.forEachEdge(st, px - r, pz - r, px + r, pz + r, (ax, az, bx, bz, realm) -> {
			if (realm) quad(pose, buf, ax - ox, az - oz, bx - ox, bz - oz, 1.1f * px1, R, G, B, 0.95f * a);
			else quad(pose, buf, ax - ox, az - oz, bx - ox, bz - oz, 0.5f * px1, R, G, B, 0.55f * a);
		});
	}

	/** Strich als Viereck, an den Enden um die halbe Breite verlängert, damit Ecken geschlossen sind. */
	private static void quad(Matrix4f m, VertexConsumer buf, double x0, double z0, double x1, double z1, float half,
							 float r, float g, float b, float a) {
		double dx = x1 - x0, dz = z1 - z0, len = Math.hypot(dx, dz);
		if (len < 1e-6) return;
		double ux = dx / len * half, uz = dz / len * half;
		x0 -= ux;
		z0 -= uz;
		x1 += ux;
		z1 += uz;
		// Normale so wählen, dass die Ecken wie bei Xaeros Rechtecken umlaufen
		float nx = (float) -uz, nz = (float) ux;
		vertex(buf, m, x0 + nx, z0 + nz, r, g, b, a);
		vertex(buf, m, x1 + nx, z1 + nz, r, g, b, a);
		vertex(buf, m, x1 - nx, z1 - nz, r, g, b, a);
		vertex(buf, m, x0 - nx, z0 - nz, r, g, b, a);
	}

	private static void vertex(VertexConsumer buf, Matrix4f m, double x, double z, float r, float g, float b, float a) {
		buf.addVertex(m, (float) x, (float) z, 0).setColor(r, g, b, a);
	}
}
