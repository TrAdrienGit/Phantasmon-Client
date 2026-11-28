package com.mystaria.phantasmon.client.gui;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * A trainer's league badges, shown in the battle lobby beside their model (2026-11-28). They will come from Leagueway
 * (the league mod's API: a player's badges with their name and picture; route requested, not there yet). Until then,
 * a test file in the game folder stands in: {@code phantasmon/badges-mock.json},
 * {@code {"<player name or uuid>": [{"texture": "<texture id>", "name": "...", "leader": "...", "type": "fire"}, ...]}}
 * (a bare texture id also works), each texture a square picture such as
 * {@code phantasmon:textures/gui/badges/mock_flame.png}. Read again every few seconds, so it can be edited while
 * playing.
 */
public final class TrainerBadges {

	private static final Logger LOG = LoggerFactory.getLogger(TrainerBadges.class);
	private static final long RELOAD_MS = 5_000;

	/** A badge: its picture (a texture of any square size), its name, its gym leader and its type (each may be null). */
	public record Badge(ResourceLocation texture, String name, String leader, String type) {
	}

	private static Map<String, List<Badge>> mock = Map.of();
	private static long loadedAt;

	private TrainerBadges() {
	}

	/** The badges of the player {@code uuid} / {@code name}, in the order they were won; empty if none known. */
	public static List<Badge> of(UUID uuid, String name) {
		reload();
		List<Badge> badges = uuid == null ? null : mock.get(uuid.toString());
		if (badges == null && name != null) {
			badges = mock.get(name.toLowerCase(Locale.ROOT));
		}
		return badges == null ? List.of() : badges;
	}

	private static String text(JsonObject object, String key) {
		return object == null || !object.has(key) || object.get(key).isJsonNull() ? null : object.get(key).getAsString();
	}

	private static void reload() {
		long now = System.currentTimeMillis();
		if (now - loadedAt < RELOAD_MS) {
			return;
		}
		loadedAt = now;
		Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("phantasmon").resolve("badges-mock.json");
		if (!Files.isRegularFile(file)) {
			mock = Map.of();
			return;
		}
		Map<String, List<Badge>> read = new HashMap<>();
		try (Reader reader = Files.newBufferedReader(file)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
				List<Badge> badges = new ArrayList<>();
				JsonArray ids = entry.getValue().getAsJsonArray();
				for (JsonElement element : ids) {
					JsonObject badge = element.isJsonObject() ? element.getAsJsonObject() : null;
					String id = badge == null ? element.getAsString() : text(badge, "texture");
					ResourceLocation texture = id == null ? null : ResourceLocation.tryParse(id);
					if (texture != null) {
						badges.add(new Badge(texture, text(badge, "name"), text(badge, "leader"), text(badge, "type")));
					}
				}
				read.put(entry.getKey().toLowerCase(Locale.ROOT), badges);
			}
		} catch (Exception ex) {
			LOG.warn("Badge test file {} not read: {}", file, ex.toString());
		}
		mock = read;
	}
}
