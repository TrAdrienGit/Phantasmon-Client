package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer;
import com.cobblemon.mod.common.client.settings.ServerSettings;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.mystaria.phantasmon.client.ghost.PhantasmonEntities;

/**
 * Ghost Pokémon are known to every player (Adrien, TODO-13): Cobblemon's label above a Pokémon shows "???" when
 * its species isn't registered in the player's Pokédex; for Phantasmon's own entities (Ghosts in the world,
 * Ghost battle send-outs) the real name — "[Ghost] Bichou" — is always shown. Real Cobblemon Pokémon keep the
 * Pokédex rule, and the player's Pokédex itself is never touched (it is synced by the server).
 *
 * <p>Targets a private Cobblemon method ({@code resolveBaseLabel}, 1.8.1): revalidate after every Cobblemon bump
 * ({@code defaultRequire = 1} fails the launch loudly if it moves).
 */
@Mixin(value = PokemonRenderer.class, remap = false)
public abstract class PokemonRendererMixin {

	@Inject(method = "resolveBaseLabel", at = @At("HEAD"), cancellable = true)
	private void phantasmon$ghostsAreAlwaysKnown(PokemonEntity entity, CallbackInfoReturnable<MutableComponent> cir) {
		if (PhantasmonEntities.isPhantasmon(entity) && ServerSettings.INSTANCE.getDisplayEntityNameLabel()) {
			// Built from the Pokémon itself, never entity.getName()/getTitledName(): other mods hook those too —
			// catchindicator (in Adrien's modpack) turns them into "??? ♀" for species missing from the Pokédex.
			Pokemon pokemon = entity.getPokemon();
			Component name = pokemon.getNickname() != null ? pokemon.getNickname() : pokemon.getSpecies().getTranslatedName();
			cir.setReturnValue(name.copy());
		}
	}
}
