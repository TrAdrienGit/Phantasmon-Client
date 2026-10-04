package com.mystaria.phantasmon.client.gui;

import java.util.ArrayList;
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
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.network.BackendApiException;
import com.mystaria.phantasmon.client.network.BackendErrorMessages;
import com.mystaria.phantasmon.client.pokemon.CobblemonHeldItems;
import com.mystaria.phantasmon.client.pokemon.HiddenPowerCalculator;
import com.mystaria.phantasmon.client.pokemon.NatureModifiers;
import com.mystaria.phantasmon.client.pokemon.PokemonClient;
import com.mystaria.phantasmon.client.pokemon.PokemonDto;
import com.mystaria.phantasmon.client.pokemon.PokemonGender;
import com.mystaria.phantasmon.client.pokemon.PokemonUpdateRequestDto;
import com.mystaria.phantasmon.client.pokemon.showdown.CobblemonShowdownNames;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownExporter;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownImportMapper;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownParseException;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownParser;
import com.mystaria.phantasmon.client.pokemon.showdown.ShowdownPokemon;

/**
 * Full Pokémon editor, rebuilt on 2026-10-02 on the trade/PC design system
 * ({@link PhantasmonCanvasScreen}) — Adrien: "refaire le menu éditer dans la
 * même idée".
 *
 * <p>Layout (shared 1600×900 px canvas): the same Pokémon card as the PC and
 * trade screens on the left, but as a <b>live preview</b> rebuilt from the
 * form every frame; the form on the right (identity, ability/item/nature/Tera,
 * IV/EV table with live EV total and Hidden Power, 4 move pickers); header
 * with IMPORTER (Showdown paste → form) as the primary action and an
 * unsaved-changes indicator; footer with status/help and ANNULER /
 * ENREGISTRER.
 *
 * <p>No vanilla widgets anymore: {@code EditBox}/{@code Button} render in GUI
 * coordinates and can't live inside the scaled canvas, so text fields and
 * pickers are drawn and hit-tested here (focus, typing, backspace, Ctrl+V,
 * Tab/Shift+Tab, mouse wheel ±1 / Shift ±10 on numbers). Dropdowns are drawn
 * at z=2000 after a flush, which also removes the old "button text bleeding
 * through the dropdown" problem by construction.
 *
 * <p>Unchanged rules from the previous editor: ability = this species' real
 * abilities only; item = battle held items only ({@link CobblemonHeldItems});
 * moves = every move, word-by-word search, never a move already in another
 * slot; Hidden Power is derived from IVs, never edited; Showdown import fills
 * the form (species/form untouched) and only saving writes anything; the save
 * is one {@code PATCH} merging onto the original {@code data}.
 */
public final class PhantasmonPcEditScreen extends PhantasmonCanvasScreen {

	private static final String[] NATURES = {
			"hardy", "lonely", "brave", "adamant", "naughty",
			"bold", "docile", "relaxed", "impish", "lax",
			"timid", "hasty", "serious", "jolly", "naive",
			"modest", "mild", "quiet", "bashful", "rash",
			"calm", "gentle", "sassy", "careful", "quirky"
	};
	private static final List<ElementalType> TERA_OPTIONS = buildTeraOptions();
	private static List<String> allHeldItemIds;
	private static List<String> allMoveIdsSorted;

	// ---- Text fields: 0 nickname, 1 level, 2-7 IVs, 8-13 EVs ----
	private static final int F_NICKNAME = 0;
	private static final int F_LEVEL = 1;
	private static final int F_IV = 2;
	private static final int F_EV = 8;
	private static final int FIELD_COUNT = 14;
	private static final int NICKNAME_MAX = 20;

	// ---- Layout (canvas px) ----
	/** The card is the PC's card shifted into the rail's spot: x 15..592. */
	private static final int CARD_DX = -204;
	private static final int PANEL_X = 601;
	private static final int PANEL_W = 984;
	private static final int COL1_X = 613;
	private static final int COL1_W = 470;
	private static final int COL2_X = 1093;
	private static final int COL2_W = 480;
	private static final int BOX_H = 56;
	/** Row 1: nickname (narrower than a full column), then the gender box up to the column's right edge. */
	private static final int NICKNAME_W = 320;
	private static final int GENDER_X = COL1_X + NICKNAME_W + 10;
	private static final int GENDER_W = COL1_X + COL1_W - GENDER_X;
	private static final int ROW1_Y = 81;
	private static final int ROW2_Y = 145;
	private static final int ROW3_Y = 209;
	private static final int BIG_Y = 273;
	private static final int BIG_H = 548;
	private static final int STAT_ROW_Y = BIG_Y + 52;
	private static final int STAT_ROW_H = 58;
	private static final int STAT_FIELD_W = 96;
	private static final int STAT_FIELD_H = 42;
	private static final int IV_FIELD_X = COL1_X + 230;
	private static final int EV_FIELD_X = COL1_X + 350;
	private static final int MOVE_ROW_Y = BIG_Y + 32;
	private static final int MOVE_ROW_H = 118;
	private static final int MOVE_ROW_STEP = 128;

	private static final int SAVE_W = 220;
	private static final int SAVE_X = 1576 - SAVE_W;
	private static final int CANCEL_W = 160;
	private static final int CANCEL_X = SAVE_X - 10 - CANCEL_W;
	private static final int FOOTER_BUTTON_Y = 844;
	private static final int FOOTER_BUTTON_H = 37;

	private static final int DROPDOWN_ROW_H = 36;
	private static final int DROPDOWN_SEARCH_H = 44;
	private static final int DROPDOWN_MAX_VISIBLE = 9;

	private enum Picker { NONE, ABILITY, ITEM, NATURE, TERA, MOVE }

	private final PokemonClient pokemonClient;
	private final AuthSession session;
	private final PokemonDto original;
	private final PhantasmonPcScreen parent;

	private final String[] fields = new String[FIELD_COUNT];
	private int focusedField = -1;
	private boolean initialized;

	private int natureIndex;
	private ElementalType teraType;
	private boolean shiny;
	/** Stored gender: {@code "M"}, {@code "F"} or {@code ""} (not set — Showdown's "random"). Only editable for mixed-ratio species, see {@link #fixedGender}. */
	private String gender = "";
	/** MALE/FEMALE/GENDERLESS when the species' ratio settles it (nothing to choose), UNKNOWN when both genders exist. */
	private PokemonGender fixedGender = PokemonGender.UNKNOWN;
	private String abilityId;
	private String heldItemId = "";
	private final String[] moveIds = new String[4];
	private List<String> abilityOptions = new ArrayList<>();
	private final Map<String, String> abilityDisplayNames = new HashMap<>();

	private Picker openPicker = Picker.NONE;
	private int activeMoveSlot = -1;
	private int pickerAnchorX, pickerAnchorY, pickerAnchorW, pickerAnchorH;
	private int pickerScroll;
	private final StringBuilder searchQuery = new StringBuilder();

