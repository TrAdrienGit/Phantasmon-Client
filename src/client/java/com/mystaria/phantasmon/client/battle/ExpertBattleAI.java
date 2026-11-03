package com.mystaria.phantasmon.client.battle;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.InBattleGimmickMove;
import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.cobblemon.mod.common.battles.ai.RandomBattleAI;
import com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Phantasmon's own competitive AI, level {@code expert} (Adrien 2026-11-03), second version. Single battles only (the
 * only kind Phantasmon plays).
 *
 * <p><b>What it knows of the opponent</b>: what a player would — the Pokémon on the field (species, level, HP, stat
 * stages, status), the moves it has used, the Pokémon of its team already seen. The rest comes from Smogon's usage
 * stats ({@code assets/phantasmon/ai/smogon_sets.json}, see {@code tools/smogon_sets.py}): its usual moves, item, ability,
 * EV spread and nature. It never reads the opponent's real set (only for a Pokémon Smogon has no data on).
 *
 * <p><b>How it chooses</b>, each turn, one turn ahead with both sides choosing at once:
 * <ol>
 * <li>its options: every move it can use — with Mega Evolution, Terastallization or its Z-move when it can — and every
 * switch;</li>
 * <li>the opponent's options: its likely moves (weighted by how often they are run) and switches to the Pokémon it has
 * shown; how likely each is comes from how good it looks to the opponent against the AI's most obvious attack — so it
 * predicts a switch into a resist, and hits the Pokémon coming in;</li>
 * <li>each pair is played out: switches first, then moves by priority and Speed, a KO stopping the slower move;
 * scored by HP taken and lost, KOs, what status moves bring (boosts when safe, recovery, hazards, status), and the
 * matchup left on the field;</li>
 * <li>it plays the option with the best expected score; Tera and Z-moves cost something (they are used once), so they
 * come out when they win the exchange; Mega Evolution is always taken.</li>
 * </ol>
 * After a KO, the Pokémon with the best matchup comes in. Battle thread only.
 */
public final class ExpertBattleAI implements BattleAI {

	private static final Logger LOG = LoggerFactory.getLogger(ExpertBattleAI.class);
	/** How sharply the opponent is expected to prefer its best options (lower: more predictable). */
	private static final double OPPONENT_TEMPERATURE = 0.15;
	/** What keeping Tera / a Z-move for later is worth. */
	private static final double TERA_RESERVE = 0.25;
	private static final double Z_RESERVE = 0.3;
	private static final double POSITION_WEIGHT = 0.25;

	private static Map<String, JsonObject> sets;

	/** Opponent's Pokémon seen on the field so far (they can be expected to switch back in). */
	private final Set<UUID> seenFoes = new HashSet<>();
	/** Hazards this AI already laid on the opponent's side, and how many times (Spikes: 3, Toxic Spikes: 2). */
	private final Map<String, Integer> hazardsLaid = new HashMap<>();
	/** Its own Pokémon terastallized, and into what. */
	private final Map<UUID, String> terastallized = new HashMap<>();
	private boolean zUsed;

	// ================================================================ choosing

