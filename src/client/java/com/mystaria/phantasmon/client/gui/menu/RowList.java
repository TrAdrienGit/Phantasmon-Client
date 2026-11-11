package com.mystaria.phantasmon.client.gui.menu;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The list of a page, as in Showtime's menu: rows of one line — a note, a sub-title, a choice, a switch (with optional
 * listen and star buttons), a row of buttons, a text field — scrolled with the wheel or the scrollbar. Rebuilt by its
 * page whenever something changes.
 */
final class RowList {

	static final int ROW_H = 18;
	static final int PITCH = 20;
	private static final int BAR_W = 5;

	enum Style { NOTE, HEADER, SELECT, SWITCH, BUTTONS, FIELD, ITEM }

	/** A button of a {@link Style#BUTTONS} row. */
	record Button(Component label, boolean primary, boolean danger, Runnable action) {
	}

	/** A text field of a {@link Style#FIELD} row: its value, read and changed through these. */
	static final class Field {
		String value;
		final int max;
		final String placeholder;
		final java.util.function.Predicate<String> valid;
		final Runnable submit;

		Field(String value, int max, String placeholder, java.util.function.Predicate<String> valid, Runnable submit) {
			this.value = value;
			this.max = max;
			this.placeholder = placeholder;
			this.valid = valid;
			this.submit = submit;
		}
	}

	record Row(Style style, Component text, boolean on, Runnable click, Runnable side, Component sideText, Runnable star,
			boolean starred, List<Button> buttons, Field field) {
	}

	private final List<Row> rows = new ArrayList<>();
	private int x;
	private int y;
	private int right;
	private int bottom;
	private int scroll;
	private boolean dragging;
	private int grab;
	private Field focused;

	void clear() {
		rows.clear();
	}

	void note(Component text) {
		rows.add(new Row(Style.NOTE, text, false, null, null, null, null, false, null, null));
	}

	void header(Component text) {
		rows.add(new Row(Style.HEADER, text, false, null, null, null, null, false, null, null));
	}

	void select(Component text, boolean on, Runnable click) {
		rows.add(new Row(Style.SELECT, text, on, click, null, null, null, false, null, null));
	}

	void toggle(Component text, boolean on, Runnable click) {
		rows.add(new Row(Style.SWITCH, text, on, click, null, null, null, false, null, null));
	}

	void toggle(Component text, boolean on, Runnable click, Runnable side, Component sideText, Runnable star, boolean starred) {
		rows.add(new Row(Style.SWITCH, text, on, click, side, sideText, star, starred, null, null));
	}

	void buttons(Button... buttons) {
		rows.add(new Row(Style.BUTTONS, Component.empty(), false, null, null, null, null, false, List.of(buttons), null));
	}

	/** A line with a ✕ on its right (an entry to delete): {@code delete} runs on the ✕. */
	void item(Component text, Runnable delete) {
		rows.add(new Row(Style.ITEM, text, false, null, delete, null, null, false, null, null));
	}

	void field(Component label, Field field) {
		rows.add(new Row(Style.FIELD, label, false, null, null, null, null, false, null, field));
	}

	void layout(int left, int top, int rightEdge, int bottomEdge) {
		x = left;
		y = top;
		right = rightEdge;
		bottom = bottomEdge;
		scroll = Mth.clamp(scroll, 0, maxScroll());
	}

	void resetScroll() {
		scroll = 0;
	}

	private int height(Row row) {
		return row.style() == Style.FIELD ? PITCH + 10 : PITCH;
	}

	private int total() {
		int total = 0;
		for (Row row : rows) {
			total += height(row);
		}
		return Math.max(0, total - 2);
	}

	private int maxScroll() {
		return Math.max(0, total() - (bottom - y));
	}

	private int rowsRight() {
		return maxScroll() > 0 ? right - BAR_W - 4 : right;
	}

	// ---------------------------------------------------------------- drawing

