package com.mystaria.phantasmon.client.battle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The Mega Evolutions newer than Cobblemon's battle engine (Legends Z-A / Champions, 2026-11-26): 49 formes, 45
 * Mega Stones and 7 abilities, installed in the engine as a {@link ShowdownExtensions} extension, so only Phantasmon's
 * battles have them. Data from Pokémon Showdown ({@code new_megas_data.js}, written by {@code tools/new_megas.py});
 * the installer ({@code new_megas.js}) adds only what the engine lacks, so a Cobblemon update that brings them wins.
 * Tested in Node by {@code tools/new_megas_test.js}. See Documentation/research/nouvelles-megas-plan.md.
 */
public final class NewMegas {

	public static final String EXTENSION = "new_megas";
	private static final String ROOT = "/assets/phantasmon/showdown/";

	private NewMegas() {
	}

	/** Once, at client start. */
	public static void register() {
		ShowdownExtensions.register(EXTENSION,
				"(function(){try{return !!globalThis.PhantasmonNewMegas&&globalThis.PhantasmonNewMegas.intact()}catch(e){return false}})()",
				NewMegas::script);
	}

	private static String script() {
		// The data file is a commented expression, the installer a commented function: both end up on lines of their own.
		return "(function(){const DATA=\n" + read("new_megas_data.js") + ";\n(\n" + read("new_megas.js") + "\n)(DATA);})();";
	}

	private static String read(String name) {
		try (InputStream in = NewMegas.class.getResourceAsStream(ROOT + name)) {
			if (in == null) {
				throw new IllegalStateException("missing " + ROOT + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
