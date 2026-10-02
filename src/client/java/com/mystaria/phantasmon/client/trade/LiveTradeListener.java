package com.mystaria.phantasmon.client.trade;

import java.util.Map;

/**
 * Receives the live trade S2C messages ({@code TradeInvite*} /
 * {@code TradeSession*}) from {@link com.mystaria.phantasmon.client.ghost.GhostSession},
 * which owns the single presence WebSocket. Always called on the client
 * thread.
 */
public interface LiveTradeListener {

	void onLiveTradeMessage(String type, Map<String, Object> data);

	/** The WebSocket went down (or the player left the world): any session in progress is gone server-side too. */
	void onConnectionLost();
}
