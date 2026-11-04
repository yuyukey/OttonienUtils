package de.lehenskarte.gui;

import de.lehenskarte.Config;
import de.lehenskarte.data.Banners;
import de.lehenskarte.data.LehenGeometry;
import de.lehenskarte.data.LehenState;
import de.lehenskarte.data.OttonienData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Zeichnet Banner, Namen und das Infofeld über Xaero's World Map (in GUI-Koordinaten). */
public final class MapOverlay {
	private static final int BANNER = 18;
	private static final int PANEL_W = 230;

	private static int panelX, panelY, panelW, panelH;

	private MapOverlay() {
	}

	private static boolean enabled() {
		Minecraft mc = Minecraft.getInstance();
		return LehenState.isActive() && mc.level != null && mc.level.dimension() == Level.OVERWORLD;
	}

	private static double framebufferHalfW() {
		return Minecraft.getInstance().getWindow().getWidth() / 2.0;
	}

	private static double framebufferHalfH() {
		return Minecraft.getInstance().getWindow().getHeight() / 2.0;
	}

	private static double toGuiX(double worldX, double camX, double scale, double screenScale) {
		return ((worldX - camX) * scale + framebufferHalfW()) / screenScale;
	}

	private static double toGuiY(double worldZ, double camZ, double scale, double screenScale) {
		return ((worldZ - camZ) * scale + framebufferHalfH()) / screenScale;
	}

	private static double toWorldX(double guiX, double camX, double scale, double screenScale) {
		return camX + (guiX * screenScale - framebufferHalfW()) / scale;
	}

	private static double toWorldZ(double guiY, double camZ, double scale, double screenScale) {
		return camZ + (guiY * screenScale - framebufferHalfH()) / scale;
	}

	public static void render(GuiGraphics g, int mouseX, int mouseY, double camX, double camZ, double scale, double screenScale) {
		panelW = 0;
		if (!enabled() || scale <= 0 || screenScale <= 0) return;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		LehenState.Style st = LehenState.style();
		LehenGeometry geo = st.geometry();
		OttonienData.Snapshot data = st.data();

		// Auswahl und Lehen unter der Maus sofort hervorheben, ohne dass Xaero Kacheln neu berechnen muss
		LehenGeometry.Shape hovered = geo.at(toWorldX(mouseX, camX, scale, screenScale), toWorldZ(mouseY, camZ, scale, screenScale));
		LehenGeometry.Shape sel = geo.shape(st.selected());
		if (sel != null) {
			fillShape(g, sel, camX, camZ, scale, screenScale, 0x38FFFFFF);
			outlineShape(g, sel, camX, camZ, scale, screenScale, 0xF0FFFFFF, 1);
		}
		if (hovered != null && hovered != sel) outlineShape(g, hovered, camX, camZ, scale, screenScale, 0x90FFFFFF, 0);

		// Banner: nur, wenn die Lehen groß genug auf dem Bildschirm sind
		double guiPerBlock = scale / screenScale;
		if (Config.get().bannersOnMap && guiPerBlock * 350 >= BANNER + 4) {
			int w = g.guiWidth(), h = g.guiHeight();
			for (LehenGeometry.Shape s : geo.shapes()) {
				int x = (int) Math.round(toGuiX(s.labelX(), camX, scale, screenScale));
				int y = (int) Math.round(toGuiY(s.labelZ(), camZ, scale, screenScale));
				if (x < -BANNER || y < -BANNER || x > w + BANNER || y > h + BANNER) continue;
				OttonienData.Faction f = data.owner(s.id());
				if (f == null) {
					if (guiPerBlock * 350 >= 60) {
						OttonienData.Region r = data.region(s.id());
						String name = r != null ? r.name() : s.id();
						g.drawCenteredString(font, name, x, y - 4, 0xCCFFFFFF);
					}
					continue;
				}
				Identifier tex = Banners.texture(f.banner());
				int bx = x - BANNER / 2, by = y - BANNER / 2;
				if (s.index() == st.selected()) g.fill(bx - 2, by - 2, bx + BANNER + 2, by + BANNER + 2, 0xC0FFFFFF);
				if (tex != null) g.blit(RenderPipelines.GUI_TEXTURED, tex, bx, by, 0, 0, BANNER, BANNER, 16, 16, 16, 16);
				if (guiPerBlock * 350 >= 70) g.drawCenteredString(font, f.name(), x, by + BANNER + 2, 0xFFFFFFFF);
			}
		}

		// Name des Lehens unter der Maus
		if (hovered != null) {
			String text = title(data, hovered);
			int tw = font.width(text);
			int tx = mouseX + 10, ty = mouseY - 14;
			g.fill(tx - 3, ty - 3, tx + tw + 3, ty + 11, 0xB0000000);
			g.drawString(font, text, tx, ty, 0xFFFFE9A6, false);
		}

		if (sel != null) drawPanel(g, font, data, sel);
	}

