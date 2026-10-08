package com.mystaria.phantasmon.client.hub;

/**
 * Where an anchor's build stands (D-34). Pure logic, no Minecraft import, so it stays unit-testable.
 *
 * <p>The build is block-aligned: its centre column is the block the anchor was posed from ({@code floor} of the
 * origin's {@code x} / {@code z}), its bottom layer the block the player's feet were in (the origin's {@code y},
 * rounded up so a player on a slab starts on the next block). The schematic's bottom-layer centre is that point, and
 * the schematic turns with the anchor by quarter-turns, clockwise like Minecraft's yaw (the schematic's +Z is the
 * direction the anchor faces, as in {@link HubCoordinates}).
 */
public record HubBuildLayout(int centerX, int baseY, int centerZ, int yaw, int size) {

	public static HubBuildLayout of(double originX, double originY, double originZ, int yaw, int size) {
		return new HubBuildLayout((int) Math.floor(originX), (int) Math.ceil(originY - 1.0E-4), (int) Math.floor(originZ),
				Math.floorMod(yaw, 360), size);
	}

	public int half() {
		return size / 2;
	}

	/** Number of clockwise quarter-turns (0 to 3). */
	public int quarterTurns() {
		return yaw / 90 % 4;
	}

	/** Schematic block {@code (x, y, z)} → world block {@code {x, y, z}}. */
	public int[] toWorld(int x, int y, int z) {
		int dx = x - half();
		int dz = z - half();
		return switch (quarterTurns()) {
			case 1 -> new int[] { centerX - dz, baseY + y, centerZ + dx };
			case 2 -> new int[] { centerX - dx, baseY + y, centerZ - dz };
			case 3 -> new int[] { centerX + dz, baseY + y, centerZ - dx };
			default -> new int[] { centerX + dx, baseY + y, centerZ + dz };
		};
	}

	/** The cube's lowest corner and highest corner, inclusive — what must be air when the anchor is posed. */
	public int[] min() {
		return new int[] { centerX - half(), baseY, centerZ - half() };
	}

	public int[] max() {
		return new int[] { centerX - half() + size - 1, baseY + size - 1, centerZ - half() + size - 1 };
	}
}
