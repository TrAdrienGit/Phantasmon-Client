package com.mystaria.phantasmon.client.battle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.net.NetworkPacket;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMakeChoicePacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleQueueRequestPacket;
import com.cobblemon.mod.common.net.messages.server.battle.BattleSelectActionsPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.client.Minecraft;

import com.mystaria.phantasmon.client.api.PhantasmonBattleAI;
import com.mystaria.phantasmon.client.pokemon.PokemonDto;
import com.mystaria.showtime.battle.BattleShowtime;

/**
 * Registry of the Ghost battles this client hosts (Phase 9), and the bridge
 * between Cobblemon's native battle UI and the engine running locally.
 *
 * <p>The only thing Cobblemon's UI ever sends back is
 * {@link BattleSelectActionsPacket} (moves, switches, forfeit). For a battle
 * listed here, {@code ClientCommonPacketListenerImplMixin} stops it before it
 * leaves for the Minecraft server and hands it to {@link #onLocalChoice},
 * which does what Cobblemon's server handler would have done.
 */
public final class GhostBattles {

	private static final Logger LOG = LoggerFactory.getLogger(GhostBattles.class);

	/**
	 * Battle ids whose choice packets must never reach the Minecraft server, and where each one goes instead:
	 * the local engine when this client hosts the battle, the backend relay to the host when it's the guest.
	 */
	private static final Map<UUID, Consumer<BattleSelectActionsPacket>> ROUTES = new ConcurrentHashMap<>();

	/**
	 * Battles whose engine runs here, and where their action effects go besides this client's own playback:
	 * the backend relay to the guest. See {@link GhostActionEffects}.
	 */
	private static final Map<UUID, Consumer<ActionEffectEvent>> EFFECT_ROUTES = new ConcurrentHashMap<>();

	/** Battles hosted here, and where their Mega Evolutions / Primal Reversions go (local scene + relay). */
	private static final Map<UUID, Consumer<FormeChangeVisual>> FORME_ROUTES = new ConcurrentHashMap<>();
	/** Cobblemon battle id → where the engine's spectator updates go (hosted battles with spectators). */
	private static final Map<UUID, Consumer<NetworkPacket<?>>> SPECTATOR_ROUTES = new ConcurrentHashMap<>();

	static {
		// Cobblemon's engine only announces these; on a Cobblemon Delta server, Delta's server mod gives the Pokémon
		// its aspect. Here the host does it, for its own hosted Ghost battles only (Adrien 2026-10-05).
		Consumer<com.cobblemon.mod.common.api.events.battles.instruction.MegaEvolutionEvent> onMega =
				event -> onFormeChange(event.getBattle(), event.getPokemon(), FormeChangeVisual.megaAspect(heldItemPath(event.getPokemon())));
		Consumer<com.cobblemon.mod.common.api.events.battles.instruction.FormeChangeEvent> onForme = event -> {
			if ("primal".equals(event.getFormeName())) {
				onFormeChange(event.getBattle(), event.getPokemon(), "primal");
			}
		};
		com.cobblemon.mod.common.api.events.CobblemonEvents.MEGA_EVOLUTION.subscribe(com.cobblemon.mod.common.api.Priority.NORMAL, onMega);
		// Z-Move (TODO-28): no model change, just the Z-Power burst on both clients.
		Consumer<com.cobblemon.mod.common.api.events.battles.instruction.ZMoveUsedEvent> onZPower =
				event -> onFormeChange(event.getBattle(), event.getPokemon(), FormeChangeVisual.Z_POWER);
		com.cobblemon.mod.common.api.events.CobblemonEvents.ZPOWER_USED.subscribe(com.cobblemon.mod.common.api.Priority.NORMAL, onZPower);
		// Terastallization: no model change, the crystal set piece then a glow in the Tera type's colour.
		Consumer<com.cobblemon.mod.common.api.events.battles.instruction.TerastallizationEvent> onTera =
				event -> onFormeChange(event.getBattle(), event.getPokemon(), FormeChangeVisual.TERA_PREFIX + event.getTeraType().getId().getPath());
		com.cobblemon.mod.common.api.events.CobblemonEvents.TERASTALLIZATION.subscribe(com.cobblemon.mod.common.api.Priority.NORMAL, onTera);
		com.cobblemon.mod.common.api.events.CobblemonEvents.FORME_CHANGE.subscribe(com.cobblemon.mod.common.api.Priority.NORMAL, onForme);
	}

