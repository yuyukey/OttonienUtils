package de.minentimer;

import java.io.InputStream;
import java.util.Properties;

/** Der eigene Sync-Server, mit dem die Mod ab Werk verbunden ist. */
public final class BuiltInSync {
	public static final String URL = "https://minen.vprondzinski.de";
	public static final String GROUP = "standard";
	/** Muss mit MINENTIMER_KEY auf dem Server übereinstimmen. Steht nicht im Quellcode, sondern wird beim Bauen eingefügt. */
	public static final String KEY = loadKey();

	private BuiltInSync() {
	}

	private static String loadKey() {
		try (InputStream in = BuiltInSync.class.getResourceAsStream("/minentimer-sync.properties")) {
			if (in == null) return "";
			Properties p = new Properties();
			p.load(in);
			return p.getProperty("key", "").trim();
		} catch (Exception e) {
			return "";
		}
	}
}
