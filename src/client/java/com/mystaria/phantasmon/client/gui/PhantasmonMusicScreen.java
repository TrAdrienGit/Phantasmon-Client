package com.mystaria.phantasmon.client.gui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.mystaria.phantasmon.client.audio.MusicSettings;
import com.mystaria.phantasmon.client.audio.PhantasmonMusic;

/**
 * Picks which music files of the resource pack may play (Adrien 2026-10-05): one tab per folder (lobby, intro,
 * battle, victory, defeat), one row per {@code .ogg} with a tick box and a listen button. Unticked files are never
 * picked ({@link MusicSettings}). Opened from the battle lobby and from Mod Menu; back to {@code parent} on close.
 */
public final class PhantasmonMusicScreen extends PhantasmonCanvasScreen {

	private static final PhantasmonMusic.Track[] TABS = PhantasmonMusic.Track.values();
	private static final int TAB_X = 250;
	private static final int TAB_W = 212;
	private static final int TAB_GAP = 10;
	private static final int TAB_Y = 84;
	private static final int TAB_H = 44;
	private static final int LIST_X = 250;
	private static final int LIST_Y = 146;
	private static final int LIST_W = 1100;
	private static final int LIST_H = 674;
	private static final int ROW_H = 60;
	private static final int VISIBLE_ROWS = LIST_H / ROW_H;
	private static final int CLOSE_SIZE = 36;
	private static final int CLOSE_X = 1585 - 6 - CLOSE_SIZE;
	private static final int CLOSE_Y = 15 + (48 - CLOSE_SIZE) / 2;

	private final Screen parent;
	private int tab = PhantasmonMusic.Track.BATTLE.ordinal();
	private int scroll;

	public PhantasmonMusicScreen(Screen parent) {
		super(Component.translatable("phantasmon.music.screen.title"));
		this.parent = parent;
	}

	@Override
	public void onClose() {
		PhantasmonMusic.endPreview();
		Minecraft.getInstance().setScreen(parent);
	}

	private List<ResourceLocation> files() {
		return PhantasmonMusic.allFiles(TABS[tab]);
	}

