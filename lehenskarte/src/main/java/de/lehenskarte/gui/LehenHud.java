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

/** Kleine Anzeige oben in der Mitte: in welchem Lehen man gerade steht. */
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
		String line1 = lehen;
		String line2 = f != null ? f.name() + " · " + f.rank() : (r != null && !r.enabled() ? "gesperrt" : "frei");

		Font font = mc.font;
		Identifier tex = f != null ? Banners.texture(f.banner()) : null;
		int icon = tex != null ? 20 : 0;
		int textW = Math.max(font.width(line1), font.width(line2));
		int w = icon + textW + 10;
		int h = 24;
		int x = (g.guiWidth() - w) / 2, y = 3;
		int accent = 0xFF000000 | LehenState.style().baseRgb()[s.index()];
		g.fill(x, y, x + w, y + h, 0x90000000);
		g.fill(x, y + h - 1, x + w, y + h, accent);
		if (tex != null) g.blit(RenderPipelines.GUI_TEXTURED, tex, x + 4, y + 3, 0, 0, 18, 18, 16, 16, 16, 16);
		int tx = x + 5 + icon;
		g.drawString(font, line1, tx, y + 3, 0xFFFFD27A, true);
		g.drawString(font, line2, tx, y + 13, 0xFFDDDDDD, false);
	}
}
