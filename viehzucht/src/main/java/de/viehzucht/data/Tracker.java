package de.viehzucht.data;

import de.viehzucht.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Verbindet die Info-Nachricht des Servers mit dem angeklickten Tier und beobachtet Paarungen
 * (Herzchen, Jungtier daneben), um die Wartezeit zu schätzen.
 */
public final class Tracker {
	/** So lange nach Shift + Rechtsklick auf die Nachricht warten. */
	private static final long EXPECT_MS = 3_000L;
	/** Ein Jungtier, das so kurz nach Herzchen in der Nähe auftaucht, stammt von diesen Eltern. */
	private static final long BIRTH_AFTER_LOVE_MS = 60_000L;
	private static final double BIRTH_RANGE = 8;

	private static UUID expected;
	private static long expectUntil;
	/** Die Abfrage kam von {@link AutoQuery}, nicht vom Spieler. */
	private static boolean expectedAuto;
	private static boolean openAuto;
	private static boolean rideRejected;
	private static boolean open;
	private static final List<String> lines = new ArrayList<>();
	private static UUID lastCaptured;
	private static long lastCapturedAt;

	private Tracker() {
	}

	public static boolean isAnimal(Entity e) {
		return e instanceof LivingEntity && !(e instanceof Player) && !(e instanceof ArmorStand);
	}

	public static UUID lastCaptured() {
		return System.currentTimeMillis() - lastCapturedAt < 5_000 ? lastCaptured : null;
	}

	/** Spieler hat ein Tier angeklickt. */
	public static void onInteract(Entity e, boolean shift) {
		if (!isAnimal(e)) return;
		long now = System.currentTimeMillis();
		AutoQuery.onOwnAnimalClick();
		if (shift) {
			expect(e.getUUID(), false);
		} else {
			AnimalInfo info = AnimalStore.get(e.getUUID());
			if (info != null && info.hasCapture()) {
				info.touchedAt = now;
				AnimalStore.markDirty();
			}
		}
	}

	/** Eine Antwort für dieses Tier erwarten. */
	public static void expect(UUID id, boolean auto) {
		expected = id;
		expectedAuto = auto;
		expectUntil = System.currentTimeMillis() + EXPECT_MS;
		rideRejected = false;
	}

	public static void cancelExpect(UUID id) {
		if (id.equals(expected) && !open) expected = null;
	}

	/** Server hat statt der Info „erst losbinden um es zu reiten“ geantwortet. */
	public static boolean takeRideRejection() {
		boolean r = rideRejected;
		rideRejected = false;
		return r;
	}

	private static boolean hide(boolean auto) {
		return Config.get().hideChatInfo || (auto && Config.get().hideAutoChat);
	}

	/**
	 * Jede Systemnachricht. Erkennt den Block zwischen zwei Strichlinien nach einem Shift-Rechtsklick.
	 *
	 * @return false, wenn die Zeile ausgeblendet werden soll
	 */
	public static boolean onMessage(String text, boolean overlay) {
		if (overlay) return true;
		long now = System.currentTimeMillis();
		String s = text.strip();
		boolean dashes = s.length() >= 10 && s.chars().allMatch(c -> c == '-');
		if (!open) {
			if (dashes && expected != null && now < expectUntil) {
				open = true;
				openAuto = expectedAuto;
				lines.clear();
				return !hide(openAuto);
			}
			if (expected != null && expectedAuto && now < expectUntil && s.toLowerCase(java.util.Locale.ROOT).contains("losbinden")) {
				rideRejected = true;
				return !hide(true);
			}
			return true;
		}
		if (dashes) {
			open = false;
			finish(now);
			return !hide(openAuto);
		}
		lines.add(s);
		if (lines.size() > 30) open = false; // war wohl doch keine Tier-Info
		return !hide(openAuto);
	}

	private static void finish(long now) {
		AnimalInfo info = AnimalInfo.parse(lines, now);
		UUID id = expected;
		expected = null;
		if (info == null || id == null) return;
		info.keepObserved(AnimalStore.get(id));
		Shearing.onCapture(id, info);
		info.seenAt = now;
		AnimalStore.put(id, info);
		lastCaptured = id;
		lastCapturedAt = now;
	}

	/** Herzchen über einem Tier (Ereignis 18). */
	public static void onEntityEvent(Entity e, byte id) {
		if (id != 18 || !isAnimal(e)) return;
		AnimalInfo info = AnimalStore.getOrCreate(e.getUUID());
		info.lovedAt = System.currentTimeMillis();
		info.seenAt = info.lovedAt;
	}

	/** Neues Tier in der Welt: ist es ein Jungtier neben verliebten Eltern, war das eine Paarung. */
	public static void onEntityLoad(Entity e) {
		if (!(e instanceof AgeableMob baby) || !baby.isBaby()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		long now = System.currentTimeMillis();
		List<Entity> parents = new ArrayList<>();
		for (Entity n : mc.level.getEntities(e, e.getBoundingBox().inflate(BIRTH_RANGE))) {
			if (n.getType() != e.getType() || !(n instanceof AgeableMob a) || a.isBaby()) continue;
			AnimalInfo info = AnimalStore.get(n.getUUID());
			if (info != null && info.lovedAt > 0 && now - info.lovedAt < BIRTH_AFTER_LOVE_MS) parents.add(n);
		}
		if (parents.isEmpty()) return;
		parents.sort(Comparator.comparingDouble(p -> p.distanceToSqr(e)));
		for (Entity p : parents.subList(0, Math.min(2, parents.size()))) {
			AnimalInfo info = AnimalStore.get(p.getUUID());
			info.bredAt = now;
			info.lovedAt = 0;
		}
		AnimalInfo child = AnimalStore.getOrCreate(e.getUUID());
		child.bornAt = now;
		child.seenAt = now;
		AnimalStore.markDirty();
	}

	/** Für die Anzeige: zuletzt gesehen merken (nur ab und zu speichern). */
	public static void seen(Entity e) {
		AnimalInfo info = AnimalStore.get(e.getUUID());
		if (info != null) info.seenAt = System.currentTimeMillis();
	}
}
