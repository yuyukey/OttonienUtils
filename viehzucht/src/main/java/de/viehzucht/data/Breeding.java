package de.viehzucht.data;

import de.viehzucht.Config;
import de.viehzucht.ViehzuchtClient;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.AgeableMob;

/**
 * Zuchttiere nach einer Paarung leuchten, bis sie sich wieder paaren können: sobald die Mod die Paarung sieht
 * (Herzchen, Jungtier daneben) oder der Server unter „Paarungsbereit“ eine Wartezeit nennt.
 */
public final class Breeding {
	/** Farbe des Leuchtens. */
	public static final int COLOR = 0xFF6FA8;

	private Breeding() {
	}

	/**
	 * Für das Leuchten: erwachsenes Tier nach einer Paarung, Wartezeit noch nicht um. Ohne bekannte Art
	 * zählt nur die Wartezeit aus der Info des Servers.
	 */
	public static boolean glows(Entity e) {
		if (e == null || !(e instanceof AgeableMob a) || a.isBaby() || !ViehzuchtClient.isActive() || !Config.get().glowBred) return false;
		AnimalInfo info = AnimalStore.get(e.getUUID());
		if (info == null) return false;
		Species species = Species.byName(info.name != null ? info.name : e.getCustomName() != null ? e.getCustomName().getString() : null);
		return info.breedReadyAt(species != null ? species.cooldownMs() : 0) > System.currentTimeMillis();
	}
}
