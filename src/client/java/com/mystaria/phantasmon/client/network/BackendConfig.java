package com.mystaria.phantasmon.client.network;

import java.net.URI;

/**
 * Single source of truth for the backend's base URL. Hardcoded to the local
 * dev backend for now (CAD Phase 5 groundwork, same MVP caveat as
 * {@link BackendHealthPinger}) — must become configurable before any real
 * deployment.
 */
public final class BackendConfig {

	public static final URI BASE_URL = URI.create("http://localhost:8080");

	private BackendConfig() {
	}
}
