package com.mystaria.phantasmon.client.battle;

import java.util.Map;

/**
 * Receives the live battle S2C messages ({@code BattleInvite*}, {@code BattleSession*}, {@code BattlePacket},
 * {@code BattleChoice}, {@code BattleTimerEnabled}, {@code BattleEnded}) from
 * {@link com.mystaria.phantasmon.client.ghost.GhostSession}, which owns the single presence WebSocket.
 * Always called on the client thread.
 */
public interface LiveBattleListener {

	void onLiveBattleMessage(String type, Map<String, Object> data);

	/** The WebSocket went down (or the player left the world): any battle in progress is over server-side too. */
	void onConnectionLost();
}