	void render(GuiGraphics g, int mouseX, int mouseY) {
		MenuUi.Palette c = MenuUi.p();
		int w = rowsRight() - x;
		g.enableScissor(x, y, right, bottom);
		int top = y - scroll;
		for (Row row : rows) {
			int rowTop = top;
			top += height(row);
			if (rowTop + height(row) < y || rowTop > bottom) {
				continue;
			}
			switch (row.style()) {
				case NOTE -> MenuUi.text(g, row.text(), x, rowTop + 5, w, c.muted());
				case HEADER -> {
					MenuUi.text(g, row.text().copy().withStyle(s -> s.withBold(true)), x, rowTop + 6, w, c.accent());
					g.fill(x, rowTop + 16, x + w, rowTop + 17, c.edge());
				}
				case SELECT -> {
					boolean hovered = MenuUi.inside(mouseX, mouseY, x, rowTop, w, ROW_H);
					MenuUi.row(g, x, rowTop, w, ROW_H, hovered);
					if (row.on()) {
						MenuUi.selectedRow(g, x, rowTop, w, ROW_H);
						g.fill(x + w - 12, rowTop + 7, x + w - 8, rowTop + 11, c.accent());
					}
					MenuUi.text(g, row.text(), x + 7, rowTop + 5, w - 22, row.on() ? c.white() : c.muted());
				}
				case SWITCH -> {
					boolean hovered = MenuUi.inside(mouseX, mouseY, x, rowTop, w, ROW_H);
					MenuUi.row(g, x, rowTop, w, ROW_H, hovered);
					MenuUi.toggle(g, x + w - 26, rowTop + 3, row.on());
					int textW = w - 38;
					if (row.side() != null) {
						int sideX = x + w - 48;
						MenuUi.mini(g, sideX, rowTop + 2, row.sideText(), MenuUi.inside(mouseX, mouseY, sideX, rowTop, 18, ROW_H), false,
								c.white());
						textW -= 22;
					}
					if (row.star() != null) {
						int starX = x + w - 70;
						MenuUi.mini(g, starX, rowTop + 2, Component.literal(row.starred() ? "★" : "☆"),
								MenuUi.inside(mouseX, mouseY, starX, rowTop, 18, ROW_H), false, row.starred() ? c.star() : c.muted());
						textW -= 22;
					}
					MenuUi.text(g, row.text(), x + 7, rowTop + 5, textW, c.white());
				}
				case BUTTONS -> {
					List<Button> buttons = row.buttons();
					int each = (w - 4 * (buttons.size() - 1)) / buttons.size();
					for (int i = 0; i < buttons.size(); i++) {
						Button button = buttons.get(i);
						int bx = x + i * (each + 4);
						boolean hover = MenuUi.inside(mouseX, mouseY, bx, rowTop, each, ROW_H);
						if (button.primary()) {
							MenuUi.primary(g, bx, rowTop, each, ROW_H, button.label(), hover, true);
						} else {
							MenuUi.button(g, bx, rowTop, each, ROW_H, button.label(), hover, button.danger(), true);
						}
					}
				}
				case ITEM -> {
					boolean hovered = MenuUi.inside(mouseX, mouseY, x, rowTop, w, ROW_H);
					MenuUi.row(g, x, rowTop, w, ROW_H, hovered);
					MenuUi.text(g, row.text(), x + 7, rowTop + 5, w - 34, c.white());
					int del = x + w - 22;
					MenuUi.mini(g, del, rowTop + 2, Component.literal("✕"), MenuUi.inside(mouseX, mouseY, del, rowTop, 18, ROW_H), true,
							c.white());
				}
				case FIELD -> {
					Field field = row.field();
					MenuUi.field(g, x + 1, rowTop + 11, w - 2, ROW_H, row.text(), field.value, field.placeholder, field == focused,
							field.valid != null && !field.valid.test(field.value));
				}
			}
		}
		g.disableScissor();
		int[] thumb = MenuUi.thumb(y, bottom - y, total(), scroll);
		MenuUi.scrollbar(g, right - BAR_W, y, bottom - y, total(), scroll,
				dragging || thumb != null && MenuUi.inside(mouseX, mouseY, right - BAR_W, thumb[0], BAR_W, thumb[1]));
	}

	// ---------------------------------------------------------------- input