	private boolean dirty;
	private boolean quitConfirmOpen;
	private boolean saving;
	private String statusMessage;
	private boolean statusIsError;

	public PhantasmonPcEditScreen(PokemonClient pokemonClient, AuthSession session, PokemonDto pokemon, PhantasmonPcScreen parent) {
		super(Component.translatable("phantasmon.pc.edit.title", pokemon.species()));
		this.pokemonClient = pokemonClient;
		this.session = session;
		this.original = pokemon;
		this.parent = parent;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	protected void init() {
		super.init();
		if (initialized) {
			// Window resize: keep everything typed so far.
			return;
		}
		initialized = true;
		Map<String, Object> data = original.data() != null ? original.data() : Map.of();
		fields[F_NICKNAME] = stringOf(data.get("nickname"));
		fields[F_LEVEL] = String.valueOf(original.level());
		Map<String, Object> ivs = asMap(data.get("ivs"));
		Map<String, Object> evs = asMap(data.get("evs"));
		for (int i = 0; i < 6; i++) {
			fields[F_IV + i] = String.valueOf(statOrDefault(ivs, STAT_KEYS[i], 31));
			fields[F_EV + i] = String.valueOf(statOrDefault(evs, STAT_KEYS[i], 0));
		}
		shiny = original.isShiny();
		gender = normalizeGender(data.get("gender"));
		natureIndex = Math.max(0, indexOf(NATURES, original.nature()));
		Object teraRaw = data.get("teraType");
		teraType = teraRaw != null ? Look.safeType(teraRaw.toString()) : null;
		abilityId = original.ability();
		heldItemId = stringOf(data.get("heldItem"));
		List<Object> initialMoves = asList(data.get("moves"));
		for (int i = 0; i < 4; i++) {
			moveIds[i] = i < initialMoves.size() ? String.valueOf(initialMoves.get(i)) : "";
		}

		// Real learnable abilities for this exact species, deduped (an ability can sit at
		// several priorities). AbilityTemplate.getDisplayName() is a raw translation key.
		Species species = PokemonSpecies.INSTANCE.getByName(original.species());
		fixedGender = fixedGenderOf(species);
		LinkedHashSet<String> abilitySet = new LinkedHashSet<>();
		if (species != null) {
			for (PotentialAbility potential : species.getAbilities()) {
				String id = potential.getTemplate().getName();
				abilitySet.add(id);
				abilityDisplayNames.put(id, Component.translatable(potential.getTemplate().getDisplayName()).getString());
			}
		}
		if (abilityId != null) {
			abilitySet.add(abilityId);
		}
		abilityOptions = new ArrayList<>(abilitySet);
	}

	// =====================================================================
	// Form state
	// =====================================================================

	private static boolean isNumeric(int field) {
		return field != F_NICKNAME;
	}

	private static int fieldMax(int field) {
		if (field == F_LEVEL) {
			return 100;
		}
		return field >= F_EV ? 252 : 31;
	}

	private static int fieldMin(int field) {
		return field == F_LEVEL ? 1 : 0;
	}

	private int fieldInt(int field, int fallback) {
		try {
			return Integer.parseInt(fields[field].trim());
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	private boolean fieldInvalid(int field) {
		if (!isNumeric(field)) {
			return false;
		}
		int value = fieldInt(field, Integer.MIN_VALUE);
		return value < fieldMin(field) || value > fieldMax(field);
	}

	private int evTotal() {
		int total = 0;
		for (int i = 0; i < 6; i++) {
			total += Math.max(0, fieldInt(F_EV + i, 0));
		}
		return total;
	}

	/** {@code data} as it would be saved — merged onto the original's so keys this form doesn't manage survive. */
	private Map<String, Object> buildData() {
		Map<String, Object> data = new HashMap<>(original.data() != null ? original.data() : Map.of());
		Map<String, Integer> ivs = new HashMap<>();
		Map<String, Integer> evs = new HashMap<>();
		for (int i = 0; i < 6; i++) {
			ivs.put(STAT_KEYS[i], clamp(fieldInt(F_IV + i, 31), 0, 31));
			evs.put(STAT_KEYS[i], clamp(fieldInt(F_EV + i, 0), 0, 252));
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
		String nickname = fields[F_NICKNAME].trim();
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
		if (genderEditable()) {
			if (gender.isEmpty()) {
				data.remove("gender");
			} else {
				data.put("gender", gender);
			}
		}
		return data;
	}

	private int level() {
		return clamp(fieldInt(F_LEVEL, original.level()), 1, 100);
	}

	/** What the card on the left shows: the Pokémon as it would be after saving. */
	private PokemonDto preview() {
		return new PokemonDto(original.uuid(), original.ownerUuid(), original.species(), original.form(), level(),
				NATURES[natureIndex], abilityId, shiny, original.boxId(), original.boxSlot(), original.teamSlot(),
				original.cobblemonDataVersion(), buildData());
	}

	private void edited() {
		dirty = true;
		statusMessage = null;
	}

	private void save() {
		if (saving) {
			return;
		}
		if (abilityId == null || abilityId.isEmpty()) {
			setStatus(Component.translatable("phantasmon.pc.edit.error_ability_required").getString(), true);
			return;
		}
		saving = true;
		statusMessage = null;
		PokemonUpdateRequestDto request = PokemonUpdateRequestDto.editing(buildData(), level(), NATURES[natureIndex],
				abilityId.toLowerCase(Locale.ROOT), shiny);
		pokemonClient.update(session.accessToken(), original.uuid(), request)
				.thenAccept(updated -> Minecraft.getInstance().execute(() -> {
					parent.notifySaved();
					parent.refresh();
					Minecraft.getInstance().setScreen(parent);
				}))
				.exceptionally(ex -> {
					Minecraft.getInstance().execute(() -> {
						saving = false;
						setStatus(errorMessage(ex), true);
					});
					return null;
				});
	}

	private void requestClose() {
		if (dirty) {
			quitConfirmOpen = true;
		} else {
			onClose();
		}
	}

	/**
	 * Same Showdown parser/mapper as every other import, but it only fills this
	 * form (species/form untouched) — nothing is written until ENREGISTRER.
	 */
	/** Copies the Pokémon as currently shown — unsaved edits included — as Showdown text (CAD Partie 1 §11, TODO-11). */
	private void exportToClipboard() {
		PokemonDto shown = new PokemonDto(original.uuid(), original.ownerUuid(), original.species(), original.form(), level(),
				NATURES[natureIndex], abilityId == null ? null : abilityId.toLowerCase(Locale.ROOT), shiny,
				original.boxId(), original.boxSlot(), original.teamSlot(), original.cobblemonDataVersion(), buildData());
		Minecraft.getInstance().keyboardHandler.setClipboard(ShowdownExporter.export(shown, CobblemonShowdownNames.INSTANCE));
		setStatus(Component.translatable("phantasmon.pc.edit.export_done").getString(), false);
	}

	private void importFromClipboard() {
		String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
		if (clipboard == null || clipboard.isBlank()) {
			setStatus(Component.translatable("phantasmon.pokemon.import.clipboard_empty").getString(), true);
			return;
		}
		ShowdownPokemon parsed;
		try {
			parsed = ShowdownParser.parseSingle(clipboard);
		} catch (ShowdownParseException ex) {
			setStatus(Component.translatable("phantasmon.pokemon.import.parse_error", ex.getMessage()).getString(), true);
			return;
		}
		var request = ShowdownImportMapper.toCreateRequest(parsed, original.cobblemonDataVersion());
		fields[F_NICKNAME] = stringOf(request.data().get("nickname"));
		heldItemId = stringOf(request.data().get("heldItem"));
		abilityId = request.ability();
		if (!abilityOptions.contains(abilityId)) {
			abilityOptions.add(abilityId);
		}
		fields[F_LEVEL] = String.valueOf(request.level());
		shiny = Boolean.TRUE.equals(request.isShiny());
		if (genderEditable()) {
			gender = normalizeGender(request.data().get("gender"));
		}
		natureIndex = Math.max(0, indexOf(NATURES, request.nature()));
		Object tera = request.data().get("teraType");
		teraType = tera != null ? Look.safeType(tera.toString()) : null;
		Map<String, Object> ivs = asMap(request.data().get("ivs"));
		Map<String, Object> evs = asMap(request.data().get("evs"));
		for (int i = 0; i < 6; i++) {
			fields[F_IV + i] = String.valueOf(statOrDefault(ivs, STAT_KEYS[i], 31));
			fields[F_EV + i] = String.valueOf(statOrDefault(evs, STAT_KEYS[i], 0));
		}
		List<Object> moves = asList(request.data().get("moves"));
		for (int i = 0; i < 4; i++) {
			moveIds[i] = i < moves.size() ? String.valueOf(moves.get(i)) : "";
		}
		dirty = true;
		setStatus(Component.translatable("phantasmon.pc.edit.import_done").getString(), false);
	}

	private void setStatus(String message, boolean error) {
		statusMessage = stripPrefix(message);
		statusIsError = error;
	}

	// =====================================================================
	// Pickers
	// =====================================================================

	private boolean searchable() {
		return openPicker == Picker.ITEM || openPicker == Picker.MOVE;
	}

	private void openPicker(Picker picker, int moveSlot, int x, int y, int w, int h) {
		if (openPicker == picker && activeMoveSlot == moveSlot) {
			openPicker = Picker.NONE;
			return;
		}
		focusedField = -1;
		openPicker = picker;
		activeMoveSlot = moveSlot;
		pickerAnchorX = x;
		pickerAnchorY = y;
		pickerAnchorW = w;
		pickerAnchorH = h;
		searchQuery.setLength(0);
		pickerScroll = 0;
		int selected = pickerSelectedIndex();
		if (selected >= DROPDOWN_MAX_VISIBLE) {
			pickerScroll = Math.min(selected - DROPDOWN_MAX_VISIBLE / 2, Math.max(0, pickerSize() - DROPDOWN_MAX_VISIBLE));
		}
	}

	/** Raw values of the open picker, in display order (ids; nature index as string; Tera as type name or ""). */
	private List<String> pickerValues() {
		return switch (openPicker) {
			case ABILITY -> abilityOptions;
			case ITEM -> itemChoices();
			case MOVE -> moveChoices();
			case NATURE -> List.of(NATURES);
			case TERA -> TERA_OPTIONS.stream().map(t -> t == null ? "" : t.getName()).toList();
			case NONE -> List.of();
		};
	}

	private int pickerSize() {
		return pickerValues().size();
	}

	private int pickerSelectedIndex() {
		List<String> values = pickerValues();
		return switch (openPicker) {
			case ABILITY -> values.indexOf(abilityId);
			case ITEM -> values.indexOf(heldItemId == null ? "" : heldItemId);
			case MOVE -> activeMoveSlot < 0 ? -1 : values.indexOf(moveIds[activeMoveSlot] == null ? "" : moveIds[activeMoveSlot]);
			case NATURE -> natureIndex;
			case TERA -> TERA_OPTIONS.indexOf(teraType);
			case NONE -> -1;
		};
	}

	private void selectPickerValue(int index) {
		List<String> values = pickerValues();
		if (index < 0 || index >= values.size()) {
			return;
		}
		switch (openPicker) {
			case ABILITY -> abilityId = values.get(index);
			case ITEM -> heldItemId = values.get(index);
			case MOVE -> moveIds[activeMoveSlot] = values.get(index);
			case NATURE -> natureIndex = index;
			case TERA -> teraType = TERA_OPTIONS.get(index);
			case NONE -> {
			}
		}
		openPicker = Picker.NONE;
		edited();
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
			if (!usedByAnotherSlot && matchesSearch(id, moveDisplayName(id))) {
				result.add(id);
			}
		}
		return result;
	}

	/** Every word of the query must appear somewhere in "id + display name", in any order (e.g. "Booster Energy" finds "Energy Booster"). */
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

	private static String itemDisplayName(String id) {
		if (id == null || id.isEmpty()) {
			return Component.translatable("phantasmon.trade.screen.no_item").getString();
		}
		Item item = CobblemonHeldItems.byId().get(id);
		return item != null ? item.getDescription().getString() : capitalize(id);
	}

	private static String moveDisplayName(String moveId) {
		if (moveId == null || moveId.isEmpty()) {
			return Component.translatable("phantasmon.pc.empty_slot").getString();
		}
		MoveTemplate template = Moves.getByName(moveId);
		return template != null ? template.getDisplayName().getString() : capitalize(moveId);
	}

	private String abilityDisplayName(String id) {
		return id == null ? "?" : abilityDisplayNames.getOrDefault(id, capitalize(id));
	}

	private static List<ElementalType> buildTeraOptions() {
		List<ElementalType> options = new ArrayList<>();
		options.add(null);
		options.addAll(ElementalTypes.all());
		return options;
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

		if (quitConfirmOpen) {
			if (inside(x, y, 652, 474, 150, 37)) {
				quitConfirmOpen = false;
			} else if (inside(x, y, 812, 474, 136, 37)) {
				onClose();
			}
			return true;
		}
		if (openPicker != Picker.NONE) {
			PickerGeometry geo = pickerGeometry();
			if (inside(x, y, geo.x(), geo.listY(), geo.w(), geo.visible() * DROPDOWN_ROW_H)) {
				selectPickerValue((int) ((y - geo.listY()) / DROPDOWN_ROW_H) + pickerScroll);
				return true;
			}
			if (inside(x, y, geo.x(), geo.y(), geo.w(), geo.listY() - geo.y())) {
				return true;
			}
			// Any other click only closes the list (never also triggers what's underneath).
			openPicker = Picker.NONE;
			return true;
		}

		if (inside(x, y, HEADER_IMPORT_X, 15, HEADER_PAIR_W, 48)) {
			importFromClipboard();
			return true;
		}
		if (inside(x, y, HEADER_EXPORT_X, 15, HEADER_PAIR_W, 48)) {
			exportToClipboard();
			return true;
		}
		if (inside(x, y, CANCEL_X, FOOTER_BUTTON_Y, CANCEL_W, FOOTER_BUTTON_H)) {
			requestClose();
			return true;
		}
		if (inside(x, y, SAVE_X, FOOTER_BUTTON_Y, SAVE_W, FOOTER_BUTTON_H)) {
			save();
			return true;
		}

		int field = fieldAt(x, y);
		if (field >= 0) {
			focusedField = field;
			return true;
		}
		focusedField = -1;

		if (genderEditable() && inside(x, y, GENDER_X, ROW1_Y, GENDER_W, BOX_H)) {
			gender = switch (gender) {
				case "" -> "M";
				case "M" -> "F";
				default -> "";
			};
			edited();
			return true;
		}
		if (inside(x, y, 1333, ROW1_Y, 240, BOX_H)) {
			shiny = !shiny;
			edited();
			return true;
		}
		if (inside(x, y, COL1_X, ROW2_Y, COL1_W, BOX_H)) {
			openPicker(Picker.ABILITY, -1, COL1_X, ROW2_Y, COL1_W, BOX_H);
			return true;
		}
		if (inside(x, y, COL2_X, ROW2_Y, COL2_W, BOX_H)) {
			openPicker(Picker.ITEM, -1, COL2_X, ROW2_Y, COL2_W, BOX_H);
			return true;
		}
		if (inside(x, y, COL1_X, ROW3_Y, COL1_W, BOX_H)) {
			openPicker(Picker.NATURE, -1, COL1_X, ROW3_Y, COL1_W, BOX_H);
			return true;
		}
		if (inside(x, y, COL2_X, ROW3_Y, COL2_W, BOX_H)) {
			openPicker(Picker.TERA, -1, COL2_X, ROW3_Y, COL2_W, BOX_H);
			return true;
		}
		for (int i = 0; i < 4; i++) {
			int rowY = MOVE_ROW_Y + i * MOVE_ROW_STEP;
			if (inside(x, y, COL2_X + 9, rowY, COL2_W - 18, MOVE_ROW_H)) {
				openPicker(Picker.MOVE, i, COL2_X + 9, rowY, COL2_W - 18, MOVE_ROW_H);
				return true;
			}
		}
		return true;
	}

	/** Mouse wheel: scrolls an open list, otherwise ±1 (Shift ±10) on the number field under the cursor. */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (openPicker != Picker.NONE) {
			int maxOffset = Math.max(0, pickerSize() - DROPDOWN_MAX_VISIBLE);
			pickerScroll = clamp(pickerScroll - (int) Math.signum(scrollY), 0, maxOffset);
			return true;
		}
		int field = fieldAt(toCanvasX(mouseX), toCanvasY(mouseY));
		if (field >= 0 && isNumeric(field) && scrollY != 0) {
			int step = (int) Math.signum(scrollY) * (hasShiftDown() ? 10 : 1);
			int current = fieldInt(field, fieldMin(field));
			fields[field] = String.valueOf(clamp(current + step, fieldMin(field), fieldMax(field)));
			edited();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (quitConfirmOpen) {
			return true;
		}
		if (searchable()) {
			if (searchQuery.length() < 30 && chr >= 32 && chr != 127) {
				searchQuery.append(chr);
				pickerScroll = 0;
			}
			return true;
		}
		if (focusedField >= 0) {
			insert(String.valueOf(chr));
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	private void insert(String text) {
		StringBuilder value = new StringBuilder(fields[focusedField]);
		for (char c : text.toCharArray()) {
			if (isNumeric(focusedField)) {
				if (Character.isDigit(c) && value.length() < 3) {
					value.append(c);
				}
			} else if (c >= 32 && c != 127 && value.length() < NICKNAME_MAX) {
				value.append(c);
			}
		}
		if (!value.toString().equals(fields[focusedField])) {
			fields[focusedField] = value.toString();
			edited();
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (quitConfirmOpen) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				quitConfirmOpen = false;
			}
			return true;
		}
		if (openPicker != Picker.NONE) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				openPicker = Picker.NONE;
			} else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && searchable() && !searchQuery.isEmpty()) {
				searchQuery.deleteCharAt(searchQuery.length() - 1);
				pickerScroll = 0;
			}
			// Everything else is swallowed while a list is open, so typing can't trigger shortcuts.
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (focusedField >= 0) {
				focusedField = -1;
			} else {
				requestClose();
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			save();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_TAB) {
			int direction = hasShiftDown() ? -1 : 1;
			focusedField = focusedField < 0 ? (direction > 0 ? 0 : FIELD_COUNT - 1)
					: Math.floorMod(focusedField + direction, FIELD_COUNT);
			return true;
		}
		if (focusedField >= 0) {
			if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
				String value = fields[focusedField];
				if (!value.isEmpty()) {
					fields[focusedField] = hasControlDown() ? "" : value.substring(0, value.length() - 1);
					edited();
				}
				return true;
			}
			if (Screen.isPaste(keyCode)) {
				insert(Minecraft.getInstance().keyboardHandler.getClipboard());
				return true;
			}
			if (isNumeric(focusedField) && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
				int step = (keyCode == GLFW.GLFW_KEY_UP ? 1 : -1) * (hasShiftDown() ? 10 : 1);
				fields[focusedField] = String.valueOf(clamp(fieldInt(focusedField, fieldMin(focusedField)) + step,
						fieldMin(focusedField), fieldMax(focusedField)));
				edited();
				return true;
			}
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private int fieldAt(double x, double y) {
		if (inside(x, y, COL1_X, ROW1_Y, COL1_W, BOX_H)) {
			return F_NICKNAME;
		}
		if (inside(x, y, COL2_X, ROW1_Y, 230, BOX_H)) {
			return F_LEVEL;
		}
		for (int i = 0; i < 6; i++) {
			int rowY = statFieldY(i);
			if (inside(x, y, IV_FIELD_X, rowY, STAT_FIELD_W, STAT_FIELD_H)) {
				return F_IV + i;
			}
			if (inside(x, y, EV_FIELD_X, rowY, STAT_FIELD_W, STAT_FIELD_H)) {
				return F_EV + i;
			}
		}
		return -1;
	}

	private static int statFieldY(int index) {
		return STAT_ROW_Y + index * STAT_ROW_H + (STAT_ROW_H - STAT_FIELD_H) / 2;
	}

	// =====================================================================
	// Rendering
	// =====================================================================

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderWorldBackdrop(graphics, mouseX, mouseY, partialTick);
		boolean blocked = quitConfirmOpen || openPicker != Picker.NONE;
		double mx = blocked ? -1 : toCanvasX(mouseX);
		double my = blocked ? -1 : toCanvasY(mouseY);

		beginCanvas(graphics);
		renderRootPanel(graphics);
		renderHeader(graphics, mx, my);
		renderPokemonCard(graphics, preview(), ownerName(), CARD_DX, ACCENT_LEFT, null, true);
		renderForm(graphics, mx, my);
		renderFooter(graphics, mx, my);
		endCanvas(graphics);

		if (openPicker != Picker.NONE) {
			renderPicker(graphics, mouseX, mouseY);
		}
		if (quitConfirmOpen) {
			renderQuitModal(graphics, mouseX, mouseY);
		}
	}

	private String ownerName() {
		String name = session.username();
		return name == null ? "?" : name;
	}

	private void renderHeader(GuiGraphics g, double mx, double my) {
		renderHeaderPlates(g, HEADER_PAIR_PLATE_W);
		String title = upper(Component.translatable("phantasmon.pc.editor.header", displayName(original)).getString());
		drawText(g, fitText(title, HEADER_PAIR_TITLE_W, 2f, true, 1.5f), 31, 32, 2f, WHITE, true, 1.5f);

		String state = upper(Component.translatable(dirty ? "phantasmon.pc.editor.dirty" : "phantasmon.pc.editor.clean").getString());
		drawText(g, state, 1569 - textWidth(state, 2f, false, 1f), 32, 2f, dirty ? ERROR_TEXT : DIM, false, 1f);

		boolean hovered = inside(mx, my, HEADER_IMPORT_X, 15, HEADER_PAIR_W, 48);
		renderPrimaryButtonFrame(g, HEADER_IMPORT_X, 15, HEADER_PAIR_W, 48, hovered, false);
		String plus = "+";
		String label = upper(Component.translatable("phantasmon.pc.screen.import").getString());
		float plusWidth = textWidth(plus, 2f, true, 0f);
		float labelWidth = textWidth(label, 2f, true, 1f);
		float startX = HEADER_IMPORT_X + (HEADER_PAIR_W - plusWidth - 8 - labelWidth) / 2f;
		drawText(g, plus, startX, 32, 2f, CYAN, true, 0f);
		drawText(g, label, startX + plusWidth + 8, 32, 2f, CYAN, true, 1f);
		renderExportButton(g, HEADER_EXPORT_X, 15, HEADER_PAIR_W, 48, inside(mx, my, HEADER_EXPORT_X, 15, HEADER_PAIR_W, 48));
	}

	private void renderForm(GuiGraphics g, double mx, double my) {
		blitTexture(g, TEX_RAIL, PANEL_X, 72, PANEL_W, 758);
		outline(g, PANEL_X, 72, PANEL_W, 758, RAIL_BORDER);

		// Row 1: nickname, level, shiny
		renderTextField(g, F_NICKNAME, COL1_X, ROW1_Y, NICKNAME_W, BOX_H, "phantasmon.pc.edit.nickname", mx, my);
		renderGenderField(g, mx, my);
		renderTextField(g, F_LEVEL, COL2_X, ROW1_Y, 230, BOX_H, "phantasmon.pc.edit.level", mx, my);
		boolean shinyHovered = inside(mx, my, 1333, ROW1_Y, 240, BOX_H);
		renderFieldFrame(g, 1333, ROW1_Y, 240, BOX_H, "phantasmon.pc.editor.shiny", false, shinyHovered);
		String shinyText = Component.translatable(shiny ? "phantasmon.pc.editor.yes" : "phantasmon.pc.editor.no").getString();
		if (shiny) {
			g.flush();
			g.blitSprite(SPRITE_STAR, 1342, ROW1_Y + 25, 16, 16);
		}
		drawText(g, shinyText, shiny ? 1364 : 1342, ROW1_Y + 26, VALUE_SCALE, shiny ? STAR : TEXT2, false, 0f);

		// Rows 2-3: pickers
		renderPickerField(g, Picker.ABILITY, COL1_X, ROW2_Y, COL1_W, "phantasmon.trade.screen.ability", mx, my);
		renderPickerField(g, Picker.ITEM, COL2_X, ROW2_Y, COL2_W, "phantasmon.trade.screen.held_item", mx, my);
		renderPickerField(g, Picker.NATURE, COL1_X, ROW3_Y, COL1_W, "phantasmon.trade.screen.nature", mx, my);
		renderPickerField(g, Picker.TERA, COL2_X, ROW3_Y, COL2_W, "phantasmon.trade.screen.tera", mx, my);

		renderStatsBox(g, mx, my);
		renderMovesBox(g, mx, my);
	}

	private void renderFieldFrame(GuiGraphics g, int x, int y, int w, int h, String labelKey, boolean focused, boolean hovered) {
		g.fill(x, y, x + w, y + h, INFO_BOX_BG);
		if (focused) {
			glowInside(g, x, y, w, h, 0x50E6FF, 0.25f, 5);
		}
		outline(g, x, y, w, h, focused ? CYAN : hovered ? CYAN2 : LINE_20);
		if (labelKey != null) {
			drawText(g, upper(Component.translatable(labelKey).getString()), x + 9, y + 8, 1f, TITLE, true, 1.2f);
		}
	}

	private void renderTextField(GuiGraphics g, int field, int x, int y, int w, int h, String labelKey, double mx, double my) {
		boolean focused = focusedField == field;
		renderFieldFrame(g, x, y, w, h, labelKey, focused, inside(mx, my, x, y, w, h));
		String value = fields[field];
		int color = fieldInvalid(field) ? ERROR_TEXT : WHITE;
		if (value.isEmpty() && !focused && field == F_NICKNAME) {
			drawText(g, displayName(new PokemonDto(null, null, original.species(), null, 0, null, null, false,
					null, null, null, null, Map.of())), x + 9, y + 26, VALUE_SCALE, DIM, false, 0f);
		} else {
			String shown = fitText(value, w - 30, VALUE_SCALE, false, 0f);
			drawText(g, shown, x + 9, y + 26, VALUE_SCALE, color, false, 0f);
			if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
				drawText(g, "_", x + 9 + textWidth(shown, VALUE_SCALE, false, 0f) + 2, y + 26, VALUE_SCALE, CYAN, false, 0f);
			}
		}
	}

	/** Click cycles Aléatoire → ♂ → ♀; species whose ratio settles the gender show it fixed, with no hover. */
	private void renderGenderField(GuiGraphics g, double mx, double my) {
		boolean editable = genderEditable();
		renderFieldFrame(g, GENDER_X, ROW1_Y, GENDER_W, BOX_H, "phantasmon.pc.editor.gender", false,
				editable && inside(mx, my, GENDER_X, ROW1_Y, GENDER_W, BOX_H));
		float valueY = ROW1_Y + 26;
		float textX = GENDER_X + 9;
		PokemonGender shown = editable ? (gender.equals("M") ? PokemonGender.MALE : gender.equals("F") ? PokemonGender.FEMALE : PokemonGender.UNKNOWN) : fixedGender;
		switch (shown) {
			case MALE -> {
				drawGender(g, PokemonGender.MALE, textX, valueY, VALUE_SCALE);
				drawText(g, Component.translatable("phantasmon.pc.editor.gender_male").getString(), textX + 22, valueY, VALUE_SCALE, editable ? WHITE : DIM, false, 0f);
			}
			case FEMALE -> {
				drawGender(g, PokemonGender.FEMALE, textX, valueY, VALUE_SCALE);
				drawText(g, Component.translatable("phantasmon.pc.editor.gender_female").getString(), textX + 22, valueY, VALUE_SCALE, editable ? WHITE : DIM, false, 0f);
			}
			case GENDERLESS -> drawText(g, fitText(Component.translatable("phantasmon.pc.editor.gender_none").getString(), GENDER_W - 18, VALUE_SCALE, false, 0f),
					textX, valueY, VALUE_SCALE, DIM, false, 0f);
			default -> drawText(g, fitText(Component.translatable("phantasmon.pc.editor.gender_random").getString(), GENDER_W - 18, VALUE_SCALE, false, 0f),
					textX, valueY, VALUE_SCALE, DIM, false, 0f);
		}
	}

	private boolean genderEditable() {
		return fixedGender == PokemonGender.UNKNOWN;
	}

	/** Same ratio rule as {@code Look.of}: the form's own ratio when it has one, else the species'. */
	private PokemonGender fixedGenderOf(Species species) {
		if (species == null) {
			return PokemonGender.UNKNOWN;
		}
		FormData form = PokemonGuiRendering.resolveForm(species, original.form());
		float ratio = form != null ? form.getMaleRatio() : species.getMaleRatio();
		return PokemonGender.resolve(null, ratio);
	}

	private static String normalizeGender(Object stored) {
		if (stored == null) {
			return "";
		}
		return switch (stored.toString().trim().toUpperCase(Locale.ROOT)) {
			case "M", "MALE" -> "M";
			case "F", "FEMALE" -> "F";
			default -> "";
		};
	}

	private void renderPickerField(GuiGraphics g, Picker picker, int x, int y, int w, String labelKey, double mx, double my) {
		boolean open = openPicker == picker;
		renderFieldFrame(g, x, y, w, BOX_H, labelKey, open, inside(mx, my, x, y, w, BOX_H));
		drawText(g, "▾", x + w - 22, y + 26, VALUE_SCALE, open ? CYAN : MUTED, false, 0f);
		float valueY = y + 26;
		switch (picker) {
			case ABILITY -> drawText(g, fitText(abilityDisplayName(abilityId), w - 50, VALUE_SCALE, false, 0f),
					x + 9, valueY, VALUE_SCALE, WHITE, false, 0f);
			case ITEM -> {
				float textX = x + 9;
				ItemStack stack = PokemonGuiRendering.heldItemStack(heldItemId);
				if (!stack.isEmpty()) {
					PokemonGuiRendering.renderItemIcon(g, stack, textX, valueY - 3, 20);
					textX += 26;
				}
				drawText(g, fitText(itemDisplayName(heldItemId), x + w - 40 - textX, VALUE_SCALE, false, 0f),
						textX, valueY, VALUE_SCALE, heldItemId == null || heldItemId.isEmpty() ? DIM : WHITE, false, 0f);
			}
			case NATURE -> renderNatureValue(g, NATURES[natureIndex], x + 9, x + w - 36, valueY);
			case TERA -> {
				if (teraType != null) {
					drawTypeBadge(g, teraType, x + 9, valueY - 3, VALUE_SCALE);
				} else {
					drawText(g, Component.translatable("phantasmon.pc.editor.tera_default").getString(), x + 9, valueY, VALUE_SCALE, DIM, false, 0f);
				}
			}
			default -> {
			}
		}
	}

	/** "Rigide   +Atk -SpA" with the same colors as the card. */
	private void renderNatureValue(GuiGraphics g, String natureId, float x, float rightX, float y) {
		NatureModifiers.Modifier modifier = NatureModifiers.get(natureId);
		float reserved = 0;
		if (modifier != null) {
			String up = "+" + modifier.boosted();
			String down = "-" + modifier.reduced();
			float downWidth = textWidth(down, VALUE_SCALE, false, 0f);
			float upWidth = textWidth(up, VALUE_SCALE, false, 0f);
			drawText(g, down, rightX - downWidth, y, VALUE_SCALE, NATURE_DOWN, false, 0f);
			drawText(g, up, rightX - downWidth - 10 - upWidth, y, VALUE_SCALE, NATURE_UP, false, 0f);
			reserved = upWidth + 10 + downWidth + 12;
		}
		drawText(g, fitText(NatureModifiers.displayName(natureId), rightX - x - reserved, VALUE_SCALE, false, 0f), x, y, VALUE_SCALE, WHITE, false, 0f);
	}

	/** IV / EV table: one editable field per stat, live EV total (red above 510) and the Hidden Power those IVs give. */
	private void renderStatsBox(GuiGraphics g, double mx, double my) {
		infoBox(g, COL1_X, BIG_Y, COL1_W, BIG_H, "phantasmon.trade.screen.ivs_evs");
		String ivHead = Component.translatable("phantasmon.trade.screen.iv").getString();
		String evHead = Component.translatable("phantasmon.trade.screen.ev").getString();
		drawCentered(g, ivHead + " (0-31)", IV_FIELD_X + STAT_FIELD_W / 2f, BIG_Y + 32, 1f, STAT_HEAD, true, 0f);
		drawCentered(g, evHead + " (0-252)", EV_FIELD_X + STAT_FIELD_W / 2f, BIG_Y + 32, 1f, STAT_HEAD, true, 0f);

		NatureModifiers.Modifier modifier = NatureModifiers.get(NATURES[natureIndex]);
		String boosted = modifier == null ? null : modifier.boosted().toLowerCase(Locale.ROOT);
		String reduced = modifier == null ? null : modifier.reduced().toLowerCase(Locale.ROOT);
		for (int i = 0; i < 6; i++) {
			int rowY = STAT_ROW_Y + i * STAT_ROW_H;
			if (i % 2 == 1) {
				g.fill(COL1_X + 9, rowY, COL1_X + COL1_W - 9, rowY + STAT_ROW_H, STAT_EVEN_BG);
			}
			int labelColor = STAT_KEYS[i].equals(boosted) ? NATURE_UP : STAT_KEYS[i].equals(reduced) ? NATURE_DOWN : WHITE;
			drawText(g, Component.translatable("phantasmon.trade.screen.stat." + STAT_KEYS[i]).getString(),
					COL1_X + 18, rowY + (STAT_ROW_H - 14) / 2f, VALUE_SCALE, labelColor, false, 0f);
			renderStatField(g, F_IV + i, IV_FIELD_X, statFieldY(i), IV_COLOR, mx, my);
			renderStatField(g, F_EV + i, EV_FIELD_X, statFieldY(i), EV_COLOR, mx, my);
		}

		int totalY = STAT_ROW_Y + 6 * STAT_ROW_H + 8;
		g.fill(COL1_X + 9, totalY, COL1_X + COL1_W - 9, totalY + hairline(), LINE_20);
		int total = evTotal();
		drawText(g, Component.translatable("phantasmon.trade.screen.total_ev").getString(), COL1_X + 18, totalY + 16, VALUE_SCALE, DIM, false, 0f);
		String totalText = total + " / 510";
		drawText(g, totalText, EV_FIELD_X + STAT_FIELD_W - textWidth(totalText, VALUE_SCALE, false, 0f), totalY + 16,
				VALUE_SCALE, total > 510 ? ERROR_TEXT : EV_COLOR, false, 0f);

		int hpY = totalY + 58;
		drawText(g, upper(Component.translatable("phantasmon.pc.screen.hidden_power").getString()), COL1_X + 18, hpY + 7, 1f, TITLE, true, 1.2f);
		Map<String, Object> ivs = new HashMap<>();
		for (int i = 0; i < 6; i++) {
			ivs.put(STAT_KEYS[i], clamp(fieldInt(F_IV + i, 31), 0, 31));
		}
		ElementalType hiddenPower = Look.safeType(HiddenPowerCalculator.type(ivs));
		if (hiddenPower != null) {
			drawTypeBadge(g, hiddenPower, IV_FIELD_X, hpY, VALUE_SCALE);
		}
	}

	private void renderStatField(GuiGraphics g, int field, int x, int y, int color, double mx, double my) {
		boolean focused = focusedField == field;
		renderFieldFrame(g, x, y, STAT_FIELD_W, STAT_FIELD_H, null, focused, inside(mx, my, x, y, STAT_FIELD_W, STAT_FIELD_H));
		String value = fields[field];
		int textColor = fieldInvalid(field) ? ERROR_TEXT : color;
		float textY = y + (STAT_FIELD_H - 14) / 2f;
		drawText(g, value, x + STAT_FIELD_W - 12 - textWidth(value, VALUE_SCALE, false, 0f), textY, VALUE_SCALE, textColor, false, 0f);
		if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
			g.fill(x + STAT_FIELD_W - 9, y + 8, x + STAT_FIELD_W - 9 + Math.max(2, hairline()), y + STAT_FIELD_H - 8, CYAN);
		}
	}

