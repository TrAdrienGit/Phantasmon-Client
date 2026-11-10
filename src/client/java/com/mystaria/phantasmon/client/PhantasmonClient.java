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
import com.mystaria.phantasmon.client.pokemon.PcOpener;
import com.mystaria.phantasmon.client.trade.LiveTradeController;

public class PhantasmonClient implements ClientModInitializer {

	private final BackendJsonClient httpClient = new BackendJsonClient();
	private final BackendHealthPinger healthPinger = new BackendHealthPinger(BackendConfig.BASE_URL.resolve("/health"));
	private final PingToggle pingToggle = new PingToggle(healthPinger);
	private final AuthSession authSession = new AuthSession();
	private final AuthService authService = new AuthService(httpClient, authSession);
	private final SessionRefreshScheduler refreshScheduler = new SessionRefreshScheduler(authService);
	private final PokemonClient pokemonClient = new PokemonClient(httpClient);
	private final com.mystaria.phantasmon.client.admin.AdminClient adminClient = new com.mystaria.phantasmon.client.admin.AdminClient(httpClient);
	private final PcOpener pcOpener = new PcOpener(pokemonClient, authSession);
	private final GhostSession ghostSession = new GhostSession(authSession);
	private final com.mystaria.phantasmon.client.ghost.GhostPartyHud ghostPartyHud =
			new com.mystaria.phantasmon.client.ghost.GhostPartyHud(pokemonClient, authSession, ghostSession);
	private final LiveTradeController liveTrade = new LiveTradeController(ghostSession, authSession);
	private final com.mystaria.phantasmon.client.battle.LiveBattleController liveBattle =
			new com.mystaria.phantasmon.client.battle.LiveBattleController(ghostSession, authSession);
	private final com.mystaria.phantasmon.client.hub.HubClient hubClient = new com.mystaria.phantasmon.client.hub.HubClient(httpClient);
	private final com.mystaria.phantasmon.client.hub.HubController hub = new com.mystaria.phantasmon.client.hub.HubController(
			hubClient, authSession, ghostSession);

	@Override
	public void onInitializeClient() {
		authService.setOnAuthenticated(() -> {
			ghostSession.start();
			// Admin (TODO-25): the backend says whether this player may use /phantasmon admin.
			com.mystaria.phantasmon.client.admin.AdminSession.refresh(adminClient, authSession);
		});
		ghostSession.setLiveTradeListener(liveTrade);
		ghostSession.setLiveBattleListener(liveBattle);
		ghostSession.setHubListener(hub);
		com.mystaria.phantasmon.client.api.PhantasmonApi.init(hub, ghostSession, httpClient, authSession);
		com.mystaria.phantasmon.client.api.PhantasmonApi.bindLiveBattle(liveBattle);
		com.mystaria.phantasmon.client.voice.HubVoice.bind(ghostSession);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			pingToggle.onJoin();
			refreshScheduler.start();
			authService.autoLoginIfBackendHealthy();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			pingToggle.onDisconnect();
			refreshScheduler.stop();
			ghostSession.stop();
			hub.reset();
			com.mystaria.phantasmon.client.hub.HubPlacement.cancel();
			com.mystaria.phantasmon.client.battle.BattleVisuals.clear();
			com.mystaria.phantasmon.client.battle.BattleFieldScenes.clear();
			com.mystaria.showtime.audio.BattleMusic.stop();
			com.mystaria.phantasmon.client.admin.AdminSession.clear();
			com.mystaria.phantasmon.client.admin.CommandTreeRefresher.clear();
			authSession.clear();
		});
		// Hub builds (D-34): a chunk the server (re)sends has lost its client-only blocks — built again.
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_LOAD.register((level, chunk) ->
				com.mystaria.phantasmon.client.hub.HubBuilds.onChunkLoad(level, chunk.getPos()));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ghostSession.onClientTick();
			pcOpener.tick();
			com.mystaria.phantasmon.client.battle.TrainerProfiles.tick();
			liveTrade.tick();
			liveBattle.tick();
			hub.tick();
			com.mystaria.phantasmon.client.hub.HubPlacement.tick();
			com.mystaria.phantasmon.client.voice.HubVoice.tick();
			com.mystaria.phantasmon.client.battle.BattleFieldScenes.tick();
			ghostPartyHud.tick();
			com.mystaria.phantasmon.client.compat.BattleExtrasCompat.tick();
			PhantasmonKeybinds.tick(pcOpener, this::openHubManager);
		});

		WorldRenderEvents.AFTER_ENTITIES.register(hub::renderWorld);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(context ->
				com.mystaria.phantasmon.client.hub.HubDebugBorders.render(context, hub::anchorsHere));
		WorldRenderEvents.AFTER_TRANSLUCENT.register(com.mystaria.phantasmon.client.hub.HubPlacement::render);
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> ghostPartyHud.render(graphics));

		com.mystaria.phantasmon.client.battle.ShowtimeBridge.register(liveBattle);
		com.mystaria.phantasmon.client.battle.GhostVirtualItems.register();
		com.mystaria.phantasmon.client.wheel.GhostWheelOptions.bind(liveTrade, liveBattle);
		PhantasmonKeybinds.register();
		com.mystaria.phantasmon.client.admin.AdminCommands.register(adminClient, authSession, pcOpener, pingToggle, liveBattle);
		com.mystaria.phantasmon.client.hub.HubCommands.register(hub);
		PhantasmonCommands.register(authService, ghostSession, liveTrade, liveBattle);
	}

	/** Key H: the hub manager — an admin's, or a player's (their anchors); a player not logged in gets a message. */
	private void openHubManager() {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player == null || mc.screen != null) {
			return;
		}
		if (!authSession.isAuthenticated()) {
			mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("phantasmon.error.not_authenticated")
					.withStyle(net.minecraft.ChatFormatting.RED), false);
			return;
		}
		// H again while placing an anchor: placing stops, the manager opens.
		com.mystaria.phantasmon.client.hub.HubPlacement.cancel();
		mc.setScreen(new com.mystaria.phantasmon.client.gui.PhantasmonHubManagerScreen(hubClient, authSession, hub,
				com.mystaria.phantasmon.client.admin.AdminSession.isAdmin()));
	}
}
