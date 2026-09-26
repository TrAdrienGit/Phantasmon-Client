package com.mystaria.phantasmon.client.command;

import com.mojang.brigadier.Command;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

import com.mystaria.phantasmon.client.auth.AuthService;

/**
 * {@code /phantasmon login} triggers the auth flow (CAD Phase 5: "écran/commande
 * de connexion"). A command is used rather than a screen for this first pass —
 * simplest way to prove the flow end to end; a dedicated login screen can
 * replace/complement it later without changing {@link AuthService}.
 */
public final class PhantasmonCommands {

	private PhantasmonCommands() {
	}

	public static void register(AuthService authService) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("phantasmon")
						.then(ClientCommandManager.literal("login").executes(context -> {
							authService.login();
							return Command.SINGLE_SUCCESS;
						}))));
	}
}
