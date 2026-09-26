package com.mystaria.phantasmon.client.ghost;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders other players' Ghost Pokémon as purely client-side entities (CAD
 * Partie 2 §7: "rendu 100% client" — no real Minecraft entity ever exists on
 * any server, since there is none). Reuses Cobblemon's own {@code PokemonEntity}
 * and its already-registered renderer for the visuals/animations, but the
 * entity is constructed here and added directly to the local
 * {@link ClientLevel} via {@link ClientLevel#addEntity}, completely bypassing
 * the normal server-authoritative spawn-packet flow — Cobblemon's own
 * {@code Pokemon.sendOut(...)} requires a {@code ServerLevel} and can't be
 * used for this.
 *
 * <p>{@code setNoAi(true)} is critical: without it this would be a full
 * {@code Mob} with Cobblemon's normal wandering/battle AI goals, which makes
 * no sense for an entity with no real collision/interaction authority (CAD
 * §7.1 — Ghosts are cosmetic, moved only by position updates from their
 * owner, never by their own decision-making).
 */
public final class GhostEntityManager {

	private static final Logger LOG = LoggerFactory.getLogger(GhostEntityManager.class);

	/** Keyed by the *owner* player's uuid — one Ghost out at a time per player (CAD Partie 1 §17 team size aside, only one is ever "sent out"). */
	private final Map<UUID, PokemonEntity> activeGhosts = new ConcurrentHashMap<>();

	public void spawn(UUID ownerUuid, String species, String form, boolean shiny, int level, double x, double y, double z) {
		ClientLevel clientLevel = Minecraft.getInstance().level;
		if (clientLevel == null) {
			return;
		}
		despawn(ownerUuid);

		Species resolvedSpecies = PokemonSpecies.INSTANCE.getByName(species);
		if (resolvedSpecies == null) {
			LOG.warn("Cannot render Ghost: unresolved species '{}' (missing Cobblemon data or version mismatch)", species);
			return;
		}

		Pokemon pokemon = new Pokemon();
		pokemon.setSpecies(resolvedSpecies);
		if (form != null) {
			FormData formData = resolveForm(resolvedSpecies, form);
			if (formData != null) {
				pokemon.setForm(formData);
			}
		}
		pokemon.setShiny(shiny);
		pokemon.setLevel(Math.max(1, level));

		PokemonEntity entity = new PokemonEntity(clientLevel, pokemon, CobblemonEntities.POKEMON);
		entity.setNoAi(true);
		entity.setInvulnerable(true);
		entity.setPos(x, y, z);

		clientLevel.addEntity(entity);
		activeGhosts.put(ownerUuid, entity);
	}

	/** No interpolation for V1 — a direct teleport-style position update, sent roughly once a second. Known limitation: can look slightly choppy between updates. */
	public void move(UUID ownerUuid, double x, double y, double z) {
		PokemonEntity entity = activeGhosts.get(ownerUuid);
		if (entity != null) {
			entity.setPos(x, y, z);
		}
	}

	public void despawn(UUID ownerUuid) {
		PokemonEntity entity = activeGhosts.remove(ownerUuid);
		if (entity == null) {
			return;
		}
		ClientLevel clientLevel = Minecraft.getInstance().level;
		if (clientLevel != null) {
			clientLevel.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
		}
	}

	/** Called on disconnect/dimension change — nothing to notify server-side here, the caller handles that. */
	public void despawnAll() {
		activeGhosts.keySet().forEach(this::despawn);
	}

	private static FormData resolveForm(Species species, String formIdentifier) {
		FormData exact = species.getFormByName(formIdentifier);
		if (exact != null) {
			return exact;
		}
		return species.getForms().stream()
				.filter(candidate -> candidate.getName().equalsIgnoreCase(formIdentifier))
				.findFirst()
				.orElse(null);
	}
}
