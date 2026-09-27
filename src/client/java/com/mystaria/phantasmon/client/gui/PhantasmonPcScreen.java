package com.mystaria.phantasmon.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.util.math.QuaternionUtilsKt;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.network.BackendApiException;
import com.mystaria.phantasmon.client.network.BackendErrorMessages;
import com.mystaria.phantasmon.client.pokemon.HiddenPowerCalculator;
import com.mystaria.phantasmon.client.pokemon.NatureModifiers;
import com.mystaria.phantasmon.client.pokemon.PokemonClient;
import com.mystaria.phantasmon.client.pokemon.PokemonCommandHandler;
import com.mystaria.phantasmon.client.pokemon.PokemonCreateRequestDto;
import com.mystaria.phantasmon.client.pokemon.PokemonDto;
import com.mystaria.phantasmon.client.pokemon.PokemonUpdateRequestDto;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownImportMapper;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownParseException;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownParser;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownPokemon;

/**
 * Graphical HUD PC screen (Adrien: 2026-09-27), modeled after
 * {@code Documentation/prototype_pc.html}. Covers grid + team + detail +
 * drag&drop move/swap + delete, matching the exact PC/team semantics already
 * enforced server-side ({@link PokemonUpdateRequestDto}). "Edit" opens
 * {@link PhantasmonPcEditScreen}; the grid's footer "Importer" button reads a
 * Showdown export from the clipboard exactly like {@code /phantasmon pokemon
 * import}.
 *
 * <p><b>Single-panel layout</b>: everything lives inside one outer "chassis"
 * panel — detail (left, 35% of content width) | PC grid (middle, reserved
 * 50%) | team (right, remainder). The PC grid keeps a fixed aspect ratio
 * (slots are always square, sized from whichever of the middle sector's
 * width/available height is more constraining, with edge padding around the
 * slot cluster at least double the gap between individual slots) and is
 * centered in the content area; the team box shrink-wraps to its 6 rows and
 * its icon slots are exactly the grid's own square slot size (name text sits
 * outside that square, not inside a stretched slot).
 *
 * <p>The detail column has a borderless "screen" area for the 3D icon (level
 * badge top-left, held item bottom-right, overlaid directly on it — no boxed
 * stat lines for either anymore) and a single merged stats+moves box below it
 * (name+form beside its type badge(s), nature/ability and Tera/Hidden Power
 * each as a two-column row, then IVs — collapsed to "Full 31 IVs" when every
 * stat is maxed — EVs — zero-value stats omitted — and the move list).
 *
 * <p>Visual fidelity is an approximation: vanilla {@link GuiGraphics} has no
 * blur/backdrop-filter/clip-path/box-shadow equivalent. Panels/slots/buttons
 * use real nine-slice textures generated from the prototype's exact CSS
 * colors (see {@code assets/phantasmon/textures/gui/sprites/pc/}).
 *
 * <p>Pokémon icons are real 3D model miniatures via Cobblemon's own
 * {@code drawProfilePokemon}, not flat sprites — the same API Cobblemon's own
 * PC screen uses. That Kotlin function has many defaulted parameters; calling
 * it from Java means going through the compiler-generated
 * {@code drawProfilePokemon$default} bridge with a bitmask marking which
 * parameters should fall back to their library defaults (verified by
 * decompiling Cobblemon's own {@code StorageSlot.renderSlot}, mask
 * {@code 65416} = bits 3,7,8,9,10,11,12,13,14,15 defaulted).
 */
public final class PhantasmonPcScreen extends Screen {

	private static final Logger LOG = LoggerFactory.getLogger(PhantasmonPcScreen.class);
	private static java.lang.reflect.Method drawProfilePokemonDefaultMethod;

	private static final int BOX_COLUMNS = 6;
	private static final int BOX_ROWS = 5;
	private static final int SLOTS_PER_BOX = BOX_COLUMNS * BOX_ROWS;
	private static final int TEAM_SIZE = 6;
	private static final int BOX_COUNT = 16;

	private static final int SLOT_GAP = 4;
	/** Padding between the grid box's own border and the outer edge of its slot cluster — kept at least double {@link #SLOT_GAP} per Adrien's feedback (the previous version only left a single gap's worth of edge padding). */
	private static final int GRID_EDGE_PAD = SLOT_GAP * 2;
	private static final int GRID_HEADER_HEIGHT = 26;
	private static final int TEAM_HEADER_HEIGHT = 16;
	private static final int NAV_BUTTON_SIZE = 16;
	private static final int BUTTON_HEIGHT = 14;
	private static final int FOOTER_HEIGHT = 20;

	/** Content width shares within the single root panel: grid gets at least half, detail 35%, team the remainder. */
	private static final int LEFT_WIDTH_PERCENT = 35;
	private static final int MID_WIDTH_PERCENT = 50;

