package com.mystaria.phantasmon.client.battle;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.api.net.NetworkPacket;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.net.messages.client.battle.BattleEndPacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMakeChoicePacket;
import com.cobblemon.mod.common.net.messages.server.battle.BattleSelectActionsPacket;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
import com.mystaria.phantasmon.client.network.BackendErrorMessages;
import com.mystaria.phantasmon.client.pokemon.PokemonDto;

/**
 * Client side of a live Ghost battle (Phase 9) — invitation, role, relay, timer, end.
 *
 * <p><b>Host</b> (picked by the backend, alternating between the two players): runs Cobblemon's battle engine
 * locally ({@link GhostBattles#startHostedBattle}); its own actor feeds this client's Cobblemon UI, the guest's
 * actor is encoded and relayed ({@code BattlePacket}); the guest's choices come back ({@code BattleChoice}) and
 * are applied to the engine; it reports the result. <b>Guest</b>: plays the relayed packets into its own
 * Cobblemon UI and sends its choices to the host. Both build the same world scene ({@link BattleVisuals}).
 *
 * <p><b>Turn timer</b> (Adrien's rule): off by default; either player turns it on for both with
 * {@code /phantasmon battle timer} (or the chat button), it can't be turned off again; after 90 s without a
 * choice an automatic one is played. The host enforces it for both sides (with a short network grace for the
 * guest); each client shows its own countdown in the action bar.
 */
public final class LiveBattleController implements LiveBattleListener {

	private static final Logger LOG = LoggerFactory.getLogger(LiveBattleController.class);
	private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
	private static final long GUEST_GRACE_MILLIS = 3000;

	private final GhostSession ghostSession;
	private final AuthSession session;

	private UUID pendingInviteUuid;
	private String pendingInviteFrom;

	private UUID battleUuid;
	private boolean host;
	private UUID opponentUuid;
	private String opponentName;
	private UUID cobblemonBattleId;
	private boolean timerEnabled;
	private int timerSeconds = 90;
	/** This client's own countdown (both roles), 0 = not waiting on us. */
	private long localDeadline;
	private boolean localAwaitingChoice;
	/** Host only: enforcement deadlines per player uuid. */
	private final Map<UUID, Long> enforcedDeadlines = new HashMap<>();
	private long lastActionBarSecond = -1;

	public LiveBattleController(GhostSession ghostSession, AuthSession session) {
		this.ghostSession = ghostSession;
		this.session = session;
		CobblemonPackets.addDeliveryListener(this::onPacketDeliveredToUi);
		GhostBattles.setLocalChoiceListener(this::onLocalChoiceMade);
	}

	/** Team a player brings (CAD Partie 1 §31): their Ghosts, or a copy of their real Cobblemon party. */
	public enum TeamChoice { GHOST, COBBLEMON }

	// ---- Player actions ----

	public void inviteByName(String playerName) {
		inviteByName(playerName, TeamChoice.GHOST);
	}

