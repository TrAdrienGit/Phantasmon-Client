package com.mystaria.phantasmon.client.trade;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.ghost.GhostSession;
import com.mystaria.phantasmon.client.gui.PhantasmonTradeScreen;
import com.mystaria.phantasmon.client.network.BackendErrorMessages;
import com.mystaria.phantasmon.client.pokemon.PokemonDto;

/**
 * Client side of the live trade (graphical trade screen, Adrien 2026-10-02):
 * turns player actions (invite command/keybind, chat [Accept]/[Decline],
 * clicks on {@link PhantasmonTradeScreen}) into WebSocket messages, and the
 * backend's answers into a {@link LiveTradeState} the screen renders every
 * frame. Never decides anything locally — every offer/ready change is only
 * shown once the server echoes it back in a {@code TradeSessionUpdate}.
 *
 * <p>Rides the presence WebSocket owned by {@link GhostSession} (no second
 * connection), same as the older {@code TradeNotificationListener} for the
 * asynchronous {@code /phantasmon trade propose} flow, which stays available.
 */
public final class LiveTradeController implements LiveTradeListener {

	private final GhostSession ghostSession;
	private final AuthSession session;

	private LiveTradeState state;
	private UUID pendingInviteUuid;
	private String pendingInviteFrom;
	private boolean screenRequested;

	public LiveTradeController(GhostSession ghostSession, AuthSession session) {
		this.ghostSession = ghostSession;
		this.session = session;
	}

	// ---- Player actions ----

