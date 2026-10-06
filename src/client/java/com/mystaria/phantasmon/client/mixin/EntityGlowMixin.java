package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;

import com.mystaria.phantasmon.client.battle.BattleSpectacle;

/** A terastallized battle Pokémon glows in its Tera type's colour (Minecraft's glowing outline), client side only. */
@Mixin(Entity.class)
public abstract class EntityGlowMixin {

	@Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
	private void phantasmon$teraGlowing(CallbackInfoReturnable<Boolean> cir) {
		if (BattleSpectacle.glowColor((Entity) (Object) this) >= 0) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void phantasmon$teraColor(CallbackInfoReturnable<Integer> cir) {
		int color = BattleSpectacle.glowColor((Entity) (Object) this);
		if (color >= 0) {
			cir.setReturnValue(color);
		}
	}
}
