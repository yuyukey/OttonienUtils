package de.lehenskarte;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import de.lehenskarte.data.LehenGeometry;
import de.lehenskarte.data.LehenState;
import de.lehenskarte.data.OttonienData;
import de.lehenskarte.gui.LehenHud;
import de.lehenskarte.gui.SettingsScreen;
import de.lehenskarte.xaero.XaeroBridge;
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

import java.util.Locale;

public class LehenskarteClient implements ClientModInitializer {
	public static final String MOD_ID = "lehenskarte";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** Alle 5 Minuten neue Daten holen. */
	private static final long REFRESH_MS = 5 * 60_000L;

	public static KeyMapping toggleKey;
	private long lastFetch;
	/** Stand, mit dem neue Daten für die Chat-Meldungen verglichen werden. */
	private static OttonienData.Snapshot lastKnown = OttonienData.Snapshot.EMPTY;
	/** Einstellungen erst im nächsten Tick öffnen, sonst schließt der Chat sie gleich wieder. */
	private static boolean openSettings;

	@Override
	public void onInitializeClient() {
		Config.load();
		LehenState.reloadGeometry();
		// letzten Stand sofort zeigen, die API wird danach abgefragt
		OttonienData.loadCache();
		LehenState.rebuild();
		lastKnown = OttonienData.get();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lehenskarte.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), LehenHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(
				ClientCommandManager.literal("lehenskarte")
						.executes(c -> status(c.getSource()))
						.then(ClientCommandManager.literal("einstellungen").executes(c -> {
							openSettings = true;
							return 1;
						}))
						.then(ClientCommandManager.literal("debug").executes(c -> debug(c.getSource())))
						.then(ClientCommandManager.literal("neuladen").executes(c -> reload(c.getSource())))
						.then(ClientCommandManager.literal("an").executes(c -> setOverlay(c.getSource(), true)))
						.then(ClientCommandManager.literal("aus").executes(c -> setOverlay(c.getSource(), false)))
						.then(ClientCommandManager.literal("verschieben")
								.then(ClientCommandManager.argument("x", IntegerArgumentType.integer(-2000, 2000))
										.then(ClientCommandManager.argument("z", IntegerArgumentType.integer(-2000, 2000))
												.executes(this::shift))))
						.then(ClientCommandManager.literal("zuruecksetzen").executes(c -> resetShift(c.getSource())))
		));
	}

	private void onTick(Minecraft mc) {
		if (openSettings) {
			openSettings = false;
			mc.setScreen(new SettingsScreen(mc.screen));
		}
		while (toggleKey.consumeClick()) {
			Config cfg = Config.get();
			cfg.overlayEnabled = !cfg.overlayEnabled;
			cfg.save();
			if (mc.player != null) {
				mc.player.displayClientMessage(Component.literal("Lehenskarte " + (cfg.overlayEnabled ? "an" : "aus")).withStyle(ChatFormatting.GOLD), true);
			}
		}

		boolean shouldBeActive = mc.level != null && Config.get().overlayEnabled && (!Config.get().onlyOnOttonien || onOttonien(mc));
		if (LehenState.setActive(shouldBeActive)) XaeroBridge.refresh();

		if (shouldBeActive) {
			long now = System.currentTimeMillis();
			if (now - lastFetch > REFRESH_MS) {
				lastFetch = now;
				fetch();
			}
		}
	}

	private static boolean onOttonien(Minecraft mc) {
		ServerData server = mc.getCurrentServer();
		return server != null && server.ip.toLowerCase(Locale.ROOT).contains("ottonien");
	}

	private static void fetch() {
		OttonienData.refresh(() -> Minecraft.getInstance().execute(() -> {
			LehenState.rebuild();
			XaeroBridge.refresh();
			announceChanges(lastKnown, OttonienData.get());
			lastKnown = OttonienData.get();
		}));
	}

	/** Meldet im Chat, welche Lehen den Besitzer gewechselt haben, frei geworden sind oder den Lehnsherrn gewechselt haben. */
	private static void announceChanges(OttonienData.Snapshot before, OttonienData.Snapshot now) {
		Minecraft mc = Minecraft.getInstance();
		if (!Config.get().notifyChanges || mc.player == null || before.regions().isEmpty() || now.regions().isEmpty()) return;
		java.util.List<Component> lines = new java.util.ArrayList<>();
		java.util.List<String> ids = new java.util.ArrayList<>(now.regions().keySet());
		ids.sort(java.util.Comparator.comparing(id -> now.region(id).name()));
		for (String id : ids) {
			OttonienData.Faction a = before.owner(id), b = now.owner(id);
			String lehen = now.region(id).name();
			String nameA = a != null ? a.name() : null, nameB = b != null ? b.name() : null;
			if (java.util.Objects.equals(nameA, nameB)) {
				if (a != null && b != null && !a.lordName().equals(b.lordName())) {
					lines.add(Component.literal(b.name() + (b.hasLord() ? " ist jetzt Vasall von " + b.lordName() : " ist jetzt unabhängig")
							+ (a.hasLord() ? " (vorher " + a.lordName() + ")" : "")).withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			if (b == null) lines.add(Component.literal(lehen + " ist jetzt frei (vorher " + nameA + ")").withStyle(ChatFormatting.GRAY));
			else lines.add(Component.literal(lehen + " gehört jetzt " + nameB + (nameA != null ? " (vorher " + nameA + ")" : "")).withStyle(ChatFormatting.GRAY));
		}
		if (lines.isEmpty()) return;
		mc.player.displayClientMessage(Component.literal("[Lehenskarte] Änderungen bei den Lehen:").withStyle(ChatFormatting.GOLD), false);
		int max = 8;
		for (int i = 0; i < Math.min(max, lines.size()); i++) mc.player.displayClientMessage(Component.literal(" • ").append(lines.get(i)), false);
		if (lines.size() > max) mc.player.displayClientMessage(Component.literal(" … und " + (lines.size() - max) + " weitere").withStyle(ChatFormatting.DARK_GRAY), false);
	}

	// ---------------------------------------------------------------- Befehle

	private static void say(FabricClientCommandSource src, String text, ChatFormatting color) {
		src.sendFeedback(Component.literal(text).withStyle(color));
	}

	private static int status(FabricClientCommandSource src) {
		Minecraft mc = Minecraft.getInstance();
		OttonienData.Snapshot data = OttonienData.get();
		say(src, "Lehenskarte: " + (LehenState.isActive() ? "aktiv" : "inaktiv")
				+ (Config.get().onlyOnOttonien && !onOttonien(mc) ? " (nur auf Ottonien-Servern)" : ""), ChatFormatting.GOLD);
		say(src, LehenState.geometry().shapes().size() + " Lehen-Grenzen (" + LehenState.geometry().source + "), "
				+ data.regions().size() + " Lehen und " + data.factionByUuid().size() + " Gefolge von der API"
				+ (OttonienData.lastError() != null ? " – Fehler: " + OttonienData.lastError() : ""), ChatFormatting.GRAY);
		say(src, "Befehle: einstellungen, debug, neuladen, an, aus, verschieben <x> <z>, zuruecksetzen", ChatFormatting.GRAY);
		return 1;
	}

	/** Für die Kalibrierung: Position, erkanntes Lehen und Abstand zur Grenze. */
	private static int debug(FabricClientCommandSource src) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return 0;
		double x = mc.player.getX(), z = mc.player.getZ();
		LehenGeometry geo = LehenState.geometry();
		OttonienData.Snapshot data = OttonienData.get();
		say(src, String.format(Locale.ROOT, "Position X %d / Z %d", (int) Math.floor(x), (int) Math.floor(z)), ChatFormatting.GOLD);
		LehenGeometry.Shape here = LehenHud.current(mc);
		if (here == null) {
			say(src, "Kein Lehen an dieser Stelle erkannt.", ChatFormatting.YELLOW);
		} else {
			OttonienData.Region r = data.region(here.id());
			OttonienData.Faction f = data.owner(here.id());
			say(src, "Erkannt: " + (r != null ? r.name() : "?") + " (" + here.id() + ")" + (f != null ? " – " + f.name() : ""), ChatFormatting.GREEN);
			say(src, String.format(Locale.ROOT, "Abstand zur Grenze: %.0f Blöcke", here.edgeDistance(x, z)), ChatFormatting.GRAY);
			if (r != null) {
				double d = Math.hypot(r.x() - x, r.z() - z);
				say(src, String.format(Locale.ROOT, "Lehen-Mittelpunkt laut API: X %d / Z %d (%.0f Blöcke entfernt)", r.x(), r.z(), d), ChatFormatting.GRAY);
			}
		}
		// Nächstes Nachbarlehen
		LehenGeometry.Shape nearest = null;
		double best = Double.MAX_VALUE;
		for (LehenGeometry.Shape s : geo.shapes()) {
			if (s == here) continue;
			double d = s.edgeDistance(x, z);
			if (d < best) {
				best = d;
				nearest = s;
			}
		}
		if (nearest != null) {
			OttonienData.Region r = data.region(nearest.id());
			say(src, String.format(Locale.ROOT, "Nächstes anderes Lehen: %s, Grenze %.0f Blöcke entfernt", r != null ? r.name() : nearest.id(), best), ChatFormatting.GRAY);
		}
		say(src, "Grenz-Verschiebung: X " + geo.offsetX + " / Z " + geo.offsetZ, ChatFormatting.DARK_GRAY);
		LOGGER.info("[Lehenskarte debug] x={} z={} lehen={} offset={},{}", (int) x, (int) z, here != null ? here.id() : "-", geo.offsetX, geo.offsetZ);
		return 1;
	}

	private static int reload(FabricClientCommandSource src) {
		LehenState.reloadGeometry();
		de.lehenskarte.data.NpcVillages.reload();
		fetch();
		XaeroBridge.refresh();
		say(src, "Lehenskarte: Grenzen neu geladen, Daten werden abgerufen …", ChatFormatting.GOLD);
		return 1;
	}

	private static int setOverlay(FabricClientCommandSource src, boolean on) {
		Config.get().overlayEnabled = on;
		Config.get().save();
		say(src, "Lehenskarte " + (on ? "an" : "aus"), ChatFormatting.GOLD);
		return 1;
	}

	private int shift(CommandContext<FabricClientCommandSource> c) {
		Config cfg = Config.get();
		cfg.offsetX += IntegerArgumentType.getInteger(c, "x");
		cfg.offsetZ += IntegerArgumentType.getInteger(c, "z");
		cfg.save();
		LehenState.reloadGeometry();
		XaeroBridge.refresh();
		say(c.getSource(), "Grenzen verschoben, gesamt jetzt X " + cfg.offsetX + " / Z " + cfg.offsetZ, ChatFormatting.GOLD);
		return 1;
	}

	private static int resetShift(FabricClientCommandSource src) {
		Config cfg = Config.get();
		cfg.offsetX = 0;
		cfg.offsetZ = 0;
		cfg.save();
		LehenState.reloadGeometry();
		XaeroBridge.refresh();
		say(src, "Grenz-Verschiebung zurückgesetzt", ChatFormatting.GOLD);
		return 1;
	}
}
