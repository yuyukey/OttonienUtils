package de.minentimer.gui;

import de.minentimer.BuiltInSync;
import de.minentimer.MineManager;
import de.minentimer.ModConfig;
import de.minentimer.SyncManager;
import de.minentimer.TimeFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Einstellungen zum Teilen: ein Code je Lehen. Minen ohne Lehen bleiben bei einem selbst. */
public class SyncScreen extends Screen {
	private static final int ROW_H = 22;
	private final Screen parent;
	private final ModConfig cfg = ModConfig.get();
	private int left, top, rowsTop, rowsShown, page;
	/** Statt der Codes die Server-Adresse zeigen (braucht fast niemand). */
	private boolean showServer;
	private EditBox joinBox;
	/** Lehen, bei dem „Neu“ schon einmal gedrückt wurde und auf Bestätigung wartet. */
	private String confirmChange;
	private String joinText = "";
	private final List<Row> rows = new ArrayList<>();

	/** Eine Zeile der Code-Liste. pending = eingetragen, aber noch kein Lehen bekannt. */
	private record Row(String label, String code, String area, boolean pending) {
	}

	public SyncScreen(Screen parent) {
		super(Component.literal("Minen-Timer – Mit anderen teilen"));
		this.parent = parent;
	}

	private void collectRows() {
		rows.clear();
		Map<String, String> areas = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (String a : MineManager.get().areas()) areas.putIfAbsent(a, a);
		for (String a : cfg.lehenCodes.keySet()) if (!cfg.hasLeft(a)) areas.putIfAbsent(a, a);
		for (String a : areas.values()) rows.add(new Row(a, cfg.codeFor(a), a, false));
		for (String c : cfg.joinCodes) rows.add(new Row("Wartet auf Daten …", c, null, true));
	}

