package com.mystaria.phantasmon.client.battle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.api.net.NetworkPacket;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleEndPacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleFaintPacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleSwitchPokemonPacket;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.mystaria.phantasmon.client.ghost.PhantasmonEntities;

/**
 * Puts each side's active Pokémon in the world in front of its trainer during a Ghost battle, with Cobblemon's
 * own send-out and recall animations (Adrien 2026-10-03): the trainer swings, the ball is thrown
 * ({@code POKE_BALL_THROW}), the send-out beam plays, then the cry and the shiny ring; on a switch or a KO the
 * Pokémon is recalled in a beam ({@code POKE_BALL_RECALL}) and the replacement comes out once the recall is
 * over — Cobblemon's own order for a trainer (see {@code SwitchInstruction}/{@code FaintInstruction}).
 *
 * <p>How: Cobblemon's send-out/recall visuals are drawn client-side by its own renderer from synched entity
 * data ({@code BEAM_MODE} 1 = sending out, 3 = recalling; {@code PHASING_TARGET_ID} = the trainer the ball
 * travels from/to; {@code SPAWN_DIRECTION}). On a server, {@code Pokemon.sendOutWithAnimation} sets those
 * and advances them on a timer; here the same values are set on our client-only entities, with Cobblemon's
 * exact durations ({@code SendOutPokemonHandler.THROW_DURATION}/{@code SEND_OUT_DURATION}). The cry and shiny
 * ring are the very packets the server would send, handed to Cobblemon's client handlers.
 *
 * <p>Purely cosmetic and local, driven by the battle packets this client receives: the host and the remote
 * player each build the same scene, with no extra network traffic.
 */
public final class BattleVisuals {

	private static final Logger LOG = LoggerFactory.getLogger(BattleVisuals.class);
	/** Cobblemon's {@code SendOutPokemonHandler} timings, in seconds. */
	private static final float THROW_DURATION = 0.5F;
	private static final float SEND_OUT_DURATION = 1.5F;
	private static final int BEAM_NONE = 0;
	private static final int BEAM_SEND_OUT = 1;
	private static final int BEAM_RECALL = 3;
	/** Distance from its trainer, along the line to the opponent (capped to a third of the gap). */
	private static final double SEND_OUT_DISTANCE = 3.0;
	/** When the opponent has no player entity in this world (debug AI), its Pokémon stands this far in front of us. */
	private static final double FACING_DISTANCE = 7.0;

