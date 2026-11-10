package com.mystaria.phantasmon.client.gui.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.mystaria.showtime.audio.BattleMusic;
import com.mystaria.showtime.audio.MusicSettings;

/**
 * Music: the same page as Showtime's (its texts too, Showtime being required) — one chip per folder (the lobby
 * included, Phantasmon's), filters by generation and favourites, a switch, a listen button and a star per track.
 */
final class MusicPage implements MenuPage {

	private static final String KEY = "cobblemon_battle_showtime.";
	private static final String[] ROMAN = { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX" };
	private static final int FAVORITES = -1;
	private static final int CHIP_H = 14;

	private record Chip(Component label, boolean selected, Runnable click) {
	}

	private final RowList list = new RowList();
	private final List<Chip> sections = new ArrayList<>();
	private final List<Chip> filters = new ArrayList<>();
	private BattleMusic.Track track = BattleMusic.Track.BATTLE;
	private int generation;
	private int x;
	private int y;
	private int right;
	private int bottom;

	MusicPage() {
		rebuild();
	}

	@Override
	public Component title() {
		return Component.translatable("phantasmon.menu.category.music");
	}

	private void rebuild() {
		list.clear();
		sections.clear();
		filters.clear();
		for (BattleMusic.Track each : BattleMusic.shownTracks()) {
			sections.add(new Chip(Component.translatable(KEY + "music.tab." + each.name().toLowerCase(Locale.ROOT)), each == track, () -> {
				track = each;
				generation = 0;
				list.resetScroll();
				BattleMusic.endPreview();
				rebuild();
			}));
		}
		List<ResourceLocation> all = BattleMusic.allFiles(track);
		Set<Integer> present = new TreeSet<>();
		all.forEach(file -> present.add(BattleMusic.generation(file)));
		boolean anyFavorite = all.stream().anyMatch(MusicSettings::favorite);
		if (generation == FAVORITES ? !anyFavorite : generation != 0 && !present.contains(generation)) {
			generation = 0;
		}
		if (present.size() > 1 || anyFavorite) {
			filters.add(filter(Component.translatable(KEY + "music.filter.all"), 0));
			if (anyFavorite) {
				filters.add(filter(Component.translatable(KEY + "music.filter.favorites"), FAVORITES));
			}
			for (int each : present) {
				filters.add(filter(each == BattleMusic.OTHER ? Component.translatable(KEY + "music.filter.other")
						: Component.translatable(KEY + "music.filter.generation", ROMAN[each]), each));
			}
		}
		if (all.isEmpty()) {
			list.note(Component.translatable(KEY + "music.screen.empty", "sounds/music/" + track.name().toLowerCase(Locale.ROOT)));
			return;
		}
		List<ResourceLocation> files = generation == 0 ? all
				: generation == FAVORITES ? all.stream().filter(MusicSettings::favorite).toList()
				: all.stream().filter(file -> BattleMusic.generation(file) == generation).toList();
		list.toggle(Component.translatable(KEY + "music.only_favorites"), MusicSettings.onlyFavorites(), () -> {
			MusicSettings.setOnlyFavorites(!MusicSettings.onlyFavorites());
			rebuild();
		});
		list.buttons(RowList.button(Component.translatable(KEY + "music.enable_all"), () -> {
			MusicSettings.setEnabled(files, true);
			rebuild();
		}), RowList.button(Component.translatable(KEY + "music.disable_all"), () -> {
			MusicSettings.setEnabled(files, false);
			rebuild();
		}));
		for (ResourceLocation file : files) {
			list.toggle(Component.literal(BattleMusic.title(file)), MusicSettings.enabled(file), () -> {
				MusicSettings.setEnabled(file, !MusicSettings.enabled(file));
				rebuild();
			}, () -> {
				if (BattleMusic.previewing(file)) {
					BattleMusic.endPreview();
				} else {
					BattleMusic.preview(file);
				}
				rebuild();
			}, Component.translatable(KEY + (BattleMusic.previewing(file) ? "music.screen.stop" : "music.screen.listen")), () -> {
				MusicSettings.setFavorite(file, !MusicSettings.favorite(file));
				rebuild();
			}, MusicSettings.favorite(file));
		}
	}

	private Chip filter(Component label, int value) {
		return new Chip(label, generation == value, () -> {
			generation = value;
			list.resetScroll();
			rebuild();
		});
	}

	private int columns(List<Chip> chips) {
		return chips == sections ? 3 : 6;
	}

	private int chipW(List<Chip> chips) {
		int n = columns(chips);
		return (right - x - (n - 1) * 3) / n;
	}

	private int filtersY() {
		int lines = (sections.size() + 2) / 3;
		return y + lines * (CHIP_H + 3) + (sections.isEmpty() ? 0 : 2);
	}

	private int rowsY() {
		int lines = (filters.size() + 5) / 6;
		return filtersY() + lines * (CHIP_H + 3) + 3;
	}

	@Override
	public void layout(int left, int top, int rightEdge, int bottomEdge) {
		x = left;
		y = top;
		right = rightEdge;
		bottom = bottomEdge;
		list.layout(x, rowsY(), right, bottom);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY) {
		drawChips(g, sections, y, mouseX, mouseY, true);
		drawChips(g, filters, filtersY(), mouseX, mouseY, false);
		list.render(g, mouseX, mouseY);
	}

	private void drawChips(GuiGraphics g, List<Chip> chips, int top, int mouseX, int mouseY, boolean section) {
		int w = chipW(chips);
		int n = columns(chips);
		for (int i = 0; i < chips.size(); i++) {
			int cx = x + (i % n) * (w + 3);
			int cy = top + (i / n) * (CHIP_H + 3);
			boolean hovered = MenuUi.inside(mouseX, mouseY, cx, cy, w, CHIP_H);
			if (section) {
				MenuUi.chip(g, cx, cy, w, CHIP_H, chips.get(i).label(), chips.get(i).selected(), hovered);
			} else {
				MenuUi.filter(g, cx, cy, w, CHIP_H, chips.get(i).label(), chips.get(i).selected(), hovered);
			}
		}
	}

	private boolean clickChips(List<Chip> chips, int top, double mouseX, double mouseY) {
		int w = chipW(chips);
		int n = columns(chips);
		for (int i = 0; i < chips.size(); i++) {
			if (MenuUi.inside(mouseX, mouseY, x + (i % n) * (w + 3), top + (i / n) * (CHIP_H + 3), w, CHIP_H)) {
				MenuUi.click();
				chips.get(i).click().run();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		return clickChips(sections, y, mouseX, mouseY) || clickChips(filters, filtersY(), mouseX, mouseY)
				|| list.click(mouseX, mouseY);
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
	public void close() {
		BattleMusic.endPreview();
	}
}
