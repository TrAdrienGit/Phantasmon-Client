package com.mystaria.phantasmon.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.mystaria.phantasmon.client.network.BackendErrorMessages;
import com.mystaria.phantasmon.client.pokemon.PokemonDto;
import com.mystaria.phantasmon.client.trade.LiveTradeController;
import com.mystaria.phantasmon.client.trade.LiveTradeState;

/**
 * Live trade screen (Adrien 2026-10-02), a faithful port of the handoff
 * mock-up {@code phantasmon_trade_ui.html} / {@code SPEC_ECRAN_ECHANGE.md}:
 * every coordinate below is the spec's "px" column (zones Z01-Z29) used
 * verbatim. Canvas, textures, colors, slots, the Pokémon card, buttons and
 * modals come from {@link PhantasmonCanvasScreen} (shared with the PC screen).
 *
 * <p>Every state shown here is the server's ({@link LiveTradeState}) — clicks
 * only send requests through {@link LiveTradeController}.
 */
public final class PhantasmonTradeScreen extends PhantasmonCanvasScreen {

	/** Z26 = Z12 + 586 px, Z27 = Z05 + 1376 px (spec §3 "Règles de répétition"). */
	private static final int RIGHT_CARD_OFFSET = 586;
	private static final int RIGHT_RAIL_OFFSET = 1376;
	private static final int BAND_LABEL_BG = 0xFF081422;

	// ---- Animations (spec §8) ----
	private static final float BALL_PERIOD_MS = 1800f;
	private static final float MODAL_FADE_MS = 200f;
	/** After a successful trade: the ball travels for 2 cycles, then "ÉCHANGE TERMINÉ" shows and the screen closes on its own (it used to loop forever). */
	private static final long TRANSFER_ANIMATION_MS = (long) (BALL_PERIOD_MS * 2);
	private static final long AUTO_CLOSE_MS = TRANSFER_ANIMATION_MS + 1800;

	private final LiveTradeController controller;

	private boolean quitConfirmOpen;
	private long completedAt = -1;

