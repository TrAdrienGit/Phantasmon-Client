package com.mystaria.phantasmon.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;

import org.lwjgl.glfw.GLFW;

import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.pokemon.Species;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.network.BackendApiException;
import com.mystaria.phantasmon.client.network.BackendErrorMessages;
import com.mystaria.phantasmon.client.pokemon.CobblemonHeldItems;
import com.mystaria.phantasmon.client.pokemon.NatureModifiers;
import com.mystaria.phantasmon.client.pokemon.PokemonClient;
import com.mystaria.phantasmon.client.pokemon.PokemonDto;
import com.mystaria.phantasmon.client.pokemon.PokemonUpdateRequestDto;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownImportMapper;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownParseException;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownParser;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownPokemon;

/**
 * The full Pokémon editor (Adrien: 2026-09-27, second HUD pass). Uses real
 * vanilla widgets ({@link EditBox}, {@link Button}) rather than manual
 * hit-testing — appropriate here since the form has many small independent
 * fields, unlike {@link PhantasmonPcScreen}'s drag&drop grid.
 *
 * <p><b>Ability/Item/Moves are pickers, not free text</b> (Adrien 2026-09-27:
 * a player can't be expected to know a raw Cobblemon id like
 * {@code "stance_change"} or {@code "leftovers"} by heart). Ability lists only
 * the species' own real learnable abilities
 * ({@link Species#getAbilities()} — a {@code PotentialAbility} pool, deduped
 * since the same ability can appear at multiple priorities e.g. normal +
 * hidden). Item is a searchable picker over only the Cobblemon items that
 * actually do something in battle — see {@link CobblemonHeldItems} — not every
 * {@code cobblemon:}-namespaced item (Adrien 2026-09-29: that let non-battle
 * utility items through). Move is a searchable picker over every move in the
 * game ({@link Moves#names()}). Every dropdown row shows each entry's real,
 * localized display name (Cobblemon's own translation keys, same as vanilla
 * item names — Adrien 2026-09-29: raw English ids aren't practical for
 * non-English players) while still storing the bare id underneath. A move
 * already chosen in one of the *other* 3 move slots is excluded from that
 * slot's own list (no duplicate moves).
 *
 * <p><b>Re-skinned to match the PC screen's visual language</b>: the outer
 * panel and every {@link Button} use the same nine-slice sprites as
 * {@link PhantasmonPcScreen} via a small {@link SpectralButton} subclass that
 * overrides {@code renderWidget} — vanilla {@link Button} has no public way
 * to swap its texture. {@link EditBox} (nickname/level/IVs/EVs) keeps its
 * default vanilla look.
 *
 * <p><b>Two-column layout</b>: left column is identity/behavior fields, right
 * column is IVs/EVs/moves/import. Every single-line field in the left column
 * has its label on the same row as its input; the label column width is
 * measured from the longest translated label.
 *
 * <p><b>Dropdowns</b> (Nature/Tera/Ability/Item/Move) are a small custom popup
 * list ({@link #openDropdown}) since vanilla Minecraft has no built-in
 * dropdown/combo-box widget. Always drawn last, after every other
 * panel/label/widget — vanilla has no z-order concept, draw order *is*
 * z-order. Covered widgets are skipped entirely during their own render pass
 * (not just painted over) since a plain draw-order + flush() turned out not
 * to be enough to keep button/field *text* from bleeding through on top of
 * the dropdown's own text (confirmed live by Adrien, see
 * {@link #coveredByDropdown}).
 *
 * <p>Saving requires the backend to accept {@code nature}/{@code ability}/
 * {@code is_shiny} on {@code PATCH /pokemon/{uuid}} — see
 * {@code PokemonUpdateRequest} on the backend.
 *
 * <p>Hidden Power is deliberately <b>not</b> an editable field here — it's
 * always derived from the IVs
 * ({@link com.mystaria.phantasmon.client.pokemon.HiddenPowerCalculator}),
 * exactly like real Pokémon games and Pokémon Showdown itself.
 */
public final class PhantasmonPcEditScreen extends Screen {

	private static final String[] STATS = { "hp", "atk", "def", "spa", "spd", "spe" };
	private static final String[] STAT_LABELS = { "HP", "ATK", "DEF", "SPA", "SPD", "SPE" };
	private static final String[] NATURES = {
			"hardy", "lonely", "brave", "adamant", "naughty",
			"bold", "docile", "relaxed", "impish", "lax",
			"timid", "hasty", "serious", "jolly", "naive",
			"modest", "mild", "quiet", "bashful", "rash",
			"calm", "gentle", "sassy", "careful", "quirky"
	};
	private static final List<ElementalType> TERA_OPTIONS = buildTeraOptions();
	/** Battle held items only (see {@link CobblemonHeldItems}), sorted once and cached — never every {@code cobblemon:}-namespaced item, which would include non-battle utility items/blocks. */
	private static List<String> allHeldItemIds;
	/** Every move in the game ({@link Moves#names()}), sorted once and cached the same way. */
	private static List<String> allMoveIdsSorted;

