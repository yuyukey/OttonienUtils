package de.minentimer.gui;

import de.minentimer.Mine;
import de.minentimer.MineManager;
import de.minentimer.ModConfig;
import de.minentimer.TimeFormat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Kleine Liste am Bildschirmrand: grün = bereit, gelb = bald, rot = Cooldown. */
public final class HudOverlay {
	public static final int READY = 0xFF55FF55;
	public static final int SOON = 0xFFFFDD55;
	public static final int COOLDOWN = 0xFFFF5555;

	private record Line(String name, String extra, String status, int color, boolean heading) {
		Line(String name, String extra, String status, int color) {
			this(name, extra, status, color, false);
		}

		static Line heading(String name) {
			return new Line(name, "", "", HEADING, true);
		}
	}

	private static final int HEADING = 0xFFE0B060;
	/** Farben wie im Aktivitäten-Overlay. */
	private static final int TITLE = 0xFFE8C170, DIM = 0xFF8C8C8C, BG = 0xB0101014, BORDER = 0x40E8C170, LINE = 0x30FFFFFF;

	private HudOverlay() {
	}

	public static int statusColor(long remainingMs) {
		if (remainingMs <= 0) return READY;
		int soon = ModConfig.get().soonMinutes;
		return soon > 0 && remainingMs <= soon * 60_000L ? SOON : COOLDOWN;
	}

	/** Lage des Overlays in GUI-Koordinaten (schon mit Skalierung). */
	public record Box(int x, int y, int w, int h) {
		public boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		ModConfig cfg = ModConfig.get();
		if (!cfg.overlayEnabled || mc.player == null || mc.options.hideGui) return;
		if (mc.getDebugOverlay().showDebugScreen() || mc.screen instanceof MineListScreen || mc.screen instanceof OverlayMoveScreen
				|| mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen) return;
		List<Line> lines = lines(mc, cfg);
		if (!lines.isEmpty()) draw(g, lines);
	}

	/** Für den Verschiebe-Modus: zeichnet das Overlay immer, notfalls mit Beispielzeilen. */
	public static Box drawPreview(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		List<Line> lines = mc.player != null ? lines(mc, ModConfig.get()) : new ArrayList<>();
		if (lines.isEmpty()) {
			lines.add(new Line("Beispiel-Mine", "", "BEREIT", READY));
			lines.add(new Line("Zweite Mine", "", "1:23:45", COOLDOWN));
		}
		return draw(g, lines);
	}

	/** Namen der Minen, die das Overlay gerade zeigt (für Tests). */
	public static List<String> visibleNames() {
		return lines(Minecraft.getInstance(), ModConfig.get()).stream().filter(l -> !l.heading).map(Line::name).toList();
	}

	/** Gleiche Sortierung wie im Menü. Bei „Alle Lehen“ zusätzlich nach Lehen gruppiert, mit Überschriften. */
	private static List<Line> lines(Minecraft mc, ModConfig cfg) {
		MineManager mgr = MineManager.get();
		long now = System.currentTimeMillis();
		List<Mine> mines = mgr.visibleMines(cfg.sortMode);
		if (cfg.overlayMode == ModConfig.OverlayMode.READY) mines.removeIf(m -> mgr.isRunning(m, now));

		List<String> areas = mgr.activeArea().isEmpty() ? mgr.areas() : List.of();
		List<Line> lines = new ArrayList<>();
		if (areas.isEmpty()) {
			for (Mine mine : mines) lines.add(line(mc, cfg, mgr, mine, now));
			return lines;
		}
		List<String> groups = new ArrayList<>(areas);
		groups.add(MineManager.NO_AREA);
		for (String area : groups) {
			List<Mine> inGroup = mines.stream().filter(m -> MineManager.inArea(m, area)).toList();
			if (inGroup.isEmpty()) continue;
			lines.add(Line.heading(MineManager.areaLabel(area)));
			for (Mine mine : inGroup) lines.add(line(mc, cfg, mgr, mine, now));
		}
		return lines;
	}

