package de.aktivitaeten.test;

import de.aktivitaeten.AktivitaetenClient;
import de.aktivitaeten.data.ActivityData;
import de.aktivitaeten.gui.ActivityHud;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.List;

/** Testwelt: echte Daten laden, dann feste Testdaten für Screenshots der Liste. */
public class AktivitaetenClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		// Auswertung von player_gathering
		ActivityData.Snapshot parsed = ActivityData.parse(
				"[{\"id\":\"lehen_1\",\"name\":\"A\",\"x\":10,\"z\":20,\"player_gathering\":\"1\"},"
						+ "{\"id\":\"lehen_2\",\"name\":\"B\",\"x\":0,\"z\":0,\"player_gathering\":\"0\"},"
						+ "{\"id\":\"lehen_3\",\"name\":\"C\",\"x\":0,\"z\":0,\"player_gathering\":2}]",
				"[{\"region\":\"lehen_1\",\"name\":\"Gefolge A\",\"rank_name\":\"Vogtei\"}]");
		if (parsed.activities().size() != 2 || parsed.regionCount() != 3) throw new AssertionError("Auswertung: " + parsed);
		if (!parsed.activities().getFirst().owner().equals("Gefolge A")) throw new AssertionError("Gefolge fehlt: " + parsed);

		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("tp @a -159 140 2791 0 0");
			world.getClientWorld().waitForChunksRender();
			ctx.waitFor(mc -> AktivitaetenClient.isActive() && (ActivityData.get().loaded() || ActivityData.lastError() != null), 1200);
			ctx.runOnClient(mc -> {
				if (!ActivityData.get().loaded()) throw new AssertionError("Live-Daten nicht geladen: " + ActivityData.lastError());
				if (ActivityData.get().regionCount() < 60) throw new AssertionError("Zu wenige Lehen: " + ActivityData.get().regionCount());
				AktivitaetenClient.LOGGER.info("[Test] Live-Aktivitäten: {}", ActivityData.get().activities());
			});
			ctx.waitTicks(20);
			ctx.waitTicks(60);
			ctx.takeScreenshot("aktivitaeten-live");

			// Feste Testdaten: zwei kleine Aktivitäten, dann kommt eine sehr hohe neu dazu
			ctx.runOnClient(mc -> {
				ActivityData.set(ActivityData.Snapshot.EMPTY);
				ActivityData.set(new ActivityData.Snapshot(List.of(
						new ActivityData.Activity("lehen_6", "Merdorf", -159, 2791, 1, "Testgefolge", "Vogtei", ""),
						new ActivityData.Activity("lehen_14", "Weiherstein", -159, 4791, 1, "Geldern", "Kloster", "geldern_schwarz_golden")
				), 65, System.currentTimeMillis()));
				ActivityData.set(new ActivityData.Snapshot(List.of(
						new ActivityData.Activity("lehen_6", "Merdorf", -159, 2791, 1, "Testgefolge", "Vogtei", ""),
						new ActivityData.Activity("lehen_14", "Weiherstein", -159, 4791, 1, "Geldern", "Kloster", "geldern_schwarz_golden"),
						new ActivityData.Activity("lehen_25", "Laughof", 1341, 2791, 2, "Rosenwacht", "Rittergut", "rosenwacht")
				), 65, System.currentTimeMillis()));
				// sehr hohe Aktivität (rot) immer oben, danach nach Gefolge
				List<ActivityHud.Row> rows = ActivityHud.rows();
				if (rows.size() != 3 || !rows.get(0).activity().id().equals("lehen_25") || !rows.get(0).high()
						|| !rows.get(1).activity().id().equals("lehen_14") || rows.get(1).high())
					throw new AssertionError("Sortierung: " + rows);
				if (ActivityHud.color(rows.get(0).activity()) != ActivityHud.HIGH || ActivityHud.color(rows.get(1).activity()) != ActivityHud.LOW)
					throw new AssertionError("Farben falsch");
				if (ActivityData.startedAt("lehen_25") == null || ActivityData.startedAt("lehen_6") != null)
					throw new AssertionError("Beginn neuer Aktivitäten falsch gemerkt");
			});
			// Wappen laden im Hintergrund
			ctx.waitFor(mc -> de.aktivitaeten.data.Banners.texture("rosenwacht") != null
					&& de.aktivitaeten.data.Banners.texture("geldern_schwarz_golden") != null
					&& de.aktivitaeten.data.Banners.texture("") != null, 400);
			ctx.waitTicks(5);
			ctx.takeScreenshot("aktivitaeten-liste");

			// Liste bleibt beim offenen Chat sichtbar
			ctx.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false)));
			ctx.waitTicks(5);
			ctx.takeScreenshot("aktivitaeten-chat");
			ctx.runOnClient(mc -> mc.setScreen(null));
			ctx.waitTicks(2);

			ctx.runOnClient(mc -> de.aktivitaeten.Config.get().position = de.aktivitaeten.Config.Position.TOP_LEFT);
			ctx.waitTicks(2);
			ctx.takeScreenshot("aktivitaeten-oben-links");

			ctx.runOnClient(mc -> ActivityData.set(new ActivityData.Snapshot(List.of(), 65, System.currentTimeMillis())));
			ctx.waitTicks(2);
			ctx.takeScreenshot("aktivitaeten-leer");

			// Verschiebe-Bildschirm: Mausrad macht größer, Liste frei platziert
			ctx.runOnClient(mc -> mc.setScreen(new de.aktivitaeten.gui.OverlayMoveScreen(null)));
			ctx.waitTicks(5);
			ctx.takeScreenshot("aktivitaeten-verschieben");
			ctx.getInput().setCursorPos(10, 10);
			ctx.getInput().scroll(1);
			ctx.getInput().scroll(1);
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> {
				var c = de.aktivitaeten.Config.get();
				if (c.scale != 120) throw new AssertionError("Mausrad: Größe " + c.scale);
				if (c.position != de.aktivitaeten.Config.Position.CUSTOM) throw new AssertionError("Nicht frei verschoben: " + c.position);
				c.overlayX = 0.5f;
				c.overlayY = 0.1f;
			});
			ctx.waitTicks(2);
			ctx.takeScreenshot("aktivitaeten-verschieben-gross");
			ctx.runOnClient(mc -> mc.setScreen(null));
			ctx.waitTicks(2);
			ctx.takeScreenshot("aktivitaeten-frei");

			ctx.runOnClient(mc -> mc.setScreen(new de.aktivitaeten.gui.SettingsScreen(null)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("aktivitaeten-einstellungen");
			ctx.runOnClient(mc -> mc.setScreen(null));
			ctx.waitTicks(5);
		}
	}
}
