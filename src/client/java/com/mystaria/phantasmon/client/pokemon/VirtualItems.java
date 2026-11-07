package com.mystaria.phantasmon.client.pokemon;

import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import com.mystaria.phantasmon.client.pokemon.showdown.CobblemonIdentifiers;

/**
 * The battle items Cobblemon has no Minecraft item for: Mega Stones, Z-Crystals, Plates, Memories, Drives, signature
 * masks and orbs, Booster Energy (Adrien 2026-11-07: Cobblemon stays our only dependency). A client-only mod can't add
 * items, so a Ghost holds one "virtually": its {@code held_item} names it (Showdown id, or the {@code firium_z} form an
 * import stores), {@code GhostVirtualItems} hands it to the battle engine, which knows them all, and Phantasmon draws
 * its own icon ({@code tools/held_item_icons.py}).
 */
public final class VirtualItems {

	/** Showdown id → name. */
	private static final Map<String, String> NAMES = Map.ofEntries(
			// GENERATED BEGIN by tools/held_item_icons.py
			Map.entry("abomasite", "Abomasite"),
			Map.entry("absolite", "Absolite"),
			Map.entry("adamantcrystal", "Adamant Crystal"),
			Map.entry("adamantorb", "Adamant Orb"),
			Map.entry("adrenalineorb", "Adrenaline Orb"),
			Map.entry("aerodactylite", "Aerodactylite"),
			Map.entry("aggronite", "Aggronite"),
			Map.entry("alakazite", "Alakazite"),
			Map.entry("aloraichiumz", "Aloraichium Z"),
			Map.entry("altarianite", "Altarianite"),
			Map.entry("ampharosite", "Ampharosite"),
			Map.entry("audinite", "Audinite"),
			Map.entry("banettite", "Banettite"),
			Map.entry("beedrillite", "Beedrillite"),
			Map.entry("blastoisinite", "Blastoisinite"),
			Map.entry("blazikenite", "Blazikenite"),
			Map.entry("blueorb", "Blue Orb"),
			Map.entry("boosterenergy", "Booster Energy"),
			Map.entry("buginiumz", "Buginium Z"),
			Map.entry("bugmemory", "Bug Memory"),
			Map.entry("burndrive", "Burn Drive"),
			Map.entry("cameruptite", "Cameruptite"),
			Map.entry("charizarditex", "Charizardite X"),
			Map.entry("charizarditey", "Charizardite Y"),
			Map.entry("chilldrive", "Chill Drive"),
			Map.entry("cornerstonemask", "Cornerstone Mask"),
			Map.entry("darkiniumz", "Darkinium Z"),
			Map.entry("darkmemory", "Dark Memory"),
			Map.entry("decidiumz", "Decidium Z"),
			Map.entry("diancite", "Diancite"),
			Map.entry("dousedrive", "Douse Drive"),
			Map.entry("dracoplate", "Draco Plate"),
			Map.entry("dragoniumz", "Dragonium Z"),
			Map.entry("dragonmemory", "Dragon Memory"),
			Map.entry("dreadplate", "Dread Plate"),
			Map.entry("earthplate", "Earth Plate"),
			Map.entry("eeviumz", "Eevium Z"),
			Map.entry("electricmemory", "Electric Memory"),
			Map.entry("electriumz", "Electrium Z"),
			Map.entry("fairiumz", "Fairium Z"),
			Map.entry("fairymemory", "Fairy Memory"),
			Map.entry("fightingmemory", "Fighting Memory"),
			Map.entry("fightiniumz", "Fightinium Z"),
			Map.entry("firememory", "Fire Memory"),
			Map.entry("firiumz", "Firium Z"),
			Map.entry("fistplate", "Fist Plate"),
			Map.entry("flameplate", "Flame Plate"),
			Map.entry("flyingmemory", "Flying Memory"),
			Map.entry("flyiniumz", "Flyinium Z"),
			Map.entry("galladite", "Galladite"),
			Map.entry("garchompite", "Garchompite"),
			Map.entry("gardevoirite", "Gardevoirite"),
			Map.entry("gengarite", "Gengarite"),
			Map.entry("ghostiumz", "Ghostium Z"),
			Map.entry("ghostmemory", "Ghost Memory"),
			Map.entry("glalitite", "Glalitite"),
			Map.entry("grassiumz", "Grassium Z"),
			Map.entry("grassmemory", "Grass Memory"),
			Map.entry("griseouscore", "Griseous Core"),
			Map.entry("griseousorb", "Griseous Orb"),
			Map.entry("groundiumz", "Groundium Z"),
			Map.entry("groundmemory", "Ground Memory"),
			Map.entry("gyaradosite", "Gyaradosite"),
			Map.entry("hearthflamemask", "Hearthflame Mask"),
			Map.entry("heracronite", "Heracronite"),
			Map.entry("houndoominite", "Houndoominite"),
			Map.entry("icememory", "Ice Memory"),
			Map.entry("icicleplate", "Icicle Plate"),
			Map.entry("iciumz", "Icium Z"),
			Map.entry("inciniumz", "Incinium Z"),
			Map.entry("insectplate", "Insect Plate"),
			Map.entry("ironplate", "Iron Plate"),
			Map.entry("kangaskhanite", "Kangaskhanite"),
			Map.entry("kommoniumz", "Kommonium Z"),
			Map.entry("latiasite", "Latiasite"),
			Map.entry("latiosite", "Latiosite"),
			Map.entry("lopunnite", "Lopunnite"),
			Map.entry("lucarionite", "Lucarionite"),
			Map.entry("luckypunch", "Lucky Punch"),
			Map.entry("lunaliumz", "Lunalium Z"),
			Map.entry("lustrousglobe", "Lustrous Globe"),
			Map.entry("lustrousorb", "Lustrous Orb"),
			Map.entry("lycaniumz", "Lycanium Z"),
			Map.entry("manectite", "Manectite"),
			Map.entry("marshadiumz", "Marshadium Z"),
			Map.entry("mawilite", "Mawilite"),
			Map.entry("meadowplate", "Meadow Plate"),
			Map.entry("medichamite", "Medichamite"),
			Map.entry("metagrossite", "Metagrossite"),
			Map.entry("mewniumz", "Mewnium Z"),
			Map.entry("mewtwonitex", "Mewtwonite X"),
			Map.entry("mewtwonitey", "Mewtwonite Y"),
			Map.entry("mimikiumz", "Mimikium Z"),
			Map.entry("mindplate", "Mind Plate"),
			Map.entry("normaliumz", "Normalium Z"),
			Map.entry("pidgeotite", "Pidgeotite"),
			Map.entry("pikaniumz", "Pikanium Z"),
			Map.entry("pikashuniumz", "Pikashunium Z"),
			Map.entry("pinsirite", "Pinsirite"),
			Map.entry("pixieplate", "Pixie Plate"),
			Map.entry("poisoniumz", "Poisonium Z"),
			Map.entry("poisonmemory", "Poison Memory"),
			Map.entry("primariumz", "Primarium Z"),
			Map.entry("psychicmemory", "Psychic Memory"),
			Map.entry("psychiumz", "Psychium Z"),
			Map.entry("redorb", "Red Orb"),
			Map.entry("rockiumz", "Rockium Z"),
			Map.entry("rockmemory", "Rock Memory"),
			Map.entry("rustedshield", "Rusted Shield"),
			Map.entry("rustedsword", "Rusted Sword"),
			Map.entry("sablenite", "Sablenite"),
			Map.entry("salamencite", "Salamencite"),
			Map.entry("sceptilite", "Sceptilite"),
			Map.entry("scizorite", "Scizorite"),
			Map.entry("sharpedonite", "Sharpedonite"),
			Map.entry("shockdrive", "Shock Drive"),
			Map.entry("skyplate", "Sky Plate"),
			Map.entry("slowbronite", "Slowbronite"),
			Map.entry("snorliumz", "Snorlium Z"),
			Map.entry("solganiumz", "Solganium Z"),
			Map.entry("souldew", "Soul Dew"),
			Map.entry("splashplate", "Splash Plate"),
			Map.entry("spookyplate", "Spooky Plate"),
			Map.entry("steeliumz", "Steelium Z"),
			Map.entry("steelixite", "Steelixite"),
			Map.entry("steelmemory", "Steel Memory"),
			Map.entry("stoneplate", "Stone Plate"),
			Map.entry("swampertite", "Swampertite"),
			Map.entry("tapuniumz", "Tapunium Z"),
			Map.entry("toxicplate", "Toxic Plate"),
			Map.entry("tyranitarite", "Tyranitarite"),
			Map.entry("ultranecroziumz", "Ultranecrozium Z"),
			Map.entry("venusaurite", "Venusaurite"),
			Map.entry("wateriumz", "Waterium Z"),
			Map.entry("watermemory", "Water Memory"),
			Map.entry("wellspringmask", "Wellspring Mask"),
			Map.entry("zapplate", "Zap Plate"));
			// GENERATED END

	private VirtualItems() {
	}

	/** The Showdown id of the item {@code heldItemId} names, or null if it isn't one of these. */
	public static String showdownId(String heldItemId) {
		if (heldItemId == null || heldItemId.isBlank()) {
			return null;
		}
		String id = CobblemonIdentifiers.slugConcat(heldItemId.contains(":") ? heldItemId.substring(heldItemId.indexOf(':') + 1) : heldItemId);
		return NAMES.containsKey(id) ? id : null;
	}

	public static boolean isVirtual(String heldItemId) {
		return showdownId(heldItemId) != null;
	}

	/** Display name ("Firium Z"), or null if {@code heldItemId} isn't one of these. */
	public static String name(String heldItemId) {
		String id = showdownId(heldItemId);
		return id == null ? null : NAMES.get(id);
	}

	/** Its 32x32 icon, or null if {@code heldItemId} isn't one of these. */
	public static ResourceLocation icon(String heldItemId) {
		String id = showdownId(heldItemId);
		return id == null ? null : ResourceLocation.fromNamespaceAndPath("phantasmon", "textures/gui/held_items/" + id + ".png");
	}

	/** Every id, for the editor's list. */
	public static List<String> ids() {
		return List.copyOf(NAMES.keySet());
	}
}