	private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "phantasmon-battle-visuals");
		thread.setDaemon(true);
		return thread;
	});

	/** Showdown actor id ("p1", "p2") → trainer uuid, from the battle's initialization. */
	private static final Map<String, UUID> trainers = new HashMap<>();
	/** Slot ("p1a"...) → entity currently standing for it. */
	private static final Map<String, PokemonEntity> entities = new HashMap<>();
	/** Entities playing their recall beam, removed once it's over (or right away by {@link #clear}). */
	private static final Set<PokemonEntity> leaving = new HashSet<>();
	/** Bumped by {@link #clear}, so timers scheduled for a previous battle do nothing. */
	private static int generation;

	private BattleVisuals() {
	}

	/** Called on the client thread for every battle packet delivered to Cobblemon's UI. */
	public static void onPacket(NetworkPacket<?> packet) {
		try {
			if (packet instanceof BattleInitializePacket init) {
				clear();
				for (var side : new BattleInitializePacket.BattleSideDTO[] { init.getSide1(), init.getSide2() }) {
					for (var actor : side.getActors()) {
						trainers.put(actor.getShowdownId(), actor.getUuid());
					}
				}
				// Right after the launch intro, the send-outs are staged for the camera: the opponent's first, then ours.
				boolean staged = stageSendOuts();
				UUID self = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
				for (var side : new BattleInitializePacket.BattleSideDTO[] { init.getSide1(), init.getSide2() }) {
					for (var actor : side.getActors()) {
						boolean local = actor.getUuid().equals(self);
						float delay = !staged ? 0f : local ? BattleCinematic.LOCAL_SEND_OUT_DELAY : BattleCinematic.OPPONENT_SEND_OUT_DELAY;
						char slot = 'a';
						for (var active : actor.getActivePokemon()) {
							if (active != null) {
								String pnx = actor.getShowdownId() + slot;
								if (delay > 0f) {
									later(delay, () -> sendOut(pnx, active));
								} else {
									sendOut(pnx, active);
								}
							}
							slot++;
						}
					}
				}
			} else if (packet instanceof BattleSwitchPokemonPacket switchPacket) {
				boolean recalled = recall(switchPacket.getPnx());
				String pnx = switchPacket.getPnx();
				var incoming = switchPacket.getNewPokemon();
				if (recalled) {
					later(SEND_OUT_DURATION, () -> sendOut(pnx, incoming));
				} else {
					sendOut(pnx, incoming);
				}
			} else if (packet instanceof BattleFaintPacket faint) {
				recall(faint.getPnx());
			} else if (packet instanceof BattleEndPacket) {
				new HashSet<>(entities.keySet()).forEach(BattleVisuals::recall);
				trainers.clear();
			}
		} catch (Exception ex) {
			// Never let a cosmetic failure break the battle UI.
			LOG.warn("Ghost battle visuals failed for {}", packet.getId(), ex);
		}
	}

	/** Tells the cinematic where both sides' Pokémon will stand; true if it films their send-outs. */
	private static boolean stageSendOuts() {
		ClientLevel level = Minecraft.getInstance().level;
		Player self = Minecraft.getInstance().player;
		if (level == null || self == null) {
			return false;
		}
		Vec3 localSpot = null;
		Vec3 opponentSpot = null;
		for (Map.Entry<String, UUID> entry : trainers.entrySet()) {
			Placement placement = placement(level, self, entry.getKey());
			if (placement == null) {
				continue;
			}
			if (entry.getValue().equals(self.getUUID())) {
				localSpot = placement.position();
			} else {
				opponentSpot = placement.position();
			}
		}
		return BattleCinematic.beginSendOuts(localSpot, opponentSpot);
	}

	/** The client-side entity standing at a battle position ({@code p1a}...), if any — for {@link ActionEffectPlayer}. */
	public static PokemonEntity entityAt(String pnx) {
		PokemonEntity entity = entities.get(pnx);
		return entity == null || entity.isRemoved() ? null : entity;
	}

	/** Changes whenever the scene is cleared: lets delayed work notice it belongs to a finished battle. */
	public static int generation() {
		return generation;
	}

	/** World left / connection lost: drop everything at once, no animation. */
	public static void clear() {
		generation++;
		ClientLevel level = Minecraft.getInstance().level;
		Set<PokemonEntity> all = new HashSet<>(entities.values());
		all.addAll(leaving);
		entities.clear();
		leaving.clear();
		trainers.clear();
		if (level != null) {
			all.forEach(entity -> level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED));
		}
	}

	private static void sendOut(String pnx, BattleInitializePacket.ActiveBattlePokemonDTO dto) {
		ClientLevel level = Minecraft.getInstance().level;
		Player self = Minecraft.getInstance().player;
		if (level == null || self == null || pnx.length() < 2 || entities.containsKey(pnx)) {
			return;
		}
		Placement placement = placement(level, self, pnx.substring(0, 2));
		if (placement == null) {
			return;
		}

		// Not PokemonProperties.create(): it initialises a default moveset through Cobblemon's moveset builders,
		// a server datapack registry that is empty on a client connected to a remote/LAN server (crashed the
		// guest's send-outs). apply() only sets the visual properties, like the Ghost entities do.
		Pokemon pokemon = new Pokemon();
		dto.getProperties().apply(pokemon);
		// The shiny model is chosen from the Pokémon's shiny flag, not only from the aspect (Adrien: sent-out
		// shinies were drawn normal).
		pokemon.setShiny(dto.getAspects().contains("shiny"));
		pokemon.setForcedAspects(new HashSet<>(dto.getAspects()));
		pokemon.updateAspects();
		PokemonEntity entity = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
		// The renderer reads the entity's synched ASPECTS, normally filled by Cobblemon's server delegate; a
		// client-only entity has none, so special forms rendered as the base model (TODO-12). Same as the Ghosts.
		entity.getEntityData().set(PokemonEntity.Companion.getASPECTS(), new HashSet<>(dto.getAspects()));
		// Same for the label's level (default 1).
		entity.getEntityData().set(PokemonEntity.Companion.getLABEL_LEVEL(), pokemon.getLevel());
		entity.setNoAi(true);
		entity.setInvulnerable(true);
		entity.setPos(placement.position().x, placement.position().y, placement.position().z);
		entity.setYRot(placement.yaw());
		entity.setYBodyRot(placement.yaw());
		entity.setYHeadRot(placement.yaw());
		entity.getEntityData().set(PokemonEntity.getSPAWN_DIRECTION(), placement.yaw());

		Player trainer = placement.trainer();
		if (trainer != null) {
			trainer.swing(InteractionHand.MAIN_HAND);
			entity.setPhasingTargetId(trainer.getId());
			playSound(level, trainer.position(), CobblemonSounds.POKE_BALL_THROW);
		}
		entity.setBeamMode(BEAM_SEND_OUT);
		level.addEntity(entity);
		PhantasmonEntities.register(entity);
		entities.put(pnx, entity);

		boolean shiny = dto.getAspects().contains("shiny");
		later(THROW_DURATION, () -> entity.setPhasingTargetId(-1));
		later(SEND_OUT_DURATION, () -> {
			if (entity.getBeamMode() == BEAM_RECALL || entity.isRemoved()) {
				return; // a recall already took over, same rule as Cobblemon's send-out
			}
			entity.setPhasingTargetId(-1);
			entity.setBeamMode(BEAM_NONE);
			CobblemonPackets.dispatchLocally(new PlayPosableAnimationPacket(entity.getId(), Set.of("cry"), List.of()));
			if (shiny) {
				CobblemonPackets.dispatchLocally(new SpawnSnowstormEntityParticlePacket(
						ResourceLocation.fromNamespaceAndPath("cobblemon", "shiny_ring"), entity.getId(),
						List.of("shiny_particles", "middle"), null, List.of()));
			}
		});
	}

	// ---- Mega Evolution / Primal Reversion (Adrien 2026-10-05) ----

	private static final int TRANSFORM_TICKS = 24;
	private static final float[][] RAINBOW = {
			{ 1f, 0.25f, 0.3f }, { 1f, 0.6f, 0.15f }, { 1f, 0.95f, 0.2f }, { 0.3f, 1f, 0.4f },
			{ 0.25f, 0.75f, 1f }, { 0.55f, 0.35f, 1f }, { 1f, 0.4f, 0.9f } };

	/**
	 * The transformation, on this client's scene: a double helix of energy rising round the Pokémon for 1.2 s
	 * (rainbow for a Mega Evolution, red for Groudon / blue for Kyogre's Primal Reversion), then a flash and a burst,
	 * hiding the moment its model switches (the aspect the pack's resolvers key on), then its cry.
	 */
	public static void transform(FormeChangeVisual change) {
		ClientLevel level = Minecraft.getInstance().level;
		PokemonEntity entity = entityAt(change.pnx());
		if (level == null || entity == null) {
			return;
		}
		float[][] palette = change.primal()
				? new float[][] { "kyogre".equals(change.species()) ? new float[] { 0.2f, 0.5f, 1f } : new float[] { 1f, 0.25f, 0.1f },
						"kyogre".equals(change.species()) ? new float[] { 0.5f, 0.9f, 1f } : new float[] { 1f, 0.7f, 0.2f } }
				: RAINBOW;
		level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT,
				SoundSource.NEUTRAL, 0.8F, change.primal() ? 0.6F : 1.2F, false);
		for (int tick = 0; tick < TRANSFORM_TICKS; tick++) {
			int t = tick;
			later(t * 0.05f, () -> helix(level, entity, palette, t));
		}
		later(TRANSFORM_TICKS * 0.05f, () -> {
			if (entity.isRemoved()) {
				return;
			}
			java.util.Set<String> aspects = new HashSet<>(entity.getEntityData().get(PokemonEntity.Companion.getASPECTS()));
			aspects.add(change.aspect());
			entity.getEntityData().set(PokemonEntity.Companion.getASPECTS(), aspects);
			double height = entity.getBbHeight();
			level.addParticle(net.minecraft.core.particles.ParticleTypes.FLASH, entity.getX(), entity.getY() + height / 2, entity.getZ(), 0, 0, 0);
			for (int i = 0; i < 40; i++) {
				double angle = i * Math.PI * 2 / 40;
				level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, entity.getX(), entity.getY() + height / 2, entity.getZ(),
						Math.cos(angle) * 0.25, (i % 5 - 2) * 0.06, Math.sin(angle) * 0.25);
			}
			level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
					SoundSource.NEUTRAL, 0.9F, 1F, false);
			later(0.35f, () -> CobblemonPackets.dispatchLocally(new PlayPosableAnimationPacket(entity.getId(), Set.of("cry"), List.of())));
		});
	}

	/** One tick of the rising double helix of coloured energy. */
	private static void helix(ClientLevel level, PokemonEntity entity, float[][] palette, int tick) {
		if (entity.isRemoved()) {
			return;
		}
		double radius = Math.max(0.6, entity.getBbWidth() * 0.8);
		double height = Math.max(1.0, entity.getBbHeight() * 1.2);
		for (int strand = 0; strand < 2; strand++) {
			for (int k = 0; k < 3; k++) {
				double progress = ((tick * 3 + k) % 36) / 36.0;
				double angle = progress * Math.PI * 6 + strand * Math.PI + tick * 0.25;
				float[] color = palette[(tick + k + strand * 3) % palette.length];
				level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(color[0], color[1], color[2]), 1.4f),
						entity.getX() + Math.cos(angle) * radius, entity.getY() + progress * height, entity.getZ() + Math.sin(angle) * radius,
						0, 0.02, 0);
			}
		}
		if (tick % 4 == 0) {
			level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY() + entity.getBbHeight() / 2,
					entity.getZ(), 0, 0.1, 0);
		}
	}

	/** Recall beam back to the trainer, then removal. Returns whether there was something to recall. */
	private static boolean recall(String pnx) {
		PokemonEntity entity = entities.remove(pnx);
		ClientLevel level = Minecraft.getInstance().level;
		if (entity == null || level == null) {
			return false;
		}
		Player trainer = trainerEntity(level, pnx.substring(0, 2));
		playSound(level, entity.position(), CobblemonSounds.POKE_BALL_RECALL);
		entity.setPhasingTargetId(trainer != null ? trainer.getId() : -1);
		entity.setBeamMode(BEAM_RECALL);
		entity.noPhysics = true;
		entity.setNoGravity(true);
		leaving.add(entity);
		later(SEND_OUT_DURATION, () -> {
			if (leaving.remove(entity)) {
				level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
			}
		});
		return true;
	}

	/** Runs {@code task} on the client thread after {@code seconds}, unless the scene was cleared meanwhile. */
	private static void later(float seconds, Runnable task) {
		int scheduledFor = generation;
		TIMER.schedule(() -> Minecraft.getInstance().execute(() -> {
			if (scheduledFor == generation) {
				try {
					task.run();
				} catch (Exception ex) {
					LOG.warn("Ghost battle visual step failed", ex);
				}
			}
		}), (long) (seconds * 1000), TimeUnit.MILLISECONDS);
	}

	private static void playSound(ClientLevel level, Vec3 position, SoundEvent sound) {
		level.playLocalSound(position.x, position.y, position.z, sound, SoundSource.NEUTRAL, 0.6F, 1F, false);
	}

	private record Placement(Vec3 position, float yaw, Player trainer) {
	}

	/** In front of the trainer, toward the opponent's trainer; falls back to the local player's facing. */
	private static Placement placement(ClientLevel level, Player self, String actorId) {
		Player trainer = trainerEntity(level, actorId);
		Player opponent = null;
		for (Map.Entry<String, UUID> entry : trainers.entrySet()) {
			if (!entry.getKey().equals(actorId)) {
				opponent = trainerEntity(level, entry.getKey());
			}
		}
		if (trainer != null && opponent != null) {
			Vec3 from = trainer.position();
			Vec3 toOpponent = horizontal(opponent.position().subtract(from));
			double distance = Math.min(SEND_OUT_DISTANCE, toOpponent.length() / 3.0);
			Vec3 direction = toOpponent.lengthSqr() < 1.0E-4 ? horizontal(trainer.getLookAngle()) : toOpponent.normalize();
			return new Placement(from.add(direction.scale(distance)), yawOf(direction), trainer);
		}
		Vec3 look = horizontal(self.getLookAngle());
		Vec3 forward = look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.normalize();
		if (trainer != null) {
			return new Placement(trainer.position().add(forward.scale(SEND_OUT_DISTANCE)), yawOf(forward), trainer);
		}
		// No trainer entity for this side (debug AI): stand in front of the local player, facing them.
		return new Placement(self.position().add(forward.scale(FACING_DISTANCE)), yawOf(forward.scale(-1)), null);
	}

	private static Player trainerEntity(ClientLevel level, String actorId) {
		UUID uuid = trainers.get(actorId);
		return uuid == null ? null : level.getPlayerByUUID(uuid);
	}

	private static Vec3 horizontal(Vec3 vector) {
		return new Vec3(vector.x, 0, vector.z);
	}

	/** Minecraft yaw facing {@code direction}. */
	private static float yawOf(Vec3 direction) {
		return (float) (Math.toDegrees(Math.atan2(-direction.x, direction.z)));
	}
}
