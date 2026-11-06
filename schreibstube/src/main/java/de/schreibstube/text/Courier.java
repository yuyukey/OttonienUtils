package de.schreibstube.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Nachbildung des Brief-Plugins auf Ottonien (CourierNew): wie {@code /letter TEXT} den Text
 * auf die Buchseiten des Briefs verteilt.
 * <ul>
 *   <li>Mehrere Leerzeichen werden zu einem, am Ende kommt ein Leerzeichen dazu.</li>
 *   <li>{@code \n} im Text wird zur neuen Zeile, {@code &x}-Codes werden zu Formatierung.</li>
 *   <li>Jeder Befehl beginnt eine neue Zeile. Leere Zeilen fallen weg.</li>
 *   <li>Eine Seite ist voll bei mehr als 13 geschätzten Zeilen (18 Zeichen je Zeile) oder 255 Zeichen.</li>
 * </ul>
 * Die Regeln stammen aus der Brief-Funktion von OttoExtra (Skyfuller1303, MIT-Lizenz), die das Plugin vermessen hat.
 */
public final class Courier {
	public static final String COMMAND = "letter";
	public static final String POST_COMMAND = "post";
	/** Längster Befehl ohne Schrägstrich, der sicher durch den Chat passt. */
	public static final int MAX_COMMAND = 255;
	public static final int MAX_PAYLOAD = MAX_COMMAND - COMMAND.length() - 1;
	public static final int MAX_PAGE_CHARS = 255;
	public static final double MAX_PAGE_LINES = 13;
	public static final int CHARS_PER_LINE = 18;
	/** So wird eine leere Zeile verschickt: unsichtbar, fällt aber nicht weg. */
	public static final String EMPTY_LINE = "&r";
	private static final String SERVER_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

	private Courier() {
	}

	// ---------------------------------------------------------------- Server

	/** Was der Server mit den Argumenten von /letter macht, bevor er sie einsortiert. */
	public static String serverText(String args) {
		return format(rejoin(args));
	}

