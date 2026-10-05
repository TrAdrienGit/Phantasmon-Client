package com.mystaria.phantasmon.client.battle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayedPacketPolicyTest {

	@Test
	void battlePacketsAndMoveAnimationsAreAccepted() {
		assertTrue(RelayedPacketPolicy.guestAccepts("cobblemon:battle_initialize"));
		assertTrue(RelayedPacketPolicy.guestAccepts("cobblemon:battle_queue_request"));
		assertTrue(RelayedPacketPolicy.guestAccepts("phantasmon:action_effect"));
		assertTrue(RelayedPacketPolicy.guestAccepts("phantasmon:forme_change"));
	}

	@Test
	void anythingElseFromTheHostIsRefused() {
		assertFalse(RelayedPacketPolicy.guestAccepts("cobblemon:set_client_playerdata"));
		assertFalse(RelayedPacketPolicy.guestAccepts("cobblemon:initialize_party"));
		assertFalse(RelayedPacketPolicy.guestAccepts("othermod:battle_initialize"));
		assertFalse(RelayedPacketPolicy.guestAccepts(""));
		assertFalse(RelayedPacketPolicy.guestAccepts(null));
	}

	@Test
	void theMegaAspectFollowsTheStone() {
		org.junit.jupiter.api.Assertions.assertEquals("mega_x", FormeChangeVisual.megaAspect("charizarditex"));
		org.junit.jupiter.api.Assertions.assertEquals("mega_y", FormeChangeVisual.megaAspect("mewtwonitey"));
		org.junit.jupiter.api.Assertions.assertEquals("mega", FormeChangeVisual.megaAspect("venusaurite"));
		org.junit.jupiter.api.Assertions.assertEquals("mega", FormeChangeVisual.megaAspect(null));
	}
}
