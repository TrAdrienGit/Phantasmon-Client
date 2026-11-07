package com.mystaria.phantasmon.client.pokemon.showdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon.client.pokemon.PokemonCreateRequestDto;

class ShowdownMegaImportTest {

	private static PokemonCreateRequestDto map(String token, String ability, List<String> abilities) {
		ShowdownPokemon set = ShowdownParser.parseSingle(token + " @ Item\nAbility: " + ability + "\n- Tackle");
		return ShowdownImportMapper.toCreateRequest(set, "1.8.1", species -> abilities);
	}

	@Test
	void megaBecomesBaseWithABaseAbility() {
		PokemonCreateRequestDto request = map("Mawile-Mega", "Huge Power", List.of("hypercutter", "intimidate", "sheerforce"));
		assertEquals("mawile", request.species());
		assertNull(request.form());
		assertEquals("hypercutter", request.ability());
		assertEquals("item", request.data().get("held_item"));
	}

	@Test
	void abilityTheBaseAlsoHasIsKept() {
		assertEquals("sheerforce", map("Mawile-Mega", "Sheer Force", List.of("hypercutter", "sheerforce")).ability());
	}

	@Test
	void otherBattleFormsAndOrdinaryForms() {
		assertNull(map("Charizard-Mega-X", "Tough Claws", List.of("blaze")).form());
		assertNull(map("Kyogre-Primal", "Primordial Sea", List.of("drizzle")).form());
		assertEquals("hisui", map("Samurott-Hisui", "Torrent", List.of("torrent")).form());
		assertEquals("torrent", map("Samurott-Hisui", "Torrent", List.of("other")).ability());
	}
}
