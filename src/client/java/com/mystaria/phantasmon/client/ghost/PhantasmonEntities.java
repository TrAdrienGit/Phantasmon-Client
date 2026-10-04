package com.mystaria.phantasmon.client.ghost;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

/**
 * The client-only {@link PokemonEntity}s this mod creates — Ghosts out in the world ({@link GhostEntityManager})
 * and Ghost battle send-outs ({@code BattleVisuals}) — so Cobblemon hooks can tell them from real Pokémon.
 * Weak references: a removed entity simply drops out. Client thread only.
 */
public final class PhantasmonEntities {

	private static final Set<PokemonEntity> ENTITIES = Collections.newSetFromMap(new WeakHashMap<>());

	private PhantasmonEntities() {
	}

	public static void register(PokemonEntity entity) {
		ENTITIES.add(entity);
	}

	public static boolean isPhantasmon(PokemonEntity entity) {
		return ENTITIES.contains(entity);
	}
}
