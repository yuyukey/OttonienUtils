package de.minentimer;

import com.mojang.blaze3d.platform.InputConstants;
import de.minentimer.gui.HudOverlay;
import de.minentimer.gui.MineListScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinenTimerClient implements ClientModInitializer {
	public static final String MOD_ID = "minentimer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static KeyMapping openMenuKey;
	public static KeyMapping quickMarkKey;
	public static KeyMapping toggleOverlayKey;
	public static KeyMapping nextAreaKey;
	public static KeyMapping prevAreaKey;

	private int notifyTicks;

	@Override
	public void onInitializeClient() {
		ModConfig.load();
		MineManager.get().load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		// Neue Namen ab 1.4.0, damit die neuen Standardtasten auch bei alten options.txt greifen.
		// GLFW_KEY_BACKSLASH ist auf deutschen Tastaturen die #-Taste.
		openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.minentimer.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_3, category));
		quickMarkKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.minentimer.mark", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, category));
		toggleOverlayKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.minentimer.overlay_toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_4, category));
		nextAreaKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.minentimer.next_area", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, category));
		prevAreaKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.minentimer.prev_area", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, category));

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "overlay"), HudOverlay::render);
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> autoMark(Minecraft.getInstance(), pos, state));
	}

	private void onTick(Minecraft mc) {
		while (openMenuKey.consumeClick()) {
			if (mc.screen == null) mc.setScreen(new MineListScreen(null));
		}
		while (quickMarkKey.consumeClick()) quickMark(mc);
		while (toggleOverlayKey.consumeClick()) {
			ModConfig cfg = ModConfig.get();
			cfg.overlayEnabled = !cfg.overlayEnabled;
			cfg.save();
			actionBar(mc, Component.literal("Minen-Overlay " + (cfg.overlayEnabled ? "an" : "aus")).withStyle(ChatFormatting.GOLD));
		}

		while (nextAreaKey.consumeClick()) switchArea(mc, 1);
		while (prevAreaKey.consumeClick()) switchArea(mc, -1);

		if (++notifyTicks >= 10) {
			notifyTicks = 0;
			MineManager.get().tickNotifications();
		}
		SyncManager.get().tick();
	}

	/** Startet den Timer der nächstgelegenen Mine. */
	private static void quickMark(Minecraft mc) {
		if (mc.player == null) return;
		ModConfig cfg = ModConfig.get();
		MineManager mgr = MineManager.get();
		Mine mine = mgr.nearest(mc.player.getX(), mc.player.getY(), mc.player.getZ(), cfg.quickMarkRadius);
		if (mine == null) {
			actionBar(mc, Component.literal("Keine Mine im Umkreis von " + cfg.quickMarkRadius + " Blöcken").withStyle(ChatFormatting.RED));
			return;
		}
		long now = System.currentTimeMillis();
		if (mgr.isRunning(mine, now)) {
			actionBar(mc, Component.literal(mine.name + " läuft bereits (noch " + TimeFormat.duration(mgr.remainingMs(mine, now)) + ")").withStyle(ChatFormatting.YELLOW));
			return;
		}
		mgr.start(mine);
		actionBar(mc, Component.literal("⛏ " + mine.name + " markiert – bereit um " + TimeFormat.clock(mgr.timer(mine).readyAt)).withStyle(ChatFormatting.GREEN));
		if (cfg.soundEnabled) Sounds.click();
	}

	/** Wechselt das Lehen, das Liste und Overlay zeigen. */
	public static void switchArea(Minecraft mc, int dir) {
		MineManager mgr = MineManager.get();
		if (mgr.areas().isEmpty()) {
			actionBar(mc, Component.literal("Noch keine Lehen – trag eins beim Bearbeiten einer Mine ein").withStyle(ChatFormatting.YELLOW));
			return;
		}
		String area = mgr.cycleArea(dir);
		int count = mgr.visibleMines(ModConfig.SortMode.LIST).size();
		actionBar(mc, Component.literal("Lehen: " + MineManager.areaLabel(area) + " (" + count + (count == 1 ? " Mine)" : " Minen)")).withStyle(ChatFormatting.GOLD));
	}

	/**
	 * Auto-Markieren: Wer einen Rohstoff abbaut, startet den Timer der nächsten Mine dieser Art im Umkreis
	 * von autoMarkMaxDistance (Standard 50 Blöcke).
	 * Liefert true, wenn ein Timer gestartet wurde.
	 */
	public static boolean autoMark(Minecraft mc, BlockPos pos, BlockState state) {
		ModConfig cfg = ModConfig.get();
		if (!cfg.autoMark) return false;
		ResourceType type = resourceOf(cfg, state);
		if (type == null) return false;
		MineManager mgr = MineManager.get();
		Mine mine = mgr.nearestOfType(type, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, Math.max(1, cfg.autoMarkMaxDistance));
		if (mine == null) return false;
		// Läuft der Timer noch länger, war das kein frisch gespawntes Erz. Kurz vor Ablauf war die Uhr nur etwas zu langsam.
		long remaining = mgr.remainingMs(mine, System.currentTimeMillis());
		boolean restart = remaining > 0;
		if (restart && remaining > Math.max(0, cfg.autoMarkRestartMinutes) * 60_000L) return false;
		mgr.start(mine);
		String what = restart ? " neu gestartet (Erz war schon da)" : " automatisch markiert";
		actionBar(mc, Component.literal("⛏ " + mine.name + what + " – bereit um " + TimeFormat.clock(mgr.timer(mine).readyAt)).withStyle(ChatFormatting.GREEN));
		if (cfg.soundEnabled) Sounds.click();
		return true;
	}

	private static ResourceType resourceOf(ModConfig cfg, BlockState state) {
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		String path = id.substring(id.indexOf(':') + 1);
		for (var e : cfg.autoMarkBlocks.entrySet()) {
			for (String b : e.getValue()) {
				if (b.equals(id) || b.equals(path)) return e.getKey();
			}
		}
		return null;
	}

	private static void actionBar(Minecraft mc, Component message) {
		if (mc.player != null) mc.player.displayClientMessage(message, true);
	}
}
