package com.mystaria.phantasmon.client.trade;

import java.util.UUID;

/** Matches the backend's {@code Trade} response schema. */
public record TradeDto(
		UUID uuid,
		UUID initiatorUuid,
		UUID recipientUuid,
		UUID offeredPokemon,
		UUID requestedPokemon,
		String status) {
}
