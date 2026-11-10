package com.mystaria.phantasmon.client.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.ghost.GhostSession;
import com.mystaria.phantasmon.client.hub.HubAnchorDto;
import com.mystaria.phantasmon.client.hub.HubController;
import com.mystaria.phantasmon.client.network.BackendConfig;
import com.mystaria.phantasmon.client.network.BackendJsonClient;

/**
 * What Phantasmon's add-ons (Phantasmon Events) may use of it, like Cobblemon Battle Showtime's {@code api} package (D-37):
 * nothing else of Phantasmon is meant for them. Set once Phantasmon's client is initialized.
 */
public final class PhantasmonApi {

	private static HubController hubs;
	private static GhostSession ghostSession;
	private static BackendJsonClient httpClient;
	private static AuthSession authSession;
	private static com.mystaria.phantasmon.client.battle.LiveBattleController liveBattle;
	private static final List<BiConsumer<String, Map<String, Object>>> EVENTS_LISTENERS = new CopyOnWriteArrayList<>();

	private PhantasmonApi() {
	}

	/** The hubs (D-35) and their mirror hubs (D-38): creating them and posing their zones. */
	public static HubController hubs() {
		return hubs;
	}

	/** Whether the backend lists this player as an admin (TODO-25). */
	public static boolean isAdmin() {
		return com.mystaria.phantasmon.client.admin.AdminSession.isAdmin();
	}

	public static boolean isAuthenticated() {
		return authSession != null && authSession.isAuthenticated();
	}

	/** The fingerprint Phantasmon gives the current server or single-player world (D-30). */
	public static String serverFingerprint() {
		return GhostSession.serverFingerprint();
	}

	/** The anchors of the current server and dimension, as Phantasmon last listed them (every 30 s). */
	public static List<HubAnchorDto> anchorsHere() {
		return hubs.anchorsHere();
	}

	/** The player's own anchors, every server. */
	public static CompletableFuture<HubAnchorDto[]> myAnchors() {
		return hubs.mine();
	}

	/** A message on Phantasmon's WebSocket; its {@code type} must start with {@code Events}. False if not connected. */
	public static boolean send(String type, Map<String, Object> data) {
		if (!type.startsWith("Events")) {
			throw new IllegalArgumentException("Add-on messages start with Events: " + type);
		}
		return ghostSession != null && ghostSession.send(type, data);
	}

	/** Receives every {@code Events*} message from the backend, on the client thread. */
	public static void onEventsMessage(BiConsumer<String, Map<String, Object>> listener) {
		EVENTS_LISTENERS.add(listener);
	}

	/** {@code GET} on the backend with the player's token ({@code path} like {@code /events/...}). */
	public static <T> CompletableFuture<T> get(String path, Class<T> type) {
		return httpClient.get(BackendConfig.BASE_URL.resolve(path), authSession.accessToken(), type);
	}

	/** {@code POST} on the backend with the player's token; {@code body} null: none. */
	public static <T> CompletableFuture<T> post(String path, Object body, Class<T> type) {
		return body == null ? httpClient.postNoBody(BackendConfig.BASE_URL.resolve(path), authSession.accessToken(), type)
				: httpClient.post(BackendConfig.BASE_URL.resolve(path), body, authSession.accessToken(), type);
	}

	/** {@code DELETE} on the backend with the player's token. */
	public static <T> CompletableFuture<T> delete(String path, Class<T> type) {
		return httpClient.delete(BackendConfig.BASE_URL.resolve(path), authSession.accessToken(), type);
	}

	/**
	 * The player's Cobblemon party as an invitation sends it ({@code team: COBBLEMON}, {@code party}), or null if empty:
	 * the backend can check it against a format.
	 */
	public static Map<String, Object> cobblemonParty() {
		List<Map<String, Object>> party = com.mystaria.phantasmon.client.battle.CobblemonPartySnapshot.current();
		return party.isEmpty() ? null : Map.of("team", "COBBLEMON", "party", party);
	}

