package com.mystaria.phantasmon.client.ghost;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.fabricmc.loader.api.FabricLoader;

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

	/** The one currently-constructed instance (there's only ever one, see {@link com.mystaria.phantasmon.client.PhantasmonClient}) — kept so the static {@link #setFingerprintOverride(String)} debug hook can force an immediate re-{@code JoinServerGroup} without needing a full reconnect. */
	private static GhostSession activeInstance;

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
		activeInstance = this;
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

	/**
	 * Forces an immediate re-{@code JoinServerGroup} with whatever
	 * {@link #serverFingerprint()} currently resolves to — used by
	 * {@link #setFingerprintOverride(String)} so changing the debug fingerprint
	 * takes effect right away instead of only on the next fresh connection
	 * (Adrien: 2026-09-29, first attempt required a full world
	 * quit-and-rejoin since {@link #joinedGroup} only ever sends once per
	 * connection). {@code PresenceService} on the backend just replaces the
	 * player's presence entry on a repeat join, so re-sending is always safe.
	 */
	private void rejoinGroupNow() {
		if (!connected) {
			return;
		}
		Player player = Minecraft.getInstance().player;
		if (player != null) {
			joinServerGroup(player);
			joinedGroup = true;
		}
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

	/**
	 * One plain-text file under the mod's config folder holding the override
	 * value (absent/empty = no override) — deliberately not a JSON config, this
	 * is a throwaway test knob, not a real user-facing setting.
	 */
	private static final Path OVERRIDE_FILE = FabricLoader.getInstance()
			.getConfigDir().resolve("phantasmon-fingerprint-override.txt");

	/**
	 * Temporary test-only override for {@link #serverFingerprint()} — see
	 * {@link #setFingerprintOverride(String)}. {@code null} means "use the real
	 * computed value" (the default, and the only thing that should ever run in
	 * production). Loaded once from {@link #OVERRIDE_FILE} on class init so it
	 * survives a full game relaunch, not just a world rejoin — see
	 * {@link #loadPersistedOverride()}.
	 */
	private static String fingerprintOverride = loadPersistedOverride();

	/**
	 * Debug hook for {@code /phantasmon debug fingerprint <value>} (Adrien:
	 * 2026-09-29, first real 2-client test). Real bug found: the LAN host's own
	 * client always computes {@code "singleplayer"} ({@link Minecraft#isLocalServer()}
	 * is true for whoever opened the world to LAN), while a guest connecting to
	 * that same session computes a hash of whatever address it typed to connect
	 * — the two can never match, even though both are genuinely in the same
	 * session, so {@code PresenceService} never groups them together. This is
	 * NOT a production bug: a real dedicated server has no "host" client at all,
	 * every player computes the same IP-based hash. It's purely an artifact of
	 * using "Open to LAN" as a stand-in for a dedicated server during testing.
	 * This override lets both testers manually agree on one literal string
	 * instead of waiting on a real dedicated server — remove once no longer
	 * needed for testing, same pattern as the earlier temporary
	 * {@code iconanchor} debug command.
	 *
	 * <p>Persisted to disk (Adrien: 2026-09-29, so it applies automatically on
	 * every future login/reconnect without retyping the command each time) —
	 * still fully opt-in: nothing is ever written unless this command is
	 * explicitly run at least once, and running it with no value both clears
	 * the in-memory override and deletes the file, restoring normal behavior
	 * for good.
	 */
	public static void setFingerprintOverride(String value) {
		fingerprintOverride = value == null || value.isBlank() ? null : value;
		try {
			if (fingerprintOverride == null) {
				Files.deleteIfExists(OVERRIDE_FILE);
			} else {
				Files.writeString(OVERRIDE_FILE, fingerprintOverride);
			}
		} catch (IOException ex) {
			LOG.warn("Failed to persist fingerprint override to {}", OVERRIDE_FILE, ex);
		}
		if (activeInstance != null) {
			activeInstance.rejoinGroupNow();
		}
	}

	public static String getFingerprintOverride() {
		return fingerprintOverride;
	}

	private static String loadPersistedOverride() {
		try {
			if (Files.exists(OVERRIDE_FILE)) {
				String value = Files.readString(OVERRIDE_FILE).strip();
				return value.isEmpty() ? null : value;
			}
		} catch (IOException ex) {
			LOG.warn("Failed to load persisted fingerprint override from {}", OVERRIDE_FILE, ex);
		}
		return null;
	}

	private static String serverFingerprint() {
		if (fingerprintOverride != null) {
			return fingerprintOverride;
		}
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
