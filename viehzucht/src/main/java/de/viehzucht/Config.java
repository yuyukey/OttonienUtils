package de.viehzucht;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Einstellungen in config/viehzucht.json. */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Config instance = new Config();

	/** Overlay beim Angucken von Tieren zeigen. */
	public boolean overlayEnabled = true;
	/** Die Info-Nachricht des Servers aus dem Chat ausblenden, wenn das Overlay sie übernommen hat. */
	public boolean hideChatInfo = false;
	/** Tier-Info beim Anvisieren automatisch abfragen (wie Shift + Rechtsklick). */
	public boolean autoQuery = true;
	/** Automatisch angeforderte Antworten im Chat ausblenden. Eigene Klicks bleiben sichtbar. */
	public boolean hideAutoChat = true;
	/** So lange anvisieren, bevor gefragt wird. */
	public int hoverDelayMs = 150;
	/** Ältere Infos beim Anvisieren neu abfragen. */
	public int autoRefreshSeconds = 60;
	/** Größe der Tafel (1 = 100 %). */
	public float hudScale = 1.0f;
	/** Verschiebung der Tafel gegenüber ihrer Stelle neben dem Fadenkreuz (GUI-Pixel). */
	public int offsetX = 0;
	public int offsetY = 0;
	/** ♂/♀ an den Namen über dem Tier hängen. */
	public boolean nametagGender = true;
	/** Selbst geschorene Schafe leuchten lassen, bis sie wieder scherbar sind. */
	public boolean glowSheared = true;
	/** Zuchttiere nach einer Paarung leuchten lassen, bis sie sich wieder paaren können. */
	public boolean glowBred = true;
	/** Analyse-Datei schreiben (nur zur Fehlersuche). */
	public boolean analysis = false;
	/** Nur auf Servern mit „ottonien“ in der Adresse. Für Tests im Einzelspieler auf false setzen. */
	public boolean onlyOnOttonien = true;

	public static Config get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("viehzucht.json");
	}

	public static void load() {
		Path p = path();
		if (Files.exists(p)) {
			try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
				Config c = GSON.fromJson(r, Config.class);
				if (c != null) instance = c;
			} catch (Exception e) {
				ViehzuchtClient.LOGGER.warn("Konnte {} nicht lesen, nehme Standardwerte", p, e);
			}
		}
		instance.save();
	}

	public void save() {
		try {
			Files.createDirectories(path().getParent());
			try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
				GSON.toJson(this, w);
			}
		} catch (IOException e) {
			ViehzuchtClient.LOGGER.warn("Konnte Einstellungen nicht speichern", e);
		}
	}
}