	/** Battle thread. Gives the battle Pokémon the aspect (kept through switches) and reports it to the controller. */
	private static void onFormeChange(PokemonBattle battle, BattlePokemon pokemon, String aspect) {
		Consumer<FormeChangeVisual> route = battle == null ? null : FORME_ROUTES.get(battle.getBattleId());
		if (route == null || pokemon == null) {
			return;
		}
		String pnx = pnxOf(pokemon);
		Pokemon effected = pokemon.getEffectedPokemon();
		if (FormeChangeVisual.changesModel(aspect)) {
			java.util.Set<String> forced = new java.util.HashSet<>(effected.getForcedAspects());
			forced.add(aspect);
			effected.setForcedAspects(forced);
			effected.updateAspects();
		}
		if (pnx != null) {
			route.accept(new FormeChangeVisual(pnx, pokemon.getUuid().toString(),
					effected.getSpecies().getResourceIdentifier().getPath(), aspect));
			// The battle waits for the set piece (both players: the guest gets the host's packets as they come).
			battle.dispatchWaitingToFront(BattleShowtime.pauseSeconds(aspect), () -> kotlin.Unit.INSTANCE);
		}
	}

	private static String heldItemPath(BattlePokemon pokemon) {
		var stack = pokemon == null ? null : pokemon.getEffectedPokemon().heldItem();
		return stack == null || stack.isEmpty() ? null
				: net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
	}

	/** Showdown position of an active battle Pokémon: its actor's id plus the slot letter. */
	private static String pnxOf(BattlePokemon pokemon) {
		BattleActor actor = pokemon.getActor();
		var active = actor.getActivePokemon();
		for (int i = 0; i < active.size(); i++) {
			if (active.get(i).getBattlePokemon() == pokemon) {
				return actor.getShowdownId() + (char) ('a' + i);
			}
		}
		return null;
	}

	private GhostBattles() {
	}

	/** Called from the network mixin, on the client thread. */
	public static boolean intercepts(BattleSelectActionsPacket packet) {
		return ROUTES.containsKey(packet.getBattleId());
	}

	public static void route(UUID battleId, Consumer<BattleSelectActionsPacket> onLocalChoice) {
		ROUTES.put(battleId, onLocalChoice);
	}

	public static void unroute(UUID battleId) {
		ROUTES.remove(battleId);
	}

	/** Null when this client does not host that battle (Cobblemon then plays its effects normally). */
	public static Consumer<ActionEffectEvent> effectRoute(UUID battleId) {
		return EFFECT_ROUTES.get(battleId);
	}

	/** A choice made in Cobblemon's UI by the local player, for a battle hosted here. */
	private static volatile Runnable localChoiceListener = () -> { };

	/** Notified (client thread) whenever the local player confirms a choice in a Ghost battle — turn timer bookkeeping. */
	public static void setLocalChoiceListener(Runnable listener) {
		localChoiceListener = listener;
	}

	public static void onLocalChoice(BattleSelectActionsPacket packet) {
		Consumer<BattleSelectActionsPacket> route = ROUTES.get(packet.getBattleId());
		if (route != null) {
			localChoiceListener.run();
			route.accept(packet);
		}
	}



