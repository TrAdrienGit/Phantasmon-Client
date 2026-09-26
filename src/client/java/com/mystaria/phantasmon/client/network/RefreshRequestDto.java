package com.mystaria.phantasmon.client.network;

/** Matches the backend {@code POST /auth/refresh} request body. */
public record RefreshRequestDto(String refreshToken) {
}
