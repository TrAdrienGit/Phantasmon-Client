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
 * The PC boxes' own settings, per PC owner (Adrien 2026-11-11): which are "team" boxes (TODO-36: a team box shows its
 * rows apart, each row a team, and a double click on a row swaps it with the player's team; the others stay "loose"
 * boxes) and their names (TODO-38: a format, a theme...). Only how a box looks here: kept in
 * {@code config/phantasmon-pc-boxes.json}, nothing on the backend.
 */
public final class PcBoxTypes {

	private static final Logger LOG = LoggerFactory.getLogger(PcBoxTypes.class);
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("phantasmon-pc-boxes.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Map<String, Set<Integer>> teamBoxes;
	private static Map<String, Map<Integer, String>> names;
	/** A box name's longest length. */
	public static final int NAME_MAX = 24;

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

	/** The box's name, or null if it has none. */
	public static String name(UUID owner, int box) {
		load();
		Map<Integer, String> owned = names.get(String.valueOf(owner));
		return owned == null ? null : owned.get(box);
	}

	/** Names the box; blank: its name goes. */
	public static void rename(UUID owner, int box, String name) {
		load();
		Map<Integer, String> owned = names.computeIfAbsent(String.valueOf(owner), key -> new java.util.TreeMap<>());
		String clean = name == null ? "" : name.strip();
		if (clean.isEmpty()) {
			owned.remove(box);
		} else {
			owned.put(box, clean.length() > NAME_MAX ? clean.substring(0, NAME_MAX) : clean);
		}
		save();
	}

	private static Set<Integer> boxes(UUID owner) {
		load();
		return teamBoxes.computeIfAbsent(String.valueOf(owner), key -> new TreeSet<>());
	}

	/**
	 * {@code {owner: {"team": [boxes], "names": {box: name}}}}; the first version's {@code {owner: [boxes]}} (team
	 * boxes only) is still read.
	 */
	private static void load() {
		if (teamBoxes != null) {
			return;
		}
		teamBoxes = new HashMap<>();
		names = new HashMap<>();
		if (!Files.exists(FILE)) {
			return;
		}
		try {
			JsonObject json = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonObject.class);
			if (json != null) {
				for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
					Set<Integer> boxes = new TreeSet<>();
					Map<Integer, String> boxNames = new java.util.TreeMap<>();
					if (entry.getValue().isJsonArray()) {
						entry.getValue().getAsJsonArray().forEach(box -> boxes.add(box.getAsInt()));
					} else {
						JsonObject owner = entry.getValue().getAsJsonObject();
						if (owner.has("team")) {
							owner.getAsJsonArray("team").forEach(box -> boxes.add(box.getAsInt()));
						}
						if (owner.has("names")) {
							owner.getAsJsonObject("names").entrySet()
									.forEach(name -> boxNames.put(Integer.parseInt(name.getKey()), name.getValue().getAsString()));
						}
					}
					teamBoxes.put(entry.getKey(), boxes);
					names.put(entry.getKey(), boxNames);
				}
			}
		} catch (IOException | RuntimeException ex) {
			LOG.warn("Unreadable {}: every box is a loose box, without a name", FILE, ex);
		}
	}

	private static void save() {
		JsonObject json = new JsonObject();
		java.util.Set<String> owners = new java.util.TreeSet<>(teamBoxes.keySet());
		owners.addAll(names.keySet());
		for (String owner : owners) {
			JsonObject entry = new JsonObject();
			JsonArray array = new JsonArray();
			teamBoxes.getOrDefault(owner, Set.of()).forEach(array::add);
			entry.add("team", array);
			JsonObject boxNames = new JsonObject();
			names.getOrDefault(owner, Map.of()).forEach((box, name) -> boxNames.addProperty(String.valueOf(box), name));
			entry.add("names", boxNames);
			json.add(owner, entry);
		}
		try {
			Files.writeString(FILE, GSON.toJson(json), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			LOG.warn("Cannot save {}", FILE, ex);
		}
	}
}
