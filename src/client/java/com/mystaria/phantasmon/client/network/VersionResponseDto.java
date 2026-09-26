package com.mystaria.phantasmon.client.network;

/** Matches the backend {@code GET /version} response (CAD Partie 3 §E). */
public record VersionResponseDto(String currentVersion, String minSupportedVersion) {
}
