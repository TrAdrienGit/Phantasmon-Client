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
	/** Team boxes (TODO-36): shorter slots, wider gaps between the rows, the same overall height. */
	private static final int TEAM_SLOT_H = 124;
	private static final int TEAM_ROW_GAP = 18;
	/** The box type switch, in the box header, left of ▶. */
	private static final int TYPE_W = 170;
	private static final int TYPE_X = NEXT_X - 10 - TYPE_W;
	/** The search field (TODO-38), in the right header plate, left of the totals. */
	private static final int SEARCH_X = 999;
	private static final int SEARCH_Y = 21;
	private static final int SEARCH_W = 232;
	private static final int SEARCH_H = 36;
	/** The box title, between ◀ and the type switch: a click names the box (TODO-38). */
	private static final int TITLE_X = PREV_X + NAV_W + 10;
	private static final int TITLE_W = TYPE_X - 10 - TITLE_X;

	/** One colour per team row of a team box: teal, violet, amber, rose, green. */
	private static final int[] TEAM_COLORS = { 0xFF38C4BC, 0xFF9B7BFF, 0xFFFFB347, 0xFFFF6B8B, 0xFF7BD86B };
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

	/** The search (TODO-38): its text, whether it has the keyboard, and each Pokémon's searchable words, cached. */
	private String search = "";
	private boolean searchFocused;
	private final java.util.Map<UUID, String> searchText = new java.util.HashMap<>();
	/** The box name being typed (click on the box title), or null. */
	private String renaming;

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

	/** The "PC on" sound plays once, not again when coming back from the editor (TODO-21). */
	private boolean loginSoundPlayed;

	@Override
	protected void init() {
		super.init();
		if (!loginSoundPlayed) {
			loginSoundPlayed = true;
			com.mystaria.phantasmon.client.audio.PhantasmonSounds.play(com.mystaria.phantasmon.client.audio.PhantasmonSounds.Sfx.PC_LOGIN);
		}
		refresh();
	}

	/** Closed by the player (Escape, the close button) — opening the editor doesn't come here. */
	@Override
	public void onClose() {
		com.mystaria.phantasmon.client.audio.PhantasmonSounds.play(com.mystaria.phantasmon.client.audio.PhantasmonSounds.Sfx.PC_LOGOUT);
		super.onClose();
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
		searchText.clear();
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
		renaming = null;
		currentBox = Math.floorMod(currentBox - 1 + delta, BOX_COUNT) + 1;
		recomputeSlots();
	}

	// ---- Search (TODO-38) ----

	/**
	 * Whether {@code pokemon} answers the search: every word of it must be found among its names (nickname, species
	 * in French and English), its types (and Tera type), "shiny" / "chromatique", its held item, ability, nature and
	 * moves — case and accents aside. Those that don't stay in place, greyed (Adrien 2026-11-11).
	 */
	private boolean matches(PokemonDto pokemon) {
		if (search.isBlank() || pokemon == null) {
			return true;
		}
		String haystack = searchText.computeIfAbsent(pokemon.uuid(), uuid -> searchableText(pokemon));
		for (String word : normalize(search).split("\\s+")) {
			if (!word.isEmpty() && !haystack.contains(word)) {
				return false;
			}
		}
		return true;
	}

	private boolean searching() {
		return !search.isBlank();
	}

	private static String searchableText(PokemonDto pokemon) {
		StringBuilder text = new StringBuilder();
		java.util.function.Consumer<Object> add = value -> {
			if (value != null) {
				text.append(' ').append(value);
			}
		};
		add.accept(displayName(pokemon));
		add.accept(pokemon.species());
		var species = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByName(pokemon.species());
		if (species != null) {
			add.accept(species.getTranslatedName().getString());
			add.accept(species.getName());
		}
		Look look = Look.of(pokemon);
		for (var type : look.types()) {
			add.accept(type.getDisplayName().getString());
			add.accept(type.getName());
		}
		if (look.teraType() != null) {
			add.accept("tera " + look.teraType().getDisplayName().getString() + " " + look.teraType().getName());
		}
		if (pokemon.isShiny()) {
			add.accept("shiny chromatique " + Component.translatable("phantasmon.pc.screen.shiny_word").getString());
		}
		add.accept(PokemonGuiRendering.heldItemName(heldItemId(pokemon)));
		add.accept(heldItemId(pokemon));
		add.accept(abilityLabel(pokemon.ability()));
		add.accept(pokemon.ability());
		add.accept(com.mystaria.phantasmon.client.pokemon.NatureModifiers.displayName(pokemon.nature()));
		add.accept(pokemon.nature());
		for (Object move : asList(pokemon.data() == null ? null : pokemon.data().get("moves"))) {
			add.accept(move);
			var template = com.cobblemon.mod.common.api.moves.Moves.INSTANCE.getByName(String.valueOf(move));
			if (template != null) {
				add.accept(template.getDisplayName().getString());
			}
		}
		return normalize(text.toString());
	}

	/** Lower case, without accents, so "électrik" finds "Electrik". */
	private static String normalize(String text) {
		return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.toLowerCase(java.util.Locale.ROOT);
	}

	/** How many Pokémon answer the search, and in which boxes. */
	private String searchSummary() {
		int found = 0;
		java.util.Set<Integer> boxes = new java.util.TreeSet<>();
		for (PokemonDto pokemon : allPokemon) {
			if (matches(pokemon)) {
				found++;
				if (pokemon.teamSlot() == null && pokemon.boxId() != null) {
					boxes.add(pokemon.boxId());
				}
			}
		}
		if (found == 0) {
			return Component.translatable("phantasmon.pc.screen.search_none").getString();
		}
		String where = boxes.isEmpty() ? "" : Component.translatable("phantasmon.pc.screen.search_boxes",
				String.join(", ", boxes.stream().map(String::valueOf).toList())).getString();
		return Component.translatable("phantasmon.pc.screen.search_found", found, where).getString();
	}

	/** A slot that doesn't answer the search: greyed over its model, above it (the model has depth). */
	private void greyOut(GuiGraphics g, int x, int y, int w, int h) {
		g.flush();
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(0, 0, 300);
		g.fill(x, y, x + w, y + h, 0xC0181818);
		pose.popPose();
	}

	/** The box's title: its name and number, or "BOX n / 16". */
	private String boxTitle() {
		String name = com.mystaria.phantasmon.client.pokemon.PcBoxTypes.name(pcOwner(), currentBox);
		return name == null ? upper(Component.translatable("phantasmon.pc.screen.box", currentBox, BOX_COUNT).getString())
				: name + "  ·  " + currentBox + "/" + BOX_COUNT;
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

	/**
	 * Team box (TODO-36): the row {@code row} and the team trade places, column by column — the row's 1st Pokémon
	 * becomes the team's 1st, the team's 1st takes its place in the row, and so on. Each column is one move the backend
	 * already knows (onto an occupied slot it swaps the two, D-04), sent one after the other.
	 */
	private void swapTeamRow(int row) {
		List<java.util.function.Supplier<java.util.concurrent.CompletableFuture<?>>> moves = new ArrayList<>();
		for (int column = 0; column < BOX_COLUMNS; column++) {
			PokemonDto inRow = boxSlots[row * BOX_COLUMNS + column];
			PokemonDto inTeam = column < TEAM_SIZE ? teamSlots[column] : null;
			int teamSlot = column + 1;
			int pcSlot = row * BOX_COLUMNS + column + 1;
			int box = currentBox;
			if (inRow != null) {
				moves.add(() -> pokemonClient.update(session.accessToken(), inRow.uuid(),
						PokemonUpdateRequestDto.movingToTeamSlot(teamSlot)));
			} else if (inTeam != null) {
				moves.add(() -> pokemonClient.update(session.accessToken(), inTeam.uuid(),
						PokemonUpdateRequestDto.movingToPcSlot(box, pcSlot)));
			}
		}
		if (moves.isEmpty()) {
			return;
		}
		loading = true;
		java.util.concurrent.CompletableFuture<?> chain = java.util.concurrent.CompletableFuture.completedFuture(null);
		for (var next : moves) {
			chain = chain.thenCompose(ignored -> next.get());
		}
		chain.thenAccept(done -> Minecraft.getInstance().execute(() -> {
			setStatus(Component.translatable("phantasmon.pc.screen.team_swapped", row + 1).getString(), false);
			refresh();
		})).exceptionally(ex -> {
			Minecraft.getInstance().execute(() -> {
				setStatus(errorMessage(ex), true);
				refresh(); // what already moved shows as it is
			});
			return null;
		});
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
	 * the clipboard and creates each via the backend.
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

		List<PokemonCreateRequestDto> requests = new ArrayList<>();
		String bearerToken = session.accessToken();
		for (ShowdownPokemon set : parsed.subList(0, Math.min(parsed.size(), 6))) { // a full team at most
			PokemonCreateRequestDto request;
			try {
				request = ShowdownImportMapper.toCreateRequest(set, com.mystaria.phantasmon.client.pokemon.CobblemonDataVersion.local(),
						com.mystaria.phantasmon.client.pokemon.showdown.CobblemonShowdownNames::baseAbilities);
			} catch (ShowdownParseException ex) {
				setStatus(Component.translatable("phantasmon.pokemon.import.parse_error_for", set.speciesToken(), ex.getMessage()).getString(), true);
				continue;
			}
			requests.add(request);
		}
		if (requests.isEmpty()) {
			return;
		}

		loading = true;
		// One after the other: the backend gives each the first free PC slot, so created together they would all want
		// the same one and only one would be kept. A refused Pokémon doesn't stop the next ones.
		java.util.concurrent.atomic.AtomicReference<Throwable> firstError = new java.util.concurrent.atomic.AtomicReference<>();
		CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
		for (PokemonCreateRequestDto request : requests) {
			chain = chain.thenCompose(done -> (someoneElses() ? pokemonClient.createFor(bearerToken, request, ownerUuid)
					: pokemonClient.create(bearerToken, request))
					.handle((created, ex) -> {
						if (ex != null) {
							firstError.compareAndSet(null, ex);
							return done;
						}
						return done + 1;
					}));
		}
		chain.thenAccept(count -> Minecraft.getInstance().execute(() -> {
			Throwable error = firstError.get();
			if (error != null) {
				setStatus(errorMessage(error), true);
			} else {
				setStatus(Component.translatable("phantasmon.pc.screen.imported", count).getString(), false);
			}
			refresh();
		}));
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
				com.mystaria.phantasmon.client.audio.PhantasmonSounds.play(com.mystaria.phantasmon.client.audio.PhantasmonSounds.Sfx.PRESSING_A);
				handleDelete();
			}
			return true;
		}
		if (inside(x, y, CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE)) {
			onClose();
			return true;
		}
		if (inside(x, y, IMPORT_X, IMPORT_Y, IMPORT_W, IMPORT_H)) {
			com.mystaria.phantasmon.client.audio.PhantasmonSounds.play(com.mystaria.phantasmon.client.audio.PhantasmonSounds.Sfx.PRESSING_A);
			importFromClipboard();
			return true;
		}
		if (inside(x, y, EXPORT_X, IMPORT_Y, IMPORT_W, IMPORT_H)) {
			com.mystaria.phantasmon.client.audio.PhantasmonSounds.play(com.mystaria.phantasmon.client.audio.PhantasmonSounds.Sfx.PRESSING_A);
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
		if (inside(x, y, SEARCH_X + SEARCH_W - 30, SEARCH_Y, 30, SEARCH_H) && !search.isEmpty()) {
			search = "";
			searchFocused = false;
			return true;
		}
		boolean onSearch = inside(x, y, SEARCH_X, SEARCH_Y, SEARCH_W, SEARCH_H);
		searchFocused = onSearch;
		if (onSearch) {
			renaming = null;
			return true;
		}
		if (renaming == null && inside(x, y, TITLE_X, NAV_Y, TITLE_W, NAV_H)) {
			String name = com.mystaria.phantasmon.client.pokemon.PcBoxTypes.name(pcOwner(), currentBox);
			renaming = name == null ? "" : name;
			return true;
		}
		if (renaming != null && !inside(x, y, TITLE_X, NAV_Y, TITLE_W, NAV_H)) {
			saveBoxName();
		}
		if (inside(x, y, TYPE_X, NAV_Y, TYPE_W, NAV_H)) {
			com.mystaria.phantasmon.client.audio.PhantasmonSounds.play(com.mystaria.phantasmon.client.audio.PhantasmonSounds.Sfx.PRESSING_A);
			boolean team = com.mystaria.phantasmon.client.pokemon.PcBoxTypes.toggle(pcOwner(), currentBox);
			setStatus(Component.translatable(team ? "phantasmon.pc.screen.box_now_team" : "phantasmon.pc.screen.box_now_loose",
					currentBox).getString(), false);
			return true;
		}
		if (selectedPokemon() != null) {
			if (inside(x, y, EDIT_X, FOOTER_BUTTON_Y, EDIT_W, FOOTER_BUTTON_H)) {
				com.mystaria.phantasmon.client.audio.PhantasmonSounds.play(com.mystaria.phantasmon.client.audio.PhantasmonSounds.Sfx.PRESSING_A);
				openEditor();
				return true;
			}
			if (inside(x, y, DELETE_X, FOOTER_BUTTON_Y, DELETE_W, FOOTER_BUTTON_H)) {
				com.mystaria.phantasmon.client.audio.PhantasmonSounds.play(com.mystaria.phantasmon.client.audio.PhantasmonSounds.Sfx.PRESSING_A);
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
					if (teamBox() && ref.kind() == SlotRef.Kind.PC) {
						swapTeamRow(ref.index() / BOX_COLUMNS);
					} else {
						quickMove(pokemon, ref);
					}
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

	private void saveBoxName() {
		com.mystaria.phantasmon.client.pokemon.PcBoxTypes.rename(pcOwner(), currentBox, renaming);
		renaming = null;
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (chr < 32 || chr == 127) {
			return super.charTyped(chr, modifiers);
		}
		if (renaming != null) {
			if (renaming.length() < com.mystaria.phantasmon.client.pokemon.PcBoxTypes.NAME_MAX) {
				renaming += chr;
			}
			return true;
		}
		if (searchFocused) {
			if (search.length() < 40) {
				search += chr;
			}
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (!deleteConfirmOpen && renaming != null) {
			switch (keyCode) {
				case 256 -> renaming = null; // Escape: unchanged
				case 257, 335 -> saveBoxName(); // Enter
				case 259 -> {
					if (!renaming.isEmpty()) {
						renaming = renaming.substring(0, renaming.length() - 1);
					}
				}
				default -> {
				}
			}
			return true;
		}
		if (!deleteConfirmOpen && searchFocused) {
			switch (keyCode) {
				case 256, 257, 335 -> searchFocused = false; // Escape / Enter: the search stays, the keyboard goes back
				case 259 -> {
					if (!search.isEmpty()) {
						search = hasControlDown() ? "" : search.substring(0, search.length() - 1);
					}
				}
				case 86 -> {
					if (hasControlDown()) {
						String clip = Minecraft.getInstance().keyboardHandler.getClipboard().strip();
						search = (search + clip).length() > 40 ? search : search + clip;
					}
				}
				default -> {
				}
			}
			return true;
		}
		if (!deleteConfirmOpen && keyCode == 70 && hasControlDown()) { // Ctrl+F
			searchFocused = true;
			return true;
		}
		if (deleteConfirmOpen) {
			if (keyCode == 256) {
				deleteConfirmOpen = false;
				return true;
			}
			if (keyCode == 257 || keyCode == 335) {
				com.mystaria.phantasmon.client.audio.PhantasmonSounds.play(com.mystaria.phantasmon.client.audio.PhantasmonSounds.Sfx.PRESSING_A);
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
			if (inside(x, y, gridSlotX(i), gridSlotY(i), GRID_SLOT_W, gridSlotH())) {
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

	private int gridSlotY(int index) {
		return teamBox() ? GRID_Y + (index / BOX_COLUMNS) * (TEAM_SLOT_H + TEAM_ROW_GAP)
				: GRID_Y + (index / BOX_COLUMNS) * (GRID_SLOT_H + GRID_GAP);
	}

	private int gridSlotH() {
		return teamBox() ? TEAM_SLOT_H : GRID_SLOT_H;
	}

	/** Whose PC: its owner's own box types. */
	private UUID pcOwner() {
		return ownerUuid != null ? ownerUuid : session.playerUuid();
	}

	/** The current box is a team box: one team per row (TODO-36). */
	private boolean teamBox() {
		return com.mystaria.phantasmon.client.pokemon.PcBoxTypes.isTeamBox(pcOwner(), currentBox);
	}

	/** The team row under the cursor in a team box, or -1. */
	private int teamRowAt(double x, double y) {
		if (!teamBox()) {
			return -1;
		}
		for (int row = 0; row < BOX_ROWS; row++) {
			int top = gridSlotY(row * BOX_COLUMNS) - 5;
			if (inside(x, y, GRID_PANEL_X + 4, top, GRID_PANEL_W - 8, TEAM_SLOT_H + 10)) {
				return row;
			}
		}
		return -1;
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
		renderSearchField(g, mx, my);
	}

	/** The search field (TODO-38): a click or Ctrl+F gives it the keyboard; ✕ clears it. */
	private void renderSearchField(GuiGraphics g, double mx, double my) {
		boolean hovered = inside(mx, my, SEARCH_X, SEARCH_Y, SEARCH_W, SEARCH_H);
		blackBorder(g, SEARCH_X, SEARCH_Y, SEARCH_W, SEARCH_H, searchFocused ? CYAN : PANEL_BORDER);
		g.fill(SEARCH_X, SEARCH_Y, SEARCH_X + SEARCH_W, SEARCH_Y + SEARCH_H, hovered || searchFocused ? MOVE_BG : INFO_BOX_BG);
		sunkenEdge(g, SEARCH_X, SEARCH_Y, SEARCH_W, SEARCH_H);
		float textY = SEARCH_Y + (SEARCH_H - 14) / 2f;
		boolean clear = !search.isEmpty();
		int textRight = SEARCH_X + SEARCH_W - (clear ? 34 : 10);
		if (search.isEmpty() && !searchFocused) {
			drawText(g, fitText(Component.translatable("phantasmon.pc.screen.search_hint").getString(), textRight - SEARCH_X - 12, 2f, false, 0f),
					SEARCH_X + 10, textY, 2f, DIM, false, 0f);
		} else {
			String shown = search;
			while (!shown.isEmpty() && textWidth(shown, 2f, false, 0f) > textRight - SEARCH_X - 26) {
				shown = shown.substring(1);
			}
			drawText(g, shown, SEARCH_X + 10, textY, 2f, WHITE, false, 0f);
			if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
				drawText(g, "_", SEARCH_X + 12 + textWidth(shown, 2f, false, 0f), textY, 2f, CYAN, false, 0f);
			}
		}
		if (clear) {
			boolean clearHovered = inside(mx, my, SEARCH_X + SEARCH_W - 30, SEARCH_Y + 4, 26, SEARCH_H - 8);
			drawCentered(g, "✕", SEARCH_X + SEARCH_W - 17, textY, 2f, clearHovered ? WHITE : MUTED, false, 0f);
		}
	}

	private void renderTeamRail(GuiGraphics g, double mx, double my) {
		renderRailFrame(g, 15, 72, 194, 758, Component.translatable("phantasmon.trade.screen.team", playerName()).getString(),
				Component.translatable("phantasmon.pc.screen.team_hint"));
		for (int i = 0; i < TEAM_SIZE; i++) {
			SlotRef ref = new SlotRef(SlotRef.Kind.TEAM, i);
			renderSlot(g, teamSlotX(i), teamSlotY(i), 85, 214, teamSlots[i], lookOf(ref, teamSlots[i], mx, my, teamSlotX(i), teamSlotY(i), 85, 214), RAIL_SLOT);
			if (searching() && !matches(teamSlots[i])) {
				greyOut(g, teamSlotX(i), teamSlotY(i), 85, 214);
			}
		}
	}

	/** Right panel: ◀ BOÎTE n / 16 ▶, then the 6×5 grid. */
	private void renderGridPanel(GuiGraphics g, double mx, double my) {
		blitTexture(g, TEX_RAIL, GRID_PANEL_X, 72, GRID_PANEL_W, 758);
		outline(g, GRID_PANEL_X, 72, GRID_PANEL_W, 758, RAIL_BORDER);

		renderArrowButton(g, PREV_X, NAV_Y, "◀", inside(mx, my, PREV_X, NAV_Y, NAV_W, NAV_H));
		renderArrowButton(g, NEXT_X, NAV_Y, "▶", inside(mx, my, NEXT_X, NAV_Y, NAV_W, NAV_H));
		if (renaming != null) {
			// Naming the box: a field in the title's place; Enter keeps it, Escape gives up, empty removes the name.
			blackBorder(g, TITLE_X, NAV_Y, TITLE_W, NAV_H, CYAN);
			g.fill(TITLE_X, NAV_Y, TITLE_X + TITLE_W, NAV_Y + NAV_H, INFO_BOX_BG);
			sunkenEdge(g, TITLE_X, NAV_Y, TITLE_W, NAV_H);
			String shown = renaming.isEmpty() ? Component.translatable("phantasmon.pc.screen.box_name_hint").getString() : renaming;
			String fitted = fitText(shown, TITLE_W - 30, 2f, false, 0f);
			drawText(g, fitted, TITLE_X + 10, NAV_Y + 8, 2f, renaming.isEmpty() ? DIM : WHITE, false, 0f);
			if (!renaming.isEmpty() && (System.currentTimeMillis() / 500) % 2 == 0) {
				drawText(g, "_", TITLE_X + 12 + textWidth(fitted, 2f, false, 0f), NAV_Y + 8, 2f, CYAN, false, 0f);
			}
		} else {
			boolean titleHovered = inside(mx, my, TITLE_X, NAV_Y, TITLE_W, NAV_H);
			String boxLabel = fitText(boxTitle(), TITLE_W - 20, 2f, true, 2f);
			drawCentered(g, boxLabel, TITLE_X + TITLE_W / 2f, NAV_Y + 8, 2f, titleHovered ? WHITE : MUTED, true, 2f);
			if (titleHovered) {
				g.fill(TITLE_X + 20, NAV_Y + NAV_H - 3, TITLE_X + TITLE_W - 20, NAV_Y + NAV_H - 1, CYAN2);
			}
		}
		g.fill(GRID_PANEL_X + 9, 117, GRID_PANEL_X + GRID_PANEL_W - 9, 117 + hairline(), LINE_25);

		boolean team = teamBox();
		boolean typeHovered = inside(mx, my, TYPE_X, NAV_Y, TYPE_W, NAV_H);
		renderPrimaryButtonFrame(g, TYPE_X, NAV_Y, TYPE_W, NAV_H, typeHovered, team);
		String type = upper(Component.translatable(team ? "phantasmon.pc.screen.box_team" : "phantasmon.pc.screen.box_loose").getString());
		drawCentered(g, fitText(type, TYPE_W - 12, 1f, true, 1.5f), TYPE_X + TYPE_W / 2f, NAV_Y + (NAV_H - 7) / 2f, 1f, WHITE, true, 1.5f);
		if (team) {
			// One band per team row, the hovered one lit: a double click there swaps it with the team.
			int hoveredRow = loading ? -1 : teamRowAt(mx, my);
			for (int row = 0; row < BOX_ROWS; row++) {
				int top = gridSlotY(row * BOX_COLUMNS) - 5;
				int h = TEAM_SLOT_H + 10;
				int left = GRID_PANEL_X + 6;
				int w = GRID_PANEL_W - 12;
				// Each team its own colour (Adrien 2026-11-11: the rows must stand apart): a tinted band, a coloured rim
				// and bar; brighter under the mouse.
				int hue = TEAM_COLORS[row % TEAM_COLORS.length];
				boolean lit = row == hoveredRow;
				blackBorder(g, left - line(), top - line(), w + 2 * line(), h + 2 * line(), PANEL_BORDER);
				blackBorder(g, left, top, w, h, withAlpha(hue, lit ? 1f : 0.7f));
				g.fillGradient(left, top, left + w, top + h, lerpColor(MOVE_BG, hue, lit ? 0.45f : 0.28f) | 0xFF000000,
						lerpColor(MOVE_BG, hue, lit ? 0.18f : 0.08f) | 0xFF000000);
				raisedEdge(g, left, top, w, h);
				g.fill(left, top, left + 7, top + h, hue);
			}
		}
		for (int i = 0; i < SLOTS_PER_BOX; i++) {
			SlotRef ref = new SlotRef(SlotRef.Kind.PC, i);
			int x = gridSlotX(i);
			int y = gridSlotY(i);
			int h = gridSlotH();
			renderSlot(g, x, y, GRID_SLOT_W, h, boxSlots[i], lookOf(ref, boxSlots[i], mx, my, x, y, GRID_SLOT_W, h), GRID_SLOT);
			if (searching() && !matches(boxSlots[i])) {
				greyOut(g, x, y, GRID_SLOT_W, h);
			}
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
		blackBorder(g, x, y, NAV_W, NAV_H, PANEL_BORDER);
		g.fillGradient(x, y, x + NAV_W, y + NAV_H, gradient[0], gradient[1]);
		raisedEdge(g, x, y, NAV_W, NAV_H);
		drawCentered(g, glyph, x + NAV_W / 2f, y + (NAV_H - 14) / 2f, 2f, WHITE, false, 0f);
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
		} else if (searching()) {
			status = searchSummary();
			color = CYAN;
		} else if (teamBox()) {
			int row = teamRowAt(mx, my);
			status = row < 0 ? Component.translatable("phantasmon.pc.screen.team_box_hint").getString()
					: Component.translatable("phantasmon.pc.screen.team_row_hint", row + 1).getString();
			color = row < 0 ? DIM : CYAN;
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
				(float) cursorX, slotModelAnchorY((float) cursorY, modelScale), modelScale, com.mystaria.phantasmon.client.pokemon.PokemonLook.of(dragged));
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
