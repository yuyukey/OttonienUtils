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
		if (mc.getDebugOverlay().showDebugScreen() || mc.screen instanceof MineListScreen || mc.screen instanceof OverlayMoveScreen) return;
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
		int nameW = 0, statusW = 0;
		int headingW = 0;
		for (Line l : lines) {
			if (l.heading) {
				headingW = Math.max(headingW, font.width(l.name));
				continue;
			}
			nameW = Math.max(nameW, font.width(l.name) + font.width(l.extra));
			statusW = Math.max(statusW, font.width(l.status));
		}
		String area = MineManager.get().activeArea();
		String header = area.isEmpty() ? "⛏ Minen" : "⛏ " + MineManager.areaLabel(area);
		int pad = 4, lineH = 10, dot = 6;
		int w = Math.max(Math.max(pad + dot + 4 + nameW + 10 + statusW + pad, font.width(header) + 2 * pad), headingW + 2 * pad);
		int h = pad + lineH + 2 + lines.size() * lineH + pad - 2;

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
		g.fill(x, y, x + w, y + h, 0x90000000);
		g.drawString(font, header, x + pad, y + pad, 0xFFFFAA00, true);
		int ly = y + pad + lineH + 2;
		for (Line l : lines) {
			if (l.heading) {
				g.drawString(font, l.name, x + pad, ly, l.color, true);
				ly += lineH;
				continue;
			}
			g.fill(x + pad, ly + 1, x + pad + dot, ly + 1 + dot, l.color);
			int tx = x + pad + dot + 4;
			g.drawString(font, l.name, tx, ly, 0xFFFFFFFF, true);
			if (!l.extra.isEmpty()) g.drawString(font, l.extra, tx + font.width(l.name), ly, 0xFFAAAAAA, true);
			g.drawString(font, l.status, x + w - pad - font.width(l.status), ly, l.color, true);
			ly += lineH;
		}
		g.pose().popMatrix();
		return new Box(Math.round(x * scale), Math.round(y * scale), Math.round(w * scale), Math.round(h * scale));
	}
}
