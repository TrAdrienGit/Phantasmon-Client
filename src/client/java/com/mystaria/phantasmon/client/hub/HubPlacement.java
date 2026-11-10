package com.mystaria.phantasmon.client.hub;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Placing an anchor from the hub manager (Adrien 2026-11-10): the manager closes and the hub's box follows the player,
 * where the anchor would stand (their position, their facing snapped to a quarter turn) — green when the box is empty,
 * red with the number of blocks in the way otherwise (D-34: the build needs air). Enter poses it
 * ({@link HubController#createAnchor}, which checks again), Escape cancels. The action bar says all that.
 */
public final class HubPlacement {

	/** Blocks in the way are counted again every this many ticks (up to 64³ block reads). */
	private static final int CHECK_EVERY = 5;

	private static HubController controller;
	private static String hub;
	private static String anchorName;
	private static HubAnchorDto.Size size;
	private static int inTheWay;
	private static int ticks;
	private static boolean enterWasDown = true;

	private HubPlacement() {
	}

	/** Starts placing an anchor of {@code hubName} ({@code name}: null for the default one); the caller closes its screen. */
	public static void start(HubController hubController, String hubName, String name, HubAnchorDto.Size hubSize) {
		controller = hubController;
		hub = hubName;
		anchorName = name;
		size = hubSize;
		inTheWay = 0;
		ticks = 0;
		enterWasDown = true; // the Enter that confirmed the manager's form must not pose the anchor at once
	}

	public static boolean active() {
		return hub != null;
	}

	public static void cancel() {
		hub = null;
	}

	private static HubBuildLayout layout(LocalPlayer player) {
		return HubBuildLayout.of(player.getX(), player.getY(), player.getZ(), Math.round(player.getYRot() / 90f) * 90,
				size.x(), size.y(), size.z());
	}

	/** Every client tick. */
	public static void tick() {
		if (hub == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			hub = null;
			return;
		}
		if (mc.screen instanceof PauseScreen) {
			// Escape: cancelled (the pause menu that opened stays, as with any Escape).
			hub = null;
			player.displayClientMessage(Component.translatable("phantasmon.hub.place.cancelled"), true);
			return;
		}
		if (mc.screen != null) {
			return;
		}
		boolean enterDown = InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_ENTER)
				|| InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_KP_ENTER);
		boolean pressed = enterDown && !enterWasDown;
		enterWasDown = enterDown;
		if (ticks++ % CHECK_EVERY == 0) {
			inTheWay = HubBuilds.blocksInTheWay((ClientLevel) player.level(), layout(player));
		}
		if (pressed) {
			if (inTheWay > 0) {
				player.displayClientMessage(Component.translatable("phantasmon.hub.place.blocked", inTheWay)
						.withStyle(ChatFormatting.RED), true);
				return;
			}
			String posing = hub;
			hub = null;
			controller.createAnchor(posing, anchorName);
			return;
		}
		Component prompt = inTheWay > 0
				? Component.translatable("phantasmon.hub.place.prompt_blocked", hub, inTheWay).withStyle(ChatFormatting.RED)
				: Component.translatable("phantasmon.hub.place.prompt", hub, size.x(), size.y(), size.z())
						.withStyle(ChatFormatting.GREEN);
		player.displayClientMessage(prompt, true);
	}

	/** {@code WorldRenderEvents.AFTER_TRANSLUCENT}: the box, green or red. */
	public static void render(WorldRenderContext context) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (hub == null || player == null) {
			return;
		}
		boolean free = inTheWay == 0;
		HubDebugBorders.renderBoxes(context, List.of(layout(player)), free ? 0.3F : 1.0F, free ? 1.0F : 0.3F, 0.35F);
	}
}
