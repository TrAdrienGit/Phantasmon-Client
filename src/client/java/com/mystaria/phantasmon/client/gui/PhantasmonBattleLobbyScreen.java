package com.mystaria.phantasmon.client.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.mystaria.phantasmon.client.battle.BattleLobbyState;
import com.mystaria.phantasmon.client.battle.LiveBattleController;
import com.mystaria.phantasmon.client.network.BackendErrorMessages;
import com.mystaria.phantasmon.client.pokemon.PokemonDto;

/**
 * Battle lobby — team preview before a live battle (Adrien 2026-10-04), same look and layout as the trade screen
 * ({@link PhantasmonTradeScreen}): our team on the left rail (click = lead), the opponent's preview on the right
 * rail (models and names only), and in the middle one card per player with their 3D model, their team source,
 * and our lead (theirs stays hidden). Header: PRÊT; footer: lobby timer and QUITTER.
 *
 * <p>Every state shown here is the server's ({@link BattleLobbyState}) — clicks only send requests through
 * {@link LiveBattleController}.
 */
public final class PhantasmonBattleLobbyScreen extends PhantasmonCanvasScreen {

	private static final int RIGHT_CARD_OFFSET = 586;
	private static final int RIGHT_RAIL_OFFSET = 1376;
	/** Player model window inside each card. */
	private static final int VIEW_Y = 117;
	private static final int VIEW_H = 380;
	/** Pixels per block for the player models (~270 px tall). */
	private static final float PLAYER_MODEL_SCALE = 150f;
	private static final int TIMER_X = 1265;
	private static final int TIMER_W = 200;
	private static final int MUSIC_X = 1055;
	private static final int MUSIC_W = 200;
	/**
	 * The music screen and the format picker open over the lobby: leaving the lobby screen for them is not leaving the
	 * lobby.
	 */
	private boolean openingOverlay;

	// ---- Footer bar (2026-11-27): the battle's rules (format, field), then its look (scene, ambiance, intro), each
	// button opening its own window over the lobby; music, timer and quit on the right ----
	private static final int BAR_Y = 849;
	private static final int BAR_H = 28;
	private static final int FORMAT_X = 27;
	private static final int RULE_W = 215;
	private static final int FIELD_X = FORMAT_X + RULE_W + 8;
	/** The thin line between the rules and the look. */
	private static final int GROUP_SPLIT_X = FIELD_X + RULE_W + 10;
	private static final int LOOK_W = 180;
	private static final int SCENE_X = GROUP_SPLIT_X + 10;
	private static final int AMBIANCE_X = SCENE_X + LOOK_W + 8;
	private static final int INTRO_X = AMBIANCE_X + LOOK_W + 8;
	// ---- Team rails: 2×3 slots, taller since the hint boxes under them are gone (2026-11-27) ----
	private static final int SLOT_W = 85;
	private static final int SLOT_H = 228;
	private static final SlotLayout LOBBY_SLOT = new SlotLayout(20, 156, SLOT_MODEL_SCALE * 1.08f, 182, 59, 204, 20);
	// ---- Lead box and team switch (2026-11-28): the switch went down to the card's foot, the lead box grew ----
	private static final int LEAD_BOX_H = 252;
	private static final int SWITCH_Y = 768;
	// ---- League badges beside the player model: 3 columns of 6 slots ----
	private static final int BADGE_COLUMNS = 3;
	private static final int BADGE_ROWS = 6;
	private static final int BADGE_SLOT = 44;
	private static final int BADGE_GAP = 6;
	private static final int BADGE_PANEL_W = BADGE_COLUMNS * BADGE_SLOT + (BADGE_COLUMNS - 1) * BADGE_GAP + 16;
	private static final int BADGE_PANEL_H = 24 + BADGE_ROWS * BADGE_SLOT + (BADGE_ROWS - 1) * BADGE_GAP + 10;
	// ---- READY button, centred on the header (2026-11-28: wider, taller than the plates) ----
	private static final int READY_W = 220;
	private static final int READY_H = 48;
	private static final int READY_X = 800 - READY_W / 2;
	private static final int READY_Y = 15;
	/** The READY button's glow, sparks and burst (2026-11-28). */
	private final ReadyButtonFx readyFx = new ReadyButtonFx();
	/** The own slot last hovered: entering a slot makes its Pokémon play its cry animation. */
	private int cryHovered = -1;
	/** The badge under the cursor this frame (its tooltip is drawn over everything), and where its slot is. */
	private TrainerBadges.Badge hoveredBadge;
	private int hoveredBadgeX;
	private int hoveredBadgeY;
	/** Issues of the own Pokémon under the cursor (the message pop-up explains them), refreshed every frame. */
	private List<BattleLobbyState.Issue> hoveredIssues = List.of();

	private final LiveBattleController controller;
	private boolean quitConfirmOpen;

