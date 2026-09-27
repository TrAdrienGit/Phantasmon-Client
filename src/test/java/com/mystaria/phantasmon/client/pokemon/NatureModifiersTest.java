package com.mystaria.phantasmon.client.pokemon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NatureModifiersTest {

	@Test
	void adamantBoostsAttackAndReducesSpecialAttack() {
		NatureModifiers.Modifier modifier = NatureModifiers.get("adamant");
		assertEquals("Atk", modifier.boosted());
		assertEquals("SpA", modifier.reduced());
	}

	@Test
	void neutralNatureHasNoModifier() {
		assertNull(NatureModifiers.get("hardy"));
		assertNull(NatureModifiers.get("docile"));
	}

	@Test
	void lookupIsCaseInsensitive() {
		assertEquals(NatureModifiers.get("jolly"), NatureModifiers.get("JOLLY"));
	}
}
