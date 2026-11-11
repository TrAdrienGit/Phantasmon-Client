package com.mystaria.phantasmon.client.gui.menu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.mystaria.phantasmon.client.api.HubManagerTab;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.hub.HubAdminDto;
import com.mystaria.phantasmon.client.hub.HubAnchorDto;
import com.mystaria.phantasmon.client.hub.HubBuildLayout;
import com.mystaria.phantasmon.client.hub.HubCapture;
import com.mystaria.phantasmon.client.hub.HubClient;
import com.mystaria.phantasmon.client.hub.HubController;
import com.mystaria.phantasmon.client.hub.HubPlacement;
import com.mystaria.phantasmon.client.network.BackendApiException;
import com.mystaria.phantasmon.client.network.BackendErrorMessages;

/**
 * The hub manager, a page of the Phantasmon menu (2026-11-10). A player sees the classic hubs (size, build, creation
 * date, anchor count), their own anchors to the selected hub, each deletable, and PLACER UNE ANCRE (the zone then
 * follows them, {@link HubPlacement}). An admin also gets the tabs (Phantasmon's hubs, the add-ons' —
 * {@link PhantasmonApi#registerHubManagerTab} — and "others"), every anchor on every server, and rename, schematic
 * (send a file, copy the zone, remove), reload, delete and create. Left the list, right the selected hub.
 */
final class HubsPage implements MenuPage {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").withZone(ZoneId.systemDefault());
	static final Path SCHEMATICS_DIR = FabricLoader.getInstance().getGameDir().resolve("schematics");
	private static final int CHIP_H = 14;
	private static final int HUB_H = 26;
	private static final int HUB_PITCH = 28;
	private static final int ANCHOR_H = 25;
	private static final int ANCHOR_PITCH = 27;
	private static final int BUTTON_H = 16;

	private final PhantasmonMenuScreen screen;
	private final HubClient client;
	private final AuthSession session;
	private final HubController controller;
	private final boolean admin;
	private final List<HubManagerTab> addonTabs = PhantasmonApi.hubManagerTabs();

	private int tab;
	private List<HubAdminDto> hubs = List.of();
	private List<HubAdminDto.Anchor> anchors = List.of();
	private String selected;
	private int hubScroll;
	private int anchorScroll;
	private boolean loading = true;
	private boolean uploading;

	private int x;
	private int y;
	private int right;
	private int bottom;

	HubsPage(PhantasmonMenuScreen screen, HubClient client, AuthSession session, HubController controller, boolean admin) {
		this.screen = screen;
		this.client = client;
		this.session = session;
		this.controller = controller;
		this.admin = admin;
		reload();
	}

	@Override
	public Component title() {
		return Component.translatable("phantasmon.menu.category.hubs");
	}

	// ---------------------------------------------------------------- tabs and data

	private final HubManagerTab coreTab = new HubManagerTab() {
		@Override
		public Component title() {
			return Component.translatable("phantasmon.hub_manager.tab.hubs");
		}

		@Override
		public boolean shows(HubAdminDto hub) {
			return hub.normal();
		}
	};

	/** Hubs no tab shows: a kind whose add-on is not installed (D-41). */
	private final HubManagerTab otherTab = new HubManagerTab() {
		@Override
		public Component title() {
			return Component.translatable("phantasmon.hub_manager.tab.others");
		}

		@Override
		public boolean shows(HubAdminDto hub) {
			return !coreTab.shows(hub) && addonTabs.stream().noneMatch(tab -> tab.shows(hub));
		}
	};

	/** Players: Phantasmon's classic hubs only, never an add-on's (Adrien 2026-11-10). */
	private List<HubManagerTab> tabs() {
		List<HubManagerTab> tabs = new ArrayList<>();
		tabs.add(coreTab);
		if (admin) {
			tabs.addAll(addonTabs);
			if (hubs.stream().anyMatch(otherTab::shows)) {
				tabs.add(otherTab);
			}
		}
		return tabs;
	}

	private HubManagerTab currentTab() {
		List<HubManagerTab> tabs = tabs();
		if (tab >= tabs.size()) {
			tab = 0;
		}
		return tabs.get(tab);
	}

	private List<HubAdminDto> tabHubs() {
		HubManagerTab current = currentTab();
		return hubs.stream().filter(current::shows).toList();
	}

	private HubAdminDto selectedHub() {
		return hubs.stream().filter(hub -> hub.name().equals(selected)).findFirst().orElse(null);
	}

	private void reload() {
		loading = true;
		(admin ? client.adminHubs(session.accessToken()) : client.overview(session.accessToken())).thenAccept(result -> onMain(() -> {
			hubs = result == null ? List.of() : Arrays.asList(result);
			loading = false;
			List<HubAdminDto> shown = tabHubs();
			if (shown.stream().noneMatch(hub -> hub.name().equals(selected))) {
				selected = shown.isEmpty() ? null : shown.get(0).name();
				anchorScroll = 0;
			}
			loadAnchors();
		})).exceptionally(this::fail);
	}

