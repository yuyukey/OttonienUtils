package de.minentimer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ModConfig {
	public enum OverlayMode {
		ALL("Alle Minen"),
		/** Blendet Minen im Cooldown aus. Früher „Nur im Cooldown“ (COOLDOWN), wird übernommen. */
		@SerializedName(value = "READY", alternate = {"COOLDOWN"})
		READY("Nicht im Cooldown");

		public final String label;

		OverlayMode(String label) {
			this.label = label;
		}
	}

	public enum OverlayPosition {
		TOP_LEFT("Oben links"), TOP_RIGHT("Oben rechts"), MIDDLE_LEFT("Mitte links"), MIDDLE_RIGHT("Mitte rechts"), BOTTOM_RIGHT("Unten rechts"), CUSTOM("Eigene Position");

		public final String label;

		OverlayPosition(String label) {
			this.label = label;
		}
	}

	public enum Sound {
		BELL("Glocke"), CHIME("Glockenspiel"), PLING("Pling"), LEVELUP("Level-Up"), ORB("Erfahrung");

		public final String label;

		Sound(String label) {
			this.label = label;
		}
	}

	public enum SortMode {
		LIST("Reihenfolge"), DISTANCE("Entfernung"), STATUS("Status");

		public final String label;

		SortMode(String label) {
			this.label = label;
		}
	}

	public boolean overlayEnabled = true;
	public OverlayMode overlayMode = OverlayMode.ALL;
	public OverlayPosition overlayPosition = OverlayPosition.TOP_LEFT;
	/** Bei {@link OverlayPosition#CUSTOM}: Anteil des freien Platzes links bzw. oberhalb (0 bis 1). */
	public float overlayX = 0f;
	public float overlayY = 0f;
	public int overlayScalePercent = 100;
	public boolean overlayShowDistance = false;
	/** Ab so vielen Minuten Restzeit wird der Timer gelb angezeigt, 0 = aus. */
	public int soonMinutes = 5;

	public boolean soundEnabled = true;
	public Sound sound = Sound.BELL;
	public int soundVolumePercent = 100;
	public boolean chatMessage = true;

	public int quickMarkRadius = 40;
	/** Timer automatisch starten, wenn man in der Nähe einer Mine ihren Rohstoff abbaut. */
	public boolean autoMark = true;
	/** Nur Minen bis zu dieser Entfernung vom abgebauten Block zählen, damit Erz anderswo nichts auslöst. */
	public int autoMarkMaxDistance = 50;
	/** Läuft der Timer nur noch so viele Minuten, gilt Abbau als „Erz war schon wieder da“ und startet ihn neu. */
	public int autoMarkRestartMinutes = 10;
	/** Welche Blöcke beim Auto-Markieren zu welcher Rohstoffart gehören. Fehlende Arten bekommen die Standardblöcke. */
	public Map<ResourceType, List<String>> autoMarkBlocks = new EnumMap<>(ResourceType.class);
	public SortMode sortMode = SortMode.LIST;
	/** Eigene Reihenfolge der Minen (IDs), nur lokal. Neue Minen stehen hinten. */
	public List<String> mineOrder = new ArrayList<>();
	/** Gewähltes Lehen für Liste und Overlay, leer = alle. */
	public String activeArea = "";

	public boolean syncEnabled = true;
	public String syncUrl = BuiltInSync.URL;
	/** Neue Installationen bekommen einen eigenen Zufalls-Code, den man mit Freunden teilt. */
	public String syncGroup = BuiltInSync.newGroupCode();
	/** Schlüssel für den eigenen Sync-Server, wird als Header mitgeschickt (Firebase ignoriert ihn). */
	public String syncKey = BuiltInSync.KEY;
	/** Fehlt in Dateien von Version 1.0.0, siehe {@link #sanitize()}. */
	public Integer configVersion;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ModConfig instance;

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("minentimer");
	}

	public static ModConfig get() {
		if (instance == null) load();
		return instance;
	}

	public static void load() {
		Path file = dir().resolve("config.json");
		instance = null;
		if (Files.exists(file)) {
			try (Reader r = Files.newBufferedReader(file)) {
				instance = GSON.fromJson(r, ModConfig.class);
			} catch (Exception e) {
				MinenTimerClient.LOGGER.error("Konnte config.json nicht lesen", e);
			}
		}
		boolean fresh = instance == null && !Files.exists(file);
		if (instance == null) instance = new ModConfig();
		instance.sanitize();
		// Neue Installation: gleich speichern, damit der Zufalls-Code beim nächsten Start derselbe ist
		if (fresh) instance.save();
	}

	private void sanitize() {
		if (overlayMode == null) overlayMode = OverlayMode.ALL;
		if (overlayPosition == null) overlayPosition = OverlayPosition.TOP_LEFT;
		if (sound == null) sound = Sound.BELL;
		if (sortMode == null) sortMode = SortMode.LIST;
		if (mineOrder == null) mineOrder = new ArrayList<>();
		if (activeArea == null) activeArea = "";
		Map<ResourceType, List<String>> blocks = new EnumMap<>(ResourceType.class);
		if (autoMarkBlocks != null) {
			// Unbekannte Rohstoffarten aus der Datei kommen als null an
			autoMarkBlocks.forEach((t, list) -> {
				if (t != null && list != null) blocks.put(t, new ArrayList<>(list));
			});
		}
		// Erste Testversion hatte für Torf noch Schlamm geraten
		if (List.of("minecraft:mud", "minecraft:muddy_mangrove_roots").equals(blocks.get(ResourceType.TORF))) blocks.remove(ResourceType.TORF);
		for (ResourceType t : ResourceType.values()) blocks.computeIfAbsent(t, k -> new ArrayList<>(k.defaultBlocks()));
		autoMarkBlocks = blocks;
		if (syncUrl == null) syncUrl = "";
		if (syncGroup == null) syncGroup = "";
		if (syncKey == null) syncKey = "";
		// Alte Einstellungen ohne eingerichtetes Teilen auf den eigenen Server umstellen
		if (configVersion == null && syncUrl.isBlank()) useBuiltInSync();
		configVersion = 2;
	}

	/** Zurück auf den eingebauten Server. Ein vorhandener Gruppen-Code bleibt, damit man in seiner Gruppe bleibt. */
	public void useBuiltInSync() {
		syncEnabled = true;
		syncUrl = BuiltInSync.URL;
		if (syncGroup.isBlank()) syncGroup = BuiltInSync.newGroupCode();
		syncKey = BuiltInSync.KEY;
	}

	public void save() {
		try {
			Files.createDirectories(dir());
			try (Writer w = Files.newBufferedWriter(dir().resolve("config.json"))) {
				GSON.toJson(this, w);
			}
		} catch (IOException e) {
			MinenTimerClient.LOGGER.error("Konnte config.json nicht speichern", e);
		}
	}
}
