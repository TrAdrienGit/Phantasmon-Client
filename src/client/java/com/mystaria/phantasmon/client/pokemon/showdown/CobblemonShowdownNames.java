package com.mystaria.phantasmon.client.pokemon.showdown;

import java.util.List;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.resources.ResourceManager;

import com.mystaria.phantasmon.client.gui.PokemonGuiRendering;

/**
 * {@link ShowdownNames} from Cobblemon's own <b>English</b> translations ({@code en_us}, loaded on purpose whatever
 * the game language: Showdown only understands English names) — e.g. {@code cobblemon.move.closecombat} →
 * {@code Close Combat}. Form suffixes come from the species' form data ({@code Hisui}). Anything without a
 * translation falls back to {@link ShowdownNames#FALLBACK}. Client thread only.
 */
public final class CobblemonShowdownNames implements ShowdownNames {

	public static final CobblemonShowdownNames INSTANCE = new CobblemonShowdownNames();

	private ResourceManager loadedFrom;
	private ClientLanguage english;

	private CobblemonShowdownNames() {
	}

	/** Loaded once, and again only after a resource reload (new resource manager). */
	private ClientLanguage english() {
		ResourceManager resources = Minecraft.getInstance().getResourceManager();
		if (english == null || resources != loadedFrom) {
			english = ClientLanguage.loadFrom(resources, List.of("en_us"), false);
			loadedFrom = resources;
		}
		return english;
	}

	private String lookup(String fallback, String... keys) {
		ClientLanguage language = english();
		for (String key : keys) {
			if (language.has(key)) {
				return language.getOrDefault(key, fallback);
			}
		}
		return fallback;
	}

	private static String bare(String id) {
		return id.substring(id.indexOf(':') + 1);
	}

	@Override
	public String species(String speciesId) {
		return lookup(FALLBACK.species(speciesId), "cobblemon.species." + bare(speciesId) + ".name");
	}

	@Override
	public String form(String speciesId, String formId) {
		Species species = PokemonSpecies.INSTANCE.getByName(bare(speciesId));
		FormData form = species == null ? null : PokemonGuiRendering.resolveForm(species, formId);
		if (form == null || form.getName() == null || form.getName().equalsIgnoreCase("normal")) {
			return form == null ? FALLBACK.form(speciesId, formId) : "";
		}
		return form.getName();
	}

	@Override
	public String move(String moveId) {
		return lookup(FALLBACK.move(moveId), "cobblemon.move." + bare(moveId));
	}

	@Override
	public String ability(String abilityId) {
		String id = bare(abilityId);
		return lookup(FALLBACK.ability(abilityId), "cobblemon.ability." + id, "cobblemon.ability." + id.replace("_", ""));
	}

	@Override
	public String item(String itemId) {
		String id = bare(itemId);
		return lookup(FALLBACK.item(itemId), "item.cobblemon." + id, "item.minecraft." + id);
	}

	@Override
	public String nature(String natureId) {
		return lookup(FALLBACK.nature(natureId), "cobblemon.nature." + bare(natureId));
	}

	@Override
	public String type(String typeId) {
		return lookup(FALLBACK.type(typeId), "cobblemon.type." + bare(typeId));
	}
}