	public PhantasmonBattleLobbyScreen(LiveBattleController controller) {
		super(Component.translatable("phantasmon.battle.lobby.title"));
		this.controller = controller;
		// Intros and ambiances won since the login (TODO-37) show in the lists.
		com.mystaria.phantasmon.client.pokemon.RewardContent.refresh();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void tick() {
		if (controller.lobby() == null) {
			onClose();
		}
	}

	@Override
	public void removed() {
		if (openingOverlay) {
			openingOverlay = false;
			return;
		}
		controller.onLobbyScreenRemoved();
	}

	// =====================================================================
	// Input
	// =====================================================================

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		BattleLobbyState state = controller.lobby();
		if (button != 0 || state == null) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		double x = toCanvasX(mouseX);
		double y = toCanvasY(mouseY);
		if (quitConfirmOpen) {
			if (inside(x, y, 695, 476, 103, 28)) {
				quitConfirmOpen = false;
			} else if (inside(x, y, 808, 476, 97, 28)) {
				controller.lobbyLeave();
				onClose();
			}
			return true;
		}
		if (inside(x, y, READY_X, READY_Y, READY_W, READY_H) && (state.ownReady() || state.teamsGenerated())) {
			com.mystaria.phantasmon.client.audio.PhantasmonSounds.play(com.mystaria.phantasmon.client.audio.PhantasmonSounds.Sfx.PRESSING_A);
			readyFx.press(READY_X, READY_Y, READY_W, READY_H, state.ownReady() ? CYAN : READY_TEXT);
			controller.lobbyToggleReady();
		} else if (inside(x, y, FORMAT_X, BAR_Y, RULE_W, BAR_H)) {
			if (!state.formatFixed()) {
				openOverlay(new PhantasmonFormatPickerScreen(this, controller));
			}
		} else if (inside(x, y, FIELD_X, BAR_Y, RULE_W, BAR_H)) {
			if (fieldEditable(state)) {
				openOverlay(fieldPicker());
			}
		} else if (inside(x, y, SCENE_X, BAR_Y, LOOK_W, BAR_H)) {
			if (!state.ambianceFixed()) {
				openOverlay(scenePicker(false));
			}
		} else if (inside(x, y, AMBIANCE_X, BAR_Y, LOOK_W, BAR_H)) {
			if (!state.ambianceFixed()) {
				openOverlay(scenePicker(true));
			}
		} else if (inside(x, y, INTRO_X, BAR_Y, LOOK_W, BAR_H)) {
			if (!state.introFixed()) {
				openOverlay(introPicker());
			}
		} else if (inside(x, y, 1475, BAR_Y, 97, BAR_H)) {
			quitConfirmOpen = true;
		} else if (inside(x, y, MUSIC_X, BAR_Y, MUSIC_W, BAR_H)) {
			openOverlay(new com.mystaria.phantasmon.client.gui.menu.PhantasmonMenuScreen(this, com.mystaria.phantasmon.client.gui.menu.PhantasmonMenuScreen.Category.MUSIC));
		} else if (inside(x, y, TIMER_X, BAR_Y, TIMER_W, BAR_H)) {
			controller.lobbyEnableTimer();
		} else if (inside(x, y, CARD_X + 9, SWITCH_Y, 559, 48)) {
			controller.lobbySwitchTeam();
		} else {
			int slot = slotAt(x, y);
			PokemonDto clicked = slot >= 0 ? state.ownSlot(slot) : null;
			if (clicked != null) {
				PokemonGuiRendering.playCry(clicked.species(), slotX(slot) + SLOT_W / 2f);
			}
			if (slot >= 0 && state.pickMode()) {
				controller.lobbyTogglePick(slot);
			} else if (slot >= 0) {
				controller.lobbySelectLead(slot);
			}
			// Right rail: read-only preview.
		}
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 256) {
			// Échap = same as QUITTER: opens the confirmation, or closes it again.
			quitConfirmOpen = !quitConfirmOpen;
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/** Own rail slot index under (x, y), -1 if none (same 2×3 grid as the trade screen). */
	private static int slotAt(double x, double y) {
		for (int i = 0; i < BattleLobbyState.TEAM_SIZE; i++) {
			if (inside(x, y, slotX(i), slotY(i), SLOT_W, SLOT_H)) {
				return i;
			}
		}
		return -1;
	}

	private static int slotX(int index) {
		return 24 + (index % 2) * 92;
	}

	private static int slotY(int index) {
		return 117 + (index / 2) * (SLOT_H + 7);
	}

	// =====================================================================
	// Rendering
	// =====================================================================

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		BattleLobbyState state = controller.lobby();
		renderWorldBackdrop(graphics, mouseX, mouseY, partialTick);
		if (state == null) {
			return;
		}
		double mx = quitConfirmOpen ? -1 : toCanvasX(mouseX);
		double my = quitConfirmOpen ? -1 : toCanvasY(mouseY);
		int hovered = slotAt(mx, my);
		hoveredIssues = state.ownIssues(hovered);
		if (hovered != cryHovered) {
			cryHovered = hovered;
			PokemonDto entered = hovered >= 0 ? state.ownSlot(hovered) : null;
			if (entered != null) {
				PokemonGuiRendering.playCry(entered.species(), slotX(hovered) + SLOT_W / 2f);
			}
		}
		hoveredBadge = null;
		double lookX = toCanvasX(mouseX);
		double lookY = toCanvasY(mouseY);

		beginCanvas(graphics);
		renderRootPanel(graphics);
		renderHeader(graphics, state, mx, my);
		// The Pokémon play their idle animation here (a slight bob for those without one).
		PokemonGuiRendering.beginAnimated("lobby");
		try {
			renderOwnRail(graphics, state, mx, my);
			renderOpponentRail(graphics, state);
			renderPlayerCard(graphics, state, true, lookX, lookY, mx, my);
			renderPlayerCard(graphics, state, false, lookX, lookY, mx, my);
		} finally {
			PokemonGuiRendering.endAnimated();
		}
		renderFooter(graphics, state, mx, my);
		readyFx.render(graphics, READY_X, READY_Y, READY_W, READY_H,
				inside(mx, my, READY_X, READY_Y, READY_W, READY_H) && (state.ownReady() || state.teamsGenerated()),
				state.ownReady() ? READY_TEXT : CYAN);
		endCanvas(graphics);
		// Over everything, the player models included.
		beginModalLayer(graphics, 0f);
		renderMessage(graphics, state);
		renderBadgeTooltip(graphics);
		endModalLayer(graphics);

		if (quitConfirmOpen) {
			renderQuitModal(graphics, mouseX, mouseY);
		}
	}

	// ---- Header ----

	private void renderHeader(GuiGraphics g, BattleLobbyState state, double mx, double my) {
		renderHeaderPlates(g, READY_X - 14 - 15);
		drawText(g, upper(controller.localPlayerName()), 31, 32, 2f, WHITE, true, 1.5f);
		String opponent = upper(state.opponentName());
		float opponentWidth = textWidth(opponent, 2f, true, 1.5f);
		drawText(g, opponent, 1569 - opponentWidth, 32, 2f, WHITE, true, 1.5f);
		if (state.opponentReady()) {
			String ready = Component.translatable("phantasmon.battle.lobby.ready").getString();
			drawText(g, ready, 1569 - opponentWidth - 16 - textWidth(ready, 1f, true, 2f), 36, 1f, READY_TEXT, true, 2f);
		}
		if (state.ownReady()) {
			String ready = Component.translatable("phantasmon.battle.lobby.ready").getString();
			drawText(g, ready, 31 + textWidth(upper(controller.localPlayerName()), 2f, true, 1.5f) + 16, 36, 1f, READY_TEXT, true, 2f);
		}

		boolean waiting = !state.ownReady() && !state.teamsGenerated();
		renderReadyButton(g, state.ownReady(), waiting, !waiting && inside(mx, my, READY_X, READY_Y, READY_W, READY_H));
	}

	/**
	 * The READY button: the menu's main-action frame (green once ready), a little wider than before and with an accent
	 * rim that breathes slowly and a light running along its bottom edge while it can be pressed, so it stands out
	 * from the header plates. Its hover and click effects are {@link ReadyButtonFx}'s.
	 */
	private void renderReadyButton(GuiGraphics g, boolean ready, boolean waiting, boolean hovered) {
		int x = READY_X;
		int y = READY_Y;
		int w = READY_W;
		int h = READY_H;
		renderPrimaryButtonFrame(g, x, y, w, h, hovered, ready);
		if (!waiting) {
			int accent = ready ? READY_TEXT : CYAN;
			float breath = 0.5f + 0.5f * (float) Math.sin(clockSeconds() * 2.2f);
			outline(g, x - 2, y - 2, w + 4, h + 4, withAlpha(accent, (hovered ? 0.75f : 0.35f) + 0.25f * breath));
			renderGlint(g, x + 2, y + h - 3, w - 4, 2, 4.5f, 0.7f, accent);
		}
		String label = ready ? Component.translatable("phantasmon.battle.lobby.ready").getString()
				: upper(Component.translatable("phantasmon.battle.lobby.ready_button").getString());
		float width = textWidth(label, 2f, true, 1f);
		drawText(g, label, x + (w - width) / 2f, y + 17, 2f, ready ? READY_TEXT : waiting ? DIM : CYAN, true, 1f);
	}

	/** Seconds on a looping clock, for the lobby's idle animations. */
	private static float clockSeconds() {
		return (System.currentTimeMillis() % 3_600_000L) / 1000f;
	}

	// ---- Idle animations (2026-11-28): small movements that keep the lobby alive ----

	/** A short light running along a horizontal line ({@code x}..{@code x + w} at {@code y}), every {@code period} s. */
	private void renderGlint(GuiGraphics g, int x, int y, int w, int thickness, float period, float delay, int color) {
		float phase = ((clockSeconds() + delay) % period) / period;
		float travel = phase * 1.6f - 0.3f; // part of the period with no light
		if (travel < 0f || travel > 1f) {
			return;
		}
		int center = x + Math.round(travel * w);
		for (int i = -30; i <= 30; i += 3) {
			float alpha = 0.6f * (1f - Math.abs(i) / 31f);
			int l = Math.max(x, center + i);
			int r = Math.min(x + w, center + i + 3);
			if (r > l) {
				g.fill(l, y, r, y + thickness, withAlpha(color | 0xFF000000, alpha));
			}
		}
	}

	/** Dust motes drifting up through a player's model window, behind the model. */
	private void renderDust(GuiGraphics g, int x, int y, int w, int h, int seed, int color) {
		float t = clockSeconds();
		for (int i = 0; i < 16; i++) {
			int hash = (i * 73856093) ^ (seed * 19349663);
			float fx = ((hash >>> 3) & 1023) / 1023f;
			float speed = 8f + ((hash >>> 13) & 15);
			float start = ((hash >>> 17) & 1023) / 1023f;
			float rise = ((t * speed / h) + start) % 1f;
			float px = x + 6 + fx * (w - 12) + (float) Math.sin(t * 0.7f + i) * 6f;
			float py = y + h - 6 - rise * (h - 12);
			float alpha = (float) Math.sin(rise * Math.PI) * 0.35f;
			int size = 1 + ((hash >>> 23) & 1);
			g.fill(Math.round(px), Math.round(py), Math.round(px) + size, Math.round(py) + size, withAlpha(color | 0xFF000000, alpha));
		}
	}

