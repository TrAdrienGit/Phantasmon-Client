package com.mystaria.phantasmon.client.pokemon;

import java.util.Locale;

/**
 * Gender shown next to a Ghost Pokémon (♂/♀/nothing). The backend only has
 * what the player stored in {@code data.gender} — Showdown import writes
 * {@code "M"}/{@code "F"} when the set specifies it, and omits it otherwise
 * (Showdown's own "random gender" convention). When nothing is stored the
 * species' own ratio still settles single-gender and genderless species;
 * a mixed-ratio species with no stored gender stays {@link #UNKNOWN}, never
 * guessed. Pure logic, the ratio comes from Cobblemon's
 * {@code Species/FormData.getMaleRatio()} ({@code -1} = genderless).
 */
public enum PokemonGender {
	MALE, FEMALE, GENDERLESS, UNKNOWN;

	public static PokemonGender resolve(Object storedGender, Float speciesMaleRatio) {
		if (storedGender != null) {
			switch (storedGender.toString().trim().toUpperCase(Locale.ROOT)) {
				case "M", "MALE" -> {
					return MALE;
				}
				case "F", "FEMALE" -> {
					return FEMALE;
				}
				case "N", "GENDERLESS" -> {
					return GENDERLESS;
				}
				default -> {
					// fall through to the species ratio
				}
			}
		}
		if (speciesMaleRatio == null) {
			return UNKNOWN;
		}
		if (speciesMaleRatio < 0) {
			return GENDERLESS;
		}
		if (speciesMaleRatio >= 1f) {
			return MALE;
		}
		if (speciesMaleRatio == 0f) {
			return FEMALE;
		}
		return UNKNOWN;
	}
}
