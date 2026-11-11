package de.viehzucht.gui;

import de.viehzucht.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Tafel mit der Maus verschieben und mit dem Mausrad größer oder kleiner machen. */
public class HudPositionScreen extends Screen {
	private int[] box = {0, 0, 0, 0};
	private boolean dragging;
	private double lastX, lastY, restX, restY;

	public HudPositionScreen() {
		super(Component.literal("Viehzucht-Tafel anpassen"));
	}

	@Override
	protected void init() {
		int bw = 98, gap = 4, y = height - 28;
		addRenderableWidget(Button.builder(Component.literal("Zurücksetzen"), b -> {
			Config.get().offsetX = 0;
			Config.get().offsetY = 0;
			Config.get().hudScale = 1.0f;
		}).bounds(width / 2 - bw - gap / 2, y, bw, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width / 2 + gap / 2, y, bw, 20).build());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		box = InfoHud.drawPreview(g, font);
		int frame = dragging || inside(mouseX, mouseY) ? 0xFFFFD27A : 0x80FFFFFF;
		g.renderOutline(box[0] - 1, box[1] - 1, box[2] + 2, box[3] + 2, frame);
		g.drawCenteredString(font, Component.literal("Tafel mit der Maus ziehen · Mausrad: Größe"), width / 2, 12, 0xFFFFFFFF);
		g.drawCenteredString(font, Component.literal(String.format(Locale.ROOT, "Größe %d %%  ·  Verschiebung %d / %d",
				Math.round(InfoHud.scale() * 100), Config.get().offsetX, Config.get().offsetY)), width / 2, 24, 0xFFAAAAAA);
	}

	private boolean inside(double mx, double my) {
		return mx >= box[0] && mx < box[0] + box[2] && my >= box[1] && my < box[1] + box[3];
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (e.button() == 0 && inside(e.x(), e.y())) {
			dragging = true;
			lastX = e.x();
			lastY = e.y();
			restX = restY = 0;
			return true;
		}
		return super.mouseClicked(e, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (!dragging) return super.mouseDragged(e, dx, dy);
		Config c = Config.get();
		// Am Rand bleibt die Tafel stehen; dann nicht weiter in die Verschiebung zählen
		int[] before = InfoHud.position(width, height, box[2], box[3]);
		restX += e.x() - lastX;
		restY += e.y() - lastY;
		lastX = e.x();
		lastY = e.y();
		int mx = (int) restX, my = (int) restY;
		restX -= mx;
		restY -= my;
		c.offsetX += mx;
		c.offsetY += my;
		int[] after = InfoHud.position(width, height, box[2], box[3]);
		c.offsetX -= mx - (after[0] - before[0]);
		c.offsetY -= my - (after[1] - before[1]);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (dragging && e.button() == 0) {
			dragging = false;
			return true;
		}
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (sy == 0) return false;
		Config c = Config.get();
		c.hudScale = Math.max(0.5f, Math.min(2f, Math.round((InfoHud.scale() + (float) Math.signum(sy) * 0.05f) * 100) / 100f));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		Config.get().save();
		super.onClose();
	}
}