	public PhantasmonTradeScreen(LiveTradeController controller) {
		super(Component.translatable("phantasmon.trade.screen.title"));
		this.controller = controller;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void tick() {
		LiveTradeState state = controller.state();
		if (state == null || state.phase() == LiveTradeState.Phase.CANCELLED) {
			onClose();
		} else if (completedAt > 0 && System.currentTimeMillis() - completedAt >= AUTO_CLOSE_MS) {
			controller.closeAfterCompletion();
		}
	}

	@Override
	public void removed() {
		controller.onScreenRemoved();
	}

	// =====================================================================
	// Input
	// =====================================================================

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		LiveTradeState state = controller.state();
		if (button != 0 || state == null) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		double x = toCanvasX(mouseX);
		double y = toCanvasY(mouseY);

		if (state.phase() == LiveTradeState.Phase.COMPLETED) {
			if (inside(x, y, 753, 529, 94, 28)) {
				controller.closeAfterCompletion();
			}
			return true;
		}
		if (quitConfirmOpen) {
			if (inside(x, y, 695, 476, 103, 28)) {
				quitConfirmOpen = false;
			} else if (inside(x, y, 808, 476, 97, 28)) {
				controller.leave();
				onClose();
			}
			return true;
		}
		if (inside(x, y, 715, 15, 170, 48)) {
			controller.toggleReady();
			return true;
		}
		if (inside(x, y, 1475, 849, 97, 28)) {
			quitConfirmOpen = true;
			return true;
		}
		int slot = slotAt(x, y, 0);
		if (slot >= 0) {
			controller.selectOffer(state.ownSlot(slot));
			return true;
		}
		// Right rail: read-only, the partner's offer is driven by the server (Adrien 2026-10-02).
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 256) {
			LiveTradeState state = controller.state();
			if (state != null && state.phase() == LiveTradeState.Phase.COMPLETED) {
				controller.closeAfterCompletion();
			} else {
				// Échap = same as QUITTER (spec §7.4): opens the confirmation, or closes it again.
				quitConfirmOpen = !quitConfirmOpen;
			}
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/** Rail slot index under (x, y) for the rail starting at Z05 + {@code railOffset}, -1 if none (spec §3: 2×3 grid, 85×214 slots, 92/221 px steps). */
	private static int slotAt(double x, double y, int railOffset) {
		for (int i = 0; i < LiveTradeState.TEAM_SIZE; i++) {
			if (inside(x, y, slotX(i) + railOffset, slotY(i), 85, 214)) {
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
		LiveTradeState state = controller.state();
		// The menu no longer fills the screen: the (blurred, dimmed) game shows around it.
		renderWorldBackdrop(graphics, mouseX, mouseY, partialTick);
		if (state == null) {
			return;
		}
		if (state.phase() == LiveTradeState.Phase.COMPLETED && completedAt < 0) {
			completedAt = System.currentTimeMillis();
			quitConfirmOpen = false;
		}
		boolean modalOpen = quitConfirmOpen || state.phase() == LiveTradeState.Phase.COMPLETED;
		double mx = modalOpen ? -1 : toCanvasX(mouseX);
		double my = modalOpen ? -1 : toCanvasY(mouseY);

		beginCanvas(graphics);
		renderRootPanel(graphics);
		renderHeader(graphics, state, mx, my);
		renderRail(graphics, state, 0, mx, my);
		renderRail(graphics, state, RIGHT_RAIL_OFFSET, mx, my);
		renderCard(graphics, state, 0);
		renderCard(graphics, state, RIGHT_CARD_OFFSET);
		renderFooter(graphics, state, mx, my);
		endCanvas(graphics);

		if (state.phase() == LiveTradeState.Phase.COMPLETED) {
			renderTradeModal(graphics, state, mouseX, mouseY);
		} else if (quitConfirmOpen) {
			renderQuitModal(graphics, mouseX, mouseY);
		}
	}

	// ---- Z02 / Z03 / Z04 ----

	private void renderHeader(GuiGraphics g, LiveTradeState state, double mx, double my) {
		renderHeaderPlates(g);

		String ownName = upper(controller.localPlayerName());
		drawText(g, ownName, 31, 32, 2f, WHITE, true, 1.5f);
		String partnerName = upper(state.partnerName());
		float partnerWidth = textWidth(partnerName, 2f, true, 1.5f);
		drawText(g, partnerName, 1569 - partnerWidth, 32, 2f, WHITE, true, 1.5f);
		if (state.partnerReady()) {
			// Not in the mock-up (which simulates the partner), but a real trade needs to show it.
			String ready = Component.translatable("phantasmon.trade.screen.ready").getString();
			drawText(g, ready, 1569 - partnerWidth - 16 - textWidth(ready, 1f, true, 2f), 36, 1f, READY_TEXT, true, 2f);
		}

		renderTradeButton(g, state, inside(mx, my, 715, 15, 170, 48));
	}

	/** Z03 — repos / survol / « PRÊT ✓ » (spec §4 Boutons, §7.3). */
	private void renderTradeButton(GuiGraphics g, LiveTradeState state, boolean hovered) {
		int x = 715;
		int y = 15;
		int w = 170;
		int h = 48;
		boolean ready = state.ownReady();
		renderPrimaryButtonFrame(g, x, y, w, h, hovered, ready);
		if (ready) {
			String label = Component.translatable("phantasmon.trade.screen.ready").getString();
			float width = textWidth(label, 2f, true, 1f);
			drawText(g, label, x + (w - width) / 2f, y + 17, 2f, READY_TEXT, true, 1f);
		} else {
			String arrows = "⇄";
			String label = upper(Component.translatable("phantasmon.trade.screen.trade_button").getString());
			float arrowsWidth = textWidth(arrows, 2f, false, 0f);
			float labelScale = textWidth(label, 2f, true, 1f) + arrowsWidth + 6 <= w - 12 ? 2f : 1f;
			float labelWidth = textWidth(label, labelScale, true, 1f);
			float startX = x + (w - arrowsWidth - 6 - labelWidth) / 2f;
			drawText(g, arrows, startX, y + 16, 2f, CYAN, false, 0f);
			drawText(g, label, startX + arrowsWidth + 6, y + (h - 7 * labelScale) / 2f, labelScale, CYAN, true, 1f);
		}
	}

	// ---- Z05-Z11 / Z27 ----

	private void renderRail(GuiGraphics g, LiveTradeState state, int dx, double mx, double my) {
		boolean own = dx == 0;
		String owner = own ? controller.localPlayerName() : state.partnerName();
		Component hint = own ? Component.translatable("phantasmon.trade.screen.rail_hint_own")
				: Component.translatable("phantasmon.trade.screen.rail_hint_partner", state.partnerName());
		renderRailFrame(g, 15 + dx, 72, 194, 758, Component.translatable("phantasmon.trade.screen.team", owner).getString(), hint);

		int selected = own ? state.ownOfferIndex() : state.partnerOfferIndex();
		int hovered = own ? slotAt(mx, my, 0) : -1;
		for (int i = 0; i < LiveTradeState.TEAM_SIZE; i++) {
			PokemonDto pokemon = own ? state.ownSlot(i) : state.partnerSlot(i);
			SlotLook look = i == selected ? SlotLook.SELECTED : (i == hovered && pokemon != null ? SlotLook.HOVER : SlotLook.NORMAL);
			renderSlot(g, slotX(i) + dx, slotY(i), 85, 214, pokemon, look, RAIL_SLOT);
		}
	}

	// ---- Z12-Z25 / Z26 ----

	private void renderCard(GuiGraphics g, LiveTradeState state, int dx) {
		boolean own = dx == 0;
		PokemonDto pokemon = own ? state.ownOfferPokemon() : state.partnerOfferPokemon();
		String owner = own ? controller.localPlayerName() : state.partnerName();
		String waiting = (own
				? Component.translatable(state.firstOwnPokemon() == null ? "phantasmon.trade.screen.empty_team" : "phantasmon.trade.screen.waiting_own")
				: Component.translatable("phantasmon.trade.screen.waiting_offer", owner)).getString();
		renderPokemonCard(g, pokemon, owner, dx, own ? ACCENT_LEFT : ACCENT_RIGHT, waiting, false);
	}

	// ---- Z28 / Z29 ----

	private void renderFooter(GuiGraphics g, LiveTradeState state, double mx, double my) {
		renderFooterBar(g, statusLine(state), state.lastErrorCode() != null ? ERROR_TEXT : MUTED, 1420);
		renderButton(g, 1475, 849, 97, 28, "phantasmon.trade.screen.quit", true, inside(mx, my, 1475, 849, 97, 28));
	}

	/** Left part of the footer — only when there's something to say (errors, or who we're waiting for). */
	private static String statusLine(LiveTradeState state) {
		if (state.lastErrorCode() != null) {
			return stripPrefix(Component.translatable(BackendErrorMessages.translationKey(state.lastErrorCode())).getString());
		}
		if (state.ownReady() && !state.partnerReady()) {
			return Component.translatable("phantasmon.trade.screen.waiting_partner", state.partnerName()).getString();
		}
		if (!state.ownReady() && state.partnerReady()) {
			return Component.translatable("phantasmon.trade.screen.partner_is_ready", state.partnerName()).getString();
		}
		return null;
	}

	// ---- Modals (spec §7.4 / §7.5) ----

	/** « ÉCHANGE EN COURS »: opens on the server's {@code TradeSessionCompleted} (spec §7.3 step 4). */
	private void renderTradeModal(GuiGraphics g, LiveTradeState state, int mouseX, int mouseY) {
		float fade = Math.min(1f, (System.currentTimeMillis() - completedAt) / MODAL_FADE_MS);
		beginModalLayer(g, fade);
		double mx = toCanvasX(mouseX);
		double my = toCanvasY(mouseY);

		// Ball travels for TRANSFER_ANIMATION_MS, then the window switches to its "done" state until tick() auto-closes it.
		boolean done = System.currentTimeMillis() - completedAt >= TRANSFER_ANIMATION_MS;
		renderModalCard(g, 540, 317, 520, 266, fade);
		String title = Component.translatable(done ? "phantasmon.trade.screen.done" : "phantasmon.trade.screen.in_progress").getString();
		drawCentered(g, upper(title), 800, 344, 2f, withAlpha(done ? READY_TEXT : TITLE, fade), true, 2f);

		// Transfer band: our name, the gradient line, the travelling Poké Ball, the partner's name.
		int bandX = 566;
		int bandY = 383;
		int bandW = 468;
		outline(g, bandX, bandY, bandW, 90, withAlpha(LINE_30, fade));
		int lineY = bandY + 44;
		int lineStart = bandX + Math.round(bandW * 0.08f);
		int lineEnd = bandX + Math.round(bandW * 0.92f);
		g.fillGradient(lineStart, lineY - 8, lineEnd, lineY, 0x0050E6FF, withAlpha(0x4050E6FF, fade));
		g.fillGradient(lineStart, lineY + 3, lineEnd, lineY + 11, withAlpha(0x4050E6FF, fade), 0x0050E6FF);
		for (int x = lineStart; x < lineEnd; x++) {
			float t = (x - lineStart) / (float) (lineEnd - lineStart);
			int color = t < 0.5f ? lerpColor(0x50E6FF, 0xFFFFFF, t * 2) : lerpColor(0xFFFFFF, 0xFF5078, (t - 0.5f) * 2);
			g.fill(x, lineY, x + 1, lineY + 3, withAlpha(0xFF000000 | color, fade));
		}
		bandLabel(g, controller.localPlayerName(), bandX + 30, lineY, false, fade);
		bandLabel(g, state.partnerName(), bandX + bandW - 30, lineY, true, fade);
		if (!done) {
			renderPokeBall(g, bandX + bandW / 2f, lineY + 1.5f, fade);
		}

		PokemonDto given = state.givenPokemon();
		PokemonDto received = state.receivedPokemon();
		String text = Component.translatable(done ? "phantasmon.trade.screen.done_text" : "phantasmon.trade.screen.in_progress_text",
				given == null ? "?" : displayName(given), received == null ? "?" : displayName(received)).getString();
		float textScale = textWidth(text, 2f, false, 0f) <= 470 ? 2f : 1f;
		drawCentered(g, fitText(text, 470, textScale, false, 0f), 800, 497 - 3.5f * textScale, textScale, withAlpha(TEXT2, fade), false, 0f);

		renderButton(g, 753, 529, 94, 28, "phantasmon.trade.screen.close", false, inside(mx, my, 753, 529, 94, 28));
		endModalLayer(g);
	}

	/** « QUITTER L'ÉCHANGE ? » (spec §7.4, ref_04_quitter.png). */
	private void renderQuitModal(GuiGraphics g, int mouseX, int mouseY) {
		beginModalLayer(g, 1f);
		double mx = toCanvasX(mouseX);
		double my = toCanvasY(mouseY);
		renderModalCard(g, 540, 370, 520, 160, 1f);
		drawCentered(g, upper(Component.translatable("phantasmon.trade.screen.quit_title").getString()), 800, 397, 2f, TITLE, true, 2f);
		drawCentered(g, Component.translatable("phantasmon.trade.screen.quit_text").getString(), 800, 437, 2f, TEXT2, false, 0f);
		renderButton(g, 695, 476, 103, 28, "phantasmon.trade.screen.cancel", false, inside(mx, my, 695, 476, 103, 28));
		renderButton(g, 808, 476, 97, 28, "phantasmon.trade.screen.quit", true, inside(mx, my, 808, 476, 97, 28));
		endModalLayer(g);
	}

	private void bandLabel(GuiGraphics g, String name, float anchorX, int lineY, boolean rightAligned, float fade) {
		String label = fitText(upper(name), 150, 1f, true, 0f);
		float labelScale = textWidth(label, 2f, true, 0f) <= 150 ? 2f : 1f;
		float width = textWidth(label, labelScale, true, 0f);
		float x = rightAligned ? anchorX - width : anchorX;
		float y = lineY + 1.5f - 3.5f * labelScale;
		g.fill(Math.round(x - 6), Math.round(y - 3), Math.round(x + width + 6), Math.round(y + 7 * labelScale + 3), withAlpha(BAND_LABEL_BG, fade));
		drawText(g, label, x, y, labelScale, withAlpha(WHITE, fade), true, 0f);
	}

	/** Poké Ball going back and forth 180 px with a full turn, 1.8 s linear cycle (spec §8). */
	private void renderPokeBall(GuiGraphics g, float centerX, float centerY, float fade) {
		if (fade < 0.5f) {
			return;
		}
		ItemStack ball = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball")));
		if (ball.isEmpty()) {
			return;
		}
		float p = (System.currentTimeMillis() % (long) BALL_PERIOD_MS) / BALL_PERIOD_MS;
		float triangle = p < 0.5f ? p * 2f : (1f - p) * 2f;
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(centerX - 90 + 180 * triangle, centerY, 0);
		pose.mulPose(Axis.ZP.rotationDegrees(360f * triangle));
		pose.scale(28 / 16f, 28 / 16f, 1f);
		g.renderItem(ball, -8, -8);
		pose.popPose();
	}
}
