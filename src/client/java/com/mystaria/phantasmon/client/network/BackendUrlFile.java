package com.mystaria.phantasmon.client.network;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the backend URL from a JSON file ({@code "backend_url"}) — see {@link BackendConfig}. The file is created
 * with {@link #DEFAULT_URL} when missing; a missing, unreadable or invalid value falls back to the default with a
 * warning in the log. No Minecraft/Fabric dependency, so it is unit-tested.
 */
public final class BackendUrlFile {

	private static final Logger LOG = LoggerFactory.getLogger(BackendUrlFile.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	static final String DEFAULT_URL = "http://100.116.43.32:8080";
	private static final String KEY = "backend_url";

	private BackendUrlFile() {
	}

	static URI load(Path file) {
		if (!Files.exists(file)) {
			writeDefault(file);
			return URI.create(DEFAULT_URL);
		}
		try {
			JsonObject json = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
			String value = json == null || !json.has(KEY) || json.get(KEY).isJsonNull() ? null : json.get(KEY).getAsString();
			URI uri = parse(value);
			if (uri == null) {
				LOG.warn("Invalid '{}' in {}: {} — using {}", KEY, file, value, DEFAULT_URL);
				return URI.create(DEFAULT_URL);
			}
			LOG.info("Backend URL: {} (from {})", uri, file);
			return uri;
		} catch (IOException | JsonParseException | IllegalStateException | UnsupportedOperationException ex) {
			LOG.warn("Cannot read {} — using {}", file, DEFAULT_URL, ex);
			return URI.create(DEFAULT_URL);
		}
	}

	/**
	 * {@code http(s)://host[:port]}, or {@code null}. No path: every call resolves an absolute path
	 * ({@code BASE_URL.resolve("/version")}) and the WebSocket keeps only the authority, so a path would be dropped.
	 */
	static URI parse(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			URI uri = URI.create(value.trim().replaceAll("/+$", ""));
			String scheme = uri.getScheme();
			if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme) || uri.getHost() == null
					|| (uri.getRawPath() != null && !uri.getRawPath().isEmpty()) || uri.getRawQuery() != null) {
				return null;
			}
			return uri;
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static void writeDefault(Path file) {
		JsonObject json = new JsonObject();
		json.addProperty(KEY, DEFAULT_URL);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(json) + System.lineSeparator(), StandardCharsets.UTF_8);
			LOG.info("Created {} with the default backend URL {}", file, DEFAULT_URL);
		} catch (IOException ex) {
			LOG.warn("Cannot create {} — using {}", file, DEFAULT_URL, ex);
		}
	}
}