	static String rejoin(String args) {
		if (args == null || args.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		for (String part : args.split(" +")) {
			if (!part.isEmpty()) sb.append(part).append(' ');
		}
		return sb.toString();
	}

	static String format(String s) {
		String t = (s == null ? "" : s).replace("%prefix%", "&7[&6Courier&7]").replace("\\n", "\n");
		StringBuilder sb = new StringBuilder(t.length());
		for (int i = 0; i < t.length(); i++) {
			char c = t.charAt(i);
			if (c == '&' && i + 1 < t.length() && SERVER_CODES.indexOf(t.charAt(i + 1)) >= 0) {
				sb.append('§').append(Character.toLowerCase(t.charAt(++i)));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	/** Ein /letter-Befehl mit diesen Argumenten auf die Seiten anwenden. */
	public static void receive(List<String> pages, String args) {
		insert(pages, serverText(args));
	}

	/** Bereits umgewandelten Text (§-Codes, echte Zeilenumbrüche) einsortieren. */
	public static void insert(List<String> pages, String text) {
		if (text.isEmpty()) return;
		if (pages.isEmpty()) pages.add("");
		for (String line : text.split("\n", -1)) {
			String cur = "";
			for (String word : line.split(" ", -1)) {
				String cand = cur.isEmpty() ? word : cur + " " + word;
				if (wouldOverflow(pages.getLast(), cand)) {
					if (!cur.isEmpty()) commitLine(pages, cur);
					else pages.add("");
					cur = word;
				} else {
					cur = cand;
				}
			}
			if (!cur.isEmpty()) commitLine(pages, cur);
		}
	}

	private static void commitLine(List<String> pages, String line) {
		String last = pages.getLast();
		if (wouldOverflow(last, line) && !last.isEmpty()) {
			pages.add("");
			last = "";
		}
		pages.set(pages.size() - 1, last.isEmpty() ? line : last + "\n" + line);
	}

	static boolean wouldOverflow(String page, String add) {
		double have = page.isEmpty() ? 0 : countLines(page);
		double extra = countLines(add) + (page.isEmpty() ? 0 : 0.5);
		return have + extra > MAX_PAGE_LINES || page.length() + add.length() + 1 > MAX_PAGE_CHARS;
	}

	static int countLines(String s) {
		int n = 0;
		for (String l : s.split("\n", -1)) n += Math.max(1, (int) Math.ceil(l.length() / (double) CHARS_PER_LINE));
		return n;
	}

	// ---------------------------------------------------------------- Planung

	/** Befehle und das Ergebnis, das der Server daraus macht. */
	/**
	 * @param pageStarts Stelle im Text → Buchseite (ab 0), an der der Server eine neue Seite beginnt
	 */
	public record Plan(List<String> commands, List<String> pages, int existingPages, List<String> warnings,
					   java.util.Map<Integer, Integer> pageStarts) {
		public int newPages() {
			return Math.max(0, pages.size() - Math.max(0, existingPages - 1));
		}
	}

	/**
	 * Plant die /letter-Befehle für einen Brief. Absätze, die über eine Seitengrenze laufen,
	 * werden genau dort geteilt, damit die Formatierung auf der neuen Seite weitergeht.
	 * Bei einem Seitenumbruch ('\f') wird die Seite mit einer unsichtbaren Zeile aufgefüllt.
	 */
	public static Plan plan(RichText doc, List<String> existing) {
		List<String> warnings = new ArrayList<>();
		List<String> state = new ArrayList<>(existing);
		if (state.isEmpty()) state.add(""); // macht der Server beim ersten Befehl genauso
		List<String> commands = new ArrayList<>();
		StringBuilder pending = new StringBuilder();

		List<RichText> paragraphs = new ArrayList<>();
		List<int[]> origins = new ArrayList<>();
		List<Integer> paraStarts = new ArrayList<>();
		List<Boolean> breakBefore = new ArrayList<>();
		/** Für jede geschickte Zeile die Stelle im Text, an der sie beginnt (-1 = Füllzeile). */
		List<Integer> lineOrigins = new ArrayList<>();
		boolean nextBreak = false;
		int start = 0;
		for (int i = 0; i <= doc.length(); i++) {
			char c = i < doc.length() ? doc.charAt(i) : '\n';
			if (c == '\n' || c == RichText.PAGE_BREAK) {
				int[] origin = new int[i - start];
				paragraphs.add(normalize(doc.sub(start, i), start, origin));
				origins.add(origin);
				paraStarts.add(start);
				breakBefore.add(nextBreak);
				nextBreak = c == RichText.PAGE_BREAK;
				start = i + 1;
			}
		}
		// Leere Zeilen am Ende weglassen
		while (!paragraphs.isEmpty() && isBlank(paragraphs.getLast()) && !breakBefore.getLast()) {
			paragraphs.removeLast();
			origins.removeLast();
			paraStarts.removeLast();
			breakBefore.removeLast();
		}

		boolean literalCodes = false, literalNewline = false;
		int prevEnd = 0;
		for (int p = 0; p < paragraphs.size(); p++) {
			RichText para = paragraphs.get(p);
			if (breakBefore.get(p)) {
				String pad = padding(state);
				if (pad != null) {
					add(commands, pending, state, pad);
					lineOrigins.add(-1);
				}
				prevEnd = 0;
			}
			String plain = para.plain();
			if (plain.contains("\\n")) literalNewline = true;
			for (int i = 0; i + 1 < plain.length(); i++)
				if (plain.charAt(i) == '&' && SERVER_CODES.indexOf(plain.charAt(i + 1)) >= 0) literalCodes = true;

			if (isBlank(para)) {
				add(commands, pending, state, EMPTY_LINE);
				lineOrigins.add(paraStarts.get(p));
				prevEnd = 0;
				continue;
			}
			int from = 0;
			while (from < para.length()) {
				int to = fitOnPage(para, from, state, prevEnd);
				String piece = encode(para, from, to, prevEnd);
				if (piece.length() > MAX_PAYLOAD) {
					to = fitInCommand(para, from, to, prevEnd);
					piece = encode(para, from, to, prevEnd);
				}
				add(commands, pending, state, piece);
				lineOrigins.add(origins.get(p)[from]);
				prevEnd = para.styleAt(to - 1);
				from = to;
				while (from < para.length() && para.charAt(from) == ' ') from++;
			}
		}
		flush(commands, pending);

		// Ergebnis genau so berechnen, wie der Server es mit diesen Befehlen macht
		// Zeile für Zeile, um zu wissen, wo im Text jede Seite beginnt
		List<String> pages = new ArrayList<>(existing);
		java.util.Map<Integer, Integer> starts = new java.util.TreeMap<>();
		int li = 0, prevPage = Math.max(0, existing.size() - 1);
		boolean aligned = true;
		for (String cmd : commands) {
			for (String line : serverText(cmd).split("\n", -1)) {
				if (pages.isEmpty()) pages.add("");
				int sb = pages.size();
				String lb = pages.getLast();
				insert(pages, line);
				int page = pages.size() > sb && pages.get(sb - 1).equals(lb) ? sb : sb - 1;
				if (li < lineOrigins.size()) {
					int o = lineOrigins.get(li);
					if (o >= 0 && page != prevPage) starts.put(o, page);
					if (o >= 0) prevPage = page;
				} else {
					aligned = false;
				}
				li++;
			}
		}
		if (!aligned || li != lineOrigins.size()) starts.clear(); // „\n“ im Text: Zuordnung unsicher
		if (literalCodes) warnings.add("Im Text steht „&“ vor einem Buchstaben oder einer Zahl – der Server macht daraus Formatierung.");
		if (literalNewline) warnings.add("Im Text steht „\\n“ – der Server macht daraus einen Zeilenumbruch.");
		return new Plan(commands, pages, existing.size(), warnings, starts);
	}

	private static boolean isBlank(RichText t) {
		return t.plain().isBlank();
	}

	/** Mehrere Leerzeichen zu einem, Leerzeichen am Rand weg: das macht der Server ohnehin. */
	private static RichText normalize(RichText t, int base, int[] origin) {
		RichText out = new RichText();
		for (int i = 0; i < t.length(); i++) {
			char c = t.charAt(i);
			if (c == ' ' && (out.isEmpty() || out.charAt(out.length() - 1) == ' ')) continue;
			origin[out.length()] = base + i;
			out.append(c, t.styleAt(i));
		}
		while (!out.isEmpty() && out.charAt(out.length() - 1) == ' ') out.delete(out.length() - 1, out.length());
		return out;
	}

	/**
	 * Codes für ein Stück eines Absatzes. Lief die vorige Zeile formatiert aus, wird der Stil
	 * am Anfang ausdrücklich gesetzt, sonst würde er in die neue Zeile hinüberlaufen.
	 */
	private static String encode(RichText para, int from, int to, int prevEnd) {
		return para.toLegacy(from, to, '&', prevEnd == 0 ? 0 : -1);
	}

	/** Ende des Stücks ab {@code from}, das noch auf eine Seite passt (an einer Wortgrenze). */
	private static int fitOnPage(RichText para, int from, List<String> state, int prevEnd) {
		int end = para.length();
		int before = state.size();
		List<String> trial = new ArrayList<>(state);
		insert(trial, format(encode(para, from, end, prevEnd)));
		if (trial.size() == before) return end;
		// Ging etwas auf die alte Seite, soll nur das dort bleiben, sonst nur eine neue Seite füllen
		boolean oldChanged = !trial.get(before - 1).equals(state.getLast());
		int target = oldChanged ? before : before + 1;
		if (!oldChanged && trial.size() == target) return end;
		int best = -1;
		for (int s : spaces(para, from, end)) {
			List<String> t = new ArrayList<>(state);
			insert(t, format(encode(para, from, s, prevEnd)));
			if (t.size() <= target) best = s;
			else break;
		}
		return best > from ? best : end;
	}

	/** Ende des Stücks, das noch in einen Befehl passt. */
	private static int fitInCommand(RichText para, int from, int to, int prevEnd) {
		int best = -1;
		for (int s : spaces(para, from, to)) {
			if (encode(para, from, s, prevEnd).length() <= MAX_PAYLOAD) best = s;
			else break;
		}
		if (best > from) return best;
		// ein einzelnes Riesenwort: hart teilen
		int s = from + 1;
		while (s < to && encode(para, from, s + 1, prevEnd).length() <= MAX_PAYLOAD) s++;
		return s;
	}

	private static List<Integer> spaces(RichText para, int from, int to) {
		List<Integer> out = new ArrayList<>();
		for (int i = from + 1; i < to; i++) if (para.charAt(i) == ' ') out.add(i);
		return out;
	}

	/**
	 * Unsichtbare Zeile, nach der die Seite als voll gilt. Sie landet am Ende der alten Seite
	 * und zwingt den folgenden Text auf eine neue Seite.
	 */
	static String padding(List<String> state) {
		if (state.isEmpty() || state.getLast().isEmpty()) return null;
		String last = state.getLast();
		int lines = countLines(last);
		if (lines >= 12 || last.length() >= 253) return null;
		// 12 geschätzte Zeilen erreichen; zwei Zeichen Luft für das Leerzeichen am Befehlsende
		int len = Math.min(CHARS_PER_LINE * (12 - lines) - 2, MAX_PAGE_CHARS - last.length() - 3);
		len -= len % 2;
		if (len < 2) return null;
		String pad = "&r".repeat(len / 2);
		List<String> trial = new ArrayList<>(state);
		insert(trial, format(pad));
		if (trial.size() != state.size()) return null;
		List<String> next = new ArrayList<>(trial);
		insert(next, "x");
		if (next.size() == trial.size()) {
			// Zeilenregel greift nicht: über die Zeichenzahl füllen
			int fill = MAX_PAGE_CHARS - last.length() - 3;
			fill -= fill % 2;
			if (fill < 2) return null;
			pad = "&r".repeat(fill / 2);
		}
		return pad;
	}

	private static void add(List<String> commands, StringBuilder pending, List<String> state, String line) {
		if (!pending.isEmpty() && pending.length() + 2 + line.length() > MAX_PAYLOAD) flush(commands, pending);
		if (!pending.isEmpty()) pending.append("\\n");
		pending.append(line);
		insert(state, format(line));
	}

	private static void flush(List<String> commands, StringBuilder pending) {
		if (pending.isEmpty()) return;
		commands.add(pending.toString());
		pending.setLength(0);
	}
}