	/** 4 move pickers: name + type badge, same look as the card's move rows. */
	private void renderMovesBox(GuiGraphics g, double mx, double my) {
		infoBox(g, COL2_X, BIG_Y, COL2_W, BIG_H, "phantasmon.trade.screen.moves");
		for (int i = 0; i < 4; i++) {
			int rowX = COL2_X + 9;
			int rowY = MOVE_ROW_Y + i * MOVE_ROW_STEP;
			int rowW = COL2_W - 18;
			boolean open = openPicker == Picker.MOVE && activeMoveSlot == i;
			boolean hovered = inside(mx, my, rowX, rowY, rowW, MOVE_ROW_H);
			g.fill(rowX, rowY, rowX + rowW, rowY + MOVE_ROW_H, MOVE_BG);
			outline(g, rowX, rowY, rowW, MOVE_ROW_H, open ? CYAN : hovered ? CYAN2 : LINE_12);
			drawText(g, "▾", rowX + rowW - 24, rowY + (MOVE_ROW_H - 14) / 2f, VALUE_SCALE, open ? CYAN : MUTED, false, 0f);

			String moveId = moveIds[i] == null ? "" : moveIds[i];
			MoveTemplate template = moveId.isEmpty() ? null : Moves.getByName(moveId);
			boolean hasType = template != null && template.getElementalType() != null;
			int badgeHeight = typeBadgeHeight(VALUE_SCALE);
			float nameHeight = 7 * snapTextScale(VALUE_SCALE);
			float blockTop = rowY + (MOVE_ROW_H - nameHeight - (hasType ? 8 + badgeHeight : 0)) / 2f;
			drawText(g, fitText(moveDisplayName(moveId), rowW - 60, VALUE_SCALE, false, 0f), rowX + 12, blockTop,
					VALUE_SCALE, moveId.isEmpty() ? DIM : TEXT2, false, 0f);
			if (hasType) {
				drawTypeBadge(g, template.getElementalType(), rowX + 12, blockTop + nameHeight + 8, VALUE_SCALE);
			}
		}
	}

