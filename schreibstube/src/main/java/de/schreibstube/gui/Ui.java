package de.schreibstube.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Gemeinsame Zeichenhilfen: Pergament, Holzleiste, kleine Knöpfe. */
public final class Ui {
	public static final int WOOD = 0xF02A1B0F;
	public static final int WOOD_EDGE = 0xFF8B6B3E;
	public static final int GOLD = 0xFFE8C170;
	public static final int PAPER_TOP = 0xFFF4E9CD, PAPER_BOTTOM = 0xFFE7D5AB;

	private Ui() {
	}

	/** Pergament mit Schatten und dunklem Rand. */
	public static void parchment(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x55000000);
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF4A3218);
		g.fillGradient(x, y, x + w, y + h, PAPER_TOP, PAPER_BOTTOM);
		g.renderOutline(x + 1, y + 1, w - 2, h - 2, 0x22704A20);
		// leicht gealterte Ecken
		g.fillGradient(x, y + h - 10, x + w, y + h, 0x00000000, 0x14704A20);
	}

	/** Dunkle Holzleiste mit goldener Kante. */
	public static void wood(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, WOOD);
		g.fill(x, y, x + w, y + 1, WOOD_EDGE);
		g.fill(x, y + h - 1, x + w, y + h, WOOD_EDGE);
	}

	/** Kleiner, frei gezeichneter Knopf. */
	public abstract static class SmallButton extends AbstractButton {
		private final Runnable action;

		protected SmallButton(int x, int y, int w, int h, Component label, String tooltip, Runnable action) {
			super(x, y, w, h, label);
			this.action = action;
			if (tooltip != null) setTooltip(Tooltip.create(Component.literal(tooltip)));
		}

		@Override
		public void onPress(InputWithModifiers input) {
			action.run();
			// Fokus abgeben, damit Eingaben wieder im Brief landen
			if (Minecraft.getInstance().screen != null) Minecraft.getInstance().screen.setFocused(null);
		}

		protected abstract boolean selected();

		@Override
		protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			int edge = selected() ? GOLD : isHoveredOrFocused() ? 0xFFC9A66B : 0xFF5C4325;
			g.fill(x, y, x + w, y + h, edge);
			drawFace(g, x + 1, y + 1, w - 2, h - 2);
		}

		protected abstract void drawFace(GuiGraphics g, int x, int y, int w, int h);

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			defaultButtonNarrationText(out);
		}
	}
}