	/** {@code /phantasmon trade invite <player>} — resolved through the server's own player list, so only players actually on this server can be picked. */
	public void inviteByName(String playerName) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(playerName);
		if (info == null) {
			chat(Component.translatable("phantasmon.trade.live.error.player_not_found", playerName).withStyle(ChatFormatting.RED));
			return;
		}
		invite(info.getProfile().getId(), info.getProfile().getName());
	}

	/** Keybind: invites whichever player the crosshair is on. */
	public void inviteTargetedPlayer() {
		if (Minecraft.getInstance().crosshairPickEntity instanceof Player target && target != Minecraft.getInstance().player) {
			invite(target.getUUID(), target.getGameProfile().getName());
		} else {
			chat(Component.translatable("phantasmon.trade.live.error.no_target").withStyle(ChatFormatting.RED));
		}
	}

	/** Names suggested by the {@code invite} command: everyone on the server except ourselves. */
	public static List<String> onlinePlayerNames() {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		Player self = Minecraft.getInstance().player;
		if (connection == null) {
			return List.of();
		}
		Collection<PlayerInfo> players = connection.getOnlinePlayers();
		return players.stream()
				.filter(info -> self == null || !info.getProfile().getId().equals(self.getUUID()))
				.map(info -> info.getProfile().getName())
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
	}

	/** Cobblemon's interaction wheel ("Ghost Trade"): invites the player the wheel is open on. */
	public void invitePlayer(UUID targetUuid) {
		invite(targetUuid, null);
	}

	private void invite(UUID targetUuid, String targetName) {
		if (!requireReady()) {
			return;
		}
		if (state != null) {
			chat(Component.translatable("phantasmon.trade.live.error.already_trading").withStyle(ChatFormatting.RED));
			return;
		}
		send("TradeInvite", Map.of("target_uuid", targetUuid));
	}

	/** {@code /phantasmon trade join} — also what the chat [Accept] button runs. */
	public void acceptInvite() {
		respondToInvite(true);
	}

	/** {@code /phantasmon trade decline} — also what the chat [Decline] button runs. */
	public void declineInvite() {
		respondToInvite(false);
	}

	private void respondToInvite(boolean accept) {
		if (!requireReady()) {
			return;
		}
		if (pendingInviteUuid == null) {
			chat(Component.translatable("phantasmon.trade.live.error.no_invite").withStyle(ChatFormatting.RED));
			return;
		}
		UUID inviteUuid = pendingInviteUuid;
		pendingInviteUuid = null;
		send("TradeInviteResponse", Map.of("invite_uuid", inviteUuid, "accept", accept));
		if (!accept) {
			chat(Component.translatable("phantasmon.trade.live.invite.you_declined", pendingInviteFrom));
		}
	}

	/** Left rail click: picks this team member as our offer (spec §7.1). */
	public void selectOffer(PokemonDto pokemon) {
		if (state == null || state.phase() != LiveTradeState.Phase.ACTIVE || pokemon == null
				|| pokemon.uuid().equals(state.ownOffer())) {
			return;
		}
		send("TradeSelectOffer", Map.of("pokemon_uuid", pokemon.uuid()));
	}

	/** ÉCHANGER / PRÊT ✓ toggle (spec §7.3). */
	public void toggleReady() {
		if (state == null || state.phase() != LiveTradeState.Phase.ACTIVE) {
			return;
		}
		if (!state.ownReady() && !state.bothOffersChosen()) {
			state.setLastErrorCode("ERROR_TRADE_OFFERS_INCOMPLETE");
			return;
		}
		send("TradeSetReady", Map.of("ready", !state.ownReady()));
	}

	/** QUITTER confirmed (spec §7.4): cancels the trade for both players. */
	public void leave() {
		if (state != null && state.phase() == LiveTradeState.Phase.ACTIVE) {
			send("TradeLeave", Map.of());
		}
		state = null;
	}

	/** FERMER on the "ÉCHANGE EN COURS" window, once the swap is done. */
	public void closeAfterCompletion() {
		state = null;
		closeTradeScreen();
	}

	/** Trade screen removed for any reason other than our own buttons (another screen forced over it...) — still a "quit" for the partner. */
	public void onScreenRemoved() {
		if (state != null && state.phase() == LiveTradeState.Phase.ACTIVE) {
			leave();
		} else if (state != null && state.phase() == LiveTradeState.Phase.COMPLETED) {
			state = null;
		}
	}

	public LiveTradeState state() {
		return state;
	}

	public String localPlayerName() {
		String name = session.username();
		if (name == null && Minecraft.getInstance().player != null) {
			name = Minecraft.getInstance().player.getGameProfile().getName();
		}
		return name == null ? "?" : name;
	}

	/** Called every client tick — opens the screen outside of any command dispatch (see {@code PokemonCommandHandler.openPc}'s chat-close race). */
	public void tick() {
		if (screenRequested) {
			screenRequested = false;
			if (state != null && state.phase() == LiveTradeState.Phase.ACTIVE) {
				Minecraft.getInstance().setScreen(new PhantasmonTradeScreen(this));
			}
		}
	}

	// ---- Server events ----

	@Override
	public void onLiveTradeMessage(String type, Map<String, Object> data) {
		switch (type) {
			case "TradeInviteReceived" -> onInviteReceived(data);
			case "TradeInviteSent" -> chat(Component.translatable("phantasmon.trade.live.invite.sent", string(data.get("to_name"))));
			case "TradeInviteDeclined" -> chat(Component.translatable("phantasmon.trade.live.invite.declined", string(data.get("by_name")))
					.withStyle(ChatFormatting.GOLD));
			case "TradeSessionStarted" -> onSessionStarted(data);
			case "TradeSessionUpdate" -> {
				if (state != null) {
					state.applyUpdate(data);
				}
			}
			case "TradeSessionCompleted" -> onSessionCompleted(data);
			case "TradeSessionCancelled" -> onSessionCancelled(data);
			case "TradeSessionError" -> onSessionError(string(data.get("error_code")));
			default -> {
				// Unknown live-trade event from a newer backend: ignore.
			}
		}
	}

	@Override
	public void onConnectionLost() {
		pendingInviteUuid = null;
		if (state != null && state.phase() == LiveTradeState.Phase.ACTIVE) {
			state = null;
			closeTradeScreen();
			chat(Component.translatable("phantasmon.trade.live.connection_lost").withStyle(ChatFormatting.RED));
		}
	}

	private void onInviteReceived(Map<String, Object> data) {
		UUID inviteUuid = uuid(data.get("invite_uuid"));
		if (inviteUuid == null) {
			return;
		}
		pendingInviteUuid = inviteUuid;
		pendingInviteFrom = string(data.get("from_name"));
		MutableComponent message = Component.translatable("phantasmon.trade.live.invite.received", pendingInviteFrom)
				.append(" ")
				.append(chatButton("phantasmon.trade.live.invite.accept_button", "/phantasmon trade join", ChatFormatting.GREEN))
				.append(" ")
				.append(chatButton("phantasmon.trade.live.invite.decline_button", "/phantasmon trade decline", ChatFormatting.RED));
		chat(message);
	}

	private void onSessionStarted(Map<String, Object> data) {
		LiveTradeState started = LiveTradeState.fromSessionStarted(data);
		if (started == null) {
			return;
		}
		state = started;
		pendingInviteUuid = null;
		screenRequested = true;
		PokemonDto defaultOffer = started.firstOwnPokemon();
		if (defaultOffer != null) {
			send("TradeSelectOffer", Map.of("pokemon_uuid", defaultOffer.uuid()));
		}
	}

	private void onSessionCompleted(Map<String, Object> data) {
		if (state == null || !state.isSameSession(data)) {
			return;
		}
		state.markCompleted(data);
		PokemonDto given = state.givenPokemon();
		PokemonDto received = state.receivedPokemon();
		chat(Component.translatable("phantasmon.trade.live.completed_chat",
				given == null ? "?" : PhantasmonTradeScreen.displayName(given),
				received == null ? "?" : PhantasmonTradeScreen.displayName(received),
				state.partnerName()).withStyle(ChatFormatting.GREEN));
		if (!(Minecraft.getInstance().screen instanceof PhantasmonTradeScreen)) {
			// Screen already gone (shouldn't happen, but never leave a finished session lingering).
			state = null;
		}
	}

	private void onSessionCancelled(Map<String, Object> data) {
		if (state == null || !state.isSameSession(data)) {
			return;
		}
		state.markCancelled(data);
		String reason = state.cancelReason();
		String partner = state.partnerName();
		state = null;
		closeTradeScreen();
		Component message = switch (reason == null ? "" : reason) {
			case "PARTNER_LEFT" -> Component.translatable("phantasmon.trade.live.cancelled.partner_left", partner);
			case "PARTNER_DISCONNECTED" -> Component.translatable("phantasmon.trade.live.cancelled.partner_disconnected", partner);
			default -> Component.translatable("phantasmon.trade.live.cancelled.error",
					Component.translatable(BackendErrorMessages.translationKey(reason)));
		};
		chat(message.copy().withStyle(ChatFormatting.GOLD));
	}

	private void onSessionError(String errorCode) {
		if (state != null && state.phase() == LiveTradeState.Phase.ACTIVE) {
			state.setLastErrorCode(errorCode);
		} else {
			chat(Component.translatable(BackendErrorMessages.translationKey(errorCode)).withStyle(ChatFormatting.RED));
		}
	}

	// ---- Helpers ----

	private boolean requireReady() {
		if (!session.isAuthenticated()) {
			chat(Component.translatable("phantasmon.error.not_authenticated").withStyle(ChatFormatting.RED));
			return false;
		}
		if (!ghostSession.isConnected()) {
			chat(Component.translatable("phantasmon.ghost.not_connected").withStyle(ChatFormatting.RED));
			return false;
		}
		return true;
	}

	private void send(String type, Map<String, Object> data) {
		if (!ghostSession.send(type, data)) {
			chat(Component.translatable("phantasmon.ghost.not_connected").withStyle(ChatFormatting.RED));
		}
	}

	private static void closeTradeScreen() {
		Minecraft client = Minecraft.getInstance();
		if (client.screen instanceof PhantasmonTradeScreen) {
			client.setScreen(null);
		}
	}

	private static MutableComponent chatButton(String labelKey, String command, ChatFormatting color) {
		return Component.translatable(labelKey).withStyle(color, ChatFormatting.BOLD)
				.withStyle(style -> style
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
	}

	private static void chat(Component message) {
		var player = Minecraft.getInstance().player;
		if (player != null) {
			player.displayClientMessage(message, false);
		}
	}

	private static String string(Object value) {
		return value == null ? "?" : value.toString();
	}

	private static UUID uuid(Object raw) {
		try {
			return raw == null ? null : UUID.fromString(raw.toString());
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
