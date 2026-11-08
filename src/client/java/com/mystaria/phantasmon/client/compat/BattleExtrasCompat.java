package com.mystaria.phantasmon.client.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;

import com.cobblemon.mod.common.client.CobblemonClient;

/**
 * Cobblemon Battle Extras (Raguto & Yemagh), recommended beside Phantasmon (Adrien 2026-11-08): type matchups, stat
 * stages, both teams, turn and field counters, speed ranges. Its damage calculator is switched off for the length of a
 * Phantasmon battle, so every player fights on the same footing; the player's own setting comes back after, and the
 * config file is never written. Reached by reflection: Battle Extras stays optional, nothing of it is bundled.
 */
public final class BattleExtrasCompat {

	private static final Logger LOG = LoggerFactory.getLogger(BattleExtrasCompat.class);
	/** Its two damage-range displays: on the move buttons and on the switch tooltips. */
	private static final String[] DAMAGE_FIELDS = { "enableMoveDamageRange", "showSwitchTooltipDamageRange" };

	public static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("cobblemon-battle-extras");

	private static Object config;
	private static Field[] fields;
	private static boolean[] saved;
	private static boolean phantasmonBattle;
	private static boolean forced;
	private static boolean broken;
	/** The opponent's team as the lobby showed it, for Battle Extras' column: actor UUID and species/form of each. */
	private static java.util.UUID opponent;
	private static java.util.List<String[]> opponentTeam = java.util.List.of();
	private static boolean teamBroken;
	/** Built once per battle from the lobby: Battle Extras draws a slot's portrait only from a Pokémon. */
	private static java.util.List<com.cobblemon.mod.common.pokemon.Pokemon> opponentModels = java.util.List.of();

	/** The player's own battle team, built in full from the lobby (stats, EVs, moves, item): Battle Extras' left column. */
	private static java.util.List<com.mystaria.phantasmon.client.pokemon.PokemonDto> ownTeam = java.util.List.of();
	private static java.util.List<com.cobblemon.mod.common.pokemon.Pokemon> ownModels = java.util.List.of();

	private BattleExtrasCompat() {
	}

	/** In a Phantasmon battle, Battle Extras' team columns show portraits, not Poké Balls (Adrien 2026-11-08). */
	public static boolean portraitsOnly() {
		return PRESENT && phantasmonBattle;
	}

	/** Whether Battle Extras knows that slot of that actor as fainted: then its darkened ball stays. */
	public static boolean fainted(java.util.UUID actor, int slot, com.cobblemon.mod.common.pokemon.Pokemon pokemon) {
		if (pokemon != null && pokemon.isFainted()) {
			return true;
		}
		if (actor == null || slot < 0) {
			return false;
		}
		try {
			Class<?> data = Class.forName("name.modid.client.net.TeamBallClientData");
			return (boolean) data.getMethod("hasData", java.util.UUID.class).invoke(null, actor)
					&& (boolean) data.getMethod("isFainted", java.util.UUID.class, int.class).invoke(null, actor, slot);
		} catch (ReflectiveOperationException | RuntimeException ex) {
			return false;
		}
	}

	/** The same, for one of its opponent slots (its private {@code OpponentSlotData}). */
	public static boolean fainted(Object opponentSlot) {
		try {
			Field actor = opponentSlot.getClass().getDeclaredField("actorUuid");
			Field index = opponentSlot.getClass().getDeclaredField("teamSlotIndex");
			actor.setAccessible(true);
			index.setAccessible(true);
			return fainted((java.util.UUID) actor.get(opponentSlot), index.getInt(opponentSlot), null);
		} catch (ReflectiveOperationException | RuntimeException ex) {
			return false;
		}
	}

	/** Stand-ins for Phantasmon's virtual items (Z-Crystals, masks…): named like them, their icon drawn instead. */
	private static final java.util.Map<net.minecraft.world.item.ItemStack, net.minecraft.resources.ResourceLocation> VIRTUAL_ICONS = new java.util.WeakHashMap<>();
	private static final java.util.Map<com.cobblemon.mod.common.pokemon.Pokemon, net.minecraft.world.item.ItemStack> VIRTUAL_STACKS = new java.util.WeakHashMap<>();

