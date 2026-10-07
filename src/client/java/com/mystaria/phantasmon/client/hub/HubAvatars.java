package com.mystaria.phantasmon.client.hub;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.mystaria.phantasmon.client.ghost.GhostEntityManager;

/**
 * The avatars of the remote players in the Global Hub (Phantasmon Network, network-cahier-des-charges.md §5.5, step
 * N4): one {@link HubAvatarEntity} per member who has moved at least once, placed in the local anchor through
 * {@link HubCoordinates}. Positions come at up to 10 Hz and are smoothed by the entity's own interpolation (like
 * vanilla network players). Height: the local ground under the avatar, near the anchor's floor, plus the
 * {@code y_offset} the remote client measured above its own ground. Client thread only.
 *
 * <p>The backend sends every member, players of this very server included (D-30): a player of this server may be in
 * the Hub through another of its anchors. An avatar shows where its player stands <i>in the Hub</i>, not in this
 * world: it is hidden only when the real player is loaded here and stands inside the very anchor this client entered
 * the Hub through — then the avatar's spot <i>is</i> the real player's, seen twice otherwise. (A first version
 * compared the avatar's computed spot with the real player within 2 blocks in 3D; the avatar's height comes from a
 * ground search that a slab or a fence in the anchor lifts, so same-anchor players got both bodies and both Ghosts.)
 * Checked on each update and every half second; the last state is kept to show the avatar back.
 *
 * <p><b>Ghosts in the Hub (N5)</b>: a member's sent-out Ghost follows their avatar. A separate
 * {@link GhostEntityManager} — the one the server group uses for real players — is keyed by the avatar's
 * <i>entity</i> UUID ({@link HubAvatarEntity#entityUuid}), so its follow, roam, send-out and recall behaviour runs on the
 * avatar exactly as on a real owner. A Hub Ghost only exists while its avatar is shown: a hidden avatar (the real
 * player stands there) has its real Ghost shown by the server group already.
 */
public final class HubAvatars {

	private static final Logger LOG = LoggerFactory.getLogger(HubAvatars.class);
	/** Ticks over which a position update is interpolated (2 ticks between updates, a little slack). */
	private static final int LERP_STEPS = 3;
	/** How far above / below the anchor's floor the local ground is searched for. */
	private static final int GROUND_SEARCH = 4;
	/** Client-only entity ids, far from the server's own (which count up from 0). */
	private static final AtomicInteger NEXT_ID = new AtomicInteger(-2_000_000);

	/** Skins by player, kept for the session so a player stepping in and out is not fetched again. */
	private static final Map<UUID, PlayerSkin> SKINS = new ConcurrentHashMap<>();
	private static final Set<UUID> SKINS_LOADING = ConcurrentHashMap.newKeySet();

	/** How far R reaches an avatar, like Cobblemon's own reach on a player. */
	private static final double AIM_REACH = 10.0;

	/** The client's one instance (owned by {@code HubController}), for the static lookups below. */
	private static HubAvatars instance;

	private final Map<UUID, HubAvatarEntity> avatars = new HashMap<>();
	/** Last state of every member, shown or not, so a hidden avatar can come back without waiting for a move. */
	private final Map<UUID, Known> known = new HashMap<>();
	/** Rendering data of each member's sent-out Ghost ({@code HubGhostSpawn}), shown or not. */
	private final Map<UUID, Map<String, Object>> ghosts = new HashMap<>();
	private final GhostEntityManager hubGhosts = new GhostEntityManager();

	private record Known(String username, Map<String, Object> state, HubCoordinates anchor) {
	}

	public HubAvatars() {
		instance = this;
	}

	/**
	 * The player to show for {@code playerUuid}: the real one when loaded in this world, else their Hub avatar, else
	 * null. Milestone 2: a battle or trade partner met in the Hub plays on another server — battle placement, camera,
	 * lobby and intro stand them on their avatar.
	 */
	public static Player playerOrAvatar(ClientLevel level, UUID playerUuid) {
		if (level == null || playerUuid == null) {
			return null;
		}
		Player real = level.getPlayerByUUID(playerUuid);
		if (real != null) {
			return real;
		}
		HubAvatarEntity avatar = instance == null ? null : instance.avatars.get(playerUuid);
		return avatar != null && !avatar.isRemoved() && avatar.level() == level ? avatar : null;
	}

