package com.mystaria.phantasmon.client.pokemon.showdown;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Pokémon Showdown export/import text format (CAD Partie 1 §8).
 * Pure text parsing only — no Cobblemon identifier conversion here, see
 * {@link CobblemonIdentifiers} and {@link ShowdownImportMapper} for that.
 */
public final class ShowdownParser {

	private static final int DEFAULT_LEVEL = 100;
	private static final String DEFAULT_NATURE = "Hardy";

	private static final Pattern NICKNAME_PATTERN = Pattern.compile("^(.+) \\((.+)\\)$");
	private static final Pattern NATURE_PATTERN = Pattern.compile("^(\\w+) Nature$");
	private static final Map<String, String> STAT_ALIASES = Map.ofEntries(
			Map.entry("hp", "hp"), Map.entry("atk", "atk"), Map.entry("def", "def"),
			Map.entry("spa", "spa"), Map.entry("spd", "spd"), Map.entry("spe", "spe"));

	private ShowdownParser() {
	}

	/** Splits a multi-Pokémon export (blocks separated by one or more blank lines) and parses each. */
	public static List<ShowdownPokemon> parseTeam(String rawText) {
		List<ShowdownPokemon> result = new ArrayList<>();
		StringBuilder currentBlock = new StringBuilder();
		for (String line : rawText.split("\r?\n")) {
			if (line.isBlank()) {
				flushBlock(currentBlock, result);
			} else {
				currentBlock.append(line).append('\n');
			}
		}
		flushBlock(currentBlock, result);
		if (result.isEmpty()) {
			throw new ShowdownParseException("empty_input");
		}
		return result;
	}

	private static void flushBlock(StringBuilder block, List<ShowdownPokemon> result) {
		if (!block.isEmpty()) {
			result.add(parseSingle(block.toString()));
			block.setLength(0);
		}
	}

	public static ShowdownPokemon parseSingle(String block) {
		String[] lines = block.split("\r?\n");
		if (lines.length == 0 || lines[0].isBlank()) {
			throw new ShowdownParseException("empty_block");
		}

		HeaderParts header = parseHeader(lines[0]);

		String ability = null;
		int level = DEFAULT_LEVEL;
		boolean shiny = false;
		String teraType = null;
		Map<String, Integer> evs = new LinkedHashMap<>();
		Map<String, Integer> ivs = new LinkedHashMap<>();
		String nature = DEFAULT_NATURE;
		Integer happiness = null;
		List<String> moves = new ArrayList<>();

		for (int i = 1; i < lines.length; i++) {
			String line = lines[i].trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.startsWith("-")) {
				moves.add(line.substring(1).trim());
			} else if (line.startsWith("Ability:")) {
				ability = value(line);
			} else if (line.startsWith("Level:")) {
				level = parseIntOrThrow(value(line), "level");
			} else if (line.startsWith("Shiny:")) {
				shiny = value(line).equalsIgnoreCase("Yes");
			} else if (line.startsWith("Tera Type:")) {
				teraType = value(line);
			} else if (line.startsWith("Happiness:")) {
				happiness = parseIntOrThrow(value(line), "happiness");
			} else if (line.startsWith("EVs:")) {
				parseStatLine(value(line), evs);
			} else if (line.startsWith("IVs:")) {
				parseStatLine(value(line), ivs);
			} else {
				Matcher natureMatcher = NATURE_PATTERN.matcher(line);
				if (natureMatcher.matches()) {
					nature = natureMatcher.group(1);
				}
				// Any other unrecognized line (Showdown has a few more, e.g. "Hidden Power" hints
				// unrelated to sets) is silently ignored rather than failing the whole import.
			}
		}

		return new ShowdownPokemon(header.nickname(), header.speciesToken(), header.gender(), header.item(),
				ability, level, shiny, teraType, evs, ivs, nature, happiness, moves);
	}

	private record HeaderParts(String nickname, String speciesToken, String gender, String item) {
	}

	private static HeaderParts parseHeader(String headerLine) {
		String[] atSplit = headerLine.split(" @ ", 2);
		String left = atSplit[0].trim();
		String item = atSplit.length > 1 ? atSplit[1].trim() : null;

		String gender = null;
		if (left.endsWith("(M)") || left.endsWith("(F)")) {
			gender = left.substring(left.length() - 2, left.length() - 1);
			left = left.substring(0, left.length() - 4).trim();
		}

		Matcher nicknameMatcher = NICKNAME_PATTERN.matcher(left);
		if (nicknameMatcher.matches()) {
			return new HeaderParts(nicknameMatcher.group(1).trim(), nicknameMatcher.group(2).trim(), gender, item);
		}
		if (left.isEmpty()) {
			throw new ShowdownParseException("missing_species");
		}
		return new HeaderParts(null, left, gender, item);
	}

	private static void parseStatLine(String statLine, Map<String, Integer> target) {
		for (String part : statLine.split("/")) {
			String[] tokens = part.trim().split("\\s+");
			if (tokens.length != 2) {
				throw new ShowdownParseException("malformed_stat_entry: " + part.trim());
			}
			int amount = parseIntOrThrow(tokens[0], "stat value");
			String stat = STAT_ALIASES.get(tokens[1].toLowerCase(Locale.ROOT));
			if (stat == null) {
				throw new ShowdownParseException("unknown_stat: " + tokens[1]);
			}
			target.put(stat, amount);
		}
	}

	private static int parseIntOrThrow(String raw, String fieldName) {
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException ex) {
			throw new ShowdownParseException("invalid_number(" + fieldName + "): " + raw);
		}
	}

	private static String value(String line) {
		return line.substring(line.indexOf(':') + 1).trim();
	}
}
