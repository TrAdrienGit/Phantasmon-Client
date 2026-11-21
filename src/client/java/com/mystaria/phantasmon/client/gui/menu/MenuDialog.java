package com.mystaria.phantasmon.client.gui.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A dialog over the menu: a framed panel (bezel, header) with a message, text fields and two buttons — Cancel and the
 * confirmation, accent or red when it destroys something. Tab moves between fields, Enter confirms, Escape cancels.
 * A subclass may add its own body ({@link #bodyHeight}, {@link #renderBody}...): the schematic picker does.
 */
class MenuDialog {

	enum Kind {
		/** A hub name: lower-case letters, digits, _ (a space or - becomes _). */
		NAME,
		/** An anchor name: letters, digits, space, _ and -. */
		ANCHOR,
		NUMBER,
		SIGNED_NUMBER,
		TEXT,
		/** A drop-down list of {@link Input#options}: a click opens it, a click on an entry picks it. */
		CHOICE
	}

	static final class Input {
		final Component label;
		final Kind kind;
		final int max;
		final String placeholder;
		String value;
		List<com.mystaria.phantasmon.client.api.HubManagerTab.Option> options = List.of();

		Input(Component label, Kind kind, int max, String value, String placeholder) {
			this.label = label;
			this.kind = kind;
			this.max = max;
			this.value = value == null ? "" : value;
			this.placeholder = placeholder;
		}

		/** The name shown for the value picked (a choice), or the value itself. */
		String shown() {
			for (var option : options) {
				if (option.value().equals(value)) {
					return option.label().getString();
				}
			}
			return value;
		}
	}

	/** The rows a drop-down list shows at once; the wheel scrolls the rest. */
	private static final int LIST_ROWS = 8;
	private static final int ROW_H = 12;
	/** The choice whose list is open (-1: none), and how far it is scrolled. */
	int openList = -1;
	int listScroll;
	private final com.mystaria.phantasmon.client.gui.ScrollGrab listGrab = new com.mystaria.phantasmon.client.gui.ScrollGrab();

	static final int WIDTH = 300;
	private static final int HEADER = 22;
	private static final int FIELD_H = 16;
	private static final int FIELD_PITCH = 30;
	private static final int BUTTON_H = 18;
	private static final int BUTTON_W = 120;

	final Component title;
	final List<Component> message = new ArrayList<>();
	final List<Input> inputs = new ArrayList<>();
	final Component confirmLabel;
	final boolean danger;
	final Consumer<List<String>> onConfirm;
	Runnable onClose;
	Component error;
	int focused;
	boolean busy;

	int left;
	int top;
	int right;
	int bottom;

	MenuDialog(Component title, Component confirmLabel, boolean danger, Consumer<List<String>> onConfirm) {
		this.title = title;
		this.confirmLabel = confirmLabel;
		this.danger = danger;
		this.onConfirm = onConfirm;
	}

	MenuDialog line(Component text) {
		message.add(text);
		return this;
	}

	MenuDialog input(Component label, Kind kind, int max, String value, String placeholder) {
		inputs.add(new Input(label, kind, max, value, placeholder));
		return this;
	}

	/** A drop-down list of {@code options}, {@code value} picked first. */
	MenuDialog choice(Component label, List<com.mystaria.phantasmon.client.api.HubManagerTab.Option> options, String value) {
		Input input = new Input(label, Kind.CHOICE, 0, value, null);
		input.options = List.copyOf(options);
		inputs.add(input);
		return this;
	}

	int width() {
		return WIDTH;
	}

	/** A custom body's height, between the message and the fields. */
	int bodyHeight() {
		return 0;
	}

	void renderBody(GuiGraphics g, int x, int y, int right, int mouseX, int mouseY) {
	}

	boolean clickBody(double mouseX, double mouseY) {
		return false;
	}

	boolean scrollBody(double mouseX, double mouseY, double amount) {
		return false;
	}

	/** A body's own scrollbar held and dragged. */
	boolean dragBody(double mouseX, double mouseY) {
		return false;
	}

	void releaseBody() {
	}

	/** Dragging: the open list's bar, then the body's. */
	boolean drag(double mouseX, double mouseY) {
		if (listGrab.active()) {
			listScroll = listGrab.dragRows(mouseY, ROW_H);
			return true;
		}
		return dragBody(mouseX, mouseY);
	}

	void release() {
		listGrab.release();
		releaseBody();
	}

	private int messageHeight() {
		int lines = 0;
		var font = Minecraft.getInstance().font;
		for (Component line : message) {
			lines += Math.max(1, font.split(line, width() - 24).size());
		}
		return lines * 10 + (message.isEmpty() ? 0 : 6);
	}

	int height() {
		return HEADER + 10 + messageHeight() + bodyHeight() + inputs.size() * FIELD_PITCH + (error == null ? 0 : 12) + BUTTON_H + 14;
	}

	void layout(int screenWidth, int screenHeight) {
		left = (screenWidth - width()) / 2;
		top = (screenHeight - height()) / 2;
		right = left + width();
		bottom = top + height();
	}

	private int bodyY() {
		return top + HEADER + 8 + messageHeight();
	}

	private int fieldY(int index) {
		return bodyY() + bodyHeight() + 12 + index * FIELD_PITCH;
	}

	private int buttonY() {
		return bottom - BUTTON_H - 8;
	}

	private int cancelX() {
		return right - 12 - 2 * BUTTON_W - 6;
	}

	private int confirmX() {
		return right - 12 - BUTTON_W;
	}

	// ---------------------------------------------------------------- drawing

	void render(GuiGraphics g, int screenWidth, int screenHeight, int mouseX, int mouseY) {
		MenuUi.Palette c = MenuUi.p();
		g.fill(0, 0, screenWidth, screenHeight, MenuUi.VEIL);
		g.fill(left, top, right, bottom, c.dark());
		MenuUi.dots(g, left, top + HEADER, right, bottom);
		MenuUi.header(g, left, top, right, HEADER);
		MenuUi.bezel(g, left, top, right, bottom);
		MenuUi.text(g, title.copy().withStyle(s -> s.withBold(true)), left + 21, top + 7, width() - 30, c.white());
		int y = top + HEADER + 8;
		var font = Minecraft.getInstance().font;
		for (Component line : message) {
			for (var part : font.split(line, width() - 24)) {
				g.drawString(font, part, left + 12, y, c.muted(), false);
				y += 10;
			}
		}
		if (bodyHeight() > 0) {
			renderBody(g, left + 12, bodyY(), right - 12, mouseX, mouseY);
		}
		for (int i = 0; i < inputs.size(); i++) {
			Input input = inputs.get(i);
			if (input.kind == Kind.CHOICE) {
				MenuUi.field(g, left + 12, fieldY(i), width() - 24, FIELD_H, input.label, input.shown(), null, false, false);
				g.drawString(Minecraft.getInstance().font, openList == i ? "\u25B2" : "\u25BC", right - 22, fieldY(i) + 4,
						MenuUi.p().accent(), false);
			} else {
				MenuUi.field(g, left + 12, fieldY(i), width() - 24, FIELD_H, input.label, input.value, input.placeholder,
						i == focused, false);
			}
		}
		if (error != null) {
			MenuUi.text(g, error, left + 12, buttonY() - 12, width() - 24, c.danger());
		}
		MenuUi.button(g, cancelX(), buttonY(), BUTTON_W, BUTTON_H, Component.translatable("phantasmon.hub_manager.cancel"),
				MenuUi.inside(mouseX, mouseY, cancelX(), buttonY(), BUTTON_W, BUTTON_H), false, true);
		boolean hover = !busy && MenuUi.inside(mouseX, mouseY, confirmX(), buttonY(), BUTTON_W, BUTTON_H);
		if (danger) {
			MenuUi.button(g, confirmX(), buttonY(), BUTTON_W, BUTTON_H, confirmLabel, hover, true, !busy);
		} else {
			MenuUi.primary(g, confirmX(), buttonY(), BUTTON_W, BUTTON_H, confirmLabel, hover, !busy);
		}
		renderList(g, mouseX, mouseY);
	}

	private int listY() {
		return fieldY(openList) + FIELD_H + 1;
	}

	private int listRows() {
		return Math.min(LIST_ROWS, inputs.get(openList).options.size());
	}

	/** The open drop-down list, over the fields and buttons under it. */
	private void renderList(GuiGraphics g, int mouseX, int mouseY) {
		if (openList < 0 || openList >= inputs.size()) {
			return;
		}
		MenuUi.Palette c = MenuUi.p();
		Input input = inputs.get(openList);
		int x = left + 12;
		int w = width() - 24;
		int y = listY();
		int rows = listRows();
		g.pose().pushPose();
		g.pose().translate(0, 0, 200);
		g.fill(x - 1, y - 1, x + w + 1, y + rows * ROW_H + 1, c.accent());
		g.fill(x, y, x + w, y + rows * ROW_H, c.dark());
		for (int r = 0; r < rows; r++) {
			int index = listScroll + r;
			if (index >= input.options.size()) {
				break;
			}
			var option = input.options.get(index);
			int rowY = y + r * ROW_H;
			boolean picked = option.value().equals(input.value);
			if (MenuUi.inside(mouseX, mouseY, x, rowY, w, ROW_H)) {
				g.fill(x, rowY, x + w, rowY + ROW_H, c.raised());
			}
			MenuUi.text(g, option.label(), x + 4, rowY + 2, w - 14, picked ? c.accent() : c.white());
		}
		if (input.options.size() > rows) {
			// Where the visible rows stand in the whole list.
			int track = rows * ROW_H;
			int thumb = Math.max(6, track * rows / input.options.size());
			int at = y + (track - thumb) * listScroll / Math.max(1, input.options.size() - rows);
			g.fill(x + w - 3, at, x + w - 1, at + thumb, c.light());
		}
		g.pose().popPose();
	}

	/** The wheel: the open list first, then the dialog's own body. */
	void scroll(double mouseX, double mouseY, double amount) {
		if (openList >= 0) {
			int max = Math.max(0, inputs.get(openList).options.size() - listRows());
			listScroll = Math.max(0, Math.min(max, listScroll - (int) Math.signum(amount)));
			return;
		}
		scrollBody(mouseX, mouseY, amount);
	}

	// ---------------------------------------------------------------- input

	/** False when the click asks to close the dialog (Cancel). */
	boolean click(double mouseX, double mouseY) {
		if (openList >= 0) {
			Input input = inputs.get(openList);
			int x = left + 12;
			int w = width() - 24;
			int rows = listRows();
			if (listGrab.press(mouseX, mouseY, x + w - 3, 2, listY(), rows * ROW_H, input.options.size() * ROW_H, listScroll * ROW_H, 6)) {
				listScroll = listGrab.dragRows(mouseY, ROW_H);
				return true;
			}
			if (MenuUi.inside(mouseX, mouseY, x, listY(), w, rows * ROW_H)) {
				int index = listScroll + (int) ((mouseY - listY()) / ROW_H);
				if (index >= 0 && index < input.options.size()) {
					input.value = input.options.get(index).value();
					MenuUi.click();
				}
			}
			openList = -1; // a click anywhere closes the list
			return true;
		}
		if (MenuUi.inside(mouseX, mouseY, cancelX(), buttonY(), BUTTON_W, BUTTON_H)) {
			MenuUi.click();
			return false;
		}
		if (MenuUi.inside(mouseX, mouseY, confirmX(), buttonY(), BUTTON_W, BUTTON_H)) {
			MenuUi.click();
			confirm();
			return true;
		}
		for (int i = 0; i < inputs.size(); i++) {
			if (MenuUi.inside(mouseX, mouseY, left + 12, fieldY(i), width() - 24, FIELD_H)) {
				focused = i;
				if (inputs.get(i).kind == Kind.CHOICE && !inputs.get(i).options.isEmpty()) {
					MenuUi.click();
					openList = i;
					// The list opens on the value picked.
					int picked = 0;
					for (int k = 0; k < inputs.get(i).options.size(); k++) {
						if (inputs.get(i).options.get(k).value().equals(inputs.get(i).value)) {
							picked = k;
						}
					}
					listScroll = Math.max(0, Math.min(picked - LIST_ROWS / 2, inputs.get(i).options.size() - listRows()));
				}
				return true;
			}
		}
		if (bodyHeight() > 0) {
			clickBody(mouseX, mouseY);
		}
		return true;
	}

	void confirm() {
		if (busy) {
			return;
		}
		List<String> values = new ArrayList<>();
		for (Input input : inputs) {
			values.add(input.value.strip());
		}
		onConfirm.accept(values);
	}

	void charTyped(char chr) {
		if (focused < 0 || focused >= inputs.size()) {
			return;
		}
		Input input = inputs.get(focused);
		if (input.kind == Kind.CHOICE || input.value.length() >= input.max) {
			return;
		}
		char c = chr;
		boolean ok = switch (input.kind) {
			case NAME -> {
				c = chr == ' ' || chr == '-' ? '_' : Character.toLowerCase(chr);
				yield c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_';
			}
			case ANCHOR -> Character.isLetterOrDigit(chr) || chr == ' ' || chr == '_' || chr == '-';
			case NUMBER -> Character.isDigit(chr);
			case SIGNED_NUMBER -> Character.isDigit(chr) || chr == '-' && input.value.isEmpty();
			case TEXT -> chr >= 32 && chr != 127;
			case CHOICE -> false;
		};
		if (ok) {
			input.value += c;
		}
	}

	/** False when the key closes the dialog (Escape). */
	boolean key(int keyCode, boolean shift) {
		switch (keyCode) {
			case GLFW.GLFW_KEY_ESCAPE -> {
				if (openList >= 0) {
					openList = -1;
					return true;
				}
				return false;
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> confirm();
			case GLFW.GLFW_KEY_TAB -> {
				if (!inputs.isEmpty()) {
					focused = Math.floorMod(focused + (shift ? -1 : 1), inputs.size());
				}
			}
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (focused >= 0 && focused < inputs.size() && inputs.get(focused).kind != Kind.CHOICE
						&& !inputs.get(focused).value.isEmpty()) {
					Input input = inputs.get(focused);
					input.value = input.value.substring(0, input.value.length() - 1);
				}
			}
			default -> {
			}
		}
		return true;
	}

	static Integer number(String text) {
		try {
			return text.isBlank() ? null : Integer.parseInt(text.strip());
		} catch (NumberFormatException ex) {
			return null;
		}
	}
}
