package com.mystaria.phantasmon.client.battle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bedrockk.molang.Expression;
import com.bedrockk.molang.runtime.MoLangRuntime;
import com.bedrockk.molang.runtime.MoParams;
import com.bedrockk.molang.runtime.struct.QueryStruct;
import com.bedrockk.molang.runtime.value.DoubleValue;
import com.bedrockk.molang.runtime.value.StringValue;
import com.cobblemon.mod.common.api.molang.ExpressionLike;
import com.cobblemon.mod.common.api.molang.MoLangFunctions;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext;
import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline;
import com.cobblemon.mod.common.api.moves.animations.ActionEffects;
import com.cobblemon.mod.common.api.moves.animations.TargetsProvider;
import com.cobblemon.mod.common.api.moves.animations.UsersProvider;
import com.cobblemon.mod.common.api.moves.animations.keyframes.ActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.AddHoldsActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.AnimationActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.ConditionalActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.EntityConditionalActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.EntityMoLangActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.EntityParticlesActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.EntitySoundActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.ForkActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.MoLangActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.MoveToTargetActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.ParallelActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.PauseActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.RemoveHoldsActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.ReturnToPositionActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.RunActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.SavePositionActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.SequenceActionEffectKeyframe;
import com.cobblemon.mod.common.entity.PosableEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;
import com.cobblemon.mod.common.net.messages.client.effect.RunPosableMoLangPacket;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket;
import com.cobblemon.mod.common.util.MoLangExtensionsKt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side reader of Cobblemon's action effect timelines (Phase 9 — "same animations as Cobblemon").
 *
 * <p>Same timelines (Cobblemon's JSON files, parsed by Cobblemon), same keyframe semantics as
 * {@code ActionEffectTimeline}/{@code *ActionEffectKeyframe}, same MoLang queries — but where Cobblemon's
 * server sends {@link PlayPosableAnimationPacket}, {@link SpawnSnowstormEntityParticlePacket},
 * {@link RunPosableMoLangPacket} or a sound to the players around its entities, this plays them straight into
 * this client against the client-side battle entities of {@link BattleVisuals}. Runs on the client thread;
 * delays are real time, like Cobblemon's server task tracker.
 */
public final class ActionEffectPlayer {

	private static final Logger LOG = LoggerFactory.getLogger(ActionEffectPlayer.class);

	/** Host only: mirrors the playback onto the engine (holds pace the battle, the end releases it). */
	public interface Listener {
		void holdsChanged(Set<String> holds);

		void finished();
	}

	private static final Listener NONE = new Listener() {
		@Override
		public void holdsChanged(Set<String> holds) {
		}

		@Override
		public void finished() {
		}
	};

	private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "phantasmon-action-effects");
		thread.setDaemon(true);
		return thread;
	});

	/** Where a playback finds its entities: the local battle's scene, or a battle seen from afar. */
	public interface Scene {
		PokemonEntity entityAt(String pnx);

		/** Changes when the scene is cleared: a playback started before stops. */
		int generation();
	}

	/** This client's own battle (player or spectator), the one the camera follows. */
	private static final Scene LOCAL = new Scene() {
		@Override
		public PokemonEntity entityAt(String pnx) {
			return BattleVisuals.entityAt(pnx);
		}

		@Override
		public int generation() {
			return BattleVisuals.generation();
		}
	};

	private ActionEffectPlayer() {
	}

	/** On the client thread. {@code listener} may be null (guest). */
	public static void play(ActionEffectEvent event, Listener listener) {
		play(event, listener, LOCAL);
	}

	/** On the client thread, against {@code scene}'s entities; only the local scene moves the camera. */
	public static void play(ActionEffectEvent event, Listener listener, Scene scene) {
		Listener safeListener = listener == null ? NONE : listener;
		if (scene == LOCAL) {
			BattleCameraDirector.onAction(event.users(), event.targets());
		}
		try {
			ClientActionEffects.ensureLoaded();
			ResourceLocation id = event.effectId() == null ? null : ResourceLocation.tryParse(event.effectId());
			ActionEffectTimeline timeline = id == null ? null : ActionEffects.INSTANCE.getActionEffects().get(id);
			ClientLevel level = Minecraft.getInstance().level;
			if (timeline == null || level == null) {
				safeListener.finished();
				return;
			}
			new Playback(event, timeline, level, safeListener, scene).start();
		} catch (Exception ex) {
			LOG.warn("Cannot play action effect {}", event.effectId(), ex);
			safeListener.finished();
		}
	}

	private static CompletableFuture<Void> done() {
		return CompletableFuture.completedFuture(null);
	}

	/** Completes on the client thread after {@code seconds}. */
	private static CompletableFuture<Void> delay(float seconds) {
		if (!(seconds > 0)) {
			return done();
		}
		CompletableFuture<Void> future = new CompletableFuture<>();
		TIMER.schedule(() -> Minecraft.getInstance().execute(() -> future.complete(null)), (long) (seconds * 1000), TimeUnit.MILLISECONDS);
		return future;
	}

	private static final class Playback {

		private final ActionEffectEvent event;
		private final ActionEffectTimeline timeline;
		private final ClientLevel level;
		private final Listener listener;
		private final Scene scene;
		private final int generation;
		private final Set<String> holds = new LinkedHashSet<>();
		private final List<Entity> users = new ArrayList<>();
		private final List<Entity> targets = new ArrayList<>();
		private final Map<Entity, Vec3> savedPositions = new HashMap<>();
		private final MoLangRuntime runtime = MoLangFunctions.INSTANCE.setup(new MoLangRuntime());
		private ActionEffectContext context;
		private boolean finished;

		Playback(ActionEffectEvent event, ActionEffectTimeline timeline, ClientLevel level, Listener listener, Scene scene) {
			this.scene = scene;
			this.generation = scene.generation();
			this.event = event;
			this.timeline = timeline;
			this.level = level;
			this.listener = listener;
		}

		void start() {
			resolve(event.users(), users);
			resolve(event.targets(), targets);
			users.forEach(user -> savedPositions.put(user, user.position()));
			setUpQueries();
			context = new ActionEffectContext(timeline, holds,
					new ArrayList<>(List.of(new UsersProvider(users), new TargetsProvider(targets))),
					runtime, false, false, new ArrayList<>(), level);
			if (!bool(timeline.getCondition())) {
				finish();
				return;
			}
			chain(timeline.getTimeline()).whenComplete((ignored, error) -> {
				if (error != null) {
					LOG.debug("Action effect {} stopped early", event.effectId(), error);
				}
				finish();
			});
		}

		private void finish() {
			if (finished) {
				return;
			}
			finished = true;
			holds.clear();
			listener.finished();
		}

		private void resolve(List<String> pnxs, List<Entity> into) {
			if (pnxs == null) {
				return;
			}
			for (String pnx : pnxs) {
				PokemonEntity entity = scene.entityAt(pnx);
				if (entity != null && !into.contains(entity)) {
					into.add(entity);
				}
			}
		}

		private void setUpQueries() {
			QueryStruct query = runtime.getEnvironment().query;
			if (event.moveName() != null) {
				MoveTemplate move = Moves.getByName(event.moveName());
				if (move != null) {
					query.addFunction("move", params -> move.getStruct());
				}
			}
			query.addFunction("missed", params -> matches(params, event.missed(), event.missed() != null && !event.missed().isEmpty()));
			query.addFunction("failed", params -> matches(params, event.failed(), event.anyFailed()));
			query.addFunction("hurt", params -> matches(params, event.hurt(), event.hurt() != null && !event.hurt().isEmpty()));
			if (event.hitCount() != null) {
				query.addFunction("hit_count", params -> new DoubleValue(event.hitCount().doubleValue()));
			}
			if (event.instructionId() != null) {
				query.addFunction("instruction_id", params -> new StringValue(event.instructionId()));
			}
			if (!users.isEmpty()) {
				Entity user = users.getFirst();
				query.addFunction("user", params -> MoLangFunctions.INSTANCE.asMostSpecificMoLangValue(user));
			}
			if (targets.size() == 1) {
				Entity target = targets.getFirst();
				query.addFunction("target", params -> MoLangFunctions.INSTANCE.asMostSpecificMoLangValue(target));
			}
		}

		/** {@code q.missed}: any target; {@code q.missed(uuid)}: that entity (here, this client's entity uuids). */
		private DoubleValue matches(MoParams params, List<String> pnxs, boolean any) {
			if (params.getParams().isEmpty()) {
				return new DoubleValue(any);
			}
			String uuid = params.getString(0);
			if (pnxs != null) {
				for (String pnx : pnxs) {
					PokemonEntity entity = scene.entityAt(pnx);
					if (entity != null && entity.getStringUUID().equals(uuid)) {
						return new DoubleValue(true);
					}
				}
			}
			return new DoubleValue(false);
		}

		private boolean stale() {
			return generation != scene.generation() || Minecraft.getInstance().level != level;
		}

		private CompletableFuture<Void> chain(List<? extends ActionEffectKeyframe> keyframes) {
			CompletableFuture<Void> chain = done();
			for (ActionEffectKeyframe keyframe : List.copyOf(keyframes)) {
				chain = chain.thenCompose(ignored -> playSafely(keyframe));
			}
			return chain;
		}

		private CompletableFuture<Void> playSafely(ActionEffectKeyframe keyframe) {
			if (stale()) {
				return done();
			}
			try {
				return play(keyframe);
			} catch (Exception ex) {
				// Cobblemon's own keyframes skip on bad data too: one broken step never stops the rest.
				LOG.debug("Action effect {}: keyframe {} failed", event.effectId(), keyframe.getClass().getSimpleName(), ex);
				return done();
			}
		}

		private CompletableFuture<Void> play(ActionEffectKeyframe keyframe) {
			if (keyframe instanceof ConditionalActionEffectKeyframe conditional && !bool(conditional.getCondition())) {
				return done();
			}
			return switch (keyframe) {
				case AddHoldsActionEffectKeyframe add -> {
					holds.addAll(add.getHolds());
					listener.holdsChanged(holds);
					yield done();
				}
				case RemoveHoldsActionEffectKeyframe remove -> delay(remove.getDelay()).thenRun(() -> {
					if (remove.getHolds().isEmpty()) {
						holds.clear();
					} else {
						holds.removeAll(remove.getHolds());
					}
					listener.holdsChanged(holds);
				});
				case PauseActionEffectKeyframe pause -> delay(number(pause.getPause()));
				case SequenceActionEffectKeyframe sequence -> chain(sequence.getKeyframes());
				case ParallelActionEffectKeyframe parallel -> CompletableFuture.allOf(
						parallel.getKeyframes().stream().map(this::playSafely).toArray(CompletableFuture[]::new));
				case ForkActionEffectKeyframe fork -> chain(bool(fork.getCondition()) ? fork.getIfTrue() : fork.getIfFalse());
				case MoLangActionEffectKeyframe molang -> {
					molang.getExpressions().resolve(runtime, Map.of());
					yield delay(number(molang.getDelay()));
				}
				case AnimationActionEffectKeyframe animation -> playAnimation(animation);
				case EntityParticlesActionEffectKeyframe particles -> playParticles(particles);
				case EntitySoundActionEffectKeyframe sound -> playSound(sound);
				case EntityMoLangActionEffectKeyframe molang -> {
					for (Entity entity : passing(molang)) {
						CobblemonPackets.dispatchLocally(new RunPosableMoLangPacket(entity.getId(), new HashSet<>(molang.getExpressions())));
					}
					yield delay(number(molang.getDelay()));
				}
				case RunActionEffectKeyframe run -> {
					ActionEffectTimeline nested = run.getActionEffect() == null ? null
							: ActionEffects.INSTANCE.getActionEffects().get(run.getActionEffect());
					if (nested == null || !bool(nested.getCondition())) {
						yield done();
					}
					CompletableFuture<Void> played = chain(nested.getTimeline());
					yield run.getWaitForActionEffect() ? played : done();
				}
				case SavePositionActionEffectKeyframe save -> {
					if (!users.isEmpty()) {
						savedPositions.put(users.getFirst(), users.getFirst().position());
					}
					yield done();
				}
				case MoveToTargetActionEffectKeyframe move -> moveToTarget(move);
				case ReturnToPositionActionEffectKeyframe back -> returnToPosition(back);
				default -> done(); // can_interrupt / cannot_interrupt: no interruption in Ghost battles
			};
		}

		private CompletableFuture<Void> playAnimation(AnimationActionEffectKeyframe keyframe) {
			Set<String> animation = new LinkedHashSet<>();
			for (String raw : keyframe.getAnimation()) {
				animation.add(stringOrRaw(raw));
			}
			List<String> expressions = keyframe.getVariables().stream().map(Expression::getOriginalString).toList();
			for (Entity entity : passing(keyframe)) {
				CobblemonPackets.dispatchLocally(new PlayPosableAnimationPacket(entity.getId(), animation, expressions));
			}
			return delay(number(keyframe.getDelay()));
		}

		private CompletableFuture<Void> playParticles(EntityParticlesActionEffectKeyframe keyframe) {
			if (keyframe.getEffect() == null) {
				return done();
			}
			ResourceLocation effect = identifier(stringOrRaw(keyframe.getEffect()));
			if (effect == null) {
				return done();
			}
			// Cobblemon tests every involved entity (users and targets), flagged by whether it is a user.
			List<Entity> sources = new ArrayList<>();
			for (Entity entity : involved()) {
				if (entity instanceof PosableEntity && test(keyframe, entity, users.contains(entity))) {
					sources.add(entity);
				}
			}
			for (Entity source : sources) {
				if (keyframe.getTargetLocators() == null) {
					CobblemonPackets.dispatchLocally(new SpawnSnowstormEntityParticlePacket(effect, source.getId(),
							keyframe.getLocators(), null, List.of()));
				} else {
					for (Entity target : targets) {
						CobblemonPackets.dispatchLocally(new SpawnSnowstormEntityParticlePacket(effect, source.getId(),
								keyframe.getLocators(), target.getId(), new ArrayList<>(keyframe.getTargetLocators())));
					}
				}
			}
			return delay(number(keyframe.getDelay()));
		}

		private CompletableFuture<Void> playSound(EntitySoundActionEffectKeyframe keyframe) {
			if (keyframe.getSound() == null) {
				return done();
			}
			ResourceLocation id = identifier(stringOrRaw(keyframe.getSound()));
			SoundEvent sound = id == null ? null : BuiltInRegistries.SOUND_EVENT.get(id);
			if (sound == null) {
				return done();
			}
			for (Entity entity : passing(keyframe)) {
				level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), sound, entity.getSoundSource(), 1F, 1F, false);
			}
			return delay(number(keyframe.getDelay()));
		}

		private CompletableFuture<Void> moveToTarget(MoveToTargetActionEffectKeyframe keyframe) {
			if (users.isEmpty() || targets.isEmpty()) {
				return done();
			}
			Entity user = users.getFirst();
			Entity target = targets.getFirst();
			float proximity = keyframe.getProximity() != -1F ? keyframe.getProximity()
					: (float) (Math.sqrt(2 * Math.pow(user.getBoundingBox().getXsize(), 2)) + 1.5
							+ Math.sqrt(2 * Math.pow(target.getBoundingBox().getXsize(), 2)));
			double distance = target.distanceTo(user);
			if (distance < proximity || distance > 20) {
				return done();
			}
			Vec3 away = user.position().subtract(target.position()).normalize().scale(proximity);
			return glide(user, target.position().add(away), keyframe.getSpeed(), number(keyframe.getTimeout()));
		}

		private CompletableFuture<Void> returnToPosition(ReturnToPositionActionEffectKeyframe keyframe) {
			if (users.isEmpty()) {
				return done();
			}
			Entity user = users.getFirst();
			Vec3 home = savedPositions.get(user);
			if (home == null || home.distanceTo(user.position()) > 20) {
				return done();
			}
			return glide(user, home, keyframe.getSpeed(), number(keyframe.getTimeout()));
		}

		/** Client entities have no AI to path-find: a straight glide at a walking pace, capped by the timeout. */
		private CompletableFuture<Void> glide(Entity entity, Vec3 destination, float speed, float timeoutSeconds) {
			Vec3 from = entity.position();
			double distance = from.distanceTo(destination);
			if (distance < 0.05) {
				return done();
			}
			int maxTicks = Math.max(1, (int) (timeoutSeconds * 20));
			int ticks = Math.min(maxTicks, Math.max(1, (int) Math.ceil(distance / (0.3 * Math.max(0.1F, speed)))));
			CompletableFuture<Void> future = new CompletableFuture<>();
			step(entity, from, destination, 1, ticks, future);
			return future;
		}

		private void step(Entity entity, Vec3 from, Vec3 to, int tick, int ticks, CompletableFuture<Void> future) {
			TIMER.schedule(() -> Minecraft.getInstance().execute(() -> {
				if (stale() || entity.isRemoved()) {
					future.complete(null);
					return;
				}
				Vec3 at = from.lerp(to, tick / (double) ticks);
				entity.setPos(at.x, at.y, at.z);
				if (tick >= ticks) {
					future.complete(null);
				} else {
					step(entity, from, to, tick + 1, ticks, future);
				}
			}), 50, TimeUnit.MILLISECONDS);
		}

		private List<Entity> involved() {
			List<Entity> all = new ArrayList<>(users);
			all.addAll(targets);
			all.removeIf(Entity::isRemoved);
			return all;
		}

		/** Each provider's entities that pass the keyframe's {@code entityCondition} (users flagged as such). */
		private List<Entity> passing(EntityConditionalActionEffectKeyframe keyframe) {
			List<Entity> result = new ArrayList<>();
			for (Entity user : users) {
				if (!user.isRemoved() && test(keyframe, user, true)) {
					result.add(user);
				}
			}
			for (Entity target : targets) {
				if (!target.isRemoved() && test(keyframe, target, false)) {
					result.add(target);
				}
			}
			return result;
		}

		private boolean test(EntityConditionalActionEffectKeyframe keyframe, Entity entity, boolean isUser) {
			try {
				return keyframe.test(context, entity, isUser);
			} catch (Exception ex) {
				return false;
			}
		}

		private boolean bool(ExpressionLike expression) {
			try {
				return MoLangExtensionsKt.resolveBoolean(runtime, expression, Map.of());
			} catch (Exception ex) {
				return false;
			}
		}

		private float number(ExpressionLike expression) {
			try {
				return MoLangExtensionsKt.resolveFloat(runtime, expression, Map.of());
			} catch (Exception ex) {
				return 0F;
			}
		}

		/** Like Cobblemon: try the text as a MoLang expression ({@code 'cobblemon:impact_' + q.move.type}), else keep it. */
		private String stringOrRaw(String raw) {
			try {
				String resolved = MoLangExtensionsKt.resolveString(runtime, MoLangExtensionsKt.asExpressionLike(raw), Map.of());
				return resolved == null || resolved.equals("0") ? raw : resolved;
			} catch (Exception ex) {
				return raw;
			}
		}

		private static ResourceLocation identifier(String value) {
			if (value == null || value.isBlank()) {
				return null;
			}
			return ResourceLocation.tryParse(value.contains(":") ? value : "cobblemon:" + value);
		}
	}
}
