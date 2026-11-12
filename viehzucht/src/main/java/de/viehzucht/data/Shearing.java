package de.viehzucht.data;

import de.viehzucht.Config;
import de.viehzucht.ViehzuchtClient;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Merkt sich selbst geschorene Schafe und lässt sie leuchten, bis sie wieder scherbar sind.
 * Geschoren zählt erst, wenn das Schaf nach dem Klick mit der Schere wirklich keine Wolle mehr hat
 * oder der Server danach eine Wartezeit unter „Scherbar“ nennt (abgelehnte Versuche leuchten nicht).
 */
public final class Shearing {
	/** So lange nach dem Klick muss das Schaf geschoren aussehen. */
	private static final long CONFIRM_MS = 3_000L;
	/** So lange gilt ein Klick, wenn die Wartezeit erst mit der nächsten Info kommt. */
	private static final long CLICK_MEMORY_MS = 10 * 60_000L;
	/** Farbe des Leuchtens. */
	public static final int COLOR = 0xFFC94A;

	private static final Map<Sheep, Long> pending = new HashMap<>();
	private static final Map<UUID, Long> clicks = new HashMap<>();

	private Shearing() {
	}

	/** Rechtsklick auf ein Tier: mit Schere auf ein Schaf mit Wolle merken. */
	public static void onInteract(Entity e, ItemStack hand, boolean shift) {
		if (shift || !(e instanceof Sheep sheep) || sheep.isSheared() || sheep.isBaby() || !hand.is(Items.SHEARS)) return;
		long now = System.currentTimeMillis();
		pending.put(sheep, now);
		clicks.put(sheep.getUUID(), now);
	}

	public static void tick() {
		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<Sheep, Long>> it = pending.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Sheep, Long> en = it.next();
			Sheep sheep = en.getKey();
			if (sheep.isSheared()) {
				confirm(sheep.getUUID(), en.getValue());
				it.remove();
			} else if (sheep.isRemoved() || now - en.getValue() > CONFIRM_MS) {
				it.remove();
			}
		}
		clicks.values().removeIf(t -> now - t > CLICK_MEMORY_MS);
	}

	/** Neue Info des Servers: nennt sie nach einem Klick mit der Schere eine Wartezeit, war das Scheren erfolgreich. */
	public static void onCapture(UUID id, AnimalInfo info) {
		Long click = clicks.get(id);
		if (click == null || info.shearedAt >= click) return;
		AnimalInfo.Line l = info.line("Scherbar");
		if (l != null && AnimalInfo.parseDuration(l.value) > 0) info.shearedAt = click;
	}

	private static void confirm(UUID id, long at) {
		AnimalInfo info = AnimalStore.getOrCreate(id);
		info.shearedAt = at;
		info.seenAt = System.currentTimeMillis();
		AnimalStore.markDirty();
	}

	/** Für das Leuchten: selbst geschoren und noch nicht wieder scherbar. */
	public static boolean glows(Entity e) {
		if (!(e instanceof Sheep) || !ViehzuchtClient.isActive() || !Config.get().glowSheared) return false;
		AnimalInfo info = AnimalStore.get(e.getUUID());
		return info != null && info.shearReadyAt() > System.currentTimeMillis();
	}
}
