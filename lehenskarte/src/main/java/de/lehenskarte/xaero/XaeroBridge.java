package de.lehenskarte.xaero;

import de.lehenskarte.LehenskarteClient;
import net.fabricmc.loader.api.FabricLoader;

/** Sagt Xaero, dass sich Farben geändert haben. Jede Mod wird nur angefasst, wenn sie installiert ist. */
public final class XaeroBridge {
	public static final boolean WORLD_MAP = FabricLoader.getInstance().isModLoaded("xaeroworldmap");
	public static final boolean MINIMAP = FabricLoader.getInstance().isModLoaded("xaerominimap");

	private XaeroBridge() {
	}

	public static void refresh() {
		if (WORLD_MAP) {
			try {
				WorldMapAccess.refresh();
			} catch (Throwable t) {
				LehenskarteClient.LOGGER.debug("World Map nicht aktualisiert", t);
			}
		}
		if (MINIMAP) {
			try {
				MinimapAccess.refresh();
			} catch (Throwable t) {
				LehenskarteClient.LOGGER.debug("Minimap nicht aktualisiert", t);
			}
		}
	}

	private static final class WorldMapAccess {
		static void refresh() {
			xaero.map.WorldMapSession session = xaero.map.WorldMapSession.getCurrentSession();
			if (session == null || session.getMapProcessor() == null) return;
			xaero.map.world.MapWorld world = session.getMapProcessor().getMapWorld();
			if (world == null || world.getCurrentDimension() == null) return;
			world.getCurrentDimension().getHighlightHandler().clearCachedHashes();
		}
	}

	private static final class MinimapAccess {
		static void refresh() {
			// So holt sich auch Xaero selbst die Minimap-Sitzung
			Object session = xaero.hud.minimap.BuiltInHudModules.MINIMAP.getCurrentSession();
			if (!(session instanceof xaero.hud.minimap.module.MinimapSession mm) || mm.getProcessor() == null) return;
			var writer = mm.getProcessor().getMinimapWriter();
			if (writer == null || writer.getDimensionHighlightHandler() == null) return;
			writer.getDimensionHighlightHandler().requestRefresh();
		}
	}
}
