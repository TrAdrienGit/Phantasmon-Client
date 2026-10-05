package com.mystaria.phantasmon.client;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

import com.mystaria.phantasmon.client.command.PhantasmonCommands;
import com.mystaria.phantasmon.client.ghost.GhostSession;
import com.mystaria.phantasmon.client.pokemon.PokemonCommandHandler;

/**
 * The mod's two keybinds (Adrien: 2026-09-29): opening the PC screen (P) and toggling sendout/recall of the team's
 * lead Pokémon (H, team slot 1 — see {@link PokemonCommandHandler#sendOutTeamLead} and
 * {@link PhantasmonCommands#toggleSendOut}). Trades and battles start from Cobblemon's interaction wheel only
 * (TODO-22, Adrien 2026-10-05: the G and B invite keys were removed).
 *
 * <p>Registered once via {@link KeyBindingHelper} with a plain default key —
 * no custom persistence needed: Minecraft itself saves any rebind to
 * {@code options.txt}, keyed by each {@link KeyMapping}'s translation key
 * (e.g. {@code "key.phantasmon.open_pc"}). As long as these id strings are
 * never renamed, a player's rebind survives both a game relaunch and a future
 * update of this mod automatically — nothing else to implement for that.
 */
public final class PhantasmonKeybinds {

	private static final String CATEGORY = "key.categories.phantasmon";

	private static final KeyMapping OPEN_PC = new KeyMapping("key.phantasmon.open_pc", GLFW.GLFW_KEY_P, CATEGORY);
	private static final KeyMapping TOGGLE_SEND_OUT = new KeyMapping("key.phantasmon.sendout", GLFW.GLFW_KEY_H, CATEGORY);

	private PhantasmonKeybinds() {
	}

	public static void register() {
		KeyBindingHelper.registerKeyBinding(OPEN_PC);
		KeyBindingHelper.registerKeyBinding(TOGGLE_SEND_OUT);
	}

	/** Called every client tick (see {@link PhantasmonClient}) — {@code consumeClick()} is the standard vanilla pattern for a keybind's action firing once per press, queued click included, regardless of how long the key is held. */
	public static void tick(PokemonCommandHandler pokemonCommands, GhostSession ghostSession) {
		while (OPEN_PC.consumeClick()) {
			pokemonCommands.openPc();
		}
		while (TOGGLE_SEND_OUT.consumeClick()) {
			PhantasmonCommands.toggleSendOut(pokemonCommands, ghostSession);
		}
	}
}
