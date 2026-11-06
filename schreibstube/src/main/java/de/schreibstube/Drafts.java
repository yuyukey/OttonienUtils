package de.schreibstube;

import de.schreibstube.text.RichText;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Entwürfe als Textdateien in config/schreibstube/briefe (Formatierung als §-Codes, Seitenumbruch
 * als eigene Zeile „--- Neue Seite ---“). Der aktuelle Brief wird laufend in aktuell.txt gesichert.
 */
public final class Drafts {
	private static final String PAGE_MARK = "--- Neue Seite ---";
	private static final long MAX_SIZE = 256 * 1024;

	public record Entry(String name, Path path, FileTime modified, String preview) {
	}

	private Drafts() {
	}

	public static Path folder() {
		return Config.dir().resolve("briefe");
	}

	private static Path current() {
		return Config.dir().resolve("aktuell.txt");
	}

	public static String serialize(RichText t) {
		return t.toLegacy('§').replace(String.valueOf(RichText.PAGE_BREAK), "\n" + PAGE_MARK + "\n");
	}

	public static RichText deserialize(String s) {
		s = s.replace("\r\n", "\n").replace("\n" + PAGE_MARK + "\n", String.valueOf(RichText.PAGE_BREAK));
		if (s.startsWith("﻿")) s = s.substring(1);
		return RichText.parseLegacy(s, true);
	}

	public static RichText loadCurrent() {
		try {
			if (Files.exists(current())) return deserialize(Files.readString(current(), StandardCharsets.UTF_8));
		} catch (IOException e) {
			SchreibstubeClient.LOGGER.warn("Konnte den letzten Brief nicht laden", e);
		}
		return new RichText();
	}

	public static void saveCurrent(RichText t) {
		try {
			Files.createDirectories(current().getParent());
			Files.writeString(current(), serialize(t), StandardCharsets.UTF_8);
		} catch (IOException e) {
			SchreibstubeClient.LOGGER.warn("Konnte den Brief nicht sichern", e);
		}
	}

	public static List<Entry> list() {
		List<Entry> out = new ArrayList<>();
		if (!Files.isDirectory(folder())) return out;
		try (Stream<Path> files = Files.list(folder())) {
			for (Path p : files.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".txt")).toList()) {
				try {
					if (Files.size(p) > MAX_SIZE) continue;
					String name = p.getFileName().toString();
					String text = deserialize(Files.readString(p, StandardCharsets.UTF_8)).plain()
							.replace('\n', ' ').replace(RichText.PAGE_BREAK, ' ').strip();
					out.add(new Entry(name.substring(0, name.length() - 4), p, Files.getLastModifiedTime(p),
							text.length() > 60 ? text.substring(0, 60) + "…" : text));
				} catch (IOException ignored) {
				}
			}
		} catch (IOException e) {
			SchreibstubeClient.LOGGER.warn("Konnte die Entwürfe nicht lesen", e);
		}
		out.sort(Comparator.comparing(Entry::modified).reversed());
		return out;
	}

	public static RichText load(Entry e) throws IOException {
		return deserialize(Files.readString(e.path(), StandardCharsets.UTF_8));
	}

	/** Speichert unter einem Namen, ungültige Zeichen fallen weg. Gibt den Dateinamen ohne .txt zurück. */
	public static String save(String name, RichText t) throws IOException {
		String clean = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").strip();
		if (clean.isEmpty()) clean = "Brief";
		if (clean.length() > 60) clean = clean.substring(0, 60).strip();
		Files.createDirectories(folder());
		Files.writeString(folder().resolve(clean + ".txt"), serialize(t), StandardCharsets.UTF_8);
		return clean;
	}

	public static void delete(Entry e) throws IOException {
		Files.deleteIfExists(e.path());
	}
}
