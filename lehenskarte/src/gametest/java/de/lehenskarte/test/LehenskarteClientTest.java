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
			if (geo.shapes().size() != 65) throw new AssertionError("Lehen-Anzahl: " + geo.shapes().size());
			LehenGeometry.Shape merdorf = geo.at(-159, 2791);
			if (merdorf == null || !merdorf.id().equals("lehen_6")) throw new AssertionError("Merdorf nicht erkannt: " + merdorf);
			int[] colors = new int[256];
			if (!LehenState.chunkColors(-159 >> 4, 2791 >> 4, colors) && LehenState.isActive()) throw new AssertionError("Keine Farben");
		});

		ctx.runOnClient(mc -> {
			if (de.lehenskarte.Config.get().fillOpacity != 14) throw new AssertionError("Alte Deckkraft nicht angepasst: " + de.lehenskarte.Config.get().fillOpacity);
		});

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

			for (int i = 0; i < 14; i++) {
				ctx.getInput().scroll(-1);
				ctx.waitTicks(5);
			}
			ctx.waitTicks(80);
			ctx.takeScreenshot("lehenskarte-worldmap-weit");

			// Auswahl muss sofort sichtbar sein, ohne Neuberechnung durch Xaero
			ctx.runOnClient(mc -> LehenState.select(LehenState.geometry().byId("lehen_6").index()));
			ctx.waitTicks(2);
			ctx.takeScreenshot("lehenskarte-worldmap-auswahl");
			ctx.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(20);
		}
	}
}
