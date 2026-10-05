package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.keybind.keybinds.HidePartyBinding;

import com.mystaria.phantasmon.client.ghost.GhostPartyHud;

/**
 * Cobblemon's "hide party" key now cycles Cobblemon team → Ghost team → nothing ({@link GhostPartyHud}) instead of
 * only showing / hiding Cobblemon's party.
 */
@Mixin(value = HidePartyBinding.class, remap = false)
public abstract class HidePartyBindingMixin {

	@Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
	private void phantasmon$cycleParties(CallbackInfo ci) {
		GhostPartyHud hud = GhostPartyHud.instance();
		if (hud != null) {
			hud.cycle();
			ci.cancel();
		}
	}
}
