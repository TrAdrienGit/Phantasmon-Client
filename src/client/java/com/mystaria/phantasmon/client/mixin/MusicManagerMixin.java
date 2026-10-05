package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.sounds.MusicManager;

import com.mystaria.phantasmon.client.audio.PhantasmonMusic;

/** Minecraft's background music steps aside while Phantasmon's battle music plays (TODO-21). */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void phantasmon$holdForBattleMusic(CallbackInfo ci) {
		if (PhantasmonMusic.active()) {
			((MusicManager) (Object) this).stopPlaying();
			ci.cancel();
		}
	}
}
