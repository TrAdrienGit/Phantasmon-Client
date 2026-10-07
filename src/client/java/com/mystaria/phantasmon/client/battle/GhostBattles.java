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

import com.mystaria.phantasmon.client.pokemon.PokemonDto;

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
			battle.dispatchWaitingToFront(BattleSpectacle.pauseSeconds(aspect), () -> kotlin.Unit.INSTANCE);
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

	/**
	 * {@code guestIsAi}: the admin solo battle (Adrien 2026-10-07) — the guest side is a mirror of the host's team that
	 * Cobblemon's random AI plays on this client, every time the engine asks it to choose; {@code guestSink} then only
	 * sees its packets.
	 */
	public static void startHostedBattle(UUID hostUuid, String hostName, List<PokemonDto> hostTeam,
			UUID guestUuid, String guestName, List<PokemonDto> guestTeam, List<String> battleRules, int adjustLevel,
			java.util.function.Consumer<NetworkPacket<?>> hostSink,
			java.util.function.Consumer<NetworkPacket<?>> guestSink, boolean guestIsAi, HostCallbacks callbacks) {
		BattleThread.get().submit(() -> {
			try {
				BattleThread.get().ensureShowdown();
				List<BattlePokemon> hostPokemon = battleTeam(hostTeam);
				List<BattlePokemon> guestPokemon = battleTeam(guestTeam);
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
								autoChoose(actor.getBattle(), actor);
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

	/** Cobblemon's random AI picks for {@code actor}, if the engine is waiting on it. Battle thread. */
	private static boolean autoChoose(PokemonBattle battle, BattleActor actor) {
		if (!actor.getMustChoose() || actor.getRequest() == null) {
			return false;
		}
		var ai = new com.cobblemon.mod.common.battles.ai.RandomBattleAI();
		actor.setActionResponses(actor.getRequest().iterate(actor.getActivePokemon(),
				(active, moveset, forceSwitch) -> ai.choose(active, battle, actor.getSide(), moveset, forceSwitch)));
		return true;
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
