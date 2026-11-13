package de.viehzucht.data;

import de.viehzucht.Config;
import de.viehzucht.ViehzuchtClient;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Kühe, deren Paarung die Mod gesehen hat, leuchten, bis sie sich wieder paaren können. */
public final class Breeding {
	/** Farbe des Leuchtens. */
	public static final int COLOR = 0xFF6FA8;
	/** Wartezeit eines Rinds laut Ottoniens Tabelle, falls die Art unbekannt ist. */
	private static final long COW_COOLDOWN_MS = 24 * 3_600_000L;

	private Breeding() {
	}

	/** Für das Leuchten: Kuh nach beobachteter Paarung, Wartezeit noch nicht um. */
	public static boolean glows(Entity e) {
		if (e == null || e.getType() != EntityType.COW || !ViehzuchtClient.isActive() || !Config.get().glowBred) return false;
		AnimalInfo info = AnimalStore.get(e.getUUID());
		if (info == null || info.bredAt <= 0) return false;
		Species species = Species.byName(info.name != null ? info.name : e.getCustomName() != null ? e.getCustomName().getString() : null);
		return info.breedReadyAt(species != null ? species.cooldownMs() : COW_COOLDOWN_MS) > System.currentTimeMillis();
	}
}