	/** Mirrors Cobblemon's server-side {@code BattleSelectActionsHandler}, on the battle thread. */
	public static void applyChoice(BattleSelectActionsPacket packet, UUID playerUuid) {
		BattleThread.get().submit(() -> {
			PokemonBattle battle = BattleRegistry.getBattle(packet.getBattleId());
			if (battle == null || playerUuid == null) {
				return;
			}
			BattleActor actor = null;
			for (BattleActor candidate : battle.getActors()) {
				if (candidate.getUuid().equals(playerUuid)) {
					actor = candidate;
				}
			}
			if (actor == null || !actor.getMustChoose()) {
				return;
			}
			try {
				actor.setActionResponses(packet.getShowdownActionResponses());
			} catch (Exception ex) {
				LOG.warn("Invalid Ghost battle choice: {}", ex.getMessage());
				if (actor.getRequest() != null) {
					actor.sendUpdate(new BattleQueueRequestPacket(actor.getRequest()));
					actor.sendUpdate(new BattleMakeChoicePacket());
				}
			}
		});
	}

	/** What a hosted live battle reports back to its controller. */
	public interface HostCallbacks {
		/** The engine's battle exists; {@code cobblemonBattleId} is the id both UIs know it by. */
		void started(UUID cobblemonBattleId);

		/** The engine finished on its own; {@code winnerUuid} is null for a draw. Not called after {@link #stop}. */
		void finished(UUID winnerUuid);

		void failed();

		/** An action effect to relay to the guest (this client plays it itself). Called on the battle thread. */
		default void effect(ActionEffectEvent event) {
		}

		/** A Mega Evolution / Primal Reversion, for both clients' scene. Called on the battle thread. */
		default void formeChange(FormeChangeVisual change) {
		}

		/** A public update the engine sends its spectators (see {@code PokemonBattleSpectatorMixin}). Battle thread. */
		default void spectator(NetworkPacket<?> packet) {
		}
	}

	/** Called by {@code PokemonBattleSpectatorMixin} for every spectator update of every battle on this client. */
	public static void onSpectatorUpdate(PokemonBattle battle, NetworkPacket<?> packet) {
		Consumer<NetworkPacket<?>> route = SPECTATOR_ROUTES.get(battle.getBattleId());
		if (route != null) {
			route.accept(packet);
		}
	}

	/**
	 * What a spectator arriving now must receive first, like Cobblemon's {@code SpectateBattleHandler}: the battle as
	 * a spectator sees it ({@code BattleInitializePacket} with no ally side) and the chat log so far. Built on the
	 * battle thread, so nothing of the stream slips in between; handed to {@code answer} there.
	 */
	public static void spectatorCatchUp(UUID cobblemonBattleId, Consumer<List<NetworkPacket<?>>> answer) {
		catchUp(cobblemonBattleId, true, answer);
	}

	/** The same for a player who only sees the field from afar ({@code BattleFieldScenes}): no chat log. */
	public static void fieldCatchUp(UUID cobblemonBattleId, Consumer<List<NetworkPacket<?>>> answer) {
		catchUp(cobblemonBattleId, false, answer);
	}

	private static void catchUp(UUID cobblemonBattleId, boolean withChat, Consumer<List<NetworkPacket<?>>> answer) {
		BattleThread.get().submit(() -> {
			PokemonBattle battle = BattleRegistry.getBattle(cobblemonBattleId);
			if (battle == null || battle.getEnded()) {
				return;
			}
			var init = new com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket(battle, null);
			answer.accept(!withChat ? List.of(init) : List.of(init,
					new com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket(new java.util.ArrayList<>(battle.getChatLog()))));
		});
	}

	private static final java.util.Set<UUID> STOPPED = ConcurrentHashMap.newKeySet();

	/**
	 * Live battle hosted by this client (Phase 9): the local player's actor feeds this client's Cobblemon UI,
	 * the guest's actor feeds {@code guestSink} (encoded and relayed by the backend). Choices from the local UI
	 * go straight to the engine; the guest's arrive through {@link #applyChoice} with the guest's uuid.
	 */
	public static void startHostedBattle(UUID hostUuid, String hostName, List<PokemonDto> hostTeam,
			UUID guestUuid, String guestName, List<PokemonDto> guestTeam, List<String> battleRules, int adjustLevel,
			java.util.function.Consumer<NetworkPacket<?>> hostSink,
			java.util.function.Consumer<NetworkPacket<?>> guestSink, HostCallbacks callbacks) {
		startHostedBattle(hostUuid, hostName, hostTeam, guestUuid, guestName, guestTeam, battleRules, adjustLevel, hostSink,
				guestSink, false, callbacks);
	}

