package com.mystaria.phantasmon.client.battle;

import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.api.net.NetworkPacket;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.battle.BattleFaintPacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleSwitchPokemonPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.mystaria.phantasmon.client.hub.HubAvatars;

/**
 * Ghost battles seen from afar (Adrien 2026-10-07): like in Cobblemon, where a battle's Pokémon are real entities that
 * everyone around sees, the players of either player's server group and — when either player is in the Global Hub —
 * every Hub member see the field without watching: the Pokémon in front of their trainer (or the trainer's Hub
 * avatar), send-outs, recalls, KOs, move animations, Mega / Primal / Z / Tera — no battle screen, camera, chat or music.
 *
 * <p>The backend decides who sees which battle and forwards the host's spectator stream as {@code BattleFieldPacket}
 * (the first one addressed to this client being the field as it stands, a {@code BattleInitializePacket} without ally
 * side); {@code BattleFieldEnded} drops the scene. Several battles can be seen at once, one scene each, independent of
 * this client's own battle ({@link BattleVisuals}). Client thread only.
 */
public final class BattleFieldScenes {

	private static final Logger LOG = LoggerFactory.getLogger(BattleFieldScenes.class);
	/** When a side's trainer isn't loaded here, its Pokémon stands this far in front of the other trainer. */
	private static final double FACING_DISTANCE = 7.0;
	/** Pending send-outs (trainer not loaded yet) are retried this often. */
	private static final int RETRY_TICKS = 20;

	/** Backend battle uuid → its scene. */
	private static final Map<UUID, Scene> scenes = new HashMap<>();
	private static int ticks;

	private BattleFieldScenes() {
	}

