package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import net.minecraft.world.entity.player.Player;

import com.mystaria.phantasmon.client.ghost.PhantasmonEntities;

/**
 * Phantasmon's client-only entities have no owner on the server, so Cobblemon takes them for wild Pokémon:
 * {@code canBattle} drives both the "Press R to start a battle" line under their label and the R key itself
 * ({@code PartySendBinding}), which would challenge an entity the server doesn't have (Adrien, 2026-10-04). They are
 * never wild-battleable; Ghost battles go through the Ghost invitation (B, wheel, command).
 *
 * <p>Targets a Cobblemon method ({@code canBattle}, 1.8.1): revalidate after every Cobblemon bump.
 */
@Mixin(value = PokemonEntity.class, remap = false)
public abstract class PokemonEntityMixin {

	@Inject(method = "canBattle", at = @At("HEAD"), cancellable = true)
	private void phantasmon$ghostsAreNotWild(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (PhantasmonEntities.isPhantasmon((PokemonEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
