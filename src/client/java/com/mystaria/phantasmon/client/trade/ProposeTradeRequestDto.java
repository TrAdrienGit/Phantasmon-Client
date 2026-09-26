package com.mystaria.phantasmon.client.trade;

import java.util.UUID;

/** Matches the backend {@code POST /trades} request body. */
public record ProposeTradeRequestDto(UUID requestUuid, UUID recipientUuid, UUID offeredPokemonUuid, UUID requestedPokemonUuid) {
}
