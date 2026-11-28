package com.mystaria.phantasmon.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.battle.BattleLobbyState;
import com.mystaria.phantasmon.client.battle.LiveBattleController;

/**
 * A window of the battle lobby for one of its choices (2026-11-27): the League Reborn field, the scene, the ambiance,
 * the intro. Like the format picker it opens over the lobby; the choices are cards in a grid, split into groups, each
 * with its preview picture behind it ({@link LobbyPreviews}) so its look is seen at a glance. A click picks it and goes
 * back to the lobby. The grid scrolls with the wheel or by dragging its scroll bar.
 */
public final class PhantasmonChoicePickerScreen extends PhantasmonCanvasScreen {

	/** A card: what is sent when picked, its name, its preview ({@code previewKind} / {@code previewId}), ticked or not. */
	public record Choice(String id, String name, String previewKind, String previewId, boolean selected) {
	}

	/** A group of cards under a header ({@code title} null: no header). */
	public record Group(String title, List<Choice> choices) {
	}

	private static final int AREA_X = 15;
	private static final int AREA_Y = 72;
	private static final int AREA_W = 1570;
	private static final int AREA_H = 758;
	private static final int LIST_TOP = AREA_Y + 46;
	private static final int LIST_BOTTOM = AREA_Y + AREA_H - 10;
	private static final int LIST_H = LIST_BOTTOM - LIST_TOP;
	private static final int TRACK_W = 6;
	private static final int TRACK_X = AREA_X + AREA_W - 16;
	private static final int COLUMNS = 5;
	private static final int CARD_GAP = 10;
	private static final int CARD_W = (AREA_W - 18 - TRACK_W - 10 - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
	private static final int IMAGE_H = CARD_W * 9 / 16;
	private static final int NAME_H = 32;
	private static final int CARD_H = IMAGE_H + NAME_H;
	private static final int HEADER_H = 30;
	private static final int GROUP_GAP = 8;
	private static final int CLOSE_SIZE = 38;
	private static final int CLOSE_X = 1585 - 10 - CLOSE_SIZE;
	private static final int CLOSE_Y = 20;

	private final PhantasmonBattleLobbyScreen lobbyScreen;
	private final LiveBattleController controller;
	private final String titleKey;
	private final Function<BattleLobbyState, List<Group>> groups;
	private final Function<BattleLobbyState, String> current;
	private final Predicate<BattleLobbyState> editable;
	private final Consumer<Choice> onPick;
	private int scroll;
	private boolean scrolledToSelection;
	private boolean dragging;
	private double dragOffset;

	/**
	 * @param titleKey the window's title; {@code current} the name of what is chosen now (top right);
	 *        {@code editable} false (fixed by an NPC, player ready...) closes the window; {@code onPick} sends the choice
	 */
	public PhantasmonChoicePickerScreen(PhantasmonBattleLobbyScreen lobbyScreen, LiveBattleController controller, String titleKey,
			Function<BattleLobbyState, List<Group>> groups, Function<BattleLobbyState, String> current,
			Predicate<BattleLobbyState> editable, Consumer<Choice> onPick) {
		super(Component.translatable(titleKey));
		this.lobbyScreen = lobbyScreen;
		this.controller = controller;
		this.titleKey = titleKey;
		this.groups = groups;
		this.current = current;
		this.editable = editable;
		this.onPick = onPick;
	}

	@Override
	public void tick() {
		BattleLobbyState state = controller.lobby();
		if (state == null) {
			minecraft.setScreen(null); // the lobby ended (battle started, cancelled): nothing to go back to
		} else if (!editable.test(state)) {
			back();
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 256) {
			back();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void back() {
		minecraft.setScreen(controller.lobby() != null ? lobbyScreen : null);
	}

	// =====================================================================
	// Layout
	// =====================================================================

	/** A line of the grid: a group header ({@code title}) or a card at column {@code column}; {@code y} from the top. */
	private record Cell(String title, Choice choice, int column, int y) {
	}

	private static List<Cell> layout(List<Group> groups) {
		List<Cell> cells = new ArrayList<>();
		int y = 0;
		for (Group group : groups) {
			if (group.choices().isEmpty()) {
				continue;
			}
			if (y > 0) {
				y += GROUP_GAP;
			}
			if (group.title() != null) {
				cells.add(new Cell(group.title(), null, 0, y));
				y += HEADER_H;
			}
			for (int i = 0; i < group.choices().size(); i++) {
				cells.add(new Cell(null, group.choices().get(i), i % COLUMNS, y + (i / COLUMNS) * (CARD_H + CARD_GAP)));
			}
			int rows = (group.choices().size() + COLUMNS - 1) / COLUMNS;
			y += rows * (CARD_H + CARD_GAP) - CARD_GAP;
		}
		return cells;
	}

	private static int contentHeight(List<Cell> cells) {
		int height = 0;
		for (Cell cell : cells) {
			height = Math.max(height, cell.y() + (cell.choice() != null ? CARD_H : HEADER_H));
		}
		return height;
	}

	private static int cardX(int column) {
		return AREA_X + 9 + column * (CARD_W + CARD_GAP);
	}

	private List<Cell> cells() {
		BattleLobbyState state = controller.lobby();
		return state == null ? List.of() : layout(groups.apply(state));
	}

	private int clampScroll(int value) {
		return Math.max(0, Math.min(value, Math.max(0, contentHeight(cells()) - LIST_H)));
	}

	private boolean overflows() {
		return contentHeight(cells()) > LIST_H;
	}

	private int[] thumb() {
		int content = contentHeight(cells());
		int thumbH = Math.max(30, LIST_H * LIST_H / Math.max(1, content));
		int thumbY = LIST_TOP + (LIST_H - thumbH) * scroll / Math.max(1, content - LIST_H);
		return new int[] { thumbY, thumbH };
	}

	private void scrollThumbTo(double thumbTop) {
		int[] thumb = thumb();
		int range = LIST_H - thumb[1];
		double ratio = range <= 0 ? 0 : (thumbTop - LIST_TOP) / range;
		scroll = clampScroll((int) Math.round(ratio * Math.max(0, contentHeight(cells()) - LIST_H)));
	}

	private Choice choiceAt(double x, double y) {
		if (y < LIST_TOP || y >= LIST_BOTTOM) {
			return null;
		}
		for (Cell cell : cells()) {
			if (cell.choice() != null && inside(x, y, cardX(cell.column()), LIST_TOP + cell.y() - scroll, CARD_W, CARD_H)) {
				return cell.choice();
			}
		}
		return null;
	}

	// =====================================================================
	// Input
	// =====================================================================

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || controller.lobby() == null) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		double x = toCanvasX(mouseX);
		double y = toCanvasY(mouseY);
		if (inside(x, y, CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE)) {
			back();
			return true;
		}
		if (overflows() && inside(x, y, TRACK_X - 5, LIST_TOP, TRACK_W + 10, LIST_H)) {
			int[] thumb = thumb();
			if (y < thumb[0] || y >= thumb[0] + thumb[1]) {
				scrollThumbTo(y - thumb[1] / 2.0);
				thumb = thumb();
			}
			dragging = true;
			dragOffset = y - thumb[0];
			return true;
		}
		Choice choice = choiceAt(x, y);
		if (choice != null) {
			com.mystaria.phantasmon.client.audio.PhantasmonSounds.play(com.mystaria.phantasmon.client.audio.PhantasmonSounds.Sfx.PRESSING_A);
			if (!choice.selected()) {
				onPick.accept(choice);
			}
			back();
		}
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (!dragging) {
			return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
		}
		scrollThumbTo(toCanvasY(mouseY) - dragOffset);
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = clampScroll(scroll - (int) Math.round(scrollY * (CARD_H + CARD_GAP) / 2.0));
		return true;
	}

	// =====================================================================
	// Rendering
	// =====================================================================

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		BattleLobbyState state = controller.lobby();
		renderWorldBackdrop(graphics, mouseX, mouseY, partialTick);
		if (state == null) {
			return;
		}
		List<Cell> cells = layout(groups.apply(state));
		if (!scrolledToSelection) {
			// Opened on a choice far down: the grid starts on it.
			scrolledToSelection = true;
			for (Cell cell : cells) {
				if (cell.choice() != null && cell.choice().selected()) {
					scroll = clampScroll(cell.y() - LIST_H / 2 + CARD_H / 2);
				}
			}
		}
		double mx = toCanvasX(mouseX);
		double my = toCanvasY(mouseY);
		beginCanvas(graphics);
		renderRootPanel(graphics);
		renderHeaderPlates(graphics);
		drawText(graphics, upper(Component.translatable(titleKey).getString()), 31, 32, 2f, WHITE, true, 1.5f);
		String now = Component.translatable("phantasmon.battle.choice_picker.current", current.apply(state)).getString();
		float nowWidth = Math.min(textWidth(now, 2f, false, 0f), 600);
		drawText(graphics, fitText(now, 600, 2f, false, 0f), CLOSE_X - 16 - nowWidth, 32, 2f, CYAN, false, 0f);
		renderCloseButton(graphics, CLOSE_X, CLOSE_Y, CLOSE_SIZE, inside(mx, my, CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE));

		long count = cells.stream().filter(cell -> cell.choice() != null).count();
		renderRailFrame(graphics, AREA_X, AREA_Y, AREA_W, AREA_H, Component.translatable(titleKey).getString() + "  ·  " + count, null);
		Choice hovered = dragging ? null : choiceAt(mx, my);
		enableCanvasScissor(graphics, AREA_X, LIST_TOP, AREA_W, LIST_H);
		for (Cell cell : cells) {
			int y = LIST_TOP + cell.y() - scroll;
			if (y + CARD_H < LIST_TOP || y > LIST_BOTTOM) {
				continue;
			}
			if (cell.choice() == null) {
				drawText(graphics, upper(cell.title()), AREA_X + 11, y + 8, 1f, CYAN, true, 1.5f);
				graphics.fill(AREA_X + 9, y + HEADER_H - 6, TRACK_X - 10, y + HEADER_H - 5, LINE_30);
				continue;
			}
			renderCard(graphics, cell.choice(), cardX(cell.column()), y, cell.choice().equals(hovered));
		}
		graphics.disableScissor();
		if (contentHeight(cells) > LIST_H) {
			graphics.fill(TRACK_X, LIST_TOP, TRACK_X + TRACK_W, LIST_BOTTOM, LINE_20);
			int[] thumb = thumb();
			boolean hot = dragging || inside(mx, my, TRACK_X - 5, thumb[0], TRACK_W + 10, thumb[1]);
			graphics.fill(TRACK_X, thumb[0], TRACK_X + TRACK_W, thumb[0] + thumb[1], hot ? WHITE : CYAN);
		}
		renderFooterHints(graphics, Component.translatable("phantasmon.battle.choice_picker.hints").getString(), 1546);
		endCanvas(graphics);
	}

