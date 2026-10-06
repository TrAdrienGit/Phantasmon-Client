package com.mystaria.phantasmon.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

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
import com.mystaria.phantasmon.client.trade.LiveTradeController;
import com.mystaria.phantasmon.client.trade.TradeClient;
import com.mystaria.phantasmon.client.trade.TradeCommandHandler;

public class PhantasmonClient implements ClientModInitializer {

	private final BackendJsonClient httpClient = new BackendJsonClient();
	private final BackendHealthPinger healthPinger = new BackendHealthPinger(BackendConfig.BASE_URL.resolve("/health"));
	private final PingToggle pingToggle = new PingToggle(healthPinger);
	private final AuthSession authSession = new AuthSession();
	private final AuthService authService = new AuthService(httpClient, authSession);
	private final SessionRefreshScheduler refreshScheduler = new SessionRefreshScheduler(authService);
	private final PokemonClient pokemonClient = new PokemonClient(httpClient);
	private final com.mystaria.phantasmon.client.admin.AdminClient adminClient = new com.mystaria.phantasmon.client.admin.AdminClient(httpClient);
	private final PokemonCommandHandler pokemonCommands = new PokemonCommandHandler(pokemonClient, authSession);
	private final GhostSession ghostSession = new GhostSession(authSession);
	private final com.mystaria.phantasmon.client.ghost.GhostPartyHud ghostPartyHud =
			new com.mystaria.phantasmon.client.ghost.GhostPartyHud(pokemonClient, authSession, ghostSession);
	private final TradeCommandHandler tradeCommands = new TradeCommandHandler(new TradeClient(httpClient), authSession);
	private final LiveTradeController liveTrade = new LiveTradeController(ghostSession, authSession);
	private final com.mystaria.phantasmon.client.battle.LiveBattleController liveBattle =
			new com.mystaria.phantasmon.client.battle.LiveBattleController(ghostSession, authSession);

	@Override
	public void onInitializeClient() {
		authService.setOnAuthenticated(() -> {
			ghostSession.start();
			// Admin (TODO-25): the backend says whether this player may use /phantasmon admin.
			com.mystaria.phantasmon.client.admin.AdminSession.refresh(adminClient, authSession);
		});
		ghostSession.setTradeNotificationListener(tradeCommands);
		ghostSession.setLiveTradeListener(liveTrade);
		ghostSession.setLiveBattleListener(liveBattle);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			pingToggle.onJoin();
			refreshScheduler.start();
			authService.autoLoginIfBackendHealthy();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			pingToggle.onDisconnect();
			refreshScheduler.stop();
			ghostSession.stop();
			com.mystaria.phantasmon.client.battle.BattleVisuals.clear();
			com.mystaria.phantasmon.client.battle.BattleCinematic.stop();
			com.mystaria.phantasmon.client.audio.PhantasmonMusic.stop();
			com.mystaria.phantasmon.client.admin.AdminSession.clear();
			authSession.clear();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ghostSession.onClientTick();
			pokemonCommands.tick();
			liveTrade.tick();
			liveBattle.tick();
			com.mystaria.phantasmon.client.battle.BattleCinematic.tick();
			com.mystaria.phantasmon.client.battle.BattleSpectacle.tick();
			ghostPartyHud.tick();
			com.mystaria.phantasmon.client.audio.PhantasmonMusic.tick();
			PhantasmonKeybinds.tick(pokemonCommands);
		});

		WorldRenderEvents.AFTER_ENTITIES.register(com.mystaria.phantasmon.client.battle.BattleCinematic::renderWorld);
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
			ghostPartyHud.render(graphics);
			com.mystaria.phantasmon.client.battle.BattleCinematic.renderHud(graphics);
		});

		com.mystaria.phantasmon.client.battle.BattleScreenButtons.register(liveBattle);
		com.mystaria.phantasmon.client.battle.GhostZCrystals.register();
		com.mystaria.phantasmon.client.wheel.GhostWheelOptions.bind(liveTrade, liveBattle);
		PhantasmonKeybinds.register();
		com.mystaria.phantasmon.client.admin.AdminCommands.register(adminClient, authSession, pokemonCommands, pingToggle);
		PhantasmonCommands.register(authService, pingToggle, pokemonCommands, ghostSession, tradeCommands, liveTrade, liveBattle);
	}
}
