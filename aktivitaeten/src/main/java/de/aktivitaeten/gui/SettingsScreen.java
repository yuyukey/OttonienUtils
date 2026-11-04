package de.aktivitaeten.gui;

import de.aktivitaeten.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Einstellungen im Spiel (über Mod Menu oder /aktivitaeten einstellungen). */
public class SettingsScreen extends Screen {
	private final Screen parent;

	public SettingsScreen(Screen parent) {
		super(Component.literal("Aktivitäten – Einstellungen"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config c = Config.get();
		int w = 150, h = 20, gap = 4;
		int left = width / 2 - w - gap / 2, right = width / 2 + gap / 2;
		int y = 40;

		toggle(left, y, w, h, "Liste", c.hudEnabled, v -> c.hudEnabled = v);
		addRenderableWidget(CycleButton.<Config.Position>builder(p -> Component.literal(p.label), c.position)
				.withValues(Config.Position.values())
				.create(right, y, w, h, Component.literal("Position"), (b, v) -> c.position = v));
		y += h + gap;
		slider(left, y, w, h, "Einträge", "", 1, 20, c.maxEntries, v -> c.maxEntries = v);
		slider(right, y, w, h, "Größe", " %", Config.MIN_SCALE, Config.MAX_SCALE, c.scale, v -> c.scale = v);
		y += h + gap;
		toggle(left, y, w, h, "Auch wenn leer", c.showWhenEmpty, v -> c.showWhenEmpty = v);
		toggle(right, y, w, h, "Chat-Meldungen", c.notifyNew, v -> c.notifyNew = v);
		y += h + gap;
		toggle(left, y, w, h, "Nur auf Ottonien", c.onlyOnOttonien, v -> c.onlyOnOttonien = v);
		addRenderableWidget(Button.builder(Component.literal("Verschieben und Größe …"), b -> {
			Config.get().save();
			minecraft.setScreen(new OverlayMoveScreen(this));
		}).bounds(right, y, w, h).build());

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width / 2 - 100, height - 28, 200, 20).build());
	}

	private void toggle(int x, int y, int w, int h, String label, boolean value, Consumer<Boolean> set) {
		addRenderableWidget(CycleButton.onOffBuilder(value).create(x, y, w, h, Component.literal(label), (b, v) -> set.accept(v)));
	}

	private void slider(int x, int y, int w, int h, String label, String unit, int min, int max, int value, IntConsumer set) {
		addRenderableWidget(new AbstractSliderButton(x, y, w, h, Component.empty(), (value - min) / (double) (max - min)) {
			{
				updateMessage();
			}

			private int current() {
				return (int) Math.round(min + this.value * (max - min));
			}

			@Override
			protected void updateMessage() {
				setMessage(Component.literal(label + ": " + current() + unit));
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
	}

	@Override
	public void onClose() {
		Config.get().save();
		minecraft.setScreen(parent);
	}
}
