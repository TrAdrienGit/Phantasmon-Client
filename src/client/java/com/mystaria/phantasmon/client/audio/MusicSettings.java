package com.mystaria.phantasmon.client.audio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;

/**
 * Which music files of the resource pack the player has unticked (Adrien 2026-10-05: everyone keeps the tracks they
 * like). Stored per player in {@code config/phantasmon-music.json}; a file not listed is on, so new files dropped in
 * the pack play straight away.
 */
public final class MusicSettings {

	private static final Logger LOG = LoggerFactory.getLogger(MusicSettings.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Set<String> disabled;

	private record Stored(Set<String> disabled) {
	}

	private MusicSettings() {
	}

	public static boolean enabled(ResourceLocation file) {
		return !disabled().contains(file.toString());
	}

	public static void setEnabled(ResourceLocation file, boolean enabled) {
		if (enabled) {
			disabled().remove(file.toString());
		} else {
			disabled().add(file.toString());
		}
		save();
	}

	private static Set<String> disabled() {
		if (disabled == null) {
			disabled = new TreeSet<>();
			Path path = path();
			if (Files.exists(path)) {
				try {
					Stored stored = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Stored.class);
					if (stored != null && stored.disabled() != null) {
						disabled.addAll(stored.disabled());
					}
				} catch (IOException | RuntimeException ex) {
					LOG.warn("Cannot read {}: all music files on", path, ex);
				}
			}
		}
		return disabled;
	}

	private static void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(new Stored(disabled)), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			LOG.warn("Cannot save {}", path, ex);
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("phantasmon-music.json");
	}
}