	/** The item Battle Extras shows for a Pokémon holding a virtual item, or null: its own reading. */
	public static net.minecraft.world.item.ItemStack virtualItem(com.cobblemon.mod.common.pokemon.Pokemon pokemon) {
		String held = com.mystaria.phantasmon.client.battle.GhostVirtualItems.held(pokemon);
		if (held == null) {
			return null;
		}
		return VIRTUAL_STACKS.computeIfAbsent(pokemon, ignored -> {
			net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
			String name = com.mystaria.phantasmon.client.pokemon.VirtualItems.name(held);
			stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
					net.minecraft.network.chat.Component.literal(name == null ? held : name));
			net.minecraft.resources.ResourceLocation icon = com.mystaria.phantasmon.client.pokemon.VirtualItems.icon(held);
			if (icon != null) {
				VIRTUAL_ICONS.put(stack, icon);
			}
			return stack;
		});
	}

	/** Draws a virtual item's icon in place of the stand-in; false when {@code stack} isn't one. */
	public static boolean drawVirtualItem(net.minecraft.client.gui.GuiGraphics g, net.minecraft.world.item.ItemStack stack, int x, int y) {
		net.minecraft.resources.ResourceLocation icon = VIRTUAL_ICONS.get(stack);
		if (icon == null) {
			return false;
		}
		g.blit(icon, x, y, 16, 16, 0, 0, 32, 32, 32, 32);
		return true;
	}

	/**
	 * The player's own team in a Phantasmon battle (Adrien 2026-11-08): Battle Extras reads the Cobblemon party, which
	 * isn't the team fighting, so its left column showed the wrong Pokémon.
	 */
	public static void ownTeam(java.util.List<com.mystaria.phantasmon.client.pokemon.PokemonDto> team) {
		ownTeam = team == null ? java.util.List.of() : java.util.List.copyOf(team);
		ownModels = java.util.List.of();
	}

	/** What Battle Extras takes for the local party (its {@code LocalBattlePartyTracker}), or null: its own reading. */
	public static java.util.List<com.cobblemon.mod.common.pokemon.Pokemon> ownParty() {
		if (!phantasmonBattle || ownTeam.isEmpty()) {
			return null;
		}
		if (ownModels.isEmpty()) {
			java.util.List<com.cobblemon.mod.common.pokemon.Pokemon> models = new java.util.ArrayList<>();
			for (com.mystaria.phantasmon.client.pokemon.PokemonDto dto : ownTeam) {
				try {
					com.cobblemon.mod.common.pokemon.Pokemon pokemon = com.mystaria.phantasmon.client.battle.GhostBattlePokemonFactory.create(dto);
					if (pokemon != null) {
						models.add(pokemon);
					}
				} catch (RuntimeException ex) {
					LOG.warn("Own team slot {} can't be shown in Battle Extras", dto.species(), ex);
				}
			}
			ownModels = models;
		}
		return ownModels;
	}

	/**
	 * The opponent's whole team, seen in the lobby by both players (Adrien 2026-11-08): Battle Extras gets it at once
	 * instead of discovering it one Pokémon at a time. {@code team}: species id and form name of each, in lobby order.
	 */
	public static void opponentTeam(java.util.UUID actor, java.util.List<String[]> team) {
		opponent = actor;
		opponentTeam = team == null ? java.util.List.of() : java.util.List.copyOf(team);
		opponentModels = java.util.List.of();
	}

	/** A Phantasmon battle begins on this client. */
	public static void phantasmonBattleStarted() {
		phantasmonBattle = true;
	}

	/** Every client tick: the calculator stays off while a Phantasmon battle is on, then comes back as it was. */
	public static void tick() {
		if (!PRESENT || broken) {
			return;
		}
		if (phantasmonBattle && CobblemonClient.INSTANCE.getBattle() == null) {
			// The battle is over: its opponent's team is forgotten (it arrives before the battle, so only here).
			phantasmonBattle = false;
			opponent = null;
			opponentModels = java.util.List.of();
			ownTeam = java.util.List.of();
			ownModels = java.util.List.of();
		}
		if (phantasmonBattle) {
			force();
			shareOpponentTeam();
		} else {
			if (forced) {
				restore();
			}
		}
	}

	/** Given again whenever Battle Extras has none for that actor (it clears its data when a battle starts). */
	private static void shareOpponentTeam() {
		if (opponent == null || opponentTeam.isEmpty() || teamBroken) {
			return;
		}
		try {
			Class<?> data = Class.forName("name.modid.client.net.TeamBallClientData");
			if ((boolean) data.getMethod("hasData", java.util.UUID.class).invoke(null, opponent)) {
				givePortraits();
				return;
			}
			Class<?> entry = Class.forName("name.modid.net.payload.PokemonTeamData");
			var make = entry.getConstructor(int.class, String.class, String.class, String.class, boolean.class);
			String ball = com.cobblemon.mod.common.api.pokeball.PokeBalls.getPokeBall().getModel2d().getPath();
			java.util.List<Object> team = new java.util.ArrayList<>();
			for (int i = 0; i < opponentTeam.size(); i++) {
				team.add(make.newInstance(i, opponentTeam.get(i)[0], opponentTeam.get(i)[1], ball, false));
			}
			data.getMethod("setTeamData", java.util.UUID.class, java.util.List.class).invoke(null, opponent, team);
			Method reveal = data.getMethod("markRevealed", java.util.UUID.class, int.class);
			for (int i = 0; i < team.size(); i++) {
				reveal.invoke(null, opponent, i);
			}
		} catch (ReflectiveOperationException | RuntimeException ex) {
			teamBroken = true;
			LOG.warn("Cobblemon Battle Extras changed: the opponent's team can't be shown from the start", ex);
		}
	}

	/** Each of the opponent's slots gets its Pokémon (species, form, shininess), so its portrait shows, not a ball. */
	private static void givePortraits() throws ReflectiveOperationException {
		if (opponentModels.isEmpty()) {
			java.util.List<com.cobblemon.mod.common.pokemon.Pokemon> models = new java.util.ArrayList<>();
			for (String[] member : opponentTeam) {
				com.cobblemon.mod.common.pokemon.Species species = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByName(member[0]);
				if (species == null) {
					models.add(null);
					continue;
				}
				com.cobblemon.mod.common.pokemon.Pokemon pokemon = new com.cobblemon.mod.common.pokemon.Pokemon();
				pokemon.setSpecies(species);
				com.cobblemon.mod.common.pokemon.FormData form = com.mystaria.phantasmon.client.gui.PokemonGuiRendering.resolveForm(species, member[1].isEmpty() ? null : member[1]);
				if (form != null) {
					pokemon.setForm(form);
				}
				pokemon.setShiny("shiny".equals(member.length > 2 ? member[2] : ""));
				models.add(pokemon);
			}
			opponentModels = models;
		}
		Class<?> renderer = Class.forName("name.modid.client.OpponentInfoSideRenderer");
		Field slotsField = renderer.getDeclaredField("OPPONENT_SLOTS");
		slotsField.setAccessible(true);
		Class<?> slot = Class.forName("name.modid.client.OpponentInfoSideRenderer$OpponentSlotData");
		Field actor = slot.getDeclaredField("actorUuid");
		Field index = slot.getDeclaredField("teamSlotIndex");
		Field roster = slot.getDeclaredField("rosterPokemon");
		Method capture = slot.getDeclaredMethod("captureFromRoster", com.cobblemon.mod.common.pokemon.Pokemon.class, String.class);
		for (Field f : new Field[] { actor, index, roster }) {
			f.setAccessible(true);
		}
		capture.setAccessible(true);
		for (Object data : ((java.util.Map<?, ?>) slotsField.get(null)).values()) {
			if (!opponent.equals(actor.get(data)) || roster.get(data) != null) {
				continue;
			}
			int i = index.getInt(data);
			if (i >= 0 && i < opponentModels.size() && opponentModels.get(i) != null) {
				capture.invoke(data, opponentModels.get(i), opponentTeam.get(i)[0]);
			}
		}
	}

	private static void force() {
		try {
			if (!bind()) {
				return;
			}
			if (!forced) {
				saved = new boolean[fields.length];
				for (int i = 0; i < fields.length; i++) {
					saved[i] = fields[i].getBoolean(config);
				}
				forced = true;
			}
			// Every tick: its config screen could switch it back on mid-battle.
			for (Field field : fields) {
				field.setBoolean(config, false);
			}
		} catch (ReflectiveOperationException | RuntimeException ex) {
			fail(ex);
		}
	}

	private static void restore() {
		try {
			for (int i = 0; i < fields.length; i++) {
				fields[i].setBoolean(config, saved[i]);
			}
		} catch (ReflectiveOperationException | RuntimeException ex) {
			fail(ex);
		}
		forced = false;
	}

	private static boolean bind() throws ReflectiveOperationException {
		Class<?> type = Class.forName("name.modid.client.BattleExtrasConfig");
		Method get = type.getMethod("get");
		Object current = get.invoke(null);
		if (current == null) {
			return false;
		}
		if (current != config) {
			config = current;
			fields = new Field[DAMAGE_FIELDS.length];
			for (int i = 0; i < DAMAGE_FIELDS.length; i++) {
				fields[i] = type.getField(DAMAGE_FIELDS[i]);
			}
		}
		return true;
	}

	private static void fail(Exception ex) {
		broken = true;
		LOG.warn("Cobblemon Battle Extras changed: its damage calculator can't be switched off in Phantasmon battles", ex);
	}
}
