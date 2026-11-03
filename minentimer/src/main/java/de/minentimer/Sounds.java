package de.minentimer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class Sounds {
	private Sounds() {
	}

	public static void playReady() {
		ModConfig cfg = ModConfig.get();
		SoundEvent event = switch (cfg.sound) {
			case BELL -> SoundEvents.NOTE_BLOCK_BELL.value();
			case CHIME -> SoundEvents.NOTE_BLOCK_CHIME.value();
			case PLING -> SoundEvents.NOTE_BLOCK_PLING.value();
			case LEVELUP -> SoundEvents.PLAYER_LEVELUP;
			case ORB -> SoundEvents.EXPERIENCE_ORB_PICKUP;
		};
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0f, cfg.soundVolumePercent / 100f));
	}

	public static void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}
}
