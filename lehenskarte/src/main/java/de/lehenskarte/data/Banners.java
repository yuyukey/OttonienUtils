package de.lehenskarte.data;

import com.mojang.blaze3d.platform.NativeImage;
import de.lehenskarte.LehenskarteClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wappen der Gefolge, live aus dem Ottonien-Ressourcenpaket auf GitHub (wie auf karte.ottonien.com).
 * Wird nur im Render-Thread abgefragt; das Herunterladen läuft im Hintergrund.
 */
public final class Banners {
	private static final String BASE = "https://raw.githubusercontent.com/Ottonien/ottonien-reformed/main/assets/ottonien/textures/item/gui/wappen_";
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private enum State { LOADING, READY, FAILED }

	private static final Map<String, State> STATE = new ConcurrentHashMap<>();
	private static final Map<String, Long> FAILED_AT = new ConcurrentHashMap<>();

	private Banners() {
	}

	/** Dateiname wie auf der Website: Umlaute ausgeschrieben, leer = kein Wappen. */
	public static String normalize(String banner) {
		if (banner == null || banner.isBlank()) return "kein_wappen";
		String s = banner.toLowerCase(Locale.ROOT)
				.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
		return s.replaceAll("[^a-z0-9_.-]", "_");
	}

	/** Textur des Wappens oder null, solange es noch lädt. Fehlt es, kommt das leere Wappen. */
	public static Identifier texture(String banner) {
		String name = normalize(banner);
		State st = STATE.get(name);
		if (st == State.READY) return id(name);
		if (st == null || (st == State.FAILED && System.currentTimeMillis() - FAILED_AT.getOrDefault(name, 0L) > 300_000)) {
			load(name);
		}
		if (st == State.FAILED && !name.equals("kein_wappen")) return texture("kein_wappen");
		return null;
	}

	private static Identifier id(String name) {
		return Identifier.fromNamespaceAndPath(LehenskarteClient.MOD_ID, "banner/" + name);
	}

	private static Path cacheFile(String name) {
		return FabricLoader.getInstance().getConfigDir().resolve("lehenskarte").resolve("cache").resolve("wappen").resolve(name + ".png");
	}

	/**
	 * Zuerst das zwischengespeicherte Wappen zeigen (sofort da), dann im Hintergrund das aktuelle laden und
	 * austauschen, falls es sich geändert hat. So bleiben Wappen aktuell, auch wenn GitHub langsam ist.
	 */
	private static void load(String name) {
		STATE.put(name, State.LOADING);
		byte[] cached = null;
		try {
			Path file = cacheFile(name);
			if (Files.exists(file)) {
				cached = Files.readAllBytes(file);
				register(name, cached);
			}
		} catch (Exception e) {
			cached = null;
			STATE.put(name, State.LOADING);
		}
		byte[] old = cached;
		HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + name + ".png"))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", "Lehenskarte-Mod (Minecraft)")
				.GET().build();
		HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((r, err) -> {
			if (err != null || r.statusCode() != 200) {
				if (old == null) fail(name, err != null ? err.toString() : "HTTP " + r.statusCode());
				return;
			}
			byte[] png = r.body();
			if (old != null && Arrays.equals(old, png)) return;
			try {
				Path file = cacheFile(name);
				Files.createDirectories(file.getParent());
				Files.write(file, png);
			} catch (Exception e) {
				LehenskarteClient.LOGGER.debug("Wappen {} nicht zwischengespeichert: {}", name, e.toString());
			}
			Minecraft.getInstance().execute(() -> {
				try {
					register(name, png);
				} catch (Exception e) {
					if (old == null) fail(name, e.toString());
				}
			});
		});
	}

	private static void register(String name, byte[] png) throws java.io.IOException {
		NativeImage img = NativeImage.read(png);
		DynamicTexture tex = new DynamicTexture(() -> "lehenskarte banner " + name, img);
		Minecraft.getInstance().getTextureManager().register(id(name), tex);
		STATE.put(name, State.READY);
	}

	private static void fail(String name, String why) {
		LehenskarteClient.LOGGER.debug("Wappen {} nicht geladen: {}", name, why);
		STATE.put(name, State.FAILED);
		FAILED_AT.put(name, System.currentTimeMillis());
	}
}
