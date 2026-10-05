package com.mystaria.phantasmon.client.battle;

import java.util.List;
import java.util.Random;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Staged camera during a Ghost battle (TODO-20, Adrien 2026-10-05): the match is filmed.
 *
 * <ul>
 *   <li><b>Choosing</b> (players pick their moves): a sequence of shots round the field — its centre is halfway
 *   between the two active Pokémon — picked at random every few seconds: slow orbit, low dolly, high crane, over a
 *   trainer's shoulder, close-up on one Pokémon; each one blends in or cuts.</li>
 *   <li><b>Action</b> (a move plays): behind the attacker, panning onto the target; a close-up for a move on itself.
 *   Send-outs and faints get a close-up too.</li>
 * </ul>
 *
 * The player takes their camera back, and gives it again, with the battle camera key (works over Cobblemon's battle
 * screen too). Applied by {@code CameraMixin} after {@link BattleCinematic}'s intro shots. Client thread only.
 */
public final class BattleCameraDirector {

	private static final float BLEND_SECONDS = 2.0f;
	private static final float ACTION_HOLD_SECONDS = 3.2f;
	private static final float FOCUS_HOLD_SECONDS = 2.6f;

	private enum Kind { ORBIT, DOLLY, CRANE, SHOULDER, CLOSE_UP, SWEEP, LOW_ANGLE, SPIRAL }

	/** One shot: where the camera is {@code t} seconds after it began. */
	private interface Shot {
		BattleCinematic.CameraPose pose(Field field, float t);
	}

	/** The battlefield this frame. */
	private record Field(Vec3 local, Vec3 opponent, Vec3 center, Vec3 axis, Vec3 side, double gap, Entity localMon, Entity opponentMon) {
	}

	private static final Random RANDOM = new Random();

	private static boolean battleActive;
	private static boolean enabled = true;

	private static Shot shot;
	private static long shotStart;
	private static float shotLength;
	private static Shot previous;
	private static long previousStart;
	private static long blendStart;
	private static float blendLength;
	private static Kind lastKind;
	/** Until then, an action / focus shot holds before the choosing shots come back. */
	private static long holdUntil;

	private BattleCameraDirector() {
	}

	// =====================================================================
	// Lifecycle and events
	// =====================================================================

	/** Battle scene built (its first packet): the director takes over once the send-out shots are over. */
	public static void start() {
		battleActive = true;
		shot = null;
		previous = null;
		holdUntil = 0;
	}

	public static void stop() {
		battleActive = false;
		shot = null;
		previous = null;
	}

	/** The battle camera key: player's own camera ⇄ staged camera. */
	public static void toggle() {
		enabled = !enabled;
		if (enabled) {
			shot = null; // a fresh shot, cut in
		}
		var player = Minecraft.getInstance().player;
		if (player != null) {
			player.displayClientMessage(Component.translatable(enabled ? "phantasmon.battle.camera.on" : "phantasmon.battle.camera.off")
					.withStyle(ChatFormatting.AQUA), true);
		}
	}

	public static boolean active() {
		return battleActive && enabled;
	}

	/** Whether a Ghost battle is being filmed (the toggle key only acts then). */
	public static boolean inBattle() {
		return battleActive;
	}

	/** Players have to choose again: back to the choosing shots, once any action shot is over. */
	public static void onChoosing() {
		holdUntil = Math.min(holdUntil, System.currentTimeMillis() + 600);
	}

	/** A move animation starts ({@link ActionEffectPlayer}): film it. */
	public static void onAction(List<String> users, List<String> targets) {
		if (!battleActive || users == null || users.isEmpty()) {
			return;
		}
		String user = users.get(0);
		String target = targets == null || targets.isEmpty() ? null : targets.get(0);
		if (target == null || target.equals(user)) {
			cutTo(closeUp(user), ACTION_HOLD_SECONDS, true);
		} else {
			cutTo(actionShot(user, target), ACTION_HOLD_SECONDS, true);
		}
	}

