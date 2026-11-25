package com.mystaria.phantasmon.client.gui.menu;

import java.util.List;
import java.util.concurrent.CompletionException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.network.BackendApiException;
import com.mystaria.phantasmon.client.network.BackendErrorMessages;

/**
 * Admins, for admins only (2026-11-25): who the backend's admins are (its {@code admins.txt}), add one by Minecraft
 * name, remove one (not oneself). Read again each time the page opens.
 */
final class AdminsPage implements MenuPage {

	/** The backend's answer: the admins' names, as written in its file. */
	record Admins(List<String> admins) {
	}

	private final PhantasmonMenuScreen screen;
	private final RowList list = new RowList();
	private final RowList.Field name;
	private List<String> admins;

	AdminsPage(PhantasmonMenuScreen screen) {
		this.screen = screen;
		this.name = new RowList.Field("", 16, Component.translatable("phantasmon.rewards.player_hint").getString(),
				value -> value.isEmpty() || value.strip().matches("[A-Za-z0-9_]{3,16}"), this::add);
		rebuild();
		load();
	}

	@Override
	public Component title() {
		return Component.translatable("phantasmon.menu.category.admins");
	}

	private void rebuild() {
		list.clear();
		list.note(Component.translatable("phantasmon.admins.intro"));
		list.field(Component.translatable("phantasmon.admins.name"), name);
		list.buttons(RowList.primary(Component.translatable("phantasmon.admins.add"), this::add));
		list.header(Component.translatable("phantasmon.admins.list", admins == null ? 0 : admins.size()));
		if (admins == null) {
			list.note(Component.translatable("phantasmon.admins.loading"));
			return;
		}
		String self = Minecraft.getInstance().getUser().getName();
		for (String admin : admins) {
			if (admin.equalsIgnoreCase(self)) {
				list.note(Component.translatable("phantasmon.admins.you", admin)); // no ✕: one cannot remove oneself
			} else {
				list.item(Component.literal(admin), () -> remove(admin));
			}
		}
	}

	private void load() {
		PhantasmonApi.get("/admin/admins", Admins.class).thenAccept(this::show).exceptionally(this::fail);
	}

	private void show(Admins result) {
		Minecraft.getInstance().execute(() -> {
			admins = result.admins() == null ? List.of() : result.admins();
			rebuild();
		});
	}

	private void add() {
		String wanted = name.value.strip();
		if (!wanted.matches("[A-Za-z0-9_]{3,16}")) {
			screen.status(Component.translatable("phantasmon.admin.error.name_invalid"), MenuUi.p().danger());
			return;
		}
		PhantasmonApi.post("/admin/admins", java.util.Map.of("player", wanted), Admins.class)
				.thenAccept(result -> {
					show(result);
					Minecraft.getInstance().execute(() -> {
						name.value = "";
						screen.status(Component.translatable("phantasmon.admins.added", wanted), MenuUi.p().good());
					});
				})
				.exceptionally(this::fail);
	}

	private void remove(String admin) {
		screen.openDialog(new MenuDialog(Component.translatable("phantasmon.admins.remove_title"),
				Component.translatable("phantasmon.hub_manager.delete"), true, values -> {
					screen.dialog().busy = true;
					PhantasmonApi.delete("/admin/admins/" + java.net.URLEncoder.encode(admin, java.nio.charset.StandardCharsets.UTF_8),
							Admins.class)
							.thenAccept(result -> {
								show(result);
								Minecraft.getInstance().execute(() -> {
									screen.closeDialog();
									screen.status(Component.translatable("phantasmon.admins.removed", admin), MenuUi.p().good());
								});
							})
							.exceptionally(this::fail);
				}).line(Component.translatable("phantasmon.admins.remove_text", admin)));
	}

	private Void fail(Throwable throwable) {
		Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
		String key = cause instanceof BackendApiException api ? BackendErrorMessages.translationKey(api.errorCode()) : "phantasmon.error.network";
		Minecraft.getInstance().execute(() -> {
			Component message = Component.literal(Component.translatable(key).getString().replaceFirst("^\\[[^]]*]\\s*", ""));
			MenuDialog open = screen.dialog();
			if (open != null) {
				open.busy = false;
				open.error = message;
			} else {
				screen.status(message, MenuUi.p().danger());
			}
		});
		return null;
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
