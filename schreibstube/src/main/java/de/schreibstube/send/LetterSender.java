package de.schreibstube.send;

import de.schreibstube.Config;
import de.schreibstube.SchreibstubeClient;
import de.schreibstube.text.Courier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

/**
 * Schickt die /letter-Befehle nacheinander mit Pause, solange das Schreibzeug in der Hand bleibt.
 * Wechselt man das Item oder den Slot, wird angehalten. Der Rest lässt sich fortsetzen.
 */
public final class LetterSender {
	private static List<String> queue = new ArrayList<>();
	private static int sent;
	private static int slot = -1;
	private static long nextAt;
	private static long lastEchoUntil;
	private static boolean paused;
	/** Alle Befehle raus, nur noch den Brief in die Hand holen. */
	private static boolean finishing;
	/** Angefangene Briefe im Inventar vor dem Start, um den eigenen danach wiederzufinden. */
	private static Set<String> before = new HashSet<>();

	private LetterSender() {
	}

	public static boolean busy() {
		return !queue.isEmpty() && (sent < queue.size() || finishing) && !paused;
	}

	public static boolean hasPaused() {
		return paused && sent < queue.size();
	}

	public static int sent() {
		return sent;
	}

	public static int total() {
		return queue.size();
	}

	/** Server-Bestätigungen kurz nach einem Befehl ausblenden. */
	public static boolean expectsEcho() {
		return System.currentTimeMillis() < lastEchoUntil;
	}

