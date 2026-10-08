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
			Map.entry("ERROR_AUTH_INVALID_CHALLENGE", "phantasmon.auth.error.invalid_challenge"),
			Map.entry("ERROR_AUTH_TOO_MANY_CHALLENGES", "phantasmon.auth.error.too_many_challenges"),
			Map.entry("ERROR_AUTH_INVALID_REFRESH_TOKEN", "phantasmon.auth.error.invalid_refresh_token"),
			Map.entry("ERROR_OWNERSHIP_MISMATCH", "phantasmon.error.ownership_mismatch"),
			Map.entry("ERROR_POKEMON_NOT_FOUND", "phantasmon.pokemon.error.not_found"),
			Map.entry("ERROR_POKEMON_PC_FULL", "phantasmon.pokemon.error.pc_full"),
			Map.entry("ERROR_POKEMON_SLOT_OCCUPIED", "phantasmon.pokemon.error.slot_occupied"),
			Map.entry("ERROR_POKEMON_NOT_IN_TEAM", "phantasmon.ghost.error.not_in_team"),
			Map.entry("ERROR_GHOST_IN_BATTLE", "phantasmon.ghost.error.in_battle"),
			Map.entry("ERROR_POKEMON_INCOMPLETE_BOX_DESTINATION", "phantasmon.pokemon.error.incomplete_box_destination"),
			Map.entry("ERROR_LEGALITY_IV_OUT_OF_RANGE", "phantasmon.pokemon.error.legality_iv"),
			Map.entry("ERROR_LEGALITY_EV_OUT_OF_RANGE", "phantasmon.pokemon.error.legality_ev"),
			Map.entry("ERROR_LEGALITY_EV_TOTAL_EXCEEDED", "phantasmon.pokemon.error.legality_ev_total"),
			Map.entry("ERROR_LEGALITY_NICKNAME_TOO_LONG", "phantasmon.pokemon.error.nickname_too_long"),
			Map.entry("ERROR_LEGALITY_INVALID_DATA", "phantasmon.pokemon.error.invalid_data"),
			Map.entry("ERROR_POKEMON_DATA_TOO_LARGE", "phantasmon.pokemon.error.data_too_large"),
			Map.entry("ERROR_IDEMPOTENCY_KEY_REUSED", "phantasmon.error.request_reused"),
			Map.entry("ERROR_WS_RATE_LIMITED", "phantasmon.error.rate_limited"),
			Map.entry("ERROR_BATTLE_INVALID_PARTY", "phantasmon.battle.error.invalid_party"),
			Map.entry("ERROR_BATTLE_LOBBY_LOCKED", "phantasmon.battle.error.lobby_locked"),
			Map.entry("ERROR_BATTLE_TEAM_NOT_ALLOWED", "phantasmon.battle.error.team_not_allowed"),
			Map.entry("ERROR_ADMIN_REQUIRED", "phantasmon.admin.error.required"),
			Map.entry("ERROR_PLAYER_NOT_FOUND", "phantasmon.admin.error.player_not_found"),
			Map.entry("ERROR_ADMIN_NOTHING_TO_STOP", "phantasmon.admin.error.nothing_to_stop"),
			Map.entry("ERROR_BATTLE_UNKNOWN_FORMAT", "phantasmon.battle.error.unknown_format"),
			Map.entry("ERROR_BATTLE_LOBBY_NOT_FOUND", "phantasmon.battle.error.lobby_not_found"),
			Map.entry("ERROR_VALIDATION_FAILED", "phantasmon.error.validation_failed"),
			Map.entry("ERROR_MALFORMED_REQUEST", "phantasmon.error.malformed_request"),
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
			Map.entry("ERROR_BATTLE_INVALID_RESULT", "phantasmon.battle.error.invalid_result"),
			Map.entry("ERROR_BATTLE_SPECTATE_NOT_BATTLING", "phantasmon.battle.error.spectate_not_battling"),
			Map.entry("ERROR_HUB_ANCHOR_QUOTA", "phantasmon.hub.error.anchor_quota"),
			Map.entry("ERROR_HUB_ANCHOR_NAME_TAKEN", "phantasmon.hub.error.anchor_name_taken"),
			Map.entry("ERROR_HUB_ANCHOR_NOT_FOUND", "phantasmon.hub.error.anchor_not_found"),
			Map.entry("ERROR_HUB_ANCHOR_FORBIDDEN", "phantasmon.hub.error.anchor_forbidden"),
			Map.entry("ERROR_HUB_ANCHOR_WRONG_SERVER", "phantasmon.hub.error.anchor_wrong_server"),
			Map.entry("ERROR_HUB_FULL", "phantasmon.hub.error.full"),
			Map.entry("ERROR_HUB_NOT_JOINED", "phantasmon.hub.error.not_joined"),
			Map.entry("ERROR_HUB_OUT_OF_BOUNDS", "phantasmon.hub.error.out_of_bounds"),
			Map.entry("ERROR_HUB_CHAT_TOO_LONG", "phantasmon.hub.error.chat_too_long"),
			Map.entry("ERROR_HUB_CHAT_RATE_LIMITED", "phantasmon.hub.error.chat_rate_limited"),
			Map.entry("ERROR_HUB_ANCHOR_OVERLAP", "phantasmon.hub.error.anchor_overlap"),
			Map.entry("ERROR_HUB_NOT_FOUND", "phantasmon.hub.error.not_found"),
			Map.entry("ERROR_HUB_NAME_TAKEN", "phantasmon.hub.error.name_taken"),
			Map.entry("ERROR_HUB_INVALID_NAME", "phantasmon.hub.error.invalid_name"),
			Map.entry("ERROR_HUB_INVALID_SIZE", "phantasmon.hub.error.invalid_size"),
			Map.entry("ERROR_HUB_SCHEMATIC_INVALID", "phantasmon.hub.error.schematic_invalid"),
			Map.entry("ERROR_HUB_SCHEMATIC_NONE", "phantasmon.hub.error.schematic_none"));

	private BackendErrorMessages() {
	}

	public static String translationKey(String errorCode) {
		return KEYS.getOrDefault(errorCode, "phantasmon.error.unknown");
	}
}
