package de.viehzucht.mixin;

import de.viehzucht.Config;
import de.viehzucht.ViehzuchtClient;
import de.viehzucht.data.AnimalInfo;
import de.viehzucht.data.AnimalStore;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ♂ oder ♀ hinter den Namen über dem Tier, sobald das Geschlecht bekannt ist. */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	@Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
	private void viehzucht$gender(Entity entity, CallbackInfoReturnable<Component> cir) {
		Component name = cir.getReturnValue();
		if (name == null || !ViehzuchtClient.isActive() || !Config.get().nametagGender) return;
		AnimalInfo info = AnimalStore.get(entity.getUUID());
		if (info == null || info.gender == null) return;
		boolean male = "m".equals(info.gender);
		cir.setReturnValue(name.copy().append(Component.literal(male ? " ♂" : " ♀").withColor(male ? 0x66AAFF : 0xFF88CC)));
	}
}
