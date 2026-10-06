package com.mystaria.phantasmon.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.network.BackendApiException;
import com.mystaria.phantasmon.client.network.BackendErrorMessages;
import com.mystaria.phantasmon.client.pokemon.PokemonClient;
import com.mystaria.phantasmon.client.pokemon.PokemonCommandHandler;
import com.mystaria.phantasmon.client.pokemon.PokemonCreateRequestDto;
import com.mystaria.phantasmon.client.pokemon.PokemonDto;
import com.mystaria.phantasmon.client.pokemon.PokemonUpdateRequestDto;
import com.mystaria.phantasmon.client.pokemon.showdown.CobblemonShowdownNames;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownExporter;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownImportMapper;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownParseException;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownParser;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownPokemon;

/**
 * Graphical PC ({@code /phantasmon pc}, keybind P), rebuilt on 2026-10-02 on
 * the trade screen's design system ({@link PhantasmonCanvasScreen}) so both
 * menus share one art direction (Adrien: the previous version was well
 * organised but looked "cheap" next to the trade screen).
 *
 * <p>Layout, in the shared 1600×900 px canvas: team rail on the left (same
 * rail as the trade screen), the full Pokémon card in the middle (same card
 * as the trade screen, plus Hidden Power), the 6×5 box grid on the right;
 * header with IMPORTER as the primary action, footer with the status line and
 * ÉDITER / SUPPRIMER for the selection.
 *
 * <p>Behavior unchanged from the previous PC, server-side semantics included:
 * double click moves a Pokémon between team and PC (first free slot, TODO-19);
 * drag &amp; drop between any two slots (team or PC) is a move to an empty
 * slot or a swap with the occupant, resolved by the backend from the
 * destination only ({@link PokemonUpdateRequestDto}); the whole Pokémon list
 * is re-fetched after every mutation. Additions: the selection follows the
 * Pokémon (not the slot) across box changes, the mouse wheel / arrow keys /
 * ◀ ▶ change box (also while dragging, to move a Pokémon to another box),
 * and deleting now asks for confirmation.
 */
public final class PhantasmonPcScreen extends PhantasmonCanvasScreen {

	private static final int BOX_COLUMNS = 6;
	private static final int BOX_ROWS = 5;
	private static final int SLOTS_PER_BOX = BOX_COLUMNS * BOX_ROWS;
	private static final int TEAM_SIZE = 6;
	private static final int BOX_COUNT = 16;
	private static final int PC_CAPACITY = BOX_COUNT * SLOTS_PER_BOX;

	// ---- Box grid panel (right of the card) ----
	private static final int GRID_PANEL_X = 805;
	private static final int GRID_PANEL_W = 780;
	private static final int NAV_W = 44;
	private static final int NAV_H = 30;
	private static final int PREV_X = 814;
	private static final int NEXT_X = GRID_PANEL_X + GRID_PANEL_W - 9 - NAV_W;
	private static final int NAV_Y = 81;
	private static final int GRID_X = 818;
	private static final int GRID_Y = 127;
	private static final int GRID_SLOT_W = 119;
	private static final int GRID_SLOT_H = 132;
	private static final int GRID_GAP = 8;
	/** Grid slots are shorter than the rail's: same layout logic; model ×2 after Adrien's capture (2026-10-02, too small). */
	private static final SlotLayout GRID_SLOT = new SlotLayout(16, 92, SLOT_MODEL_SCALE * 88 / 142f * 2f, 112, GRID_SLOT_W - 24, 84, 18);

