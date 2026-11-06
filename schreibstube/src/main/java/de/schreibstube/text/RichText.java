package de.schreibstube.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;

import java.util.Arrays;

/**
 * Text mit einer Formatierung pro Zeichen. Farbe und Stile sind unabhängig voneinander,
 * erst beim Umwandeln in §/&-Codes wird daraus die Minecraft-Schreibweise.
 * <p>
 * Zeilenumbruch ist '\n', ein erzwungener Seitenumbruch '\f'.
 */
public final class RichText {
	public static final char PAGE_BREAK = '\f';

	// Aufbau eines Stils: Bit 0–4 Farbe (0 = Standard, 1–16 = Farbcode 0–f), danach die Stile
	public static final int COLOR_MASK = 0x1F;
	public static final int BOLD = 1 << 5, ITALIC = 1 << 6, UNDERLINE = 1 << 7, STRIKE = 1 << 8, OBFUSCATED = 1 << 9;
	public static final int FLAG_MASK = BOLD | ITALIC | UNDERLINE | STRIKE | OBFUSCATED;
	private static final String COLOR_CODES = "0123456789abcdef";
	/** Reihenfolge der Stil-Codes beim Ausgeben. */
	private static final int[] FLAGS = {BOLD, ITALIC, UNDERLINE, STRIKE, OBFUSCATED};
	private static final char[] FLAG_CODES = {'l', 'o', 'n', 'm', 'k'};

	private final StringBuilder chars = new StringBuilder();
	private int[] styles = new int[16];

	public RichText() {
	}

	public static RichText of(String plain, int style) {
		RichText t = new RichText();
		t.append(plain, style);
		return t;
	}

	public int length() {
		return chars.length();
	}

	public boolean isEmpty() {
		return chars.isEmpty();
	}

	public char charAt(int i) {
		return chars.charAt(i);
	}

	public int styleAt(int i) {
		return styles[i];
	}

	public String plain() {
		return chars.toString();
	}

	public String plain(int from, int to) {
		return chars.substring(from, to);
	}

	private void ensure(int n) {
		if (styles.length < n) styles = Arrays.copyOf(styles, Math.max(n, styles.length * 2));
	}

	public RichText append(char c, int style) {
		ensure(chars.length() + 1);
		styles[chars.length()] = style;
		chars.append(c);
		return this;
	}

	public RichText append(String s, int style) {
		for (int i = 0; i < s.length(); i++) append(s.charAt(i), style);
		return this;
	}

	public RichText append(RichText o) {
		insert(length(), o);
		return this;
	}

	public void insert(int pos, RichText o) {
		int n = o.length();
		if (n == 0) return;
		ensure(length() + n);
		System.arraycopy(styles, pos, styles, pos + n, length() - pos);
		System.arraycopy(o.styles, 0, styles, pos, n);
		chars.insert(pos, o.chars);
	}

	public void delete(int from, int to) {
		if (to <= from) return;
		System.arraycopy(styles, to, styles, from, length() - to);
		chars.delete(from, to);
	}

	public RichText sub(int from, int to) {
		RichText t = new RichText();
		t.ensure(to - from);
		t.chars.append(chars, from, to);
		System.arraycopy(styles, from, t.styles, 0, to - from);
		return t;
	}

	public RichText copy() {
		return sub(0, length());
	}

	public void setStyle(int i, int style) {
		styles[i] = style;
	}

	public boolean contentEquals(RichText o) {
		if (o.length() != length() || !o.chars.toString().contentEquals(chars)) return false;
		for (int i = 0; i < length(); i++) if (styles[i] != o.styles[i]) return false;
		return true;
	}

	// ------------------------------------------------------------ Stile

	public static int color(int style) {
		return style & COLOR_MASK;
	}

	public static int withColor(int style, int color) {
		return (style & ~COLOR_MASK) | color;
	}

	/** Farbe aus einem Farbcode 0–15, -1 für Standard. */
	public static int colorOf(int code) {
		return code < 0 ? 0 : code + 1;
	}

	public static Style toStyle(int s, boolean obfuscate) {
		Style st = Style.EMPTY;
		int c = color(s);
		if (c > 0) st = st.withColor(ChatFormatting.getById(c - 1));
		if ((s & BOLD) != 0) st = st.withBold(true);
		if ((s & ITALIC) != 0) st = st.withItalic(true);
		if ((s & UNDERLINE) != 0) st = st.withUnderlined(true);
		if ((s & STRIKE) != 0) st = st.withStrikethrough(true);
		if (obfuscate && (s & OBFUSCATED) != 0) st = st.withObfuscated(true);
		return st;
	}

