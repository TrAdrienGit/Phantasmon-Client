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
		TEXT
	}

	static final class Input {
		final Component label;
		final Kind kind;
		final int max;
		final String placeholder;
		String value;

		Input(Component label, Kind kind, int max, String value, String placeholder) {
			this.label = label;
			this.kind = kind;
			this.max = max;
			this.value = value == null ? "" : value;
			this.placeholder = placeholder;
		}
	}

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
			MenuUi.field(g, left + 12, fieldY(i), width() - 24, FIELD_H, input.label, input.value, input.placeholder,
					i == focused, false);
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
	}

	// ---------------------------------------------------------------- input

	/** False when the click asks to close the dialog (Cancel). */
	boolean click(double mouseX, double mouseY) {
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
		if (input.value.length() >= input.max) {
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
		};
		if (ok) {
			input.value += c;
		}
	}

	/** False when the key closes the dialog (Escape). */
	boolean key(int keyCode, boolean shift) {
		switch (keyCode) {
			case GLFW.GLFW_KEY_ESCAPE -> {
				return false;
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> confirm();
			case GLFW.GLFW_KEY_TAB -> {
				if (!inputs.isEmpty()) {
					focused = Math.floorMod(focused + (shift ? -1 : 1), inputs.size());
				}
			}
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (focused >= 0 && focused < inputs.size() && !inputs.get(focused).value.isEmpty()) {
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
