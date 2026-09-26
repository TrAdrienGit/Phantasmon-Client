package com.mystaria.phantasmon.client.pokemon;

import java.util.Map;
import java.util.UUID;

/** Matches the backend's {@code Pokemon} response schema. */
public record PokemonDto(
		UUID uuid,
		UUID ownerUuid,
		String species,
		String form,
		int level,
		String nature,
		String ability,
		boolean isShiny,
		Integer boxId,
		Integer boxSlot,
		Integer teamSlot,
		String cobblemonDataVersion,
		Map<String, Object> data) {
}