	public static void startHostedBattle(UUID hostUuid, String hostName, List<PokemonDto> hostTeam,
			UUID guestUuid, String guestName, List<PokemonDto> guestTeam, List<String> battleRules, int adjustLevel,
			java.util.function.Consumer<NetworkPacket<?>> hostSink,
			java.util.function.Consumer<NetworkPacket<?>> guestSink, boolean guestIsAi, HostCallbacks callbacks) {
		startHostedBattle(hostUuid, hostName, hostTeam, guestUuid, guestName, guestTeam, battleRules, adjustLevel, hostSink,
				guestSink, guestIsAi ? "random" : null, callbacks);
	}

	/**
	 * {@code guestAi} non-null: the guest side is played by an AI on this client, every time the engine asks it to
	 * choose — the admin solo battle (Adrien 2026-10-07, a mirror of the host's team, {@code random}) or an event's
	 * trainer NPC (an add-on's, its AI level: see {@link #ai}); {@code guestSink} then only sees its packets.
	 */
	public static void startHostedBattle(UUID hostUuid, String hostName, List<PokemonDto> hostTeam,
			UUID guestUuid, String guestName, List<PokemonDto> guestTeam, List<String> battleRules, int adjustLevel,
			java.util.function.Consumer<NetworkPacket<?>> hostSink,
			java.util.function.Consumer<NetworkPacket<?>> guestSink, String guestAi, HostCallbacks callbacks) {
		boolean guestIsAi = guestAi != null;
		com.cobblemon.mod.common.api.battles.model.ai.BattleAI ai = guestIsAi ? ai(guestAi) : null;
		BattleThread.get().submit(() -> {
			try {
				BattleThread.get().ensureShowdown();
				List<BattlePokemon> hostPokemon = battleTeam(hostTeam);
				List<BattlePokemon> guestPokemon = battleTeam(guestTeam);
				if (ai instanceof PhantasmonBattleAI addonAi && !hostPokemon.isEmpty() && guestPokemon.size() > 1) {
					// An add-on's AI may pick its lead against the player's team, as at team preview (Adrien 2026-11-03).
					guestPokemon = addonAi.orderTeam(guestPokemon, hostPokemon);
				}
				if (hostPokemon.isEmpty() || guestPokemon.isEmpty()) {
					callbacks.failed();
					return;
				}
				GhostBattleActor host = new GhostBattleActor(hostUuid, hostName, hostPokemon, hostSink);
				GhostBattleActor[] guestRef = new GhostBattleActor[1];
				java.util.function.Consumer<NetworkPacket<?>> sink = !guestIsAi ? guestSink : packet -> {
					guestSink.accept(packet);
					if (packet instanceof com.cobblemon.mod.common.net.messages.client.battle.BattleMakeChoicePacket) {
						// Queued after the engine's current step, once the request and "must choose" are set.
						BattleThread.get().submit(() -> {
							GhostBattleActor actor = guestRef[0];
							if (actor != null && actor.getBattle() != null && !actor.getBattle().getEnded()) {
								autoChoose(actor.getBattle(), actor, ai);
							}
						});
					}
				};
				GhostBattleActor guest = new GhostBattleActor(guestUuid, guestName, guestPokemon, sink);
				guestRef[0] = guest;
				// The picked format (TODO-24): Cobblemon's singles plus the rules Showdown's engine applies itself
				// (Sleep Clause Mod, Terastal Clause...), and the format's level for everyone (0 = their own).
				BattleFormat singles = BattleFormat.Companion.getGEN_9_SINGLES();
				java.util.Set<String> rules = new java.util.LinkedHashSet<>(singles.getRuleSet());
				rules.addAll(battleRules);
				BattleFormat format = singles.copy(singles.getMod(), singles.getBattleType(), rules, singles.getGen(),
						adjustLevel > 0 ? adjustLevel : singles.getAdjustLevel());
				BattleStartResult result = BattleRegistry.startBattle(format,
						new BattleSide(host), new BattleSide(guest), false);
				if (!(result instanceof SuccessfulBattleStart success)) {
					callbacks.failed();
					return;
				}
				PokemonBattle battle = success.getBattle();
				route(battle.getBattleId(), choice -> applyChoice(choice, hostUuid));
				EFFECT_ROUTES.put(battle.getBattleId(), callbacks::effect);
				FORME_ROUTES.put(battle.getBattleId(), callbacks::formeChange);
				SPECTATOR_ROUTES.put(battle.getBattleId(), callbacks::spectator);
				battle.getOnEndHandlers().add(ended -> {
					GhostBattleLog.write(ended);
					unroute(ended.getBattleId());
					EFFECT_ROUTES.remove(ended.getBattleId());
					FORME_ROUTES.remove(ended.getBattleId());
					SPECTATOR_ROUTES.remove(ended.getBattleId());
					if (!STOPPED.remove(ended.getBattleId())) {
						UUID winner = null;
						for (BattleActor actor : ended.getWinners()) {
							winner = actor.getUuid();
						}
						callbacks.finished(ended.getWinners().size() == 1 ? winner : null);
					}
					return kotlin.Unit.INSTANCE;
				});
				callbacks.started(battle.getBattleId());
				LOG.info("Hosted Ghost battle {} started: {} vs {}", battle.getBattleId(), hostName, guestName);
			} catch (Exception ex) {
				LOG.error("Hosted Ghost battle failed to start", ex);
				callbacks.failed();
			}
		});
	}

