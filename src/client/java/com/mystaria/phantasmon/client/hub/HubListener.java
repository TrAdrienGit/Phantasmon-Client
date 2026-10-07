package com.mystaria.phantasmon.client.hub;

import java.util.Map;

/** Global Hub WebSocket messages ({@code Hub*}), delivered on the client thread by the presence connection. */
public interface HubListener {

	void onHubMessage(String type, Map<String, Object> data);

	/** The presence connection dropped: the backend took the player out of the Hub. */
	void onConnectionLost();
}
