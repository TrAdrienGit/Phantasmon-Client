package com.mystaria.phantasmon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;

import net.minecraft.resources.ResourceLocation;

import com.mystaria.phantasmon.client.pokemon.LookTextures;
import com.mystaria.phantasmon.client.pokemon.PokemonLook;

/**
 * The filter of a Pokémon's look (TODO-37): when the drawn Pokémon carries a {@code phantasmon_filter_<id>} aspect, the
 * texture Cobblemon picked is swapped for its recoloured copy ({@link LookTextures}). Covers the world (Ghosts, Ghost
 * battles, the hub) and the menus' models alike: both ask this repository for the texture. Real Cobblemon Pokémon
 * never carry the aspect. Kotlin method of Cobblemon 1.8.1: revalidate after every Cobblemon bump.
 */
@Mixin(value = VaryingModelRepository.class, remap = false)
public abstract class VaryingModelRepositoryLookMixin {

	@Inject(method = "getTexture", at = @At("RETURN"), cancellable = true)
	private void phantasmon$lookFilter(ResourceLocation name, PosableState state, CallbackInfoReturnable<ResourceLocation> cir) {
		if (state == null) {
			return;
		}
		for (String aspect : state.getCurrentAspects()) {
			if (aspect.startsWith(PokemonLook.ASPECT_FILTER)) {
				cir.setReturnValue(LookTextures.filtered(cir.getReturnValue(), aspect.substring(PokemonLook.ASPECT_FILTER.length())));
				return;
			}
		}
	}
}
