package de.minentimer.gui;

import de.minentimer.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Overlay mit der Maus an eine beliebige Stelle ziehen. */
public class OverlayMoveScreen extends Screen {
	private final Screen parent;
	private final ModConfig cfg = ModConfig.get();
	private HudOverlay.Box box;
	private boolean dragging;
	private double grabX, grabY;

	public OverlayMoveScreen(Screen parent) {
		super(Component.literal("Overlay verschieben"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int by = height - 30;
		addRenderableWidget(Button.builder(Component.literal("Zurücksetzen"), b -> {
			cfg.overlayPosition = ModConfig.OverlayPosition.TOP_LEFT;
			cfg.overlayX = 0f;
			cfg.overlayY = 0f;
		}).bounds(width / 2 - 104, by, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose()).bounds(width / 2 + 4, by, 100, 20).build());
	}

	/** Zuletzt gezeichnete Lage des Overlays (null vor dem ersten Bild). */
	public HudOverlay.Box box() {
		return box;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// Kein Abdunkeln, damit man sieht, wo das Overlay im Spiel landet
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		box = HudOverlay.drawPreview(g);
		boolean hover = dragging || box.contains(mouseX, mouseY);
		int c = hover ? 0xFFFFAA00 : 0xAAFFFFFF;
		g.fill(box.x() - 1, box.y() - 1, box.x() + box.w() + 1, box.y(), c);
		g.fill(box.x() - 1, box.y() + box.h(), box.x() + box.w() + 1, box.y() + box.h() + 1, c);
		g.fill(box.x() - 1, box.y(), box.x(), box.y() + box.h(), c);
		g.fill(box.x() + box.w(), box.y(), box.x() + box.w() + 1, box.y() + box.h(), c);

		int ty = height - 64;
		g.fill(width / 2 - 130, ty - 4, width / 2 + 130, ty + 24, 0xA0000000);
		g.drawCenteredString(font, "Overlay mit der Maus an die gewünschte Stelle ziehen", width / 2, ty, 0xFFFFFFFF);
		g.drawCenteredString(font, "Größe änderst du in den Einstellungen", width / 2, ty + 12, 0xFFAAAAAA);
		super.render(g, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.button() == 0 && box != null && box.contains(event.x(), event.y())) {
			dragging = true;
			grabX = event.x() - box.x();
			grabY = event.y() - box.y();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!dragging || box == null) return super.mouseDragged(event, dx, dy);
		int spaceX = width - box.w(), spaceY = height - box.h();
		cfg.overlayPosition = ModConfig.OverlayPosition.CUSTOM;
		cfg.overlayX = spaceX > 0 ? (float) Mth.clamp((event.x() - grabX) / spaceX, 0, 1) : 0f;
		cfg.overlayY = spaceY > 0 ? (float) Mth.clamp((event.y() - grabY) / spaceY, 0, 1) : 0f;
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		cfg.save();
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