	// ---- Header / footer buttons ----
	/** Red ✕ closing the PC, at the right end of the right header plate (Adrien 2026-10-03). */
	private static final int CLOSE_SIZE = 36;
	private static final int CLOSE_X = 1585 - 6 - CLOSE_SIZE;
	private static final int CLOSE_Y = 15 + (48 - CLOSE_SIZE) / 2;
	private static final int IMPORT_X = HEADER_IMPORT_X;
	private static final int IMPORT_Y = 15;
	private static final int IMPORT_W = HEADER_PAIR_W;
	private static final int IMPORT_H = 48;
	private static final int EXPORT_X = HEADER_EXPORT_X;
	/** Footer buttons: scale-2 labels, so they're as tall as the footer allows (Adrien 2026-10-02: too small, pixelated). */
	private static final int DELETE_W = 170;
	private static final int DELETE_X = 1576 - DELETE_W;
	private static final int EDIT_W = 136;
	private static final int EDIT_X = DELETE_X - 10 - EDIT_W;
	private static final int FOOTER_BUTTON_Y = 844;
	private static final int FOOTER_BUTTON_H = 37;
	/** Delete confirmation buttons, centered under the modal text. */
	private static final int MODAL_CANCEL_W = 150;
	private static final int MODAL_DELETE_W = 170;
	private static final int MODAL_CANCEL_X = 800 - (MODAL_CANCEL_W + 10 + MODAL_DELETE_W) / 2;
	private static final int MODAL_DELETE_X = MODAL_CANCEL_X + MODAL_CANCEL_W + 10;
	private static final int MODAL_BUTTON_Y = 474;
	private static final int MODAL_BUTTON_H = 37;

	private final PokemonClient pokemonClient;
	private final AuthSession session;

	private List<PokemonDto> allPokemon = List.of();
	private final PokemonDto[] boxSlots = new PokemonDto[SLOTS_PER_BOX];
	private final PokemonDto[] teamSlots = new PokemonDto[TEAM_SIZE];

	private int currentBox = 1;
	/** Selection follows the Pokémon itself, so it survives box changes and moves. */
	private UUID selectedUuid;

	private PokemonDto dragged;
	private SlotRef dragSource;
	private int dragSourceBox;
	private boolean dragging;
	private double pressX;
	private double pressY;
	private double cursorX = -1;
	private double cursorY = -1;

	/** Double click (TODO-19): the last click's Pokémon and time. */
	private UUID lastClickUuid;
	private long lastClickTime;
	private static final long DOUBLE_CLICK_MS = 350;

	private boolean loading;
	private String statusMessage;
	private boolean statusIsError;
	private boolean deleteConfirmOpen;

	/** Whose PC this is: the player's own, or — admin (TODO-25) — another player's, handled as if it were theirs. */
	private final java.util.UUID ownerUuid;
	private final String ownerName;

	public PhantasmonPcScreen(PokemonClient pokemonClient, AuthSession session) {
		this(pokemonClient, session, null, null);
	}

	/** {@code ownerUuid} null = the player's own PC. */
	public PhantasmonPcScreen(PokemonClient pokemonClient, AuthSession session, java.util.UUID ownerUuid, String ownerName) {
		super(Component.translatable("phantasmon.pc.title"));
		this.pokemonClient = pokemonClient;
		this.session = session;
		this.ownerUuid = ownerUuid;
		this.ownerName = ownerName;
	}

	private boolean someoneElses() {
		return ownerUuid != null && !ownerUuid.equals(session.playerUuid());
	}

	@Override
	protected void init() {
		super.init();
		refresh();
	}

	@Override
	public void removed() {
		super.removed();
		// The team may have changed here: the Ghost team overlay re-reads it right away.
		com.mystaria.phantasmon.client.ghost.GhostPartyHud.teamChanged();
	}

	// =====================================================================
	// Data (unchanged behavior)
	// =====================================================================

	/** Public: also called by {@link PhantasmonPcEditScreen} after a save, to reload this screen's data before returning to it. */
	public void refresh() {
		if (!session.isAuthenticated()) {
			setStatus(Component.translatable("phantasmon.error.not_authenticated").getString(), true);
			return;
		}
		loading = true;
		pokemonClient.listForOwner(session.accessToken(), someoneElses() ? ownerUuid : session.playerUuid())
				.thenAccept(pokemons -> Minecraft.getInstance().execute(() -> {
					this.allPokemon = List.of(pokemons);
					this.loading = false;
					recomputeSlots();
				}))
				.exceptionally(ex -> {
					Minecraft.getInstance().execute(() -> {
						this.loading = false;
						setStatus(errorMessage(ex), true);
					});
					return null;
				});
	}

