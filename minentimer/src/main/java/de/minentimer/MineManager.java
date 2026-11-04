package de.minentimer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Hält alle Minen und ihre Timer (einer pro Mine), speichert sie und verschickt Bereit-Meldungen. */
public final class MineManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type MINES_TYPE = new TypeToken<LinkedHashMap<String, Mine>>() {}.getType();
	private static final Type TIMERS_TYPE = new TypeToken<HashMap<String, TimerEntry>>() {}.getType();
	private static final MineManager INSTANCE = new MineManager();

	private final Map<String, Mine> mines = new LinkedHashMap<>();
	/** Timer nach Minen-ID. */
	private final Map<String, TimerEntry> timers = new HashMap<>();
	/** Für welchen readyAt-Wert schon eine Bereit-Meldung kam. */
	private final Map<String, Long> notified = new HashMap<>();

	public static MineManager get() {
		return INSTANCE;
	}

	// ---------- Abfragen ----------

	public List<Mine> mines() {
		List<Mine> list = new ArrayList<>();
		for (Mine m : mines.values()) if (!m.deleted) list.add(m);
		return list;
	}

	public Map<String, Mine> allMinesIncludingDeleted() {
		return mines;
	}

	public Map<String, TimerEntry> allTimers() {
		return timers;
	}

	public List<Mine> sortedMines(ModConfig.SortMode mode) {
		List<Mine> list = mines();
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		switch (mode) {
			case DISTANCE -> {
				if (mc.player != null) {
					double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
					list.sort(Comparator.comparingDouble(m -> m.distanceTo(px, py, pz)));
				}
			}
			case STATUS -> list.sort(Comparator.comparingLong(m -> remainingMs(m, now)));
			default -> {
				List<String> order = ModConfig.get().mineOrder;
				list.sort(Comparator.comparingInt(m -> {
					int i = order.indexOf(m.id);
					return i < 0 ? Integer.MAX_VALUE : i;
				}));
			}
		}
		return list;
	}

	/** Nur die Minen des gewählten Lehens, sortiert. */
	public List<Mine> visibleMines(ModConfig.SortMode mode) {
		String active = activeArea();
		List<Mine> list = sortedMines(mode);
		if (!active.isEmpty()) list.removeIf(m -> !inArea(m, active));
		return list;
	}

	/**
	 * Verschiebt eine Mine in der angezeigten Liste um eine Stelle und merkt sich das als eigene Reihenfolge.
	 * Die angezeigte Liste kann nach Lehen gefiltert sein, getauscht wird mit dem sichtbaren Nachbarn.
	 */
	public boolean move(List<Mine> shown, int index, int delta) {
		int target = index + delta;
		if (index < 0 || index >= shown.size() || target < 0 || target >= shown.size()) return false;
		ModConfig cfg = ModConfig.get();
		List<Mine> list = sortedMines(cfg.sortMode);
		int a = list.indexOf(shown.get(index)), b = list.indexOf(shown.get(target));
		if (a < 0 || b < 0) return false;
		list.set(a, shown.get(target));
		list.set(b, shown.get(index));
		cfg.mineOrder = new ArrayList<>(list.stream().map(m -> m.id).toList());
		cfg.sortMode = ModConfig.SortMode.LIST;
		cfg.save();
		return true;
	}

	// ---------- Lehen ----------

	/** Auswahlwert für Minen ohne Lehen. */
	public static final String NO_AREA = "#ohne";

	/** Alle vergebenen Lehen, alphabetisch. */
	public List<String> areas() {
		Map<String, String> byKey = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Mine m : mines()) if (!m.area.isEmpty()) byKey.putIfAbsent(m.area, m.area);
		return new ArrayList<>(byKey.values());
	}

	/** Reihenfolge beim Durchschalten: alle, jedes Lehen, ohne Lehen (falls es solche Minen gibt). */
	public List<String> areaCycle() {
		List<String> cycle = new ArrayList<>();
		cycle.add("");
		List<String> areas = areas();
		cycle.addAll(areas);
		if (!areas.isEmpty() && mines().stream().anyMatch(m -> m.area.isEmpty())) cycle.add(NO_AREA);
		return cycle;
	}

	/** Gewähltes Lehen, leer = alle. Gibt es das Lehen nicht mehr, gelten wieder alle. */
	public String activeArea() {
		String active = ModConfig.get().activeArea;
		for (String a : areaCycle()) if (a.equalsIgnoreCase(active)) return a;
		return "";
	}

	/** Schaltet zum nächsten (+1) bzw. vorherigen (-1) Lehen und liefert es. */
	public String cycleArea(int dir) {
		List<String> cycle = areaCycle();
		int i = cycle.indexOf(activeArea());
		String next = cycle.get(Math.floorMod(i + dir, cycle.size()));
		ModConfig cfg = ModConfig.get();
		cfg.activeArea = next;
		cfg.save();
		return next;
	}

	public static boolean inArea(Mine m, String area) {
		return area.isEmpty() || (NO_AREA.equals(area) ? m.area.isEmpty() : m.area.equalsIgnoreCase(area));
	}

	public static String areaLabel(String area) {
		if (area.isEmpty()) return "Alle Lehen";
		return NO_AREA.equals(area) ? "Ohne Lehen" : area;
	}

	public TimerEntry timer(Mine mine) {
		return timers.get(mine.id);
	}

	public long remainingMs(Mine mine, long now) {
		TimerEntry t = timers.get(mine.id);
		return t == null ? 0 : Math.max(0, t.readyAt - now);
	}

	public boolean isRunning(Mine mine, long now) {
		return remainingMs(mine, now) > 0;
	}

	public Mine nearest(double px, double py, double pz, double radius) {
		Mine best = null;
		double bestDist = radius;
		for (Mine m : mines()) {
			double d = m.distanceTo(px, py, pz);
			if (d <= bestDist) {
				best = m;
				bestDist = d;
			}
		}
		return best;
	}

	/** Nächste Mine dieser Rohstoffart im Umkreis, sonst null. */
	public Mine nearestOfType(ResourceType type, double px, double py, double pz, double radius) {
		Mine best = null;
		double bestDist = radius;
		for (Mine m : mines()) {
			if (m.type != type) continue;
			double d = m.distanceTo(px, py, pz);
			if (d <= bestDist) {
				best = m;
				bestDist = d;
			}
		}
		return best;
	}

	// ---------- Timer ändern ----------

	public void start(Mine mine) {
		long now = System.currentTimeMillis();
		TimerEntry t = new TimerEntry();
		t.startedAt = now;
		t.readyAt = now + mine.respawnMinutes() * 60_000L;
		t.updatedAt = now;
		t.by = playerName();
		putTimer(mine.id, t);
	}

	public void reset(Mine mine) {
		TimerEntry t = new TimerEntry();
		t.updatedAt = System.currentTimeMillis();
		t.by = playerName();
		putTimer(mine.id, t);
	}

	/** Verschiebt die Restzeit eines laufenden Timers (z. B. wenn zu spät markiert). */
	public void adjust(Mine mine, long deltaMs) {
		TimerEntry old = timers.get(mine.id);
		long now = System.currentTimeMillis();
		if (old == null || !old.isRunning(now)) return;
		TimerEntry t = new TimerEntry();
		t.startedAt = old.startedAt;
		t.readyAt = Math.max(now + 1000, old.readyAt + deltaMs);
		t.updatedAt = now;
		t.by = old.by;
		putTimer(mine.id, t);
	}

	private void putTimer(String key, TimerEntry t) {
		timers.put(key, t);
		notified.remove(key);
		saveTimers();
		SyncManager.get().pushTimer(key, t);
	}

	// ---------- Minen ändern ----------

	public Mine createMine(String name, int x, int y, int z) {
		return new Mine(newId(name), name, x, y, z, MineSize.VORKOMMEN, ResourceType.EISEN);
	}

	public void saveMine(Mine mine) {
		Mine before = mines.get(mine.id);
		String previousArea = before == null || before == mine ? null : before.area;
		mine.updatedAt = System.currentTimeMillis();
		mines.put(mine.id, mine);
		saveMines();
		SyncManager.get().pushMine(mine, previousArea);
	}

	/** Verlässt ein Lehen: seine Minen und Timer verschwinden nur hier, bei den anderen bleiben sie. */
	public void leaveLehen(String area) {
		ModConfig cfg = ModConfig.get();
		SyncManager.get().reportLeft(cfg.mappedCode(area));
		mines.values().removeIf(m -> {
			boolean gone = !m.area.isEmpty() && m.area.equalsIgnoreCase(area);
			if (gone) timers.remove(m.id);
			return gone;
		});
		cfg.lehenCodes.keySet().removeIf(k -> k.equalsIgnoreCase(area));
		if (!cfg.hasLeft(area)) cfg.leftLehen.add(area);
		if (cfg.activeArea.equalsIgnoreCase(area)) cfg.activeArea = "";
		cfg.save();
		saveMines();
		saveTimers();
	}

	public void deleteMine(Mine mine) {
		mine.deleted = true;
		saveMine(mine);
	}

	private String newId(String name) {
		String slug = name.toLowerCase(Locale.ROOT)
				.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
				.replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
		if (slug.isEmpty()) slug = "mine";
		return slug + "_" + UUID.randomUUID().toString().substring(0, 6);
	}

	// ---------- Sync-Eingang ----------

	/** Übernimmt eine Mine vom Sync-Server, falls sie neuer ist. Liefert true bei Änderung. */
	public boolean mergeRemoteMine(Mine remote) {
		if (remote == null || remote.id == null) return false;
		Mine local = mines.get(remote.id);
		remote.normalize();
		if (local != null && local.updatedAt > remote.updatedAt) return false;
		// Gleicher Stand: nur übernehmen, wenn eine ältere Version hier das Lehen verloren hat
		if (local != null && local.updatedAt == remote.updatedAt && !(local.area.isEmpty() && !remote.area.isEmpty())) return false;
		mines.put(remote.id, remote);
		return true;
	}

	public boolean mergeRemoteTimer(String key, TimerEntry remote) {
		if (remote == null) return false;
		TimerEntry local = timers.get(key);
		if (local != null && local.updatedAt >= remote.updatedAt) return false;
		if (remote.by == null) remote.by = "";
		timers.put(key, remote);
		// Bereits abgelaufene Timer nicht nachträglich melden
		if (remote.readyAt <= System.currentTimeMillis()) notified.put(key, remote.readyAt);
		else notified.remove(key);
		return true;
	}

	// ---------- Bereit-Meldungen ----------

	public void tickNotifications() {
		long now = System.currentTimeMillis();
		Minecraft mc = Minecraft.getInstance();
		List<String> ready = new ArrayList<>();
		for (Mine m : mines()) {
			TimerEntry t = timers.get(m.id);
			if (t == null || t.readyAt == 0 || t.readyAt > now) continue;
			Long done = notified.get(m.id);
			if (done != null && done == t.readyAt) continue;
			notified.put(m.id, t.readyAt);
			ready.add(m.name);
		}
		if (ready.isEmpty() || mc.player == null) return;
		ModConfig cfg = ModConfig.get();
		if (cfg.chatMessage) {
			for (String name : ready) {
				mc.player.displayClientMessage(Component.literal("[Minen-Timer] ").withStyle(ChatFormatting.GOLD)
						.append(Component.literal(name + " ist wieder bereit!").withStyle(ChatFormatting.GREEN)), false);
			}
		}
		if (cfg.soundEnabled) Sounds.playReady();
	}

	// ---------- Speichern / Laden ----------

	public void load() {
		mines.clear();
		timers.clear();
		notified.clear();
		Path dir = ModConfig.dir();
		Map<String, Mine> loadedMines = read(dir.resolve("mines.json"), MINES_TYPE);
		// Beim ersten Start ist die Liste leer: Jeder trägt seine eigenen Minen ein.
		if (loadedMines == null) {
			saveMines();
		} else {
			for (Mine m : loadedMines.values()) {
				if (m == null || m.id == null) continue;
				m.normalize();
				mines.put(m.id, m);
			}
		}
		Map<String, TimerEntry> loadedTimers = read(dir.resolve("timers.json"), TIMERS_TYPE);
		if (loadedTimers != null) timers.putAll(loadedTimers);
		// Während man offline war abgelaufene Timer nicht alle auf einmal melden
		long now = System.currentTimeMillis();
		timers.forEach((k, t) -> {
			if (t.readyAt <= now) notified.put(k, t.readyAt);
		});
	}

	private static <T> T read(Path file, Type type) {
		if (!Files.exists(file)) return null;
		try (Reader r = Files.newBufferedReader(file)) {
			return GSON.fromJson(r, type);
		} catch (Exception e) {
			MinenTimerClient.LOGGER.error("Konnte {} nicht lesen", file.getFileName(), e);
			return null;
		}
	}

	public void saveMines() {
		write(ModConfig.dir().resolve("mines.json"), mines);
	}

	public void saveTimers() {
		write(ModConfig.dir().resolve("timers.json"), timers);
	}

	private static void write(Path file, Object data) {
		try {
			Files.createDirectories(file.getParent());
			try (Writer w = Files.newBufferedWriter(file)) {
				GSON.toJson(data, w);
			}
		} catch (IOException e) {
			MinenTimerClient.LOGGER.error("Konnte {} nicht speichern", file.getFileName(), e);
		}
	}

	public static String playerName() {
		Minecraft mc = Minecraft.getInstance();
		return mc.getUser() != null ? mc.getUser().getName() : "";
	}
}
