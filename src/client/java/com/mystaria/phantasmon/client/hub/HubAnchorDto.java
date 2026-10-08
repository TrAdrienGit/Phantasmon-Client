package com.mystaria.phantasmon.client.hub;

import java.util.UUID;

/** Matches the backend's {@code HubAnchor} response schema (Phantasmon Network, {@code /hub/anchors}, D-35). */
public record HubAnchorDto(
		UUID uuid,
		UUID ownerUuid,
		String hub,
		String name,
		String serverFingerprint,
		String dimension,
		Origin origin,
		int yaw,
		Size size) {

	public record Origin(double x, double y, double z) {
	}

	/** The hub's size: {@code x} wide, {@code y} high, {@code z} long. */
	public record Size(int x, int y, int z) {
	}

	public HubCoordinates coordinates() {
		return new HubCoordinates(origin.x(), origin.y(), origin.z(), yaw, size.x(), size.y(), size.z());
	}

	public HubBuildLayout layout() {
		return HubBuildLayout.of(origin.x(), origin.y(), origin.z(), yaw, size.x(), size.y(), size.z());
	}
}
