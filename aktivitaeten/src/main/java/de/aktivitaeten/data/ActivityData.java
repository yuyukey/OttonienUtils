package de.aktivitaeten.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.aktivitaeten.AktivitaetenClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Aktivitäten von der öffentlichen API hinter karte.ottonien.com.
 * Die Karte lässt ein Lehen pulsieren, wenn sein Feld {@code player_gathering} nicht 0 ist.
 */
public final class ActivityData {
	private static final String API = "https://mcp-api.ottonien.com/api/public/";
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	/** Ein Lehen mit Aktivität. level ist der Wert von player_gathering (1 = Aktivität, höher = mehr los). */
	public record Activity(String id, String name, int x, int z, int level, String owner, String rank, String banner) {
	}

	public record Snapshot(List<Activity> activities, int regionCount, long fetchedAt) {
		public static final Snapshot EMPTY = new Snapshot(List.of(), 0, 0);

		public boolean loaded() {
			return fetchedAt > 0;
		}
	}

	private static volatile Snapshot snapshot = Snapshot.EMPTY;
	private static volatile boolean loading;
	private static volatile String lastError;
	/** Seit wann eine Aktivität bekannt ist, nur für die, die während dieser Sitzung begonnen haben. */
	private static final Map<String, Long> startedAt = new HashMap<>();

	private ActivityData() {
	}

	public static Snapshot get() {
		return snapshot;
	}

	public static String lastError() {
		return lastError;
	}

	/** Beginn einer Aktivität, wenn sie während dieser Sitzung angefangen hat, sonst null. */
	public static synchronized Long startedAt(String id) {
		return startedAt.get(id);
	}

	/**
	 * Lädt Lehen und Gefolge neu. onDone bekommt im Hintergrund-Thread den alten und den neuen Stand,
	 * aber nur, wenn das Laden geklappt hat.
	 */
	public static void refresh(BiConsumer<Snapshot, Snapshot> onDone) {
		if (loading) return;
		loading = true;
		fetch("regions").thenCombine(fetch("factions"), ActivityData::parse).whenComplete((snap, err) -> {
			loading = false;
			if (err != null) {
				lastError = err.getMessage();
				AktivitaetenClient.LOGGER.warn("Aktivitäten: Daten konnten nicht geladen werden: {}", err.toString());
				return;
			}
			lastError = null;
			Snapshot before = snapshot;
			set(snap);
			onDone.accept(before, snap);
		});
	}

	/** Neuen Stand übernehmen und den Beginn neuer Aktivitäten merken. Auch für Tests. */
	public static synchronized void set(Snapshot snap) {
		Snapshot before = snapshot;
		Set<String> now = new HashSet<>();
		for (Activity a : snap.activities()) {
			now.add(a.id());
			boolean isNew = before.activities().stream().noneMatch(b -> b.id().equals(a.id()));
			if (before.loaded() && isNew) startedAt.put(a.id(), System.currentTimeMillis());
		}
		startedAt.keySet().retainAll(now);
		snapshot = snap;
	}

	/** Lehen, in denen seit dem letzten Stand eine Aktivität begonnen hat. */
	public static List<Activity> newSince(Snapshot before, Snapshot now) {
		if (!before.loaded()) return List.of();
		Set<String> old = new HashSet<>();
		for (Activity a : before.activities()) old.add(a.id());
		List<Activity> out = new ArrayList<>();
		for (Activity a : now.activities()) if (!old.contains(a.id())) out.add(a);
		return out;
	}

	private static CompletableFuture<String> fetch(String what) {
		HttpRequest req = HttpRequest.newBuilder(URI.create(API + what))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", "Aktivitaeten-Mod (Minecraft)")
				.header("Accept", "application/json")
				.GET().build();
		return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(r -> {
			if (r.statusCode() != 200) throw new IllegalStateException(what + ": HTTP " + r.statusCode());
			return r.body();
		});
	}

	public static Snapshot parse(String regionsJson, String factionsJson) {
		Map<String, JsonObject> ownerByRegion = new HashMap<>();
		for (JsonElement e : JsonParser.parseString(factionsJson).getAsJsonArray()) {
			JsonObject f = e.getAsJsonObject();
			String region = str(f, "region");
			if (!region.isEmpty()) ownerByRegion.put(region, f);
		}
		List<Activity> list = new ArrayList<>();
		int count = 0;
		for (JsonElement e : JsonParser.parseString(regionsJson).getAsJsonArray()) {
			JsonObject r = e.getAsJsonObject();
			count++;
			int level = level(r.get("player_gathering"));
			if (level <= 0) continue;
			String id = str(r, "id");
			JsonObject f = ownerByRegion.get(id);
			list.add(new Activity(id, str(r, "name"), num(r, "x"), num(r, "z"), level,
					f != null ? str(f, "name") : "", f != null ? str(f, "rank_name") : "", Banners.forLehen(f != null ? str(f, "banner") : "", str(r, "name"))));
		}
		list.sort(Comparator.comparing(Activity::name));
		return new Snapshot(List.copyOf(list), count, System.currentTimeMillis());
	}

	/** player_gathering kommt als Text ("0", "1"), vielleicht später als Zahl oder Wahrheitswert. */
	private static int level(JsonElement e) {
		if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return 0;
		var p = e.getAsJsonPrimitive();
		if (p.isBoolean()) return p.getAsBoolean() ? 1 : 0;
		String s = p.getAsString().trim();
		try {
			return (int) Math.round(Double.parseDouble(s));
		} catch (NumberFormatException ex) {
			return s.isEmpty() || s.equalsIgnoreCase("false") ? 0 : 1;
		}
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static int num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		try {
			return e == null || e.isJsonNull() ? 0 : (int) Math.round(e.getAsDouble());
		} catch (Exception ex) {
			return 0;
		}
	}
}
