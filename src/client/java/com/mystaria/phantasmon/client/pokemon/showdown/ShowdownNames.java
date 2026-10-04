package com.mystaria.phantasmon.client.pokemon.showdown;

import java.util.Locale;

/**
 * Cobblemon identifier → English Showdown display name, used by {@link ShowdownExporter}. The in-game
 * implementation ({@code CobblemonShowdownNames}) reads Cobblemon's own English translations; {@link #FALLBACK}
 * only title-cases the identifier ({@code flash_fire} → {@code Flash Fire}), which Showdown still reads back
 * since it normalizes every name the same way ({@code toID}).
 */
public interface ShowdownNames {

	String species(String speciesId);

	/** Showdown form suffix, e.g. {@code Hisui} for {@code Samurott-Hisui}. */
	String form(String speciesId, String formId);

	String move(String moveId);

	String ability(String abilityId);

	String item(String itemId);

	String nature(String natureId);

	String type(String typeId);

	ShowdownNames FALLBACK = new ShowdownNames() {
		@Override
		public String species(String speciesId) {
			return titleCase(speciesId);
		}

		@Override
		public String form(String speciesId, String formId) {
			return titleCase(formId);
		}

		@Override
		public String move(String moveId) {
			return titleCase(moveId);
		}

		@Override
		public String ability(String abilityId) {
			return titleCase(abilityId);
		}

		@Override
		public String item(String itemId) {
			return titleCase(itemId);
		}

		@Override
		public String nature(String natureId) {
			return titleCase(natureId);
		}

		@Override
		public String type(String typeId) {
			return titleCase(typeId);
		}
	};

	/** {@code flash_fire} / {@code flash-fire} → {@code Flash Fire}; a namespace prefix ({@code cobblemon:}) is dropped. */
	static String titleCase(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		String bare = id.substring(id.indexOf(':') + 1);
		StringBuilder result = new StringBuilder();
		for (String word : bare.split("[_\\-\\s]+")) {
			if (word.isEmpty()) {
				continue;
			}
			if (!result.isEmpty()) {
				result.append(' ');
			}
			result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1).toLowerCase(Locale.ROOT));
		}
		return result.toString();
	}
}