	/** Ends a hosted battle without reporting a result (the backend already decided: forfeit or disconnection). */
	public static void stop(UUID cobblemonBattleId) {
		BattleThread.get().submit(() -> {
			PokemonBattle battle = BattleRegistry.getBattle(cobblemonBattleId);
			if (battle != null && !battle.getEnded()) {
				STOPPED.add(cobblemonBattleId);
				battle.stop();
			}
		});
	}

	/**
	 * Turn timer ran out for {@code playerUuid}: plays an automatic choice for them (Cobblemon's random AI, the
	 * same engine an AI trainer would use), exactly as if they had picked it.
	 */
	public static void forceAutomaticChoice(UUID cobblemonBattleId, UUID playerUuid) {
		BattleThread.get().submit(() -> {
			PokemonBattle battle = BattleRegistry.getBattle(cobblemonBattleId);
			if (battle == null || battle.getEnded()) {
				return;
			}
			for (BattleActor actor : battle.getActors()) {
				if (actor.getUuid().equals(playerUuid) && autoChoose(battle, actor)) {
					LOG.info("Ghost battle timer: automatic choice played for {}", playerUuid);
				}
			}
		});
	}

	/** AI levels added by add-ons (TODO-35, D-41): level → a fresh AI, its argument the chat debug. */
	private static final Map<String, java.util.function.Function<Boolean, PhantasmonBattleAI>> ADDON_AIS =
			new java.util.concurrent.ConcurrentHashMap<>();

	/** Cobblemon's own levels: never replaced by an add-on. */
	private static final java.util.Set<String> COBBLEMON_AIS = java.util.Set.of("random", "easy", "normal", "hard", "veryhard");

	/** {@link com.mystaria.phantasmon.client.api.PhantasmonApi#registerBattleAi}. */
	public static void registerAi(String level, java.util.function.Function<Boolean, PhantasmonBattleAI> factory) {
		if (level == null || factory == null || COBBLEMON_AIS.contains(level)) {
			throw new IllegalArgumentException("Cannot register battle AI level " + level);
		}
		ADDON_AIS.put(level, factory);
		LOG.info("Battle AI level {} registered", level);
	}

