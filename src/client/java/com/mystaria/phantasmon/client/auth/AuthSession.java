package com.mystaria.phantasmon.client.auth;

import java.time.Instant;
import java.util.UUID;

import com.mystaria.phantasmon.client.network.AuthSessionResponseDto;

/**
 * In-memory holder for the current JWT session (CAD Partie 2 §3, client hard
 * rule 8: JWT kept in memory only, never persisted to disk). Cleared on
 * disconnect. Access is synchronized since network callbacks land off the
 * client thread.
 */
public final class AuthSession {

	private static final long REFRESH_SAFETY_MARGIN_SECONDS = 30;

	private UUID playerUuid;
	private String username;
	private String accessToken;
	private String refreshToken;
	private Instant accessTokenExpiresAt;

	public synchronized void update(UUID playerUuid, String username, AuthSessionResponseDto response) {
		this.playerUuid = playerUuid;
		this.username = username;
		this.accessToken = response.accessToken();
		this.refreshToken = response.refreshToken();
		this.accessTokenExpiresAt = Instant.now().plusSeconds(response.expiresIn());
	}

	public synchronized void clear() {
		playerUuid = null;
		username = null;
		accessToken = null;
		refreshToken = null;
		accessTokenExpiresAt = null;
	}

	public synchronized boolean isAuthenticated() {
		return accessToken != null;
	}

	public synchronized boolean needsRefresh() {
		return accessTokenExpiresAt != null
				&& Instant.now().isAfter(accessTokenExpiresAt.minusSeconds(REFRESH_SAFETY_MARGIN_SECONDS));
	}

	public synchronized String accessToken() {
		return accessToken;
	}

	public synchronized String refreshToken() {
		return refreshToken;
	}

	public synchronized UUID playerUuid() {
		return playerUuid;
	}

	public synchronized String username() {
		return username;
	}
}
