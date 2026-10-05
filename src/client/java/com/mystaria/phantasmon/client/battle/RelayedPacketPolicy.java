package com.mystaria.phantasmon.client.battle;

/**
 * Which packets a Ghost battle guest accepts from the host (SEC-2, security audit 2026-10-04). The host's client
 * may be modified, so only what a battle needs is ever decoded and handed to Cobblemon: Cobblemon's battle packets
 * (every packet its battle engine sends to an actor is named {@code cobblemon:battle_*}) and Phantasmon's own move
 * animation and forme change (Mega / Primal) events. Anything else (party, PC, player data, GUIs...) is dropped before decoding.
 */
public final class RelayedPacketPolicy {

	private static final String COBBLEMON_BATTLE_PREFIX = "cobblemon:battle_";

	private RelayedPacketPolicy() {
	}

	public static boolean guestAccepts(String packetId) {
		return packetId != null
				&& (packetId.startsWith(COBBLEMON_BATTLE_PREFIX) || ActionEffectEvent.PACKET_ID.equals(packetId)
						|| FormeChangeVisual.PACKET_ID.equals(packetId));
	}
}
