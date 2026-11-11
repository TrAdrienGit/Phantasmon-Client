package com.mystaria.phantasmon.client.gui.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;

import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.network.BackendApiException;
import com.mystaria.phantasmon.client.network.BackendErrorMessages;
import com.mystaria.phantasmon.client.pokemon.PokemonLook;

/**
 * Rewards, admins only (TODO-37, Adrien 2026-11-11): looks are won, not picked. An admin loads a player by name, sees
 * what they won, gives a filter, an aura or the right to resize — for all their Pokémon or one species — with a note
 * (the event, the tournament), and takes one back. A look sealed on one Pokémon is set in that Pokémon's editor
 * (admin PC). The player then picks among their rewards in the editor's LOOK window.
 */
final class RewardsPage implements MenuPage {

	private final PhantasmonMenuScreen screen;
	private final RowList list = new RowList();
	private final RowList.Field player;
	private PokemonLook.PlayerRewards loaded;

	RewardsPage(PhantasmonMenuScreen screen) {
		this.screen = screen;
		this.player = new RowList.Field("", 16, Component.translatable("phantasmon.rewards.player_hint").getString(), null, this::load);
		rebuild();
	}

	@Override
	public Component title() {
		return Component.translatable("phantasmon.menu.category.rewards");
	}

	private void rebuild() {
		list.clear();
		list.note(Component.translatable("phantasmon.rewards.intro"));
		list.field(Component.translatable("phantasmon.rewards.player"), player);
		list.buttons(RowList.primary(Component.translatable("phantasmon.rewards.load"), this::load));
		if (loaded == null) {
			return;
		}
		PokemonLook.Reward[] rewards = loaded.rewards() == null ? new PokemonLook.Reward[0] : loaded.rewards();
		list.header(Component.translatable("phantasmon.rewards.of", loaded.playerName(), rewards.length));
		if (rewards.length == 0) {
			list.note(Component.translatable("phantasmon.rewards.none"));
		}
		for (PokemonLook.Reward reward : rewards) {
			list.item(Component.literal(describe(reward)), () -> revoke(reward));
		}
		list.buttons(RowList.primary(Component.translatable("phantasmon.rewards.give"), () -> screen.openDialog(new GrantDialog())));
	}

	private static String describe(PokemonLook.Reward reward) {
		String what = switch (reward.kind()) {
			case "FILTER" -> Component.translatable("phantasmon.look.filter").getString() + " · "
					+ PokemonLook.label("FILTER", reward.value()).getString();
			case "AURA" -> Component.translatable("phantasmon.look.aura").getString() + " · "
					+ PokemonLook.label("AURA", reward.value()).getString();
			default -> Component.translatable("phantasmon.look.size").getString();
		};
		String scope = reward.species() == null ? Component.translatable("phantasmon.rewards.all_species").getString()
				: speciesName(reward.species());
		String note = reward.note() == null ? "" : "  ·  " + reward.note();
		return what + "  ·  " + scope + note;
	}

	private static String speciesName(String id) {
		Species species = PokemonSpecies.INSTANCE.getByName(id);
		return species == null ? id : species.getTranslatedName().getString();
	}

	private void load() {
		String name = player.value.strip();
		if (name.isEmpty()) {
			return;
		}
		PhantasmonApi.get("/admin/players/" + encode(name) + "/rewards", PokemonLook.PlayerRewards.class)
				.thenAccept(result -> Minecraft.getInstance().execute(() -> {
					loaded = result;
					screen.status(Component.empty(), MenuUi.p().muted());
					rebuild();
				}))
				.exceptionally(this::fail);
	}

