package com.mystaria.phantasmon.client.pokemon.showdown;

import java.util.List;
import java.util.Map;

/**
 * One Pokémon set parsed from a Pokémon Showdown export block, still in raw
 * display-name form (species/moves/ability/item/nature) — {@link CobblemonIdentifiers}
 * converts those to actual Cobblemon identifiers when building the backend request.
 */
public record ShowdownPokemon(
		String nickname,
		String speciesToken,
		String gender,
		String item,
		String ability,
		int level,
		boolean shiny,
		String teraType,
		Map<String, Integer> evs,
		Map<String, Integer> ivs,
		String nature,
		Integer happiness,
		List<String> moves) {
}
