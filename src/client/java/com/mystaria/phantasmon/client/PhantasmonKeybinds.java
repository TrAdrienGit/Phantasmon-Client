package com.mystaria.phantasmon.client;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

import com.mystaria.phantasmon.client.command.PhantasmonCommands;
import com.mystaria.phantasmon.client.ghost.GhostSession;
import com.mystaria.phantasmon.client.pokemon.PokemonCommandHandler;
import com.mystaria.phantasmon.client.trade.LiveTradeController;

/**
 * Two convenience keybinds (Adrien: 2026-09-29) for the two most-used chat
 * commands: opening the PC screen and toggling sendout/recall of the team's
 * lead Pokémon (team slot 1 — see {@link PokemonCommandHandler#sendOutTeamLead}
 * and {@link PhantasmonCommands#toggleSendOut}, shared with the
 * {@code /phantasmon sendout} command so both stay in sync). A third one
 * (Adrien: 2026-10-02) invites the targeted player to a live trade, same as
 * {@code /phantasmon trade invite <player>}.
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
	private static final KeyMapping TOGGLE_SEND_OUT = new KeyMapping("key.phantasmon.sendout", GLFW.GLFW_KEY_O, CATEGORY);
	/** Live trade invite to whoever the crosshair is on (Adrien 2026-10-02 — the "Ghost Trade" keybind planned instead of a Cobblemon interaction-wheel Mixin). G is unused by vanilla and Cobblemon. */
	private static final KeyMapping TRADE_WITH_TARGET = new KeyMapping("key.phantasmon.trade", GLFW.GLFW_KEY_G, CATEGORY);

	private PhantasmonKeybinds() {
	}

	public static void register() {
		KeyBindingHelper.registerKeyBinding(OPEN_PC);
		KeyBindingHelper.registerKeyBinding(TOGGLE_SEND_OUT);
		KeyBindingHelper.registerKeyBinding(TRADE_WITH_TARGET);
	}

	/** Called every client tick (see {@link PhantasmonClient}) — {@code consumeClick()} is the standard vanilla pattern for a keybind's action firing once per press, queued click included, regardless of how long the key is held. */
	public static void tick(PokemonCommandHandler pokemonCommands, GhostSession ghostSession, LiveTradeController liveTrade) {
		while (OPEN_PC.consumeClick()) {
			pokemonCommands.openPc();
		}
		while (TOGGLE_SEND_OUT.consumeClick()) {
			PhantasmonCommands.toggleSendOut(pokemonCommands, ghostSession);
		}
		while (TRADE_WITH_TARGET.consumeClick()) {
			liveTrade.inviteTargetedPlayer();
		}
	}
}