	@Override
	public ShowdownActionResponse choose(ActiveBattlePokemon active, PokemonBattle battle, BattleSide side,
			ShowdownMoveset moveset, boolean forceSwitch) {
		ActiveBattlePokemon foeSlot = side.getOppositeSide().getActivePokemon().get(0);
		BattlePokemon foeBattler = foeSlot.getBattlePokemon();
		if (foeBattler != null) {
			seenFoes.add(foeBattler.getUuid());
		}
		Mon foe = foeBattler == null ? null : belief(foeBattler);
		BattlePokemon meBattler = active.getBattlePokemon();
		if (forceSwitch || meBattler == null || meBattler.getHealth() <= 0) {
			BattlePokemon next = bestReplacement(active, foe);
			return next == null ? null : new SwitchActionResponse(next.getUuid());
		}
		if (foe == null || moveset == null) {
			return new RandomBattleAI().choose(active, battle, side, moveset, forceSwitch);
		}
		Mon me = own(meBattler);
		List<Mine> mine = myOptions(active, me, moveset);
		List<Theirs> theirs = foeOptions(foe, foeBattler);
		if (mine.isEmpty()) {
			return new RandomBattleAI().choose(active, battle, side, moveset, forceSwitch);
		}
		// The opponent's guess of what we do: our most obvious attack, no gimmick.
		Mine obvious = null;
		double obviousDamage = -1;
		for (Mine option : mine) {
			if (option.move != null && option.gimmick == null) {
				double damage = damage(me, foe, option.move, null, 0);
				if (damage > obviousDamage) {
					obviousDamage = damage;
					obvious = option;
				}
			}
		}
		double[] weights = new double[theirs.size()];
		double total = 0;
		for (int i = 0; i < theirs.size(); i++) {
			double good = obvious == null ? 0 : -play(obvious, theirs.get(i), me, foe);
			weights[i] = theirs.get(i).prior * Math.exp(good / OPPONENT_TEMPERATURE);
			total += weights[i];
		}
		Mine best = null;
		double bestValue = Double.NEGATIVE_INFINITY;
		for (Mine option : mine) {
			double value = 0;
			for (int i = 0; i < theirs.size(); i++) {
				value += weights[i] / total * play(option, theirs.get(i), me, foe);
			}
			value -= option.cost;
			if (value > bestValue) {
				bestValue = value;
				best = option;
			}
		}
		LOG.debug("Expert AI: {} ({})", best.label, String.format(Locale.ROOT, "%.2f", bestValue));
		if (best.switchTo != null) {
			return new SwitchActionResponse(best.switchTo.battler.getUuid());
		}
		String gimmick = best.gimmick;
		if (gimmick == null && moveset.getCanMegaEvo()) {
			gimmick = ShowdownMoveset.Gimmick.MEGA_EVOLUTION.getId(); // no reason to wait
		}
		if (ShowdownMoveset.Gimmick.TERASTALLIZATION.getId().equals(best.gimmick)) {
			terastallized.put(meBattler.getUuid(), moveset.getCanTerastallize().toLowerCase(Locale.ROOT));
		}
		if (ShowdownMoveset.Gimmick.Z_POWER.getId().equals(best.gimmick)) {
			zUsed = true;
		}
		if (AIUtility.INSTANCE.getEntryHazards().contains(best.move.getName())) {
			hazardsLaid.merge(best.move.getName(), 1, Integer::sum);
		}
		return new MoveActionResponse(best.moveId, foeSlot.getPNX(), gimmick);
	}

	/** One of our options: a move (with a gimmick or not), or a switch. */
	private static final class Mine {
		String label;
		String moveId;
		MoveTemplate move;
		String gimmick;
		/** Tera: the type taken; Z: the Z-move's power. */
		String teraType;
		double zPower;
		Mon switchTo;
		double cost;
	}

	/** One of the opponent's options, and how likely it is a priori. */
	private record Theirs(MoveTemplate move, Mon switchTo, double prior) {
	}

	private List<Mine> myOptions(ActiveBattlePokemon active, Mon me, ShowdownMoveset moveset) {
		List<Mine> options = new ArrayList<>();
		String teraType = moveset.getCanTerastallize() == null ? null : moveset.getCanTerastallize().toLowerCase(Locale.ROOT);
		List<InBattleGimmickMove> zMoves = moveset.getCanZMove();
		for (int i = 0; i < moveset.getMoves().size(); i++) {
			InBattleMove move = moveset.getMoves().get(i);
			MoveTemplate template = move.canBeUsed() ? Moves.getByName(move.getId()) : null;
			if (template == null) {
				continue;
			}
			options.add(move(move.getId(), template, null));
			if (teraType != null && !teraType.equals("stellar")) {
				Mine tera = move(move.getId(), template, ShowdownMoveset.Gimmick.TERASTALLIZATION.getId());
				tera.teraType = teraType;
				tera.cost = TERA_RESERVE;
				tera.label += " + tera " + teraType;
				options.add(tera);
			}
			if (!zUsed && zMoves != null && i < zMoves.size() && zMoves.get(i) != null && !isStatus(template)) {
				Mine z = move(move.getId(), template, ShowdownMoveset.Gimmick.Z_POWER.getId());
				z.zPower = zPower(template.getPower());
				z.cost = Z_RESERVE;
				z.label += " (Z)";
				options.add(z);
			}
		}
		if (!moveset.getTrapped()) {
			for (BattlePokemon candidate : active.getActor().getPokemonList()) {
				if (canComeIn(candidate, me.battler)) {
					Mine change = new Mine();
					change.switchTo = own(candidate);
					change.label = "switch to " + candidate.getEffectedPokemon().getSpecies().getName();
					change.cost = 0.05; // tempo
					options.add(change);
				}
			}
		}
		return options;
	}