	private void revoke(PokemonLook.Reward reward) {
		screen.openDialog(new MenuDialog(Component.translatable("phantasmon.rewards.revoke_title"),
				Component.translatable("phantasmon.hub_manager.delete"), true, values -> {
					screen.dialog().busy = true;
					PhantasmonApi.delete("/admin/rewards/" + reward.uuid(), Void.class)
							.thenAccept(done -> Minecraft.getInstance().execute(() -> {
								screen.closeDialog();
								screen.status(Component.translatable("phantasmon.rewards.revoked"), MenuUi.p().good());
								load();
							}))
							.exceptionally(this::fail);
				}).line(Component.literal(describe(reward))).line(Component.translatable("phantasmon.rewards.revoke_text")));
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

	private static String encode(String value) {
		return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
	}

	/** Cobblemon's species id for what the admin typed: its id, or its name in the game's language or in English. */
	private static String speciesId(String typed) {
		String wanted = typed.strip().toLowerCase(Locale.ROOT);
		for (Species species : PokemonSpecies.INSTANCE.getSpecies()) {
			if (species.getName().equalsIgnoreCase(wanted) || species.showdownId().equalsIgnoreCase(wanted)
					|| species.getTranslatedName().getString().equalsIgnoreCase(wanted)) {
				return species.showdownId();
			}
		}
		return null;
	}

	/** GIVE: the kind (chips), which one (chips), for all species or one, and a note. */
	private final class GrantDialog extends MenuDialog {

		private static final int CHIP_H = 16;
		private String kind = "FILTER";
		private String value = PokemonLook.FILTERS.get(0);

		GrantDialog() {
			super(Component.translatable("phantasmon.rewards.give_title", loaded.playerName()),
					Component.translatable("phantasmon.rewards.give_confirm"), false, values -> {
					});
			input(Component.translatable("phantasmon.rewards.species"), Kind.TEXT, 32, "",
					Component.translatable("phantasmon.rewards.all_species").getString());
			input(Component.translatable("phantasmon.rewards.note"), Kind.TEXT, 100, "", null);
		}

		@Override
		int width() {
			return 360;
		}

		private List<String> values() {
			return switch (kind) {
				case "FILTER" -> PokemonLook.FILTERS;
				case "AURA" -> PokemonLook.AURAS;
				default -> List.of();
			};
		}

		@Override
		int bodyHeight() {
			int rows = (values().size() + 2) / 3;
			return 14 + CHIP_H + 8 + (rows == 0 ? 0 : 12 + rows * (CHIP_H + 4));
		}

		/** Each chip of the body, recomputed from the layout: kinds, then values. */
		private List<int[]> chipRects(int bx, int by, int bRight) {
			List<int[]> rects = new ArrayList<>();
			int w = (bRight - bx - 8) / 3;
			for (int i = 0; i < 3; i++) {
				rects.add(new int[] { bx + i * (w + 4), by + 12, w, CHIP_H });
			}
			List<String> values = values();
			for (int i = 0; i < values.size(); i++) {
				rects.add(new int[] { bx + (i % 3) * (w + 4), by + 12 + CHIP_H + 20 + (i / 3) * (CHIP_H + 4), w, CHIP_H });
			}
			return rects;
		}

		private int bodyTop() {
			var font = Minecraft.getInstance().font;
			int lines = 0;
			for (Component line : message) {
				lines += Math.max(1, font.split(line, width() - 24).size());
			}
			return top + 22 + 8 + lines * 10 + (message.isEmpty() ? 0 : 6);
		}

		@Override
		void renderBody(GuiGraphics g, int bx, int by, int bRight, int mouseX, int mouseY) {
			MenuUi.Palette c = MenuUi.p();
			String[] kinds = { "FILTER", "AURA", "SCALE" };
			String[] kindKeys = { "phantasmon.look.filter", "phantasmon.look.aura", "phantasmon.look.size" };
			MenuUi.text(g, Component.translatable("phantasmon.rewards.kind"), bx, by, bRight - bx, c.muted());
			List<int[]> rects = chipRects(bx, by, bRight);
			for (int i = 0; i < 3; i++) {
				int[] r = rects.get(i);
				MenuUi.chip(g, r[0], r[1], r[2], r[3], Component.translatable(kindKeys[i]), kinds[i].equals(kind),
						MenuUi.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]));
			}
			List<String> values = values();
			if (!values.isEmpty()) {
				MenuUi.text(g, Component.translatable("phantasmon.rewards.which"), bx, by + 12 + CHIP_H + 8, bRight - bx, c.muted());
			}
			for (int i = 0; i < values.size(); i++) {
				int[] r = rects.get(3 + i);
				MenuUi.filter(g, r[0], r[1], r[2], r[3], PokemonLook.label(kind, values.get(i)), values.get(i).equals(value),
						MenuUi.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]));
			}
		}

		@Override
		boolean clickBody(double mouseX, double mouseY) {
			int bx = left + 12;
			List<int[]> rects = chipRects(bx, bodyTop(), right - 12);
			String[] kinds = { "FILTER", "AURA", "SCALE" };
			for (int i = 0; i < rects.size(); i++) {
				int[] r = rects.get(i);
				if (MenuUi.inside(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
					MenuUi.click();
					if (i < 3) {
						kind = kinds[i];
						value = values().isEmpty() ? "size" : values().get(0);
						layout(Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight());
					} else {
						value = values().get(i - 3);
					}
					return true;
				}
			}
			return false;
		}

		@Override
		void confirm() {
			if (busy) {
				return;
			}
			String typed = inputs.get(0).value.strip();
			String species = null;
			if (!typed.isEmpty()) {
				species = speciesId(typed);
				if (species == null) {
					error = Component.translatable("phantasmon.rewards.unknown_species", typed);
					return;
				}
			}
			Map<String, Object> body = new HashMap<>();
			body.put("kind", kind);
			body.put("value", "SCALE".equals(kind) ? "size" : value);
			if (species != null) {
				body.put("species", species);
			}
			String note = inputs.get(1).value.strip();
			if (!note.isEmpty()) {
				body.put("note", note);
			}
			busy = true;
			error = null;
			PhantasmonApi.post("/admin/players/" + encode(loaded.playerName()) + "/rewards", body, PokemonLook.Reward.class)
					.thenAccept(given -> Minecraft.getInstance().execute(() -> {
						screen.closeDialog();
						screen.status(Component.translatable("phantasmon.rewards.given", loaded.playerName()), MenuUi.p().good());
						load();
					}))
					.exceptionally(RewardsPage.this::fail);
		}
	}

	// ---------------------------------------------------------------- MenuPage

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
