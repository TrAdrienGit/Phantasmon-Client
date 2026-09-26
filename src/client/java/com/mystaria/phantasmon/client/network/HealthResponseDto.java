package com.mystaria.phantasmon.client.network;

/** Matches the backend {@code GET /health} response body. */
public record HealthResponseDto(String status, String database) {

	public boolean isFullyUp() {
		return "UP".equals(status) && "UP".equals(database);
	}
}
