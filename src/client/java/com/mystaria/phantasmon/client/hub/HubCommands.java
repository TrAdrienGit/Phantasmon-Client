package com.mystaria.phantasmon.client.hub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import com.mystaria.phantasmon.client.admin.AdminSession;

/**
 * Hub commands (Phantasmon Network, network-cahier-des-charges.md §5.2 and §5.8, D-35):
 * <ul>
 *   <li>{@code /phantasmon hub list} — the hubs, their size and whether they have a build;</li>
 *   <li>{@code /phantasmon hub anchor create <hub> [name]} — an anchor of that hub centred on the player, facing where
 *   they look (one per hub; the hub names are suggested from the backend's list);</li>
 *   <li>{@code /phantasmon hub anchor info} / {@code delete <hub>} — the player's own anchors; {@code delete here} — the
 *   anchor the player stands in (its creator or an admin). Anchors are shared within a server, never across (D-30);</li>
 *   <li>admins: {@code /phantasmon admin hub create <hub> <length> <width> <height>}, {@code delete <hub>},
 *   {@code reload <hub>};</li>
 *   <li>{@code /phantasmon hub join} / {@code decline} / {@code always} — the invitation's chat buttons;</li>
 *   <li>{@code /phantasmon hub autojoin on|off} — join without being asked through the anchor the player stands in;</li>
 *   <li>{@code /phantasmon hub leave};</li>
 *   <li>{@code /phantasmon hub chat <message>}, shortcut {@code /hc <message>} — never sent to the Minecraft server.</li>
 * </ul>
 */
public final class HubCommands {

	private HubCommands() {
	}

	/** A hub name, suggested from the backend's hubs list — new hubs show up without restarting the game (D-35). */
	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource, String> hubArgument(HubController hub) {
		return ClientCommandManager.argument("hub", StringArgumentType.word())
				.suggests((context, builder) -> hub.suggestHubs(builder));
	}

	public static void register(HubController hub) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			var anchorNode = ClientCommandManager.literal("anchor")
					.then(ClientCommandManager.literal("create")
							.then(hubArgument(hub)
									.executes(context -> {
										hub.createAnchor(StringArgumentType.getString(context, "hub"), null);
										return Command.SINGLE_SUCCESS;
									})
									.then(ClientCommandManager.argument("name", StringArgumentType.greedyString()).executes(context -> {
										hub.createAnchor(StringArgumentType.getString(context, "hub"),
												StringArgumentType.getString(context, "name"));
										return Command.SINGLE_SUCCESS;
									}))))
					.then(ClientCommandManager.literal("info").executes(context -> {
						hub.showMyAnchor();
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("delete")
							.then(ClientCommandManager.literal("here").executes(context -> {
								hub.deleteAnchorHere();
								return Command.SINGLE_SUCCESS;
							}))
							.then(hubArgument(hub).executes(context -> {
								hub.deleteMyAnchor(StringArgumentType.getString(context, "hub"));
								return Command.SINGLE_SUCCESS;
							})));

			// Admins (D-35): hubs are created with their size, deleted, and their schematic folder read again.
			var adminHubNode = ClientCommandManager.literal("hub")
					.then(ClientCommandManager.literal("create")
							.then(ClientCommandManager.argument("hub", StringArgumentType.word())
									.then(ClientCommandManager.argument("length", IntegerArgumentType.integer(3, 64))
											.then(ClientCommandManager.argument("width", IntegerArgumentType.integer(3, 64))
													.then(ClientCommandManager.argument("height", IntegerArgumentType.integer(3, 64))
															.executes(context -> {
																hub.adminCreateHub(StringArgumentType.getString(context, "hub"),
																		IntegerArgumentType.getInteger(context, "length"),
																		IntegerArgumentType.getInteger(context, "width"),
																		IntegerArgumentType.getInteger(context, "height"));
																return Command.SINGLE_SUCCESS;
															}))))))
					.then(ClientCommandManager.literal("delete").then(hubArgument(hub).executes(context -> {
						hub.adminDeleteHub(StringArgumentType.getString(context, "hub"));
						return Command.SINGLE_SUCCESS;
					})))
					.then(ClientCommandManager.literal("reload").then(hubArgument(hub).executes(context -> {
						hub.adminReloadHub(StringArgumentType.getString(context, "hub"));
						return Command.SINGLE_SUCCESS;
					})));

			var hubNode = ClientCommandManager.literal("hub")
					.then(anchorNode)
					.then(ClientCommandManager.literal("list").executes(context -> {
						hub.listHubs();
						return Command.SINGLE_SUCCESS;
					}))
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
			// Merged by Brigadier into the "admin" node of AdminCommands (same name, same requirement).
			dispatcher.register(ClientCommandManager.literal("phantasmon").then(ClientCommandManager.literal("admin")
					.requires(source -> AdminSession.isAdmin()).then(adminHubNode)));
			dispatcher.register(ClientCommandManager.literal("hc")
					.then(ClientCommandManager.argument("message", StringArgumentType.greedyString()).executes(context -> {
						hub.sendChat(StringArgumentType.getString(context, "message"));
						return Command.SINGLE_SUCCESS;
					})));
		});
	}
}
