package com.mystaria.phantasmon.client.pokemon.showdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.mystaria.phantasmon.client.pokemon.HiddenPowerCalculator;
import com.mystaria.phantasmon.client.pokemon.PokemonDto;

/**
 * Writes Ghost Pokémon in the Pokémon Showdown export format (CAD Partie 1 §11) — the reverse of
 * {@link ShowdownParser} + {@link ShowdownImportMapper}, so an export re-imports to the same Pokémon. Lines follow
 * Showdown's own export: header, {@code Ability}, {@code Level} (omitted at 100), {@code Shiny}, {@code Happiness}
 * (omitted at 255), {@code Tera Type}, {@code EVs} (non-zero only), nature, {@code IVs} (only those below 31), moves.
 * Hidden Power is written {@code Hidden Power [Type]}, its type derived from the IVs like everywhere else.
 * No Minecraft dependency: display names come from a {@link ShowdownNames}.
 */
public final class ShowdownExporter {

	private static final String[] STATS = { "hp", "atk", "def", "spa", "spd", "spe" };
	private static final String[] STAT_LABELS = { "HP", "Atk", "Def", "SpA", "SpD", "Spe" };
	private static final String HIDDEN_POWER = "hiddenpower";

	private ShowdownExporter() {
	}

	/** Several sets separated by a blank line, as Showdown's team export (and {@link ShowdownParser#parseTeam}). */
	public static String exportTeam(List<PokemonDto> team, ShowdownNames names) {
		return team.stream().map(pokemon -> export(pokemon, names)).collect(Collectors.joining("\n\n"));
	}

	public static String export(PokemonDto pokemon, ShowdownNames names) {
		Map<String, Object> data = pokemon.data() == null ? Map.of() : pokemon.data();
		List<String> lines = new ArrayList<>();

		String species = names.species(pokemon.species());
		if (pokemon.form() != null && !pokemon.form().isBlank()) {
			String form = names.form(pokemon.species(), pokemon.form());
			if (!form.isBlank()) {
				species = species + "-" + form;
			}
		}
		StringBuilder header = new StringBuilder();
		String nickname = string(data.get("nickname"));
		if (nickname != null && !nickname.equals(species)) {
			header.append(nickname).append(" (").append(species).append(')');
		} else {
			header.append(species);
		}
		String gender = string(data.get("gender"));
		if ("M".equals(gender) || "F".equals(gender)) {
			header.append(" (").append(gender).append(')');
		}
		String item = string(data.get("heldItem"));
		if (item != null) {
			header.append(" @ ").append(names.item(item));
		}
		lines.add(header.toString());

		if (pokemon.ability() != null && !pokemon.ability().isBlank()) {
			lines.add("Ability: " + names.ability(pokemon.ability()));
		}
		if (pokemon.level() != 100) {
			lines.add("Level: " + pokemon.level());
		}
		if (pokemon.isShiny()) {
			lines.add("Shiny: Yes");
		}
		Integer friendship = integer(data.get("friendship"));
		if (friendship != null && friendship != 255) {
			lines.add("Happiness: " + friendship);
		}
		String tera = string(data.get("teraType"));
		if (tera != null) {
			lines.add("Tera Type: " + names.type(tera));
		}
		Map<String, Integer> evs = statMap(data.get("evs"), 0);
		String evLine = statLine(evs, 0);
		if (!evLine.isEmpty()) {
			lines.add("EVs: " + evLine);
		}
		if (pokemon.nature() != null && !pokemon.nature().isBlank()) {
			lines.add(names.nature(pokemon.nature()) + " Nature");
		}
		Map<String, Integer> ivs = statMap(data.get("ivs"), 31);
		String ivLine = statLine(ivs, 31);
		if (!ivLine.isEmpty()) {
			lines.add("IVs: " + ivLine);
		}
		if (data.get("moves") instanceof List<?> moves) {
			for (Object move : moves) {
				String id = string(move);
				if (id == null) {
					continue;
				}
				if (HIDDEN_POWER.equals(id)) {
					String type = HiddenPowerCalculator.type(new java.util.HashMap<String, Object>(ivs));
					lines.add("- " + names.move(id) + (type == null ? "" : " [" + names.type(type) + "]"));
				} else {
					lines.add("- " + names.move(id));
				}
			}
		}
		return String.join("\n", lines);
	}

	/** Only the stats that differ from Showdown's implicit default, e.g. {@code 252 SpA / 4 SpD / 252 Spe}. */
	private static String statLine(Map<String, Integer> values, int implicitDefault) {
		List<String> parts = new ArrayList<>();
		for (int i = 0; i < STATS.length; i++) {
			int value = values.get(STATS[i]);
			if (value != implicitDefault) {
				parts.add(value + " " + STAT_LABELS[i]);
			}
		}
		return String.join(" / ", parts);
	}

	private static Map<String, Integer> statMap(Object raw, int missingValue) {
		Map<String, Integer> result = new java.util.HashMap<>();
		Map<?, ?> source = raw instanceof Map<?, ?> map ? map : Map.of();
		for (String stat : STATS) {
			Integer value = integer(source.get(stat));
			result.put(stat, value == null ? missingValue : value);
		}
		return result;
	}

	private static String string(Object value) {
		if (value == null) {
			return null;
		}
		String text = value.toString().trim();
		return text.isEmpty() ? null : text;
	}

	/** JSON numbers arrive as {@code Double} through Gson's {@code Map<String, Object>}. */
	private static Integer integer(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value instanceof String text) {
			try {
				return Integer.parseInt(text.trim());
			} catch (NumberFormatException ex) {
				return null;
			}
		}
		return null;
	}
}
