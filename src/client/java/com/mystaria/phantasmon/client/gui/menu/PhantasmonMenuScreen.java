package com.mystaria.phantasmon.client.gui.menu;

import java.util.ArrayList;
import java.util.List;

import com.cobblemon.mod.common.client.gui.ExitButton;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.admin.AdminSession;
import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.hub.HubClient;
import com.mystaria.phantasmon.client.hub.HubController;

/**
 * The Phantasmon menu (redesign, Adrien 2026-11-10), built like Cobblemon Battle Showtime's: a header, a sidebar of
 * categories — Hubs (the hub manager), the add-ons' pages ({@code PhantasmonApi.registerMenuPage}: Phantasmon Events'
 * Tournaments), Rewards, Music, Settings — and the selected category's page. Opened by H (Hubs), Mod Menu
 * (Settings) and the battle lobby or Showtime's N key (Music). Colours from the chosen look ({@link MenuUi#p()}).
 */
public final class PhantasmonMenuScreen extends Screen {

	public enum Category { HUBS, REWARDS, MUSIC, SETTINGS }

	private static final int HEADER_H = 24;
	private static final int SIDEBAR_W = 92;
	private static final int NAV_H = 24;
	private static final int STATUS_H = 14;

	/** What the Hubs page needs; null outside a world (Mod Menu from the title screen): no Hubs page then. */
	public record HubAccess(HubClient client, AuthSession session, HubController controller) {
	}

	private static HubAccess hubAccess;

	/** A sidebar entry: one of Phantasmon's categories, or an add-on's page. */
	private record Nav(Category builtIn, com.mystaria.phantasmon.client.api.PhantasmonApi.MenuPageEntry addon) {

		String key() {
			return builtIn != null ? builtIn.name() : "addon:" + addon.id();
		}
	}

	private final Screen parent;
	private final List<Nav> categories = new ArrayList<>();
	private final java.util.Map<String, MenuPage> pages = new java.util.HashMap<>();
	private Nav category;
	private final Host host = new Host();
	private MenuDialog dialog;
	private Component status = Component.empty();
	private int statusColor;
	private int left;
	private int top;
	private int right;
	private int bottom;

	/** Phantasmon's client gives the Hubs page its access once, at startup. */
	public static void bindHubs(HubAccess access) {
		hubAccess = access;
	}

	public PhantasmonMenuScreen(Screen parent, Category start) {
		this(parent, start.name());
	}

	private PhantasmonMenuScreen(Screen parent, String startKey) {
		super(Component.translatable("phantasmon.menu.title"));
		this.parent = parent;
		boolean hubs = hubAccess != null && hubAccess.session().isAuthenticated()
				&& net.minecraft.client.Minecraft.getInstance().player != null;
		if (hubs) {
			categories.add(new Nav(Category.HUBS, null));
		}
		for (var addon : com.mystaria.phantasmon.client.api.PhantasmonApi.menuPages()) {
			if (addon.visible().getAsBoolean()) {
				categories.add(new Nav(null, addon));
			}
		}
		if (hubs && AdminSession.isAdmin()) {
			categories.add(new Nav(Category.REWARDS, null)); // admins give the looks (TODO-37)
		}
		categories.add(new Nav(Category.MUSIC, null));
		categories.add(new Nav(Category.SETTINGS, null));
		category = categories.stream().filter(nav -> nav.key().equals(startKey)).findFirst()
				.orElse(categories.get(categories.size() - 1));
		statusColor = MenuUi.p().muted();
	}

	/** The menu opened on an add-on's page ({@code PhantasmonApi.openMenu}). */
	public static Screen onPage(Screen parent, String addonId) {
		return new PhantasmonMenuScreen(parent, "addon:" + addonId);
	}

	/** Mod Menu and the music hooks take a {@code Screen -> Screen}. */
	public static Screen settings(Screen parent) {
		return new PhantasmonMenuScreen(parent, Category.SETTINGS);
	}

	public static Screen music(Screen parent) {
		return new PhantasmonMenuScreen(parent, Category.MUSIC);
	}

	private MenuPage page(Nav each) {
		return pages.computeIfAbsent(each.key(), key -> each.addon() != null ? each.addon().factory().apply(host) : switch (each.builtIn()) {
			case HUBS -> new HubsPage(this, hubAccess.client(), hubAccess.session(), hubAccess.controller(), AdminSession.isAdmin());
			case REWARDS -> new RewardsPage(this);
			case MUSIC -> new MusicPage();
			case SETTINGS -> new SettingsPage(this);
		});
	}

