package com.mystaria.phantasmon.client;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import com.mystaria.phantasmon.client.gui.PhantasmonMusicScreen;
import com.mystaria.phantasmon.client.pokemon.PokemonCommandHandler;

/**
 * The mod's two keybinds: opening the PC screen (P, Adrien: 2026-09-29) and the music screen (N, Adrien 2026-10-05:
 * which tracks of the resource pack may play). Everything else has moved off keys: trades and battles start from
 * Cobblemon's interaction wheel (TODO-22), Ghosts go out and back with Cobblemon's party keys on the Ghost overlay
 * (TODO-23), and the battle camera / music skip / timer are buttons on Cobblemon's battle screen.
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
	private static final KeyMapping MUSIC_MENU = new KeyMapping("key.phantasmon.music_menu", GLFW.GLFW_KEY_N, CATEGORY);

	private PhantasmonKeybinds() {
	}

	public static void register() {
		KeyBindingHelper.registerKeyBinding(OPEN_PC);
		KeyBindingHelper.registerKeyBinding(MUSIC_MENU);
	}

	/** Called every client tick (see {@link PhantasmonClient}) — {@code consumeClick()} is the standard vanilla pattern for a keybind's action firing once per press, queued click included, regardless of how long the key is held. */
	public static void tick(PokemonCommandHandler pokemonCommands) {
		while (OPEN_PC.consumeClick()) {
			pokemonCommands.openPc();
		}
		while (MUSIC_MENU.consumeClick()) {
			Minecraft.getInstance().setScreen(new PhantasmonMusicScreen(null));
		}
	}
}
