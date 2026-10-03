package com.mystaria.phantasmon.client.battle;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.net.NetworkPacket;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleSetTeamPokemonPacket;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * One player of a Ghost battle, inside Cobblemon's battle engine running on
 * the host client (Phase 9). Its {@link #getUuid() uuid} is the player's
 * Mojang uuid — that's how Cobblemon's client UI recognizes "my side" — but
 * it deliberately reports no {@link #getPlayerUUIDs() player uuids}: those
 * would make the engine look the player up as a {@code ServerPlayer} and,
 * on a LAN host, push real packets through the integrated server on top of
 * ours. Every packet the engine addresses to this player goes to
 * {@code sink} instead: Cobblemon's UI on this machine, or the backend relay
 * to the other player's.
 */
public class GhostBattleActor extends BattleActor {

	private final String playerName;
	private final Consumer<NetworkPacket<?>> sink;
	private boolean initializeSent;

	public GhostBattleActor(UUID playerUuid, String playerName, List<BattlePokemon> team, Consumer<NetworkPacket<?>> sink) {
		super(playerUuid, new java.util.ArrayList<>(team));
		this.playerName = playerName;
		this.sink = sink;
	}

	@Override
	public ActorType getType() {
		return ActorType.PLAYER;
	}

	@Override
	public MutableComponent getName() {
		return Component.literal(playerName);
	}

	@Override
	public MutableComponent nameOwned(String name) {
		return Component.translatable("cobblemon.battle.owned_pokemon", getName(), name);
	}

	/**
	 * Cobblemon's {@code InitializeInstruction} only sends {@link BattleInitializePacket} (what opens the battle
	 * screen) to its own final {@code PlayerBattleActor} class — never to us — right before every actor's
	 * {@link BattleSetTeamPokemonPacket}. So that packet is built and sent here, at that exact same moment, with
	 * the same constructor (first prototype test, 2026-10-03: without it the client got the team before any
	 * battle existed and nothing opened).
	 */
	/**
	 * At battle start Cobblemon only fills an actor's active slots through the Pokémon <em>entity</em> it sends out
	 * into the world ({@code SwitchInstruction}, "battle not started" branch); entity-less Ghost Pokémon were never
	 * placed, so the screen showed no Pokémon and no move could be chosen (second prototype test, 2026-10-03).
	 * Done here instead, for every actor of the battle, from what Showdown itself declared active in each actor's
	 * request ({@code details} = "species, &lt;uuid&gt;, ..."). Mid-battle switches already have an entity-less path
	 * in Cobblemon ({@code createNonEntitySwitch}).
	 */
	static void placeStartingPokemon(com.cobblemon.mod.common.api.battles.model.PokemonBattle battle) {
		for (BattleActor actor : battle.getActors()) {
			List<BattlePokemon> leads = leadsFromRequest(actor);
			if (leads.isEmpty()) {
				// The other actor's request may not have arrived yet when the first actor is initialized (third
				// prototype test: the AI's side stayed empty and it never acted). Without team preview Showdown
				// always leads with the first team members, in team order — same result, no need to wait.
				leads = actor.getPokemonList().subList(0, Math.min(actor.getActivePokemon().size(), actor.getPokemonList().size()));
			}
			for (int slot = 0; slot < leads.size() && slot < actor.getActivePokemon().size(); slot++) {
				var active = actor.getActivePokemon().get(slot);
				if (active.getBattlePokemon() == null) {
					active.setBattlePokemon(leads.get(slot));
				}
			}
		}
	}

	/** Leads declared by Showdown in the actor's request ({@code details} = "species, &lt;uuid&gt;, ..."), in slot order. */
	private static List<BattlePokemon> leadsFromRequest(BattleActor actor) {
		List<BattlePokemon> leads = new java.util.ArrayList<>();
		var request = actor.getRequest();
		if (request == null || request.getSide() == null) {
			return leads;
		}
		for (var showdownPokemon : request.getSide().getPokemon()) {
			String[] details = showdownPokemon.getDetails().split(",");
			if (!showdownPokemon.getActive() || details.length < 2) {
				continue;
			}
			UUID uuid = UUID.fromString(details[1].trim());
			actor.getPokemonList().stream().filter(candidate -> candidate.getUuid().equals(uuid)).findFirst().ifPresent(leads::add);
		}
		return leads;
	}

	@Override
	public void sendUpdate(NetworkPacket<?> packet) {
		super.sendUpdate(packet);
		if (packet instanceof BattleSetTeamPokemonPacket && !initializeSent) {
			initializeSent = true;
			placeStartingPokemon(getBattle());
			sink.accept(new BattleInitializePacket(getBattle(), getSide()));
		}
		sink.accept(packet);
	}
}
