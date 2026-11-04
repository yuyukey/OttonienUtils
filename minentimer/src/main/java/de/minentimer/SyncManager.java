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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Teilt Minen und Timer über den eigenen Sync-Server oder eine Firebase Realtime Database (gleiche REST-Schnittstelle).
 * Jedes Lehen hat einen eigenen Code, Minen ohne Lehen werden nicht geteilt. Der eigene Code ({@link ModConfig#syncGroup})
 * wird nur noch gelesen: Aus ihm leiten sich die Lehen-Codes ab, und ältere Versionen legen dort noch alles ab.
 * Daten liegen unter {url}/minentimer/{code}/mines/{id} und .../timers/{id}.
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
	private final Set<String> pollInFlight = new HashSet<>();
	private final Set<String> flushInFlight = new HashSet<>();
	private int retryTicks;
	/** Noch nicht hochgeladene Änderungen: Code -> (Pfad -> Wert). */
	private final Map<String, JsonObject> pending = new LinkedHashMap<>();
	/** Letzter Zustand je Code, null = in Ordnung. */
	private final Map<String, String> errors = new HashMap<>();
	private final Set<String> seen = new HashSet<>();
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
		if (!isActive()) return "URL oder Code fehlt";
		for (String code : codes()) {
			String e = errors.get(code);
			if (e != null) return e;
		}
		for (String code : codes()) if (!seen.contains(code)) return "Verbinde …";
		return "Verbunden";
	}

	public long lastSuccess() {
		return lastSuccess;
	}

	/** Alle Codes, die abgefragt werden: der eigene, die der Lehen und neu eingetragene. */
	public List<String> codes() {
		ModConfig cfg = ModConfig.get();
		Set<String> set = new LinkedHashSet<>();
		set.add(cfg.syncGroup.trim());
		for (Mine m : MineManager.get().mines()) if (!m.area.isEmpty()) set.add(cfg.codeFor(m.area));
		set.addAll(cfg.lehenCodes.values());
		set.addAll(cfg.joinCodes);
		set.removeIf(String::isBlank);
		return new ArrayList<>(set);
	}

	private String base(String code) {
		ModConfig cfg = ModConfig.get();
		String url = cfg.syncUrl.trim();
		while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
		if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
		return url + "/minentimer/" + safeKey(code.trim());
	}

	private HttpRequest.Builder request(String code) {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base(code) + ".json")).timeout(Duration.ofSeconds(10));
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
			for (String code : codes()) poll(code);
		}
		if (retryTicks > 0) retryTicks--;
		else flush();
	}

	/** Sofort abfragen, z. B. nach Änderung der Einstellungen. */
	public void requestPoll() {
		tickCounter = POLL_TICKS;
	}

	/** Trägt einen Code ein, den jemand geteilt hat. Welches Lehen dahintersteckt, zeigt die erste Abfrage. */
	public void join(String code) {
		code = code.trim();
		if (code.isEmpty()) return;
		ModConfig cfg = ModConfig.get();
		if (!cfg.joinCodes.contains(code) && !code.equals(cfg.syncGroup) && !cfg.lehenCodes.containsValue(code)) cfg.joinCodes.add(code);
		cfg.save();
		requestPoll();
	}

	private void poll(String code) {
		if (pollInFlight.contains(code)) return;
		HttpRequest req;
		try {
			req = request(code).GET().build();
		} catch (IllegalArgumentException e) {
			errors.put(code, "Ungültige URL");
			return;
		}
		pollInFlight.add(code);
		http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((resp, err) -> Minecraft.getInstance().execute(() -> {
			pollInFlight.remove(code);
			if (err != null) {
				errors.put(code, "Keine Verbindung");
				return;
			}
			if (resp.statusCode() != 200) {
				errors.put(code, "Fehler " + resp.statusCode() + (resp.statusCode() == 401 ? " (Zugriff verweigert)" : ""));
				return;
			}
			try {
				merge(code, JsonParser.parseString(resp.body()));
				errors.remove(code);
				seen.add(code);
				lastSuccess = System.currentTimeMillis();
				// Server wieder erreichbar: liegengebliebene Änderungen sofort senden
				retryTicks = 0;
				// Gerade neu entdeckte Lehen gleich abfragen, nicht erst in zehn Sekunden
				for (String c : codes()) if (!seen.contains(c) && !errors.containsKey(c)) poll(c);
			} catch (Exception e) {
				MinenTimerClient.LOGGER.warn("Sync-Antwort konnte nicht gelesen werden", e);
				errors.put(code, "Ungültige Antwort");
			}
		}));
	}

	private void merge(String code, JsonElement root) {
		ModConfig cfg = ModConfig.get();
		MineManager mgr = MineManager.get();
		JsonObject remoteMines = new JsonObject();
		JsonObject remoteTimers = new JsonObject();
		if (root != null && root.isJsonObject()) {
			JsonObject obj = root.getAsJsonObject();
			if (obj.has("mines") && obj.get("mines").isJsonObject()) remoteMines = obj.getAsJsonObject("mines");
			if (obj.has("timers") && obj.get("timers").isJsonObject()) remoteTimers = obj.getAsJsonObject("timers");
		}

		List<Mine> incoming = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : remoteMines.entrySet()) {
			try {
				Mine m = GSON.fromJson(e.getValue(), Mine.class);
				if (m == null || m.id == null) continue;
				m.normalize();
				incoming.add(m);
			} catch (Exception ex) {
				MinenTimerClient.LOGGER.warn("Ungültige Mine {} im Sync", e.getKey());
			}
		}

		boolean own = code.equals(cfg.syncGroup.trim());
		// Eingetragener Code: das Lehen dahinter läuft ab jetzt über diesen Code
		if (cfg.joinCodes.contains(code) && !incoming.isEmpty()) {
			for (Mine m : incoming) {
				if (m.area.isEmpty()) {
					// Gruppen-Code aus älteren Versionen: Lehen von dort übernehmen
					cfg.syncGroup = code;
					own = true;
				} else if (!code.equals(cfg.mappedCode(m.area))) {
					cfg.setLehenCode(m.area, code);
				}
			}
			cfg.joinCodes.remove(code);
			cfg.save();
		}
		// Älterer Stand, in dem alle Lehen über einen Code liefen: Lehen von dort übernehmen
		if (own) {
			for (Mine m : incoming) {
				if (!m.area.isEmpty() && !m.deleted && cfg.mappedCode(m.area) == null && !cfg.hasLeft(m.area)) cfg.codeFor(m.area);
			}
		}

		boolean minesChanged = false, timersChanged = false;
		for (Mine m : incoming) {
			if (!accepts(code, own, m, mgr.allMinesIncludingDeleted().get(m.id))) continue;
			minesChanged |= mgr.mergeRemoteMine(m);
		}
		for (Map.Entry<String, JsonElement> e : remoteTimers.entrySet()) {
			Mine m = mgr.allMinesIncludingDeleted().get(e.getKey());
			if (m == null || m.area.isEmpty() || (!own && !code.equals(cfg.mappedCode(m.area)))) continue;
			try {
				timersChanged |= mgr.mergeRemoteTimer(e.getKey(), GSON.fromJson(e.getValue(), TimerEntry.class));
			} catch (Exception ex) {
				MinenTimerClient.LOGGER.warn("Ungültiger Timer {} im Sync", e.getKey());
			}
		}
		if (minesChanged) mgr.saveMines();
		if (timersChanged) mgr.saveTimers();
		// In den eigenen Code wird nichts mehr geschrieben
		if (own) return;

		// Lokale Einträge dieses Codes hochladen, die auf dem Server fehlen oder älter sind
		for (Mine m : mgr.allMinesIncludingDeleted().values()) {
			if (code.equals(cfg.codeFor(m.area)) && isNewer(m.updatedAt, remoteMines.get(m.id))) queue(code, "mines/" + safeKey(m.id), GSON.toJsonTree(m));
		}
		for (Map.Entry<String, TimerEntry> e : mgr.allTimers().entrySet()) {
			Mine m = mgr.allMinesIncludingDeleted().get(e.getKey());
			String target = cfg.codeFor(m == null ? "" : m.area);
			if (code.equals(target) && isNewer(e.getValue().updatedAt, remoteTimers.get(e.getKey()))) queue(code, "timers/" + safeKey(e.getKey()), GSON.toJsonTree(e.getValue()));
		}
	}

	/**
	 * Unter einem Lehen-Code zählen nur Minen dieses Lehens. So kann niemand mit einem Lehen-Code
	 * eigene Minen in ein anderes Lehen schieben. Der eigene Code nimmt alles an (ältere Versionen).
	 */
	private static boolean accepts(String code, boolean own, Mine remote, Mine local) {
		ModConfig cfg = ModConfig.get();
		if (remote.area.isEmpty() || cfg.hasLeft(remote.area)) return false;
		if (own) return true;
		if (remote.area.isEmpty() || !code.equals(cfg.mappedCode(remote.area))) return false;
		return local == null || code.equals(cfg.mappedCode(local.area));
	}

	private static boolean isNewer(long localUpdatedAt, JsonElement remote) {
		if (remote == null || !remote.isJsonObject()) return true;
		JsonElement u = remote.getAsJsonObject().get("updatedAt");
		return u == null || localUpdatedAt > u.getAsLong();
	}

	public void pushMine(Mine mine) {
		pushMine(mine, null);
	}

	/** Lädt eine Mine hoch. Wechselt sie das Lehen, verschwindet sie beim alten Lehen-Code. */
	public void pushMine(Mine mine, String previousArea) {
		ModConfig cfg = ModConfig.get();
		String code = mine.area.isEmpty() ? "" : cfg.codeFor(mine.area);
		queue(code, "mines/" + safeKey(mine.id), GSON.toJsonTree(mine));
		if (previousArea != null && !previousArea.isEmpty()) {
			String old = cfg.codeFor(previousArea);
			if (!old.equals(code)) {
				Mine gone = mine.copy();
				gone.area = previousArea;
				gone.deleted = true;
				gone.updatedAt = mine.updatedAt - 1;
				queue(old, "mines/" + safeKey(mine.id), GSON.toJsonTree(gone));
			}
		}
	}

	public void pushTimer(String key, TimerEntry timer) {
		Mine m = MineManager.get().allMinesIncludingDeleted().get(key);
		if (m == null || m.area.isEmpty()) return;
		queue(ModConfig.get().codeFor(m.area), "timers/" + safeKey(key), GSON.toJsonTree(timer));
	}

	/**
	 * Gibt einem Lehen einen neuen Zufalls-Code, z. B. wenn der alte bei den Falschen gelandet ist.
	 * Alles zieht zum neuen Code um, beim alten verschwinden die Minen. Wer weiter mitmachen soll, braucht den neuen Code.
	 */
	public String changeCode(String area) {
		ModConfig cfg = ModConfig.get();
		MineManager mgr = MineManager.get();
		String old = cfg.mappedCode(area);
		if (area.isBlank() || old == null) return null;
		String fresh = BuiltInSync.newGroupCode();
		cfg.setLehenCode(area, fresh);
		long now = System.currentTimeMillis();
		for (Mine m : mgr.allMinesIncludingDeleted().values()) {
			if (m.area.isEmpty() || !m.area.equalsIgnoreCase(area)) continue;
			m.updatedAt = now;
			queue(fresh, "mines/" + safeKey(m.id), GSON.toJsonTree(m));
			if (!m.deleted) {
				Mine gone = m.copy();
				gone.deleted = true;
				gone.updatedAt = now - 1;
				queue(old, "mines/" + safeKey(m.id), GSON.toJsonTree(gone));
			}
			TimerEntry t = mgr.allTimers().get(m.id);
			if (t != null) queue(fresh, "timers/" + safeKey(m.id), GSON.toJsonTree(t));
		}
		mgr.saveMines();
		requestPoll();
		return fresh;
	}

	private void queue(String code, String path, JsonElement value) {
		if (!isActive() || code.isBlank()) return;
		pending.computeIfAbsent(code, k -> new JsonObject()).add(path, value);
	}

	/** Schickt die gesammelten Änderungen je Code in einem einzigen PATCH (Firebase Multi-Path-Update). */
	private void flush() {
		for (String code : new ArrayList<>(pending.keySet())) flush(code);
	}

	private void flush(String code) {
		JsonObject batch = pending.get(code);
		if (batch == null || batch.size() == 0 || flushInFlight.contains(code)) return;
		pending.remove(code);
		HttpRequest req;
		try {
			req = request(code)
					.header("Content-Type", "application/json")
					.method("PATCH", HttpRequest.BodyPublishers.ofString(GSON.toJson(batch)))
					.build();
		} catch (IllegalArgumentException e) {
			errors.put(code, "Ungültige URL");
			return;
		}
		flushInFlight.add(code);
		http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((resp, err) -> Minecraft.getInstance().execute(() -> {
			flushInFlight.remove(code);
			boolean failed = err != null || resp.statusCode() != 200;
			if (err != null) errors.put(code, "Keine Verbindung");
			else if (resp.statusCode() != 200) errors.put(code, "Fehler " + resp.statusCode() + " beim Hochladen");
			if (failed) {
				// Neuere Änderungen behalten Vorrang, der Rest wird erneut versucht
				JsonObject now = pending.computeIfAbsent(code, k -> new JsonObject());
				for (Map.Entry<String, JsonElement> e : batch.entrySet()) {
					if (!now.has(e.getKey())) now.add(e.getKey(), e.getValue());
				}
				retryTicks = 20 * 15;
			}
		}));
	}
}
