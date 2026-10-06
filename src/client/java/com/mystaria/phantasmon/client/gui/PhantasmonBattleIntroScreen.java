package com.mystaria.phantasmon.client.gui;

import java.util.List;
import java.util.UUID;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.mystaria.phantasmon.client.battle.BattleCinematic;
import com.mystaria.phantasmon.client.battle.BattleCinematic.Intro;

/**
 * The 2D part of the battle intro ({@link BattleCinematic}), one scene per {@link Intro} (TODO-26), all on the same
 * clock ({@link BattleCinematic#INTRO_END}) and all ending on the same white flash:
 * <ul>
 *   <li><b>X/Y</b>: the "eyes meet" dialog box over the world shot, the diamond wipe to black, the challenger panel
 *   (speed-line background, the opponent running in as a silhouette then revealed, their team's balls, "You are
 *   challenged by …!");</li>
 *   <li><b>Sword/Shield</b>: blue and pink halves slamming in along a diagonal, both trainers face to face and a "VS",
 *   then the opponent throwing their ball over pink speed lines;</li>
 *   <li><b>Diamond/Pearl/Platinum</b>: a spinning Poké Ball closed in by black corners, a blue band, a white burst, then
 *   the field's platforms sliding in from both sides with both trainers, the ball bar and the challenge text;</li>
 *   <li><b>Emerald</b>: the typed dialog box, bands of black behind rolling Poké Balls, the screen opening on a line,
 *   the trainers sliding across the field, both ball bars, "… would like to battle!";</li>
 *   <li><b>Black/White</b>: radial streaks while the world camera rushes at the opponent, white, then the view
 *   pulling back from the opponent's silhouette on their platform before revealing them.</li>
 * </ul>
 * Driven by the cinematic's clock only; it closes itself when the intro is over. No input.
 */
public final class PhantasmonBattleIntroScreen extends Screen {

	// ---- X/Y ----
	private static final long PANEL_RUN_MS = 650;
	private static final long PANEL_REVEAL_MS = 450;
	private static final long PANEL_TEXT_DELAY = 450;
	private static final int SPEED_LINES = 80;
	private static final int[] XY_SPEED = { 0xFF0A1A35, 0xFF2C5DA6, 0xFFE6F2FF, 0xFFFFFFFF, 0xFF4C7CC2, 0xFF26395E, 0xFF0D1729 };

	// ---- Sword/Shield (ms since the session started) ----
	private static final long SWSH_SPLIT = 2450;
	private static final long SWSH_VS = 2750;
	private static final long SWSH_THROW = 4600;
	private static final float SWSH_TILT = 16f;
	private static final int SWSH_BLUE = 0xFF1C4ED6;
	private static final int SWSH_PINK = 0xFFE5307F;
	private static final int[] SWSH_SPEED = { 0xFF3A0A28, 0xFFC0306E, 0xFFFFE0EE, 0xFFFFFFFF, 0xFFE0569A, 0xFF6A1E48, 0xFF2A0A1C };

	// ---- Diamond/Pearl/Platinum ----
	private static final long DP_WIPE_END = 2700;
	private static final long DP_BAND = 2850;
	private static final long DP_BURST = 3050;
	private static final long DP_FIELD = 3300;

	// ---- Emerald ----
	private static final long EM_ROLL_END = 2750;
	private static final long EM_OPEN = 2950;
	private static final long EM_OPEN_END = 3350;
	private static final int EM_BANDS = 5;

	// ---- Black/White ----
	private static final long BW_WHITE = 2450;
	private static final long BW_FIELD = BattleCinematic.ZOOM_END;

	/** Sounds of each scene, at their phase boundaries. */
	private record Cue(long at, SoundEvent sound, float pitch, float volume) {
	}

	private final Intro intro;
	private final List<Cue> cues;
	private final boolean[] cuePlayed;
	private IntroTrainer trainer;
	private IntroTrainer self;
	private boolean throwStarted;

	public PhantasmonBattleIntroScreen() {
		super(Component.translatable("phantasmon.battle.intro.title"));
		this.intro = BattleCinematic.intro();
		this.cues = cues(intro);
		this.cuePlayed = new boolean[cues.size()];
	}