	private static Mine move(String id, MoveTemplate template, String gimmick) {
		Mine option = new Mine();
		option.moveId = id;
		option.move = template;
		option.gimmick = gimmick;
		option.label = id;
		return option;
	}

	private List<Theirs> foeOptions(Mon foe, BattlePokemon foeBattler) {
		List<Theirs> options = new ArrayList<>();
		for (Map.Entry<MoveTemplate, Double> move : foe.moves.entrySet()) {
			options.add(new Theirs(move.getKey(), null, move.getValue()));
		}
		for (BattlePokemon other : foeBattler.getActor().getPokemonList()) {
			if (other != foeBattler && other.getHealth() > 0 && seenFoes.contains(other.getUuid())) {
				options.add(new Theirs(null, belief(other), 0.3));
			}
		}
		if (options.isEmpty()) {
			options.add(new Theirs(null, null, 1)); // nothing known: it does nothing that counts
		}
		return options;
	}

	// ================================================================ one turn played out

	/** Our score for this pair of choices, one turn ahead (higher is better for us). */
	private double play(Mine a, Theirs b, Mon me, Mon foe) {
		Mon mine = a.switchTo != null ? a.switchTo : me;
		Mon theirs = b.switchTo != null ? b.switchTo : foe;
		String myTera = a.teraType != null ? a.teraType : terastallized.get(mine.battler.getUuid());
		double myHp = mine.hp;
		double foeHp = theirs.hp;
		double score = 0;
		// Switches happen first: the move then hits the Pokémon coming in.
		double dealt = a.move == null ? 0 : damage(mine, theirs, a.move, myTera, a.zPower);
		double taken = b.move == null ? 0 : damage(theirs, mine, b.move, null, 0, myTera);
		if (a.move != null && b.move != null) {
			boolean meFirst = movesFirst(mine, a.move.getPriority(), theirs, b.move.getPriority());
			if (meFirst && dealt >= foeHp) {
				taken = 0;
			} else if (!meFirst && taken >= myHp) {
				dealt = 0;
			}
		}
		dealt = Math.min(dealt, foeHp);
		taken = Math.min(taken, myHp);
		score += 1.2 * dealt / theirs.maxHp + (dealt >= foeHp ? 0.6 : 0);
		score -= taken / mine.maxHp + (taken >= myHp ? 0.6 : 0);
		if (a.move != null && isStatus(a.move) && taken < myHp) {
			score += utility(mine, theirs, a.move, b.move == null ? 0 : taken);
		}
		if (b.move != null && isStatus(b.move) && dealt < foeHp) {
			score -= utility(theirs, mine, b.move, dealt) * 0.8;
		}
		if (a.switchTo != null && b.move != null && AIUtility.INSTANCE.getPivotMoves().contains(b.move.getName())) {
			score -= 0.05;
		}
		// The matchup left on the field.
		if (taken < myHp && dealt < foeHp) {
			score += POSITION_WEIGHT * matchup(mine, theirs, myHp - taken, foeHp - dealt, myTera);
		}
		return score;
	}

