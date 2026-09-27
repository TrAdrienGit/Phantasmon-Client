package com.mystaria.phantasmon.client.command;

import java.util.UUID;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.auth.AuthService;
import com.mystaria.phantasmon.client.ghost.GhostSession;
import com.mystaria.phantasmon.client.network.PingToggle;
import com.mystaria.phantasmon.client.pokemon.PokemonCommandHandler;
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
 * <p>{@code /phantasmon pokemon *} (CAD Phase 6, all-commands approach, Adrien:
 * 2026-09-26): {@code import} (reads a Showdown block from the clipboard),
 * {@code list}, {@code pc <box>}, {@code delete <uuid>}, {@code clone <uuid>},
 * {@code edit <uuid> level <n>} — see {@link PokemonCommandHandler}.
 *
 * <p>{@code /phantasmon sendout <uuid>}/{@code recall} (CAD Phase 7) trigger
 * the Ghost Entity spawn/despawn over the presence WebSocket — see
 * {@link GhostSession}.
 *
 * <p>{@code /phantasmon trade *} (CAD Phase 8, same all-commands approach):
 * {@code propose <recipient> <offered> <requested>}, {@code accept <uuid>},
 * {@code cancel <uuid>}, {@code view <uuid>}, {@code list} — see
 * {@link TradeCommandHandler}, which also renders the WS trade notifications.
 */
public final class PhantasmonCommands {

	private PhantasmonCommands() {
	}

	public static void register(AuthService authService, PingToggle pingToggle, PokemonCommandHandler pokemonCommands,
			GhostSession ghostSession, TradeCommandHandler tradeCommands) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			var pokemonNode = ClientCommandManager.literal("pokemon")
					.then(ClientCommandManager.literal("import").executes(context -> {
						pokemonCommands.importFromClipboard(context.getSource());
						return Command.SINGLE_SUCCESS;
					}))
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
					}));

			dispatcher.register(ClientCommandManager.literal("phantasmon")
					.then(ClientCommandManager.literal("login").executes(context -> {
						authService.login();
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("toggle-ping").executes(context -> {
						boolean enabled = pingToggle.toggle();
						context.getSource().sendFeedback(Component.translatable(
								enabled ? "phantasmon.ping.enabled" : "phantasmon.ping.disabled"));
						return Command.SINGLE_SUCCESS;
					}))
					.then(pokemonNode)
					.then(tradeNode)
					.then(ClientCommandManager.literal("sendout")
							.then(ClientCommandManager.argument("uuid", UuidArgument.uuid()).executes(context -> {
								ghostSession.sendOut(context.getArgument("uuid", UUID.class));
								return Command.SINGLE_SUCCESS;
							})))
					.then(ClientCommandManager.literal("recall").executes(context -> {
						ghostSession.recall();
						return Command.SINGLE_SUCCESS;
					})));
		});
	}
}
