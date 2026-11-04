package de.minentimer.test;

import de.minentimer.Mine;
import de.minentimer.MineManager;
import de.minentimer.MineSize;
import de.minentimer.MinenTimerClient;
import de.minentimer.ModConfig;
import de.minentimer.ResourceType;
import de.minentimer.SyncManager;
import de.minentimer.TimerEntry;
import de.minentimer.gui.MineEditScreen;
import de.minentimer.gui.MineListScreen;
import de.minentimer.gui.OverlayMoveScreen;
import de.minentimer.gui.SettingsScreen;
import de.minentimer.gui.SyncScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;


/** Startet eine Testwelt, bedient Mod-Funktionen und macht Screenshots. */
public class MinenTimerClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			world.getClientWorld().waitForChunksRender();

			ctx.runOnClient(mc -> {
				MineManager mgr = MineManager.get();
				// Eine eigene große Kohlemine direkt am Spieler
				Mine m = mgr.createMine("Meine Kohlemine", mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
				m.size = MineSize.MINE;
				m.type = ResourceType.KOHLE;
				mgr.saveMine(m);
				for (Mine mine : mgr.mines()) {
					if (mine.id.equals("kohle_vorkommen_1")) {
						mgr.start(mine);
						mgr.adjust(mine, -117 * 60_000L);
					}
					if (mine.id.equals("ton_vorkommen")) mgr.start(mine);
				}
			});

			ctx.getInput().pressKey(MinenTimerClient.quickMarkKey);
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				MineManager mgr = MineManager.get();
				Mine kohlemine = mgr.mines().stream().filter(x -> x.name.equals("Meine Kohlemine")).findFirst().orElseThrow();
				long remaining = mgr.remainingMs(kohlemine, System.currentTimeMillis());
				if (remaining < 239 * 60_000L) throw new AssertionError("Markier-Taste hat Meine Kohlemine nicht mit 4 h gestartet: " + remaining);
				// Alte Salz-Sorten werden zu Salz
				Mine salz = new com.google.gson.Gson().fromJson("{\"id\":\"s\",\"type\":\"SALZ_LAPIS\",\"size\":\"MINE\"}", Mine.class);
				salz.normalize();
				if (salz.type != de.minentimer.ResourceType.SALZ || salz.respawnMinutes() != 240) throw new AssertionError("Salz-Übernahme: " + salz.type);
				// Altes Format (lehen + Rohstoffliste) wird korrekt übernommen
				Mine alt = new com.google.gson.Gson().fromJson("{\"id\":\"alt\",\"lehen\":true,\"resources\":[\"KOHLE\"]}", Mine.class);
				alt.normalize();
				if (alt.size != MineSize.MINE || alt.type != ResourceType.KOHLE || alt.respawnMinutes() != 240) throw new AssertionError("Migration falsch: " + alt.size + " " + alt.type);
			});
			ctx.takeScreenshot("minentimer-overlay");

			ctx.runOnClient(mc -> {
				ModConfig.get().overlayPosition = ModConfig.OverlayPosition.TOP_RIGHT;
				ModConfig.get().overlayShowDistance = true;
			});
			ctx.waitTicks(2);
			ctx.takeScreenshot("minentimer-overlay-rechts");

			// „Nicht im Cooldown“ blendet laufende Timer aus, alte Einstellung „COOLDOWN“ wird dazu
			ctx.runOnClient(mc -> ModConfig.get().overlayMode = ModConfig.OverlayMode.READY);
			ctx.waitTicks(2);
			ctx.takeScreenshot("minentimer-overlay-bereit");
			ctx.runOnClient(mc -> {
				MineManager mgr = MineManager.get();
				long now = System.currentTimeMillis();
				java.util.List<String> shown = de.minentimer.gui.HudOverlay.visibleNames();
				for (Mine m : mgr.mines()) {
					if (mgr.isRunning(m, now) == shown.contains(m.name)) throw new AssertionError("Nicht im Cooldown falsch für " + m.name + ": " + shown);
				}
				ModConfig old = new com.google.gson.Gson().fromJson("{\"overlayMode\":\"COOLDOWN\"}", ModConfig.class);
				if (old.overlayMode != ModConfig.OverlayMode.READY) throw new AssertionError("Alte Zeigen-Einstellung: " + old.overlayMode);
				ModConfig.get().overlayMode = ModConfig.OverlayMode.ALL;
			});

			// Neue Installationen bekommen einen eigenen Zufalls-Code statt „standard“
			ctx.runOnClient(mc -> {
				String a = new ModConfig().syncGroup, b = new ModConfig().syncGroup;
				if (!a.matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}") || a.equals(b)) throw new AssertionError("Zufalls-Code: " + a + " / " + b);
				ModConfig keep = new ModConfig();
				keep.syncGroup = "Bambus";
				keep.useBuiltInSync();
				if (!"Bambus".equals(keep.syncGroup)) throw new AssertionError("Standard-Server hat den Gruppen-Code verloren");
			});
			// Auto-Markieren: Abbau des passenden Rohstoffs nahe einer Mine startet ihren Timer
			ctx.runOnClient(mc -> {
				MineManager mgr = MineManager.get();
				ModConfig cfg = ModConfig.get();
				var pos = mc.player.blockPosition();
				Mine eisen = mgr.createMine("Auto-Eisen", pos.getX(), pos.getY(), pos.getZ());
				eisen.type = ResourceType.EISEN;
				mgr.saveMine(eisen);
				var iron = net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState();
				if (!new ModConfig().autoMark) throw new AssertionError("Auto-Markieren ist nicht standardmäßig an");
				cfg.autoMark = false;
				if (MinenTimerClient.autoMark(mc, pos, iron)) throw new AssertionError("Auto-Markieren lief, obwohl aus");
				cfg.autoMark = true;
				if (MinenTimerClient.autoMark(mc, pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState())) throw new AssertionError("Stein hat markiert");
				long before = System.currentTimeMillis();
				if (!MinenTimerClient.autoMark(mc, pos, iron) || !mgr.isRunning(eisen, System.currentTimeMillis())) throw new AssertionError("Eisenerz hat nicht markiert");
				// Respawn-Zeit plus 3 Minuten Abbauzeit
				long expected = before + (eisen.respawnMinutes() + 3) * 60_000L;
				if (Math.abs(mgr.timer(eisen).readyAt - expected) > 5_000) throw new AssertionError("Abbauzeit nicht draufgerechnet: " + (mgr.timer(eisen).readyAt - before) / 60_000 + " Min");
				if (MinenTimerClient.autoMark(mc, pos, iron)) throw new AssertionError("Laufender Timer wurde neu gestartet");
				mgr.deleteMine(eisen);
				// Bis 50 Blöcke zählt die nächste Mine dieser Art (hier Eisen Vorkommen 2 bei 622/84/1423)
				Mine fern = mgr.mines().stream().filter(m -> m.id.equals("eisen_vorkommen_2")).findFirst().orElseThrow();
				mgr.reset(fern);
				if (!MinenTimerClient.autoMark(mc, new net.minecraft.core.BlockPos(630, 80, 1430), iron) || !mgr.isRunning(fern, System.currentTimeMillis())) throw new AssertionError("Nächste ferne Mine wurde nicht markiert");
				mgr.reset(fern);
				if (MinenTimerClient.autoMark(mc, new net.minecraft.core.BlockPos(622 + 60, 84, 1423), iron)) throw new AssertionError("Mine 60 Blöcke weg wurde markiert");
				// Torf ist auf Ottonien Tuff
				Mine torf = mgr.createMine("Auto-Torf", pos.getX(), pos.getY(), pos.getZ());
				torf.type = ResourceType.TORF;
				mgr.saveMine(torf);
				if (!MinenTimerClient.autoMark(mc, pos, net.minecraft.world.level.block.Blocks.TUFF.defaultBlockState())) throw new AssertionError("Tuff hat Torf nicht markiert");
				mgr.deleteMine(torf);
				ModConfig old = new com.google.gson.Gson().fromJson("{\"autoMarkBlocks\":{\"TORF\":[\"minecraft:mud\",\"minecraft:muddy_mangrove_roots\"]}}", ModConfig.class);
				try {
					var m = ModConfig.class.getDeclaredMethod("sanitize");
					m.setAccessible(true);
					m.invoke(old);
				} catch (Exception e) {
					throw new AssertionError(e);
				}
				if (!java.util.List.of("minecraft:tuff").equals(old.autoMarkBlocks.get(ResourceType.TORF))) throw new AssertionError("Alte Torf-Liste nicht ersetzt: " + old.autoMarkBlocks);
			});
			// Echter Abbau im Spiel: Ton-Block neben dem Spieler abbauen
			ctx.runOnClient(mc -> {
				MineManager mgr = MineManager.get();
				var pos = mc.player.blockPosition().offset(2, 0, 0);
				Mine ton = mgr.createMine("Auto-Ton", pos.getX(), pos.getY(), pos.getZ());
				ton.type = ResourceType.TON;
				mgr.saveMine(ton);
				mc.level.setBlock(pos, net.minecraft.world.level.block.Blocks.CLAY.defaultBlockState(), 3);
				mc.gameMode.destroyBlock(pos);
				if (!mgr.isRunning(ton, System.currentTimeMillis())) throw new AssertionError("Abbau im Spiel hat Ton nicht markiert");
				mgr.deleteMine(ton);
				ModConfig.get().autoMark = false;
			});
			// Standardtasten: # markieren, Numpad 3 Menü, Numpad 4 Overlay
			if (MinenTimerClient.quickMarkKey.getDefaultKey().getValue() != org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSLASH
					|| MinenTimerClient.openMenuKey.getDefaultKey().getValue() != org.lwjgl.glfw.GLFW.GLFW_KEY_KP_3
					|| MinenTimerClient.toggleOverlayKey.getDefaultKey().getValue() != org.lwjgl.glfw.GLFW.GLFW_KEY_KP_4) {
				throw new AssertionError("Standardtasten falsch");
			}
			// Overlay-Taste schaltet das Overlay aus und wieder an
			ctx.getInput().pressKey(MinenTimerClient.toggleOverlayKey);
			ctx.waitTicks(2);
			if (ctx.computeOnClient(mc -> ModConfig.get().overlayEnabled)) throw new AssertionError("Overlay-Taste schaltet nicht aus");
			ctx.getInput().pressKey(MinenTimerClient.toggleOverlayKey);
			ctx.waitTicks(2);
			if (!ctx.computeOnClient(mc -> ModConfig.get().overlayEnabled)) throw new AssertionError("Overlay-Taste schaltet nicht an");

			// Overlay mit der Maus in die Bildmitte ziehen
			ctx.setScreen(() -> new OverlayMoveScreen(null));
			ctx.waitTicks(2);
			double[] grab = ctx.computeOnClient(mc -> {
				var b = ((OverlayMoveScreen) mc.screen).box();
				double s = mc.getWindow().getGuiScale();
				return new double[] {(b.x() + 10) * s, (b.y() + 5) * s, s};
			});
			ctx.getInput().setCursorPos(grab[0], grab[1]);
			ctx.getInput().holdMouse(0);
			ctx.waitTicks(1);
			ctx.getInput().setCursorPos(grab[0] + 150 * grab[2], grab[1] + 60 * grab[2]);
			ctx.waitTicks(1);
			ctx.getInput().releaseMouse(0);
			ctx.waitTicks(2);
			ctx.takeScreenshot("minentimer-verschieben");
			ctx.runOnClient(mc -> {
				ModConfig cfg = ModConfig.get();
				if (cfg.overlayPosition != ModConfig.OverlayPosition.CUSTOM || cfg.overlayX <= 0.05f || cfg.overlayY <= 0.05f) {
					throw new AssertionError("Overlay nicht verschoben: " + cfg.overlayPosition + " " + cfg.overlayX + "/" + cfg.overlayY);
				}
			});
			ctx.setScreen(() -> null);
			ctx.waitTicks(2);
			ctx.takeScreenshot("minentimer-overlay-verschoben");

			ctx.getInput().pressKey(MinenTimerClient.openMenuKey);
			ctx.waitForScreen(MineListScreen.class);
			ctx.takeScreenshot("minentimer-menue");

			// Zweite Mine per Klick auf ▲ nach oben schieben, auch aus einer anderen Sortierung heraus
			String[] before = ctx.computeOnClient(mc -> {
				ModConfig.get().sortMode = ModConfig.SortMode.STATUS;
				var list = MineManager.get().sortedMines(ModConfig.SortMode.STATUS);
				return new String[] {list.get(0).id, list.get(1).id};
			});
			ctx.setScreen(() -> new MineListScreen(null));
			ctx.waitTicks(2);
			double[] arrow = ctx.computeOnClient(mc -> {
				int w = mc.screen.width, panelW = Math.min(360, w - 20), right = (w + panelW) / 2;
				double s = mc.getWindow().getGuiScale();
				return new double[] {(right - 16 - 12 - 5 + 6) * s, (34 + 24 + 4) * s};
			});
			ctx.getInput().setCursorPos(arrow[0], arrow[1]);
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> {
				ModConfig cfg = ModConfig.get();
				var list = MineManager.get().sortedMines(cfg.sortMode);
				if (cfg.sortMode != ModConfig.SortMode.LIST || !list.get(0).id.equals(before[1]) || !list.get(1).id.equals(before[0])) {
					throw new AssertionError("Umsortieren fehlgeschlagen: " + cfg.sortMode + " " + list.get(0).id + "," + list.get(1).id);
				}
				// ▼ auf der letzten Mine geht nicht weiter
				if (MineManager.get().move(list, list.size() - 1, 1)) throw new AssertionError("Letzte Mine ließ sich nach unten schieben");
			});
			ctx.takeScreenshot("minentimer-umsortiert");

			// Lehen: zwei Minen nach Sturmhöhe, eine nach Rosenwacht, dann mit der Taste durchschalten
			ctx.setScreen(() -> null);
			ctx.runOnClient(mc -> {
				MineManager mgr = MineManager.get();
				for (Mine m : mgr.mines()) {
					String area = switch (m.id) {
						case "eisen_vorkommen_1", "eisen_vorkommen_2" -> "Sturmhöhe";
						case "ton_vorkommen" -> "Rosenwacht";
						default -> "";
					};
					if (!area.isEmpty()) {
						Mine c = m.copy();
						c.area = area;
						mgr.saveMine(c);
					}
				}
				ModConfig.get().activeArea = "";
				ModConfig.get().overlayMode = ModConfig.OverlayMode.ALL;
			});
			ctx.getInput().pressKey(MinenTimerClient.nextAreaKey);
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> {
				if (!"Rosenwacht".equals(MineManager.get().activeArea())) throw new AssertionError("Erstes Lehen: " + MineManager.get().activeArea());
			});
			ctx.getInput().pressKey(MinenTimerClient.nextAreaKey);
			ctx.waitTicks(2);
			ctx.takeScreenshot("minentimer-lehen-overlay");
			ctx.runOnClient(mc -> {
				MineManager mgr = MineManager.get();
				var shown = de.minentimer.gui.HudOverlay.visibleNames();
				if (!"Sturmhöhe".equals(mgr.activeArea()) || shown.size() != 2 || !shown.contains("Eisen Vorkommen 1") || !shown.contains("Eisen Vorkommen 2")) {
					throw new AssertionError("Lehen-Filter: " + mgr.activeArea() + " " + shown);
				}
			});
			ctx.getInput().pressKey(MinenTimerClient.prevAreaKey);
			ctx.getInput().pressKey(MinenTimerClient.prevAreaKey);
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> {
				if (!MineManager.get().activeArea().isEmpty()) throw new AssertionError("Zurück zu allen: " + MineManager.get().activeArea());
				ModConfig.get().activeArea = "Sturmhöhe";
			});
			ctx.setScreen(() -> new MineListScreen(null));
			ctx.waitTicks(2);
			ctx.takeScreenshot("minentimer-lehen-menue");
			ctx.setScreen(() -> new MineEditScreen(null, MineManager.get().mines().getFirst().copy(), false));
			ctx.waitTicks(2);
			ctx.takeScreenshot("minentimer-lehen-bearbeiten");
			ctx.runOnClient(mc -> ModConfig.get().activeArea = "");

			// Alle Lehen im Overlay: nach Lehen gruppiert, innerhalb nach Entfernung wie im Menü eingestellt
			ctx.runOnClient(mc -> {
				ModConfig cfg = ModConfig.get();
				cfg.sortMode = ModConfig.SortMode.DISTANCE;
				MineManager mgr = MineManager.get();
				var shown = de.minentimer.gui.HudOverlay.visibleNames();
				double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
				java.util.List<String> expected = new java.util.ArrayList<>();
				for (String area : java.util.List.of("Rosenwacht", "Sturmhöhe", MineManager.NO_AREA)) {
					mgr.mines().stream().filter(m -> MineManager.inArea(m, area))
							.sorted(java.util.Comparator.comparingDouble(m -> m.distanceTo(px, py, pz)))
							.forEach(m -> expected.add(m.name));
				}
				if (!shown.equals(expected)) throw new AssertionError("Overlay-Gruppen/Entfernung: " + shown + " statt " + expected);
			});
			ctx.setScreen(() -> null);
			ctx.waitTicks(2);
			ctx.takeScreenshot("minentimer-overlay-gruppiert");
			ctx.runOnClient(mc -> ModConfig.get().sortMode = ModConfig.SortMode.LIST);

			// Mine kam mit einer alten Version ohne Lehen an: gleicher Zeitstempel, Lehen wird nachgeholt
			ctx.runOnClient(mc -> {
				MineManager mgr = MineManager.get();
				Mine local = mgr.createMine("Alte Kopie", 0, 0, 0);
				local.updatedAt = 1000;
				mgr.allMinesIncludingDeleted().put(local.id, local);
				Mine remote = local.copy();
				remote.area = "Sturmhöhe";
				if (!mgr.mergeRemoteMine(remote) || !"Sturmhöhe".equals(mgr.allMinesIncludingDeleted().get(local.id).area)) {
					throw new AssertionError("Lehen bei gleichem Stand nicht nachgeholt");
				}
				Mine same = remote.copy();
				if (mgr.mergeRemoteMine(same)) throw new AssertionError("Gleicher Stand wurde unnötig übernommen");
				Mine older = remote.copy();
				older.area = "";
				older.updatedAt = 999;
				if (mgr.mergeRemoteMine(older)) throw new AssertionError("Älterer Stand hat überschrieben");
				mgr.allMinesIncludingDeleted().remove(local.id);
			});

			ctx.setScreen(() -> new SettingsScreen(null));
			ctx.takeScreenshot("minentimer-einstellungen");

			ctx.setScreen(() -> new MineEditScreen(null, MineManager.get().mines().getLast().copy(), false));
			ctx.takeScreenshot("minentimer-bearbeiten");

			// Sync gegen den echten Sync-Server (server/minentimer-server.py lokal auf Port 8765)
			ctx.runOnClient(mc -> {
				ModConfig cfg = ModConfig.get();
				cfg.syncEnabled = true;
				cfg.syncUrl = "http://127.0.0.1:8765/";
				cfg.syncGroup = "test";
				cfg.syncKey = de.minentimer.BuiltInSync.KEY;
				SyncManager.get().requestPoll();
			});
			ctx.waitTicks(60);
			ctx.runOnClient(mc -> {
				MineManager mgr = MineManager.get();
				Mine mine = mgr.mines().stream().filter(x -> x.id.equals("eisen_vorkommen_1")).findFirst().orElseThrow();
				TimerEntry t = mgr.timer(mine);
				if (t == null || !"Freund".equals(t.by) || !mgr.isRunning(mine, System.currentTimeMillis())) {
					throw new AssertionError("Remote-Timer wurde nicht übernommen, Status: " + SyncManager.get().status());
				}
				if (!"Verbunden".equals(SyncManager.get().status())) throw new AssertionError("Sync-Status: " + SyncManager.get().status());
				mgr.mines().stream().filter(x -> x.id.equals("eisen_vorkommen_2")).findFirst().ifPresent(mgr::start);
			});
			ctx.waitTicks(40);
			String uploaded;
			try {
				uploaded = java.net.http.HttpClient.newHttpClient().send(
						java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:8765/minentimer/test.json"))
								.header("X-MinenTimer-Key", de.minentimer.BuiltInSync.KEY).build(),
						java.net.http.HttpResponse.BodyHandlers.ofString()).body();
			} catch (Exception e) {
				throw new AssertionError("Mock-Server nicht erreichbar", e);
			}
			if (!uploaded.replace(" ", "").contains("\"eisen_vorkommen_2\":{\"readyAt\"") || !uploaded.contains("meine_kohlemine_")) {
				throw new AssertionError("Lokale Änderungen fehlen auf dem Server: " + uploaded);
			}
			ctx.runOnClient(mc -> {
				if (!"Verbunden".equals(SyncManager.get().status())) throw new AssertionError("Sync-Status nach Upload: " + SyncManager.get().status());
			});
			ctx.setScreen(() -> new SyncScreen(null));
			ctx.takeScreenshot("minentimer-sync");
			ctx.setScreen(() -> new MineListScreen(null));
			ctx.takeScreenshot("minentimer-menue-sync");
			ctx.setScreen(() -> null);
		}
	}
}