	/** What a status move is worth to its user: a boost when it is safe, a heal when hurt, hazards, status. */
	private double utility(Mon user, Mon target, MoveTemplate move, double damageTaken) {
		AIUtility ai = AIUtility.INSTANCE;
		String name = move.getName();
		Map<Stat, Integer> boosts = ai.getBoostFromMoves().get(name);
		if (boosts != null) {
			double threat = bestDamage(target, user, null);
			if (threat * 2 >= user.hp - damageTaken) {
				return -0.2; // not safe: it would be knocked out before it uses the boost
			}
			double value = 0;
			boolean physical = user.stat(Stats.ATTACK) >= user.stat(Stats.SPECIAL_ATTACK);
			for (Map.Entry<Stat, Integer> boost : boosts.entrySet()) {
				int stage = user.stages.getOrDefault(boost.getKey(), 0);
				if (stage >= 4 || boost.getValue() <= 0) {
					continue;
				}
				Stat stat = boost.getKey();
				double weight = stat == Stats.SPEED ? (speed(user) < speed(target) ? 0.35 : 0.1)
						: stat == (physical ? Stats.ATTACK : Stats.SPECIAL_ATTACK) ? 0.35
						: stat == Stats.DEFENCE || stat == Stats.SPECIAL_DEFENCE ? 0.15 : 0.05;
				value += weight * boost.getValue() * (stage <= 0 ? 1 : 0.6);
			}
			return value;
		}
		if (ai.getSelfRecoveryMoves().contains(name)) {
			double missing = user.maxHp - (user.hp - damageTaken);
			return missing < user.maxHp * 0.3 ? -0.1 : Math.min(missing, user.maxHp * 0.5) / user.maxHp;
		}
		if (ai.getEntryHazards().contains(name)) {
			if (!user.ours) {
				return 0.2; // the opponent laying hazards on us
			}
			int max = name.equals("spikes") ? 3 : name.equals("toxicspikes") ? 2 : 1;
			int alive = target.battler == null ? 0 : aliveBench(target.battler);
			return hazardsLaid.getOrDefault(name, 0) >= max || alive < 1 ? -0.3 : (name.equals("stealthrock") ? 0.12 : 0.08) * alive;
		}
		String status = ai.getStatusMoves().get(name);
		if (status != null) {
			if (!target.status.isEmpty() || target.battler == null
					|| !ai.canAffectWithStatus(status, target.battler.getEffectedPokemon().getTypes(), target.battler.getEffectedPokemon().getAbility())) {
				return -0.3;
			}
			double value = switch (status) {
				case "brn" -> target.stat(Stats.ATTACK) > target.stat(Stats.SPECIAL_ATTACK) ? 0.45 : 0.15;
				case "par" -> speed(target) > speed(user) ? 0.4 : 0.2;
				case "tox", "psn" -> 0.3;
				case "slp" -> 0.5;
				default -> 0.15;
			};
			return value * accuracy(move);
		}
		if (ai.getProtectMoves().contains(name)) {
			return -0.1;
		}
		return -0.2; // other status moves: not understood yet
	}

	/** How {@code mine} fares one on one with these HP: best damage both ways, Speed, who knocks out whom first. */
	private double matchup(Mon mine, Mon theirs, double myHp, double foeHp, String myTera) {
		double dealt = 0;
		int myPriority = 0;
		for (MoveTemplate move : mine.moves.keySet()) {
			double damage = damage(mine, theirs, move, myTera, 0);
			if (damage > dealt) {
				dealt = damage;
				myPriority = move.getPriority();
			}
		}
		double taken = 0;
		int foePriority = 0;
		for (MoveTemplate move : theirs.moves.keySet()) {
			double damage = damage(theirs, mine, move, null, 0, myTera);
			if (damage > taken) {
				taken = damage;
				foePriority = move.getPriority();
			}
		}
		boolean first = movesFirst(mine, myPriority, theirs, foePriority);
		double turnsToKo = dealt <= 0 ? 99 : Math.ceil(foeHp / dealt);
		double turnsToDie = taken <= 0 ? 99 : Math.ceil(myHp / taken);
		double score = 0.6 * Math.min(1, dealt / theirs.maxHp) - 0.5 * Math.min(1, taken / mine.maxHp);
		if (turnsToKo < turnsToDie || turnsToKo == turnsToDie && first) {
			score += 0.5;
		} else {
			score -= 0.5;
		}
		return score;
	}

	private double bestDamage(Mon attacker, Mon defender, String defenderTera) {
		double best = 0;
		for (MoveTemplate move : attacker.moves.keySet()) {
			best = Math.max(best, damage(attacker, defender, move, null, 0, defenderTera));
		}
		return best;
	}

	// ================================================================ switches

	private static boolean canComeIn(BattlePokemon candidate, BattlePokemon current) {
		return candidate != current && candidate.getHealth() > 0 && !candidate.isSentOut() && candidate.canBeSentOut();
	}

