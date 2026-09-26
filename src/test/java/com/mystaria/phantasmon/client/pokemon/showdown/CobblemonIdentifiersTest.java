package com.mystaria.phantasmon.client.pokemon.showdown;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon.client.pokemon.showdown.CobblemonIdentifiers.SpeciesForm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CobblemonIdentifiersTest {

	@Test
	void regionalFormSplitsOnLastHyphen() {
		SpeciesForm result = CobblemonIdentifiers.splitSpeciesForm("Samurott-Hisui");
		assertEquals("samurott", result.species());
		assertEquals("hisui", result.form());
	}

	@Test
	void plainSpeciesHasNoForm() {
		SpeciesForm result = CobblemonIdentifiers.splitSpeciesForm("Pikachu");
		assertEquals("pikachu", result.species());
		assertNull(result.form());
	}

	@Test
	void knownHyphenatedSpeciesIsNotSplit() {
		assertEquals("hooh", CobblemonIdentifiers.splitSpeciesForm("Ho-Oh").species());
		assertEquals("porygonz", CobblemonIdentifiers.splitSpeciesForm("Porygon-Z").species());
		assertEquals("nidoranf", CobblemonIdentifiers.splitSpeciesForm("Nidoran-F").species());
		assertEquals("kommoo", CobblemonIdentifiers.splitSpeciesForm("Kommo-o").species());
	}

	@Test
	void mimeJrConcatenatesWithNoSeparator() {
		assertEquals("mimejr", CobblemonIdentifiers.slugConcat("Mime Jr."));
	}

	@Test
	void moveNameConcatenatesLikeShowdownToId() {
		assertEquals("closecombat", CobblemonIdentifiers.slugConcat("Close Combat"));
		assertEquals("bodyslam", CobblemonIdentifiers.slugConcat("Body Slam"));
	}

	@Test
	void abilityAndItemUseUnderscoreSeparator() {
		assertEquals("flash_fire", CobblemonIdentifiers.slugUnderscore("Flash Fire"));
		assertEquals("assault_vest", CobblemonIdentifiers.slugUnderscore("Assault Vest"));
		assertEquals("choice_band", CobblemonIdentifiers.slugUnderscore("Choice Band"));
	}
}
