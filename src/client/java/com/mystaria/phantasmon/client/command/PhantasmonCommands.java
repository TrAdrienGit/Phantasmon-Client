package com.mystaria.phantasmon.client.command;

import com.mojang.brigadier.Command;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.auth.AuthService;
import com.mystaria.phantasmon.client.battle.LiveBattleController;
import com.mystaria.phantasmon.client.ghost.GhostSession;
import com.mystaria.phantasmon.client.trade.LiveTradeController;

/**
 * The players' commands (Adrien 2026-10-30: as few as possible, everything else in game — the interaction wheel, the
 * PC, the menus): only {@code /phantasmon login}, which triggers the auth flow (CAD Phase 5) or re-opens a dropped
 * presence connection. The hub's are in {@link com.mystaria.phantasmon.client.hub.HubCommands}, the admins' under
 * {@code /phantasmon admin} ({@link com.mystaria.phantasmon.client.admin.AdminCommands}).
 *
 * <p>The answers to a trade or battle invitation and the turn timer are chat buttons ({@link ChatActions}), not
 * commands.
 */
public final class PhantasmonCommands {

	private PhantasmonCommands() {
	}

	public static void register(AuthService authService, GhostSession ghostSession, LiveTradeController liveTrade,
			LiveBattleController liveBattle) {
		ChatActions.register("trade.join", liveTrade::acceptInvite);
		ChatActions.register("trade.decline", liveTrade::declineInvite);
		ChatActions.register("battle.join", liveBattle::acceptInvite);
		ChatActions.register("battle.decline", liveBattle::declineInvite);
		ChatActions.register("battle.timer", liveBattle::enableTimer);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("phantasmon")
						.then(ClientCommandManager.literal("login").executes(context -> {
							// Still authenticated but the presence connection dropped: re-open it now (no new login needed).
							if (ghostSession.reconnectNow()) {
								context.getSource().sendFeedback(Component.translatable("phantasmon.ghost.reconnecting"));
								return Command.SINGLE_SUCCESS;
							}
							authService.login();
							return Command.SINGLE_SUCCESS;
						}))
						// Preview pictures of the lobby's choice windows (2026-11-27): what is on screen, without the HUD.
						.then(ClientCommandManager.literal("preview")
								.then(ClientCommandManager.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
										.suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
												java.util.List.of("field", "preset", "stadium", "ambiance", "intro"), builder))
										.then(ClientCommandManager.argument("id", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
												.suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
														previewIds(com.mojang.brigadier.arguments.StringArgumentType.getString(context, "kind"), liveBattle), builder))
												.executes(context -> {
													com.mystaria.phantasmon.client.gui.PreviewCapture.request(
															com.mojang.brigadier.arguments.StringArgumentType.getString(context, "kind"),
															com.mojang.brigadier.arguments.StringArgumentType.getString(context, "id").trim());
													return Command.SINGLE_SUCCESS;
												}))))));
		com.mystaria.phantasmon.client.gui.PreviewCapture.register();
	}

	/** The ids a preview may be taken of, for the command's suggestions. */
	private static java.util.List<String> previewIds(String kind, LiveBattleController liveBattle) {
		return switch (kind) {
			case "preset" -> com.mystaria.showtime.ambiance.Ambiances.scenePresetIds();
			case "stadium" -> com.mystaria.showtime.ambiance.Ambiances.stadiumIds();
			case "ambiance" -> com.mystaria.showtime.ambiance.Ambiances.plainAmbianceIds();
			case "intro" -> com.mystaria.showtime.battle.BattleCinematic.introNames();
			case "field" -> liveBattle.lobby() != null ? liveBattle.lobby().fields() : java.util.List.of();
			default -> java.util.List.of();
		};
	}
}
