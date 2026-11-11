package com.mystaria.phantasmon.client.pokemon;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/**
 * The glowing shells of a Pokémon's look (TODO-37, redone 2026-11-11 after particles were judged not worthy of a
 * reward): the Pokémon's own model drawn again over itself in additive light with a moving pattern — Minecraft's own
 * way of drawing a charged creeper's aura ({@link RenderType#energySwirl}), so it holds with shaders. Super Saiyan:
 * golden flames rising; electric: blue-white arcs jumping; holographic: scan lines scrolling down; shadow: a slow
 * violet smoke. The patterns are generated here once, tiling. Render thread only.
 */
public final class LookShells {

	/** What a shell needs to be drawn: its pattern scrolled to now, its colour (additive: brightness) and its size. */
	public record Shell(RenderType type, int color, float inflate) {
	}

	private static final int SIZE = 64;
	private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

	private LookShells() {
	}

	/** The shell of {@code look} at this moment, or null if it has none. An aura's wins over a filter's. */
	public static Shell of(PokemonLook look) {
		if (look == null) {
			return null;
		}
		double t = (System.currentTimeMillis() % 1_000_000L) / 1000.0;
		if ("super_saiyan".equals(look.aura())) {
			float pulse = 0.85f + 0.15f * (float) Math.sin(t * 7.0);
			return new Shell(RenderType.energySwirl(texture("flames"), (float) (Math.sin(t * 1.3) * 0.05), (float) (t * 0.9 % 1.0)),
					shade(1.0f * pulse, 0.78f * pulse, 0.25f * pulse), 1.06f);
		}
		if ("electric".equals(look.aura())) {
			// The arcs jump: a new offset about twelve times a second, and a brief dark gap now and then.
			long step = (long) (t * 12);
			float u = (float) ((step * 0.37) % 1.0);
			float v = (float) ((step * 0.61) % 1.0);
			float on = step % 9 == 0 ? 0.25f : 1f;
			return new Shell(RenderType.energySwirl(texture("arcs"), u, v), shade(0.55f * on, 0.85f * on, 1.0f * on), 1.04f);
		}
		if ("holographic".equals(look.filter())) {
			// Scan lines scrolling down, the light flickering a little and, now and then, a glitch.
			long glitch = (long) (t * 9);
			float flicker = glitch % 23 == 0 ? 0.35f : 0.75f + 0.25f * (float) Math.sin(t * 31.0);
			return new Shell(RenderType.energySwirl(texture("scanlines"), 0f, (float) (-t * 0.35 % 1.0)),
					shade(0.25f * flicker, 0.95f * flicker, 1.0f * flicker), 1.01f);
		}
		if ("obscure".equals(look.filter())) {
			float breath = 0.75f + 0.25f * (float) Math.sin(t * 1.6);
			return new Shell(RenderType.energySwirl(texture("smoke"), (float) (t * 0.03 % 1.0), (float) (t * 0.12 % 1.0)),
					shade(0.42f * breath, 0.12f * breath, 0.62f * breath), 1.05f);
		}
		return null;
	}

	private static int shade(float r, float g, float b) {
		return FastColor.ARGB32.colorFromFloat(1f, clamp(r), clamp(g), clamp(b));
	}

	private static float clamp(float value) {
		return Math.max(0f, Math.min(1f, value));
	}

