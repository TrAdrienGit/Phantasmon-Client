package com.mystaria.phantasmon.client.pokemon;

import java.util.Map;
import java.util.UUID;

/** Matches the backend {@code POST /pokemon} request body. */
public record PokemonCreateRequestDto(
		UUID requestUuid,
		String species,
		String form,
		int level,
		String nature,
		String ability,
		Boolean isShiny,
		Integer boxId,
		Integer boxSlot,
		Integer teamSlot,
		String cobblemonDataVersion,
		Map<String, Object> data) {
}
