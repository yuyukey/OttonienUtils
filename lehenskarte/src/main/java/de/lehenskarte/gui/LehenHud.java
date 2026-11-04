package de.lehenskarte.gui;

import de.lehenskarte.Config;
import de.lehenskarte.data.Banners;
import de.lehenskarte.data.LehenGeometry;
import de.lehenskarte.data.LehenState;
import de.lehenskarte.data.OttonienData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/** Kleine Anzeige (standardmäßig oben in der Mitte, verschiebbar): in welchem Lehen man gerade steht. */
public final class LehenHud {
	private LehenHud() {
	}

	/** Lehen an der Position des Spielers oder null. */
	public static LehenGeometry.Shape current(Minecraft mc) {
		if (mc.player == null || mc.level == null || mc.level.dimension() != Level.OVERWORLD) return null;
		return LehenState.geometry().at(mc.player.getX(), mc.player.getZ());
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!Config.get().hudEnabled || !LehenState.isActive() || mc.options.hideGui) return;
		if (mc.getDebugOverlay().showDebugScreen() || mc.screen != null) return;
		LehenGeometry.Shape s = current(mc);
		if (s == null) return;
		OttonienData.Snapshot data = LehenState.style().data();
		OttonienData.Region r = data.region(s.id());
		OttonienData.Faction f = data.owner(s.id());
		String lehen = r != null ? r.name() : s.id();
		// Das Gefolge ist wichtiger als der Name des Lehens: oben das Gefolge, darunter Lehen und Rang
		String line1 = f != null ? f.name() : lehen;
		String line2 = f != null ? lehen + " · " + f.rank() : (r != null && !r.enabled() ? "gesperrt" : "frei");

		Identifier tex = f != null ? Banners.texture(f.banner()) : null;
		draw(g, mc.font, line1, line2, tex, 0xFF000000 | LehenState.style().baseRgb()[s.index()]);
	}

	public static final int HEIGHT = 24;

	public static int width(Font font, String line1, String line2, boolean banner) {
		return (banner ? 20 : 0) + Math.max(font.width(line1), font.width(line2)) + 10;
	}

	/** Linke Kante: Config.hudX ist die Mitte der Anzeige als Anteil der Bildschirmbreite, am Rand begrenzt. */
	public static int left(int screenW, int w) {
		int x = (int) Math.round(Config.get().hudX * screenW - w / 2.0);
		return Math.max(2, Math.min(screenW - w - 2, x));
	}

	/** Obere Kante: Config.hudY ist der Anteil des freien Platzes über der Anzeige (0 = ganz oben). */
	public static int top(int screenH) {
		double f = Math.max(0, Math.min(1, Config.get().hudY));
		return 3 + (int) Math.round(f * Math.max(0, screenH - HEIGHT - 6));
	}

	/** Zeichnet die Anzeige an der eingestellten Stelle und gibt ihre Breite zurück. */
	static int draw(GuiGraphics g, Font font, String line1, String line2, Identifier tex, int accent) {
		int icon = tex != null ? 20 : 0;
		int w = width(font, line1, line2, tex != null);
		int x = left(g.guiWidth(), w), y = top(g.guiHeight());
		g.fill(x, y, x + w, y + HEIGHT, 0x90000000);
		g.fill(x, y + HEIGHT - 1, x + w, y + HEIGHT, accent);
		if (tex != null) g.blit(RenderPipelines.GUI_TEXTURED, tex, x + 4, y + 3, 0, 0, 18, 18, 16, 16, 16, 16);
		int tx = x + 5 + icon;
		g.drawString(font, line1, tx, y + 3, 0xFFFFD27A, true);
		g.drawString(font, line2, tx, y + 13, 0xFFDDDDDD, false);
		return w;
	}
}
