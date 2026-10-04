package com.mystaria.phantasmon.client.battle;

import java.util.UUID;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.mystaria.phantasmon.client.gui.PhantasmonBattleIntroScreen;

/**
 * Battle launch cinematic (Adrien 2026-10-04, after Pokémon X/Y's trainer battle intro), in two parts:
 *
 * <ol>
 *   <li><b>Intro</b>, played by both players as soon as the battle session starts ({@link #INTRO_END} ms, the
 *   same on both clients — the host only starts its engine afterwards, the guest holds relayed packets until
 *   then): "eyes meet" side shot of the two players with a "!" over the opponent, diamond wipe to black, the
 *   challenger panel ({@link PhantasmonBattleIntroScreen}), white flash.</li>
 *   <li><b>Send-outs</b>, when the battle's first packet arrives: the opponent's Pokémon comes out first, filmed
 *   from the front, then ours, over our shoulder; the camera then blends back to the player's own view.</li>
 * </ol>
 *
 * The camera is moved by {@code CameraMixin} (after vanilla and other camera mods have placed it), the HUD is
 * hidden while a shot plays. Purely local and cosmetic; client thread only.
 */
public final class BattleCinematic {

	// ---- Intro timeline (ms since the session started) ----
	public static final long EYES_MEET_END = 2000;
	public static final long WIPE_END = 2600;
	public static final long PANEL_START = 2750;
	public static final long FLASH_START = 5200;
	public static final long INTRO_END = 5500;
	private static final long EXCLAMATION_START = 250;
	private static final long HUD_FLASH_MS = 500;

	// ---- Send-out timeline (ms since the battle's first packet) ----
	/** Delays of each side's first send-out, in seconds — the opponent's first, then ours (read by {@link BattleVisuals}). */
	public static final float OPPONENT_SEND_OUT_DELAY = 0.3f;
	public static final float LOCAL_SEND_OUT_DELAY = 2.1f;
	private static final long SHOT_OPPONENT_END = 2100;
	private static final long SHOT_SELF_END = 4000;
	private static final long BLEND_END = 4700;

	private static long introStart = -1;
	private static UUID opponentUuid;
	private static String opponentName = "?";
	private static int opponentTeamSize;
	private static boolean sendOutPending;
	private static long sendOutStart = -1;
	private static Vec3 localSpot;
	private static Vec3 opponentSpot;
	private static Boolean savedHideGui;
	private static boolean exclamationSoundPlayed;

	private BattleCinematic() {
	}

	public record CameraPose(Vec3 position, float yaw, float pitch, boolean detached) {
	}

	// =====================================================================
	// Lifecycle
	// =====================================================================

	/** Battle session started: plays the intro (both roles). */
	public static void startIntro(UUID opponent, String name, int teamSize) {
		introStart = System.currentTimeMillis();
		opponentUuid = opponent;
		opponentName = name == null ? "?" : name;
		opponentTeamSize = Mth.clamp(teamSize, 0, 6);
		sendOutPending = true;
		sendOutStart = -1;
		exclamationSoundPlayed = false;
		Minecraft.getInstance().setScreen(new PhantasmonBattleIntroScreen());
	}

	/** Local clock time at which the intro is over (engine start / end of the guest's packet hold). */
	public static long introEndsAt() {
		return introStart < 0 ? 0 : introStart + INTRO_END;
	}

	public static boolean introPlaying() {
		return introStart >= 0 && introElapsed() < INTRO_END;
	}

	public static long introElapsed() {
		return introStart < 0 ? Long.MAX_VALUE : System.currentTimeMillis() - introStart;
	}

	public static UUID opponentUuid() {
		return opponentUuid;
	}

	public static String opponentName() {
		return opponentName;
	}

	public static int opponentTeamSize() {
		return opponentTeamSize;
	}

	/**
	 * The battle's first packet: if an intro just played, the send-outs get staged and filmed. Returns whether
	 * they should be ({@link BattleVisuals} then delays each side's first send-out).
	 */
	public static boolean beginSendOuts(Vec3 localPokemonSpot, Vec3 opponentPokemonSpot) {
		if (!sendOutPending || localPokemonSpot == null || opponentPokemonSpot == null) {
			sendOutPending = false;
			return false;
		}
		sendOutPending = false;
		localSpot = localPokemonSpot;
		opponentSpot = opponentPokemonSpot;
		sendOutStart = System.currentTimeMillis();
		return true;
	}

