package de.lehenskarte.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lehenskarte.LehenskarteClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Live-Daten von der öffentlichen API hinter karte.ottonien.com. */
public final class OttonienData {
	private static final String API = "https://mcp-api.ottonien.com/api/public/";
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	public record Region(String id, String name, boolean enabled, int x, int z, String agriculture, String mines,
						 String bonus, int fertility) {
	}

	public record Faction(String uuid, String name, String rank, int rankPriority, String leader, int members,
						  String description, String banner, String region, String lordName, String lordRegion,
						  String lordNameTop, String lordRegionTop, String orderName, List<String> vassalNames) {
		public boolean hasLord() {
			return !lordName.isEmpty();
		}

		/** Lehen des obersten Lehnsherrn, nach dem sich die Farbe richtet. */
		public String realmRegion() {
			return lordRegionTop.isEmpty() ? region : lordRegionTop;
		}
	}

	/** Unveränderlicher Stand der Daten. */
	public record Snapshot(Map<String, Region> regions, Map<String, Faction> factionByRegion, Map<String, Faction> factionByUuid,
						   long fetchedAt) {
		public static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of(), 0);

		public Region region(String id) {
			return regions.get(id);
		}

		public Faction owner(String regionId) {
			return factionByRegion.get(regionId);
		}
	}

	private static volatile Snapshot snapshot = Snapshot.EMPTY;
	private static volatile boolean loading;
	private static volatile String lastError;

	private OttonienData() {
	}

	public static Snapshot get() {
		return snapshot;
	}

	public static boolean isLoading() {
		return loading;
	}

	public static String lastError() {
		return lastError;
	}

	/** Lädt Lehen und Gefolge neu. Ruft onDone im Hintergrund-Thread auf, wenn sich etwas geändert hat. */
	public static void refresh(Runnable onChange) {
		if (loading) return;
		loading = true;
		CompletableFuture<String> regions = fetch("regions");
		CompletableFuture<String> factions = fetch("factions");
		regions.thenCombine(factions, OttonienData::parse).whenComplete((snap, err) -> {
			loading = false;
			if (err != null) {
				lastError = err.getMessage();
				LehenskarteClient.LOGGER.warn("Lehenskarte: Daten konnten nicht geladen werden: {}", err.toString());
				return;
			}
			lastError = null;
			boolean changed = !snap.regions.equals(snapshot.regions) || !snap.factionByRegion.equals(snapshot.factionByRegion);
			snapshot = snap;
			if (changed) onChange.run();
		});
	}

	private static CompletableFuture<String> fetch(String what) {
		HttpRequest req = HttpRequest.newBuilder(URI.create(API + what))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", "Lehenskarte-Mod (Minecraft)")
				.header("Accept", "application/json")
				.GET().build();
		return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(r -> {
			if (r.statusCode() != 200) throw new IllegalStateException(what + ": HTTP " + r.statusCode());
			return r.body();
		});
	}

	private static Snapshot parse(String regionsJson, String factionsJson) {
		Map<String, Region> regions = new HashMap<>();
		for (JsonElement el : JsonParser.parseString(regionsJson).getAsJsonArray()) {
			JsonObject o = el.getAsJsonObject();
			Region r = new Region(str(o, "id"), str(o, "name"), o.has("enabled") && o.get("enabled").getAsBoolean(),
					num(o, "x"), num(o, "z"), str(o, "agriculture"), str(o, "mines"), str(o, "bonus"), num(o, "fertility"));
			regions.put(r.id, r);
		}
		Map<String, Faction> byRegion = new HashMap<>();
		Map<String, Faction> byUuid = new HashMap<>();
		for (JsonElement el : JsonParser.parseString(factionsJson).getAsJsonArray()) {
			JsonObject o = el.getAsJsonObject();
			List<String> vassals = new ArrayList<>();
			if (o.has("vassal_names") && o.get("vassal_names").isJsonArray()) {
				for (JsonElement v : o.getAsJsonArray("vassal_names")) vassals.add(v.getAsString());
			}
			Faction f = new Faction(str(o, "uuid"), str(o, "name"), str(o, "rank_name"), num(o, "rank_priority"),
					str(o, "leader_name"), num(o, "member_count"), str(o, "description"), str(o, "banner"), str(o, "region"),
					str(o, "lord_name"), str(o, "lord_region"), str(o, "lord_name_recursive"), str(o, "lord_region_recursive"),
					str(o, "order_name"), Collections.unmodifiableList(vassals));
			byUuid.put(f.uuid, f);
			if (!f.region.isEmpty()) {
				Faction old = byRegion.get(f.region);
				if (old == null || f.rankPriority > old.rankPriority) byRegion.put(f.region, f);
			}
		}
		return new Snapshot(Map.copyOf(regions), Map.copyOf(byRegion), Map.copyOf(byUuid), System.currentTimeMillis());
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static int num(JsonObject o, String k) {
		JsonElement e = o.get(k);
		if (e == null || e.isJsonNull()) return 0;
		try {
			return (int) Math.round(e.getAsDouble());
		} catch (Exception ex) {
			return 0;
		}
	}
}
