package com.mystaria.phantasmon.client.pokemon.showdown;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShowdownParserTest {

	private static final String CAD_EXAMPLE = """
			Bichou (Samurott-Hisui) @ Assault Vest
			Ability: Torrent
			Shiny: Yes
			Tera Type: Grass
			EVs: 144 Atk / 64 Def / 136 SpD
			Timid Nature
			- Avalanche
			- Aqua Tail
			- Body Slam
			- Dark Pulse""";

	@Test
	void parsesTheCadReferenceExample() {
		ShowdownPokemon parsed = ShowdownParser.parseSingle(CAD_EXAMPLE);

		assertEquals("Bichou", parsed.nickname());
		assertEquals("Samurott-Hisui", parsed.speciesToken());
		assertEquals("Assault Vest", parsed.item());
		assertEquals("Torrent", parsed.ability());
		assertTrue(parsed.shiny());
		assertEquals("Grass", parsed.teraType());
		assertEquals("Timid", parsed.nature());
		assertEquals(100, parsed.level());
		assertEquals(Integer.valueOf(144), parsed.evs().get("atk"));
		assertEquals(Integer.valueOf(64), parsed.evs().get("def"));
		assertEquals(Integer.valueOf(136), parsed.evs().get("spd"));
		assertEquals(List.of("Avalanche", "Aqua Tail", "Body Slam", "Dark Pulse"), parsed.moves());
	}

	@Test
	void parsesSpeciesOnlyHeaderWithoutNickname() {
		ShowdownPokemon parsed = ShowdownParser.parseSingle("Pikachu\nAbility: Static\n- Thunderbolt");
		assertNull(parsed.nickname());
		assertEquals("Pikachu", parsed.speciesToken());
	}

	@Test
	void parsesGenderMarker() {
		ShowdownPokemon parsed = ShowdownParser.parseSingle("Pikachu (M) @ Light Ball\nAbility: Static\n- Thunderbolt");
		assertEquals("M", parsed.gender());
		assertEquals("Pikachu", parsed.speciesToken());
	}

	@Test
	void parsesLevelLine() {
		ShowdownPokemon parsed = ShowdownParser.parseSingle("Pikachu\nLevel: 50\nAbility: Static\n- Thunderbolt");
		assertEquals(50, parsed.level());
	}

	@Test
	void parsesTeamOfMultiplePokemonSeparatedByBlankLines() {
		String team = "Pikachu\nAbility: Static\n- Thunderbolt\n\nCharmander\nAbility: Blaze\n- Ember";
		List<ShowdownPokemon> parsed = ShowdownParser.parseTeam(team);
		assertEquals(2, parsed.size());
		assertEquals("Pikachu", parsed.get(0).speciesToken());
		assertEquals("Charmander", parsed.get(1).speciesToken());
	}

	@Test
	void malformedEvLineThrows() {
		assertThrows(ShowdownParseException.class,
				() -> ShowdownParser.parseSingle("Pikachu\nEVs: garbage\nAbility: Static\n- Thunderbolt"));
	}

	@Test
	void emptyInputThrows() {
		assertThrows(ShowdownParseException.class, () -> ShowdownParser.parseTeam("   \n  \n"));
	}
}