	private static List<Cue> cues(Intro intro) {
		Cue flash = new Cue(BattleCinematic.FLASH_START, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1f);
		return switch (intro) {
			case XY -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1f),
					new Cue(BattleCinematic.PANEL_START, SoundEvents.ELYTRA_FLYING, 1.6f, 0.35f),
					flash);
			case SWORD_SHIELD -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1f),
					new Cue(SWSH_SPLIT, SoundEvents.PLAYER_ATTACK_STRONG, 0.7f, 1f),
					new Cue(SWSH_VS, SoundEvents.FIREWORK_ROCKET_BLAST, 0.7f, 1f),
					new Cue(SWSH_THROW, SoundEvents.ELYTRA_FLYING, 1.8f, 0.35f),
					new Cue(SWSH_THROW + 200, SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.8f),
					flash);
			case DIAMOND_PEARL -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1f),
					new Cue(DP_BAND, SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.7f),
					new Cue(DP_BURST, SoundEvents.FIREWORK_ROCKET_BLAST, 1.1f, 0.9f),
					new Cue(DP_FIELD, SoundEvents.ELYTRA_FLYING, 1.2f, 0.3f),
					flash);
			case EMERALD -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.ITEM_PICKUP, 0.5f, 1f),
					new Cue(BattleCinematic.EYES_MEET_END + 250, SoundEvents.ITEM_PICKUP, 0.6f, 1f),
					new Cue(EM_OPEN, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1f),
					new Cue(EM_OPEN_END, SoundEvents.ELYTRA_FLYING, 1.0f, 0.3f),
					flash);
			case BLACK_WHITE -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.ELYTRA_FLYING, 2.0f, 0.6f),
					new Cue(BW_WHITE, SoundEvents.FIREWORK_ROCKET_BLAST, 1.2f, 0.9f),
					new Cue(BW_FIELD + 200, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 0.8f),
					flash);
		};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// The world shot shows through during the first part.
	}

	@Override
	public void tick() {
		if (!BattleCinematic.introPlaying()) {
			onClose();
			return;
		}
		long elapsed = BattleCinematic.introElapsed();
		for (int i = 0; i < cues.size(); i++) {
			Cue cue = cues.get(i);
			if (!cuePlayed[i] && elapsed >= cue.at()) {
				cuePlayed[i] = true;
				BattleCinematic.play(cue.sound(), cue.pitch(), cue.volume());
			}
		}
		IntroTrainer model = trainer();
		if (model != null) {
			boolean running = intro == Intro.XY && elapsed >= BattleCinematic.PANEL_START
					&& elapsed - BattleCinematic.PANEL_START < PANEL_RUN_MS;
			animate(model, running);
			if (intro == Intro.SWORD_SHIELD && elapsed >= SWSH_THROW + 150 && !throwStarted) {
				throwStarted = true;
				model.swing(InteractionHand.MAIN_HAND);
			}
			model.tickSwing();
		}
		IntroTrainer me = self();
		if (me != null) {
			animate(me, false);
		}
	}

	private static void animate(RemotePlayer model, boolean running) {
		model.walkAnimation.update(running ? 1.0f : 0f, running ? 0.6f : 0.25f);
		model.tickCount++;
	}

	// =====================================================================
	// Rendering
	// =====================================================================

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		long elapsed = BattleCinematic.introElapsed();
		switch (intro) {
			case XY -> renderXy(g, elapsed, partialTick);
			case SWORD_SHIELD -> renderSwordShield(g, elapsed, partialTick);
			case DIAMOND_PEARL -> renderDiamondPearl(g, elapsed);
			case EMERALD -> renderEmerald(g, elapsed);
			case BLACK_WHITE -> renderBlackWhite(g, elapsed);
		}
		if (elapsed >= BattleCinematic.FLASH_START) {
			float k = Math.min(1f, (elapsed - BattleCinematic.FLASH_START) / 150f);
			front(g, 900);
			g.fill(0, 0, width, height, (Math.round(255 * k) << 24) | 0xFFFFFF);
			g.pose().popPose();
		}
	}

	// ---- X/Y ------------------------------------------------------------

	private void renderXy(GuiGraphics g, long elapsed, float partialTick) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			renderEyesMeetBox(g, elapsed, false);
		} else if (elapsed < BattleCinematic.PANEL_START) {
			renderWipe(g, (elapsed - BattleCinematic.EYES_MEET_END) / (float) (BattleCinematic.WIPE_END - BattleCinematic.EYES_MEET_END));
		} else {
			renderPanel(g, elapsed - BattleCinematic.PANEL_START, partialTick);
		}
	}

	/** Dialog box at the top, typed out, as in the overworld encounter: X/Y's white one, or Emerald's framed one. */
	private void renderEyesMeetBox(GuiGraphics g, long elapsed, boolean gba) {
		if (elapsed < 350) {
			return;
		}
		Component text = Component.translatable("phantasmon.battle.intro.eyes_meet");
		float scale = width >= 640 ? 1.5f : 1f;
		int boxW = Math.min(width - 24, Math.round(360 * scale));
		int boxX = 12;
		int boxY = gba ? 0 : 10;
		List<FormattedCharSequence> lines = font.split(text, Math.round((boxW - 28) / scale));
		int boxH = Math.round((lines.size() * 11 + 14) * scale);
		if (gba) {
			boxY = height - boxH - 12;
			g.fill(boxX - 3, boxY - 3, boxX + boxW + 3, boxY + boxH + 3, 0xFF5A6C84);
			g.fill(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, 0xFF9CD0D8);
			g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFFF8F8F8);
		} else {
			g.fill(boxX + 2, boxY + 2, boxX + boxW + 2, boxY + boxH + 2, 0x40000000);
			g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFFFAFAFA);
			outline(g, boxX, boxY, boxW, boxH, 0xFFB8B8B8);
		}
		int visible = (int) ((elapsed - 350) / 22);
		String full = text.getString();
		int drawn = 0;
		PoseStack pose = g.pose();
		for (int i = 0; i < lines.size(); i++) {
			String line = sequenceText(lines.get(i));
			int keep = Math.max(0, Math.min(line.length(), visible - drawn));
			drawn += line.length();
			pose.pushPose();
			pose.translate(boxX + 14, boxY + 8 * scale + i * 11 * scale, 0);
			pose.scale(scale, scale, 1f);
			if (gba) {
				g.drawString(font, line.substring(0, keep), 1, 1, 0xFFD0D0C8, false);
				g.drawString(font, line.substring(0, keep), 0, 0, 0xFF484848, false);
			} else {
				g.drawString(font, line.substring(0, keep), 0, 0, 0xFF3C3C3C, false);
			}
			pose.popPose();
		}
		if (visible >= full.length() && (elapsed / 300) % 2 == 0) {
			g.drawString(font, "▼", boxX + boxW - 14, boxY + boxH - 12, gba ? 0xFFE04830 : 0xFF3C3C3C, false);
		}
	}

	/** Black diamonds growing from the right edge until the screen is black. */
	private void renderWipe(GuiGraphics g, float progress) {
		if (progress >= 1f) {
			g.fill(0, 0, width, height, 0xFF000000);
			return;
		}
		float cell = Math.max(16, height / 7f);
		float front = progress * 1.6f;
		PoseStack pose = g.pose();
		for (int i = 0; i * cell < width + cell; i++) {
			float cx = (i + 0.5f) * cell;
			float local = Mth.clamp((front - (1f - cx / width)) / 0.6f, 0f, 1f);
			if (local <= 0f) {
				continue;
			}
			float half = local * cell * 1.05f / (float) Math.sqrt(2);
			for (int j = 0; j * cell < height + cell; j++) {
				float cy = (j + 0.5f) * cell;
				pose.pushPose();
				pose.translate(cx, cy, 0);
				pose.mulPose(Axis.ZP.rotationDegrees(45));
				pose.scale(half, half, 1f);
				g.fill(-1, -1, 1, 1, 0xFF000000);
				pose.popPose();
			}
		}
	}

	/** The challenger panel. {@code t} = ms since it appeared. */
	private void renderPanel(GuiGraphics g, long t, float partialTick) {
		renderSpeedBackground(g, t, XY_SPEED, 0x9080B8FF);
		renderTrainer(g, t);
		front(g, 500);
		renderTeamBalls(g, t);
		renderChallengeText(g, t);
		g.pose().popPose();
	}

	private void renderSpeedBackground(GuiGraphics g, long t, int[] c, int lineColor) {
		int h = height;
		int w = width;
		g.fillGradient(0, 0, w, (int) (h * 0.28f), c[0], c[1]);
		g.fillGradient(0, (int) (h * 0.28f), w, (int) (h * 0.44f), c[1], c[2]);
		g.fillGradient(0, (int) (h * 0.44f), w, (int) (h * 0.54f), c[2], c[3]);
		g.fillGradient(0, (int) (h * 0.54f), w, (int) (h * 0.68f), c[3], c[4]);
		g.fillGradient(0, (int) (h * 0.68f), w, h, c[5], c[6]);
		// Dotted floor, scrolling.
		float scroll = (t * 0.45f) % 10f;
		for (int y = (int) (h * 0.70f); y < h; y += 6) {
			for (float x = -scroll; x < w; x += 10) {
				g.fill((int) x, y, (int) x + 1, y + 1, 0x30FFFFFF);
			}
		}
		// Speed lines rushing to the left.
		for (int i = 0; i < SPEED_LINES; i++) {
			float r1 = hash(i * 3 + 1);
			float r2 = hash(i * 3 + 2);
			float r3 = hash(i * 3 + 3);
			float bias = (r1 - 0.5f) * (r1 - 0.5f) * 4f * Math.signum(r1 - 0.5f);
			int y = (int) (h * (0.47f + 0.24f * bias));
			int length = (int) (w * (0.08f + 0.30f * r2));
			float speed = w * (1.3f + 2.2f * r3) / 1000f;
			float travel = (t * speed + r2 * w * 3) % (w + length);
			int x = (int) (w - travel);
			int thickness = r3 > 0.7f ? 2 : 1;
			int color = r1 > 0.5f ? 0xA0FFFFFF : lineColor;
			g.fill(x, y, x + length, y + thickness, color);
		}
	}

	/** The opponent: runs in from the left as a silhouette, then their colors come in. */
	private void renderTrainer(GuiGraphics g, long t) {
		RemotePlayer model = trainer();
		if (model == null) {
			return;
		}
		float run = Math.min(1f, t / (float) PANEL_RUN_MS);
		float ease = 1f - (1f - run) * (1f - run);
		float centerX = Mth.lerp(ease, -width * 0.15f, width * 0.30f);
		float scale = height * 0.62f / 1.8f;
		float brightness = t < PANEL_RUN_MS ? 0.05f
				: Mth.lerp(Math.min(1f, (t - PANEL_RUN_MS) / (float) PANEL_REVEAL_MS), 0.05f, 1f);
		boolean running = t < PANEL_RUN_MS;
		model.setXRot(running ? 8f : -4f);
		drawModel(g, model, centerX, height * 0.80f, scale, running ? 125f : 150f, brightness);
	}

	/** Top right: one ball per Pokémon in the opponent's team, slid in. */
	private void renderTeamBalls(GuiGraphics g, long t) {
		int size = 14;
		int gap = 4;
		int barW = 6 * (size + gap) + 14;
		float slide = Math.min(1f, t / 400f);
		int x = Math.round(width - 8 - barW * (1f - (1f - slide) * (1f - slide)));
		int y = 8;
		g.fill(x, y, x + barW, y + size + 8, 0xA0101828);
		outline(g, x, y, barW, size + 8, 0x80FFFFFF);
		for (int i = 0; i < 6; i++) {
			int bx = x + 7 + i * (size + gap);
			int by = y + 4;
			if (i < BattleCinematic.opponentTeamSize()) {
				item(g, ball(), bx + size / 2f, by + size / 2f, size, 0f);
			} else {
				g.fill(bx + 4, by + 4, bx + size - 4, by + size - 4, 0xFF8A8F99);
			}
		}
	}

	/** Bottom text bar: "You are challenged by …!", typed out. */
	private void renderChallengeText(GuiGraphics g, long t) {
		int barY = (int) (height * 0.80f);
		g.fillGradient(0, barY, width, height, 0xE0383C44, 0xF0101216);
		g.fill(0, barY, width, barY + 2, 0xFFD0D4DC);
		if (t < PANEL_TEXT_DELAY) {
			return;
		}
		String text = Component.translatable("phantasmon.battle.intro.challenged", BattleCinematic.opponentName()).getString();
		int visible = (int) Math.min(text.length(), (t - PANEL_TEXT_DELAY) / 28);
		float scale = width >= 640 ? 2f : 1.5f;
		text(g, text.substring(0, visible), width * 0.05f, barY + (height - barY - 9 * scale) / 2f, scale, 0xFFFFFFFF, true);
	}

	// ---- Sword/Shield ---------------------------------------------------

	private void renderSwordShield(GuiGraphics g, long elapsed, float partialTick) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			return;
		}
		if (elapsed >= SWSH_THROW) {
			renderSwordShieldThrow(g, elapsed - SWSH_THROW);
			return;
		}
		// The two halves slam in along the diagonal, then stay, their stripes streaming.
		float slam = Math.min(1f, (elapsed - BattleCinematic.EYES_MEET_END) / (float) (SWSH_SPLIT - BattleCinematic.EYES_MEET_END));
		float ease = slam * slam;
		float d = (float) Math.sqrt(width * (float) width + height * (float) height);
		long t = Math.max(0, elapsed - SWSH_SPLIT);
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(width / 2f, height / 2f, 0);
		pose.mulPose(Axis.ZP.rotationDegrees(SWSH_TILT));
		int left = Math.round(Mth.lerp(ease, -d, 0));
		int right = Math.round(Mth.lerp(ease, d, 0));
		g.fill(Math.round(-d), Math.round(-d), left, Math.round(d), SWSH_BLUE);
		g.fill(right, Math.round(-d), Math.round(d), Math.round(d), SWSH_PINK);
		for (int i = 0; i < 18; i++) {
			float r1 = hash(i * 7 + 11);
			float r2 = hash(i * 7 + 12);
			float span = 2 * d;
			float speed = d * (0.4f + 0.8f * r2) / 1000f;
			int thickness = 2 + Math.round(6 * r1);
			int yDown = Math.round((r1 * span + t * speed) % span - d);
			int yUp = Math.round(d - (r2 * span + t * speed) % span);
			g.fill(Math.round(-d), yDown, left, yDown + thickness, 0x2CFFFFFF);
			g.fill(right, yUp, Math.round(d), yUp + thickness, 0x2CFFFFFF);
		}
		if (slam >= 1f) {
			g.fill(-5, Math.round(-d), 5, Math.round(d), 0xFF101018);
			g.fill(-3, Math.round(-d), 3, Math.round(d), 0xFFFFFFFF);
		} else {
			g.fill(left - 3, Math.round(-d), left, Math.round(d), 0xFFFFFFFF);
			g.fill(right, Math.round(-d), right + 3, Math.round(d), 0xFFFFFFFF);
		}
		pose.popPose();
		if (elapsed < SWSH_SPLIT) {
			return;
		}

		// Both trainers, sliding in from their side, a ball in hand.
		float in = Math.min(1f, t / 450f);
		float slide = 1f - (1f - in) * (1f - in);
		float scale = height * 0.58f / 1.8f;
		IntroTrainer me = self();
		if (me != null) {
			me.setXRot(-4f);
			drawModel(g, me, Mth.lerp(slide, -width * 0.25f, width * 0.24f), height * 0.97f, scale, 150f, 1f);
		}
		IntroTrainer opponent = trainer();
		if (opponent != null) {
			opponent.setXRot(-4f);
			drawModel(g, opponent, Mth.lerp(slide, width * 1.25f, width * 0.76f), height * 0.97f, scale, 210f, 1f);
		}

		front(g, 500);
		float textScale = width >= 640 ? 2f : 1.5f;
		float plate = 9 * textScale + 10;
		String selfName = selfName();
		String opponentName = BattleCinematic.opponentName();
		float plateIn = Math.min(1f, t / 300f);
		int selfPlateW = Math.round((font.width(selfName) * textScale + 30) * plateIn);
		int opponentPlateW = Math.round((font.width(opponentName) * textScale + 30) * plateIn);
		g.fill(0, 10, selfPlateW, Math.round(10 + plate), 0xC0081A55);
		g.fill(width - opponentPlateW, 10, width, Math.round(10 + plate), 0xC055081E);
		if (plateIn >= 1f) {
			text(g, selfName, 14, 15, textScale, 0xFFFFFFFF, true);
			text(g, opponentName, width - 14 - font.width(opponentName) * textScale, 15, textScale, 0xFFFFFFFF, true);
		}
		if (elapsed >= SWSH_VS) {
			long v = elapsed - SWSH_VS;
			float pop = v < 160 ? Mth.lerp(v / 160f, 3.2f, 0.9f) : v < 260 ? Mth.lerp((v - 160) / 100f, 0.9f, 1f) : 1f;
			float shake = v < 400 ? (float) Math.sin(v * 0.35) * 3f * (1f - v / 400f) : 0f;
			String vs = Component.translatable("phantasmon.battle.intro.vs").getString();
			float vsScale = height / 55f * pop;
			float vx = width / 2f - font.width(vs) * vsScale / 2f + shake;
			float vy = height * 0.48f - 4.5f * vsScale;
			for (int[] o : new int[][] { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 }, { 1, 1 } }) {
				text(g, vs, vx + o[0] * vsScale * 0.6f, vy + o[1] * vsScale * 0.6f, vsScale, 0xFF14141E, false);
			}
			text(g, vs, vx, vy, vsScale, 0xFFFFD43A, false);
		}
		g.pose().popPose();
	}

	/** The opponent, close, throws their ball at us over pink speed lines. */
	private void renderSwordShieldThrow(GuiGraphics g, long t) {
		renderSpeedBackground(g, t, SWSH_SPEED, 0x90FF9CCB);
		IntroTrainer opponent = trainer();
		if (opponent != null) {
			float push = 1f + 0.06f * Math.min(1f, t / 600f);
			opponent.setXRot(4f);
			drawModel(g, opponent, width * 0.40f, height * 1.30f, height * 1.0f / 1.8f * push, 205f, 1f);
		}
		if (t >= 300) {
			float k = Math.min(1f, (t - 300) / 300f);
			float x = Mth.lerp(k, width * 0.52f, width * 0.78f);
			float y = Mth.lerp(k, height * 0.42f, height * 0.30f) - (float) Math.sin(k * Math.PI) * height * 0.08f;
			front(g, 500);
			item(g, ball(), x, y, Mth.lerp(k * k, 18f, height * 0.45f), t * 1.4f);
			g.pose().popPose();
		}
	}

	// ---- Diamond/Pearl/Platinum -----------------------------------------

	private void renderDiamondPearl(GuiGraphics g, long elapsed) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			return;
		}
		if (elapsed < DP_WIPE_END) {
			// The screen's four corners close in on a spinning Poké Ball.
			float p = (elapsed - BattleCinematic.EYES_MEET_END) / (float) (DP_WIPE_END - BattleCinematic.EYES_MEET_END);
			float a = p * p * (3 - 2 * p);
			int hw = Math.round(width / 2f * a);
			int hh = Math.round(height / 2f * a);
			g.fill(0, 0, hw, hh, 0xFF000000);
			g.fill(width - hw, 0, width, hh, 0xFF000000);
			g.fill(0, height - hh, hw, height, 0xFF000000);
			g.fill(width - hw, height - hh, width, height, 0xFF000000);
			if (p < 0.92f) {
				item(g, ball(), width / 2f, height / 2f, height * (0.55f - 0.35f * p), 720f * p);
			}
			return;
		}
		if (elapsed < DP_FIELD) {
			g.fill(0, 0, width, height, 0xFF000000);
			if (elapsed >= DP_BAND) {
				// A blue band opens across the middle, then a white burst out of it.
				float b = Math.min(1f, (elapsed - DP_BAND) / 200f);
				int half = Math.round(height * 0.18f * (1f - (1f - b) * (1f - b)));
				g.fillGradient(0, height / 2 - half, width, height / 2, 0xFF1C3C90, 0xFF5C9CF0);
				g.fillGradient(0, height / 2, width, height / 2 + half, 0xFF5C9CF0, 0xFF1C3C90);
				g.fill(0, height / 2 - 1, width, height / 2 + 1, 0xC0FFFFFF);
			}
			if (elapsed >= DP_BURST) {
				float k = (elapsed - DP_BURST) / (float) (DP_FIELD - DP_BURST);
				rays(g, width / 2f, height / 2f, 48, 0f, Math.max(width, height) * (0.2f + 0.9f * k), 3f + 6f * k,
						(Math.round(255 * Math.min(1f, k * 2f)) << 24) | 0xFFFFFF, 0f);
				g.fill(0, 0, width, height, (Math.round(255 * k * k) << 24) | 0xFFFFFF);
			}
			return;
		}

		long t = elapsed - DP_FIELD;
		g.fillGradient(0, 0, width, Math.round(height * 0.55f), 0xFFF4F4DC, 0xFFC8E0A0);
		g.fillGradient(0, Math.round(height * 0.55f), width, height, 0xFF98C870, 0xFF70A848);
		float in = Math.min(1f, t / 700f);
		float slide = 1f - (1f - in) * (1f - in) * (1f - in);
		float farX = Mth.lerp(slide, -width * 0.3f, width * 0.70f);
		float nearX = Mth.lerp(slide, width * 1.3f, width * 0.26f);
		float farY = height * 0.46f;
		float nearY = height * 0.84f;
		platform(g, farX, farY, width * 0.19f, height * 0.065f, 0xFF507838, 0xFF6C9A48, 0xFF8CBC60);
		platform(g, nearX, nearY, width * 0.27f, height * 0.09f, 0xFF507838, 0xFF6C9A48, 0xFF8CBC60);
		IntroTrainer opponent = trainer();
		if (opponent != null) {
			opponent.setXRot(-4f);
			drawModel(g, opponent, farX, farY - height * 0.01f, height * 0.30f / 1.8f, 205f, 1f);
		}
		IntroTrainer me = self();
		if (me != null) {
			me.setXRot(0f);
			drawModel(g, me, nearX - width * 0.03f, height * 1.12f, height * 0.62f / 1.8f, 30f, 1f);
		}
		front(g, 500);
		if (t < 200) {
			g.fill(0, 0, width, height, (Math.round(255 * (1f - t / 200f)) << 24) | 0xFFFFFF);
		}
		if (t >= 700) {
			ballBar(g, 0, height * 0.30f, width * 0.42f, BattleCinematic.opponentTeamSize(), true, Math.min(1f, (t - 700) / 300f), 0xFF404858);
		}
		if (t >= 950) {
			int boxY = Math.round(height * 0.78f);
			g.fill(0, boxY, width, height, 0xFF586878);
			g.fill(3, boxY + 3, width - 3, height - 3, 0xFFA8B8C8);
			g.fill(6, boxY + 6, width - 6, height - 6, 0xFFF8F8F8);
			typed(g, Component.translatable("phantasmon.battle.intro.challenged", BattleCinematic.opponentName()).getString(),
					t - 950, 16, boxY + 6, height - 6, 0xFF505058, 0xFFD0D0D8);
		}
		g.pose().popPose();
	}

	// ---- Emerald ---------------------------------------------------------

	private void renderEmerald(GuiGraphics g, long elapsed) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			renderEyesMeetBox(g, elapsed, true);
			return;
		}
		if (elapsed < EM_OPEN) {
			// Bands of black, each pushed across by a rolling Poké Ball, every other one from the other side.
			float bandH = height / (float) EM_BANDS;
			for (int i = 0; i < EM_BANDS; i++) {
				long local = elapsed - BattleCinematic.EYES_MEET_END - i * 70L;
				float p = Mth.clamp(local / (float) (EM_ROLL_END - BattleCinematic.EYES_MEET_END - (EM_BANDS - 1) * 70L), 0f, 1f);
				float front = p * (width + bandH);
				int top = Math.round(i * bandH);
				int bottom = Math.round((i + 1) * bandH);
				boolean fromLeft = i % 2 == 0;
				if (fromLeft) {
					g.fill(0, top, Math.round(front - bandH / 2f), bottom, 0xFF000000);
				} else {
					g.fill(Math.round(width - front + bandH / 2f), top, width, bottom, 0xFF000000);
				}
				if (p > 0f && p < 1f) {
					float x = fromLeft ? front - bandH / 2f : width - front + bandH / 2f;
					float roll = (float) Math.toDegrees(front / (bandH / 2f)) * (fromLeft ? 1 : -1);
					item(g, ball(), x, top + bandH / 2f, bandH * 0.95f, roll);
				}
			}
			return;
		}

		long t = elapsed - EM_OPEN;
		g.fillGradient(0, 0, width, Math.round(height * 0.52f), 0xFFF0F8E8, 0xFFC8E4B0);
		g.fillGradient(0, Math.round(height * 0.52f), width, height, 0xFFB0D890, 0xFF88C068);
		float slide = Math.min(1f, t / 1000f);
		float farX = Mth.lerp(slide, -width * 0.25f, width * 0.70f);
		float nearX = Mth.lerp(slide, width * 1.25f, width * 0.28f);
		float farY = height * 0.42f;
		float nearY = height * 0.74f;
		platform(g, farX, farY, width * 0.17f, height * 0.06f, 0xFF588840, 0xFF78A858, 0xFF98C878);
		platform(g, nearX, nearY, width * 0.22f, height * 0.075f, 0xFF588840, 0xFF78A858, 0xFF98C878);
		IntroTrainer opponent = trainer();
		if (opponent != null) {
			opponent.setXRot(-4f);
			drawModel(g, opponent, farX, farY - height * 0.01f, height * 0.28f / 1.8f, 200f, 1f);
		}
		IntroTrainer me = self();
		if (me != null) {
			me.setXRot(0f);
			drawModel(g, me, nearX, nearY + height * 0.02f, height * 0.40f / 1.8f, 30f, 1f);
		}
		front(g, 500);
		if (elapsed < EM_OPEN_END) {
			// The black opens from a white line across the middle.
			float o = (elapsed - EM_OPEN) / (float) (EM_OPEN_END - EM_OPEN);
			int half = Math.round(height / 2f * (1f - o * o));
			g.fill(0, 0, width, half, 0xFF000000);
			g.fill(0, height - half, width, height, 0xFF000000);
			g.fill(0, half - 1, width, half, 0xFFFFFFFF);
			g.fill(0, height - half, width, height - half + 1, 0xFFFFFFFF);
		}
		if (t >= 1050) {
			float k = Math.min(1f, (t - 1050) / 250f);
			ballBar(g, 0, height * 0.24f, width * 0.40f, BattleCinematic.opponentTeamSize(), true, k, 0xFF303038);
			ballBar(g, width * 0.58f, height * 0.60f, width * 0.42f, BattleCinematic.ownTeamSize(), false, k, 0xFF303038);
		}
		if (t >= 1250) {
			int boxY = Math.round(height * 0.78f);
			g.fill(0, boxY, width, height, 0xFFC85030);
			g.fill(3, boxY + 3, width - 3, height - 3, 0xFFE8D0A0);
			g.fill(6, boxY + 6, width - 6, height - 6, 0xFF284860);
			typed(g, Component.translatable("phantasmon.battle.intro.would_like", BattleCinematic.opponentName()).getString(),
					t - 1250, 16, boxY + 6, height - 6, 0xFFF8F8F8, 0xFF687888);
		}
		g.pose().popPose();
	}

	// ---- Black/White -----------------------------------------------------

	private void renderBlackWhite(GuiGraphics g, long elapsed) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			return;
		}
		if (elapsed < BW_FIELD) {
			// The world camera rushes at the opponent (BattleCinematic): radial streaks over it, then white.
			float p = (elapsed - BattleCinematic.EYES_MEET_END) / (float) (BW_FIELD - BattleCinematic.EYES_MEET_END);
			float reach = Math.max(width, height) * 0.8f;
			rays(g, width / 2f, height / 2f, 90, reach * (0.55f - 0.35f * p), reach, 1.5f,
					(Math.round(170 * Math.min(1f, p * 2.5f)) << 24) | 0xFFFFFF, elapsed * 0.0004f);
			if (elapsed >= BW_WHITE) {
				float w = (elapsed - BW_WHITE) / (float) (BW_FIELD - BW_WHITE);
				g.fill(0, 0, width, height, (Math.round(255 * Math.min(1f, w * 1.4f)) << 24) | 0xFFFFFF);
			}
			return;
		}

		long t = elapsed - BW_FIELD;
		g.fillGradient(0, 0, width, Math.round(height * 0.46f), 0xFF4E6E9E, 0xFFAEC4CC);
		g.fillGradient(0, Math.round(height * 0.46f), width, height, 0xFF4A8A82, 0xFF245650);
		// The view pulls back from the opponent's platform.
		float pull = Math.min(1f, t / 1000f);
		float zoom = Mth.lerp(1f - (1f - pull) * (1f - pull) * (1f - pull), 2.6f, 1f);
		float focusX = width * 0.66f;
		float focusY = height * 0.40f;
		float farX = width * 0.66f;
		float farY = height * 0.50f;
		float nearX = width * 0.20f;
		float nearY = height * 0.93f;
		platform(g, focusX + (nearX - focusX) * zoom, focusY + (nearY - focusY) * zoom, width * 0.30f * zoom, height * 0.10f * zoom,
				0xFF1E5E58, 0xFF2F7F78, 0xFF6CC2B0);
		platform(g, focusX + (farX - focusX) * zoom, focusY + (farY - focusY) * zoom, width * 0.20f * zoom, height * 0.065f * zoom,
				0xFF1E5E58, 0xFF2F7F78, 0xFF6CC2B0);
		IntroTrainer opponent = trainer();
		if (opponent != null) {
			float brightness = t < 1000 ? 0.05f : Mth.lerp(Math.min(1f, (t - 1000) / 400f), 0.05f, 1f);
			opponent.setXRot(-4f);
			drawModel(g, opponent, focusX + (farX - focusX) * zoom, focusY + (farY - height * 0.01f - focusY) * zoom,
					height * 0.32f / 1.8f * zoom, 200f, brightness);
		}
		front(g, 500);
		if (t < 400) {
			g.fill(0, 0, width, height, (Math.round(255 * (1f - t / 400f)) << 24) | 0xFFFFFF);
		}
		if (t >= 1150) {
			ballBar(g, 0, height * 0.18f, width * 0.40f, BattleCinematic.opponentTeamSize(), true, Math.min(1f, (t - 1150) / 300f), 0xFF20242C);
		}
		if (t >= 1400) {
			int boxY = Math.round(height * 0.80f);
			g.fill(4, boxY, width - 4, height - 4, 0xD0182028);
			outline(g, 4, boxY, width - 8, height - boxY - 4, 0xFF8CA0B0);
			typed(g, Component.translatable("phantasmon.battle.intro.challenged", BattleCinematic.opponentName()).getString(),
					t - 1400, 18, boxY, height - 4, 0xFFFFFFFF, 0xFF404850);
		}
		g.pose().popPose();
	}

	// =====================================================================
	// Drawing helpers
	// =====================================================================

	/**
	 * Draws a stand-in on screen: feet at {@code (x, feetY)}, {@code scale} pixels per block, body turned by
	 * {@code bodyYaw} (180 = facing us, 0 = its back), darkened to {@code brightness} (a silhouette near 0).
	 */
	private void drawModel(GuiGraphics g, RemotePlayer model, float x, float feetY, float scale, float bodyYaw, float brightness) {
		model.yBodyRot = bodyYaw;
		model.yBodyRotO = bodyYaw;
		model.setYRot(bodyYaw + 10f);
		model.yRotO = model.getYRot();
		model.xRotO = model.getXRot();
		model.yHeadRot = bodyYaw + 15f;
		model.yHeadRotO = model.yHeadRot;
		Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf camera = new Quaternionf().rotateX(-6f * Mth.DEG_TO_RAD);
		pose.mul(camera);
		g.flush();
		RenderSystem.setShaderColor(brightness, brightness, brightness * 1.08f, 1f);
		InventoryScreen.renderEntityInInventory(g, x, feetY - model.getBbHeight() / 2f * scale, scale,
				new Vector3f(0f, model.getBbHeight() / 2f, 0f), pose, camera, model);
		g.flush();
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
	}

	/** Pushes the pose forward, so that what follows covers the models (pop it after). */
	private static void front(GuiGraphics g, float z) {
		g.pose().pushPose();
		g.pose().translate(0, 0, z);
	}

	/** A battle platform: a dark rim, its body, a lighter top. */
	private static void platform(GuiGraphics g, float cx, float cy, float rx, float ry, int rim, int body, int top) {
		ellipse(g, cx, cy + ry * 0.12f, rx * 1.03f, ry * 1.08f, rim);
		ellipse(g, cx, cy, rx, ry, body);
		ellipse(g, cx, cy - ry * 0.08f, rx * 0.78f, ry * 0.72f, top);
	}

	private static void ellipse(GuiGraphics g, float cx, float cy, float rx, float ry, int color) {
		if (rx < 1f || ry < 1f) {
			return;
		}
		int top = Math.round(cy - ry);
		int bottom = Math.round(cy + ry);
		for (int y = top; y < bottom; y++) {
			float dy = (y + 0.5f - cy) / ry;
			float half = rx * (float) Math.sqrt(Math.max(0f, 1f - dy * dy));
			g.fill(Math.round(cx - half), y, Math.round(cx + half), y + 1, color);
		}
	}

	/** Lines out of {@code (cx, cy)}, from {@code from} to {@code to} px, at stable pseudo-random angles. */
	private static void rays(GuiGraphics g, float cx, float cy, int count, float from, float to, float thickness, int color, float spin) {
		PoseStack pose = g.pose();
		for (int i = 0; i < count; i++) {
			float angle = hash(i * 5 + 101) * Mth.TWO_PI + spin;
			float start = from * (0.8f + 0.4f * hash(i * 5 + 102));
			int half = Math.max(1, Math.round(thickness * (0.5f + hash(i * 5 + 103))));
			pose.pushPose();
			pose.translate(cx, cy, 0);
			pose.mulPose(Axis.ZP.rotation(angle));
			g.fill(Math.round(start), -half / 2, Math.round(to), half - half / 2, color);
			pose.popPose();
		}
	}

	/**
	 * A ball bar: a line with one ball per Pokémon above it (empty slots greyed), sliding in from its side;
	 * {@code fromLeft} = anchored on the left edge.
	 */
	private void ballBar(GuiGraphics g, float x, float y, float length, int count, boolean fromLeft, float progress, int lineColor) {
		float ease = 1f - (1f - progress) * (1f - progress);
		float offset = (1f - ease) * (length + 20) * (fromLeft ? -1 : 1);
		int size = Math.max(10, Math.round(height / 22f));
		int x0 = Math.round(x + offset);
		g.fill(x0, Math.round(y), Math.round(x0 + length), Math.round(y + 3), lineColor);
		g.fill(x0, Math.round(y + 3), Math.round(x0 + length), Math.round(y + 4), 0x80FFFFFF);
		float start = fromLeft ? x0 + length - 8 - 6 * (size + 2) : x0 + 8;
		for (int i = 0; i < 6; i++) {
			float bx = start + i * (size + 2) + size / 2f;
			float by = y - size / 2f - 2;
			if (i < count) {
				item(g, ball(), bx, by, size, 0f);
			} else {
				ellipse(g, bx, by, size / 2f - 1, size / 2f - 1, 0xFF505660);
				ellipse(g, bx, by, size / 2f - 3, size / 2f - 3, 0xFF8A8F99);
			}
		}
	}

	/** Typed text in a box's band {@code [top, bottom]}, with a drop shadow. */
	private void typed(GuiGraphics g, String text, long t, float x, float top, float bottom, int color, int shadow) {
		int visible = (int) Math.min(text.length(), Math.max(0, t) / 28);
		float scale = width >= 640 ? 2f : 1.5f;
		float y = top + (bottom - top - 9 * scale) / 2f;
		String shown = text.substring(0, visible);
		text(g, shown, x + scale, y + scale, scale, shadow, false);
		text(g, shown, x, y, scale, color, false);
	}

	private void text(GuiGraphics g, String text, float x, float y, float scale, int color, boolean shadow) {
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(x, y, 0);
		pose.scale(scale, scale, 1f);
		g.drawString(font, text, 0, 0, color, shadow);
		pose.popPose();
	}

	/** An item centered on {@code (cx, cy)}, {@code size} px wide, turned by {@code degrees}. */
	private static void item(GuiGraphics g, ItemStack stack, float cx, float cy, float size, float degrees) {
		if (stack.isEmpty()) {
			return;
		}
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(cx, cy, 0);
		pose.mulPose(Axis.ZP.rotationDegrees(degrees));
		pose.scale(size / 16f, size / 16f, 1f);
		g.renderItem(stack, -8, -8);
		pose.popPose();
	}

	private static ItemStack ball() {
		return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball")));
	}

	// =====================================================================
	// Stand-ins
	// =====================================================================

	/** The opponent's stand-in, never added to the world: their skin, their worn equipment, a Poké Ball in hand. */
	private IntroTrainer trainer() {
		if (trainer == null) {
			trainer = standIn(BattleCinematic.opponentUuid(), BattleCinematic.opponentName());
		}
		return trainer;
	}

	/** Ours, for the scenes showing both trainers. */
	private IntroTrainer self() {
		if (self == null) {
			Player player = Minecraft.getInstance().player;
			self = player == null ? null : standIn(player.getUUID(), player.getGameProfile().getName());
		}
		return self;
	}

	private static String selfName() {
		Player player = Minecraft.getInstance().player;
		return player == null ? "?" : player.getGameProfile().getName();
	}

	private static IntroTrainer standIn(UUID uuid, String name) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || uuid == null) {
			return null;
		}
		PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(uuid);
		GameProfile profile = info != null ? info.getProfile() : new GameProfile(uuid, name);
		Player real = mc.level.getPlayerByUUID(uuid);
		IntroTrainer standIn = new IntroTrainer(mc.level, profile, real);
		if (real != null) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				standIn.setItemSlot(slot, real.getItemBySlot(slot).copy());
			}
		}
		standIn.setItemSlot(EquipmentSlot.MAINHAND, ball());
		return standIn;
	}

	/**
	 * The stand-in's shown skin parts (outer layer: hat, jacket, sleeves, trousers; cape) are synced entity data,
	 * all off on a fresh entity — they come from the real player when loaded, else all on (Adrien 2026-10-05: the
	 * intro showed neither the outer layer nor the cape). A subclass, for the protected accessors.
	 */
	private static final class IntroTrainer extends RemotePlayer {
		IntroTrainer(net.minecraft.client.multiplayer.ClientLevel level, GameProfile profile, Player real) {
			super(level, profile);
			byte parts = real != null ? real.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION) : (byte) 0x7F;
			getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, parts);
		}

		/** Advances an arm swing (the Sword/Shield throw); the stand-in never ticks in the world. */
		void tickSwing() {
			oAttackAnim = attackAnim;
			updateSwingTime();
		}
	}

	private static String sequenceText(FormattedCharSequence sequence) {
		StringBuilder builder = new StringBuilder();
		sequence.accept((index, style, codePoint) -> {
			builder.appendCodePoint(codePoint);
			return true;
		});
		return builder.toString();
	}

	private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	/** Stable pseudo-random value in [0, 1). */
	private static float hash(int seed) {
		int x = seed * 0x9E3779B1;
		x ^= x >>> 15;
		x *= 0x85EBCA77;
		x ^= x >>> 13;
		return (x & 0xFFFFFF) / (float) 0x1000000;
	}
}
