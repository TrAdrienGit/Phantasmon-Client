package com.mystaria.phantasmon.client.pokemon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PokemonGenderTest {

	@Test
	void storedShowdownGenderWins() {
		assertEquals(PokemonGender.MALE, PokemonGender.resolve("M", 0f));
		assertEquals(PokemonGender.FEMALE, PokemonGender.resolve("f", 1f));
		assertEquals(PokemonGender.FEMALE, PokemonGender.resolve("FEMALE", null));
	}

	@Test
	void speciesRatioSettlesSingleGenderAndGenderlessSpecies() {
		assertEquals(PokemonGender.GENDERLESS, PokemonGender.resolve(null, -1f));
		assertEquals(PokemonGender.MALE, PokemonGender.resolve(null, 1f));
		assertEquals(PokemonGender.FEMALE, PokemonGender.resolve(null, 0f));
	}

	@Test
	void mixedRatioWithoutStoredGenderIsNeverGuessed() {
		assertEquals(PokemonGender.UNKNOWN, PokemonGender.resolve(null, 0.5f));
		assertEquals(PokemonGender.UNKNOWN, PokemonGender.resolve("?", 0.875f));
		assertEquals(PokemonGender.UNKNOWN, PokemonGender.resolve(null, null));
	}
}
