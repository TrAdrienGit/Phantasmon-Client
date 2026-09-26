package com.mystaria.phantasmon.client.ghost;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.network.BackendConfig;
import com.mystaria.phantasmon.client.network.BackendErrorMessages;
import com.mystaria.phantasmon.client.network.PhantasmonWebSocketClient;
import com.mystaria.phantasmon.client.trade.TradeNotificationListener;

/**
 * Orchestrates the client's WebSocket presence connection and Ghost Entity
 * lifecycle (CAD Partie 2 §4/§7/§8, client Phase 7). Starts once auth
 * succeeds (needs the JWT), not merely on world join. Position/heartbeat run
 * on the same ~1s cadence as {@code PositionUpdate} (CAD §4) — that single
 * message doubles as the Ghost's movement update server-side (no separate
 * "move" message, see the backend's {@code PhantasmonWebSocketHandler}).
 */
public final class GhostSession {

	private static final Logger LOG = LoggerFactory.getLogger(GhostSession.class);
	private static final long TICK_INTERVAL_SECONDS = 1;

	private final PhantasmonWebSocketClient webSocketClient;
	private final GhostEntityManager entityManager;
	private final AuthSession authSession;

	private ScheduledExecutorService scheduler;
	private UUID activeGhostPokemonUuid;
	/** Only for {@link #onClientTick()}'s dimension-change detection — do not reuse this for "have I joined the group yet" (that was a real bug: {@code onClientTick()} runs ~20x/s and sets this almost immediately, long before the 1s {@link #tick()} scheduler's own check could ever see it as unset). Use {@link #joinedGroup} for that. */
	private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lastKnownDimension;
	private volatile boolean joinedGroup;
	private boolean connected;
	private TradeNotificationListener tradeListener = new TradeNotificationListener() {
		@Override
		public void onTradeProposed(Map<String, Object> data) {
		}

		@Override
		public void onTradeAccepted(Map<String, Object> data) {
		}

		@Override
		public void onTradeCancelled(Map<String, Object> data) {
		}
	};

	public GhostSession(AuthSession authSession) {
		this.webSocketClient = new PhantasmonWebSocketClient();
		this.entityManager = new GhostEntityManager();
		this.authSession = authSession;
	}

	/** Trade notifications (CAD Phase 8) ride the same presence WebSocket this class owns. */
	public void setTradeNotificationListener(TradeNotificationListener tradeListener) {
		this.tradeListener = tradeListener;
	}

