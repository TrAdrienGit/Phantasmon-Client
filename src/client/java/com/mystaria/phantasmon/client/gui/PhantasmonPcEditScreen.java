package com.mystaria.phantasmon.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;

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

import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.network.BackendApiException;
import com.mystaria.phantasmon.client.network.BackendErrorMessages;
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
 * <p><b>Re-skinned to match the PC screen's visual language</b> (Adrien
 * 2026-09-27, after the PC screen's texture pass): the outer panel and every
 * {@link Button} use the same nine-slice sprites as {@link PhantasmonPcScreen}
 * ({@link PhantasmonPcScreen#SPRITE_PANEL}/{@code SPRITE_BUTTON}) via a small
 * {@link SpectralButton} subclass that overrides {@code renderWidget} — vanilla
 * {@link Button} has no public way to swap its texture, so this is the
 * standard way to reskin one. {@link EditBox} keeps its default vanilla look
 * (reskinning it would mean reimplementing text-cursor/selection rendering,
 * out of scope here).
 *
 * <p><b>Two-column layout</b> (Adrien's first live-test feedback: the
 * original single-column form overflowed past the bottom of the screen) —
 * left column is identity/behavior fields, right column is IVs/EVs/moves/
 * import. Every single-line field in the left column (nickname/level/
 * ability/item/nature/Tera) now has its label on the <em>same row</em> as its
 * input instead of on the row above (Adrien's second round of feedback); the
 * label column width is measured from the longest translated label so it
 * still lines up correctly in either language.
 *
 * <p><b>Nature/Tera type use a real dropdown</b>, implemented as a small
 * custom popup list ({@link #openDropdown}) since vanilla Minecraft has no
 * built-in dropdown/combo-box widget. It is always drawn last, strictly after
 * every other panel/label/widget (Adrien: it must render above everything
 * else) — vanilla has no z-order concept, draw order *is* z-order, so
 * {@link #renderDropdown} runs after {@code super.render(...)} in
 * {@link #render}.
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

	private static final int PANEL_BORDER = 0xFF2FB7C9;
	private static final int LABEL_COLOR = 0x9FD9E6;
	private static final int DROPDOWN_BG = 0xFF0B0F14;
	private static final int DROPDOWN_ROW_HOVER = 0xFF224466;
	private static final int DROPDOWN_ROW_SELECTED = 0xFF17303C;
	private static final int DROPDOWN_ROW_H = 14;
	private static final int DROPDOWN_MAX_VISIBLE = 8;

	/** Consistent spacing used throughout this form (Adrien: margins/padding must be coherent across every element). */
	private static final int ROW_H = 22;
	private static final int FIELD_H = 16;
	private static final int LABEL_GAP = 6;

	private enum DropdownKind { NONE, NATURE, TERA }

	private final PokemonClient pokemonClient;
	private final AuthSession session;
	private final PokemonDto original;
	private final PhantasmonPcScreen parent;

	private EditBox nicknameBox;
	private EditBox levelBox;
	private EditBox abilityBox;
	private EditBox itemBox;
	private final EditBox[] ivBoxes = new EditBox[6];
	private final EditBox[] evBoxes = new EditBox[6];
	private final EditBox[] moveBoxes = new EditBox[4];

	private int natureIndex;
	private ElementalType teraType;
	private boolean shiny;

	private Button natureValueButton;
	private Button teraValueButton;
	private Button shinyButton;

	private DropdownKind openDropdown = DropdownKind.NONE;
	private int dropdownX, dropdownY, dropdownW;
	private int dropdownScroll;

	private boolean saving;
	private Component statusMessage = Component.empty();

	private int panelX, panelY, panelW, panelH;
	private final List<LabelSpot> labels = new ArrayList<>();
	/**
	 * Widgets rendered manually, in this exact order, instead of via
	 * {@code addRenderableWidget} — Adrien reported the dropdown still painting
	 * behind button/field text even after a {@code graphics.flush()} call placed
	 * right before it, meaning something in GuiGraphics's own text-batching
	 * doesn't respect a simple call-order + flush guarantee here. Registering
	 * widgets via {@link #addWidget} instead (input/focus/narration only, no
	 * auto-render) and rendering them ourselves in a single straight-line loop —
	 * with the dropdown drawn immediately after, in the same loop's continuation —
	 * removes any dependency on Mojang's internal batch-flush ordering: it's just
	 * one Java method executing top to bottom.
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
		Map<String, Object> data = original.data() != null ? original.data() : Map.of();
		shiny = original.isShiny();
		natureIndex = Math.max(0, indexOf(NATURES, original.nature()));
		Object teraRaw = data.get("teraType");
		teraType = teraRaw != null ? safeGetType(teraRaw.toString()) : null;

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

		// Label column width measured from the longest of the 6 inline labels, so the
		// fields all start at the same x regardless of which language is active.
		int labelW = 0;
		for (String key : new String[] { "phantasmon.pc.edit.nickname", "phantasmon.pc.edit.level",
				"phantasmon.pc.edit.ability", "phantasmon.pc.edit.item", "phantasmon.pc.edit.nature", "phantasmon.pc.edit.tera" }) {
			labelW = Math.max(labelW, font.width(Component.translatable(key)));
		}
		int fieldX = leftX + labelW + LABEL_GAP;
		int fieldW = leftX + colW - fieldX;

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
		abilityBox = addField(fieldX, y, fieldW, original.ability());
		y += ROW_H;

		labels.add(new LabelSpot(Component.translatable("phantasmon.pc.edit.item"), leftX, y + 4));
		itemBox = addField(fieldX, y, fieldW, stringOf(data.get("heldItem")));
		y += ROW_H;

		labels.add(new LabelSpot(Component.translatable("phantasmon.pc.edit.nature"), leftX, y + 4));
		int natureDropdownY = y + FIELD_H;
		// Wider than the trigger button itself — "Adamant (+Atk / -SpA)"-style entries
		// don't fit in the narrow field column, so the dropdown list overhangs it a bit.
		int natureDropdownW = Math.min(panelW - (fieldX - panelX) - 10, 190);
		natureValueButton = addTracked(new SpectralButton(fieldX, y, fieldW, FIELD_H, Component.empty(),
				b -> toggleDropdown(DropdownKind.NATURE, fieldX, natureDropdownY, natureDropdownW)));
		updateNatureLabel();
		y += ROW_H;

		shinyButton = addTracked(new SpectralButton(leftX, y, colW, FIELD_H, Component.empty(), b -> toggleShiny()));
		updateShinyLabel();
		y += ROW_H;

		labels.add(new LabelSpot(Component.translatable("phantasmon.pc.edit.tera"), leftX, y + 4));
		int teraDropdownY = y + FIELD_H;
		teraValueButton = addTracked(new SpectralButton(fieldX, y, fieldW, FIELD_H, Component.empty(),
				b -> toggleDropdown(DropdownKind.TERA, fieldX, teraDropdownY, fieldW)));
		updateTeraLabel();
		y += ROW_H;
		int leftBottom = y;

		// ---- Right column: IVs / EVs / moves / import ----
		// Each block's next label is positioned from the *actual* box height (FIELD_H)
		// rather than a flat ROW_H advance — the two didn't match before, so a label
		// would start drawing while the previous row's boxes were still a few pixels
		// tall below it (Adrien: "EVs"/"Capacités" titles ended up under the boxes above).
		int labelToBoxGap = 10;
		int boxToNextLabelGap = 6;
		y = top;
		// "IVs (0-31)" header, then the HP/ATK/.../SPE column labels on their OWN line
		// below it (Adrien: these were previously drawn at the exact same y as the
		// header, overlapping it — visible in his screenshot as garbled "IVs"/"HP" text).
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
		List<Object> moves = asList(data.get("moves"));
		int moveColW = (colW - 6) / 2;
		int moveRowGap = 4;
		for (int i = 0; i < 4; i++) {
			int col = i % 2;
			int row = i / 2;
			String value = i < moves.size() ? String.valueOf(moves.get(i)) : "";
			moveBoxes[i] = addField(rightX + col * (moveColW + 6), moveBoxY + row * (FIELD_H + moveRowGap), moveColW, value);
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

	private void toggleDropdown(DropdownKind kind, int x, int y, int w) {
		if (openDropdown == kind) {
			openDropdown = DropdownKind.NONE;
			return;
		}
		openDropdown = kind;
		dropdownX = x;
		dropdownY = y;
		dropdownW = w;
		dropdownScroll = 0;
	}

	private List<String> dropdownOptions() {
		return switch (openDropdown) {
			case NATURE -> Arrays.asList(NATURES).stream().map(PhantasmonPcEditScreen::natureOptionLabel).toList();
			case TERA -> TERA_OPTIONS.stream()
					.map(t -> t == null ? Component.translatable("phantasmon.pc.edit.tera_none").getString() : t.getDisplayName().getString())
					.toList();
			case NONE -> List.of();
		};
	}

	/** e.g. "Adamant (+Atk / -SpA)" — same +Bonus/-Malus format as the read-only PC detail panel (Adrien: it must show here too, both in the dropdown list and once selected). */
	private static String natureOptionLabel(String natureId) {
		String display = capitalize(natureId);
		NatureModifiers.Modifier modifier = NatureModifiers.get(natureId);
		return modifier == null ? display : display + " (+" + modifier.boosted() + " / -" + modifier.reduced() + ")";
	}

	private int dropdownSelectedIndex() {
		return switch (openDropdown) {
			case NATURE -> natureIndex;
			case TERA -> TERA_OPTIONS.indexOf(teraType);
			case NONE -> -1;
		};
	}

	private void selectDropdownOption(int index) {
		if (openDropdown == DropdownKind.NATURE) {
			natureIndex = index;
			updateNatureLabel();
		} else if (openDropdown == DropdownKind.TERA) {
			teraType = TERA_OPTIONS.get(index);
			updateTeraLabel();
		}
	}

	private void updateNatureLabel() {
		natureValueButton.setMessage(Component.literal(natureOptionLabel(NATURES[natureIndex]) + " ▾"));
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
			int visible = Math.min(DROPDOWN_MAX_VISIBLE, options.size());
			int listH = visible * DROPDOWN_ROW_H;
			if (mouseX >= dropdownX && mouseX < dropdownX + dropdownW && mouseY >= dropdownY && mouseY < dropdownY + listH) {
				int row = (int) ((mouseY - dropdownY) / DROPDOWN_ROW_H) + dropdownScroll;
				if (row >= 0 && row < options.size()) {
					selectDropdownOption(row);
				}
			}
			// Any click while a dropdown is open is consumed by it — either it picked an
			// option above, or it just dismisses the list; it never also activates
			// whatever widget happens to be underneath (avoids immediately reopening the
			// same toggle button that was just clicked to close it).
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
		itemBox.setValue(stringOf(request.data().get("heldItem")));
		abilityBox.setValue(request.ability());
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
			moveBoxes[i].setValue(i < moves.size() ? String.valueOf(moves.get(i)) : "");
		}
		statusMessage = Component.translatable("phantasmon.pc.edit.import_done");
	}

	private void save() {
		String ability = abilityBox.getValue().trim();
		if (ability.isEmpty()) {
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
		for (EditBox box : moveBoxes) {
			String value = box.getValue().trim().toLowerCase(Locale.ROOT);
			if (!value.isEmpty()) {
				moves.add(value);
			}
		}
		data.put("moves", moves);

		String nickname = nicknameBox.getValue().trim();
		if (nickname.isEmpty()) {
			data.remove("nickname");
		} else {
			data.put("nickname", nickname);
		}

		String item = itemBox.getValue().trim().toLowerCase(Locale.ROOT);
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
		PokemonUpdateRequestDto request = PokemonUpdateRequestDto.editing(data, level, nature, ability.toLowerCase(Locale.ROOT), shiny);
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

		// Widgets are rendered manually here, in this exact loop, rather than via
		// addRenderableWidget/super.render(). Adrien's screenshot pinpointed the real
		// bug precisely: the dropdown's *background* correctly covers the buttons
		// underneath (their sprite is hidden), but the buttons' own *text* still
		// painted on top of the dropdown's text — text specifically goes through a
		// separate deferred buffer in GuiGraphics/Font that a plain call-order + flush
		// didn't reliably beat before. Belt and suspenders this time: any widget whose
		// bounds the open dropdown would cover is skipped entirely (no render call at
		// all, so neither its background nor its text can possibly appear), and
		// flush() still runs afterward for anything not caught by that bounds check.
		int dropdownListHeight = Math.min(DROPDOWN_MAX_VISIBLE, dropdownOptions().size()) * DROPDOWN_ROW_H;
		for (Renderable widget : orderedWidgets) {
			if (widget instanceof AbstractWidget abstractWidget && coveredByDropdown(abstractWidget, dropdownListHeight)) {
				continue;
			}
			widget.render(graphics, mouseX, mouseY, partialTick);
		}
		graphics.flush();

		renderDropdown(graphics, mouseX, mouseY);
	}

	private boolean coveredByDropdown(AbstractWidget widget, int dropdownListHeight) {
		if (openDropdown == DropdownKind.NONE) {
			return false;
		}
		return widget.getX() < dropdownX + dropdownW && widget.getX() + widget.getWidth() > dropdownX
				&& widget.getY() < dropdownY + dropdownListHeight && widget.getY() + widget.getHeight() > dropdownY;
	}

	private void renderDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
		if (openDropdown == DropdownKind.NONE) {
			return;
		}
		List<String> options = dropdownOptions();
		int visible = Math.min(DROPDOWN_MAX_VISIBLE, options.size());
		int listH = visible * DROPDOWN_ROW_H;
		int selected = dropdownSelectedIndex();

		graphics.fill(dropdownX, dropdownY, dropdownX + dropdownW, dropdownY + listH, DROPDOWN_BG);
		graphics.renderOutline(dropdownX, dropdownY, dropdownW, listH, PANEL_BORDER);
		for (int i = 0; i < visible; i++) {
			int optionIndex = i + dropdownScroll;
			if (optionIndex >= options.size()) {
				break;
			}
			int rowY = dropdownY + i * DROPDOWN_ROW_H;
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
		return value.isEmpty() ? value : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
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

	/** A {@link Button} skinned with the PC screen's own cyan nine-slice sprite instead of vanilla's default button texture, so this form matches the same visual language (Adrien 2026-09-27). */
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
