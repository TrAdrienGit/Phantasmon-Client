package com.mystaria.phantasmon.client.command;

import com.mojang.brigadier.Command;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.auth.AuthService;
import com.mystaria.phantasmon.client.network.PingToggle;

/**
 * {@code /phantasmon login} triggers the auth flow (CAD Phase 5: "écran/commande
 * de connexion"). A command is used rather than a screen for this first pass —
 * simplest way to prove the flow end to end; a dedicated login screen can
 * replace/complement it later without changing {@link AuthService}.
 *
 * <p>{@code /phantasmon toggle-ping} switches the {@code GET /health} heartbeat
 * on/off for the current session — off by default (Adrien: 2026-09-26).
 */
public final class PhantasmonCommands {

	private PhantasmonCommands() {
	}

	public static void register(AuthService authService, PingToggle pingToggle) {
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
						}))));
	}
}