	// ---- Rails ----

	private void renderOwnRail(GuiGraphics g, BattleLobbyState state, double mx, double my) {
		// The names are in the header: the rails just say whose team (2026-11-28).
		String title = Component.translatable("phantasmon.battle.lobby.team_own").getString();
		if (state.pickMode() && state.ownTeamSize() > 0) {
			title += "  ·  " + state.ownPicks().size() + "/" + state.battleSize();
		}
		renderRailFrame(g, 15, 72, 194, 758, title, null, true);
		renderGlint(g, 24, 106, 176, 2, 7f, 1.5f, 0xFFFFFF);
		int hovered = state.ownReady() ? -1 : slotAt(mx, my);
		for (int i = 0; i < BattleLobbyState.TEAM_SIZE; i++) {
			PokemonDto pokemon = state.ownSlot(i);
			int pick = state.pickMode() ? state.ownPickNumber(i) : 0;
			boolean chosen = state.pickMode() ? pick > 0 : state.ownLeadSlot(i) >= 0;
			SlotLook look = pokemon != null && chosen ? SlotLook.SELECTED
					: (i == hovered && pokemon != null ? SlotLook.HOVER : SlotLook.NORMAL);
			renderSlot(g, slotX(i), slotY(i), SLOT_W, SLOT_H, pokemon, look, LOBBY_SLOT);
			if (pokemon != null && chosen) {
				// Chosen (lead / picked): a slow breathing rim.
				outline(g, slotX(i) - 1, slotY(i) - 1, SLOT_W + 2, SLOT_H + 2,
						withAlpha(CYAN, 0.25f + 0.25f * (float) Math.sin(clockSeconds() * 2.4f + i)));
			}
			if (pokemon != null && !state.ownIssues(i).isEmpty()) {
				renderRuleBreak(g, slotX(i), slotY(i));
			}
			if (pokemon != null && pick > 0) {
				// The pick order: in doubles 1 and 2 lead, on the left and on the right; in singles 1 leads.
				String label = !state.doubles() ? (pick == 1 ? "1 · " + Component.translatable("phantasmon.battle.lobby.lead_first").getString()
						: String.valueOf(pick))
						: pick == 1 ? "1 · " + Component.translatable("phantasmon.battle.lobby.lead_left").getString()
						: pick == 2 ? "2 · " + Component.translatable("phantasmon.battle.lobby.lead_right").getString()
						: String.valueOf(pick);
				drawText(g, label, slotX(i) + 6, slotY(i) + 6, 1f, CYAN, true, 1f);
			}
		}
	}

	private void renderOpponentRail(GuiGraphics g, BattleLobbyState state) {
		renderRailFrame(g, 15 + RIGHT_RAIL_OFFSET, 72, 194, 758,
				Component.translatable("phantasmon.battle.lobby.team_opponent").getString(), null, true);
		renderGlint(g, 24 + RIGHT_RAIL_OFFSET, 106, 176, 2, 7f, 5f, 0xFFFFFF);
		for (int i = 0; i < BattleLobbyState.TEAM_SIZE; i++) {
			if (state.randomTeam()) {
				renderHiddenSlot(g, slotX(i) + RIGHT_RAIL_OFFSET, slotY(i));
				continue;
			}
			renderSlot(g, slotX(i) + RIGHT_RAIL_OFFSET, slotY(i), SLOT_W, SLOT_H, state.opponentSlot(i), SlotLook.NORMAL, LOBBY_SLOT, false);
			if (state.opponentSlot(i) != null && state.opponentFlagged(i)) {
				renderRuleBreak(g, slotX(i) + RIGHT_RAIL_OFFSET, slotY(i));
			}
		}
	}

	/** A random format's slot: the team is generated when the battle starts, unknown to both until then. */
	private void renderHiddenSlot(GuiGraphics g, int x, int y) {
		renderSlot(g, x, y, SLOT_W, SLOT_H, null, SlotLook.NORMAL, LOBBY_SLOT, false);
		drawCentered(g, "?", x + SLOT_W / 2f, y + SLOT_H / 2f - 30, 6f, TITLE, true, 0f);
	}

	// ---- Player cards ----

	private void renderPlayerCard(GuiGraphics g, BattleLobbyState state, boolean own, double lookX, double lookY, double mx, double my) {
		int x = CARD_X + (own ? 0 : RIGHT_CARD_OFFSET);
		blitTexture(g, TEX_CARD, x, 72, 577, 758);
		outline(g, x, 72, 577, 758, CYAN2);
		g.fill(x, 72, x + 577, 75, own ? ACCENT_LEFT : ACCENT_RIGHT);
		renderGlint(g, x, 72, 577, 3, 6f, own ? 0f : 3f, 0xFFFFFF);

		// Head: the team source (the player's name is in the header, 2026-11-28).
		g.fill(x + 1, 75, x + 576, VIEW_Y, CARD_HEAD_BG);
		g.fill(x + 1, VIEW_Y - hairline(), x + 576, VIEW_Y, LINE_28);
		boolean cobblemon = own ? state.ownIsCobblemon() : state.opponentIsCobblemon();
		String source = fitText(upper(sourceLabel(cobblemon).getString()), 550, 2f, true, 0.6f);
		// Mirrored: ours on the left, theirs against the right edge.
		drawText(g, source, own ? x + 12 : x + 565 - textWidth(source, 2f, true, 0.6f), 89, 2f, WHITE, true, 0.6f);

		// Player model window.
		blitTexture(g, TEX_VIEWPORT, x + 1, VIEW_Y, 575, VIEW_H);
		blitTexture(g, TEX_SHADOW, x + 288 - 80, 446, 160, 26);
		renderDust(g, x + 1, VIEW_Y, 575, VIEW_H, own ? 1 : 2, own ? ACCENT_LEFT : ACCENT_RIGHT);
		// League badges beside the model (ours on the right, theirs on the left), drawn first: the model passes in front.
		renderBadges(g, own ? x + 575 - 12 - BADGE_PANEL_W : x + 13, own
				? TrainerBadges.of(Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID(), controller.localPlayerName())
				: TrainerBadges.of(state.opponentUuid(), state.opponentName()), mx, my);
		renderPlayer(g, own ? Minecraft.getInstance().player : opponentEntity(state.opponentUuid()),
				own ? null : state.opponentUuid(), x + 1, VIEW_Y, 575, VIEW_H, lookX, lookY);
		boolean ready = own ? state.ownReady() : state.opponentReady();
		String readyText = ready ? Component.translatable("phantasmon.battle.lobby.ready").getString()
				: Component.translatable("phantasmon.battle.lobby.not_ready").getString();
		float readyWidth = textWidth(readyText, 1f, true, 1.5f);
		// Above the badges: ours top right, theirs top left (2026-11-28).
		int tagW = Math.round(readyWidth + 16);
		int tagX = own ? x + 575 - 12 - tagW : x + 10;
		g.fill(tagX, VIEW_Y + 10, tagX + tagW, VIEW_Y + 32, BADGE_BORDER);
		outline(g, tagX, VIEW_Y + 10, tagW, 22, ready ? READY_BORDER : LINE_30);
		drawText(g, readyText, tagX + 8, VIEW_Y + 17, 1f, ready ? READY_TEXT : DIM, true, 1.5f);
		g.fill(x + 1, VIEW_Y + VIEW_H, x + 576, VIEW_Y + VIEW_H + hairline(), LINE_20);

		// Lead.
		boolean twoLeads = state.doubles();
		infoBox(g, x + 9, 506, 559, LEAD_BOX_H, own
				? (twoLeads ? "phantasmon.battle.lobby.own_leads" : "phantasmon.battle.lobby.own_lead")
				: (twoLeads ? "phantasmon.battle.lobby.opponent_leads" : "phantasmon.battle.lobby.opponent_lead"));
		if (own && twoLeads) {
			List<Integer> leads = state.ownLeads();
			renderOwnLeadCompact(g, leads.isEmpty() ? null : state.ownSlot(leads.get(0)), x + 9, "phantasmon.battle.lobby.lead_left");
			renderOwnLeadCompact(g, leads.size() < 2 ? null : state.ownSlot(leads.get(1)), x + 288, "phantasmon.battle.lobby.lead_right");
		} else if (own) {
			renderOwnLead(g, state.ownLeadPokemon(), x);
		} else {
			// The hidden lead: a question mark bobbing gently.
			float bob = (float) Math.sin(clockSeconds() * 1.8f) * 4f;
			drawCentered(g, "?", x + 288, 570 + bob, 6f, withAlpha(TITLE, 0.75f + 0.25f * (float) Math.sin(clockSeconds() * 1.8f + 1f)), true, 0f);
			drawCentered(g, Component.translatable("phantasmon.battle.lobby.lead_hidden").getString(), x + 288, 680, 2f, DIM, false, 0f);
		}

		// Team switch (own) / team source (opponent).
		if (own) {
			boolean locked = state.ownReady();
			boolean hovered = !locked && inside(mx, my, x + 9, SWITCH_Y, 559, 48);
			int[] gradient = hovered ? STD_BUTTON_HOVER : STD_BUTTON;
			blackBorder(g, x + 9, SWITCH_Y, 559, 48, PANEL_BORDER);
			g.fillGradient(x + 9, SWITCH_Y, x + 568, SWITCH_Y + 48, locked ? MOVE_BG : gradient[0], locked ? MOVE_BG : gradient[1]);
			raisedEdge(g, x + 9, SWITCH_Y, 559, 48);
			String label = fitText(upper(Component.translatable("phantasmon.battle.lobby.switch_team", sourceLabel(!cobblemon)).getString()),
					540, 2f, true, 1f);
			drawCentered(g, label, x + 288, SWITCH_Y + 17, 2f, locked ? DIM : WHITE, true, 1f);
		} else {
			g.fill(x + 9, SWITCH_Y, x + 568, SWITCH_Y + 48, INFO_BOX_BG);
			outline(g, x + 9, SWITCH_Y, 559, 48, LINE_20);
			drawCentered(g, upper(sourceLabel(cobblemon).getString()), x + 288, SWITCH_Y + 17, 2f, TEXT2, true, 1f);
		}
	}

