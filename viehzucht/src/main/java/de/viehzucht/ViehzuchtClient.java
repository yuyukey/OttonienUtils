package de.viehzucht;

import com.mojang.blaze3d.platform.InputConstants;
import de.viehzucht.data.AnimalStore;
import de.viehzucht.data.AutoQuery;
import de.viehzucht.data.Shearing;
import de.viehzucht.data.Tracker;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import de.viehzucht.gui.HudPositionScreen;
import de.viehzucht.gui.InfoHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;

public class ViehzuchtClient implements ClientModInitializer {
	public static final String MOD_ID = "viehzucht";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static KeyMapping toggleKey;
	private static boolean active;
	private static boolean openPositionScreen;

	@Override
	public void onInitializeClient() {
		Config.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.viehzucht.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

		ClientTickEvents.END_CLIENT_TICK.register(ViehzuchtClient::onTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			AnimalStore.save();
			AutoQuery.reset();
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide()) AutoQuery.onBlockInteraction();
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && active) {
				Tracker.onInteract(entity, player.isShiftKeyDown());
				Shearing.onInteract(entity, player.getItemInHand(hand), player.isShiftKeyDown());
				Analysis.onInteract(entity, player, hand.name());
			}
			return InteractionResult.PASS;
		});
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !active || Tracker.onMessage(message.getString(), overlay));
		ClientReceiveMessageEvents.GAME.register(Analysis::onMessage);
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> Analysis.onMessage(message, false));
		ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (active) Tracker.onEntityLoad(entity);
		});
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "info"), InfoHud::render);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(
				ClientCommandManager.literal("viehzucht")
						.executes(c -> help(c.getSource()))
						.then(ClientCommandManager.literal("an").executes(c -> setOverlay(c.getSource(), true)))
						.then(ClientCommandManager.literal("aus").executes(c -> setOverlay(c.getSource(), false)))
						.then(ClientCommandManager.literal("chat")
								.then(ClientCommandManager.literal("an").executes(c -> setHideChat(c.getSource(), false)))
								.then(ClientCommandManager.literal("aus").executes(c -> setHideChat(c.getSource(), true))))
						.then(ClientCommandManager.literal("auto")
								.then(ClientCommandManager.literal("an").executes(c -> setAuto(c.getSource(), true)))
								.then(ClientCommandManager.literal("aus").executes(c -> setAuto(c.getSource(), false))))
						.then(ClientCommandManager.literal("leuchten")
								.then(ClientCommandManager.literal("an").executes(c -> setGlow(c.getSource(), true)))
								.then(ClientCommandManager.literal("aus").executes(c -> setGlow(c.getSource(), false))))
						.then(ClientCommandManager.literal("anpassen").executes(c -> {
							openPositionScreen = true;
							return 1;
						}))
						.then(ClientCommandManager.literal("groesse")
								.then(ClientCommandManager.argument("prozent", IntegerArgumentType.integer(50, 200)).executes(c -> {
									Config.get().hudScale = IntegerArgumentType.getInteger(c, "prozent") / 100f;
									Config.get().save();
									say(c.getSource(), "Tafel auf " + IntegerArgumentType.getInteger(c, "prozent") + " %");
									return 1;
								})))
						.then(ClientCommandManager.literal("verschieben")
								.then(ClientCommandManager.argument("x", IntegerArgumentType.integer(-2000, 2000))
										.then(ClientCommandManager.argument("y", IntegerArgumentType.integer(-2000, 2000)).executes(c -> {
											Config.get().offsetX += IntegerArgumentType.getInteger(c, "x");
											Config.get().offsetY += IntegerArgumentType.getInteger(c, "y");
											Config.get().save();
											say(c.getSource(), "Tafel verschoben, gesamt jetzt X " + Config.get().offsetX + " / Y " + Config.get().offsetY);
											return 1;
										}))))
						.then(ClientCommandManager.literal("zuruecksetzen").executes(c -> {
							Config.get().offsetX = 0;
							Config.get().offsetY = 0;
							Config.get().hudScale = 1.0f;
							Config.get().save();
							say(c.getSource(), "Tafel wieder neben dem Fadenkreuz in normaler Größe");
							return 1;
						}))
						.then(ClientCommandManager.literal("vergessen").executes(c -> forget(c.getSource())))
						.then(ClientCommandManager.literal("analyse")
								.executes(c -> analysisInfo(c.getSource()))
								.then(ClientCommandManager.literal("an").executes(c -> setAnalysis(c.getSource(), true)))
								.then(ClientCommandManager.literal("aus").executes(c -> setAnalysis(c.getSource(), false)))
								.then(ClientCommandManager.literal("leeren").executes(c -> clearAnalysis(c.getSource()))))));
	}

	/** Auf einem Ottonien-Server (oder überall, wenn so eingestellt) und in einer Welt. */
	public static boolean isActive() {
		return active;
	}

	private static void onTick(Minecraft mc) {
		// Erst im nächsten Tick öffnen, sonst schließt der Chat das Fenster gleich wieder
		if (openPositionScreen) {
			openPositionScreen = false;
			mc.setScreen(new HudPositionScreen());
		}
		while (toggleKey.consumeClick()) {
			Config.get().overlayEnabled = !Config.get().overlayEnabled;
			Config.get().save();
			if (mc.player != null) mc.player.displayClientMessage(Component.literal("Viehzucht-Overlay " + (Config.get().overlayEnabled ? "an" : "aus")).withStyle(ChatFormatting.GOLD), true);
		}
		active = mc.level != null && mc.player != null && (!Config.get().onlyOnOttonien || onOttonien(mc));
		if (!active) return;
		ServerData server = mc.getCurrentServer();
		AnimalStore.switchTo(server == null ? "einzelspieler" : server.ip);
		AnimalStore.tick();
		if (Tracker.isAnimal(mc.crosshairPickEntity)) Tracker.seen(mc.crosshairPickEntity);
		AutoQuery.tick(mc);
		Shearing.tick();
		Analysis.onLook(mc.crosshairPickEntity);
	}

	private static boolean onOttonien(Minecraft mc) {
		ServerData server = mc.getCurrentServer();
		return server != null && server.ip.toLowerCase(Locale.ROOT).contains("ottonien");
	}

	private static void say(FabricClientCommandSource src, String text) {
		src.sendFeedback(Component.literal(text).withStyle(ChatFormatting.GOLD));
	}

	private static int help(FabricClientCommandSource src) {
		say(src, "Viehzucht: " + AnimalStore.size() + " Tiere gemerkt, Overlay " + (Config.get().overlayEnabled ? "an" : "aus")
				+ (Config.get().onlyOnOttonien && !active ? " (nur auf Ottonien)" : ""));
		src.sendFeedback(Component.literal("Tier kurz anvisieren (oder Shift + Rechtsklick): die Mod holt die Infos, merkt sie sich und zählt die Zeiten weiter.").withStyle(ChatFormatting.GRAY));
		src.sendFeedback(Component.literal("/viehzucht an|aus · anpassen · groesse <%> · verschieben <x> <y> · zuruecksetzen · auto an|aus · chat an|aus · leuchten an|aus · vergessen · analyse").withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static int setOverlay(FabricClientCommandSource src, boolean on) {
		Config.get().overlayEnabled = on;
		Config.get().save();
		say(src, "Viehzucht-Overlay " + (on ? "an" : "aus"));
		return 1;
	}

	private static int setHideChat(FabricClientCommandSource src, boolean hide) {
		Config.get().hideChatInfo = hide;
		Config.get().save();
		say(src, hide ? "Tier-Infos des Servers werden im Chat ausgeblendet (stehen im Overlay)." : "Tier-Infos des Servers erscheinen wieder im Chat.");
		return 1;
	}

	private static int setAuto(FabricClientCommandSource src, boolean on) {
		Config.get().autoQuery = on;
		Config.get().save();
		say(src, on ? "Tier-Infos werden beim Anvisieren automatisch abgefragt." : "Automatische Abfrage aus, Infos nur noch per Shift + Rechtsklick.");
		return 1;
	}

	private static int setGlow(FabricClientCommandSource src, boolean on) {
		Config.get().glowSheared = on;
		Config.get().glowBred = on;
		Config.get().save();
		say(src, on ? "Selbst geschorene Schafe und gepaarte Kühe leuchten, bis sie wieder können." : "Geschorene Schafe und gepaarte Kühe leuchten nicht mehr.");
		return 1;
	}

	private static int forget(FabricClientCommandSource src) {
		AnimalStore.clear();
		say(src, "Alle gemerkten Tiere auf diesem Server vergessen.");
		return 1;
	}

	private static int setAnalysis(FabricClientCommandSource src, boolean on) {
		Config.get().analysis = on;
		Config.get().save();
		say(src, "Viehzucht-Analyse " + (on ? "an, schreibt nach " + Analysis.file().getFileName() : "aus"));
		return 1;
	}

	private static int analysisInfo(FabricClientCommandSource src) {
		src.sendFeedback(Component.literal("Viehzucht-Analyse " + (Config.get().analysis ? "an" : "aus") + ", " + Analysis.entries() + " Einträge in dieser Sitzung. ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal("[Datei öffnen]").withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
						.withClickEvent(new ClickEvent.OpenFile(Analysis.file())))));
		return 1;
	}

	private static int clearAnalysis(FabricClientCommandSource src) {
		try {
			Analysis.clear();
			say(src, "Viehzucht-Analyse geleert.");
		} catch (IOException ex) {
			src.sendError(Component.literal("Konnte die Datei nicht löschen: " + ex.getMessage()));
		}
		return 1;
	}
}
