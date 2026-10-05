package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.keybind.keybinds.PartySendBinding;

import com.mystaria.phantasmon.client.ghost.GhostPartyHud;

/**
 * Cobblemon's send key (R) sends out / recalls the selected Ghost while the Ghost overlay shows (TODO-23).
 * Aiming at a real entity or riding leaves it to Cobblemon (interaction wheel, challenge, dismount).
 */
@Mixin(value = PartySendBinding.class, remap = false)
public abstract class PartySendBindingMixin {

	@Inject(method = "onRelease", at = @At("HEAD"), cancellable = true)
	private void phantasmon$sendGhost(CallbackInfo ci) {
		GhostPartyHud hud = GhostPartyHud.instance();
		if (hud == null || !hud.ownsSendKey()) {
			return;
		}
		// Not Cobblemon's canAction()/actioned(): actioned() closes its "canApplyChange" lock, which only its own
		// onRelease reopens — and that is the very method cancelled here, so R stayed locked after the first Ghost
		// (Adrien 2026-10-05). Only the key's own press state is reset; Cobblemon's lock is left as it was.
		PartySendBinding binding = (PartySendBinding) (Object) this;
		binding.setWasDown(false);
		binding.setHeldDownSeconds(0f);
		ci.cancel();
		hud.sendSelected();
	}
}
