package com.mystaria.phantasmon.client.mixin;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext;
import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline;

import kotlin.Unit;

import com.mystaria.phantasmon.client.battle.GhostActionEffects;

/**
 * Move animations in Ghost battles (Phase 9): Cobblemon would play an action effect on the server against
 * entities a Ghost battle doesn't have. For a battle hosted on this client, the effect is handed to
 * {@link GhostActionEffects} instead, which plays it on both players' clients. Any other battle (a normal
 * Cobblemon battle on the integrated server) is untouched.
 */
@Mixin(value = ActionEffectTimeline.class, remap = false)
public abstract class ActionEffectTimelineMixin {

	@Inject(method = "run", at = @At("HEAD"), cancellable = true)
	private void phantasmon$playOnGhostClients(ActionEffectContext context, CallbackInfoReturnable<CompletableFuture<Unit>> cir) {
		CompletableFuture<Unit> intercepted = GhostActionEffects.intercept((ActionEffectTimeline) (Object) this, context);
		if (intercepted != null) {
			cir.setReturnValue(intercepted);
		}
	}
}