	/**
	 * An AI level (NPC battles): {@code random} is Cobblemon's random AI; {@code easy}, {@code normal}, {@code hard},
	 * {@code veryhard} its "strong" AI at skill 1, 3, 4 and 5 (damage, type matchups, switching out, statuses and
	 * boosts by skill); any other level comes from an add-on ({@link #registerAi}; Phantasmon Events brings
	 * {@code expert}, {@code reborn}, {@code reborn_tweak}). Unknown: {@code normal}.
	 */
	public static com.cobblemon.mod.common.api.battles.model.ai.BattleAI ai(String level) {
		// "<level>:debug": the NPC's AI debug, its reasoning in the chat (Adrien 2026-11-03).
		boolean debug = level != null && level.endsWith(":debug");
		if (debug) {
			level = level.substring(0, level.length() - ":debug".length());
		}
		java.util.function.Function<Boolean, PhantasmonBattleAI> addon = level == null ? null : ADDON_AIS.get(level);
		if (addon != null) {
			return addon.apply(debug);
		}
		if (level != null && !COBBLEMON_AIS.contains(level)) {
			LOG.warn("Battle AI level {} unknown (add-on missing?): Cobblemon's normal AI plays", level);
		}
		return switch (level == null ? "normal" : level) {
			case "random" -> new com.cobblemon.mod.common.battles.ai.RandomBattleAI();
			case "easy" -> new com.cobblemon.mod.common.battles.ai.StrongBattleAI(1);
			case "hard" -> new com.cobblemon.mod.common.battles.ai.StrongBattleAI(4);
			case "veryhard" -> new com.cobblemon.mod.common.battles.ai.StrongBattleAI(5);
			default -> new com.cobblemon.mod.common.battles.ai.StrongBattleAI(3);
		};
	}

	/** Cobblemon's random AI picks for {@code actor} (the turn timer ran out). Battle thread. */
	private static boolean autoChoose(PokemonBattle battle, BattleActor actor) {
		return autoChoose(battle, actor, new com.cobblemon.mod.common.battles.ai.RandomBattleAI());
	}

	/** {@code ai} picks for {@code actor}, if the engine is waiting on it. Battle thread. */
	private static boolean autoChoose(PokemonBattle battle, BattleActor actor, com.cobblemon.mod.common.api.battles.model.ai.BattleAI ai) {
		if (!actor.getMustChoose() || actor.getRequest() == null) {
			return false;
		}
		java.util.Set<UUID> switchedIn = new java.util.HashSet<>();
		actor.setActionResponses(actor.getRequest().iterate(actor.getActivePokemon(),
				(active, moveset, forceSwitch) -> safeChoice(ai, battle, actor, active, moveset, forceSwitch, switchedIn)));
		return true;
	}

	/**
	 * A move aimed at one Pokémon must name its target, or Cobblemon refuses the choice: the AIs named none, so every
	 * Fire Punch, Ice Beam or Taunt fell to the random AI (Adrien 2026-11-07). The first foe it can reach, else the
	 * first possible target (an ally for an ally-only move).
	 */
	private static void withTarget(com.cobblemon.mod.common.battles.ShowdownActionResponse response, com.cobblemon.mod.common.battles.ActiveBattlePokemon active, com.cobblemon.mod.common.battles.ShowdownMoveset moveset) {
		if (!(response instanceof com.cobblemon.mod.common.battles.MoveActionResponse move) || move.getTargetPnx() != null || moveset == null) {
			return;
		}
		for (com.cobblemon.mod.common.battles.InBattleMove known : moveset.getMoves()) {
			if (!known.getId().equals(move.getMoveName())) {
				continue;
			}
			com.cobblemon.mod.common.battles.InBattleGimmickMove gimmick = known.getGimmickMove();
			com.cobblemon.mod.common.battles.MoveTarget aim = move.getGimmickID() != null && gimmick != null && !gimmick.getDisabled() ? gimmick.getTarget() : known.getTarget();
			java.util.List<com.cobblemon.mod.common.battles.Targetable> targets = aim.getTargetList().invoke(active);
			if (targets == null || targets.isEmpty()) {
				return;
			}
			com.cobblemon.mod.common.battles.Targetable chosen = targets.get(0);
			for (com.cobblemon.mod.common.battles.Targetable target : targets) {
				if (!target.isAllied(active)) {
					chosen = target;
					break;
				}
			}
			move.setTargetPnx(chosen.getPNX());
			return;
		}
	}

