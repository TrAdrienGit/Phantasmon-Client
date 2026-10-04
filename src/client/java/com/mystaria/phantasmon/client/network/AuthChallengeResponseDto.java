package com.mystaria.phantasmon.client.network;

/** {@code POST /auth/challenge}: the one-time value to use as the Mojang {@code serverId} (SEC-1). */
public record AuthChallengeResponseDto(String challenge) {
}
