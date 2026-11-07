package com.mystaria.phantasmon.client.pokemon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualItemsTest {

	@Test
	void aZCrystalIsRecognisedWhateverHowItWasStored() {
		// Showdown import stores "Firium Z" as firium_z; the editor stores the Showdown id.
		assertEquals("firiumz", VirtualItems.showdownId("firium_z"));
		assertEquals("firiumz", VirtualItems.showdownId("firiumz"));
		assertEquals("Pikanium Z", VirtualItems.name("pikanium_z"));
		assertEquals("charizarditex", VirtualItems.showdownId("charizardite_x"));
		assertTrue(VirtualItems.isVirtual("booster_energy"));
		assertTrue(VirtualItems.isVirtual("hearthflame_mask"));
		assertEquals(137, VirtualItems.ids().size());
	}

	@Test
	void otherItemsAreNot() {
		assertFalse(VirtualItems.isVirtual("choice_band"));
		assertFalse(VirtualItems.isVirtual(""));
		assertNull(VirtualItems.showdownId(null));
		assertTrue(VirtualItems.isVirtual("cobblemon:normalium_z"));
	}
}
