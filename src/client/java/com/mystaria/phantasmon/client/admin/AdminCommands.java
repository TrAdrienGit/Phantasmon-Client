package com.mystaria.phantasmon.client.admin;

import java.util.List;
import java.util.concurrent.CompletionException;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.ghost.GhostSession;
import com.mystaria.phantasmon.client.network.BackendApiException;
import com.mystaria.phantasmon.client.network.BackendErrorMessages;
import com.mystaria.phantasmon.client.network.PingToggle;
import com.mystaria.phantasmon.client.pokemon.PokemonCommandHandler;

/**
 * {@code /phantasmon admin ...} (TODO-25, Adrien 2026-10-06), only offered to the players of the backend's admin file
 * ({@link AdminSession}; the backend checks each request again):
 * <ul>
 *   <li>{@code pc <player>} — that player's PC, handled as if it were one's own;</li>
 *   <li>{@code stopbattle <player>} — ends the battle (draw) or cancels the lobby that player is in;</li>
 *   <li>{@code reboot} — restarts the backend, after a click on [Confirm];</li>
 *   <li>{@code ping} and {@code debug fingerprint [value]} — moved here from the players' commands.</li>
 * </ul>
 */
public final class AdminCommands {

	private AdminCommands() {
	}

	public static void register(AdminClient admin, AuthSession session, PokemonCommandHandler pokemonCommands, PingToggle pingToggle) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			LiteralArgumentBuilder<FabricClientCommandSource> node = ClientCommandManager.literal("admin")
					.requires(source -> AdminSession.isAdmin())
					.then(ClientCommandManager.literal("pc").then(playerArgument().executes(context -> {
						String name = StringArgumentType.getString(context, "player");
						admin.player(session.accessToken(), name)
								.thenAccept(player -> Minecraft.getInstance().execute(() -> pokemonCommands.openPcOf(player.uuid(), player.name())))
								.exceptionally(ex -> fail(ex));
						return Command.SINGLE_SUCCESS;
					})))
					.then(ClientCommandManager.literal("stopbattle").then(playerArgument().executes(context -> {
						String name = StringArgumentType.getString(context, "player");
						admin.stopBattle(session.accessToken(), name)
								.thenAccept(stopped -> chat(Component.translatable("BATTLE".equals(stopped.stopped())
										? "phantasmon.admin.battle_stopped" : "phantasmon.admin.lobby_stopped", stopped.player())
										.withStyle(ChatFormatting.GOLD)))
								.exceptionally(ex -> fail(ex));
						return Command.SINGLE_SUCCESS;
					})))
					.then(ClientCommandManager.literal("reboot")
							.executes(context -> {
								chat(Component.translatable("phantasmon.admin.reboot_confirm").withStyle(ChatFormatting.RED)
										.append(" ")
										.append(Component.translatable("phantasmon.admin.reboot_button").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
												.withStyle(style -> style
														.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/phantasmon admin reboot confirm"))
														.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/phantasmon admin reboot confirm"))))));
								return Command.SINGLE_SUCCESS;
							})
							.then(ClientCommandManager.literal("confirm").executes(context -> {
								admin.reboot(session.accessToken())
										.thenAccept(ignored -> chat(Component.translatable("phantasmon.admin.rebooting").withStyle(ChatFormatting.GOLD)))
										.exceptionally(ex -> fail(ex));
								return Command.SINGLE_SUCCESS;
							})))
					.then(ClientCommandManager.literal("ping").executes(context -> {
						boolean enabled = pingToggle.toggle();
						context.getSource().sendFeedback(Component.translatable(enabled ? "phantasmon.ping.enabled" : "phantasmon.ping.disabled"));
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("debug").then(ClientCommandManager.literal("fingerprint")
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
							}))));
			dispatcher.register(ClientCommandManager.literal("phantasmon").then(node));
		});
	}

	/** A player name; suggestions from the server's player list (offline players can be typed). */
	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource, String> playerArgument() {
		return ClientCommandManager.argument("player", StringArgumentType.word())
				.suggests((context, builder) -> SharedSuggestionProvider.suggest(onlinePlayerNames(), builder));
	}

	private static List<String> onlinePlayerNames() {
		var connection = Minecraft.getInstance().getConnection();
		return connection == null ? List.of()
				: connection.getOnlinePlayers().stream().map(info -> info.getProfile().getName()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
	}

	private static Void fail(Throwable ex) {
		Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
		String key = cause instanceof BackendApiException api ? BackendErrorMessages.translationKey(api.errorCode()) : "phantasmon.error.network";
		chat(Component.translatable(key).withStyle(ChatFormatting.RED));
		return null;
	}

	private static void chat(Component message) {
		Minecraft.getInstance().execute(() -> {
			var player = Minecraft.getInstance().player;
			if (player != null) {
				player.displayClientMessage(message, false);
			}
		});
	}
}