	/** The avatar the player aims at within reach, blocks in the way excepted; null if none. */
	public static HubAvatarEntity aimedAvatar(LocalPlayer player) {
		if (instance == null || instance.avatars.isEmpty()) {
			return null;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 view = player.getViewVector(1f);
		Vec3 reach = eye.add(view.scale(AIM_REACH));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, reach,
				player.getBoundingBox().expandTowards(view.scale(AIM_REACH)).inflate(1.0),
				entity -> entity instanceof HubAvatarEntity && !entity.isRemoved(), AIM_REACH * AIM_REACH);
		if (hit == null) {
			return null;
		}
		HitResult block = player.pick(AIM_REACH, 1f, false);
		if (block.getType() == HitResult.Type.BLOCK && block.getLocation().distanceToSqr(eye) < hit.getLocation().distanceToSqr(eye)) {
			return null;
		}
		return (HubAvatarEntity) hit.getEntity();
	}

	/** Creates or moves {@code playerUuid}'s avatar to its Hub {@code state}, seen from {@code anchor}. */
	public void update(UUID playerUuid, String username, Map<String, Object> state, HubCoordinates anchor) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || state == null || anchor == null) {
			return;
		}
		known.put(playerUuid, new Known(username, state, anchor));
		double[] local = anchor.toLocal(number(state.get("x")), number(state.get("z")));
		double y = groundY(level, anchor, local[0], local[1]) + Mth.clamp(number(state.get("y_offset")), 0, anchor.size());
		if (isDouble(level, playerUuid, anchor)) {
			if (avatars.containsKey(playerUuid)) {
				LOG.info("Hub avatar of {} hidden: the real player stands in this anchor", username);
			}
			despawn(playerUuid);
			return;
		}
		float yaw = anchor.toLocalYaw((float) number(state.get("yaw")));
		float headYaw = anchor.toLocalYaw((float) number(state.get("head_yaw")));
		float pitch = (float) number(state.get("pitch"));
		String pose = state.get("pose") instanceof String text ? text : "STANDING";
		boolean onGround = !Boolean.FALSE.equals(state.get("on_ground"));
		int skinParts = state.get("skin_parts") instanceof Number parts ? parts.intValue() : HubAvatarEntity.ALL_SKIN_PARTS;

		HubAvatarEntity avatar = avatars.get(playerUuid);
		if (avatar == null || avatar.isRemoved() || avatar.level() != level) {
			avatar = new HubAvatarEntity(level, new GameProfile(playerUuid, username), () -> SKINS.get(playerUuid));
			avatar.setId(NEXT_ID.getAndDecrement());
			avatar.moveTo(local[0], y, local[1], yaw, pitch);
			avatar.setYHeadRot(headYaw);
			avatar.setYBodyRot(yaw);
			level.addEntity(avatar);
			avatars.put(playerUuid, avatar);
			LOG.info("Hub avatar of {} shown", username);
			loadSkin(playerUuid);
			showGhost(playerUuid, avatar);
		} else {
			avatar.lerpTo(local[0], y, local[1], yaw, pitch, LERP_STEPS);
			avatar.lerpHeadTo(headYaw, LERP_STEPS);
		}
		avatar.applyPose(pose, onGround);
		avatar.setSkinParts(skinParts);
	}

	/** Every half second: hide the avatars whose real player now stands on them, show back the others. */
	public void tick(ClientLevel level) {
		if (level == null) {
			return;
		}
		for (Map.Entry<UUID, Known> entry : Map.copyOf(known).entrySet()) {
			HubAvatarEntity avatar = avatars.get(entry.getKey());
			if (avatar != null && !avatar.isRemoved()) {
				if (isDouble(level, entry.getKey(), entry.getValue().anchor())) {
					LOG.info("Hub avatar of {} hidden: the real player stands in this anchor", entry.getValue().username());
					despawn(entry.getKey());
				}
			} else {
				Known last = entry.getValue();
				update(entry.getKey(), last.username(), last.state(), last.anchor());
			}
		}
	}

	/**
	 * The real player is loaded here and stands inside {@code anchor} — the one this client is in the Hub through — so
	 * they are in the Hub through it too and their avatar would stand exactly where they do: a double.
	 */
	private static boolean isDouble(ClientLevel level, UUID playerUuid, HubCoordinates anchor) {
		var real = level.getPlayerByUUID(playerUuid);
		return real != null && !(real instanceof HubAvatarEntity) && anchor.contains(real.getX(), real.getY(), real.getZ());
	}

	public void remove(UUID playerUuid) {
		known.remove(playerUuid);
		ghosts.remove(playerUuid);
		despawn(playerUuid);
	}

	/** {@code HubGhostSpawn} ({@code ghost} = its data) or {@code HubGhostDespawn} ({@code null}) for a member. */
	public void setGhost(UUID playerUuid, Map<String, Object> ghost) {
		if (ghost == null) {
			ghosts.remove(playerUuid);
			hubGhosts.despawn(HubAvatarEntity.entityUuid(playerUuid));
			return;
		}
		ghosts.put(playerUuid, ghost);
		HubAvatarEntity avatar = avatars.get(playerUuid);
		if (avatar != null && !avatar.isRemoved()) {
			showGhost(playerUuid, avatar);
		}
	}

	/** Every client tick: Hub Ghosts follow and roam around their avatars. */
	public void tickGhosts() {
		hubGhosts.tick();
	}

	private void showGhost(UUID playerUuid, HubAvatarEntity avatar) {
		Map<String, Object> ghost = ghosts.get(playerUuid);
		if (ghost == null || !(ghost.get("species") instanceof String species)) {
			return;
		}
		hubGhosts.spawn(avatar.getUUID(), species, ghost.get("form") instanceof String form ? form : null,
				Boolean.TRUE.equals(ghost.get("is_shiny")), ghost.get("gender"), (int) number(ghost.get("level")),
				ghost.get("nickname") instanceof String nickname ? nickname : null,
				avatar.getX(), avatar.getY(), avatar.getZ());
	}

	private void despawn(UUID playerUuid) {
		hubGhosts.despawn(HubAvatarEntity.entityUuid(playerUuid));
		HubAvatarEntity avatar = avatars.remove(playerUuid);
		if (avatar != null && !avatar.isRemoved()) {
			ClientLevel level = Minecraft.getInstance().level;
			if (level != null && avatar.level() == level) {
				level.removeEntity(avatar.getId(), Entity.RemovalReason.DISCARDED);
			} else {
				avatar.discard();
			}
		}
	}

	public void clear() {
		known.clear();
		ghosts.clear();
		hubGhosts.despawnAll();
		for (UUID playerUuid : Set.copyOf(avatars.keySet())) {
			despawn(playerUuid);
		}
	}

	/** The top of the first solid block near the anchor's floor at ({@code x}, {@code z}); the floor itself if none. */
	private static double groundY(ClientLevel level, HubCoordinates anchor, double x, double z) {
		int floor = Mth.floor(anchor.originY());
		int blockX = Mth.floor(x);
		int blockZ = Mth.floor(z);
		for (int blockY = floor + GROUND_SEARCH; blockY >= floor - GROUND_SEARCH; blockY--) {
			BlockPos pos = new BlockPos(blockX, blockY, blockZ);
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				return blockY + shape.max(Direction.Axis.Y);
			}
		}
		return anchor.originY();
	}

	/** Mojang profile (with its textures) then the skin itself, both off the client thread; default skin meanwhile. */
	private static void loadSkin(UUID playerUuid) {
		if (SKINS.containsKey(playerUuid) || !SKINS_LOADING.add(playerUuid)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		CompletableFuture.supplyAsync(() -> mc.getMinecraftSessionService().fetchProfile(playerUuid, false), Util.backgroundExecutor())
				.thenCompose(result -> result == null
						? CompletableFuture.<PlayerSkin>completedFuture(null)
						: mc.getSkinManager().getOrLoad(((ProfileResult) result).profile()))
				.whenComplete((skin, error) -> {
					SKINS_LOADING.remove(playerUuid);
					if (skin != null) {
						SKINS.put(playerUuid, skin);
					} else if (error != null) {
						LOG.warn("Could not load the skin of Hub player {}", playerUuid, error);
					}
				});
	}

	private static double number(Object value) {
		return value instanceof Number number ? number.doubleValue() : 0;
	}
}
