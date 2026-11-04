package de.lehenskarte;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Einstellungen in config/lehenskarte.json. */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Config instance = new Config();

	/** Flächen, Grenzen und Banner in Xaeros Karten. */
	public boolean overlayEnabled = true;
	/** Anzeige des aktuellen Lehens oben am Bildschirm. */
	public boolean hudEnabled = true;
	/** Nur auf Servern mit „ottonien“ in der Adresse anzeigen. Für Tests im Einzelspieler auf false setzen. */
	public boolean onlyOnOttonien = true;
	/** Verschiebung aller Grenzen in Blöcken, zum Feinjustieren (/lehenskarte verschieben). */
	public int offsetX = 0;
	public int offsetZ = 0;
	/** Deckkraft der Flächen und Grenzen in Prozent. */
	public int fillOpacity = 14;
	public int borderOpacity = 70;
	/** Banner in der World Map zeigen. */
	public boolean bannersOnMap = true;
	/** Beim Rauszoomen Lehen zusammenfassen: erst Freiherrschaften mit Vasallen, dann ganze Machtbereiche. */
	public boolean groupOnZoomOut = true;
	/** In unerkundeten (schwarzen) Bereichen der World Map die gemalte Karte von karte.ottonien.com zeigen. */
	public boolean paintedMap = true;
	/** Verschiebung der gemalten Karte in Blöcken (positiv = nach rechts bzw. unten). */
	public int paintedMapOffsetX = 65;
	public int paintedMapOffsetZ = -40;
	/** Im Chat melden, wenn ein Lehen den Besitzer wechselt oder frei wird. */
	public boolean notifyChanges = true;
	/** NPC-Dörfer auf der World Map zeigen. */
	public boolean npcVillages = true;
	/** Version des Dateiformats, für Anpassungen alter Einstellungen. */
	public int configVersion = 0;

	private static final int CURRENT_VERSION = 3;

	/** 0.1.0-test1 war zu dunkel: alte Deckkraft-Werte auf die neuen Standards setzen. */
	private void migrate() {
		if (configVersion < 2) {
			fillOpacity = 14;
			borderOpacity = 70;
		}
		// test9 hatte eine geschätzte Verschiebung; ab test10 an echten Kartendaten (Insel von Finsterfels) gemessen
		if (configVersion < 3) {
			paintedMapOffsetX = 65;
			paintedMapOffsetZ = -40;
		}
		configVersion = CURRENT_VERSION;
	}

	public static Config get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("lehenskarte.json");
	}

	public static void load() {
		Path p = path();
		if (Files.exists(p)) {
			try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
				Config c = GSON.fromJson(r, Config.class);
				if (c != null) instance = c;
			} catch (Exception e) {
				LehenskarteClient.LOGGER.warn("Konnte {} nicht lesen, nehme Standardwerte", p, e);
			}
		}
		instance.migrate();
		instance.save();
	}

	public void save() {
		try {
			Files.createDirectories(path().getParent());
			try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
				GSON.toJson(this, w);
			}
		} catch (IOException e) {
			LehenskarteClient.LOGGER.warn("Konnte Einstellungen nicht speichern", e);
		}
	}
}
