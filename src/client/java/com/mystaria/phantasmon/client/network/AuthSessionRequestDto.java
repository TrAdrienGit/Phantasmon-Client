package com.mystaria.phantasmon.client.network;

import java.util.UUID;

/** Matches the backend {@code POST /auth/session} request body. */
public record AuthSessionRequestDto(UUID uuid, String username, String serverId) {
}
