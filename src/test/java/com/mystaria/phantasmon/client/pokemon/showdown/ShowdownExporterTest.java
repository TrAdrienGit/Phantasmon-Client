package com.mystaria.phantasmon.client.pokemon.showdown;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon.client.pokemon.PokemonCreateRequestDto;
import com.mystaria.phantasmon.client.pokemon.PokemonDto;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShowdownExporterTest {

	/** Display names as Cobblemon's English translations give them, for the ids used below. */
	private static final ShowdownNames NAMES = new ShowdownNames() {
		private final Map<String, String> known = Map.of(
				"closecombat", "Close Combat", "uturn", "U-turn", "hiddenpower", "Hidden Power",
				"choice_scarf", "Choice Scarf", "samurott", "Samurott", "hisui", "Hisui");

		private String name(String id) {
			return known.getOrDefault(id, ShowdownNames.titleCase(id));
		}

		@Override public String species(String id) { return name(id); }
		@Override public String form(String speciesId, String formId) { return name(formId); }
		@Override public String move(String id) { return name(id); }
		@Override public String ability(String id) { return name(id); }
		@Override public String item(String id) { return name(id); }
		@Override public String nature(String id) { return name(id); }
		@Override public String type(String id) { return name(id); }
	};

	private static PokemonDto pokemon(String species, String form, int level, String nature, String ability, boolean shiny,
			Map<String, Object> data) {
		return new PokemonDto(UUID.randomUUID(), UUID.randomUUID(), species, form, level, nature, ability, shiny,
				1, 1, null, "1.8.1", data);
	}

	@Test
	void writesShowdownsOwnExportLayout() {
		Map<String, Object> data = new HashMap<>();
		data.put("nickname", "Bichou");
		data.put("gender", "F");
		data.put("held_item", "choice_scarf");
		data.put("tera_type", "water");
		data.put("friendship", 70.0); // Gson hands JSON numbers over as Double
		data.put("evs", Map.of("hp", 0.0, "atk", 252.0, "def", 0.0, "spa", 4.0, "spd", 0.0, "spe", 252.0));
		data.put("ivs", Map.of("hp", 31.0, "atk", 31.0, "def", 31.0, "spa", 0.0, "spd", 31.0, "spe", 31.0));
		data.put("moves", List.of("closecombat", "uturn"));

		String text = ShowdownExporter.export(pokemon("samurott", "hisui", 50, "jolly", "sharpness", true, data), NAMES);

		assertEquals("""
				Bichou (Samurott-Hisui) (F) @ Choice Scarf
				Ability: Sharpness
				Level: 50
				Shiny: Yes
				Happiness: 70
				Tera Type: Water
				EVs: 252 Atk / 4 SpA / 252 Spe
				Jolly Nature
				IVs: 0 SpA
				- Close Combat
				- U-turn""", text);
	}

	@Test
	void omitsShowdownsImplicitDefaults() {
		Map<String, Object> data = new HashMap<>();
		data.put("friendship", 255);
		data.put("evs", Map.of("hp", 0, "atk", 0, "def", 0, "spa", 0, "spd", 0, "spe", 0));
		data.put("ivs", Map.of("hp", 31, "atk", 31, "def", 31, "spa", 31, "spd", 31, "spe", 31));
		data.put("moves", List.of("thunderbolt"));

		assertEquals("""
				Pikachu
				Ability: Static
				Hardy Nature
				- Thunderbolt""", ShowdownExporter.export(pokemon("pikachu", null, 100, "hardy", "static", false, data), NAMES));
	}

	@Test
	void hiddenPowerCarriesTheTypeFromTheIvs() {
		Map<String, Object> data = new HashMap<>();
		// Classic Hidden Power Fire spread: Atk 30 / SpA 30 / Spe 30, the rest 31.
		data.put("ivs", Map.of("hp", 31, "atk", 30, "def", 31, "spa", 30, "spd", 31, "spe", 30));
		data.put("moves", List.of("hiddenpower"));

		String text = ShowdownExporter.export(pokemon("pikachu", null, 100, "hardy", "static", false, data), NAMES);

		assertEquals("- Hidden Power [Fire]", text.lines().reduce((a, b) -> b).orElseThrow());
	}

	@Test
	void teamIsSeparatedByBlankLines() {
		PokemonDto a = pokemon("pikachu", null, 100, "hardy", "static", false, Map.of());
		PokemonDto b = pokemon("eevee", null, 100, "hardy", "adaptability", false, Map.of());

		assertEquals(2, ShowdownParser.parseTeam(ShowdownExporter.exportTeam(List.of(a, b), NAMES)).size());
	}

	@Test
	void exportReimportsToTheSamePokemon() {
		Map<String, Object> data = new HashMap<>();
		data.put("nickname", "Bichou");
		data.put("gender", "M");
		data.put("held_item", "choice_scarf");
		data.put("tera_type", "water");
		data.put("friendship", 70);
		data.put("evs", Map.of("hp", 4, "atk", 252, "def", 0, "spa", 0, "spd", 0, "spe", 252));
		data.put("ivs", Map.of("hp", 31, "atk", 30, "def", 31, "spa", 30, "spd", 31, "spe", 30));
		data.put("moves", List.of("closecombat", "uturn", "hiddenpower"));
		PokemonDto original = pokemon("samurott", "hisui", 63, "adamant", "sharpness", true, data);

		PokemonCreateRequestDto back = ShowdownImportMapper.toCreateRequest(
				ShowdownParser.parseSingle(ShowdownExporter.export(original, NAMES)), "1.8.1");

		assertEquals("samurott", back.species());
		assertEquals("hisui", back.form());
		assertEquals(63, back.level());
		assertEquals("adamant", back.nature());
		assertEquals("sharpness", back.ability());
		assertEquals(true, back.isShiny());
		assertEquals("Bichou", back.data().get("nickname"));
		assertEquals("M", back.data().get("gender"));
		assertEquals("choice_scarf", back.data().get("held_item"));
		assertEquals("water", back.data().get("tera_type"));
		assertEquals(70, back.data().get("friendship"));
		assertEquals(data.get("evs"), back.data().get("evs"));
		assertEquals(data.get("ivs"), back.data().get("ivs"));
		assertEquals(List.of("closecombat", "uturn", "hiddenpower"), back.data().get("moves"));
	}

	@Test
	void fallbackTitleCasesIdentifiers() {
		assertEquals("Flash Fire", ShowdownNames.titleCase("flash_fire"));
		assertEquals("Choice Band", ShowdownNames.titleCase("cobblemon:choice_band"));
		assertEquals("", ShowdownNames.titleCase(null));
	}
}
