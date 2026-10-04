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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.mystaria.phantasmon.client.battle.BattleCinematic;

/**
 * The 2D part of the battle intro ({@link BattleCinematic}), after Pokémon X/Y: the "eyes meet" dialog box over the
 * world shot, the diamond wipe to black, the challenger panel (speed-line background, the opponent running in as a
 * silhouette then revealed, Poké Ball in hand, their team's balls, "You are challenged by …!"), and the white flash.
 * Driven by the cinematic's clock only; it closes itself when the intro is over. No input.
 */
public final class PhantasmonBattleIntroScreen extends Screen {

	private static final long PANEL_RUN_MS = 650;
	private static final long PANEL_REVEAL_MS = 450;
	private static final long PANEL_TEXT_DELAY = 450;
	private static final int SPEED_LINES = 80;

	private RemotePlayer trainer;
	private boolean wipeSoundPlayed;
	private boolean panelSoundPlayed;
	private boolean flashSoundPlayed;

	public PhantasmonBattleIntroScreen() {
		super(Component.translatable("phantasmon.battle.intro.title"));
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
		if (!wipeSoundPlayed && elapsed >= BattleCinematic.EYES_MEET_END) {
			wipeSoundPlayed = true;
			BattleCinematic.play(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1f);
		}
		if (!panelSoundPlayed && elapsed >= BattleCinematic.PANEL_START) {
			panelSoundPlayed = true;
			BattleCinematic.play(SoundEvents.ELYTRA_FLYING, 1.6f, 0.35f);
		}
		if (!flashSoundPlayed && elapsed >= BattleCinematic.FLASH_START) {
			flashSoundPlayed = true;
			BattleCinematic.play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1f);
		}
		RemotePlayer model = trainer();
		if (model != null && elapsed >= BattleCinematic.PANEL_START) {
			boolean running = elapsed - BattleCinematic.PANEL_START < PANEL_RUN_MS;
			model.walkAnimation.update(running ? 1.0f : 0f, running ? 0.6f : 0.25f);
			model.tickCount++;
		}
	}

	// =====================================================================
	// Rendering
	// =====================================================================

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		long elapsed = BattleCinematic.introElapsed();
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			renderEyesMeetBox(g, elapsed);
		} else if (elapsed < BattleCinematic.PANEL_START) {
			renderWipe(g, (elapsed - BattleCinematic.EYES_MEET_END) / (float) (BattleCinematic.WIPE_END - BattleCinematic.EYES_MEET_END));
		} else {
			renderPanel(g, elapsed - BattleCinematic.PANEL_START, partialTick);
		}
		if (elapsed >= BattleCinematic.FLASH_START) {
			float k = Math.min(1f, (elapsed - BattleCinematic.FLASH_START) / 150f);
			g.fill(0, 0, width, height, (Math.round(255 * k) << 24) | 0xFFFFFF);
		}
	}

	/** White dialog box at the top, typed out, as in the overworld encounter. */
	private void renderEyesMeetBox(GuiGraphics g, long elapsed) {
		if (elapsed < 350) {
			return;
		}
		Component text = Component.translatable("phantasmon.battle.intro.eyes_meet");
		float scale = width >= 640 ? 1.5f : 1f;
		int boxW = Math.min(width - 24, Math.round(360 * scale));
		int boxX = 12;
		int boxY = 10;
		List<FormattedCharSequence> lines = font.split(text, Math.round((boxW - 28) / scale));
		int boxH = Math.round((lines.size() * 11 + 14) * scale);
		g.fill(boxX + 2, boxY + 2, boxX + boxW + 2, boxY + boxH + 2, 0x40000000);
		g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFFFAFAFA);
		outline(g, boxX, boxY, boxW, boxH, 0xFFB8B8B8);
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
			g.drawString(font, line.substring(0, keep), 0, 0, 0xFF3C3C3C, false);
			pose.popPose();
		}
		if (visible >= full.length() && (elapsed / 300) % 2 == 0) {
			g.drawString(font, "▼", boxX + boxW - 14, boxY + boxH - 12, 0xFF3C3C3C, false);
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
		renderSpeedBackground(g, t);
		renderTrainer(g, t);
		renderTeamBalls(g, t);
		renderChallengeText(g, t);
	}

	private void renderSpeedBackground(GuiGraphics g, long t) {
		int h = height;
		int w = width;
		g.fillGradient(0, 0, w, (int) (h * 0.28f), 0xFF0A1A35, 0xFF2C5DA6);
		g.fillGradient(0, (int) (h * 0.28f), w, (int) (h * 0.44f), 0xFF2C5DA6, 0xFFE6F2FF);
		g.fillGradient(0, (int) (h * 0.44f), w, (int) (h * 0.54f), 0xFFE6F2FF, 0xFFFFFFFF);
		g.fillGradient(0, (int) (h * 0.54f), w, (int) (h * 0.68f), 0xFFFFFFFF, 0xFF4C7CC2);
		g.fillGradient(0, (int) (h * 0.68f), w, h, 0xFF26395E, 0xFF0D1729);
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
			int color = r1 > 0.5f ? 0xA0FFFFFF : 0x9080B8FF;
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
		float centerY = height * 0.80f - 0.9f * scale;
		float brightness = t < PANEL_RUN_MS ? 0.05f
				: Mth.lerp(Math.min(1f, (t - PANEL_RUN_MS) / (float) PANEL_REVEAL_MS), 0.05f, 1f);
		boolean running = t < PANEL_RUN_MS;

		float body = running ? 125f : 150f;
		model.yBodyRot = body;
		model.yBodyRotO = body;
		model.setYRot(body + 10f);
		model.yRotO = model.getYRot();
		model.setXRot(running ? 8f : -4f);
		model.xRotO = model.getXRot();
		model.yHeadRot = body + 15f;
		model.yHeadRotO = model.yHeadRot;

		Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf camera = new Quaternionf().rotateX(-6f * Mth.DEG_TO_RAD);
		pose.mul(camera);
		g.flush();
		RenderSystem.setShaderColor(brightness, brightness, brightness * 1.08f, 1f);
		InventoryScreen.renderEntityInInventory(g, centerX, centerY, scale, new Vector3f(0f, model.getBbHeight() / 2f, 0f),
				pose, camera, model);
		g.flush();
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
	}

	/** Top right: one ball per Pokémon in the opponent's team, slid in. */
	private void renderTeamBalls(GuiGraphics g, long t) {
		int size = 14;
		int gap = 4;
		int barW = 6 * (size + gap) + 14;
		float slide = Math.min(1f, t / 400f);
		int x = Math.round(width - 8 - barW * (1f - (1f - slide) * (1f - slide)) + (1f - slide) * 0);
		int y = 8;
		g.fill(x, y, x + barW, y + size + 8, 0xA0101828);
		outline(g, x, y, barW, size + 8, 0x80FFFFFF);
		ItemStack ball = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball")));
		for (int i = 0; i < 6; i++) {
			int bx = x + 7 + i * (size + gap);
			int by = y + 4;
			if (i < BattleCinematic.opponentTeamSize() && !ball.isEmpty()) {
				PoseStack pose = g.pose();
				pose.pushPose();
				pose.translate(bx, by, 0);
				pose.scale(size / 16f, size / 16f, 1f);
				g.renderItem(ball, 0, 0);
				pose.popPose();
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
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(width * 0.05f, barY + (height - barY - 9 * scale) / 2f, 0);
		pose.scale(scale, scale, 1f);
		g.drawString(font, text.substring(0, visible), 0, 0, 0xFFFFFFFF, true);
		pose.popPose();
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	/** A stand-in for the opponent, never added to the world: their skin, their worn equipment, a Poké Ball in hand. */
	private RemotePlayer trainer() {
		if (trainer != null) {
			return trainer;
		}
		Minecraft mc = Minecraft.getInstance();
		UUID uuid = BattleCinematic.opponentUuid();
		if (mc.level == null || uuid == null) {
			return null;
		}
		PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(uuid);
		GameProfile profile = info != null ? info.getProfile() : new GameProfile(uuid, BattleCinematic.opponentName());
		trainer = new RemotePlayer(mc.level, profile);
		Player real = mc.level.getPlayerByUUID(uuid);
		if (real != null) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				trainer.setItemSlot(slot, real.getItemBySlot(slot).copy());
			}
		}
		ItemStack ball = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball")));
		trainer.setItemSlot(EquipmentSlot.MAINHAND, ball);
		return trainer;
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

	/** Stable pseudo-random value in [0, 1) for a speed line. */
	private static float hash(int seed) {
		int x = seed * 0x9E3779B1;
		x ^= x >>> 15;
		x *= 0x85EBCA77;
		x ^= x >>> 13;
		return (x & 0xFFFFFF) / (float) 0x1000000;
	}
}
