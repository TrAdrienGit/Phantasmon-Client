package com.mystaria.phantasmon.client.pokemon;

import java.util.Locale;
import java.util.Map;

/**
 * The standard Gen 3+ nature stat bonus/malus table. The 5 neutral natures
 * (Hardy, Docile, Serious, Bashful, Quirky) are deliberately absent from the
 * table — {@link #get} returns null for them, meaning "no modifier to show".
 * Keyed by Cobblemon's lowercase nature identifier (matches
 * {@code CobblemonIdentifiers.slugUnderscore} — single-word nature names need
 * no further transformation).
 */
public final class NatureModifiers {

	public record Modifier(String boosted, String reduced) {
	}

	private static final Map<String, Modifier> TABLE = Map.ofEntries(
			Map.entry("lonely", new Modifier("Atk", "Def")),
			Map.entry("adamant", new Modifier("Atk", "SpA")),
			Map.entry("naughty", new Modifier("Atk", "SpD")),
			Map.entry("brave", new Modifier("Atk", "Spe")),
			Map.entry("bold", new Modifier("Def", "Atk")),
			Map.entry("impish", new Modifier("Def", "SpA")),
			Map.entry("lax", new Modifier("Def", "SpD")),
			Map.entry("relaxed", new Modifier("Def", "Spe")),
			Map.entry("modest", new Modifier("SpA", "Atk")),
			Map.entry("mild", new Modifier("SpA", "Def")),
			Map.entry("rash", new Modifier("SpA", "SpD")),
			Map.entry("quiet", new Modifier("SpA", "Spe")),
			Map.entry("calm", new Modifier("SpD", "Atk")),
			Map.entry("gentle", new Modifier("SpD", "Def")),
			Map.entry("careful", new Modifier("SpD", "SpA")),
			Map.entry("sassy", new Modifier("SpD", "Spe")),
			Map.entry("timid", new Modifier("Spe", "Atk")),
			Map.entry("hasty", new Modifier("Spe", "Def")),
			Map.entry("jolly", new Modifier("Spe", "SpA")),
			Map.entry("naive", new Modifier("Spe", "SpD")));

	private NatureModifiers() {
	}

	public static Modifier get(String natureId) {
		return natureId == null ? null : TABLE.get(natureId.toLowerCase(Locale.ROOT));
	}
}
