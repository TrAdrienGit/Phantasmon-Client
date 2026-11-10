package com.mystaria.phantasmon.client.hub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import com.mystaria.phantasmon.client.admin.AdminSession;

/**
 * Hub commands (Phantasmon Network, network-cahier-des-charges.md §5.8). Hubs and anchors are managed from the hub
 * manager (key H, 2026-11-10: players place and remove their own anchors there, admins manage the hubs); only these stay:
 * <ul>
 *   <li>{@code /phantasmon hub leave} — entering an anchor joins the hub by itself;</li>
 *   <li>{@code /phantasmon hub chat <message>}, shortcut {@code /hc <message>} — never sent to the Minecraft server;</li>
 *   <li>admins: {@code /phantasmon admin hub debug <hub>} — shows or hides that hub's borders.</li>
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
			// Hubs and anchors are managed from the hub manager (key H, 2026-11-10): only the borders stay a command.
			var adminHubNode = ClientCommandManager.literal("hub")
					.then(ClientCommandManager.literal("debug").then(hubArgument(hub).executes(context -> {
						hub.adminToggleDebug(StringArgumentType.getString(context, "hub"));
						return Command.SINGLE_SUCCESS;
					})));

			var hubNode = ClientCommandManager.literal("hub")
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
