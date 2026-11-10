package com.mystaria.phantasmon.client.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.api.HubManagerTab;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.hub.HubAdminDto;
import com.mystaria.phantasmon.client.hub.HubClient;
import com.mystaria.phantasmon.client.hub.HubController;
import com.mystaria.phantasmon.client.hub.HubPlacement;
import com.mystaria.phantasmon.client.network.BackendApiException;
import com.mystaria.phantasmon.client.network.BackendErrorMessages;

/**
 * The hub manager (Adrien 2026-11-10), opened with its key (H). A player sees the hubs they can join (size, build,
 * creation date, anchor count), their own anchors to the selected hub, each deletable, and PLACER UNE ANCRE, which
 * closes the screen for a preview of the box ({@link HubPlacement}). An admin sees more: one tab per hub family — Phantasmon's own hubs,
 * then the add-ons' ({@link PhantasmonApi#registerHubManagerTab}; Phantasmon Events: its event hubs). Left, the
 * scrolling list of the tab's hubs (size, build, anchors, creation); right, the selected hub's details, its actions
 * (rename, reload the build, delete) and the scrolling list of the anchors leading to it on every server, each
 * deletable. CRÉER UN HUB opens a form (name, width, height, length). Every change asks for confirmation when it
 * destroys something, then reloads the lists. Everything is drawn on the Phantasmon canvas (no vanilla widget).
 */
public final class PhantasmonHubManagerScreen extends PhantasmonCanvasScreen {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").withZone(ZoneId.systemDefault());

	private static final int CLOSE_SIZE = 36;
	private static final int CLOSE_X = 1585 - 6 - CLOSE_SIZE;
	private static final int CLOSE_Y = 15 + (48 - CLOSE_SIZE) / 2;

	private static final int TAB_X = 30;
	private static final int TAB_Y = 78;
	private static final int TAB_W = 260;
	private static final int TAB_H = 40;
	private static final int TAB_GAP = 10;

	private static final int LIST_X = 30;
	private static final int LIST_Y = 132;
	private static final int LIST_W = 620;
	private static final int LIST_H = 694;
	private static final int ROW_H = 86;
	private static final int VISIBLE_HUBS = LIST_H / ROW_H;

	private static final int DETAIL_X = 670;
	private static final int DETAIL_Y = 132;
	private static final int DETAIL_W = 900;
	private static final int ACTION_Y = 300;
	private static final int ACTION_H = 36;
	private static final int ANCHOR_Y = 360;
	private static final int ANCHOR_H = 466;
	private static final int ANCHOR_HEAD = 42;
	private static final int ANCHOR_ROW_H = 60;
	private static final int VISIBLE_ANCHORS = (ANCHOR_H - ANCHOR_HEAD) / ANCHOR_ROW_H;

	private static final int CREATE_X = 1180;
	private static final int CREATE_W = 280;
	private static final int FOOT_BUTTON_Y = 849;
	private static final int FOOT_BUTTON_H = 28;
	private static final int CLOSE_TEXT_X = 1475;
	private static final int CLOSE_TEXT_W = 97;

	private static final int MODAL_X = 450;
	private static final int MODAL_W = 700;
	private static final int FIELD_H = 50;
	private static final int MODAL_BUTTON_W = 200;
	private static final int MODAL_BUTTON_H = 36;

	private enum Modal { NONE, CREATE, RENAME, PLACE, SCHEMATIC, DELETE_HUB, DELETE_ANCHOR }

	private final HubClient client;
	private final AuthSession session;
	private final HubController controller;
	/** An admin's manager; otherwise a player's (their anchors only, no hub management). */
	private final boolean admin;
	private final List<HubManagerTab> addonTabs = PhantasmonApi.hubManagerTabs();

	private int tab;
	private List<HubAdminDto> hubs = List.of();
	private List<HubAdminDto.Anchor> anchors = List.of();
	private String selected;
	private int hubScroll;
	private int anchorScroll;
	private boolean loading = true;
	private Component status = Component.empty();
	private int statusColor;

	private Modal modal = Modal.NONE;
	private HubAdminDto.Anchor anchorToDelete;
	/** The form's fields: create = name, width, height, length; rename = name. */
	private final String[] fields = { "", "", "", "" };
	private int focusedField;

	/** The schematic picker (admins): the .schem / .litematic files of the game's schematics/ folder. */
	private static final java.nio.file.Path SCHEMATICS_DIR = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
			.resolve("schematics");
	private static final int SCHEM_ROW_H = 40;
	private static final int SCHEM_ROWS = 8;
	private List<java.nio.file.Path> schematicFiles = List.of();
	private int schematicScroll;
	private int schematicPick = -1;
	private boolean uploading;

	public PhantasmonHubManagerScreen(HubClient client, AuthSession session, HubController controller, boolean admin) {
		super(Component.translatable("phantasmon.hub_manager.title"));
		this.client = client;
		this.session = session;
		this.controller = controller;
		this.admin = admin;
		statusColor = MUTED;
		reload();
	}

	// =====================================================================
	// Data
	// =====================================================================

	/** Phantasmon's own hubs: the ordinary ones. */
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

	/** Hubs no tab shows: a kind whose add-on is not installed (D-41) — kept in the database, hidden from players. */
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