	/** {@code BattleFieldPacket}: one packet of a battle's public stream. */
	public static void onPacket(UUID battleUuid, String id, String payloadBase64) {
		if (battleUuid == null || id == null || payloadBase64 == null) {
			return;
		}
		if (!RelayedPacketPolicy.guestAccepts(id)) {
			LOG.warn("Dropped a non-battle packet relayed to a field viewer: {}", id);
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		try {
			byte[] payload = Base64.getDecoder().decode(payloadBase64);
			Scene scene = scenes.get(battleUuid);
			if (FormeChangeVisual.PACKET_ID.equals(id)) {
				if (scene != null) {
					scene.transform(FormeChangeVisual.fromBytes(payload));
				}
				return;
			}
			if (ActionEffectEvent.PACKET_ID.equals(id)) {
				if (scene != null) {
					ActionEffectPlayer.play(ActionEffectEvent.fromBytes(payload), null, scene);
				}
				return;
			}
			NetworkPacket<?> packet = CobblemonPackets.decode(new CobblemonPackets.Encoded(id, payload));
			if (packet instanceof BattleInitializePacket init) {
				if (scene != null) {
					scene.discard();
				}
				scene = new Scene(level);
				scenes.put(battleUuid, scene);
				scene.initialize(init);
			} else if (scene != null) {
				// Anything before the field as it stands is dropped: it carries the whole battle.
				scene.onPacket(packet);
			}
		} catch (Exception ex) {
			// Never let a cosmetic failure break anything else.
			LOG.warn("Battle field packet {} failed", id, ex);
		}
	}

	/**
	 * {@code BattleFieldEnded}: over, or this client no longer sees it — every Pokémon goes back in. When we start
	 * watching it ({@code SPECTATING}), the spectator scene takes over at once: no recall beams beside it.
	 */
	public static void onEnded(UUID battleUuid, String reason) {
		Scene scene = scenes.remove(battleUuid);
		if (scene == null) {
			return;
		}
		if ("SPECTATING".equals(reason)) {
			scene.discard();
		} else {
			scene.end();
		}
	}

	/** World left / connection lost: everything goes at once. */
	public static void clear() {
		scenes.values().forEach(Scene::discard);
		scenes.clear();
	}

	/** Whether this entity belongs to a battle seen from afar (its Tera glow survives the local scene's clear). */
	public static boolean owns(int entityId) {
		for (Scene scene : scenes.values()) {
			for (PokemonEntity entity : scene.entities.values()) {
				if (entity.getId() == entityId) {
					return true;
				}
			}
		}
		return false;
	}

	/** Every client tick: scenes of another world are dropped, pending send-outs retried. */
	public static void tick() {
		if (scenes.isEmpty()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		scenes.values().removeIf(scene -> {
			if (scene.level != level) {
				scene.generation++;
				return true;
			}
			return false;
		});
		if (++ticks % RETRY_TICKS == 0) {
			scenes.values().forEach(Scene::retryPending);
		}
	}

	private static final class Scene implements ActionEffectPlayer.Scene {

		final ClientLevel level;
		/** Showdown actor id ("p1", "p2") → trainer uuid. */
		final Map<String, UUID> trainers = new HashMap<>();
		/** Slot ("p1a"...) → entity standing for it. */
		final Map<String, PokemonEntity> entities = new HashMap<>();
		/** Slots whose trainer (and the other one) aren't loaded here yet: sent out once one is. */
		final Map<String, BattleInitializePacket.ActiveBattlePokemonDTO> pending = new LinkedHashMap<>();
		final Set<PokemonEntity> leaving = new HashSet<>();
		/** Battle Pokémon uuid → Tera type, so it glows again when sent back out. */
		final Map<String, String> tera = new HashMap<>();
		int generation;

		Scene(ClientLevel level) {
			this.level = level;
		}

		@Override
		public PokemonEntity entityAt(String pnx) {
			PokemonEntity entity = entities.get(pnx);
			return entity == null || entity.isRemoved() ? null : entity;
		}

		@Override
		public int generation() {
			return generation;
		}

		void initialize(BattleInitializePacket init) {
			for (var side : new BattleInitializePacket.BattleSideDTO[] { init.getSide1(), init.getSide2() }) {
				for (var actor : side.getActors()) {
					trainers.put(actor.getShowdownId(), actor.getUuid());
				}
			}
			for (var side : new BattleInitializePacket.BattleSideDTO[] { init.getSide1(), init.getSide2() }) {
				for (var actor : side.getActors()) {
					char slot = 'a';
					for (var active : actor.getActivePokemon()) {
						if (active != null) {
							sendOut(actor.getShowdownId() + slot, active);
						}
						slot++;
					}
				}
			}
		}

		void onPacket(NetworkPacket<?> packet) {
			if (packet instanceof BattleSwitchPokemonPacket switchPacket) {
				String pnx = switchPacket.getPnx();
				var incoming = switchPacket.getNewPokemon();
				if (recall(pnx)) {
					later(BattleVisuals.recallSeconds(), () -> sendOut(pnx, incoming));
				} else {
					sendOut(pnx, incoming);
				}
			} else if (packet instanceof BattleFaintPacket faint) {
				recall(faint.getPnx());
			}
		}

		void transform(FormeChangeVisual change) {
			if (change.aspect() != null && change.aspect().startsWith(FormeChangeVisual.TERA_PREFIX)) {
				tera.put(change.uuid(), change.teraType());
			}
			BattleSpectacle.playInWorld(change, entityAt(change.pnx()));
		}

		void retryPending() {
			for (Map.Entry<String, BattleInitializePacket.ActiveBattlePokemonDTO> entry : Map.copyOf(pending).entrySet()) {
				sendOut(entry.getKey(), entry.getValue());
			}
		}

		private void sendOut(String pnx, BattleInitializePacket.ActiveBattlePokemonDTO dto) {
			if (pnx.length() < 2 || entities.containsKey(pnx) || Minecraft.getInstance().level != level) {
				return;
			}
			String actor = pnx.substring(0, 2);
			Player trainer = trainer(actor);
			Player other = null;
			for (String id : trainers.keySet()) {
				if (!id.equals(actor)) {
					other = trainer(id);
				}
			}
			Vec3 at;
			Vec3 facing;
			if (trainer != null && other != null) {
				Vec3 toOpponent = BattleVisuals.horizontal(other.position().subtract(trainer.position()));
				facing = toOpponent.lengthSqr() < 1.0E-4 ? forward(trainer) : toOpponent.normalize();
				at = trainer.position().add(facing.scale(BattleVisuals.sendOutDistance(toOpponent.length())));
			} else if (trainer != null) {
				// The opponent plays elsewhere (another server, not in the Hub here) or is the admin's mirror.
				facing = forward(trainer);
				at = trainer.position().add(facing.scale(BattleVisuals.sendOutDistance(Double.MAX_VALUE)));
			} else if (other != null) {
				facing = forward(other).scale(-1);
				at = other.position().add(forward(other).scale(FACING_DISTANCE));
			} else {
				pending.put(pnx, dto);
				return;
			}
			pending.remove(pnx);
			PokemonEntity entity = BattleVisuals.sendOutEntity(level, dto, at, BattleVisuals.yawOf(facing), trainer, this::later);
			entities.put(pnx, entity);
			BattleSpectacle.glow(entity, tera.get(dto.getUuid().toString()));
		}

		/** Recall beam, then removal; whether there was something to recall. */
		private boolean recall(String pnx) {
			pending.remove(pnx);
			PokemonEntity entity = entities.remove(pnx);
			if (entity == null) {
				return false;
			}
			if (entity.isRemoved() || Minecraft.getInstance().level != level) {
				return true;
			}
			BattleVisuals.recallEntity(level, entity, pnx.length() < 2 ? null : trainer(pnx.substring(0, 2)));
			leaving.add(entity);
			int scheduledFor = generation;
			BattleVisuals.schedule(BattleVisuals.recallSeconds(), () -> {
				if (leaving.remove(entity) && scheduledFor == generation) {
					level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
				}
			});
			return true;
		}

		/** The battle is over for this client: recall beams, then the scene is gone. */
		void end() {
			pending.clear();
			Set.copyOf(entities.keySet()).forEach(this::recall);
			// Anything a recall could not reach in time is removed with the scene.
			int scheduledFor = generation;
			BattleVisuals.schedule(BattleVisuals.recallSeconds() + 0.5f, () -> {
				if (scheduledFor == generation) {
					discard();
				}
			});
		}

		/** Everything removed at once, no animation; timers of this scene do nothing any more. */
		void discard() {
			generation++;
			Set<PokemonEntity> all = new HashSet<>(entities.values());
			all.addAll(leaving);
			entities.clear();
			leaving.clear();
			pending.clear();
			if (Minecraft.getInstance().level == level) {
				all.forEach(entity -> level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED));
			}
		}

		private void later(float seconds, Runnable task) {
			int scheduledFor = generation;
			BattleVisuals.schedule(seconds, () -> {
				if (scheduledFor == generation && Minecraft.getInstance().level == level) {
					try {
						task.run();
					} catch (Exception ex) {
						LOG.warn("Battle field visual step failed", ex);
					}
				}
			});
		}

		private Player trainer(String actorId) {
			UUID uuid = trainers.get(actorId);
			// A player of another server stands here as their Hub avatar, if this client is in the Hub.
			return HubAvatars.playerOrAvatar(level, uuid);
		}

		private static Vec3 forward(Player player) {
			Vec3 look = BattleVisuals.horizontal(player.getLookAngle());
			return look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.normalize();
		}
	}
}
