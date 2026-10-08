package com.mystaria.phantasmon.client.hub;

import java.util.UUID;

/** Matches {@code POST /hub/anchors}; the backend snaps {@code yaw} to a quarter turn. {@code hub}: its name (D-35). */
public record HubAnchorCreateRequestDto(
		UUID requestUuid,
		String hub,
		String name,
		String serverFingerprint,
		String dimension,
		HubAnchorDto.Origin origin,
		double yaw) {
}
