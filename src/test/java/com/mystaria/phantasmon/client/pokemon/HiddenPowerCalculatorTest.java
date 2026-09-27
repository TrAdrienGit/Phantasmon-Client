package com.mystaria.phantasmon.client.pokemon;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HiddenPowerCalculatorTest {

	@Test
	void allMaxIvsIsHiddenPowerDark() {
		assertEquals("dark", HiddenPowerCalculator.type(ivs(31, 31, 31, 31, 31, 31)));
	}

	@Test
	void allZeroIvsIsHiddenPowerFighting() {
		assertEquals("fighting", HiddenPowerCalculator.type(ivs(0, 0, 0, 0, 0, 0)));
	}

	@Test
	void missingStatReturnsNull() {
		assertNull(HiddenPowerCalculator.type(Map.of("hp", 31, "atk", 31)));
	}

	private static Map<String, Object> ivs(int hp, int atk, int def, int spa, int spd, int spe) {
		return Map.of("hp", hp, "atk", atk, "def", def, "spa", spa, "spd", spd, "spe", spe);
	}
}
