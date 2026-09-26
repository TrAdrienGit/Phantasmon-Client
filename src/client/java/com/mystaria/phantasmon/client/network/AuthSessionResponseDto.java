package com.mystaria.phantasmon.client.network;

/** Matches the backend {@code POST /auth/session} and {@code POST /auth/refresh} response body. */
public record AuthSessionResponseDto(String accessToken, String refreshToken, long expiresIn) {
}
