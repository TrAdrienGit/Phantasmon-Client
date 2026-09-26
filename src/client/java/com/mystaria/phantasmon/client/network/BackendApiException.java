package com.mystaria.phantasmon.client.network;

import java.util.Map;

/**
 * A structured backend error ({@code {"error_code": ..., "details": {...}}},
 * CAD Partie 3 §L) surfaced as an exception. Callers translate
 * {@link #errorCode()} locally via {@code lang/*.json} — never display
 * {@link #getMessage()} to the player.
 */
public final class BackendApiException extends RuntimeException {

	private final int status;
	private final String errorCode;
	private final Map<String, Object> details;

	public BackendApiException(int status, String errorCode, Map<String, Object> details) {
		super(errorCode);
		this.status = status;
		this.errorCode = errorCode == null ? "ERROR_UNKNOWN" : errorCode;
		this.details = details == null ? Map.of() : details;
	}

	public int status() {
		return status;
	}

	public String errorCode() {
		return errorCode;
	}

	public Map<String, Object> details() {
		return details;
	}
}
