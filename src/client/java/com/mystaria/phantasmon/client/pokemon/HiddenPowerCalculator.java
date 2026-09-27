package com.mystaria.phantasmon.client.pokemon;

import java.util.Map;

/**
 * Computes a Pokémon's Hidden Power type from its IVs — there is no stored
 * "hidden power type" field anywhere in this codebase (nor in Cobblemon, nor
 * in Pokémon Showdown itself): it's always derived from the 6 IVs via the
 * standard Gen 2+ formula, unchanged since. Two well-known reference points
 * confirm the formula/type order below: all-31 IVs is Hidden Power Dark,
 * all-0 IVs is Hidden Power Fighting.
 */
public final class HiddenPowerCalculator {

	private static final String[] TYPES = {
			"fighting", "flying", "poison", "ground", "rock", "bug", "ghost", "steel",
			"fire", "water", "grass", "electric", "psychic", "ice", "dragon", "dark"
	};

	private HiddenPowerCalculator() {
	}

	/** {@code ivs} keyed by hp/atk/def/spa/spd/spe (matches {@code ShowdownImportMapper}'s stat map); returns null if any stat is missing. */
	public static String type(Map<String, Object> ivs) {
		Integer hp = asInt(ivs.get("hp"));
		Integer atk = asInt(ivs.get("atk"));
		Integer def = asInt(ivs.get("def"));
		Integer spe = asInt(ivs.get("spe"));
		Integer spa = asInt(ivs.get("spa"));
		Integer spd = asInt(ivs.get("spd"));
		if (hp == null || atk == null || def == null || spe == null || spa == null || spd == null) {
			return null;
		}
		int bits = (hp & 1) + (atk & 1) * 2 + (def & 1) * 4 + (spe & 1) * 8 + (spa & 1) * 16 + (spd & 1) * 32;
		int index = bits * 15 / 63;
		return TYPES[index];
	}

	private static Integer asInt(Object value) {
		return value instanceof Number number ? number.intValue() : null;
	}
}