	/** Füllt das Polygon zeilenweise in GUI-Pixeln (Scanline). */
	private static void fillShape(GuiGraphics g, LehenGeometry.Shape s, double camX, double camZ, double scale, double screenScale, int color) {
		int w = g.guiWidth(), h = g.guiHeight();
		int from = Math.max(0, (int) Math.floor(toGuiY(s.minZ(), camZ, scale, screenScale)));
		int to = Math.min(h - 1, (int) Math.ceil(toGuiY(s.maxZ(), camZ, scale, screenScale)));
		int[] xs = s.xs(), zs = s.zs();
		double[] cross = new double[xs.length];
		for (int y = from; y <= to; y++) {
			double wz = toWorldZ(y + 0.5, camZ, scale, screenScale);
			int n = 0;
			for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
				if ((zs[i] > wz) != (zs[j] > wz)) cross[n++] = xs[i] + (wz - zs[i]) * (xs[j] - xs[i]) / (double) (zs[j] - zs[i]);
			}
			java.util.Arrays.sort(cross, 0, n);
			for (int k = 0; k + 1 < n; k += 2) {
				int x0 = (int) Math.max(0, Math.round(toGuiX(cross[k], camX, scale, screenScale)));
				int x1 = (int) Math.min(w, Math.round(toGuiX(cross[k + 1], camX, scale, screenScale)));
				if (x1 > x0) g.fill(x0, y, x1, y + 1, color);
			}
		}
	}

	/** Zeichnet die Umrandung, nur den sichtbaren Teil jeder Kante. thick 0 = 1 Pixel, 1 = 2 Pixel. */
	private static void outlineShape(GuiGraphics g, LehenGeometry.Shape s, double camX, double camZ, double scale, double screenScale, int color, int thick) {
		int w = g.guiWidth(), h = g.guiHeight();
		int[] xs = s.xs(), zs = s.zs();
		for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
			double[] seg = clip(toGuiX(xs[j], camX, scale, screenScale), toGuiY(zs[j], camZ, scale, screenScale),
					toGuiX(xs[i], camX, scale, screenScale), toGuiY(zs[i], camZ, scale, screenScale), -2, -2, w + 2, h + 2);
			if (seg == null) continue;
			double dx = seg[2] - seg[0], dy = seg[3] - seg[1];
			int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)));
			for (int k = 0; k <= steps; k++) {
				double t = steps == 0 ? 0 : (double) k / steps;
				int px = (int) Math.floor(seg[0] + dx * t), py = (int) Math.floor(seg[1] + dy * t);
				g.fill(px - thick, py - thick, px + 1, py + 1, color);
			}
		}
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

	private static String title(OttonienData.Snapshot data, LehenGeometry.Shape s) {
		OttonienData.Region r = data.region(s.id());
		OttonienData.Faction f = data.owner(s.id());
		String lehen = r != null ? r.name() : s.id();
		if (f != null) return f.name().equals(lehen) ? lehen : lehen + " – " + f.name();
		if (r != null && !r.enabled()) return lehen + " (gesperrt)";
		return lehen + " (frei)";
	}

	private static void drawPanel(GuiGraphics g, Font font, OttonienData.Snapshot data, LehenGeometry.Shape s) {
		OttonienData.Region r = data.region(s.id());
		OttonienData.Faction f = data.owner(s.id());
		String lehen = r != null ? r.name() : s.id();
		int w = Math.min(PANEL_W, g.guiWidth() - 20);
		// unter Xaeros Koordinaten- und Biom-Anzeige oben in der Mitte
		int x = (g.guiWidth() - w) / 2, y = 44;
		int textX = x + 8 + 32 + 8;
		int textW = x + w - 8 - textX;

		List<Line> lines = new ArrayList<>();
		String head;
		String sub;
		if (f != null) {
			head = f.name();
			sub = f.rank() + " · Lehen " + lehen;
			lines.add(new Line("Anführer: ", f.leader().isEmpty() ? "–" : f.leader()));
			lines.add(new Line("Mitglieder: ", String.valueOf(f.members())));
			if (f.hasLord()) {
				lines.add(new Line("Lehnsherr: ", f.lordName()));
				if (!f.lordNameTop().isEmpty() && !f.lordNameTop().equals(f.lordName())) lines.add(new Line("Oberster Lehnsherr: ", f.lordNameTop()));
			}
			if (!f.orderName().isEmpty()) lines.add(new Line("Orden: ", f.orderName()));
			if (!f.vassalNames().isEmpty()) lines.add(new Line("Vasallen: ", String.join(", ", f.vassalNames())));
		} else {
			head = lehen;
			sub = r != null && !r.enabled() ? "Gesperrtes Lehen" : "Freies Lehen – kein Gefolge";
		}

		List<FormattedCharSequence> body = new ArrayList<>();
		for (Line l : lines) {
			body.addAll(font.split(Component.literal(l.label).withColor(0xAAAAAA).append(Component.literal(l.value).withColor(0xFFFFFF)), textW));
		}
		List<FormattedCharSequence> desc = f != null && !f.description().isBlank()
				? font.split(Component.literal("„" + f.description().trim() + "“").withColor(0xC8B98F), w - 16)
				: List.of();
		if (desc.size() > 5) desc = desc.subList(0, 5);

		int headerH = Math.max(36, 10 + 10 + 4 + body.size() * 10);
		int h = 8 + headerH + (desc.isEmpty() ? 0 : 4 + desc.size() * 10) + 6;
		panelX = x;
		panelY = y;
		panelW = w;
		panelH = h;

		int accent = 0xFF000000 | LehenState.shade(LehenState.style().baseRgb()[s.index()], 0.9);
		g.fill(x, y, x + w, y + h, 0xE0161210);
		g.fill(x, y, x + w, y + 2, accent);
		g.renderOutline(x, y, w, h, 0xFF5A4A35);

		Identifier tex = f != null ? Banners.texture(f.banner()) : null;
		if (tex != null) g.blit(RenderPipelines.GUI_TEXTURED, tex, x + 8, y + 8, 0, 0, 32, 32, 16, 16, 16, 16);
		else g.fill(x + 8, y + 8, x + 40, y + 40, 0xFF2A2420);

		int ty = y + 8;
		g.drawString(font, head, textX, ty, 0xFFFFD27A, true);
		ty += 10;
		g.drawString(font, sub, textX, ty, 0xFF9FD38A, false);
		ty += 14;
		for (FormattedCharSequence seq : body) {
			g.drawString(font, seq, textX, ty, 0xFFFFFFFF, false);
			ty += 10;
		}
		ty = y + 8 + headerH + 4;
		for (FormattedCharSequence seq : desc) {
			g.drawString(font, seq, x + 8, ty, 0xFFC8B98F, false);
			ty += 10;
		}
	}

	private record Line(String label, String value) {
	}

	/** Linksklick auf die Karte (GUI-Koordinaten): Lehen auswählen bzw. Auswahl aufheben. */
	public static void click(double guiX, double guiY, double camX, double camZ, double scale, double screenScale) {
		if (!enabled() || scale <= 0 || screenScale <= 0) return;
		if (panelW > 0 && guiX >= panelX && guiX < panelX + panelW && guiY >= panelY && guiY < panelY + panelH) {
			LehenState.select(-1);
			return;
		}
		LehenGeometry.Shape s = LehenState.geometry().at(toWorldX(guiX, camX, scale, screenScale), toWorldZ(guiY, camZ, scale, screenScale));
		int index = s == null || s.index() == LehenState.selected() ? -1 : s.index();
		LehenState.select(index);
	}
}
