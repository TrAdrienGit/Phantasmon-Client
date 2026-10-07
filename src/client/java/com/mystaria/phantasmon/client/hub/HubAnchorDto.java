package com.mystaria.phantasmon.client.hub;

import java.util.UUID;

/** Matches the backend's {@code HubAnchor} response schema (Phantasmon Network, {@code /hub/anchors}). */
public record HubAnchorDto(
		UUID uuid,
		UUID ownerUuid,
		String name,
		String serverFingerprint,
		String dimension,
		Origin origin,
		int yaw,
		int size) {

	public record Origin(double x, double y, double z) {
	}

	public HubCoordinates coordinates() {
		return new HubCoordinates(origin.x(), origin.y(), origin.z(), yaw, size);
	}
}
