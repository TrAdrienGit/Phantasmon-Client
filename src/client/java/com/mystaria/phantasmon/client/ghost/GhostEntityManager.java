package com.mystaria.phantasmon.client.ghost;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mystaria.phantasmon.client.battle.CobblemonPackets;
import com.mystaria.phantasmon.client.gui.PokemonGuiRendering;

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
 * §7.1 — Ghosts are cosmetic, never driven by their own decision-making).
 *
 * <p><b>Follow and roam (Adrien 2026-10-03)</b>: every client tick ({@link #tick()}) a Ghost is eased toward a
 * spot behind and beside its owner (collision-aware, jumping one-block steps, facing its travel direction).
 * The spot is anchored on the owner's <i>travel</i> heading, frozen while the owner stands still, so merely
 * turning the camera never moves the Ghost. After {@link #ROAM_IDLE_TICKS} without the owner moving, the
 * Ghost roams: every few seconds it strolls to a random point inside a {@link #ROAM_RADIUS}-block circle
 * around the owner, then rests. As soon as the owner moves again it comes back to its spot. The owner's live
 * position comes from its own client-side {@code Player} entity when loaded, else the last backend-relayed one.
 *
 * <p><b>Same send-out and recall animations as a Ghost battle</b>: Cobblemon draws them client-side from
 * synched entity data ({@code BEAM_MODE}, {@code PHASING_TARGET_ID}) with a server-side timer; the same values
 * and Cobblemon's exact durations are driven from here, like {@code BattleVisuals} does.
 *
 * <p><b>Forms and shiny</b>: the renderer reads the entity's <i>synched</i> {@code ASPECTS}, which Cobblemon's
 * server delegate normally fills in — a client-only entity has none, so the aspects (form aspects + shiny)
 * are written to the entity data by hand, otherwise special forms and shinies render as the base model.
 *
 * <p><b>{@code [Ghost]} indicator</b> (CAD Partie 1 §5, §22.1): the Pokémon's nickname is set to
 * {@code "[Ghost] <nickname or species>"}, so Cobblemon's own label (shown when looking at the Pokémon, with its
 * level) carries the indicator — same look and behaviour as a normal Pokémon's label otherwise.
 */
public final class GhostEntityManager {

	private static final Logger LOG = LoggerFactory.getLogger(GhostEntityManager.class);

	/** Target spot relative to the owner, in blocks: behind (along the owner's travel heading) and to the right. */
	private static final double FOLLOW_BEHIND = 1.5;
	private static final double FOLLOW_SIDE = 1.2;
	/** A flying Ghost hovers this far above its target instead of walking. */
	private static final double FLYER_HOVER_HEIGHT = 1.2;
	/** Hysteresis: an idle Ghost only starts walking once this far from its spot, and walks until nearly on it. */
	private static final double START_MOVING_DISTANCE = 1.3;
	private static final double STOP_MOVING_DISTANCE = 0.4;
	/** Blocks per tick (20 tps): eased toward the target, capped just above sprint speed so it keeps up. */
	private static final double MAX_STEP = 0.40;
	private static final double MIN_STEP = 0.06;
	private static final double STEP_GAIN = 0.12;
	private static final double TELEPORT_DISTANCE = 20.0;
	private static final int STUCK_TICKS_BEFORE_SNAP = 60;
	private static final double STUCK_MIN_DISTANCE = 3.0;
	private static final float MAX_TURN_PER_TICK = 25f;
	private static final double GRAVITY = 0.08;
	private static final double JUMP_VELOCITY = 0.42;

	/** The owner counts as standing still below this displacement per tick (a camera turn moves nothing). */
	private static final double OWNER_MOVE_EPSILON = 0.02;
	private static final double OWNER_VERTICAL_EPSILON = 0.08;
	/** 5 seconds of an owner who doesn't move, then the Ghost starts roaming. */
	private static final int ROAM_IDLE_TICKS = 100;
	private static final double ROAM_RADIUS = 10.0;
	/** A stroll, not a chase. */
	private static final double ROAM_MAX_STEP = 0.12;
	private static final double ROAM_ARRIVED_DISTANCE = 0.5;
	private static final int ROAM_FIRST_PAUSE_MIN = 20;
	private static final int ROAM_FIRST_PAUSE_MAX = 80;
	private static final int ROAM_PAUSE_MIN = 60;
	private static final int ROAM_PAUSE_MAX = 200;
	private static final float OWNER_HEADING_TURN_PER_TICK = 10f;

	/** Cobblemon's {@code SendOutPokemonHandler} timings, in seconds (same as {@code BattleVisuals}). */
	private static final float THROW_DURATION = 0.5F;
	private static final float SEND_OUT_DURATION = 1.5F;
	private static final int BEAM_NONE = 0;
	private static final int BEAM_SEND_OUT = 1;
	private static final int BEAM_RECALL = 3;

	private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "phantasmon-ghost-visuals");
		thread.setDaemon(true);
		return thread;
	});

	private static final class Ghost {
		final PokemonEntity entity;
		final boolean flyer;
		final boolean shiny;
		/** Last owner position relayed by the backend — only used when the owner's own entity isn't loaded client-side. */
		Vec3 lastRelayedOwnerPos;
		Vec3 lastOwnerPos;
		/** Heading the follow spot is anchored on: follows the owner's travel direction, frozen while it stands still. */
		float anchorYaw;
		int ownerIdleTicks;
		boolean roaming;
		Vec3 roamTarget;
		int age;
		int nextRoamAge;
		boolean moving;
		double verticalVelocity;
		int stuckTicks;
		/** Send-out beam in progress: the Ghost stands still until then. */
		long busyUntilMillis;
		/** Widens the follow spot for big hitboxes so the Ghost doesn't stand inside its owner (the hitbox itself is never touched). */
		double followScale = 1.0;

		Ghost(PokemonEntity entity, boolean flyer, boolean shiny, Vec3 spawnPos, float anchorYaw) {
			this.entity = entity;
			this.flyer = flyer;
			this.shiny = shiny;
			this.lastRelayedOwnerPos = spawnPos;
			this.anchorYaw = anchorYaw;
		}
	}

	/** Keyed by the *owner* player's uuid — one Ghost out at a time per player (CAD Partie 1 §17 team size aside, only one is ever "sent out"). */
	private final Map<UUID, Ghost> activeGhosts = new ConcurrentHashMap<>();
	/** Entities playing their recall beam, removed once it's over (or right away by {@link #despawnAll}). */
	private final Set<PokemonEntity> leaving = ConcurrentHashMap.newKeySet();
	/** Bumped by {@link #despawnAll}, so timers scheduled before do nothing. */
	private volatile int generation;

	public void spawn(UUID ownerUuid, String species, String form, boolean shiny, Object storedGender, int level, String nickname,
			double x, double y, double z) {
		ClientLevel clientLevel = Minecraft.getInstance().level;
		if (clientLevel == null) {
			return;
		}
		removeNow(ownerUuid);

		Species resolvedSpecies = PokemonSpecies.INSTANCE.getByName(species);
		if (resolvedSpecies == null) {
			LOG.warn("Cannot render Ghost: unresolved species '{}' (missing Cobblemon data or version mismatch)", species);
			return;
		}

		Pokemon pokemon = new Pokemon();
		pokemon.setSpecies(resolvedSpecies);
		pokemon.setShiny(shiny);
		// Models that differ by gender (Meowstic, Pikachu...) read the gender aspect: stored gender, else the species' ratio.
		String genderAspect = PokemonGuiRendering.genderAspect(resolvedSpecies, form, storedGender);
		if (genderAspect != null) {
			pokemon.setGender(switch (genderAspect) {
				case "male" -> Gender.MALE;
				case "female" -> Gender.FEMALE;
				default -> Gender.GENDERLESS;
			});
		}
		FormData formData = form == null ? null : PokemonGuiRendering.resolveForm(resolvedSpecies, form);
		if (formData != null) {
			// The form's aspects are what actually select its model (Arceus plates, Rotom appliances, Ogerpon masks...).
			// Forcing them replaces the computed aspects, so "shiny" has to be part of the forced set (same rule as the
			// battle visuals) — setShiny after forcing them used to drop it.
			Set<String> forced = new HashSet<>(formData.getAspects());
			if (shiny) {
				forced.add("shiny");
			}
			if (genderAspect != null) {
				forced.add(genderAspect);
			}
			pokemon.setForm(formData);
			pokemon.setForcedAspects(forced);
		}
		pokemon.setLevel(Math.max(1, level));
		pokemon.updateAspects();
		Component baseName = nickname == null || nickname.isBlank() ? resolvedSpecies.getTranslatedName() : Component.literal(nickname);
		pokemon.setNickname(Component.translatable("phantasmon.ghost.nameplate", baseName));

		Player owner = clientLevel.getPlayerByUUID(ownerUuid);
		float anchorYaw = owner != null ? owner.getYRot() : 0f;
		PokemonEntity entity = new PokemonEntity(clientLevel, pokemon, CobblemonEntities.POKEMON);
		entity.setNoAi(true);
		entity.setInvulnerable(true);
		// Never pushes nor gets pushed (Entity.push does nothing if either side has noPhysics) — a big Ghost
		// (Arceus, Rayquaza...) used to shove its owner around while following. Only flipped off around our own
		// Entity.move call in tickGhost, so it still collides with blocks. The hitbox is left exactly as it is.
		entity.noPhysics = true;
		double followScale = followScaleFor(entity);
		Vec3 spawnPos = new Vec3(x, y, z);
		if (owner != null) {
			spawnPos = followSpot(owner.position(), anchorYaw, followScale);
		}
		entity.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
		entity.setYRot(anchorYaw);
		entity.setYBodyRot(anchorYaw);
		entity.setYHeadRot(anchorYaw);
		syncAspects(entity, pokemon, shiny, genderAspect);
		// The label's level is synched entity data too (default 1, normally filled by the server delegate).
		entity.getEntityData().set(PokemonEntity.Companion.getLABEL_LEVEL(), pokemon.getLevel());
		entity.getEntityData().set(PokemonEntity.getSPAWN_DIRECTION(), anchorYaw);

		boolean flyer = canFly(entity);
		Ghost ghost = new Ghost(entity, flyer, shiny, new Vec3(x, y, z), anchorYaw);
		ghost.followScale = followScale;
		if (flyer) {
			entity.setPos(spawnPos.x, spawnPos.y + FLYER_HOVER_HEIGHT, spawnPos.z);
		}

		if (owner != null) {
			owner.swing(InteractionHand.MAIN_HAND);
			entity.setPhasingTargetId(owner.getId());
			playSound(clientLevel, owner.position(), CobblemonSounds.POKE_BALL_THROW);
		}
		entity.setBeamMode(BEAM_SEND_OUT);
		ghost.busyUntilMillis = System.currentTimeMillis() + (long) (SEND_OUT_DURATION * 1000);
		clientLevel.addEntity(entity);
		PhantasmonEntities.register(entity);
		activeGhosts.put(ownerUuid, ghost);

		later(THROW_DURATION, () -> entity.setPhasingTargetId(-1));
		later(SEND_OUT_DURATION, () -> {
			if (entity.getBeamMode() == BEAM_RECALL || entity.isRemoved()) {
				return;
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

	/** The renderer reads the synched ASPECTS; a client-only entity never gets them from a server delegate. */
	private static void syncAspects(PokemonEntity entity, Pokemon pokemon, boolean shiny, String genderAspect) {
		Set<String> aspects = new HashSet<>(pokemon.getAspects());
		if (shiny) {
			aspects.add("shiny");
		}
		if (genderAspect != null) {
			aspects.add(genderAspect);
		}
		entity.getEntityData().set(PokemonEntity.Companion.getASPECTS(), aspects);
	}

	/** Backend-relayed owner position (about once a second) — only a fallback target, see {@link #tick()}. */
	public void move(UUID ownerUuid, double x, double y, double z) {
		Ghost ghost = activeGhosts.get(ownerUuid);
		if (ghost != null) {
			ghost.lastRelayedOwnerPos = new Vec3(x, y, z);
		}
	}

	/** Called every client tick: follows or roams, for every active Ghost. */
	public void tick() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || activeGhosts.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Ghost> entry : activeGhosts.entrySet()) {
			try {
				tickGhost(level, entry.getKey(), entry.getValue());
			} catch (RuntimeException ex) {
				LOG.warn("Ghost follow tick failed for owner {}", entry.getKey(), ex);
			}
		}
	}

	/**
	 * Distance the Ghost's spot must keep from its owner so the two bodies don't overlap: the Ghost's half width
	 * + the player's half width (0.3) + a margin. Small and medium Pokémon keep the default spot (scale 1).
	 */
	private static double followScaleFor(PokemonEntity entity) {
		double required = entity.getBbWidth() / 2.0 + 0.3 + 0.5;
		double base = Math.sqrt(FOLLOW_BEHIND * FOLLOW_BEHIND + FOLLOW_SIDE * FOLLOW_SIDE);
		return Math.max(1.0, required / base);
	}

	private static Vec3 followSpot(Vec3 ownerPos, float anchorYaw, double scale) {
		double yawRad = Math.toRadians(anchorYaw);
		double forwardX = -Math.sin(yawRad);
		double forwardZ = Math.cos(yawRad);
		double rightX = -Math.cos(yawRad);
		double rightZ = -Math.sin(yawRad);
		return new Vec3(
				ownerPos.x + (-forwardX * FOLLOW_BEHIND + rightX * FOLLOW_SIDE) * scale,
				ownerPos.y,
				ownerPos.z + (-forwardZ * FOLLOW_BEHIND + rightZ * FOLLOW_SIDE) * scale);
	}

	private void tickGhost(ClientLevel level, UUID ownerUuid, Ghost ghost) {
		PokemonEntity entity = ghost.entity;
		if (System.currentTimeMillis() < ghost.busyUntilMillis) {
			return; // send-out beam still playing
		}
		ghost.age++;
		Player owner = level.getPlayerByUUID(ownerUuid);
		Vec3 ownerPos = owner != null ? owner.position() : ghost.lastRelayedOwnerPos;

		if (owner != null) {
			trackOwner(ghost, ownerPos);
		} else {
			ghost.lastOwnerPos = null;
			ghost.ownerIdleTicks = 0;
		}
		boolean shouldRoam = owner != null && ghost.ownerIdleTicks >= ROAM_IDLE_TICKS;
		if (shouldRoam && !ghost.roaming) {
			ghost.roaming = true;
			ghost.roamTarget = null;
			ghost.nextRoamAge = ghost.age + randomBetween(ROAM_FIRST_PAUSE_MIN, ROAM_FIRST_PAUSE_MAX);
		} else if (!shouldRoam && ghost.roaming) {
			ghost.roaming = false;
			ghost.roamTarget = null;
		}

		double hover = ghost.flyer ? FLYER_HOVER_HEIGHT : 0;
		Vec3 spot = followSpot(ownerPos, ghost.anchorYaw, ghost.followScale).add(0, hover, 0);
		Vec3 position = entity.position();

		double toOwnerX = ownerPos.x - position.x;
		double toOwnerZ = ownerPos.z - position.z;
		if (Math.sqrt(toOwnerX * toOwnerX + toOwnerZ * toOwnerZ) > TELEPORT_DISTANCE || ghost.stuckTicks > STUCK_TICKS_BEFORE_SNAP) {
			if (ghost.roaming) {
				ghost.roamTarget = null;
				ghost.stuckTicks = 0;
			} else {
				snapTo(ghost, spot);
				return;
			}
		}

		Vec3 target = null;
		double maxStep = MAX_STEP;
		if (ghost.roaming) {
			maxStep = ROAM_MAX_STEP;
			if (ghost.roamTarget == null && ghost.age >= ghost.nextRoamAge) {
				ghost.roamTarget = randomPointAround(ownerPos).add(0, hover, 0);
			}
			if (ghost.roamTarget != null) {
				double rx = ghost.roamTarget.x - position.x;
				double rz = ghost.roamTarget.z - position.z;
				if (Math.sqrt(rx * rx + rz * rz) <= ROAM_ARRIVED_DISTANCE) {
					ghost.roamTarget = null;
					ghost.nextRoamAge = ghost.age + randomBetween(ROAM_PAUSE_MIN, ROAM_PAUSE_MAX);
				} else {
					target = ghost.roamTarget;
				}
			}
			ghost.moving = target != null;
		} else {
			target = spot;
			double hx = target.x - position.x;
			double hz = target.z - position.z;
			double horizontal = Math.sqrt(hx * hx + hz * hz);
			ghost.moving = ghost.moving ? horizontal > STOP_MOVING_DISTANCE : horizontal > START_MOVING_DISTANCE;
		}

		double stepX = 0;
		double stepZ = 0;
		double dy = 0;
		if (target != null) {
			double dx = target.x - position.x;
			double dz = target.z - position.z;
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			dy = target.y - position.y;
			if (ghost.moving && horizontal > 1.0E-4) {
				double step = Math.min(maxStep, MIN_STEP + horizontal * STEP_GAIN);
				step = Math.min(step, horizontal);
				stepX = dx / horizontal * step;
				stepZ = dz / horizontal * step;
			}
		}

		double stepY;
		if (ghost.flyer) {
			ghost.verticalVelocity = 0;
			stepY = target == null ? 0 : Mth.clamp(dy * 0.15, -0.3, 0.3);
		} else {
			if (entity.onGround()) {
				ghost.verticalVelocity = entity.horizontalCollision && ghost.moving ? JUMP_VELOCITY : 0;
			} else {
				ghost.verticalVelocity = (ghost.verticalVelocity - GRAVITY) * 0.98;
			}
			stepY = ghost.verticalVelocity;
		}

		entity.noPhysics = false;
		try {
			entity.move(MoverType.SELF, new Vec3(stepX, stepY, stepZ));
		} finally {
			entity.noPhysics = true;
		}

		boolean blocked = ghost.moving && entity.horizontalCollision && !entity.onGround();
		boolean noProgress = ghost.moving && Math.abs(stepX) + Math.abs(stepZ) < 1.0E-3;
		double distanceToTarget = target == null ? 0 : target.distanceTo(entity.position());
		ghost.stuckTicks = distanceToTarget > STUCK_MIN_DISTANCE && (blocked || noProgress) ? ghost.stuckTicks + 1 : 0;

		float desiredYaw;
		if (ghost.moving && (stepX != 0 || stepZ != 0)) {
			desiredYaw = (float) (Mth.atan2(-stepX, stepZ) * (180.0 / Math.PI));
		} else if (owner != null) {
			Vec3 now = entity.position();
			desiredYaw = (float) (Mth.atan2(-(owner.getX() - now.x), owner.getZ() - now.z) * (180.0 / Math.PI));
		} else {
			desiredYaw = entity.getYRot();
		}
		float yaw = approachAngle(entity.getYRot(), desiredYaw, MAX_TURN_PER_TICK);
		entity.setYRot(yaw);
		entity.setYBodyRot(yaw);
		entity.setYHeadRot(yaw);

		applyPose(ghost);
	}

	/** Idle counter and travel heading of the owner — position only, so turning the camera counts as standing still. */
	private static void trackOwner(Ghost ghost, Vec3 ownerPos) {
		Vec3 previous = ghost.lastOwnerPos;
		ghost.lastOwnerPos = ownerPos;
		if (previous == null) {
			ghost.ownerIdleTicks = 0;
			return;
		}
		double dx = ownerPos.x - previous.x;
		double dz = ownerPos.z - previous.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		double vertical = Math.abs(ownerPos.y - previous.y);
		if (horizontal > OWNER_MOVE_EPSILON || vertical > OWNER_VERTICAL_EPSILON) {
			ghost.ownerIdleTicks = 0;
			if (horizontal > OWNER_MOVE_EPSILON) {
				float heading = (float) (Mth.atan2(-dx, dz) * (180.0 / Math.PI));
				ghost.anchorYaw = approachAngle(ghost.anchorYaw, heading, OWNER_HEADING_TURN_PER_TICK);
			}
		} else {
			ghost.ownerIdleTicks++;
		}
	}

	private static Vec3 randomPointAround(Vec3 center) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		double radius = ROAM_RADIUS * Math.sqrt(random.nextDouble());
		double angle = random.nextDouble() * Math.PI * 2;
		return new Vec3(center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius);
	}

	private static int randomBetween(int min, int max) {
		return ThreadLocalRandom.current().nextInt(min, max + 1);
	}

	private static void snapTo(Ghost ghost, Vec3 spot) {
		ghost.entity.setPos(spot.x, spot.y, spot.z);
		ghost.entity.setDeltaMovement(Vec3.ZERO);
		ghost.verticalVelocity = 0;
		ghost.stuckTicks = 0;
		ghost.moving = false;
		applyPose(ghost);
	}

	private static float approachAngle(float current, float desired, float maxStep) {
		float delta = Mth.wrapDegrees(desired - current);
		return current + Mth.clamp(delta, -maxStep, maxStep);
	}

	/** Cobblemon picks the walk/fly animation from these synced flags; its server delegate normally maintains them, but a client-only entity has none. */
	private static void applyPose(Ghost ghost) {
		PokemonEntity entity = ghost.entity;
		PoseType pose;
		if (ghost.flyer) {
			pose = ghost.moving ? PoseType.FLY : PoseType.HOVER;
		} else {
			pose = ghost.moving ? PoseType.WALK : PoseType.STAND;
		}
		entity.getEntityData().set(PokemonEntity.Companion.getMOVING(), ghost.moving);
		entity.getEntityData().set(PokemonEntity.Companion.getPOSE_TYPE(), pose);
	}

	private static boolean canFly(PokemonEntity entity) {
		try {
			return entity.getBehaviour().getMoving().getFly().getCanFly();
		} catch (RuntimeException ex) {
			return false;
		}
	}

	/** Recall: Cobblemon's recall beam back to the owner when it is around, otherwise an instant removal. */
	public void despawn(UUID ownerUuid) {
		Ghost ghost = activeGhosts.remove(ownerUuid);
		if (ghost == null) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		PokemonEntity entity = ghost.entity;
		Player owner = level == null ? null : level.getPlayerByUUID(ownerUuid);
		if (level == null || owner == null || entity.isRemoved()) {
			remove(entity);
			return;
		}
		playSound(level, entity.position(), CobblemonSounds.POKE_BALL_RECALL);
		entity.setPhasingTargetId(owner.getId());
		entity.setBeamMode(BEAM_RECALL);
		entity.noPhysics = true;
		entity.setNoGravity(true);
		leaving.add(entity);
		later(SEND_OUT_DURATION, () -> {
			if (leaving.remove(entity)) {
				remove(entity);
			}
		});
	}

	/** Spawn replacing an existing Ghost: no animation for the old one. */
	private void removeNow(UUID ownerUuid) {
		Ghost ghost = activeGhosts.remove(ownerUuid);
		if (ghost != null) {
			remove(ghost.entity);
		}
	}

	private static void remove(PokemonEntity entity) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
		}
	}

	/** Called on disconnect/dimension change — everything goes at once, no animation; nothing to notify server-side, the caller handles that. */
	public void despawnAll() {
		generation++;
		activeGhosts.values().forEach(ghost -> remove(ghost.entity));
		activeGhosts.clear();
		leaving.forEach(GhostEntityManager::remove);
		leaving.clear();
	}

	private void later(float seconds, Runnable task) {
		int scheduledFor = generation;
		TIMER.schedule(() -> Minecraft.getInstance().execute(() -> {
			if (scheduledFor == generation) {
				try {
					task.run();
				} catch (RuntimeException ex) {
					LOG.warn("Ghost visual step failed", ex);
				}
			}
		}), (long) (seconds * 1000), TimeUnit.MILLISECONDS);
	}

	private static void playSound(ClientLevel level, Vec3 position, SoundEvent sound) {
		level.playLocalSound(position.x, position.y, position.z, sound, SoundSource.NEUTRAL, 0.6F, 1F, false);
	}
}
