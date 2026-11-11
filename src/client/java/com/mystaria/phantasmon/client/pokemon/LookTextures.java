package com.mystaria.phantasmon.client.pokemon;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * The filters of a Pokémon's look (TODO-37): a copy of Cobblemon's texture with its colours changed, made once per
 * texture and filter, then drawn in its place ({@code VaryingModelRepositoryLookMixin}). Recolouring the texture
 * rather than tinting while drawing keeps it right with shaders (Iris): the copy is an ordinary texture. Render
 * thread only.
 */
public final class LookTextures {

	private static final Logger LOG = LoggerFactory.getLogger(LookTextures.class);
	/** original + filter → its copy (or the original itself when it couldn't be read). */
	private static final Map<String, ResourceLocation> COPIES = new ConcurrentHashMap<>();

	private LookTextures() {
	}

	/** {@code texture} through {@code filter}; the texture itself for an unknown filter or an unreadable texture. */
	public static ResourceLocation filtered(ResourceLocation texture, String filter) {
		if (texture == null || filter == null || !PokemonLook.FILTERS.contains(filter)) {
			return texture;
		}
		return COPIES.computeIfAbsent(texture + "|" + filter, key -> make(texture, filter));
	}

	private static ResourceLocation make(ResourceLocation texture, String filter) {
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
		if (resource.isEmpty()) {
			return texture;
		}
		try (InputStream in = resource.get().open()) {
			NativeImage image = NativeImage.read(in);
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					image.setPixelRGBA(x, y, recolor(image.getPixelRGBA(x, y), filter, y));
				}
			}
			ResourceLocation copy = ResourceLocation.fromNamespaceAndPath("phantasmon",
					"look/" + filter + "/" + texture.getNamespace() + "/" + texture.getPath());
			Minecraft.getInstance().getTextureManager().register(copy, new DynamicTexture(image));
			return copy;
		} catch (Exception ex) {
			LOG.warn("Look filter {} not applied to {}", filter, texture, ex);
			return texture;
		}
	}

	/** One pixel, NativeImage's ABGR, through the filter; {@code y}: its row (holographic scan lines). */
	static int recolor(int abgr, String filter, int y) {
		int a = abgr >>> 24;
		if (a == 0) {
			return abgr;
		}
		int b = (abgr >> 16) & 0xFF;
		int g = (abgr >> 8) & 0xFF;
		int r = abgr & 0xFF;
		float luminance = 0.299f * r + 0.587f * g + 0.114f * b;
		switch (filter) {
			case "black_white" -> {
				r = g = b = Math.round(luminance);
			}
			case "negative" -> {
				r = 255 - r;
				g = 255 - g;
				b = 255 - b;
			}
			case "obscure" -> {
				// Shadow Pokémon (Adrien 2026-11-11: darker): almost black, a deep purple left in the light parts;
				// the violet smoke of its shell does the rest.
				float grey = luminance * 0.22f;
				r = clamp(Math.round(grey * 0.9f + 10));
				g = clamp(Math.round(grey * 0.45f + 2));
				b = clamp(Math.round(grey * 1.25f + 22));
			}
			case "holographic" -> {
				// Cyan light, see-through (drawn translucent); the moving scan lines are its shell's.
				float light = 50 + luminance * 0.8f;
				r = clamp(Math.round(light * 0.3f));
				g = clamp(Math.round(light + 35));
				b = clamp(Math.round(light + 80));
				a = Math.min(a, 140);
			}
			default -> {
				return abgr;
			}
		}
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(255, value));
	}

	/** The shell a menu is drawing now (second pass of a Pokémon's model), or null. Render thread. */
	private static LookShells.Shell guiShell;

	public static void guiShell(LookShells.Shell shell) {
		guiShell = shell;
	}

	/**
	 * The render type a Pokémon texture takes ({@code RenderTypeLookMixin}): the shell's while a menu draws one, a
	 * translucent one for a holographic copy, else null (unchanged).
	 */
	public static net.minecraft.client.renderer.RenderType renderType(ResourceLocation texture) {
		if (guiShell != null) {
			return guiShell.type();
		}
		if (texture != null && "phantasmon".equals(texture.getNamespace()) && texture.getPath().startsWith("look/holographic/")) {
			return net.minecraft.client.renderer.RenderType.entityTranslucent(texture);
		}
		return null;
	}

	/** Resource packs reloaded (F3+T): the copies are made again from the new textures. */
	public static void clear() {
		COPIES.clear();
	}
}