	private static final int SECTION_HEADER_COLOR = 0x5FE6F2;
	/** Nine-slice GUI sprites generated from the prototype's exact CSS colors — see {@code Documentation/prototype_pc.html}'s {@code .wisp-chassis}/{@code .grid-slot}/{@code .btn-spectral} rules. Package-visible: also reused by {@link PhantasmonPcEditScreen} to match the same visual language. */
	static final ResourceLocation SPRITE_PANEL = ResourceLocation.fromNamespaceAndPath("phantasmon", "pc/panel");
	/** Same look as {@link #SPRITE_PANEL} but noticeably more transparent (Adrien 2026-09-27) — used only for the single outer root chassis, so the game world still shows through it a bit; the nested sub-boxes stay on the more opaque texture for text legibility. */
	private static final ResourceLocation SPRITE_PANEL_ROOT = ResourceLocation.fromNamespaceAndPath("phantasmon", "pc/panel_root");
	private static final ResourceLocation SPRITE_SLOT = ResourceLocation.fromNamespaceAndPath("phantasmon", "pc/slot");
	private static final ResourceLocation SPRITE_SLOT_HOVER = ResourceLocation.fromNamespaceAndPath("phantasmon", "pc/slot_hover");
	private static final ResourceLocation SPRITE_SLOT_SELECTED = ResourceLocation.fromNamespaceAndPath("phantasmon", "pc/slot_selected");
	private static final ResourceLocation SPRITE_SLOT_DRAG = ResourceLocation.fromNamespaceAndPath("phantasmon", "pc/slot_drag");
	static final ResourceLocation SPRITE_BUTTON = ResourceLocation.fromNamespaceAndPath("phantasmon", "pc/button");
	static final ResourceLocation SPRITE_BUTTON_HOVER = ResourceLocation.fromNamespaceAndPath("phantasmon", "pc/button_hover");
	private static final ResourceLocation SPRITE_BUTTON_RED = ResourceLocation.fromNamespaceAndPath("phantasmon", "pc/button_red");
	private static final ResourceLocation SPRITE_BUTTON_RED_HOVER = ResourceLocation.fromNamespaceAndPath("phantasmon", "pc/button_red_hover");
	/** Small shiny-status star (Adrien 2026-09-27: replaces a plain "*" suffix in the name — drawn top-right of the icon "screen" instead). */
	private static final ResourceLocation SPRITE_STAR = ResourceLocation.fromNamespaceAndPath("phantasmon", "pc/star");
	/**
	 * Cobblemon's own {@code StorageSlot.renderSlot} inner scale argument to
	 * {@code drawProfilePokemon} (decompiled, not guessed) — kept as-is. What we'd
	 * been missing (found only after two rounds of failed centering attempts,
	 * Adrien 2026-09-27) is the OUTER pose transform Cobblemon wraps around that
	 * call: {@code pose.translate(x, y, 0f)} (z=0 — we were using z=100) followed
	 * by {@code pose.scale(2.5f, 2.5f, 1f)} <em>before</em> handing the stack to
	 * {@code drawProfilePokemon}. Without that outer scale+z, the model rendered
	 * with a real, reproducible off-center offset that got worse the more we
	 * tried to enlarge it — replicating Cobblemon's exact transform shape is what
	 * actually fixes it, not any particular anchor point we guessed at. Final
	 * on-screen size is controlled purely by {@link #SLOT_OUTER_SCALE}/
	 * {@link #DETAIL_OUTER_SCALE} (a multiplier on top of this same 2.5f base),
	 * never by changing this constant.
	 */
	private static final float ICON_INNER_SCALE = 4.5f;
	private static final float COBBLEMON_OUTER_SCALE = 2.5f;
	/** Slot/team icon final size = {@link #COBBLEMON_OUTER_SCALE} × this. Adrien asked twice to enlarge further ("3x", then "still a bit more"). */
	private static final float SLOT_ENLARGE = 2f;
	/** The detail screen gets a distinctly bigger render than a small slot, same mechanism. */
	private static final float DETAIL_ENLARGE = 5f;
	private static final float SLOT_OUTER_SCALE = COBBLEMON_OUTER_SCALE * SLOT_ENLARGE;
	private static final float DETAIL_OUTER_SCALE = COBBLEMON_OUTER_SCALE * DETAIL_ENLARGE;
	/**
	 * Fraction of the box's own height, from its top edge, used as the model's
	 * vertical anchor point (0 = top edge, 0.5 = dead center, 1 = bottom edge;
	 * negative values sit above the box's own top edge). Found empirically by
	 * Adrien in-game via a temporary {@code /phantasmon debug iconanchor}
	 * command (removed 2026-09-27 once he confirmed this value) after several
	 * blind guess-then-screenshot rounds failed to converge. Used for the grid
	 * and team slots; the detail screen's icon (much bigger relative to its
	 * box, different proportions) gets its own independent value, see
	 * {@link #DETAIL_ICON_ANCHOR_RATIO}.
	 */
	private static final float SLOT_ICON_ANCHOR_RATIO = -0.1f;
	private static final float DETAIL_ICON_ANCHOR_RATIO = -0.15f;
	private static final int NAME_ROW_HEIGHT = 12;

	private final PokemonClient pokemonClient;
	private final AuthSession session;

	private List<PokemonDto> allPokemon = List.of();
	private final PokemonDto[] boxSlots = new PokemonDto[SLOTS_PER_BOX];
	private final PokemonDto[] teamSlots = new PokemonDto[TEAM_SIZE];

	private int currentBox = 1;
	private SlotRef selected;
	private SlotRef dragSource;
	private boolean loading;
	private Component statusMessage = Component.empty();

	// Single outer chassis panel.
	private int rootX, rootY, rootW, rootH;
	// Left column (detail).
	private int leftX, leftY, leftW, leftH;
	/** Name + type badge(s) row, above the 3D display. */
	private int nameRowY;
	/** Icon "screen" area — deliberately borderless (Adrien: remove the border around the 3D model). */
	private int screenBoxX, screenBoxY, screenBoxW, screenBoxH;
	/** Single box holding both stats and moves (previously two separate sub-boxes, merged per feedback). */
	private int mergedBoxX, mergedBoxY, mergedBoxW, mergedBoxH;
	// PC grid — square slots, fixed aspect, centered in its reserved sector.
	private int gridX, gridY, gridW, gridH;
	private int slotSize;
	// Team box — shrink-wrapped to its 6 rows, icon slots match the grid's own slot size.
	private int teamBoxX, teamBoxY, teamBoxW, teamBoxH;

	private int editButtonX, editButtonY, editButtonW;
	private int deleteButtonX, deleteButtonY, deleteButtonW;
	private int prevBoxX, nextBoxX, boxNavY;
	private int importButtonX, importButtonY, importButtonW;

	public PhantasmonPcScreen(PokemonClient pokemonClient, AuthSession session) {
		super(Component.translatable("phantasmon.pc.title"));
		this.pokemonClient = pokemonClient;
		this.session = session;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		layoutPanels();
		refresh();
	}

