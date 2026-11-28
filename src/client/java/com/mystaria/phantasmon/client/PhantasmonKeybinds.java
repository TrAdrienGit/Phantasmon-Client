package com.mystaria.phantasmon.client;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

import com.mystaria.phantasmon.client.pokemon.PcOpener;

/**
 * The mod's keybind: opening the PC screen (P, Adrien: 2026-09-29). The music menu's key (N) is Cobblemon Battle Showtime's
 * since 2026-10-29: it opens Phantasmon's music screen, given to Showtime ({@code ShowtimeBridge}). Everything else
 * has moved off keys: trades and battles start from Cobblemon's interaction wheel (TODO-22), Ghosts go out and back
 * with Cobblemon's party keys on the Ghost overlay (TODO-23), and the battle camera / music / timer are on Cobblemon's
 * battle screen.
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

	private static final KeyMapping OPEN_PC = new KeyMapping("key.phantasmon.open_pc",
			// LeagueWay takes P (its PokéPhone) and keeps it from other mods: U by default then (2026-11-19).
			net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("leagueway") ? GLFW.GLFW_KEY_U : GLFW.GLFW_KEY_P, CATEGORY);
	/** The Phantasmon menu (renamed 2026-11-18): H, on the Hubs; without a connection, on the Settings. Rebindable, one key. */
	private static final KeyMapping OPEN_HUB_MANAGER = new KeyMapping("key.phantasmon.hub_manager", GLFW.GLFW_KEY_H, CATEGORY);

	private PhantasmonKeybinds() {
	}

	/** Whether a key pressed in a screen is the Phantasmon menu's: pressed again, it closes the menu. */
	public static boolean isMenuKey(int keyCode, int scanCode) {
		return OPEN_HUB_MANAGER.matches(keyCode, scanCode);
	}

	/** Whether a key pressed in a screen is the PC's: pressed again, it closes the PC (2026-11-28, every menu key toggles). */
	public static boolean isPcKey(int keyCode, int scanCode) {
		return OPEN_PC.matches(keyCode, scanCode);
	}

	public static void register() {
		KeyBindingHelper.registerKeyBinding(OPEN_PC);
		KeyBindingHelper.registerKeyBinding(OPEN_HUB_MANAGER);
	}

	/** Called every client tick (see {@link PhantasmonClient}) — {@code consumeClick()} is the standard vanilla pattern for a keybind's action firing once per press, queued click included, regardless of how long the key is held. */
	public static void tick(PcOpener pcOpener, Runnable openHubManager) {
		while (OPEN_PC.consumeClick()) {
			pcOpener.openPc();
		}
		while (OPEN_HUB_MANAGER.consumeClick()) {
			openHubManager.run();
		}
	}
}
