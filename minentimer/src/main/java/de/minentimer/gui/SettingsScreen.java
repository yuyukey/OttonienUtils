package de.minentimer.gui;

import de.minentimer.ModConfig;
import de.minentimer.Sounds;
import de.minentimer.SyncManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Einstellungen für Overlay, Ton und Schnellmarkierung. */
public class SettingsScreen extends Screen {
	private final Screen parent;
	private final ModConfig cfg = ModConfig.get();
	private int left, overlayLabelY, soundLabelY;

	public SettingsScreen(Screen parent) {
		super(Component.literal("Minen-Timer – Einstellungen"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int w = 310, cw = 153;
		left = width / 2 - w / 2;
		int right = left + w - cw;
		int y = 22;

		overlayLabelY = y;
		y += 11;
		addRenderableWidget(CycleButton.onOffBuilder(cfg.overlayEnabled)
				.create(left, y, cw, 20, Component.literal("Overlay"), (b, v) -> cfg.overlayEnabled = v));
		addRenderableWidget(CycleButton.<ModConfig.OverlayMode>builder(m -> Component.literal(m.label), cfg.overlayMode)
				.withValues(ModConfig.OverlayMode.values())
				.create(right, y, cw, 20, Component.literal("Zeigen"), (b, v) -> cfg.overlayMode = v));
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("Overlay verschieben…"), b -> {
			cfg.save();
			minecraft.setScreen(new OverlayMoveScreen(this));
		}).bounds(left, y, cw, 20).build());
		addRenderableWidget(new IntSlider(right, y, cw, 50, 150, 10, cfg.overlayScalePercent, v -> "Größe: " + v + " %", v -> cfg.overlayScalePercent = v));
		y += 24;
		addRenderableWidget(CycleButton.onOffBuilder(cfg.overlayShowDistance)
				.create(left, y, cw, 20, Component.literal("Entfernung"), (b, v) -> cfg.overlayShowDistance = v));
		addRenderableWidget(new IntSlider(right, y, cw, 0, 30, 1, cfg.soonMinutes, v -> v == 0 ? "Gelb: aus" : "Gelb ab " + v + " Min", v -> cfg.soonMinutes = v));

		y += 26;
		soundLabelY = y;
		y += 11;
		addRenderableWidget(CycleButton.onOffBuilder(cfg.soundEnabled)
				.create(left, y, cw, 20, Component.literal("Ton"), (b, v) -> cfg.soundEnabled = v));
		addRenderableWidget(new IntSlider(right, y, cw, 0, 100, 5, cfg.soundVolumePercent, v -> "Lautstärke: " + v + " %", v -> cfg.soundVolumePercent = v));
		y += 24;
		addRenderableWidget(CycleButton.<ModConfig.Sound>builder(s -> Component.literal(s.label), cfg.sound)
				.withValues(ModConfig.Sound.values())
				.create(left, y, cw, 20, Component.literal("Ton-Art"), (b, v) -> cfg.sound = v));
		addRenderableWidget(Button.builder(Component.literal("▶ Ton testen"), b -> Sounds.playReady()).bounds(right, y, cw, 20).build());
		y += 24;
		addRenderableWidget(CycleButton.onOffBuilder(cfg.chatMessage)
				.create(left, y, cw, 20, Component.literal("Chat-Meldung"), (b, v) -> cfg.chatMessage = v));
		addRenderableWidget(new IntSlider(right, y, cw, 5, 200, 5, cfg.quickMarkRadius, v -> "Taste O Radius: " + v, v -> cfg.quickMarkRadius = v));

		int by = Math.max(y + 28, height - 26);
		addRenderableWidget(Button.builder(Component.literal("Mit anderen teilen…"), b -> {
			cfg.save();
			minecraft.setScreen(new SyncScreen(this));
		}).bounds(left, by, cw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose()).bounds(right, by, cw, 20).build());
	}

	@Override
	public void onClose() {
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
		g.drawCenteredString(font, title, width / 2, 8, 0xFFFFAA00);
		g.drawString(font, "Overlay", left, overlayLabelY, 0xFFAAAAAA, false);
		g.drawString(font, "Ton & Meldungen", left, soundLabelY, 0xFFAAAAAA, false);
	}
}
