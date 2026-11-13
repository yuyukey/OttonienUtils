package de.lehenskarte.test;

import de.lehenskarte.data.LehenGeometry;
import de.lehenskarte.data.LehenState;
import de.lehenskarte.data.OttonienData;
import de.lehenskarte.gui.LehenHud;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;

import java.util.Arrays;

/** Testwelt, Spieler in Merdorf, HUD und World Map mit Screenshots. */
public class LehenskarteClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		// Geometrie: Mittelpunkte der Lehen liegen in ihren Polygonen
		ctx.runOnClient(mc -> {
			LehenGeometry geo = LehenState.geometry();
			if (de.lehenskarte.data.NpcVillages.all().size() != 6) throw new AssertionError("NPC-Dörfer: " + de.lehenskarte.data.NpcVillages.all().size());
			if (geo.shapes().size() != 65) throw new AssertionError("Lehen-Anzahl: " + geo.shapes().size());
			LehenGeometry.Shape merdorf = geo.at(-159, 2791);
			if (merdorf == null || !merdorf.id().equals("lehen_6")) throw new AssertionError("Merdorf nicht erkannt: " + merdorf);
			// Türfdorf (Taunestein) reicht bis an Altenessen; der Zipfel von Eugensbrück bleibt
			LehenGeometry.Shape tail = geo.at(-540, -200), east = geo.at(-540, 150);
			if (tail == null || !tail.id().equals("lehen_28")) throw new AssertionError("Eugensbrück-Zipfel: " + tail);
			if (east == null || !east.id().equals("lehen_24")) throw new AssertionError("Türfdorf-Erweiterung: " + east);
			int[] colors = new int[256];
			if (!LehenState.chunkColors(-159 >> 4, 2791 >> 4, colors, true) && LehenState.isActive()) throw new AssertionError("Keine Farben");
		});

		ctx.runOnClient(mc -> {
			if (de.lehenskarte.Config.get().fillOpacity != 14) throw new AssertionError("Alte Deckkraft nicht angepasst: " + de.lehenskarte.Config.get().fillOpacity);
		});

		// Kalibrierlauf nur, wenn build/kalibrieren.txt den Pfad zu echten Xaero-Kartendaten enthält
		java.nio.file.Path flag = java.nio.file.Path.of("../../kalibrieren.txt").toAbsolutePath().normalize();
		if (java.nio.file.Files.exists(flag)) {
			try {
				java.nio.file.Path src = java.nio.file.Path.of(java.nio.file.Files.readString(flag).trim());
				for (java.nio.file.Path dst : new java.nio.file.Path[]{java.nio.file.Path.of("xaero", "world-map", "New World", "null"),
						java.nio.file.Path.of("xaero", "world-map", "New World", "null", "mw$default")}) {
					java.nio.file.Files.createDirectories(dst);
					try (var files = java.nio.file.Files.list(src)) {
						for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files::iterator) {
							if (f.toString().endsWith(".zip")) java.nio.file.Files.copy(f, dst.resolve(f.getFileName().toString()),
									java.nio.file.StandardCopyOption.REPLACE_EXISTING);
						}
					}
				}
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
			calibrate(ctx);
			return;
		}
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("tp @a -159 140 2791");
			world.getClientWorld().waitForChunksRender();
			ctx.waitFor(mc -> !OttonienData.get().regions().isEmpty(), 1200);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> {
				if (!LehenState.isActive()) throw new AssertionError("Lehenskarte nicht aktiv");
				LehenGeometry.Shape here = LehenHud.current(mc);
				if (here == null || !here.id().equals("lehen_6")) throw new AssertionError("HUD-Lehen: " + here);
				if (OttonienData.get().owner("lehen_6") == null) throw new AssertionError("Merdorf hat kein Gefolge in den Daten");
				// Zusammenfassen beim Rauszoomen: oberste Stufe = Lehen des obersten Lehnsherrn
				var geo = LehenState.geometry();
				var groups = de.lehenskarte.data.LehenGroups.of(geo, OttonienData.get());
				for (LehenGeometry.Shape s : geo.shapes()) {
					OttonienData.Faction f = OttonienData.get().owner(s.id());
					LehenGeometry.Shape top = f != null ? geo.byId(f.realmRegion()) : s;
					if (top != null && groups.root(2, s.index()) != top.index())
						throw new AssertionError("Machtbereich von " + s.id() + ": " + geo.shape(groups.root(2, s.index())).id() + " statt " + top.id());
					LehenGeometry.Shape mid = geo.shape(groups.root(1, s.index()));
					OttonienData.Faction mf = OttonienData.get().owner(mid.id());
					if (mf != null && mf.hasLord() && !mf.lordRegion().equals(f.realmRegion()))
						throw new AssertionError("Mittlere Stufe von " + s.id() + " ist " + mid.id() + ", kein direkter Vasall des obersten Lehnsherrn");
				}
			});
			ctx.takeScreenshot("lehenskarte-hud");

			// Minimap mit kräftiger Füllung, um die Einfärbung sicher zu sehen
			ctx.runOnClient(mc -> {
				de.lehenskarte.Config.get().fillOpacity = 75;
				LehenState.rebuild();
				de.lehenskarte.xaero.XaeroBridge.refresh();
			});
			ctx.waitTicks(60);
			ctx.takeScreenshot("lehenskarte-minimap-kraeftig");
			ctx.runOnClient(mc -> {
				de.lehenskarte.Config.get().fillOpacity = 14;
				LehenState.rebuild();
				de.lehenskarte.xaero.XaeroBridge.refresh();
			});

			KeyMapping openMap = ctx.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings)
					.filter(k -> k.getName().equals("gui.xaero_open_map")).findFirst().orElseThrow());
			ctx.getInput().pressKey(openMap);
			ctx.waitTicks(100);
			ctx.takeScreenshot("lehenskarte-worldmap");

			for (int i = 0; i < 7; i++) {
				ctx.getInput().scroll(-1);
				ctx.waitTicks(5);
			}
			ctx.waitTicks(60);
			ctx.takeScreenshot("lehenskarte-worldmap-mittel");
			for (int i = 0; i < 7; i++) {
				ctx.getInput().scroll(-1);
				ctx.waitTicks(5);
			}
			ctx.waitTicks(80);
			ctx.takeScreenshot("lehenskarte-worldmap-weit");
			for (int i = 0; i < 6; i++) {
				ctx.getInput().scroll(-1);
				ctx.waitTicks(5);
			}
			ctx.waitTicks(80);
			ctx.takeScreenshot("lehenskarte-worldmap-machtbereiche");

			// Auswahl muss sofort sichtbar sein, ohne Neuberechnung durch Xaero
			ctx.runOnClient(mc -> LehenState.select(LehenState.geometry().byId("lehen_6").index()));
			ctx.waitTicks(2);
			ctx.takeScreenshot("lehenskarte-worldmap-auswahl");
			ctx.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(20);

			// Nahaufnahme an einer Ecke von Merdorf, wo mehrere Grenzen zusammenlaufen
			ctx.runOnClient(mc -> LehenState.select(-1));
			int[] corner = ctx.computeOnClient(mc -> {
				LehenGeometry.Shape m = LehenState.geometry().byId("lehen_6");
				return new int[]{m.xs()[0], m.zs()[0]};
			});
			world.getServer().runCommand("tp @a " + corner[0] + " 140 " + corner[1]);
			world.getClientWorld().waitForChunksRender();
			ctx.waitTicks(80);
			ctx.takeScreenshot("lehenskarte-minimap-grenze");
			ctx.getInput().pressKey(openMap);
			ctx.waitTicks(60);
			for (int i = 0; i < 3; i++) {
				ctx.getInput().scroll(1);
				ctx.waitTicks(5);
			}
			ctx.waitTicks(40);
			ctx.takeScreenshot("lehenskarte-grenze-nah");
			ctx.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> mc.setScreen(new de.lehenskarte.gui.SettingsScreen(null)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("lehenskarte-einstellungen");
			ctx.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(20);

			// Anzeige verschieben: unten links ablegen, im Spiel prüfen, wieder zurücksetzen
			ctx.runOnClient(mc -> {
				de.lehenskarte.Config.get().hudX = 0.15;
				de.lehenskarte.Config.get().hudY = 1;
				mc.setScreen(new de.lehenskarte.gui.HudPositionScreen(null));
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("lehenskarte-hud-verschieben");
			ctx.runOnClient(mc -> mc.setScreen(null));
			ctx.waitTicks(10);
			ctx.takeScreenshot("lehenskarte-hud-verschoben");
			ctx.runOnClient(mc -> {
				de.lehenskarte.Config.get().hudX = 0.5;
				de.lehenskarte.Config.get().hudY = 0;
			});

			// NPC-Dorf ohne Gefolge: eigenes Wappen (wappen_holdern) statt „kein Wappen“
			world.getServer().runCommand("tp @a -1776 140 410");
			ctx.waitTicks(100);
			ctx.takeScreenshot("lehenskarte-hud-holdern");
		}
	}

	/**
	 * Einmaliger Kalibrierlauf: zeigt echte Xaero-Kartendaten (vorher in den Testordner kopiert) ohne Overlay
	 * und speichert Screenshots samt Ausschnitt, um die gemalte Karte daran auszurichten.
	 */
	private static void calibrate(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			world.getServer().runCommand("gamemode spectator @a");
			world.getServer().runCommand("tp @a 40000 200 40000");
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> {
				de.lehenskarte.Config.get().overlayEnabled = false;
				de.lehenskarte.Config.get().paintedMap = false;
			});
			KeyMapping openMap = ctx.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings)
					.filter(k -> k.getName().equals("gui.xaero_open_map")).findFirst().orElseThrow());
			ctx.getInput().pressKey(openMap);
			ctx.waitTicks(40);
			StringBuilder log = new StringBuilder();
			int n = 0;
			for (int z = -3300; z <= 3900; z += 1200) {
				for (int x = -3300; x <= 3900; x += 2400) {
					double[] req = {x, z, 0.25};
					ctx.runOnClient(mc -> de.lehenskarte.gui.MapDebug.request = req);
					ctx.waitTicks(120);
					String name = String.format("kalib-%02d", n++);
					ctx.takeScreenshot(name);
					double[] cam = ctx.computeOnClient(mc -> new double[]{de.lehenskarte.gui.MapDebug.cameraX,
							de.lehenskarte.gui.MapDebug.cameraZ, de.lehenskarte.gui.MapDebug.scale,
							mc.getWindow().getWidth(), mc.getWindow().getHeight()});
					log.append(name).append(' ').append(cam[0]).append(' ').append(cam[1]).append(' ').append(cam[2])
							.append(' ').append(cam[3]).append(' ').append(cam[4]).append(System.lineSeparator());
				}
			}
			try {
				java.nio.file.Files.writeString(java.nio.file.Path.of("kalibrierung.txt"), log.toString());
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
