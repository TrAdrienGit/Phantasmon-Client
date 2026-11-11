package com.mystaria.phantasmon.client.pokemon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Which PC boxes are "team" boxes (TODO-36, Adrien 2026-11-11): a team box shows its rows apart, each row a team, and
 * a double click on a row swaps it with the player's team; the others stay "loose" boxes, sorted at will. Only how a
 * box looks and behaves here: kept in {@code config/phantasmon-pc-boxes.json}, per PC owner, nothing on the backend.
 */
public final class PcBoxTypes {

	private static final Logger LOG = LoggerFactory.getLogger(PcBoxTypes.class);
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("phantasmon-pc-boxes.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Map<String, Set<Integer>> teamBoxes;

	private PcBoxTypes() {
	}

	public static boolean isTeamBox(UUID owner, int box) {
		return boxes(owner).contains(box);
	}

	/** Flips the box's type; the new one: true for a team box. */
	public static boolean toggle(UUID owner, int box) {
		Set<Integer> boxes = boxes(owner);
		boolean team = !boxes.remove(box);
		if (team) {
			boxes.add(box);
		}
		save();
		return team;
	}

	private static Set<Integer> boxes(UUID owner) {
		if (teamBoxes == null) {
			teamBoxes = load();
		}
		return teamBoxes.computeIfAbsent(String.valueOf(owner), key -> new TreeSet<>());
	}

	private static Map<String, Set<Integer>> load() {
		Map<String, Set<Integer>> loaded = new HashMap<>();
		if (!Files.exists(FILE)) {
			return loaded;
		}
		try {
			JsonObject json = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonObject.class);
			if (json != null) {
				for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
					Set<Integer> boxes = new TreeSet<>();
					entry.getValue().getAsJsonArray().forEach(box -> boxes.add(box.getAsInt()));
					loaded.put(entry.getKey(), boxes);
				}
			}
		} catch (IOException | RuntimeException ex) {
			LOG.warn("Unreadable {}: every box is a loose box", FILE, ex);
		}
		return loaded;
	}

	private static void save() {
		JsonObject json = new JsonObject();
		teamBoxes.forEach((owner, boxes) -> {
			JsonArray array = new JsonArray();
			boxes.forEach(array::add);
			json.add(owner, array);
		});
		try {
			Files.writeString(FILE, GSON.toJson(json), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			LOG.warn("Cannot save {}", FILE, ex);
		}
	}
}
