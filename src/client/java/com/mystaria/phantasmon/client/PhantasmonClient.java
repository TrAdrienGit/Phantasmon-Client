package com.mystaria.phantasmon.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import com.mystaria.phantasmon.client.auth.AuthService;
import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.command.PhantasmonCommands;
import com.mystaria.phantasmon.client.network.BackendConfig;
import com.mystaria.phantasmon.client.network.BackendHealthPinger;
import com.mystaria.phantasmon.client.network.BackendJsonClient;

public class PhantasmonClient implements ClientModInitializer {

	private final BackendHealthPinger healthPinger = new BackendHealthPinger(BackendConfig.BASE_URL.resolve("/health"));
	private final AuthSession authSession = new AuthSession();
	private final AuthService authService = new AuthService(new BackendJsonClient(), authSession);

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> healthPinger.start());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			healthPinger.stop();
			authSession.clear();
		});
		PhantasmonCommands.register(authService);
	}
}
