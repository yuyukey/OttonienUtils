package de.lehenskarte.gui;

import de.lehenskarte.Config;
import de.lehenskarte.data.Banners;
import de.lehenskarte.data.LehenGeometry;
import de.lehenskarte.data.LehenGroups;
import de.lehenskarte.data.LehenState;
import de.lehenskarte.data.NpcVillages;
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
	private static final int PANEL_W = 230;

	/** Ungefähre Breite eines Lehens in Blöcken, für die Zoomstufen. */
	private static final double LEHEN_SIZE = 350;

	private static int panelX, panelY, panelW, panelH;
	/** Zoomstufe, auf der die Auswahl getroffen wurde: Sie bleibt beim Zoomen dieselbe Gruppe. */
	private static int selStage;

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

		// Beim Rauszoomen Lehen zusammenfassen: erst Freiherrschaften mit ihren Vasallen, dann ganze Machtbereiche.
		// Die Stufen werden weich überblendet; Maus und Auswahl richten sich nach der überwiegenden Stufe.
		double guiPerBlock = scale / screenScale;
		double lehenPx = guiPerBlock * LEHEN_SIZE;
		float[] weights = weights(lehenPx);
		int stage = 0;
		for (int k = 1; k < weights.length; k++) if (weights[k] > weights[stage]) stage = k;
		LehenGroups groups = LehenGroups.of(geo, data);

		// Auswahl und Lehen unter der Maus sofort hervorheben, ohne dass Xaero Kacheln neu berechnen muss
		LehenGeometry.Shape hovered = geo.at(toWorldX(mouseX, camX, scale, screenScale), toWorldZ(mouseY, camZ, scale, screenScale));
		LehenGeometry.Shape sel = geo.shape(st.selected());
		LehenGroups.Group hoverGroup = hovered != null ? groups.group(stage, hovered.index()) : null;
		LehenGroups.Group selGroup = sel != null ? groups.group(selStage, sel.index()) : null;
		var win = mc.getWindow();
		BorderRenderer.View view = new BorderRenderer.View(camX, camZ, scale, win.getWidth() / 2.0, win.getHeight() / 2.0,
				win.getWidth(), win.getHeight());
		if (selGroup != null) {
			for (int m : selGroup.members()) BorderRenderer.fill(g, geo.shape(m), view, screenScale, 0x38FFFFFF);
		}
		BorderRenderer.drawAll(g, st, groups, weights, view, screenScale);
		if (hoverGroup != null && hoverGroup != selGroup) BorderRenderer.groupOutline(g, st, hoverGroup, view, screenScale, 2f, 0xA0FFFFFF);
		if (selGroup != null) BorderRenderer.groupOutline(g, st, selGroup, view, screenScale, (float) Math.max(3, screenScale * 1.5), 0xF0FFFFFF);

		// Wappen: eins pro Lehen bzw. pro zusammengefasster Gruppe, Größe wächst mit dem Zoom
		if (Config.get().bannersOnMap && lehenPx >= 5) {
			for (int k = 0; k < weights.length; k++) {
				// freie und gesperrte Lehen sehen auf jeder Stufe gleich aus: nur einmal, auf der überwiegenden Stufe
				if (weights[k] > 0.02f || k == stage) drawBanners(g, font, geo, data, groups, k, weights[k], k == stage, lehenPx, camX, camZ, scale, screenScale);
			}
		}

		// NPC-Dörfer: Glocke und Name; liegt die Maus darauf, steht das Dorf statt des Lehens im Hinweis
		NpcVillages.Village hoverVillage = Config.get().npcVillages && lehenPx >= 5
				? drawVillages(g, font, lehenPx, mouseX, mouseY, camX, camZ, scale, screenScale) : null;
		if (hoverVillage != null) {
			String text = "NPC-Dorf " + hoverVillage.name() + " (" + hoverVillage.x() + ", " + hoverVillage.z() + ")";
			int tw = font.width(text);
			int tx = mouseX + 10, ty = mouseY - 14;
			g.fill(tx - 3, ty - 3, tx + tw + 3, ty + 11, 0xB0000000);
			g.drawString(font, text, tx, ty, 0xFFFFE9A6, false);
			hovered = null;
		}

		// Name des Lehens bzw. der Gruppe unter der Maus
		if (hovered != null) {
			String text = title(data, geo, hoverGroup != null ? hoverGroup : groups.group(0, hovered.index()));
			int tw = font.width(text);
			int tx = mouseX + 10, ty = mouseY - 14;
			g.fill(tx - 3, ty - 3, tx + tw + 3, ty + 11, 0xB0000000);
			g.drawString(font, text, tx, ty, 0xFFFFE9A6, false);
		}

		if (sel != null) drawPanel(g, font, data, sel, selGroup != null ? selGroup.members().length : 1);
	}

	private static String title(OttonienData.Snapshot data, LehenGeometry geo, LehenGroups.Group grp) {
		LehenGeometry.Shape s = geo.shape(grp.root());
		OttonienData.Region r = data.region(s.id());
		OttonienData.Faction f = data.owner(s.id());
		String lehen = r != null ? r.name() : s.id();
		if (grp.members().length > 1 && f != null) return f.name() + " (" + grp.members().length + " Lehen)";
		if (f != null) return f.name().equals(lehen) ? lehen : lehen + " – " + f.name();
		if (r != null && !r.enabled()) return lehen + " (gesperrt)";
		return lehen + " (frei)";
	}

	private static final Identifier BELL = Identifier.withDefaultNamespace("textures/item/bell.png");

	/** Zeichnet die NPC-Dörfer und gibt das Dorf unter der Maus zurück. */
	private static NpcVillages.Village drawVillages(GuiGraphics g, Font font, double lehenPx, int mouseX, int mouseY,
												   double camX, double camZ, double scale, double screenScale) {
		int size = (int) Math.round(Math.max(10, Math.min(16, lehenPx * 0.25)));
		boolean names = lehenPx >= 30;
		NpcVillages.Village hover = null;
		for (NpcVillages.Village v : NpcVillages.all()) {
			int x = (int) Math.round(toGuiX(v.x() + 0.5, camX, scale, screenScale));
			int y = (int) Math.round(toGuiY(v.z() + 0.5, camZ, scale, screenScale));
			if (x < -100 || y < -size || x > g.guiWidth() + 100 || y > g.guiHeight() + size) continue;
			int bx = x - size / 2, by = y - size / 2;
			g.blit(RenderPipelines.GUI_TEXTURED, BELL, bx, by, 0, 0, size, size, 16, 16, 16, 16);
			if (names) outlined(g, font, v.name(), x, by + size + 2, 0xFFFFE08A, 1f);
			if (mouseX >= bx - 2 && mouseX < bx + size + 2 && mouseY >= by - 2 && mouseY < by + size + 2) hover = v;
		}
		return hover;
	}

	/** Reserviert ein Rechteck für Wappen und Namen; false, wenn es sich mit einem schon belegten überschneidet. */
	private static boolean place(List<int[]> taken, int x0, int y0, int x1, int y1) {
		for (int[] r : taken) if (x0 < r[2] && x1 > r[0] && y0 < r[3] && y1 > r[1]) return false;
		taken.add(new int[]{x0, y0, x1, y1});
		return true;
	}

	/** Discrete Stufe für Klicks: 0 = einzelne Lehen, 1 = Freiherrschaften o. Ä. mit Vasallen, 2 = ganze Machtbereiche. */
	private static int stage(double lehenPx) {
		float[] w = weights(lehenPx);
		int best = 0;
		for (int k = 1; k < w.length; k++) if (w[k] > w[best]) best = k;
		return best;
	}

	/** Anteil jeder Stufe bei dieser Zoomstufe; an den Übergängen werden zwei Stufen weich überblendet. */
	private static float[] weights(double lehenPx) {
		if (!Config.get().groupOnZoomOut) return new float[]{1, 0, 0};
		float a = smooth(lehenPx, 36, 56);
		float b = smooth(lehenPx, 16, 25);
		return new float[]{a, (1 - a) * b, (1 - a) * (1 - b)};
	}

	private static float smooth(double v, double from, double to) {
		float t = (float) Math.max(0, Math.min(1, (v - from) / (to - from)));
		return t * t * (3 - 2 * t);
	}

	/** Wappengröße je Stufe: wächst mit dem Zoom, zusammengefasste Bereiche etwas größer, mal der eingestellten Größe. */
	private static int bannerSize(int stage, double lehenPx) {
		double f = stage == 0 ? 0.38 : stage == 1 ? 0.95 : 1.6;
		double scale = Math.max(50, Math.min(200, Config.get().bannerScale)) / 100.0;
		return (int) Math.round(Math.max(12, Math.min(30, lehenPx * f)) * scale);
	}

	private static void drawBanners(GuiGraphics g, Font font, LehenGeometry geo, OttonienData.Snapshot data, LehenGroups groups,
									int stage, float alpha, boolean withFree, double lehenPx, double camX, double camZ, double scale, double screenScale) {
		int groupSize = bannerSize(stage, lehenPx);
		if (stage == 0 && !Config.get().groupOnZoomOut && lehenPx < 16) return;
		int w = g.guiWidth(), h = g.guiHeight();
		// größere Bereiche zuerst; was sich mit einem schon gezeichneten Wappen überschneiden würde, entfällt
		List<LehenGroups.Group> order = new ArrayList<>();
		for (LehenGeometry.Shape s : geo.shapes()) {
			LehenGroups.Group grp = groups.group(stage, s.index());
			if (grp != null && grp.root() == s.index() && (withFree || data.owner(s.id()) != null)) order.add(grp);
		}
		// besetzte Lehen vor freien, größere Bereiche zuerst
		order.sort((a, b) -> {
			boolean fa = data.owner(geo.shape(a.root()).id()) == null, fb = data.owner(geo.shape(b.root()).id()) == null;
			if (fa != fb) return fa ? 1 : -1;
			return Integer.compare(b.members().length, a.members().length);
		});
		List<int[]> taken = new ArrayList<>();
		for (LehenGroups.Group grp : order) {
			LehenGeometry.Shape root = geo.shape(grp.root());
			OttonienData.Faction f = data.owner(root.id());
			// freie und gesperrte Lehen: leeres Wappen „kein Wappen“ (NPC-Dörfer ihr eigenes) und der Name des Lehens
			float a = f != null ? alpha : 1f;
			String name;
			if (f != null) {
				name = f.name();
			} else {
				OttonienData.Region r = data.region(root.id());
				name = r != null ? r.name() : root.id();
			}
			int x = (int) Math.round(toGuiX(grp.labelX(), camX, scale, screenScale));
			int y = (int) Math.round(toGuiY(grp.labelZ(), camZ, scale, screenScale));
			// freie Lehen bleiben einzeln, ihr Wappen wächst darum nicht mit wie das zusammengefasster Bereiche
			int size = f != null ? groupSize : Math.min(groupSize, bannerSize(0, lehenPx));
			if (x < -size * 4 || y < -size || x > w + size * 4 || y > h + size) continue;
			int bx = x - size / 2, by = y - size / 2;
			boolean showName = f == null ? lehenPx >= 60 : stage == 0 ? lehenPx >= 60 : lehenPx >= 9;
			int nw = showName ? font.width(name) : 0;
			int half = Math.max(size, nw) / 2;
			if (!place(taken, x - half, by, x + half, by + size + (showName ? 11 : 0))) continue;
			OttonienData.Region rr = data.region(root.id());
			Identifier tex = Banners.texture(Banners.forLehen(f != null ? f.banner() : "", rr != null ? rr.name() : ""));
			int tint = (Math.round(a * (f != null ? 255 : 220)) << 24) | 0xFFFFFF;
			if (tex != null) g.blit(RenderPipelines.GUI_TEXTURED, tex, bx, by, 0, 0, size, size, 16, 16, 16, 16, tint);
			if (showName) outlined(g, font, name, x, by + size + 2, f != null ? 0xFFFFFFFF : 0xFFD8D8D8, a);
		}
	}

	/** Zentrierter Text mit dunklem Rand, damit er auf heller Karte und hellem Gelände lesbar bleibt. */
	private static void outlined(GuiGraphics g, Font font, String text, int cx, int y, int color, float alpha) {
		int a = Math.round(alpha * (color >>> 24));
		if (a < 8) return;
		int x = cx - font.width(text) / 2;
		int shadow = (Math.round(a * 0.85f) << 24) | 0x101010;
		g.drawString(font, text, x - 1, y, shadow, false);
		g.drawString(font, text, x + 1, y, shadow, false);
		g.drawString(font, text, x, y - 1, shadow, false);
		g.drawString(font, text, x, y + 1, shadow, false);
		g.drawString(font, text, x, y, (a << 24) | (color & 0xFFFFFF), false);
	}

	private static void drawPanel(GuiGraphics g, Font font, OttonienData.Snapshot data, LehenGeometry.Shape s, int count) {
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
			sub = count > 1 ? f.rank() + " · " + count + " Lehen" : f.rank() + " · Lehen " + lehen;
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

		// Bergbau und Bonus des Lehens (nur bei einem einzelnen Lehen, nicht bei zusammengefassten Bereichen)
		if (r != null && count == 1) {
			if (!r.mines().isBlank()) lines.add(new Line("Bergbau: ", r.mines()));
			if (!r.bonus().isBlank()) lines.add(new Line("Bonus: ", r.bonus()));
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

		String bannerName = Banners.forLehen(f != null ? f.banner() : "", r != null ? r.name() : "");
		Identifier tex = bannerName.isEmpty() ? null : Banners.texture(bannerName);
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
		if (s == null) {
			LehenState.select(-1);
			return;
		}
		int stage = stage(scale / screenScale * LEHEN_SIZE);
		int root = LehenGroups.of(LehenState.geometry(), LehenState.style().data()).root(stage, s.index());
		int index = root == LehenState.selected() && stage == selStage ? -1 : root;
		selStage = stage;
		LehenState.select(index);
	}
}
