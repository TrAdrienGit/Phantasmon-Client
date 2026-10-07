package com.mystaria.phantasmon.client.hub;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;

import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Pose;

/**
 * A player from another Minecraft server, seen in the Global Hub (Phantasmon Network, step N4). Purely client-side:
 * added to the local {@link ClientLevel} only, the Minecraft server never hears of it, no damage, not targetable.
 *
 * <p>Collides like a real player: Minecraft players do not block each other, they push each other. On the client,
 * the local player pushes against every pushable {@code Player} in its box ({@code LivingEntity.pushEntities}), so the
 * avatar stays pushable and keeps its physics on; it is never moved by the push itself (its position comes from the
 * Hub), the local player is. The remote player is pushed the same way by this player's avatar on their own client.
 *
 * <p>Built on {@link RemotePlayer} for the vanilla player model and its walking animation (driven by
 * {@link #lerpTo}). A fake player has no {@code PlayerInfo} in the connection, so the skin is supplied by
 * {@link HubAvatars} once fetched from Mojang ({@link DefaultPlayerSkin} meanwhile). {@code RemotePlayer} never
 * recomputes its pose itself: {@link #applyPose} sets it from the Hub state.
 *
 * <p>The entity's own UUID is derived from the player's ({@link #entityUuid}): the real player may be loaded in the
 * same world (same server, nearby), and a level refuses a second entity with an existing UUID.
 */
public final class HubAvatarEntity extends RemotePlayer {

	private static final int FALL_FLYING_FLAG = 7;

	private final Supplier<PlayerSkin> skin;

	public HubAvatarEntity(ClientLevel level, GameProfile profile, Supplier<PlayerSkin> skin) {
		super(level, profile);
		setUUID(entityUuid(profile.getId()));
		this.skin = skin;
		setSkinParts(ALL_SKIN_PARTS);
	}

	/** Cape, jacket, sleeves, pant legs and hat: every outer layer shown until the remote player says otherwise. */
	static final int ALL_SKIN_PARTS = 0x7F;

	/** The outer skin layers and cape the remote player shows (their {@code PlayerModelPart} mask). */
	void setSkinParts(int mask) {
		getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) (mask & ALL_SKIN_PARTS));
	}

	@Override
	public PlayerSkin getSkin() {
		PlayerSkin loaded = skin.get();
		return loaded != null ? loaded : DefaultPlayerSkin.get(getGameProfile().getId());
	}

	static UUID entityUuid(UUID playerUuid) {
		return UUID.nameUUIDFromBytes(("phantasmon-hub-avatar:" + playerUuid).getBytes(StandardCharsets.UTF_8));
	}

	/** Pseudo + a discreet « [Hub] »: never the server they come from (network-cahier-des-charges.md §5.5). */
	@Override
	public Component getDisplayName() {
		return Component.literal(getGameProfile().getName())
				.append(Component.translatable("phantasmon.hub.avatar.tag").withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return true;
	}

	void applyPose(String pose, boolean onGround) {
		setOnGround(onGround);
		setShiftKeyDown("CROUCHING".equals(pose));
		setSwimming("SWIMMING".equals(pose));
		setSharedFlag(FALL_FLYING_FLAG, "FALL_FLYING".equals(pose));
		setPose(switch (pose) {
			case "CROUCHING" -> Pose.CROUCHING;
			case "SWIMMING" -> Pose.SWIMMING;
			case "FALL_FLYING" -> Pose.FALL_FLYING;
			default -> Pose.STANDING;
		});
	}
}
