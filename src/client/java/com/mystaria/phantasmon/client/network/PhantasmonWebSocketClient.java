package com.mystaria.phantasmon.client.network;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin wrapper over {@link java.net.http.WebSocket} for {@code /ws?token=...}
 * (CAD Partie 2 §11). Encodes/decodes the generic {@code {"type":...,"data":{...}}}
 * envelope; callers only see already-parsed {@code (type, data)} pairs.
 */
public final class PhantasmonWebSocketClient {

	private static final Logger LOG = LoggerFactory.getLogger(PhantasmonWebSocketClient.class);

	private static final Gson GSON = new GsonBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.create();

	/** Matches the backend's {@code websocket.WsMessage} record. */
	private record Envelope(String type, Map<String, Object> data) {
	}

	public interface Listener {
		void onMessage(String type, Map<String, Object> data);

		void onClose();
	}

	private final HttpClient httpClient = HttpClient.newHttpClient();

	private volatile WebSocket webSocket;

	public CompletableFuture<Void> connect(URI baseUrl, String jwt, Listener listener) {
		URI wsUri = toWebSocketUri(baseUrl).resolve("/ws?token=" + jwt);
		return httpClient.newWebSocketBuilder()
				.buildAsync(wsUri, new EnvelopeListener(listener))
				.thenAccept(ws -> this.webSocket = ws);
	}

	public void send(String type, Map<String, Object> data) {
		WebSocket ws = webSocket;
		if (ws == null) {
			LOG.warn("Dropped WebSocket message '{}' — not connected yet (or connection failed)", type);
			return;
		}
		ws.sendText(GSON.toJson(new Envelope(type, data)), true);
	}

	public void close() {
		WebSocket ws = webSocket;
		webSocket = null;
		if (ws != null) {
			ws.sendClose(WebSocket.NORMAL_CLOSURE, "");
		}
	}

	private static URI toWebSocketUri(URI httpBaseUrl) {
		String scheme = "https".equals(httpBaseUrl.getScheme()) ? "wss" : "ws";
		return URI.create(scheme + "://" + httpBaseUrl.getAuthority());
	}

	/** Buffers partial text frames and parses a full envelope once a message is complete. */
	private static final class EnvelopeListener implements WebSocket.Listener {

		private final Listener listener;
		private final StringBuilder buffer = new StringBuilder();

		private EnvelopeListener(Listener listener) {
			this.listener = listener;
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			buffer.append(data);
			if (last) {
				String message = buffer.toString();
				buffer.setLength(0);
				dispatch(message);
			}
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			listener.onClose();
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			LOG.warn("WebSocket error", error);
			listener.onClose();
		}

		private void dispatch(String rawMessage) {
			try {
				Envelope envelope = GSON.fromJson(rawMessage, Envelope.class);
				if (envelope != null && envelope.type() != null) {
					listener.onMessage(envelope.type(), envelope.data() == null ? Map.of() : envelope.data());
				}
			} catch (JsonSyntaxException ex) {
				LOG.warn("Received malformed WebSocket message: {}", rawMessage, ex);
			}
		}
	}
}
