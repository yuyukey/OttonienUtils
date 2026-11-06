package de.schreibstube.gui;

import de.schreibstube.Drafts;
import de.schreibstube.SchreibstubeClient;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Entwürfe speichern, laden und löschen. Sie liegen als .txt in config/schreibstube/briefe. */
public class DraftsScreen extends Screen {
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.systemDefault());
	private static final int ROW = 22;

	private final Screen parent;
	private final RichTextEditor editor;
	private List<Drafts.Entry> entries = List.of();
	private int selected = -1;
	private int scroll;
	private EditBox name;
	private Button load, delete;
	private boolean confirmDelete;
	private Component status;
	private int listX, listY, listW, listH;

	public DraftsScreen(Screen parent, RichTextEditor editor) {
		super(Component.literal("Entwürfe"));
		this.parent = parent;
		this.editor = editor;
	}

	@Override
	protected void init() {
		entries = Drafts.list();
		listW = Math.min(340, width - 40);
		listX = (width - listW) / 2;
		listY = 40;
		listH = height - 40 - 80;

		int y = height - 66;
		name = addRenderableWidget(new EditBox(font, listX, y, listW - 84, 20, Component.literal("Name")));
		name.setMaxLength(60);
		name.setHint(Component.literal("Name für den Entwurf").withStyle(ChatFormatting.DARK_GRAY));
		addRenderableWidget(Button.builder(Component.literal("Speichern"), b -> save()).bounds(listX + listW - 80, y, 80, 20).build());

		int by = height - 40, bw = (listW - 12) / 4;
		load = addRenderableWidget(Button.builder(Component.literal("Laden"), b -> load()).bounds(listX, by, bw, 20).build());
		delete = addRenderableWidget(Button.builder(Component.literal("Löschen"), b -> delete()).bounds(listX + bw + 4, by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Ordner öffnen"), b -> {
			try {
				Files.createDirectories(Drafts.folder());
			} catch (IOException ignored) {
			}
			Util.getPlatform().openPath(Drafts.folder());
		}).bounds(listX + 2 * (bw + 4), by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Zurück"), b -> onClose()).bounds(listX + 3 * (bw + 4), by, bw, 20).build());
		updateButtons();
	}

	private void updateButtons() {
		boolean has = selected >= 0 && selected < entries.size();
		load.active = has;
		delete.active = has;
		delete.setMessage(Component.literal(confirmDelete ? "Wirklich?" : "Löschen"));
	}

	private void save() {
		String n = name.getValue().strip();
		if (n.isEmpty()) {
			String plain = editor.text().plain().strip();
			n = plain.isEmpty() ? "Brief" : plain.split("\\s+", 2)[0];
			n = n + " " + DateTimeFormatter.ofPattern("dd.MM.").withZone(ZoneId.systemDefault()).format(java.time.Instant.now());
		}
		try {
			String saved = Drafts.save(n, editor.text());
			status = Component.literal("Gespeichert als „" + saved + "“").withStyle(ChatFormatting.GREEN);
			entries = Drafts.list();
			selected = -1;
			for (int i = 0; i < entries.size(); i++) if (entries.get(i).name().equals(saved)) selected = i;
		} catch (IOException e) {
			SchreibstubeClient.LOGGER.warn("Speichern fehlgeschlagen", e);
			status = Component.literal("Speichern fehlgeschlagen: " + e.getMessage()).withStyle(ChatFormatting.RED);
		}
		confirmDelete = false;
		updateButtons();
	}

	private void load() {
		if (selected < 0 || selected >= entries.size()) return;
		try {
			editor.setText(Drafts.load(entries.get(selected)), true);
			onClose();
		} catch (IOException e) {
			status = Component.literal("Laden fehlgeschlagen: " + e.getMessage()).withStyle(ChatFormatting.RED);
		}
	}

	private void delete() {
		if (selected < 0 || selected >= entries.size()) return;
		if (!confirmDelete) {
			confirmDelete = true;
			updateButtons();
			return;
		}
		try {
			Drafts.delete(entries.get(selected));
			status = Component.literal("Gelöscht").withStyle(ChatFormatting.GRAY);
		} catch (IOException e) {
			status = Component.literal("Löschen fehlgeschlagen: " + e.getMessage()).withStyle(ChatFormatting.RED);
		}
		entries = Drafts.list();
		selected = -1;
		confirmDelete = false;
		updateButtons();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		Ui.wood(g, 0, 0, width, 30);
		g.drawCenteredString(font, Component.literal("✒ Entwürfe").withStyle(ChatFormatting.BOLD), width / 2, 11, Ui.GOLD);
		Ui.parchment(g, listX, listY, listW, listH);
		int rows = listH / ROW;
		scroll = Math.max(0, Math.min(scroll, entries.size() - rows));
		g.enableScissor(listX, listY, listX + listW, listY + listH);
		if (entries.isEmpty()) {
			g.drawCenteredString(font, "Noch keine Entwürfe gespeichert.", listX + listW / 2, listY + 12, 0xFF8A7A60);
			g.drawCenteredString(font, "Textdateien im Ordner erscheinen hier auch.", listX + listW / 2, listY + 24, 0xFF8A7A60);
		}
		for (int i = scroll; i < Math.min(entries.size(), scroll + rows + 1); i++) {
			Drafts.Entry e = entries.get(i);
			int ry = listY + (i - scroll) * ROW;
			boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= ry && mouseY < ry + ROW;
			if (i == selected) g.fill(listX + 2, ry + 1, listX + listW - 2, ry + ROW - 1, 0x40A0522D);
			else if (hover) g.fill(listX + 2, ry + 1, listX + listW - 2, ry + ROW - 1, 0x20A0522D);
			g.drawString(font, Component.literal(e.name()).withStyle(ChatFormatting.BOLD), listX + 6, ry + 2, RichTextEditor.INK, false);
			String date = DATE.format(e.modified().toInstant());
			g.drawString(font, date, listX + listW - 6 - font.width(date), ry + 2, 0xFF8A7A60, false);
			g.drawString(font, font.plainSubstrByWidth(e.preview(), listW - 12), listX + 6, ry + 12, 0xFF6B5A40, false);
		}
		g.disableScissor();
		if (status != null) g.drawCenteredString(font, status, width / 2, height - 78 + 0, 0xFFFFFFFF);
		super.render(g, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (super.mouseClicked(e, doubleClick)) return true;
		if (e.x() >= listX && e.x() < listX + listW && e.y() >= listY && e.y() < listY + listH) {
			int i = scroll + (int) ((e.y() - listY) / ROW);
			if (i < entries.size()) {
				if (i != selected) confirmDelete = false;
				selected = i;
				name.setValue(entries.get(i).name());
				updateButtons();
				if (doubleClick) load();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		scroll -= (int) Math.signum(sy);
		return true;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