	private MenuPage page() {
		return page(category);
	}

	// ---------------------------------------------------------------- for the pages

	void status(Component message, int color) {
		status = message;
		statusColor = color;
	}

	void openDialog(MenuDialog opened) {
		dialog = opened;
		dialog.layout(width, height);
	}

	void closeDialog() {
		MenuDialog closing = dialog;
		dialog = null;
		if (closing != null && closing.onClose != null) {
			closing.onClose.run();
		}
	}

	MenuDialog dialog() {
		return dialog;
	}

	// ---------------------------------------------------------------- layout

	@Override
	protected void init() {
		boolean wide = page().wide();
		int w = Math.min(width - 16, wide ? 640 : 520);
		int h = Math.min(height - 16, wide ? 360 : 320);
		left = (width - w) / 2;
		top = (height - h) / 2;
		right = left + w;
		bottom = top + h;
		addRenderableWidget(new ExitButton(right - 32, top + 6, b -> onClose()));
		if (dialog != null) {
			dialog.layout(width, height);
		}
	}

	private int contentX() {
		return left + SIDEBAR_W + 10;
	}

	private int navY(int index) {
		return top + HEADER_H + 6 + index * (NAV_H + 2);
	}

	private void layoutPage() {
		page().layout(contentX(), top + HEADER_H + 24, right - 10, bottom - 6 - STATUS_H);
	}

