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
 * auth, and every authenticated domain since). Field names are converted
 * camelCase <-> snake_case automatically, matching the backend's global
 * Jackson {@code SNAKE_CASE} convention — DTOs use plain camelCase Java
 * fields, no per-field annotation needed, same idea as the backend side.
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
		return get(uri, null, responseType);
	}

	public <T> CompletableFuture<T> get(URI uri, String bearerToken, Class<T> responseType) {
		HttpRequest.Builder builder = baseBuilder(uri).GET();
		return send(authorize(builder, bearerToken), responseType);
	}

	public <T> CompletableFuture<T> post(URI uri, Object requestBody, Class<T> responseType) {
		return post(uri, requestBody, null, responseType);
	}

	public <T> CompletableFuture<T> post(URI uri, Object requestBody, String bearerToken, Class<T> responseType) {
		HttpRequest.Builder builder = baseBuilder(uri)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)));
		return send(authorize(builder, bearerToken), responseType);
	}

	/** {@code POST} with no request body (e.g. {@code /pokemon/{uuid}/clone}). */
	public <T> CompletableFuture<T> postNoBody(URI uri, String bearerToken, Class<T> responseType) {
		HttpRequest.Builder builder = baseBuilder(uri).POST(HttpRequest.BodyPublishers.noBody());
		return send(authorize(builder, bearerToken), responseType);
	}

	public <T> CompletableFuture<T> patch(URI uri, Object requestBody, String bearerToken, Class<T> responseType) {
		HttpRequest.Builder builder = baseBuilder(uri)
				.header("Content-Type", "application/json")
				.method("PATCH", HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)));
		return send(authorize(builder, bearerToken), responseType);
	}

	/** {@code GET} of a binary body (e.g. the Hub schematic), with a longer timeout than JSON calls. */
	public CompletableFuture<byte[]> getBytes(URI uri, String bearerToken) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
				.header("Accept", "application/octet-stream").GET();
		return httpClient.sendAsync(authorize(builder, bearerToken).build(), HttpResponse.BodyHandlers.ofByteArray())
				.thenApply(response -> {
					if (response.statusCode() >= 200 && response.statusCode() < 300) {
						return response.body();
					}
					throw new BackendApiException(response.statusCode(), "ERROR_UNKNOWN", null);
				});
	}

	/** {@code DELETE} answered with a JSON body. */
	public <T> CompletableFuture<T> delete(URI uri, String bearerToken, Class<T> responseType) {
		return send(authorize(baseBuilder(uri).DELETE(), bearerToken), responseType);
	}

	/** {@code DELETE} expecting {@code 204 No Content} — no response body to parse. */
	public CompletableFuture<Void> delete(URI uri, String bearerToken) {
		HttpRequest.Builder builder = baseBuilder(uri).DELETE();
		HttpRequest request = authorize(builder, bearerToken).build();
		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() >= 200 && response.statusCode() < 300) {
						return null;
					}
					throw toApiException(response);
				});
	}

	private static HttpRequest.Builder baseBuilder(URI uri) {
		return HttpRequest.newBuilder(uri)
				.timeout(TIMEOUT)
				.header("Accept", "application/json");
	}

	private static HttpRequest.Builder authorize(HttpRequest.Builder builder, String bearerToken) {
		return bearerToken == null ? builder : builder.header("Authorization", "Bearer " + bearerToken);
	}

	private <T> CompletableFuture<T> send(HttpRequest.Builder builder, Class<T> responseType) {
		return httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
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
