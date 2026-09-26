package com.mystaria.phantasmon.client.network;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Minimal client -> backend heartbeat: pings {@code GET /health} every 30
 * seconds while connected to a world and echoes the result into the player's
 * chat. This is a deliberate first vertical slice proving the client/backend
 * wire end to end before any real Ghost feature is built on top of it
 * (CAD Phase 5 groundwork). Off by default — see {@link PingToggle}.
 *
 * <p>The backend URL is hardcoded to the local dev backend for now — this is
 * intentional for this MVP slice, not an oversight. It must become
 * configurable before any real deployment.
 */
public final class BackendHealthPinger {

	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);
	private static final long PING_INTERVAL_SECONDS = 30;

	private final URI healthUri;
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(REQUEST_TIMEOUT)
			.build();

	private ScheduledExecutorService scheduler;

	public BackendHealthPinger(URI healthUri) {
		this.healthUri = healthUri;
	}

	public synchronized void start() {
		if (scheduler != null) {
			return;
		}
		scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "phantasmon-health-pinger");
			thread.setDaemon(true);
			return thread;
		});
		scheduler.scheduleAtFixedRate(this::pingOnce, 0, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	public synchronized void stop() {
		if (scheduler == null) {
			return;
		}
		scheduler.shutdownNow();
		scheduler = null;
	}

	private void pingOnce() {
		HttpRequest request = HttpRequest.newBuilder(healthUri)
				.timeout(REQUEST_TIMEOUT)
				.GET()
				.build();

		httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(response -> report("Backend " + response.statusCode() + " " + response.body()))
				.exceptionally(ex -> {
					report("Backend injoignable (" + ex.getMessage() + ")");
					return null;
				});
	}

	private void report(String message) {
		Minecraft.getInstance().execute(() -> {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				client.player.displayClientMessage(Component.literal("[Phantasmon] " + message), false);
			}
		});
	}
}
