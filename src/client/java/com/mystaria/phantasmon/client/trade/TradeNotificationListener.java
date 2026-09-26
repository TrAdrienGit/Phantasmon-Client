package com.mystaria.phantasmon.client.trade;

import java.util.Map;

/**
 * Receives the backend's {@code TradeProposed}/{@code TradeAccepted}/{@code TradeCancelled}
 * WS events (CAD Phase 8: "notifications visuelles/i18n"). Registered on
 * {@link com.mystaria.phantasmon.client.ghost.GhostSession}, which owns the
 * one shared presence WebSocket connection — trade notifications ride the
 * same socket as Ghost/presence traffic, no second connection.
 */
public interface TradeNotificationListener {

	void onTradeProposed(Map<String, Object> data);

	void onTradeAccepted(Map<String, Object> data);

	void onTradeCancelled(Map<String, Object> data);
}
