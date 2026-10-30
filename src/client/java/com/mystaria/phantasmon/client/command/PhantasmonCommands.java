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
						}))));
	}
}
