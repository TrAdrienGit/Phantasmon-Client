package com.mystaria.phantasmon.client.battle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext;
import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline;
import com.cobblemon.mod.common.api.moves.animations.keyframes.ActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.AddHoldsActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.ForkActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.ParallelActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.SequenceActionEffectKeyframe;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.dispatch.ActionEffectInstruction;
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.ActivateInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.BoostInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.CantInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.DamageInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.FailInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.HitCountInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.MissInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.PrepareInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.StartInstruction;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;

import kotlin.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Host side of Cobblemon's move animations in Ghost battles (Phase 9).
 *
 * <p>Cobblemon plays an action effect on the server, against the battle Pokémon's server entities, and sends
 * packets to the players around. A Ghost battle has no server entities: each client draws its own
 * ({@link BattleVisuals}). So for a battle hosted here, {@code ActionEffectTimelineMixin} stops
 * {@code ActionEffectTimeline.run} and hands the effect to {@link #intercept}; the instruction that started it
 * then stores the returned future ({@code ActionEffectInstructionsMixin} → {@link #bind}), which tells us who
 * uses it on whom. The effect is then played by {@link ActionEffectPlayer} on this client and relayed to the
 * guest, who plays it too.
 *
 * <p>Battle pacing stays Cobblemon's: the engine waits on the effect's {@code effects} hold (damage numbers
 * and HP bars only move once the hit lands) and on the future (the next move waits for the previous
 * animation). Both now follow this client's playback, mirrored onto the engine's context.
 */
public final class GhostActionEffects {

	private static final Logger LOG = LoggerFactory.getLogger(GhostActionEffects.class);
	private static final String EFFECTS_HOLD = "effects";
	/** If an effect was not claimed by an instruction (unknown caller), it plays without participants. */
	private static final long UNBOUND_GRACE_MILLIS = 100;
	/** Never let a broken playback freeze the battle. */
	private static final long SAFETY_TIMEOUT_MILLIS = 20_000;

	private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "phantasmon-action-effect-timer");
		thread.setDaemon(true);
		return thread;
	});

	private static final Map<CompletableFuture<?>, Pending> PENDING = Collections.synchronizedMap(new IdentityHashMap<>());

	private record Pending(PokemonBattle battle, ActionEffectTimeline timeline, ActionEffectContext context,
			CompletableFuture<Unit> future, Consumer<ActionEffectEvent> guestRoute) {
	}

	private GhostActionEffects() {
	}

	/** From the mixin, on the battle thread. Null = not a Ghost battle hosted here, let Cobblemon run it. */
	public static CompletableFuture<Unit> intercept(ActionEffectTimeline timeline, ActionEffectContext context) {
		PokemonBattle battle = null;
		for (Object provider : context.getProviders()) {
			if (provider instanceof PokemonBattle candidate) {
				battle = candidate;
			}
		}
		Consumer<ActionEffectEvent> guestRoute = battle == null ? null : GhostBattles.effectRoute(battle.getBattleId());
		if (guestRoute == null) {
			return null;
		}
		if (timeline.getTimeline().isEmpty()) {
			return CompletableFuture.completedFuture(Unit.INSTANCE);
		}
		CompletableFuture<Unit> future = new CompletableFuture<>();
		Pending pending = new Pending(battle, timeline, context, future, guestRoute);
		PENDING.put(future, pending);
		// Held from the very start, like Cobblemon's first add_holds keyframe would be: the engine checks the
		// hold on its next tick, before this client's playback has even begun.
		if (addsHold(timeline.getTimeline(), EFFECTS_HOLD)) {
			context.getHolds().add(EFFECTS_HOLD);
		}
		TIMER.schedule(() -> BattleThread.get().submit(() -> {
			if (PENDING.remove(future) != null) {
				LOG.warn("Action effect {} was not claimed by any instruction: played without user or target",
						ClientActionEffects.idOf(timeline));
				start(pending, describe(pending, null));
			}
		}), UNBOUND_GRACE_MILLIS, TimeUnit.MILLISECONDS);
		TIMER.schedule(() -> BattleThread.get().submit(() -> release(pending)), SAFETY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
		return future;
	}

	/** From the mixin: {@code instruction.future = actionEffect.run(context)}, right after {@link #intercept}. */
	public static void bind(Object instruction, CompletableFuture<?> future) {
		if (future == null) {
			return;
		}
		Pending pending = PENDING.remove(future);
		if (pending != null) {
			start(pending, describe(pending, instruction));
		}
	}

	private static void start(Pending pending, ActionEffectEvent event) {
		if (event.effectId() == null) {
			release(pending);
			return;
		}
		try {
			pending.guestRoute().accept(event);
		} catch (Exception ex) {
			LOG.warn("Cannot relay action effect {} to the guest", event.effectId(), ex);
		}
		ActionEffectContext context = pending.context();
		Minecraft.getInstance().execute(() -> ActionEffectPlayer.play(event, new ActionEffectPlayer.Listener() {
			@Override
			public void holdsChanged(Set<String> holds) {
				Set<String> copy = Set.copyOf(holds);
				BattleThread.get().submit(() -> {
					context.getHolds().clear();
					context.getHolds().addAll(copy);
				});
			}

			@Override
			public void finished() {
				BattleThread.get().submit(() -> release(pending));
			}
		}));
	}

	/** On the battle thread: lets the engine go on (idempotent). */
	private static void release(Pending pending) {
		pending.context().getHolds().clear();
		if (!pending.future().isDone()) {
			pending.future().complete(Unit.INSTANCE);
		}
	}

	private static ActionEffectEvent describe(Pending pending, Object instruction) {
		PokemonBattle battle = pending.battle();
		ResourceLocation id = ClientActionEffects.idOf(pending.timeline());
		List<String> users = new ArrayList<>();
		List<String> targets = new ArrayList<>();
		List<String> missed = new ArrayList<>();
		List<String> failed = new ArrayList<>();
		List<String> hurt = new ArrayList<>();
		boolean anyFailed = false;
		Integer hitCount = null;
		String moveName = null;
		String instructionId = null;

		if (instruction instanceof MoveInstruction move) {
			add(users, pnx(battle, move.getUserPokemon()));
			if (!move.getSpreadTargets().isEmpty()) {
				targets.addAll(move.getSpreadTargets());
			} else {
				add(targets, pnx(battle, move.getTargetPokemon()));
			}
			moveName = move.getMove().getName();
			instructionId = "cobblemon:move";
			for (InterpreterInstruction caused : move.getInstructionSet().findInstructionsCausedBy(move)) {
				if (caused instanceof MissInstruction miss) {
					add(missed, pnx(battle, miss.getTarget()));
				} else if (caused instanceof FailInstruction fail) {
					anyFailed = true;
					add(failed, pnx(battle, fail.getTarget()));
				} else if (caused instanceof DamageInstruction damage) {
					add(hurt, pnx(battle, damage.getExpectedTarget()));
				} else if (caused instanceof HitCountInstruction hits && hits.getHitCount() != null) {
					hitCount = hits.getHitCount();
				}
			}
		} else if (instruction instanceof DamageInstruction damage) {
			BattlePokemon subject = damage.getPublicMessage().battlePokemon(0, battle);
			add(users, pnx(battle, subject != null ? subject : damage.getExpectedTarget()));
		} else if (instruction instanceof BoostInstruction boost) {
			add(users, pnx(battle, boost.getPokemon()));
		} else {
			BattleMessage message = switch (instruction) {
				case ActivateInstruction activate -> activate.getMessage();
				case CantInstruction cant -> cant.getMessage();
				case PrepareInstruction prepare -> prepare.getMessage();
				case StartInstruction startInstruction -> startInstruction.getMessage();
				case null, default -> null;
			};
			if (message != null) {
				add(users, pnx(battle, message.battlePokemon(0, battle)));
			}
		}
		if (instructionId == null && instruction instanceof ActionEffectInstruction effectInstruction) {
			instructionId = effectInstruction.getId().toString();
		}
		return new ActionEffectEvent(id == null ? null : id.toString(), users, targets, missed, failed, anyFailed, hurt,
				hitCount, moveName, instructionId);
	}

	private static String pnx(PokemonBattle battle, BattlePokemon pokemon) {
		if (pokemon == null) {
			return null;
		}
		for (ActiveBattlePokemon active : battle.getActivePokemon()) {
			if (active.getBattlePokemon() == pokemon) {
				return active.getPNX();
			}
		}
		return null;
	}

	private static void add(List<String> list, String value) {
		if (value != null && !list.contains(value)) {
			list.add(value);
		}
	}

	private static boolean addsHold(List<? extends ActionEffectKeyframe> keyframes, String hold) {
		for (ActionEffectKeyframe keyframe : keyframes) {
			boolean found = switch (keyframe) {
				case AddHoldsActionEffectKeyframe add -> add.getHolds().contains(hold);
				case SequenceActionEffectKeyframe sequence -> addsHold(sequence.getKeyframes(), hold);
				case ParallelActionEffectKeyframe parallel -> addsHold(parallel.getKeyframes(), hold);
				case ForkActionEffectKeyframe fork -> addsHold(fork.getIfTrue(), hold) || addsHold(fork.getIfFalse(), hold);
				default -> false;
			};
			if (found) {
				return true;
			}
		}
		return false;
	}
}
