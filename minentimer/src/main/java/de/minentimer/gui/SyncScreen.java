package de.minentimer.gui;

import de.minentimer.BuiltInSync;
import de.minentimer.ModConfig;
import de.minentimer.SyncManager;
import de.minentimer.TimeFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Einstellungen zum Teilen der Timer (eigener Server oder Firebase). */
public class SyncScreen extends Screen {
	private final Screen parent;
	private final ModConfig cfg = ModConfig.get();
	private int left, top;

	public SyncScreen(Screen parent) {
		super(Component.literal("Minen-Timer – Mit anderen teilen"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int w = 310;
		left = width / 2 - w / 2;
		top = 30;
		int y = top;

		addRenderableWidget(CycleButton.onOffBuilder(cfg.syncEnabled)
				.create(left, y, 153, 20, Component.literal("Teilen"), (b, v) -> {
					cfg.syncEnabled = v;
					cfg.save();
					SyncManager.get().requestPoll();
				}));
		addRenderableWidget(Button.builder(Component.literal("Jetzt synchronisieren"), b -> {
			cfg.save();
			SyncManager.get().requestPoll();
		}).bounds(left + 157, y, 153, 20).build());

		y += 36;
		EditBox urlBox = new EditBox(font, left, y, w, 20, Component.literal("Server-URL"));
		urlBox.setMaxLength(256);
		urlBox.setValue(cfg.syncUrl);
		urlBox.setHint(Component.literal(BuiltInSync.URL).withStyle(s -> s.withColor(0x777777)));
		urlBox.setResponder(s -> cfg.syncUrl = s.trim());
		addRenderableWidget(urlBox);

		y += 36;
		EditBox groupBox = new EditBox(font, left, y, w - 84, 20, Component.literal("Gruppen-Code"));
		groupBox.setMaxLength(64);
		groupBox.setValue(cfg.syncGroup);
		groupBox.setHint(Component.literal("z. B. clan-eisenwerk-7381").withStyle(s -> s.withColor(0x777777)));
		groupBox.setResponder(s -> cfg.syncGroup = s.trim());
		addRenderableWidget(groupBox);
		addRenderableWidget(Button.builder(Component.literal("Kopieren"), b -> {
			minecraft.keyboardHandler.setClipboard(cfg.syncGroup);
			b.setMessage(Component.literal("Kopiert ✔"));
		}).bounds(left + w - 80, y, 80, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose()).bounds(width / 2 - 75, height - 26, 150, 20).build());
	}

	@Override
	public void onClose() {
		// Leere Adresse heißt: wieder der eingebaute Server (der Gruppen-Code bleibt)
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
		g.drawString(font, "Server-URL (leer lassen für " + BuiltInSync.URL.replace("https://", "") + ")", left, top + 25, gray, false);
		g.drawString(font, "Gruppen-Code (alle in der Gruppe brauchen denselben)", left, top + 61, gray, false);

		SyncManager sync = SyncManager.get();
		String status = "Status: " + sync.status();
		if (sync.lastSuccess() > 0) status += "  (zuletzt " + TimeFormat.clock(sync.lastSuccess()) + ")";
		boolean ok = sync.isActive() && "Verbunden".equals(sync.status());
		g.drawString(font, "Teile diesen Code mit deinen Freunden: Sie tragen ihn hier ein,", left, top + 97, 0xFFFFDD55, false);
		g.drawString(font, "dann seht ihr dieselben Minen und Timer.", left, top + 108, 0xFFFFDD55, false);
		g.drawString(font, status, left, top + 124, ok ? 0xFF55FF55 : 0xFFFF7777, false);
		g.drawString(font, "Der Code ist wie ein Passwort – nur an deine Gruppe geben.", left, top + 140, gray, false);
	}
}
