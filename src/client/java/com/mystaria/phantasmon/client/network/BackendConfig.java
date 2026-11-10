package com.mystaria.phantasmon.client.network;

import java.net.URI;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Single source of truth for the backend's base URL, read once at startup from {@code config/phantasmon.json}
 * ({@code "backend_url"}, {@code http(s)://host[:port]}) by {@link BackendUrlFile}. The file is created on first
 * launch; editing it takes effect at the next game launch.
 *
 * <p>The default is our server's public address ({@link BackendUrlFile#DEFAULT_URL}); the settings screen
 * (Phantasmon menu, Settings page) changes it for the next launch.
 */
public final class BackendConfig {

	public static final java.nio.file.Path FILE = FabricLoader.getInstance().getConfigDir().resolve("phantasmon.json");
	public static final URI BASE_URL = BackendUrlFile.load(FILE);

	private BackendConfig() {
	}
}