	private static Line line(Minecraft mc, ModConfig cfg, MineManager mgr, Mine mine, long now) {
		long remaining = mgr.remainingMs(mine, now);
		String extra = cfg.overlayShowDistance
				? " " + Math.round(mine.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ())) + "m"
				: "";
		return new Line(mine.name, extra, remaining <= 0 ? "BEREIT" : TimeFormat.duration(remaining), statusColor(remaining));
	}

	private static Box draw(GuiGraphics g, List<Line> lines) {
		Font font = Minecraft.getInstance().font;
		ModConfig cfg = ModConfig.get();
		float scale = cfg.overlayScalePercent / 100f;
		String area = MineManager.get().activeArea();
		String title = area.isEmpty() ? "Minen" : MineManager.areaLabel(area);
		long ready = lines.stream().filter(l -> !l.heading && l.color == READY).count();
		String count = ready == 0 ? "" : ready + " bereit";

		// Gleicher Aufbau wie das Aktivitäten-Overlay: Kopf mit Linie, darunter die Zeilen
		int pad = 5, rowH = 12, dot = 5, headerH = 15, headingH = 12;
		int contentW = font.width(title) + (count.isEmpty() ? 0 : 8 + font.width(count));
		for (Line l : lines) {
			if (l.heading) contentW = Math.max(contentW, font.width(l.name));
			else contentW = Math.max(contentW, dot + 5 + font.width(l.name) + font.width(l.extra) + 10 + font.width(l.status));
		}
		int w = contentW + 2 * pad;
		int body = 0;
		for (Line l : lines) body += l.heading ? headingH : rowH;
		int h = headerH + 3 + body + 3;

		int screenW = Math.round(g.guiWidth() / scale), screenH = Math.round(g.guiHeight() / scale);
		int margin = 4;
		int x = switch (cfg.overlayPosition) {
			case TOP_LEFT, MIDDLE_LEFT -> margin;
			case CUSTOM -> Math.round(cfg.overlayX * Math.max(0, screenW - w));
			default -> screenW - w - margin;
		};
		int y = switch (cfg.overlayPosition) {
			case TOP_LEFT, TOP_RIGHT -> margin;
			case MIDDLE_LEFT, MIDDLE_RIGHT -> (screenH - h) / 2;
			case BOTTOM_RIGHT -> screenH - h - 40;
			case CUSTOM -> Math.round(cfg.overlayY * Math.max(0, screenH - h));
		};

		g.pose().pushMatrix();
		g.pose().scale(scale, scale);
		panel(g, x, y, w, h);

		// Kopf: Titel links, Anzahl bereiter Minen rechts, darunter eine feine Linie
		g.drawString(font, title, x + pad, y + 4, TITLE, true);
		if (!count.isEmpty()) g.drawString(font, count, x + w - pad - font.width(count), y + 4, DIM, false);
		g.fill(x + pad, y + headerH, x + w - pad, y + headerH + 1, LINE);

		int ly = y + headerH + 3;
		long t = System.currentTimeMillis();
		boolean first = true;
		for (Line l : lines) {
			if (l.heading) {
				// Lehen-Überschrift, ab der zweiten mit Trennlinie darüber
				if (!first) g.fill(x + pad, ly + 1, x + w - pad, ly + 2, LINE);
				g.drawString(font, l.name, x + pad, ly + 3, TITLE, true);
				ly += headingH;
				first = false;
				continue;
			}
			first = false;
			boolean isReady = l.color == READY;
			// Bereite Minen bekommen eine grüne Fläche, damit sie sofort auffallen
			if (isReady) {
				g.fill(x + 2, ly, x + w - 2, ly + rowH - 1, 0x60124818);
				g.fill(x + 2, ly, x + 3, ly + rowH - 1, l.color);
			}
			int ty = ly + (rowH - 8) / 2;
			// runder Punkt in der Statusfarbe, bei bereiten Minen pulsierend
			float pulse = isReady ? 0.5f + 0.5f * (float) Math.sin(t / 320.0 + l.name.hashCode()) : 1f;
			int dc = ((int) (90 + pulse * 165) << 24) | (l.color & 0xFFFFFF);
			int dx = x + pad, dy = ly + (rowH - dot) / 2;
			g.fill(dx + 1, dy, dx + dot - 1, dy + dot, dc);
			g.fill(dx, dy + 1, dx + dot, dy + dot - 1, dc);
			int tx = dx + dot + 5;
			g.drawString(font, l.name, tx, ty, 0xFFFFFFFF, true);
			if (!l.extra.isEmpty()) g.drawString(font, l.extra, tx + font.width(l.name), ty, DIM, false);
			g.drawString(font, l.status, x + w - pad - font.width(l.status), ty, l.color, true);
			ly += rowH;
		}
		g.pose().popMatrix();
		return new Box(Math.round(x * scale), Math.round(y * scale), Math.round(w * scale), Math.round(h * scale));
	}

	/** Dunkle Fläche mit feinem Rahmen und abgeschrägten Ecken, wie bei der Aktivitäten-Mod. */
	private static void panel(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x + 1, y, x + w - 1, y + h, BG);
		g.fill(x, y + 1, x + 1, y + h - 1, BG);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, BG);
		g.fill(x + 1, y, x + w - 1, y + 1, BORDER);
		g.fill(x + 1, y + h - 1, x + w - 1, y + h, BORDER);
		g.fill(x, y + 1, x + 1, y + h - 1, BORDER);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, BORDER);
	}
}
