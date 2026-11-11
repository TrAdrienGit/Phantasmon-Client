package com.mystaria.phantasmon.client.pokemon;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The size of a Pokémon's look (TODO-37) on Phantasmon's own entities (Ghosts, Ghost battle send-outs, hub Ghosts):
 * the entity's {@code scale} attribute, which Minecraft's renderer applies. Auras are glowing shells
 * ({@link LookShells}), no longer particles (Adrien 2026-11-11: not worthy of a reward). Client thread.
 */
public final class LookAuras {

	private LookAuras() {
	}

	/** The look's size on {@code entity} (50 to 200 %), through Minecraft's scale attribute. */
	public static void applyScale(PokemonEntity entity, PokemonLook look) {
		if (look.scale() == 100) {
			return;
		}
		AttributeInstance scale = entity.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(Math.max(PokemonLook.SCALE_MIN, Math.min(PokemonLook.SCALE_MAX, look.scale())) / 100.0);
		}
	}
}