	private void renderCard(GuiGraphics g, Choice choice, int x, int y, boolean hovered) {
		blackBorder(g, x, y, CARD_W, CARD_H, palette().black());
		g.fill(x, y, x + CARD_W, y + CARD_H, choice.selected() ? SLOT_SELECTED_BG : hovered ? SLOT_HOVER_BG : SLOT_BG);
		LobbyPreviews.Preview preview = LobbyPreviews.get(choice.previewKind(), choice.previewId());
		if (preview != null) {
			// The picture covers the card's image area, cut in the middle if its shape differs.
			float scale = Math.max((float) CARD_W / preview.width(), (float) IMAGE_H / preview.height());
			int u = Math.round((preview.width() - CARD_W / scale) / 2f);
			int v = Math.round((preview.height() - IMAGE_H / scale) / 2f);
			RenderSystem.enableBlend();
			g.blit(preview.texture(), x, y, CARD_W, IMAGE_H, u, v, Math.round(CARD_W / scale), Math.round(IMAGE_H / scale),
					preview.width(), preview.height());
			if (!hovered && !choice.selected()) {
				g.fill(x, y, x + CARD_W, y + IMAGE_H, 0x30000000);
			}
		} else {
			// No picture yet: a backdrop tinted by the id, its name large in the middle.
			int hue = (choice.previewId() == null ? 0 : choice.previewId().hashCode()) & 0xFFFFFF;
			int top = 0xFF000000 | (((hue >> 16) & 0xFF) / 4 + 0x10) << 16 | (((hue >> 8) & 0xFF) / 4 + 0x18) << 8 | ((hue & 0xFF) / 4 + 0x24);
			g.fillGradient(x, y, x + CARD_W, y + IMAGE_H, top, 0xFF05080C);
			drawCentered(g, fitText(choice.name(), CARD_W - 24, 3f, true, 0.5f), x + CARD_W / 2f, y + IMAGE_H / 2f - 12, 3f,
					0x50FFFFFF, true, 0.5f);
		}
		// Name strip under the picture.
		g.fill(x, y + IMAGE_H, x + CARD_W, y + CARD_H, choice.selected() ? SLOT_SELECTED_BG : 0xE0081016);
		g.fill(x, y + IMAGE_H, x + CARD_W, y + IMAGE_H + 1, LINE_30);
		String name = (choice.selected() ? "✔ " : "") + choice.name();
		drawText(g, fitText(name, CARD_W - 20, 2f, false, 0f), x + 10, y + IMAGE_H + 9, 2f, choice.selected() ? READY_TEXT : WHITE,
				false, 0f);
		raisedEdge(g, x, y, CARD_W, CARD_H);
		if (choice.selected()) {
			outline(g, x, y, CARD_W, CARD_H, CYAN);
			outline(g, x + 1, y + 1, CARD_W - 2, CARD_H - 2, CYAN);
		} else if (hovered) {
			outline(g, x, y, CARD_W, CARD_H, WHITE);
		}
	}
}
