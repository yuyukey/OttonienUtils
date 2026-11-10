package de.viehzucht.data;

import de.viehzucht.Config;
import de.viehzucht.ViehzuchtClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fragt die Tier-Info automatisch ab, sobald man ein Zuchttier kurz anvisiert: wie ein Shift + Rechtsklick,
 * nur ohne Tasten. Ablauf wie in OttoExtra (Skyfuller1303, MIT): zuerst die Interaktion mit gesetztem
 * Shift-Flag, bei Pferden oder ohne Antwort mit kurz gesendetem Schleichen.
 */
public final class AutoQuery {
	private static final long GLOBAL_COOLDOWN_MS = 350L;
	private static final long BLOCK_GUARD_MS = 750L;
	private static final long OWN_CLICK_GUARD_MS = 500L;
	private static final long RESPONSE_TIMEOUT_MS = 3_000L;
	/** Antwortet der Server zu einem Tier gar nicht, so lange in Ruhe lassen. */
	private static final long FAILED_BACKOFF_MS = 120_000L;

	enum Attempt { FLAG, SNEAK }

	private record Pending(UUID id, long sentAt, Attempt attempt) {
	}

	private static UUID focused;
	private static long focusedSince;
	private static long lastQueryAt;
	private static long blockGuardUntil;
	private static long ownClickGuardUntil;
	private static Pending pending;
	/**
	 * Aufräumen nach jeder Abfrage: Der Server übernimmt das Shift-Flag des Klicks als „schleicht“ und
	 * vergisst es erst, wenn sich die Eingabe ändert. Ohne das konnte man danach z.B. keine Zauntore mehr
	 * mit einem Item in der Hand öffnen. Deshalb in den nächsten Ticks einmal Schleichen an und wieder aus
	 * senden (wie OttoExtras CleanupPhase).
	 */
	private enum Cleanup { NONE, ASSERT_SNEAK, RELEASE_SNEAK }

	private static Cleanup cleanup = Cleanup.NONE;
	private static final Map<UUID, Long> backoff = new HashMap<>();
	private static final Map<UUID, Long> queriedAt = new HashMap<>();
	private static int sent;

	private AutoQuery() {
	}

	/** Für die Anzeige, solange noch keine Info da ist. */
	public static String waitingText(UUID id) {
		if (!Config.get().autoQuery) return null;
		if (pending != null && pending.id.equals(id)) return "frage Infos ab …";
		Long until = backoff.get(id);
		if (until != null && System.currentTimeMillis() < until) return "keine Antwort, Shift + Rechtsklick";
		return "kurz anvisieren für Infos";
	}

	/** Für Tests: wie viele Abfragen gesendet wurden. */
	public static int sentCount() {
		return sent;
	}

	public static void onBlockInteraction() {
		blockGuardUntil = System.currentTimeMillis() + BLOCK_GUARD_MS;
	}

	public static void onOwnAnimalClick() {
		ownClickGuardUntil = System.currentTimeMillis() + OWN_CLICK_GUARD_MS;
	}

	public static void reset() {
		focused = null;
		pending = null;
		cleanup = Cleanup.NONE;
		backoff.clear();
		queriedAt.clear();
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientPacketListener connection = mc.getConnection();
		if (player == null || connection == null) return;
		long now = System.currentTimeMillis();

		runCleanup(player, connection);
		checkPending(mc, now);

		Entity target = mc.crosshairPickEntity;
		if (!Config.get().autoQuery || mc.screen != null || !(target instanceof Animal animal) || !isBreedingAnimal(animal)) {
			focused = null;
			return;
		}
		UUID id = animal.getUUID();
		if (!id.equals(focused)) {
			focused = id;
			focusedSince = now;
		}
		if (pending != null || cleanup != Cleanup.NONE || now - focusedSince < Math.max(0, Config.get().hoverDelayMs)) return;
		if (!needsQuery(id, now)) return;
		Long until = backoff.get(id);
		if (until != null && now < until) return;
		if (now - lastQueryAt < GLOBAL_COOLDOWN_MS || now < blockGuardUntil || now < ownClickGuardUntil) return;
		if (blockedByPlayer(mc, player, animal)) return;
		send(mc, animal, animal instanceof AbstractHorse ? Attempt.SNEAK : Attempt.FLAG, now);
	}

	/** Nur Ottoniens Zuchttiere (Name aus der Tabelle) oder Tiere, zu denen schon eine Info kam. */
	private static boolean isBreedingAnimal(Animal a) {
		AnimalInfo info = AnimalStore.get(a.getUUID());
		if (info != null && info.hasCapture()) return true;
		return a.getCustomName() != null && Species.byName(a.getCustomName().getString()) != null;
	}

