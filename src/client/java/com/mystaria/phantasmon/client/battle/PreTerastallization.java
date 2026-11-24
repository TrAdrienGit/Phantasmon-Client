package com.mystaria.phantasmon.client.battle;

import java.util.List;
import java.util.StringJoiner;

import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.mystaria.phantasmon.client.pokemon.PokemonDto;

/**
 * Pokémon terastallized from the start (2026-11-24, asked by the League's players): a Ghost marked so in the PC
 * ({@code pre_tera} in its data) terastallizes, in its Tera type, the first time it enters the field, with the usual
 * Terastallization (its message, the set piece, the type change and every mechanic that follows), and its team can
 * terastallize no other Pokémon in that battle.
 * <p>
 * Done in the battle engine by a rule of our own, {@code Phantasmon Pre Tera = <ids>}: at the start it takes
 * Terastallization from the whole team of a marked Pokémon, and on that Pokémon's first switch-in it terastallizes it
 * as Showdown's own Terastallization does. One per team (the first marked), even under the Terastal Clause (it
 * stops only the Terastallizations chosen in battle); none on a Pokémon that could not terastallize (a Z-Crystal or a
 * Mega Stone held): the lobby already refuses those teams.
 */
final class PreTerastallization {

	static final String DATA_KEY = "pre_tera";
	private static final String EXTENSION = "phantasmon_pre_tera";
	private static final String RULE_NAME = "Phantasmon Pre Tera";

	private PreTerastallization() {
	}

	static void register() {
		ShowdownExtensions.register(EXTENSION,
				"(function(){try{return !!(typeof Dex!=='undefined'?Dex:require('./sim/dex').Dex).data.Rulesets.phantasmonpretera}catch(e){return false}})()",
				PreTerastallization::script);
	}

	/** Whether this Ghost is marked terastallized from the start. */
	static boolean marked(PokemonDto dto) {
		return dto.data() != null && Boolean.TRUE.equals(dto.data().get(DATA_KEY));
	}

	/** The rule for a battle, or null when no Pokémon in it is marked. {@code marked}: their battle Pokémon. */
	static String rule(List<BattlePokemon> marked) {
		if (marked.isEmpty()) {
			return null;
		}
		StringJoiner ids = new StringJoiner("+");
		for (BattlePokemon pokemon : marked) {
			ids.add(pokemon.getUuid().toString());
		}
		return RULE_NAME + " = " + ids;
	}

	private static String script() {
		return """
				(function () {
					const D = (typeof Dex !== 'undefined') ? Dex : require('./sim/dex').Dex;
					const RULE = 'phantasmonpretera';
					D.data.Rulesets[RULE] = {
						effectType: 'Rule',
						name: 'Phantasmon Pre Tera',
						desc: "Pokemon terastallized from the start; the value: their ids, joined by '+'.",
						hasValue: true,
						onValidateRule(value) {
							return value;
						},
						onBegin() {
							const ids = String(this.ruleTable.valueRules.get(RULE) || '').split('+')
								.map((id) => id.trim().toLowerCase()).filter((id) => id);
							if (!ids.length) return;
							for (const side of this.sides) {
								const marked = side.pokemon.find((pokemon) => ids.includes(String(pokemon.uuid).toLowerCase())
									&& this.actions.canTerastallize(pokemon));
								if (!marked) continue;
								// One Terastallization a battle: the marked one's, so none for the rest of its team.
								for (const pokemon of side.pokemon) pokemon.canTerastallize = null;
								marked.m.phantasmonPreTera = true;
							}
						},
						onSwitchIn(pokemon) {
							if (pokemon.m.phantasmonPreTera && !pokemon.terastallized) this.actions.terastallize(pokemon);
						},
					};
				})();
				""";
	}
}
