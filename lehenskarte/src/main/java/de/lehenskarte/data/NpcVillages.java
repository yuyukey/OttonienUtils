package de.lehenskarte.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lehenskarte.LehenskarteClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * NPC-Dörfer von Ottonien. Mitgeliefert in assets/lehenskarte/npc_doerfer.json; eine eigene Liste unter
 * config/lehenskarte/npc_doerfer.json (gleiches Format) hat Vorrang.
 */
public final class NpcVillages {
	public record Village(String name, int x, int y, int z) {
	}

	private static List<Village> villages;

	private NpcVillages() {
	}

	public static List<Village> all() {
		if (villages == null) villages = load();
		return villages;
	}

	public static void reload() {
		villages = null;
	}

	private static List<Village> load() {
		Path override = FabricLoader.getInstance().getConfigDir().resolve("lehenskarte").resolve("npc_doerfer.json");
		try {
			if (Files.exists(override)) {
				try (Reader r = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
					return parse(r);
				}
			}
			try (InputStream in = NpcVillages.class.getResourceAsStream("/assets/lehenskarte/npc_doerfer.json")) {
				if (in == null) return List.of();
				return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
			}
		} catch (Exception e) {
			LehenskarteClient.LOGGER.warn("Lehenskarte: NPC-Dörfer nicht lesbar: {}", e.toString());
			return List.of();
		}
	}

	private static List<Village> parse(Reader r) {
		List<Village> list = new ArrayList<>();
		for (JsonElement el : JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("doerfer")) {
			JsonObject o = el.getAsJsonObject();
			list.add(new Village(o.get("name").getAsString(), o.get("x").getAsInt(),
					o.has("y") ? o.get("y").getAsInt() : 64, o.get("z").getAsInt()));
		}
		return List.copyOf(list);
	}
}
