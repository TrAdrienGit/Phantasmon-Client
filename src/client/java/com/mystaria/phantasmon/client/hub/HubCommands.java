package com.mystaria.phantasmon.client.hub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

/**
 * Global Hub commands (Phantasmon Network, network-cahier-des-charges.md §5.2 and §5.8):
 * <ul>
 *   <li>{@code /phantasmon hub anchor create <name>} — an anchor centred on the player, facing where they look;</li>
 *   <li>{@code /phantasmon hub anchor info} / {@code delete} — the player's own anchor; {@code delete here} — the
 *   anchor the player stands in (its creator or an admin). Anchors are shared within a server, never across (D-30);</li>
 *   <li>{@code /phantasmon hub join} / {@code decline} / {@code always} — the invitation's chat buttons;</li>
 *   <li>{@code /phantasmon hub autojoin on|off} — join without being asked through the anchor the player stands in;</li>
 *   <li>{@code /phantasmon hub leave};</li>
 *   <li>{@code /phantasmon hub chat <message>}, shortcut {@code /hc <message>} — never sent to the Minecraft server.</li>
 * </ul>
 */
public final class HubCommands {

	private HubCommands() {
	}

	public static void register(HubController hub) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			var anchorNode = ClientCommandManager.literal("anchor")
					.then(ClientCommandManager.literal("create")
							.then(ClientCommandManager.argument("name", StringArgumentType.greedyString()).executes(context -> {
								hub.createAnchor(StringArgumentType.getString(context, "name"));
								return Command.SINGLE_SUCCESS;
							})))
					.then(ClientCommandManager.literal("info").executes(context -> {
						hub.showMyAnchor();
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("delete")
							.executes(context -> {
								hub.deleteMyAnchor();
								return Command.SINGLE_SUCCESS;
							})
							.then(ClientCommandManager.literal("here").executes(context -> {
								hub.deleteAnchorHere();
								return Command.SINGLE_SUCCESS;
							})));

			var hubNode = ClientCommandManager.literal("hub")
					.then(anchorNode)
					.then(ClientCommandManager.literal("join").executes(context -> {
						hub.acceptInvite();
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("decline").executes(context -> {
						hub.declineInvite();
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("always").executes(context -> {
						hub.acceptAlways();
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("autojoin")
							.then(ClientCommandManager.literal("on").executes(context -> {
								hub.setAutoJoinHere(true);
								return Command.SINGLE_SUCCESS;
							}))
							.then(ClientCommandManager.literal("off").executes(context -> {
								hub.setAutoJoinHere(false);
								return Command.SINGLE_SUCCESS;
							})))
					.then(ClientCommandManager.literal("leave").executes(context -> {
						hub.leave();
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("chat")
							.then(ClientCommandManager.argument("message", StringArgumentType.greedyString()).executes(context -> {
								hub.sendChat(StringArgumentType.getString(context, "message"));
								return Command.SINGLE_SUCCESS;
							})));

			dispatcher.register(ClientCommandManager.literal("phantasmon").then(hubNode));
			dispatcher.register(ClientCommandManager.literal("hc")
					.then(ClientCommandManager.argument("message", StringArgumentType.greedyString()).executes(context -> {
						hub.sendChat(StringArgumentType.getString(context, "message"));
						return Command.SINGLE_SUCCESS;
					})));
		});
	}
}
