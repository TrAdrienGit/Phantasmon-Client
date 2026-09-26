package com.mystaria.phantasmon.client.pokemon.showdown;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon.client.pokemon.PokemonCreateRequestDto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShowdownImportMapperTest {

	private static final String VERSION = "1.8.1";

	@Test
	void mapsCadReferenceExampleToCreateRequest() {
		ShowdownPokemon parsed = ShowdownParser.parseSingle("""
				Bichou (Samurott-Hisui) @ Assault Vest
				Ability: Torrent
				Shiny: Yes
				Tera Type: Grass
				EVs: 144 Atk / 64 Def / 136 SpD
				Timid Nature
				- Avalanche
				- Aqua Tail
				- Body Slam
				- Dark Pulse""");

		PokemonCreateRequestDto request = ShowdownImportMapper.toCreateRequest(parsed, VERSION);

		assertEquals("samurott", request.species());
		assertEquals("hisui", request.form());
		assertEquals("torrent", request.ability());
		assertEquals("timid", request.nature());
		assertEquals(Boolean.TRUE, request.isShiny());
		assertEquals(VERSION, request.cobblemonDataVersion());

		@SuppressWarnings("unchecked")
		Map<String, Integer> evs = (Map<String, Integer>) request.data().get("evs");
		assertEquals(144, evs.get("atk"));
		assertEquals(64, evs.get("def"));
		assertEquals(136, evs.get("spd"));
		assertEquals(0, evs.get("hp"));

		@SuppressWarnings("unchecked")
		Map<String, Integer> ivs = (Map<String, Integer>) request.data().get("ivs");
		assertEquals(31, ivs.get("hp"));
		assertEquals(31, ivs.get("spe"));

		assertEquals("assault_vest", request.data().get("heldItem"));
		assertEquals("grass", request.data().get("teraType"));
		assertEquals("Bichou", request.data().get("nickname"));
	}

	@Test
	void movesAreConcatenatedLikeShowdownIds() {
		ShowdownPokemon parsed = ShowdownParser.parseSingle("Pikachu\nAbility: Static\n- Body Slam\n- Aqua Tail");
		PokemonCreateRequestDto request = ShowdownImportMapper.toCreateRequest(parsed, VERSION);

		@SuppressWarnings("unchecked")
		var moves = (java.util.List<String>) request.data().get("moves");
		assertEquals("bodyslam", moves.get(0));
		assertEquals("aquatail", moves.get(1));
	}

	@Test
	void missingAbilityThrows() {
		ShowdownPokemon parsed = ShowdownParser.parseSingle("Pikachu\n- Thunderbolt");
		assertThrows(ShowdownParseException.class, () -> ShowdownImportMapper.toCreateRequest(parsed, VERSION));
	}
}
