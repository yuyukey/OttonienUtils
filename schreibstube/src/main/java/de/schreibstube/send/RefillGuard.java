package de.schreibstube.send;

import de.schreibstube.SchreibstubeClient;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;

/**
 * Mouse Wheelie füllt leere Hotbar-Plätze automatisch nach. Wenn der Server beim ersten /letter das
 * Pergament gegen den Brief tauscht, sieht Mouse Wheelie kurz einen leeren Platz, klickt den Stapel
 * Pergamente hinein und hat danach den Brief am Mauszeiger. Deshalb schalten wir das Nachfüllen nur
 * während des Schreibens ab. Gespeichert wird dabei nichts, die eigene Einstellung bleibt unverändert.
 */
final class RefillGuard {
	private static Field enableField;
	private static Object refillConfig;
	private static boolean suspended;
	private static boolean previous;

	private RefillGuard() {
	}

	static void suspend() {
		if (suspended || !FabricLoader.getInstance().isModLoaded("mousewheelie")) return;
		try {
			Class<?> mw = Class.forName("de.siphalor.mousewheelie.MouseWheelie");
			Object config = mw.getField("config").get(null);
			if (config == null) return;
			refillConfig = config.getClass().getField("refill").get(config);
			enableField = refillConfig.getClass().getField("enable");
			previous = enableField.getBoolean(refillConfig);
			enableField.setBoolean(refillConfig, false);
			suspended = true;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			SchreibstubeClient.LOGGER.warn("Konnte das Nachfüllen von Mouse Wheelie nicht pausieren", e);
		}
	}

	static void restore() {
		if (!suspended) return;
		suspended = false;
		try {
			// nur zurücksetzen, wenn es noch unser Objekt ist (Mouse Wheelie lädt seine Einstellungen manchmal neu)
			Class<?> mw = Class.forName("de.siphalor.mousewheelie.MouseWheelie");
			Object config = mw.getField("config").get(null);
			Object refill = config == null ? null : config.getClass().getField("refill").get(config);
			if (refill == refillConfig && !enableField.getBoolean(refill)) enableField.setBoolean(refill, previous);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			SchreibstubeClient.LOGGER.warn("Konnte das Nachfüllen von Mouse Wheelie nicht wieder einschalten", e);
		}
	}
}