	/**
	 * Our lead (singles): on the left its model, name, level, item, ability and nature; on the right its four moves
	 * and its EV spread (2026-11-28).
	 */
	private void renderOwnLead(GuiGraphics g, PokemonDto lead, int x) {
		if (lead == null) {
			drawCentered(g, "—", x + 288, 620, 2f, DIM, false, 0f);
			return;
		}
		String star = lead.isShiny() ? "★ " : "";
		float starWidth = textWidth(star, 2f, true, 0f);
		if (!star.isEmpty()) {
			drawText(g, star, x + 21, 528, 2f, STAR, true, 0f);
		}
		String level = Component.translatable("phantasmon.trade.screen.level", lead.level()).getString();
		float levelWidth = textWidth(level, 2f, false, 0f);
		drawText(g, fitText(displayName(lead), 240 - starWidth - levelWidth - 10, 2f, true, 0.6f), x + 21 + starWidth, 528, 2f, WHITE, true, 0.6f);
		drawText(g, level, x + 262 - levelWidth, 528, 2f, TEXT2, false, 0f);

		int boxX = x + 15;
		int boxY = 556;
		int boxW = 116;
		int boxH = 150;
		g.flush();
		enableCanvasScissor(g, boxX, boxY, boxW, boxH);
		PokemonGuiRendering.renderModel(g, lead.species(), lead.form(), lead.isShiny(), PokemonGuiRendering.storedGender(lead),
				boxX + boxW / 2f, slotModelAnchorY(boxY + boxH / 2f, SLOT_MODEL_SCALE * 1.05f), SLOT_MODEL_SCALE * 1.05f);
		g.disableScissor();

		float textX = x + 138;
		float maxWidth = x + 262 - textX;
		renderLeadFact(g, "phantasmon.trade.screen.held_item", textX, 566, maxWidth, null);
		String itemName = PokemonGuiRendering.heldItemName(heldItemId(lead));
		float itemX = textX;
		if (itemName != null && PokemonGuiRendering.renderHeldItem(g, heldItemId(lead), textX, 578, 18)) {
			itemX += 22;
		}
		drawText(g, fitText(itemName == null ? Component.translatable("phantasmon.trade.screen.no_item").getString() : itemName,
				textX + maxWidth - itemX, 2f, false, 0f), itemX, 581, 2f, TEXT2, false, 0f);
		renderLeadFact(g, "phantasmon.trade.screen.ability", textX, 614, maxWidth, abilityLabel(lead.ability()));
		renderLeadFact(g, "phantasmon.trade.screen.nature", textX, 662, maxWidth,
				com.mystaria.phantasmon.client.pokemon.NatureModifiers.displayName(lead.nature()));

		g.fill(x + 272, 524, x + 273, 748, LINE_20);
		renderLeadMoves(g, lead, x + 282, 520, 278, false);
		renderLeadEvs(g, lead, x + 282, 650, 278);
	}

	/** A small heading and, under it, its value (none: the caller draws it). */
	private void renderLeadFact(GuiGraphics g, String labelKey, float x, float y, float maxWidth, String value) {
		drawText(g, upper(Component.translatable(labelKey).getString()), x, y, 1f, TITLE, true, 1.2f);
		if (value != null) {
			drawText(g, fitText(value, maxWidth, 2f, false, 0f), x, y + 15, 2f, TEXT2, false, 0f);
		}
	}

	/** A lead's four moves from {@code y}: a row each, its type's colour on the left ({@code compact}: 2×2, small). */
	private void renderLeadMoves(GuiGraphics g, PokemonDto lead, int x, int y, int w, boolean compact) {
		Map<String, Object> data = lead.data() != null ? lead.data() : Map.of();
		List<Object> moves = asList(data.get("moves"));
		if (!compact) {
			drawText(g, upper(Component.translatable("phantasmon.trade.screen.moves").getString()), x, y + 2, 1f, TITLE, true, 1.2f);
		}
		int rowH = compact ? 20 : 25;
		int cellW = compact ? (w - 6) / 2 : w;
		int top = compact ? y : y + 16;
		for (int i = 0; i < 4; i++) {
			int cellX = compact ? x + (i % 2) * (cellW + 6) : x;
			int cellY = compact ? top + (i / 2) * (rowH + 4) : top + i * (rowH + 3);
			g.fill(cellX, cellY, cellX + cellW, cellY + rowH, MOVE_BG);
			if (i >= moves.size()) {
				continue;
			}
			String moveId = moves.get(i).toString();
			com.cobblemon.mod.common.api.moves.MoveTemplate template = com.cobblemon.mod.common.api.moves.Moves.INSTANCE.getByName(moveId);
			com.cobblemon.mod.common.api.types.ElementalType type = moveType(moveId, template, asMap(data.get("ivs")));
			if (type != null) {
				g.fill(cellX, cellY, cellX + 4, cellY + rowH, 0xFF000000 | typeColor(type));
			}
			String name = template != null ? template.getDisplayName().getString() : moveId;
			float scale = compact ? 1f : 2f;
			drawText(g, fitText(name, cellW - 16, scale, false, 0f), cellX + 10, cellY + (rowH - 7 * scale) / 2f, scale, WHITE, false, 0f);
		}
	}

