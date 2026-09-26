package com.mystaria.phantasmon.client.network;

/**
 * On/off switch for {@link BackendHealthPinger}, off by default (Adrien:
 * 2026-09-26, the heartbeat was only ever meant as a wire-proving MVP, not a
 * feature players should see spamming their chat every session). Player
 * toggles it with {@code /phantasmon toggle-ping}; the chosen state applies
 * for the current game session only (no persisted setting yet).
 */
public final class PingToggle {

	private final BackendHealthPinger pinger;

	private boolean enabled = false;

	public PingToggle(BackendHealthPinger pinger) {
		this.pinger = pinger;
	}

	public synchronized void onJoin() {
		if (enabled) {
			pinger.start();
		}
	}

	public synchronized void onDisconnect() {
		pinger.stop();
	}

	public synchronized boolean toggle() {
		enabled = !enabled;
		if (enabled) {
			pinger.start();
		} else {
			pinger.stop();
		}
		return enabled;
	}
}
