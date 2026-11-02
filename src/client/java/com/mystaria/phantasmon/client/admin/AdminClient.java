package com.mystaria.phantasmon.client.admin;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mystaria.phantasmon.client.network.BackendConfig;
import com.mystaria.phantasmon.client.network.BackendJsonClient;

/** The backend's admin endpoints (TODO-25). Every one but {@link #me} answers 403 to a non-admin. */
public final class AdminClient {

	public record Me(boolean admin) {
	}

	public record PlayerRef(UUID uuid, String name) {
	}

	public record Stopped(String stopped, String player) {
	}

	private final BackendJsonClient http;

	public AdminClient(BackendJsonClient http) {
		this.http = http;
	}

	public CompletableFuture<Me> me(String token) {
		return http.get(BackendConfig.BASE_URL.resolve("/admin/me"), token, Me.class);
	}

	public CompletableFuture<PlayerRef> player(String token, String name) {
		return http.get(BackendConfig.BASE_URL.resolve("/admin/players/" + URLEncoder.encode(name, StandardCharsets.UTF_8)),
				token, PlayerRef.class);
	}

	public CompletableFuture<Stopped> stopBattle(String token, String playerName) {
		return http.post(BackendConfig.BASE_URL.resolve("/admin/battles/stop"), Map.of("player", playerName), token, Stopped.class);
	}

	public record Muted(String player, String until) {
	}

	public record Unmuted(String player, boolean was_muted) {
	}

	public record Locked(boolean locked) {
	}

	/** Hub chat moderation (TODO-31). */
	public CompletableFuture<Muted> hubMute(String token, String playerName, long minutes) {
		return http.post(BackendConfig.BASE_URL.resolve("/admin/hub/mute"), Map.of("player", playerName, "minutes", minutes), token, Muted.class);
	}

	public CompletableFuture<Unmuted> hubUnmute(String token, String playerName) {
		return http.post(BackendConfig.BASE_URL.resolve("/admin/hub/unmute"), Map.of("player", playerName), token, Unmuted.class);
	}

	public CompletableFuture<Locked> hubLock(String token) {
		return http.post(BackendConfig.BASE_URL.resolve("/admin/hub/lock"), Map.of(), token, Locked.class);
	}

	public CompletableFuture<Map> reboot(String token) {
		return http.postNoBody(BackendConfig.BASE_URL.resolve("/admin/reboot"), token, Map.class);
	}
}
