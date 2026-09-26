package com.mystaria.phantasmon.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import com.mystaria.phantasmon.client.auth.AuthService;
import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.auth.SessionRefreshScheduler;
import com.mystaria.phantasmon.client.command.PhantasmonCommands;
import com.mystaria.phantasmon.client.ghost.GhostSession;
import com.mystaria.phantasmon.client.network.BackendConfig;
import com.mystaria.phantasmon.client.network.BackendHealthPinger;
import com.mystaria.phantasmon.client.network.BackendJsonClient;
import com.mystaria.phantasmon.client.network.PingToggle;
import com.mystaria.phantasmon.client.pokemon.PokemonClient;
import com.mystaria.phantasmon.client.pokemon.PokemonCommandHandler;
import com.mystaria.phantasmon.client.trade.TradeClient;
import com.mystaria.phantasmon.client.trade.TradeCommandHandler;

public class PhantasmonClient implements ClientModInitializer {

	private final BackendJsonClient httpClient = new BackendJsonClient();
	private final BackendHealthPinger healthPinger = new BackendHealthPinger(BackendConfig.BASE_URL.resolve("/health"));
	private final PingToggle pingToggle = new PingToggle(healthPinger);
	private final AuthSession authSession = new AuthSession();
	private final AuthService authService = new AuthService(httpClient, authSession);
	private final SessionRefreshScheduler refreshScheduler = new SessionRefreshScheduler(authService);
	private final PokemonCommandHandler pokemonCommands = new PokemonCommandHandler(new PokemonClient(httpClient), authSession);
	private final GhostSession ghostSession = new GhostSession(authSession);
	private final TradeCommandHandler tradeCommands = new TradeCommandHandler(new TradeClient(httpClient), authSession);

	@Override
	public void onInitializeClient() {
		authService.setOnAuthenticated(ghostSession::start);
		ghostSession.setTradeNotificationListener(tradeCommands);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			pingToggle.onJoin();
			refreshScheduler.start();
			authService.autoLoginIfBackendHealthy();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			pingToggle.onDisconnect();
			refreshScheduler.stop();
			ghostSession.stop();
			authSession.clear();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> ghostSession.onClientTick());

		PhantasmonCommands.register(authService, pingToggle, pokemonCommands, ghostSession, tradeCommands);
	}
}
