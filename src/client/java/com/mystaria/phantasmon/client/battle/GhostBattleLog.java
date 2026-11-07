package com.mystaria.phantasmon.client.battle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.pokemon.helditem.HeldItemProvider;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.fabricmc.loader.api.FabricLoader;

/**
 * One file per Ghost battle hosted here, in {@code logs/phantasmon-battles/} (Adrien 2026-11-07: battle bugs must be
 * checkable afterwards): each team as the engine gets it (species, form, ability, held item, nature, EVs, IVs, level,
 * moves), then everything the engine said. The 50 latest are kept.
 */
final class GhostBattleLog {

	private static final Logger LOG = LoggerFactory.getLogger(GhostBattleLog.class);
	private static final int KEPT = 50;

	private GhostBattleLog() {
	}

	/** At the end of the battle: its teams and its whole Showdown log. */
	static void write(PokemonBattle battle) {
		try {
			Path dir = FabricLoader.getInstance().getGameDir().resolve("logs/phantasmon-battles");
			Files.createDirectories(dir);
			List<String> lines = new ArrayList<>();
			lines.add("Battle " + battle.getBattleId() + " — " + battle.getFormat());
			for (BattleActor actor : battle.getActors()) {
				lines.add("");
				lines.add("== " + actor.getName().getString() + " (" + actor.getShowdownId() + ")");
				for (BattlePokemon member : actor.getPokemonList()) {
					lines.add(describe(member));
				}
			}
			lines.add("");
			lines.add("== Showdown");
			lines.addAll(battle.getShowdownMessages());
			String name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".log";
			Files.write(dir.resolve(name), lines, StandardCharsets.UTF_8);
			prune(dir);
		} catch (IOException | RuntimeException ex) {
			LOG.warn("Ghost battle log not written", ex);
		}
	}

	private static String describe(BattlePokemon member) {
		Pokemon pk = member.getEffectedPokemon();
		StringBuilder line = new StringBuilder("  ").append(pk.getSpecies().getName());
		if (!pk.getForm().getName().equalsIgnoreCase("normal")) {
			line.append('-').append(pk.getForm().getName());
		}
		line.append(" L").append(pk.getLevel())
				.append(" | ability ").append(pk.getAbility().getName())
				.append(" | item ").append(String.valueOf(HeldItemProvider.provideShowdownId(member)))
				.append(" | ").append(pk.getNature().getName().getPath())
				.append(" | EVs");
		for (var stat : Stats.Companion.getPERMANENT()) {
			line.append(' ').append(pk.getEvs().getOrDefault(stat));
		}
		line.append(" | IVs");
		for (var stat : Stats.Companion.getPERMANENT()) {
			line.append(' ').append(pk.getIvs().getOrDefault(stat));
		}
		line.append(" | moves");
		pk.getMoveSet().getMoves().forEach(move -> line.append(' ').append(move.getName()));
		line.append(" | tera ").append(pk.getTeraType().getId().getPath()).append(" | friendship ").append(pk.getFriendship());
		return line.toString();
	}

	private static void prune(Path dir) throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			List<Path> sorted = files.filter(p -> p.toString().endsWith(".log")).sorted(Comparator.reverseOrder()).toList();
			for (int i = KEPT; i < sorted.size(); i++) {
				Files.deleteIfExists(sorted.get(i));
			}
		}
	}
}
