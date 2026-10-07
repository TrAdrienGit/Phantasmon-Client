package com.mystaria.phantasmon.client.hub;

import java.util.UUID;

/** Matches {@code POST /hub/anchors}; the backend snaps {@code yaw} to a quarter turn. */
public record HubAnchorCreateRequestDto(
		UUID requestUuid,
		String name,
		String serverFingerprint,
		String dimension,
		HubAnchorDto.Origin origin,
		double yaw) {
}