	/** A Pokémon comes in or faints ({@link BattleVisuals}): a close-up. */
	public static void onFocus(String pnx) {
		if (battleActive && pnx != null) {
			cutTo(closeUp(pnx), FOCUS_HOLD_SECONDS, false);
		}
	}

	// =====================================================================
	// Camera (CameraMixin)
	// =====================================================================

	public static BattleCinematic.CameraPose cameraPose(Camera camera, float partialTick) {
		if (!active()) {
			return null;
		}
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			return null;
		}
		Field field = field(partialTick);
		if (field == null) {
			return null;
		}
		long now = System.currentTimeMillis();
		if (shot == null || (now >= holdUntil && (now - shotStart) / 1000f >= shotLength)) {
			nextChoosingShot(now);
		}
		BattleCinematic.CameraPose pose = shot.pose(field, (now - shotStart) / 1000f);
		if (previous != null && now - blendStart < blendLength * 1000) {
			float k = (now - blendStart) / (blendLength * 1000f);
			float s = k * k * (3 - 2 * k);
			BattleCinematic.CameraPose from = previous.pose(field, (now - previousStart) / 1000f);
			pose = new BattleCinematic.CameraPose(from.position().lerp(pose.position(), s),
					Mth.rotLerp(s, from.yaw(), pose.yaw()), Mth.lerp(s, from.pitch(), pose.pitch()), true);
		}
		return unclip(level, pose, field.center().add(0, 0.8, 0));
	}

	// =====================================================================
	// Shots
	// =====================================================================

	private static void nextChoosingShot(long now) {
		Kind kind;
		do {
			kind = Kind.values()[RANDOM.nextInt(Kind.values().length)];
		} while (kind == lastKind);
		lastKind = kind;
		Shot next = switch (kind) {
			case ORBIT -> orbit(RANDOM.nextFloat() * 360f, RANDOM.nextBoolean() ? 1 : -1, 2.2 + RANDOM.nextDouble() * 2.0);
			case DOLLY -> dolly(RANDOM.nextBoolean() ? 1 : -1);
			case CRANE -> crane(RANDOM.nextBoolean() ? 1 : -1);
			case SHOULDER -> shoulder(RANDOM.nextBoolean());
			case CLOSE_UP -> RANDOM.nextBoolean() ? closeUpOf(true) : closeUpOf(false);
			case SWEEP -> sweep(RANDOM.nextBoolean() ? 1 : -1, RANDOM.nextBoolean() ? 1 : -1);
			case LOW_ANGLE -> lowAngle(RANDOM.nextBoolean(), RANDOM.nextBoolean() ? 1 : -1);
			case SPIRAL -> spiral(RANDOM.nextFloat() * 360f, RANDOM.nextBoolean() ? 1 : -1);
		};
		boolean cut = shot == null || RANDOM.nextFloat() < 0.3f;
		switchTo(next, 5f + RANDOM.nextFloat() * 5f, cut, now);
	}

	private static void cutTo(Shot next, float hold, boolean cut) {
		long now = System.currentTimeMillis();
		switchTo(next, hold, cut, now);
		holdUntil = now + (long) (hold * 1000);
	}

	private static void switchTo(Shot next, float length, boolean cut, long now) {
		if (!cut && shot != null) {
			previous = shot;
			previousStart = shotStart;
			blendStart = now;
			blendLength = BLEND_SECONDS;
		} else {
			previous = null;
		}
		shot = next;
		shotStart = now;
		shotLength = length;
	}

	/** Slow circle round the field's centre. */
	private static Shot orbit(float startDegrees, int direction, double height) {
		return (field, t) -> {
			double radius = 3.5 + field.gap() * 0.75;
			double angle = Math.toRadians(startDegrees + direction * 9.0 * t);
			Vec3 position = field.center().add(Math.cos(angle) * radius, height + field.gap() * 0.12, Math.sin(angle) * radius);
			return lookAt(position, field.center().add(0, 0.6, 0));
		};
	}

	/** Low travelling shot along the side of the field, tracking its centre. */
	private static Shot dolly(int sideSign) {
		return (field, t) -> {
			double reach = 1.0 + field.gap() * 0.55;
			double along = Mth.clamp(-reach + t * reach * 0.22, -reach, reach);
			Vec3 position = field.center().add(field.side().scale(sideSign * (3.0 + field.gap() * 0.65)))
					.add(field.axis().scale(along)).add(0, 0.9, 0);
			return lookAt(position, field.center().add(0, 0.7, 0));
		};
	}

	/** High wide shot slowly coming down. */
	private static Shot crane(int sideSign) {
		return (field, t) -> {
			double top = 4.0 + field.gap() * 0.6;
			double bottom = 3.0 + field.gap() * 0.25;
			double height = Math.max(bottom, top - t * (top - bottom) / 8.0);
			Vec3 position = field.center().add(field.side().scale(sideSign * (2.0 + field.gap() * 0.4))).add(0, height, 0);
			return lookAt(position, field.center());
		};
	}

	/** Behind one side's Pokémon, over its shoulder, on the other one. */
	private static Shot shoulder(boolean localSide) {
		return (field, t) -> {
			Vec3 from = localSide ? field.local() : field.opponent();
			Vec3 to = localSide ? field.opponent() : field.local();
			Vec3 forward = horizontal(to.subtract(from));
			Vec3 right = new Vec3(-forward.z, 0, forward.x);
			double back = 4.4 - Math.min(0.9, t * 0.12);
			Vec3 position = from.subtract(forward.scale(back)).add(right.scale(2.5)).add(0, 2.3, 0);
			return lookAt(position, to.add(0, 0.8, 0));
		};
	}

	/** Diagonal pass across the field, from one end to the other, tracking its centre. */
	private static Shot sweep(int sideSign, int direction) {
		return (field, t) -> {
			double reach = 3.0 + field.gap() * 0.6;
			float k = Mth.clamp(t / 9f, 0f, 1f);
			double along = direction * Mth.lerp(k, -reach, reach);
			double across = sideSign * Mth.lerp(k, reach * 0.9, reach * 0.35);
			Vec3 position = field.center().add(field.axis().scale(along)).add(field.side().scale(across)).add(0, 2.4 + field.gap() * 0.1, 0);
			return lookAt(position, field.center().add(0, 0.6, 0));
		};
	}

	/** Close to the ground beside one side's Pokémon, looking up across the field, slowly arcing. */
	private static Shot lowAngle(boolean localSide, int sideSign) {
		return (field, t) -> {
			Vec3 from = localSide ? field.local() : field.opponent();
			Vec3 to = localSide ? field.opponent() : field.local();
			Vec3 forward = horizontal(to.subtract(from));
			Vec3 right = new Vec3(-forward.z, 0, forward.x);
			double arc = Math.toRadians(sideSign * (20 + t * 4));
			double distance = 2.0 + field.gap() * 0.15;
			Vec3 offset = right.scale(Math.cos(arc) * distance).add(forward.scale(-Math.sin(arc) * distance * 0.6));
			return lookAt(from.add(offset).add(0, 0.35, 0), to.add(0, 1.4, 0));
		};
	}

	/** Orbit closing in while rising. */
	private static Shot spiral(float startDegrees, int direction) {
		return (field, t) -> {
			float k = Mth.clamp(t / 10f, 0f, 1f);
			double radius = (3.5 + field.gap() * 0.75) * Mth.lerp(k, 1.3f, 0.75f);
			double angle = Math.toRadians(startDegrees + direction * 14.0 * t);
			double height = Mth.lerp(k, 1.0f, 4.5f) + field.gap() * 0.1;
			Vec3 position = field.center().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
			return lookAt(position, field.center().add(0, 0.6, 0));
		};
	}

	private static Shot closeUpOf(boolean localSide) {
		return (field, t) -> closeUpPose(localSide ? field.local() : field.opponent(),
				localSide ? field.opponent() : field.local(), localSide ? field.localMon() : field.opponentMon(), t);
	}

	/** Front three-quarter close-up of the Pokémon at {@code pnx}, slow push-in. */
	private static Shot closeUp(String pnx) {
		return (field, t) -> {
			Entity entity = BattleVisuals.entityAt(pnx);
			if (entity == null) {
				return lookAt(field.center().add(field.side().scale(6)).add(0, 2, 0), field.center());
			}
			Vec3 at = entity.position();
			Vec3 other = at.distanceToSqr(field.local()) < at.distanceToSqr(field.opponent()) ? field.opponent() : field.local();
			return closeUpPose(at, other, entity, t);
		};
	}

	private static BattleCinematic.CameraPose closeUpPose(Vec3 at, Vec3 facing, Entity entity, float t) {
		double size = entity == null ? 1.0 : Math.max(entity.getBbHeight(), entity.getBbWidth());
		Vec3 forward = horizontal(facing.subtract(at));
		Vec3 right = new Vec3(-forward.z, 0, forward.x);
		double distance = (2.6 + size * 1.6) * (1.0 - Math.min(0.15, t * 0.04));
		Vec3 position = at.add(forward.scale(distance)).add(right.scale(distance * 0.45)).add(0, 0.6 + size * 0.5, 0);
		return lookAt(position, at.add(0, size * 0.55, 0));
	}

	/** Behind the attacker, panning from it onto its target. */
	private static Shot actionShot(String userPnx, String targetPnx) {
		return (field, t) -> {
			Entity user = BattleVisuals.entityAt(userPnx);
			Entity target = BattleVisuals.entityAt(targetPnx);
			if (user == null || target == null) {
				return lookAt(field.center().add(field.side().scale(6)).add(0, 2, 0), field.center());
			}
			Vec3 from = user.position();
			Vec3 to = target.position();
			Vec3 forward = horizontal(to.subtract(from));
			Vec3 right = new Vec3(-forward.z, 0, forward.x);
			double size = Math.max(user.getBbHeight(), user.getBbWidth());
			Vec3 position = from.subtract(forward.scale(2.4 + size)).add(right.scale(1.6 + size * 0.5)).add(0, 1.2 + size * 0.6, 0);
			float pan = Mth.clamp((t - 0.3f) / 1.2f, 0f, 1f);
			float ease = pan * pan * (3 - 2 * pan);
			Vec3 look = from.add(0, user.getBbHeight() * 0.6, 0).lerp(to.add(0, target.getBbHeight() * 0.5, 0), 0.35 + 0.5 * ease);
			return lookAt(position, look);
		};
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	/** The two active Pokémon (or, failing that, the trainers) and the field they define. */
	private static Field field(float partialTick) {
		Entity localMon = BattleVisuals.activeEntity(true);
		Entity opponentMon = BattleVisuals.activeEntity(false);
		Vec3 local = localMon != null ? localMon.getPosition(partialTick) : BattleVisuals.trainerPosition(true);
		Vec3 opponent = opponentMon != null ? opponentMon.getPosition(partialTick) : BattleVisuals.trainerPosition(false);
		if (local == null || opponent == null) {
			return null;
		}
		Vec3 axis = horizontal(opponent.subtract(local));
		Vec3 side = new Vec3(-axis.z, 0, axis.x);
		return new Field(local, opponent, local.add(opponent).scale(0.5), axis, side,
				Math.sqrt(local.distanceToSqr(opponent)), localMon, opponentMon);
	}

	/** If the camera ended up inside a block, slide it toward what it looks at until it's free. */
	private static BattleCinematic.CameraPose unclip(ClientLevel level, BattleCinematic.CameraPose pose, Vec3 target) {
		Vec3 position = pose.position();
		for (int i = 0; i < 24 && blocked(level, position); i++) {
			position = position.lerp(target, 0.12).add(0, 0.15, 0);
		}
		return position == pose.position() ? pose : lookAt(position, target);
	}

	private static boolean blocked(ClientLevel level, Vec3 position) {
		BlockPos pos = BlockPos.containing(position);
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	private static BattleCinematic.CameraPose lookAt(Vec3 position, Vec3 target) {
		Vec3 d = target.subtract(position);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		return new BattleCinematic.CameraPose(position, yaw, pitch, true);
	}

	private static Vec3 horizontal(Vec3 vector) {
		Vec3 flat = new Vec3(vector.x, 0, vector.z);
		return flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
	}
}