	/**
	 * {@code ai}'s choice, made safe (Adrien's tests 2026-11-02: after a K.O., Cobblemon's strong AI kept sending
	 * "pass" where a replacement was required — "Can't pass: You need to switch in a Pokémon" — and the battle stuck,
	 * its last Pokémon, a damaged Zamazenta, never sent). A <b>forced switch</b> is never left to the AI: the healthiest
	 * Pokémon of the actor's own list, not on the field, goes in. Any other switch the AI proposes must name one of
	 * them; a failure or anything else invalid falls back to the random AI.
	 */
	private static com.cobblemon.mod.common.battles.ShowdownActionResponse safeChoice(
			com.cobblemon.mod.common.api.battles.model.ai.BattleAI ai, PokemonBattle battle, BattleActor actor,
			com.cobblemon.mod.common.battles.ActiveBattlePokemon active, com.cobblemon.mod.common.battles.ShowdownMoveset moveset,
			boolean forceSwitch, java.util.Set<UUID> switchedIn) {
		if (forceSwitch) {
			// An add-on's AI (expert, Reborn) picks its replacement (checked); Cobblemon's get the healthiest Pokémon.
			if (ai instanceof PhantasmonBattleAI) {
				try {
					if (ai.choose(active, battle, actor.getSide(), moveset, true) instanceof com.cobblemon.mod.common.battles.SwitchActionResponse change
							&& switchable(actor, change.getNewPokemonId(), switchedIn) != null) {
						switchedIn.add(change.getNewPokemonId());
						return change;
					}
				} catch (RuntimeException ex) {
					LOG.warn("Battle AI failed to pick a replacement, falling back", ex);
				}
			}
			BattlePokemon replacement = bestReplacement(actor, switchedIn);
			if (replacement != null) {
				switchedIn.add(replacement.getUuid());
				return new com.cobblemon.mod.common.battles.SwitchActionResponse(replacement.getUuid());
			}
			LOG.warn("Forced switch with no Pokémon left to send: passing");
			return com.cobblemon.mod.common.battles.PassActionResponse.INSTANCE;
		}
		com.cobblemon.mod.common.battles.ShowdownActionResponse response = null;
		try {
			response = ai.choose(active, battle, actor.getSide(), moveset, false);
		} catch (RuntimeException ex) {
			LOG.warn("Battle AI failed to choose, falling back", ex);
		}
		if (response instanceof com.cobblemon.mod.common.battles.SwitchActionResponse change) {
			BattlePokemon target = switchable(actor, change.getNewPokemonId(), switchedIn);
			if (target != null) {
				switchedIn.add(target.getUuid());
				return response;
			}
			LOG.info("Battle AI switch to {} refused (not a Pokémon able to come in): falling back", change.getNewPokemonId());
			response = null;
		}
		withTarget(response, active, moveset);
		if (response != null && !(response instanceof com.cobblemon.mod.common.battles.PassActionResponse)
				&& response.isValid(active, moveset, false)) {
			return response;
		}
		com.cobblemon.mod.common.battles.ShowdownActionResponse fallback = new com.cobblemon.mod.common.battles.ai.RandomBattleAI()
				.choose(active, battle, actor.getSide(), moveset, false);
		// Adrien 2026-11-03: the AI debug announced a move the NPC then did not play — Cobblemon refused it and the
		// random AI played. Say so, so the debug never shows a choice that was not played.
		LOG.info("Battle AI choice {} refused by Cobblemon: random AI plays {}", describe(response), describe(fallback));
		if (ai instanceof PhantasmonBattleAI addonAi) {
			addonAi.onChoiceRefused("§c[IA] Choix refusé par Cobblemon (" + describe(response) + ") : joué au hasard → " + describe(fallback));
		}
		return fallback;
	}

