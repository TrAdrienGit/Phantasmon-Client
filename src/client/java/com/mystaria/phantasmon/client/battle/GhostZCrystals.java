package com.mystaria.phantasmon.client.battle;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.pokemon.helditem.HeldItemManager;
import com.cobblemon.mod.common.api.pokemon.helditem.HeldItemProvider;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.pokemon.ZCrystals;

/**
 * Z-Crystals held "virtually" by Ghost battle Pokémon (TODO-28): there is no Minecraft item for them in the pack, so
 * {@link GhostBattlePokemonFactory} records the crystal here and this manager — first in Cobblemon's
 * {@link HeldItemProvider} — tells the battle engine the Pokémon holds it. Showdown then offers the Z-Move, and
 * Cobblemon's battle screen shows its Z button. Every other Pokémon is left to Cobblemon's own managers (null).
 */
public final class GhostZCrystals implements HeldItemManager {

	private static final Map<Pokemon, String> HELD = Collections.synchronizedMap(new WeakHashMap<>());
	private static boolean registered;

	private GhostZCrystals() {
	}

	/** Once, at client start. */
	public static synchronized void register() {
		if (!registered) {
			registered = true;
			HeldItemProvider.register(new GhostZCrystals(), Priority.HIGHEST);
		}
	}

	/** A battle copy built from a Ghost holding the Z-Crystal {@code showdownId}. */
	static void hold(Pokemon pokemon, String showdownId) {
		HELD.put(pokemon, showdownId);
	}

	@Override
	public String showdownId(BattlePokemon pokemon) {
		String held = HELD.get(pokemon.getEffectedPokemon());
		return held != null ? held : HELD.get(pokemon.getOriginalPokemon());
	}

	@Override
	public Component nameOf(String showdownId) {
		String name = ZCrystals.name(showdownId);
		return Component.literal(name != null ? name : showdownId);
	}

	@Override
	public void handleStartInstruction(BattlePokemon pokemon, PokemonBattle battle, BattleMessage message) {
	}

	@Override
	public void handleEndInstruction(BattlePokemon pokemon, PokemonBattle battle, BattleMessage message) {
	}

	@Override
	public void give(BattlePokemon pokemon, String showdownId) {
	}

	@Override
	public void take(BattlePokemon pokemon, String showdownId) {
		// Z-Crystals can't be knocked off or stolen in Showdown; nothing to do.
	}
}