	private static final int PANEL_BORDER = 0xFF2FB7C9;
	private static final int LABEL_COLOR = 0x9FD9E6;
	private static final int DROPDOWN_BG = 0xFF0B0F14;
	private static final int DROPDOWN_ROW_HOVER = 0xFF224466;
	private static final int DROPDOWN_ROW_SELECTED = 0xFF17303C;
	private static final int DROPDOWN_ROW_H = 14;
	private static final int DROPDOWN_MAX_VISIBLE = 8;

	/** Consistent spacing used throughout this form. */
	private static final int ROW_H = 22;
	private static final int FIELD_H = 16;
	private static final int LABEL_GAP = 6;

	private enum DropdownKind { NONE, NATURE, TERA, ABILITY, ITEM, MOVE }

	private static boolean isSearchable(DropdownKind kind) {
		return kind == DropdownKind.ITEM || kind == DropdownKind.MOVE;
	}

	private final PokemonClient pokemonClient;
	private final AuthSession session;
	private final PokemonDto original;
	private final PhantasmonPcScreen parent;

	private EditBox nicknameBox;
	private EditBox levelBox;
	private final EditBox[] ivBoxes = new EditBox[6];
	private final EditBox[] evBoxes = new EditBox[6];

	private int natureIndex;
	private ElementalType teraType;
	private boolean shiny;
	private String abilityId;
	/** Empty string = no item (matches the read-only detail panel's "no item" convention). */
	private String heldItemId = "";
	/** Empty entry = empty move slot. */
	private final String[] moveIds = new String[4];
	/** This species' own real learnable abilities (deduped, current value always included even if species lookup fails). */
	private List<String> abilityOptions = List.of();
	/** id → localized display name, built alongside {@link #abilityOptions} in {@link #init()} (the ability's {@code AbilityTemplate} isn't kept around, only its id, so the name is captured once up front). */
	private final Map<String, String> abilityDisplayNames = new HashMap<>();

	private Button natureValueButton;
	private Button teraValueButton;
	private Button shinyButton;
	private Button abilityValueButton;
	private Button itemValueButton;
	private final Button[] moveValueButtons = new Button[4];

	private DropdownKind openDropdown = DropdownKind.NONE;
	private int dropdownX, dropdownY, dropdownW;
	private int dropdownScroll;
	/** Which of the 4 move slots is being edited, only meaningful while {@code openDropdown == MOVE}. */
	private int activeMoveSlot = -1;
	private final StringBuilder searchQuery = new StringBuilder();

	private boolean saving;
	private Component statusMessage = Component.empty();

	private int panelX, panelY, panelW, panelH;
	private final List<LabelSpot> labels = new ArrayList<>();
	/**
	 * Widgets rendered manually, in this exact order, instead of via
	 * {@code addRenderableWidget} — a plain draw-order + {@code flush()} wasn't
	 * enough to keep the dropdown above button/field *text* specifically (only
	 * their background sprite correctly got covered). Registering widgets via
	 * {@link #addWidget} instead (input/focus/narration only, no auto-render)
	 * and rendering them ourselves lets {@link #coveredByDropdown} skip a
	 * widget's render call entirely when the open dropdown would cover it —
	 * neither its background nor its text can possibly appear then, regardless
	 * of any GuiGraphics batching quirk.
	 */
	private final List<Renderable> orderedWidgets = new ArrayList<>();

	private <T extends GuiEventListener & Renderable & NarratableEntry> T addTracked(T widget) {
		addWidget(widget);
		orderedWidgets.add(widget);
		return widget;
	}

	private record LabelSpot(Component text, int x, int y) {
	}

	public PhantasmonPcEditScreen(PokemonClient pokemonClient, AuthSession session, PokemonDto pokemon, PhantasmonPcScreen parent) {
		super(Component.translatable("phantasmon.pc.edit.title", pokemon.species()));
		this.pokemonClient = pokemonClient;
		this.session = session;
		this.original = pokemon;
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}