	/** A lead's EV spread: 3×2 cells, the stat coloured by the nature (+ red, - blue), its EVs and a bar out of 252. */
	private void renderLeadEvs(GuiGraphics g, PokemonDto lead, int x, int y, int w) {
		Map<String, Object> data = lead.data() != null ? lead.data() : Map.of();
		Map<String, Object> evs = asMap(data.get("evs"));
		com.mystaria.phantasmon.client.pokemon.NatureModifiers.Modifier modifier =
				com.mystaria.phantasmon.client.pokemon.NatureModifiers.get(lead.nature());
		String boosted = modifier == null ? null : modifier.boosted().toLowerCase(java.util.Locale.ROOT);
		String reduced = modifier == null ? null : modifier.reduced().toLowerCase(java.util.Locale.ROOT);
		drawText(g, upper(Component.translatable("phantasmon.battle.lobby.lead_evs").getString()), x, y + 2, 1f, TITLE, true, 1.2f);
		int cellW = (w - 2 * 6) / 3;
		int cellH = 40;
		for (int i = 0; i < STAT_KEYS.length; i++) {
			int cellX = x + (i % 3) * (cellW + 6);
			int cellY = y + 16 + (i / 3) * (cellH + 4);
			g.fill(cellX, cellY, cellX + cellW, cellY + cellH, MOVE_BG);
			int labelColor = STAT_KEYS[i].equals(boosted) ? NATURE_UP : STAT_KEYS[i].equals(reduced) ? NATURE_DOWN : TEXT2;
			drawText(g, Component.translatable("phantasmon.battle.lobby.stat_short." + STAT_KEYS[i]).getString(), cellX + 6, cellY + 5, 1f,
					labelColor, true, 1f);
			int ev = asInt(evs.get(STAT_KEYS[i]));
			String value = String.valueOf(ev);
			drawText(g, value, cellX + cellW - 6 - textWidth(value, 2f, false, 0f), cellY + 16, 2f, ev > 0 ? EV_COLOR : DIM, false, 0f);
			int barLeft = cellX + 6;
			int barRight = cellX + cellW - 6;
			g.fill(barLeft, cellY + cellH - 5, barRight, cellY + cellH - 3, LINE_20);
			int filled = Math.round((barRight - barLeft) * Math.min(252, Math.max(0, ev)) / 252f);
			if (filled > 0) {
				g.fill(barLeft, cellY + cellH - 5, barLeft + filled, cellY + cellH - 3, withAlpha(EV_COLOR, 0.8f));
			}
		}
	}

	/**
	 * Doubles: one of our two leads in half the box ({@code x}..{@code x + 279}): its side, model, name, level and
	 * item, then its four moves (2×2) and its EV spread on one line.
	 */
	private void renderOwnLeadCompact(GuiGraphics g, PokemonDto lead, int x, String sideKey) {
		if (lead == null) {
			drawCentered(g, "—", x + 140, 620, 2f, DIM, false, 0f);
			return;
		}
		int boxX = x + 6;
		int boxY = 528;
		int boxW = 96;
		int boxH = 112;
		g.flush();
		enableCanvasScissor(g, boxX, boxY, boxW, boxH);
		PokemonGuiRendering.renderModel(g, lead.species(), lead.form(), lead.isShiny(), PokemonGuiRendering.storedGender(lead),
				boxX + boxW / 2f, slotModelAnchorY(boxY + boxH / 2f, SLOT_MODEL_SCALE * 0.8f), SLOT_MODEL_SCALE * 0.8f);
		g.disableScissor();
		float textX = boxX + boxW + 8;
		float maxWidth = x + 272 - textX;
		drawText(g, upper(Component.translatable(sideKey).getString()), textX, 532, 1f, CYAN, true, 1f);
		String star = lead.isShiny() ? "★ " : "";
		float starWidth = textWidth(star, 2f, true, 0f);
		if (!star.isEmpty()) {
			drawText(g, star, textX, 552, 2f, STAR, true, 0f);
		}
		drawText(g, fitText(displayName(lead), maxWidth - starWidth, 2f, true, 0.6f), textX + starWidth, 552, 2f, WHITE, true, 0.6f);
		drawText(g, Component.translatable("phantasmon.trade.screen.level", lead.level()).getString(), textX, 584, 2f, TEXT2, false, 0f);
		String itemName = PokemonGuiRendering.heldItemName(heldItemId(lead));
		if (itemName != null && PokemonGuiRendering.renderHeldItem(g, heldItemId(lead), textX, 610, 18)) {
			drawText(g, fitText(itemName, maxWidth - 24, 2f, false, 0f), textX + 24, 613, 2f, TEXT2, false, 0f);
		}
		renderLeadMoves(g, lead, x + 6, 648, 266, true);
		// EVs on one line: only the stats that have some ("252 Atq · 252 Vit · 4 PV").
		Map<String, Object> evs = asMap(lead.data() != null ? lead.data().get("evs") : null);
		List<String> parts = new java.util.ArrayList<>();
		for (String stat : STAT_KEYS) {
			int ev = asInt(evs.get(stat));
			if (ev > 0) {
				parts.add(ev + " " + Component.translatable("phantasmon.battle.lobby.stat_short." + stat).getString());
			}
		}
		String spread = Component.translatable("phantasmon.battle.lobby.lead_evs").getString() + " : "
				+ (parts.isEmpty() ? "—" : String.join(" · ", parts));
		drawText(g, fitText(spread, 266, 1f, false, 0f), x + 6, 702, 1f, EV_COLOR, false, 0f);
		String nature = com.mystaria.phantasmon.client.pokemon.NatureModifiers.displayName(lead.nature());
		drawText(g, fitText(nature, 266, 1f, false, 0f), x + 6, 718, 1f, TEXT2, false, 0f);
	}

	/** League badges: 3 columns of 6 slots, empty by default, filled with each badge's picture in order. */
	private void renderBadges(GuiGraphics g, int x, List<TrainerBadges.Badge> badges, double mx, double my) {
		int y = VIEW_Y + (VIEW_H - BADGE_PANEL_H) / 2 + 10;
		g.fill(x, y, x + BADGE_PANEL_W, y + BADGE_PANEL_H, 0x90050A10);
		outline(g, x, y, BADGE_PANEL_W, BADGE_PANEL_H, LINE_30);
		String title = upper(Component.translatable("phantasmon.battle.lobby.badges").getString()) + "  " + badges.size();
		drawCentered(g, fitText(title, BADGE_PANEL_W - 10, 1f, true, 1.2f), x + BADGE_PANEL_W / 2f, y + 8, 1f, TITLE, true, 1.2f);
		for (int i = 0; i < BADGE_COLUMNS * BADGE_ROWS; i++) {
			int slotX = x + 8 + (i % BADGE_COLUMNS) * (BADGE_SLOT + BADGE_GAP);
			int slotY = y + 24 + (i / BADGE_COLUMNS) * (BADGE_SLOT + BADGE_GAP);
			g.fill(slotX, slotY, slotX + BADGE_SLOT, slotY + BADGE_SLOT, 0x80000000);
			sunkenEdge(g, slotX, slotY, BADGE_SLOT, BADGE_SLOT);
			if (i < badges.size()) {
				if (inside(mx, my, slotX, slotY, BADGE_SLOT, BADGE_SLOT)) {
					hoveredBadge = badges.get(i);
					hoveredBadgeX = slotX;
					hoveredBadgeY = slotY;
					outline(g, slotX, slotY, BADGE_SLOT, BADGE_SLOT, CYAN);
				}
				com.mojang.blaze3d.systems.RenderSystem.enableBlend();
				g.blit(badges.get(i).texture(), slotX + 4, slotY + 4, 0, 0, BADGE_SLOT - 8, BADGE_SLOT - 8, BADGE_SLOT - 8, BADGE_SLOT - 8);
				// Now and then a shine runs across the badge (each at its own moment).
				float shine = ((clockSeconds() + i * 0.83f) % 5f) / 0.6f;
				if (shine < 1f) {
					int band = slotX + 2 + Math.round(shine * (BADGE_SLOT - 8));
					g.fill(band, slotY + 3, Math.min(slotX + BADGE_SLOT - 3, band + 4), slotY + BADGE_SLOT - 3,
							withAlpha(0xFFFFFFFF, 0.35f * (float) Math.sin(shine * Math.PI)));
				}
			} else {
				// Empty slot: a faint badge outline.
				outline(g, slotX + 12, slotY + 12, BADGE_SLOT - 24, BADGE_SLOT - 24, 0x18FFFFFF);
			}
		}
	}

