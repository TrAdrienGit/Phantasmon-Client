package com.mystaria.phantasmon.client.mixin;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mystaria.phantasmon.client.battle.GhostActionEffects;

/**
 * Every Cobblemon instruction that plays an action effect stores it as {@code this.future = effect.run(context)}
 * right away. That store tells {@link GhostActionEffects} which instruction — so which Pokémon, on which
 * targets, missed or not — the effect it just intercepted belongs to.
 */
@Mixin(targets = {
		"com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction",
		"com.cobblemon.mod.common.battles.interpreter.instructions.DamageInstruction",
		"com.cobblemon.mod.common.battles.interpreter.instructions.BoostInstruction",
		"com.cobblemon.mod.common.battles.interpreter.instructions.ActivateInstruction",
		"com.cobblemon.mod.common.battles.interpreter.instructions.CantInstruction",
		"com.cobblemon.mod.common.battles.interpreter.instructions.PrepareInstruction",
		"com.cobblemon.mod.common.battles.interpreter.instructions.StartInstruction"
}, remap = false)
public abstract class ActionEffectInstructionsMixin {

	@Inject(method = "setFuture", at = @At("HEAD"))
	private void phantasmon$bindGhostActionEffect(CompletableFuture<?> future, CallbackInfo ci) {
		GhostActionEffects.bind(this, future);
	}
}
