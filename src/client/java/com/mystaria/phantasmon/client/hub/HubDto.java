package com.mystaria.phantasmon.client.hub;

/**
 * Matches the backend's {@code GET /hubs} entries (D-35): a hub, its size ({@code x} wide, {@code y} high, {@code z}
 * long) and its build, null when it has none (D-34).
 */
public record HubDto(String name, HubAnchorDto.Size size, Schematic schematic) {

	/** {@code format}: {@code SCHEM} or {@code LITEMATIC}. */
	public record Schematic(String name, String format, String sha256, int bytes) {

		public boolean litematic() {
			return "LITEMATIC".equals(format);
		}
	}
}
