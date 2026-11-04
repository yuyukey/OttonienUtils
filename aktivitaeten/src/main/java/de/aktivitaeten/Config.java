package de.aktivitaeten;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Einstellungen in config/aktivitaeten.json. */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Config instance = new Config();

	public enum Position {
		TOP_LEFT("Oben links"), TOP_RIGHT("Oben rechts"), MIDDLE_LEFT("Mitte links"), MIDDLE_RIGHT("Mitte rechts"), CUSTOM("Frei verschoben");

		public final String label;

		Position(String label) {
			this.label = label;
		}
	}

	/** Liste im Spiel zeigen. */
	public boolean hudEnabled = true;
	/** Wo die Liste steht. Oben links sitzt schon der Minen-Timer, deshalb standardmäßig rechts. */
	public Position position = Position.MIDDLE_RIGHT;
	/** Bei {@link Position#CUSTOM}: Anteil des freien Platzes links bzw. oberhalb (0 bis 1). */
	public float overlayX = 1f;
	public float overlayY = 0.5f;
	/** Höchstens so viele Lehen in der Liste, die nächsten zuerst. */
	public int maxEntries = 8;
	/** Liste auch zeigen, wenn gerade nirgends etwas los ist. */
	public boolean showWhenEmpty = true;
	/** Im Chat melden, wenn in einem Lehen eine Aktivität beginnt. */
	public boolean notifyNew = true;
	/** Größe der Liste in Prozent (50–200). */
	public int scale = 100;
	public static final int MIN_SCALE = 50, MAX_SCALE = 200;
	/** Nur auf Servern mit „ottonien“ in der Adresse anzeigen. Für Tests im Einzelspieler auf false setzen. */
	public boolean onlyOnOttonien = true;

	public static Config get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("aktivitaeten.json");
	}

	public static void load() {
		Path p = path();
		if (Files.exists(p)) {
			try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
				Config c = GSON.fromJson(r, Config.class);
				if (c != null) instance = c;
			} catch (Exception e) {
				AktivitaetenClient.LOGGER.warn("Konnte {} nicht lesen, nehme Standardwerte", p, e);
			}
		}
		if (instance.position == null) instance.position = Position.MIDDLE_RIGHT;
		instance.maxEntries = Math.clamp(instance.maxEntries, 1, 30);
		instance.scale = Math.clamp(instance.scale, MIN_SCALE, MAX_SCALE);
		instance.overlayX = Math.clamp(instance.overlayX, 0f, 1f);
		instance.overlayY = Math.clamp(instance.overlayY, 0f, 1f);
		instance.save();
	}

	public void save() {
		try {
			Files.createDirectories(path().getParent());
			try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
				GSON.toJson(this, w);
			}
		} catch (IOException e) {
			AktivitaetenClient.LOGGER.warn("Konnte Einstellungen nicht speichern", e);
		}
	}
}
