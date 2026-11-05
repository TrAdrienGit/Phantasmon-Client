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
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.command.ChatActions;
import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.ghost.GhostSession;
import com.mystaria.phantasmon.client.network.BackendApiException;
import com.mystaria.phantasmon.client.network.BackendErrorMessages;
import com.mystaria.phantasmon.client.network.PingToggle;
import com.mystaria.phantasmon.client.pokemon.PcOpener;

/**
 * {@code /phantasmon admin ...} (TODO-25, Adrien 2026-10-06), only offered to the players of the backend's admin file
 * ({@link AdminSession}; the backend checks each request again):
 * <ul>
 *   <li>{@code pc <player>} — that player's PC, handled as if it were one's own;</li>
 *   <li>{@code stopbattle <player>} — ends the battle (draw) or cancels the lobby that player is in;</li>
 *   <li>{@code reboot} — restarts the backend, after a click on the [Confirm] chat button;</li>
 *   <li>{@code battle solo} — a live Ghost battle against a mirror of one's own team, played by Cobblemon's AI
 *   (Adrien 2026-10-07): spectatable, never stored;</li>
 *   <li>{@code ping} — moved here from the players' commands;</li>
 *   <li>{@code hub mute <player> <duration>}, {@code hub unmute <player>}, {@code hub lock} — hub chat moderation
 *   (TODO-31): a timed mute ({@code 30m}, {@code 2h}, {@code 7d}; a bare number is minutes), the global chat lock
 *   (toggled; admins still write). An admin's hub message starting with {@code !!} is an announcement.</li>
 * </ul>
 * The intro and set piece previews are Cobblemon Battle Showtime's ({@code /showtime preview}, D-37).
 */
public final class AdminCommands {

	private AdminCommands() {
	}

	public static void register(AdminClient admin, AuthSession session, PcOpener pcOpener, PingToggle pingToggle,
			com.mystaria.phantasmon.client.battle.LiveBattleController liveBattle) {
		// The reboot is confirmed by a chat button, not a command.
		ChatActions.register("admin.reboot", () -> admin.reboot(session.accessToken())
				.thenAccept(ignored -> chat(Component.translatable("phantasmon.admin.rebooting").withStyle(ChatFormatting.GOLD)))
				.exceptionally(ex -> fail(ex)));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			LiteralArgumentBuilder<FabricClientCommandSource> node = ClientCommandManager.literal("admin")
					.requires(source -> AdminSession.isAdmin())
					.then(ClientCommandManager.literal("pc").then(playerArgument().executes(context -> {
						String name = StringArgumentType.getString(context, "player");
						admin.player(session.accessToken(), name)
								.thenAccept(player -> Minecraft.getInstance().execute(() -> pcOpener.openPcOf(player.uuid(), player.name())))
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
					.then(ClientCommandManager.literal("reboot").executes(context -> {
						chat(Component.translatable("phantasmon.admin.reboot_confirm").withStyle(ChatFormatting.RED)
								.append(" ")
								.append(ChatActions.button("phantasmon.admin.reboot_button", "admin.reboot", ChatFormatting.RED)));
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("battle").then(ClientCommandManager.literal("solo").executes(context -> {
						liveBattle.startSolo();
						return Command.SINGLE_SUCCESS;
					})))
					.then(ClientCommandManager.literal("hub")
							.then(ClientCommandManager.literal("mute").then(playerArgument()
									.then(ClientCommandManager.argument("duration", StringArgumentType.word()).executes(context -> {
										String name = StringArgumentType.getString(context, "player");
										long minutes = minutes(StringArgumentType.getString(context, "duration"));
										if (minutes <= 0) {
											chat(Component.translatable("phantasmon.admin.hub.bad_duration").withStyle(ChatFormatting.RED));
											return Command.SINGLE_SUCCESS;
										}
										admin.hubMute(session.accessToken(), name, minutes)
												.thenAccept(muted -> chat(Component.translatable("phantasmon.admin.hub.muted", muted.player(),
														localTime(muted.until())).withStyle(ChatFormatting.GOLD)))
												.exceptionally(ex -> fail(ex));
										return Command.SINGLE_SUCCESS;
									}))))
							.then(ClientCommandManager.literal("unmute").then(playerArgument().executes(context -> {
								String name = StringArgumentType.getString(context, "player");
								admin.hubUnmute(session.accessToken(), name)
										.thenAccept(unmuted -> chat(Component.translatable(unmuted.was_muted()
												? "phantasmon.admin.hub.unmuted" : "phantasmon.admin.hub.not_muted", unmuted.player())
												.withStyle(ChatFormatting.GOLD)))
										.exceptionally(ex -> fail(ex));
								return Command.SINGLE_SUCCESS;
							})))
							.then(ClientCommandManager.literal("lock").executes(context -> {
								admin.hubLock(session.accessToken())
										.thenAccept(lock -> chat(Component.translatable(lock.locked()
												? "phantasmon.admin.hub.locked" : "phantasmon.admin.hub.unlocked").withStyle(ChatFormatting.GOLD)))
										.exceptionally(ex -> fail(ex));
								return Command.SINGLE_SUCCESS;
							})))
					.then(ClientCommandManager.literal("ping").executes(context -> {
						boolean enabled = pingToggle.toggle();
						context.getSource().sendFeedback(Component.translatable(enabled ? "phantasmon.ping.enabled" : "phantasmon.ping.disabled"));
						return Command.SINGLE_SUCCESS;
					}));
			dispatcher.register(ClientCommandManager.literal("phantasmon").then(node));
		});
	}

	/** A player name; suggestions from the server's player list (offline players can be typed). */
	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource, String> playerArgument() {
		return ClientCommandManager.argument("player", StringArgumentType.word())
				.suggests((context, builder) -> SharedSuggestionProvider.suggest(onlinePlayerNames(), builder));
	}

	/** {@code 30m}, {@code 2h}, {@code 7d}, or a bare number of minutes; 0 if unreadable. */
	static long minutes(String duration) {
		String text = duration.strip().toLowerCase(java.util.Locale.ROOT);
		long unit = 1;
		if (text.endsWith("m")) {
			text = text.substring(0, text.length() - 1);
		} else if (text.endsWith("h")) {
			unit = 60;
			text = text.substring(0, text.length() - 1);
		} else if (text.endsWith("d") || text.endsWith("j")) {
			unit = 60 * 24;
			text = text.substring(0, text.length() - 1);
		}
		try {
			long value = Long.parseLong(text);
			return value > 0 ? value * unit : 0;
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	/** An ISO instant from the backend, in local time (DD-MM-YYYY HH:mm, like the tournaments' dates). */
	public static String localTime(String instant) {
		try {
			return java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
					.format(java.time.Instant.parse(instant).atZone(java.time.ZoneId.systemDefault()));
		} catch (RuntimeException ex) {
			return instant;
		}
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
