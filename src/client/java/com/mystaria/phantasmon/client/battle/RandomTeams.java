package com.mystaria.phantasmon.client.battle;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.mystaria.phantasmon.client.pokemon.PokemonCreateRequestDto;
import com.mystaria.phantasmon.client.pokemon.PokemonDto;
import com.mystaria.phantasmon.client.pokemon.showdown.CobblemonShowdownNames;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownImportMapper;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownParser;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownPokemon;

/**
 * Teams of the random formats (Random Battle, Random Doubles, Monotype Random, Hackmons Cup; 2026-11-27): generated
 * by Showdown's own team generators, in the engine Cobblemon runs on this client ({@code Teams.generate}), exported
 * as a Showdown paste and read back the way a pasted team is imported. Never stored: they only live for the battle.
 */
public final class RandomTeams {

	private static final Logger LOG = LoggerFactory.getLogger(RandomTeams.class);

	private RandomTeams() {
	}

	/**
	 * A team of {@code formatId} (a Showdown id, e.g. "gen9randombattle"), empty if the engine can't make one. Must run
	 * on the battle thread, after the engine booted.
	 */
	public static List<PokemonDto> generate(String formatId) {
		String id = formatId == null ? "" : formatId.replaceAll("[^a-z0-9]", "");
		if (id.isEmpty()) {
			return List.of();
		}
		String paste;
		try {
			Object service = ShowdownService.Companion.getService();
			Object context = service.getClass().getMethod("getContext").invoke(service);
			Method eval = context.getClass().getMethod("eval", String.class, CharSequence.class);
			Object value = eval.invoke(context, "js",
					// Node's "global" (the generators read global.Config for Pokémon of the Day) is not in Graal.
					"(function () { if (typeof global === 'undefined') { globalThis.global = globalThis; }"
							+ " const Teams = require('./sim/teams').Teams; return Teams.export(Teams.generate('" + id + "')); })()");
			paste = (String) value.getClass().getMethod("asString").invoke(value);
		} catch (ReflectiveOperationException | RuntimeException ex) {
			Throwable cause = ex instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : ex;
			LOG.error("Random team for {} not generated", id, cause);
			return List.of();
		}
		List<PokemonDto> team = new ArrayList<>();
		String version = com.mystaria.phantasmon.client.pokemon.CobblemonDataVersion.local();
		for (ShowdownPokemon set : ShowdownParser.parseTeam(paste)) {
			try {
				PokemonCreateRequestDto request = ShowdownImportMapper.toCreateRequest(set, version, CobblemonShowdownNames::baseAbilities);
				team.add(new PokemonDto(UUID.randomUUID(), null, request.species(), request.form(), request.level(), request.nature(),
						request.ability(), Boolean.TRUE.equals(request.isShiny()), null, null, team.size() + 1,
						request.cobblemonDataVersion(), request.data()));
			} catch (RuntimeException ex) {
				LOG.warn("Random set {} of {} skipped: {}", set.speciesToken(), id, ex.toString());
			}
		}
		LOG.info("Random team for {}: {}", id, team.stream().map(PokemonDto::species).toList());
		return team;
	}
}