	private void recomputeSlots() {
		Arrays.fill(boxSlots, null);
		Arrays.fill(teamSlots, null);
		for (PokemonDto pokemon : allPokemon) {
			if (pokemon.teamSlot() != null) {
				int index = pokemon.teamSlot() - 1;
				if (index >= 0 && index < TEAM_SIZE) {
					teamSlots[index] = pokemon;
				}
			} else if (pokemon.boxId() != null && pokemon.boxId() == currentBox && pokemon.boxSlot() != null) {
				int index = pokemon.boxSlot() - 1;
				if (index >= 0 && index < SLOTS_PER_BOX) {
					boxSlots[index] = pokemon;
				}
			}
		}
		if (selectedUuid != null && selectedPokemon() == null) {
			selectedUuid = null;
		}
	}

	private PokemonDto selectedPokemon() {
		if (selectedUuid == null) {
			return null;
		}
		for (PokemonDto pokemon : allPokemon) {
			if (selectedUuid.equals(pokemon.uuid())) {
				return pokemon;
			}
		}
		return null;
	}

	private void changeBox(int delta) {
		currentBox = Math.floorMod(currentBox - 1 + delta, BOX_COUNT) + 1;
		recomputeSlots();
	}

	private PokemonDto pokemonAt(SlotRef ref) {
		if (ref == null) {
			return null;
		}
		return ref.kind() == SlotRef.Kind.PC ? boxSlots[ref.index()] : teamSlots[ref.index()];
	}

	/** Same backend move/swap as before: only the destination is sent, the backend resolves any occupant. */
	private void handleDrop(PokemonDto moving, SlotRef to) {
		move(moving, to.kind() == SlotRef.Kind.TEAM
				? PokemonUpdateRequestDto.movingToTeamSlot(to.index() + 1)
				: PokemonUpdateRequestDto.movingToPcSlot(currentBox, to.index() + 1));
	}

	/**
	 * Double click (TODO-19): a boxed Pokémon joins the team in its first free slot; a team member goes to the
	 * first free PC slot (box 1 slot 1 onward). Full team / full PC: said in the footer, nothing moves.
	 */
	private void quickMove(PokemonDto moving, SlotRef from) {
		if (from.kind() == SlotRef.Kind.PC) {
			for (int i = 0; i < TEAM_SIZE; i++) {
				if (teamSlots[i] == null) {
					move(moving, PokemonUpdateRequestDto.movingToTeamSlot(i + 1));
					return;
				}
			}
			setStatus(Component.translatable("phantasmon.pc.screen.team_full").getString(), true);
			return;
		}
		boolean[][] taken = new boolean[BOX_COUNT + 1][SLOTS_PER_BOX + 1];
		for (PokemonDto pokemon : allPokemon) {
			if (pokemon.teamSlot() == null && pokemon.boxId() != null && pokemon.boxSlot() != null
					&& pokemon.boxId() >= 1 && pokemon.boxId() <= BOX_COUNT && pokemon.boxSlot() >= 1 && pokemon.boxSlot() <= SLOTS_PER_BOX) {
				taken[pokemon.boxId()][pokemon.boxSlot()] = true;
			}
		}
		for (int box = 1; box <= BOX_COUNT; box++) {
			for (int slot = 1; slot <= SLOTS_PER_BOX; slot++) {
				if (!taken[box][slot]) {
					move(moving, PokemonUpdateRequestDto.movingToPcSlot(box, slot));
					return;
				}
			}
		}
		setStatus(Component.translatable("phantasmon.pc.screen.pc_full").getString(), true);
	}

	private void move(PokemonDto moving, PokemonUpdateRequestDto request) {
		loading = true;
		pokemonClient.update(session.accessToken(), moving.uuid(), request)
				.thenAccept(updated -> Minecraft.getInstance().execute(() -> {
					selectedUuid = moving.uuid();
					clearStatus();
					refresh();
				}))
				.exceptionally(ex -> {
					Minecraft.getInstance().execute(() -> {
						loading = false;
						setStatus(errorMessage(ex), true);
					});
					return null;
				});
	}

