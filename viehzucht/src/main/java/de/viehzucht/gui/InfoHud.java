package de.viehzucht.gui;

import de.viehzucht.Config;
import de.viehzucht.ViehzuchtClient;
import de.viehzucht.data.AnimalInfo;
import de.viehzucht.data.AnimalStore;
import de.viehzucht.data.Species;
import de.viehzucht.data.Tracker;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tafel rechts neben dem Fadenkreuz, solange man ein Tier anguckt. */
public final class InfoHud {
	public static final int GREEN = 0xFF55FF55, RED = 0xFFFF5555, ORANGE = 0xFFFFAA00, PINK = 0xFFFF77CC,
			AQUA = 0xFF55DDFF, GRAY = 0xFFAAAAAA, DIM = 0xFF808080, WHITE = 0xFFFFFFFF, YELLOW = 0xFFFFE055,
			MALE = 0xFF66AAFF, FEMALE = 0xFFFF88CC;

	private record Row(String label, String text, int color, int barValue, int barMax) {
		static Row text(String text, int color) {
			return new Row(null, text, color, -1, -1);
		}
	}

	private InfoHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!ViehzuchtClient.isActive() || !Config.get().overlayEnabled || mc.options.hideGui || mc.player == null || mc.screen != null) return;
		// solange die Spielerliste (TAB) offen ist, ausblenden
		if (mc.options.keyPlayerList.isDown()) return;
		Entity e = mc.crosshairPickEntity;
		if (!Tracker.isAnimal(e)) return;
		draw(g, mc.font, e, System.currentTimeMillis());
	}

	public static AnimalInfo.Status status(Entity e, long now) {
		AnimalInfo info = AnimalStore.get(e.getUUID());
		if (info == null) info = new AnimalInfo();
		return info.breedStatus(now, Species.byName(name(e)), isBaby(e));
	}

	private static boolean isBaby(Entity e) {
		return e instanceof AgeableMob a && a.isBaby();
	}

	private static String name(Entity e) {
		return e.getCustomName() != null ? e.getCustomName().getString() : e.getDisplayName().getString();
	}

	private static void draw(GuiGraphics g, Font font, Entity e, long now) {
		AnimalInfo info = AnimalStore.get(e.getUUID());
		Species species = Species.byName(info != null && info.name != null ? info.name : name(e));
		AnimalInfo data = info != null ? info : new AnimalInfo();
		AnimalInfo.Status status = data.breedStatus(now, species, isBaby(e));
		int stateColor = color(status.state());

		// Kopfzeile: Name und Geschlecht
		Component name = e.getDisplayName();
		String gender = "";
		int genderColor = WHITE;
		if ("m".equals(data.gender)) {
			gender = "♂ " + nz(data.genderWord, "männlich");
			genderColor = MALE;
		} else if ("w".equals(data.gender)) {
			gender = "♀ " + nz(data.genderWord, "weiblich");
			genderColor = FEMALE;
		}

		List<Row> rows = new ArrayList<>();
		if (status.state() == AnimalInfo.State.UNKNOWN) {
			String wait = de.viehzucht.data.AutoQuery.waitingText(e.getUUID());
			if (wait != null) status = new AnimalInfo.Status(AnimalInfo.State.UNKNOWN, wait, false);
		}
		rows.add(Row.text(icon(status.state()) + " " + capitalize(status.text()) + (status.estimated() ? " (geschätzt)" : ""), stateColor));
		if (data.hasCapture()) {
			if (data.mast >= 0 && data.mastMax > 0) rows.add(new Row("Mast", data.mast + "/" + data.mastMax, WHITE, data.mast, data.mastMax));
			for (AnimalInfo.Line l : data.lines) {
				String k = l.key.toLowerCase(Locale.ROOT);
				if (k.equals("geschlecht") || k.equals("mast") || k.equals("paarungsbereit") || k.equals("zuchtbereit")) continue;
				rows.add(lineRow(data, l, now));
			}
			String age = "Stand: " + ago(now - data.capturedAt);
			if (data.touchedAt > data.capturedAt) age += " · seitdem angeklickt";
			rows.add(Row.text(age, DIM));
		} else if (species != null) {
			rows.add(Row.text("Paarung ab Mast " + species.breedMast() + " · Wartezeit " + tableTime(species.cooldownMs()), DIM));
		}

		// Größe ausmessen
		int pad = 5, barW = 50, lineH = 11;
		int headW = font.width(name) + (gender.isEmpty() ? 0 : 8 + font.width(gender));
		int w = headW;
		for (Row r : rows) {
			int rw = r.barMax > 0 ? font.width(r.label) + 4 + barW + 4 + font.width(r.text) : font.width(r.text);
			if (r.label != null && r.barMax <= 0) rw += font.width(r.label + ": ");
			w = Math.max(w, rw);
		}
		w += pad * 2 + 3;
		int h = pad + 10 + 3 + rows.size() * lineH + pad - 2;
		int x = g.guiWidth() / 2 + 14;
		int y = g.guiHeight() / 2 - 14;
		if (x + w > g.guiWidth() - 2) x = g.guiWidth() - 2 - w;

		g.fill(x, y, x + w, y + h, 0xB0101010);
		g.fill(x, y, x + 3, y + h, stateColor);
		int tx = x + 3 + pad, ty = y + pad;
		g.drawString(font, name, tx, ty, WHITE, true);
		if (!gender.isEmpty()) g.drawString(font, gender, tx + font.width(name) + 8, ty, genderColor, true);
		ty += 10;
		g.fill(tx, ty, x + w - pad, ty + 1, 0x40FFFFFF);
		ty += 3;
		for (Row r : rows) {
			if (r.barMax > 0) {
				g.drawString(font, r.label, tx, ty, GRAY, false);
				int bx = tx + font.width(r.label) + 4, by = ty + 2;
				int need = species != null ? species.breedMast() : r.barMax;
				int fillColor = r.barValue >= need ? GREEN : YELLOW;
				g.fill(bx, by, bx + barW, by + 5, 0xFF303030);
				g.fill(bx, by, bx + (int) Math.round(barW * Math.min(1.0, r.barValue / (double) r.barMax)), by + 5, fillColor);
				g.drawString(font, r.text, bx + barW + 4, ty, WHITE, false);
			} else if (r.label != null) {
				g.drawString(font, r.label + ": ", tx, ty, GRAY, false);
				g.drawString(font, r.text, tx + font.width(r.label + ": "), ty, r.color, false);
			} else {
				g.drawString(font, r.text, tx, ty, r.color, r.color != DIM);
			}
			ty += lineH;
		}
	}

	/** Melkbar, Scherbar, Mastverlust, Leben … mit live laufender Zeit. */
	private static Row lineRow(AnimalInfo data, AnimalInfo.Line l, long now) {
		List<AnimalInfo.Part> parts = data.parts(l.value, now);
		boolean lossLine = l.key.toLowerCase(Locale.ROOT).startsWith("mastverlust");
		StringBuilder text = new StringBuilder();
		boolean allDone = true, anyStatus = false, anyOther = false;
		for (AnimalInfo.Part p : parts) {
			if (!text.isEmpty()) text.append(" | ");
			if (p.kind() == AnimalInfo.Kind.TIME && p.done()) text.append(lossLine ? "Mast sinkt" : "bereit");
			else text.append(p.text());
			if (p.kind() != AnimalInfo.Kind.OTHER) anyStatus = true;
			else anyOther = true;
			if (!p.done()) allDone = false;
		}
		int color;
		if (lossLine) color = parts.stream().anyMatch(p -> p.kind() == AnimalInfo.Kind.TIME) ? (allDone ? RED : YELLOW) : GRAY;
		else if (anyStatus && allDone) color = GREEN;
		else if (anyStatus) color = YELLOW;
		else if (anyOther && l.value.toLowerCase(Locale.ROOT).startsWith("nur")) color = DIM;
		else color = WHITE;
		return new Row(l.key, text.toString(), color, -1, -1);
	}

	public static int color(AnimalInfo.State s) {
		return switch (s) {
			case READY -> GREEN;
			case LOVE -> PINK;
			case WAIT -> ORANGE;
			case GROWING -> AQUA;
			case NEED_MAST, BLOCKED -> RED;
			case UNKNOWN -> GRAY;
		};
	}

	private static String icon(AnimalInfo.State s) {
		return switch (s) {
			case READY, LOVE -> "❤";
			case WAIT -> "⌛";
			case GROWING -> "✿";
			case NEED_MAST, BLOCKED -> "✖";
			case UNKNOWN -> "?";
		};
	}

	/** Werte aus der Tabelle: „24h“, „40m“. */
	private static String tableTime(long ms) {
		long min = ms / 60_000;
		return min % 60 == 0 ? (min / 60) + "h" : min >= 60 ? (min / 60) + "h " + (min % 60) + "m" : min + "m";
	}

	static String ago(long ms) {
		long min = ms / 60_000;
		if (min < 1) return "gerade eben";
		if (min < 60) return "vor " + min + " Min";
		if (min < 48 * 60) return "vor " + (min / 60) + " Std";
		return "vor " + (min / 1440) + " Tagen";
	}

	private static String capitalize(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	private static String nz(String s, String fallback) {
		return s == null || s.isBlank() ? fallback : s;
	}
}
