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

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single source of truth for the backend's base URL, read once at startup from {@code config/phantasmon.json}
 * ({@code "backend_url"}). The file is created on first launch with {@link #DEFAULT_URL}; editing it takes effect
 * at the next game launch. A missing, unreadable or invalid value (anything but an absolute {@code http}/{@code https}
 * URL) falls back to the default with a warning in the log.
 *
 * <p>The default is the dev machine's Tailscale IP (Adrien: 2026-09-29): both test clients — dev machine's
 * "MystAria_" and production-server's "TheMashen" — must reach the same single backend instance.
 */
public final class BackendConfig {

	private static final Logger LOG = LoggerFactory.getLogger(BackendConfig.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	static final String DEFAULT_URL = "http://100.116.43.32:8080";
	private static final String KEY = "backend_url";

	public static final URI BASE_URL = load(FabricLoader.getInstance().getConfigDir().resolve("phantasmon.json"));

	private BackendConfig() {
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

	/** An absolute http(s) URL with a host, without trailing slash, or {@code null}. */
	static URI parse(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			URI uri = URI.create(value.trim().replaceAll("/+$", ""));
			String scheme = uri.getScheme();
			if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme) || uri.getHost() == null) {
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
