package com.mystaria.phantasmon.client.pokemon.showdown;

import java.util.Locale;
import java.util.Set;

/**
 * Converts Pokémon Showdown's human-readable display names into the
 * lowercase identifier strings Cobblemon actually uses (CAD Phase 6: "la
 * table de correspondance Showdown ↔ identifiants Cobblemon se construit à
 * cette phase"). Rather than a hardcoded lookup table for every species/move/
 * ability/item — an unmaintainable list of 1000+ entries — this applies the
 * same normalization rules Cobblemon's own data files follow, verified by
 * inspecting the read-only {@code cobblemon} reference repo directly
 * (2026-09-26):
 *
 * <ul>
 *   <li>Species/forms are lowercased and fully concatenated, no separator —
 *       confirmed against {@code data/cobblemon/species/**}, e.g. Samurott's
 *       "Hisui" form file is {@code samurott.json} with a form entry named
 *       "Hisui" (lowercased "hisui" at use), and folder names like
 *       {@code 0439_mimejr} confirm "Mime Jr." -> "mimejr".</li>
 *   <li>Moves follow the exact same rule (matches Pokémon Showdown's own
 *       internal {@code toID()}) — confirmed against move animation files
 *       like {@code closecombat.animation.json}, {@code bodyslam...}.</li>
 *   <li>Abilities and held items instead use lowercase words joined by a
 *       single underscore — confirmed against ability files like
 *       {@code flash_fire.json} and item textures like
 *       {@code assault_vest.png}, {@code choice_band.png}.</li>
 * </ul>
 *
 * <p>Known limitation: a handful of real Pokémon species have a hyphen as
 * part of their actual name rather than as a Showdown form separator (e.g.
 * Ho-Oh, Porygon-Z, Nidoran-M/F, Kommo-o) — {@link #splitSpeciesForm}
 * special-cases the ones Cobblemon ships. Anything not on that list is
 * treated as a real form suffix, which is the common case for regional forms
 * (Samurott-Hisui, Tauros-Paldea-Aqua, etc.) and is what actually matters for
 * fully custom Ghost species anyway.
 */
public final class CobblemonIdentifiers {

	private static final Set<String> NO_SPLIT_HYPHENATED_SPECIES = Set.of(
			"ho-oh", "porygon-z", "jangmo-o", "hakamo-o", "kommo-o", "type-null", "nidoran-m", "nidoran-f");

	private CobblemonIdentifiers() {
	}

	public record SpeciesForm(String species, String form) {
	}

	public static SpeciesForm splitSpeciesForm(String showdownSpeciesToken) {
		String trimmed = showdownSpeciesToken.trim();
		int lastHyphen = trimmed.lastIndexOf('-');
		if (lastHyphen <= 0 || NO_SPLIT_HYPHENATED_SPECIES.contains(trimmed.toLowerCase(Locale.ROOT))) {
			return new SpeciesForm(slugConcat(trimmed), null);
		}
		String base = trimmed.substring(0, lastHyphen);
		String suffix = trimmed.substring(lastHyphen + 1);
		return new SpeciesForm(slugConcat(base), slugConcat(suffix));
	}

	/** Species/form/move identifier convention: lowercase, every non-alphanumeric character stripped. */
	public static String slugConcat(String displayName) {
		StringBuilder result = new StringBuilder();
		for (char c : displayName.toLowerCase(Locale.ROOT).toCharArray()) {
			if (Character.isLetterOrDigit(c)) {
				result.append(c);
			}
		}
		return result.toString();
	}

	/** Ability/held-item/type identifier convention: lowercase words joined by a single underscore. */
	public static String slugUnderscore(String displayName) {
		String normalized = displayName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
		return normalized.replaceAll("^_+|_+$", "");
	}
}
