package de.viehzucht;

import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Zur Fehlersuche (/viehzucht analyse an): schreibt in eine Datei, was der Server über Tiere schickt.
 */
public final class Analysis {
	/** Nach einem Klick auf ein Tier so lange Chat und Datenänderungen mitschreiben. */
	private static final long WINDOW_MS = 15_000L;
	/** Dasselbe Tier beim Angucken höchstens so oft neu aufzeichnen. */
	private static final long LOOK_REPEAT_MS = 120_000L;
	private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static final Map<UUID, Long> lookedAt = new HashMap<>();
	private static boolean headerWritten;
	private static long windowUntil;
	private static int windowEntity = -1;
	private static long lastClick;
	private static int lastClickEntity = -1;
	private static int entries;
	private static String lastError;

	private Analysis() {
	}

	public static Path file() {
		return FabricLoader.getInstance().getGameDir().resolve("viehzucht-analyse.txt");
	}

	public static int entries() {
		return entries;
	}

	public static String lastError() {
		return lastError;
	}

	public static boolean recordedRecently(Entity e) {
		return e != null && System.currentTimeMillis() < windowUntil && e.getId() == windowEntity;
	}

	/** Lebende Tiere und Mobs, keine Spieler und Rüstungsständer. */
	public static boolean isAnimal(Entity e) {
		return e instanceof LivingEntity && !(e instanceof Player) && !(e instanceof ArmorStand);
	}

	// ---------------------------------------------------------------- Auslöser

	/** Jeden Tick: angeguckte Tiere einmal aufzeichnen. */
	public static void onLook(Entity e) {
		if (!Config.get().analysis || !isAnimal(e)) return;
		long now = System.currentTimeMillis();
		Long last = lookedAt.get(e.getUUID());
		if (last != null && now - last < LOOK_REPEAT_MS) return;
		lookedAt.put(e.getUUID(), now);
		write(dump(e, "ANGEGUCKT"));
	}

	public static void onInteract(Entity e, Player player, String hand) {
		if (!Config.get().analysis || !isAnimal(e)) return;
		long now = System.currentTimeMillis();
		if (e.getId() == lastClickEntity && now - lastClick < 500) return; // zweite Hand
		lastClick = now;
		lastClickEntity = e.getId();
		windowUntil = now + WINDOW_MS;
		windowEntity = e.getId();
		ItemStack held = player.getMainHandItem();
		String reason = "ANGEKLICKT" + (player.isShiftKeyDown() ? " mit Shift" : "") + ", Hand " + hand
				+ ", in der Hand: " + (held.isEmpty() ? "nichts" : item(held));
		write(dump(e, reason));
	}

	public static void onMessage(Component message, boolean overlay) {
		if (!Config.get().analysis || System.currentTimeMillis() > windowUntil) return;
		write(stamp() + (overlay ? "ACTIONBAR" : "CHAT") + ": " + message.getString() + "\n    json: " + json(message) + "\n\n");
	}

	public static void onEntityEvent(Entity e, byte id) {
		if (!Config.get().analysis || !isAnimal(e) || !near(e, 12)) return;
		write(stamp() + "EREIGNIS " + id + " (" + eventName(id) + ") bei " + shortName(e) + "\n\n");
	}

	public static void onEntityData(Entity e, List<SynchedEntityData.DataValue<?>> values) {
		if (!Config.get().analysis || e == null || System.currentTimeMillis() > windowUntil) return;
		Minecraft mc = Minecraft.getInstance();
		Entity clicked = mc.level == null ? null : mc.level.getEntity(windowEntity);
		if (e.getId() != windowEntity && (clicked == null || e.distanceTo(clicked) > 3)) return;
		StringBuilder sb = new StringBuilder(stamp()).append("DATEN GEÄNDERT bei ").append(shortName(e)).append('\n');
		for (SynchedEntityData.DataValue<?> v : values) sb.append("    [").append(v.id()).append("] ").append(value(v.value())).append('\n');
		write(sb.append('\n').toString());
	}

	// ---------------------------------------------------------------- Ausgabe

