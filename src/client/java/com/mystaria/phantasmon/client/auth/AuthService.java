package com.mystaria.phantasmon.client.auth;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.mojang.authlib.exceptions.AuthenticationException;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.network.AuthSessionRequestDto;
import com.mystaria.phantasmon.client.network.AuthSessionResponseDto;
import com.mystaria.phantasmon.client.network.BackendApiException;
import com.mystaria.phantasmon.client.network.BackendConfig;
import com.mystaria.phantasmon.client.network.BackendJsonClient;
import com.mystaria.phantasmon.client.network.RefreshRequestDto;
import com.mystaria.phantasmon.client.network.VersionResponseDto;
import com.mystaria.phantasmon.client.version.VersionCompatibility;

/**
 * Orchestrates the client-side auth flow (CAD Partie 2 §3, Partie 3 §E,
 * client Phase 5): {@code GET /version} first, then — if the client isn't
 * flagged incompatible — the Mojang {@code joinServer} proof followed by
 * {@code POST /auth/session}. The JWT is kept only in the in-memory
 * {@link AuthSession}, never on disk (client hard rule 8).
 */
public final class AuthService {

	private static final String MOD_ID = "phantasmon";

	private final BackendJsonClient httpClient;
	private final AuthSession session;

	public AuthService(BackendJsonClient httpClient, AuthSession session) {
		this.httpClient = httpClient;
		this.session = session;
	}

	public void login() {
		if (session.isAuthenticated()) {
			report("phantasmon.auth.already_connected");
			return;
		}

		Minecraft client = Minecraft.getInstance();
		User user = client.getUser();
		if (user.getType() == User.Type.LEGACY) {
			report("phantasmon.auth.offline_unsupported");
			return;
		}

		report("phantasmon.auth.checking_version");
		httpClient.get(BackendConfig.BASE_URL.resolve("/version"), VersionResponseDto.class)
				.thenComposeAsync(version -> handleVersion(version, user, client))
				.exceptionally(this::reportFailure);
	}

	/**
	 * Renews the access token when it's close to expiring. Best-effort and
	 * silent on success; the caller (a periodic hook, once WebSocket/presence
	 * work lands) doesn't need to react either way.
	 */
	public void refreshIfNeeded() {
		if (!session.isAuthenticated() || !session.needsRefresh()) {
			return;
		}
		httpClient.post(BackendConfig.BASE_URL.resolve("/auth/refresh"),
				new RefreshRequestDto(session.refreshToken()), AuthSessionResponseDto.class)
				.thenAccept(response -> session.update(session.playerUuid(), session.username(), response))
				.exceptionally(ex -> {
					session.clear();
					report("phantasmon.auth.session_expired");
					return null;
				});
	}

	private CompletableFuture<Void> handleVersion(VersionResponseDto version, User user, Minecraft client) {
		VersionCompatibility.Status status = VersionCompatibility.evaluate(
				modVersion(), version.minSupportedVersion(), version.currentVersion());

		if (status == VersionCompatibility.Status.INCOMPATIBLE) {
			report("phantasmon.auth.version_incompatible", version.minSupportedVersion());
			return CompletableFuture.completedFuture(null);
		}
		if (status == VersionCompatibility.Status.OUTDATED) {
			report("phantasmon.auth.version_outdated", version.currentVersion());
		}

		return CompletableFuture.supplyAsync(() -> joinMojangServer(user, client))
				.thenCompose(serverId -> httpClient.post(BackendConfig.BASE_URL.resolve("/auth/session"),
						new AuthSessionRequestDto(user.getProfileId(), user.getName(), serverId),
						AuthSessionResponseDto.class))
				.thenAccept(response -> {
					session.update(user.getProfileId(), user.getName(), response);
					report("phantasmon.auth.success");
				});
	}

	private static String joinMojangServer(User user, Minecraft client) {
		String serverId = randomServerId();
		try {
			client.getMinecraftSessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
		} catch (AuthenticationException ex) {
			throw new MojangJoinFailedException(ex);
		}
		return serverId;
	}

	private Void reportFailure(Throwable throwable) {
		Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
		if (cause instanceof MojangJoinFailedException) {
			report("phantasmon.auth.mojang_failed");
		} else if (cause instanceof BackendApiException apiException) {
			report(BackendErrorMessages.translationKey(apiException.errorCode()));
		} else {
			report("phantasmon.auth.network_error");
		}
		return null;
	}

	private static String randomServerId() {
		return new BigInteger(130, new SecureRandom()).toString(32);
	}

	private static String modVersion() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("0.0.0");
	}

	private static void report(String translationKey, Object... args) {
		Minecraft.getInstance().execute(() -> {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				client.player.displayClientMessage(Component.translatable(translationKey, args), false);
			}
		});
	}
}
