package de.schreibstube.gui;

import de.schreibstube.Config;
import de.schreibstube.Drafts;
import de.schreibstube.SchreibstubeClient;
import de.schreibstube.send.HeldLetter;
import de.schreibstube.send.LetterSender;
import de.schreibstube.text.Courier;
import de.schreibstube.text.RichText;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Schreibstube: links der Brief auf Pergament, rechts die Buchvorschau genau so, wie der Server
 * die Seiten füllt. Oben die Formatierungsleiste, unten Entwürfe, Signatur und „Schreiben“.
 */
public class LetterScreen extends Screen {
	/** Ausschnitt der Buch-Textur, in dem die Seite liegt. */
	private static final int BOOK_U = 18, BOOK_W = 150, BOOK_H = 182;
	private static final int PAGE_TEXT_X = 18, PAGE_TEXT_Y = 30, PAGE_TEXT_W = 114, PAGE_LINES = 14;
	private static final String[] COLOR_NAMES = {"Schwarz", "Dunkelblau", "Dunkelgrün", "Dunkeltürkis", "Dunkelrot", "Lila",
			"Gold", "Grau", "Dunkelgrau", "Blau", "Grün", "Türkis", "Rot", "Pink", "Gelb", "Weiß"};

	private final Screen parent;
	private RichTextEditor editor;
	private Courier.Plan plan;
	private boolean planDirty = true;
	private long lastEdit;
	private long lastSave;
	private boolean unsaved;
	private String heldKey = "";
	private List<String> existing = List.of();

	private int previewPage;
	private boolean followCursor = true;
	private int bookX, bookY;
	private float bookScale = 1f;
	private int edX, edY, edW, edH;
	private Button writeButton;
	private PageButton prevPage, nextPage;
	private Component status;
	private long statusUntil;
	private boolean openDraftsOnTick;

	public LetterScreen(Screen parent) {
		super(Component.literal("Schreibstube"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (editor == null) {
			editor = new RichTextEditor(font, this::onEdit);
			editor.setText(Drafts.loadCurrent(), false);
		}

		int top = 42, bottom = height - 52;
		int mainH = bottom - top;
		bookScale = Math.min(1f, mainH / (float) BOOK_H);
		int bookW = Math.round(BOOK_W * bookScale), bookH = Math.round(BOOK_H * bookScale);
		edW = Math.min(380, width - 16 - bookW - 12);
		int total = edW + 12 + bookW;
		edX = (width - total) / 2;
		edY = top;
		edH = mainH;
		bookX = edX + edW + 12;
		bookY = top + (mainH - bookH) / 2;
		editor.setBounds(edX, edY, edW, edH);

		// Formatierungsleiste
		int tx = Math.max(8, edX), ty = 22, sw = 12;
		addRenderableWidget(new Swatch(tx, ty, sw, -1));
		tx += sw + 4;
		for (int c = 0; c < 16; c++) {
			addRenderableWidget(new Swatch(tx, ty, sw, c));
			tx += sw + 2;
		}
		tx += 6;
		addRenderableWidget(new StyleButton(tx, ty, "B", RichText.BOLD, "Fett (Strg+B)"));
		addRenderableWidget(new StyleButton(tx += 16, ty, "I", RichText.ITALIC, "Kursiv (Strg+I)"));
		addRenderableWidget(new StyleButton(tx += 16, ty, "U", RichText.UNDERLINE, "Unterstrichen (Strg+U)"));
		addRenderableWidget(new StyleButton(tx += 16, ty, "S", RichText.STRIKE, "Durchgestrichen"));
		addRenderableWidget(new StyleButton(tx += 16, ty, "?", RichText.OBFUSCATED, "Geheimschrift (wirbelnde Zeichen)"));
		tx += 20;
		addRenderableWidget(new Ui.SmallButton(tx, ty, 14, 14, Component.literal("Formatierung entfernen"),
				"Formatierung entfernen (markierter Text)", () -> editor.clearFormatting()) {
			@Override
			protected boolean selected() {
				return false;
			}

			@Override
			protected void drawFace(GuiGraphics g, int x, int y, int w, int h) {
				g.fill(x, y, x + w, y + h, 0xFFE7D5AB);
				g.drawCenteredString(font, "✕", x + w / 2, y + 2, 0xFFA02020);
			}
		});
		tx += 18;
		addRenderableWidget(new Ui.SmallButton(tx, ty, 14, 14, Component.literal("Seitenumbruch"),
				"Neue Seite beginnen (Strg+Enter)", () -> editor.insertPageBreak()) {
			@Override
			protected boolean selected() {
				return false;
			}

			@Override
			protected void drawFace(GuiGraphics g, int x, int y, int w, int h) {
				g.fill(x, y, x + w, y + h, 0xFFE7D5AB);
				g.drawCenteredString(font, "✂", x + w / 2, y + 2, 0xFF6B4A23);
			}
		});

		// Seiten der Vorschau blättern
		prevPage = addRenderableWidget(new PageButton(bookX + Math.round(26 * bookScale), bookY + Math.round(157 * bookScale), false, b -> flip(-1), true));
		nextPage = addRenderableWidget(new PageButton(bookX + Math.round(100 * bookScale), bookY + Math.round(157 * bookScale), true, b -> flip(1), true));

		// Untere Leiste
		int by = height - 26, gap = 4;
		int bw = 70;
		int left = Math.max(8, edX);
		addRenderableWidget(Button.builder(Component.literal("Entwürfe …"), b -> openDrafts())
				.bounds(left, by, bw, 20).tooltip(Tooltip.create(Component.literal("Briefe speichern, laden und als Textdatei austauschen"))).build());
		addRenderableWidget(Button.builder(Component.literal("Übernehmen"), b -> importHeld())
				.bounds(left + (bw + gap), by, bw, 20)
				.tooltip(Tooltip.create(Component.literal("Text aus dem Buch oder Brief in deiner Hand übernehmen"))).build());
		addRenderableWidget(Button.builder(Component.literal("Signatur"), b -> signature())
				.bounds(left + 2 * (bw + gap), by, bw, 20)
				.tooltip(Tooltip.create(Component.literal("Gruß und Namen einfügen.\nUmschalt-Klick: markierten Text als Signatur merken"))).build());
		addRenderableWidget(Button.builder(Component.literal("⚙"), b -> minecraft.setScreen(new SettingsScreen(this)))
				.bounds(left + 3 * (bw + gap), by, 20, 20).tooltip(Tooltip.create(Component.literal("Einstellungen"))).build());

		int right = Math.min(width - 8, bookX + bookW);
		addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> onClose()).bounds(right - 64, by, 64, 20).build());
		writeButton = addRenderableWidget(Button.builder(Component.literal("✒ Schreiben"), b -> write())
				.bounds(right - 64 - gap - 96, by, 96, 20).build());
		refreshHeld(true);
	}