	private void layoutPanels() {
		int rootMargin = 12;
		int pad = 10;
		int gap = 10;

		// Left column width and a provisional "grid sector" width are first computed
		// against the full available screen width, purely to size the grid's square
		// slots (needs *some* width figure to work from) — not the final layout.
		int provisionalRootW = this.width - rootMargin * 2;
		int provisionalContentW = provisionalRootW - 2 * pad;
		int available = provisionalContentW - 2 * gap;
		leftW = available * LEFT_WIDTH_PERCENT / 100;
		int midSectorW = available * MID_WIDTH_PERCENT / 100;

		rootH = this.height - rootMargin * 2;
		int contentH = rootH - 2 * pad;

		// ---- PC grid: square slots, fixed aspect ratio ----
		int sectorPad = 6;
		int availableGridW = midSectorW - 2 * sectorPad;
		int availableGridH = contentH - GRID_HEADER_HEIGHT - FOOTER_HEIGHT - 2 * sectorPad;
		int slotFromWidth = (availableGridW - 2 * GRID_EDGE_PAD - (BOX_COLUMNS - 1) * SLOT_GAP) / BOX_COLUMNS;
		int slotFromHeight = (availableGridH - 2 * GRID_EDGE_PAD - (BOX_ROWS - 1) * SLOT_GAP) / BOX_ROWS;
		slotSize = Math.max(16, Math.min(slotFromWidth, slotFromHeight));

		gridW = 2 * GRID_EDGE_PAD + BOX_COLUMNS * slotSize + (BOX_COLUMNS - 1) * SLOT_GAP;
		int gridInnerH = 2 * GRID_EDGE_PAD + BOX_ROWS * slotSize + (BOX_ROWS - 1) * SLOT_GAP;
		gridH = GRID_HEADER_HEIGHT + gridInnerH + FOOTER_HEIGHT;

		// ---- Team: shrink-wrapped tightly around its square slots (equal padding on
		// both sides — Adrien: it used to reserve a whole sector's width, leaving a big
		// empty gap to the right of the slot once its name label was removed). Since
		// team no longer claims a fixed percentage of the width, the whole root panel
		// is now packed to fit its actual content instead of stretching to the screen
		// ("libère de la place ... tu vas pouvoir le rapetisser").
		int teamPad = 8;
		teamBoxW = 2 * teamPad + slotSize;
		teamBoxH = TEAM_HEADER_HEIGHT + teamPad + TEAM_SIZE * slotSize + (TEAM_SIZE - 1) * SLOT_GAP + teamPad;

		// ---- Pack sections left-to-right and size the root panel to fit exactly ----
		int totalContentW = leftW + gap + gridW + gap + teamBoxW;
		rootW = totalContentW + 2 * pad;
		rootX = (this.width - rootW) / 2;
		rootY = rootMargin;

		int contentX = rootX + pad;
		int contentY = rootY + pad;

		leftX = contentX;
		leftY = contentY;
		leftH = contentH;

		gridX = leftX + leftW + gap;
		gridY = contentY + (contentH - gridH) / 2;

		teamBoxX = gridX + gridW + gap;
		teamBoxY = contentY + (contentH - teamBoxH) / 2;

		// ---- Detail column: buttons, then a borderless icon "screen", then one merged stats+moves box ----
		int buttonGap = 6;
		editButtonW = (leftW - 12 - buttonGap) / 2;
		deleteButtonW = editButtonW;
		editButtonX = leftX + 6;
		deleteButtonX = editButtonX + editButtonW + buttonGap;
		editButtonY = leftY + 6;
		deleteButtonY = leftY + 6;

		nameRowY = editButtonY + BUTTON_HEIGHT + 6;

		int subBoxesTop = nameRowY + NAME_ROW_HEIGHT + 4;
		int subBoxesBottom = leftY + leftH - 6;
		int subBoxesAvailable = Math.max(140, subBoxesBottom - subBoxesTop - gap);
		screenBoxH = subBoxesAvailable * 35 / 100;
		mergedBoxH = subBoxesAvailable - screenBoxH;

		screenBoxX = leftX + 4;
		screenBoxW = leftW - 8;
		screenBoxY = subBoxesTop;

		mergedBoxX = screenBoxX;
		mergedBoxW = screenBoxW;
		mergedBoxY = screenBoxY + screenBoxH + gap;

		int navMargin = 8;
		boxNavY = gridY + navMargin;
		prevBoxX = gridX + navMargin;
		nextBoxX = gridX + gridW - navMargin - NAV_BUTTON_SIZE;

		importButtonX = gridX + navMargin;
		importButtonY = gridY + gridH - FOOTER_HEIGHT + 2;
		importButtonW = gridW - 2 * navMargin;
	}