	boolean click(double mouseX, double mouseY) {
		int[] thumb = MenuUi.thumb(y, bottom - y, total(), scroll);
		if (thumb != null && MenuUi.inside(mouseX, mouseY, right - BAR_W - 1, y, BAR_W + 2, bottom - y)) {
			boolean onThumb = mouseY >= thumb[0] && mouseY < thumb[0] + thumb[1];
			grab = onThumb ? (int) mouseY - thumb[0] : thumb[1] / 2;
			dragging = true;
			dragBar(mouseY);
			return true;
		}
		if (mouseY < y || mouseY >= bottom) {
			return false;
		}
		int w = rowsRight() - x;
		int top = y - scroll;
		Field wasFocused = focused;
		focused = null;
		for (Row row : rows) {
			int rowTop = top;
			top += height(row);
			if (!MenuUi.inside(mouseX, mouseY, x, rowTop, w, height(row))) {
				continue;
			}
			Runnable action = null;
			switch (row.style()) {
				case SELECT -> action = row.click();
				case SWITCH -> {
					if (row.star() != null && MenuUi.inside(mouseX, mouseY, x + w - 70, rowTop, 18, ROW_H)) {
						action = row.star();
					} else if (row.side() != null && MenuUi.inside(mouseX, mouseY, x + w - 48, rowTop, 18, ROW_H)) {
						action = row.side();
					} else {
						action = row.click();
					}
				}
				case BUTTONS -> {
					List<Button> buttons = row.buttons();
					int each = (w - 4 * (buttons.size() - 1)) / buttons.size();
					int index = Math.min(buttons.size() - 1, (int) ((mouseX - x) / (each + 4)));
					action = buttons.get(index).action();
				}
				case FIELD -> {
					focused = row.field();
					return true;
				}
				case ITEM -> {
					if (MenuUi.inside(mouseX, mouseY, x + w - 22, rowTop, 18, ROW_H)) {
						action = row.side();
					}
				}
				default -> {
				}
			}
			if (action != null) {
				MenuUi.click();
				action.run();
				return true;
			}
			return wasFocused != null;
		}
		return false;
	}

	private void dragBar(double mouseY) {
		int[] thumb = MenuUi.thumb(y, bottom - y, total(), scroll);
		if (thumb == null) {
			return;
		}
		int view = bottom - y - thumb[1];
		float t = view <= 0 ? 0 : (float) ((mouseY - grab - y) / view);
		scroll = Mth.clamp(Math.round(t * maxScroll()), 0, maxScroll());
	}

	boolean drag(double mouseY) {
		if (dragging) {
			dragBar(mouseY);
			return true;
		}
		return false;
	}

	void release() {
		dragging = false;
	}

	boolean scroll(double mouseX, double mouseY, double amount) {
		if (!MenuUi.inside(mouseX, mouseY, x, y, right - x, bottom - y)) {
			return false;
		}
		scroll = Mth.clamp(scroll - (int) Math.round(amount * PITCH * 2), 0, maxScroll());
		return true;
	}

	boolean focused() {
		return focused != null;
	}

	boolean charTyped(char chr) {
		if (focused == null || chr < 32 || chr == 127) {
			return false;
		}
		if (focused.value.length() < focused.max) {
			focused.value += chr;
		}
		return true;
	}

	/** Backspace (Ctrl: all), Ctrl+V, Enter (the field's submit), Escape (leaves the field). */
	boolean key(int keyCode, boolean control) {
		if (focused == null) {
			return false;
		}
		switch (keyCode) {
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (!focused.value.isEmpty()) {
					focused.value = control ? "" : focused.value.substring(0, focused.value.length() - 1);
				}
			}
			case GLFW.GLFW_KEY_V -> {
				if (!control) {
					return false;
				}
				String clip = Minecraft.getInstance().keyboardHandler.getClipboard().strip();
				if ((focused.value + clip).length() <= focused.max) {
					focused.value += clip;
				}
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				if (focused.submit != null) {
					focused.submit.run();
				}
			}
			case GLFW.GLFW_KEY_ESCAPE -> focused = null;
			default -> {
				return false;
			}
		}
		return true;
	}

	/** Helper for pages: a button row's button. */
	static Button button(Component label, Runnable action) {
		return new Button(label, false, false, action);
	}

	static Button primary(Component label, Runnable action) {
		return new Button(label, true, false, action);
	}

	static Button danger(Component label, Runnable action) {
		return new Button(label, false, true, action);
	}

}
