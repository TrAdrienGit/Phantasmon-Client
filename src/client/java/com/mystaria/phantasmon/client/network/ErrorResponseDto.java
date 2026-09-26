package com.mystaria.phantasmon.client.network;

import java.util.Map;

/** Matches the backend's structured error body (CAD Partie 3 §L). */
record ErrorResponseDto(String errorCode, Map<String, Object> details) {
}
