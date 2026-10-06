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
import com.mystaria.phantasmon.client.battle.BattleCinematic;
import com.mystaria.phantasmon.client.battle.BattleSpectacle;
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
 *   <li>{@code ping} and {@code debug fingerprint [value]} — moved here from the players' commands;</li>
 *   <li>{@code debug intro <name>} — plays one of the battle intros alone (TODO-26 previews);</li>
 *   <li>{@code debug spectacle <mega|primal|zmove|tera> [type]} — plays a battle set piece on the nearest Pokémon.</li>
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
					.then(ClientCommandManager.literal("debug").then(ClientCommandManager.literal("spectacle")
							.then(ClientCommandManager.argument("kind", StringArgumentType.word())
									.suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("mega", "primal", "zmove", "tera"), builder))
									.executes(context -> spectacle(context.getSource(), StringArgumentType.getString(context, "kind"), null))
									.then(ClientCommandManager.argument("type", StringArgumentType.word())
											.suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("normal", "fire", "water", "grass",
													"electric", "ice", "fighting", "poison", "ground", "flying", "psychic", "bug", "rock", "ghost",
													"dragon", "dark", "steel", "fairy", "stellar"), builder))
											.executes(context -> spectacle(context.getSource(), StringArgumentType.getString(context, "kind"),
													StringArgumentType.getString(context, "type"))))))
							.then(ClientCommandManager.literal("intro")
							.then(ClientCommandManager.argument("intro", StringArgumentType.word())
									.suggests((context, builder) -> SharedSuggestionProvider.suggest(
											java.util.Arrays.stream(BattleCinematic.Intro.values()).map(i -> i.name().toLowerCase(java.util.Locale.ROOT)), builder))
									.executes(context -> {
										String name = StringArgumentType.getString(context, "intro").toUpperCase(java.util.Locale.ROOT);
										BattleCinematic.Intro intro = java.util.Arrays.stream(BattleCinematic.Intro.values())
												.filter(i -> i.name().equals(name)).findFirst().orElse(null);
										if (intro == null) {
											context.getSource().sendError(Component.literal("[Phantasmon] intro inconnue : " + name.toLowerCase(java.util.Locale.ROOT)));
											return 0;
										}
										// Run from the chat screen: the intro's screen opens once the chat has closed.
										Minecraft.getInstance().tell(() -> BattleCinematic.preview(intro));
										return Command.SINGLE_SUCCESS;
									})))
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
							}))));
			dispatcher.register(ClientCommandManager.literal("phantasmon").then(node));
		});
	}

	/**
	 * Admin preview of a battle set piece on the nearest Pokémon in the world (a Ghost, a Cobblemon...), nothing
	 * changes on it (no Mega model): {@code mega}, {@code primal}, {@code zmove}, {@code tera [type]}.
	 */
	private static int spectacle(FabricClientCommandSource source, String kindName, String type) {
		BattleSpectacle.Kind kind = switch (kindName.toLowerCase(java.util.Locale.ROOT)) {
			case "mega" -> BattleSpectacle.Kind.MEGA;
			case "primal" -> BattleSpectacle.Kind.PRIMAL;
			case "zmove", "z" -> BattleSpectacle.Kind.Z_MOVE;
			case "tera" -> BattleSpectacle.Kind.TERA;
			default -> null;
		};
		if (kind == null) {
			source.sendError(Component.literal("[Phantasmon] effet inconnu : " + kindName + " (mega, primal, zmove, tera)"));
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		var target = mc.level == null || mc.player == null ? null
				: mc.level.getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity.class, mc.player.getBoundingBox().inflate(24))
						.stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(mc.player))).orElse(null);
		if (target == null) {
			source.sendError(Component.literal("[Phantasmon] aucun Pokémon à moins de 24 blocs (sors un Ghost ou un Pokémon)."));
			return 0;
		}
		// Run from the chat screen: once it has closed.
		mc.tell(() -> BattleSpectacle.preview(kind, target, type == null ? null : type.toLowerCase(java.util.Locale.ROOT)));
		return Command.SINGLE_SUCCESS;
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