	private static String describe(com.cobblemon.mod.common.battles.ShowdownActionResponse response) {
		if (response instanceof com.cobblemon.mod.common.battles.MoveActionResponse move) {
			return move.getMoveName() + (move.getGimmickID() == null ? "" : " + " + move.getGimmickID());
		}
		if (response instanceof com.cobblemon.mod.common.battles.SwitchActionResponse change) {
			return "switch " + change.getNewPokemonId();
		}
		return response == null ? "aucun" : response.getClass().getSimpleName();
	}

	/** The actor's Pokémon with {@code uuid} if it can come in: healthy, not on the field, not already chosen. */
	private static BattlePokemon switchable(BattleActor actor, UUID uuid, java.util.Set<UUID> switchedIn) {
		if (uuid == null || switchedIn.contains(uuid)) {
			return null;
		}
		java.util.Set<UUID> onField = new java.util.HashSet<>();
		for (com.cobblemon.mod.common.battles.ActiveBattlePokemon slot : actor.getActivePokemon()) {
			if (slot.getBattlePokemon() != null) {
				onField.add(slot.getBattlePokemon().getUuid());
			}
		}
		for (BattlePokemon pokemon : actor.getPokemonList()) {
			if (pokemon.getUuid().equals(uuid)) {
				return pokemon.getHealth() > 0 && !onField.contains(uuid) ? pokemon : null;
			}
		}
		return null;
	}

	/** The healthiest Pokémon (by share of HP left) able to come in, or null. */
	private static BattlePokemon bestReplacement(BattleActor actor, java.util.Set<UUID> switchedIn) {
		BattlePokemon best = null;
		double bestShare = -1;
		for (BattlePokemon pokemon : actor.getPokemonList()) {
			if (switchable(actor, pokemon.getUuid(), switchedIn) == null) {
				continue;
			}
			double share = pokemon.getHealth() / (double) Math.max(1, pokemon.getMaxHealth());
			if (share > bestShare) {
				bestShare = share;
				best = pokemon;
			}
		}
		return best;
	}

	/** Whether {@code playerUuid} currently has to choose in this hosted battle (timer bookkeeping). */
	public static void whenMustChoose(UUID cobblemonBattleId, UUID playerUuid, java.util.function.Consumer<Boolean> answer) {
		BattleThread.get().submit(() -> {
			PokemonBattle battle = BattleRegistry.getBattle(cobblemonBattleId);
			boolean mustChoose = false;
			if (battle != null && !battle.getEnded()) {
				for (BattleActor actor : battle.getActors()) {
					if (actor.getUuid().equals(playerUuid)) {
						mustChoose = actor.getMustChoose();
					}
				}
			}
			answer.accept(mustChoose);
		});
	}

	/** Pokémon this client's Cobblemon doesn't recognize sit the battle out; the host is told which (DEBT-5). */
	static List<BattlePokemon> battleTeam(List<PokemonDto> team) {
		List<BattlePokemon> result = new ArrayList<>();
		for (PokemonDto dto : team) {
			net.minecraft.network.chat.Component unrecognized = com.mystaria.phantasmon.client.pokemon.PokemonRecognition.problem(dto);
			Pokemon pokemon = unrecognized == null ? GhostBattlePokemonFactory.create(dto) : null;
			if (pokemon != null) {
				result.add(new BattlePokemon(pokemon, pokemon, new ArrayList<>(), new ArrayList<>()));
			} else if (unrecognized != null) {
				Minecraft.getInstance().execute(() -> {
					if (Minecraft.getInstance().player != null) {
						Minecraft.getInstance().player.displayClientMessage(unrecognized, false);
					}
				});
			}
		}
		return result;
	}
}