	@Override
	protected void init() {
		labels.clear();
		orderedWidgets.clear();
		openDropdown = DropdownKind.NONE;
		searchQuery.setLength(0);
		Map<String, Object> data = original.data() != null ? original.data() : Map.of();
		shiny = original.isShiny();
		natureIndex = Math.max(0, indexOf(NATURES, original.nature()));
		Object teraRaw = data.get("teraType");
		teraType = teraRaw != null ? safeGetType(teraRaw.toString()) : null;
		abilityId = original.ability();
		heldItemId = stringOf(data.get("heldItem"));
		List<Object> initialMoves = asList(data.get("moves"));
		for (int i = 0; i < 4; i++) {
			moveIds[i] = i < initialMoves.size() ? String.valueOf(initialMoves.get(i)) : "";
		}

		// Real learnable abilities for this exact species (not a free-text field
		// anymore) — deduped since the same ability can appear at more than one
		// priority (e.g. a normal ability slot and a hidden-ability slot). Display
		// names are captured here too since AbilityTemplate isn't kept around.
		Species species = PokemonSpecies.INSTANCE.getByName(original.species());
		LinkedHashSet<String> abilitySet = new LinkedHashSet<>();
		abilityDisplayNames.clear();
		if (species != null) {
			for (PotentialAbility potential : species.getAbilities()) {
				String id = potential.getTemplate().getName();
				abilitySet.add(id);
				// AbilityTemplate.getDisplayName() returns the raw translation key (e.g.
				// "cobblemon.ability.stance_change"), not resolved text — confirmed by
				// Adrien seeing that literal string in the dropdown/button.
				abilityDisplayNames.put(id, Component.translatable(potential.getTemplate().getDisplayName()).getString());
			}
		}
		if (!abilitySet.contains(abilityId)) {
			// Always keep the current value selectable even if the species lookup
			// failed or doesn't (yet) know about it.
			abilitySet.add(abilityId);
		}
		abilityOptions = new ArrayList<>(abilitySet);

		panelW = Math.min(440, this.width - 20);
		panelX = (this.width - panelW) / 2;
		panelY = Math.max(10, (this.height - 240) / 2);

		int contentX = panelX + 14;
		int contentW = panelW - 28;
		int colGap = 12;
		int colW = (contentW - colGap) / 2;
		int leftX = contentX;
		int rightX = contentX + colW + colGap;
		int top = panelY + 24;

		// Label column width measured from the longest of the inline labels, so the
		// fields all start at the same x regardless of which language is active.
		int labelW = 0;
		for (String key : new String[] { "phantasmon.pc.edit.nickname", "phantasmon.pc.edit.level",
				"phantasmon.pc.edit.ability", "phantasmon.pc.edit.item", "phantasmon.pc.edit.nature", "phantasmon.pc.edit.tera" }) {
			labelW = Math.max(labelW, font.width(Component.translatable(key)));
		}
		int fieldX = leftX + labelW + LABEL_GAP;
		int fieldW = leftX + colW - fieldX;
		int wideDropdownW = Math.min(panelW - (fieldX - panelX) - 10, 220);

		// ---- Left column: identity & behavior — label and field share one row throughout ----
		int y = top;
		labels.add(new LabelSpot(Component.translatable("phantasmon.pc.edit.nickname"), leftX, y + 4));
		nicknameBox = addField(fieldX, y, fieldW, stringOf(data.get("nickname")));
		y += ROW_H;

		labels.add(new LabelSpot(Component.translatable("phantasmon.pc.edit.level"), leftX, y + 4));
		levelBox = addField(fieldX, y, fieldW, String.valueOf(original.level()));
		levelBox.setFilter(PhantasmonPcEditScreen::isDigits);
		y += ROW_H;

		labels.add(new LabelSpot(Component.translatable("phantasmon.pc.edit.ability"), leftX, y + 4));
		int abilityDropdownY = y + FIELD_H;
		abilityValueButton = addTracked(new SpectralButton(fieldX, y, fieldW, FIELD_H, Component.empty(),
				b -> toggleDropdown(DropdownKind.ABILITY, fieldX, abilityDropdownY, fieldW, -1)));
		updateAbilityLabel();
		y += ROW_H;

		labels.add(new LabelSpot(Component.translatable("phantasmon.pc.edit.item"), leftX, y + 4));
		int itemDropdownY = y + FIELD_H;
		itemValueButton = addTracked(new SpectralButton(fieldX, y, fieldW, FIELD_H, Component.empty(),
				b -> toggleDropdown(DropdownKind.ITEM, fieldX, itemDropdownY, wideDropdownW, -1)));
		updateItemLabel();
		y += ROW_H;

		labels.add(new LabelSpot(Component.translatable("phantasmon.pc.edit.nature"), leftX, y + 4));
		int natureDropdownY = y + FIELD_H;
		// Wider than the trigger button itself — "Adamant (+Atk / -SpA)"-style entries
		// don't fit in the narrow field column, so the dropdown list overhangs it a bit.
		int natureDropdownW = Math.min(panelW - (fieldX - panelX) - 10, 190);
		natureValueButton = addTracked(new SpectralButton(fieldX, y, fieldW, FIELD_H, Component.empty(),
				b -> toggleDropdown(DropdownKind.NATURE, fieldX, natureDropdownY, natureDropdownW, -1)));
		updateNatureLabel();
		y += ROW_H;

		shinyButton = addTracked(new SpectralButton(leftX, y, colW, FIELD_H, Component.empty(), b -> toggleShiny()));
		updateShinyLabel();
		y += ROW_H;

		labels.add(new LabelSpot(Component.translatable("phantasmon.pc.edit.tera"), leftX, y + 4));
		int teraDropdownY = y + FIELD_H;
		teraValueButton = addTracked(new SpectralButton(fieldX, y, fieldW, FIELD_H, Component.empty(),
				b -> toggleDropdown(DropdownKind.TERA, fieldX, teraDropdownY, fieldW, -1)));
		updateTeraLabel();
		y += ROW_H;
		int leftBottom = y;

		// ---- Right column: IVs / EVs / moves / import ----
		// Each block's next label is positioned from the *actual* box height (FIELD_H)
		// rather than a flat ROW_H advance, so a label never overlaps the previous
		// row's boxes regardless of exact spacing constants.
		int labelToBoxGap = 10;
		int boxToNextLabelGap = 6;
		y = top;
		labels.add(new LabelSpot(Component.translatable("phantasmon.pc.edit.ivs"), rightX, y));
		int statLabelY = y + 10;
		int ivBoxY = statLabelY + labelToBoxGap;
		int statColW = colW / 6;
		Map<String, Object> ivs = asMap(data.get("ivs"));
		for (int i = 0; i < 6; i++) {
			labels.add(new LabelSpot(Component.literal(STAT_LABELS[i]), rightX + i * statColW, statLabelY));
			ivBoxes[i] = addField(rightX + i * statColW, ivBoxY, statColW - 3, String.valueOf(statOrDefault(ivs, STATS[i], 31)));
			ivBoxes[i].setFilter(PhantasmonPcEditScreen::isDigits);
		}
		y = ivBoxY + FIELD_H + boxToNextLabelGap;

		labels.add(new LabelSpot(Component.translatable("phantasmon.pc.edit.evs"), rightX, y));
		int evBoxY = y + labelToBoxGap;
		Map<String, Object> evs = asMap(data.get("evs"));
		for (int i = 0; i < 6; i++) {
			evBoxes[i] = addField(rightX + i * statColW, evBoxY, statColW - 3, String.valueOf(statOrDefault(evs, STATS[i], 0)));
			evBoxes[i].setFilter(PhantasmonPcEditScreen::isDigits);
		}
		y = evBoxY + FIELD_H + boxToNextLabelGap;

		labels.add(new LabelSpot(Component.translatable("phantasmon.pc.edit.moves"), rightX, y));
		int moveBoxY = y + labelToBoxGap;
		int moveColW = (colW - 6) / 2;
		int moveRowGap = 4;
		int moveDropdownW = Math.min(panelW - 20, 240);
		for (int i = 0; i < 4; i++) {
			int col = i % 2;
			int row = i / 2;
			int mx = rightX + col * (moveColW + 6);
			int my = moveBoxY + row * (FIELD_H + moveRowGap);
			int slot = i;
			int moveDropdownY = my + FIELD_H;
			moveValueButtons[i] = addTracked(new SpectralButton(mx, my, moveColW, FIELD_H, Component.empty(),
					b -> toggleDropdown(DropdownKind.MOVE, mx, moveDropdownY, moveDropdownW, slot)));
			updateMoveLabel(i);
		}
		y = moveBoxY + 2 * FIELD_H + moveRowGap + boxToNextLabelGap;

		addTracked(new SpectralButton(rightX, y, colW, FIELD_H,
				Component.translatable("phantasmon.pc.edit.import_clipboard"), b -> importFromClipboard()));
		y += ROW_H;
		int rightBottom = y;

		int bottom = Math.max(leftBottom, rightBottom) + 6;
		int buttonW = (contentW - 8) / 2;
		addTracked(new SpectralButton(contentX, bottom, buttonW, 18,
				Component.translatable("phantasmon.pc.edit.save"), b -> save()));
		addTracked(new SpectralButton(contentX + buttonW + 8, bottom, buttonW, 18,
				Component.translatable("phantasmon.pc.edit.cancel"), b -> onClose()));
		bottom += 22;

		panelH = bottom - panelY + 12;
	}

