package com.mystaria.phantasmon.client.hub;

/**
 * Where an anchor's build stands (D-34, D-35). Pure logic, no Minecraft import, so it stays unit-testable; the backend's
 * {@code HubBox} computes the same boxes (overlap check).
 *
 * <p>The build is block-aligned: its centre column is the block the anchor was posed from ({@code floor} of the
 * origin's {@code x} / {@code z}), its bottom layer the block the player's feet were in (the origin's {@code y},
 * rounded up so a player on a slab starts on the next block). The schematic's bottom-layer centre (block
 * {@code sizeX / 2}, {@code sizeZ / 2}) is that point, and the schematic turns with the anchor by quarter-turns,
 * clockwise like Minecraft's yaw (the schematic's +Z is the direction the anchor faces, as in {@link HubCoordinates}).
 */
public record HubBuildLayout(int centerX, int baseY, int centerZ, int yaw, int sizeX, int sizeY, int sizeZ) {

	public static HubBuildLayout of(double originX, double originY, double originZ, int yaw, int sizeX, int sizeY, int sizeZ) {
		return new HubBuildLayout((int) Math.floor(originX), (int) Math.ceil(originY - 1.0E-4), (int) Math.floor(originZ),
				Math.floorMod(yaw, 360), sizeX, sizeY, sizeZ);
	}

	/** Number of clockwise quarter-turns (0 to 3). */
	public int quarterTurns() {
		return yaw / 90 % 4;
	}

	/** Schematic block {@code (x, y, z)} → world block {@code {x, y, z}}. */
	public int[] toWorld(int x, int y, int z) {
		int dx = x - sizeX / 2;
		int dz = z - sizeZ / 2;
		return switch (quarterTurns()) {
			case 1 -> new int[] { centerX - dz, baseY + y, centerZ + dx };
			case 2 -> new int[] { centerX - dx, baseY + y, centerZ - dz };
			case 3 -> new int[] { centerX + dz, baseY + y, centerZ - dx };
			default -> new int[] { centerX + dx, baseY + y, centerZ + dz };
		};
	}

	/** The box's lowest corner, inclusive — with {@link #max()}, what must be air when the anchor is posed. */
	public int[] min() {
		int[] a = toWorld(0, 0, 0);
		int[] b = toWorld(sizeX - 1, sizeY - 1, sizeZ - 1);
		return new int[] { Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]) };
	}

	/** The box's highest corner, inclusive. */
	public int[] max() {
		int[] a = toWorld(0, 0, 0);
		int[] b = toWorld(sizeX - 1, sizeY - 1, sizeZ - 1);
		return new int[] { Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]) };
	}
}
