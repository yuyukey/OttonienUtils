package de.lehenskarte.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.lehenskarte.xaero.MinimapBorders;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.MinimapProcessor;
import xaero.common.minimap.render.MinimapFBORenderer;
import xaero.hud.minimap.module.MinimapSession;
import xaero.lib.client.graphics.XaeroBufferProvider;

/** Lehensgrenzen in der Minimap: nach Xaeros Chunk-Gitter, bevor die Karte gedreht wird. */
@Mixin(value = MinimapFBORenderer.class, remap = false)
public abstract class MinimapFBORendererMixin {
	@Inject(method = "renderChunksToFBO", at = @At(value = "INVOKE",
			target = "Lxaero/lib/client/graphics/XaeroBufferProvider;endBatch()V", ordinal = 0))
	private void lehenskarte$borders(MinimapSession session, PoseStack poseStack, MinimapProcessor processor, Vec3 pos,
									 ResourceKey<Level> dim, double a, int b, float c, int d, boolean e, boolean f, int g,
									 double h, double i, boolean j, XaeroBufferProvider provider, CallbackInfo ci) {
		if (Level.OVERWORLD.equals(dim)) MinimapBorders.render(poseStack.last().pose(), pos, ((MinimapRendererAccessor) this).lehenskarte$zoom());
	}
}