	private void renderFooter(GuiGraphics g, double mx, double my) {
		String status;
		int color;
		if (saving) {
			status = Component.translatable("phantasmon.pc.loading").getString();
			color = MUTED;
		} else if (statusMessage != null) {
			status = statusMessage;
			color = statusIsError ? ERROR_TEXT : READY_TEXT;
		} else {
			status = Component.translatable("phantasmon.pc.editor.hint").getString();
			color = DIM;
		}
		renderFooterBar(g, status, color, CANCEL_X - 27 - 16);
		renderButton(g, CANCEL_X, FOOTER_BUTTON_Y, CANCEL_W, FOOTER_BUTTON_H, "phantasmon.pc.edit.cancel", false,
				inside(mx, my, CANCEL_X, FOOTER_BUTTON_Y, CANCEL_W, FOOTER_BUTTON_H), 2f);
		boolean saveHovered = inside(mx, my, SAVE_X, FOOTER_BUTTON_Y, SAVE_W, FOOTER_BUTTON_H);
		renderPrimaryButtonFrame(g, SAVE_X, FOOTER_BUTTON_Y, SAVE_W, FOOTER_BUTTON_H, saveHovered, dirty);
		String save = upper(Component.translatable("phantasmon.pc.edit.save").getString());
		drawText(g, save, SAVE_X + (SAVE_W - textWidth(save, 2f, true, 1.5f)) / 2f, FOOTER_BUTTON_Y + (FOOTER_BUTTON_H - 14) / 2f,
				2f, dirty ? READY_TEXT : CYAN, true, 1.5f);
	}

