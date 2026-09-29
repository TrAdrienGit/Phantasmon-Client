package com.mystaria.phantasmon.client.network;

import java.net.URI;

/**
 * Single source of truth for the backend's base URL. Points at the dev
 * machine's Tailscale IP (Adrien: 2026-09-29, first real multi-client test —
 * the backend/Postgres only run on the dev machine for now, not on the
 * "production-server" machine, even though that one also has the repos +
 * an NSSM-managed backend service; both Minecraft clients — dev machine's
 * "MystAria_" and production-server's "TheMashen" — must point at the same
 * single backend instance to share player/trade/presence data, so this one
 * constant is what both builds get via the shared deploy script). Still
 * hardcoded rather than user-configurable — must become configurable before
 * any real deployment beyond this test setup.
 */
public final class BackendConfig {

	public static final URI BASE_URL = URI.create("http://100.116.43.32:8080");

	private BackendConfig() {
	}
}