	private static Component sourceLabel(boolean cobblemon) {
		return Component.translatable(cobblemon ? "phantasmon.battle.lobby.source.cobblemon" : "phantasmon.battle.lobby.source.ghost");
	}

	private static LivingEntity opponentEntity(UUID uuid) {
		var level = Minecraft.getInstance().level;
		// A partner met in the Global Hub plays on another server: their avatar stands in for them.
		return com.mystaria.phantasmon.client.hub.HubAvatars.playerOrAvatar(level, uuid);
	}

	/**
	 * A player's 3D model in a window, turned toward the cursor like the inventory's; when the player isn't loaded
	 * nearby, their skin's face instead.
	 */
	private void renderPlayer(GuiGraphics g, LivingEntity entity, UUID fallbackUuid, int boxX, int boxY, int boxW, int boxH,
			double lookX, double lookY) {
		float centerX = boxX + boxW / 2f;
		float centerY = boxY + boxH / 2f + 12;
		if (entity == null) {
			if (fallbackUuid != null) {
				var connection = Minecraft.getInstance().getConnection();
				PlayerInfo info = connection == null ? null : connection.getPlayerInfo(fallbackUuid);
				PlayerSkin skin = info != null ? info.getSkin() : DefaultPlayerSkin.get(fallbackUuid);
				g.flush();
				PlayerFaceRenderer.draw(g, skin, Math.round(centerX - 80), Math.round(centerY - 100), 160);
			}
			return;
		}
		float yaw = (float) Math.atan((centerX - lookX) / 400f);
		float pitch = (float) Math.atan((centerY - 90 - lookY) / 400f);
		Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf camera = new Quaternionf().rotateX(pitch * 20f * Mth.DEG_TO_RAD);
		pose.mul(camera);
		float bodyRot = entity.yBodyRot;
		float yRot = entity.getYRot();
		float xRot = entity.getXRot();
		float headRotO = entity.yHeadRotO;
		float headRot = entity.yHeadRot;
		entity.yBodyRot = 180f + yaw * 20f;
		entity.setYRot(180f + yaw * 40f);
		entity.setXRot(-pitch * 20f);
		entity.yHeadRot = entity.getYRot();
		entity.yHeadRotO = entity.getYRot();
		float entityScale = entity.getScale();
		Vector3f translate = new Vector3f(0f, entity.getBbHeight() / 2f, 0f);
		g.flush();
		enableCanvasScissor(g, boxX, boxY, boxW, boxH);
		InventoryScreen.renderEntityInInventory(g, centerX, centerY, PLAYER_MODEL_SCALE / entityScale, translate, pose, camera, entity);
		g.disableScissor();
		entity.yBodyRot = bodyRot;
		entity.setYRot(yRot);
		entity.setXRot(xRot);
		entity.yHeadRotO = headRotO;
		entity.yHeadRot = headRot;
	}

	// ---- Footer ----

	/** A Pokémon breaking the battle format: circled in red (TODO-24). */
	private void renderRuleBreak(GuiGraphics g, int x, int y) {
		glowInside(g, x, y, SLOT_W, SLOT_H, 0xFF5078, 0.35f, 8);
		outline(g, x - 2, y - 2, SLOT_W + 4, SLOT_H + 4, DANGER_BORDER);
		outline(g, x - 1, y - 1, SLOT_W + 2, SLOT_H + 2, DANGER_BORDER);
		outline(g, x, y, SLOT_W, SLOT_H, DANGER_BORDER);
		drawText(g, "!", x + 8, y + SLOT_H - 24, 2f, DANGER_BORDER, true, 0f);
	}

	private void renderFooter(GuiGraphics g, BattleLobbyState state, double mx, double my) {
		renderFooterBar(g, null, MUTED, 0);
		// The battle's rules: format and field.
		renderBarButton(g, FORMAT_X, RULE_W, Component.translatable("phantasmon.battle.lobby.format",
				PhantasmonFormatPickerScreen.formatName(state.format())).getString(), !state.formatFixed(), mx, my);
		renderBarButton(g, FIELD_X, RULE_W, Component.translatable("phantasmon.battle.lobby.field", fieldName(state.field())).getString(),
				fieldEditable(state), mx, my);
		g.fill(GROUP_SPLIT_X, BAR_Y + 2, GROUP_SPLIT_X + 1, BAR_Y + BAR_H - 2, LINE_30);
		// Its look: scene (place), ambiance, intro.
		renderBarButton(g, SCENE_X, LOOK_W, Component.translatable("phantasmon.battle.lobby.scene", stadiumLabel(state.ambiance())).getString(),
				!state.ambianceFixed(), mx, my);
		renderBarButton(g, AMBIANCE_X, LOOK_W, Component.translatable("phantasmon.battle.lobby.ambiance", ambianceLabel(state.ambiance())).getString(),
				!state.ambianceFixed(), mx, my);
		renderBarButton(g, INTRO_X, LOOK_W, Component.translatable("phantasmon.battle.lobby.intro", introName(state.intro())).getString(),
				!state.introFixed(), mx, my);
		renderButton(g, MUSIC_X, BAR_Y, MUSIC_W, BAR_H, "phantasmon.music.button", false, inside(mx, my, MUSIC_X, BAR_Y, MUSIC_W, BAR_H));
		if (state.timerOn()) {
			long left = state.timerSecondsLeft();
			renderPrimaryButtonFrame(g, TIMER_X, BAR_Y, TIMER_W, BAR_H, false, false);
			drawCentered(g, "⏱ " + clock(left), TIMER_X + TIMER_W / 2f, 856, 2f, left <= 10 ? DANGER_BORDER : CYAN, true, 0f);
		} else {
			renderButton(g, TIMER_X, BAR_Y, TIMER_W, BAR_H, "phantasmon.battle.lobby.timer_button", false, inside(mx, my, TIMER_X, BAR_Y, TIMER_W, BAR_H));
		}
		renderButton(g, 1475, BAR_Y, 97, BAR_H, "phantasmon.battle.lobby.quit", true, inside(mx, my, 1475, BAR_Y, 97, BAR_H));
	}

	/** A footer button opening a choice window; imposed (event NPC, player ready...): no arrow, no hover, dimmed. */
	private void renderBarButton(GuiGraphics g, int x, int w, String label, boolean editable, double mx, double my) {
		renderPrimaryButtonFrame(g, x, BAR_Y, w, BAR_H, editable && inside(mx, my, x, BAR_Y, w, BAR_H), false);
		drawText(g, fitText(label + (editable ? "  ▸" : ""), w - 16, 2f, false, 0f), x + 8, 856, 2f, editable ? CYAN : DIM, false, 0f);
	}

	private void openOverlay(net.minecraft.client.gui.screens.Screen screen) {
		openingOverlay = true;
		minecraft.setScreen(screen);
	}

	// ---- Message pop-up (2026-11-27): over our card's model, errors, warnings and the random-team wait ----

	private enum Level { INFO, WARNING, ERROR }

	private record Message(String text, Level level) {
	}

	private Message message(BattleLobbyState state) {
		if (!hoveredIssues.isEmpty()) {
			return new Message(hoveredIssues.stream()
					.map(issue -> Component.translatable("phantasmon.battle.lobby.issue." + issue.code(), issue.subject()).getString())
					.distinct().collect(java.util.stream.Collectors.joining(" · ")), Level.ERROR);
		}
		if (state.lastErrorCode() != null) {
			return new Message(stripPrefix(Component.translatable(BackendErrorMessages.translationKey(state.lastErrorCode())).getString()),
					Level.ERROR);
		}
		if (state.ownTeamBreaksRules()) {
			long count = java.util.stream.IntStream.range(0, BattleLobbyState.TEAM_SIZE).filter(i -> !state.ownIssues(i).isEmpty()).count();
			return new Message(Component.translatable("phantasmon.battle.lobby.issues", count).getString(), Level.WARNING);
		}
		if (!state.ownTeamGenerated()) {
			return new Message(Component.translatable("phantasmon.battle.lobby.generating_own_team").getString(), Level.INFO);
		}
		if (!state.teamsGenerated()) {
			return new Message(Component.translatable("phantasmon.battle.lobby.generating_opponent_team", state.opponentName()).getString(),
					Level.INFO);
		}
		// Who is ready shows in the header, the timer on its button: nothing more to say.
		return null;
	}