	// ---- Picker popup ----

	private record PickerGeometry(int x, int y, int w, int listY, int visible) {
	}

	/** Under its field (or above it when there's no room), at least 380 px wide, kept inside the panel. */
	private PickerGeometry pickerGeometry() {
		int w = Math.max(pickerAnchorW, 380);
		int x = Math.min(pickerAnchorX, 1576 - w);
		int visible = Math.max(1, Math.min(DROPDOWN_MAX_VISIBLE, pickerSize()));
		int searchH = searchable() ? DROPDOWN_SEARCH_H : 0;
		int totalH = searchH + visible * DROPDOWN_ROW_H;
		int y = pickerAnchorY + pickerAnchorH + 4;
		if (y + totalH > 880) {
			y = pickerAnchorY - 4 - totalH;
		}
		y = Math.max(20, y);
		return new PickerGeometry(x, y, w, y + searchH, visible);
	}

	private void renderPicker(GuiGraphics g, int mouseX, int mouseY) {
		g.flush();
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(0, 0, 2000);
		pose.translate(originX, originY, 0);
		pose.scale(scale, scale, 1f);
		double mx = toCanvasX(mouseX);
		double my = toCanvasY(mouseY);

		PickerGeometry geo = pickerGeometry();
		List<String> values = pickerValues();
		int totalH = geo.listY() - geo.y() + geo.visible() * DROPDOWN_ROW_H;
		glowOutside(g, geo.x(), geo.y(), geo.w(), totalH, 0x000000, 0.6f, 10);
		g.fill(geo.x(), geo.y(), geo.x() + geo.w(), geo.y() + totalH, MOVE_BG);
		outline(g, geo.x(), geo.y(), geo.w(), totalH, CYAN);

		if (searchable()) {
			boolean empty = searchQuery.isEmpty();
			String text = empty ? Component.translatable("phantasmon.pc.edit.search_placeholder").getString() : searchQuery.toString();
			drawText(g, "⌕", geo.x() + 10, geo.y() + 15, VALUE_SCALE, MUTED, false, 0f);
			drawText(g, fitText(text, geo.w() - 60, VALUE_SCALE, false, 0f) + (empty || (System.currentTimeMillis() / 500) % 2 == 1 ? "" : "_"),
					geo.x() + 36, geo.y() + 15, VALUE_SCALE, empty ? DIM : WHITE, false, 0f);
			g.fill(geo.x(), geo.listY() - hairline(), geo.x() + geo.w(), geo.listY(), LINE_30);
		}

		int selected = pickerSelectedIndex();
		for (int i = 0; i < geo.visible(); i++) {
			int index = i + pickerScroll;
			if (index >= values.size()) {
				break;
			}
			int rowY = geo.listY() + i * DROPDOWN_ROW_H;
			boolean hovered = inside(mx, my, geo.x(), rowY, geo.w(), DROPDOWN_ROW_H);
			if (hovered) {
				g.fill(geo.x() + 1, rowY, geo.x() + geo.w() - 1, rowY + DROPDOWN_ROW_H, SLOT_HOVER_BG);
			} else if (index == selected) {
				g.fill(geo.x() + 1, rowY, geo.x() + geo.w() - 1, rowY + DROPDOWN_ROW_H, SLOT_SELECTED_BG);
			}
			renderPickerRow(g, values.get(index), index, geo.x() + 10, rowY, geo.w() - 24);
		}

		if (values.size() > geo.visible()) {
			int trackH = geo.visible() * DROPDOWN_ROW_H;
			int thumbH = Math.max(20, trackH * geo.visible() / values.size());
			int maxOffset = values.size() - geo.visible();
			int thumbY = geo.listY() + (trackH - thumbH) * pickerScroll / Math.max(1, maxOffset);
			g.fill(geo.x() + geo.w() - 7, thumbY, geo.x() + geo.w() - 3, thumbY + thumbH, CYAN2);
		}
		g.flush();
		pose.popPose();
	}

