package com.mystaria.phantasmon.client.gui;

import java.util.List;
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
	/** The music screen opens over the lobby: leaving the lobby screen for it is not leaving the lobby. */
	private boolean openingMusic;

	// ---- Battle format drop-down (TODO-24), bottom left; its list opens upward ----
	private static final int FORMAT_X = 27;
	private static final int FORMAT_W = 470;
	private static final int FORMAT_ROW_H = 30;
	private static final int STATUS_X = FORMAT_X + FORMAT_W + 14;
	private boolean formatListOpen;
	/** Issues of the own Pokémon under the cursor (footer explains them), refreshed every frame. */
	private List<BattleLobbyState.Issue> hoveredIssues = List.of();

	private final LiveBattleController controller;
	private boolean quitConfirmOpen;

	public PhantasmonBattleLobbyScreen(LiveBattleController controller) {
		super(Component.translatable("phantasmon.battle.lobby.title"));
		this.controller = controller;
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
		if (openingMusic) {
			openingMusic = false;
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
		if (formatListOpen) {
			List<BattleLobbyState.FormatOption> formats = state.formats();
			int top = formatListTop(formats.size());
			for (int i = 0; i < formats.size(); i++) {
				if (inside(x, y, FORMAT_X, top + i * FORMAT_ROW_H, FORMAT_W, FORMAT_ROW_H)) {
					controller.lobbySetFormat(formats.get(i).id());
				}
			}
			formatListOpen = false;
			return true;
		}
		if (quitConfirmOpen) {
			if (inside(x, y, 695, 476, 103, 28)) {
				quitConfirmOpen = false;
			} else if (inside(x, y, 808, 476, 97, 28)) {
				controller.lobbyLeave();
				onClose();
			}
			return true;
		}
		if (inside(x, y, 715, 15, 170, 48)) {
			com.mystaria.phantasmon.client.audio.PhantasmonSounds.play(com.mystaria.phantasmon.client.audio.PhantasmonSounds.Sfx.PRESSING_A);
			controller.lobbyToggleReady();
		} else if (inside(x, y, FORMAT_X, 849, FORMAT_W, 28)) {
			formatListOpen = true;
		} else if (inside(x, y, 1475, 849, 97, 28)) {
			quitConfirmOpen = true;
		} else if (inside(x, y, MUSIC_X, 849, MUSIC_W, 28)) {
			openingMusic = true;
			minecraft.setScreen(new PhantasmonMusicScreen(this));
		} else if (inside(x, y, TIMER_X, 849, TIMER_W, 28)) {
			controller.lobbyEnableTimer();
		} else if (inside(x, y, CARD_X + 9, 716, 559, 48)) {
			controller.lobbySwitchTeam();
		} else {
			int slot = slotAt(x, y);
			if (slot >= 0) {
				controller.lobbySelectLead(slot);
			}
			// Right rail: read-only preview.
		}
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 256) {
			if (formatListOpen) {
				formatListOpen = false;
				return true;
			}
			// Échap = same as QUITTER: opens the confirmation, or closes it again.
			quitConfirmOpen = !quitConfirmOpen;
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/** Own rail slot index under (x, y), -1 if none (same 2×3 grid as the trade screen). */
	private static int slotAt(double x, double y) {
		for (int i = 0; i < BattleLobbyState.TEAM_SIZE; i++) {
			if (inside(x, y, slotX(i), slotY(i), 85, 214)) {
				return i;
			}
		}
		return -1;
	}

	private static int slotX(int index) {
		return 24 + (index % 2) * 92;
	}

	private static int slotY(int index) {
		return 117 + (index / 2) * 221;
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
		double mx = quitConfirmOpen || formatListOpen ? -1 : toCanvasX(mouseX);
		double my = quitConfirmOpen || formatListOpen ? -1 : toCanvasY(mouseY);
		int hovered = slotAt(mx, my);
		hoveredIssues = state.ownIssues(hovered);
		double lookX = toCanvasX(mouseX);
		double lookY = toCanvasY(mouseY);

		beginCanvas(graphics);
		renderRootPanel(graphics);
		renderHeader(graphics, state, mx, my);
		renderOwnRail(graphics, state, mx, my);
		renderOpponentRail(graphics, state);
		renderPlayerCard(graphics, state, true, lookX, lookY, mx, my);
		renderPlayerCard(graphics, state, false, lookX, lookY, mx, my);
		renderFooter(graphics, state, mx, my);
		endCanvas(graphics);
		if (formatListOpen) {
			renderFormatList(graphics, state, mouseX, mouseY);
		}

		if (quitConfirmOpen) {
			renderQuitModal(graphics, mouseX, mouseY);
		}
	}

	// ---- Header ----

	private void renderHeader(GuiGraphics g, BattleLobbyState state, double mx, double my) {
		renderHeaderPlates(g);
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

		int x = 715;
		int y = 15;
		int w = 170;
		int h = 48;
		renderPrimaryButtonFrame(g, x, y, w, h, inside(mx, my, x, y, w, h), state.ownReady());
		String label = state.ownReady() ? Component.translatable("phantasmon.battle.lobby.ready").getString()
				: upper(Component.translatable("phantasmon.battle.lobby.ready_button").getString());
		float width = textWidth(label, 2f, true, 1f);
		drawText(g, label, x + (w - width) / 2f, y + 17, 2f, state.ownReady() ? READY_TEXT : CYAN, true, 1f);
	}

	// ---- Rails ----

	private void renderOwnRail(GuiGraphics g, BattleLobbyState state, double mx, double my) {
		renderRailFrame(g, 15, 72, 194, 758,
				Component.translatable("phantasmon.battle.lobby.team", controller.localPlayerName()).getString(),
				Component.translatable("phantasmon.battle.lobby.rail_hint_own"));
		int hovered = state.ownReady() ? -1 : slotAt(mx, my);
		for (int i = 0; i < BattleLobbyState.TEAM_SIZE; i++) {
			PokemonDto pokemon = state.ownSlot(i);
			SlotLook look = pokemon != null && i == state.ownLead() ? SlotLook.SELECTED
					: (i == hovered && pokemon != null ? SlotLook.HOVER : SlotLook.NORMAL);
			renderSlot(g, slotX(i), slotY(i), 85, 214, pokemon, look, RAIL_SLOT);
			if (pokemon != null && !state.ownIssues(i).isEmpty()) {
				renderRuleBreak(g, slotX(i), slotY(i));
			}
		}
	}

	private void renderOpponentRail(GuiGraphics g, BattleLobbyState state) {
		renderRailFrame(g, 15 + RIGHT_RAIL_OFFSET, 72, 194, 758,
				Component.translatable("phantasmon.battle.lobby.team", state.opponentName()).getString(),
				Component.translatable("phantasmon.battle.lobby.rail_hint_opponent", state.opponentName()));
		for (int i = 0; i < BattleLobbyState.TEAM_SIZE; i++) {
			renderSlot(g, slotX(i) + RIGHT_RAIL_OFFSET, slotY(i), 85, 214, state.opponentSlot(i), SlotLook.NORMAL, RAIL_SLOT, false);
			if (state.opponentSlot(i) != null && state.opponentFlagged(i)) {
				renderRuleBreak(g, slotX(i) + RIGHT_RAIL_OFFSET, slotY(i));
			}
		}
	}

	// ---- Player cards ----

	private void renderPlayerCard(GuiGraphics g, BattleLobbyState state, boolean own, double lookX, double lookY, double mx, double my) {
		int x = CARD_X + (own ? 0 : RIGHT_CARD_OFFSET);
		blitTexture(g, TEX_CARD, x, 72, 577, 758);
		outline(g, x, 72, 577, 758, CYAN2);
		g.fill(x, 72, x + 577, 75, own ? ACCENT_LEFT : ACCENT_RIGHT);

		// Head: player name, team source.
		g.fill(x + 1, 75, x + 576, VIEW_Y, CARD_HEAD_BG);
		g.fill(x + 1, VIEW_Y - hairline(), x + 576, VIEW_Y, LINE_28);
		boolean cobblemon = own ? state.ownIsCobblemon() : state.opponentIsCobblemon();
		String source = upper(sourceLabel(cobblemon).getString());
		float sourceWidth = textWidth(source, 1f, false, 1.5f);
		drawText(g, source, x + 565 - sourceWidth, 93, 1f, MUTED, false, 1.5f);
		String name = fitText(own ? controller.localPlayerName() : state.opponentName(), 565 - sourceWidth - 24 - 12, 2f, true, 0.6f);
		drawText(g, name, x + 12, 89, 2f, WHITE, true, 0.6f);

		// Player model window.
		blitTexture(g, TEX_VIEWPORT, x + 1, VIEW_Y, 575, VIEW_H);
		blitTexture(g, TEX_SHADOW, x + 288 - 80, 446, 160, 26);
		renderPlayer(g, own ? Minecraft.getInstance().player : opponentEntity(state.opponentUuid()),
				own ? null : state.opponentUuid(), x + 1, VIEW_Y, 575, VIEW_H, lookX, lookY);
		boolean ready = own ? state.ownReady() : state.opponentReady();
		String readyText = ready ? Component.translatable("phantasmon.battle.lobby.ready").getString()
				: Component.translatable("phantasmon.battle.lobby.not_ready").getString();
		float readyWidth = textWidth(readyText, 1f, true, 1.5f);
		g.fill(x + 10, VIEW_Y + 10, Math.round(x + 10 + readyWidth + 16), VIEW_Y + 32, BADGE_BORDER);
		outline(g, x + 10, VIEW_Y + 10, Math.round(readyWidth + 16), 22, ready ? READY_BORDER : LINE_30);
		drawText(g, readyText, x + 18, VIEW_Y + 17, 1f, ready ? READY_TEXT : DIM, true, 1.5f);
		g.fill(x + 1, VIEW_Y + VIEW_H, x + 576, VIEW_Y + VIEW_H + hairline(), LINE_20);

		// Lead.
		infoBox(g, x + 9, 506, 559, 200, own ? "phantasmon.battle.lobby.own_lead" : "phantasmon.battle.lobby.opponent_lead");
		if (own) {
			renderOwnLead(g, state.ownLeadPokemon(), x);
		} else {
			drawCentered(g, "?", x + 288, 548, 6f, TITLE, true, 0f);
			drawCentered(g, Component.translatable("phantasmon.battle.lobby.lead_hidden").getString(), x + 288, 640, 2f, DIM, false, 0f);
		}

		// Team switch (own) / team source (opponent), and what the other player can see.
		String hint;
		if (own) {
			boolean locked = state.ownReady();
			boolean hovered = !locked && inside(mx, my, x + 9, 716, 559, 48);
			int[] gradient = hovered ? STD_BUTTON_HOVER : STD_BUTTON;
			g.fillGradient(x + 9, 716, x + 568, 764, gradient[0], gradient[1]);
			outline(g, x + 9, 716, 559, 48, locked ? LINE_30 : CYAN);
			String label = fitText(upper(Component.translatable("phantasmon.battle.lobby.switch_team", sourceLabel(!cobblemon)).getString()),
					540, 2f, true, 1f);
			drawCentered(g, label, x + 288, 733, 2f, locked ? DIM : WHITE, true, 1f);
			hint = Component.translatable(locked ? "phantasmon.battle.lobby.switch_locked" : "phantasmon.battle.lobby.privacy_own").getString();
		} else {
			g.fill(x + 9, 716, x + 568, 764, INFO_BOX_BG);
			outline(g, x + 9, 716, 559, 48, LINE_20);
			drawCentered(g, upper(sourceLabel(cobblemon).getString()), x + 288, 733, 2f, TEXT2, true, 1f);
			hint = Component.translatable("phantasmon.battle.lobby.privacy_opponent").getString();
		}
		drawCentered(g, fitText(hint, 550, 1f, false, 0f), x + 288, 790, 1f, DIM, false, 0f);
	}

	/** Our lead: model on the left, name / level / held item on the right. */
	private void renderOwnLead(GuiGraphics g, PokemonDto lead, int x) {
		if (lead == null) {
			drawCentered(g, "—", x + 288, 600, 2f, DIM, false, 0f);
			return;
		}
		int boxX = x + 19;
		int boxY = 530;
		int boxW = 170;
		int boxH = 168;
		g.flush();
		enableCanvasScissor(g, boxX, boxY, boxW, boxH);
		PokemonGuiRendering.renderModel(g, lead.species(), lead.form(), lead.isShiny(), PokemonGuiRendering.storedGender(lead),
				boxX + boxW / 2f, slotModelAnchorY(boxY + boxH / 2f, SLOT_MODEL_SCALE * 1.15f), SLOT_MODEL_SCALE * 1.15f);
		g.disableScissor();

		float textX = boxX + boxW + 16;
		float maxWidth = x + 560 - textX;
		String star = lead.isShiny() ? "★ " : "";
		float starWidth = textWidth(star, 2f, true, 0f);
		if (!star.isEmpty()) {
			drawText(g, star, textX, 556, 2f, STAR, true, 0f);
		}
		drawText(g, fitText(displayName(lead), maxWidth - starWidth, 2f, true, 0.6f), textX + starWidth, 556, 2f, WHITE, true, 0.6f);
		drawText(g, Component.translatable("phantasmon.trade.screen.level", lead.level()).getString(), textX, 600, 2f, TEXT2, false, 0f);
		String itemName = PokemonGuiRendering.heldItemName(heldItemId(lead));
		if (itemName != null && PokemonGuiRendering.renderHeldItem(g, heldItemId(lead), textX, 640, 20)) {
			drawText(g, fitText(itemName, maxWidth - 26, 2f, false, 0f), textX + 26, 643, 2f, TEXT2, false, 0f);
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
		glowInside(g, x, y, 85, 214, 0xFF5078, 0.35f, 8);
		outline(g, x - 2, y - 2, 89, 218, DANGER_BORDER);
		outline(g, x - 1, y - 1, 87, 216, DANGER_BORDER);
		outline(g, x, y, 85, 214, DANGER_BORDER);
		drawText(g, "!", x + 8, y + 190, 2f, DANGER_BORDER, true, 0f);
	}

	private void renderFooter(GuiGraphics g, BattleLobbyState state, double mx, double my) {
		renderFooterBar(g, null, MUTED, 0);
		String status = statusLine(state);
		int statusColor = state.lastErrorCode() != null || !hoveredIssues.isEmpty() ? ERROR_TEXT : MUTED;
		if (status != null) {
			drawText(g, fitText(status, MUSIC_X - 30 - STATUS_X, 2f, false, 0f), STATUS_X, 855.5f, 2f, statusColor, false, 0f);
		}
		boolean formatHovered = inside(mx, my, FORMAT_X, 849, FORMAT_W, 28);
		renderPrimaryButtonFrame(g, FORMAT_X, 849, FORMAT_W, 28, formatHovered, false);
		String label = Component.translatable("phantasmon.battle.lobby.format", formatName(state.format())).getString() + "  ▲";
		drawText(g, fitText(label, FORMAT_W - 16, 2f, false, 0f), FORMAT_X + 8, 856, 2f, CYAN, false, 0f);
		renderButton(g, MUSIC_X, 849, MUSIC_W, 28, "phantasmon.music.button", false, inside(mx, my, MUSIC_X, 849, MUSIC_W, 28));
		if (state.timerOn()) {
			long left = state.timerSecondsLeft();
			renderPrimaryButtonFrame(g, TIMER_X, 849, TIMER_W, 28, false, false);
			drawCentered(g, "⏱ " + clock(left), TIMER_X + TIMER_W / 2f, 856, 2f, left <= 10 ? DANGER_BORDER : CYAN, true, 0f);
		} else {
			renderButton(g, TIMER_X, 849, TIMER_W, 28, "phantasmon.battle.lobby.timer_button", false, inside(mx, my, TIMER_X, 849, TIMER_W, 28));
		}
		renderButton(g, 1475, 849, 97, 28, "phantasmon.battle.lobby.quit", true, inside(mx, my, 1475, 849, 97, 28));
	}

	private String statusLine(BattleLobbyState state) {
		if (!hoveredIssues.isEmpty()) {
			return hoveredIssues.stream()
					.map(issue -> Component.translatable("phantasmon.battle.lobby.issue." + issue.code(), issue.subject()).getString())
					.distinct().collect(java.util.stream.Collectors.joining(" · "));
		}
		if (state.lastErrorCode() != null) {
			return stripPrefix(Component.translatable(BackendErrorMessages.translationKey(state.lastErrorCode())).getString());
		}
		if (state.ownReady() && !state.opponentReady()) {
			return Component.translatable("phantasmon.battle.lobby.waiting_opponent", state.opponentName()).getString();
		}
		if (!state.ownReady() && state.opponentReady()) {
			return Component.translatable("phantasmon.battle.lobby.opponent_is_ready", state.opponentName()).getString();
		}
		if (state.ownTeamBreaksRules()) {
			long count = java.util.stream.IntStream.range(0, BattleLobbyState.TEAM_SIZE).filter(i -> !state.ownIssues(i).isEmpty()).count();
			return Component.translatable("phantasmon.battle.lobby.issues", count).getString();
		}
		if (state.timerOn()) {
			return Component.translatable("phantasmon.battle.lobby.timer_on", clock(state.timerTotalSeconds()),
					state.timerByName() == null ? "?" : state.timerByName()).getString();
		}
		return null;
	}

	private static String clock(long seconds) {
		return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
	}

	// ---- Format list ----

	private static int formatListTop(int count) {
		return 845 - count * FORMAT_ROW_H;
	}

	private static String formatName(BattleLobbyState.FormatOption format) {
		return "free".equals(format.id()) ? Component.translatable("phantasmon.battle.lobby.format.free").getString() : format.name();
	}

	/** Above everything (models included), on the canvas transform. */
	private void renderFormatList(GuiGraphics g, BattleLobbyState state, int mouseX, int mouseY) {
		beginModalLayer(g, 0f);
		double mx = toCanvasX(mouseX);
		double my = toCanvasY(mouseY);
		List<BattleLobbyState.FormatOption> formats = state.formats();
		int top = formatListTop(formats.size());
		g.fill(FORMAT_X - 4, top - 4, FORMAT_X + FORMAT_W + 4, 849, 0xF0081422);
		outline(g, FORMAT_X - 4, top - 4, FORMAT_W + 8, 849 - top + 4, CYAN);
		for (int i = 0; i < formats.size(); i++) {
			BattleLobbyState.FormatOption format = formats.get(i);
			int rowY = top + i * FORMAT_ROW_H;
			boolean selected = format.id().equals(state.formatId());
			boolean hovered = inside(mx, my, FORMAT_X, rowY, FORMAT_W, FORMAT_ROW_H);
			if (hovered || selected) {
				g.fill(FORMAT_X, rowY, FORMAT_X + FORMAT_W, rowY + FORMAT_ROW_H, selected ? SLOT_SELECTED_BG : SLOT_HOVER_BG);
			}
			// A thin line between "Free", the National Dex formats and the Gen 9 ones.
			if (i == 1 || "gen9ou".equals(format.id())) {
				g.fill(FORMAT_X + 6, rowY, FORMAT_X + FORMAT_W - 6, rowY + 1, LINE_30);
			}
			drawText(g, fitText((selected ? "✔ " : "   ") + formatName(format), FORMAT_W - 16, 2f, false, 0f), FORMAT_X + 8,
					rowY + 8, 2f, selected ? READY_TEXT : WHITE, false, 0f);
		}
		endModalLayer(g);
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
