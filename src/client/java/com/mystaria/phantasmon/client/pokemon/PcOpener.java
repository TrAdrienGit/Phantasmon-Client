package com.mystaria.phantasmon.client.pokemon;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.gui.PhantasmonPcScreen;

/**
 * Opens the Phantasmon PC on the next client tick — from its key, or another player's for an admin (TODO-25). Not
 * right away: a screen opened while the chat closes after a command is closed with it. Everything else on the
 * Pokémon (import, export, edit, move, delete) is done in the PC itself (Adrien 2026-10-30: no commands).
 */
public final class PcOpener {

	private final PokemonClient pokemonClient;
	private final AuthSession session;
	private boolean requested;
	private UUID owner;
	private String ownerName;

	public PcOpener(PokemonClient pokemonClient, AuthSession session) {
		this.pokemonClient = pokemonClient;
		this.session = session;
	}

	/** The player's own PC. */
	public void openPc() {
		if (!session.isAuthenticated()) {
			var player = Minecraft.getInstance().player;
			if (player != null) {
				player.displayClientMessage(Component.translatable("phantasmon.error.not_authenticated"), false);
			}
			return;
		}
		owner = null;
		ownerName = null;
		requested = true;
	}

	/** Admin: another player's PC, handled as if it were one's own. */
	public void openPcOf(UUID ownerUuid, String name) {
		owner = ownerUuid;
		ownerName = name;
		requested = true;
	}

	/** Every client tick: opens the PC if one was asked for. */
	public void tick() {
		if (requested) {
			requested = false;
			Minecraft.getInstance().setScreen(new PhantasmonPcScreen(pokemonClient, session, owner, ownerName));
		}
	}
}