	private void renderPickerRow(GuiGraphics g, String value, int index, int x, int rowY, int w) {
		float textY = rowY + (DROPDOWN_ROW_H - 14) / 2f;
		switch (openPicker) {
			case ABILITY -> drawText(g, fitText(abilityDisplayName(value), w, VALUE_SCALE, false, 0f), x, textY, VALUE_SCALE, WHITE, false, 0f);
			case ITEM -> {
				float textX = x;
				ItemStack stack = PokemonGuiRendering.heldItemStack(value);
				if (!stack.isEmpty()) {
					PokemonGuiRendering.renderItemIcon(g, stack, textX, rowY + 8, 20);
					textX += 28;
				}
				drawText(g, fitText(itemDisplayName(value), x + w - textX, VALUE_SCALE, false, 0f), textX, textY,
						VALUE_SCALE, value.isEmpty() ? DIM : WHITE, false, 0f);
			}
			case NATURE -> renderNatureValue(g, value, x, x + w, textY);
			case TERA -> {
				ElementalType type = TERA_OPTIONS.get(index);
				if (type != null) {
					drawTypeBadge(g, type, x, rowY + (DROPDOWN_ROW_H - typeBadgeHeight(VALUE_SCALE)) / 2f, VALUE_SCALE);
				} else {
					drawText(g, Component.translatable("phantasmon.pc.editor.tera_default").getString(), x, textY, VALUE_SCALE, DIM, false, 0f);
				}
			}
			case MOVE -> {
				MoveTemplate template = value.isEmpty() ? null : Moves.getByName(value);
				float reserved = 0;
				if (template != null && template.getElementalType() != null) {
					int badgeWidth = typeBadgeWidth(template.getElementalType(), 1f);
					drawTypeBadge(g, template.getElementalType(), x + w - badgeWidth, rowY + (DROPDOWN_ROW_H - typeBadgeHeight(1f)) / 2f, 1f);
					reserved = badgeWidth + 10;
				}
				drawText(g, fitText(moveDisplayName(value), w - reserved, VALUE_SCALE, false, 0f), x, textY, VALUE_SCALE,
						value.isEmpty() ? DIM : WHITE, false, 0f);
			}
			default -> {
			}
		}
	}

