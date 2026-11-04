package de.lehenskarte.xaero;

import de.lehenskarte.data.LehenState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.common.minimap.highlight.AbstractHighlighter;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

/** Färbt die Lehen in Xaero's Minimap ein. */
public class MinimapLehenHighlighter extends AbstractHighlighter {
	public MinimapLehenHighlighter() {
		super(true);
	}

	private static boolean overworld(ResourceKey<Level> dim) {
		return Level.OVERWORLD.equals(dim);
	}

	@Override
	public boolean regionHasHighlights(ResourceKey<Level> dim, int regionX, int regionZ) {
		return overworld(dim) && LehenState.regionHasLehen(regionX, regionZ);
	}

	@Override
	public boolean chunkIsHighlit(ResourceKey<Level> dim, int chunkX, int chunkZ) {
		return overworld(dim) && LehenState.chunkHasLehen(chunkX, chunkZ);
	}

	@Override
	public int[] getChunkHighlitColor(ResourceKey<Level> dim, int chunkX, int chunkZ) {
		if (!overworld(dim)) return null;
		return LehenState.chunkColors(chunkX, chunkZ, resultStore, true) ? resultStore : null;
	}

	@Override
	public void addBlockHighlightTooltips(InfoDisplayCompiler compiler, ResourceKey<Level> dim, int x, int z, int width) {
	}
}
