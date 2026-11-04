package de.lehenskarte.gui;

import de.lehenskarte.Config;
import de.lehenskarte.data.LehenState;
import de.lehenskarte.xaero.XaeroBridge;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Einstellungen im Spiel (über Mod Menu oder /lehenskarte einstellungen). */
public class SettingsScreen extends Screen {
	private final Screen parent;

	public SettingsScreen(Screen parent) {
		super(Component.literal("Lehenskarte – Einstellungen"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config c = Config.get();
		int w = 150, h = 20, gap = 4;
		int left = width / 2 - w - gap / 2, right = width / 2 + gap / 2;
		int y = 40;

		toggle(left, y, w, h, "Karte", c.overlayEnabled, v -> c.overlayEnabled = v);
		toggle(right, y, w, h, "Anzeige oben (HUD)", c.hudEnabled, v -> c.hudEnabled = v);
		y += h + gap;
		slider(left, y, w, h, "Füllung", 0, 60, c.fillOpacity, v -> c.fillOpacity = v);
		slider(right, y, w, h, "Grenzen", 0, 100, c.borderOpacity, v -> c.borderOpacity = v);
		y += h + gap;
		toggle(left, y, w, h, "Wappen auf der Karte", c.bannersOnMap, v -> c.bannersOnMap = v);
		slider(right, y, w, h, "Wappengröße", 50, 200, c.bannerScale, v -> c.bannerScale = v);
		y += h + gap;
		toggle(left, y, w, h, "Zusammenfassen", c.groupOnZoomOut, v -> c.groupOnZoomOut = v);
		toggle(right, y, w, h, "Gemalte Karte", c.paintedMap, v -> c.paintedMap = v);
		y += h + gap;
		toggle(left, y, w, h, "Chat-Meldungen", c.notifyChanges, v -> c.notifyChanges = v);
		toggle(right, y, w, h, "Nur auf Ottonien", c.onlyOnOttonien, v -> c.onlyOnOttonien = v);
		y += h + gap;
		toggle(left, y, w, h, "NPC-Dörfer", c.npcVillages, v -> c.npcVillages = v);

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width / 2 - 100, height - 28, 200, 20).build());
	}

	private void toggle(int x, int y, int w, int h, String label, boolean value, Consumer<Boolean> set) {
		addRenderableWidget(CycleButton.onOffBuilder(value).create(x, y, w, h, Component.literal(label), (b, v) -> set.accept(v)));
	}

	private void slider(int x, int y, int w, int h, String label, int min, int max, int value, IntConsumer set) {
		addRenderableWidget(new AbstractSliderButton(x, y, w, h, Component.empty(), (value - min) / (double) (max - min)) {
			{
				updateMessage();
			}

			private int current() {
				return (int) Math.round(min + this.value * (max - min));
			}

			@Override
			protected void updateMessage() {
				setMessage(Component.literal(label + ": " + current() + " %"));
			}

			@Override
			protected void applyValue() {
				set.accept(current());
			}
		});
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		g.drawCenteredString(font, title, width / 2, 16, 0xFFFFFFFF);
		g.drawCenteredString(font, Component.literal("„Gemalte Karte“ wirkt vollständig erst nach einem Neustart"),
				width / 2, height - 44, 0xFF9A9A9A);
	}

	@Override
	public void onClose() {
		Config.get().save();
		LehenState.rebuild();
		XaeroBridge.refresh();
		minecraft.setScreen(parent);
	}
}
