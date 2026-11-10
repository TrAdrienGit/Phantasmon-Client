package com.mystaria.phantasmon.client.gui.menu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * One category of the Phantasmon menu (Hubs, Music, Settings): it draws and handles its content area, from
 * {@code (x, y)} to {@code (right, bottom)}, set by {@link #layout} before every frame. GUI pixels, client thread.
 */
interface MenuPage {

	Component title();

	void layout(int x, int y, int right, int bottom);

	void render(GuiGraphics g, int mouseX, int mouseY);

	boolean click(double mouseX, double mouseY);

	default boolean drag(double mouseX, double mouseY) {
		return false;
	}

	default void release() {
	}

	default boolean scroll(double mouseX, double mouseY, double amount) {
		return false;
	}

	default boolean charTyped(char chr) {
		return false;
	}

	/** A key; true if the page used it (Escape is the screen's unless a field is focused). */
	default boolean key(int keyCode, boolean control) {
		return false;
	}

	default void tick() {
	}

	/** Leaving the page or the menu. */
	default void close() {
	}
}
