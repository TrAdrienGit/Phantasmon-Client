package com.mystaria.phantasmon.client.gui;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.network.BackendConfig;
import com.mystaria.phantasmon.client.network.BackendUrlFile;

/**
 * Phantasmon's settings (Adrien 2026-11-10), opened from Mod Menu: the backend's URL ({@code config/phantasmon.json},
 * our server's by default), tested before saving and used from the next launch; the look (V1 / Cobblemon); the
 * battle music picker. Drawn on the Phantasmon canvas.
 */
public final class PhantasmonSettingsScreen extends PhantasmonCanvasScreen {

	private static final int CLOSE_SIZE = 36;
	private static final int CLOSE_X = 1585 - 6 - CLOSE_SIZE;
	private static final int CLOSE_Y = 15 + (48 - CLOSE_SIZE) / 2;
	private static final int PANEL_X = 250;
	private static final int PANEL_W = 1100;
	private static final int FIELD_Y = 170;
	private static final int FIELD_H = 56;
	private static final int ROW_Y = FIELD_Y + FIELD_H + 20;
	private static final int BUTTON_H = 36;
	private static final int BUTTON_W = 260;
	private static final int OTHER_Y = 470;

	private final Screen parent;
	private String url;
	private boolean focused = true;
	private Component status = Component.empty();
	private int statusColor;
	private boolean testing;

	public PhantasmonSettingsScreen(Screen parent) {
		super(Component.translatable("phantasmon.settings.title"));
		this.parent = parent;
		this.url = BackendUrlFile.saved(BackendConfig.FILE);
		statusColor = MUTED;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}

	private static int buttonX(int index) {
		return PANEL_X + index * (BUTTON_W + 20);
	}

	// =====================================================================
	// Actions
	// =====================================================================

