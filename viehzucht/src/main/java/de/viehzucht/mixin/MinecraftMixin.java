package de.viehzucht.mixin;

import de.viehzucht.data.Breeding;
import de.viehzucht.data.Shearing;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Selbst geschorene Schafe und gepaarte Tiere leuchten, bis sie wieder scherbar bzw. paarungsbereit sind. */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void viehzucht$sheared(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (Shearing.glows(entity) || Breeding.glows(entity)) cir.setReturnValue(true);
	}
}
