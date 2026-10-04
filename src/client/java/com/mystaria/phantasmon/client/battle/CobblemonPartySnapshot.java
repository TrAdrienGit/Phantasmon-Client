package com.mystaria.phantasmon.client.battle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import com.mystaria.phantasmon.client.pokemon.CobblemonDataVersion;

/**
 * A copy of the player's real Cobblemon party, in the shape of a Ghost Pokémon, for a "Ghost vs normal Pokémon"
 * battle (CAD Partie 1 §31). Read from the party Cobblemon keeps on the client (species, form, level, IVs/EVs, nature
 * — the minted one if any —, ability, moves, held item, Tera type, shiny, gender, nickname, friendship). Only a copy is
 * sent: the battle runs on throwaway Pokémon, nothing is ever written back to the real ones (no XP, no damage kept).
 */
public final class CobblemonPartySnapshot {

	private static final String[] STAT_KEYS = { "hp", "atk", "def", "spa", "spd", "spe" };
	private static final Stat[] STATS = { Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED };
	/** Same limit as the editor and the backend legality check. */
	private static final int NICKNAME_MAX = 20;

	private CobblemonPartySnapshot() {
	}

	/** The party in slot order, empty slots skipped; empty if there is none. Client thread. */
	public static List<Map<String, Object>> current() {
		List<Map<String, Object>> party = new ArrayList<>();
		for (Pokemon pokemon : CobblemonClient.INSTANCE.getStorage().getParty().getSlots()) {
			if (pokemon != null) {
				party.add(copy(pokemon));
			}
		}
		return party;
	}

	private static Map<String, Object> copy(Pokemon pokemon) {
		Map<String, Object> member = new LinkedHashMap<>();
		member.put("uuid", pokemon.getUuid().toString());
		member.put("species", pokemon.getSpecies().getResourceIdentifier().getPath());
		FormData form = pokemon.getForm();
		boolean standard = form == null || form.equals(pokemon.getSpecies().getStandardForm());
		member.put("form", standard ? null : form.formOnlyShowdownId());
		member.put("level", pokemon.getLevel());
		member.put("nature", pokemon.getEffectiveNature().getName().getPath());
		member.put("ability", pokemon.getAbility().getName());
		member.put("is_shiny", pokemon.getShiny());
		member.put("cobblemon_data_version", CobblemonDataVersion.local());

		Map<String, Object> data = new HashMap<>();
		Map<String, Object> ivs = new HashMap<>();
		Map<String, Object> evs = new HashMap<>();
		for (int i = 0; i < STATS.length; i++) {
			Integer iv = pokemon.getIvs().get(STATS[i]);
			Integer ev = pokemon.getEvs().get(STATS[i]);
			ivs.put(STAT_KEYS[i], iv == null ? 31 : iv);
			evs.put(STAT_KEYS[i], ev == null ? 0 : ev);
		}
		data.put("ivs", ivs);
		data.put("evs", evs);
		List<String> moves = new ArrayList<>();
		for (Move move : pokemon.getMoveSet().getMoves()) {
			moves.add(move.getName());
		}
		data.put("moves", moves);
		ItemStack item = pokemon.heldItem();
		if (!item.isEmpty()) {
			data.put("held_item", BuiltInRegistries.ITEM.getKey(item.getItem()).getPath());
		}
		data.put("tera_type", pokemon.getTeraType().getId().getPath());
		if (pokemon.getGender() == Gender.MALE) {
			data.put("gender", "M");
		} else if (pokemon.getGender() == Gender.FEMALE) {
			data.put("gender", "F");
		}
		if (pokemon.getNickname() != null) {
			String nickname = pokemon.getNickname().getString();
			if (!nickname.isBlank()) {
				data.put("nickname", nickname.length() > NICKNAME_MAX ? nickname.substring(0, NICKNAME_MAX) : nickname);
			}
		}
		data.put("friendship", pokemon.getFriendship());
		member.put("data", data);
		return member;
	}
}