	static String dump(Entity e, String reason) {
		Minecraft mc = Minecraft.getInstance();
		StringBuilder sb = new StringBuilder("=== ").append(stamp()).append(reason).append(" ===\n");
		sb.append("Typ: ").append(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()))
				.append("  id=").append(e.getId()).append("  uuid=").append(e.getStringUUID())
				.append("  Klasse=").append(e.getClass().getSimpleName()).append('\n');
		sb.append("Name: ").append(e.getCustomName() == null ? "-" : json(e.getCustomName()))
				.append("  sichtbar=").append(e.isCustomNameVisible()).append('\n');
		sb.append("Anzeigename: ").append(e.getDisplayName().getString()).append('\n');
		if (e instanceof AgeableMob a) sb.append("Baby: ").append(a.isBaby()).append("  Alter (Client): ").append(a.getAge()).append('\n');
		if (e instanceof LivingEntity l) sb.append("Leben: ").append(l.getHealth()).append(" / ").append(l.getMaxHealth()).append('\n');
		sb.append(String.format(java.util.Locale.ROOT, "Position: %.1f %.1f %.1f", e.getX(), e.getY(), e.getZ()));
		if (mc.player != null) sb.append(String.format(java.util.Locale.ROOT, "  Abstand %.1f", e.distanceTo(mc.player)));
		sb.append('\n');
		if (e.getTeam() != null) {
			var t = e.getTeam();
			sb.append("Team: ").append(t.getName()).append("  Präfix=").append(json(t.getPlayerPrefix()))
					.append("  Suffix=").append(json(t.getPlayerSuffix())).append("  Farbe=").append(t.getColor()).append('\n');
		}
		sb.append("Daten:\n");
		appendData(sb, e, "    ");
		if (e instanceof LivingEntity l) {
			sb.append("Attribute:\n");
			for (AttributeInstance a : l.getAttributes().getSyncableAttributes()) {
				sb.append("    ").append(a.getAttribute().getRegisteredName()).append(" = ").append(a.getValue())
						.append(" (Basis ").append(a.getBaseValue()).append(')');
				if (!a.getModifiers().isEmpty()) sb.append("  Modifikatoren ").append(a.getModifiers());
				sb.append('\n');
			}
			StringBuilder eq = new StringBuilder();
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				ItemStack s = l.getItemBySlot(slot);
				if (!s.isEmpty()) eq.append("    ").append(slot.getName()).append(": ").append(item(s)).append('\n');
			}
			if (!eq.isEmpty()) sb.append("Ausrüstung:\n").append(eq);
		}
		if (e.getVehicle() != null) sb.append("Sitzt auf: ").append(shortName(e.getVehicle())).append('\n');
		for (Entity p : e.getPassengers()) {
			sb.append("Mitfahrer: ").append(shortName(p)).append('\n');
			appendData(sb, p, "    ");
		}
		if (mc.level != null) {
			for (Entity n : mc.level.getEntities(e, e.getBoundingBox().inflate(3))) {
				if (n == mc.player || e.getPassengers().contains(n)) continue;
				sb.append("In der Nähe: ").append(shortName(n)).append(String.format(java.util.Locale.ROOT, "  Abstand %.1f", n.distanceTo(e))).append('\n');
				if (!(n instanceof Player)) appendData(sb, n, "    ");
			}
		}
		return sb.append('\n').toString();
	}

	private static void appendData(StringBuilder sb, Entity e, String indent) {
		List<SynchedEntityData.DataValue<?>> values = e.getEntityData().getNonDefaultValues();
		if (values == null || values.isEmpty()) {
			sb.append(indent).append("(nur Standardwerte)\n");
			return;
		}
		for (SynchedEntityData.DataValue<?> v : values) sb.append(indent).append('[').append(v.id()).append("] ").append(value(v.value())).append('\n');
	}

	private static String value(Object v) {
		if (v instanceof Optional<?> o) return o.isEmpty() ? "leer" : value(o.get());
		if (v instanceof Component c) return "Text " + json(c);
		if (v instanceof ItemStack s) return "Item " + item(s);
		if (v == null) return "null";
		return v.getClass().getSimpleName() + " " + v;
	}

	private static String item(ItemStack s) {
		return s.getCount() + "x " + BuiltInRegistries.ITEM.getKey(s.getItem()) + " " + s.getComponentsPatch();
	}

	private static String shortName(Entity e) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()) + " #" + e.getId() + " \"" + e.getDisplayName().getString() + "\"";
	}

	static String json(Component c) {
		if (c == null) return "-";
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) return c.getString();
			var ops = mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
			return ComponentSerialization.CODEC.encodeStart(ops, c).result().map(Object::toString).orElse(c.getString());
		} catch (RuntimeException ex) {
			return c.getString();
		}
	}

	private static String eventName(byte id) {
		return switch (id) {
			case 6 -> "Rauch, Zähmen fehlgeschlagen";
			case 7 -> "Herzchen, gezähmt";
			case 10 -> "frisst Gras";
			case 18 -> "Herzchen, verliebt";
			case 60 -> "Tod";
			default -> "?";
		};
	}

	private static boolean near(Entity e, double dist) {
		Player p = Minecraft.getInstance().player;
		return p != null && e.distanceTo(p) <= dist;
	}

	private static String stamp() {
		return LocalDateTime.now().format(TIME) + "  ";
	}

	private static void write(String text) {
		Path f = file();
		try {
			if (Files.exists(f) && Files.size(f) > MAX_FILE_BYTES) {
				lastError = "Datei ist voll (8 MB), bitte /viehzucht leeren";
				return;
			}
			if (!headerWritten) {
				headerWritten = true;
				ServerData server = Minecraft.getInstance().getCurrentServer();
				String version = FabricLoader.getInstance().getModContainer(ViehzuchtClient.MOD_ID)
						.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
				text = "##### Viehzucht " + version + " – Sitzung " + stamp() + "Server: " + (server == null ? "Einzelspieler" : server.ip) + "\n\n" + text;
			}
			Files.writeString(f, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			entries++;
			lastError = null;
		} catch (IOException ex) {
			lastError = ex.getMessage();
			ViehzuchtClient.LOGGER.warn("Analyse-Datei nicht schreibbar", ex);
		}
	}

	public static void clear() throws IOException {
		Files.deleteIfExists(file());
		lookedAt.clear();
		headerWritten = false;
		entries = 0;
	}
}
