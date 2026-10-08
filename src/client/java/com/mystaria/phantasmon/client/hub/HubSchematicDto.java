package com.mystaria.phantasmon.client.hub;

/** Matches the backend's {@code GET /hub/schematic} (D-34). {@code format}: {@code SCHEM} or {@code LITEMATIC}. */
public record HubSchematicDto(String name, String format, String sha256, Size size, int bytes) {

	public record Size(int x, int y, int z) {
	}

	public boolean litematic() {
		return "LITEMATIC".equals(format);
	}
}
