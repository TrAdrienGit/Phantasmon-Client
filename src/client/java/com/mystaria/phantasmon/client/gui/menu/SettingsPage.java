package com.mystaria.phantasmon.client.gui.menu;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.gui.PhantasmonTheme;
import com.mystaria.phantasmon.client.network.BackendConfig;
import com.mystaria.phantasmon.client.network.BackendUrlFile;

/**
 * Settings: the backend's address ({@code config/phantasmon.json}, our server's by default), tested and saved for the
 * next launch; the look (Cobblemon or V1).
 */
final class SettingsPage implements MenuPage {

	private final PhantasmonMenuScreen screen;
	private final RowList list = new RowList();
	private final RowList.Field url;

	SettingsPage(PhantasmonMenuScreen screen) {
		this.screen = screen;
		this.url = new RowList.Field(BackendUrlFile.saved(BackendConfig.FILE), 200, BackendUrlFile.DEFAULT_URL,
				value -> BackendUrlFile.parse(value) != null, this::save);
		rebuild();
	}

	@Override
	public Component title() {
		return Component.translatable("phantasmon.menu.category.settings");
	}

	private void rebuild() {
		list.clear();
		list.header(Component.translatable("phantasmon.settings.backend"));
		list.note(Component.translatable("phantasmon.settings.in_use", BackendConfig.BASE_URL.toString()));
		list.field(Component.translatable("phantasmon.settings.url"), url);
		list.buttons(RowList.button(Component.translatable("phantasmon.settings.test"), this::test),
				RowList.button(Component.translatable("phantasmon.settings.default"), () -> {
					url.value = BackendUrlFile.DEFAULT_URL;
					screen.status(Component.translatable("phantasmon.settings.default_set"), MenuUi.p().muted());
				}),
				RowList.primary(Component.translatable("phantasmon.settings.save"), this::save));
		list.note(Component.translatable("phantasmon.settings.hint"));
		list.header(Component.translatable("phantasmon.settings.other"));
		list.select(Component.translatable("phantasmon.ui.theme",
				Component.translatable("phantasmon.ui.theme." + PhantasmonTheme.current().id)), true, () -> {
					PhantasmonTheme.toggle();
					rebuild();
				});
	}

	private void test() {
		URI uri = BackendUrlFile.parse(url.value);
		if (uri == null) {
			screen.status(Component.translatable("phantasmon.settings.url_invalid"), MenuUi.p().danger());
			return;
		}
		screen.status(Component.translatable("phantasmon.settings.testing", uri.toString()), MenuUi.p().muted());
		HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
				.sendAsync(HttpRequest.newBuilder(uri.resolve("/health")).timeout(Duration.ofSeconds(5)).GET().build(),
						HttpResponse.BodyHandlers.ofString())
				.whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
					boolean up = error == null && response.statusCode() == 200 && response.body().contains("UP");
					screen.status(Component.translatable(up ? "phantasmon.settings.test_ok" : "phantasmon.settings.test_failed",
							uri.toString()), up ? MenuUi.p().good() : MenuUi.p().danger());
				}));
	}

	private void save() {
		URI uri = BackendUrlFile.parse(url.value);
		if (uri == null) {
			screen.status(Component.translatable("phantasmon.settings.url_invalid"), MenuUi.p().danger());
			return;
		}
		if (!BackendUrlFile.save(BackendConfig.FILE, uri)) {
			screen.status(Component.translatable("phantasmon.settings.save_failed"), MenuUi.p().danger());
			return;
		}
		url.value = uri.toString();
		screen.status(Component.translatable(uri.equals(BackendConfig.BASE_URL) ? "phantasmon.settings.saved_same"
				: "phantasmon.settings.saved_restart"), MenuUi.p().good());
	}

	@Override
	public void layout(int x, int y, int right, int bottom) {
		list.layout(x, y, right, bottom);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY) {
		list.render(g, mouseX, mouseY);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		return list.click(mouseX, mouseY);
	}

	@Override
	public boolean drag(double mouseX, double mouseY) {
		return list.drag(mouseY);
	}

	@Override
	public void release() {
		list.release();
	}

	@Override
	public boolean scroll(double mouseX, double mouseY, double amount) {
		return list.scroll(mouseX, mouseY, amount);
	}

	@Override
	public boolean charTyped(char chr) {
		return list.charTyped(chr);
	}

	@Override
	public boolean key(int keyCode, boolean control) {
		return list.key(keyCode, control);
	}
}
