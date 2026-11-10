package com.mystaria.phantasmon.client.hub;

import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.command.ChatActions;

/**
 * Placing an anchor from the hub manager (Adrien 2026-11-10): the manager closes and the hub's box follows the player,
 * where the anchor would stand (their position, their facing snapped to a quarter turn) — green when the box is empty,
 * red with the number of blocks in the way otherwise (D-34: the build needs air). Two chat buttons (Adrien 2026-11-10:
 * no key) — [Accepter] poses it where the player stands when they click ({@link HubController#createAnchor}, which
 * checks again), [Annuler] gives up; opening the hub manager (H) gives up too. The action bar says all that.
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
		postButtons();
	}

	/** The chat buttons, registered once ({@link ChatActions}); a click once placing is over does nothing. */
	public static void register() {
		ChatActions.register("hub.place.accept", HubPlacement::accept);
		ChatActions.register("hub.place.cancel", () -> {
			if (hub != null) {
				hub = null;
				message(Component.translatable("phantasmon.hub.place.cancelled").withStyle(ChatFormatting.GRAY));
			}
		});
	}

	private static void postButtons() {
		message(Component.translatable("phantasmon.hub.place.chat", hub, size.x(), size.y(), size.z())
				.withStyle(ChatFormatting.LIGHT_PURPLE)
				.append(" ")
				.append(ChatActions.button("phantasmon.hub.place.accept", "hub.place.accept", ChatFormatting.GREEN))
				.append(" ")
				.append(ChatActions.button("phantasmon.hub.place.cancel", "hub.place.cancel", ChatFormatting.RED)));
	}

	private static void message(Component text) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.displayClientMessage(text, false);
		}
	}

	private static void accept() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (hub == null || player == null) {
			return;
		}
		inTheWay = HubBuilds.blocksInTheWay((ClientLevel) player.level(), layout(player));
		if (inTheWay > 0) {
			message(Component.translatable("phantasmon.hub.place.blocked", inTheWay).withStyle(ChatFormatting.RED));
			postButtons();
			return;
		}
		String posing = hub;
		hub = null;
		controller.createAnchor(posing, anchorName);
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
		if (ticks++ % CHECK_EVERY == 0) {
			inTheWay = HubBuilds.blocksInTheWay((ClientLevel) player.level(), layout(player));
		}
		if (mc.screen != null) {
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
