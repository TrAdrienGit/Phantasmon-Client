package com.mystaria.phantasmon.client.command;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Clickable chat buttons that run client code directly (Adrien 2026-10-30: as few commands as possible): the button
 * carries {@code phantasmon-action:<name>} instead of a command, {@code ScreenChatActionMixin} catches the click and
 * runs the action registered under that name — nothing is typed, sent to the server, nor shown in the commands'
 * completion.
 */
public final class ChatActions {

	private static final String PREFIX = "phantasmon-action:";
	private static final Map<String, Runnable> ACTIONS = new ConcurrentHashMap<>();

	private ChatActions() {
	}

	/** What a click on a button named {@code name} does. */
	public static void register(String name, Runnable action) {
		ACTIONS.put(name, action);
	}

	/** A bold chat button running the action {@code name}. */
	public static MutableComponent button(String labelKey, String name, ChatFormatting color) {
		return Component.translatable(labelKey).withStyle(color, ChatFormatting.BOLD)
				.withStyle(style -> style
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, PREFIX + name))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(labelKey))));
	}

	/** {@code ScreenChatActionMixin}: runs the click if it is one of ours; false leaves it to Minecraft. */
	public static boolean handle(Style style) {
		ClickEvent click = style == null ? null : style.getClickEvent();
		if (click == null || click.getAction() != ClickEvent.Action.RUN_COMMAND || !click.getValue().startsWith(PREFIX)) {
			return false;
		}
		Runnable action = ACTIONS.get(click.getValue().substring(PREFIX.length()));
		if (action != null) {
			action.run();
		}
		return true;
	}
}