	/**
	 * Reads a Showdown export (one or more Pokémon, blank-line separated) from
	 * the clipboard and creates each via the backend, exactly mirroring
	 * {@code /phantasmon pokemon import}'s logic — just triggered from the HUD.
	 */
	/** Copies the active team (slot order) as Showdown text (CAD Partie 1 §11, TODO-11). */
	private void exportTeamToClipboard() {
		List<PokemonDto> team = Arrays.stream(teamSlots).filter(p -> p != null).toList();
		if (team.isEmpty()) {
			setStatus(Component.translatable("phantasmon.pc.screen.export_empty").getString(), true);
			return;
		}
		Minecraft.getInstance().keyboardHandler.setClipboard(ShowdownExporter.exportTeam(team, CobblemonShowdownNames.INSTANCE));
		setStatus(Component.translatable("phantasmon.pc.screen.exported", team.size()).getString(), false);
	}

	private void importFromClipboard() {
		String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
		if (clipboard == null || clipboard.isBlank()) {
			setStatus(Component.translatable("phantasmon.pokemon.import.clipboard_empty").getString(), true);
			return;
		}
		List<ShowdownPokemon> parsed;
		try {
			parsed = ShowdownParser.parseTeam(clipboard);
		} catch (ShowdownParseException ex) {
			setStatus(Component.translatable("phantasmon.pokemon.import.parse_error", ex.getMessage()).getString(), true);
			return;
		}

		List<CompletableFuture<PokemonDto>> creations = new ArrayList<>();
		String bearerToken = session.accessToken();
		for (ShowdownPokemon set : parsed) {
			PokemonCreateRequestDto request;
			try {
				request = ShowdownImportMapper.toCreateRequest(set, com.mystaria.phantasmon.client.pokemon.CobblemonDataVersion.local());
			} catch (ShowdownParseException ex) {
				setStatus(Component.translatable("phantasmon.pokemon.import.parse_error_for", set.speciesToken(), ex.getMessage()).getString(), true);
				continue;
			}
			creations.add(someoneElses() ? pokemonClient.createFor(bearerToken, request, ownerUuid)
					: pokemonClient.create(bearerToken, request));
		}
		if (creations.isEmpty()) {
			return;
		}

		loading = true;
		int count = creations.size();
		CompletableFuture.allOf(creations.toArray(new CompletableFuture[0]))
				.thenRun(() -> Minecraft.getInstance().execute(() -> {
					setStatus(Component.translatable("phantasmon.pc.screen.imported", count).getString(), false);
					refresh();
				}))
				.exceptionally(ex -> {
					Minecraft.getInstance().execute(() -> {
						loading = false;
						setStatus(errorMessage(ex), true);
					});
					return null;
				});
	}

	private void handleDelete() {
		PokemonDto target = selectedPokemon();
		deleteConfirmOpen = false;
		if (target == null) {
			return;
		}
		loading = true;
		pokemonClient.delete(session.accessToken(), target.uuid())
				.thenAccept(ignored -> Minecraft.getInstance().execute(() -> {
					selectedUuid = null;
					clearStatus();
					refresh();
				}))
				.exceptionally(ex -> {
					Minecraft.getInstance().execute(() -> {
						loading = false;
						setStatus(errorMessage(ex), true);
					});
					return null;
				});
	}

	private void openEditor() {
		PokemonDto pokemon = selectedPokemon();
		if (pokemon != null) {
			Minecraft.getInstance().setScreen(new PhantasmonPcEditScreen(pokemonClient, session, pokemon, this));
		}
	}

	/** Called by {@link PhantasmonPcEditScreen} after a successful save, so the footer confirms it once back here. */
	public void notifySaved() {
		setStatus(Component.translatable("phantasmon.pc.editor.saved").getString(), false);
	}

		private void setStatus(String message, boolean error) {
		statusMessage = stripPrefix(message);
		statusIsError = error;
	}

	private void clearStatus() {
		statusMessage = null;
		statusIsError = false;
	}

