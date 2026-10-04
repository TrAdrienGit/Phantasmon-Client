package com.mystaria.phantasmon.client.command;

import java.util.UUID;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.commands.SharedSuggestionProvider;
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
 * <p>{@code /phantasmon toggle-ping} switches the {@code GET /health} heartbeat
 * on/off for the current session — off by default (Adrien: 2026-09-26).
 *
 * <p>{@code /phantasmon pc} opens the graphical PC HUD screen (Adrien:
 * 2026-09-27, first HUD pass) — see {@link com.mystaria.phantasmon.client.gui.PhantasmonPcScreen}.
 * The command-based {@code /phantasmon pokemon pc <box>}/{@code pc move} below
 * remain available alongside it.
 *
 * <p>{@code /phantasmon pokemon *} (CAD Phase 6, all-commands approach, Adrien:
 * 2026-09-26): {@code import} (reads a Showdown block from the clipboard),
 * {@code export [uuid]} (copies the team, or one Pokémon, as Showdown text — TODO-11),
 * {@code list}, {@code pc <box>}, {@code delete <uuid>}, {@code clone <uuid>},
 * {@code edit <uuid> level <n>} — see {@link PokemonCommandHandler}.
 *
 * <p>{@code /phantasmon sendout}/{@code recall} (CAD Phase 7) trigger the
 * Ghost Entity spawn/despawn over the presence WebSocket — see
 * {@link GhostSession}. {@code sendout} takes no argument (Adrien:
 * 2026-09-29): it always sends out whichever Pokémon is in team slot 1, see
 * {@link PokemonCommandHandler#sendOutTeamLead}.
 *
 * <p>{@code /phantasmon trade *} (CAD Phase 8, same all-commands approach):
 * {@code propose <recipient> <offered> <requested>}, {@code accept <uuid>},
 * {@code cancel <uuid>}, {@code view <uuid>}, {@code list} — see
 * {@link TradeCommandHandler}, which also renders the WS trade notifications.
 *
 * <p>Live trade screen (Adrien 2026-10-02): {@code trade invite <player>}
 * (names suggested from the server's player list — no UUID to type),
 * {@code trade join}/{@code trade decline} to answer the latest invitation
 * (also run by the clickable [Accept]/[Decline] chat buttons) — see
 * {@link LiveTradeController}. Same invitation is also bound to a keybind.
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

			var tradeNode = ClientCommandManager.literal("trade")
					.then(ClientCommandManager.literal("propose")
							.then(ClientCommandManager.argument("recipient", UuidArgument.uuid())
									.then(ClientCommandManager.argument("offered", UuidArgument.uuid())
											.then(ClientCommandManager.argument("requested", UuidArgument.uuid()).executes(context -> {
												tradeCommands.propose(context.getSource(),
														context.getArgument("recipient", UUID.class),
														context.getArgument("offered", UUID.class),
														context.getArgument("requested", UUID.class));
												return Command.SINGLE_SUCCESS;
											})))))
					.then(ClientCommandManager.literal("accept")
							.then(ClientCommandManager.argument("uuid", UuidArgument.uuid()).executes(context -> {
								tradeCommands.accept(context.getSource(), context.getArgument("uuid", UUID.class));
								return Command.SINGLE_SUCCESS;
							})))
					.then(ClientCommandManager.literal("cancel")
							.then(ClientCommandManager.argument("uuid", UuidArgument.uuid()).executes(context -> {
								tradeCommands.cancel(context.getSource(), context.getArgument("uuid", UUID.class));
								return Command.SINGLE_SUCCESS;
							})))
					.then(ClientCommandManager.literal("view")
							.then(ClientCommandManager.argument("uuid", UuidArgument.uuid()).executes(context -> {
								tradeCommands.view(context.getSource(), context.getArgument("uuid", UUID.class));
								return Command.SINGLE_SUCCESS;
							})))
					.then(ClientCommandManager.literal("list").executes(context -> {
						tradeCommands.list(context.getSource());
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("invite")
							.then(ClientCommandManager.argument("player", StringArgumentType.word())
									.suggests((context, builder) -> SharedSuggestionProvider.suggest(LiveTradeController.onlinePlayerNames(), builder))
									.executes(context -> {
										liveTrade.inviteByName(StringArgumentType.getString(context, "player"));
										return Command.SINGLE_SUCCESS;
									})))
					.then(ClientCommandManager.literal("join").executes(context -> {
						liveTrade.acceptInvite();
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("decline").executes(context -> {
						liveTrade.declineInvite();
						return Command.SINGLE_SUCCESS;
					}));

			// Live Ghost battles (Phase 9): invite (names from the server's player list), answer, turn timer.
			var battleNode = ClientCommandManager.literal("battle")
					.then(ClientCommandManager.literal("invite")
							.then(ClientCommandManager.argument("player", StringArgumentType.word())
									.suggests((context, builder) -> SharedSuggestionProvider.suggest(LiveTradeController.onlinePlayerNames(), builder))
									.executes(context -> {
										liveBattle.inviteByName(StringArgumentType.getString(context, "player"));
										return Command.SINGLE_SUCCESS;
									})
									// Ghost vs normal Pokémon (CAD Partie 1 §31): bring a copy of the real Cobblemon party.
									.then(ClientCommandManager.literal("ghost").executes(context -> {
										liveBattle.inviteByName(StringArgumentType.getString(context, "player"),
												com.mystaria.phantasmon.client.battle.LiveBattleController.TeamChoice.GHOST);
										return Command.SINGLE_SUCCESS;
									}))
									.then(ClientCommandManager.literal("cobblemon").executes(context -> {
										liveBattle.inviteByName(StringArgumentType.getString(context, "player"),
												com.mystaria.phantasmon.client.battle.LiveBattleController.TeamChoice.COBBLEMON);
										return Command.SINGLE_SUCCESS;
									}))))
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
					.then(ClientCommandManager.literal("toggle-ping").executes(context -> {
						boolean enabled = pingToggle.toggle();
						context.getSource().sendFeedback(Component.translatable(
								enabled ? "phantasmon.ping.enabled" : "phantasmon.ping.disabled"));
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("pc").executes(context -> {
						pokemonCommands.openPc(context.getSource());
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("debug")
							.then(ClientCommandManager.literal("fingerprint")
									.executes(context -> {
										GhostSession.setFingerprintOverride(null);
										context.getSource().sendFeedback(Component.literal(
												"[Phantasmon] server_fingerprint override retiré, valeur calculée normalement."));
										return Command.SINGLE_SUCCESS;
									})
									.then(ClientCommandManager.argument("value", StringArgumentType.word()).executes(context -> {
										String value = StringArgumentType.getString(context, "value");
										GhostSession.setFingerprintOverride(value);
										context.getSource().sendFeedback(Component.literal(
												"[Phantasmon] server_fingerprint forcé à \"" + value + "\" (test uniquement)."));
										return Command.SINGLE_SUCCESS;
									}))))
					.then(pokemonNode)
					.then(tradeNode)
					.then(battleNode)
					.then(ClientCommandManager.literal("sendout").executes(context -> {
						toggleSendOut(pokemonCommands, ghostSession);
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("recall").executes(context -> {
						ghostSession.recall();
						return Command.SINGLE_SUCCESS;
					})));
		});
	}

	/**
	 * {@code /phantasmon sendout} is a toggle (Adrien: 2026-09-29): sends out the
	 * team lead if nothing is currently out, recalls otherwise — same behavior
	 * shared by the {@code sendout} keybind (see
	 * {@code com.mystaria.phantasmon.client.PhantasmonKeybinds}), hence
	 * {@code public static} rather than private to this class.
	 */
	public static void toggleSendOut(PokemonCommandHandler pokemonCommands, GhostSession ghostSession) {
		if (ghostSession.hasActiveGhost()) {
			ghostSession.recall();
		} else {
			pokemonCommands.sendOutTeamLead(ghostSession::sendOut);
		}
	}
}
