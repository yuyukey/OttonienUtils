package de.minentimer;

import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Properties;

/** Der eigene Sync-Server, mit dem die Mod ab Werk verbunden ist. */
public final class BuiltInSync {
	public static final String URL = "https://minen.vprondzinski.de";
	/** Gruppe älterer Versionen. Neue Installationen bekommen stattdessen einen Zufalls-Code, siehe {@link #newGroupCode()}. */
	public static final String GROUP = "standard";
	/** Ohne leicht verwechselbare Zeichen wie 0/O und 1/I. */
	private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final SecureRandom RANDOM = new SecureRandom();
	/** Muss mit MINENTIMER_KEY auf dem Server übereinstimmen. Steht nicht im Quellcode, sondern wird beim Bauen eingefügt. */
	public static final String KEY = loadKey();

	private BuiltInSync() {
	}

	/** Zufälliger Gruppen-Code wie „K7QX-M2PA-9TZD“, schwer zu erraten. */
	public static String newGroupCode() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 12; i++) {
			if (i > 0 && i % 4 == 0) sb.append('-');
			sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
		}
		return sb.toString();
	}

	/**
	 * Code eines Lehens, abgeleitet aus dem eigenen Code für Minen ohne Lehen. Wer denselben Code hat
	 * (z. B. eine Gruppe aus älteren Versionen), kommt so ohne Absprache auf denselben Lehen-Code.
	 * Aus dem Lehen-Code lässt sich der eigene Code nicht zurückrechnen.
	 */
	public static String lehenCode(String ownCode, String area) {
		try {
			byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(
					("minentimer-lehen\n" + ownCode.trim() + "\n" + area.trim().toLowerCase(java.util.Locale.ROOT))
							.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 12; i++) {
				if (i > 0 && i % 4 == 0) sb.append('-');
				sb.append(CODE_CHARS.charAt((h[i] & 0xFF) % CODE_CHARS.length()));
			}
			return sb.toString();
		} catch (java.security.NoSuchAlgorithmException e) {
			return newGroupCode();
		}
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
