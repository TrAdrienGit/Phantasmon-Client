package com.mystaria.phantasmon.client.battle;

import java.util.List;
import java.util.UUID;

import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.battles.ai.RandomBattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Debug opponent for {@code /phantasmon debug battle}: Cobblemon's own
 * random AI playing a copy of the player's team, so the whole local engine +
 * native UI chain can be tested with a single account.
 */
public final class GhostAiBattleActor extends AIBattleActor {

	public GhostAiBattleActor(List<BattlePokemon> team) {
		super(UUID.randomUUID(), team, new RandomBattleAI());
	}

	@Override
	public ActorType getType() {
		return ActorType.PLAYER;
	}

	@Override
	public MutableComponent getName() {
		return Component.literal("Phantasmon AI");
	}

	@Override
	public MutableComponent nameOwned(String name) {
		return Component.translatable("cobblemon.battle.owned_pokemon", getName(), name);
	}
}
