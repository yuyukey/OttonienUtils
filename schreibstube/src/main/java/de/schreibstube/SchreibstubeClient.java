package de.schreibstube;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.schreibstube.gui.LetterScreen;
import de.schreibstube.gui.RecipientScreen;
import de.schreibstube.gui.SettingsScreen;
import de.schreibstube.send.HeldLetter;
import de.schreibstube.send.LetterSender;
import de.schreibstube.text.RichText;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Supplier;

public class SchreibstubeClient implements ClientModInitializer {
	public static final String MOD_ID = "schreibstube";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** Hinweis des Servers beim Rechtsklick mit frischem Schreibzeug. */
	private static final String HINT = "Beschreibe den Brief mit /letter";
	/** Bestätigung des Servers nach jedem /letter. */
	private static final String ECHO = "Du hast den Brief bearbeitet";

	public static KeyMapping openKey;
	/** Bildschirm erst im nächsten Tick öffnen, sonst schließt der Chat ihn gleich wieder. */
	private static Supplier<Screen> pendingScreen;
	private static long openedAt;
	private static RichText writingDoc;

	@Override
	public void onInitializeClient() {
		Config.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.schreibstube.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

		ClientTickEvents.END_CLIENT_TICK.register(SchreibstubeClient::onTick);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(
				ClientCommandManager.literal("schreibstube")
						.executes(c -> open(() -> new LetterScreen(null)))
						.then(ClientCommandManager.literal("verschicken")
								.executes(c -> open(() -> new RecipientScreen(null, null)))
								.then(ClientCommandManager.argument("spieler", StringArgumentType.word())
										.executes(c -> open(() -> new RecipientScreen(null, StringArgumentType.getString(c, "spieler"))))))
						.then(ClientCommandManager.literal("entwuerfe").executes(c -> open(() -> new LetterScreen(null).openingDrafts())))
						.then(ClientCommandManager.literal("einstellungen").executes(c -> open(() -> new SettingsScreen(null))))
						.then(ClientCommandManager.literal("weiter").executes(c -> {
							LetterSender.resume();
							return 1;
						}))
						.then(ClientCommandManager.literal("abbrechen").executes(c -> {
							boolean any = LetterSender.busy() || LetterSender.hasPaused();
							LetterSender.cancel();
							c.getSource().sendFeedback(LetterSender.prefix().append(Component.literal(any
									? "Schreiben abgebrochen. Was schon geschrieben ist, bleibt im Brief." : "Es wird gerade nichts geschrieben.").withStyle(ChatFormatting.GRAY)));
							return 1;
						}))
		));

		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			String s = ChatFormatting.stripFormatting(message.getString());
			if (s == null) return true;
			if (s.contains(ECHO) && (LetterSender.busy() || LetterSender.expectsEcho()) && Config.get().hideServerEcho) return false;
			if (s.contains(HINT)) {
				Minecraft mc = Minecraft.getInstance();
				if (mc.screen instanceof LetterScreen || System.currentTimeMillis() - openedAt < 3000) return false;
				if (Config.get().openOnUse && !LetterSender.busy() && onOttonien(mc)) {
					open(() -> new LetterScreen(null));
					return false;
				}
			}
			return true;
		});

		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			Minecraft mc = Minecraft.getInstance();
			if (Config.get().openOnUse && !LetterSender.busy() && onOttonien(mc) && HeldLetter.isFresh(player.getMainHandItem())
					&& !(mc.screen instanceof LetterScreen)) {
				open(() -> new LetterScreen(null));
			}
			return InteractionResult.PASS;
		});

		// Im geöffneten Brief: weiterschreiben oder verschicken; einen erhaltenen Brief in den Editor holen
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof BookViewScreen) || client.player == null) return;
			ItemStack received = receivedLetter(client.player);
			if (received != null) {
				Screens.getButtons(screen).add(Button.builder(Component.literal("✒ Im Editor öffnen"), b -> HeldLetter.importable(received)
								.ifPresent(text -> client.setScreen(new LetterScreen(null).opening(text))))
						.tooltip(Tooltip.create(Component.literal("Text samt Farben in die Schreibstube übernehmen, zum Bearbeiten, Speichern oder Kopieren.")))
						.bounds(6, 6, 130, 20).build());
				return;
			}
			if (!HeldLetter.isStarted(client.player.getMainHandItem())) return;
			Screens.getButtons(screen).add(Button.builder(Component.literal("✒ Weiterschreiben"), b -> client.setScreen(new LetterScreen(null)))
					.bounds(6, 6, 110, 20).build());
			Screens.getButtons(screen).add(Button.builder(Component.literal("✉ Verschicken …"), b -> client.setScreen(new RecipientScreen(null, null)))
					.bounds(6, 30, 110, 20).build());
		});
	}

	/** Ein fertiger Brief (mit Empfänger „Zu …“) in einer der Hände, sonst null. */
	private static ItemStack receivedLetter(Player player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack s = player.getItemInHand(hand);
			if (HeldLetter.isLetter(s) && !HeldLetter.isStarted(s)) return s;
		}
		return null;
	}

	private static int open(Supplier<Screen> screen) {
		pendingScreen = screen;
		return 1;
	}

	private static void onTick(Minecraft mc) {
		if (pendingScreen != null) {
			Supplier<Screen> s = pendingScreen;
			pendingScreen = null;
			openedAt = System.currentTimeMillis();
			mc.setScreen(s.get());
		}
		while (openKey.consumeClick()) {
			if (mc.screen == null) open(() -> new LetterScreen(null));
		}
		LetterSender.tick(mc);
	}

	public static boolean onOttonien(Minecraft mc) {
		if (!Config.get().onlyOnOttonien) return true;
		ServerData server = mc.getCurrentServer();
		return server != null && server.ip.toLowerCase(Locale.ROOT).contains("ottonien");
	}

	/** Der Text, der gerade auf das Pergament geschrieben wird. */
	public static void startedWriting(RichText doc) {
		writingDoc = doc;
	}

	/** Brief fertig: Text archivieren, Entwurf leeren, auf Wunsch Empfänger fragen. */
	public static void onLetterWritten() {
		if (writingDoc != null && !writingDoc.isEmpty()) {
			try {
				Drafts.save("Geschrieben " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm")), writingDoc);
			} catch (IOException e) {
				LOGGER.warn("Konnte den geschriebenen Brief nicht archivieren", e);
			}
			// Nur leeren, wenn seither nichts Neues getippt wurde
			if (Drafts.loadCurrent().contentEquals(writingDoc)) Drafts.saveCurrent(new RichText());
		}
		writingDoc = null;
		if (Config.get().askRecipientAfterWrite && Minecraft.getInstance().screen == null) open(() -> new RecipientScreen(null, null));
	}
}