	public synchronized void start() {
		if (connected) {
			return;
		}
		LOG.info("Connecting Ghost presence WebSocket to {}", BackendConfig.BASE_URL);
		webSocketClient.connect(BackendConfig.BASE_URL, authSession.accessToken(), new PhantasmonWebSocketClient.Listener() {
			@Override
			public void onMessage(String type, Map<String, Object> data) {
				handleMessage(type, data);
			}

			@Override
			public void onClose() {
				LOG.info("Ghost presence WebSocket closed");
				connected = false;
			}
		}).thenRun(() -> {
			LOG.info("Ghost presence WebSocket connected");
			connected = true;
		}).exceptionally(ex -> {
			LOG.warn("Ghost presence WebSocket failed to connect", ex);
			report("phantasmon.ghost.connection_failed");
			connected = false;
			return null;
		});

		scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "phantasmon-ghost-session");
			thread.setDaemon(true);
			return thread;
		});
		scheduler.scheduleAtFixedRate(this::tick, TICK_INTERVAL_SECONDS, TICK_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	public synchronized void stop() {
		if (!connected && scheduler == null) {
			return;
		}
		connected = false;
		if (scheduler != null) {
			scheduler.shutdownNow();
			scheduler = null;
		}
		if (activeGhostPokemonUuid != null) {
			webSocketClient.send("RecallGhost", Map.of());
			activeGhostPokemonUuid = null;
		}
		webSocketClient.close();
		entityManager.despawnAll();
		lastKnownDimension = null;
		joinedGroup = false;
	}

	public void sendOut(UUID pokemonUuid) {
		if (!connected) {
			report("phantasmon.ghost.not_connected");
			return;
		}
		activeGhostPokemonUuid = pokemonUuid;
		webSocketClient.send("SendOutGhost", Map.of("pokemon_uuid", pokemonUuid));
		report("phantasmon.ghost.sendout_sent");
	}

	public void recall() {
		if (!connected) {
			report("phantasmon.ghost.not_connected");
			return;
		}
		activeGhostPokemonUuid = null;
		webSocketClient.send("RecallGhost", Map.of());
		report("phantasmon.ghost.recall_sent");
	}

	public boolean hasActiveGhost() {
		return activeGhostPokemonUuid != null;
	}

	/** Called every client tick (cheap early-return when not connected) — detects dimension change and death, both of which auto-recall the Ghost (CAD Partie 2 §8). */
	public void onClientTick() {
		if (!connected) {
			return;
		}
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		if (player.isDeadOrDying() && activeGhostPokemonUuid != null) {
			recall();
			return;
		}
		var currentDimension = player.level().dimension();
		if (lastKnownDimension != null && !lastKnownDimension.equals(currentDimension) && activeGhostPokemonUuid != null) {
			recall();
		}
		lastKnownDimension = currentDimension;
	}

	private void tick() {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (player == null) {
			return;
		}
		if (!joinedGroup) {
			joinServerGroup(player);
			joinedGroup = true;
		}
		Vec3 position = player.position();
		webSocketClient.send("PositionUpdate", Map.of(
				"x", position.x, "y", position.y, "z", position.z,
				"dimension", player.level().dimension().location().toString()));
		webSocketClient.send("Heartbeat", Map.of());
	}

	private void joinServerGroup(Player player) {
		webSocketClient.send("JoinServerGroup", Map.of(
				"server_fingerprint", serverFingerprint(),
				"dimension", player.level().dimension().location().toString()));
	}

	private void handleMessage(String type, Map<String, Object> data) {
		switch (type) {
			case "GhostEntitySpawn" -> Minecraft.getInstance().execute(() -> {
				UUID ownerUuid = uuid(data.get("player_uuid"));
				Vec3 position = position(data.get("position"));
				String species = (String) data.get("species");
				if (ownerUuid != null && position != null && species != null) {
					entityManager.spawn(ownerUuid, species, (String) data.get("form"),
							Boolean.TRUE.equals(data.get("is_shiny")), (int) number(data.get("level")),
							position.x, position.y, position.z);
					if (ownerUuid.equals(authSession.playerUuid())) {
						report("phantasmon.ghost.sendout_confirmed", species);
					}
				}
			});
			case "GhostEntityMove" -> Minecraft.getInstance().execute(() -> {
				UUID ownerUuid = uuid(data.get("player_uuid"));
				Vec3 position = position(data.get("position"));
				if (ownerUuid != null && position != null) {
					entityManager.move(ownerUuid, position.x, position.y, position.z);
				}
			});
			case "GhostEntityDespawn" -> Minecraft.getInstance().execute(() -> {
				UUID ownerUuid = uuid(data.get("player_uuid"));
				if (ownerUuid != null) {
					entityManager.despawn(ownerUuid);
					if (ownerUuid.equals(authSession.playerUuid())) {
						report("phantasmon.ghost.recall_confirmed");
					}
				}
			});
			case "Error" -> report(BackendErrorMessages.translationKey(String.valueOf(data.get("error_code"))));
			case "TradeProposed" -> Minecraft.getInstance().execute(() -> tradeListener.onTradeProposed(data));
			case "TradeAccepted" -> Minecraft.getInstance().execute(() -> tradeListener.onTradeAccepted(data));
			case "TradeCancelled" -> Minecraft.getInstance().execute(() -> tradeListener.onTradeCancelled(data));
			default -> {
				// HeartbeatAck: nothing to do for V1.
			}
		}
	}

	private static void report(String translationKey, Object... args) {
		Minecraft.getInstance().execute(() -> {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				client.player.displayClientMessage(Component.translatable(translationKey, args), false);
			}
		});
	}

	private static UUID uuid(Object raw) {
		try {
			return raw == null ? null : UUID.fromString(raw.toString());
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static Vec3 position(Object raw) {
		if (!(raw instanceof Map<?, ?> map)) {
			return null;
		}
		Map<String, Object> position = (Map<String, Object>) map;
		return new Vec3(number(position.get("x")), number(position.get("y")), number(position.get("z")));
	}

	private static double number(Object value) {
		return value instanceof Number number ? number.doubleValue() : 0;
	}

	private static String serverFingerprint() {
		Minecraft client = Minecraft.getInstance();
		if (client.isLocalServer()) {
			return "singleplayer";
		}
		ServerData server = client.getCurrentServer();
		String ip = server == null ? "unknown" : server.ip;
		return sha256Hex(ip);
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