	private void loadAnchors() {
		String hub = selected;
		if (hub == null) {
			anchors = List.of();
			return;
		}
		if (!admin) {
			client.mine(session.accessToken()).thenAccept(result -> onMain(() -> {
				if (hub.equals(selected)) {
					anchors = result == null ? List.of() : Arrays.stream(result)
							.filter(anchor -> anchor.hub().equalsIgnoreCase(hub))
							.map(anchor -> new HubAdminDto.Anchor(anchor.uuid(), anchor.name(), anchor.ownerUuid(), session.username(),
									anchor.serverFingerprint(), anchor.dimension(), anchor.origin().x(), anchor.origin().y(),
									anchor.origin().z(), anchor.yaw(), anchor.role(), null))
							.toList();
				}
			})).exceptionally(this::fail);
			return;
		}
		client.adminAnchors(session.accessToken(), hub).thenAccept(result -> onMain(() -> {
			if (hub.equals(selected)) {
				anchors = result == null ? List.of() : Arrays.asList(result);
			}
		})).exceptionally(this::fail);
	}

	private void select(String hub) {
		if (!hub.equals(selected)) {
			selected = hub;
			anchors = List.of();
			anchorScroll = 0;
			loadAnchors();
		}
	}

	private void selectTab(int index) {
		if (tab != index) {
			tab = index;
			hubScroll = 0;
			List<HubAdminDto> shown = tabHubs();
			selected = shown.isEmpty() ? null : shown.get(0).name();
			anchors = List.of();
			anchorScroll = 0;
			loadAnchors();
		}
	}

	private Void fail(Throwable throwable) {
		Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
		String key = cause instanceof BackendApiException api ? BackendErrorMessages.translationKey(api.errorCode())
				: "phantasmon.error.network";
		onMain(() -> {
			loading = false;
			String text = Component.translatable(key).getString().replaceFirst("^\\[[^]]*]\\s*", "");
			MenuDialog open = screen.dialog();
			if (open != null) {
				open.busy = false;
				open.error = Component.literal(text);
			} else {
				screen.status(Component.literal(text), MenuUi.p().danger());
			}
		});
		return null;
	}