	// ---------------------------------------------------------------- Aktionen

	private void onEdit() {
		planDirty = true;
		unsaved = true;
		lastEdit = Util.getMillis();
		followCursor = true;
	}

	private void flip(int d) {
		followCursor = false;
		previewPage += d;
		clampPreview();
	}

	private void clampPreview() {
		int n = plan == null ? 1 : Math.max(1, plan.pages().size());
		previewPage = Math.max(0, Math.min(previewPage, n - 1));
	}

	private void showStatus(String text, ChatFormatting color) {
		status = Component.literal(text).withStyle(color);
		statusUntil = Util.getMillis() + 5000;
	}

	private void openDrafts() {
		saveNow();
		minecraft.setScreen(new DraftsScreen(this, editor));
	}

	private void importHeld() {
		if (minecraft.player == null) return;
		ItemStack hand = minecraft.player.getMainHandItem();
		var text = HeldLetter.importable(hand);
		if (text.isEmpty()) {
			showStatus("Halte ein Buch, Buch und Feder oder einen Brief in der Hand.", ChatFormatting.RED);
			return;
		}
		editor.setText(text.get(), true);
		showStatus("Text übernommen – mit Strg+Z rückgängig.", ChatFormatting.DARK_GREEN);
	}

	private void signature() {
		Config cfg = Config.get();
		if (hasShiftDown()) {
			if (!editor.hasSelection()) {
				showStatus("Erst den Text der Signatur markieren.", ChatFormatting.RED);
				return;
			}
			cfg.signature = editor.selection().toLegacy('&').replace(String.valueOf(RichText.PAGE_BREAK), "\n");
			cfg.save();
			showStatus("Signatur gemerkt.", ChatFormatting.DARK_GREEN);
			return;
		}
		String name = minecraft.player != null ? minecraft.player.getGameProfile().name() : "";
		RichText sig = RichText.parseLegacy(cfg.signature.replace("%spieler%", name), true);
		RichText ins = new RichText();
		RichText t = editor.text();
		int c = editor.selStart();
		if (c > 0 && t.charAt(c - 1) != '\n') ins.append("\n", 0);
		ins.append(sig);
		editor.replaceSelection(ins, false);
	}

