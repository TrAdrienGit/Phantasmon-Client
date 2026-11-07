package com.mystaria.phantasmon.client.pokemon.showdown;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon.client.pokemon.showdown.CobblemonIdentifiers.SpeciesForm;

class HyphenatedSpeciesTest {

	private static void check(String token, String species, String form) {
		assertEquals(new SpeciesForm(species, form), CobblemonIdentifiers.splitSpeciesForm(token));
	}

	@Test
	void hyphenInTheName() {
		check("Ting-Lu", "tinglu", null);
		check("Chien-Pao", "chienpao", null);
		check("Wo-Chien", "wochien", null);
		check("Chi-Yu", "chiyu", null);
		check("Kommo-o", "kommoo", null);
		check("Kommo-o-Totem", "kommoo", "totem");
	}

	@Test
	void hyphensInTheForm() {
		check("Samurott-Hisui", "samurott", "hisui");
		check("Urshifu-Rapid-Strike", "urshifu", "rapidstrike");
		check("Tauros-Paldea-Aqua", "tauros", "paldeaaqua");
	}

	@Test
	void accent() {
		check("Flabébé", "flabebe", null);
	}
}
