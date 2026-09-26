package com.mystaria.phantasmon.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import com.mystaria.phantasmon.client.auth.AuthService;
import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.auth.SessionRefreshScheduler;
import com.mystaria.phantasmon.client.command.PhantasmonCommands;
import com.mystaria.phantasmon.client.network.BackendConfig;
import com.mystaria.phantasmon.client.network.BackendHealthPinger;
import com.mystaria.phantasmon.client.network.BackendJsonClient;
import com.mystaria.phantasmon.client.network.PingToggle;

public class PhantasmonClient implements ClientModInitializer {

	private final BackendHealthPinger healthPinger = new BackendHealthPinger(BackendConfig.BASE_URL.resolve("/health"));
	private final PingToggle pingToggle = new PingToggle(healthPinger);
	private final AuthSession authSession = new AuthSession();
	private final AuthService authService = new AuthService(new BackendJsonClient(), authSession);
	private final SessionRefreshScheduler refreshScheduler = new SessionRefreshScheduler(authService);

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			pingToggle.onJoin();
			refreshScheduler.start();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			pingToggle.onDisconnect();
			refreshScheduler.stop();
			authSession.clear();
		});
		PhantasmonCommands.register(authService, pingToggle);
	}
}
