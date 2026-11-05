package de.lehenskarte.gui;

import de.lehenskarte.Config;
import de.lehenskarte.data.Banners;
import de.lehenskarte.data.LehenGeometry;
import de.lehenskarte.data.LehenState;
import de.lehenskarte.data.OttonienData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Anzeige oben mit der Maus an eine andere Stelle ziehen. */
public class HudPositionScreen extends Screen {
	private final Screen parent;
	private String line1 = "Gefolge", line2 = "Lehen · Rang";
	private Identifier tex;
	private int accent = 0xFFF4EEDC;
	private boolean dragging;
	private double grabX, grabY;
	private int boxW;

	public HudPositionScreen(Screen parent) {
		super(Component.literal("Anzeige verschieben"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		// Wenn man gerade in einem Lehen steht, dieses zeigen, sonst ein Beispiel
		LehenGeometry.Shape s = minecraft != null && LehenState.isActive() ? LehenHud.current(minecraft) : null;
		if (s != null) {
			OttonienData.Snapshot data = LehenState.style().data();
			OttonienData.Region r = data.region(s.id());
			OttonienData.Faction f = data.owner(s.id());
			String lehen = r != null ? r.name() : s.id();
			line1 = f != null ? f.name() : lehen;
			line2 = f != null ? lehen + " · " + f.rank() : (r != null && !r.enabled() ? "gesperrt" : "frei");
			String bannerName = Banners.forLehen(f != null ? f.banner() : "", r != null ? r.name() : "");
			tex = bannerName.isEmpty() ? null : Banners.texture(bannerName);
			accent = 0xFF000000 | LehenState.style().baseRgb()[s.index()];
		}
		int bw = 98, gap = 4, y = height / 2 + 20;
		addRenderableWidget(Button.builder(Component.literal("Zurücksetzen"), b -> {
			Config.get().hudX = 0.5;
			Config.get().hudY = 0;
		}).bounds(width / 2 - bw - gap / 2, y, bw, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width / 2 + gap / 2, y, bw, 20).build());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		boxW = LehenHud.draw(g, font, line1, line2, tex, accent);
		int x = LehenHud.left(width, boxW), y = LehenHud.top(height);
		int frame = dragging || inside(mouseX, mouseY) ? 0xFFFFD27A : 0x80FFFFFF;
		g.renderOutline(x - 1, y - 1, boxW + 2, LehenHud.HEIGHT + 2, frame);
		g.drawCenteredString(font, Component.literal("Anzeige mit der Maus an die gewünschte Stelle ziehen"),
				width / 2, height / 2 - 4, 0xFFFFFFFF);
	}

	private boolean inside(double mx, double my) {
		int x = LehenHud.left(width, boxW), y = LehenHud.top(height);
		return mx >= x && mx < x + boxW && my >= y && my < y + LehenHud.HEIGHT;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (e.button() == 0 && inside(e.x(), e.y())) {
			dragging = true;
			grabX = e.x() - (LehenHud.left(width, boxW) + boxW / 2.0);
			grabY = e.y() - LehenHud.top(height);
			return true;
		}
		return super.mouseClicked(e, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (!dragging) return super.mouseDragged(e, dx, dy);
		Config c = Config.get();
		c.hudX = Math.max(0, Math.min(1, (e.x() - grabX) / width));
		double free = Math.max(1, height - LehenHud.HEIGHT - 6);
		c.hudY = Math.max(0, Math.min(1, (e.y() - grabY - 3) / free));
		// in die Mitte einrasten
		if (Math.abs(c.hudX - 0.5) * width < 4) c.hudX = 0.5;
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
	public void onClose() {
		Config.get().save();
		minecraft.setScreen(parent);
	}
}
