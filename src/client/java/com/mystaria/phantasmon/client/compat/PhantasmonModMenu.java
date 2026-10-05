package com.mystaria.phantasmon.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import com.mystaria.phantasmon.client.gui.PhantasmonMusicScreen;

/**
 * Mod Menu's "Configure" button for Phantasmon opens the music screen (Adrien 2026-10-05). Only loaded when Mod Menu
 * is installed ({@code modmenu} entrypoint); Phantasmon doesn't depend on it.
 */
public final class PhantasmonModMenu implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return PhantasmonMusicScreen::new;
	}
}
