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
			Map.entry("ERROR_POKEMON_NOT_IN_TEAM", "phantasmon.ghost.error.not_in_team"),
			Map.entry("ERROR_POKEMON_INCOMPLETE_BOX_DESTINATION", "phantasmon.pokemon.error.incomplete_box_destination"),
			Map.entry("ERROR_LEGALITY_IV_OUT_OF_RANGE", "phantasmon.pokemon.error.legality_iv"),
			Map.entry("ERROR_LEGALITY_EV_OUT_OF_RANGE", "phantasmon.pokemon.error.legality_ev"),
			Map.entry("ERROR_LEGALITY_EV_TOTAL_EXCEEDED", "phantasmon.pokemon.error.legality_ev_total"),
			Map.entry("ERROR_TRADE_SELF", "phantasmon.trade.error.self"),
			Map.entry("ERROR_TRADE_NOT_FOUND", "phantasmon.trade.error.not_found"),
			Map.entry("ERROR_TRADE_INVALID_RECIPIENT_POKEMON", "phantasmon.trade.error.invalid_recipient_pokemon"),
			Map.entry("ERROR_TRADE_INVALID_STATE", "phantasmon.trade.error.invalid_state"),
			Map.entry("ERROR_TRADE_OWNERSHIP_CHANGED", "phantasmon.trade.error.ownership_changed"),
			Map.entry("ERROR_TRADE_PARTNER_UNAVAILABLE", "phantasmon.trade.error.partner_unavailable"),
			Map.entry("ERROR_TRADE_PARTNER_BUSY", "phantasmon.trade.error.partner_busy"),
			Map.entry("ERROR_TRADE_ALREADY_IN_SESSION", "phantasmon.trade.error.already_in_session"),
			Map.entry("ERROR_TRADE_INVITE_NOT_FOUND", "phantasmon.trade.error.invite_not_found"),
			Map.entry("ERROR_TRADE_NOT_IN_SESSION", "phantasmon.trade.error.not_in_session"),
			Map.entry("ERROR_TRADE_OFFER_NOT_IN_TEAM", "phantasmon.trade.error.offer_not_in_team"),
			Map.entry("ERROR_TRADE_OFFERS_INCOMPLETE", "phantasmon.trade.error.offers_incomplete"),
			Map.entry("ERROR_POKEMON_IN_PENDING_TRADE", "phantasmon.pokemon.error.in_pending_trade"),
			Map.entry("ERROR_BATTLE_SELF", "phantasmon.battle.error.self"),
			Map.entry("ERROR_BATTLE_ALREADY_IN_BATTLE", "phantasmon.battle.error.already_in_battle"),
			Map.entry("ERROR_BATTLE_PARTNER_UNAVAILABLE", "phantasmon.battle.error.partner_unavailable"),
			Map.entry("ERROR_BATTLE_PARTNER_BUSY", "phantasmon.battle.error.partner_busy"),
			Map.entry("ERROR_BATTLE_INVITE_NOT_FOUND", "phantasmon.battle.error.invite_not_found"),
			Map.entry("ERROR_BATTLE_NOT_IN_BATTLE", "phantasmon.battle.error.not_battling"),
			Map.entry("ERROR_BATTLE_EMPTY_TEAM", "phantasmon.battle.error.empty_team"),
			Map.entry("ERROR_BATTLE_NOT_HOST", "phantasmon.battle.error.not_host"),
			Map.entry("ERROR_BATTLE_NOT_GUEST", "phantasmon.battle.error.not_guest"),
			Map.entry("ERROR_BATTLE_INVALID_RESULT", "phantasmon.battle.error.invalid_result"));

	private BackendErrorMessages() {
	}

	public static String translationKey(String errorCode) {
		return KEYS.getOrDefault(errorCode, "phantasmon.error.unknown");
	}
}
