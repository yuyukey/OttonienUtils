package de.aktivitaeten.gui;

import de.aktivitaeten.AktivitaetenClient;
import de.aktivitaeten.Config;
import de.aktivitaeten.data.ActivityData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import de.aktivitaeten.data.Banners;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Liste der Lehen mit Aktivität: sehr hohe Aktivität rot und oben, kleine orange. */
public final class ActivityHud {
	private static final int TITLE = 0xFFE8C170, DIM = 0xFF8C8C8C, BG = 0xB0101014, BORDER = 0x40E8C170, LINE = 0x30FFFFFF;
	/** Kleine Aktivität: orange. */
	public static final int LOW = 0xFFFFA640;
	/** Sehr hohe Aktivität: rot. */
	public static final int HIGH = 0xFFFF5040;

	private ActivityHud() {
	}

	/** Eine Zeile der Liste, fertig zum Zeichnen. */
	public record Row(ActivityData.Activity activity, boolean high, String since) {
	}

	/** Sehr hohe Aktivität zuerst, sonst nach Stufe und dann nach Namen. */
	public static List<Row> rows() {
		List<Row> rows = new ArrayList<>();
		long now = System.currentTimeMillis();
		for (ActivityData.Activity a : ActivityData.get().activities()) {
			Long start = ActivityData.startedAt(a.id());
			String since = start == null ? "" : now - start < 60_000 ? "neu" : (now - start) / 60_000 + " min";
			rows.add(new Row(a, a.level() >= 2, since));
		}
		rows.sort(Comparator.comparingInt((Row r) -> -r.activity().level()).thenComparing(r -> label(r.activity())));
		return rows;
	}

	public static int color(ActivityData.Activity a) {
		return a.level() >= 2 ? HIGH : LOW;
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Config cfg = Config.get();
		if (!cfg.hudEnabled || !AktivitaetenClient.isActive() || mc.options.hideGui) return;
		// beim offenen Chat sichtbar bleiben, bei anderen Menüs ausblenden
		if (mc.getDebugOverlay().showDebugScreen() || (mc.screen != null && !(mc.screen instanceof ChatScreen))) return;
		ActivityData.Snapshot snap = ActivityData.get();
		if (!snap.loaded()) return;

		List<Row> all = rows();
		if (all.isEmpty() && !cfg.showWhenEmpty) return;
		draw(g, all);
	}

	/** Lage der Liste auf dem Bildschirm in GUI-Koordinaten (schon skaliert). */
	public record Box(int x, int y, int w, int h) {
		public boolean contains(double px, double py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
	}

	/** Vorschau für den Verschiebe-Bildschirm: echte Aktivitäten, sonst Beispiele. */
	public static Box drawPreview(GuiGraphics g) {
		List<Row> all = rows();
		if (all.isEmpty()) {
			all = List.of(
					new Row(new ActivityData.Activity("beispiel_1", "Beispiel", 0, 0, 2, "Sehr hohe Aktivität", "", ""), true, "neu"),
					new Row(new ActivityData.Activity("beispiel_2", "Beispiel", 0, 0, 1, "Kleine Aktivität", "", ""), false, ""),
					new Row(new ActivityData.Activity("beispiel_3", "Beispiel", 0, 0, 1, "Noch ein Gefolge", "", ""), false, "12 min"));
		}
		return draw(g, all);
	}