	private void test() {
		URI uri = BackendUrlFile.parse(url);
		if (uri == null) {
			setStatus(Component.translatable("phantasmon.settings.url_invalid"), ERROR_TEXT);
			return;
		}
		testing = true;
		setStatus(Component.translatable("phantasmon.settings.testing", uri.toString()), MUTED);
		HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
				.sendAsync(HttpRequest.newBuilder(uri.resolve("/health")).timeout(Duration.ofSeconds(5)).GET().build(),
						HttpResponse.BodyHandlers.ofString())
				.whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
					testing = false;
					if (error == null && response.statusCode() == 200 && response.body().contains("UP")) {
						setStatus(Component.translatable("phantasmon.settings.test_ok", uri.toString()), READY_TEXT);
					} else {
						setStatus(Component.translatable("phantasmon.settings.test_failed", uri.toString()), ERROR_TEXT);
					}
				}));
	}

	private void save() {
		URI uri = BackendUrlFile.parse(url);
		if (uri == null) {
			setStatus(Component.translatable("phantasmon.settings.url_invalid"), ERROR_TEXT);
			return;
		}
		if (!BackendUrlFile.save(BackendConfig.FILE, uri)) {
			setStatus(Component.translatable("phantasmon.settings.save_failed"), ERROR_TEXT);
			return;
		}
		url = uri.toString();
		setStatus(uri.equals(BackendConfig.BASE_URL) ? Component.translatable("phantasmon.settings.saved_same")
				: Component.translatable("phantasmon.settings.saved_restart"), READY_TEXT);
	}

	private void setStatus(Component message, int color) {
		status = message;
		statusColor = color;
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
		focused = inside(x, y, PANEL_X, FIELD_Y, PANEL_W, FIELD_H);
		if (inside(x, y, buttonX(0), ROW_Y, BUTTON_W, BUTTON_H) && !testing) {
			test();
		} else if (inside(x, y, buttonX(1), ROW_Y, BUTTON_W, BUTTON_H)) {
			url = BackendUrlFile.DEFAULT_URL;
			setStatus(Component.translatable("phantasmon.settings.default_set"), MUTED);
		} else if (inside(x, y, buttonX(2), ROW_Y, BUTTON_W, BUTTON_H)) {
			save();
		} else if (inside(x, y, buttonX(0), OTHER_Y, BUTTON_W, BUTTON_H)) {
			PhantasmonTheme.toggle();
		} else if (inside(x, y, buttonX(1), OTHER_Y, BUTTON_W, BUTTON_H)) {
			Minecraft.getInstance().setScreen(new PhantasmonMusicScreen(this));
		}
		return true;
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (focused && url.length() < 200 && chr > 32 && chr != 127) {
			url += chr;
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (focused) {
			if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !url.isEmpty()) {
				url = hasControlDown() ? "" : url.substring(0, url.length() - 1);
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_V && hasControlDown()) {
				String clip = Minecraft.getInstance().keyboardHandler.getClipboard().strip();
				url = (url + clip).length() > 200 ? url : url + clip;
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				save();
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
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
		drawText(g, upper(Component.translatable("phantasmon.settings.title").getString()), 31, 32, 2f, WHITE, true, 1.5f);
		renderCloseButton(g, CLOSE_X, CLOSE_Y, CLOSE_SIZE, inside(mx, my, CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE));

		// Backend.
		drawText(g, upper(Component.translatable("phantasmon.settings.backend").getString()), PANEL_X, 110, 2f, MUTED, false, 2f);
		drawText(g, fitText(Component.translatable("phantasmon.settings.in_use", BackendConfig.BASE_URL.toString()).getString(),
				PANEL_W, 1f, false, 0f), PANEL_X, 140, 1f, TEXT2, false, 0f);
		g.fill(PANEL_X, FIELD_Y, PANEL_X + PANEL_W, FIELD_Y + FIELD_H, INFO_BOX_BG);
		if (focused) {
			glowInside(g, PANEL_X, FIELD_Y, PANEL_W, FIELD_H, 0x50E6FF, 0.25f, 5);
		}
		outline(g, PANEL_X, FIELD_Y, PANEL_W, FIELD_H, focused ? CYAN : LINE_20);
		drawText(g, upper(Component.translatable("phantasmon.settings.url").getString()), PANEL_X + 9, FIELD_Y + 7, 1f, TITLE, true, 1.2f);
		String shown = fitText(url, PANEL_W - 30, 2f, false, 0f);
		drawText(g, shown, PANEL_X + 9, FIELD_Y + 27, 2f, BackendUrlFile.parse(url) == null ? ERROR_TEXT : WHITE, false, 0f);
		if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
			drawText(g, "_", PANEL_X + 9 + textWidth(shown, 2f, false, 0f) + 2, FIELD_Y + 27, 2f, CYAN, false, 0f);
		}
		String[] labels = { testing ? "phantasmon.settings.testing_short" : "phantasmon.settings.test", "phantasmon.settings.default",
				"phantasmon.settings.save" };
		for (int i = 0; i < labels.length; i++) {
			renderButton(g, buttonX(i), ROW_Y, BUTTON_W, BUTTON_H, labels[i], false, inside(mx, my, buttonX(i), ROW_Y, BUTTON_W, BUTTON_H), 2f);
		}
		drawText(g, fitText(Component.translatable("phantasmon.settings.hint").getString(), PANEL_W, 1f, false, 0f), PANEL_X,
				ROW_Y + BUTTON_H + 16, 1f, DIM, false, 0f);

		// Look and music.
		drawText(g, upper(Component.translatable("phantasmon.settings.other").getString()), PANEL_X, OTHER_Y - 40, 2f, MUTED, false, 2f);
		renderPrimaryButtonFrame(g, buttonX(0), OTHER_Y, BUTTON_W, BUTTON_H, inside(mx, my, buttonX(0), OTHER_Y, BUTTON_W, BUTTON_H), false);
		String theme = upper(Component.translatable("phantasmon.ui.theme",
				Component.translatable("phantasmon.ui.theme." + PhantasmonTheme.current().id)).getString());
		drawCentered(g, fitText(theme, BUTTON_W - 16, 2f, true, 1f), buttonX(0) + BUTTON_W / 2f, OTHER_Y + 11, 2f, CYAN, true, 1f);
		renderButton(g, buttonX(1), OTHER_Y, BUTTON_W, BUTTON_H, "phantasmon.settings.music", false,
				inside(mx, my, buttonX(1), OTHER_Y, BUTTON_W, BUTTON_H), 2f);

		renderFooterBar(g, status.getString(), statusColor, 1420);
		renderButton(g, 1475, 849, 97, 28, "phantasmon.music.screen.close", false, inside(mx, my, 1475, 849, 97, 28));
		endCanvas(g);
	}
}