	/** The tabs: Phantasmon's, the add-ons', then "others" when some hub fits none of them. */
	private List<HubManagerTab> tabs() {
		List<HubManagerTab> tabs = new ArrayList<>();
		tabs.add(coreTab);
		if (!admin) {
			return tabs; // players: Phantasmon's classic hubs only, never an add-on's (Adrien 2026-11-10)
		}
		tabs.addAll(addonTabs);
		if (hubs.stream().anyMatch(otherTab::shows)) {
			tabs.add(otherTab);
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

	private Predicate<HubAdminDto> tabFilter() {
		return currentTab()::shows;
	}

	private List<HubAdminDto> tabHubs() {
		return hubs.stream().filter(tabFilter()).toList();
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
			if (selectedHub() == null || shown.stream().noneMatch(hub -> hub.name().equals(selected))) {
				selected = shown.isEmpty() ? null : shown.get(0).name();
				anchorScroll = 0;
			}
			hubScroll = Math.min(hubScroll, Math.max(0, shown.size() - VISIBLE_HUBS));
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
			// A player: their own anchors to that hub, on every server.
			client.mine(session.accessToken()).thenAccept(result -> onMain(() -> {
				if (hub.equals(selected)) {
					anchors = result == null ? List.of() : Arrays.stream(result)
							.filter(anchor -> anchor.hub().equalsIgnoreCase(hub))
							.map(anchor -> new HubAdminDto.Anchor(anchor.uuid(), anchor.name(), anchor.ownerUuid(), session.username(),
									anchor.serverFingerprint(), anchor.dimension(), anchor.origin().x(), anchor.origin().y(),
									anchor.origin().z(), anchor.yaw(), anchor.role(), null))
							.toList();
					anchorScroll = Math.min(anchorScroll, Math.max(0, anchors.size() - VISIBLE_ANCHORS));
				}
			})).exceptionally(this::fail);
			return;
		}
		client.adminAnchors(session.accessToken(), hub).thenAccept(result -> onMain(() -> {
			if (hub.equals(selected)) {
				anchors = result == null ? List.of() : Arrays.asList(result);
				anchorScroll = Math.min(anchorScroll, Math.max(0, anchors.size() - VISIBLE_ANCHORS));
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

	private Void fail(Throwable throwable) {
		Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
		String key = cause instanceof BackendApiException api ? BackendErrorMessages.translationKey(api.errorCode())
				: "phantasmon.error.network";
		onMain(() -> {
			loading = false;
			setStatus(Component.literal(stripPrefix(Component.translatable(key).getString())), ERROR_TEXT);
		});
		return null;
	}

	private void setStatus(Component message, int color) {
		status = message;
		statusColor = color;
	}

	private static void onMain(Runnable action) {
		Minecraft.getInstance().execute(action);
	}

	// =====================================================================
	// Actions
	// =====================================================================

	private void openModal(Modal next) {
		if (next == Modal.SCHEMATIC) {
			schematicFiles = listSchematics();
			schematicScroll = 0;
			schematicPick = -1;
		}
		modal = next;
		Arrays.fill(fields, "");
		focusedField = 0;
		if (next == Modal.RENAME && selected != null) {
			fields[0] = selected;
		}
	}

	private void confirmModal() {
		HubAdminDto hub = selectedHub();
		switch (modal) {
			case CREATE -> {
				Integer width = number(fields[1]);
				Integer height = number(fields[2]);
				Integer length = number(fields[3]);
				if (fields[0].isBlank() || width == null || height == null || length == null) {
					setStatus(Component.translatable("phantasmon.hub_manager.form_incomplete"), ERROR_TEXT);
					return;
				}
				String name = fields[0];
				client.createHub(session.accessToken(), name, length, width, height).thenAccept(created -> onMain(() -> {
					setStatus(Component.translatable("phantasmon.hub_manager.created", created.name()), READY_TEXT);
					selected = created.name();
					reload();
				})).exceptionally(this::fail);
			}
			case RENAME -> {
				if (hub == null || fields[0].isBlank()) {
					return;
				}
				String oldName = hub.name();
				client.renameHub(session.accessToken(), oldName, fields[0]).thenAccept(renamed -> onMain(() -> {
					setStatus(Component.translatable("phantasmon.hub_manager.renamed", oldName, renamed.name()), READY_TEXT);
					selected = renamed.name();
					reload();
				})).exceptionally(this::fail);
			}
			case SCHEMATIC -> {
				if (hub != null) {
					uploadSchematic(hub); // the picker closes once the backend accepted the file
				}
				return;
			}
			case PLACE -> {
				if (hub == null) {
					return;
				}
				String name = fields[0].isBlank() ? null : fields[0].strip();
				modal = Modal.NONE;
				onClose();
				HubPlacement.start(controller, hub.name(), name, hub.size());
				return;
			}
			case DELETE_HUB -> {
				if (hub == null) {
					return;
				}
				client.deleteHub(session.accessToken(), hub.name()).thenAccept(deleted -> onMain(() -> {
					controller.refreshAnchors();
					setStatus(Component.translatable("phantasmon.hub_manager.deleted", deleted.hub(), deleted.anchorsDeleted()),
							READY_TEXT);
					selected = null;
					reload();
				})).exceptionally(this::fail);
			}
			case DELETE_ANCHOR -> {
				HubAdminDto.Anchor anchor = anchorToDelete;
				if (anchor == null) {
					return;
				}
				client.delete(session.accessToken(), anchor.uuid()).thenAccept(done -> onMain(() -> {
					setStatus(Component.translatable("phantasmon.hub_manager.anchor_deleted", anchor.name()), READY_TEXT);
					controller.refreshAnchors(); // its build goes at once here, not at the next 30 s refresh
					reload();
				})).exceptionally(this::fail);
			}
			default -> {
			}
		}
		modal = Modal.NONE;
	}

	/** The schematics/ folder's .schem and .litematic files (sub-folders too, as Litematica saves them), newest first. */
	private static List<java.nio.file.Path> listSchematics() {
		try {
			java.nio.file.Files.createDirectories(SCHEMATICS_DIR);
			try (var files = java.nio.file.Files.walk(SCHEMATICS_DIR, 4)) {
				return files.filter(java.nio.file.Files::isRegularFile)
						.filter(path -> {
							String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
							return name.endsWith(".schem") || name.endsWith(".litematic");
						})
						.sorted(java.util.Comparator.comparingLong((java.nio.file.Path path) -> path.toFile().lastModified()).reversed())
						.toList();
			}
		} catch (java.io.IOException ex) {
			return List.of();
		}
	}

	/** The name the backend accepts: letters, digits, space, _ . ( ) -; anything else becomes _. */
	private static String uploadName(java.nio.file.Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String base = name.substring(0, dot).replaceAll("[^A-Za-z0-9 _.()-]", "_");
		if (base.isBlank()) {
			base = "hub";
		}
		return (base.length() > 90 ? base.substring(0, 90) : base) + name.substring(dot).toLowerCase(Locale.ROOT);
	}

	private void uploadSchematic(HubAdminDto hub) {
		if (schematicPick < 0 || schematicPick >= schematicFiles.size() || uploading) {
			return;
		}
		java.nio.file.Path file = schematicFiles.get(schematicPick);
		String name = uploadName(file);
		uploading = true;
		setStatus(Component.translatable("phantasmon.hub_manager.schematic_sending", name), MUTED);
		java.util.concurrent.CompletableFuture.supplyAsync(() -> {
			try {
				return java.nio.file.Files.readAllBytes(file);
			} catch (java.io.IOException ex) {
				throw new CompletionException(ex);
			}
		}).thenCompose(bytes -> client.uploadSchematic(session.accessToken(), hub.name(), name, bytes))
				.thenAccept(view -> onMain(() -> {
					uploading = false;
					modal = Modal.NONE;
					setStatus(Component.translatable("phantasmon.hub_manager.schematic_sent", name, view.name()), READY_TEXT);
					reload();
				}))
				.exceptionally(throwable -> {
					onMain(() -> uploading = false);
					return failSchematic(throwable);
				});
	}

	private void removeBuild(HubAdminDto hub) {
		client.removeSchematic(session.accessToken(), hub.name()).thenAccept(view -> onMain(() -> {
			modal = Modal.NONE;
			setStatus(Component.translatable("phantasmon.hub_manager.schematic_removed", view.name()), READY_TEXT);
			reload();
		})).exceptionally(this::failSchematic);
	}

	/** Like {@link #fail}, with the backend's reason when the file was refused (wrong size, unreadable). */
	private Void failSchematic(Throwable throwable) {
		Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
		if (cause instanceof BackendApiException api && "ERROR_HUB_SCHEMATIC_INVALID".equals(api.errorCode())
				&& api.details() != null && api.details().get("reason") != null) {
			onMain(() -> setStatus(Component.translatable("phantasmon.hub_manager.schematic_refused", api.details().get("reason")),
					ERROR_TEXT));
			return null;
		}
		return fail(throwable);
	}

	private void reloadBuild(HubAdminDto hub) {
		client.reloadHub(session.accessToken(), hub.name()).thenAccept(view -> onMain(() -> {
			setStatus(Component.translatable("phantasmon.hub_manager.reloaded", view.name()), READY_TEXT);
			reload();
		})).exceptionally(this::fail);
	}

	private static Integer number(String text) {
		try {
			return text.isBlank() ? null : Integer.parseInt(text);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	/** The buttons under the selected hub: Phantasmon's, then the tab's own. */
	private List<HubManagerTab.Action> actions(HubAdminDto hub) {
		List<HubManagerTab.Action> actions = new ArrayList<>();
		HubManagerTab.Action place = new HubManagerTab.Action(Component.translatable("phantasmon.hub_manager.place"), false,
				(h, done, report) -> openModal(Modal.PLACE));
		if (!admin) {
			if (hub.normal()) {
				actions.add(place);
			}
			return actions;
		}
		actions.add(new HubManagerTab.Action(Component.translatable("phantasmon.hub_manager.rename"), false,
				(h, done, report) -> openModal(Modal.RENAME)));
		if (hub.normal()) {
			actions.add(new HubManagerTab.Action(Component.translatable("phantasmon.hub_manager.schematic"), false,
					(h, done, report) -> openModal(Modal.SCHEMATIC)));
			actions.add(new HubManagerTab.Action(Component.translatable("phantasmon.hub_manager.reload"), false,
					(h, done, report) -> reloadBuild(h)));
			actions.add(place);
		}
		actions.addAll(currentTab().actions());
		actions.add(new HubManagerTab.Action(Component.translatable("phantasmon.hub_manager.delete"), true,
				(h, done, report) -> openModal(Modal.DELETE_HUB)));
		return actions;
	}

	private HubManagerTab.Action createAction() {
		if (!admin) {
			return null;
		}
		if (currentTab() == coreTab) {
			return new HubManagerTab.Action(Component.translatable("phantasmon.hub_manager.create"), false,
					(h, done, report) -> openModal(Modal.CREATE));
		}
		return currentTab().createAction();
	}

	private void run(HubManagerTab.Action action, HubAdminDto hub) {
		action.run().run(hub, () -> onMain(this::reload), message -> onMain(() -> setStatus(message, MUTED)));
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
		if (modal != Modal.NONE) {
			return clickModal(x, y);
		}
		if (inside(x, y, CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE)
				|| inside(x, y, CLOSE_TEXT_X, FOOT_BUTTON_Y, CLOSE_TEXT_W, FOOT_BUTTON_H)) {
			onClose();
			return true;
		}
		int tabCount = tabs().size();
		for (int i = 0; i < tabCount; i++) {
			if (inside(x, y, TAB_X + i * (TAB_W + TAB_GAP), TAB_Y, TAB_W, TAB_H)) {
				if (tab != i) {
					tab = i;
					hubScroll = 0;
					List<HubAdminDto> shown = tabHubs();
					selected = shown.isEmpty() ? null : shown.get(0).name();
					anchors = List.of();
					anchorScroll = 0;
					loadAnchors();
				}
				return true;
			}
		}
		HubManagerTab.Action create = createAction();
		if (create != null && inside(x, y, CREATE_X, FOOT_BUTTON_Y, CREATE_W, FOOT_BUTTON_H)) {
			run(create, null);
			return true;
		}
		List<HubAdminDto> shown = tabHubs();
		for (int row = 0; row < VISIBLE_HUBS && hubScroll + row < shown.size(); row++) {
			if (inside(x, y, LIST_X, LIST_Y + row * ROW_H, LIST_W, ROW_H)) {
				select(shown.get(hubScroll + row).name());
				return true;
			}
		}
		HubAdminDto hub = selectedHub();
		if (hub != null) {
			List<HubManagerTab.Action> actions = actions(hub);
			for (int i = 0; i < actions.size(); i++) {
				if (inside(x, y, actionX(actions, i), ACTION_Y, actionW(actions), ACTION_H)) {
					run(actions.get(i), hub);
					return true;
				}
			}
			for (int row = 0; row < VISIBLE_ANCHORS && anchorScroll + row < anchors.size(); row++) {
				int rowY = ANCHOR_Y + ANCHOR_HEAD + row * ANCHOR_ROW_H;
				if (inside(x, y, DETAIL_X + DETAIL_W - 60, rowY + 12, 36, 36)) {
					anchorToDelete = anchors.get(anchorScroll + row);
					modal = Modal.DELETE_ANCHOR;
					return true;
				}
			}
		}
		return true;
	}

	private boolean clickModal(double x, double y) {
		if (modal == Modal.SCHEMATIC) {
			return clickSchematic(x, y);
		}
		int fieldCount = fieldCount();
		for (int i = 0; i < fieldCount; i++) {
			if (inside(x, y, fieldX(i), fieldY(i), fieldW(i), FIELD_H)) {
				focusedField = i;
				return true;
			}
		}
		int buttonY = modalY() + modalH() - MODAL_BUTTON_H - 22;
		if (inside(x, y, 800 - MODAL_BUTTON_W - 10, buttonY, MODAL_BUTTON_W, MODAL_BUTTON_H)) {
			modal = Modal.NONE;
			return true;
		}
		if (inside(x, y, 810, buttonY, MODAL_BUTTON_W, MODAL_BUTTON_H)) {
			confirmModal();
			return true;
		}
		return true;
	}

	private boolean clickSchematic(double x, double y) {
		HubAdminDto hub = selectedHub();
		int top = modalY();
		for (int row = 0; row < SCHEM_ROWS && schematicScroll + row < schematicFiles.size(); row++) {
			if (inside(x, y, MODAL_X + 30, schemListY() + row * SCHEM_ROW_H, MODAL_W - 60, SCHEM_ROW_H)) {
				schematicPick = schematicScroll + row;
				return true;
			}
		}
		if (inside(x, y, MODAL_X + MODAL_W - 30 - 240, schemFolderY() - 6, 240, 28)) {
			net.minecraft.Util.getPlatform().openFile(SCHEMATICS_DIR.toFile());
			return true;
		}
		int buttonY = top + modalH() - MODAL_BUTTON_H - 22;
		if (inside(x, y, MODAL_X + 30, buttonY, 180, MODAL_BUTTON_H)) {
			modal = Modal.NONE;
		} else if (hub != null && hub.schematic() != null && inside(x, y, 800 - 120, buttonY, 240, MODAL_BUTTON_H)) {
			removeBuild(hub);
		} else if (hub != null && inside(x, y, MODAL_X + MODAL_W - 30 - 180, buttonY, 180, MODAL_BUTTON_H)) {
			uploadSchematic(hub);
		}
		return true;
	}

	private int schemListY() {
		return modalY() + 96;
	}

	private int schemFolderY() {
		return schemListY() + SCHEM_ROWS * SCHEM_ROW_H + 18;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (modal == Modal.SCHEMATIC) {
			schematicScroll = clamp(schematicScroll - (int) Math.signum(scrollY), schematicFiles.size() - SCHEM_ROWS);
			return true;
		}
		if (modal != Modal.NONE) {
			return true;
		}
		double x = toCanvasX(mouseX);
		double y = toCanvasY(mouseY);
		int step = -(int) Math.signum(scrollY);
		if (inside(x, y, LIST_X, LIST_Y, LIST_W, LIST_H)) {
			hubScroll = clamp(hubScroll + step, tabHubs().size() - VISIBLE_HUBS);
		} else if (inside(x, y, DETAIL_X, ANCHOR_Y, DETAIL_W, ANCHOR_H)) {
			anchorScroll = clamp(anchorScroll + step, anchors.size() - VISIBLE_ANCHORS);
		}
		return true;
	}

	private static int clamp(int value, int max) {
		return Math.max(0, Math.min(Math.max(0, max), value));
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (fieldCount() == 0) {
			return super.charTyped(chr, modifiers);
		}
		String value = fields[focusedField];
		if (modal == Modal.PLACE) {
			// Anchor names: letters, digits, space, _ and -, up to 32 (the backend checks the rest).
			if (value.length() < 32 && (Character.isLetterOrDigit(chr) || chr == ' ' || chr == '_' || chr == '-')) {
				fields[0] = value + chr;
			}
		} else if (focusedField == 0) {
			// Hub names: lower-case letters, digits and _, 2 to 32 (the backend's rule); a space becomes _.
			char c = chr == ' ' || chr == '-' ? '_' : Character.toLowerCase(chr);
			if (value.length() < 32 && (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_')) {
				fields[0] = value + c;
			}
		} else if (Character.isDigit(chr) && value.length() < 2) {
			fields[focusedField] = value + chr;
		}
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (modal != Modal.NONE) {
			int fieldCount = fieldCount();
			switch (keyCode) {
				case GLFW.GLFW_KEY_ESCAPE -> modal = Modal.NONE;
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> confirmModal();
				case GLFW.GLFW_KEY_TAB -> {
					if (fieldCount > 0) {
						focusedField = Math.floorMod(focusedField + (hasShiftDown() ? -1 : 1), fieldCount);
					}
				}
				case GLFW.GLFW_KEY_BACKSPACE -> {
					if (fieldCount > 0 && !fields[focusedField].isEmpty()) {
						fields[focusedField] = fields[focusedField].substring(0, fields[focusedField].length() - 1);
					}
				}
				default -> {
				}
			}
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	// =====================================================================
	// Rendering
	// =====================================================================

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		renderWorldBackdrop(g, mouseX, mouseY, partialTick);
		double mx = modal == Modal.NONE ? toCanvasX(mouseX) : -1;
		double my = modal == Modal.NONE ? toCanvasY(mouseY) : -1;
		beginCanvas(g);
		renderRootPanel(g);
		renderHeaderPlates(g, 1500);
		drawText(g, upper(Component.translatable("phantasmon.hub_manager.title").getString()), 31, 32, 2f, WHITE, true, 1.5f);
		renderCloseButton(g, CLOSE_X, CLOSE_Y, CLOSE_SIZE, inside(mx, my, CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE));

		renderTabs(g, mx, my);
		renderHubList(g, mx, my);
		renderDetails(g, mx, my);

		renderFooterBar(g, status.getString(), statusColor, CREATE_X - 40);
		HubManagerTab.Action create = createAction();
		if (create != null) {
			renderLabelButton(g, CREATE_X, FOOT_BUTTON_Y, CREATE_W, FOOT_BUTTON_H, create.label().getString(), create.danger(),
					inside(mx, my, CREATE_X, FOOT_BUTTON_Y, CREATE_W, FOOT_BUTTON_H));
		}
		renderButton(g, CLOSE_TEXT_X, FOOT_BUTTON_Y, CLOSE_TEXT_W, FOOT_BUTTON_H, "phantasmon.hub_manager.close", false,
				inside(mx, my, CLOSE_TEXT_X, FOOT_BUTTON_Y, CLOSE_TEXT_W, FOOT_BUTTON_H));
		endCanvas(g);

		if (modal != Modal.NONE) {
			renderModal(g, mouseX, mouseY);
		}
	}

	private void renderTabs(GuiGraphics g, double mx, double my) {
		List<HubManagerTab> tabs = tabs();
		currentTab();
		for (int i = 0; i < tabs.size(); i++) {
			int tx = TAB_X + i * (TAB_W + TAB_GAP);
			boolean current = i == tab;
			HubManagerTab shownTab = tabs.get(i);
			long count = hubs.stream().filter(shownTab::shows).count();
			String title = shownTab.title().getString();
			renderPrimaryButtonFrame(g, tx, TAB_Y, TAB_W, TAB_H, inside(mx, my, tx, TAB_Y, TAB_W, TAB_H), current);
			String label = fitText(upper(title) + " (" + count + ")", TAB_W - 16, 2f, true, 1f);
			drawCentered(g, label, tx + TAB_W / 2f, TAB_Y + 13, 2f, current ? READY_TEXT : CYAN, true, 1f);
		}
	}

	private void renderHubList(GuiGraphics g, double mx, double my) {
		g.fill(LIST_X, LIST_Y, LIST_X + LIST_W, LIST_Y + LIST_H, INFO_BOX_BG);
		outline(g, LIST_X, LIST_Y, LIST_W, LIST_H, LINE_20);
		List<HubAdminDto> shown = tabHubs();
		if (shown.isEmpty()) {
			String empty = Component.translatable(loading ? "phantasmon.hub_manager.loading" : "phantasmon.hub_manager.no_hub").getString();
			drawCentered(g, empty, LIST_X + LIST_W / 2f, LIST_Y + LIST_H / 2f - 7, 2f, DIM, false, 0f);
			return;
		}
		for (int row = 0; row < VISIBLE_HUBS && hubScroll + row < shown.size(); row++) {
			HubAdminDto hub = shown.get(hubScroll + row);
			int rowY = LIST_Y + row * ROW_H;
			boolean isSelected = hub.name().equals(selected);
			if (isSelected) {
				g.fill(LIST_X + 1, rowY, LIST_X + LIST_W - 1, rowY + ROW_H, SLOT_SELECTED_BG);
				g.fill(LIST_X + 1, rowY, LIST_X + 5, rowY + ROW_H, CYAN);
			} else if (inside(mx, my, LIST_X, rowY, LIST_W, ROW_H)) {
				g.fill(LIST_X + 1, rowY, LIST_X + LIST_W - 1, rowY + ROW_H, SLOT_HOVER_BG);
			}
			g.fill(LIST_X + 10, rowY + ROW_H - 1, LIST_X + LIST_W - 10, rowY + ROW_H, LINE_12);
			drawText(g, fitText(hub.name(), LIST_W - 200, 2f, true, 1f), LIST_X + 18, rowY + 12, 2f,
					hub.available() ? WHITE : DIM, true, 1f);
			String anchorsLabel = Component.translatable("phantasmon.hub_manager.anchor_count", hub.anchors()).getString();
			float anchorsWidth = textWidth(anchorsLabel, 2f, false, 0f);
			drawText(g, anchorsLabel, LIST_X + LIST_W - 18 - anchorsWidth, rowY + 12, 2f, hub.anchors() > 0 ? CYAN : DIM, false, 0f);
			drawText(g, fitText(size(hub) + "  ·  " + build(hub), LIST_W - 36, 1f, false, 0f), LIST_X + 18, rowY + 40, 1f, TEXT2,
					false, 0f);
			drawText(g, fitText(created(hub), LIST_W - 36, 1f, false, 0f), LIST_X + 18, rowY + 60, 1f, MUTED, false, 0f);
		}
		renderScrollbar(g, LIST_X + LIST_W - 8, LIST_Y + 4, LIST_H - 8, shown.size(), VISIBLE_HUBS, hubScroll);
	}

	private void renderDetails(GuiGraphics g, double mx, double my) {
		HubAdminDto hub = selectedHub();
		int boxH = ACTION_Y - DETAIL_Y - 16;
		g.fill(DETAIL_X, DETAIL_Y, DETAIL_X + DETAIL_W, DETAIL_Y + boxH, INFO_BOX_BG);
		outline(g, DETAIL_X, DETAIL_Y, DETAIL_W, boxH, LINE_20);
		if (hub == null) {
			drawCentered(g, Component.translatable("phantasmon.hub_manager.pick").getString(), DETAIL_X + DETAIL_W / 2f,
					DETAIL_Y + boxH / 2f - 7, 2f, DIM, false, 0f);
			return;
		}
		drawText(g, fitText(upper(hub.name()), DETAIL_W - 40, 2f, true, 1.5f), DETAIL_X + 18, DETAIL_Y + 14, 2f, TITLE, true, 1.5f);
		List<String> lines = new ArrayList<>();
		lines.add(Component.translatable("phantasmon.hub_manager.kind", kindLabel(hub)).getString() + "  ·  " + size(hub));
		lines.add(Component.translatable("phantasmon.hub_manager.build", build(hub)).getString()
				+ (hub.schematic() == null ? "" : "  ·  " + hub.schematic().format() + ", " + hub.schematic().bytes() / 1024
						+ " Kio, sha256 " + hub.schematic().sha256().substring(0, Math.min(12, hub.schematic().sha256().length()))));
		lines.add(created(hub));
		if (!hub.available()) {
			lines.add(Component.translatable("phantasmon.hub_manager.unavailable").getString());
		}
		currentTab().details(hub).forEach(line -> lines.add(line.getString()));
		for (int i = 0; i < lines.size() && i < 5; i++) {
			drawText(g, fitText(lines.get(i), DETAIL_W - 36, 1f, false, 0f), DETAIL_X + 18, DETAIL_Y + 46 + i * 20, 1f,
					i == 3 && !hub.available() ? ERROR_TEXT : TEXT2, false, 0f);
		}

		List<HubManagerTab.Action> actions = actions(hub);
		for (int i = 0; i < actions.size(); i++) {
			int ax = actionX(actions, i);
			renderLabelButton(g, ax, ACTION_Y, actionW(actions), ACTION_H, actions.get(i).label().getString(), actions.get(i).danger(),
					inside(mx, my, ax, ACTION_Y, actionW(actions), ACTION_H));
		}

		renderAnchors(g, mx, my);
	}

	private void renderAnchors(GuiGraphics g, double mx, double my) {
		g.fill(DETAIL_X, ANCHOR_Y, DETAIL_X + DETAIL_W, ANCHOR_Y + ANCHOR_H, INFO_BOX_BG);
		outline(g, DETAIL_X, ANCHOR_Y, DETAIL_W, ANCHOR_H, LINE_20);
		drawText(g, upper(Component.translatable(admin ? "phantasmon.hub_manager.anchors" : "phantasmon.hub_manager.my_anchors", anchors.size()).getString()), DETAIL_X + 18,
				ANCHOR_Y + 14, 2f, MUTED, false, 2f);
		g.fill(DETAIL_X + 9, ANCHOR_Y + ANCHOR_HEAD - 4, DETAIL_X + DETAIL_W - 9, ANCHOR_Y + ANCHOR_HEAD - 4 + hairline(), LINE_25);
		if (anchors.isEmpty()) {
			drawCentered(g, Component.translatable(admin ? "phantasmon.hub_manager.no_anchor" : "phantasmon.hub_manager.no_anchor_mine").getString(), DETAIL_X + DETAIL_W / 2f,
					ANCHOR_Y + ANCHOR_H / 2f, 2f, DIM, false, 0f);
			return;
		}
		String here = PhantasmonApi.serverFingerprint();
		for (int row = 0; row < VISIBLE_ANCHORS && anchorScroll + row < anchors.size(); row++) {
			HubAdminDto.Anchor anchor = anchors.get(anchorScroll + row);
			int rowY = ANCHOR_Y + ANCHOR_HEAD + row * ANCHOR_ROW_H;
			g.fill(DETAIL_X + 10, rowY + ANCHOR_ROW_H - 1, DETAIL_X + DETAIL_W - 10, rowY + ANCHOR_ROW_H, LINE_12);
			String owner = anchor.ownerName() == null ? anchor.ownerUuid().toString() : anchor.ownerName();
			String role = "NORMAL".equals(anchor.role()) ? "" : "  [" + anchor.role() + "]";
			drawText(g, fitText(anchor.name() + role + "  ·  " + owner, DETAIL_W - 110, 2f, false, 0f), DETAIL_X + 18, rowY + 8, 2f,
					WHITE, false, 0f);
			String server = anchor.serverFingerprint().equals(here)
					? Component.translatable("phantasmon.hub_manager.this_server").getString()
					: Component.translatable("phantasmon.hub_manager.other_server",
							anchor.serverFingerprint().substring(0, Math.min(10, anchor.serverFingerprint().length()))).getString();
			String place = server + "  ·  " + shortDimension(anchor.dimension()) + "  ·  "
					+ Math.round(anchor.x()) + " " + Math.round(anchor.y()) + " " + Math.round(anchor.z())
					+ (anchor.createdAt() == null ? "" : "  ·  " + date(anchor.createdAt()));
			drawText(g, fitText(place, DETAIL_W - 110, 1f, false, 0f), DETAIL_X + 18, rowY + 34, 1f, MUTED, false, 0f);
			int bx = DETAIL_X + DETAIL_W - 60;
			renderCloseButton(g, bx, rowY + 12, 36, inside(mx, my, bx, rowY + 12, 36, 36));
		}
		renderScrollbar(g, DETAIL_X + DETAIL_W - 8, ANCHOR_Y + ANCHOR_HEAD, ANCHOR_H - ANCHOR_HEAD - 4, anchors.size(),
				VISIBLE_ANCHORS, anchorScroll);
	}

	private void renderScrollbar(GuiGraphics g, int x, int y, int trackH, int total, int visible, int scroll) {
		if (total <= visible) {
			return;
		}
		int thumbH = Math.max(30, trackH * visible / total);
		int thumbY = y + (trackH - thumbH) * scroll / Math.max(1, total - visible);
		g.fill(x, thumbY, x + 4, thumbY + thumbH, CYAN2);
	}

	private void renderLabelButton(GuiGraphics g, int x, int y, int w, int h, String label, boolean danger, boolean hovered) {
		int[] gradient = danger ? (hovered ? DANGER_BUTTON_HOVER : DANGER_BUTTON) : (hovered ? STD_BUTTON_HOVER : STD_BUTTON);
		g.fillGradient(x, y, x + w, y + h, gradient[0], gradient[1]);
		outline(g, x, y, w, h, danger ? DANGER_BORDER : CYAN);
		String text = fitText(upper(label), w - 12, 1f, true, 1.5f);
		drawCentered(g, text, x + w / 2f, y + (h - 7 * snapTextScale(1f)) / 2f, 1f, WHITE, true, 1.5f);
	}

	private static int actionW(List<HubManagerTab.Action> actions) {
		return Math.min(220, (DETAIL_W - 10 * (actions.size() - 1)) / actions.size());
	}

	private static int actionX(List<HubManagerTab.Action> actions, int index) {
		return DETAIL_X + index * (actionW(actions) + 10);
	}

	// ---- Modals ----

	private int fieldCount() {
		return switch (modal) {
			case CREATE -> 4;
			case RENAME, PLACE -> 1;
			default -> 0;
		};
	}

	private int modalH() {
		return switch (modal) {
			case CREATE -> 400;
			case RENAME -> 240;
			case PLACE -> 260;
			case SCHEMATIC -> 560;
			default -> 190;
		};
	}

	private int modalY() {
		return 450 - modalH() / 2;
	}

	private int fieldX(int field) {
		return field == 0 ? MODAL_X + 30 : MODAL_X + 30 + (field - 1) * 216;
	}

	private int fieldW(int field) {
		return field == 0 ? MODAL_W - 60 : 208;
	}

	private int fieldY(int field) {
		return modalY() + 76 + (field == 0 ? 0 : FIELD_H + 40);
	}

	private void renderModal(GuiGraphics g, int mouseX, int mouseY) {
		beginModalLayer(g, 1f);
		double mx = toCanvasX(mouseX);
		double my = toCanvasY(mouseY);
		int y = modalY();
		int h = modalH();
		renderModalCard(g, MODAL_X, y, MODAL_W, h, 1f);
		HubAdminDto hub = selectedHub();
		String title = switch (modal) {
			case CREATE -> Component.translatable("phantasmon.hub_manager.create_title").getString();
			case RENAME -> Component.translatable("phantasmon.hub_manager.rename_title", hub == null ? "?" : hub.name()).getString();
			case PLACE -> Component.translatable("phantasmon.hub_manager.place_title", hub == null ? "?" : hub.name()).getString();
			case SCHEMATIC -> Component.translatable("phantasmon.hub_manager.schematic_title", hub == null ? "?" : hub.name()).getString();
			case DELETE_HUB -> Component.translatable("phantasmon.hub_manager.delete_title", hub == null ? "?" : hub.name()).getString();
			case DELETE_ANCHOR -> Component.translatable("phantasmon.hub_manager.delete_anchor_title",
					anchorToDelete == null ? "?" : anchorToDelete.name()).getString();
			default -> "";
		};
		drawCentered(g, fitText(upper(title), MODAL_W - 40, 2f, true, 2f), 800, y + 26, 2f, TITLE, true, 2f);
		switch (modal) {
			case CREATE -> {
				renderField(g, 0, "phantasmon.hub_manager.field.name", mx, my);
				renderField(g, 1, "phantasmon.hub_manager.field.width", mx, my);
				renderField(g, 2, "phantasmon.hub_manager.field.height", mx, my);
				renderField(g, 3, "phantasmon.hub_manager.field.length", mx, my);
				drawCentered(g, Component.translatable("phantasmon.hub_manager.create_hint").getString(), 800, fieldY(1) + FIELD_H + 22,
						1f, DIM, false, 0f);
			}
			case RENAME -> renderField(g, 0, "phantasmon.hub_manager.field.name", mx, my);
			case PLACE -> {
				renderField(g, 0, "phantasmon.hub_manager.field.anchor_name", mx, my);
				if (fields[0].isEmpty() && hub != null) {
					drawText(g, HubController.defaultAnchorName(hub.name(), session.username() == null ? "?" : session.username()),
							fieldX(0) + 9, fieldY(0) + 24, 2f, DIM, false, 0f);
				}
				drawCentered(g, fitText(Component.translatable("phantasmon.hub_manager.place_hint").getString(), MODAL_W - 40, 1f, false, 0f),
						800, fieldY(0) + FIELD_H + 16, 1f, DIM, false, 0f);
			}
			case DELETE_HUB -> drawCentered(g, fitText(Component.translatable("phantasmon.hub_manager.delete_text",
					hub == null ? 0 : hub.anchors()).getString(), MODAL_W - 40, 2f, false, 0f), 800, y + 76, 2f, TEXT2, false, 0f);
			case DELETE_ANCHOR -> drawCentered(g, fitText((admin ? Component.translatable("phantasmon.hub_manager.delete_anchor_text",
					anchorToDelete == null || anchorToDelete.ownerName() == null ? "?" : anchorToDelete.ownerName())
					: Component.translatable("phantasmon.hub_manager.delete_own_anchor_text")).getString(),
					MODAL_W - 40, 2f, false, 0f), 800, y + 76, 2f, TEXT2, false, 0f);
			default -> {
			}
		}
		if (modal == Modal.SCHEMATIC) {
			renderSchematicPicker(g, hub, mx, my);
			endModalLayer(g);
			return;
		}
		int buttonY = y + h - MODAL_BUTTON_H - 22;
		boolean destructive = modal == Modal.DELETE_HUB || modal == Modal.DELETE_ANCHOR;
		renderButton(g, 800 - MODAL_BUTTON_W - 10, buttonY, MODAL_BUTTON_W, MODAL_BUTTON_H, "phantasmon.hub_manager.cancel", false,
				inside(mx, my, 800 - MODAL_BUTTON_W - 10, buttonY, MODAL_BUTTON_W, MODAL_BUTTON_H), 2f);
		renderButton(g, 810, buttonY, MODAL_BUTTON_W, MODAL_BUTTON_H, destructive ? "phantasmon.hub_manager.delete"
				: modal == Modal.CREATE ? "phantasmon.hub_manager.create_confirm"
						: modal == Modal.PLACE ? "phantasmon.hub_manager.place_confirm" : "phantasmon.hub_manager.rename_confirm",
				destructive, inside(mx, my, 810, buttonY, MODAL_BUTTON_W, MODAL_BUTTON_H), 2f);
		endModalLayer(g);
	}

	private void renderSchematicPicker(GuiGraphics g, HubAdminDto hub, double mx, double my) {
		int y = modalY();
		String expected = hub == null || hub.size() == null ? "?"
				: Component.translatable("phantasmon.hub_manager.schematic_size", hub.size().x(), hub.size().y(), hub.size().z(),
						hub.schematic() == null ? Component.translatable("phantasmon.hub_manager.no_build").getString()
								: hub.schematic().name()).getString();
		drawCentered(g, fitText(expected, MODAL_W - 40, 1f, false, 0f), 800, y + 62, 1f, TEXT2, false, 0f);
		int listY = schemListY();
		int listH = SCHEM_ROWS * SCHEM_ROW_H;
		g.fill(MODAL_X + 30, listY, MODAL_X + MODAL_W - 30, listY + listH, INFO_BOX_BG);
		outline(g, MODAL_X + 30, listY, MODAL_W - 60, listH, LINE_20);
		if (schematicFiles.isEmpty()) {
			drawCentered(g, fitText(Component.translatable("phantasmon.hub_manager.schematic_empty").getString(), MODAL_W - 80, 1f, false, 0f),
					800, listY + listH / 2f - 4, 1f, DIM, false, 0f);
		}
		for (int row = 0; row < SCHEM_ROWS && schematicScroll + row < schematicFiles.size(); row++) {
			java.nio.file.Path file = schematicFiles.get(schematicScroll + row);
			int rowY = listY + row * SCHEM_ROW_H;
			boolean picked = schematicScroll + row == schematicPick;
			if (picked) {
				g.fill(MODAL_X + 31, rowY, MODAL_X + MODAL_W - 31, rowY + SCHEM_ROW_H, SLOT_SELECTED_BG);
				g.fill(MODAL_X + 31, rowY, MODAL_X + 35, rowY + SCHEM_ROW_H, CYAN);
			} else if (inside(mx, my, MODAL_X + 30, rowY, MODAL_W - 60, SCHEM_ROW_H)) {
				g.fill(MODAL_X + 31, rowY, MODAL_X + MODAL_W - 31, rowY + SCHEM_ROW_H, SLOT_HOVER_BG);
			}
			String relative = SCHEMATICS_DIR.relativize(file).toString().replace('\\', '/');
			String kib = Math.max(1, file.toFile().length() / 1024) + " Kio";
			float kibWidth = textWidth(kib, 1f, false, 0f);
			drawText(g, fitText(relative, MODAL_W - 120 - kibWidth, 2f, false, 0f), MODAL_X + 44, rowY + 13, 2f,
					picked ? WHITE : TEXT2, false, 0f);
			drawText(g, kib, MODAL_X + MODAL_W - 44 - kibWidth, rowY + 17, 1f, MUTED, false, 0f);
		}
		renderScrollbar(g, MODAL_X + MODAL_W - 38, listY + 4, listH - 8, schematicFiles.size(), SCHEM_ROWS, schematicScroll);
		int folderY = schemFolderY();
		drawText(g, fitText(SCHEMATICS_DIR.toAbsolutePath().toString(), MODAL_W - 300, 1f, false, 0f), MODAL_X + 30, folderY + 4, 1f,
				DIM, false, 0f);
		int openX = MODAL_X + MODAL_W - 30 - 240;
		renderLabelButton(g, openX, folderY - 6, 240, 28, Component.translatable("phantasmon.hub_manager.schematic_open_folder").getString(),
				false, inside(mx, my, openX, folderY - 6, 240, 28));

		int buttonY = y + modalH() - MODAL_BUTTON_H - 22;
		renderButton(g, MODAL_X + 30, buttonY, 180, MODAL_BUTTON_H, "phantasmon.hub_manager.cancel", false,
				inside(mx, my, MODAL_X + 30, buttonY, 180, MODAL_BUTTON_H), 2f);
		if (hub != null && hub.schematic() != null) {
			renderButton(g, 800 - 120, buttonY, 240, MODAL_BUTTON_H, "phantasmon.hub_manager.schematic_remove", true,
					inside(mx, my, 800 - 120, buttonY, 240, MODAL_BUTTON_H), 2f);
		}
		int sendX = MODAL_X + MODAL_W - 30 - 180;
		boolean canSend = schematicPick >= 0 && !uploading;
		renderButton(g, sendX, buttonY, 180, MODAL_BUTTON_H, uploading ? "phantasmon.hub_manager.schematic_sending_short"
				: "phantasmon.hub_manager.schematic_send", false, canSend && inside(mx, my, sendX, buttonY, 180, MODAL_BUTTON_H), 2f);
	}

	private void renderField(GuiGraphics g, int field, String labelKey, double mx, double my) {
		int x = fieldX(field);
		int y = fieldY(field);
		int w = fieldW(field);
		boolean focused = focusedField == field;
		g.fill(x, y, x + w, y + FIELD_H, INFO_BOX_BG);
		if (focused) {
			glowInside(g, x, y, w, FIELD_H, 0x50E6FF, 0.25f, 5);
		}
		outline(g, x, y, w, FIELD_H, focused ? CYAN : inside(mx, my, x, y, w, FIELD_H) ? CYAN2 : LINE_20);
		drawText(g, upper(Component.translatable(labelKey).getString()), x + 9, y + 7, 1f, TITLE, true, 1.2f);
		String shown = fitText(fields[field], w - 30, 2f, false, 0f);
		drawText(g, shown, x + 9, y + 24, 2f, WHITE, false, 0f);
		if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
			drawText(g, "_", x + 9 + textWidth(shown, 2f, false, 0f) + 2, y + 24, 2f, CYAN, false, 0f);
		}
	}

	// ---- Text ----

	private static String size(HubAdminDto hub) {
		return hub.size() == null ? "?" : hub.size().x() + " × " + hub.size().y() + " × " + hub.size().z();
	}

	private static String build(HubAdminDto hub) {
		return hub.schematic() == null ? Component.translatable("phantasmon.hub_manager.no_build").getString() : hub.schematic().name();
	}

	private String created(HubAdminDto hub) {
		if (!admin) {
			return Component.translatable("phantasmon.hub_manager.created_on", hub.createdAt() == null ? "?" : date(hub.createdAt()))
					.getString();
		}
		String by = hub.createdByName() != null ? hub.createdByName()
				: Component.translatable("phantasmon.hub_manager.by_system").getString();
		return Component.translatable("phantasmon.hub_manager.created_at", hub.createdAt() == null ? "?" : date(hub.createdAt()), by)
				.getString();
	}

	private static String kindLabel(HubAdminDto hub) {
		return hub.normal() ? Component.translatable("phantasmon.hub_manager.kind.normal").getString()
				: hub.kind().toLowerCase(Locale.ROOT);
	}

	private static String date(String iso) {
		try {
			return DATE.format(Instant.parse(iso));
		} catch (RuntimeException ex) {
			return iso;
		}
	}

	private static String shortDimension(String dimension) {
		return dimension != null && dimension.startsWith("minecraft:") ? dimension.substring("minecraft:".length()) : dimension;
	}
}