	/** After a KO: the Pokémon with the best matchup against the one on the field (the healthiest if none). */
	private BattlePokemon bestReplacement(ActiveBattlePokemon active, Mon foe) {
		BattlePokemon best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (BattlePokemon candidate : active.getActor().getPokemonList()) {
			if (!canComeIn(candidate, active.getBattlePokemon())) {
				continue;
			}
			Mon mine = own(candidate);
			double score = foe == null ? mine.hp / mine.maxHp
					: matchup(mine, foe, mine.hp, foe.hp, terastallized.get(candidate.getUuid())) + 0.1 * mine.hp / mine.maxHp;
			if (score > bestScore) {
				bestScore = score;
				best = candidate;
			}
		}
		return best;
	}

	private static int aliveBench(BattlePokemon pokemon) {
		int alive = 0;
		for (BattlePokemon other : pokemon.getActor().getPokemonList()) {
			if (other != pokemon && other.getHealth() > 0) {
				alive++;
			}
		}
		return alive;
	}

	// ================================================================ what is known of a Pokémon

	/** A Pokémon as the AI sees it: exact for its own, believed (revealed + Smogon) for the opponent's. */
	private static final class Mon {
		BattlePokemon battler;
		boolean ours;
		int level;
		double hp;
		double maxHp;
		final Map<Stat, Double> stats = new HashMap<>();
		Map<Stat, Integer> stages = Map.of();
		final List<String> types = new ArrayList<>();
		String ability = "";
		String item = "";
		String status = "";
		/** Moves it has or is believed to have, with how likely each is. */
		final Map<MoveTemplate, Double> moves = new LinkedHashMap<>();

		double stat(Stat stat) {
			double value = stats.getOrDefault(stat, 100.0);
			int stage = stages.getOrDefault(stat, 0);
			return value * (stage >= 0 ? (2.0 + stage) / 2 : 2.0 / (2 - stage));
		}
	}

	private static Mon common(BattlePokemon battler) {
		Pokemon pokemon = battler.getEffectedPokemon();
		Mon mon = new Mon();
		mon.battler = battler;
		mon.level = pokemon.getLevel();
		mon.hp = battler.getHealth();
		mon.maxHp = Math.max(1, battler.getMaxHealth());
		mon.stages = battler.getStatChanges();
		for (ElementalType type : pokemon.getTypes()) {
			mon.types.add(type.getName().toLowerCase(Locale.ROOT));
		}
		var status = pokemon.getStatus();
		mon.status = status == null ? "" : status.getStatus().getShowdownName();
		return mon;
	}

