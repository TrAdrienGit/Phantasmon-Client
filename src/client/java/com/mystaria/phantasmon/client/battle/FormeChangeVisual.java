package com.mystaria.phantasmon.client.battle;

import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;

/**
 * A Mega Evolution or Primal Reversion in a Ghost battle, for both clients' world scene (Adrien 2026-10-05).
 * Cobblemon's engine only announces it ({@code MEGA_EVOLUTION} / {@code FORME_CHANGE} events) — on a Cobblemon Delta
 * server, Delta's server mod then gives the Pokémon its aspect; here the host does it ({@link GhostBattles}) and
 * relays this to the guest. {@code aspect} is what the model resolvers of the pack's resource pack (CCC) key on:
 * {@code mega}, {@code mega_x}, {@code mega_y}, {@code primal}.
 *
 * @param pnx     battle position ({@code p1a}...), see {@link BattleVisuals#entityAt}
 * @param uuid    the battle Pokémon's uuid
 * @param species species id, for the effect's colours
 * @param aspect  the aspect to add
 */
public record FormeChangeVisual(String pnx, String uuid, String species, String aspect) {

	/** Relayed to the guest inside a {@code BattlePacket}, next to the real Cobblemon packets. */
	public static final String PACKET_ID = "phantasmon:forme_change";

	private static final Gson GSON = new Gson();

	public boolean primal() {
		return "primal".equals(aspect);
	}

	public byte[] toBytes() {
		return GSON.toJson(this).getBytes(StandardCharsets.UTF_8);
	}

	public static FormeChangeVisual fromBytes(byte[] bytes) {
		return GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), FormeChangeVisual.class);
	}

	/**
	 * The Mega aspect from the Mega Stone held: {@code …ite x} → {@code mega_x}, {@code …ite y} → {@code mega_y}
	 * (Charizard, Mewtwo), anything else — including no stone (Rayquaza) — {@code mega}.
	 */
	public static String megaAspect(String heldItemPath) {
		if (heldItemPath != null && heldItemPath.contains("ite")) {
			if (heldItemPath.endsWith("x")) {
				return "mega_x";
			}
			if (heldItemPath.endsWith("y")) {
				return "mega_y";
			}
		}
		return "mega";
	}
}