	/** Battle over / connection lost: everything back to normal at once. */
	public static void stop() {
		introStart = -1;
		sendOutPending = false;
		sendOutStart = -1;
		applyHideGui(false);
		if (Minecraft.getInstance().screen instanceof PhantasmonBattleIntroScreen) {
			Minecraft.getInstance().setScreen(null);
		}
	}

	/** Every client tick: HUD visibility and the end of each part. */
	public static void tick() {
		long now = System.currentTimeMillis();
		if (introStart >= 0 && now - introStart >= INTRO_END + HUD_FLASH_MS && !sendOutPending) {
			introStart = -1;
		}
		if (sendOutStart >= 0 && now - sendOutStart >= BLEND_END) {
			sendOutStart = -1;
		}
		if (introPlaying() && !exclamationSoundPlayed && introElapsed() >= EXCLAMATION_START) {
			exclamationSoundPlayed = true;
			play(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.8f);
		}
		applyHideGui(introPlaying() || (sendOutStart >= 0 && now - sendOutStart < SHOT_SELF_END));
	}

	private static void applyHideGui(boolean hide) {
		var options = Minecraft.getInstance().options;
		if (hide && savedHideGui == null) {
			savedHideGui = options.hideGui;
			options.hideGui = true;
		} else if (!hide && savedHideGui != null) {
			options.hideGui = savedHideGui;
			savedHideGui = null;
		}
	}