	/** « QUITTER SANS ENREGISTRER ? » — same modal as the trade/PC confirmations. */
	private void renderQuitModal(GuiGraphics g, int mouseX, int mouseY) {
		beginModalLayer(g, 1f);
		double mx = toCanvasX(mouseX);
		double my = toCanvasY(mouseY);
		renderModalCard(g, 540, 370, 520, 160, 1f);
		drawCentered(g, upper(Component.translatable("phantasmon.pc.editor.quit_title").getString()), 800, 397, 2f, TITLE, true, 2f);
		drawCentered(g, Component.translatable("phantasmon.pc.editor.quit_text").getString(), 800, 437, 2f, TEXT2, false, 0f);
		renderButton(g, 652, 474, 150, 37, "phantasmon.trade.screen.cancel", false, inside(mx, my, 652, 474, 150, 37), 2f);
		renderButton(g, 812, 474, 136, 37, "phantasmon.trade.screen.quit", true, inside(mx, my, 812, 474, 136, 37), 2f);
		endModalLayer(g);
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	private static String capitalize(String value) {
		return value == null || value.isEmpty() ? String.valueOf(value) : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
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

	private static String errorMessage(Throwable throwable) {
		Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
		String key = cause instanceof BackendApiException apiException
				? BackendErrorMessages.translationKey(apiException.errorCode())
				: "phantasmon.error.network";
		return Component.translatable(key).getString();
	}
}