	private void select(Nav chosen) {
		if (chosen != category) {
			page().close();
			boolean wasWide = page().wide();
			category = chosen;
			if (page().wide() != wasWide) {
				rebuildWidgets(); // the frame changes size
			}
			status(Component.empty(), MenuUi.p().muted());
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		pages.values().forEach(MenuPage::close);
		minecraft.setScreen(parent);
	}

	@Override
	public void tick() {
		page().tick();
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		MenuUi.Palette c = MenuUi.p();
		if (minecraft.level == null) {
			super.renderBackground(g, mouseX, mouseY, partialTick);
		} else {
			g.fill(0, 0, width, top, MenuUi.VEIL);
			g.fill(0, bottom, width, height, MenuUi.VEIL);
			g.fill(0, top, left, bottom, MenuUi.VEIL);
			g.fill(right, top, width, bottom, MenuUi.VEIL);
		}
		int contentTop = top + HEADER_H;
		g.fill(left + SIDEBAR_W, contentTop, right, bottom, c.dark());
		MenuUi.dots(g, left + SIDEBAR_W, contentTop, right, bottom);
		MenuUi.header(g, left, top, right, HEADER_H);
		g.fillGradient(left, contentTop, left + SIDEBAR_W, bottom, c.panel(), c.dark());
		g.fill(left + SIDEBAR_W - 1, contentTop, left + SIDEBAR_W, bottom, c.black());
		g.fill(left + SIDEBAR_W, contentTop, left + SIDEBAR_W + 1, bottom, c.raised());
		MenuUi.frame(g, left, top, right, bottom);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		MenuUi.Palette c = MenuUi.p();
		boolean modal = dialog != null;
		int mx = modal ? -1 : mouseX;
		int my = modal ? -1 : mouseY;
		super.render(g, mx, my, partialTick);
		MenuUi.text(g, title.copy().withStyle(s -> s.withBold(true)), left + 21, top + 7, right - left - 150, c.white());
		navigation(g, mx, my);
		layoutPage();
		MenuUi.title(g, page().title(), contentX(), top + HEADER_H + 7, right - 10);
		page().render(g, mx, my);
		if (status != null && !status.getString().isEmpty()) {
			MenuUi.text(g, status, contentX(), bottom - STATUS_H, right - 10 - contentX(), statusColor);
		}
		if (modal) {
			g.pose().pushPose();
			g.pose().translate(0, 0, 400);
			dialog.layout(width, height);
			dialog.render(g, width, height, mouseX, mouseY);
			g.pose().popPose();
		}
	}

	private Component categoryName(Nav each) {
		return each.addon() != null ? each.addon().title()
				: Component.translatable("phantasmon.menu.category." + each.builtIn().name().toLowerCase(java.util.Locale.ROOT));
	}

	/** The menu as add-on pages see it: the footer and the dialogs. */
	private final class Host implements com.mystaria.phantasmon.client.api.MenuHost {

		@Override
		public void status(Component message, boolean error) {
			PhantasmonMenuScreen.this.status(message, error ? MenuUi.p().danger() : MenuUi.p().muted());
		}

		@Override
		public void confirm(Component title, List<Component> lines, Component confirmLabel, boolean danger, Runnable onConfirm) {
			MenuDialog opened = new MenuDialog(title, confirmLabel, danger, values -> {
				closeDialog();
				onConfirm.run();
			});
			lines.forEach(opened::line);
			openDialog(opened);
		}

		@Override
		public void ask(Component title, List<Component> labels, List<String> values, Component confirmLabel,
				java.util.function.Consumer<List<String>> onConfirm) {
			MenuDialog opened = new MenuDialog(title, confirmLabel, false, typed -> {
				dialog.busy = true;
				dialog.error = null;
				onConfirm.accept(typed);
			});
			for (int i = 0; i < labels.size(); i++) {
				opened.input(labels.get(i), MenuDialog.Kind.TEXT, 64, i < values.size() ? values.get(i) : "", null);
			}
			openDialog(opened);
		}

		@Override
		public void dialogError(Component message) {
			if (dialog != null) {
				dialog.busy = false;
				dialog.error = message;
			}
		}

		@Override
		public void closeDialog() {
			PhantasmonMenuScreen.this.closeDialog();
		}

		@Override
		public boolean dialogOpen() {
			return dialog != null;
		}

		@Override
		public void closeMenu() {
			onClose();
		}
	}

	private void navigation(GuiGraphics g, int mouseX, int mouseY) {
		MenuUi.Palette c = MenuUi.p();
		for (int i = 0; i < categories.size(); i++) {
			int y = navY(i);
			boolean selected = categories.get(i) == category;
			if (selected) {
				MenuUi.gradientRight(g, left + 1, y, left + SIDEBAR_W - 1, y + NAV_H, c.accentDim(), c.raised());
				MenuUi.raised(g, left + 1, y, left + SIDEBAR_W - 1, y + NAV_H);
				g.fill(left + 1, y, left + 4, y + NAV_H, c.accent());
				for (int step = 0; step < 4; step++) {
					g.fill(left + SIDEBAR_W - 6 + step, y + 8 + step, left + SIDEBAR_W - 5 + step, y + 16 - step, c.white());
				}
			} else if (MenuUi.inside(mouseX, mouseY, left, y, SIDEBAR_W, NAV_H)) {
				g.fill(left + 1, y, left + SIDEBAR_W - 1, y + NAV_H, c.raised());
				MenuUi.raised(g, left + 1, y, left + SIDEBAR_W - 1, y + NAV_H);
			}
			g.fill(left + 1, y + NAV_H, left + SIDEBAR_W - 1, y + NAV_H + 1, c.black());
			g.fill(left + 9, y + 10, left + 13, y + 14, selected ? c.white() : c.edge());
			MenuUi.text(g, categoryName(categories.get(i)), left + 17, y + 8, SIDEBAR_W - 26, selected ? c.white() : c.muted());
		}
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (dialog != null) {
			if (button == 0 && !dialog.click(mouseX, mouseY)) {
				closeDialog();
			}
			return true;
		}
		if (button != 0) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		for (int i = 0; i < categories.size(); i++) {
			if (MenuUi.inside(mouseX, mouseY, left, navY(i), SIDEBAR_W, NAV_H)) {
				MenuUi.click();
				select(categories.get(i));
				return true;
			}
		}
		layoutPage();
		if (page().click(mouseX, mouseY)) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (dialog == null && page().drag(mouseX, mouseY)) {
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		page().release();
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (dialog != null) {
			dialog.scrollBody(mouseX, mouseY, scrollY);
			return true;
		}
		page().scroll(mouseX, mouseY, scrollY);
		return true;
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (dialog != null) {
			dialog.charTyped(chr);
			return true;
		}
		return page().charTyped(chr) || super.charTyped(chr, modifiers);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (dialog != null) {
			if (!dialog.key(keyCode, hasShiftDown())) {
				closeDialog();
			}
			return true;
		}
		if (page().key(keyCode, hasControlDown())) {
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}
