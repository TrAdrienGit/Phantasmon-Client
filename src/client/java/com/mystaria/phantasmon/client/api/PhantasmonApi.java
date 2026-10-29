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
 * What Phantasmon's add-ons (Phantasmon Events) may use of it, like Cobblemon Spectacle's {@code api} package (D-37):
 * nothing else of Phantasmon is meant for them. Set once Phantasmon's client is initialized.
 */
public final class PhantasmonApi {

	private static HubController hubs;
	private static GhostSession ghostSession;
	private static BackendJsonClient httpClient;
	private static AuthSession authSession;
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

	public static void init(HubController hubController, GhostSession session, BackendJsonClient client, AuthSession auth) {
		hubs = hubController;
		ghostSession = session;
		httpClient = client;
		authSession = auth;
	}

	/** Phantasmon's WebSocket received an {@code Events*} message (client thread). */
	public static void dispatchEvents(String type, Map<String, Object> data) {
		EVENTS_LISTENERS.forEach(listener -> listener.accept(type, data));
	}
}
