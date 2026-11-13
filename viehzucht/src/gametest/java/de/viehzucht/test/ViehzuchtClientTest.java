package de.viehzucht.test;

import de.viehzucht.data.AnimalInfo;
import de.viehzucht.data.AnimalStore;
import de.viehzucht.data.AutoQuery;
import de.viehzucht.data.Species;
import de.viehzucht.data.Tracker;
import de.viehzucht.Config;
import de.viehzucht.gui.HudPositionScreen;
import de.viehzucht.gui.InfoHud;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.List;

/** Echte Server-Nachrichten aus der Analyse einspielen und das Overlay in allen Zuständen ansehen. */
public class ViehzuchtClientTest implements FabricClientGameTest {
	private static final String DASH = "--------------------------------";

	@Override
	public void runTest(ClientGameTestContext ctx) {
		checkParser();

		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("time set day");
			world.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			String name = "CustomName:{text:\"Rotvieh Rind\",color:\"gold\"}";
			world.getServer().runCommand("execute as @p at @s run summon cow ^ ^ ^3 {NoAI:1b,Tags:[\"kuh\"]," + name + "}");
			world.getServer().runCommand("execute as @p at @s run summon cow ^2 ^ ^3 {NoAI:1b,Tags:[\"bulle\"]," + name + "}");
			world.getServer().runCommand("execute as @p at @s run summon cow ^-2 ^ ^3 {NoAI:1b,Age:-20000,Tags:[\"kalb\"]," + name + "}");
			world.getServer().runCommand("execute as @p at @s run summon sheep ^4 ^ ^3 {NoAI:1b,Tags:[\"zaupel\"],CustomName:{text:\"Zaupel\",color:\"gold\"}}");
			world.getClientWorld().waitForChunksRender();

			// Noch nie angeklickt: Hinweis und Werte aus der Tabelle
			look(ctx, "kuh");
			ctx.takeScreenshot("viehzucht-1-unbekannt");

			// Kuh mit zu wenig Mast
			click(ctx, "kuh", List.of("- Rotvieh Rind", "⚥  Geschlecht: Kuh (Weiblich)", "🍽 Mast: 65/80",
					"⏲  Mastverlust: in 22m 3s", "🥛 Melkbar: Bereit", "❤ Paarungsbereit: Mast 65/80"));
			ctx.runOnClient(mc -> {
				AnimalInfo.Status st = InfoHud.status(mc.crosshairPickEntity, System.currentTimeMillis());
				if (st.state() != AnimalInfo.State.NEED_MAST) throw new AssertionError("Kuh: " + st);
				AnimalInfo info = AnimalStore.get(mc.crosshairPickEntity.getUUID());
				if (!"w".equals(info.gender) || info.mast != 65 || info.mastMax != 80) throw new AssertionError("Kuh-Werte falsch");
			});
			ctx.takeScreenshot("viehzucht-2-kuh-mast");

			// Bulle bereit
			click(ctx, "bulle", List.of("- Rotvieh Rind", "⚥  Geschlecht: Bulle (Männlich)", "🍽 Mast: 80/80",
					"⏲  Mastverlust: in 2h 10m", "🥛 Melkbar: Nur weiblich", "❤ Paarungsbereit: Bereit"));
			ctx.runOnClient(mc -> {
				AnimalInfo.Status st = InfoHud.status(mc.crosshairPickEntity, System.currentTimeMillis());
				if (st.state() != AnimalInfo.State.READY) throw new AssertionError("Bulle: " + st);
			});
			ctx.takeScreenshot("viehzucht-3-bulle-bereit");

			// Kalb wächst noch
			click(ctx, "kalb", List.of("- Rotvieh Rind", "⚥  Geschlecht: Bulle (Männlich)", "🍽 Mast: 0/80",
					"⏲  Mastverlust: sobald das Tier gefüttert wurde", "🥛 Melkbar: Nur weiblich", "❤ Paarungsbereit: wächst noch 1d 16h | Mast 0/80"));
			ctx.runOnClient(mc -> {
				AnimalInfo.Status st = InfoHud.status(mc.crosshairPickEntity, System.currentTimeMillis());
				if (st.state() != AnimalInfo.State.GROWING || !st.text().startsWith("wächst noch 1d 1")) throw new AssertionError("Kalb: " + st);
			});
			ctx.takeScreenshot("viehzucht-4-kalb");

			// Zaupel nach beobachteter Paarung: geschätzte Wartezeit
			click(ctx, "zaupel", List.of("- Zaupel", "⚥  Geschlecht: Schaf (Weiblich)", "🍽 Mast: 30/30",
					"⏲  Mastverlust: in 40m", "✂ Scherbar: Bereit", "❤ Paarungsbereit: Bereit"));
			ctx.runOnClient(mc -> {
				Entity e = mc.crosshairPickEntity;
				AnimalStore.get(e.getUUID()).bredAt = System.currentTimeMillis();
				AnimalInfo.Status st = InfoHud.status(e, System.currentTimeMillis());
				if (st.state() != AnimalInfo.State.WAIT || !st.estimated()) throw new AssertionError("Zaupel: " + st);
			});
			ctx.takeScreenshot("viehzucht-5-zaupel-wartet");

			// Scheren: Klick mit Schere, Server schert das Schaf, dann leuchtet es bis „Scherbar: Bereit“
			ctx.runOnClient(mc -> {
				Entity e = mc.crosshairPickEntity;
				if (de.viehzucht.data.Shearing.glows(e)) throw new AssertionError("Leuchtet schon vor dem Scheren");
				de.viehzucht.data.Shearing.onInteract(e, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHEARS), false);
			});
			world.getServer().runCommand("execute as @e[tag=zaupel] run data merge entity @s {Sheared:1b}");
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				Entity e = mc.crosshairPickEntity;
				if (!de.viehzucht.data.Shearing.glows(e) || !mc.shouldEntityAppearGlowing(e)) throw new AssertionError("Geschorenes Schaf leuchtet nicht");
				if (e.getTeamColor() != de.viehzucht.data.Shearing.COLOR) throw new AssertionError("Farbe: " + Integer.toHexString(e.getTeamColor()));
				long left = AnimalStore.get(e.getUUID()).shearReadyAt() - System.currentTimeMillis();
				if (left < AnimalInfo.SHEAR_MS - 60_000 || left > AnimalInfo.SHEAR_MS) throw new AssertionError("Wartezeit: " + left);
			});
			ctx.takeScreenshot("viehzucht-5b-zaupel-geschoren");
			// Neue Info mit genauer Zeit: leuchtet weiter; „Bereit“: aus
			click(ctx, "zaupel", List.of("- Zaupel", "⚥  Geschlecht: Schaf (Weiblich)", "🍽 Mast: 10/30", "✂ Scherbar: in 5h 59m"));
			ctx.runOnClient(mc -> {
				if (!de.viehzucht.data.Shearing.glows(mc.crosshairPickEntity)) throw new AssertionError("Leuchten nach neuer Info weg");
			});
			click(ctx, "zaupel", List.of("- Zaupel", "⚥  Geschlecht: Schaf (Weiblich)", "🍽 Mast: 30/30", "✂ Scherbar: Bereit"));
			ctx.runOnClient(mc -> {
				if (de.viehzucht.data.Shearing.glows(mc.crosshairPickEntity)) throw new AssertionError("Leuchtet trotz „Scherbar: Bereit“");
			});

			// Gepaarte Kuh leuchtet rosa, bis der Server keine Wartezeit mehr nennt
			look(ctx, "kuh");
			ctx.runOnClient(mc -> {
				Entity e = mc.crosshairPickEntity;
				if (de.viehzucht.data.Breeding.glows(e)) throw new AssertionError("Kuh leuchtet schon vor der Paarung");
				AnimalStore.get(e.getUUID()).bredAt = System.currentTimeMillis();
				if (!de.viehzucht.data.Breeding.glows(e) || !mc.shouldEntityAppearGlowing(e)) throw new AssertionError("Gepaarte Kuh leuchtet nicht");
				if (e.getTeamColor() != de.viehzucht.data.Breeding.COLOR) throw new AssertionError("Kuh-Farbe: " + Integer.toHexString(e.getTeamColor()));
			});
			ctx.takeScreenshot("viehzucht-5c-kuh-gepaart");
			click(ctx, "kuh", List.of("- Rotvieh Rind", "⚥  Geschlecht: Kuh (Weiblich)", "🍽 Mast: 0/80", "❤ Paarungsbereit: in 23h 59m | Mast 0/80"));
			ctx.runOnClient(mc -> {
				if (!de.viehzucht.data.Breeding.glows(mc.crosshairPickEntity)) throw new AssertionError("Kuh-Leuchten nach neuer Info weg");
			});
			click(ctx, "kuh", List.of("- Rotvieh Rind", "⚥  Geschlecht: Kuh (Weiblich)", "🍽 Mast: 10/80", "❤ Paarungsbereit: Mast 10/80"));
			ctx.runOnClient(mc -> {
				if (de.viehzucht.data.Breeding.glows(mc.crosshairPickEntity)) throw new AssertionError("Kuh leuchtet trotz abgelaufener Wartezeit");
			});

			// Automatische Abfrage: neues Rind anvisieren, ohne Antwort einmal mit Schleichen nachfassen, dann Ruhe
			world.getServer().runCommand("execute as @p at @s run summon cow ~ ~ ~-3 {NoAI:1b,Tags:[\"neu\"]," + name + "}");
			int[] sentBefore = new int[1];
			ctx.runOnClient(mc -> sentBefore[0] = AutoQuery.sentCount());
			look(ctx, "neu");
			ctx.waitFor(mc -> AutoQuery.sentCount() >= sentBefore[0] + 1, 100);
			ctx.takeScreenshot("viehzucht-6-frage-ab");
			// Nach der Abfrage räumt die Mod das Schleichen auf dem Server auf (Schleichen an, dann aus)
			ctx.waitFor(mc -> !AutoQuery.cleaningUp(), 20);
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> {
				var sp = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
				if (sp.isShiftKeyDown() || sp.getLastClientInput().shift()) throw new AssertionError("Server hält Spieler für schleichend");
				if (mc.player.isShiftKeyDown()) throw new AssertionError("Client schleicht");
			});
			ctx.waitFor(mc -> AutoQuery.sentCount() >= sentBefore[0] + 2, 120);
			ctx.waitTicks(80);
			ctx.runOnClient(mc -> {
				if (AutoQuery.sentCount() != sentBefore[0] + 2) throw new AssertionError("Zu viele Abfragen: " + (AutoQuery.sentCount() - sentBefore[0]));
				if (mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().isShiftKeyDown()) throw new AssertionError("Schleichen nicht gelöst");
			});

			// Tafel größer und verschoben
			ctx.runOnClient(mc -> {
				Config.get().hudScale = 1.5f;
				Config.get().offsetX = -60;
				Config.get().offsetY = -70;
			});
			look(ctx, "bulle");
			ctx.takeScreenshot("viehzucht-7-gross-verschoben");
			ctx.runOnClient(mc -> {
				int gw = mc.getWindow().getGuiScaledWidth(), gh = mc.getWindow().getGuiScaledHeight();
				int[] p = InfoHud.position(gw, gh, 100, 50);
				if (p[0] != gw / 2 + 14 - 60 || p[1] != gh / 2 - 14 - 70) throw new AssertionError("Position: " + p[0] + "," + p[1]);
				int[] edge = InfoHud.position(gw, gh, 100, 50);
				Config.get().offsetX = 5000;
				edge = InfoHud.position(gw, gh, 100, 50);
				if (edge[0] != gw - 2 - 100) throw new AssertionError("Rand: " + edge[0]);
				Config.get().offsetX = -60;
				mc.setScreen(new HudPositionScreen());
			});
			ctx.waitTicks(3);
			ctx.takeScreenshot("viehzucht-8-anpassen");
			ctx.runOnClient(mc -> {
				mc.setScreen(null);
				Config.get().hudScale = 1.0f;
				Config.get().offsetX = 0;
				Config.get().offsetY = 0;
			});

			// Auf dem eigenen Pferd: keine Tafel für das Reittier
			world.getServer().runCommand("execute as @p at @s run summon horse ~ ~ ~ {NoAI:1b,Tame:1b,Tags:[\"ross\"],CustomName:{text:\"Reitpferd\",color:\"gold\"},SaddleItem:{id:\"saddle\",count:1}}");
			world.getServer().runCommand("execute as @p at @s run ride @s mount @e[tag=ross,limit=1,sort=nearest]");
			ctx.waitFor(mc -> mc.player.getVehicle() != null, 60);
			ctx.runOnClient(mc -> {
				mc.crosshairPickEntity = mc.player.getVehicle();
				if (InfoHud.target(mc) != null) throw new AssertionError("Tafel für das eigene Reittier");
			});
			world.getServer().runCommand("execute as @p run ride @s dismount");
			ctx.waitFor(mc -> mc.player.getVehicle() == null, 60);

			// Fremde Nachrichten ohne Klick bleiben unberührt
			ctx.runOnClient(mc -> {
				int before = AnimalStore.size();
				mc.getChatListener().handleSystemMessage(Component.literal(DASH), false);
				mc.getChatListener().handleSystemMessage(Component.literal("⚥  Geschlecht: Kuh (Weiblich)"), false);
				mc.getChatListener().handleSystemMessage(Component.literal(DASH), false);
				if (AnimalStore.size() != before) throw new AssertionError("Nachricht ohne Klick wurde zugeordnet");
			});
		}
	}

	private static void look(ClientGameTestContext ctx, String tag) {
		ctx.waitFor(mc -> {
			for (Entity e : mc.level.entitiesForRendering()) {
				if (tagged(mc, e, tag)) mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, e.getBoundingBox().getCenter());
			}
			return mc.crosshairPickEntity != null && tagged(mc, mc.crosshairPickEntity, tag);
		}, 200);
		ctx.waitTicks(3);
	}

	/** Tags kennt nur der Server, deshalb in der Server-Welt nachsehen. */
	private static boolean tagged(Minecraft mc, Entity e, String tag) {
		var server = mc.getSingleplayerServer();
		if (server == null) return false;
		Entity se = server.overworld().getEntity(e.getUUID());
		return se != null && se.getTags().contains(tag);
	}

	private static void click(ClientGameTestContext ctx, String tag, List<String> lines) {
		look(ctx, tag);
		ctx.runOnClient(mc -> {
			Tracker.onInteract(mc.crosshairPickEntity, true);
			mc.getChatListener().handleSystemMessage(Component.literal(DASH), false);
			for (String l : lines) mc.getChatListener().handleSystemMessage(Component.literal(l), false);
			mc.getChatListener().handleSystemMessage(Component.literal(DASH), false);
			if (AnimalStore.get(mc.crosshairPickEntity.getUUID()) == null) throw new AssertionError("Nicht erfasst: " + tag);
		});
		ctx.waitTicks(3);
	}

	private static void checkParser() {
		if (AnimalInfo.parseDuration("in 22m 3s") != 22 * 60_000L + 3000) throw new AssertionError("Dauer 22m 3s");
		if (AnimalInfo.parseDuration("wächst noch 1d 16h | Mast 0/80") != 40 * 3_600_000L) throw new AssertionError("Dauer 1d 16h");
		if (AnimalInfo.parseDuration("Mast 0/80") != -1) throw new AssertionError("Mast ist keine Dauer");
		if (!AnimalInfo.formatDuration(3_600_000L * 25).equals("1d 1h")) throw new AssertionError("Format");
		if (Species.byName("Rotvieh-Rind") == null || Species.byName("Rotvieh Rind").breedMast() != 80) throw new AssertionError("Art");
		AnimalInfo horse = AnimalInfo.parse(List.of("- Reitpferd", "⚥  Geschlecht: Hengst (Männlich)", "💪 Leben: 25",
				"📏 Sprunghöhe: 1.47 Blöcke", "🍽 Mast: 0/100", "❤ Zuchtbereit: Mast 0/100"), 1000);
		if (horse == null || !"Reitpferd".equals(horse.name) || !"m".equals(horse.gender) || !"Hengst".equals(horse.genderWord)
				|| horse.mastMax != 100 || horse.line("Sprunghöhe") == null) throw new AssertionError("Pferd: " + horse);
		AnimalInfo.Status st = horse.breedStatus(2000, Species.byName("Reitpferd"), false);
		if (st.state() != AnimalInfo.State.NEED_MAST || !st.text().equals("braucht Mast 0/100")) throw new AssertionError("Pferd-Status: " + st);
	}
}