	/** The backend's error code of a failed call ({@code ERROR_...}), or null for a network failure. */
	public static String errorCode(Throwable failure) {
		Throwable cause = failure instanceof java.util.concurrent.CompletionException ? failure.getCause() : failure;
		return cause instanceof com.mystaria.phantasmon.client.network.BackendApiException api ? api.errorCode() : null;
	}

	public static void init(HubController hubController, GhostSession session, BackendJsonClient client, AuthSession auth) {
		hubs = hubController;
		ghostSession = session;
		httpClient = client;
		authSession = auth;
	}

	private static final List<java.util.function.Predicate<net.minecraft.world.entity.Entity>> WHEEL_TARGETS = new CopyOnWriteArrayList<>();

	/**
	 * Cobblemon's send key (R) on an entity of another mod (Phantasmon Events' trainer NPCs): aimed at an entity
	 * {@code accepts} takes, {@code open} opens its own wheel for it (see
	 * {@link com.mystaria.phantasmon.client.wheel.GhostWheelOptions#openCustom}), and Cobblemon then does nothing.
	 */
	public static void registerWheelTarget(java.util.function.Predicate<net.minecraft.world.entity.Entity> accepts,
			java.util.function.Consumer<net.minecraft.world.entity.Entity> open) {
		WHEEL_TARGETS.add(accepts);
		WHEEL_OPENERS.add(open);
	}

	private static final List<java.util.function.Consumer<net.minecraft.world.entity.Entity>> WHEEL_OPENERS = new CopyOnWriteArrayList<>();

	/**
	 * The player aims at an entity a registered mod accepts, within the wheel's reach (the hub avatars' 20 blocks):
	 * that mod opens its wheel; true if one did.
	 */
	public static boolean openAimedWheel(net.minecraft.client.player.LocalPlayer player) {
		for (int i = 0; i < WHEEL_TARGETS.size(); i++) {
			java.util.function.Predicate<net.minecraft.world.entity.Entity> accepts = WHEEL_TARGETS.get(i);
			net.minecraft.world.entity.Entity aimed = com.mystaria.phantasmon.client.hub.HubAvatars.aimed(player, accepts);
			if (aimed != null) {
				WHEEL_OPENERS.get(i).accept(aimed);
				return true;
			}
		}
		return false;
	}

	public static void bindLiveBattle(com.mystaria.phantasmon.client.battle.LiveBattleController controller) {
		liveBattle = controller;
	}

	/**
	 * A battle against an event's trainer NPC ({@code npcUuid}), with the Ghost team or the Cobblemon party; the
	 * NPC's AI plays it on this client. False if there is no party when asked for one.
	 */
	public static boolean startNpcBattle(java.util.UUID npcUuid, boolean cobblemonTeam) {
		if (liveBattle == null) {
			return false;
		}
		Map<String, Object> party = cobblemonTeam ? cobblemonParty() : null;
		if (cobblemonTeam && party == null) {
			return false;
		}
		liveBattle.startNpc(npcUuid, party);
		return true;
	}

	/**
	 * Adds an AI level for NPC battles (TODO-35, D-41): {@code factory} makes a fresh AI for one battle, its argument
	 * the NPC's chat debug ({@code ai_debug}). Cobblemon's levels ({@code random}, {@code easy}, {@code normal},
	 * {@code hard}, {@code veryhard}) are Phantasmon's own and cannot be replaced.
	 */
	public static void registerBattleAi(String level, java.util.function.Function<Boolean, PhantasmonBattleAI> factory) {
		com.mystaria.phantasmon.client.battle.GhostBattles.registerAi(level, factory);
	}

	/** Phantasmon's WebSocket received an {@code Events*} message (client thread). */
	public static void dispatchEvents(String type, Map<String, Object> data) {
		EVENTS_LISTENERS.forEach(listener -> listener.accept(type, data));
	}
}
