package com.mystaria.phantasmon.client.pokemon;

import java.util.List;
import java.util.Locale;

import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;

import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.gui.PhantasmonCanvasScreen;
import com.mystaria.phantasmon.client.gui.PokemonGuiRendering;

/**
 * Whether this client's Cobblemon data knows a stored Ghost Pokémon (CAD Partie 2 §6.1, DEBT-5): its species, form
 * and moves must all resolve. When one doesn't — typically a Pokémon made with another Cobblemon version — the
 * player is told so (with both versions) and the Pokémon is kept out of send-out and battles, but never modified:
 * the stored data stays intact.
 */
public final class PokemonRecognition {

	private PokemonRecognition() {
	}

	/** {@code null} if recognized, else the message to show the player. */
	public static Component problem(PokemonDto pokemon) {
		String missing = missingPart(pokemon);
		if (missing == null) {
			return null;
		}
		String stored = pokemon.cobblemonDataVersion() == null ? "?" : pokemon.cobblemonDataVersion();
		return Component.translatable("phantasmon.pokemon.unrecognized", PhantasmonCanvasScreen.displayName(pokemon),
				missing, stored, CobblemonDataVersion.local());
	}

	private static String missingPart(PokemonDto pokemon) {
		Species species = pokemon.species() == null ? null : PokemonSpecies.INSTANCE.getByName(pokemon.species());
		if (species == null) {
			return String.valueOf(pokemon.species());
		}
		if (pokemon.form() != null && !pokemon.form().isBlank()
				&& PokemonGuiRendering.resolveForm(species, pokemon.form()) == null) {
			return pokemon.species() + "-" + pokemon.form();
		}
		if (pokemon.data() != null && pokemon.data().get("moves") instanceof List<?> moves) {
			for (Object move : moves) {
				String id = move == null ? "" : move.toString().toLowerCase(Locale.ROOT);
				if (!id.isBlank() && Moves.getByName(id) == null) {
					return id;
				}
			}
		}
		return null;
	}
}
