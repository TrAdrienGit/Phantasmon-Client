package com.mystaria.phantasmon.client.hub;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.ghost.GhostSession;
import com.mystaria.phantasmon.client.network.BackendApiException;
import com.mystaria.phantasmon.client.network.BackendErrorMessages;

/**
 * The client side of the Global Hub (Phantasmon Network, network-cahier-des-charges.md §4.1 and §5, step N3).
 *
 * <ul>
 * <li>Knows the Hub Anchors of the current server and dimension ({@code GET /hub/anchors}, refreshed every 30 s) —
 * shared by the players of that server, never seen from another one (D-30) — and draws their ground square in
 * particles and their name above the centre.</li>
 * <li>Entering any of them <b>invites</b> the player into the Hub (D-29), unless they chose "always here" for that
 * anchor; leaving the cube leaves the Hub.</li>
 * <li>While in the Hub, sends {@code HubMove} (anchor-relative coordinates, height above the local ground) up to
 * 10 times a second, only when something changed.</li>
 * <li>Shows the Hub chat, arrivals and departures in the local chat, and the remote players' avatars
 * ({@link HubAvatars}, step N4) placed in the anchor the player is in the Hub through, with their sent-out Ghosts
 * following them (N5).</li>
 * </ul>
 */
public final class HubController implements HubListener {

	private static final Logger LOG = LoggerFactory.getLogger(HubController.class);
	private static final int ANCHOR_REFRESH_TICKS = 600;
	private static final int MOVE_INTERVAL_TICKS = 2;
	private static final int OUTLINE_INTERVAL_TICKS = 10;
	/** A refused {@code HubJoin} (full, wrong server) only gets an {@code Error}: stop waiting after 5 s. */
	private static final int JOIN_TIMEOUT_TICKS = 100;
	private static final double DRAW_DISTANCE = 48;
	private static final int OUTLINE_COLOR = 0x9B6CFF;
	/** Same rule as the backend: 3 to 32 letters, digits, spaces, - or _, no space at either end. */
	/** Edge of an anchor's cube, as the backend's {@code phantasmon.hub.anchor-size} (checked before posing, D-34). */
	private static final int ANCHOR_SIZE = 21;
	private static final Pattern ANCHOR_NAME = Pattern.compile("[\\p{L}\\p{N}_-][\\p{L}\\p{N} _-]{1,30}[\\p{L}\\p{N}_-]");
	private static final Path AUTO_JOIN_FILE = FabricLoader.getInstance().getConfigDir().resolve("phantasmon-hub-autojoin.txt");

	/** A remote player in the Hub as last reported; {@code state} is null until they move (no avatar until then). */
	public record Member(UUID playerUuid, String username, Map<String, Object> state) {
	}

	private final HubClient hubClient;
	private final AuthSession session;
	private final GhostSession ghostSession;
	private final Set<UUID> autoJoin = new LinkedHashSet<>();
	private final Map<UUID, Member> members = new LinkedHashMap<>();
	private final HubAvatars avatars = new HubAvatars();
	/** The Global Hub's build around each anchor (D-34). */
	private final HubBuilds builds;

	private volatile List<HubAnchorDto> anchors = List.of();
	/** {@code fingerprint|dimension} the anchor list belongs to. */
	private String anchorsKey;
	private int ticksSinceRefresh;
	private int tickCount;
	/** The anchor whose cube the player stands in, or null. */
	private HubAnchorDto currentAnchor;
	/** The anchor the player was invited through and has not answered yet. */
	private HubAnchorDto pendingInvite;
	/** {@code HubJoin} sent through this anchor, waiting for {@code HubJoined}. */
	private HubAnchorDto joining;
	private int joiningSinceTick;
	/** The anchor the player is in the Hub through, or null when out of the Hub. */
	private HubAnchorDto joinedAnchor;
	/** After a lost connection: rejoin through this anchor without asking again (consent was already given). */
	private UUID rejoinAnchorUuid;
	private Map<String, Object> lastSentState;

