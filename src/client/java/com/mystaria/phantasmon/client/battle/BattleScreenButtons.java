package com.mystaria.phantasmon.client.battle;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.audio.PhantasmonMusic;

/**
 * Three Phantasmon buttons over Cobblemon's battle screen during a Ghost battle (Adrien 2026-10-05): next music,
 * staged / free camera, turn timer. Drawn over the screen and clicked through Fabric's screen events rather than
 * added as widgets — Cobblemon rebuilds its own widgets at every sub-screen (moves, switch...). Right side, under the
 * opponent's tile, in the Phantasmon look.
 */
public final class BattleScreenButtons {

	private static final int W = 124;
	private static final int H = 18;
	private static final int GAP = 3;
	private static final int RIGHT_INSET = 12;
	/** Under Cobblemon's opponent tile (inset 10, tile 40 high). */
	private static final int TOP = 58;

	private static final int BG_TOP = 0xE0123A55;
	private static final int BG_BOTTOM = 0xE00B2236;
	private static final int BG_HOVER_TOP = 0xF01C5274;
	private static final int BG_HOVER_BOTTOM = 0xF0123A55;
	private static final int CYAN = 0xFF50E6FF;
	private static final int GREEN = 0xFF76FFB0;
	private static final int DIM = 0xFF6C8C96;
	private static final int RED = 0xFFFF5078;

	private BattleScreenButtons() {
	}

	public static void register(LiveBattleController controller) {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!"BattleGUI".equals(screen.getClass().getSimpleName())) {
				return;
			}
			ScreenEvents.afterRender(screen).register((current, graphics, mouseX, mouseY, tickDelta) -> {
				if (BattleCameraDirector.inBattle()) {
					render(current, graphics, mouseX, mouseY, controller);
				}
			});
			ScreenMouseEvents.allowMouseClick(screen).register((current, mouseX, mouseY, button) -> {
				if (button != 0 || !BattleCameraDirector.inBattle()) {
					return true;
				}
				int index = buttonAt(current, mouseX, mouseY);
				if (index < 0) {
					return true;
				}
				click(index, controller);
				return false; // ours: Cobblemon's screen doesn't see this click
			});
		});
	}

	private static int x(Screen screen) {
		return screen.width - RIGHT_INSET - W;
	}

	private static int y(int index) {
		return TOP + index * (H + GAP);
	}

	private static int buttonAt(Screen screen, double mouseX, double mouseY) {
		int x = x(screen);
		for (int i = 0; i < 3; i++) {
			if (mouseX >= x && mouseX < x + W && mouseY >= y(i) && mouseY < y(i) + H) {
				return i;
			}
		}
		return -1;
	}

	private static void click(int index, LiveBattleController controller) {
		Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
				net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
		switch (index) {
			case 0 -> PhantasmonMusic.skip();
			case 1 -> BattleCameraDirector.toggle();
			default -> {
				if (!controller.timerOn()) {
					controller.enableTimer();
				}
			}
		}
	}

	private static void render(Screen screen, GuiGraphics g, int mouseX, int mouseY, LiveBattleController controller) {
		Font font = Minecraft.getInstance().font;
		int x = x(screen);
		int hovered = buttonAt(screen, mouseX, mouseY);
		g.pose().pushPose();
		g.pose().translate(0, 0, 400);
		button(g, font, x, y(0), hovered == 0, Component.translatable("phantasmon.battle.buttons.music").getString(),
				PhantasmonMusic.skippable() ? CYAN : DIM);
		boolean staged = BattleCameraDirector.active();
		button(g, font, x, y(1), hovered == 1, Component.translatable(staged ? "phantasmon.battle.buttons.camera_staged"
				: "phantasmon.battle.buttons.camera_free").getString(), staged ? GREEN : CYAN);
		// Once on, the timer button becomes the countdown (Adrien 2026-10-05).
		boolean timer = controller.timerOn();
		long left = controller.timerSecondsLeft();
		String timerLabel = !timer ? Component.translatable("phantasmon.battle.buttons.timer").getString()
				: left < 0 ? Component.translatable("phantasmon.battle.buttons.timer_waiting").getString()
				: "⏱ " + left + " s";
		int timerColor = !timer ? CYAN : left >= 0 && left <= 10 ? RED : GREEN;
		button(g, font, x, y(2), hovered == 2 && !timer, timerLabel, timerColor);
		g.pose().popPose();
	}

	private static void button(GuiGraphics g, Font font, int x, int y, boolean hovered, String label, int accent) {
		g.fillGradient(x, y, x + W, y + H, hovered ? BG_HOVER_TOP : BG_TOP, hovered ? BG_HOVER_BOTTOM : BG_BOTTOM);
		g.fill(x, y, x + W, y + 1, accent);
		g.fill(x, y + H - 1, x + W, y + H, accent);
		g.fill(x, y, x + 1, y + H, accent);
		g.fill(x + W - 1, y, x + W, y + H, accent);
		String text = font.width(label) > W - 8 ? font.plainSubstrByWidth(label, W - 12) + "…" : label;
		g.drawString(font, text, x + (W - font.width(text)) / 2, y + (H - 8) / 2, accent, true);
	}
}
