package com.mystaria.phantasmon.client.command;

import java.util.UUID;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.auth.AuthService;
import com.mystaria.phantasmon.client.ghost.GhostSession;
import com.mystaria.phantasmon.client.network.PingToggle;
import com.mystaria.phantasmon.client.pokemon.PokemonCommandHandler;
import com.mystaria.phantasmon.client.battle.LiveBattleController;
import com.mystaria.phantasmon.client.trade.LiveTradeController;
import com.mystaria.phantasmon.client.trade.TradeCommandHandler;

/**
 * {@code /phantasmon login} triggers the auth flow (CAD Phase 5: "écran/commande
 * de connexion"). A command is used rather than a screen for this first pass —
 * simplest way to prove the flow end to end; a dedicated login screen can
 * replace/complement it later without changing {@link AuthService}.
 *
 * <p>The ping toggle and the debug commands moved under {@code /phantasmon admin} (TODO-25,
 * {@link com.mystaria.phantasmon.client.admin.AdminCommands}).
 *
 * <p>No command opens the PC or starts a trade or a battle (TODO-22, Adrien 2026-10-05): the PC opens with its
 * key, trades and battles start from Cobblemon's interaction wheel. What remains of {@code trade} / {@code battle}
 * ({@code join}, {@code decline}, {@code timer}) only exists for the clickable chat buttons.
 *
 * <p>{@code /phantasmon pokemon *} (CAD Phase 6, all-commands approach, Adrien:
 * 2026-09-26): {@code import} (reads a Showdown block from the clipboard),
 * {@code export [uuid]} (copies the team, or one Pokémon, as Showdown text — TODO-11),
 * {@code list}, {@code pc <box>}, {@code delete <uuid>}, {@code clone <uuid>},
 * {@code edit <uuid> level <n>} — see {@link PokemonCommandHandler}.
 *
 * <p>No {@code sendout}/{@code recall} command either (Adrien 2026-10-05): Ghosts go out and back with
 * Cobblemon's party keys on the Ghost overlay.
 *
 */
public final class PhantasmonCommands {

	private PhantasmonCommands() {
	}