	/** Like {@link #fail}, with the backend's reason when a schematic was refused (wrong size, unreadable). */
	private Void failSchematic(Throwable throwable) {
		Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
		if (cause instanceof BackendApiException api && "ERROR_HUB_SCHEMATIC_INVALID".equals(api.errorCode())
				&& api.details() != null && api.details().get("reason") != null) {
			onMain(() -> {
				uploading = false;
				Component message = Component.translatable("phantasmon.hub_manager.schematic_refused", api.details().get("reason"));
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
		onMain(() -> uploading = false);
		return fail(throwable);
	}

	private void ok(Component message) {
		screen.status(message, MenuUi.p().good());
	}

	private static void onMain(Runnable action) {
		Minecraft.getInstance().execute(action);
	}

	// ---------------------------------------------------------------- actions

	/** The buttons of the selected hub: Phantasmon's, then the tab's own. */
	private List<HubManagerTab.Action> actions(HubAdminDto hub) {
		List<HubManagerTab.Action> actions = new ArrayList<>();
		HubManagerTab.Action place = new HubManagerTab.Action(Component.translatable("phantasmon.hub_manager.place"), false,
				(h, done, report) -> placeDialog(h));
		if (!admin) {
			if (hub.normal()) {
				actions.add(place);
			}
			return actions;
		}
		if (hub.normal()) {
			actions.add(place);
			actions.add(new HubManagerTab.Action(Component.translatable("phantasmon.hub_manager.schematic"), false,
					(h, done, report) -> screen.openDialog(new SchematicDialog(h))));
			actions.add(new HubManagerTab.Action(Component.translatable("phantasmon.hub_manager.reload"), false,
					(h, done, report) -> reloadBuild(h)));
		}
		actions.add(new HubManagerTab.Action(Component.translatable("phantasmon.hub_manager.rename"), false,
				(h, done, report) -> renameDialog(h)));
		actions.addAll(currentTab().actions());
		actions.add(new HubManagerTab.Action(Component.translatable("phantasmon.hub_manager.delete"), true,
				(h, done, report) -> deleteDialog(h)));
		return actions;
	}

	private HubManagerTab.Action createAction() {
		if (!admin) {
			return null;
		}
		if (currentTab() == coreTab) {
			return new HubManagerTab.Action(Component.translatable("phantasmon.hub_manager.create"), false,
					(h, done, report) -> createDialog());
		}
		return currentTab() == otherTab ? null : currentTab().createAction();
	}

	private void run(HubManagerTab.Action action, HubAdminDto hub) {
		if (action.fields().isEmpty()) {
			runForm(action, hub, List.of());
			return;
		}
		MenuDialog form = new MenuDialog(action.label(), Component.translatable("phantasmon.hub_manager.form_confirm"), action.danger(),
				values -> {
					for (int i = 0; i < values.size(); i++) {
						HubManagerTab.Field.Kind kind = action.fields().get(i).kind();
						boolean number = kind == HubManagerTab.Field.Kind.NUMBER || kind == HubManagerTab.Field.Kind.SIGNED_NUMBER;
						if (values.get(i).isEmpty() || number && MenuDialog.number(values.get(i)) == null) {
							screen.dialog().error = Component.translatable("phantasmon.hub_manager.form_fill");
							return;
						}
					}
					screen.closeDialog();
					runForm(action, hub, values);
				});
		for (HubManagerTab.Field field : action.fields()) {
			MenuDialog.Kind kind = switch (field.kind()) {
				case NAME -> MenuDialog.Kind.NAME;
				case NUMBER -> MenuDialog.Kind.NUMBER;
				case SIGNED_NUMBER -> MenuDialog.Kind.SIGNED_NUMBER;
				case TEXT -> MenuDialog.Kind.TEXT;
			};
			form.input(field.label(), kind, kind == MenuDialog.Kind.TEXT || kind == MenuDialog.Kind.NAME ? 32 : 6, "", null);
		}
		screen.openDialog(form);
	}

	private void runForm(HubManagerTab.Action action, HubAdminDto hub, List<String> values) {
		action.form().run(hub, values, () -> onMain(this::reload), message -> onMain(() -> screen.status(message, MenuUi.p().muted())));
	}

	private void createDialog() {
		screen.openDialog(new MenuDialog(Component.translatable("phantasmon.hub_manager.create_title"),
				Component.translatable("phantasmon.hub_manager.create_confirm"), false, values -> {
					Integer width = MenuDialog.number(values.get(1));
					Integer height = MenuDialog.number(values.get(2));
					Integer length = MenuDialog.number(values.get(3));
					if (values.get(0).isEmpty() || width == null || height == null || length == null) {
						screen.dialog().error = Component.translatable("phantasmon.hub_manager.form_incomplete");
						return;
					}
					screen.dialog().busy = true;
					client.createHub(session.accessToken(), values.get(0), length, width, height).thenAccept(created -> onMain(() -> {
						screen.closeDialog();
						ok(Component.translatable("phantasmon.hub_manager.created", created.name()));
						selected = created.name();
						reload();
					})).exceptionally(this::fail);
				})
				.line(Component.translatable("phantasmon.hub_manager.create_hint"))
				.input(Component.translatable("phantasmon.hub_manager.field.name"), MenuDialog.Kind.NAME, 32, "", null)
				.input(Component.translatable("phantasmon.hub_manager.field.width"), MenuDialog.Kind.NUMBER, 2, "", null)
				.input(Component.translatable("phantasmon.hub_manager.field.height"), MenuDialog.Kind.NUMBER, 2, "", null)
				.input(Component.translatable("phantasmon.hub_manager.field.length"), MenuDialog.Kind.NUMBER, 2, "", null));
	}

	private void renameDialog(HubAdminDto hub) {
		screen.openDialog(new MenuDialog(Component.translatable("phantasmon.hub_manager.rename_title", hub.name()),
				Component.translatable("phantasmon.hub_manager.rename_confirm"), false, values -> {
					if (values.get(0).isEmpty()) {
						return;
					}
					screen.dialog().busy = true;
					client.renameHub(session.accessToken(), hub.name(), values.get(0)).thenAccept(renamed -> onMain(() -> {
						screen.closeDialog();
						ok(Component.translatable("phantasmon.hub_manager.renamed", hub.name(), renamed.name()));
						selected = renamed.name();
						reload();
					})).exceptionally(this::fail);
				})
				.input(Component.translatable("phantasmon.hub_manager.field.name"), MenuDialog.Kind.NAME, 32, hub.name(), null));
	}

	private void placeDialog(HubAdminDto hub) {
		String player = session.username() == null ? "?" : session.username();
		screen.openDialog(new MenuDialog(Component.translatable("phantasmon.hub_manager.place_title", hub.name()),
				Component.translatable("phantasmon.hub_manager.place_confirm"), false, values -> {
					String name = values.get(0).isEmpty() ? null : values.get(0);
					screen.closeDialog();
					screen.onClose();
					HubPlacement.start(controller, hub.name(), name, hub.size());
				})
				.line(Component.translatable("phantasmon.hub_manager.place_hint"))
				.input(Component.translatable("phantasmon.hub_manager.field.anchor_name"), MenuDialog.Kind.ANCHOR, 32, "",
						HubController.defaultAnchorName(hub.name(), player)));
	}

	private void deleteDialog(HubAdminDto hub) {
		screen.openDialog(new MenuDialog(Component.translatable("phantasmon.hub_manager.delete_title", hub.name()),
				Component.translatable("phantasmon.hub_manager.delete"), true, values -> {
					screen.dialog().busy = true;
					client.deleteHub(session.accessToken(), hub.name()).thenAccept(deleted -> onMain(() -> {
						screen.closeDialog();
						controller.refreshAnchors();
						ok(Component.translatable("phantasmon.hub_manager.deleted", deleted.hub(), deleted.anchorsDeleted()));
						selected = null;
						reload();
					})).exceptionally(this::fail);
				})
				.line(Component.translatable("phantasmon.hub_manager.delete_text", hub.anchors())));
	}

	private void deleteAnchorDialog(HubAdminDto.Anchor anchor) {
		Component text = admin ? Component.translatable("phantasmon.hub_manager.delete_anchor_text",
				anchor.ownerName() == null ? "?" : anchor.ownerName())
				: Component.translatable("phantasmon.hub_manager.delete_own_anchor_text");
		screen.openDialog(new MenuDialog(Component.translatable("phantasmon.hub_manager.delete_anchor_title", anchor.name()),
				Component.translatable("phantasmon.hub_manager.delete"), true, values -> {
					screen.dialog().busy = true;
					client.delete(session.accessToken(), anchor.uuid()).thenAccept(done -> onMain(() -> {
						screen.closeDialog();
						controller.refreshAnchors(); // its build goes at once here, not at the next 30 s refresh
						ok(Component.translatable("phantasmon.hub_manager.anchor_deleted", anchor.name()));
						reload();
					})).exceptionally(this::fail);
				})
				.line(text));
	}

	private void reloadBuild(HubAdminDto hub) {
		client.reloadHub(session.accessToken(), hub.name()).thenAccept(view -> onMain(() -> {
			ok(Component.translatable("phantasmon.hub_manager.reloaded", view.name()));
			reload();
		})).exceptionally(this::fail);
	}

	// ---------------------------------------------------------------- schematics (admins)

	/** The schematics/ folder's .schem and .litematic files (sub-folders too, as Litematica saves them), newest first. */
	private static List<Path> listSchematics() {
		try {
			Files.createDirectories(SCHEMATICS_DIR);
			try (var files = Files.walk(SCHEMATICS_DIR, 4)) {
				return files.filter(Files::isRegularFile)
						.filter(path -> {
							String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
							return name.endsWith(".schem") || name.endsWith(".litematic");
						})
						.sorted(Comparator.comparingLong((Path path) -> path.toFile().lastModified()).reversed())
						.toList();
			}
		} catch (java.io.IOException ex) {
			return List.of();
		}
	}

	/** The name the backend accepts: letters, digits, space, _ . ( ) -; anything else becomes _. */
	private static String uploadName(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String base = name.substring(0, dot).replaceAll("[^A-Za-z0-9 _.()-]", "_");
		if (base.isBlank()) {
			base = "hub";
		}
		return (base.length() > 90 ? base.substring(0, 90) : base) + name.substring(dot).toLowerCase(Locale.ROOT);
	}

	private void upload(HubAdminDto hub, String name, CompletableFuture<byte[]> bytes, Component success) {
		uploading = true;
		screen.status(Component.translatable("phantasmon.hub_manager.schematic_sending", name), MenuUi.p().muted());
		bytes.thenCompose(data -> client.uploadSchematic(session.accessToken(), hub.name(), name, data))
				.thenAccept(view -> onMain(() -> {
					uploading = false;
					screen.closeDialog();
					ok(success);
					reload();
				}))
				.exceptionally(this::failSchematic);
	}

	/** COPIER LA ZONE: what stands in this world's anchor of the hub (the one the admin is in, or the nearest). */
	private void captureZone(HubAdminDto hub) {
		Minecraft mc = Minecraft.getInstance();
		if (uploading || mc.player == null || mc.level == null) {
			return;
		}
		Vec3 at = mc.player.position();
		HubAnchorDto anchor = controller.anchorsHere().stream()
				.filter(candidate -> candidate.hub().equalsIgnoreCase(hub.name()))
				.min(Comparator.comparingDouble((HubAnchorDto candidate) -> candidate.coordinates().contains(at.x, at.y, at.z) ? -1
						: at.distanceToSqr(candidate.origin().x(), candidate.origin().y(), candidate.origin().z())))
				.orElse(null);
		MenuDialog open = screen.dialog();
		if (anchor == null) {
			open.error = Component.translatable("phantasmon.hub_manager.capture_no_anchor", hub.name());
			return;
		}
		HubBuildLayout layout = anchor.layout();
		if (!HubCapture.loaded(mc.level, layout)) {
			open.error = Component.translatable("phantasmon.hub_manager.capture_too_far", anchor.name());
			return;
		}
		byte[] bytes;
		try {
			bytes = HubCapture.capture(mc.level, layout);
		} catch (java.io.IOException | RuntimeException ex) {
			open.error = Component.translatable("phantasmon.hub_manager.capture_failed", String.valueOf(ex.getMessage()));
			return;
		}
		String name = hub.name() + "-copie-" + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
				+ ".schem";
		upload(hub, name, CompletableFuture.completedFuture(bytes),
				Component.translatable("phantasmon.hub_manager.captured", anchor.name(), hub.name()));
	}

	private void removeBuild(HubAdminDto hub) {
		client.removeSchematic(session.accessToken(), hub.name()).thenAccept(view -> onMain(() -> {
			screen.closeDialog();
			ok(Component.translatable("phantasmon.hub_manager.schematic_removed", view.name()));
			reload();
		})).exceptionally(this::failSchematic);
	}

	/** The schematic picker: the instance's files, the folder, the zone copy, removing the build; SEND uploads. */
	private final class SchematicDialog extends MenuDialog {

		private static final int LIST_ROWS = 7;
		private static final int ROW = 16;
		private final HubAdminDto hub;
		private final List<Path> files = listSchematics();
		private int pick = -1;
		private int scroll;

		SchematicDialog(HubAdminDto hub) {
			super(Component.translatable("phantasmon.hub_manager.schematic_title", hub.name()),
					Component.translatable("phantasmon.hub_manager.schematic_send"), false, values -> {
					});
			this.hub = hub;
			line(Component.translatable("phantasmon.hub_manager.schematic_size", hub.size().x(), hub.size().y(), hub.size().z(),
					hub.schematic() == null ? Component.translatable("phantasmon.hub_manager.no_build").getString() : hub.schematic().name()));
		}

		@Override
		int width() {
			return 380;
		}

		@Override
		int bodyHeight() {
			return LIST_ROWS * ROW + 4 + 12 + 22;
		}

		private int toolW() {
			return (width() - 24 - 8) / 3;
		}

		@Override
		void renderBody(GuiGraphics g, int bx, int by, int bRight, int mouseX, int mouseY) {
			MenuUi.Palette c = MenuUi.p();
			int listH = LIST_ROWS * ROW + 4;
			g.fill(bx - 1, by - 1, bRight + 1, by + listH + 1, c.black());
			g.fill(bx, by, bRight, by + listH, c.dark());
			MenuUi.sunken(g, bx, by, bRight, by + listH);
			if (files.isEmpty()) {
				MenuUi.centered(g, Component.translatable("phantasmon.hub_manager.schematic_empty"), (bx + bRight) / 2, by + listH / 2 - 4,
						bRight - bx - 8, c.muted());
			}
			g.enableScissor(bx, by + 2, bRight, by + listH - 2);
			for (int i = 0; i < LIST_ROWS && scroll + i < files.size(); i++) {
				Path file = files.get(scroll + i);
				int ry = by + 2 + i * ROW;
				boolean picked = scroll + i == pick;
				if (picked) {
					MenuUi.selectedRow(g, bx + 2, ry, bRight - bx - 10, ROW - 1);
				} else if (MenuUi.inside(mouseX, mouseY, bx, ry, bRight - bx, ROW)) {
					g.fill(bx + 2, ry, bRight - 8, ry + ROW - 1, c.raised());
				}
				String relative = SCHEMATICS_DIR.relativize(file).toString().replace('\\', '/');
				String kib = Math.max(1, file.toFile().length() / 1024) + " Kio";
				MenuUi.text(g, Component.literal(relative), bx + 7, ry + 4, bRight - bx - 70, picked ? c.white() : c.muted());
				MenuUi.small(g, Component.literal(kib), bRight - 50, ry + 5, 40, c.edge());
			}
			g.disableScissor();
			MenuUi.scrollbar(g, bRight - 6, by + 2, listH - 4, files.size() * ROW, scroll * ROW, false);
			int pathY = by + listH + 3;
			MenuUi.small(g, Component.literal(SCHEMATICS_DIR.toAbsolutePath().toString()), bx, pathY, bRight - bx, c.edge());
			int toolY = pathY + 11;
			Component[] tools = { Component.translatable("phantasmon.hub_manager.schematic_open_folder"),
					Component.translatable("phantasmon.hub_manager.schematic_capture"),
					Component.translatable("phantasmon.hub_manager.schematic_remove") };
			for (int i = 0; i < tools.length; i++) {
				int tx = bx + i * (toolW() + 4);
				boolean enabled = i != 2 || hub.schematic() != null;
				MenuUi.button(g, tx, toolY, toolW(), 16, tools[i], enabled && MenuUi.inside(mouseX, mouseY, tx, toolY, toolW(), 16),
						i == 2, enabled && !uploading);
			}
		}

		@Override
		boolean clickBody(double mouseX, double mouseY) {
			int bx = left + 12;
			int by = bodyTop();
			int listH = LIST_ROWS * ROW + 4;
			for (int i = 0; i < LIST_ROWS && scroll + i < files.size(); i++) {
				if (MenuUi.inside(mouseX, mouseY, bx, by + 2 + i * ROW, right - 12 - bx, ROW)) {
					pick = scroll + i;
					return true;
				}
			}
			int toolY = by + listH + 3 + 11;
			for (int i = 0; i < 3; i++) {
				int tx = bx + i * (toolW() + 4);
				if (MenuUi.inside(mouseX, mouseY, tx, toolY, toolW(), 16) && !uploading) {
					MenuUi.click();
					error = null;
					switch (i) {
						case 0 -> net.minecraft.Util.getPlatform().openFile(SCHEMATICS_DIR.toFile());
						case 1 -> captureZone(hub);
						default -> {
							if (hub.schematic() != null) {
								removeBuild(hub);
							}
						}
					}
					return true;
				}
			}
			return false;
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
		boolean scrollBody(double mouseX, double mouseY, double amount) {
			scroll = Mth.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, files.size() - LIST_ROWS));
			return true;
		}

		@Override
		void confirm() {
			if (pick < 0 || pick >= files.size() || uploading) {
				error = Component.translatable("phantasmon.hub_manager.schematic_pick");
				return;
			}
			Path file = files.get(pick);
			String name = uploadName(file);
			error = null;
			upload(hub, name, CompletableFuture.supplyAsync(() -> {
				try {
					return Files.readAllBytes(file);
				} catch (java.io.IOException ex) {
					throw new CompletionException(ex);
				}
			}), Component.translatable("phantasmon.hub_manager.schematic_sent", name, hub.name()));
		}
	}

	// ---------------------------------------------------------------- layout

	@Override
	public void layout(int left, int top, int rightEdge, int bottomEdge) {
		x = left;
		y = top;
		right = rightEdge;
		bottom = bottomEdge;
	}

	private boolean hasTabs() {
		return admin;
	}

	private int listTop() {
		return hasTabs() ? y + CHIP_H + 5 : y;
	}

	private int listRight() {
		return x + (right - x) * 2 / 5;
	}

	private int listBottom() {
		return createAction() != null ? bottom - BUTTON_H - 5 : bottom;
	}

	private int detailX() {
		return listRight() + 8;
	}

	private int tabW() {
		return Math.min(110, (right - x - 3 * (tabs().size() - 1)) / Math.max(1, tabs().size()));
	}

	private int actionsY() {
		return listTop() + 74;
	}

	private int actionW() {
		return (right - detailX() - 4) / 2;
	}

	private int anchorsY(List<HubManagerTab.Action> actions) {
		return actionsY() + ((actions.size() + 1) / 2) * (BUTTON_H + 3) + 4;
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY) {
		MenuUi.Palette c = MenuUi.p();
		if (hasTabs()) {
			List<HubManagerTab> tabs = tabs();
			currentTab();
			for (int i = 0; i < tabs.size(); i++) {
				int tx = x + i * (tabW() + 3);
				HubManagerTab each = tabs.get(i);
				long count = hubs.stream().filter(each::shows).count();
				MenuUi.chip(g, tx, y, tabW(), CHIP_H, Component.literal(each.title().getString() + " (" + count + ")"), i == tab,
						MenuUi.inside(mouseX, mouseY, tx, y, tabW(), CHIP_H));
			}
		}
		renderHubList(g, mouseX, mouseY);
		HubManagerTab.Action create = createAction();
		if (create != null) {
			int cy = bottom - BUTTON_H;
			MenuUi.primary(g, x, cy, listRight() - x, BUTTON_H, create.label(), MenuUi.inside(mouseX, mouseY, x, cy, listRight() - x, BUTTON_H),
					true);
		}
		HubAdminDto hub = selectedHub();
		if (hub == null) {
			MenuUi.centered(g, Component.translatable(loading ? "phantasmon.hub_manager.loading" : "phantasmon.hub_manager.pick"),
					(detailX() + right) / 2, (listTop() + bottom) / 2, right - detailX(), c.muted());
			return;
		}
		renderDetails(g, hub, mouseX, mouseY);
	}

	private void renderHubList(GuiGraphics g, int mouseX, int mouseY) {
		MenuUi.Palette c = MenuUi.p();
		int top = listTop();
		int listBottom = listBottom();
		int w = listRight() - x;
		g.fill(x - 1, top - 1, listRight() + 1, listBottom + 1, c.black());
		g.fill(x, top, listRight(), listBottom, c.dark());
		MenuUi.sunken(g, x, top, listRight(), listBottom);
		List<HubAdminDto> shown = tabHubs();
		if (shown.isEmpty()) {
			MenuUi.centered(g, Component.translatable(loading ? "phantasmon.hub_manager.loading" : "phantasmon.hub_manager.no_hub"),
					x + w / 2, (top + listBottom) / 2 - 4, w - 8, c.muted());
			return;
		}
		int view = listBottom - top - 4;
		int total = shown.size() * HUB_PITCH;
		hubScroll = Mth.clamp(hubScroll, 0, Math.max(0, total - view));
		boolean bar = total > view;
		int rowW = w - 4 - (bar ? 7 : 0);
		g.enableScissor(x + 1, top + 2, listRight() - 1, listBottom - 2);
		for (int i = 0; i < shown.size(); i++) {
			HubAdminDto hub = shown.get(i);
			int ry = top + 2 + i * HUB_PITCH - hubScroll;
			if (ry + HUB_H < top || ry > listBottom) {
				continue;
			}
			boolean isSelected = hub.name().equals(selected);
			MenuUi.row(g, x + 2, ry, rowW, HUB_H, !isSelected && MenuUi.inside(mouseX, mouseY, x + 2, ry, rowW, HUB_H));
			if (isSelected) {
				MenuUi.selectedRow(g, x + 2, ry, rowW, HUB_H);
			}
			String count = String.valueOf(hub.anchors());
			int countW = Minecraft.getInstance().font.width(count) + 12;
			MenuUi.text(g, Component.literal(hub.name()), x + 8, ry + 4, rowW - countW - 10,
					hub.available() ? c.white() : c.edge());
			g.fill(x + 2 + rowW - countW - 2, ry + 3, x + 2 + rowW - countW - 1, ry + 11, c.edge());
			MenuUi.text(g, Component.literal(count), x + 2 + rowW - countW + 2, ry + 4, countW, hub.anchors() > 0 ? c.accent() : c.edge());
			// Players in the hub now (2026-11-11), on the right of the second line, green when someone is there.
			Component online = Component.translatable("phantasmon.hub_manager.online", hub.online());
			int onlineW = Minecraft.getInstance().font.width(online) + 4;
			MenuUi.small(g, Component.literal(size(hub) + "  ·  " + build(hub)), x + 8, ry + 15, rowW - onlineW - 16, c.muted());
			MenuUi.small(g, online, x + 2 + rowW - onlineW - 2, ry + 15, onlineW, hub.online() > 0 ? c.good() : c.edge());
		}
		g.disableScissor();
		MenuUi.scrollbar(g, listRight() - 7, top + 2, view, total, hubScroll, false);
	}

	private void renderDetails(GuiGraphics g, HubAdminDto hub, int mouseX, int mouseY) {
		MenuUi.Palette c = MenuUi.p();
		int dx = detailX();
		int top = listTop();
		int w = right - dx;
		// The hub's card: name, then its facts.
		g.fill(dx - 1, top - 1, right + 1, actionsY() - 3, c.black());
		g.fillGradient(dx, top, right, actionsY() - 4, c.raised(), c.panel());
		MenuUi.raised(g, dx, top, right, actionsY() - 4);
		g.fill(dx, top, dx + 3, actionsY() - 4, c.accent());
		MenuUi.text(g, Component.literal(hub.name()).withStyle(s -> s.withBold(true)), dx + 8, top + 4, w - 12, c.white());
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(Component.translatable("phantasmon.hub_manager.kind", kindLabel(hub)).getString() + "  ·  " + size(hub)));
		lines.add(Component.literal(Component.translatable("phantasmon.hub_manager.build", build(hub)).getString()
				+ (hub.schematic() == null ? "" : "  ·  " + hub.schematic().format() + ", " + Math.max(1, hub.schematic().bytes() / 1024) + " Kio")));
		lines.add(Component.literal(created(hub)));
		lines.add(Component.translatable("phantasmon.hub_manager.online_detail", hub.online()));
		currentTab().details(hub).forEach(lines::add);
		int shown = Math.min(lines.size(), hub.available() ? 5 : 4);
		for (int i = 0; i < shown; i++) {
			MenuUi.small(g, lines.get(i), dx + 8, top + 17 + i * 10, w - 12, c.muted());
		}
		if (!hub.available()) {
			MenuUi.small(g, Component.translatable("phantasmon.hub_manager.unavailable"), dx + 8, top + 17 + 4 * 10, w - 12, c.danger());
		}

		List<HubManagerTab.Action> actions = actions(hub);
		for (int i = 0; i < actions.size(); i++) {
			int ax = dx + (i % 2) * (actionW() + 4);
			int ay = actionsY() + (i / 2) * (BUTTON_H + 3);
			HubManagerTab.Action action = actions.get(i);
			MenuUi.button(g, ax, ay, actionW(), BUTTON_H, action.label(), MenuUi.inside(mouseX, mouseY, ax, ay, actionW(), BUTTON_H),
					action.danger(), true);
		}

		int ay = anchorsY(actions);
		MenuUi.text(g, Component.translatable(admin ? "phantasmon.hub_manager.anchors" : "phantasmon.hub_manager.my_anchors",
				anchors.size()).copy().withStyle(s -> s.withBold(true)), dx, ay, w, c.accent());
		g.fill(dx, ay + 10, right, ay + 11, c.edge());
		int listTop = ay + 14;
		if (anchors.isEmpty()) {
			MenuUi.small(g, Component.translatable(admin ? "phantasmon.hub_manager.no_anchor" : "phantasmon.hub_manager.no_anchor_mine"),
					dx, listTop + 4, w, c.muted());
			return;
		}
		int view = bottom - listTop;
		int total = anchors.size() * ANCHOR_PITCH;
		anchorScroll = Mth.clamp(anchorScroll, 0, Math.max(0, total - view));
		boolean bar = total > view;
		int rowW = w - (bar ? 7 : 0);
		String here = PhantasmonApi.serverFingerprint();
		g.enableScissor(dx, listTop, right, bottom);
		for (int i = 0; i < anchors.size(); i++) {
			HubAdminDto.Anchor anchor = anchors.get(i);
			int ry = listTop + i * ANCHOR_PITCH - anchorScroll;
			if (ry + ANCHOR_H < listTop || ry > bottom) {
				continue;
			}
			MenuUi.row(g, dx, ry, rowW, ANCHOR_H, false);
			String owner = anchor.ownerName() == null ? anchor.ownerUuid().toString() : anchor.ownerName();
			String role = "NORMAL".equals(anchor.role()) ? "" : "  [" + anchor.role() + "]";
			MenuUi.text(g, Component.literal(anchor.name() + role + "  ·  " + owner), dx + 6, ry + 3, rowW - 32, c.white());
			String server = anchor.serverFingerprint().equals(here)
					? Component.translatable("phantasmon.hub_manager.this_server").getString()
					: Component.translatable("phantasmon.hub_manager.other_server",
							anchor.serverFingerprint().substring(0, Math.min(10, anchor.serverFingerprint().length()))).getString();
			String dimension = anchor.dimension() != null && anchor.dimension().startsWith("minecraft:")
					? anchor.dimension().substring("minecraft:".length()) : anchor.dimension();
			MenuUi.small(g, Component.literal(server + "  ·  " + dimension + "  ·  " + Math.round(anchor.x()) + " " + Math.round(anchor.y())
					+ " " + Math.round(anchor.z()) + (anchor.createdAt() == null ? "" : "  ·  " + date(anchor.createdAt()))), dx + 6, ry + 14,
					rowW - 32, c.muted());
			int del = dx + rowW - 22;
			MenuUi.mini(g, del, ry + 5, Component.literal("✕"), MenuUi.inside(mouseX, mouseY, del, ry + 5, 18, 14), true, c.white());
		}
		g.disableScissor();
		MenuUi.scrollbar(g, right - 5, listTop, view, total, anchorScroll, false);
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean click(double mouseX, double mouseY) {
		if (hasTabs()) {
			List<HubManagerTab> tabs = tabs();
			for (int i = 0; i < tabs.size(); i++) {
				if (MenuUi.inside(mouseX, mouseY, x + i * (tabW() + 3), y, tabW(), CHIP_H)) {
					MenuUi.click();
					selectTab(i);
					return true;
				}
			}
		}
		HubManagerTab.Action create = createAction();
		if (create != null && MenuUi.inside(mouseX, mouseY, x, bottom - BUTTON_H, listRight() - x, BUTTON_H)) {
			MenuUi.click();
			run(create, null);
			return true;
		}
		int top = listTop();
		if (MenuUi.inside(mouseX, mouseY, x, top, listRight() - x, listBottom() - top)) {
			List<HubAdminDto> shown = tabHubs();
			int index = (int) ((mouseY - top - 2 + hubScroll) / HUB_PITCH);
			if (index >= 0 && index < shown.size()) {
				MenuUi.click();
				select(shown.get(index).name());
			}
			return true;
		}
		HubAdminDto hub = selectedHub();
		if (hub == null) {
			return false;
		}
		List<HubManagerTab.Action> actions = actions(hub);
		for (int i = 0; i < actions.size(); i++) {
			int ax = detailX() + (i % 2) * (actionW() + 4);
			int ay = actionsY() + (i / 2) * (BUTTON_H + 3);
			if (MenuUi.inside(mouseX, mouseY, ax, ay, actionW(), BUTTON_H)) {
				MenuUi.click();
				run(actions.get(i), hub);
				return true;
			}
		}
		int listTop = anchorsY(actions) + 14;
		int total = anchors.size() * ANCHOR_PITCH;
		int rowW = right - detailX() - (total > bottom - listTop ? 7 : 0);
		for (int i = 0; i < anchors.size(); i++) {
			int ry = listTop + i * ANCHOR_PITCH - anchorScroll;
			int del = detailX() + rowW - 22;
			if (ry >= listTop - 5 && ry < bottom && MenuUi.inside(mouseX, mouseY, del, ry + 5, 18, 14)) {
				MenuUi.click();
				deleteAnchorDialog(anchors.get(i));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean scroll(double mouseX, double mouseY, double amount) {
		int step = (int) Math.round(-amount * 24);
		if (MenuUi.inside(mouseX, mouseY, x, listTop(), listRight() - x, listBottom() - listTop())) {
			hubScroll = Math.max(0, hubScroll + step); // clamped when drawn
			return true;
		}
		if (mouseX >= detailX()) {
			anchorScroll = Math.max(0, anchorScroll + step);
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- text

	private static String size(HubAdminDto hub) {
		return hub.size() == null ? "?" : hub.size().x() + " × " + hub.size().y() + " × " + hub.size().z();
	}

	private static String build(HubAdminDto hub) {
		return hub.schematic() == null ? Component.translatable("phantasmon.hub_manager.no_build").getString() : hub.schematic().name();
	}

	private String created(HubAdminDto hub) {
		String when = hub.createdAt() == null ? "?" : date(hub.createdAt());
		if (!admin) {
			return Component.translatable("phantasmon.hub_manager.created_on", when).getString();
		}
		String by = hub.createdByName() != null ? hub.createdByName()
				: Component.translatable("phantasmon.hub_manager.by_system").getString();
		return Component.translatable("phantasmon.hub_manager.created_at", when, by).getString();
	}

	private static String kindLabel(HubAdminDto hub) {
		return hub.normal() ? Component.translatable("phantasmon.hub_manager.kind.normal").getString() : hub.kind().toLowerCase(Locale.ROOT);
	}

	private static String date(String iso) {
		try {
			return DATE.format(Instant.parse(iso));
		} catch (RuntimeException ex) {
			return iso;
		}
	}
}
