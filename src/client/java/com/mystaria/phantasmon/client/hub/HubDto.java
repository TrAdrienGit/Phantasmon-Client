package com.mystaria.phantasmon.client.hub;

/**
 * Matches the backend's {@code GET /hubs} entries (D-35): a hub, its size ({@code x} wide, {@code y} high, {@code z}
 * long) and its build, null when it has none (D-34). {@code mirror}: a mirror hub (D-38), terrain copied from a server
 * zone by Phantasmon Events, never a build: a square of side {@code 2 * radius + 1}, the whole height of the world.
 */
public record HubDto(String name, HubAnchorDto.Size size, Schematic schematic, boolean mirror, Integer radius) {

	/** {@code format}: {@code SCHEM} or {@code LITEMATIC}. */
	public record Schematic(String name, String format, String sha256, int bytes) {

		public boolean litematic() {
			return "LITEMATIC".equals(format);
		}
	}
}
