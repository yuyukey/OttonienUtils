package de.schreibstube.gui;

import de.schreibstube.Config;
import de.schreibstube.text.RichText;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Mehrzeiliges Textfeld mit Formatierung: Markieren mit Maus und Umschalt, Kopieren, Ausschneiden,
 * Einfügen (mit &-Codes), Rückgängig/Wiederholen, Wort-Sprünge, Seitenumbrüche.
 */
public class RichTextEditor {
	public static final int MAX_LENGTH = 30_000;
	public static final int LINE_HEIGHT = 10;
	public static final int PAD = 6;
	public static final int INK = 0xFF2B1D0E;
	private static final int SELECTION = 0x663F7FBF;

	private final Font font;
	private final Runnable onChange;
	private RichText text = new RichText();
	private int cursor, anchor;
	/** Stil für neu getippte Zeichen. */
	private int typingStyle;
	private int x, y, w, h;
	private int scroll; // in Zeilen
	private final List<VLine> lines = new ArrayList<>();
	private boolean layoutDirty = true;
	private final Map<Integer, Integer> widthCache = new HashMap<>();
	private long blinkStart = Util.getMillis();
	private boolean dragging;
	/** Spalte für Hoch/Runter, damit der Cursor über kurze Zeilen hinweg seine Position hält. */
	private int goalX = -1;

	private final List<Snapshot> undo = new ArrayList<>();
	private final List<Snapshot> redo = new ArrayList<>();
	private long lastTypeAt;
	private boolean lastWasTyping;

	private record Snapshot(RichText text, int cursor, int anchor) {
	}

	/** Sichtbare Zeile: ein Stück Text oder ein Seitenumbruch. */
	private record VLine(int start, int end, int kind, int page) {
		static final int TEXT = 0, MANUAL = 1, AUTO = 2;

		boolean divider() {
			return kind != TEXT;
		}
	}

	/** Stelle im Text → Buchseite (ab 0), an der der Server eine neue Seite beginnt. */
	private java.util.Map<Integer, Integer> pageStarts = java.util.Map.of();

	public void setPageStarts(java.util.Map<Integer, Integer> starts) {
		if (starts.equals(pageStarts)) return;
		pageStarts = starts;
		layoutDirty = true;
	}

	public RichTextEditor(Font font, Runnable onChange) {
		this.font = font;
		this.onChange = onChange;
	}

