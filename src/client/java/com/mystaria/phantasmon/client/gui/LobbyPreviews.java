package com.mystaria.phantasmon.client.gui;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Preview pictures of the lobby's choices (2026-11-27): a League Reborn field, a scene (preset or stadium), an
 * ambiance, an intro. Drawn behind their card in the choice windows so their look is seen at a glance.
 *
 * <p>Looked up in the game folder first ({@code phantasmon/previews/<kind>/<id>.png}, what {@code /phantasmon preview}
 * captures), then in the mod's resources ({@code assets/phantasmon/textures/gui/previews/<kind>/<id>.png}). A namespaced
 * id ({@code ns:name}) is the sub-folder {@code ns/name}. No picture: the card draws a plain backdrop.
 */
public final class LobbyPreviews {

	private static final Logger LOG = LoggerFactory.getLogger(LobbyPreviews.class);

	/** A loaded picture: its texture and its size in pixels. */
	public record Preview(ResourceLocation texture, int width, int height) {
	}

	private static final Map<String, Optional<Preview>> CACHE = new HashMap<>();

	private LobbyPreviews() {
	}

	/** The picture of {@code id} of {@code kind} ("field", "scene", "ambiance", "intro"), or null. */
	public static Preview get(String kind, String id) {
		if (id == null || id.isEmpty() || "*".equals(id)) {
			return null;
		}
		String path = kind + "/" + path(id);
		return CACHE.computeIfAbsent(path, LobbyPreviews::load).orElse(null);
	}

	/** Forgets every picture, so new captures show (their textures are released). */
	public static void clear() {
		Minecraft minecraft = Minecraft.getInstance();
		CACHE.values().forEach(preview -> preview.ifPresent(p -> minecraft.getTextureManager().release(p.texture())));
		CACHE.clear();
	}

	/** Where a capture of {@code id} of {@code kind} is written. */
	public static Path file(String kind, String id) {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("phantasmon").resolve("previews")
				.resolve(kind).resolve(path(id) + ".png");
	}

	/** An id as a resource path: lower case, "ns:name" → "ns/name", anything else odd → "_". */
	static String path(String id) {
		return id.toLowerCase(Locale.ROOT).replace(':', '/').replaceAll("[^a-z0-9_./-]", "_");
	}

	private static Optional<Preview> load(String path) {
		Minecraft minecraft = Minecraft.getInstance();
		NativeImage image = null;
		try {
			Path file = minecraft.gameDirectory.toPath().resolve("phantasmon").resolve("previews").resolve(path + ".png");
			if (Files.isRegularFile(file)) {
				try (InputStream stream = Files.newInputStream(file)) {
					image = NativeImage.read(stream);
				}
			} else {
				ResourceLocation resource = ResourceLocation.fromNamespaceAndPath("phantasmon", "textures/gui/previews/" + path + ".png");
				var found = minecraft.getResourceManager().getResource(resource);
				if (found.isPresent()) {
					try (InputStream stream = found.get().open()) {
						image = NativeImage.read(stream);
					}
				}
			}
		} catch (Exception ex) {
			LOG.warn("Preview {} not loaded: {}", path, ex.toString());
		}
		if (image == null) {
			return Optional.empty();
		}
		ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("phantasmon", "preview/" + path);
		int width = image.getWidth();
		int height = image.getHeight();
		minecraft.getTextureManager().register(texture, new DynamicTexture(image));
		return Optional.of(new Preview(texture, width, height));
	}
}
