package de.minentimer.gui;

import de.minentimer.Mine;
import de.minentimer.MineManager;
import de.minentimer.MineSize;
import de.minentimer.ResourceType;
import de.minentimer.TimeFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Mine anlegen oder bearbeiten: Name, Position, Rohstoff, Größe, eigene Zeit, Lehen. */
public class MineEditScreen extends Screen {
	private final Screen parent;
	private final Mine mine;
	private final boolean isNew;

	private EditBox nameBox, xBox, yBox, zBox, minutesBox, areaBox;
	private Button saveButton;
	private boolean confirmDelete;
	private int left, top;

	public MineEditScreen(Screen parent, Mine mine, boolean isNew) {
		super(Component.literal(isNew ? "Neue Mine" : "Mine bearbeiten"));
		this.parent = parent;
		this.mine = mine;
		this.isNew = isNew;
	}

	@Override
	protected void init() {
		int w = 300;
		left = width / 2 - w / 2;
		top = Math.max(28, height / 2 - 110);
		int y = top + 10;

		nameBox = new EditBox(font, left, y, w, 20, Component.literal("Name"));
		nameBox.setMaxLength(40);
		nameBox.setValue(mine.name);
		nameBox.setResponder(s -> updateSave());
		addRenderableWidget(nameBox);

		y += 34;
		xBox = coordBox(left, y, mine.x, "X");
		yBox = coordBox(left + 62, y, mine.y, "Y");
		zBox = coordBox(left + 124, y, mine.z, "Z");
		addRenderableWidget(Button.builder(Component.literal("Meine Position"), b -> {
			if (minecraft.player == null) return;
			xBox.setValue(String.valueOf(minecraft.player.getBlockX()));
			yBox.setValue(String.valueOf(minecraft.player.getBlockY()));
			zBox.setValue(String.valueOf(minecraft.player.getBlockZ()));
		}).bounds(left + 186, y, w - 186, 20).build());

		y += 34;
		addRenderableWidget(CycleButton.<ResourceType>builder(r -> Component.literal(r.displayName).withStyle(s -> s.withColor(r.color & 0xFFFFFF)), mine.type)
				.withValues(ResourceType.values())
				.create(left, y, 148, 20, Component.literal("Rohstoff"), (b, v) -> mine.type = v));
		addRenderableWidget(CycleButton.<MineSize>builder(s -> Component.literal(s.label), mine.size)
				.withValues(MineSize.values())
				.create(left + 152, y, w - 152, 20, Component.literal("Größe"), (b, v) -> mine.size = v));

		y += 34;
		minutesBox = new EditBox(font, left, y, 148, 20, Component.literal("Eigene Zeit"));
		minutesBox.setFilter(s -> s.matches("\\d{0,4}"));
		minutesBox.setValue(mine.customMinutes > 0 ? String.valueOf(mine.customMinutes) : "");
		minutesBox.setHint(Component.literal("Standard").withStyle(s -> s.withColor(0x777777)));
		addRenderableWidget(minutesBox);

		y += 34;
		areaBox = new EditBox(font, left, y, 148, 20, Component.literal("Lehen"));
		areaBox.setMaxLength(32);
		areaBox.setValue(mine.area);
		areaBox.setHint(Component.literal("keins").withStyle(s -> s.withColor(0x777777)));
		addRenderableWidget(areaBox);
		addRenderableWidget(Button.builder(Component.literal("Vorhandenes Lehen ▸"), b -> {
			List<String> areas = MineManager.get().areas();
			if (areas.isEmpty()) return;
			int i = -1;
			for (int k = 0; k < areas.size(); k++) if (areas.get(k).equalsIgnoreCase(areaBox.getValue().trim())) i = k;
			areaBox.setValue(areas.get((i + 1) % areas.size()));
		}).bounds(left + 152, y, w - 152, 20).build());

		int by = height - 28;
		Button deleteButton = Button.builder(Component.literal("Löschen"), b -> {
			if (!confirmDelete) {
				confirmDelete = true;
				b.setMessage(Component.literal("Wirklich löschen?").withStyle(s -> s.withColor(0xFF5555)));
				return;
			}
			MineManager.get().deleteMine(mine);
			minecraft.setScreen(parent);
		}).bounds(left, by, 96, 20).build();
		deleteButton.active = !isNew;
		addRenderableWidget(deleteButton);
		addRenderableWidget(Button.builder(Component.literal("Abbrechen"), b -> onClose()).bounds(left + 102, by, 96, 20).build());
		saveButton = addRenderableWidget(Button.builder(Component.literal("Speichern"), b -> save()).bounds(left + 204, by, 96, 20).build());
		updateSave();
	}

	private EditBox coordBox(int x, int y, int value, String label) {
		EditBox box = new EditBox(font, x, y, 58, 20, Component.literal(label));
		box.setFilter(s -> s.matches("-?\\d{0,8}"));
		box.setValue(String.valueOf(value));
		box.setResponder(s -> updateSave());
		addRenderableWidget(box);
		return box;
	}

	private static Integer parse(EditBox box) {
		try {
			return Integer.parseInt(box.getValue().trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void updateSave() {
		if (saveButton == null) return;
		saveButton.active = !nameBox.getValue().isBlank() && parse(xBox) != null && parse(yBox) != null && parse(zBox) != null;
	}

	private void save() {
		mine.name = nameBox.getValue().trim();
		mine.x = parse(xBox);
		mine.y = parse(yBox);
		mine.z = parse(zBox);
		Integer minutes = parse(minutesBox);
		mine.customMinutes = minutes == null ? 0 : minutes;
		mine.area = areaBox.getValue().trim();
		MineManager.get().saveMine(mine);
		minecraft.setScreen(parent);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		g.drawCenteredString(font, title, width / 2, top - 16, 0xFFFFAA00);
		int gray = 0xFFAAAAAA;
		g.drawString(font, "Name", left, top, gray, false);
		g.drawString(font, "Position (X / Y / Z)", left, top + 34, gray, false);
		g.drawString(font, "Rohstoff-Art", left, top + 68, gray, false);
		g.drawString(font, "Größe", left + 152, top + 68, gray, false);
		g.drawString(font, "Eigene Zeit (Minuten)", left, top + 102, gray, false);
		g.drawString(font, "Lehen (zum Gruppieren)", left, top + 136, gray, false);

		Integer custom = parse(minutesBox);
		int minutes = custom != null && custom > 0 ? custom : mine.type.minutes(mine.size);
		String info = "Respawn: " + TimeFormat.minutes(minutes) + (custom != null && custom > 0 ? " (eigene Zeit)" : " (Standard)");
		g.drawString(font, info, left + 152, top + 118, 0xFFFFFFFF, false);
	}
}
