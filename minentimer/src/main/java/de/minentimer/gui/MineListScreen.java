package de.minentimer.gui;

import de.minentimer.Mine;
import de.minentimer.MineManager;
import de.minentimer.MinenTimerClient;
import de.minentimer.ModConfig;
import de.minentimer.Sounds;
import de.minentimer.SyncManager;
import de.minentimer.TimeFormat;
import de.minentimer.TimerEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Hauptmenü (Standard: Numpad 3): Liste aller Minen mit ihren Timern. */
public class MineListScreen extends Screen {
	private static final int ROW_H = 24;
	private static final int EDIT_W = 16;
	private static final int MOVE_W = 12;

	private final Screen parent;
	private List<Mine> rows = new ArrayList<>();
	private double scroll;
	private int left, right, listTop, listBottom;

	public MineListScreen(Screen parent) {
		super(Component.literal("Minen-Timer"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int panelW = Math.min(360, width - 20);
		left = (width - panelW) / 2;
		right = left + panelW;
		listTop = 34;
		listBottom = height - 34;

		ModConfig cfg = ModConfig.get();
		addRenderableWidget(CycleButton.<ModConfig.SortMode>builder(m -> Component.literal(m.label), cfg.sortMode)
				.withValues(ModConfig.SortMode.values())
				.create(right - 130, 8, 130, 20, Component.literal("Sortierung"), (b, v) -> {
					cfg.sortMode = v;
					cfg.save();
				}));

		// Lehen umschalten (auch mit den Hotkeys)
		addRenderableWidget(Button.builder(Component.literal("◀"), b -> MinenTimerClient.switchArea(minecraft, -1)).bounds(areaLeft(), 8, 16, 20).build());
		addRenderableWidget(Button.builder(Component.literal("▶"), b -> MinenTimerClient.switchArea(minecraft, 1)).bounds(areaRight() - 16, 8, 16, 20).build());

		int bw = Math.min(116, (panelW - 8) / 3);
		int bx = width / 2 - (bw * 3 + 8) / 2;
		int by = height - 26;
		addRenderableWidget(Button.builder(Component.literal("+ Mine hier"), b -> addMineHere()).bounds(bx, by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Einstellungen"), b -> minecraft.setScreen(new SettingsScreen(this))).bounds(bx + bw + 4, by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose()).bounds(bx + 2 * (bw + 4), by, bw, 20).build());
	}

	private int areaLeft() {
		return left + 78;
	}

	private int areaRight() {
		return right - 134;
	}

	private void addMineHere() {
		if (minecraft.player == null) return;
		Mine m = MineManager.get().createMine("Neue Mine", minecraft.player.getBlockX(), minecraft.player.getBlockY(), minecraft.player.getBlockZ());
		String area = MineManager.get().activeArea();
		if (!area.isEmpty() && !MineManager.NO_AREA.equals(area)) m.area = area;
		minecraft.setScreen(new MineEditScreen(this, m, true));
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int maxScroll() {
		return Math.max(0, rows.size() * ROW_H - (listBottom - listTop));
	}

	private int rowAt(double mx, double my) {
		if (mx < left || mx >= right || my < listTop || my >= listBottom) return -1;
		int i = (int) ((my - listTop + scroll) / ROW_H);
		return i >= 0 && i < rows.size() ? i : -1;
	}

	private int moveLeft() {
		return right - EDIT_W - MOVE_W - 5;
	}

	private boolean onMove(double mx) {
		return mx >= moveLeft() && mx < moveLeft() + MOVE_W;
	}

	/** -1 für die obere Pfeilhälfte (hoch), +1 für die untere (runter). */
	private int moveDir(double my) {
		return (my - listTop + scroll) % ROW_H < ROW_H / 2.0 ? -1 : 1;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		rows = MineManager.get().visibleMines(ModConfig.get().sortMode);
		scroll = Mth.clamp(scroll, 0, maxScroll());

		g.drawString(font, title, left, 10, 0xFFFFAA00, true);
		SyncManager sync = SyncManager.get();
		boolean connected = sync.isActive() && "Verbunden".equals(sync.status());
		g.drawString(font, "Sync: " + sync.status(), left, 21, connected ? 0xFF55FF55 : 0xFF888888, false);
		String area = MineManager.get().activeArea();
		String areaText = font.plainSubstrByWidth(MineManager.areaLabel(area), areaRight() - areaLeft() - 38);
		g.drawCenteredString(font, areaText, (areaLeft() + areaRight()) / 2, 14, area.isEmpty() ? 0xFFAAAAAA : 0xFFFFFFFF);

		g.fill(left, listTop, right, listBottom, 0x70000000);
		g.enableScissor(left, listTop, right, listBottom);
		long now = System.currentTimeMillis();
		MineManager mgr = MineManager.get();
		int hovered = rowAt(mouseX, mouseY);
		for (int i = 0; i < rows.size(); i++) {
			int y = listTop + i * ROW_H - (int) scroll;
			if (y + ROW_H < listTop || y > listBottom) continue;
			renderRow(g, i, y, i == hovered, mouseX, mouseY, mgr, now);
		}
		g.disableScissor();

		if (rows.isEmpty()) {
			g.drawCenteredString(font, MineManager.get().mines().isEmpty()
					? "Noch keine Minen. Stell dich an eine Mine und klicke „+ Mine hier“."
					: "Keine Minen in diesem Lehen.", width / 2, listTop + 20, 0xFFAAAAAA);
		}

		if (hovered >= 0) {
			Mine mine = rows.get(hovered);
			boolean onEdit = mouseX >= right - EDIT_W - 4;
			List<Component> tip = new ArrayList<>();
			if (onEdit) {
				tip.add(Component.literal("Mine bearbeiten"));
			} else if (onMove(mouseX)) {
				tip.add(Component.literal(moveDir(mouseY) < 0 ? "Nach oben schieben" : "Nach unten schieben"));
				if (ModConfig.get().sortMode != ModConfig.SortMode.LIST) {
					tip.add(Component.literal("Schaltet die Sortierung auf „Reihenfolge“").withStyle(s -> s.withColor(0xAAAAAA)));
				}
			} else {
				TimerEntry t = mgr.timer(mine);
				if (mgr.isRunning(mine, now)) {
					tip.add(Component.literal("Bereit um " + TimeFormat.clock(t.readyAt)));
					if (t.by != null && !t.by.isEmpty()) tip.add(Component.literal("Markiert von " + t.by + " um " + TimeFormat.clock(t.startedAt)).withStyle(s -> s.withColor(0xAAAAAA)));
					tip.add(Component.literal("Rechtsklick: zurücksetzen").withStyle(s -> s.withColor(0xAAAAAA)));
					tip.add(Component.literal("Shift + Mausrad: ±5 Min, Strg + Mausrad: ±1 Min").withStyle(s -> s.withColor(0xAAAAAA)));
				} else {
					tip.add(Component.literal("Linksklick: Timer starten (" + TimeFormat.minutes(mine.respawnMinutes()) + ")"));
				}
			}
			g.setTooltipForNextFrame(font, tip, Optional.empty(), mouseX, mouseY);
		}
	}

	private void renderRow(GuiGraphics g, int index, int y, boolean hovered, int mouseX, int mouseY, MineManager mgr, long now) {
		Mine m = rows.get(index);
		long remaining = mgr.remainingMs(m, now);
		int color = HudOverlay.statusColor(remaining);

		g.fill(left + 1, y + 1, right - 1, y + ROW_H - 1, hovered ? 0x50FFFFFF : 0x30000000);
		g.fill(left + 1, y + 1, left + 4, y + ROW_H - 1, m.type.color);

		g.drawString(font, m.name, left + 8, y + 3, 0xFFFFFFFF, true);
		g.drawString(font, m.type.displayName, left + 8 + font.width(m.name) + 6, y + 3, m.type.color, false);

		StringBuilder info = new StringBuilder(m.x + " / " + m.y + " / " + m.z);
		if (minecraft.player != null) info.append("  ·  ").append(Math.round(m.distanceTo(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ()))).append(" m");
		info.append("  ·  ").append(TimeFormat.minutes(m.respawnMinutes()));
		info.append("  ·  ").append(m.size.label);
		g.drawString(font, info.toString(), left + 8, y + 13, 0xFF999999, false);

		int statusRight = moveLeft() - 4;
		String status = remaining <= 0 ? "BEREIT" : TimeFormat.duration(remaining);
		g.drawString(font, status, statusRight - font.width(status), y + 3, color, true);
		TimerEntry t = mgr.timer(m);
		if (remaining > 0 && t != null) {
			String sub = "um " + TimeFormat.clock(t.readyAt);
			g.drawString(font, sub, statusRight - font.width(sub), y + 13, 0xFF999999, false);
			long total = Math.max(1, t.readyAt - t.startedAt);
			float progress = Mth.clamp(1f - (float) remaining / total, 0f, 1f);
			int barL = left + 4, barR = right - 1;
			g.fill(barL, y + ROW_H - 2, barR, y + ROW_H - 1, 0x40FFFFFF);
			g.fill(barL, y + ROW_H - 2, barL + (int) ((barR - barL) * progress), y + ROW_H - 1, color);
		}

		boolean editHover = hovered && mouseX >= right - EDIT_W - 4;
		int ex = right - EDIT_W - 3;
		g.fill(ex, y + 4, ex + EDIT_W, y + ROW_H - 4, editHover ? 0x80FFFFFF : 0x40FFFFFF);
		g.drawCenteredString(font, "✎", ex + EDIT_W / 2, y + 8, 0xFFFFFFFF);

		int mx = moveLeft(), half = ROW_H / 2;
		boolean moveHover = hovered && onMove(mouseX);
		int hoverDir = moveHover ? moveDir(mouseY) : 0;
		if (index > 0) {
			g.fill(mx, y + 2, mx + MOVE_W, y + half, hoverDir < 0 ? 0x80FFFFFF : 0x30FFFFFF);
			g.drawCenteredString(font, "▲", mx + MOVE_W / 2, y + 3, 0xFFFFFFFF);
		}
		if (index < rows.size() - 1) {
			g.fill(mx, y + half, mx + MOVE_W, y + ROW_H - 2, hoverDir > 0 ? 0x80FFFFFF : 0x30FFFFFF);
			g.drawCenteredString(font, "▼", mx + MOVE_W / 2, y + half + 2, 0xFFFFFFFF);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		int i = rowAt(event.x(), event.y());
		if (i < 0) return false;
		Mine mine = rows.get(i);
		MineManager mgr = MineManager.get();
		if (event.x() >= right - EDIT_W - 4) {
			Sounds.click();
			minecraft.setScreen(new MineEditScreen(this, mine.copy(), false));
			return true;
		}
		if (onMove(event.x())) {
			boolean wasList = ModConfig.get().sortMode == ModConfig.SortMode.LIST;
			if (mgr.move(rows, i, moveDir(event.y()))) {
				Sounds.click();
				if (!wasList) rebuildWidgets();
			}
			return true;
		}
		if (event.button() == 0) {
			if (!mgr.isRunning(mine, System.currentTimeMillis())) {
				mgr.start(mine);
				Sounds.click();
			}
			return true;
		}
		if (event.button() == 1) {
			if (mgr.timer(mine) != null && mgr.timer(mine).readyAt != 0) {
				mgr.reset(mine);
				Sounds.click();
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		int i = rowAt(mx, my);
		boolean shift = minecraft.hasShiftDown(), ctrl = minecraft.hasControlDown();
		if (i >= 0 && (shift || ctrl) && MineManager.get().isRunning(rows.get(i), System.currentTimeMillis())) {
			long step = (shift ? 5 : 1) * 60_000L;
			MineManager.get().adjust(rows.get(i), scrollY > 0 ? step : -step);
			return true;
		}
		if (mx >= left && mx < right && my >= listTop && my < listBottom) {
			scroll = Mth.clamp(scroll - scrollY * ROW_H, 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (MinenTimerClient.openMenuKey.matches(event)) {
			onClose();
			return true;
		}
		if (MinenTimerClient.nextAreaKey.matches(event) || MinenTimerClient.prevAreaKey.matches(event)) {
			MinenTimerClient.switchArea(minecraft, MinenTimerClient.nextAreaKey.matches(event) ? 1 : -1);
			scroll = 0;
			return true;
		}
		return super.keyPressed(event);
	}
}
