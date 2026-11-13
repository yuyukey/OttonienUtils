package de.viehzucht.mixin;

import de.viehzucht.data.Breeding;
import de.viehzucht.data.Shearing;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Farbe des Leuchtens bei geschorenen Schafen und gepaarten Tieren. */
@Mixin(Entity.class)
public class EntityMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void viehzucht$shearedColor(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (Shearing.glows(self)) cir.setReturnValue(Shearing.COLOR);
		else if (Breeding.glows(self)) cir.setReturnValue(Breeding.COLOR);
	}
}