	/** One of ours: everything known. */
	private static Mon own(BattlePokemon battler) {
		Mon mon = common(battler);
		Pokemon pokemon = battler.getEffectedPokemon();
		mon.ours = true;
		for (Stat stat : List.of(Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED)) {
			mon.stats.put(stat, (double) pokemon.getStat(stat));
		}
		mon.ability = id(pokemon.getAbility() == null ? "" : pokemon.getAbility().getName());
		var stack = pokemon.heldItem();
		mon.item = stack == null || stack.isEmpty() ? "" : id(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
		for (Move move : battler.getMoveSet().getMoves()) {
			if (move.getCurrentPp() > 0) {
				mon.moves.put(move.getTemplate(), 1.0);
			}
		}
		return mon;
	}

	/**
	 * One of the opponent's: what is visible (species, level, HP, stages, status, types) and the moves it has used;
	 * the rest from Smogon's usage (moves until four, item, ability, spread). A Pokémon Smogon has no data on is read
	 * as it is.
	 */
	private static Mon belief(BattlePokemon battler) {
		Pokemon pokemon = battler.getEffectedPokemon();
		JsonObject set = sets().get(id(pokemon.getForm().showdownId()));
		if (set == null) {
			set = sets().get(id(pokemon.getSpecies().getName()));
		}
		if (set == null) {
			Mon real = own(battler);
			real.ours = false;
			return real;
		}
		Mon mon = common(battler);
		Map<Stat, Integer> base = pokemon.getForm().getBaseStats();
		Nature nature = set.has("nature") ? Natures.getNature(set.get("nature").getAsString()) : null;
		JsonArray evs = set.has("evs") ? set.getAsJsonArray("evs") : null;
		List<Stat> order = List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED);
		for (int i = 1; i < order.size(); i++) {
			Stat stat = order.get(i);
			int ev = evs == null ? 84 : evs.get(i).getAsInt();
			double value = Math.floor((2.0 * base.getOrDefault(stat, 80) + 31 + Math.floor(ev / 4.0)) * mon.level / 100) + 5;
			if (nature != null && stat == nature.getIncreasedStat() && stat != nature.getDecreasedStat()) {
				value = Math.floor(value * 1.1);
			} else if (nature != null && stat == nature.getDecreasedStat() && stat != nature.getIncreasedStat()) {
				value = Math.floor(value * 0.9);
			}
			mon.stats.put(stat, value);
		}
		mon.ability = set.has("ability") ? id(set.get("ability").getAsString()) : "";
		mon.item = set.has("item") ? id(set.get("item").getAsString()) : "";
		// Revealed moves for sure, then the most run ones until four.
		for (Move move : battler.getMoveSet().getMoves()) {
			if (move.getCurrentPp() < move.getMaxPp()) {
				mon.moves.put(move.getTemplate(), 1.0);
			}
		}
		for (JsonElement element : set.getAsJsonArray("moves")) {
			if (mon.moves.size() >= 4) {
				break;
			}
			MoveTemplate template = Moves.getByName(element.getAsJsonArray().get(0).getAsString());
			if (template != null && !mon.moves.containsKey(template)) {
				mon.moves.put(template, element.getAsJsonArray().get(1).getAsDouble());
			}
		}
		return mon;
	}

	private static synchronized Map<String, JsonObject> sets() {
		if (sets == null) {
			sets = new HashMap<>();
			try (InputStream in = ExpertBattleAI.class.getResourceAsStream("/assets/phantasmon/ai/smogon_sets.json")) {
				if (in != null) {
					JsonObject all = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					all.entrySet().forEach(entry -> sets.put(entry.getKey(), entry.getValue().getAsJsonObject()));
				}
			} catch (Exception ex) {
				LOG.warn("Smogon sets unreadable: the Expert AI reads the opponents as they are", ex);
			}
		}
		return sets;
	}

