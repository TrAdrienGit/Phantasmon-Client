package com.mystaria.phantasmon.client.gui;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

/**
 * {@code /phantasmon preview <kind> <id>} (2026-11-27): captures what is on screen, without the HUD, as the preview
 * picture of a lobby choice ({@link LobbyPreviews}): 16:9 from the middle of the screen, 640×360. Set the scene up
 * (a battle with that field, stadium, ambiance; an intro playing), then type the command: the chat closes, the HUD
 * hides for a moment and the picture is taken.
 */
public final class PreviewCapture {

	private static final Logger LOG = LoggerFactory.getLogger(PreviewCapture.class);
	private static final int WIDTH = 640;
	private static final int HEIGHT = 360;
	/** Ticks with the HUD hidden before the shot: the chat has closed and a clean frame was drawn. */
	private static final int DELAY_TICKS = 3;

	private static String kind;
	private static String id;
	private static int ticksLeft;
	private static boolean hudWasHidden;

	private PreviewCapture() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(PreviewCapture::tick);
	}

	/** Takes the picture of {@code id} of {@code kind} a few ticks from now. */
	public static void request(String kind, String id) {
		PreviewCapture.kind = kind;
		PreviewCapture.id = id;
		ticksLeft = DELAY_TICKS;
		Minecraft minecraft = Minecraft.getInstance();
		hudWasHidden = minecraft.options.hideGui;
		minecraft.options.hideGui = true;
	}

	private static void tick(Minecraft minecraft) {
		if (kind == null || --ticksLeft > 0) {
			return;
		}
		String shotKind = kind;
		String shotId = id;
		kind = null;
		Path file = LobbyPreviews.file(shotKind, shotId);
		try (NativeImage screen = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
				NativeImage picture = new NativeImage(WIDTH, HEIGHT, false)) {
			// The largest 16:9 rectangle in the middle of the screen, scaled down.
			int w = Math.min(screen.getWidth(), screen.getHeight() * 16 / 9);
			int h = w * 9 / 16;
			screen.resizeSubRectTo((screen.getWidth() - w) / 2, (screen.getHeight() - h) / 2, w, h, picture);
			Files.createDirectories(file.getParent());
			picture.writeToFile(file);
			LobbyPreviews.clear();
			if (minecraft.player != null) {
				minecraft.player.displayClientMessage(Component.translatable("phantasmon.preview.saved", shotKind, shotId), false);
			}
		} catch (Exception ex) {
			LOG.error("Preview {} {} not saved", shotKind, shotId, ex);
			if (minecraft.player != null) {
				minecraft.player.displayClientMessage(Component.translatable("phantasmon.preview.failed", ex.toString()), false);
			}
		} finally {
			minecraft.options.hideGui = hudWasHidden;
		}
	}
}
