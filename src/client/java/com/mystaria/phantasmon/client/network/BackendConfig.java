package com.mystaria.phantasmon.client.network;

import java.net.URI;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Single source of truth for the backend's base URL, read once at startup from {@code config/phantasmon.json}
 * ({@code "backend_url"}, {@code http(s)://host[:port]}) by {@link BackendUrlFile}. The file is created on first
 * launch; editing it takes effect at the next game launch.
 *
 * <p>There is no default (2026-11-25): until the player enters their community's address on the settings screen
 * (Phantasmon menu, Settings page), {@link #BASE_URL} is null and the mod opens no connection at all.
 */
public final class BackendConfig {

	public static final java.nio.file.Path FILE = FabricLoader.getInstance().getConfigDir().resolve("phantasmon.json");
	/** Null when no backend is set: check {@link #isSet()} before any request. */
	public static final URI BASE_URL = BackendUrlFile.load(FILE);

	/** Whether a backend address is set (no connection is ever attempted otherwise). */
	public static boolean isSet() {
		return BASE_URL != null;
	}

	private BackendConfig() {
	}
}
