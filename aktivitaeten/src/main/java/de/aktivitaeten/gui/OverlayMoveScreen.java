package de.aktivitaeten.gui;

import de.aktivitaeten.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Liste mit der Maus verschieben und mit dem Mausrad oder den Knöpfen größer und kleiner machen. */
public class OverlayMoveScreen extends Screen {
	private static final int STEP = 10;
	private final Screen parent;
	private final Config cfg = Config.get();
	private ActivityHud.Box box;
	private boolean dragging;
	private double grabX, grabY;

	public OverlayMoveScreen(Screen parent) {
		super(Component.literal("Aktivitäten verschieben"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int by = height - 30;
		addRenderableWidget(Button.builder(Component.literal("−"), b -> resize(-STEP)).bounds(width / 2 - 160, by, 20, 20).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> resize(STEP)).bounds(width / 2 - 136, by, 20, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Zurücksetzen"), b -> {
			cfg.position = Config.Position.MIDDLE_RIGHT;
			cfg.overlayX = 1f;
			cfg.overlayY = 0.5f;
			cfg.scale = 100;
		}).bounds(width / 2 - 104, by, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose()).bounds(width / 2 + 4, by, 100, 20).build());
	}

	/** Größe ändern, die Mitte der Liste bleibt dabei ungefähr an ihrer Stelle. */
	private void resize(int delta) {
		int next = Mth.clamp(cfg.scale + delta, Config.MIN_SCALE, Config.MAX_SCALE);
		if (next == cfg.scale) return;
		if (box != null) {
			// in freie Lage wechseln, damit die Liste beim Vergrößern nicht an einen Rand springt
			double cx = box.x() + box.w() / 2.0, cy = box.y() + box.h() / 2.0;
			double f = next / (double) cfg.scale;
			double w = box.w() * f, h = box.h() * f;
			place(cx - w / 2, cy - h / 2, w, h);
		}
		cfg.scale = next;
	}

	/** Freie Lage aus der linken oberen Ecke in GUI-Koordinaten setzen. */
	private void place(double left, double top, double w, double h) {
		double spaceX = width - w, spaceY = height - h;
		cfg.position = Config.Position.CUSTOM;
		cfg.overlayX = spaceX > 0 ? (float) Mth.clamp(left / spaceX, 0, 1) : 0f;
		cfg.overlayY = spaceY > 0 ? (float) Mth.clamp(top / spaceY, 0, 1) : 0f;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// Kein Abdunkeln, damit man sieht, wo die Liste im Spiel landet
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		box = ActivityHud.drawPreview(g);
		boolean hover = dragging || box.contains(mouseX, mouseY);
		int c = hover ? 0xFFE8C170 : 0x90FFFFFF;
		g.fill(box.x() - 1, box.y() - 1, box.x() + box.w() + 1, box.y(), c);
		g.fill(box.x() - 1, box.y() + box.h(), box.x() + box.w() + 1, box.y() + box.h() + 1, c);
		g.fill(box.x() - 1, box.y(), box.x(), box.y() + box.h(), c);
		g.fill(box.x() + box.w(), box.y(), box.x() + box.w() + 1, box.y() + box.h(), c);

		int ty = height - 64;
		g.fill(width / 2 - 140, ty - 4, width / 2 + 140, ty + 24, 0xA0000000);
		g.drawCenteredString(font, "Liste mit der Maus an die gewünschte Stelle ziehen", width / 2, ty, 0xFFFFFFFF);
		g.drawCenteredString(font, "Mausrad oder − / +: Größe " + cfg.scale + " %", width / 2, ty + 12, 0xFFAAAAAA);
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
		place(event.x() - grabX, event.y() - grabY, box.w(), box.h());
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			resize(scrollY > 0 ? STEP : -STEP);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
