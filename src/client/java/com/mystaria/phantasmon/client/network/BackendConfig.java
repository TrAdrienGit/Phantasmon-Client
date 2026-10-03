package com.mystaria.phantasmon.client.network;

import java.net.URI;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Single source of truth for the backend's base URL, read once at startup from {@code config/phantasmon.json}
 * ({@code "backend_url"}, {@code http(s)://host[:port]}) by {@link BackendUrlFile}. The file is created on first
 * launch; editing it takes effect at the next game launch.
 *
 * <p>The default is the dev machine's Tailscale IP (Adrien: 2026-09-29): both test clients — dev machine's
 * "MystAria_" and production-server's "TheMashen" — must reach the same single backend instance.
 */
public final class BackendConfig {

	public static final URI BASE_URL = BackendUrlFile.load(FabricLoader.getInstance().getConfigDir().resolve("phantasmon.json"));

	private BackendConfig() {
	}
}
