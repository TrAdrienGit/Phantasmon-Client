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
 * Reads the backend URL from a JSON file ({@code "backend_url"}) — see {@link BackendConfig}. There is no default
 * address (2026-11-25, for publication: the mod must not connect anywhere the player did not choose): the file is
 * created with an empty value when missing, and a missing, empty, unreadable or invalid value means "no backend", with
 * a line in the log. No Minecraft/Fabric dependency, so it is unit-tested.
 */
public final class BackendUrlFile {

	private static final Logger LOG = LoggerFactory.getLogger(BackendUrlFile.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final String KEY = "backend_url";

	private BackendUrlFile() {
	}

	/** The backend's URL, or null when none is set (or the value is not a valid address). */
	static URI load(Path file) {
		if (!Files.exists(file)) {
			writeEmpty(file);
			return null;
		}
		try {
			JsonObject json = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
			String value = json == null || !json.has(KEY) || json.get(KEY).isJsonNull() ? null : json.get(KEY).getAsString();
			URI uri = parse(value);
			if (uri == null) {
				if (value == null || value.isBlank()) {
					LOG.info("No backend URL in {}: Phantasmon stays offline until one is set (Settings page)", file);
				} else {
					LOG.warn("Invalid '{}' in {}: {} — Phantasmon stays offline", KEY, file, value);
				}
				return null;
			}
			LOG.info("Backend URL: {} (from {})", uri, file);
			return uri;
		} catch (IOException | JsonParseException | IllegalStateException | UnsupportedOperationException ex) {
			LOG.warn("Cannot read {} — Phantasmon stays offline", file, ex);
			return null;
		}
	}

	/**
	 * {@code http(s)://host[:port]}, or {@code null}. No path: every call resolves an absolute path
	 * ({@code BASE_URL.resolve("/version")}) and the WebSocket keeps only the authority, so a path would be dropped.
	 */
	public static URI parse(String value) {
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

	private static void writeEmpty(Path file) {
		JsonObject json = new JsonObject();
		json.addProperty(KEY, "");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(json) + System.lineSeparator(), StandardCharsets.UTF_8);
			LOG.info("Created {} with no backend URL: set one on the Settings page", file);
		} catch (IOException ex) {
			LOG.warn("Cannot create {}", file, ex);
		}
	}

	/** Writes {@code url} as the backend URL (settings screen, which then uses it at once). False if it could not. */
	public static boolean save(Path file, URI url) {
		JsonObject json = new JsonObject();
		try {
			if (Files.exists(file)) {
				JsonObject existing = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
				if (existing != null) {
					json = existing; // other keys kept
				}
			}
		} catch (IOException | JsonParseException | IllegalStateException ex) {
			LOG.warn("Cannot read {} before saving: rewritten", file, ex);
		}
		json.addProperty(KEY, url.toString());
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(json) + System.lineSeparator(), StandardCharsets.UTF_8);
			LOG.info("Backend URL saved in {}: {}", file, url);
			return true;
		} catch (IOException ex) {
			LOG.warn("Cannot write {}", file, ex);
			return false;
		}
	}

	/** The URL written in the file now (a hand edit is not in use until the next launch); empty if none. */
	public static String saved(Path file) {
		try {
			JsonObject json = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
			if (json != null && json.has(KEY) && !json.get(KEY).isJsonNull()) {
				return json.get(KEY).getAsString();
			}
		} catch (IOException | JsonParseException | IllegalStateException | UnsupportedOperationException ex) {
			// none below
		}
		return "";
	}
}