	/** Public: also called by {@link PhantasmonPcEditScreen} after a save, to reload this screen's data before returning to it. */
	public void refresh() {
		if (!session.isAuthenticated()) {
			statusMessage = Component.translatable("phantasmon.error.not_authenticated");
			return;
		}
		loading = true;
		pokemonClient.listForOwner(session.accessToken(), session.playerUuid())
				.thenAccept(pokemons -> Minecraft.getInstance().execute(() -> {
					this.allPokemon = List.of(pokemons);
					this.loading = false;
					this.statusMessage = Component.empty();
					recomputeSlots();
				}))
				.exceptionally(ex -> {
					Minecraft.getInstance().execute(() -> {
						this.loading = false;
						this.statusMessage = errorMessage(ex);
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
		if (selected != null && pokemonAt(selected) == null) {
			selected = null;
		}
	}

	private void changeBox(int delta) {
		currentBox = Math.floorMod(currentBox - 1 + delta, BOX_COUNT) + 1;
		selected = null;
		recomputeSlots();
	}

	private PokemonDto pokemonAt(SlotRef ref) {
		if (ref == null) {
			return null;
		}
		return ref.kind() == SlotRef.Kind.PC ? boxSlots[ref.index()] : teamSlots[ref.index()];
	}

	private void handleDrop(SlotRef from, SlotRef to) {
		PokemonDto moving = pokemonAt(from);
		if (moving == null) {
			return;
		}
		PokemonUpdateRequestDto request = to.kind() == SlotRef.Kind.TEAM
				? PokemonUpdateRequestDto.movingToTeamSlot(to.index() + 1)
				: PokemonUpdateRequestDto.movingToPcSlot(currentBox, to.index() + 1);
		loading = true;
		pokemonClient.update(session.accessToken(), moving.uuid(), request)
				.thenAccept(updated -> Minecraft.getInstance().execute(() -> {
					selected = to;
					refresh();
				}))
				.exceptionally(ex -> {
					Minecraft.getInstance().execute(() -> {
						loading = false;
						statusMessage = errorMessage(ex);
					});
					return null;
				});
	}

	/**
	 * Reads a Showdown export (one or more Pokémon, blank-line separated) from
	 * the clipboard and creates each via the backend, exactly mirroring
	 * {@code /phantasmon pokemon import}'s logic — just triggered from the HUD.
	 */
	private void importFromClipboard() {
		String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
		if (clipboard == null || clipboard.isBlank()) {
			statusMessage = Component.translatable("phantasmon.pokemon.import.clipboard_empty");
			return;
		}
		List<ShowdownPokemon> parsed;
		try {
			parsed = ShowdownParser.parseTeam(clipboard);
		} catch (ShowdownParseException ex) {
			statusMessage = Component.translatable("phantasmon.pokemon.import.parse_error", ex.getMessage());
			return;
		}

		List<CompletableFuture<PokemonDto>> creations = new ArrayList<>();
		String bearerToken = session.accessToken();
		for (ShowdownPokemon set : parsed) {
			PokemonCreateRequestDto request;
			try {
				request = ShowdownImportMapper.toCreateRequest(set, PokemonCommandHandler.COBBLEMON_DATA_VERSION);
			} catch (ShowdownParseException ex) {
				statusMessage = Component.translatable("phantasmon.pokemon.import.parse_error_for", set.speciesToken(), ex.getMessage());
				continue;
			}
			creations.add(pokemonClient.create(bearerToken, request));
		}
		if (creations.isEmpty()) {
			return;
		}

		loading = true;
		CompletableFuture.allOf(creations.toArray(new CompletableFuture[0]))
				.thenRun(() -> Minecraft.getInstance().execute(this::refresh))
				.exceptionally(ex -> {
					Minecraft.getInstance().execute(() -> {
						loading = false;
						statusMessage = errorMessage(ex);
					});
					return null;
				});
	}

	private void handleDelete() {
		PokemonDto target = pokemonAt(selected);
		if (target == null) {
			return;
		}
		loading = true;
		pokemonClient.delete(session.accessToken(), target.uuid())
				.thenAccept(ignored -> Minecraft.getInstance().execute(() -> {
					selected = null;
					refresh();
				}))
				.exceptionally(ex -> {
					Minecraft.getInstance().execute(() -> {
						loading = false;
						statusMessage = errorMessage(ex);
					});
					return null;
				});
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		if (isInside(editButtonX, editButtonY, editButtonW, BUTTON_HEIGHT, mouseX, mouseY)) {
			PokemonDto dto = pokemonAt(selected);
			if (dto != null) {
				Minecraft.getInstance().setScreen(new PhantasmonPcEditScreen(pokemonClient, session, dto, this));
			}
			return true;
		}
		if (isInside(deleteButtonX, deleteButtonY, deleteButtonW, BUTTON_HEIGHT, mouseX, mouseY)) {
			if (selected != null) {
				handleDelete();
			}
			return true;
		}
		if (isInside(prevBoxX, boxNavY, NAV_BUTTON_SIZE, NAV_BUTTON_SIZE, mouseX, mouseY)) {
			changeBox(-1);
			return true;
		}
		if (isInside(nextBoxX, boxNavY, NAV_BUTTON_SIZE, NAV_BUTTON_SIZE, mouseX, mouseY)) {
			changeBox(1);
			return true;
		}
		if (isInside(importButtonX, importButtonY, importButtonW, BUTTON_HEIGHT, mouseX, mouseY)) {
			importFromClipboard();
			return true;
		}
		SlotRef ref = slotAt(mouseX, mouseY);
		if (ref != null) {
			selected = ref;
			dragSource = pokemonAt(ref) != null ? ref : null;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && dragSource != null) {
			SlotRef target = slotAt(mouseX, mouseY);
			SlotRef source = dragSource;
			dragSource = null;
			if (target != null && !target.equals(source)) {
				handleDrop(source, target);
			}
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private SlotRef slotAt(double mouseX, double mouseY) {
		int originX = gridX + GRID_EDGE_PAD;
		int originY = gridY + GRID_HEADER_HEIGHT + GRID_EDGE_PAD;
		for (int i = 0; i < SLOTS_PER_BOX; i++) {
			int col = i % BOX_COLUMNS;
			int row = i / BOX_COLUMNS;
			int sx = originX + col * (slotSize + SLOT_GAP);
			int sy = originY + row * (slotSize + SLOT_GAP);
			if (isInside(sx, sy, slotSize, slotSize, mouseX, mouseY)) {
				return new SlotRef(SlotRef.Kind.PC, i);
			}
		}
		int teamPad = 8;
		int teamOriginX = teamBoxX + teamPad;
		int teamOriginY = teamBoxY + TEAM_HEADER_HEIGHT + teamPad;
		int teamRowW = teamBoxW - 2 * teamPad;
		for (int i = 0; i < TEAM_SIZE; i++) {
			int sy = teamOriginY + i * (slotSize + SLOT_GAP);
			if (isInside(teamOriginX, sy, teamRowW, slotSize, mouseX, mouseY)) {
				return new SlotRef(SlotRef.Kind.TEAM, i);
			}
		}
		return null;
	}

	private static boolean isInside(int x, int y, int w, int h, double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(graphics, mouseX, mouseY, partialTick);

		graphics.blitSprite(SPRITE_PANEL_ROOT, rootX, rootY, rootW, rootH);

		renderDetailPanel(graphics, mouseX, mouseY);
		renderGridPanel(graphics, mouseX, mouseY);
		renderTeamPanel(graphics, mouseX, mouseY);

		if (loading) {
			graphics.drawCenteredString(font, Component.translatable("phantasmon.pc.loading"), this.width / 2, this.height - 14, 0xAAAAAA);
		} else if (!statusMessage.getString().isEmpty()) {
			graphics.drawCenteredString(font, statusMessage, this.width / 2, this.height - 14, 0xFFAA55);
		}

		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void fillPanel(GuiGraphics graphics, int x, int y, int w, int h) {
		graphics.blitSprite(SPRITE_PANEL, x, y, w, h);
	}

	private void renderButton(GuiGraphics graphics, int x, int y, int w, int h, Component label, int mouseX, int mouseY) {
		boolean hovered = isInside(x, y, w, h, mouseX, mouseY);
		graphics.blitSprite(hovered ? SPRITE_BUTTON_HOVER : SPRITE_BUTTON, x, y, w, h);
		graphics.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, 0xFFFFFF);
	}

	private void renderDeleteButton(GuiGraphics graphics, int x, int y, int w, int h, Component label, int mouseX, int mouseY) {
		boolean hovered = isInside(x, y, w, h, mouseX, mouseY);
		graphics.blitSprite(hovered ? SPRITE_BUTTON_RED_HOVER : SPRITE_BUTTON_RED, x, y, w, h);
		graphics.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, 0xFFFFFF);
	}

	private static ResourceLocation slotSprite(boolean isSelected, boolean isDragSource, boolean hovered) {
		if (isDragSource) {
			return SPRITE_SLOT_DRAG;
		}
		if (isSelected) {
			return SPRITE_SLOT_SELECTED;
		}
		return hovered ? SPRITE_SLOT_HOVER : SPRITE_SLOT;
	}

	private void renderDetailPanel(GuiGraphics graphics, int mouseX, int mouseY) {
		PokemonDto dto = pokemonAt(selected);

		// Edit/Delete only make sense with a selection (Adrien: they shouldn't even appear otherwise).
		if (dto != null) {
			renderButton(graphics, editButtonX, editButtonY, editButtonW, BUTTON_HEIGHT,
					Component.translatable("phantasmon.pc.edit"), mouseX, mouseY);
			renderDeleteButton(graphics, deleteButtonX, deleteButtonY, deleteButtonW, BUTTON_HEIGHT,
					Component.translatable("phantasmon.pc.delete"), mouseX, mouseY);
		}

		fillPanel(graphics, mergedBoxX, mergedBoxY, mergedBoxW, mergedBoxH);

		if (dto == null) {
			graphics.drawCenteredString(font, Component.translatable("phantasmon.pc.no_selection"),
					mergedBoxX + mergedBoxW / 2, mergedBoxY + mergedBoxH / 2, 0x999999);
			return;
		}

		Species species = PokemonSpecies.INSTANCE.getByName(dto.species());
		Map<String, Object> data = dto.data() != null ? dto.data() : Map.of();

		// ---- Name + type badge(s), above the 3D display ----
		// Species name always capitalized + bold (Adrien: was raw lowercase Cobblemon
		// id text like "dialga"); shiny no longer shown as a "*" suffix here, see the
		// star icon drawn on the screen area below instead.
		String title = capitalize(dto.species()) + (dto.form() != null && !dto.form().isBlank() ? " (" + dto.form() + ")" : "");
		Component titleComponent = Component.literal(title).withStyle(ChatFormatting.BOLD);
		graphics.drawString(font, titleComponent, leftX + 6, nameRowY, 0xFFFFFF);
		if (species != null) {
			// Right-aligned to the column's own right edge (Adrien: was flowing right
			// after the name before) — pack from the right edge inward, secondary type
			// flush against the edge and primary just to its left.
			int rightEdge = leftX + leftW - 6;
			ElementalType secondary = species.getSecondaryType();
			if (secondary != null) {
				int w = typeBadgeWidth(secondary);
				drawTypeBadgeBox(graphics, rightEdge - w, nameRowY - 2, w, secondary);
				rightEdge -= w + 4;
			}
			ElementalType primary = species.getPrimaryType();
			if (primary != null) {
				int w = typeBadgeWidth(primary);
				drawTypeBadgeBox(graphics, rightEdge - w, nameRowY - 2, w, primary);
			}
		}

		// ---- Borderless "screen": icon, level top-left, held item bottom-right ----
		// No scissor here (Adrien: the 3D model must be free to render over the box
		// that contains it, not get cropped by its edges) — unlike the grid/team slots,
		// which still scissor to avoid bleeding into neighboring slots.
		int iconCenterX = screenBoxX + screenBoxW / 2;
		renderIcon(graphics, dto, iconCenterX, iconAnchorY(screenBoxY, screenBoxH, DETAIL_ICON_ANCHOR_RATIO), DETAIL_OUTER_SCALE);

		graphics.drawString(font, Component.translatable("phantasmon.pc.detail.level_badge", dto.level()),
				screenBoxX + 4, screenBoxY + 4, 0xFFFFFF);

		Object heldItem = data.get("heldItem");
		String itemText = heldItem != null ? heldItem.toString() : Component.translatable("phantasmon.pc.detail.no_item").getString();
		ItemStack heldItemStack = heldItem != null ? resolveHeldItemStack(heldItem.toString()) : ItemStack.EMPTY;
		int iconSize = font.lineHeight;
		int itemTextWidth = font.width(itemText);
		int groupWidth = itemTextWidth + (heldItemStack.isEmpty() ? 0 : iconSize + 3);
		int itemY = screenBoxY + screenBoxH - 12;
		int cursorX = screenBoxX + screenBoxW - 4 - groupWidth;
		if (!heldItemStack.isEmpty()) {
			renderItemIcon(graphics, heldItemStack, cursorX, itemY - 1, iconSize);
			cursorX += iconSize + 3;
		}
		graphics.drawString(font, itemText, cursorX, itemY, 0xCCCCCC);

		if (dto.isShiny()) {
			int starSize = 12;
			graphics.blitSprite(SPRITE_STAR, screenBoxX + screenBoxW - starSize - 2, screenBoxY + 2, starSize, starSize);
		}

		// ---- Merged stats+moves box ----
		int pad = 9;
		int textX = mergedBoxX + pad;

		// Fixed-height rows (ability/nature/tera/hidden/ivs/evs) plus however many move
		// lines this Pokémon actually has — computed up front so the top and bottom
		// margins around this block can be made equal (Adrien: they weren't before).
		List<Object> moves = asList(data.get("moves"));
		int moveCount = Math.min(4, moves.size());
		int contentHeight = 10 + 10 + 12 + 13 + 11 + 13 + (moveCount > 0 ? 11 + moveCount * 13 : 0);
		int verticalPad = Math.max(4, (mergedBoxH - contentHeight) / 2);
		int rowY = mergedBoxY + verticalPad;

		// Ability/Nature/Tera/Hidden Power, in that order, one per line (Adrien: no longer 2-per-row).
		drawLine(graphics, textX, rowY, "phantasmon.pc.detail.ability", String.valueOf(dto.ability()));
		rowY += 10;
		drawLine(graphics, textX, rowY, "phantasmon.pc.detail.nature", natureLabel(dto.nature()));
		rowY += 10;

		Object teraTypeRaw = data.get("teraType");
		// Every real Pokémon has an inherent Tera Type even before ever using a Tera
		// Shard — it defaults to one of its own types until explicitly changed.
		ElementalType teraType = teraTypeRaw != null ? safeGetType(teraTypeRaw.toString())
				: (species != null ? species.getPrimaryType() : null);
		drawTypeLine(graphics, textX, rowY, "phantasmon.pc.detail.tera", teraType);
		rowY += 12;
		String hiddenPowerTypeId = HiddenPowerCalculator.type(asMap(data.get("ivs")));
		drawTypeLine(graphics, textX, rowY, "phantasmon.pc.detail.hidden_power", safeGetType(hiddenPowerTypeId));
		rowY += 13;

		// IVs/EVs: the "IVs"/"EVs" label sits on the same line as the values (Adrien: was its own line before).
		Map<String, Object> ivs = asMap(data.get("ivs"));
		drawStatLine(graphics, textX, rowY, "phantasmon.pc.detail.ivs",
				isFullIvs(ivs) ? Component.translatable("phantasmon.pc.detail.full_ivs").getString() : statLine(ivs), 0x88CCFF);
		rowY += 11;

		Map<String, Object> evs = asMap(data.get("evs"));
		String evText = nonZeroStatLine(evs);
		drawStatLine(graphics, textX, rowY, "phantasmon.pc.detail.evs",
				evText != null ? evText : Component.translatable("phantasmon.pc.detail.no_evs").getString(), 0xFFCC88);
		rowY += 13;

		if (!moves.isEmpty()) {
			graphics.drawString(font, Component.translatable("phantasmon.pc.detail.moves"), textX, rowY, SECTION_HEADER_COLOR);
			rowY += 11;
			for (Object moveObj : moves) {
				if (rowY > mergedBoxY + mergedBoxH - 11) {
					break;
				}
				String moveId = moveObj.toString();
				MoveTemplate move = Moves.INSTANCE.getByName(moveId);
				if (move != null) {
					graphics.drawString(font, move.getDisplayName(), textX, rowY, 0xCCCCCC);
					renderTypeBadgeRightAligned(graphics, mergedBoxX + mergedBoxW - pad, rowY - 1, move.getElementalType());
				} else {
					graphics.drawString(font, "- " + moveId, textX, rowY, 0xCCCCCC);
				}
				rowY += 13;
			}
		}
	}

	private void drawLine(GuiGraphics graphics, int x, int y, String key, String value) {
		graphics.drawString(font, Component.translatable(key, value), x, y, 0xCCCCCC);
	}

	/** Label and value on the same line (used for IVs/EVs — Adrien: the label used to be its own line above the value). */
	private void drawStatLine(GuiGraphics graphics, int x, int y, String labelKey, String value, int valueColor) {
		Component label = Component.translatable(labelKey);
		graphics.drawString(font, label, x, y, SECTION_HEADER_COLOR);
		graphics.drawString(font, value, x + font.width(label) + 4, y, valueColor);
	}

	/** e.g. "Adamant (+Atk / -SpA)" — no suffix at all for one of the 5 neutral natures. */
	private static String natureLabel(String natureId) {
		NatureModifiers.Modifier modifier = NatureModifiers.get(natureId);
		String display = natureId == null ? "?" : capitalize(natureId);
		return modifier == null ? display : display + " (+" + modifier.boosted() + " / -" + modifier.reduced() + ")";
	}

	private static String capitalize(String value) {
		return value == null || value.isEmpty() ? value : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
	}

	/** A label followed by a real Cobblemon type badge (used for Tera type / Hidden Power) instead of raw text. */
	private void drawTypeLine(GuiGraphics graphics, int x, int y, String labelKey, ElementalType type) {
		Component label = Component.translatable(labelKey);
		graphics.drawString(font, label, x, y, 0xCCCCCC);
		int afterLabelX = x + font.width(label) + 4;
		if (type != null) {
			renderTypeBadge(graphics, afterLabelX, y - 2, type);
		} else {
			graphics.drawString(font, "?", afterLabelX, y, 0xCCCCCC);
		}
	}

	private static ElementalType safeGetType(String typeId) {
		if (typeId == null) {
			return null;
		}
		try {
			return ElementalTypes.INSTANCE.get(typeId.toLowerCase(Locale.ROOT));
		} catch (Exception ex) {
			return null;
		}
	}

	/** Real species type badge (Cobblemon's own {@code ElementalType} colors/translated name), not a placeholder. Returns the x to place the next badge at. */
	private int renderTypeBadge(GuiGraphics graphics, int x, int y, ElementalType type) {
		if (type == null) {
			return x;
		}
		int w = typeBadgeWidth(type);
		drawTypeBadgeBox(graphics, x, y, w, type);
		return x + w + 4;
	}

	/** Same badge, but flush against {@code rightEdgeX} instead of flowing left-to-right from a start x (used for move types). */
	private void renderTypeBadgeRightAligned(GuiGraphics graphics, int rightEdgeX, int y, ElementalType type) {
		if (type == null) {
			return;
		}
		int w = typeBadgeWidth(type);
		drawTypeBadgeBox(graphics, rightEdgeX - w, y, w, type);
	}

	private int typeBadgeWidth(ElementalType type) {
		return font.width(type.getDisplayName().getString().toUpperCase(Locale.ROOT)) + 8;
	}

	private void drawTypeBadgeBox(GuiGraphics graphics, int x, int y, int w, ElementalType type) {
		String label = type.getDisplayName().getString().toUpperCase(Locale.ROOT);
		int h = 12;
		int bg = type.getPrimaryColor() | 0xFF000000;
		graphics.fill(x, y, x + w, y + h, bg);
		graphics.renderOutline(x, y, w, h, 0x55000000);
		graphics.drawString(font, label, x + 4, y + 2, readableTextColor(bg), false);
	}

	private static int readableTextColor(int argbColor) {
		int r = (argbColor >> 16) & 0xFF;
		int g = (argbColor >> 8) & 0xFF;
		int b = argbColor & 0xFF;
		double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
		return luminance > 0.6 ? 0x000000 : 0xFFFFFF;
	}

	private void renderGridPanel(GuiGraphics graphics, int mouseX, int mouseY) {
		fillPanel(graphics, gridX, gridY, gridW, gridH);
		graphics.drawCenteredString(font, Component.translatable("phantasmon.pc.box_label", currentBox, BOX_COUNT),
				gridX + gridW / 2, boxNavY + 4, 0xFFFFFF);
		renderButton(graphics, prevBoxX, boxNavY, NAV_BUTTON_SIZE, NAV_BUTTON_SIZE, Component.literal("<"), mouseX, mouseY);
		renderButton(graphics, nextBoxX, boxNavY, NAV_BUTTON_SIZE, NAV_BUTTON_SIZE, Component.literal(">"), mouseX, mouseY);

		int originX = gridX + GRID_EDGE_PAD;
		int originY = gridY + GRID_HEADER_HEIGHT + GRID_EDGE_PAD;
		for (int i = 0; i < SLOTS_PER_BOX; i++) {
			int col = i % BOX_COLUMNS;
			int row = i / BOX_COLUMNS;
			int sx = originX + col * (slotSize + SLOT_GAP);
			int sy = originY + row * (slotSize + SLOT_GAP);
			SlotRef ref = new SlotRef(SlotRef.Kind.PC, i);
			renderSlot(graphics, sx, sy, slotSize, slotSize, ref, boxSlots[i], mouseX, mouseY, SLOT_OUTER_SCALE);
		}

		renderButton(graphics, importButtonX, importButtonY, importButtonW, BUTTON_HEIGHT,
				Component.translatable("phantasmon.pc.import_clipboard"), mouseX, mouseY);
	}

	private void renderTeamPanel(GuiGraphics graphics, int mouseX, int mouseY) {
		fillPanel(graphics, teamBoxX, teamBoxY, teamBoxW, teamBoxH);
		graphics.drawCenteredString(font, Component.translatable("phantasmon.pc.team_label"),
				teamBoxX + teamBoxW / 2, teamBoxY + 6, 0xFFFFFF);

		int teamPad = 8;
		int rowX = teamBoxX + teamPad;
		int rowW = teamBoxW - 2 * teamPad;
		int originY = teamBoxY + TEAM_HEADER_HEIGHT + teamPad;
		for (int i = 0; i < TEAM_SIZE; i++) {
			int sy = originY + i * (slotSize + SLOT_GAP);
			SlotRef ref = new SlotRef(SlotRef.Kind.TEAM, i);
			PokemonDto dto = teamSlots[i];
			boolean isSelected = ref.equals(selected);
			boolean isDragSrc = ref.equals(dragSource);
			// Hit-test area stays the full row width (easier to click), but the drawn slot
			// box itself is exactly slotSize×slotSize — matching the PC grid's own square
			// slots exactly, per Adrien's feedback that they didn't match before.
			boolean hovered = isInside(rowX, sy, rowW, slotSize, mouseX, mouseY);
			graphics.blitSprite(slotSprite(isSelected, isDragSrc, hovered), rowX, sy, slotSize, slotSize);
			if (dto != null) {
				graphics.enableScissor(rowX, sy, rowX + slotSize, sy + slotSize);
				renderIcon(graphics, dto, rowX + slotSize / 2, iconAnchorY(sy, slotSize, SLOT_ICON_ANCHOR_RATIO), SLOT_OUTER_SCALE);
				graphics.disableScissor();
			}
		}
	}

	private void renderSlot(GuiGraphics graphics, int sx, int sy, int w, int h, SlotRef ref, PokemonDto dto,
			int mouseX, int mouseY, float outerScale) {
		boolean isSelected = ref.equals(selected);
		boolean isDragSrc = ref.equals(dragSource);
		boolean hovered = isInside(sx, sy, w, h, mouseX, mouseY);
		graphics.blitSprite(slotSprite(isSelected, isDragSrc, hovered), sx, sy, w, h);
		if (dto != null) {
			graphics.enableScissor(sx, sy, sx + w, sy + h);
			renderIcon(graphics, dto, sx + w / 2, iconAnchorY(sy, h, SLOT_ICON_ANCHOR_RATIO), outerScale);
			graphics.disableScissor();
		}
	}

	/**
	 * Draws a live 3D miniature via Cobblemon's {@code drawProfilePokemon}. That
	 * Kotlin function has 9 defaulted parameters (mask {@code 65416}, verified
	 * by decompiling Cobblemon's own {@code StorageSlot.renderSlot} — see class
	 * javadoc). The generated {@code drawProfilePokemon$default} bridge that
	 * lets a caller omit them is marked {@code @JvmSynthetic}, which the Java
	 * compiler enforces by hiding it from ordinary method calls even though
	 * it's a public bytecode-level method — so it's invoked here via
	 * reflection instead, which isn't subject to that source-level filter.
	 *
	 * <p>{@code outerScale} replicates Cobblemon's own {@code pose.scale(2.5f,
	 * 2.5f, 1f)} wrapping call (see {@link #COBBLEMON_OUTER_SCALE} javadoc for
	 * why this — not the inner {@code drawProfilePokemon} scale argument, and
	 * not the translate z — turned out to be what actually keeps the model
	 * centered).
	 */
	private static int iconAnchorY(int top, int height, float ratio) {
		return top + Math.round(height * ratio);
	}

	/** Resolves a held item id (e.g. "leftovers") to its real Cobblemon-registered {@link ItemStack} for icon rendering; {@link ItemStack#EMPTY} if unresolvable (matches how species/moves are resolved elsewhere in this class — real Cobblemon data, not a placeholder). */
	private static ItemStack resolveHeldItemStack(String heldItemId) {
		if (heldItemId == null || heldItemId.isBlank()) {
			return ItemStack.EMPTY;
		}
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", heldItemId.toLowerCase(Locale.ROOT)));
		return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
	}

	/** Vanilla item icons always render at a fixed 16×16 — scaled here via pose to match the requested pixel size (Adrien: same height as the item name's own font). */
	private static void renderItemIcon(GuiGraphics graphics, ItemStack stack, int x, int y, int size) {
		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(x, y, 0);
		float scale = size / 16f;
		poseStack.scale(scale, scale, 1f);
		graphics.renderItem(stack, 0, 0);
		poseStack.popPose();
	}

	private static void renderIcon(GuiGraphics graphics, PokemonDto dto, int centerX, int centerY, float outerScale) {
		Species species = PokemonSpecies.INSTANCE.getByName(dto.species());
		if (species == null) {
			return;
		}
		Set<String> aspects = new HashSet<>();
		if (dto.form() != null && !dto.form().isBlank()) {
			aspects.add(dto.form().toLowerCase(Locale.ROOT));
		}
		if (dto.isShiny()) {
			aspects.add("shiny");
		}
		RenderablePokemon renderable = new RenderablePokemon(species, aspects, ItemStack.EMPTY);

		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		try {
			poseStack.translate(centerX, centerY, 0);
			poseStack.scale(outerScale, outerScale, 1f);
			Quaternionf rotation = QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(), new Vector3f(13.0f, 35.0f, 0f));
			drawProfilePokemonDefault().invoke(null,
					renderable,
					poseStack,
					rotation,
					null,
					new FloatingState(),
					0f,
					ICON_INNER_SCALE,
					null,
					false,
					0f, 0f, 0f, 0f, 0f, 0f,
					0,
					65416,
					null);
		} catch (Exception ex) {
			// Anything here (reflection failure, a bad argument, a Cobblemon-side
			// rendering error) must not propagate: an unbalanced pushPose/popPose
			// would corrupt every remaining draw call for the rest of this frame,
			// potentially blanking the whole screen instead of just this one icon.
			LOG.warn("Failed to render Pokémon icon for {} (Cobblemon API mismatch?)", dto.species(), ex);
		} finally {
			poseStack.popPose();
		}
	}

	private static java.lang.reflect.Method drawProfilePokemonDefault() {
		java.lang.reflect.Method cached = drawProfilePokemonDefaultMethod;
		if (cached != null) {
			return cached;
		}
		for (java.lang.reflect.Method candidate : PokemonGuiUtilsKt.class.getMethods()) {
			if (candidate.getName().equals("drawProfilePokemon$default")
					&& candidate.getParameterCount() == 18
					&& candidate.getParameterTypes()[0] == RenderablePokemon.class) {
				candidate.setAccessible(true);
				drawProfilePokemonDefaultMethod = candidate;
				return candidate;
			}
		}
		throw new IllegalStateException("Cobblemon's PokemonGuiUtilsKt.drawProfilePokemon$default not found (API changed?)");
	}

	private static String statLine(Map<String, Object> stats) {
		return "HP:" + asInt(stats.get("hp")) + " ATK:" + asInt(stats.get("atk")) + " DEF:" + asInt(stats.get("def"))
				+ " SPA:" + asInt(stats.get("spa")) + " SPD:" + asInt(stats.get("spd")) + " SPE:" + asInt(stats.get("spe"));
	}

	private static boolean isFullIvs(Map<String, Object> ivs) {
		return asInt(ivs.get("hp")) == 31 && asInt(ivs.get("atk")) == 31 && asInt(ivs.get("def")) == 31
				&& asInt(ivs.get("spa")) == 31 && asInt(ivs.get("spd")) == 31 && asInt(ivs.get("spe")) == 31;
	}

	/** Same shape as {@link #statLine} but skips any stat at 0 — returns null if every stat is 0. */
	private static String nonZeroStatLine(Map<String, Object> stats) {
		StringBuilder sb = new StringBuilder();
		appendIfNonZero(sb, "HP", asInt(stats.get("hp")));
		appendIfNonZero(sb, "ATK", asInt(stats.get("atk")));
		appendIfNonZero(sb, "DEF", asInt(stats.get("def")));
		appendIfNonZero(sb, "SPA", asInt(stats.get("spa")));
		appendIfNonZero(sb, "SPD", asInt(stats.get("spd")));
		appendIfNonZero(sb, "SPE", asInt(stats.get("spe")));
		return sb.isEmpty() ? null : sb.toString();
	}

	private static void appendIfNonZero(StringBuilder sb, String label, int value) {
		if (value != 0) {
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(label).append(':').append(value);
		}
	}

	private static int asInt(Object value) {
		return value instanceof Number number ? number.intValue() : 0;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object value) {
		return value instanceof Map ? (Map<String, Object>) value : Map.of();
	}

	@SuppressWarnings("unchecked")
	private static List<Object> asList(Object value) {
		return value instanceof List ? (List<Object>) value : List.of();
	}

	private static Component errorMessage(Throwable throwable) {
		Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
		String key = cause instanceof BackendApiException apiException
				? BackendErrorMessages.translationKey(apiException.errorCode())
				: "phantasmon.error.network";
		return Component.translatable(key);
	}

	private record SlotRef(Kind kind, int index) {
		enum Kind { PC, TEAM }
	}
}