	@Override
	protected void init() {
		int w = 330;
		left = width / 2 - w / 2;
		top = 30;
		int y = top;
		collectRows();

		addRenderableWidget(CycleButton.onOffBuilder(cfg.syncEnabled)
				.create(left, y, 106, 20, Component.literal("Teilen"), (b, v) -> {
					cfg.syncEnabled = v;
					cfg.save();
					SyncManager.get().requestPoll();
				}));
		addRenderableWidget(Button.builder(Component.literal("Jetzt abgleichen"), b -> {
			cfg.save();
			SyncManager.get().requestPoll();
		}).bounds(left + 111, y, 108, 20).build());
		addRenderableWidget(Button.builder(Component.literal(showServer ? "Codes …" : "Server …"), b -> {
			showServer = !showServer;
			rebuildWidgets();
		}).bounds(left + 224, y, 106, 20).build());

		rowsTop = y + 36;
		if (showServer) {
			EditBox urlBox = new EditBox(font, left, rowsTop, w, 20, Component.literal("Server-URL"));
			urlBox.setMaxLength(256);
			urlBox.setValue(cfg.syncUrl);
			urlBox.setHint(Component.literal(BuiltInSync.URL).withStyle(s -> s.withColor(0x777777)));
			urlBox.setResponder(s -> cfg.syncUrl = s.trim());
			addRenderableWidget(urlBox);
			addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose()).bounds(width / 2 - 75, height - 24, 150, 20).build());
			return;
		}
		// Unten: Eingabefeld, zwei Hinweiszeilen und Fertig
		rowsShown = Math.max(1, (height - rowsTop - 84) / ROW_H);
		int pages = Math.max(1, (rows.size() + rowsShown - 1) / rowsShown);
		page = Math.min(page, pages - 1);
		int ry = rowsTop;
		for (int i = page * rowsShown; i < Math.min(rows.size(), (page + 1) * rowsShown); i++) {
			Row row = rows.get(i);
			Button copy = Button.builder(Component.literal("Kopieren"), b -> {
				minecraft.keyboardHandler.setClipboard(row.code());
				b.setMessage(Component.literal("Kopiert ✔"));
			}).bounds(left + 192, ry, 62, 20).build();
			copy.setTooltip(Tooltip.create(Component.literal("Codes sind wie Passwörter – nur an Leute geben, die dieses Lehen sehen dürfen.")));
			addRenderableWidget(copy);
			if (!row.pending()) {
				boolean sure = row.area().equalsIgnoreCase(confirmChange == null ? "" : confirmChange);
				Button change = Button.builder(Component.literal(sure ? "Sicher?" : "Neu"), b -> {
					if (!sure) {
						confirmChange = row.area();
					} else {
						confirmChange = null;
						SyncManager.get().changeCode(row.area());
					}
					rebuildWidgets();
				}).bounds(left + 258, ry, 48, 20).build();
				change.setTooltip(Tooltip.create(Component.literal(
						"Neuer Code für dieses Lehen, z. B. wenn der alte bei den Falschen gelandet ist. "
								+ "Wer den alten hat, sieht das Lehen nicht mehr – gib den neuen allen, die weiter mitmachen sollen.")));
				addRenderableWidget(change);
			}
			{
				Button leave = Button.builder(Component.literal("✕"), b -> {
					if (row.pending()) {
						cfg.joinCodes.remove(row.code());
						cfg.save();
					} else {
						MineManager.get().leaveLehen(row.area());
					}
					rebuildWidgets();
				}).bounds(left + 310, ry, 20, 20).build();
				leave.setTooltip(Tooltip.create(Component.literal(row.pending()
						? "Code wieder entfernen"
						: "Lehen verlassen: Seine Minen verschwinden nur bei dir.")));
				addRenderableWidget(leave);
			}
			ry += ROW_H;
		}
		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
				page = Math.floorMod(page - 1, pages);
				rebuildWidgets();
			}).bounds(left + w - 46, rowsTop - 14, 20, 12).build());
			addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
				page = (page + 1) % pages;
				rebuildWidgets();
			}).bounds(left + w - 20, rowsTop - 14, 20, 12).build());
		}

		int jy = height - 74;
		joinBox = new EditBox(font, left, jy, w - 94, 20, Component.literal("Code eintragen"));
		joinBox.setMaxLength(64);
		joinBox.setValue(joinText);
		joinBox.setHint(Component.literal("Code, den dir jemand gegeben hat").withStyle(s -> s.withColor(0x777777)));
		joinBox.setResponder(s -> joinText = s);
		addRenderableWidget(joinBox);
		addRenderableWidget(Button.builder(Component.literal("Eintragen"), b -> {
			SyncManager.get().join(joinText);
			joinText = "";
			rebuildWidgets();
		}).bounds(left + w - 90, jy, 90, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose()).bounds(width / 2 - 75, height - 24, 150, 20).build());
	}

	@Override
	public void onClose() {
		// Leere Adresse heißt: wieder der eingebaute Server (die Codes bleiben)
		if (cfg.syncUrl.isBlank()) cfg.useBuiltInSync();
		cfg.save();
		SyncManager.get().requestPoll();
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		g.drawCenteredString(font, title, width / 2, 10, 0xFFFFAA00);
		int gray = 0xFFAAAAAA;
		if (showServer) {
			g.drawString(font, "Server-URL (leer lassen für " + BuiltInSync.URL.replace("https://", "") + ")", left, rowsTop - 12, gray, false);
			return;
		}
		g.drawString(font, "Codes – jedes Lehen hat einen eigenen:", left, rowsTop - 12, gray, false);
		if (rows.isEmpty()) {
			g.drawString(font, "Noch keine Lehen. Gib einer Mine ein Lehen, dann", left, rowsTop + 6, gray, false);
			g.drawString(font, "bekommt es einen Code. Minen ohne Lehen teilst du nicht.", left, rowsTop + 17, gray, false);
		}

		int ry = rowsTop;
		for (int i = page * rowsShown; i < Math.min(rows.size(), (page + 1) * rowsShown); i++) {
			Row row = rows.get(i);
			int color = row.pending() ? 0xFFAAAAAA : 0xFF55FFFF;
			String label = font.plainSubstrByWidth(row.label(), 98);
			g.drawString(font, label, left, ry + 6, color, false);
			g.drawString(font, font.plainSubstrByWidth(row.code(), 86), left + 102, ry + 6, 0xFFFFDD55, false);
			ry += ROW_H;
		}

		int ty = height - 48;
		g.drawString(font, "Gib den Code eines Lehens allen, die dort Rechte haben.", left, ty, 0xFFFFDD55, false);
		SyncManager sync = SyncManager.get();
		String status = "Status: " + sync.status();
		if (sync.lastSuccess() > 0) status += "  (zuletzt " + TimeFormat.clock(sync.lastSuccess()) + ")";
		boolean ok = sync.isActive() && "Verbunden".equals(sync.status());
		g.drawString(font, status, left, ty + 11, ok ? 0xFF55FF55 : 0xFFFF7777, false);
	}
}