	public static void play(SoundEvent sound, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	// =====================================================================
	// Camera (CameraMixin)
	// =====================================================================

	/** Where the camera should be this frame, or null to leave it where vanilla (and other mods) put it. */
	public static CameraPose cameraPose(Camera camera, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		Player self = mc.player;
		ClientLevel level = mc.level;
		if (self == null || level == null) {
			return null;
		}
		if (introPlaying()) {
			return eyesMeetShot(level, self, partialTick, introElapsed());
		}
		if (sendOutStart < 0) {
			return null;
		}
		long t = System.currentTimeMillis() - sendOutStart;
		if (t < SHOT_OPPONENT_END) {
			return opponentPokemonShot(t / (float) SHOT_OPPONENT_END);
		}
		CameraPose shoulder = shoulderShot(self, partialTick, Math.min(1f, (t - SHOT_OPPONENT_END) / (float) (SHOT_SELF_END - SHOT_OPPONENT_END)));
		if (t < SHOT_SELF_END) {
			return shoulder;
		}
		// Blend back to the player's own view.
		float k = (t - SHOT_SELF_END) / (float) (BLEND_END - SHOT_SELF_END);
		float s = k * k * (3 - 2 * k);
		Vec3 position = shoulder.position().lerp(camera.getPosition(), s);
		return new CameraPose(position, Mth.rotLerp(s, shoulder.yaw(), camera.getYRot()),
				Mth.lerp(s, shoulder.pitch(), camera.getXRot()), k < 0.9f);
	}

	/** Side shot of the two trainers facing each other (or of us alone if the opponent isn't loaded), slow push-in. */
	private static CameraPose eyesMeetShot(ClientLevel level, Player self, float partialTick, long elapsed) {
		Vec3 selfEye = self.getEyePosition(partialTick);
		Player opponent = opponentUuid == null ? null : level.getPlayerByUUID(opponentUuid);
		float push = 1.08f - 0.08f * Math.min(1f, elapsed / (float) EYES_MEET_END);
		if (opponent == null) {
			Vec3 forward = horizontal(self.getViewVector(partialTick));
			Vec3 position = selfEye.add(forward.scale(3.2 * push)).add(0, 0.2, 0);
			return lookAt(position, selfEye.add(0, -0.3, 0), true);
		}
		Vec3 opponentEye = opponent.getEyePosition(partialTick);
		Vec3 middle = selfEye.add(opponentEye).scale(0.5);
		Vec3 axis = horizontal(opponentEye.subtract(selfEye));
		double gap = axis.length();
		Vec3 direction = gap < 1.0E-3 ? horizontal(self.getViewVector(partialTick)) : axis.scale(1 / gap);
		Vec3 side = new Vec3(-direction.z, 0, direction.x);
		double distance = Mth.clamp(gap * 0.85, 3.0, 10.0) * push;
		Vec3 position = middle.add(side.scale(distance)).add(0, 0.5, 0);
		if (solid(level, position)) {
			Vec3 other = middle.add(side.scale(-distance)).add(0, 0.5, 0);
			position = solid(level, other) ? middle.add(side.scale(2.0)).add(0, 0.5, 0) : other;
		}
		return lookAt(position, middle.add(0, -0.2, 0), true);
	}

	/** Facing the opponent's Pokémon as it comes out, from our side of the field, slow push-in. */
	private static CameraPose opponentPokemonShot(float progress) {
		Vec3 towardUs = horizontal(localSpot.subtract(opponentSpot));
		Vec3 direction = towardUs.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : towardUs.normalize();
		Vec3 side = new Vec3(-direction.z, 0, direction.x);
		double distance = 4.6 - 1.0 * progress;
		Vec3 position = opponentSpot.add(direction.scale(distance)).add(side.scale(1.1)).add(0, 1.4, 0);
		return lookAt(position, opponentSpot.add(0, 0.8, 0), true);
	}

	/** Behind us, over the right shoulder, looking where our Pokémon comes out. */
	private static CameraPose shoulderShot(Player self, float partialTick, float progress) {
		Vec3 feet = self.getPosition(partialTick);
		Vec3 toward = horizontal(opponentSpot.subtract(feet));
		Vec3 forward = toward.lengthSqr() < 1.0E-4 ? horizontal(self.getViewVector(partialTick)) : toward.normalize();
		Vec3 right = new Vec3(-forward.z, 0, forward.x);
		double back = 2.6 - 0.4 * progress;
		Vec3 position = feet.subtract(forward.scale(back)).add(right.scale(1.0)).add(0, 2.0, 0);
		return lookAt(position, localSpot.add(forward.scale(1.5)).add(0, 0.6, 0), true);
	}

	private static CameraPose lookAt(Vec3 position, Vec3 target, boolean detached) {
		Vec3 d = target.subtract(position);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		return new CameraPose(position, yaw, pitch, detached);
	}

	private static boolean solid(ClientLevel level, Vec3 position) {
		BlockPos pos = BlockPos.containing(position);
		return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
	}

	private static Vec3 horizontal(Vec3 vector) {
		Vec3 flat = new Vec3(vector.x, 0, vector.z);
		return flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	// =====================================================================
	// World and HUD overlays
	// =====================================================================

	/** The "!" popping over the opponent's head during the eyes-meet shot. */
	public static void renderWorld(WorldRenderContext context) {
		long elapsed = introElapsed();
		if (!introPlaying() || elapsed < EXCLAMATION_START || elapsed >= EYES_MEET_END || opponentUuid == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Player opponent = mc.level == null ? null : mc.level.getPlayerByUUID(opponentUuid);
		if (opponent == null) {
			return;
		}
		float pop = Math.min(1f, (elapsed - EXCLAMATION_START) / 180f);
		float scale = pop < 1f ? 1.35f * pop : 1f + 0.06f * (float) Math.sin((elapsed - EXCLAMATION_START) / 90.0);
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true);
		Vec3 head = opponent.getPosition(partialTick).add(0, opponent.getBbHeight() + 0.75, 0);
		Camera camera = context.camera();
		Vec3 cameraPosition = camera.getPosition();

		PoseStack pose = new PoseStack();
		pose.translate(head.x - cameraPosition.x, head.y - cameraPosition.y, head.z - cameraPosition.z);
		pose.mulPose(camera.rotation());
		float size = 0.06f * scale;
		pose.scale(size, -size, size);
		Font font = mc.font;
		String mark = "!";
		Matrix4f matrix = pose.last().pose();
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		font.drawInBatch(" " + mark + " ", -font.width(" " + mark + " ") / 2f, -4, 0xFFE8362F, false, matrix, buffers,
				Font.DisplayMode.SEE_THROUGH, 0xF0FFFFFF, 0xF000F0);
		buffers.endBatch();
	}

	/** White flash fading out over the world once the intro panel closes. */
	public static void renderHud(GuiGraphics graphics) {
		if (introStart < 0) {
			return;
		}
		long t = introElapsed() - INTRO_END;
		if (t < 0 || t >= HUD_FLASH_MS) {
			return;
		}
		int alpha = Math.round(255 * (1f - t / (float) HUD_FLASH_MS));
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | 0xFFFFFF);
	}
}