	public static void register(AuthService authService, PingToggle pingToggle, PokemonCommandHandler pokemonCommands,
			GhostSession ghostSession, TradeCommandHandler tradeCommands, LiveTradeController liveTrade,
			LiveBattleController liveBattle) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			var pokemonNode = ClientCommandManager.literal("pokemon")
					.then(ClientCommandManager.literal("import").executes(context -> {
						pokemonCommands.importFromClipboard(context.getSource());
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("export")
							.executes(context -> {
								pokemonCommands.exportToClipboard(context.getSource(), null);
								return Command.SINGLE_SUCCESS;
							})
							.then(ClientCommandManager.argument("uuid", UuidArgument.uuid()).executes(context -> {
								pokemonCommands.exportToClipboard(context.getSource(), context.getArgument("uuid", UUID.class));
								return Command.SINGLE_SUCCESS;
							})))
					.then(ClientCommandManager.literal("list").executes(context -> {
						pokemonCommands.list(context.getSource());
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("pc")
							.then(ClientCommandManager.argument("box", IntegerArgumentType.integer(1, 16)).executes(context -> {
								pokemonCommands.pcBox(context.getSource(), IntegerArgumentType.getInteger(context, "box"));
								return Command.SINGLE_SUCCESS;
							}))
							.then(ClientCommandManager.literal("move")
									.then(ClientCommandManager.argument("uuid", UuidArgument.uuid())
											.then(ClientCommandManager.argument("box", IntegerArgumentType.integer(1, 16))
													.then(ClientCommandManager.argument("slot", IntegerArgumentType.integer(1, 30)).executes(context -> {
														pokemonCommands.pcMove(context.getSource(),
																context.getArgument("uuid", UUID.class),
																IntegerArgumentType.getInteger(context, "box"),
																IntegerArgumentType.getInteger(context, "slot"));
														return Command.SINGLE_SUCCESS;
													}))))))
					.then(ClientCommandManager.literal("delete")
							.then(ClientCommandManager.argument("uuid", UuidArgument.uuid()).executes(context -> {
								pokemonCommands.delete(context.getSource(), context.getArgument("uuid", UUID.class));
								return Command.SINGLE_SUCCESS;
							})))
					.then(ClientCommandManager.literal("clone")
							.then(ClientCommandManager.argument("uuid", UuidArgument.uuid()).executes(context -> {
								pokemonCommands.clone(context.getSource(), context.getArgument("uuid", UUID.class));
								return Command.SINGLE_SUCCESS;
							})))
					.then(ClientCommandManager.literal("edit")
							.then(ClientCommandManager.argument("uuid", UuidArgument.uuid())
									.then(ClientCommandManager.literal("level")
											.then(ClientCommandManager.argument("level", IntegerArgumentType.integer(1, 100)).executes(context -> {
												pokemonCommands.editLevel(context.getSource(),
														context.getArgument("uuid", UUID.class),
														IntegerArgumentType.getInteger(context, "level"));
												return Command.SINGLE_SUCCESS;
											})))))
						.then(ClientCommandManager.literal("team")
								.executes(context -> {
									pokemonCommands.teamView(context.getSource());
									return Command.SINGLE_SUCCESS;
								})
								.then(ClientCommandManager.literal("set")
										.then(ClientCommandManager.argument("uuid", UuidArgument.uuid())
												.then(ClientCommandManager.argument("slot", IntegerArgumentType.integer(1, 6)).executes(context -> {
													pokemonCommands.teamSet(context.getSource(),
															context.getArgument("uuid", UUID.class),
															IntegerArgumentType.getInteger(context, "slot"));
													return Command.SINGLE_SUCCESS;
												}))))
								.then(ClientCommandManager.literal("clear")
										.then(ClientCommandManager.argument("uuid", UuidArgument.uuid())
												.then(ClientCommandManager.argument("box", IntegerArgumentType.integer(1, 16))
														.then(ClientCommandManager.argument("slot", IntegerArgumentType.integer(1, 30)).executes(context -> {
															pokemonCommands.teamClear(context.getSource(),
																context.getArgument("uuid", UUID.class),
																IntegerArgumentType.getInteger(context, "box"),
																IntegerArgumentType.getInteger(context, "slot"));
															return Command.SINGLE_SUCCESS;
														}))))));

			// Only the answers to an invitation, run by the chat [Accept] / [Decline] buttons: inviting goes through
			// Cobblemon's interaction wheel (TODO-22, Adrien 2026-10-05).
			var tradeNode = ClientCommandManager.literal("trade")
					.then(ClientCommandManager.literal("join").executes(context -> {
						liveTrade.acceptInvite();
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("decline").executes(context -> {
						liveTrade.declineInvite();
						return Command.SINGLE_SUCCESS;
					}));

			// Live Ghost battles: only the answers and the turn timer, run by the chat buttons; inviting goes through the
			// wheel (TODO-22).
			var battleNode = ClientCommandManager.literal("battle")
					.then(ClientCommandManager.literal("join")
							.executes(context -> {
								liveBattle.acceptInvite();
								return Command.SINGLE_SUCCESS;
							})
							.then(ClientCommandManager.literal("ghost").executes(context -> {
								liveBattle.acceptInvite(com.mystaria.phantasmon.client.battle.LiveBattleController.TeamChoice.GHOST);
								return Command.SINGLE_SUCCESS;
							}))
							.then(ClientCommandManager.literal("cobblemon").executes(context -> {
								liveBattle.acceptInvite(com.mystaria.phantasmon.client.battle.LiveBattleController.TeamChoice.COBBLEMON);
								return Command.SINGLE_SUCCESS;
							})))
					.then(ClientCommandManager.literal("decline").executes(context -> {
						liveBattle.declineInvite();
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("timer").executes(context -> {
						liveBattle.enableTimer();
						return Command.SINGLE_SUCCESS;
					}));

			dispatcher.register(ClientCommandManager.literal("phantasmon")
					.then(ClientCommandManager.literal("login").executes(context -> {
						// Still authenticated but the presence connection dropped: re-open it now (no new login needed).
						if (ghostSession.reconnectNow()) {
							context.getSource().sendFeedback(Component.translatable("phantasmon.ghost.reconnecting"));
							return Command.SINGLE_SUCCESS;
						}
						authService.login();
						return Command.SINGLE_SUCCESS;
					}))
					.then(pokemonNode)
					.then(tradeNode)
					.then(battleNode));
		});
	}
}
