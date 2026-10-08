package com.mystaria.phantasmon.client.hub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phantasmon Network §5.4 — local ⇄ Hub coordinates, for every quarter-turn an anchor can face. */
class HubCoordinatesTest {

	private static final double EPSILON = 1e-9;

	private static HubCoordinates anchor(int yaw) {
		return new HubCoordinates(100.5, 64.0, -20.5, yaw, 21, 21, 21);
	}

	@Test
	void anAnchorFacingSouthIsATranslation() {
		double[] hub = anchor(0).toHub(103.5, -25.5);
		assertEquals(3.0, hub[0], EPSILON);
		assertEquals(-5.0, hub[1], EPSILON);
	}

	@Test
	void theAnchorsForwardIsTheHubsForwardWhateverItFaces() {
		for (int yaw : new int[] { 0, 90, 180, 270 }) {
			HubCoordinates anchor = anchor(yaw);
			// One block ahead of the anchor's centre, in the direction it faces (Minecraft: yaw 0 = +Z, 90 = -X).
			double radians = Math.toRadians(yaw);
			double[] hub = anchor.toHub(100.5 - Math.sin(radians), -20.5 + Math.cos(radians));
			assertEquals(0.0, hub[0], EPSILON, "yaw " + yaw);
			assertEquals(1.0, hub[1], EPSILON, "yaw " + yaw);
			assertEquals(30f, anchor.toHubYaw(yaw + 30f), 1e-4f, "yaw " + yaw);
		}
	}

	@Test
	void hubToLocalUndoesLocalToHub() {
		for (int yaw : new int[] { 0, 90, 180, 270 }) {
			HubCoordinates anchor = anchor(yaw);
			double[] hub = anchor.toHub(95.25, -13.0);
			double[] local = anchor.toLocal(hub[0], hub[1]);
			assertEquals(95.25, local[0], EPSILON, "yaw " + yaw);
			assertEquals(-13.0, local[1], EPSILON, "yaw " + yaw);
			assertEquals(-42f, anchor.toLocalYaw(anchor.toHubYaw(-42f)), 1e-4f);
		}
	}

	@Test
	void twoAnchorsFacingDifferentWaysShareTheSameHubSpot() {
		HubCoordinates south = new HubCoordinates(0.5, 70, 0.5, 0, 21, 21, 21);
		HubCoordinates west = new HubCoordinates(500.5, 12, -300.5, 90, 21, 21, 21);
		// Two blocks ahead and one to the anchor's left, on both servers.
		double[] a = south.toHub(0.5 + 1, 0.5 + 2);
		double[] b = west.toHub(500.5 - 2, -300.5 + 1);
		assertEquals(a[0], b[0], EPSILON);
		assertEquals(a[1], b[1], EPSILON);
	}

	@Test
	void theCubeIsCentredHorizontallyAndRisesFromTheOrigin() {
		HubCoordinates anchor = anchor(90);
		assertTrue(anchor.contains(100.5, 64.0, -20.5));
		assertTrue(anchor.contains(110.9, 64.0, -30.9));
		assertFalse(anchor.contains(111.1, 64.0, -20.5));
		assertFalse(anchor.contains(100.5, 63.9, -20.5));
		assertTrue(anchor.contains(100.5, 84.9, -20.5));
		assertFalse(anchor.contains(100.5, 85.0, -20.5));
	}

	@Test
	void hubPositionsAreClampedToTheBoxTheBackendAccepts() {
		HubCoordinates anchor = new HubCoordinates(0, 64, 0, 0, 5, 4, 9);
		assertEquals(2.5, anchor.clampX(12.0));
		assertEquals(-2.5, anchor.clampX(-11.0));
		assertEquals(3.0, anchor.clampZ(3.0));
		assertEquals(-4.5, anchor.clampZ(-6.0));
	}

	@Test
	void aLongHubTurnsWithItsAnchor() {
		// D-35: 5 wide, 9 long, 4 high; facing west (yaw 90) its length runs along -X / +X.
		HubCoordinates west = new HubCoordinates(0.5, 64, 0.5, 90, 5, 4, 9);
		assertTrue(west.contains(0.5 - 4.4, 64, 0.5));
		assertFalse(west.contains(0.5, 64, 0.5 - 4.4));
		assertTrue(west.contains(0.5, 67.9, 0.5 + 2.4));
		assertFalse(west.contains(0.5, 68.0, 0.5));
		assertEquals(4.5, west.radius());
	}
}
