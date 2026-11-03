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

	private record Line(String name, String extra, String status, int color) {
	}

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
		return lines(Minecraft.getInstance(), ModConfig.get()).stream().map(Line::name).toList();
	}

	private static List<Line> lines(Minecraft mc, ModConfig cfg) {
		MineManager mgr = MineManager.get();
		long now = System.currentTimeMillis();
		List<Line> lines = new ArrayList<>();
		for (Mine mine : mgr.visibleMines(ModConfig.SortMode.LIST)) {
			long remaining = mgr.remainingMs(mine, now);
			if (cfg.overlayMode == ModConfig.OverlayMode.READY && remaining > 0) continue;
			String extra = cfg.overlayShowDistance
					? " " + Math.round(mine.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ())) + "m"
					: "";
			lines.add(new Line(mine.name, extra, remaining <= 0 ? "BEREIT" : TimeFormat.duration(remaining), statusColor(remaining)));
		}
		return lines;
	}

	private static Box draw(GuiGraphics g, List<Line> lines) {
		Font font = Minecraft.getInstance().font;
		ModConfig cfg = ModConfig.get();
		float scale = cfg.overlayScalePercent / 100f;
		int nameW = 0, statusW = 0;
		for (Line l : lines) {
			nameW = Math.max(nameW, font.width(l.name) + font.width(l.extra));
			statusW = Math.max(statusW, font.width(l.status));
		}
		String area = MineManager.get().activeArea();
		String header = area.isEmpty() ? "⛏ Minen" : "⛏ " + MineManager.areaLabel(area);
		int pad = 4, lineH = 10, dot = 6;
		int w = Math.max(pad + dot + 4 + nameW + 10 + statusW + pad, font.width(header) + 2 * pad);
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