	/** A Showdown-style id: lowercase letters and digits. */
	private static String id(String name) {
		return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	// ================================================================ damage

	private static final Set<String> FIXED_DAMAGE = Set.of("seismictoss", "nightshade");

	private static boolean isStatus(MoveTemplate move) {
		return "status".equals(move.getDamageCategory().getName()) || move.getPower() <= 0 && !FIXED_DAMAGE.contains(move.getName());
	}

	private static double damage(Mon attacker, Mon defender, MoveTemplate move, String attackerTera, double zPower) {
		return damage(attacker, defender, move, attackerTera, zPower, null);
	}

	/**
	 * Expected damage in HP points, accuracy included; 0 for a status move or an immune target. {@code attackerTera}:
	 * the attacker's Tera type (STAB), {@code defenderTera}: the defender's (its only type), {@code zPower}: a Z-move's.
	 */
	private static double damage(Mon attacker, Mon defender, MoveTemplate move, String attackerTera, double zPower, String defenderTera) {
		String name = move.getName();
		String moveType = move.getElementalType().getName().toLowerCase(Locale.ROOT);
		double effectiveness = effectiveness(move.getElementalType(), defender, defenderTera);
		if (effectiveness == 0) {
			return 0;
		}
		if (FIXED_DAMAGE.contains(name)) {
			return attacker.level * accuracy(move);
		}
		if (isStatus(move)) {
			return 0;
		}
		boolean physical = "physical".equals(move.getDamageCategory().getName());
		double attack = name.equals("foulplay") ? defender.stat(Stats.ATTACK)
				: name.equals("bodypress") ? attacker.stat(Stats.DEFENCE)
				: attacker.stat(physical ? Stats.ATTACK : Stats.SPECIAL_ATTACK);
		boolean targetsDefence = physical || name.equals("psyshock") || name.equals("psystrike") || name.equals("secretsword");
		double defence = defender.stat(targetsDefence ? Stats.DEFENCE : Stats.SPECIAL_DEFENCE);
		if (physical && (attacker.ability.equals("hugepower") || attacker.ability.equals("purepower"))) {
			attack *= 2;
		}
		if (physical && attacker.item.equals("choiceband") || !physical && attacker.item.equals("choicespecs")) {
			attack *= 1.5;
		}
		if (!targetsDefence && defender.item.equals("assaultvest")) {
			defence *= 1.5;
		}
		if (defender.item.equals("eviolite")) {
			defence *= 1.5;
		}
		double power = zPower > 0 ? zPower : move.getPower();
		double base = Math.floor(Math.floor(Math.floor(2.0 * attacker.level / 5 + 2) * power * attack / Math.max(1, defence)) / 50) + 2;
		double stab = 1;
		boolean original = attacker.types.contains(moveType);
		if (original) {
			stab = attacker.ability.equals("adaptability") ? 2 : 1.5;
		}
		if (attackerTera != null && attackerTera.equals(moveType)) {
			stab = original ? 2 : 1.5;
		}
		double modifier = 0.925 * stab * effectiveness;
		if (physical && attacker.status.equals("brn") && !attacker.ability.equals("guts")) {
			modifier *= 0.5;
		}
		if (physical && attacker.ability.equals("guts") && !attacker.status.isEmpty()) {
			modifier *= 1.5;
		}
		if (attacker.item.equals("lifeorb")) {
			modifier *= 1.3;
		}
		if (attacker.item.equals("expertbelt") && effectiveness > 1) {
			modifier *= 1.2;
		}
		var hits = zPower > 0 ? null : AIUtility.INSTANCE.getMultiHitMoves().get(name);
		double count = hits == null ? 1
				: attacker.ability.equals("skilllink") ? hits.getSecond() : (hits.getFirst() + hits.getSecond()) / 2.0 + (hits.getSecond() == 5 ? 0.1 : 0);
		return base * modifier * count * (zPower > 0 ? 1 : accuracy(move));
	}

	/** Type effectiveness on a Pokémon (its Tera type alone if terastallized), immunity abilities included. */
	private static double effectiveness(ElementalType moveType, Mon defender, String defenderTera) {
		ElementalType immune = AIUtility.INSTANCE.getTypeImmuneAbilities().get(defender.ability);
		if (immune != null && immune.getName().equalsIgnoreCase(moveType.getName())) {
			return 0;
		}
		if (defender.item.equals("airballoon") && moveType.getName().equalsIgnoreCase("ground")) {
			return 0;
		}
		List<String> types = defenderTera != null && !defenderTera.equals("stellar") ? List.of(defenderTera) : defender.types;
		double multiplier = 1;
		for (String type : types) {
			ElementalType defending = com.cobblemon.mod.common.api.types.ElementalTypes.get(type);
			if (defending != null) {
				multiplier *= AIUtility.INSTANCE.getDamageMultiplier(moveType, defending);
			}
		}
		return multiplier;
	}

	/** A Z-move's power from its base move's (the games' table). */
	private static double zPower(double power) {
		if (power <= 55) {
			return 100;
		} else if (power <= 65) {
			return 120;
		} else if (power <= 75) {
			return 140;
		} else if (power <= 85) {
			return 160;
		} else if (power <= 95) {
			return 175;
		} else if (power <= 100) {
			return 180;
		} else if (power <= 110) {
			return 185;
		} else if (power <= 125) {
			return 190;
		} else if (power <= 130) {
			return 195;
		}
		return 200;
	}

	private static double accuracy(MoveTemplate move) {
		double accuracy = move.getAccuracy();
		return accuracy <= 0 || accuracy > 100 ? 1 : accuracy / 100;
	}

	private static double speed(Mon mon) {
		double speed = mon.stat(Stats.SPEED);
		if (mon.status.equals("par")) {
			speed *= 0.5;
		}
		if (mon.item.equals("choicescarf")) {
			speed *= 1.5;
		}
		return speed;
	}

	private static boolean movesFirst(Mon me, int myPriority, Mon foe, int foePriority) {
		if (myPriority != foePriority) {
			return myPriority > foePriority;
		}
		return speed(me) >= speed(foe);
	}
}
