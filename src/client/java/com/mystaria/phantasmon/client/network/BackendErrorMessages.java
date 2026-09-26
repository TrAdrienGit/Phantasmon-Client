package com.mystaria.phantasmon.client.network;

import java.util.Map;

/**
 * Maps backend {@code error_code} values to translation keys (CAD Partie 3
 * §L.1: the client translates locally, never displays server text). Shared
 * across every domain (auth, pokemon, ...) rather than duplicated per package.
 */
public final class BackendErrorMessages {

	private static final Map<String, String> KEYS = Map.ofEntries(
			Map.entry("ERROR_AUTH_MOJANG_VERIFICATION_FAILED", "phantasmon.auth.error.mojang_verification_failed"),
			Map.entry("ERROR_AUTH_UUID_MISMATCH", "phantasmon.auth.error.uuid_mismatch"),
			Map.entry("ERROR_AUTH_INVALID_REFRESH_TOKEN", "phantasmon.auth.error.invalid_refresh_token"),
			Map.entry("ERROR_OWNERSHIP_MISMATCH", "phantasmon.error.ownership_mismatch"),
			Map.entry("ERROR_POKEMON_NOT_FOUND", "phantasmon.pokemon.error.not_found"),
			Map.entry("ERROR_POKEMON_PC_FULL", "phantasmon.pokemon.error.pc_full"),
			Map.entry("ERROR_POKEMON_SLOT_OCCUPIED", "phantasmon.pokemon.error.slot_occupied"),
			Map.entry("ERROR_LEGALITY_IV_OUT_OF_RANGE", "phantasmon.pokemon.error.legality_iv"),
			Map.entry("ERROR_LEGALITY_EV_OUT_OF_RANGE", "phantasmon.pokemon.error.legality_ev"),
			Map.entry("ERROR_LEGALITY_EV_TOTAL_EXCEEDED", "phantasmon.pokemon.error.legality_ev_total"));

	private BackendErrorMessages() {
	}

	public static String translationKey(String errorCode) {
		return KEYS.getOrDefault(errorCode, "phantasmon.error.unknown");
	}
}
