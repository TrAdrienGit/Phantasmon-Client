package com.mystaria.phantasmon.client.pokemon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.network.chat.Component;

/**
 * A Pokémon's look (TODO-37, Adrien 2026-11-11), in its data's {@code look}: a filter over its colours, an aura around
 * it, its size (50 to 200 %), and whether an admin sealed it. Purely cosmetic. Looks are won (events, tournaments)
 * or given by an admin: a player picks only among their rewards ({@link Reward}).
 */
public record PokemonLook(String filter, String aura, int scale, boolean sealed) {

	public static final PokemonLook NONE = new PokemonLook(null, null, 100, false);
	public static final int SCALE_MIN = 50;
	public static final int SCALE_MAX = 200;

	/** The filters this version draws (step 1 of TODO-37); others may come later. */
	public static final List<String> FILTERS = List.of("obscure", "black_white", "negative", "holographic");
	public static final List<String> AURAS = List.of("super_saiyan", "electric");

	public static PokemonLook of(PokemonDto pokemon) {
		return fromMap(pokemon == null || pokemon.data() == null ? null : pokemon.data().get("look"));
	}

	/** Cobblemon aspects carrying the look to the renderer (they travel with the entity and in battle packets). */
	public static final String ASPECT_FILTER = "phantasmon_filter_";
	public static final String ASPECT_AURA = "phantasmon_aura_";
	public static final String ASPECT_SCALE = "phantasmon_scale_";

	/** The look of a {@code look} map as the data and the Ghost payloads carry it. */
	public static PokemonLook fromMap(Object look) {
		if (!(look instanceof Map<?, ?> map)) {
			return NONE;
		}
		int scale = map.get("scale") instanceof Number number ? number.intValue() : 100;
		return new PokemonLook(map.get("filter") instanceof String text ? text : null,
				map.get("aura") instanceof String text ? text : null, scale, Boolean.TRUE.equals(map.get("sealed")));
	}

	/** The aspects to add to a Pokémon wearing this look (none for no look). */
	public java.util.Set<String> aspects() {
		java.util.Set<String> aspects = new java.util.HashSet<>();
		if (filter != null) {
			aspects.add(ASPECT_FILTER + filter);
		}
		if (aura != null) {
			aspects.add(ASPECT_AURA + aura);
		}
		if (scale != 100) {
			aspects.add(ASPECT_SCALE + scale);
		}
		return aspects;
	}

	/** The look carried by a Pokémon's aspects (render side). */
	public static PokemonLook fromAspects(java.util.Collection<String> aspects) {
		String filter = null;
		String aura = null;
		int scale = 100;
		if (aspects != null) {
			for (String aspect : aspects) {
				if (aspect.startsWith(ASPECT_FILTER)) {
					filter = aspect.substring(ASPECT_FILTER.length());
				} else if (aspect.startsWith(ASPECT_AURA)) {
					aura = aspect.substring(ASPECT_AURA.length());
				} else if (aspect.startsWith(ASPECT_SCALE)) {
					try {
						scale = Integer.parseInt(aspect.substring(ASPECT_SCALE.length()));
					} catch (NumberFormatException ignored) {
						// not ours
					}
				}
			}
		}
		return new PokemonLook(filter, aura, scale, false);
	}

	public boolean empty() {
		return filter == null && aura == null && scale == 100 && !sealed;
	}

	/** As the data's {@code look}, or null when there is none. */
	public Map<String, Object> toData() {
		if (empty()) {
			return null;
		}
		Map<String, Object> data = new HashMap<>();
		if (filter != null) {
			data.put("filter", filter);
		}
		if (aura != null) {
			data.put("aura", aura);
		}
		if (scale != 100) {
			data.put("scale", scale);
		}
		if (sealed) {
			data.put("sealed", true);
		}
		return data;
	}

	public PokemonLook withFilter(String value) {
		return new PokemonLook(value, aura, scale, sealed);
	}

	public PokemonLook withAura(String value) {
		return new PokemonLook(filter, value, scale, sealed);
	}

	public PokemonLook withScale(int value) {
		return new PokemonLook(filter, aura, Math.max(SCALE_MIN, Math.min(SCALE_MAX, value)), sealed);
	}

	public PokemonLook withSealed(boolean value) {
		return new PokemonLook(filter, aura, scale, value);
	}

	/** A filter's or aura's name; an id this version doesn't know shows as it is. */
	public static Component label(String kind, String id) {
		return Component.translatableWithFallback("phantasmon.look." + kind.toLowerCase(java.util.Locale.ROOT) + "." + id, id);
	}

	/** A reward as the backend gives it ({@code GET /rewards/mine}): what kind, which one, for which species (null: all). */
	public record Reward(String uuid, String kind, String value, String species, String note, String grantedAt) {

		public boolean covers(String pokemonSpecies) {
			return species == null || species.equalsIgnoreCase(pokemonSpecies);
		}
	}

	/** An admin's view of a player's rewards ({@code GET /admin/players/{name}/rewards}). */
	public record PlayerRewards(String playerUuid, String playerName, Reward[] rewards) {
	}
}