	private static boolean needsQuery(UUID id, long now) {
		AnimalInfo info = AnimalStore.get(id);
		Long last = queriedAt.get(id);
		if (info == null || !info.hasCapture()) return true;
		// Nach eigenem Füttern oder Anklicken die neuen Werte holen
		if (info.touchedAt > info.capturedAt && (last == null || last < info.touchedAt)) return true;
		return now - info.capturedAt > Math.max(10, Config.get().autoRefreshSeconds) * 1000L;
	}

	/** Nichts auslösen, was mit dem Tier etwas anstellt (füttern, melken, anleinen, scheren …) oder den Spieler stört. */
	private static boolean blockedByPlayer(Minecraft mc, LocalPlayer player, Animal animal) {
		if (player.isPassenger() || player.isShiftKeyDown() || player.isSprinting() || player.isUsingItem()) return true;
		Input input = player.getLastSentInput();
		if (input != null && (input.shift() || input.sprint())) return true;
		if (mc.options.keyUse.isDown() || mc.options.keyAttack.isDown()) return true;
		for (ItemStack s : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
			if (s.isEmpty()) continue;
			if (animal.isFood(s)) return true;
			if (s.is(Items.LEAD) || s.is(Items.SHEARS) || s.is(Items.BUCKET) || s.is(Items.WATER_BUCKET) || s.is(Items.MILK_BUCKET)
					|| s.is(Items.NAME_TAG) || s.is(Items.SADDLE) || s.is(Items.BOWL) || s.is(Items.GLASS_BOTTLE)) return true;
		}
		return false;
	}

	private static void send(Minecraft mc, Animal animal, Attempt attempt, long now) {
		LocalPlayer player = mc.player;
		ClientPacketListener c = mc.getConnection();
		Input neutral = withShift(player.getLastSentInput(), false);
		try {
			c.send(new ServerboundSetCarriedItemPacket(player.getInventory().getSelectedSlot()));
			c.send(new ServerboundPlayerInputPacket(attempt == Attempt.SNEAK ? withShift(neutral, true) : neutral));
			c.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
			Tracker.expect(animal.getUUID(), true);
			c.send(ServerboundInteractPacket.createInteractionPacket(animal, true, InteractionHand.MAIN_HAND));
			c.send(new ServerboundPlayerInputPacket(neutral));
			cleanup = Cleanup.ASSERT_SNEAK;
		} catch (RuntimeException ex) {
			ViehzuchtClient.LOGGER.warn("Tierabfrage nicht gesendet: {}", ex.toString());
			backoff.put(animal.getUUID(), now + FAILED_BACKOFF_MS);
			return;
		}
		sent++;
		lastQueryAt = now;
		queriedAt.put(animal.getUUID(), now);
		pending = new Pending(animal.getUUID(), now, attempt);
	}

	private static void runCleanup(LocalPlayer player, ClientPacketListener connection) {
		if (cleanup == Cleanup.NONE) return;
		Input real = player.getLastSentInput();
		// Schleicht der Spieler inzwischen selbst, stimmt der Zustand auf dem Server schon
		if (real != null && real.shift()) {
			cleanup = Cleanup.NONE;
			return;
		}
		try {
			if (cleanup == Cleanup.ASSERT_SNEAK) {
				connection.send(new ServerboundPlayerInputPacket(withShift(real, true)));
				cleanup = Cleanup.RELEASE_SNEAK;
			} else {
				connection.send(new ServerboundPlayerInputPacket(withShift(real, false)));
				cleanup = Cleanup.NONE;
			}
		} catch (RuntimeException ex) {
			ViehzuchtClient.LOGGER.warn("Schleichen nicht zurückgesetzt: {}", ex.toString());
			cleanup = Cleanup.NONE;
		}
	}

	/** Solange aufgeräumt wird, keine neue Abfrage. */
	public static boolean cleaningUp() {
		return cleanup != Cleanup.NONE;
	}

	private static void checkPending(Minecraft mc, long now) {
		if (pending == null) return;
		AnimalInfo info = AnimalStore.get(pending.id);
		if (info != null && info.capturedAt >= pending.sentAt) {
			backoff.remove(pending.id);
			pending = null;
			return;
		}
		boolean rejected = Tracker.takeRideRejection();
		if (!rejected && now - pending.sentAt < RESPONSE_TIMEOUT_MS) return;
		Pending p = pending;
		pending = null;
		Tracker.cancelExpect(p.id);
		// Erst ohne Antwort? Dann einmal mit echtem Schleichen versuchen
		if (p.attempt == Attempt.FLAG && mc.crosshairPickEntity instanceof Animal a && a.getUUID().equals(p.id)
				&& !blockedByPlayer(mc, mc.player, a)) {
			send(mc, a, Attempt.SNEAK, now);
			return;
		}
		backoff.put(p.id, now + FAILED_BACKOFF_MS);
	}

	private static Input withShift(Input in, boolean shift) {
		if (in == null) in = Input.EMPTY;
		return new Input(in.forward(), in.backward(), in.left(), in.right(), in.jump(), shift, in.sprint());
	}
}