	public void inviteByName(String playerName, TeamChoice team) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(playerName);
		if (info == null) {
			chat(Component.translatable("phantasmon.trade.live.error.player_not_found", playerName).withStyle(ChatFormatting.RED));
			return;
		}
		invite(info.getProfile().getId(), team);
	}

	public void inviteTargetedPlayer() {
		if (Minecraft.getInstance().crosshairPickEntity instanceof Player target && target != Minecraft.getInstance().player) {
			invite(target.getUUID(), TeamChoice.GHOST);
		} else {
			chat(Component.translatable("phantasmon.battle.error.no_target").withStyle(ChatFormatting.RED));
		}
	}

	/** Cobblemon's interaction wheel ("Ghost Battle"): invites the player the wheel is open on. */
	public void invitePlayer(UUID targetUuid) {
		invite(targetUuid, TeamChoice.GHOST);
	}

	private void invite(UUID targetUuid, TeamChoice team) {
		if (!requireReady()) {
			return;
		}
		if (battleUuid != null) {
			chat(Component.translatable("phantasmon.battle.error.already_battling").withStyle(ChatFormatting.RED));
			return;
		}
		Map<String, Object> message = withTeam(team);
		if (message == null) {
			return;
		}
		message.put("target_uuid", targetUuid);
		send("BattleInvite", message);
	}

	public void acceptInvite() {
		acceptInvite(TeamChoice.GHOST);
	}

	public void acceptInvite(TeamChoice team) {
		respond(true, team);
	}

	public void declineInvite() {
		respond(false, TeamChoice.GHOST);
	}

	/**
	 * The team fields of an invite / answer: nothing for Ghosts (the backend reads the active team), else
	 * {@code team: COBBLEMON} and a copy of the party. {@code null} (and a chat message) if the party is empty.
	 */
	private static Map<String, Object> withTeam(TeamChoice team) {
		Map<String, Object> message = new java.util.HashMap<>();
		if (team == TeamChoice.COBBLEMON) {
			List<Map<String, Object>> party = CobblemonPartySnapshot.current();
			if (party.isEmpty()) {
				chat(Component.translatable("phantasmon.battle.error.empty_cobblemon_party").withStyle(ChatFormatting.RED));
				return null;
			}
			message.put("team", "COBBLEMON");
			message.put("party", party);
		}
		return message;
	}

	private void respond(boolean accept, TeamChoice team) {
		if (!requireReady()) {
			return;
		}
		if (pendingInviteUuid == null) {
			chat(Component.translatable("phantasmon.battle.error.no_invite").withStyle(ChatFormatting.RED));
			return;
		}
		Map<String, Object> message = accept ? withTeam(team) : new java.util.HashMap<>();
		if (message == null) {
			return; // empty Cobblemon party: the invitation stays open, the player can still answer with Ghosts
		}
		UUID inviteUuid = pendingInviteUuid;
		pendingInviteUuid = null;
		message.put("invite_uuid", inviteUuid);
		message.put("accept", accept);
		send("BattleInviteResponse", message);
		if (!accept) {
			chat(Component.translatable("phantasmon.battle.invite.you_declined", pendingInviteFrom));
		}
	}

	/** {@code /phantasmon battle timer}: turns the 90 s turn timer on for both players, for the rest of the battle. */
	public void enableTimer() {
		if (battleUuid == null) {
			chat(Component.translatable("phantasmon.battle.error.not_battling").withStyle(ChatFormatting.RED));
			return;
		}
		if (timerEnabled) {
			chat(Component.translatable("phantasmon.battle.timer.already_on").withStyle(ChatFormatting.GRAY));
			return;
		}
		send("BattleTimerEnable", Map.of("battle_uuid", battleUuid));
	}

	/** Called every client tick: countdown display and (host) timer enforcement. */
	public void tick() {
		if (battleUuid == null || !timerEnabled) {
			return;
		}
		long now = System.currentTimeMillis();
		if (localDeadline > 0) {
			long remaining = Math.max(0, (localDeadline - now + 999) / 1000);
			if (remaining != lastActionBarSecond && Minecraft.getInstance().player != null) {
				lastActionBarSecond = remaining;
				Minecraft.getInstance().player.displayClientMessage(
						Component.translatable("phantasmon.battle.timer.countdown", remaining)
								.withStyle(remaining <= 10 ? ChatFormatting.RED : ChatFormatting.AQUA), true);
			}
		}
		if (host && cobblemonBattleId != null) {
			for (Map.Entry<UUID, Long> entry : new ArrayList<>(enforcedDeadlines.entrySet())) {
				if (now >= entry.getValue()) {
					enforcedDeadlines.remove(entry.getKey());
					GhostBattles.forceAutomaticChoice(cobblemonBattleId, entry.getKey());
					if (entry.getKey().equals(localUuid())) {
						clearLocalCountdown();
					}
				}
			}
		}
	}

	// ---- Server events ----

	@Override
	public void onLiveBattleMessage(String type, Map<String, Object> data) {
		switch (type) {
			case "BattleInviteReceived" -> onInviteReceived(data);
			case "BattleInviteSent" -> chat(Component.translatable("phantasmon.battle.invite.sent", string(data.get("to_name"))));
			case "BattleInviteDeclined" -> chat(Component.translatable("phantasmon.battle.invite.declined", string(data.get("by_name")))
					.withStyle(ChatFormatting.GOLD));
			case "BattleSessionStarted" -> onSessionStarted(data);
			case "BattlePacket" -> onRelayedPacket(data);
			case "BattleChoice" -> onRelayedChoice(data);
			case "BattleTimerEnabled" -> onTimerEnabled(data);
			case "BattleEnded" -> onEnded(data);
			case "BattleSessionError" -> onSessionError(string(data.get("error_code")));
			default -> {
			}
		}
	}

	@Override
	public void onConnectionLost() {
		pendingInviteUuid = null;
		if (battleUuid != null) {
			endLocally();
			chat(Component.translatable("phantasmon.battle.connection_lost").withStyle(ChatFormatting.RED));
		}
	}

	private void onInviteReceived(Map<String, Object> data) {
		UUID inviteUuid = uuid(data.get("invite_uuid"));
		if (inviteUuid == null) {
			return;
		}
		pendingInviteUuid = inviteUuid;
		pendingInviteFrom = string(data.get("from_name"));
		boolean cobblemon = "COBBLEMON".equals(data.get("from_team"));
		chat(Component.translatable(cobblemon ? "phantasmon.battle.invite.received_cobblemon" : "phantasmon.battle.invite.received", pendingInviteFrom)
				.append(" ")
				.append(chatButton("phantasmon.battle.invite.accept_ghost_button", "/phantasmon battle join", ChatFormatting.GREEN))
				.append(" ")
				.append(chatButton("phantasmon.battle.invite.accept_cobblemon_button", "/phantasmon battle join cobblemon", ChatFormatting.GOLD))
				.append(" ")
				.append(chatButton("phantasmon.trade.live.invite.decline_button", "/phantasmon battle decline", ChatFormatting.RED)));
	}

	private void onSessionStarted(Map<String, Object> data) {
		battleUuid = uuid(data.get("battle_uuid"));
		host = "HOST".equals(data.get("role"));
		opponentUuid = uuid(data.get("opponent_uuid"));
		opponentName = string(data.get("opponent_name"));
		pendingInviteUuid = null;
		timerEnabled = false;
		cobblemonBattleId = null;
		enforcedDeadlines.clear();
		clearLocalCountdown();
		chat(Component.translatable("phantasmon.battle.started_teams", opponentName,
						teamLabel(data.get("own_team_source")), teamLabel(data.get("opponent_team_source")))
				.append(" ")
				.append(chatButton("phantasmon.battle.timer.button", "/phantasmon battle timer", ChatFormatting.AQUA)));
		if (!host) {
			return; // the host's engine will send us the battle through BattlePacket
		}
		UUID self = localUuid();
		String selfName = localName();
		List<PokemonDto> ownTeam = team(data.get("own_team"));
		List<PokemonDto> opponentTeam = team(data.get("opponent_team"));
		UUID currentBattle = battleUuid;
		GhostBattles.startHostedBattle(self, selfName, ownTeam, opponentUuid, opponentName, opponentTeam,
				this::relayToGuest, new GhostBattles.HostCallbacks() {
					@Override
					public void started(UUID id) {
						Minecraft.getInstance().execute(() -> {
							if (currentBattle.equals(battleUuid)) {
								cobblemonBattleId = id;
							}
						});
					}

					@Override
					public void finished(UUID winnerUuid) {
						Map<String, Object> result = new HashMap<>();
						result.put("battle_uuid", currentBattle);
						result.put("winner_uuid", winnerUuid);
						send("BattleResult", result);
					}

					@Override
					public void effect(ActionEffectEvent event) {
						relayEffectToGuest(currentBattle, event);
					}

					@Override
					public void failed() {
						Map<String, Object> result = new HashMap<>();
						result.put("battle_uuid", currentBattle);
						result.put("winner_uuid", null);
						send("BattleResult", result);
						chat(Component.translatable("phantasmon.battle.error.engine").withStyle(ChatFormatting.RED));
					}
				});
	}

	/** Host: one Cobblemon packet addressed to the guest's actor, sent through the backend. Called on the battle thread. */
	private void relayToGuest(NetworkPacket<?> packet) {
		UUID currentBattle = battleUuid;
		if (currentBattle == null) {
			return;
		}
		if (packet instanceof BattleMakeChoicePacket && timerEnabled) {
			Minecraft.getInstance().execute(() -> enforcedDeadlines.put(opponentUuid,
					System.currentTimeMillis() + timerSeconds * 1000L + GUEST_GRACE_MILLIS));
		}
		try {
			CobblemonPackets.Encoded encoded = CobblemonPackets.encode(packet);
			send("BattlePacket", Map.of("battle_uuid", currentBattle, "id", encoded.id(),
					"payload", Base64.getEncoder().encodeToString(encoded.payload())));
		} catch (Exception ex) {
			LOG.error("Cannot relay battle packet {} to the guest", packet.getId(), ex);
		}
	}

	/** Host: a move animation for the guest's client, riding the same relay as the Cobblemon packets (keeps the order). */
	private void relayEffectToGuest(UUID currentBattle, ActionEffectEvent event) {
		send("BattlePacket", Map.of("battle_uuid", currentBattle, "id", ActionEffectEvent.PACKET_ID,
				"payload", Base64.getEncoder().encodeToString(event.toBytes())));
	}

	/** Guest: a packet from the host's engine, played into this client's Cobblemon UI. */
	private void onRelayedPacket(Map<String, Object> data) {
		if (host || battleUuid == null || !battleUuid.equals(uuid(data.get("battle_uuid")))) {
			return;
		}
		String id = string(data.get("id"));
		if (!RelayedPacketPolicy.guestAccepts(id)) {
			LOG.warn("Dropped a non-battle packet relayed by the host: {}", id);
			return;
		}
		try {
			if (ActionEffectEvent.PACKET_ID.equals(id)) {
				ActionEffectPlayer.play(ActionEffectEvent.fromBytes(Base64.getDecoder().decode(string(data.get("payload")))), null);
				return;
			}
			NetworkPacket<?> packet = CobblemonPackets.decode(new CobblemonPackets.Encoded(string(data.get("id")),
					Base64.getDecoder().decode(string(data.get("payload")))));
			if (packet instanceof BattleInitializePacket init) {
				cobblemonBattleId = init.getBattleId();
				GhostBattles.route(cobblemonBattleId, this::sendChoiceToHost);
			}
			CobblemonPackets.dispatchLocally(packet);
		} catch (Exception ex) {
			LOG.error("Cannot play relayed battle packet {}", data.get("id"), ex);
		}
	}

	/** Guest: our choice in Cobblemon's UI, for the host's engine. */
	private void sendChoiceToHost(BattleSelectActionsPacket choice) {
		if (battleUuid == null) {
			return;
		}
		CobblemonPackets.Encoded encoded = CobblemonPackets.encode(choice);
		send("BattleChoice", Map.of("battle_uuid", battleUuid, "id", encoded.id(),
				"payload", Base64.getEncoder().encodeToString(encoded.payload())));
	}

	/** Host: the guest's choice, applied to the engine as the guest. */
	private void onRelayedChoice(Map<String, Object> data) {
		if (!host || battleUuid == null || !battleUuid.equals(uuid(data.get("battle_uuid")))) {
			return;
		}
		try {
			NetworkPacket<?> packet = CobblemonPackets.decode(new CobblemonPackets.Encoded(string(data.get("id")),
					Base64.getDecoder().decode(string(data.get("payload")))));
			if (packet instanceof BattleSelectActionsPacket choice) {
				enforcedDeadlines.remove(opponentUuid);
				GhostBattles.applyChoice(choice, opponentUuid);
			}
		} catch (Exception ex) {
			LOG.error("Cannot apply the guest's battle choice", ex);
		}
	}

	private void onTimerEnabled(Map<String, Object> data) {
		if (battleUuid == null || !battleUuid.equals(uuid(data.get("battle_uuid")))) {
			return;
		}
		timerEnabled = true;
		Object seconds = data.get("seconds");
		timerSeconds = seconds instanceof Number number ? number.intValue() : 90;
		chat(Component.translatable("phantasmon.battle.timer.enabled", string(data.get("by_name")), timerSeconds)
				.withStyle(ChatFormatting.AQUA));
		long deadline = System.currentTimeMillis() + timerSeconds * 1000L;
		if (localAwaitingChoice) {
			localDeadline = deadline;
		}
		if (host && cobblemonBattleId != null) {
			UUID id = cobblemonBattleId;
			for (UUID player : new UUID[] { localUuid(), opponentUuid }) {
				long grace = player.equals(opponentUuid) ? GUEST_GRACE_MILLIS : 0;
				GhostBattles.whenMustChoose(id, player, mustChoose -> {
					if (mustChoose) {
						Minecraft.getInstance().execute(() -> enforcedDeadlines.put(player, deadline + grace));
					}
				});
			}
		}
	}

	private void onEnded(Map<String, Object> data) {
		if (battleUuid == null || !battleUuid.equals(uuid(data.get("battle_uuid")))) {
			return;
		}
		String reason = string(data.get("reason"));
		UUID winner = uuid(data.get("winner_uuid"));
		endLocally();
		Component message;
		if ("PARTNER_DISCONNECTED".equals(reason)) {
			message = Component.translatable("phantasmon.battle.ended.disconnected").withStyle(ChatFormatting.GOLD);
		} else if ("BACKEND_LOST".equals(reason)) {
			// The backend is stopping (CAD Partie 1 §44): draw, no winner.
			message = Component.translatable("phantasmon.battle.ended.backend_lost").withStyle(ChatFormatting.GOLD);
		} else if (winner == null) {
			message = Component.translatable("phantasmon.battle.ended.draw").withStyle(ChatFormatting.GOLD);
		} else if (winner.equals(localUuid())) {
			message = Component.translatable("FORFEIT".equals(reason) ? "phantasmon.battle.ended.won_forfeit" : "phantasmon.battle.ended.won")
					.withStyle(ChatFormatting.GREEN);
		} else {
			message = Component.translatable("phantasmon.battle.ended.lost").withStyle(ChatFormatting.RED);
		}
		chat(message);
	}

	private void onSessionError(String errorCode) {
		// Commands already check "not in a battle" locally, so from the backend this only means the engine's last
		// packets (or the guest's last choice) crossed the end of the battle — nothing for the player to read.
		if ("ERROR_BATTLE_NOT_IN_BATTLE".equals(errorCode)) {
			LOG.debug("Battle message arrived after the battle ended");
			return;
		}
		chat(Component.translatable(BackendErrorMessages.translationKey(errorCode)).withStyle(ChatFormatting.RED));
	}

	/** Stops whatever is still running for the current battle on this client and forgets it. */
	private void endLocally() {
		UUID id = cobblemonBattleId;
		if (id != null) {
			GhostBattles.unroute(id);
			if (host) {
				GhostBattles.stop(id);
			}
			var clientBattle = CobblemonClient.INSTANCE.getBattle();
			if (clientBattle != null && id.equals(clientBattle.getBattleId())) {
				CobblemonPackets.dispatchLocally(new BattleEndPacket());
			}
		}
		BattleVisuals.clear();
		battleUuid = null;
		cobblemonBattleId = null;
		opponentUuid = null;
		timerEnabled = false;
		enforcedDeadlines.clear();
		clearLocalCountdown();
	}

	/** Every battle packet reaching this client's Cobblemon UI (both roles): tracks when we must choose. */
	private void onPacketDeliveredToUi(NetworkPacket<?> packet) {
		if (battleUuid == null) {
			return;
		}
		if (packet instanceof BattleMakeChoicePacket) {
			localAwaitingChoice = true;
			if (timerEnabled) {
				localDeadline = System.currentTimeMillis() + timerSeconds * 1000L;
				if (host) {
					enforcedDeadlines.put(localUuid(), localDeadline);
				}
			}
		}
	}

	/** Host or guest: the local player made a choice (called from the choice routes). */
	void onLocalChoiceMade() {
		clearLocalCountdown();
		if (host) {
			enforcedDeadlines.remove(localUuid());
		}
	}

	private void clearLocalCountdown() {
		localAwaitingChoice = false;
		localDeadline = 0;
		lastActionBarSecond = -1;
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

	private UUID localUuid() {
		Player player = Minecraft.getInstance().player;
		return player != null ? player.getUUID() : session.playerUuid();
	}

	private String localName() {
		String name = session.username();
		if (name == null && Minecraft.getInstance().player != null) {
			name = Minecraft.getInstance().player.getGameProfile().getName();
		}
		return name == null ? "?" : name;
	}

	private static List<PokemonDto> team(Object raw) {
		List<PokemonDto> team = new ArrayList<>();
		if (raw instanceof List<?> list) {
			for (Object entry : list) {
				PokemonDto pokemon = GSON.fromJson(GSON.toJsonTree(entry), PokemonDto.class);
				if (pokemon != null) {
					team.add(pokemon);
				}
			}
		}
		team.sort((a, b) -> Integer.compare(a.teamSlot() == null ? 99 : a.teamSlot(), b.teamSlot() == null ? 99 : b.teamSlot()));
		return team;
	}

	private static Component teamLabel(Object source) {
		return Component.translatable("COBBLEMON".equals(source) ? "phantasmon.battle.team.cobblemon" : "phantasmon.battle.team.ghost");
	}

	private static MutableComponent chatButton(String labelKey, String command, ChatFormatting color) {
		return Component.translatable(labelKey).withStyle(color, ChatFormatting.BOLD)
				.withStyle(style -> style
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
	}

	private static void chat(Component message) {
		Minecraft.getInstance().execute(() -> {
			var player = Minecraft.getInstance().player;
			if (player != null) {
				player.displayClientMessage(message, false);
			}
		});
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
