package com.mystaria.phantasmon.client.network;

import java.net.URI;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Single source of truth for the backend's base URL, read once at startup from {@code config/phantasmon.json}
 * ({@code "backend_url"}, {@code http(s)://host[:port]}) by {@link BackendUrlFile}. The file is created on first
 * launch. The settings screen changes it live ({@link #use}, 2026-11-25): the session on the old backend is closed and
 * a login on the new one starts at once, no restart. Editing the file by hand still waits for the next launch.
 *
 * <p>There is no default (2026-11-25): until the player enters their community's address on the settings screen
 * (Phantasmon menu, Settings page), {@link #BASE_URL} is null and the mod opens no connection at all.
 */
public final class BackendConfig {

	public static final java.nio.file.Path FILE = FabricLoader.getInstance().getConfigDir().resolve("phantasmon.json");
	/** Null when no backend is set: check {@link #isSet()} before any request. Changed only by {@link #use}. */
	public static volatile URI BASE_URL = BackendUrlFile.load(FILE);

	/** What switching backends does (Phantasmon's client: leave the old one's session, log in to the new one). */
	private static Runnable onChange = () -> {
	};

	public static void setOnChange(Runnable onChange) {
		BackendConfig.onChange = onChange;
	}

	/**
	 * Uses {@code url} from now on (saved by the settings screen): the old backend's session ends and a login on the
	 * new one starts. Called with the address already in use, it only logs in again if not connected. Client thread.
	 */
	public static void use(URI url) {
		BASE_URL = url;
		onChange.run();
	}

	/** Whether a backend address is set (no connection is ever attempted otherwise). */
	public static boolean isSet() {
		return BASE_URL != null;
	}

	private BackendConfig() {
	}
}
