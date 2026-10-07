package com.mystaria.phantasmon.client.hub;

/**
 * Local ⇄ Hub coordinates for one Hub Anchor (Phantasmon Network, network-cahier-des-charges.md §5.4). Pure logic, no
 * Minecraft import, so it stays unit-testable.
 *
 * <p>The anchor's cube is {@code size} blocks wide, centred on the origin in {@code x} / {@code z} and rising from
 * {@code originY}. In the Hub, the anchor's centre is (0, 0) and the direction the anchor faces is the Hub's yaw 0
 * (Minecraft's yaw: 0 = +Z, 90 = -X), so every anchor maps the same shared square whatever way it was built.
 */
public record HubCoordinates(double originX, double originY, double originZ, int yaw, int size) {

	public double halfSize() {
		return size / 2.0;
	}

	/** Whether a local position is inside the anchor's cube. */
	public boolean contains(double x, double y, double z) {
		return Math.abs(x - originX) <= halfSize() && Math.abs(z - originZ) <= halfSize()
				&& y >= originY && y < originY + size;
	}

	/** Local {@code (x, z)} → Hub {@code {hx, hz}}. */
	public double[] toHub(double x, double z) {
		return rotate(x - originX, z - originZ, -yaw);
	}

	/** Hub {@code (hx, hz)} → local {@code {x, z}}. */
	public double[] toLocal(double hx, double hz) {
		double[] offset = rotate(hx, hz, yaw);
		return new double[] { originX + offset[0], originZ + offset[1] };
	}

	public float toHubYaw(float localYaw) {
		return localYaw - yaw;
	}

	public float toLocalYaw(float hubYaw) {
		return hubYaw + yaw;
	}

	/** Keeps a Hub coordinate inside the square the backend accepts. */
	public double clampToSquare(double hubCoordinate) {
		return Math.max(-halfSize(), Math.min(halfSize(), hubCoordinate));
	}

	/** Rotation by a quarter-turn multiple, exact (no floating-point drift from sin/cos). */
	private static double[] rotate(double dx, double dz, int degrees) {
		return switch (Math.floorMod(degrees, 360)) {
			case 90 -> new double[] { -dz, dx };
			case 180 -> new double[] { -dx, -dz };
			case 270 -> new double[] { dz, -dx };
			default -> new double[] { dx, dz };
		};
	}
}
