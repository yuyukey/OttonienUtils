package de.lehenskarte.xaero;

import de.lehenskarte.data.LehenState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.highlight.AbstractHighlighter;

import java.util.List;

/** Färbt die Lehen in Xaero's World Map ein, über dieselbe Schnittstelle wie Open Parties and Claims. */
public class WorldMapLehenHighlighter extends AbstractHighlighter {
	public WorldMapLehenHighlighter() {
		// true: auch in noch nicht erkundeten Gebieten zeigen, wie auf der Website
		super(true);
	}

	private static boolean overworld(ResourceKey<Level> dim) {
		return Level.OVERWORLD.equals(dim);
	}

	@Override
	public int calculateRegionHash(ResourceKey<Level> dim, int regionX, int regionZ) {
		return overworld(dim) ? LehenState.regionHash(regionX, regionZ) : 0;
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
		return LehenState.chunkColors(chunkX, chunkZ, resultStore) ? resultStore : null;
	}

	// Namen zeigt die Lehenskarte selbst beim Überfahren an, darum hier keine Tooltips.
	@Override
	public Component getBlockHighlightSubtleTooltip(ResourceKey<Level> dim, int x, int z) {
		return null;
	}

	@Override
	public Component getBlockHighlightBluntTooltip(ResourceKey<Level> dim, int x, int z) {
		return null;
	}

	@Override
	public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dim, int x, int z, int width) {
	}
}