	private EditBox addField(int x, int y, int w, String value) {
		EditBox box = new EditBox(font, x, y, w, FIELD_H, Component.empty());
		box.setMaxLength(64);
		box.setValue(value == null ? "" : value);
		return addTracked(box);
	}

	private void toggleDropdown(DropdownKind kind, int x, int y, int w, int moveSlot) {
		if (openDropdown == kind && activeMoveSlot == moveSlot) {
			openDropdown = DropdownKind.NONE;
			return;
		}
		openDropdown = kind;
		activeMoveSlot = moveSlot;
		dropdownX = x;
		dropdownY = y;
		dropdownW = w;
		dropdownScroll = 0;
		searchQuery.setLength(0);
	}

	/** The underlying ids/values (not display strings) for the currently open dropdown, in display order. Nature/Tera keep their own existing raw sources (a String array / the ElementalType list); this only covers the 3 new pickers. */
	private List<String> rawOptions() {
		return switch (openDropdown) {
			case ABILITY -> abilityOptions;
			case ITEM -> itemChoices();
			case MOVE -> moveChoices();
			case NATURE, TERA, NONE -> List.of();
		};
	}

	private List<String> itemChoices() {
		List<String> result = new ArrayList<>();
		result.add("");
		for (String id : allHeldItemIds()) {
			if (matchesSearch(id, itemDisplayName(id))) {
				result.add(id);
			}
		}
		return result;
	}