	// =====================================================================
	// Input
	// =====================================================================

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		double x = toCanvasX(mouseX);
		double y = toCanvasY(mouseY);
		if (inside(x, y, CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE) || inside(x, y, 1475, 849, 97, 28)) {
			onClose();
			return true;
		}
		for (int i = 0; i < TABS.length; i++) {
			if (inside(x, y, TAB_X + i * (TAB_W + TAB_GAP), TAB_Y, TAB_W, TAB_H)) {
				tab = i;
				scroll = 0;
				return true;
			}
		}
		List<ResourceLocation> files = files();
		for (int row = 0; row < VISIBLE_ROWS && scroll + row < files.size(); row++) {
			ResourceLocation file = files.get(scroll + row);
			int rowY = LIST_Y + row * ROW_H;
			if (inside(x, y, LIST_X + LIST_W - 190, rowY + 12, 170, 36)) {
				if (PhantasmonMusic.previewing(file)) {
					PhantasmonMusic.endPreview();
				} else {
					PhantasmonMusic.preview(file);
				}
				return true;
			}
			if (inside(x, y, LIST_X, rowY, LIST_W - 200, ROW_H)) {
				MusicSettings.setEnabled(file, !MusicSettings.enabled(file));
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int max = Math.max(0, files().size() - VISIBLE_ROWS);
		scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
		return true;
	}

	// =====================================================================
	// Rendering
	// =====================================================================

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		renderWorldBackdrop(g, mouseX, mouseY, partialTick);
		double mx = toCanvasX(mouseX);
		double my = toCanvasY(mouseY);
		beginCanvas(g);
		renderRootPanel(g);
		renderHeaderPlates(g, 1500);
		drawText(g, upper(Component.translatable("phantasmon.music.screen.title").getString()), 31, 32, 2f, WHITE, true, 1.5f);
		renderCloseButton(g, CLOSE_X, CLOSE_Y, CLOSE_SIZE, inside(mx, my, CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE));

		for (int i = 0; i < TABS.length; i++) {
			int tx = TAB_X + i * (TAB_W + TAB_GAP);
			boolean selected = i == tab;
			renderPrimaryButtonFrame(g, tx, TAB_Y, TAB_W, TAB_H, inside(mx, my, tx, TAB_Y, TAB_W, TAB_H), selected);
			String label = upper(Component.translatable("phantasmon.music.tab." + TABS[i].name().toLowerCase(java.util.Locale.ROOT)).getString())
					+ " (" + PhantasmonMusic.allFiles(TABS[i]).size() + ")";
			drawCentered(g, label, tx + TAB_W / 2f, TAB_Y + 15, 2f, selected ? READY_TEXT : CYAN, true, 1f);
		}

		g.fill(LIST_X, LIST_Y, LIST_X + LIST_W, LIST_Y + LIST_H, INFO_BOX_BG);
		outline(g, LIST_X, LIST_Y, LIST_W, LIST_H, LINE_20);
		List<ResourceLocation> files = files();
		if (files.isEmpty()) {
			String empty = Component.translatable("phantasmon.music.screen.empty",
					"assets/phantasmon/sounds/music/" + TABS[tab].name().toLowerCase(java.util.Locale.ROOT) + "/").getString();
			drawCentered(g, fitText(empty, LIST_W - 40, 2f, false, 0f), LIST_X + LIST_W / 2f, LIST_Y + LIST_H / 2f - 7, 2f, DIM, false, 0f);
		}
		for (int row = 0; row < VISIBLE_ROWS && scroll + row < files.size(); row++) {
			renderRow(g, files.get(scroll + row), LIST_Y + row * ROW_H, mx, my);
		}
		if (files.size() > VISIBLE_ROWS) {
			int trackH = LIST_H - 8;
			int thumbH = Math.max(30, trackH * VISIBLE_ROWS / files.size());
			int thumbY = LIST_Y + 4 + (trackH - thumbH) * scroll / Math.max(1, files.size() - VISIBLE_ROWS);
			g.fill(LIST_X + LIST_W - 8, thumbY, LIST_X + LIST_W - 4, thumbY + thumbH, CYAN2);
		}

		renderFooterBar(g, Component.translatable("phantasmon.music.screen.hint").getString(), MUTED, 1420);
		renderButton(g, 1475, 849, 97, 28, "phantasmon.music.screen.close", false, inside(mx, my, 1475, 849, 97, 28));
		endCanvas(g);
	}

	private void renderRow(GuiGraphics g, ResourceLocation file, int rowY, double mx, double my) {
		boolean enabled = MusicSettings.enabled(file);
		boolean hovered = inside(mx, my, LIST_X, rowY, LIST_W - 200, ROW_H);
		if (hovered) {
			g.fill(LIST_X + 1, rowY, LIST_X + LIST_W - 1, rowY + ROW_H, SLOT_HOVER_BG);
		}
		g.fill(LIST_X + 10, rowY + ROW_H - 1, LIST_X + LIST_W - 10, rowY + ROW_H, LINE_12);

		// Tick box.
		int boxX = LIST_X + 22;
		int boxY = rowY + 16;
		g.fill(boxX, boxY, boxX + 28, boxY + 28, MOVE_BG);
		outline(g, boxX, boxY, 28, 28, enabled ? READY_BORDER : LINE_30);
		if (enabled) {
			drawCentered(g, "✔", boxX + 14, boxY + 7, 2f, READY_TEXT, false, 0f);
		}
		drawText(g, fitText(PhantasmonMusic.title(file), LIST_W - 320, 2f, false, 0f), boxX + 48, rowY + 23, 2f, enabled ? WHITE : DIM, false, 0f);

		// Listen / stop.
		int bx = LIST_X + LIST_W - 190;
		boolean listening = PhantasmonMusic.previewing(file);
		boolean bHovered = inside(mx, my, bx, rowY + 12, 170, 36);
		int[] gradient = bHovered ? STD_BUTTON_HOVER : STD_BUTTON;
		g.fillGradient(bx, rowY + 12, bx + 170, rowY + 48, gradient[0], gradient[1]);
		outline(g, bx, rowY + 12, 170, 36, listening ? READY_BORDER : CYAN);
		String label = upper(Component.translatable(listening ? "phantasmon.music.screen.stop" : "phantasmon.music.screen.listen").getString());
		drawCentered(g, label, bx + 85, rowY + 23, 2f, listening ? READY_TEXT : WHITE, true, 1f);
	}
}
