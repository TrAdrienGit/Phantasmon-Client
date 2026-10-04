package com.mystaria.phantasmon.client.pokemon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CobblemonDataVersionTest {

	@Test
	void buildMetadataIsDropped() {
		assertEquals("1.8.1", CobblemonDataVersion.normalize("1.8.1+1.21.1"));
		assertEquals("1.9.0-beta.2", CobblemonDataVersion.normalize("1.9.0-beta.2+1.21.4"));
		assertEquals("1.8.1", CobblemonDataVersion.normalize("1.8.1"));
	}

	@Test
	void unreadableVersionsFallBack() {
		assertEquals(CobblemonDataVersion.FALLBACK, CobblemonDataVersion.normalize(null));
		assertEquals(CobblemonDataVersion.FALLBACK, CobblemonDataVersion.normalize("+build"));
		assertEquals(32, CobblemonDataVersion.normalize("9".repeat(40)).length());
	}
}