	private List<String> moveChoices() {
		List<String> result = new ArrayList<>();
		result.add("");
		for (String id : allMoveIdsSorted()) {
			boolean usedByAnotherSlot = false;
			for (int i = 0; i < 4; i++) {
				if (i != activeMoveSlot && id.equalsIgnoreCase(moveIds[i])) {
					usedByAnotherSlot = true;
					break;
				}
			}
			if (usedByAnotherSlot) {
				continue;
			}
			if (matchesSearch(id, moveDisplayName(id))) {
				result.add(id);
			}
		}
		return result;
	}

	/**
	 * Matches the current {@link #searchQuery} against an id and its localized display name,
	 * word-by-word and in any order — e.g. typing "Booster Energy" must still find an item whose
	 * real name is "Energy Booster" (Adrien: 2026-09-29, a plain whole-string
	 * {@code String.contains} required typing the name in the exact order it's actually written,
	 * which isn't how people search). Every whitespace-separated token in the query must appear
	 * *somewhere* in "id + display name" for the entry to match; an empty query matches everything.
	 */
	private boolean matchesSearch(String id, String displayName) {
		String query = searchQuery.toString().trim();
		if (query.isEmpty()) {
			return true;
		}
		String haystack = (id + " " + displayName).toLowerCase(Locale.ROOT);
		for (String token : query.toLowerCase(Locale.ROOT).split("\\s+")) {
			if (!haystack.contains(token)) {
				return false;
			}
		}
		return true;
	}

	/** Battle held items only — see {@link CobblemonHeldItems} for why this isn't every {@code cobblemon:}-namespaced item. */
	private static List<String> allHeldItemIds() {
		if (allHeldItemIds == null) {
			List<String> ids = new ArrayList<>(CobblemonHeldItems.byId().keySet());
			ids.sort(String::compareTo);
			allHeldItemIds = ids;
		}
		return allHeldItemIds;
	}

	private static List<String> allMoveIdsSorted() {
		if (allMoveIdsSorted == null) {
			List<String> ids = new ArrayList<>(Moves.names());
			ids.sort(String::compareTo);
			allMoveIdsSorted = ids;
		}
		return allMoveIdsSorted;
	}

	/** Localized item name (respects the client's own language, falling back to en_us like any other Minecraft item) instead of the raw id — a French player shouldn't have to read "leftovers" (Adrien: 2026-09-29). */
	private static String itemDisplayName(String id) {
		if (id.isEmpty()) {
			return Component.translatable("phantasmon.pc.detail.no_item").getString();
		}
		Item item = CobblemonHeldItems.byId().get(id);
		return item != null ? item.getDescription().getString() : capitalize(id);
	}

	/** Localized move name via Cobblemon's own {@link com.cobblemon.mod.common.api.moves.MoveTemplate#getDisplayName()} — same localization reasoning as {@link #itemDisplayName}. */
	private static String moveDisplayName(String moveId) {
		if (moveId.isEmpty()) {
			return Component.translatable("phantasmon.pc.empty_slot").getString();
		}
		MoveTemplate template = Moves.getByName(moveId);
		return template != null ? template.getDisplayName().getString() : capitalize(moveId);
	}

	private List<String> dropdownOptions() {
		return switch (openDropdown) {
			case NATURE -> Arrays.asList(NATURES).stream().map(PhantasmonPcEditScreen::natureOptionLabel).toList();
			case TERA -> TERA_OPTIONS.stream()
					.map(t -> t == null ? Component.translatable("phantasmon.pc.edit.tera_none").getString() : t.getDisplayName().getString())
					.toList();
			case ABILITY -> rawOptions().stream().map(this::abilityDisplayName).toList();
			case ITEM -> rawOptions().stream().map(PhantasmonPcEditScreen::itemDisplayName).toList();
			case MOVE -> rawOptions().stream().map(PhantasmonPcEditScreen::moveDisplayName).toList();
			case NONE -> List.of();
		};
	}

	/** e.g. "Adamant (+Atk / -SpA)" — same +Bonus/-Malus format as the read-only PC detail panel (Adrien: it must show here too, both in the dropdown list and once selected). */
	private static String natureOptionLabel(String natureId) {
		String display = NatureModifiers.displayName(natureId);
		NatureModifiers.Modifier modifier = NatureModifiers.get(natureId);
		return modifier == null ? display : display + " (+" + modifier.boosted() + " / -" + modifier.reduced() + ")";
	}

	private int dropdownSelectedIndex() {
		return switch (openDropdown) {
			case NATURE -> natureIndex;
			case TERA -> TERA_OPTIONS.indexOf(teraType);
			case ABILITY -> abilityOptions.indexOf(abilityId);
			case ITEM -> rawOptions().indexOf(heldItemId == null ? "" : heldItemId);
			case MOVE -> activeMoveSlot < 0 ? -1 : rawOptions().indexOf(moveIds[activeMoveSlot] == null ? "" : moveIds[activeMoveSlot]);
			case NONE -> -1;
		};
	}

