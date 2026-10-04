package com.mystaria.phantasmon.client.pokemon.showdown;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mystaria.phantasmon.client.pokemon.PokemonCreateRequestDto;
import com.mystaria.phantasmon.client.pokemon.showdown.CobblemonIdentifiers.SpeciesForm;

/**
 * Turns a parsed {@link ShowdownPokemon} into the backend's
 * {@code POST /pokemon} request shape: converts every display name to its
 * Cobblemon identifier ({@link CobblemonIdentifiers}) and fills in Showdown's
 * own implicit defaults for any stat not explicitly listed — a Showdown set
 * with no {@code IVs:} line means every IV is 31 (perfect), and no
 * {@code EVs:} line means every EV is 0, exactly like Showdown's own import
 * dialog interprets it.
 */
public final class ShowdownImportMapper {

	private static final int DEFAULT_IV = 31;
	private static final int DEFAULT_EV = 0;
	private static final List<String> STATS = List.of("hp", "atk", "def", "spa", "spd", "spe");

	private ShowdownImportMapper() {
	}

	public static PokemonCreateRequestDto toCreateRequest(ShowdownPokemon parsed, String cobblemonDataVersion) {
		if (parsed.ability() == null || parsed.ability().isBlank()) {
			throw new ShowdownParseException("missing_ability");
		}

		SpeciesForm speciesForm = CobblemonIdentifiers.splitSpeciesForm(parsed.speciesToken());

		Map<String, Object> data = new HashMap<>();
		data.put("ivs", filledStatMap(parsed.ivs(), DEFAULT_IV));
		data.put("evs", filledStatMap(parsed.evs(), DEFAULT_EV));
		data.put("moves", parsed.moves().stream().map(ShowdownImportMapper::moveId).toList());
		if (parsed.nickname() != null) {
			data.put("nickname", parsed.nickname());
		}
		if (parsed.gender() != null) {
			data.put("gender", parsed.gender());
		}
		if (parsed.item() != null) {
			data.put("heldItem", CobblemonIdentifiers.slugUnderscore(parsed.item()));
		}
		if (parsed.teraType() != null) {
			data.put("teraType", CobblemonIdentifiers.slugUnderscore(parsed.teraType()));
		}
		if (parsed.happiness() != null) {
			data.put("friendship", parsed.happiness());
		}

		return new PokemonCreateRequestDto(
				UUID.randomUUID(),
				speciesForm.species(),
				speciesForm.form(),
				parsed.level(),
				CobblemonIdentifiers.slugUnderscore(parsed.nature()),
				CobblemonIdentifiers.slugUnderscore(parsed.ability()),
				parsed.shiny(),
				null, null, null,
				cobblemonDataVersion,
				data);
	}

	/**
	 * Showdown writes Hidden Power with its type ({@code Hidden Power [Fire]}); Cobblemon has a single
	 * {@code hiddenpower} move whose type comes from the IVs, so the bracketed type is dropped.
	 */
	static String moveId(String showdownMove) {
		String id = CobblemonIdentifiers.slugConcat(showdownMove);
		return id.startsWith("hiddenpower") ? "hiddenpower" : id;
	}

	private static Map<String, Integer> filledStatMap(Map<String, Integer> explicit, int defaultValue) {
		Map<String, Integer> result = new HashMap<>();
		for (String stat : STATS) {
			result.put(stat, explicit.getOrDefault(stat, defaultValue));
		}
		return result;
	}
}
