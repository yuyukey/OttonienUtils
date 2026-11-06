package de.schreibstube.gui;

import de.schreibstube.Config;
import de.schreibstube.send.HeldLetter;
import de.schreibstube.send.LetterSender;
import de.schreibstube.text.Courier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Empfänger wählen und den Brief mit /post verschicken. Zuletzt angeschriebene stehen oben. */
public class RecipientScreen extends Screen {
	private static final int ROW = 14;

	private final Screen parent;
	private EditBox name;
	private Button send;
	private final List<String> recent = new ArrayList<>();
	private List<String> shown = List.of();
	private int scroll;
	private int listX, listY, listW, listH;
	private Component error;

	public RecipientScreen(Screen parent, String preset) {
		super(Component.literal("Brief verschicken"));
		this.parent = parent;
		if (preset != null) recent.add(preset);
	}

	@Override
	protected void init() {
		listW = Math.min(240, width - 40);
		listX = (width - listW) / 2;
		listY = 70;
		listH = height - 70 - 40;
		String before = name != null ? name.getValue() : (recent.isEmpty() ? "" : recent.getFirst());
		name = addRenderableWidget(new EditBox(font, listX, 42, listW, 20, Component.literal("Empfänger")));
		name.setMaxLength(16);
		name.setFilter(s -> s.matches("[A-Za-z0-9_]*"));
		name.setHint(Component.literal("Spielername").withStyle(ChatFormatting.DARK_GRAY));
		name.setValue(before);
		name.setResponder(s -> refresh());
		setInitialFocus(name);
		int bw = (listW - 4) / 2;
		send = addRenderableWidget(Button.builder(Component.literal("✉ Verschicken"), b -> send()).bounds(listX, height - 30, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Später"), b -> onClose()).bounds(listX + bw + 4, height - 30, bw, 20).build());
		refresh();
	}

	private void refresh() {
		String q = name.getValue().toLowerCase(Locale.ROOT);
		Set<String> all = new LinkedHashSet<>(Config.get().recentRecipients);
		List<String> online = new ArrayList<>();
		if (minecraft.getConnection() != null) {
			String self = minecraft.player != null ? minecraft.player.getGameProfile().name() : "";
			for (PlayerInfo p : minecraft.getConnection().getOnlinePlayers()) {
				String n = p.getProfile().name();
				if (!n.equalsIgnoreCase(self) && n.matches("[A-Za-z0-9_]{1,16}")) online.add(n);
			}
		}
		online.sort(String.CASE_INSENSITIVE_ORDER);
		all.addAll(online);
		List<String> out = new ArrayList<>();
		for (String n : all) if (n.toLowerCase(Locale.ROOT).contains(q)) out.add(n);
		shown = out;
		scroll = 0;
		send.active = name.getValue().matches("[A-Za-z0-9_]{1,16}");
	}

	private boolean isOnline(String n) {
		if (minecraft.getConnection() == null) return false;
		for (PlayerInfo p : minecraft.getConnection().getOnlinePlayers()) if (p.getProfile().name().equalsIgnoreCase(n)) return true;
		return false;
	}

	private void send() {
		String n = name.getValue();
		if (!n.matches("[A-Za-z0-9_]{1,16}") || minecraft.getConnection() == null || minecraft.player == null) return;
		if (LetterSender.busy()) {
			error = Component.literal("Warte, bis der Brief fertig geschrieben ist.").withStyle(ChatFormatting.RED);
			return;
		}
		if (!HeldLetter.isStarted(minecraft.player.getMainHandItem()) && !HeldLetter.isFresh(minecraft.player.getMainHandItem())) {
			error = Component.literal("Halte den beschriebenen Brief in der Hand.").withStyle(ChatFormatting.RED);
			return;
		}
		Config.get().rememberRecipient(n);
		minecraft.getConnection().sendCommand(Courier.POST_COMMAND + " " + n);
		minecraft.player.displayClientMessage(LetterSender.prefix().append(Component.literal("Brief an " + n + " übergeben.").withStyle(ChatFormatting.GRAY)), false);
		minecraft.setScreen(null);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		Ui.wood(g, 0, 0, width, 30);
		g.drawCenteredString(font, Component.literal("✉ Brief verschicken").withStyle(ChatFormatting.BOLD), width / 2, 11, Ui.GOLD);
		Ui.parchment(g, listX, listY, listW, listH);
		int rows = listH / ROW;
		scroll = Math.max(0, Math.min(scroll, shown.size() - rows));
		if (shown.isEmpty()) g.drawCenteredString(font, "Niemand gefunden – Namen oben eintippen", listX + listW / 2, listY + 8, 0xFF8A7A60);
		g.enableScissor(listX, listY, listX + listW, listY + listH);
		List<String> recentCfg = Config.get().recentRecipients;
		for (int i = scroll; i < Math.min(shown.size(), scroll + rows + 1); i++) {
			String n = shown.get(i);
			int ry = listY + 2 + (i - scroll) * ROW;
			boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= ry && mouseY < ry + ROW;
			if (n.equalsIgnoreCase(name.getValue())) g.fill(listX + 2, ry, listX + listW - 2, ry + ROW, 0x40A0522D);
			else if (hover) g.fill(listX + 2, ry, listX + listW - 2, ry + ROW, 0x20A0522D);
			boolean on = isOnline(n);
			g.drawString(font, (on ? "● " : "○ ") + n, listX + 6, ry + 3, on ? 0xFF2E6B2E : RichTextEditor.INK, false);
			if (recentCfg.stream().anyMatch(r -> r.equalsIgnoreCase(n))) {
				g.drawString(font, "zuletzt", listX + listW - 6 - font.width("zuletzt"), ry + 3, 0xFF8A7A60, false);
			}
		}
		g.disableScissor();
		if (error != null) g.drawCenteredString(font, error, width / 2, height - 42, 0xFFFFFFFF);
		super.render(g, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if ((e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) && send.active) {
			send();
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (super.mouseClicked(e, doubleClick)) return true;
		if (e.x() >= listX && e.x() < listX + listW && e.y() >= listY + 2 && e.y() < listY + listH) {
			int i = scroll + (int) ((e.y() - listY - 2) / ROW);
			if (i < shown.size()) {
				String n = shown.get(i);
				name.setValue(n);
				if (doubleClick) send();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		scroll -= (int) Math.signum(sy) * 2;
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
