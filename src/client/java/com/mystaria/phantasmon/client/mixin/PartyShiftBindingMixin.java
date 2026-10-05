package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.keybind.keybinds.DownShiftPartyBinding;
import com.cobblemon.mod.common.client.keybind.keybinds.UpShiftPartyBinding;

import com.mystaria.phantasmon.client.ghost.GhostPartyHud;

/** Cobblemon's up / down party keys move the Ghost selection while the Ghost overlay shows (TODO-23). */
@Mixin(value = { UpShiftPartyBinding.class, DownShiftPartyBinding.class }, remap = false)
public abstract class PartyShiftBindingMixin {

	@Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
	private void phantasmon$shiftGhosts(CallbackInfo ci) {
		GhostPartyHud hud = GhostPartyHud.instance();
		if (hud != null && hud.ownsPartyKeys()) {
			hud.shift((Object) this instanceof UpShiftPartyBinding ? -1 : 1);
			ci.cancel();
		}
	}
}
