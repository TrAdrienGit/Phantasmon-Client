package com.mystaria.phantasmon.client.command;

import java.util.UUID;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.auth.AuthService;
import com.mystaria.phantasmon.client.network.PingToggle;
import com.mystaria.phantasmon.client.pokemon.PokemonCommandHandler;

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
 */
public final class PhantasmonCommands {

	private PhantasmonCommands() {
	}

	public static void register(AuthService authService, PingToggle pingToggle, PokemonCommandHandler pokemonCommands) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("phantasmon")
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
						.then(ClientCommandManager.literal("pokemon")
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
										})))
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
						)));
	}
}
