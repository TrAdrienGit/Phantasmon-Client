package com.mystaria.phantasmon.client.hub;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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

	/** The caller's anchors, one per hub at most (D-35). */
	public CompletableFuture<HubAnchorDto[]> mine(String bearerToken) {
		return httpClient.get(BackendConfig.BASE_URL.resolve("/hub/anchors/mine"), bearerToken, HubAnchorDto[].class);
	}

	public CompletableFuture<HubAnchorDto> create(String bearerToken, HubAnchorCreateRequestDto request) {
		return httpClient.post(BackendConfig.BASE_URL.resolve("/hub/anchors"), request, bearerToken, HubAnchorDto.class);
	}

	public CompletableFuture<Void> delete(String bearerToken, UUID anchorUuid) {
		return httpClient.delete(BackendConfig.BASE_URL.resolve("/hub/anchors/" + anchorUuid), bearerToken);
	}

	/** Every hub (D-35): name, size, build (D-34) or null. */
	public CompletableFuture<HubDto[]> hubs(String bearerToken) {
		return httpClient.get(BackendConfig.BASE_URL.resolve("/hubs"), bearerToken, HubDto[].class);
	}

	/** A hub's build file itself. */
	public CompletableFuture<byte[]> schematicFile(String bearerToken, String hub) {
		return httpClient.getBytes(BackendConfig.BASE_URL.resolve("/hubs/" + encode(hub) + "/schematic/file"), bearerToken);
	}

	/** Admin: a new hub, {@code length} along the anchor's front, {@code width} across it, {@code height}. */
	public CompletableFuture<HubDto> createHub(String bearerToken, String name, int length, int width, int height) {
		return httpClient.post(BackendConfig.BASE_URL.resolve("/admin/hubs"),
				Map.of("name", name, "length", length, "width", width, "height", height), bearerToken, HubDto.class);
	}

	public record HubDeleted(String hub, int anchorsDeleted, String archivedAs) {
	}

	/** Admin: deletes a hub and its anchors; its folder is archived. */
	public CompletableFuture<HubDeleted> deleteHub(String bearerToken, String name) {
		return httpClient.delete(BackendConfig.BASE_URL.resolve("/admin/hubs/" + encode(name)), bearerToken, HubDeleted.class);
	}

	/** Admin: reads the hub's schematic folder again. */
	public CompletableFuture<HubDto> reloadHub(String bearerToken, String name) {
		return httpClient.postNoBody(BackendConfig.BASE_URL.resolve("/admin/hubs/" + encode(name) + "/reload"), bearerToken, HubDto.class);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
