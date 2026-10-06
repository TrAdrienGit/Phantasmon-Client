package com.mystaria.phantasmon.client.pokemon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZCrystalsTest {

	@Test
	void aZCrystalIsRecognisedWhateverHowItWasStored() {
		// Showdown import stores "Firium Z" as firium_z; the editor stores the Showdown id.
		assertEquals("firiumz", ZCrystals.showdownId("firium_z"));
		assertEquals("firiumz", ZCrystals.showdownId("firiumz"));
		assertEquals("Pikanium Z", ZCrystals.name("pikanium_z"));
		assertEquals(35, ZCrystals.ids().size());
	}

	@Test
	void otherItemsAreNot() {
		assertFalse(ZCrystals.isZCrystal("choice_band"));
		assertFalse(ZCrystals.isZCrystal(""));
		assertNull(ZCrystals.showdownId(null));
		assertTrue(ZCrystals.isZCrystal("cobblemon:normalium_z"));
	}
}
