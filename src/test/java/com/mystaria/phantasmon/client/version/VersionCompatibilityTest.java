package com.mystaria.phantasmon.client.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionCompatibilityTest {

	@Test
	void belowMinSupportedIsIncompatible() {
		assertEquals(VersionCompatibility.Status.INCOMPATIBLE,
				VersionCompatibility.evaluate("1.2.0", "1.3.0", "1.4.2"));
	}

	@Test
	void equalToMinSupportedButBehindCurrentIsOutdated() {
		assertEquals(VersionCompatibility.Status.OUTDATED,
				VersionCompatibility.evaluate("1.3.0", "1.3.0", "1.4.2"));
	}

	@Test
	void equalToCurrentIsUpToDate() {
		assertEquals(VersionCompatibility.Status.UP_TO_DATE,
				VersionCompatibility.evaluate("1.4.2", "1.3.0", "1.4.2"));
	}

	@Test
	void aheadOfCurrentIsUpToDate() {
		assertEquals(VersionCompatibility.Status.UP_TO_DATE,
				VersionCompatibility.evaluate("2.0.0", "1.3.0", "1.4.2"));
	}

	@Test
	void differingSegmentCountsAreComparedAsIfZeroPadded() {
		assertEquals(VersionCompatibility.Status.UP_TO_DATE,
				VersionCompatibility.evaluate("1.4", "1.3.0", "1.4.0"));
		assertEquals(VersionCompatibility.Status.OUTDATED,
				VersionCompatibility.evaluate("1.4", "1.3.0", "1.4.0.1"));
	}

	@Test
	void nonNumericSuffixIsIgnored() {
		assertEquals(VersionCompatibility.Status.UP_TO_DATE,
				VersionCompatibility.evaluate("1.4.2-SNAPSHOT", "1.3.0", "1.4.2"));
	}
}
