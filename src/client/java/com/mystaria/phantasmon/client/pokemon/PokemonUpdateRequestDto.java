package com.mystaria.phantasmon.client.pokemon;

import java.util.Map;

/**
 * Matches the backend {@code PATCH /pokemon/{uuid}} request body — a partial
 * update, but note {@code data} <b>replaces</b> the whole JSONB blob
 * server-side rather than merging field-by-field, so any caller that wants to
 * change one {@code data} field must send back the full current map with
 * just that field changed (see {@link PokemonCommands}).
 */
public record PokemonUpdateRequestDto(Map<String, Object> data, Integer level, Integer teamSlot) {
}
