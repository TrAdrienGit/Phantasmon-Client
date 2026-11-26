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

	@Test
	void newMegasBecomeTheirBaseForm() {
		PokemonCreateRequestDto absol = map("Absol-Mega-Z", "Magic Bounce", List.of("pressure"));
		assertEquals("absol", absol.species());
		assertNull(absol.form());
		assertEquals("pressure", absol.ability());
		assertNull(map("Raichu-Mega-X", "Static", List.of("static")).form());
		assertNull(map("Clefable-Mega", "Magic Guard", List.of("magicguard")).form());
	}

	@Test
	void baseFormIsKeptForFormedSpecies() {
		PokemonCreateRequestDto meowstic = map("Meowstic-F-Mega", "Keen Eye", List.of("keeneye"));
		assertEquals("meowstic", meowstic.species());
		assertEquals("f", meowstic.form());
		assertEquals("eternal", map("Floette-Mega", "Fairy Aura", List.of("flowerveil")).form());
		assertNull(map("Tatsugiri-Curly-Mega", "Commander", List.of("commander")).form());
		assertEquals("droopy", map("Tatsugiri-Droopy-Mega", "Commander", List.of("commander")).form());
		assertNull(map("Meowstic-M-Mega", "Trace", List.of("keeneye", "infiltrator")).form());
		assertEquals("original", map("Magearna-Original-Mega", "Soul-Heart", List.of("soulheart")).form());
		PokemonCreateRequestDto zygarde = map("Zygarde-Mega", "Aura Break", List.of("aurabreak", "powerconstruct"));
		assertEquals("zygarde", zygarde.species());
		assertEquals("50c", zygarde.form());
		assertEquals("powerconstruct", zygarde.ability());
	}
}
