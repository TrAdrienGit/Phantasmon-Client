package com.mystaria.phantasmon.client.hub;

import java.util.UUID;

/**
 * Matches the backend's {@code GET /admin/hubs} entries (hub manager, admins): a hub of any kind, its size and build,
 * whether its kind's add-on is loaded ({@code available}), when and by whom it was created, how many anchors lead to
 * it. {@code mirror} / {@code radius}: fields Phantasmon Events adds to its mirror hubs (D-38), absent otherwise.
 */
public record HubAdminDto(String name, String kind, HubAnchorDto.Size size, HubDto.Schematic schematic, boolean available,
		String createdAt, UUID createdBy, String createdByName, int anchors, boolean mirror, Integer radius) {

	public boolean normal() {
		return kind == null || "NORMAL".equals(kind);
	}

	/** An anchor leading to a hub, as the hub manager lists it ({@code GET /admin/hubs/{name}/anchors}). */
	public record Anchor(UUID uuid, String name, UUID ownerUuid, String ownerName, String serverFingerprint,
			String dimension, double x, double y, double z, int yaw, String role, String createdAt) {
	}
}
