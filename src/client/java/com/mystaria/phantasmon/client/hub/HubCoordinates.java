package com.mystaria.phantasmon.client.hub;

/**
 * Local ⇄ Hub coordinates for one Hub Anchor (Phantasmon Network, network-cahier-des-charges.md §5.4). Pure logic, no
 * Minecraft import, so it stays unit-testable.
 *
 * <p>The anchor's box is its hub's size (D-35): {@code sizeX} wide across the anchor, {@code sizeZ} long along its
 * front, {@code sizeY} high, centred on the origin horizontally and rising from {@code originY}. In the Hub, the
 * anchor's centre is (0, 0) and the direction the anchor faces is the Hub's yaw 0 (Minecraft's yaw: 0 = +Z, 90 = -X),
 * so every anchor maps the same shared space whatever way it was built.
 */
public record HubCoordinates(double originX, double originY, double originZ, int yaw, int sizeX, int sizeY, int sizeZ) {

	public double halfX() {
		return sizeX / 2.0;
	}

	public double halfZ() {
		return sizeZ / 2.0;
	}

	/** Half the box's longest side: how far from the origin it reaches at most, horizontally. */
	public double radius() {
		return Math.max(halfX(), halfZ());
	}

	/** Whether a local position is inside the anchor's box. */
	public boolean contains(double x, double y, double z) {
		double[] hub = toHub(x, z);
		return Math.abs(hub[0]) <= halfX() && Math.abs(hub[1]) <= halfZ() && y >= originY && y < originY + sizeY;
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

	/** Keeps a Hub {@code x} inside the box the backend accepts. */
	public double clampX(double hubX) {
		return Math.max(-halfX(), Math.min(halfX(), hubX));
	}

	/** Keeps a Hub {@code z} inside the box the backend accepts. */
	public double clampZ(double hubZ) {
		return Math.max(-halfZ(), Math.min(halfZ(), hubZ));
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
