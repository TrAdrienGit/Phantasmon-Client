package com.mystaria.phantasmon.client.api;

import java.util.List;

import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;

/**
 * A battle AI an add-on brings (TODO-35, D-41; Phantasmon Events' expert and Reborn AIs), registered with
 * {@link PhantasmonApi#registerBattleAi}. Beyond Cobblemon's {@link BattleAI}, Phantasmon lets it:
 * <ul>
 *   <li>pick its lead against the player's team, as at team preview ({@link #orderTeam});</li>
 *   <li>pick its own replacement after a K.O. (checked; Cobblemon's AIs get the healthiest Pokémon instead);</li>
 *   <li>be told when Cobblemon refused its choice and the random AI played ({@link #onChoiceRefused}), so its chat
 *   debug never shows a move that was not played.</li>
 * </ul>
 */
public interface PhantasmonBattleAI extends BattleAI {

	/** Its own team, the lead first, against {@code opponents} (both have at least one Pokémon); as given by default. */
	default List<BattlePokemon> orderTeam(List<BattlePokemon> own, List<BattlePokemon> opponents) {
		return own;
	}

	/** Cobblemon refused its choice; {@code message} says what was refused and what the random AI played instead. */
	default void onChoiceRefused(String message) {
	}
}
