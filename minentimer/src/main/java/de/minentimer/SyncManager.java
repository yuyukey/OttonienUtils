package de.minentimer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Teilt Minen und Timer über den eigenen Sync-Server oder eine Firebase Realtime Database (gleiche REST-Schnittstelle).
 * Daten liegen unter {url}/minentimer/{gruppe}/mines/{id} und .../timers/{key}.
 * Bei Konflikten gewinnt der Eintrag mit dem neueren updatedAt.
 */
public final class SyncManager {
	private static final SyncManager INSTANCE = new SyncManager();
	private static final Gson GSON = new Gson();
	private static final int POLL_TICKS = 20 * 10;

	private final HttpClient http = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(8))
			.build();
	private int tickCounter = POLL_TICKS;
	private boolean pollInFlight;
	private boolean flushInFlight;
	private int retryTicks;
	/** Noch nicht hochgeladene Änderungen, Pfad relativ zur Gruppe -> Wert. */
	private JsonObject pending = new JsonObject();
	private String status = "Aus";
	private long lastSuccess;

	public static SyncManager get() {
		return INSTANCE;
	}

	public boolean isActive() {
		ModConfig cfg = ModConfig.get();
		return cfg.syncEnabled && !cfg.syncUrl.isBlank() && !cfg.syncGroup.isBlank();
	}

	public String status() {
		if (!ModConfig.get().syncEnabled) return "Aus";
		if (!isActive()) return "URL oder Gruppen-Code fehlt";
		return status;
	}

	public long lastSuccess() {
		return lastSuccess;
	}

	private String base() {
		ModConfig cfg = ModConfig.get();
		String url = cfg.syncUrl.trim();
		while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
		if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
		return url + "/minentimer/" + safeKey(cfg.syncGroup.trim());
	}

	private HttpRequest.Builder request() {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base() + ".json")).timeout(Duration.ofSeconds(10));
		String key = ModConfig.get().syncKey.trim();
		if (!key.isEmpty()) b.header("X-MinenTimer-Key", key);
		return b;
	}

	/** Firebase erlaubt . # $ [ ] / nicht in Schlüsseln. */
	private static String safeKey(String s) {
		return s.replaceAll("[.#$\\[\\]/\\s]", "_");
	}

	public void tick() {
		if (!isActive()) return;
		if (++tickCounter >= POLL_TICKS) {
			tickCounter = 0;
			poll();
		}
		if (retryTicks > 0) retryTicks--;
		else flush();
	}

	/** Sofort abfragen, z. B. nach Änderung der Einstellungen. */
	public void requestPoll() {
		tickCounter = POLL_TICKS;
	}

	private void poll() {
		if (pollInFlight) return;
		pollInFlight = true;
		HttpRequest req;
		try {
			req = request().GET().build();
		} catch (IllegalArgumentException e) {
			pollInFlight = false;
			status = "Ungültige URL";
			return;
		}
		http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((resp, err) -> Minecraft.getInstance().execute(() -> {
			pollInFlight = false;
			if (err != null) {
				status = "Keine Verbindung";
				return;
			}
			if (resp.statusCode() != 200) {
				status = "Fehler " + resp.statusCode() + (resp.statusCode() == 401 ? " (Zugriff verweigert)" : "");
				return;
			}
			try {
				merge(JsonParser.parseString(resp.body()));
				status = "Verbunden";
				lastSuccess = System.currentTimeMillis();
				// Server wieder erreichbar: liegengebliebene Änderungen sofort senden
				retryTicks = 0;
			} catch (Exception e) {
				MinenTimerClient.LOGGER.warn("Sync-Antwort konnte nicht gelesen werden", e);
				status = "Ungültige Antwort";
			}
		}));
	}

	private void merge(JsonElement root) {
		MineManager mgr = MineManager.get();
		JsonObject remoteMines = new JsonObject();
		JsonObject remoteTimers = new JsonObject();
		if (root != null && root.isJsonObject()) {
			JsonObject obj = root.getAsJsonObject();
			if (obj.has("mines") && obj.get("mines").isJsonObject()) remoteMines = obj.getAsJsonObject("mines");
			if (obj.has("timers") && obj.get("timers").isJsonObject()) remoteTimers = obj.getAsJsonObject("timers");
		}

		boolean minesChanged = false, timersChanged = false;
		for (Map.Entry<String, JsonElement> e : remoteMines.entrySet()) {
			try {
				minesChanged |= mgr.mergeRemoteMine(GSON.fromJson(e.getValue(), Mine.class));
			} catch (Exception ex) {
				MinenTimerClient.LOGGER.warn("Ungültige Mine {} im Sync", e.getKey());
			}
		}
		for (Map.Entry<String, JsonElement> e : remoteTimers.entrySet()) {
			try {
				timersChanged |= mgr.mergeRemoteTimer(e.getKey(), GSON.fromJson(e.getValue(), TimerEntry.class));
			} catch (Exception ex) {
				MinenTimerClient.LOGGER.warn("Ungültiger Timer {} im Sync", e.getKey());
			}
		}
		if (minesChanged) mgr.saveMines();
		if (timersChanged) mgr.saveTimers();

		// Lokale Einträge hochladen, die auf dem Server fehlen oder älter sind
		for (Mine m : mgr.allMinesIncludingDeleted().values()) {
			if (isNewer(m.updatedAt, remoteMines.get(m.id))) pushMine(m);
		}
		for (Map.Entry<String, TimerEntry> e : mgr.allTimers().entrySet()) {
			if (isNewer(e.getValue().updatedAt, remoteTimers.get(e.getKey()))) pushTimer(e.getKey(), e.getValue());
		}
	}

	private static boolean isNewer(long localUpdatedAt, JsonElement remote) {
		if (remote == null || !remote.isJsonObject()) return true;
		JsonElement u = remote.getAsJsonObject().get("updatedAt");
		return u == null || localUpdatedAt > u.getAsLong();
	}

	public void pushMine(Mine mine) {
		queue("mines/" + safeKey(mine.id), GSON.toJsonTree(mine));
	}

	public void pushTimer(String key, TimerEntry timer) {
		queue("timers/" + safeKey(key), GSON.toJsonTree(timer));
	}

	private void queue(String path, JsonElement value) {
		if (!isActive()) return;
		pending.add(path, value);
	}

	/** Schickt alle gesammelten Änderungen in einem einzigen PATCH (Firebase Multi-Path-Update). */
	private void flush() {
		if (pending.size() == 0 || flushInFlight) return;
		JsonObject batch = pending;
		pending = new JsonObject();
		HttpRequest req;
		try {
			req = request()
					.header("Content-Type", "application/json")
					.method("PATCH", HttpRequest.BodyPublishers.ofString(GSON.toJson(batch)))
					.build();
		} catch (IllegalArgumentException e) {
			status = "Ungültige URL";
			return;
		}
		flushInFlight = true;
		http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((resp, err) -> Minecraft.getInstance().execute(() -> {
			flushInFlight = false;
			boolean failed = err != null || resp.statusCode() != 200;
			if (err != null) status = "Keine Verbindung";
			else if (resp.statusCode() != 200) status = "Fehler " + resp.statusCode() + " beim Hochladen";
			if (failed) {
				// Neuere Änderungen behalten Vorrang, der Rest wird erneut versucht
				for (Map.Entry<String, JsonElement> e : batch.entrySet()) {
					if (!pending.has(e.getKey())) pending.add(e.getKey(), e.getValue());
				}
				retryTicks = 20 * 15;
			}
		}));
	}
}