	public HubController(HubClient hubClient, AuthSession session, GhostSession ghostSession) {
		this.hubClient = hubClient;
		this.session = session;
		this.ghostSession = ghostSession;
		this.builds = new HubBuilds(hubClient, session);
		loadAutoJoin();
	}

	// ---------------------------------------------------------------- tick

	/** Every client tick. */
	public void tick() {
		// The builds stay up even while the backend is unreachable: they only depend on the last anchor list.
		builds.tick();
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || !session.isAuthenticated() || !ghostSession.isConnected()) {
			return;
		}
		tickCount++;
		String key = GhostSession.serverFingerprint() + "|" + dimension(player);
		if (!key.equals(anchorsKey)) {
			// New server or dimension: the backend takes the player out of the Hub itself (SERVER_CHANGED).
			anchorsKey = key;
			anchors = List.of();
			builds.setAnchors(anchors);
			currentAnchor = null;
			pendingInvite = null;
			refreshAnchors();
		} else if (++ticksSinceRefresh >= ANCHOR_REFRESH_TICKS) {
			refreshAnchors();
		}

		HubAnchorDto here = anchorAt(player.position());
		if (!sameAnchor(here, currentAnchor)) {
			if (currentAnchor != null) {
				onExitAnchor(currentAnchor);
			}
			currentAnchor = here;
			if (here != null) {
				onEnterAnchor(here);
			}
		}

