package com.mystaria.phantasmon.client.auth;

import java.util.Map;

/**
 * Maps backend {@code error_code} values to translation keys (CAD Partie 3
 * §L.1: the client translates locally, never displays server text).
 */
final class BackendErrorMessages {

	private static final Map<String, String> KEYS = Map.of(
			"ERROR_AUTH_MOJANG_VERIFICATION_FAILED", "phantasmon.auth.error.mojang_verification_failed",
			"ERROR_AUTH_UUID_MISMATCH", "phantasmon.auth.error.uuid_mismatch",
			"ERROR_AUTH_INVALID_REFRESH_TOKEN", "phantasmon.auth.error.invalid_refresh_token");

	private BackendErrorMessages() {
	}

	static String translationKey(String errorCode) {
		return KEYS.getOrDefault(errorCode, "phantasmon.error.unknown");
	}
}