	/** The message, if any: a box in the middle of our card's model window, its border coloured by how serious it is. */
	private void renderMessage(GuiGraphics g, BattleLobbyState state) {
		Message message = message(state);
		if (message == null) {
			return;
		}
		int boxW = 460;
		int x = CARD_X + (577 - boxW) / 2;
		List<String> lines = wrap(message.text(), boxW - 28, 4);
		int boxH = 14 + lines.size() * 22;
		int y = VIEW_Y + (VIEW_H - boxH) / 2;
		int border = switch (message.level()) {
			case ERROR -> DANGER_BORDER;
			case WARNING -> 0xFFD4A017;
			case INFO -> CYAN;
		};
		g.fill(x, y, x + boxW, y + boxH, PhantasmonTheme.cobblemon() ? 0xFF2F2F2F : 0xFF081422);
		outline(g, x, y, boxW, boxH, border);
		g.fill(x, y, x + 4, y + boxH, border);
		int color = message.level() == Level.INFO ? WHITE : message.level() == Level.ERROR ? ERROR_TEXT : 0xFFF5D76E;
		for (int i = 0; i < lines.size(); i++) {
			drawText(g, lines.get(i), x + 16, y + 10 + i * 22, 2f, color, false, 0f);
		}
	}

	/** {@code text} cut into lines at most {@code width} wide (2× text), {@code maxLines} at most (the last one cut short). */
	private List<String> wrap(String text, float width, int maxLines) {
		List<String> lines = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (textWidth(next, 2f, false, 0f) <= width || line.isEmpty()) {
				line = new StringBuilder(next);
			} else {
				lines.add(line.toString());
				line = new StringBuilder(word);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		if (lines.size() > maxLines) {
			List<String> cut = new java.util.ArrayList<>(lines.subList(0, maxLines));
			cut.set(maxLines - 1, fitText(cut.get(maxLines - 1) + " " + lines.get(maxLines), width, 2f, false, 0f));
			return cut;
		}
		return lines;
	}

	private static String clock(long seconds) {
		return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
	}

	// ---- Choice windows (2026-11-27): field, scene, ambiance, intro ----

	private static String text(String key) {
		return Component.translatable(key).getString();
	}

	private PhantasmonChoicePickerScreen fieldPicker() {
		return new PhantasmonChoicePickerScreen(this, controller, "phantasmon.battle.lobby.field.title",
				state -> {
					List<PhantasmonChoicePickerScreen.Choice> choices = new java.util.ArrayList<>();
					for (String id : fieldOptions(state)) {
						choices.add(new PhantasmonChoicePickerScreen.Choice(id, fieldName(id), "field", id, id.equals(state.field())));
					}
					return List.of(new PhantasmonChoicePickerScreen.Group(null, choices));
				},
				state -> fieldName(state.field()), PhantasmonBattleLobbyScreen::fieldEditable,
				choice -> controller.lobbySetField(choice.id()));
	}

	/** The scene window (presets, then stadiums), or with {@code ambiance} the ambiance window. */
	private PhantasmonChoicePickerScreen scenePicker(boolean ambiance) {
		return new PhantasmonChoicePickerScreen(this, controller,
				ambiance ? "phantasmon.battle.lobby.ambiance.title" : "phantasmon.battle.lobby.scene.title",
				state -> ambiance ? List.of(sceneGroup(state, 2, null))
						: List.of(sceneGroup(state, 0, text("phantasmon.battle.lobby.scene.tab.presets")),
								sceneGroup(state, 1, text("phantasmon.battle.lobby.scene.tab.stadiums"))),
				state -> ambiance ? ambianceLabel(state.ambiance()) : stadiumLabel(state.ambiance()),
				state -> !state.ambianceFixed(),
				choice -> {
					BattleLobbyState state = controller.lobby();
					if (state != null) {
						int tab = switch (choice.previewKind()) {
							case "preset" -> 0;
							case "stadium" -> 1;
							default -> 2;
						};
						controller.lobbySetAmbiance(sceneAfterPick(tab, state.ambiance(), choice.id()));
					}
				});
	}

	private PhantasmonChoicePickerScreen.Group sceneGroup(BattleLobbyState state, int tab, String title) {
		List<PhantasmonChoicePickerScreen.Choice> choices = new java.util.ArrayList<>();
		String kind = tab == 0 ? "preset" : tab == 1 ? "stadium" : "ambiance";
		for (String id : sceneOptions(state, tab)) {
			if (tab == 0 && id.isEmpty()) {
				continue; // "each player's own" is in the stadiums' group
			}
			choices.add(new PhantasmonChoicePickerScreen.Choice(id, sceneOptionName(tab, id), kind, id, sceneSelected(tab, state.ambiance(), id)));
		}
		return new PhantasmonChoicePickerScreen.Group(title, choices);
	}

	private PhantasmonChoicePickerScreen introPicker() {
		return new PhantasmonChoicePickerScreen(this, controller, "phantasmon.battle.lobby.intro.title",
				state -> {
					List<PhantasmonChoicePickerScreen.Choice> choices = new java.util.ArrayList<>();
					for (String id : intros()) {
						choices.add(new PhantasmonChoicePickerScreen.Choice(id, introName(id), "intro", id, id.equals(state.intro())));
					}
					return List.of(new PhantasmonChoicePickerScreen.Group(null, choices));
				},
				state -> introName(state.intro()), state -> !state.introFixed(),
				choice -> controller.lobbySetIntro(choice.id()));
	}

	// ---- Battle scene (Showtime 1.3, 2026-11-19): presets, stadiums, ambiances, in one string "<ambiance>|<stadium>" ----

	/** A stadium option meaning "each player's own stadium". */
	private static final String EACH_STADIUM = "*";

	/** Options of tab {@code tab} (0: presets, 1: stadiums, 2: ambiances): "" / "*" first (each player's own). */
	private static List<String> sceneOptions(BattleLobbyState state, int tab) {
		List<String> list = new java.util.ArrayList<>();
		switch (tab) {
			case 0 -> {
				list.add("");
				list.addAll(com.mystaria.showtime.ambiance.Ambiances.scenePresetIds());
			}
			case 1 -> {
				list.add(EACH_STADIUM);
				// With a League Reborn field: its own stadium, if it suggests one (a cave field: the Cave).
				if (state != null && fieldShown(state)) {
					list.add(com.mystaria.showtime.ambiance.Ambiances.FIELD_STADIUM);
				}
				list.add(com.mystaria.showtime.ambiance.Ambiances.NONE);
				list.addAll(com.mystaria.showtime.ambiance.Ambiances.stadiumIds());
			}
			default -> {
				list.add("");
				// Reserved ones (TODO-37) only once won: Showtime asks Phantasmon's rewards.
				list.addAll(com.mystaria.showtime.ambiance.Ambiances.plainAmbianceIds());
			}
		}
		return list;
	}

	/** The scene once {@code option} of tab {@code tab} is picked: a preset sets both, the others keep what is not theirs. */
	private static String sceneAfterPick(int tab, String scene, String option) {
		String ambiance = com.mystaria.showtime.ambiance.Ambiances.sceneAmbiance(scene);
		String stadium = com.mystaria.showtime.ambiance.Ambiances.sceneStadium(scene);
		return switch (tab) {
			case 0 -> option;
			case 1 -> com.mystaria.showtime.ambiance.Ambiances.scene(
					com.mystaria.showtime.ambiance.Ambiances.isPreset(ambiance)
							? com.mystaria.showtime.ambiance.Ambiances.presetAmbiance(ambiance) : ambiance,
					EACH_STADIUM.equals(option) ? null : option);
			default -> {
				String keep = stadium != null ? stadium : com.mystaria.showtime.ambiance.Ambiances.presetStadium(ambiance);
				yield com.mystaria.showtime.ambiance.Ambiances.scene(option, keep);
			}
		};
	}

	/** Whether {@code option} of tab {@code tab} is what the scene holds now. */
	private static boolean sceneSelected(int tab, String scene, String option) {
		String ambiance = com.mystaria.showtime.ambiance.Ambiances.sceneAmbiance(scene);
		String stadium = com.mystaria.showtime.ambiance.Ambiances.sceneStadium(scene);
		boolean preset = com.mystaria.showtime.ambiance.Ambiances.isPreset(ambiance) && stadium == null;
		return switch (tab) {
			case 0 -> !option.isEmpty() && preset && option.equals(ambiance);
			case 1 -> {
				if (preset) {
					yield false; // a preset is ticked in its own group
				}
				yield EACH_STADIUM.equals(option) ? stadium == null : option.equals(stadium);
			}
			default -> {
				String playing = com.mystaria.showtime.ambiance.Ambiances.isPreset(ambiance)
						? com.mystaria.showtime.ambiance.Ambiances.presetAmbiance(ambiance) : ambiance;
				yield option.equals(playing == null ? "" : playing);
			}
		};
	}

	private static String sceneOptionName(int tab, String option) {
		if (tab == 1) {
			if (com.mystaria.showtime.ambiance.Ambiances.FIELD_STADIUM.equals(option)) {
				return text("phantasmon.battle.lobby.scene.stadium_field");
			}
			return EACH_STADIUM.equals(option) ? text("phantasmon.battle.lobby.scene.stadium_each")
					: com.mystaria.showtime.ambiance.Ambiances.stadiumName(option).getString();
		}
		return ambianceName(option);
	}

	private static String ambianceName(String id) {
		return id == null || id.isEmpty() ? text("phantasmon.battle.lobby.ambiance.each")
				: com.mystaria.showtime.ambiance.Ambiances.displayName(id).getString();
	}

	/** The scene button's label: the preset, else the stadium, else "each their own". */
	private static String stadiumLabel(String scene) {
		String ambiance = com.mystaria.showtime.ambiance.Ambiances.sceneAmbiance(scene);
		String stadium = com.mystaria.showtime.ambiance.Ambiances.sceneStadium(scene);
		if (stadium == null && com.mystaria.showtime.ambiance.Ambiances.isPreset(ambiance)) {
			return ambianceName(ambiance);
		}
		return sceneOptionName(1, stadium == null ? EACH_STADIUM : stadium);
	}

	/** The ambiance button's label: the one playing (a preset's own included), else "each their own". */
	private static String ambianceLabel(String scene) {
		String ambiance = com.mystaria.showtime.ambiance.Ambiances.sceneAmbiance(scene);
		return ambianceName(com.mystaria.showtime.ambiance.Ambiances.isPreset(ambiance)
				? com.mystaria.showtime.ambiance.Ambiances.presetAmbiance(ambiance) : ambiance);
	}

	/** "" (each player's own), then every Showtime intro, add-ons' included, reserved ones only once won (TODO-37). */
	private static List<String> intros() {
		List<String> list = new java.util.ArrayList<>();
		list.add("");
		list.addAll(com.mystaria.showtime.battle.BattleCinematic.introNames());
		return list;
	}

	private static String introName(String name) {
		return name == null || name.isEmpty() ? text("phantasmon.battle.lobby.intro.each")
				: com.mystaria.showtime.battle.BattleCinematic.introDisplayName(name).getString();
	}

	// ---- Battle field (League Reborn) ----

	/** The add-on that runs fields (Phantasmon Events) is here: only then may we lay one down (we host). */
	private static boolean leagueReborn() {
		return com.mystaria.phantasmon.client.battle.ShowdownExtensions.has("league_reborn");
	}

	/** A field is laid down, or we could lay one down. */
	private static boolean fieldShown(BattleLobbyState state) {
		return !state.field().isEmpty() || (leagueReborn() && !state.fields().isEmpty());
	}

	private static boolean fieldEditable(BattleLobbyState state) {
		return leagueReborn() && !state.fieldFixed() && !state.fields().isEmpty() && !state.ownReady();
	}

	/** "" (no field: the plain format), then the fields we may lay down. */
	private static List<String> fieldOptions(BattleLobbyState state) {
		List<String> list = new java.util.ArrayList<>();
		list.add("");
		list.addAll(state.fields());
		return list;
	}

	/** A field's name, from the add-on's lang files ({@code league_reborn.field.<id>}). */
	private static String fieldName(String id) {
		return id == null || id.isEmpty() ? text("phantasmon.battle.lobby.field.none")
				: Component.translatableWithFallback("league_reborn.field." + id, id).getString();
	}

	/**
	 * The badge under the cursor, enlarged: its picture, name, gym leader and type, in a card beside its slot (kept
	 * on the screen).
	 */
	private void renderBadgeTooltip(GuiGraphics g) {
		TrainerBadges.Badge badge = hoveredBadge;
		if (badge == null) {
			return;
		}
		int image = 112;
		int h = image + 24;
		String name = badge.name() != null ? badge.name() : Component.translatable("phantasmon.battle.lobby.badge.unnamed").getString();
		// As wide as its longest line needs (2026-11-28: a long name was cut).
		float need = Math.max(textWidth(name, 2f, true, 0.6f), textWidth(badge.leader() != null ? badge.leader() : "?", 2f, false, 0f));
		int w = Math.max(300, Math.min(760, Math.round(12 + image + 14 + need + 16)));
		int x = hoveredBadgeX + BADGE_SLOT + 12;
		if (x + w > 1580) {
			x = hoveredBadgeX - 12 - w;
		}
		int y = Math.max(20, Math.min(hoveredBadgeY + BADGE_SLOT / 2 - h / 2, 880 - h));
		renderModalCard(g, x, y, w, h, 1f);
		g.fill(x + 12, y + 12, x + 12 + image, y + 12 + image, 0xC0000000);
		sunkenEdge(g, x + 12, y + 12, image, image);
		com.mojang.blaze3d.systems.RenderSystem.enableBlend();
		g.blit(badge.texture(), x + 16, y + 16, 0, 0, image - 8, image - 8, image - 8, image - 8);
		float textX = x + 12 + image + 14;
		float maxWidth = x + w - 12 - textX;
		drawText(g, fitText(name, maxWidth, 2f, true, 0.6f), textX, y + 18, 2f, WHITE, true, 0.6f);
		drawText(g, upper(Component.translatable("phantasmon.battle.lobby.badge.leader").getString()), textX, y + 50, 1f, TITLE, true, 1.2f);
		drawText(g, fitText(badge.leader() != null ? badge.leader() : "?", maxWidth, 2f, false, 0f), textX, y + 64, 2f, TEXT2, false, 0f);
		drawText(g, upper(Component.translatable("phantasmon.battle.lobby.badge.type").getString()), textX, y + 92, 1f, TITLE, true, 1.2f);
		com.cobblemon.mod.common.api.types.ElementalType type = badge.type() == null ? null : Look.safeType(badge.type());
		if (type != null) {
			drawTypeBadge(g, type, textX, y + 106, 1.5f);
		} else {
			drawText(g, "?", textX, y + 106, 2f, TEXT2, false, 0f);
		}
	}

	// ---- Quit modal ----

	private void renderQuitModal(GuiGraphics g, int mouseX, int mouseY) {
		beginModalLayer(g, 1f);
		double mx = toCanvasX(mouseX);
		double my = toCanvasY(mouseY);
		renderModalCard(g, 540, 370, 520, 160, 1f);
		drawCentered(g, upper(Component.translatable("phantasmon.battle.lobby.quit_title").getString()), 800, 397, 2f, TITLE, true, 2f);
		drawCentered(g, Component.translatable("phantasmon.battle.lobby.quit_text").getString(), 800, 437, 2f, TEXT2, false, 0f);
		renderButton(g, 695, 476, 103, 28, "phantasmon.battle.lobby.cancel", false, inside(mx, my, 695, 476, 103, 28));
		renderButton(g, 808, 476, 97, 28, "phantasmon.battle.lobby.quit", true, inside(mx, my, 808, 476, 97, 28));
		endModalLayer(g);
	}
}