		avatars.tickGhosts();
		if (joining != null && tickCount - joiningSinceTick > JOIN_TIMEOUT_TICKS) {
			joining = null;
		}
		if (joinedAnchor != null && tickCount % MOVE_INTERVAL_TICKS == 0) {
			sendMove(player, joinedAnchor);
		}
		if (tickCount % OUTLINE_INTERVAL_TICKS == 0) {
			// Paused (singleplayer menu): particles no longer age, so new ones would pile up and all play at once on
			// resume — the outline waits for the game to run again.
			if (!mc.isPaused()) {
				drawOutlines(mc.level, player);
			}
			avatars.tick(mc.level);
		}
	}

	private void onEnterAnchor(HubAnchorDto anchor) {
		boolean rejoin = anchor.uuid().equals(rejoinAnchorUuid);
		rejoinAnchorUuid = null;
		if (joinedAnchor != null || joining != null) {
			return;
		}
		if (rejoin || autoJoin.contains(anchor.uuid())) {
			join(anchor);
			return;
		}
		pendingInvite = anchor;
		chat(Component.translatable("phantasmon.hub.invite", anchor.name()).withStyle(ChatFormatting.LIGHT_PURPLE)
				.append(" ").append(chatButton("phantasmon.hub.invite.yes", "/phantasmon hub join", ChatFormatting.GREEN))
				.append(" ").append(chatButton("phantasmon.hub.invite.no", "/phantasmon hub decline", ChatFormatting.RED))
				.append(" ").append(chatButton("phantasmon.hub.invite.always", "/phantasmon hub always", ChatFormatting.AQUA)));
	}

	private void onExitAnchor(HubAnchorDto anchor) {
		pendingInvite = null;
		if (sameAnchor(anchor, joinedAnchor) || sameAnchor(anchor, joining)) {
			joining = null;
			ghostSession.send("HubLeave", Map.of());
		}
	}

	private void join(HubAnchorDto anchor) {
		pendingInvite = null;
		if (ghostSession.send("HubJoin", Map.of("anchor_uuid", anchor.uuid()))) {
			joining = anchor;
			joiningSinceTick = tickCount;
		} else {
			chat(Component.translatable("phantasmon.ghost.not_connected"));
		}
	}

	// ---------------------------------------------------------------- movement

	private void sendMove(LocalPlayer player, HubAnchorDto anchor) {
		HubCoordinates coordinates = anchor.coordinates();
		double[] hub = coordinates.toHub(player.getX(), player.getZ());
		Map<String, Object> state = new HashMap<>();
		state.put("x", round(coordinates.clampToSquare(hub[0])));
		state.put("z", round(coordinates.clampToSquare(hub[1])));
		state.put("y_offset", round(Mth.clamp(heightAboveGround(player, coordinates), 0, coordinates.size())));
		state.put("yaw", round(Mth.wrapDegrees(coordinates.toHubYaw(player.getYRot()))));
		state.put("head_yaw", round(Mth.wrapDegrees(coordinates.toHubYaw(player.getYHeadRot()))));
		state.put("pitch", round(player.getXRot()));
		state.put("pose", pose(player));
		state.put("on_ground", player.onGround());
		state.put("skin_parts", skinParts());
		if (!state.equals(lastSentState) && ghostSession.send("HubMove", state)) {
			lastSentState = state;
		}
	}

	/** 0 on the ground; otherwise the distance down to the first block the player could stand on (within the cube). */
	private static double heightAboveGround(LocalPlayer player, HubCoordinates coordinates) {
		if (player.onGround()) {
			return 0;
		}
		ClientLevel level = (ClientLevel) player.level();
		BlockPos feet = player.blockPosition();
		for (int dy = 0; dy <= coordinates.size(); dy++) {
			BlockPos pos = feet.below(dy);
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				double top = pos.getY() + shape.max(Direction.Axis.Y);
				if (top <= player.getY() + 1e-3) {
					return player.getY() - top;
				}
			}
		}
		return player.getY() - coordinates.originY();
	}

	/** The outer skin layers and cape this player shows (Options → Skin Customization), as Minecraft's 7-bit mask. */
	private static int skinParts() {
		int mask = 0;
		for (PlayerModelPart part : PlayerModelPart.values()) {
			if (Minecraft.getInstance().options.isModelPartEnabled(part)) {
				mask |= part.getMask();
			}
		}
		return mask;
	}

	private static String pose(LocalPlayer player) {
		if (player.isFallFlying()) {
			return "FALL_FLYING";
		}
		if (player.isVisuallySwimming()) {
			return "SWIMMING";
		}
		return player.isCrouching() ? "CROUCHING" : "STANDING";
	}

	private static double round(double value) {
		return Math.round(value * 100) / 100.0;
	}

	// ---------------------------------------------------------------- messages

	@Override
	public void onHubMessage(String type, Map<String, Object> data) {
		switch (type) {
			case "HubJoined" -> {
				// A late HubJoined (after the 5 s wait) still counts, through the anchor the player stands in.
				joinedAnchor = joining != null ? joining : currentAnchor;
				joining = null;
				lastSentState = null;
				members.clear();
				avatars.clear();
				if (data.get("members") instanceof List<?> list) {
					for (Object entry : list) {
						if (entry instanceof Map<?, ?> member) {
							Member joined = putMember(member);
							showAvatar(joined);
							if (joined != null && member.get("ghost") instanceof Map<?, ?> ghost) {
								avatars.setGhost(joined.playerUuid(), stringKeys(ghost));
							}
						}
					}
				}
				chat(Component.translatable("phantasmon.hub.joined", members.size()).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			case "HubLeft" -> {
				joinedAnchor = null;
				joining = null;
				members.clear();
				avatars.clear();
				String reason = String.valueOf(data.get("reason"));
				chat(Component.translatable("phantasmon.hub.left." + switch (reason) {
					case "SERVER_CHANGED" -> "server_changed";
					case "ANCHOR_DELETED" -> "anchor_deleted";
					default -> "left";
				}).withStyle(ChatFormatting.LIGHT_PURPLE));
				if ("ANCHOR_DELETED".equals(reason)) {
					refreshAnchors();
				}
			}
			case "HubPlayerEnter" -> {
				Member member = putMember(data);
				showAvatar(member);
				if (member != null) {
					chat(Component.translatable("phantasmon.hub.player_enter", member.username()).withStyle(ChatFormatting.GRAY));
				}
			}
			case "HubPlayerMove" -> {
				UUID playerUuid = uuid(data.get("player_uuid"));
				Member member = playerUuid == null ? null : members.get(playerUuid);
				if (member != null && data.get("state") instanceof Map<?, ?> state) {
					Member moved = new Member(playerUuid, member.username(), stringKeys(state));
					members.put(playerUuid, moved);
					showAvatar(moved);
				}
			}
			case "HubPlayerLeave" -> {
				UUID playerUuid = uuid(data.get("player_uuid"));
				Member member = playerUuid == null ? null : members.remove(playerUuid);
				if (playerUuid != null) {
					avatars.remove(playerUuid);
				}
				if (member != null) {
					chat(Component.translatable("phantasmon.hub.player_leave", member.username()).withStyle(ChatFormatting.GRAY));
				}
			}
			case "HubGhostSpawn" -> {
				UUID playerUuid = uuid(data.get("player_uuid"));
				if (playerUuid != null && members.containsKey(playerUuid)) {
					avatars.setGhost(playerUuid, data);
				}
			}
			case "HubGhostDespawn" -> {
				UUID playerUuid = uuid(data.get("player_uuid"));
				if (playerUuid != null) {
					avatars.setGhost(playerUuid, null);
				}
			}
			case "HubChatMessage" -> chat(Component.translatable("phantasmon.hub.chat.prefix").withStyle(ChatFormatting.LIGHT_PURPLE)
					.append(Component.literal(" <" + data.get("username") + "> ").withStyle(ChatFormatting.WHITE))
					.append(Component.literal(String.valueOf(data.get("message"))).withStyle(ChatFormatting.WHITE)));
			default -> {
				// Messages of later steps are ignored by this version.
			}
		}
	}

	@Override
	public void onConnectionLost() {
		if (joinedAnchor != null) {
			rejoinAnchorUuid = joinedAnchor.uuid();
		}
		joinedAnchor = null;
		joining = null;
		pendingInvite = null;
		currentAnchor = null;
		members.clear();
		avatars.clear();
		lastSentState = null;
	}

	/** Leaving the world: forget everything about this server. */
	public void reset() {
		onConnectionLost();
		rejoinAnchorUuid = null;
		anchors = List.of();
		anchorsKey = null;
		builds.reset();
	}

	private Member putMember(Map<?, ?> data) {
		UUID playerUuid = uuid(data.get("player_uuid"));
		if (playerUuid == null) {
			return null;
		}
		Member member = new Member(playerUuid, String.valueOf(data.get("username")),
				data.get("state") instanceof Map<?, ?> state ? stringKeys(state) : null);
		members.put(playerUuid, member);
		return member;
	}

	private void showAvatar(Member member) {
		if (member != null && member.state() != null && joinedAnchor != null) {
			avatars.update(member.playerUuid(), member.username(), member.state(), joinedAnchor.coordinates());
		}
	}

	/** Remote players currently in the Hub with this client, keyed by player UUID. */
	public Map<UUID, Member> members() {
		return members;
	}

	/** The anchor this client is in the Hub through, or null. */
	public HubAnchorDto joinedAnchor() {
		return joinedAnchor;
	}

	// ---------------------------------------------------------------- commands

	/** {@code /phantasmon hub join} — the chat [Yes] button. */
	public void acceptInvite() {
		if (pendingInvite == null || !sameAnchor(pendingInvite, currentAnchor)) {
			chat(Component.translatable("phantasmon.hub.no_invite"));
			return;
		}
		join(pendingInvite);
	}

	/** {@code /phantasmon hub decline} — the chat [No] button. */
	public void declineInvite() {
		if (pendingInvite == null) {
			chat(Component.translatable("phantasmon.hub.no_invite"));
			return;
		}
		pendingInvite = null;
		chat(Component.translatable("phantasmon.hub.declined").withStyle(ChatFormatting.GRAY));
	}

	/** {@code /phantasmon hub always} — the chat [Always here] button: join now, and from now on without asking. */
	public void acceptAlways() {
		HubAnchorDto anchor = pendingInvite != null ? pendingInvite : currentAnchor;
		if (anchor == null || !sameAnchor(anchor, currentAnchor)) {
			chat(Component.translatable("phantasmon.hub.not_in_anchor"));
			return;
		}
		setAutoJoin(anchor, true);
		if (joinedAnchor == null && joining == null) {
			join(anchor);
		}
	}

	/** {@code /phantasmon hub autojoin on|off} for the anchor the player stands in. */
	public void setAutoJoinHere(boolean enabled) {
		if (currentAnchor == null) {
			chat(Component.translatable("phantasmon.hub.not_in_anchor"));
			return;
		}
		setAutoJoin(currentAnchor, enabled);
		if (enabled && joinedAnchor == null && joining == null) {
			join(currentAnchor);
		}
	}

	/** {@code /phantasmon hub leave}: out of the Hub; stepping out of the cube and back in invites again. */
	public void leave() {
		if (joinedAnchor == null && joining == null) {
			chat(Component.translatable("phantasmon.hub.not_in_hub"));
			return;
		}
		joining = null;
		ghostSession.send("HubLeave", Map.of());
	}

	/** {@code /phantasmon hub chat <message>} and {@code /hc <message>}. */
	public void sendChat(String message) {
		if (joinedAnchor == null) {
			chat(Component.translatable("phantasmon.hub.not_in_hub"));
			return;
		}
		ghostSession.send("HubChat", Map.of("message", message));
	}

	/** {@code /phantasmon hub anchor create <name>}: a cube centred on the player, facing where they look. */
	public void createAnchor(String rawName) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !requireAuthenticated()) {
			return;
		}
		String name = rawName.strip();
		if (!ANCHOR_NAME.matcher(name).matches()) {
			chat(Component.translatable("phantasmon.hub.anchor.invalid_name").withStyle(ChatFormatting.RED));
			return;
		}
		// D-34: the Global Hub's build fills the whole cube, which must be empty (air) where the anchor is posed.
		int inTheWay = HubBuilds.blocksInTheWay((ClientLevel) player.level(),
				HubBuildLayout.of(player.getX(), player.getY(), player.getZ(), 0, ANCHOR_SIZE));
		if (inTheWay > 0) {
			chat(Component.translatable("phantasmon.hub.anchor.not_empty", ANCHOR_SIZE, inTheWay).withStyle(ChatFormatting.RED));
			return;
		}
		HubAnchorCreateRequestDto request = new HubAnchorCreateRequestDto(UUID.randomUUID(), name,
				GhostSession.serverFingerprint(), dimension(player),
				new HubAnchorDto.Origin(player.getX(), player.getY(), player.getZ()), player.getYRot());
		hubClient.create(session.accessToken(), request)
				.thenAccept(anchor -> onClientThread(() -> {
					chat(Component.translatable("phantasmon.hub.anchor.created", anchor.name(), anchor.size())
							.withStyle(ChatFormatting.LIGHT_PURPLE));
					refreshAnchors();
				}))
				.exceptionally(this::reportFailure);
	}

	/** {@code /phantasmon hub anchor info}: the player's own anchor. */
	public void showMyAnchor() {
		if (!requireAuthenticated()) {
			return;
		}
		hubClient.mine(session.accessToken())
				.thenAccept(anchor -> onClientThread(() -> chat(Component.translatable("phantasmon.hub.anchor.info",
						anchor.name(), anchor.dimension(), Math.round(anchor.origin().x()), Math.round(anchor.origin().y()),
						Math.round(anchor.origin().z())))))
				.exceptionally(this::reportFailure);
	}

	/** {@code /phantasmon hub anchor delete}: the player's own anchor. */
	public void deleteMyAnchor() {
		if (!requireAuthenticated()) {
			return;
		}
		hubClient.mine(session.accessToken())
				.thenCompose(anchor -> hubClient.delete(session.accessToken(), anchor.uuid()).thenApply(ignored -> anchor))
				.thenAccept(anchor -> onClientThread(() -> {
					setAutoJoin(anchor, false);
					chat(Component.translatable("phantasmon.hub.anchor.deleted", anchor.name()).withStyle(ChatFormatting.LIGHT_PURPLE));
					refreshAnchors();
				}))
				.exceptionally(this::reportFailure);
	}

	/** {@code /phantasmon hub anchor delete here}: the anchor the player stands in (its creator or an admin). */
	public void deleteAnchorHere() {
		if (!requireAuthenticated()) {
			return;
		}
		HubAnchorDto anchor = currentAnchor;
		if (anchor == null) {
			chat(Component.translatable("phantasmon.hub.not_in_anchor"));
			return;
		}
		hubClient.delete(session.accessToken(), anchor.uuid())
				.thenAccept(ignored -> onClientThread(() -> {
					chat(Component.translatable("phantasmon.hub.anchor.deleted", anchor.name()).withStyle(ChatFormatting.LIGHT_PURPLE));
					refreshAnchors();
				}))
				.exceptionally(this::reportFailure);
	}

	// ---------------------------------------------------------------- anchors

	private void refreshAnchors() {
		ticksSinceRefresh = 0;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !session.isAuthenticated()) {
			return;
		}
		String key = anchorsKey;
		hubClient.list(session.accessToken(), GhostSession.serverFingerprint(), dimension(player))
				.thenAccept(list -> onClientThread(() -> {
					if (key != null && key.equals(anchorsKey)) {
						anchors = List.of(list);
						builds.setAnchors(anchors);
						builds.refreshSchematic();
					}
				}))
				.exceptionally(ex -> {
					LOG.warn("Could not load the Hub Anchors of this server", ex);
					return null;
				});
	}

	private HubAnchorDto anchorAt(Vec3 position) {
		for (HubAnchorDto anchor : anchors) {
			if (anchor.coordinates().contains(position.x, position.y, position.z)) {
				return anchor;
			}
		}
		return null;
	}

	/** The ground square of each nearby anchor's cube, in purple dust, plus a short pillar at each corner. */
	private void drawOutlines(ClientLevel level, LocalPlayer player) {
		if (level == null) {
			return;
		}
		DustParticleOptions dust = new DustParticleOptions(new Vector3f(
				((OUTLINE_COLOR >> 16) & 0xFF) / 255f, ((OUTLINE_COLOR >> 8) & 0xFF) / 255f, (OUTLINE_COLOR & 0xFF) / 255f), 1.2f);
		for (HubAnchorDto anchor : anchors) {
			HubCoordinates c = anchor.coordinates();
			if (player.position().distanceTo(new Vec3(c.originX(), c.originY(), c.originZ())) > DRAW_DISTANCE + c.halfSize()) {
				continue;
			}
			double half = c.halfSize();
			double y = c.originY() + 0.1;
			for (double t = -half; t <= half; t += 1.0) {
				level.addParticle(dust, c.originX() + t, y, c.originZ() - half, 0, 0, 0);
				level.addParticle(dust, c.originX() + t, y, c.originZ() + half, 0, 0, 0);
				level.addParticle(dust, c.originX() - half, y, c.originZ() + t, 0, 0, 0);
				level.addParticle(dust, c.originX() + half, y, c.originZ() + t, 0, 0, 0);
			}
			for (double dy = 0.5; dy <= 3; dy += 0.5) {
				level.addParticle(dust, c.originX() - half, y + dy, c.originZ() - half, 0, 0, 0);
				level.addParticle(dust, c.originX() + half, y + dy, c.originZ() - half, 0, 0, 0);
				level.addParticle(dust, c.originX() - half, y + dy, c.originZ() + half, 0, 0, 0);
				level.addParticle(dust, c.originX() + half, y + dy, c.originZ() + half, 0, 0, 0);
			}
		}
	}

	/** Each nearby anchor's name floating above its centre ({@code WorldRenderEvents.AFTER_ENTITIES}). */
	public void renderWorld(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || anchors.isEmpty()) {
			return;
		}
		Camera camera = context.camera();
		Vec3 cameraPosition = camera.getPosition();
		Font font = mc.font;
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		for (HubAnchorDto anchor : anchors) {
			Vec3 label = new Vec3(anchor.origin().x(), anchor.origin().y() + 3.2, anchor.origin().z());
			if (label.distanceTo(cameraPosition) > DRAW_DISTANCE) {
				continue;
			}
			PoseStack pose = new PoseStack();
			pose.translate(label.x - cameraPosition.x, label.y - cameraPosition.y, label.z - cameraPosition.z);
			pose.mulPose(camera.rotation());
			pose.scale(0.035f, -0.035f, 0.035f);
			Matrix4f matrix = pose.last().pose();
			String title = Component.translatable("phantasmon.hub.anchor.floating", anchor.name()).getString();
			font.drawInBatch(title, -font.width(title) / 2f, 0, 0xFFC9A8FF, false, matrix, buffers,
					Font.DisplayMode.NORMAL, 0x60000000, 0xF000F0);
		}
		buffers.endBatch();
	}

	// ---------------------------------------------------------------- helpers

	private void setAutoJoin(HubAnchorDto anchor, boolean enabled) {
		boolean changed = enabled ? autoJoin.add(anchor.uuid()) : autoJoin.remove(anchor.uuid());
		if (changed) {
			saveAutoJoin();
		}
		chat(Component.translatable(enabled ? "phantasmon.hub.autojoin.on" : "phantasmon.hub.autojoin.off", anchor.name())
				.withStyle(ChatFormatting.GRAY));
	}

	private void loadAutoJoin() {
		try {
			if (Files.exists(AUTO_JOIN_FILE)) {
				for (String line : Files.readAllLines(AUTO_JOIN_FILE)) {
					UUID anchorUuid = uuid(line.strip());
					if (anchorUuid != null) {
						autoJoin.add(anchorUuid);
					}
				}
			}
		} catch (IOException ex) {
			LOG.warn("Could not read {}", AUTO_JOIN_FILE, ex);
		}
	}

	private void saveAutoJoin() {
		try {
			Files.write(AUTO_JOIN_FILE, autoJoin.stream().map(UUID::toString).toList());
		} catch (IOException ex) {
			LOG.warn("Could not write {}", AUTO_JOIN_FILE, ex);
		}
	}

	private boolean requireAuthenticated() {
		if (!session.isAuthenticated()) {
			chat(Component.translatable("phantasmon.error.not_authenticated").withStyle(ChatFormatting.RED));
			return false;
		}
		return true;
	}

	private Void reportFailure(Throwable throwable) {
		Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
		String translationKey = cause instanceof BackendApiException apiException
				? BackendErrorMessages.translationKey(apiException.errorCode())
				: "phantasmon.error.network";
		onClientThread(() -> chat(Component.translatable(translationKey).withStyle(ChatFormatting.RED)));
		return null;
	}

	private static boolean sameAnchor(HubAnchorDto a, HubAnchorDto b) {
		return a == null ? b == null : b != null && a.uuid().equals(b.uuid());
	}

	private static String dimension(LocalPlayer player) {
		return player.level().dimension().location().toString();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> stringKeys(Map<?, ?> map) {
		return (Map<String, Object>) map;
	}

	private static UUID uuid(Object raw) {
		try {
			return raw == null ? null : UUID.fromString(raw.toString());
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static MutableComponent chatButton(String labelKey, String command, ChatFormatting color) {
		return Component.translatable(labelKey).withStyle(color, ChatFormatting.BOLD)
				.withStyle(style -> style
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
	}

	private static void onClientThread(Runnable action) {
		Minecraft.getInstance().execute(action);
	}

	private static void chat(Component message) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.displayClientMessage(message, false);
		}
	}
}
