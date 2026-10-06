package com.mystaria.phantasmon.client.pokemon;

import java.util.Map;

import com.mystaria.phantasmon.client.pokemon.showdown.CobblemonIdentifiers;

/**
 * The 35 Z-Crystals of Pokémon Showdown (TODO-28). No mod of the pack makes them Minecraft items, and a client-only
 * mod can't add items — so a Ghost holds one "virtually": its {@code held_item} names it (Showdown id, or the
 * {@code firium_z} form an import stores) and {@code GhostZCrystals} hands it to the battle engine directly.
 */
public final class ZCrystals {

	/** Showdown id → name. */
	private static final Map<String, String> NAMES = Map.ofEntries(
			Map.entry("aloraichiumz", "Aloraichium Z"),
			Map.entry("buginiumz", "Buginium Z"),
			Map.entry("darkiniumz", "Darkinium Z"),
			Map.entry("decidiumz", "Decidium Z"),
			Map.entry("dragoniumz", "Dragonium Z"),
			Map.entry("eeviumz", "Eevium Z"),
			Map.entry("electriumz", "Electrium Z"),
			Map.entry("fairiumz", "Fairium Z"),
			Map.entry("fightiniumz", "Fightinium Z"),
			Map.entry("firiumz", "Firium Z"),
			Map.entry("flyiniumz", "Flyinium Z"),
			Map.entry("ghostiumz", "Ghostium Z"),
			Map.entry("grassiumz", "Grassium Z"),
			Map.entry("groundiumz", "Groundium Z"),
			Map.entry("iciumz", "Icium Z"),
			Map.entry("inciniumz", "Incinium Z"),
			Map.entry("kommoniumz", "Kommonium Z"),
			Map.entry("lunaliumz", "Lunalium Z"),
			Map.entry("lycaniumz", "Lycanium Z"),
			Map.entry("marshadiumz", "Marshadium Z"),
			Map.entry("mewniumz", "Mewnium Z"),
			Map.entry("mimikiumz", "Mimikium Z"),
			Map.entry("normaliumz", "Normalium Z"),
			Map.entry("pikaniumz", "Pikanium Z"),
			Map.entry("pikashuniumz", "Pikashunium Z"),
			Map.entry("poisoniumz", "Poisonium Z"),
			Map.entry("primariumz", "Primarium Z"),
			Map.entry("psychiumz", "Psychium Z"),
			Map.entry("rockiumz", "Rockium Z"),
			Map.entry("snorliumz", "Snorlium Z"),
			Map.entry("solganiumz", "Solganium Z"),
			Map.entry("steeliumz", "Steelium Z"),
			Map.entry("tapuniumz", "Tapunium Z"),
			Map.entry("ultranecroziumz", "Ultranecrozium Z"),
			Map.entry("wateriumz", "Waterium Z"));

	private ZCrystals() {
	}

	/** The Showdown id of the Z-Crystal {@code heldItemId} names, or null if it isn't one. */
	public static String showdownId(String heldItemId) {
		if (heldItemId == null || heldItemId.isBlank()) {
			return null;
		}
		String id = CobblemonIdentifiers.slugConcat(heldItemId.contains(":") ? heldItemId.substring(heldItemId.indexOf(':') + 1) : heldItemId);
		return NAMES.containsKey(id) ? id : null;
	}

	public static boolean isZCrystal(String heldItemId) {
		return showdownId(heldItemId) != null;
	}

	/** Display name ("Firium Z"), or null if {@code heldItemId} isn't a Z-Crystal. */
	public static String name(String heldItemId) {
		String id = showdownId(heldItemId);
		return id == null ? null : NAMES.get(id);
	}

	/** Every Z-Crystal id, for the editor's item list. */
	public static java.util.Set<String> ids() {
		return NAMES.keySet();
	}
}