	/** Als Minecraft-Text mit echten Stilen statt §-Codes (die verrutschen beim Zeilenumbruch). */
	public net.minecraft.network.chat.MutableComponent toComponent(boolean obfuscate) {
		net.minecraft.network.chat.MutableComponent out = net.minecraft.network.chat.Component.empty();
		int i = 0;
		while (i < length()) {
			int st = styles[i];
			int j = i + 1;
			while (j < length() && styles[j] == st) j++;
			out.append(net.minecraft.network.chat.Component.literal(chars.substring(i, j)).withStyle(toStyle(st, obfuscate)));
			i = j;
		}
		return out;
	}

	// ------------------------------------------------------------ §/&-Codes

	public static boolean isCode(char c) {
		c = Character.toLowerCase(c);
		return COLOR_CODES.indexOf(c) >= 0 || "klmnor".indexOf(c) >= 0;
	}

	/**
	 * Liest Text mit §-Codes (und auf Wunsch &-Codes) ein. Wie in Minecraft setzt eine Farbe
	 * alle Stile zurück, §r setzt alles zurück.
	 */
	public static RichText parseLegacy(String s, boolean ampersand) {
		RichText t = new RichText();
		int style = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if ((c == '§' || (ampersand && c == '&')) && i + 1 < s.length() && isCode(s.charAt(i + 1))) {
				style = applyCode(style, Character.toLowerCase(s.charAt(++i)));
				continue;
			}
			if (c == '§') continue; // einzelnes § lässt sich nicht verschicken
			if (c == '\r') continue;
			if (c == '\t') c = ' ';
			t.append(c, style);
		}
		return t;
	}

	public static int applyCode(int style, char code) {
		int ci = COLOR_CODES.indexOf(code);
		if (ci >= 0) return colorOf(ci);
		return switch (code) {
			case 'l' -> style | BOLD;
			case 'o' -> style | ITALIC;
			case 'n' -> style | UNDERLINE;
			case 'm' -> style | STRIKE;
			case 'k' -> style | OBFUSCATED;
			default -> 0; // r
		};
	}

	/** Codes, die vom Stil {@code from} zum Stil {@code to} führen. */
	public static String transition(int from, int to, char mark) {
		if (from == to) return "";
		StringBuilder sb = new StringBuilder();
		boolean onlyAdds = color(from) == color(to) && (from & FLAG_MASK & ~to) == 0;
		if (onlyAdds) {
			for (int k = 0; k < FLAGS.length; k++)
				if ((to & FLAGS[k]) != 0 && (from & FLAGS[k]) == 0) sb.append(mark).append(FLAG_CODES[k]);
			return sb.toString();
		}
		int c = color(to);
		sb.append(mark).append(c == 0 ? 'r' : COLOR_CODES.charAt(c - 1));
		for (int k = 0; k < FLAGS.length; k++) if ((to & FLAGS[k]) != 0) sb.append(mark).append(FLAG_CODES[k]);
		return sb.toString();
	}

	/** Ausschnitt als Text mit Codes, beginnend beim Stil {@code startStyle}. */
	public String toLegacy(int from, int to, char mark, int startStyle) {
		StringBuilder sb = new StringBuilder();
		int cur = startStyle;
		for (int i = from; i < to; i++) {
			char c = chars.charAt(i);
			if (c == '\n' || c == PAGE_BREAK) {
				sb.append(c);
				continue;
			}
			int s = styles[i];
			// Leerzeichen ohne Unterstreichen oder Durchstreichen brauchen keinen Stilwechsel
			if (c == ' ' && (s & (UNDERLINE | STRIKE)) == 0 && (cur & (UNDERLINE | STRIKE)) == 0) {
				sb.append(c);
				continue;
			}
			sb.append(transition(cur, s, mark));
			cur = s;
			sb.append(c);
		}
		return sb.toString();
	}

	public String toLegacy(char mark) {
		return toLegacy(0, length(), mark, 0);
	}

	public boolean hasFormatting(int from, int to) {
		for (int i = from; i < to; i++) if (styles[i] != 0) return true;
		return false;
	}
}
