package com.mystaria.phantasmon.client.battle;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.tera.TeraType;
import com.cobblemon.mod.common.api.types.tera.TeraTypes;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.mystaria.phantasmon.client.gui.PokemonGuiRendering;
import com.mystaria.phantasmon.client.pokemon.PokemonDto;

/**
 * Builds a throwaway, fully specified Cobblemon {@link Pokemon} from a Ghost
 * Pokémon's backend data, for the battle engine only (Phase 9). It is never
 * stored anywhere and dies with the battle — which is exactly CAD Partie 1
 * §25 ("état après combat"): HP/PP/status/boosts live on this copy, the
 * Ghost Pokémon itself is never touched.
 */
public final class GhostBattlePokemonFactory {

	private static final String[] STAT_KEYS = { "hp", "atk", "def", "spa", "spd", "spe" };
	private static final Stats[] STATS = { Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED };

	private GhostBattlePokemonFactory() {
	}

	/** {@code null} if the species isn't known to this client's Cobblemon data (can't be battled). */
	public static Pokemon create(PokemonDto dto) {
		Species species = PokemonSpecies.INSTANCE.getByName(dto.species());
		if (species == null) {
			return null;
		}
		Map<String, Object> data = dto.data() != null ? dto.data() : Map.of();

		Pokemon pokemon = new Pokemon();
		pokemon.setSpecies(species);
		FormData form = PokemonGuiRendering.resolveForm(species, dto.form());
		if (form != null) {
			pokemon.setForm(form);
		}
		pokemon.setLevel(Math.max(1, Math.min(100, dto.level())));
		pokemon.setShiny(dto.isShiny());

		Nature nature = dto.nature() == null ? null : Natures.getNature(dto.nature().toLowerCase(Locale.ROOT));
		if (nature != null) {
			pokemon.setNature(nature);
		}
		AbilityTemplate ability = dto.ability() == null ? null : Abilities.get(dto.ability().toLowerCase(Locale.ROOT));
		if (ability != null) {
			pokemon.updateAbility(ability.create(true, Priority.NORMAL));
		}

		Map<String, Object> ivs = asMap(data.get("ivs"));
		Map<String, Object> evs = asMap(data.get("evs"));
		for (int i = 0; i < STAT_KEYS.length; i++) {
			pokemon.getIvs().set(STATS[i], intOf(ivs.get(STAT_KEYS[i]), 31));
			pokemon.getEvs().set(STATS[i], intOf(evs.get(STAT_KEYS[i]), 0));
		}

		pokemon.getMoveSet().clear();
		int slot = 0;
		for (Object moveId : asList(data.get("moves"))) {
			MoveTemplate template = Moves.getByName(moveId.toString().toLowerCase(Locale.ROOT));
			if (template != null && slot < 4) {
				pokemon.getMoveSet().setMove(slot++, template.create());
			}
		}

		Object item = data.get("heldItem");
		if (item != null) {
			ItemStack stack = PokemonGuiRendering.heldItemStack(item.toString());
			if (!stack.isEmpty()) {
				pokemon.swapHeldItem(stack, false, false);
			}
		}
		Object gender = data.get("gender");
		if ("M".equalsIgnoreCase(String.valueOf(gender))) {
			pokemon.setGender(Gender.MALE);
		} else if ("F".equalsIgnoreCase(String.valueOf(gender))) {
			pokemon.setGender(Gender.FEMALE);
		}
		Object tera = data.get("teraType");
		if (tera != null) {
			TeraType teraType = TeraTypes.get(tera.toString().toLowerCase(Locale.ROOT));
			if (teraType != null) {
				pokemon.setTeraType(teraType);
			}
		}
		Object nickname = data.get("nickname");
		if (nickname != null && !nickname.toString().isBlank()) {
			pokemon.setNickname(Component.literal(nickname.toString()));
		}
		if (form != null) {
			// The form's aspects select its model (Arceus plates, Rotom appliances, Ogerpon masks...) and travel to
			// both clients in the battle packets. Forcing them replaces the computed aspects, so shiny and gender
			// must be part of the forced set and forced last — same rule as GhostEntityManager (TODO-12).
			Set<String> forced = new HashSet<>(form.getAspects());
			if (dto.isShiny()) {
				forced.add("shiny");
			}
			String genderAspect = PokemonGuiRendering.genderAspect(species, dto.form(), gender);
			if (genderAspect != null) {
				forced.add(genderAspect);
			}
			pokemon.setForcedAspects(forced);
		}
		pokemon.updateAspects();
		pokemon.heal();
		return pokemon;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object value) {
		return value instanceof Map ? (Map<String, Object>) value : Map.of();
	}

	@SuppressWarnings("unchecked")
	private static List<Object> asList(Object value) {
		return value instanceof List ? (List<Object>) value : List.of();
	}

	private static int intOf(Object value, int fallback) {
		return value instanceof Number number ? number.intValue() : fallback;
	}
}
