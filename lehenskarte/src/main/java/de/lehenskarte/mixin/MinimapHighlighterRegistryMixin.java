package de.lehenskarte.mixin;

import de.lehenskarte.xaero.MinimapLehenHighlighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.highlight.HighlighterRegistry;

/** Meldet die Lehen-Flächen bei der Minimap an, kurz bevor Xaero die Liste abschließt. */
@Mixin(value = HighlighterRegistry.class, remap = false)
public abstract class MinimapHighlighterRegistryMixin {
	@Inject(method = "end", at = @At("HEAD"))
	private void lehenskarte$register(CallbackInfo ci) {
		((HighlighterRegistry) (Object) this).register(new MinimapLehenHighlighter());
	}
}