	private void write() {
		updatePlan();
		if (plan == null || plan.commands().isEmpty()) {
			showStatus("Der Brief ist leer.", ChatFormatting.RED);
			return;
		}
		String err = LetterSender.start(plan.commands());
		if (err != null) {
			showStatus(err, ChatFormatting.RED);
			return;
		}
		SchreibstubeClient.startedWriting(editor.text().copy());
		saveNow();
		minecraft.setScreen(null);
	}

	private boolean hasShiftDown() {
		return minecraft != null && (com.mojang.blaze3d.platform.InputConstants.isKeyDown(minecraft.getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
				|| com.mojang.blaze3d.platform.InputConstants.isKeyDown(minecraft.getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT));
	}

	private void saveNow() {
		if (editor == null) return;
		Drafts.saveCurrent(editor.text());
		unsaved = false;
		lastSave = Util.getMillis();
	}

	/** Liest die Seiten, die schon im gehaltenen Brief stehen, damit die Vorschau dort ansetzt. */
	private void refreshHeld(boolean force) {
		if (minecraft == null || minecraft.player == null) return;
		ItemStack hand = minecraft.player.getMainHandItem();
		List<String> pages = HeldLetter.existingPages(hand);
		String key = hand.getItem() + "|" + hand.getCount() + "|" + String.join("\u0000", pages);
		if (!force && key.equals(heldKey)) return;
		heldKey = key;
		existing = pages;
		planDirty = true;
	}

	private void updatePlan() {
		if (!planDirty && plan != null) return;
		planDirty = false;
		plan = Courier.plan(editor.text(), existing);
		editor.setPageStarts(plan.pageStarts());
		clampPreview();
	}

	// ---------------------------------------------------------------- Zeichnen

	/** Gleich nach dem Öffnen die Entwürfe zeigen. */
	public LetterScreen openingDrafts() {
		openDraftsOnTick = true;
		return this;
	}

	@Override
	public void tick() {
		if (openDraftsOnTick) {
			openDraftsOnTick = false;
			openDrafts();
			return;
		}
		refreshHeld(false);
		long now = Util.getMillis();
		if (unsaved && now - lastSave > 2000) saveNow();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		if (planDirty) updatePlan();
		if (followCursor && plan != null) {
			previewPage = editor.cursor() >= editor.text().length() ? plan.pages().size() - 1 : previewPageForCursor();
			clampPreview();
		}

		// Kopfzeile und Leiste
		Ui.wood(g, 0, 0, width, 40);
		g.drawString(font, Component.literal("✒ Schreibstube").withStyle(ChatFormatting.BOLD), Math.max(8, edX), 7, Ui.GOLD, true);
		drawHeldState(g);

		Ui.parchment(g, edX, edY, edW, edH);
		editor.render(g, true);

		drawBook(g);
		drawStatus(g, mouseX, mouseY);

		if (writeButton != null) {
			String why = whyNotWrite();
			writeButton.active = why == null;
			writeButton.setTooltip(Tooltip.create(Component.literal(why != null ? why
					: "Schreibt den Brief mit " + plan.commands().size() + " /letter-Befehlen auf dein Pergament.")));
		}
		if (prevPage != null) {
			prevPage.visible = previewPage > 0;
			nextPage.visible = plan != null && previewPage < plan.pages().size() - 1;
		}
		super.render(g, mouseX, mouseY, delta);
	}

	/** Buchseite, auf der der Cursor steht. */
	private int previewPageForCursor() {
		int page = Math.max(0, plan.existingPages() - 1);
		for (var e : plan.pageStarts().entrySet()) {
			if (e.getKey() <= editor.cursor()) page = e.getValue();
			else break;
		}
		return page;
	}

	private String whyNotWrite() {
		if (LetterSender.busy()) return "Es wird gerade ein Brief geschrieben.";
		if (plan == null || plan.commands().isEmpty()) return "Der Brief ist noch leer.";
		if (minecraft.player == null) return "Nicht im Spiel.";
		return HeldLetter.checkInventory(minecraft.player);
	}

	private void drawHeldState(GuiGraphics g) {
		if (minecraft.player == null) return;
		ItemStack hand = minecraft.player.getMainHandItem();
		String text;
		int color;
		String problem = HeldLetter.checkInventory(minecraft.player);
		if (problem == null) {
			text = HeldLetter.isStarted(hand) ? "✔ Angefangener Brief in der Hand – wird fortgesetzt" : "✔ " + Config.get().itemName + " in der Hand";
			color = 0xFF8FD18F;
		} else if (HeldLetter.isWritable(hand) && hand.getCount() > 1) {
			text = "✖ Nur ein Pergament in die Hand nehmen";
			color = 0xFFFF8080;
		} else if (HeldLetter.isWritable(hand)) {
			text = "✖ Weiter links liegt noch ein Pergament";
			color = 0xFFFF8080;
		} else {
			text = "✖ Nimm „" + Config.get().itemName + "“ in die Hand";
			color = 0xFFFF8080;
		}
		int right = Math.min(width - 8, bookX + Math.round(BOOK_W * bookScale));
		int tw = font.width(text);
		g.drawString(font, text, right - tw, 7, color, true);
		if (!hand.isEmpty()) g.renderItem(hand, right - tw - 20, 3);
	}

	private void drawBook(GuiGraphics g) {
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(bookX, bookY);
		pose.scale(bookScale, bookScale);
		g.blit(RenderPipelines.GUI_TEXTURED, BookViewScreen.BOOK_LOCATION, 0, 0, BOOK_U, 0, BOOK_W, BOOK_H, 256, 256);
		if (plan != null && !plan.pages().isEmpty()) {
			int total = plan.pages().size();
			Component ind = Component.translatable("book.pageIndicator", previewPage + 1, total);
			g.drawString(font, ind, 130 - font.width(ind), 16, 0xFF000000, false);
			int first = plan.existingPages() - 1;
			if (previewPage < first) g.drawString(font, "schon im Brief", PAGE_TEXT_X, 16, 0xFF8A7A60, false);
			else if (previewPage == first && first >= 0) g.drawString(font, "wird ergänzt", PAGE_TEXT_X, 16, 0xFF8A7A60, false);
			String page = plan.pages().get(previewPage);
			List<FormattedCharSequence> lines = font.split(pageText(page), PAGE_TEXT_W);
			for (int i = 0; i < Math.min(PAGE_LINES, lines.size()); i++) {
				g.drawString(font, lines.get(i), PAGE_TEXT_X, PAGE_TEXT_Y + i * 9, 0xFF000000, false);
			}
			if (lines.size() > PAGE_LINES) {
				g.fill(PAGE_TEXT_X, PAGE_TEXT_Y + PAGE_LINES * 9, PAGE_TEXT_X + PAGE_TEXT_W, PAGE_TEXT_Y + PAGE_LINES * 9 + 1, 0xFFC03030);
			}
		} else {
			g.drawString(font, "Vorschau", PAGE_TEXT_X, PAGE_TEXT_Y, 0xFF8A7A60, false);
		}
		pose.popMatrix();
	}

	/** Buchseite mit §-Codes als richtig formatierter Text. */
	private static Component pageText(String page) {
		return RichText.parseLegacy(page, false).toComponent(true);
	}

	/** Seiten, die im Buch mehr als 14 Zeilen bräuchten und deshalb abgeschnitten würden. */
	private List<Integer> cutPages() {
		List<Integer> out = new ArrayList<>();
		if (plan == null) return out;
		for (int i = Math.max(0, plan.existingPages() - 1); i < plan.pages().size(); i++) {
			if (font.split(pageText(plan.pages().get(i)), PAGE_TEXT_W).size() > PAGE_LINES) out.add(i + 1);
		}
		return out;
	}

	private void drawStatus(GuiGraphics g, int mouseX, int mouseY) {
		int y = height - 44;
		int x = Math.max(8, edX);
		if (status != null && Util.getMillis() < statusUntil) {
			g.drawString(font, status, x, y, 0xFFFFFFFF, true);
			return;
		}
		if (plan == null) return;
		RichText t = editor.text();
		int newPages = plan.pages().size() - Math.max(0, plan.existingPages() - 1);
		if (t.isEmpty()) newPages = 0;
		int secs = (int) Math.ceil(plan.commands().size() * Config.get().delayMs / 1000.0);
		String info = String.format(Locale.ROOT, "%d Wörter · %d Zeichen · %d %s · %d %s · ca. %d s",
				editor.words(), t.length(), newPages, newPages == 1 ? "Buchseite" : "Buchseiten",
				plan.commands().size(), plan.commands().size() == 1 ? "Befehl" : "Befehle", secs);
		g.drawString(font, info, x, y, 0xFFCFC2A8, true);

		List<String> warn = new ArrayList<>(plan.warnings());
		List<Integer> cut = cutPages();
		if (!cut.isEmpty()) warn.addFirst((cut.size() == 1 ? "Seite " : "Seiten ") + joinInts(cut)
				+ " passt nicht ganz ins Buch und wird unten abgeschnitten. Füge davor mit ✂ einen Seitenumbruch ein.");
		if (plan.pages().size() > 100) warn.addFirst("Mehr als 100 Seiten – so lang darf ein Buch nicht sein.");
		if (!warn.isEmpty()) {
			String label = "⚠ " + warn.size() + (warn.size() == 1 ? " Hinweis" : " Hinweise");
			int right = Math.min(width - 8, bookX + Math.round(BOOK_W * bookScale));
			int lw = font.width(label);
			g.drawString(font, label, right - lw, y, 0xFFFFB050, true);
			if (mouseX >= right - lw && mouseX <= right && mouseY >= y - 2 && mouseY <= y + 10) {
				List<FormattedCharSequence> tip = new ArrayList<>();
				for (String w : warn) tip.addAll(font.split(Component.literal("• " + w), 220));
				g.setTooltipForNextFrame(tip, mouseX, mouseY);
			}
		}
	}

	private static String joinInts(List<Integer> v) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < v.size(); i++) {
			if (i > 0) sb.append(i == v.size() - 1 ? " und " : ", ");
			sb.append(v.get(i));
		}
		return sb.toString();
	}

	// ---------------------------------------------------------------- Eingabe

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.isEscape()) return super.keyPressed(e);
		if (editor.keyPressed(e)) return true;
		return super.keyPressed(e);
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		return editor.charTyped(e) || super.charTyped(e);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (super.mouseClicked(e, doubleClick)) return true;
		setFocused(null);
		return editor.mouseClicked(e.x(), e.y(), e.hasShiftDown(), doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (editor.mouseDragged(e.x(), e.y())) return true;
		return super.mouseDragged(e, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		editor.mouseReleased();
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (editor.mouseScrolled(mx, my, sy)) return true;
		int bw = Math.round(BOOK_W * bookScale), bh = Math.round(BOOK_H * bookScale);
		if (mx >= bookX && mx < bookX + bw && my >= bookY && my < bookY + bh && sy != 0) {
			flip(sy > 0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public void onClose() {
		saveNow();
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	public RichTextEditor editor() {
		return editor;
	}

	public Courier.Plan plan() {
		updatePlan();
		return plan;
	}

	// ---------------------------------------------------------------- Leiste

	private class Swatch extends Ui.SmallButton {
		private final int code;

		Swatch(int x, int y, int size, int code) {
			super(x, y, size, size, Component.literal(code < 0 ? "Standardfarbe" : COLOR_NAMES[code]),
					code < 0 ? "Standardfarbe" : COLOR_NAMES[code], () -> editor.setColor(RichText.colorOf(code)));
			this.code = code;
		}

		@Override
		protected boolean selected() {
			return RichText.color(editor.currentStyle()) == RichText.colorOf(code);
		}

		@Override
		protected void drawFace(GuiGraphics g, int x, int y, int w, int h) {
			if (code < 0) {
				g.fill(x, y, x + w, y + h, 0xFFE7D5AB);
				g.drawCenteredString(font, "A", x + w / 2, y + 1, RichTextEditor.INK);
				return;
			}
			Integer rgb = ChatFormatting.getById(code).getColor();
			g.fill(x, y, x + w, y + h, 0xFF000000 | (rgb == null ? 0 : rgb));
		}
	}

	private class StyleButton extends Ui.SmallButton {
		private final int flag;
		private final String letter;

		StyleButton(int x, int y, String letter, int flag, String tip) {
			super(x, y, 14, 14, Component.literal(tip), tip, () -> editor.toggleFlag(flag));
			this.flag = flag;
			this.letter = letter;
		}

		@Override
		protected boolean selected() {
			return (editor.currentStyle() & flag) != 0;
		}

		@Override
		protected void drawFace(GuiGraphics g, int x, int y, int w, int h) {
			g.fill(x, y, x + w, y + h, selected() ? 0xFFF7E3A8 : 0xFFE7D5AB);
			Component c = Component.literal(letter).withStyle(RichText.toStyle(flag, false));
			g.drawString(font, c, x + (w - font.width(c)) / 2 + 1, y + 3, RichTextEditor.INK, false);
		}
	}
}