	private static ResourceLocation texture(String name) {
		return TEXTURES.computeIfAbsent(name, key -> {
			NativeImage image = new NativeImage(SIZE, SIZE, true);
			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) {
					image.setPixelRGBA(x, y, 0xFF000000);
				}
			}
			switch (key) {
				case "flames" -> flames(image);
				case "arcs" -> arcs(image);
				case "scanlines" -> scanlines(image);
				default -> smoke(image);
			}
			ResourceLocation location = ResourceLocation.fromNamespaceAndPath("phantasmon", "look/shell/" + key);
			Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
			return location;
		});
	}

	// ---------------------------------------------------------------- patterns (tiling, black = nothing: additive)

	/** Tall flame tongues: tiling noise stretched upward, gold, white-hot where it peaks. */
	private static void flames(NativeImage image) {
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double n = fbm(x / 6.0, y / 22.0, 4, 11);
				double tongue = Math.pow(Math.max(0, n - 0.35) / 0.65, 2.2);
				int r = (int) Math.min(255, 255 * tongue * 1.25);
				int g = (int) Math.min(255, 215 * tongue * 1.1);
				int b = (int) Math.min(255, 90 * Math.pow(tongue, 2.5) * 1.4);
				put(image, x, y, r, g, b);
			}
		}
	}

	/** Jagged lightning bolts with a white core and a blue glow. */
	private static void arcs(NativeImage image) {
		java.util.Random random = new java.util.Random(7);
		float[][] glow = new float[SIZE][SIZE];
		for (int bolt = 0; bolt < 7; bolt++) {
			double x = random.nextInt(SIZE);
			double y = random.nextInt(SIZE);
			double angle = random.nextDouble() * Math.PI * 2;
			for (int step = 0; step < 70; step++) {
				angle += (random.nextDouble() - 0.5) * 1.6;
				x += Math.cos(angle);
				y += Math.sin(angle);
				for (int dy = -2; dy <= 2; dy++) {
					for (int dx = -2; dx <= 2; dx++) {
						int px = Math.floorMod((int) Math.round(x) + dx, SIZE);
						int py = Math.floorMod((int) Math.round(y) + dy, SIZE);
						float d = (float) Math.sqrt(dx * dx + dy * dy);
						glow[py][px] = Math.max(glow[py][px], d < 0.5f ? 1f : d < 1.5f ? 0.55f : 0.18f);
					}
				}
			}
		}
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				float v = glow[y][x];
				int white = v >= 1f ? 255 : 0;
				put(image, x, y, Math.max(white, (int) (120 * v)), Math.max(white, (int) (200 * v)), (int) (255 * v));
			}
		}
	}

	/** Thin bright lines every eight pixels, a soft halo around each. */
	private static void scanlines(NativeImage image) {
		for (int y = 0; y < SIZE; y++) {
			int phase = y % 8;
			double v = phase == 0 ? 1.0 : phase == 1 || phase == 7 ? 0.35 : 0.06;
			for (int x = 0; x < SIZE; x++) {
				put(image, x, y, (int) (90 * v), (int) (240 * v), (int) (255 * v));
			}
		}
	}

	/** Slow, soft smoke. */
	private static void smoke(NativeImage image) {
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double n = fbm(x / 14.0, y / 14.0, 5, 3);
				double v = Math.pow(Math.max(0, n - 0.3) / 0.7, 1.6);
				put(image, x, y, (int) (170 * v), (int) (60 * v), (int) (230 * v));
			}
		}
	}

	private static void put(NativeImage image, int x, int y, int r, int g, int b) {
		image.setPixelRGBA(x, y, 0xFF000000 | (Math.min(255, b) << 16) | (Math.min(255, g) << 8) | Math.min(255, r));
	}

	/** Tiling fractal value noise over the 64-pixel tile, in [0, 1]. */
	private static double fbm(double x, double y, int octaves, int seed) {
		double sum = 0;
		double amplitude = 0.5;
		double frequency = 1;
		double norm = 0;
		for (int i = 0; i < octaves; i++) {
			int period = Math.max(1, (int) Math.round(SIZE / 6.0 * frequency));
			sum += amplitude * valueNoise(x * frequency, y * frequency, seed + i, period);
			norm += amplitude;
			amplitude *= 0.5;
			frequency *= 2;
		}
		return sum / norm;
	}

	private static double valueNoise(double x, double y, int seed, int period) {
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		double fx = x - x0;
		double fy = y - y0;
		double sx = fx * fx * (3 - 2 * fx);
		double sy = fy * fy * (3 - 2 * fy);
		double a = hash(x0, y0, seed, period);
		double b = hash(x0 + 1, y0, seed, period);
		double c = hash(x0, y0 + 1, seed, period);
		double d = hash(x0 + 1, y0 + 1, seed, period);
		return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy;
	}

	private static double hash(int x, int y, int seed, int period) {
		int px = Math.floorMod(x, period);
		int py = Math.floorMod(y, Math.max(1, period * 3));
		long h = px * 374761393L + py * 668265263L + seed * 2147483647L;
		h = (h ^ (h >>> 13)) * 1274126177L;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
	}
}
