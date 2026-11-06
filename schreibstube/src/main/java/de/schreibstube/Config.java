package de.schreibstube;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Einstellungen in config/schreibstube.json. */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Config instance = new Config();

	/** Schreibstube öffnet sich, wenn man mit Pergament und Feder rechtsklickt. */
	public boolean openOnUse = true;
	/** Name des Schreibzeugs (Teil des Namens reicht). */
	public String itemName = "Pergament und Feder";
	/** Pause zwischen zwei /letter-Befehlen in Millisekunden. */
	public int delayMs = 1500;
	public static final int MIN_DELAY = 600, MAX_DELAY = 5000;
	/** Server-Meldung „Du hast den Brief bearbeitet“ beim Schreiben ausblenden. */
	public boolean hideServerEcho = true;
	/** Beim Kopieren Formatierung als &-Codes mitnehmen. */
	public boolean copyWithCodes = true;
	/** Beim Einfügen &-Codes als Formatierung lesen. */
	public boolean pasteCodes = true;
	/** Nach dem Schreiben die Empfänger-Auswahl öffnen. */
	public boolean askRecipientAfterWrite = true;
	/** Gruß und Name am Briefende, mit &-Codes. %spieler% wird zum eigenen Namen. */
	public String signature = "&oHochachtungsvoll,\n&l%spieler%";
	/** Zuletzt angeschriebene Spieler, neueste zuerst. */
	public List<String> recentRecipients = new ArrayList<>();
	/** Nur auf Servern mit „ottonien“ in der Adresse aktiv. */
	public boolean onlyOnOttonien = true;

	public static Config get() {
		return instance;
	}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("schreibstube");
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("schreibstube.json");
	}

	public static void load() {
		Path p = path();
		if (Files.exists(p)) {
			try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
				Config c = GSON.fromJson(r, Config.class);
				if (c != null) instance = c;
			} catch (Exception e) {
				SchreibstubeClient.LOGGER.warn("Konnte {} nicht lesen, nehme Standardwerte", p, e);
			}
		}
		if (instance.itemName == null || instance.itemName.isBlank()) instance.itemName = "Pergament und Feder";
		if (instance.signature == null) instance.signature = "";
		if (instance.recentRecipients == null) instance.recentRecipients = new ArrayList<>();
		instance.delayMs = Math.clamp(instance.delayMs, MIN_DELAY, MAX_DELAY);
		instance.save();
	}

	public void rememberRecipient(String name) {
		recentRecipients.removeIf(n -> n.equalsIgnoreCase(name));
		recentRecipients.addFirst(name);
		while (recentRecipients.size() > 12) recentRecipients.removeLast();
		save();
	}

	public void save() {
		try {
			Files.createDirectories(path().getParent());
			try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
				GSON.toJson(this, w);
			}
		} catch (IOException e) {
			SchreibstubeClient.LOGGER.warn("Konnte Einstellungen nicht speichern", e);
		}
	}
}
