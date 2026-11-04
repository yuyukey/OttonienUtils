package de.aktivitaeten;

import com.mojang.blaze3d.platform.InputConstants;
import de.aktivitaeten.data.ActivityData;
import de.aktivitaeten.gui.ActivityHud;
import de.aktivitaeten.gui.SettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

public class AktivitaetenClient implements ClientModInitializer {
	public static final String MOD_ID = "aktivitaeten";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** Jede Minute neu abfragen, Aktivitäten ändern sich schneller als Besitzer. */
	private static final long REFRESH_MS = 60_000L;

	public static KeyMapping toggleKey;
	private static boolean active;
	private static long lastFetch;
	/** Einstellungen erst im nächsten Tick öffnen, sonst schließt der Chat sie gleich wieder. */
	private static boolean openSettings;
	private static boolean openMove;

	@Override
	public void onInitializeClient() {
		Config.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.aktivitaeten.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), ActivityHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(AktivitaetenClient::onTick);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(
				ClientCommandManager.literal("aktivitaeten")
						.executes(c -> list(c.getSource()))
						.then(ClientCommandManager.literal("einstellungen").executes(c -> {
							openSettings = true;
							return 1;
						}))
						.then(ClientCommandManager.literal("verschieben").executes(c -> {
							openMove = true;
							return 1;
						}))
						.then(ClientCommandManager.literal("chat")
								.then(ClientCommandManager.literal("an").executes(c -> setChat(c.getSource(), true)))
								.then(ClientCommandManager.literal("aus").executes(c -> setChat(c.getSource(), false))))
						.then(ClientCommandManager.literal("neuladen").executes(c -> reload(c.getSource())))
						.then(ClientCommandManager.literal("an").executes(c -> setHud(c.getSource(), true)))
						.then(ClientCommandManager.literal("aus").executes(c -> setHud(c.getSource(), false)))
		));
	}

	/** Auf einem Ottonien-Server (oder überall, wenn so eingestellt) und in einer Welt. */
	public static boolean isActive() {
		return active;
	}

	private static void onTick(Minecraft mc) {
		if (openSettings) {
			openSettings = false;
			mc.setScreen(new SettingsScreen(mc.screen));
		}
		if (openMove) {
			openMove = false;
			mc.setScreen(new de.aktivitaeten.gui.OverlayMoveScreen(null));
		}
		while (toggleKey.consumeClick()) {
			Config cfg = Config.get();
			cfg.hudEnabled = !cfg.hudEnabled;
			cfg.save();
			if (mc.player != null) {
				mc.player.displayClientMessage(Component.literal("Aktivitäten " + (cfg.hudEnabled ? "an" : "aus")).withStyle(ChatFormatting.GOLD), true);
			}
		}

		boolean nowActive = mc.level != null && (!Config.get().onlyOnOttonien || onOttonien(mc));
		if (nowActive && !active) lastFetch = 0; // beim Betreten sofort abfragen
		active = nowActive;
		if (!active) return;
		long now = System.currentTimeMillis();
		if (now - lastFetch > REFRESH_MS) {
			lastFetch = now;
			fetch();
		}
	}

	private static boolean onOttonien(Minecraft mc) {
		ServerData server = mc.getCurrentServer();
		return server != null && server.ip.toLowerCase(Locale.ROOT).contains("ottonien");
	}

	private static void fetch() {
		ActivityData.refresh((before, now) -> Minecraft.getInstance().execute(() -> announce(ActivityData.newSince(before, now))));
	}

	/** Meldet im Chat, in welchen Lehen gerade eine Aktivität begonnen hat. */
	private static void announce(List<ActivityData.Activity> started) {
		Minecraft mc = Minecraft.getInstance();
		if (!Config.get().notifyNew || mc.player == null || started.isEmpty()) return;
		for (ActivityData.Activity a : started) {
			mc.player.displayClientMessage(Component.literal("[Aktivitäten] ").withStyle(ChatFormatting.GOLD)
					.append(Component.literal(a.level() >= 2 ? "Sehr hohe Aktivität bei " : "Neue Aktivität bei ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(ActivityHud.label(a)).withColor(ActivityHud.color(a) & 0xFFFFFF))
					.append(Component.literal((a.owner().isEmpty() ? "" : " (" + a.name() + ")")
							+ String.format(Locale.ROOT, ", X %d / Z %d", a.x(), a.z())).withStyle(ChatFormatting.GRAY)), false);
		}
	}

	// ---------------------------------------------------------------- Befehle

	private static void say(FabricClientCommandSource src, String text, ChatFormatting color) {
		src.sendFeedback(Component.literal(text).withStyle(color));
	}

	private static int list(FabricClientCommandSource src) {
		Minecraft mc = Minecraft.getInstance();
		ActivityData.Snapshot snap = ActivityData.get();
		if (!snap.loaded()) {
			say(src, "Aktivitäten: noch keine Daten" + (ActivityData.lastError() != null ? " – Fehler: " + ActivityData.lastError() : "")
					+ (Config.get().onlyOnOttonien && !onOttonien(mc) ? " (nur auf Ottonien-Servern)" : ""), ChatFormatting.GOLD);
			return 1;
		}
		var rows = ActivityHud.rows();
		say(src, "Aktivitäten: " + (rows.isEmpty() ? "gerade nirgends etwas los" : rows.size() + " von " + snap.regionCount() + " Lehen"), ChatFormatting.GOLD);
		for (ActivityHud.Row r : rows) {
			ActivityData.Activity a = r.activity();
			src.sendFeedback(Component.literal(" • ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(ActivityHud.label(a)).withColor(ActivityHud.color(a) & 0xFFFFFF))
					.append(Component.literal((r.high() ? " (sehr hoch)" : "") + (a.owner().isEmpty() ? "" : " – " + a.name())
							+ String.format(Locale.ROOT, " (X %d / Z %d)", a.x(), a.z())).withStyle(ChatFormatting.GRAY)));
		}
		long ago = (System.currentTimeMillis() - snap.fetchedAt()) / 1000;
		say(src, "Stand vor " + ago + " s · Befehle: einstellungen, verschieben, chat an|aus, neuladen, an, aus", ChatFormatting.DARK_GRAY);
		return 1;
	}

	private static int reload(FabricClientCommandSource src) {
		lastFetch = System.currentTimeMillis();
		fetch();
		say(src, "Aktivitäten werden abgerufen …", ChatFormatting.GOLD);
		return 1;
	}

	private static int setChat(FabricClientCommandSource src, boolean on) {
		Config.get().notifyNew = on;
		Config.get().save();
		say(src, "Chat-Meldungen bei neuen Aktivitäten " + (on ? "an" : "aus"), ChatFormatting.GOLD);
		return 1;
	}

	private static int setHud(FabricClientCommandSource src, boolean on) {
		Config.get().hudEnabled = on;
		Config.get().save();
		say(src, "Aktivitäten-Liste " + (on ? "an" : "aus"), ChatFormatting.GOLD);
		return 1;
	}
}
