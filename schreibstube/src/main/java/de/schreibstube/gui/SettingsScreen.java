package de.schreibstube.gui;

import de.schreibstube.Config;
import de.schreibstube.text.RichText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Einstellungen (über Mod Menu, das Zahnrad in der Schreibstube oder /schreibstube einstellungen). */
public class SettingsScreen extends Screen {
	private final Screen parent;
	private EditBox itemName, signature;

	public SettingsScreen(Screen parent) {
		super(Component.literal("Schreibstube – Einstellungen"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config c = Config.get();
		int w = 150, h = 20, gap = 4;
		int left = width / 2 - w - gap / 2, right = width / 2 + gap / 2;
		int y = 34;

		toggle(left, y, w, h, "Öffnen per Rechtsklick", c.openOnUse, v -> c.openOnUse = v,
				"Rechtsklick mit Pergament und Feder öffnet die Schreibstube");
		toggle(right, y, w, h, "Danach verschicken", c.askRecipientAfterWrite, v -> c.askRecipientAfterWrite = v,
				"Öffnet nach dem Schreiben die Empfänger-Auswahl");
		y += h + gap;
		toggle(left, y, w, h, "Server-Echo ausblenden", c.hideServerEcho, v -> c.hideServerEcho = v,
				"Blendet „Du hast den Brief bearbeitet“ beim Schreiben aus");
		addRenderableWidget(new AbstractSliderButton(right, y, w, h, Component.empty(),
				(c.delayMs - Config.MIN_DELAY) / (double) (Config.MAX_DELAY - Config.MIN_DELAY)) {
			{
				updateMessage();
				setTooltip(Tooltip.create(Component.literal("Pause zwischen zwei /letter-Befehlen. Zu kurz kann vom Server als Spam gewertet werden.")));
			}

			private int current() {
				return (int) (Math.round((Config.MIN_DELAY + value * (Config.MAX_DELAY - Config.MIN_DELAY)) / 100.0) * 100);
			}

			@Override
			protected void updateMessage() {
				setMessage(Component.literal("Pause: " + String.format(java.util.Locale.GERMANY, "%.1f", current() / 1000.0) + " s"));
			}

			@Override
			protected void applyValue() {
				c.delayMs = current();
			}
		});
		y += h + gap;
		toggle(left, y, w, h, "Kopieren mit &-Codes", c.copyWithCodes, v -> c.copyWithCodes = v,
				"Strg+C nimmt die Formatierung als &-Codes mit (Strg+Umschalt+C kopiert immer ohne)");
		toggle(right, y, w, h, "&-Codes beim Einfügen", c.pasteCodes, v -> c.pasteCodes = v,
				"Eingefügte &6/&l-Codes werden zu Formatierung");
		y += h + gap;
		toggle(left, y, w, h, "Nur auf Ottonien", c.onlyOnOttonien, v -> c.onlyOnOttonien = v,
				"Rechtsklick-Öffnen nur auf Servern mit „ottonien“ in der Adresse");

		y += h + gap + 12;
		itemName = addRenderableWidget(new EditBox(font, left, y, w * 2 + gap, h, Component.literal("Schreibzeug")));
		itemName.setMaxLength(64);
		itemName.setValue(c.itemName);
		itemName.setResponder(s -> {
			if (!s.isBlank()) c.itemName = s;
		});
		y += h + gap + 12;
		signature = addRenderableWidget(new EditBox(font, left, y, w * 2 + gap, h, Component.literal("Signatur")));
		signature.setMaxLength(240);
		signature.setValue(c.signature.replace("\n", "\\n"));
		signature.setResponder(s -> c.signature = s.replace("\\n", "\n"));

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width / 2 - 100, height - 28, 200, 20).build());
	}

	private void toggle(int x, int y, int w, int h, String label, boolean value, Consumer<Boolean> set, String tip) {
		CycleButton<Boolean> b = CycleButton.onOffBuilder(value).create(x, y, w, h, Component.literal(label), (btn, v) -> set.accept(v));
		b.setTooltip(Tooltip.create(Component.literal(tip)));
		addRenderableWidget(b);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		Ui.wood(g, 0, 0, width, 28);
		g.drawCenteredString(font, Component.literal(title.getString()).withStyle(ChatFormatting.BOLD), width / 2, 10, Ui.GOLD);
		super.render(g, mouseX, mouseY, partial);
		int left = width / 2 - 152;
		g.drawString(font, "Name des Schreibzeugs (Teil reicht)", left, itemName.getY() - 11, 0xFFCFC2A8, true);
		g.drawString(font, "Signatur – &-Codes, \\n = neue Zeile, %spieler% = dein Name", left, signature.getY() - 11, 0xFFCFC2A8, true);
		// Signatur als Vorschau
		String name = minecraft.player != null ? minecraft.player.getGameProfile().name() : "Spieler";
		RichText sig = RichText.parseLegacy(Config.get().signature.replace("%spieler%", name), true);
		int py = signature.getY() + 24;
		if (py + 20 > height - 30) {
			// wenig Platz: Vorschau rechts neben die Überschrift
			py = signature.getY() - 11;
			left = width / 2 + 154;
			if (left + 60 > width) return;
		}
		int x0 = left;
		int lineStart = 0;
		int row = 0;
		for (int i = 0; i <= sig.length(); i++) {
			if (i == sig.length() || sig.charAt(i) == '\n') {
				g.drawString(font, Component.literal(sig.toLegacy(lineStart, i, '§', 0)), x0, py + row * 10, 0xFFFFFFFF, true);
				row++;
				lineStart = i + 1;
			}
		}
	}

	@Override
	public void onClose() {
		Config.get().save();
		minecraft.setScreen(parent);
	}
}
