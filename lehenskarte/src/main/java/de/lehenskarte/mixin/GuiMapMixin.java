package de.lehenskarte.mixin;

import de.lehenskarte.gui.MapDebug;
import de.lehenskarte.gui.MapOverlay;
import de.lehenskarte.gui.PaintedMap;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.GuiMap;

/** Banner, Namen beim Überfahren und Infofeld in Xaero's World Map. */
@Mixin(value = GuiMap.class, remap = false)
public abstract class GuiMapMixin {
	@Shadow private double cameraX;
	@Shadow private double cameraZ;
	@Shadow private double scale;
	@Shadow private double screenScale;
	@Shadow private int[] cameraDestination;
	@Shadow private double userScale;
	@Shadow private static double destScale;

	/** Nur für Tests: Ausschnitt setzen und ablesen. */
	@Inject(method = {"render", "method_25394"}, at = @At("HEAD"))
	private void lehenskarte$debugCamera(CallbackInfo ci) {
		double[] r = MapDebug.request;
		if (r != null) {
			MapDebug.request = null;
			cameraDestination = new int[]{(int) r[0], (int) r[1]};
			userScale = r[2];
			destScale = r[2];
		}
		MapDebug.cameraX = cameraX;
		MapDebug.cameraZ = cameraZ;
		MapDebug.scale = scale;
	}

	@Inject(method = "renderPreDropdown", at = @At("HEAD"))
	private void lehenskarte$render(GuiGraphics g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
		MapOverlay.render(g, mouseX, mouseY, cameraX, cameraZ, scale, screenScale);
	}

	/**
	 * Gemalte Karte in unerkundeten Bereichen: nach Xaeros Kacheln, vor Wegpunkten und anderen Kartenelementen.
	 * render heißt in der Entwicklungsumgebung „render“, im Spiel „method_25394“.
	 */
	@Inject(method = {"render", "method_25394"},
			at = @At(value = "INVOKE", target = "Lxaero/map/element/MapElementRenderHandler;render"))
	private void lehenskarte$paintedMap(CallbackInfo ci) {
		PaintedMap.render(cameraX, cameraZ, scale);
	}

	@Inject(method = "mapClicked", at = @At("HEAD"))
	private void lehenskarte$click(int button, int x, int y, CallbackInfo ci) {
		if (button == 0) MapOverlay.click(x / screenScale, y / screenScale, cameraX, cameraZ, scale, screenScale);
	}
}