	private void selectDropdownOption(int index) {
		switch (openDropdown) {
			case NATURE -> {
				natureIndex = index;
				updateNatureLabel();
			}
			case TERA -> {
				teraType = TERA_OPTIONS.get(index);
				updateTeraLabel();
			}
			case ABILITY -> {
				abilityId = abilityOptions.get(index);
				updateAbilityLabel();
			}
			case ITEM -> {
				heldItemId = rawOptions().get(index);
				updateItemLabel();
			}
			case MOVE -> {
				moveIds[activeMoveSlot] = rawOptions().get(index);
				updateMoveLabel(activeMoveSlot);
			}
			case NONE -> {
			}
		}
	}

	private void updateNatureLabel() {
		natureValueButton.setMessage(Component.literal(natureOptionLabel(NATURES[natureIndex]) + " ▾"));
	}

	private String abilityDisplayName(String id) {
		return abilityDisplayNames.getOrDefault(id, capitalize(id));
	}

	private void updateAbilityLabel() {
		abilityValueButton.setMessage(Component.literal(abilityDisplayName(abilityId) + " ▾"));
	}

	private void updateItemLabel() {
		String label = itemDisplayName(heldItemId == null ? "" : heldItemId);
		itemValueButton.setMessage(Component.literal(label + " ▾"));
	}

	private void updateMoveLabel(int index) {
		moveValueButtons[index].setMessage(Component.literal(moveDisplayName(moveIds[index] == null ? "" : moveIds[index])));
	}

	private void toggleShiny() {
		shiny = !shiny;
		updateShinyLabel();
	}

	private void updateShinyLabel() {
		shinyButton.setMessage(Component.translatable(shiny ? "phantasmon.pc.edit.shiny_on" : "phantasmon.pc.edit.shiny_off"));
	}

	private static List<ElementalType> buildTeraOptions() {
		List<ElementalType> options = new ArrayList<>();
		options.add(null);
		options.addAll(ElementalTypes.all());
		return options;
	}