	/** Startet das Schreiben. Gibt eine Fehlermeldung zurück oder null. */
	public static String start(List<String> commands) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.getConnection() == null) return "Nicht mit einem Server verbunden.";
		if (commands.isEmpty()) return "Der Brief ist leer.";
		if (busy()) return "Es wird gerade schon ein Brief geschrieben.";
		String held = HeldLetter.checkInventory(mc.player);
		if (held != null) return held;
		queue = new ArrayList<>(commands);
		sent = 0;
		paused = false;
		finishing = false;
		before = startedLetters(mc);
		RefillGuard.suspend();
		slot = mc.player.getInventory().getSelectedSlot();
		nextAt = 0;
		return null;
	}

	public static void resume() {
		Minecraft mc = Minecraft.getInstance();
		if (!hasPaused() || mc.player == null) return;
		if (sent > 0) fetchLetter(mc);
		String held = sent > 0 && !isOurs(mc.player.getMainHandItem())
				? "Dein angefangener Brief ist nicht mehr im Inventar." : HeldLetter.check(mc.player.getMainHandItem());
		if (held != null) {
			mc.player.displayClientMessage(prefix().append(Component.literal(held).withStyle(ChatFormatting.RED)), false);
			return;
		}
		slot = mc.player.getInventory().getSelectedSlot();
		paused = false;
		nextAt = 0;
	}

	public static void cancel() {
		RefillGuard.restore();
		queue = new ArrayList<>();
		sent = 0;
		paused = false;
		finishing = false;
	}

	public static void tick(Minecraft mc) {
		if (queue.isEmpty() || paused) return;
		if (mc.player == null || mc.getConnection() == null) {
			cancel();
			return;
		}
		long now = System.currentTimeMillis();
		if (now < nextAt) {
			progress(mc);
			return;
		}
		// Ab dem zweiten Befehl muss genau unser angefangener Brief in der Hand sein. Hat ihn der Server
		// oder eine Nachfüll-Mod (z. B. Mouse Wheelie) gegen ein leeres Pergament getauscht, zurückholen.
		if (sent > 0 && !isOurs(mc.player.getMainHandItem())) {
			logState(mc, "Brief nicht in der Hand");
			if (fetchLetter(mc)) {
				nextAt = now + 300;
				return;
			}
			pause(mc, "Dein angefangener Brief ist nicht mehr in der Hand und wurde im Inventar nicht gefunden. ");
			return;
		}
		if (finishing) {
			finished(mc);
			return;
		}
		ItemStack hand = mc.player.getMainHandItem();
		if (mc.player.getInventory().getSelectedSlot() != slot || (sent == 0 && HeldLetter.check(hand) != null)) {
			pause(mc, "Angehalten – halte den Brief wieder in der Hand. ");
			return;
		}
		String cmd = queue.get(sent++);
		lastEchoUntil = now + 3000;
		logState(mc, "vor Befehl " + sent);
		mc.getConnection().sendCommand(Courier.COMMAND + " " + cmd);
		nextAt = now + Config.get().delayMs;
		progress(mc);
		if (sent >= queue.size()) {
			// noch einmal warten, damit der Brief am Ende sicher in der Hand liegt
			finishing = true;
			nextAt = now + Math.min(1000, Config.get().delayMs);
		}
	}

	/** Für Fehlersuche: was in Hand und am Mauszeiger liegt (landet in logs/latest.log). */
	private static void logState(Minecraft mc, String when) {
		ItemStack hand = mc.player.getMainHandItem(), cursor = mc.player.inventoryMenu.getCarried();
		SchreibstubeClient.LOGGER.info("[Schreibstube] {}: Platz {}, Hand {} x{} ({}), Mauszeiger {} x{} ({})", when,
				mc.player.getInventory().getSelectedSlot() + 1, hand.getItem(), hand.getCount(), hand.getHoverName().getString(),
				cursor.getItem(), cursor.getCount(), cursor.getHoverName().getString());
	}

	private static void pause(Minecraft mc, String why) {
		logState(mc, "angehalten");
		paused = true;
		MutableComponent msg = prefix().append(Component.literal(why).withStyle(ChatFormatting.GOLD))
				.append(button("[Weiter]", "/schreibstube weiter", "Restliche " + (queue.size() - sent) + " Befehle schreiben", ChatFormatting.GREEN))
				.append(Component.literal(" "))
				.append(button("[Abbrechen]", "/schreibstube abbrechen", "Schreiben beenden", ChatFormatting.RED));
		mc.player.displayClientMessage(msg, false);
	}

	/** Erkennungszeichen eines angefangenen Briefs: sein Inhalt. */
	private static String signature(ItemStack s) {
		if (!HeldLetter.isStarted(s)) return null;
		return String.join("|", HeldLetter.existingPages(s)) + "|" + s.getHoverName().getString();
	}

	private static Set<String> startedLetters(Minecraft mc) {
		Set<String> out = new HashSet<>();
		var inv = mc.player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			String sig = signature(inv.getItem(i));
			if (sig != null) out.add(sig);
		}
		return out;
	}

	/** Der Brief, den wir gerade schreiben: angefangen und neu oder verändert seit dem Start. */
	private static boolean isOurs(ItemStack s) {
		String sig = signature(s);
		return sig != null && s.getCount() == 1 && !before.contains(sig);
	}

	/** Sucht unseren Brief im Inventar und tauscht ihn in den gewählten Hotbar-Platz. */
	private static boolean fetchLetter(Minecraft mc) {
		if (mc.gameMode == null || mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) return false;
		var inv = mc.player.getInventory();
		int sel = inv.getSelectedSlot();
		int menu = mc.player.inventoryMenu.containerId;
		// Brief hängt am Mauszeiger (so landet er, wenn eine Nachfüll-Mod dazwischenklickt)
		if (isOurs(mc.player.inventoryMenu.getCarried()) && mc.player.containerMenu == mc.player.inventoryMenu) {
			mc.gameMode.handleInventoryMouseClick(menu, 36 + sel, 0, ClickType.PICKUP, mc.player);
			// was vorher in der Hand lag, in einen freien Platz legen
			if (!mc.player.inventoryMenu.getCarried().isEmpty()) {
				for (int i = 9; i < 45; i++) {
					if (i == 36 + sel) continue;
					if (mc.player.inventoryMenu.getSlot(i).getItem().isEmpty()) {
						mc.gameMode.handleInventoryMouseClick(menu, i, 0, ClickType.PICKUP, mc.player);
						break;
					}
				}
			}
			slot = sel;
			return true;
		}
		for (int i = 0; i < 36; i++) {
			if (i == sel || !isOurs(inv.getItem(i))) continue;
			int menuSlot = i < 9 ? 36 + i : i; // Hotbar liegt im Inventar-Menü auf 36–44
			mc.gameMode.handleInventoryMouseClick(menu, menuSlot, sel, ClickType.SWAP, mc.player);
			slot = sel;
			return true;
		}
		return false;
	}

	private static void progress(Minecraft mc) {
		if (mc.player == null) return;
		int w = 20, done = (int) Math.round(w * sent / (double) queue.size());
		mc.player.displayClientMessage(Component.literal("✒ Brief wird geschrieben  ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal("█".repeat(done)).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal("█".repeat(w - done)).withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal("  " + sent + "/" + queue.size()).withStyle(ChatFormatting.GRAY)), true);
	}

	private static void finished(Minecraft mc) {
		int n = queue.size();
		RefillGuard.restore();
		queue = new ArrayList<>();
		sent = 0;
		finishing = false;
		SchreibstubeClient.onLetterWritten();
		if (mc.player == null) return;
		mc.player.displayClientMessage(Component.literal("✒ Brief fertig geschrieben").withStyle(ChatFormatting.GREEN), true);
		mc.player.displayClientMessage(prefix()
				.append(Component.literal("Brief geschrieben (" + n + (n == 1 ? " Befehl). " : " Befehle). ")).withStyle(ChatFormatting.GRAY))
				.append(button("[Verschicken …]", "/schreibstube verschicken", "Empfänger auswählen", ChatFormatting.GREEN))
				.append(Component.literal(" "))
				.append(button("[Weiterschreiben]", "/schreibstube", "Schreibstube öffnen", ChatFormatting.GOLD)), false);
	}

	public static MutableComponent prefix() {
		return Component.literal("[Schreibstube] ").withStyle(ChatFormatting.GOLD);
	}

	public static MutableComponent button(String text, String command, String hover, ChatFormatting color) {
		return Component.literal(text).withStyle(s -> s.withColor(color)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}
}