	private static Box draw(GuiGraphics g, List<Row> all) {
		Minecraft mc = Minecraft.getInstance();
		Config cfg = Config.get();
		List<Row> rows = all.subList(0, Math.min(all.size(), cfg.maxEntries));
		Font font = mc.font;
		float scale = cfg.scale / 100f;
		String title = "Aktivitäten";
		String count = all.isEmpty() ? "" : String.valueOf(all.size());

		// Wappen in Originalgröße (16 px), sonst werden die Pixel ungleichmäßig
		int pad = 5, icon = 16, rowH = 18, dot = 5, headerH = 15;
		String empty = "Gerade nirgends etwas los";
		int contentW = Math.max(font.width(title) + (count.isEmpty() ? 0 : 8 + font.width(count)), rows.isEmpty() ? font.width(empty) : 0);
		for (Row r : rows) {
			int sw = r.since().isEmpty() ? 0 : 8 + font.width(r.since());
			contentW = Math.max(contentW, icon + 5 + font.width(label(r.activity())) + sw + 8 + dot);
		}
		String more = all.size() > rows.size() ? "+ " + (all.size() - rows.size()) + " weitere" : "";
		if (!more.isEmpty()) contentW = Math.max(contentW, font.width(more));
		int w = contentW + 2 * pad;
		int body = rows.isEmpty() ? 14 : rows.size() * rowH;
		if (!more.isEmpty()) body += 11;
		int h = headerH + 3 + body + 3;

		int screenW = Math.round(g.guiWidth() / scale), screenH = Math.round(g.guiHeight() / scale);
		int margin = 4;
		int x = switch (cfg.position) {
			case TOP_LEFT, MIDDLE_LEFT -> margin;
			case TOP_RIGHT, MIDDLE_RIGHT -> screenW - w - margin;
			// Anteil des freien Platzes, so bleibt die Liste auch bei anderer Fenstergröße auf dem Bildschirm
			case CUSTOM -> Math.round(cfg.overlayX * Math.max(0, screenW - w));
		};
		int y = switch (cfg.position) {
			case TOP_LEFT -> margin;
			// oben rechts unter den Statuseffekten bleiben
			case TOP_RIGHT -> 52;
			case MIDDLE_LEFT, MIDDLE_RIGHT -> (screenH - h) / 2;
			case CUSTOM -> Math.round(cfg.overlayY * Math.max(0, screenH - h));
		};

		g.pose().pushMatrix();
		g.pose().scale(scale, scale);
		panel(g, x, y, w, h);

		// Kopf: Titel links, Anzahl rechts, darunter eine feine Linie
		g.drawString(font, title, x + pad, y + 4, TITLE, true);
		if (!count.isEmpty()) g.drawString(font, count, x + w - pad - font.width(count), y + 4, DIM, false);
		g.fill(x + pad, y + headerH, x + w - pad, y + headerH + 1, LINE);

		int ly = y + headerH + 3;
		if (rows.isEmpty()) g.drawString(font, empty, x + pad, ly + 3, DIM, false);
		long t = System.currentTimeMillis();
		for (Row r : rows) {
			ActivityData.Activity a = r.activity();
			int c = color(a);
			// Sehr hohe Aktivität bekommt eine rote Fläche, damit sie sofort auffällt
			if (r.high()) {
				g.fill(x + 2, ly, x + w - 2, ly + rowH - 1, 0x70601812);
				g.fill(x + 2, ly, x + 3, ly + rowH - 1, c);
			}
			Identifier tex = Banners.texture(a.banner());
			if (tex != null) g.blit(RenderPipelines.GUI_TEXTURED, tex, x + pad, ly + 1, 0, 0, icon, icon, 16, 16, 16, 16);
			int ty = ly + (rowH - 8) / 2;
			int tx = x + pad + icon + 5;
			g.drawString(font, label(a), tx, ty, c, true);
			// pulsierender Punkt rechts wie auf karte.ottonien.com, bei Rot schneller
			float pulse = 0.5f + 0.5f * (float) Math.sin(t / (r.high() ? 140.0 : 320.0) + a.id().hashCode());
			int dx = x + w - pad - dot, dy = ly + (rowH - dot) / 2;
			int dc = ((int) (90 + pulse * 165) << 24) | (c & 0xFFFFFF);
			g.fill(dx + 1, dy, dx + dot - 1, dy + dot, dc);
			g.fill(dx, dy + 1, dx + dot, dy + dot - 1, dc);
			if (!r.since().isEmpty()) {
				g.drawString(font, r.since(), dx - 6 - font.width(r.since()), ty, DIM, false);
			}
			ly += rowH;
		}
		if (!more.isEmpty()) g.drawString(font, more, x + pad, ly + 2, DIM, false);
		g.pose().popMatrix();
		return new Box(Math.round(x * scale), Math.round(y * scale), Math.round(w * scale), Math.round(h * scale));
	}

	/** Dunkle Fläche mit feinem Rahmen und abgeschrägten Ecken. */
	private static void panel(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x + 1, y, x + w - 1, y + h, BG);
		g.fill(x, y + 1, x + 1, y + h - 1, BG);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, BG);
		g.fill(x + 1, y, x + w - 1, y + 1, BORDER);
		g.fill(x + 1, y + h - 1, x + w - 1, y + h, BORDER);
		g.fill(x, y + 1, x + 1, y + h - 1, BORDER);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, BORDER);
	}

	/** Name des Gefolges; bei einem freien Lehen ohne Gefolge der Name des Lehens. */
	public static String label(ActivityData.Activity a) {
		return a.owner().isEmpty() ? a.name() : a.owner();
	}
}
