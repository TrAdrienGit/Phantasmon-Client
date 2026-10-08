package com.mystaria.phantasmon.client.hub;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mystaria.phantasmon.client.network.BackendConfig;
import com.mystaria.phantasmon.client.network.BackendJsonClient;

/** Thin REST wrapper for the backend's Hub Anchors (Phantasmon Network) — every call requires the caller's JWT. */
public final class HubClient {

	private final BackendJsonClient httpClient;

	public HubClient(BackendJsonClient httpClient) {
		this.httpClient = httpClient;
	}

	/** The anchors of one server and dimension (D-30: shared by its players, never seen from another server). */
	public CompletableFuture<HubAnchorDto[]> list(String bearerToken, String serverFingerprint, String dimension) {
		return httpClient.get(BackendConfig.BASE_URL.resolve("/hub/anchors?server_fingerprint=" + encode(serverFingerprint)
				+ "&dimension=" + encode(dimension)), bearerToken, HubAnchorDto[].class);
	}

	public CompletableFuture<HubAnchorDto> mine(String bearerToken) {
		return httpClient.get(BackendConfig.BASE_URL.resolve("/hub/anchors/mine"), bearerToken, HubAnchorDto.class);
	}

	public CompletableFuture<HubAnchorDto> create(String bearerToken, HubAnchorCreateRequestDto request) {
		return httpClient.post(BackendConfig.BASE_URL.resolve("/hub/anchors"), request, bearerToken, HubAnchorDto.class);
	}

	public CompletableFuture<Void> delete(String bearerToken, UUID anchorUuid) {
		return httpClient.delete(BackendConfig.BASE_URL.resolve("/hub/anchors/" + anchorUuid), bearerToken);
	}

	/** The Global Hub's build (D-34): name, format, SHA-256, size. */
	public CompletableFuture<HubSchematicDto> schematic(String bearerToken) {
		return httpClient.get(BackendConfig.BASE_URL.resolve("/hub/schematic"), bearerToken, HubSchematicDto.class);
	}

	/** The build's file itself. */
	public CompletableFuture<byte[]> schematicFile(String bearerToken) {
		return httpClient.getBytes(BackendConfig.BASE_URL.resolve("/hub/schematic/file"), bearerToken);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
