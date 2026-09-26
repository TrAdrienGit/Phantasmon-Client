package com.mystaria.phantasmon.client.pokemon;

import java.util.Map;

/**
 * Matches the backend {@code PATCH /pokemon/{uuid}} request body — a partial
 * update, but note {@code data} <b>replaces</b> the whole JSONB blob
 * server-side rather than merging field-by-field, so any caller that wants to
 * change one {@code data} field must send back the full current map with
 * just that field changed (see {@link PokemonCommands}).
 *
 * <p>PC and team storage are mutually exclusive server-side (2026-09-27
 * polish): {@code teamSlot} and {@code boxId}/{@code boxSlot} are alternative
 * "move it here" destinations, whichever is given clears the other. Uniform
 * drag&drop semantics apply regardless of the PC/team mix on either side — an
 * empty destination is a plain move, an occupied one swaps the two Pokémon.
 */
public record PokemonUpdateRequestDto(Map<String, Object> data, Integer level, Integer teamSlot,
		Integer boxId, Integer boxSlot) {

	public static PokemonUpdateRequestDto setLevel(int level) {
		return new PokemonUpdateRequestDto(null, level, null, null, null);
	}

	public static PokemonUpdateRequestDto movingToTeamSlot(int teamSlot) {
		return new PokemonUpdateRequestDto(null, null, teamSlot, null, null);
	}

	public static PokemonUpdateRequestDto movingToPcSlot(int boxId, int boxSlot) {
		return new PokemonUpdateRequestDto(null, null, null, boxId, boxSlot);
	}
}
