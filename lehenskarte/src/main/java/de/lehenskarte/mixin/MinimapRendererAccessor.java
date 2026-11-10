package de.lehenskarte.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import xaero.common.minimap.render.MinimapRenderer;

@Mixin(value = MinimapRenderer.class, remap = false)
public interface MinimapRendererAccessor {
	@Accessor("zoom")
	double lehenskarte$zoom();
}
