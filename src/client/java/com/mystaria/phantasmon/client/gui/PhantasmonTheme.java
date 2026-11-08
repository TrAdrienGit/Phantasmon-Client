package com.mystaria.phantasmon.client.gui;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Phantasmon's look (Adrien 2026-11-08), chosen in its settings: {@link #V1}, the cyan-on-navy first look, or
 * {@link #COBBLEMON}, Cobblemon's own greys and teal as Showtime's menu uses them, to sit well beside Cobblemon and
 * Cobblemon Battle Extras. Stored in {@code config/phantasmon-ui.json}. Client thread.
 */
public enum PhantasmonTheme {

	V1("v1"), COBBLEMON("cobblemon");

	private static final Logger LOG = LoggerFactory.getLogger(PhantasmonTheme.class);
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("phantasmon-ui.json");

	private static PhantasmonTheme current;

	public final String id;

	PhantasmonTheme(String id) {
		this.id = id;
	}

	public static PhantasmonTheme current() {
		if (current == null) {
			current = load();
		}
		return current;
	}

	public static boolean cobblemon() {
		return current() == COBBLEMON;
	}

	/** The other look, saved; the open screen redraws with it. */
	public static PhantasmonTheme toggle() {
		current = current() == V1 ? COBBLEMON : V1;
		save();
		PhantasmonCanvasScreen.applyTheme();
		return current;
	}

	/** The folder of a screen texture for this look: {@code trade} or {@code trade_cobblemon}. */
	public String textureFolder() {
		return this == COBBLEMON ? "trade_cobblemon" : "trade";
	}

	private static PhantasmonTheme load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				String id = json.has("theme") ? json.get("theme").getAsString() : "";
				for (PhantasmonTheme theme : values()) {
					if (theme.id.equals(id)) {
						return theme;
					}
				}
			} catch (IOException | RuntimeException ex) {
				LOG.warn("Unreadable {}: the Cobblemon look", FILE, ex);
			}
		}
		return COBBLEMON;
	}

	private static void save() {
		JsonObject json = new JsonObject();
		json.addProperty("theme", current.id);
		try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
			writer.write(json.toString());
		} catch (IOException ex) {
			LOG.warn("Cannot save {}", FILE, ex);
		}
	}
}
