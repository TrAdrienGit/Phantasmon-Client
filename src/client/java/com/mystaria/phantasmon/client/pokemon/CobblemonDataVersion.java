package com.mystaria.phantasmon.client.pokemon;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The Cobblemon version recorded on each Pokémon as {@code cobblemon_data_version} (CAD Partie 2 §6.1, DEBT-5): the
 * one actually installed on this client, read from Fabric, not a constant. Build metadata is dropped
 * ({@code 1.8.1+1.21.1} → {@code 1.8.1}).
 */
public final class CobblemonDataVersion {

	/** Only if Cobblemon's mod metadata can't be read (never in game: Cobblemon is a required dependency). */
	static final String FALLBACK = "1.8.1";
	private static final int MAX_LENGTH = 32;

	private static volatile String local;

	private CobblemonDataVersion() {
	}

	public static String local() {
		String version = local;
		if (version == null) {
			version = FabricLoader.getInstance().getModContainer("cobblemon")
					.map(mod -> normalize(mod.getMetadata().getVersion().getFriendlyString()))
					.orElse(FALLBACK);
			local = version;
		}
		return version;
	}

	static String normalize(String friendlyVersion) {
		if (friendlyVersion == null) {
			return FALLBACK;
		}
		int plus = friendlyVersion.indexOf('+');
		String core = (plus >= 0 ? friendlyVersion.substring(0, plus) : friendlyVersion).trim();
		if (core.isEmpty()) {
			return FALLBACK;
		}
		return core.length() > MAX_LENGTH ? core.substring(0, MAX_LENGTH) : core;
	}
}
