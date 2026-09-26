package com.mystaria.phantasmon.client.auth;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically calls {@link AuthService#refreshIfNeeded()} so a logged-in
 * session's access token gets renewed before it expires, without the player
 * having to notice or re-run {@code /phantasmon login} (client hard rule 8:
 * "JWT kept in memory + refresh"). {@link AuthService#refreshIfNeeded()}
 * itself is a no-op unless the token is actually close to expiring, so a
 * short check interval here is cheap.
 */
public final class SessionRefreshScheduler {

	private static final long CHECK_INTERVAL_SECONDS = 60;

	private final AuthService authService;

	private ScheduledExecutorService scheduler;

	public SessionRefreshScheduler(AuthService authService) {
		this.authService = authService;
	}

	public synchronized void start() {
		if (scheduler != null) {
			return;
		}
		scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "phantasmon-session-refresh");
			thread.setDaemon(true);
			return thread;
		});
		scheduler.scheduleAtFixedRate(authService::refreshIfNeeded, CHECK_INTERVAL_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	public synchronized void stop() {
		if (scheduler == null) {
			return;
		}
		scheduler.shutdownNow();
		scheduler = null;
	}
}
