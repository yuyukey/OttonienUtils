package de.lehenskarte.mixin;

import de.lehenskarte.gui.MapOverlay;
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

	@Inject(method = "renderPreDropdown", at = @At("HEAD"))
	private void lehenskarte$render(GuiGraphics g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
		MapOverlay.render(g, mouseX, mouseY, cameraX, cameraZ, scale, screenScale);
	}

	@Inject(method = "mapClicked", at = @At("HEAD"))
	private void lehenskarte$click(int button, int x, int y, CallbackInfo ci) {
		if (button == 0) MapOverlay.click(x / screenScale, y / screenScale, cameraX, cameraZ, scale, screenScale);
	}
}