	public void setBounds(int x, int y, int w, int h) {
		if (w != this.w) layoutDirty = true;
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	public RichText text() {
		return text;
	}

	public void setText(RichText t, boolean keepUndo) {
		if (keepUndo) pushUndo();
		else {
			undo.clear();
			redo.clear();
		}
		text = t.copy();
		cursor = anchor = text.length();
		typingStyle = styleBefore(cursor);
		changed(false);
		ensureCursorVisible();
	}

	public int cursor() {
		return cursor;
	}

	public boolean hasSelection() {
		return cursor != anchor;
	}

	public int selStart() {
		return Math.min(cursor, anchor);
	}

	public int selEnd() {
		return Math.max(cursor, anchor);
	}

	public RichText selection() {
		return text.sub(selStart(), selEnd());
	}

	/** Stil am Cursor bzw. der Markierung, für die Anzeige in der Leiste. */
	public int currentStyle() {
		if (hasSelection()) {
			int s = selStart();
			while (s < selEnd() && isBreakChar(text.charAt(s))) s++;
			return s < selEnd() ? text.styleAt(s) : typingStyle;
		}
		return typingStyle;
	}

	public boolean contains(double mx, double my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ---------------------------------------------------------------- Layout

	private static boolean isBreakChar(char c) {
		return c == '\n' || c == RichText.PAGE_BREAK;
	}

	private int charWidth(int i) {
		char c = text.charAt(i);
		boolean bold = (text.styleAt(i) & RichText.BOLD) != 0;
		int key = (c << 1) | (bold ? 1 : 0);
		Integer cached = widthCache.get(key);
		if (cached != null) return cached;
		int wd = font.width(FormattedCharSequence.codepoint(c, bold ? Style.EMPTY.withBold(true) : Style.EMPTY));
		widthCache.put(key, wd);
		return wd;
	}

	private int textWidth() {
		return w - 2 * PAD - 4;
	}

	private void layout() {
		if (!layoutDirty) return;
		layoutDirty = false;
		lines.clear();
		int n = text.length();
		int maxW = Math.max(20, textWidth());
		int para = 0;
		while (true) {
			int e = para;
			while (e < n && !isBreakChar(text.charAt(e))) e++;
			int lineStart = para, xw = 0, lastSpace = -1, lastDivider = -1;
			for (int j = para; j < e; j++) {
				// Hier beginnt der Server eine neue Buchseite (nicht direkt nach einem eigenen Seitenumbruch)
				Integer pg = pageStarts.get(j);
				if (pg != null && j != lastDivider && !(j == para && para > 0 && text.charAt(para - 1) == RichText.PAGE_BREAK)) {
					if (j > lineStart) lines.add(new VLine(lineStart, j, VLine.TEXT, 0));
					lines.add(new VLine(j, j, VLine.AUTO, pg));
					lastDivider = j;
					lineStart = j;
					lastSpace = -1;
					xw = 0;
				}
				int cw = charWidth(j);
				if (xw + cw > maxW && j > lineStart) {
					int brk = lastSpace >= lineStart ? lastSpace + 1 : j;
					lines.add(new VLine(lineStart, brk, VLine.TEXT, 0));
					lineStart = brk;
					lastSpace = -1;
					xw = 0;
					j = brk - 1;
					continue;
				}
				if (text.charAt(j) == ' ') lastSpace = j;
				xw += cw;
			}
			if (e == para && pageStarts.containsKey(para) && para != lastDivider
					&& !(para > 0 && text.charAt(para - 1) == RichText.PAGE_BREAK)) {
				lines.add(new VLine(para, para, VLine.AUTO, pageStarts.get(para))); // leere Zeile oben auf neuer Seite
			}
			lines.add(new VLine(lineStart, e, VLine.TEXT, 0));
			if (e >= n) break;
			if (text.charAt(e) == RichText.PAGE_BREAK) lines.add(new VLine(e, e + 1, VLine.MANUAL, pageStarts.getOrDefault(e + 1, -1)));
			para = e + 1;
		}
	}

	private int lineOf(int pos) {
		layout();
		int best = 0;
		for (int i = 0; i < lines.size(); i++) {
			VLine l = lines.get(i);
			if (l.divider()) continue;
			if (l.start <= pos) best = i;
			else break;
		}
		return best;
	}

	private int xOf(VLine l, int pos) {
		int xw = 0;
		for (int i = l.start; i < Math.min(pos, l.end); i++) xw += charWidth(i);
		return xw;
	}

	private int posAtX(VLine l, int px) {
		if (l.divider()) return l.end;
		int xw = 0;
		for (int i = l.start; i < l.end; i++) {
			int cw = charWidth(i);
			if (px < xw + cw / 2) return i;
			xw += cw;
		}
		// am Zeilenende vor dem Leerzeichen des Umbruchs bleiben
		return l.end;
	}

	private int visibleLines() {
		return Math.max(1, (h - 2 * PAD) / LINE_HEIGHT);
	}

	public int lineCount() {
		layout();
		return lines.size();
	}

	private void ensureCursorVisible() {
		int li = lineOf(cursor);
		int vis = visibleLines();
		if (li < scroll) scroll = li;
		if (li >= scroll + vis) scroll = li - vis + 1;
		clampScroll();
	}

	private void clampScroll() {
		scroll = Mth.clamp(scroll, 0, Math.max(0, lineCount() - visibleLines()));
	}

	// ---------------------------------------------------------------- Zeichnen

	public void render(GuiGraphics g, boolean focused) {
		layout();
		clampScroll();
		int tx = x + PAD + 2, ty = y + PAD;
		int vis = visibleLines();
		// Linien wie auf liniertem Papier
		for (int i = 0; i < vis; i++) g.fill(x + 3, ty + i * LINE_HEIGHT + 9, x + w - 3, ty + i * LINE_HEIGHT + 10, 0x1A6B4A23);
		g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
		int s0 = selStart(), s1 = selEnd();
		for (int i = scroll; i < Math.min(lines.size(), scroll + vis); i++) {
			VLine l = lines.get(i);
			int ly = ty + (i - scroll) * LINE_HEIGHT;
			if (l.divider()) {
				boolean manual = l.kind == VLine.MANUAL;
				boolean sel = manual && s0 <= l.start && s1 > l.start;
				if (sel) g.fill(tx - 2, ly - 1, x + w - PAD, ly + 9, SELECTION);
				int mid = ly + 4;
				String label = manual ? (l.page >= 0 ? "✂ Seite " + (l.page + 1) : "✂ neue Seite") : "Seite " + (l.page + 1);
				int color = manual ? 0xFFA0522D : 0xFF9C8A6A;
				int lw = font.width(label);
				int cx = x + w / 2;
				for (int dx = tx; dx < x + w - PAD; dx += 6) {
					if (dx + 4 < cx - lw / 2 - 4 || dx > cx + lw / 2 + 4) g.fill(dx, mid, Math.min(dx + 3, x + w - PAD), mid + 1, (color & 0xFFFFFF) | 0x99000000);
				}
				g.drawString(font, label, cx - lw / 2, ly, color, false);
				continue;
			}
			// Markierung
			int a = Math.max(l.start, s0), b = Math.min(l.end, s1);
			if (s0 != s1 && s0 <= l.end && s1 >= l.start && (a < b || s1 > l.end)) {
				int x0 = tx + xOf(l, a), x1 = tx + xOf(l, b);
				if (s1 > l.end) x1 += 3; // Zeilenumbruch mit markiert
				g.fill(x0, ly - 1, x1, ly + 9, SELECTION);
			}
			g.drawString(font, sequence(l.start, l.end), tx, ly, INK, false);
		}
		// Cursor
		if (focused && ((Util.getMillis() - blinkStart) / 530) % 2 == 0) {
			int li = lineOf(cursor);
			if (li >= scroll && li < scroll + vis) {
				VLine l = lines.get(li);
				int cx = tx + xOf(l, cursor);
				int cy = ty + (li - scroll) * LINE_HEIGHT;
				g.fill(cx, cy - 1, cx + 1, cy + 9, 0xFF1A1008);
			}
		}
		g.disableScissor();
		// Bildlaufleiste
		if (lines.size() > vis) {
			int trackH = h - 4;
			int barH = Math.max(12, trackH * vis / lines.size());
			int barY = y + 2 + (trackH - barH) * scroll / Math.max(1, lines.size() - vis);
			g.fill(x + w - 4, y + 2, x + w - 2, y + h - 2, 0x226B4A23);
			g.fill(x + w - 4, barY, x + w - 2, barY + barH, 0x996B4A23);
		}
		if (text.isEmpty()) {
			g.drawString(font, "Hier deinen Brief schreiben …", tx, ty, 0x806B4A23, false);
		}
	}

	private FormattedCharSequence sequence(int from, int to) {
		List<FormattedCharSequence> parts = new ArrayList<>();
		int i = from;
		while (i < to) {
			int s = text.styleAt(i);
			int j = i + 1;
			while (j < to && text.styleAt(j) == s) j++;
			Style st = RichText.toStyle(s, false);
			// Geheimschrift im Editor lesbar lassen, nur gestrichelt markieren
			if ((s & RichText.OBFUSCATED) != 0) st = st.withUnderlined(true);
			parts.add(FormattedCharSequence.forward(text.plain(i, j), st));
			i = j;
		}
		return FormattedCharSequence.composite(parts);
	}

	// ---------------------------------------------------------------- Bearbeiten

	private void pushUndo() {
		undo.add(new Snapshot(text.copy(), cursor, anchor));
		if (undo.size() > 200) undo.removeFirst();
		redo.clear();
	}

	private void changed(boolean typing) {
		layoutDirty = true;
		lastWasTyping = typing;
		blinkStart = Util.getMillis();
		onChange.run();
	}

	private void moved(boolean select) {
		if (!select) anchor = cursor;
		blinkStart = Util.getMillis();
		lastWasTyping = false;
		if (!hasSelection()) typingStyle = styleBefore(cursor);
		ensureCursorVisible();
	}

	private int styleBefore(int pos) {
		if (pos > 0 && !isBreakChar(text.charAt(pos - 1))) return text.styleAt(pos - 1);
		if (pos < text.length() && !isBreakChar(text.charAt(pos))) return text.styleAt(pos);
		return 0;
	}

	/** Ersetzt die Markierung durch {@code ins}. */
	public void replaceSelection(RichText ins, boolean typing) {
		long now = Util.getMillis();
		boolean merge = typing && lastWasTyping && !hasSelection() && now - lastTypeAt < 1200 && !undo.isEmpty();
		if (!merge) pushUndo();
		lastTypeAt = now;
		int room = MAX_LENGTH - (text.length() - (selEnd() - selStart()));
		if (ins.length() > room) ins = ins.sub(0, Math.max(0, room));
		int s = selStart();
		text.delete(s, selEnd());
		text.insert(s, ins);
		cursor = anchor = s + ins.length();
		changed(typing);
		goalX = -1;
		ensureCursorVisible();
	}

	public void insertPlain(String s) {
		RichText t = new RichText();
		for (int i = 0; i < s.length(); i++) t.append(s.charAt(i), typingStyle);
		replaceSelection(t, false);
	}

	public void insertPageBreak() {
		replaceSelection(RichText.of(String.valueOf(RichText.PAGE_BREAK), 0), false);
	}

	private void deleteRange(int from, int to) {
		if (to <= from) return;
		pushUndo();
		text.delete(from, to);
		cursor = anchor = from;
		changed(false);
		typingStyle = styleBefore(cursor);
		ensureCursorVisible();
	}

	/** Formatierung auf die Markierung anwenden, ohne Markierung auf das Weitertippen. */
	public void applyStyle(IntUnaryOperator op) {
		if (!hasSelection()) {
			typingStyle = op.applyAsInt(typingStyle);
			return;
		}
		pushUndo();
		for (int i = selStart(); i < selEnd(); i++) {
			if (!isBreakChar(text.charAt(i))) text.setStyle(i, op.applyAsInt(text.styleAt(i)));
		}
		changed(false);
	}

	/** Stil umschalten: hat alles in der Markierung ihn schon, wird er entfernt. */
	public void toggleFlag(int flag) {
		boolean all = (currentStyle() & flag) != 0;
		if (hasSelection()) {
			all = true;
			for (int i = selStart(); i < selEnd(); i++) {
				if (!isBreakChar(text.charAt(i)) && (text.styleAt(i) & flag) == 0) {
					all = false;
					break;
				}
			}
		}
		boolean remove = all;
		applyStyle(s -> remove ? s & ~flag : s | flag);
	}

	public void setColor(int color) {
		applyStyle(s -> RichText.withColor(s, color));
	}

	public void clearFormatting() {
		applyStyle(s -> 0);
	}

	public void select(int anchor, int cursor) {
		this.anchor = Mth.clamp(anchor, 0, text.length());
		this.cursor = Mth.clamp(cursor, 0, text.length());
		moved(true);
	}

	public void selectAll() {
		anchor = 0;
		cursor = text.length();
		moved(true);
	}

	public void undo() {
		if (undo.isEmpty()) return;
		redo.add(new Snapshot(text.copy(), cursor, anchor));
		restore(undo.removeLast());
	}

	public void redo() {
		if (redo.isEmpty()) return;
		undo.add(new Snapshot(text.copy(), cursor, anchor));
		restore(redo.removeLast());
	}

	private void restore(Snapshot s) {
		text = s.text.copy();
		cursor = Math.min(s.cursor, text.length());
		anchor = Math.min(s.anchor, text.length());
		changed(false);
		lastWasTyping = false;
		ensureCursorVisible();
	}

	public void copy(boolean plain) {
		if (!hasSelection()) return;
		RichText sel = selection();
		String out = !plain && Config.get().copyWithCodes && sel.hasFormatting(0, sel.length())
				? sel.toLegacy('&') : sel.plain();
		out = out.replace(String.valueOf(RichText.PAGE_BREAK), "\n");
		Minecraft.getInstance().keyboardHandler.setClipboard(out);
	}

	public void paste() {
		String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
		if (clip == null || clip.isEmpty()) return;
		RichText raw = RichText.parseLegacy(clip, Config.get().pasteCodes);
		RichText ins = new RichText();
		boolean formatted = raw.hasFormatting(0, raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c != '\n' && !StringUtil.isAllowedChatCharacter(c)) continue;
			ins.append(c, formatted ? raw.styleAt(i) : (c == '\n' ? 0 : typingStyle));
		}
		replaceSelection(ins, false);
	}

	// ---------------------------------------------------------------- Eingabe

	public boolean charTyped(CharacterEvent e) {
		if (!e.isAllowedChatCharacter()) return false;
		String s = e.codepointAsString();
		RichText t = new RichText();
		for (int i = 0; i < s.length(); i++) t.append(s.charAt(i), typingStyle);
		replaceSelection(t, true);
		return true;
	}

	public boolean keyPressed(KeyEvent e) {
		boolean ctrl = e.hasControlDown(), shift = e.hasShiftDown();
		int key = e.key();
		if (ctrl && !e.hasAltDown()) {
			switch (key) {
				case GLFW.GLFW_KEY_A -> {
					selectAll();
					return true;
				}
				case GLFW.GLFW_KEY_C -> {
					copy(shift);
					return true;
				}
				case GLFW.GLFW_KEY_X -> {
					copy(false);
					if (hasSelection()) deleteRange(selStart(), selEnd());
					return true;
				}
				case GLFW.GLFW_KEY_V -> {
					paste();
					return true;
				}
				case GLFW.GLFW_KEY_Z -> {
					if (shift) redo();
					else undo();
					return true;
				}
				case GLFW.GLFW_KEY_Y -> {
					redo();
					return true;
				}
				case GLFW.GLFW_KEY_B -> {
					toggleFlag(RichText.BOLD);
					return true;
				}
				case GLFW.GLFW_KEY_I -> {
					toggleFlag(RichText.ITALIC);
					return true;
				}
				case GLFW.GLFW_KEY_U -> {
					toggleFlag(RichText.UNDERLINE);
					return true;
				}
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
					insertPageBreak();
					return true;
				}
				default -> {
				}
			}
		}
		switch (key) {
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				RichText nl = RichText.of("\n", typingStyle);
				replaceSelection(nl, false);
				return true;
			}
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (hasSelection()) deleteRange(selStart(), selEnd());
				else if (cursor > 0) deleteRange(ctrl ? wordLeft(cursor) : cursor - 1, cursor);
				return true;
			}
			case GLFW.GLFW_KEY_DELETE -> {
				if (hasSelection()) deleteRange(selStart(), selEnd());
				else if (cursor < text.length()) deleteRange(cursor, ctrl ? wordRight(cursor) : cursor + 1);
				return true;
			}
			case GLFW.GLFW_KEY_LEFT -> {
				if (hasSelection() && !shift) cursor = selStart();
				else if (cursor > 0) cursor = ctrl ? wordLeft(cursor) : cursor - 1;
				goalX = -1;
				moved(shift);
				return true;
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				if (hasSelection() && !shift) cursor = selEnd();
				else if (cursor < text.length()) cursor = ctrl ? wordRight(cursor) : cursor + 1;
				goalX = -1;
				moved(shift);
				return true;
			}
			case GLFW.GLFW_KEY_UP -> {
				verticalMove(-1, shift);
				return true;
			}
			case GLFW.GLFW_KEY_DOWN -> {
				verticalMove(1, shift);
				return true;
			}
			case GLFW.GLFW_KEY_PAGE_UP -> {
				verticalMove(-visibleLines(), shift);
				return true;
			}
			case GLFW.GLFW_KEY_PAGE_DOWN -> {
				verticalMove(visibleLines(), shift);
				return true;
			}
			case GLFW.GLFW_KEY_HOME -> {
				cursor = ctrl ? 0 : lines.get(lineOf(cursor)).start;
				goalX = -1;
				moved(shift);
				return true;
			}
			case GLFW.GLFW_KEY_END -> {
				if (ctrl) cursor = text.length();
				else {
					VLine l = lines.get(lineOf(cursor));
					cursor = l.end;
				}
				goalX = -1;
				moved(shift);
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	private void verticalMove(int delta, boolean shift) {
		layout();
		int li = lineOf(cursor);
		if (goalX < 0) goalX = xOf(lines.get(li), cursor);
		int target = Mth.clamp(li + delta, 0, lines.size() - 1);
		if (lines.get(target).divider()) target = Mth.clamp(target + Integer.signum(delta), 0, lines.size() - 1);
		if (target == li) cursor = delta < 0 ? 0 : text.length();
		else cursor = posAtX(lines.get(target), goalX);
		int keep = goalX;
		moved(shift);
		goalX = keep;
	}

	private int wordLeft(int pos) {
		int p = pos;
		while (p > 0 && Character.isWhitespace(text.charAt(p - 1))) p--;
		while (p > 0 && !Character.isWhitespace(text.charAt(p - 1))) p--;
		return p;
	}

	private int wordRight(int pos) {
		int n = text.length(), p = pos;
		while (p < n && !Character.isWhitespace(text.charAt(p))) p++;
		while (p < n && Character.isWhitespace(text.charAt(p)) && !isBreakChar(text.charAt(p))) p++;
		return p;
	}

	private int posAt(double mx, double my) {
		layout();
		int li = scroll + (int) Math.floor((my - y - PAD) / LINE_HEIGHT);
		li = Mth.clamp(li, 0, lines.size() - 1);
		VLine l = lines.get(li);
		return posAtX(l, (int) (mx - x - PAD - 2));
	}

	public boolean mouseClicked(double mx, double my, boolean shift, boolean doubleClick) {
		if (!contains(mx, my)) return false;
		cursor = posAt(mx, my);
		goalX = -1;
		if (doubleClick) {
			anchor = cursor;
			// Wort markieren
			int a = cursor, b = cursor, n = text.length();
			while (a > 0 && !Character.isWhitespace(text.charAt(a - 1))) a--;
			while (b < n && !Character.isWhitespace(text.charAt(b))) b++;
			anchor = a;
			cursor = b;
			moved(true);
			return true;
		}
		moved(shift);
		dragging = true;
		return true;
	}

	public boolean mouseDragged(double mx, double my) {
		if (!dragging) return false;
		if (my < y) scroll = Math.max(0, scroll - 1);
		else if (my > y + h) scroll++;
		clampScroll();
		cursor = posAt(mx, Mth.clamp(my, y + PAD, y + h - PAD - 1));
		moved(true);
		return true;
	}

	public void mouseReleased() {
		dragging = false;
	}

	public boolean mouseScrolled(double mx, double my, double amount) {
		if (!contains(mx, my)) return false;
		scroll -= (int) Math.signum(amount) * 3;
		clampScroll();
		return true;
	}

	/** Wortzahl für die Statuszeile. */
	public int words() {
		String p = text.plain().strip();
		return p.isEmpty() ? 0 : p.split("\\s+").length;
	}
}