	private void updateTeraLabel() {
		String base = teraType == null ? Component.translatable("phantasmon.pc.edit.tera_none").getString() : teraType.getDisplayName().getString();
		teraValueButton.setMessage(Component.literal(base + " ▾"));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (openDropdown != DropdownKind.NONE) {
			List<String> options = dropdownOptions();
			int searchRowH = isSearchable(openDropdown) ? DROPDOWN_ROW_H : 0;
			int visible = Math.min(DROPDOWN_MAX_VISIBLE, options.size());
			int listTop = dropdownY + searchRowH;
			int listH = visible * DROPDOWN_ROW_H;
			if (mouseX >= dropdownX && mouseX < dropdownX + dropdownW && mouseY >= listTop && mouseY < listTop + listH) {
				int row = (int) ((mouseY - listTop) / DROPDOWN_ROW_H) + dropdownScroll;
				if (row >= 0 && row < options.size()) {
					selectDropdownOption(row);
				}
				openDropdown = DropdownKind.NONE;
				return true;
			}
			// Clicking the search row itself just keeps the dropdown open (so typing
			// afterward works) instead of dismissing it on the same click.
			if (searchRowH > 0 && mouseX >= dropdownX && mouseX < dropdownX + dropdownW && mouseY >= dropdownY && mouseY < listTop) {
				return true;
			}
			// Any other click while a dropdown is open just dismisses it — never also
			// activates whatever widget happens to be underneath (avoids immediately
			// reopening the same toggle button that was just clicked to close it).
			openDropdown = DropdownKind.NONE;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (openDropdown != DropdownKind.NONE) {
			int maxOffset = Math.max(0, dropdownOptions().size() - DROPDOWN_MAX_VISIBLE);
			dropdownScroll = clamp(dropdownScroll - (int) Math.signum(scrollY), 0, maxOffset);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (isSearchable(openDropdown)) {
			if (searchQuery.length() < 30 && chr >= 32 && chr != 127) {
				searchQuery.append(chr);
				dropdownScroll = 0;
			}
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (isSearchable(openDropdown)) {
			if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
				if (!searchQuery.isEmpty()) {
					searchQuery.deleteCharAt(searchQuery.length() - 1);
					dropdownScroll = 0;
				}
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				openDropdown = DropdownKind.NONE;
				return true;
			}
			// Swallow everything else while a search dropdown is focused so typing
			// can't accidentally trigger other screen shortcuts.
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/**
	 * Reuses the exact same Showdown parser/mapper as the chat-command import
	 * and the PC screen's clipboard import — pre-fills this form's fields
	 * instead of creating a new Pokémon, so the player can quickly reconfigure
	 * an existing one from a Showdown paste before hitting Save. Species/form
	 * are deliberately left untouched (this edits the current Pokémon, it
	 * doesn't turn it into a different species).
	 */
	private void importFromClipboard() {
		String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
		if (clipboard == null || clipboard.isBlank()) {
			statusMessage = Component.translatable("phantasmon.pokemon.import.clipboard_empty");
			return;
		}
		ShowdownPokemon parsed;
		try {
			parsed = ShowdownParser.parseSingle(clipboard);
		} catch (ShowdownParseException ex) {
			statusMessage = Component.translatable("phantasmon.pokemon.import.parse_error", ex.getMessage());
			return;
		}

		var request = ShowdownImportMapper.toCreateRequest(parsed, original.cobblemonDataVersion());
		nicknameBox.setValue(stringOf(request.data().get("nickname")));
		heldItemId = stringOf(request.data().get("heldItem"));
		updateItemLabel();
		abilityId = request.ability();
		if (!abilityOptions.contains(abilityId)) {
			abilityOptions.add(abilityId);
		}
		updateAbilityLabel();
		levelBox.setValue(String.valueOf(request.level()));
		shiny = Boolean.TRUE.equals(request.isShiny());
		updateShinyLabel();
		natureIndex = Math.max(0, indexOf(NATURES, request.nature()));
		updateNatureLabel();
		Object tera = request.data().get("teraType");
		teraType = tera != null ? safeGetType(tera.toString()) : null;
		updateTeraLabel();

		Map<String, Object> ivs = asMap(request.data().get("ivs"));
		Map<String, Object> evs = asMap(request.data().get("evs"));
		for (int i = 0; i < 6; i++) {
			ivBoxes[i].setValue(String.valueOf(statOrDefault(ivs, STATS[i], 31)));
			evBoxes[i].setValue(String.valueOf(statOrDefault(evs, STATS[i], 0)));
		}
		List<Object> moves = asList(request.data().get("moves"));
		for (int i = 0; i < 4; i++) {
			moveIds[i] = i < moves.size() ? String.valueOf(moves.get(i)) : "";
			updateMoveLabel(i);
		}
		statusMessage = Component.translatable("phantasmon.pc.edit.import_done");
	}

	private void save() {
		if (abilityId == null || abilityId.isEmpty()) {
			statusMessage = Component.translatable("phantasmon.pc.edit.error_ability_required");
			return;
		}

		Map<String, Object> data = new HashMap<>(original.data() != null ? original.data() : Map.of());
		Map<String, Integer> ivs = new HashMap<>();
		Map<String, Integer> evs = new HashMap<>();
		for (int i = 0; i < 6; i++) {
			ivs.put(STATS[i], clamp(parseIntOrDefault(ivBoxes[i].getValue(), 31), 0, 31));
			evs.put(STATS[i], clamp(parseIntOrDefault(evBoxes[i].getValue(), 0), 0, 252));
		}
		data.put("ivs", ivs);
		data.put("evs", evs);

		List<String> moves = new ArrayList<>();
		for (String id : moveIds) {
			if (id != null && !id.isEmpty()) {
				moves.add(id.toLowerCase(Locale.ROOT));
			}
		}
		data.put("moves", moves);

		String nickname = nicknameBox.getValue().trim();
		if (nickname.isEmpty()) {
			data.remove("nickname");
		} else {
			data.put("nickname", nickname);
		}

		String item = heldItemId == null ? "" : heldItemId.trim().toLowerCase(Locale.ROOT);
		if (item.isEmpty()) {
			data.remove("heldItem");
		} else {
			data.put("heldItem", item);
		}

		if (teraType != null) {
			data.put("teraType", teraType.getName().toLowerCase(Locale.ROOT));
		} else {
			data.remove("teraType");
		}

		int level = clamp(parseIntOrDefault(levelBox.getValue(), original.level()), 1, 100);
		String nature = NATURES[natureIndex];

		saving = true;
		statusMessage = Component.empty();
		PokemonUpdateRequestDto request = PokemonUpdateRequestDto.editing(data, level, nature, abilityId.toLowerCase(Locale.ROOT), shiny);
		pokemonClient.update(session.accessToken(), original.uuid(), request)
				.thenAccept(updated -> Minecraft.getInstance().execute(() -> {
					parent.refresh();
					Minecraft.getInstance().setScreen(parent);
				}))
				.exceptionally(ex -> {
					Minecraft.getInstance().execute(() -> {
						saving = false;
						statusMessage = errorMessage(ex);
					});
					return null;
				});
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blitSprite(PhantasmonPcScreen.SPRITE_PANEL, panelX, panelY, panelW, panelH);
		graphics.drawCenteredString(font, this.title, panelX + panelW / 2, panelY + 6, 0xFFFFFF);

		for (LabelSpot spot : labels) {
			graphics.drawString(font, spot.text(), spot.x(), spot.y(), LABEL_COLOR);
		}

		if (saving) {
			graphics.drawCenteredString(font, Component.translatable("phantasmon.pc.loading"), panelX + panelW / 2, panelY + panelH - 10, 0xAAAAAA);
		} else if (!statusMessage.getString().isEmpty()) {
			graphics.drawCenteredString(font, statusMessage, panelX + panelW / 2, panelY + panelH - 10, 0xFFAA55);
		}

		super.render(graphics, mouseX, mouseY, partialTick);

		List<String> currentDropdownOptions = dropdownOptions();
		int searchRowH = isSearchable(openDropdown) ? DROPDOWN_ROW_H : 0;
		int dropdownTotalHeight = searchRowH + Math.min(DROPDOWN_MAX_VISIBLE, currentDropdownOptions.size()) * DROPDOWN_ROW_H;
		for (Renderable widget : orderedWidgets) {
			if (widget instanceof AbstractWidget abstractWidget && coveredByDropdown(abstractWidget, dropdownTotalHeight)) {
				continue;
			}
			widget.render(graphics, mouseX, mouseY, partialTick);
		}
		graphics.flush();

		renderDropdown(graphics, mouseX, mouseY, currentDropdownOptions, searchRowH);
	}

	private boolean coveredByDropdown(AbstractWidget widget, int dropdownTotalHeight) {
		if (openDropdown == DropdownKind.NONE) {
			return false;
		}
		return widget.getX() < dropdownX + dropdownW && widget.getX() + widget.getWidth() > dropdownX
				&& widget.getY() < dropdownY + dropdownTotalHeight && widget.getY() + widget.getHeight() > dropdownY;
	}

	private void renderDropdown(GuiGraphics graphics, int mouseX, int mouseY, List<String> options, int searchRowH) {
		if (openDropdown == DropdownKind.NONE) {
			return;
		}
		int visible = Math.min(DROPDOWN_MAX_VISIBLE, options.size());
		int listH = visible * DROPDOWN_ROW_H;
		int totalH = searchRowH + listH;
		int selected = dropdownSelectedIndex();

		graphics.fill(dropdownX, dropdownY, dropdownX + dropdownW, dropdownY + totalH, DROPDOWN_BG);
		graphics.renderOutline(dropdownX, dropdownY, dropdownW, totalH, PANEL_BORDER);

		if (searchRowH > 0) {
			boolean empty = searchQuery.isEmpty();
			String text = empty ? Component.translatable("phantasmon.pc.edit.search_placeholder").getString() : searchQuery.toString();
			graphics.drawString(font, text + (empty ? "" : "_"), dropdownX + 4, dropdownY + 3, empty ? 0x777777 : 0xFFFFFF);
			graphics.fill(dropdownX, dropdownY + searchRowH - 1, dropdownX + dropdownW, dropdownY + searchRowH, PANEL_BORDER);
		}

		int listTop = dropdownY + searchRowH;
		for (int i = 0; i < visible; i++) {
			int optionIndex = i + dropdownScroll;
			if (optionIndex >= options.size()) {
				break;
			}
			int rowY = listTop + i * DROPDOWN_ROW_H;
			boolean hovered = mouseX >= dropdownX && mouseX < dropdownX + dropdownW && mouseY >= rowY && mouseY < rowY + DROPDOWN_ROW_H;
			if (hovered) {
				graphics.fill(dropdownX, rowY, dropdownX + dropdownW, rowY + DROPDOWN_ROW_H, DROPDOWN_ROW_HOVER);
			} else if (optionIndex == selected) {
				graphics.fill(dropdownX, rowY, dropdownX + dropdownW, rowY + DROPDOWN_ROW_H, DROPDOWN_ROW_SELECTED);
			}
			graphics.drawString(font, options.get(optionIndex), dropdownX + 4, rowY + 3, 0xFFFFFF);
		}
	}

	private static String capitalize(String value) {
		return value == null || value.isEmpty() ? String.valueOf(value) : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
	}

	private static boolean isDigits(String value) {
		return value.isEmpty() || value.chars().allMatch(Character::isDigit);
	}

	private static int parseIntOrDefault(String value, int fallback) {
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static int indexOf(String[] array, String value) {
		if (value == null) {
			return -1;
		}
		for (int i = 0; i < array.length; i++) {
			if (array[i].equalsIgnoreCase(value)) {
				return i;
			}
		}
		return -1;
	}

	private static int statOrDefault(Map<String, Object> stats, String key, int fallback) {
		Object value = stats.get(key);
		return value instanceof Number number ? number.intValue() : fallback;
	}

	private static String stringOf(Object value) {
		return value == null ? "" : value.toString();
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

	/** A {@link Button} skinned with the PC screen's own cyan nine-slice sprite instead of vanilla's default button texture, so this form matches the same visual language. */
	private static final class SpectralButton extends Button {

		SpectralButton(int x, int y, int w, int h, Component message, OnPress onPress) {
			super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
		}

		@Override
		public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			ResourceLocation sprite = isHoveredOrFocused() ? PhantasmonPcScreen.SPRITE_BUTTON_HOVER : PhantasmonPcScreen.SPRITE_BUTTON;
			graphics.blitSprite(sprite, getX(), getY(), getWidth(), getHeight());
			int color = isActive() ? 0xFFFFFF : 0x888888;
			graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color);
		}
	}
}
