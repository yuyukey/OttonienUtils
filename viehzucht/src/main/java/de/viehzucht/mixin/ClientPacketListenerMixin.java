package de.viehzucht.mixin;

import de.viehzucht.Analysis;
import de.viehzucht.data.Tracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entity-Ereignisse (Herzchen) für die Paarungserkennung, dazu die Analyse. TAIL läuft nur im Spiel-Thread. */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleEntityEvent", at = @At("TAIL"))
	private void viehzucht$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		var entity = packet.getEntity(mc.level);
		Tracker.onEntityEvent(entity, packet.getEventId());
		Analysis.onEntityEvent(entity, packet.getEventId());
	}

	@Inject(method = "handleParticleEvent", at = @At("TAIL"))
	private void viehzucht$particles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		Tracker.onParticle(packet.getParticle(), packet.getX(), packet.getY(), packet.getZ());
	}

	@Inject(method = "handleSetEntityData", at = @At("TAIL"))
	private void viehzucht$entityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) Analysis.onEntityData(mc.level.getEntity(packet.id()), packet.packedItems());
	}
}