	private static String errorMessage(Throwable throwable) {
		Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
		String key = cause instanceof BackendApiException apiException
				? BackendErrorMessages.translationKey(apiException.errorCode())
				: "phantasmon.error.network";
		return Component.translatable(key).getString();
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

		if (deleteConfirmOpen) {
			if (inside(x, y, MODAL_CANCEL_X, MODAL_BUTTON_Y, MODAL_CANCEL_W, MODAL_BUTTON_H)) {
				deleteConfirmOpen = false;
			} else if (inside(x, y, MODAL_DELETE_X, MODAL_BUTTON_Y, MODAL_DELETE_W, MODAL_BUTTON_H)) {
				handleDelete();
			}
			return true;
		}
		if (inside(x, y, CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE)) {
			onClose();
			return true;
		}
		if (inside(x, y, IMPORT_X, IMPORT_Y, IMPORT_W, IMPORT_H)) {
			importFromClipboard();
			return true;
		}
		if (inside(x, y, EXPORT_X, IMPORT_Y, IMPORT_W, IMPORT_H)) {
			exportTeamToClipboard();
			return true;
		}
		if (inside(x, y, PREV_X, NAV_Y, NAV_W, NAV_H)) {
			changeBox(-1);
			return true;
		}
		if (inside(x, y, NEXT_X, NAV_Y, NAV_W, NAV_H)) {
			changeBox(1);
			return true;
		}
		if (selectedPokemon() != null) {
			if (inside(x, y, EDIT_X, FOOTER_BUTTON_Y, EDIT_W, FOOTER_BUTTON_H)) {
				openEditor();
				return true;
			}
			if (inside(x, y, DELETE_X, FOOTER_BUTTON_Y, DELETE_W, FOOTER_BUTTON_H)) {
				deleteConfirmOpen = true;
				return true;
			}
		}
		SlotRef ref = slotAt(x, y);
		if (ref != null) {
			PokemonDto pokemon = pokemonAt(ref);
			long now = System.currentTimeMillis();
			if (pokemon != null && pokemon.uuid().equals(lastClickUuid) && now - lastClickTime <= DOUBLE_CLICK_MS) {
				lastClickUuid = null;
				dragged = null;
				dragSource = null;
				dragging = false;
				if (!loading) {
					quickMove(pokemon, ref);
				}
				return true;
			}
			lastClickUuid = pokemon == null ? null : pokemon.uuid();
			lastClickTime = now;
			selectedUuid = pokemon == null ? null : pokemon.uuid();
			Component unrecognized = pokemon == null ? null : com.mystaria.phantasmon.client.pokemon.PokemonRecognition.problem(pokemon);
			if (unrecognized != null) {
				setStatus(unrecognized.getString(), true);
			}
			if (pokemon != null && !loading) {
				dragged = pokemon;
				dragSource = ref;
				dragSourceBox = currentBox;
				dragging = false;
				pressX = x;
				pressY = y;
			}
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (button == 0 && dragged != null) {
			double x = toCanvasX(mouseX);
			double y = toCanvasY(mouseY);
			if (!dragging && Math.hypot(x - pressX, y - pressY) > 6) {
				dragging = true;
			}
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && dragged != null) {
			PokemonDto moving = dragged;
			boolean wasDragging = dragging;
			SlotRef source = dragSource;
			int sourceBox = dragSourceBox;
			dragged = null;
			dragSource = null;
			dragging = false;
			if (wasDragging) {
				SlotRef target = slotAt(toCanvasX(mouseX), toCanvasY(mouseY));
				boolean sameSlot = target != null && target.equals(source)
						&& (target.kind() == SlotRef.Kind.TEAM || sourceBox == currentBox);
				if (target != null && !sameSlot) {
					handleDrop(moving, target);
				}
			}
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	/** Mouse wheel anywhere changes box — also works mid-drag, to drop a Pokémon into another box. */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!deleteConfirmOpen && scrollY != 0) {
			changeBox(scrollY > 0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (deleteConfirmOpen) {
			if (keyCode == 256) {
				deleteConfirmOpen = false;
				return true;
			}
			if (keyCode == 257 || keyCode == 335) {
				handleDelete();
				return true;
			}
			return true;
		}
		switch (keyCode) {
			case 263 -> { // left
				changeBox(-1);
				return true;
			}
			case 262 -> { // right
				changeBox(1);
				return true;
			}
			case 261 -> { // delete
				if (selectedPokemon() != null) {
					deleteConfirmOpen = true;
				}
				return true;
			}
			default -> {
				return super.keyPressed(keyCode, scanCode, modifiers);
			}
		}
	}

	private SlotRef slotAt(double x, double y) {
		for (int i = 0; i < TEAM_SIZE; i++) {
			if (inside(x, y, teamSlotX(i), teamSlotY(i), 85, 214)) {
				return new SlotRef(SlotRef.Kind.TEAM, i);
			}
		}
		for (int i = 0; i < SLOTS_PER_BOX; i++) {
			if (inside(x, y, gridSlotX(i), gridSlotY(i), GRID_SLOT_W, GRID_SLOT_H)) {
				return new SlotRef(SlotRef.Kind.PC, i);
			}
		}
		return null;
	}

	/** Same 2×3 rail geometry as the trade screen (85×214 slots, 92/221 px steps). */
	private static int teamSlotX(int index) {
		return 24 + (index % 2) * 92;
	}

	private static int teamSlotY(int index) {
		return 117 + (index / 2) * 221;
	}

	private static int gridSlotX(int index) {
		return GRID_X + (index % BOX_COLUMNS) * (GRID_SLOT_W + GRID_GAP);
	}

	private static int gridSlotY(int index) {
		return GRID_Y + (index / BOX_COLUMNS) * (GRID_SLOT_H + GRID_GAP);
	}

	// =====================================================================
	// Rendering
	// =====================================================================

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderWorldBackdrop(graphics, mouseX, mouseY, partialTick);
		cursorX = toCanvasX(mouseX);
		cursorY = toCanvasY(mouseY);
		double mx = deleteConfirmOpen ? -1 : cursorX;
		double my = deleteConfirmOpen ? -1 : cursorY;

		beginCanvas(graphics);
		renderRootPanel(graphics);
		renderHeader(graphics, mx, my);
		renderTeamRail(graphics, mx, my);
		renderPokemonCard(graphics, selectedPokemon(), playerName(), 0, ACCENT_LEFT,
				Component.translatable("phantasmon.pc.no_selection").getString(), true);
		renderGridPanel(graphics, mx, my);
		renderFooter(graphics, mx, my);
		renderDraggedModel(graphics);
		endCanvas(graphics);

		if (deleteConfirmOpen) {
			renderDeleteModal(graphics, mouseX, mouseY);
		}
	}

	private String playerName() {
		if (someoneElses()) {
			return ownerName;
		}
		String name = session.username();
		if (name == null && Minecraft.getInstance().player != null) {
			name = Minecraft.getInstance().player.getGameProfile().getName();
		}
		return name == null ? "?" : name;
	}

	/** Header plates: who's PC this is (left), IMPORTER as the primary action (center, ÉCHANGER's spot), totals (right). */
	private void renderHeader(GuiGraphics g, double mx, double my) {
		renderHeaderPlates(g, HEADER_PAIR_PLATE_W);
		String title = upper(Component.translatable(someoneElses() ? "phantasmon.pc.screen.header_admin" : "phantasmon.pc.screen.header",
				playerName()).getString());
		drawText(g, fitText(title, HEADER_PAIR_TITLE_W, 2f, true, 1.5f), 31, 32, 2f, WHITE, true, 1.5f);

		long teamCount = Arrays.stream(teamSlots).filter(p -> p != null).count();
		long pcCount = allPokemon.size() - teamCount;
		String totals = upper(Component.translatable("phantasmon.pc.screen.count", pcCount, PC_CAPACITY, teamCount, TEAM_SIZE).getString());
		drawText(g, totals, CLOSE_X - 14 - textWidth(totals, 2f, false, 1f), 32, 2f, MUTED, false, 1f);
		renderCloseButton(g, CLOSE_X, CLOSE_Y, CLOSE_SIZE, inside(mx, my, CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE));

		boolean hovered = inside(mx, my, IMPORT_X, IMPORT_Y, IMPORT_W, IMPORT_H);
		renderPrimaryButtonFrame(g, IMPORT_X, IMPORT_Y, IMPORT_W, IMPORT_H, hovered, false);
		String plus = "+";
		String label = upper(Component.translatable("phantasmon.pc.screen.import").getString());
		float plusWidth = textWidth(plus, 2f, true, 0f);
		float labelScale = textWidth(label, 2f, true, 1f) + plusWidth + 8 <= IMPORT_W - 16 ? 2f : 1f;
		float labelWidth = textWidth(label, labelScale, true, 1f);
		float startX = IMPORT_X + (IMPORT_W - plusWidth - 8 - labelWidth) / 2f;
		drawText(g, plus, startX, IMPORT_Y + 17, 2f, CYAN, true, 0f);
		drawText(g, label, startX + plusWidth + 8, IMPORT_Y + (IMPORT_H - 7 * labelScale) / 2f, labelScale, CYAN, true, 1f);
		renderExportButton(g, EXPORT_X, IMPORT_Y, IMPORT_W, IMPORT_H, inside(mx, my, EXPORT_X, IMPORT_Y, IMPORT_W, IMPORT_H));
	}

	private void renderTeamRail(GuiGraphics g, double mx, double my) {
		renderRailFrame(g, 15, 72, 194, 758, Component.translatable("phantasmon.trade.screen.team", playerName()).getString(),
				Component.translatable("phantasmon.pc.screen.team_hint"));
		for (int i = 0; i < TEAM_SIZE; i++) {
			SlotRef ref = new SlotRef(SlotRef.Kind.TEAM, i);
			renderSlot(g, teamSlotX(i), teamSlotY(i), 85, 214, teamSlots[i], lookOf(ref, teamSlots[i], mx, my, teamSlotX(i), teamSlotY(i), 85, 214), RAIL_SLOT);
		}
	}

	/** Right panel: ◀ BOÎTE n / 16 ▶, then the 6×5 grid. */
	private void renderGridPanel(GuiGraphics g, double mx, double my) {
		blitTexture(g, TEX_RAIL, GRID_PANEL_X, 72, GRID_PANEL_W, 758);
		outline(g, GRID_PANEL_X, 72, GRID_PANEL_W, 758, RAIL_BORDER);

		renderArrowButton(g, PREV_X, NAV_Y, "◀", inside(mx, my, PREV_X, NAV_Y, NAV_W, NAV_H));
		renderArrowButton(g, NEXT_X, NAV_Y, "▶", inside(mx, my, NEXT_X, NAV_Y, NAV_W, NAV_H));
		String boxLabel = upper(Component.translatable("phantasmon.pc.screen.box", currentBox, BOX_COUNT).getString());
		drawCentered(g, boxLabel, GRID_PANEL_X + GRID_PANEL_W / 2f, NAV_Y + 8, 2f, MUTED, true, 2f);
		g.fill(GRID_PANEL_X + 9, 117, GRID_PANEL_X + GRID_PANEL_W - 9, 117 + hairline(), LINE_25);

		for (int i = 0; i < SLOTS_PER_BOX; i++) {
			SlotRef ref = new SlotRef(SlotRef.Kind.PC, i);
			int x = gridSlotX(i);
			int y = gridSlotY(i);
			renderSlot(g, x, y, GRID_SLOT_W, GRID_SLOT_H, boxSlots[i], lookOf(ref, boxSlots[i], mx, my, x, y, GRID_SLOT_W, GRID_SLOT_H), GRID_SLOT);
		}
	}

	/** Selected = the selected Pokémon's slot; while dragging, the hovered slot is the drop target (empty or not). */
	private SlotLook lookOf(SlotRef ref, PokemonDto pokemon, double mx, double my, int x, int y, int w, int h) {
		boolean isDragSource = dragging && ref.equals(dragSource)
				&& (ref.kind() == SlotRef.Kind.TEAM || dragSourceBox == currentBox);
		if (isDragSource) {
			return SlotLook.DRAG_SOURCE;
		}
		boolean hovered = inside(mx, my, x, y, w, h);
		if (dragging && hovered) {
			return SlotLook.HOVER;
		}
		if (pokemon != null && pokemon.uuid().equals(selectedUuid)) {
			return SlotLook.SELECTED;
		}
		return hovered && pokemon != null ? SlotLook.HOVER : SlotLook.NORMAL;
	}

	private void renderArrowButton(GuiGraphics g, int x, int y, String glyph, boolean hovered) {
		int[] gradient = hovered ? STD_BUTTON_HOVER : STD_BUTTON;
		g.fillGradient(x, y, x + NAV_W, y + NAV_H, gradient[0], gradient[1]);
		outline(g, x, y, NAV_W, NAV_H, CYAN);
		drawCentered(g, glyph, x + NAV_W / 2f, y + (NAV_H - 14) / 2f, 2f, CYAN, false, 0f);
	}

	private void renderFooter(GuiGraphics g, double mx, double my) {
		String status;
		int color;
		if (loading) {
			status = Component.translatable("phantasmon.pc.loading").getString();
			color = MUTED;
		} else if (statusMessage != null) {
			status = statusMessage;
			color = statusIsError ? ERROR_TEXT : READY_TEXT;
		} else {
			status = Component.translatable("phantasmon.pc.screen.idle_hint").getString();
			color = DIM;
		}
		renderFooterBar(g, status, color, EDIT_X - 27 - 16);
		if (selectedPokemon() != null) {
			renderButton(g, EDIT_X, FOOTER_BUTTON_Y, EDIT_W, FOOTER_BUTTON_H, "phantasmon.pc.edit", false,
					inside(mx, my, EDIT_X, FOOTER_BUTTON_Y, EDIT_W, FOOTER_BUTTON_H), 2f);
			renderButton(g, DELETE_X, FOOTER_BUTTON_Y, DELETE_W, FOOTER_BUTTON_H, "phantasmon.pc.delete", true,
					inside(mx, my, DELETE_X, FOOTER_BUTTON_Y, DELETE_W, FOOTER_BUTTON_H), 2f);
		}
	}

	/** The dragged Pokémon's model follows the cursor, above every slot. */
	private void renderDraggedModel(GuiGraphics g) {
		if (!dragging || dragged == null) {
			return;
		}
		g.flush();
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(0, 0, 400);
		float modelScale = GRID_SLOT.modelScale() * 1.15f;
		PokemonGuiRendering.renderModel(g, dragged.species(), dragged.form(), dragged.isShiny(), PokemonGuiRendering.storedGender(dragged),
				(float) cursorX, slotModelAnchorY((float) cursorY, modelScale), modelScale);
		pose.popPose();
	}

	/** « SUPPRIMER X ? » — same modal as the trade screen's quit confirmation. */
	private void renderDeleteModal(GuiGraphics g, int mouseX, int mouseY) {
		PokemonDto pokemon = selectedPokemon();
		beginModalLayer(g, 1f);
		double mx = toCanvasX(mouseX);
		double my = toCanvasY(mouseY);
		renderModalCard(g, 540, 370, 520, 160, 1f);
		String title = upper(Component.translatable("phantasmon.pc.screen.delete_title",
				pokemon == null ? "?" : displayName(pokemon)).getString());
		drawCentered(g, fitText(title, 480, 2f, true, 2f), 800, 397, 2f, TITLE, true, 2f);
		drawCentered(g, Component.translatable("phantasmon.pc.screen.delete_text").getString(), 800, 437, 2f, TEXT2, false, 0f);
		renderButton(g, MODAL_CANCEL_X, MODAL_BUTTON_Y, MODAL_CANCEL_W, MODAL_BUTTON_H, "phantasmon.trade.screen.cancel", false,
				inside(mx, my, MODAL_CANCEL_X, MODAL_BUTTON_Y, MODAL_CANCEL_W, MODAL_BUTTON_H), 2f);
		renderButton(g, MODAL_DELETE_X, MODAL_BUTTON_Y, MODAL_DELETE_W, MODAL_BUTTON_H, "phantasmon.pc.delete", true,
				inside(mx, my, MODAL_DELETE_X, MODAL_BUTTON_Y, MODAL_DELETE_W, MODAL_BUTTON_H), 2f);
		endModalLayer(g);
	}

	private record SlotRef(Kind kind, int index) {
		enum Kind { PC, TEAM }
	}
}
