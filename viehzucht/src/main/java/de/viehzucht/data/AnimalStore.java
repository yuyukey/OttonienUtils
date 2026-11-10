package de.viehzucht.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.viehzucht.ViehzuchtClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Gemerkte Tiere je Server in config/viehzucht/tiere-&lt;server&gt;.json, damit Zeiten auch nach dem Neustart weiterlaufen. */
public final class AnimalStore {
	private static final Gson GSON = new GsonBuilder().create();
	/** Tiere, die so lange nicht gesehen wurden, vergessen. */
	private static final long FORGET_MS = 14L * 24 * 3_600_000L;
	private static final long SAVE_EVERY_MS = 10_000L;

	private static Map<UUID, AnimalInfo> animals = new HashMap<>();
	private static String server;
	private static boolean dirty;
	private static long lastSave;

	private AnimalStore() {
	}

	public static AnimalInfo get(UUID id) {
		return animals.get(id);
	}

	public static AnimalInfo getOrCreate(UUID id) {
		dirty = true;
		return animals.computeIfAbsent(id, k -> new AnimalInfo());
	}

	public static void put(UUID id, AnimalInfo info) {
		animals.put(id, info);
		dirty = true;
	}

	public static void markDirty() {
		dirty = true;
	}

	public static int size() {
		return animals.size();
	}

	public static void clear() {
		animals.clear();
		dirty = true;
		save();
	}

	private static Path path(String server) {
		String safe = server.toLowerCase().replaceAll("[^a-z0-9._-]", "_");
		return FabricLoader.getInstance().getConfigDir().resolve("viehzucht").resolve("tiere-" + safe + ".json");
	}

	/** Beim Betreten eines Servers (oder einer Welt) die passende Datei laden. */
	public static void switchTo(String newServer) {
		if (newServer.equals(server)) return;
		save();
		server = newServer;
		animals = new HashMap<>();
		Path p = path(server);
		if (Files.exists(p)) {
			try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
				Map<UUID, AnimalInfo> loaded = GSON.fromJson(r, new TypeToken<Map<UUID, AnimalInfo>>() {
				}.getType());
				if (loaded != null) animals = new HashMap<>(loaded);
			} catch (Exception e) {
				ViehzuchtClient.LOGGER.warn("Konnte {} nicht lesen", p, e);
			}
		}
		long now = System.currentTimeMillis();
		animals.values().removeIf(a -> a == null || now - Math.max(a.seenAt, a.capturedAt) > FORGET_MS);
		dirty = false;
	}

	public static void tick() {
		long now = System.currentTimeMillis();
		if (dirty && now - lastSave > SAVE_EVERY_MS) save();
	}

	public static void save() {
		if (server == null || !dirty) return;
		dirty = false;
		lastSave = System.currentTimeMillis();
		Path p = path(server);
		try {
			Files.createDirectories(p.getParent());
			try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
				GSON.toJson(animals, w);
			}
		} catch (IOException e) {
			ViehzuchtClient.LOGGER.warn("Konnte Tiere nicht speichern", e);
		}
	}
}
