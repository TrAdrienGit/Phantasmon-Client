package com.mystaria.phantasmon.client.network;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * Minimal JSON REST helper shared by every backend call (version handshake,
 * auth, and future domains). Field names are converted camelCase <->
 * snake_case automatically, matching the backend's global Jackson
 * {@code SNAKE_CASE} convention — DTOs use plain camelCase Java fields, no
 * per-field annotation needed, same idea as the backend side.
 */
public final class BackendJsonClient {

	private static final Duration TIMEOUT = Duration.ofSeconds(5);
	private static final Gson GSON = new GsonBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.create();

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(TIMEOUT)
			.build();

	public <T> CompletableFuture<T> get(URI uri, Class<T> responseType) {
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(TIMEOUT)
				.header("Accept", "application/json")
				.GET()
				.build();
		return send(request, responseType);
	}

	public <T> CompletableFuture<T> post(URI uri, Object requestBody, Class<T> responseType) {
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(TIMEOUT)
				.header("Accept", "application/json")
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
				.build();
		return send(request, responseType);
	}

	public <T> CompletableFuture<T> post(URI uri, Object requestBody, String bearerToken, Class<T> responseType) {
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(TIMEOUT)
				.header("Accept", "application/json")
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + bearerToken)
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
				.build();
		return send(request, responseType);
	}

	private <T> CompletableFuture<T> send(HttpRequest request, Class<T> responseType) {
		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() >= 200 && response.statusCode() < 300) {
						return GSON.fromJson(response.body(), responseType);
					}
					throw toApiException(response);
				});
	}

	private static BackendApiException toApiException(HttpResponse<String> response) {
		try {
			ErrorResponseDto error = GSON.fromJson(response.body(), ErrorResponseDto.class);
			return new BackendApiException(response.statusCode(), error == null ? null : error.errorCode(),
					error == null ? null : error.details());
		} catch (JsonSyntaxException ex) {
			return new BackendApiException(response.statusCode(), "ERROR_UNKNOWN", null);
		}
	}
}
