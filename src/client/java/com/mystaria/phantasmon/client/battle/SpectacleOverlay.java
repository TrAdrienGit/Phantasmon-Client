package com.mystaria.phantasmon.client.battle;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;

import com.mystaria.phantasmon.client.battle.BattleSpectacle.Kind;

/**
 * The 2D layer of {@link BattleSpectacle}, over everything ({@code GameRendererOverlayMixin}). The camera keeps the
 * Pokémon at the centre of the screen, so the effects are drawn round the centre: radial glows, rays, ribbons,
 * crystal facets and shards, built from coloured triangles (additive blending for light). Client thread only.
 */
public final class SpectacleOverlay {

	private SpectacleOverlay() {
	}

	public static void render(GuiGraphics g) {
		if (!BattleSpectacle.playing()) {
			return;
		}
		float t = BattleSpectacle.time();
		Kind kind = BattleSpectacle.kind();
		switch (kind) {
			case MEGA, PRIMAL -> mega(g, t, kind == Kind.PRIMAL);
			case Z_MOVE -> zMove(g, t);
			case TERA -> tera(g, t);
		}
	}

	// =====================================================================
	// Mega Evolution / Primal Reversion
	// =====================================================================

	private static void mega(GuiGraphics g, float t, boolean primal) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		float cx = w / 2f;
		float cy = h / 2f;
		float total = Kind.MEGA.seconds;
		float vis = Math.min(BattleSpectacle.smooth(t / 0.4f), 1f - BattleSpectacle.smooth((t - (total - 0.8f)) / 0.6f));
		boolean kyogre = "kyogre".equals(BattleSpectacle.species());
		int core = primal ? (kyogre ? 0x2A70FF : 0xFF3A18) : 0xC9A0FF;
		int ribbon = primal ? (kyogre ? 0x7FE8FF : 0xFFB040) : 0xFF4FD8;
		float shatter = BattleSpectacle.MEGA_SHATTER;

		Tris tris = Tris.begin(g, false);
		vignette(tris, w, h, 0x0B0618, 0.85f * vis, h * (t < shatter ? 0.22f : 0.32f));
		tris.end();

		Tris light = Tris.begin(g, true);
		if (t >= 0.4f && t < 1.7f) {
			// Motes drawn in from all round.
			for (int i = 0; i < 46; i++) {
				float delay = hash(i * 3 + 1) * 0.5f;
				float p = Mth.clamp((t - 0.4f - delay) / 0.8f, 0f, 1f);
				if (p <= 0f || p >= 1f) {
					continue;
				}
				float angle = hash(i * 3 + 2) * Mth.TWO_PI + p * 2.2f;
				float r0 = w * (0.45f + 0.35f * hash(i * 3 + 3));
				float r = r0 * (float) Math.pow(1 - p, 1.5);
				float size = 2.5f + 4f * hash(i * 5 + 7);
				light.disc(cx + Mth.cos(angle) * r, cy + Mth.sin(angle) * r, size * 2.4f, argb(0.9f, 0xDDE8FF), argb(0f, 0x8AA0FF), 10);
				light.disc(cx + Mth.cos(angle) * r, cy + Mth.sin(angle) * r, size * 0.7f, argb(1f, 0xFFFFFF), argb(0.6f, 0xFFFFFF), 8);
			}
		}
		if (t >= 1.2f && t < shatter + 0.05f) {
			// The cocoon, its ribbons, its flares.
			float grow = BattleSpectacle.easeOut((t - 1.2f) / 0.5f);
			float radius = h * 0.2f * grow * (1f + 0.03f * Mth.sin(t * 12f));
			float tint = BattleSpectacle.smooth((t - 2.5f) / 0.6f);
			int shell = BattleSpectacle.mix(0xFFFFFF, core, tint);
			light.disc(cx, cy, radius * 2.0f, argb(0.55f, shell), argb(0f, shell), 40);
			if (t >= 2.9f) {
				// Golden light leaking through the cracks.
				float k = (t - 2.9f) / (shatter - 2.9f);
				for (int i = 0; i < 9; i++) {
					float angle = hash(i * 7 + 40) * Mth.TWO_PI;
					light.wedge(cx, cy, angle, 0.05f + 0.04f * hash(i + 3), radius * 0.6f, radius * (1.2f + 1.4f * k),
							argb(0.9f * k, 0xFFE89A), argb(0f, 0xFFB030));
				}
			}
			if (t >= 2.4f) {
				float k = BattleSpectacle.smooth((t - 2.4f) / 0.4f);
				for (int i = 0; i < 14; i++) {
					float side = i % 2 == 0 ? -1f : 1f;
					float flicker = 0.75f + 0.25f * Mth.sin(t * 20f + i * 1.7f);
					float angle = -Mth.HALF_PI + side * (0.5f + 0.9f * hash(i * 11 + 5));
					int color = primal ? (i % 3 == 0 ? 0xFFFFFF : core) : BattleSpectacle.rainbow(hash(i * 13 + 1) + t * 0.3f);
					light.wedge(cx + side * radius * 0.6f, cy + radius * 0.7f, angle, 0.12f, 0f, radius * (0.8f + 0.9f * hash(i + 9)) * flicker * k,
							argb(0.7f * k, color), argb(0f, color));
				}
			}
		}
		light.end();

		if (t >= 1.2f && t < shatter + 0.05f) {
			float grow = BattleSpectacle.easeOut((t - 1.2f) / 0.5f);
			float radius = h * 0.2f * grow * (1f + 0.03f * Mth.sin(t * 12f));
			float tint = BattleSpectacle.smooth((t - 2.5f) / 0.6f);
			int shell = BattleSpectacle.mix(0xFFFFFF, core, tint);
			Tris solid = Tris.begin(g, false);
			solid.disc(cx, cy, radius, argb(0.97f, 0xFFFFFF), argb(0.92f, shell), 48);
			solid.end();
			Tris glow = Tris.begin(g, true);
			// Three ribbons sweeping round the shell, a bright head running along each.
			for (int k = 0; k < 3; k++) {
				float tilt = k * Mth.PI / 3f + t * 0.7f;
				int segments = 64;
				for (int s = 0; s < segments; s++) {
					float a0 = s * Mth.TWO_PI / segments;
					float a1 = (s + 1) * Mth.TWO_PI / segments;
					float head = 0.25f + 0.75f * (0.5f + 0.5f * Mth.cos(a0 - t * 6f - k * 2f));
					float[] p0 = ellipse(cx, cy, radius * 1.35f, radius * 0.32f, tilt, a0);
					float[] p1 = ellipse(cx, cy, radius * 1.35f, radius * 0.32f, tilt, a1);
					glow.line(p0[0], p0[1], p1[0], p1[1], radius * 0.07f, argb(0.85f * head, ribbon));
				}
			}
			if (t >= 2.9f) {
				float k = Mth.clamp((t - 2.9f) / (shatter - 2.9f), 0f, 1f);
				for (int i = 0; i < 7; i++) {
					float angle = hash(i * 7 + 40) * Mth.TWO_PI;
					float x = cx;
					float y = cy;
					for (int seg = 1; seg <= 4; seg++) {
						float rr = radius * k * seg / 4f;
						float jitter = (hash(i * 31 + seg) - 0.5f) * 0.5f;
						float nx = cx + Mth.cos(angle + jitter) * rr;
						float ny = cy + Mth.sin(angle + jitter) * rr;
						glow.line(x, y, nx, ny, 2.2f, argb(1f, 0xFFF4C0));
						x = nx;
						y = ny;
					}
				}
			}
			glow.end();
		}

		if (t >= shatter) {
			float s = t - shatter;
			Tris burst = Tris.begin(g, true);
			if (s < 1.4f) {
				float fade = 1f - BattleSpectacle.smooth((s - 0.6f) / 0.8f);
				for (int i = 0; i < 26; i++) {
					float angle = i * Mth.TWO_PI / 26 + s * 0.4f;
					burst.wedge(cx, cy, angle, 0.07f, 0f, w * (0.6f + 0.4f * hash(i + 77)),
							argb(0.55f * fade, primal ? core : 0xFFD860), argb(0f, 0xFFFFFF));
				}
			}
			if (s < 0.9f) {
				// The shell's shards flying off.
				for (int i = 0; i < 34; i++) {
					float angle = hash(i * 5 + 300) * Mth.TWO_PI;
					float speed = h * (0.6f + 0.9f * hash(i * 5 + 301));
					float d = speed * s;
					float size = h * (0.02f + 0.035f * hash(i * 5 + 302));
					burst.shard(cx + Mth.cos(angle) * d, cy + Mth.sin(angle) * d, size, s * 8f + i,
							argb(1f - s / 0.9f, i % 3 == 0 ? 0xFFFFFF : core));
				}
			}
			if (s >= 0.3f && s < 1.6f) {
				symbol(burst, g, cx, cy - h * 0.3f, h * 0.085f, s - 0.3f, primal, kyogre, core);
			}
			burst.end();
			if (s < 0.45f) {
				g.fill(0, 0, w, h, (Math.round(255 * (1f - s / 0.45f)) << 24) | 0xFFF8E8);
			}
			if (s >= 0.3f && s < 1.6f && primal) {
				// Groudon's Ω / Kyogre's α in the middle of the emblem.
				float pop = elastic((s - 0.3f) / 0.5f);
				String letter = kyogre ? "α" : "Ω";
				float scale = h * 0.085f * pop / 6f;
				var pose = g.pose();
				pose.pushPose();
				pose.translate(cx, cy - h * 0.3f, 0);
				pose.scale(scale, scale, 1f);
				var font = Minecraft.getInstance().font;
				g.drawString(font, letter, -font.width(letter) / 2, -4, 0xFFFFFFFF, true);
				pose.popPose();
			}
		}
	}

	/** The Mega symbol (rainbow ring, its rays, the white swirl) — or a red / blue ring for a Primal. */
	private static void symbol(Tris tris, GuiGraphics g, float x, float y, float size, float s, boolean primal, boolean kyogre, int core) {
		float pop = elastic(s / 0.5f);
		float fade = 1f - BattleSpectacle.smooth((s - 0.9f) / 0.4f);
		float r = size * pop;
		for (int i = 0; i < 36; i++) {
			float angle = i * Mth.TWO_PI / 36 + s * 0.8f;
			int color = primal ? (i % 2 == 0 ? core : 0xFFFFFF) : BattleSpectacle.rainbow(i / 36f);
			tris.wedge(x, y, angle, 0.06f, r * 0.9f, r * (3.2f + hash(i + 500)), argb(0.6f * fade, color), argb(0f, color));
		}
		tris.disc(x, y, r * 1.6f, argb(0.5f * fade, 0xFFFFFF), argb(0f, 0xFFFFFF), 32);
		int segments = 48;
		for (int i = 0; i < segments; i++) {
			float a0 = i * Mth.TWO_PI / segments;
			float a1 = (i + 1) * Mth.TWO_PI / segments;
			int c0 = primal ? core : BattleSpectacle.rainbow(i / (float) segments);
			int c1 = primal ? core : BattleSpectacle.rainbow((i + 1) / (float) segments);
			tris.quad(x + Mth.cos(a0) * r * 0.78f, y + Mth.sin(a0) * r * 0.78f, argb(fade, c0),
					x + Mth.cos(a0) * r, y + Mth.sin(a0) * r, argb(fade, c0),
					x + Mth.cos(a1) * r, y + Mth.sin(a1) * r, argb(fade, c1),
					x + Mth.cos(a1) * r * 0.78f, y + Mth.sin(a1) * r * 0.78f, argb(fade, c1));
		}
		tris.disc(x, y, r * 0.78f, argb(fade, 0xFFFFFF), argb(fade, primal ? core : 0x8040D0), 32);
		if (!primal) {
			// The swirl: two half-circles making an S.
			arc(tris, x, y - r * 0.24f, r * 0.24f, Mth.HALF_PI, Mth.HALF_PI + Mth.PI, r * 0.1f, argb(fade, 0xFFFFFF));
			arc(tris, x, y + r * 0.24f, r * 0.24f, -Mth.HALF_PI, Mth.HALF_PI, r * 0.1f, argb(fade, 0xFFFFFF));
		}
	}

	// =====================================================================
	// Z-Move
	// =====================================================================

	private static void zMove(GuiGraphics g, float t) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		float cx = w / 2f;
		float cy = h / 2f;
		float total = Kind.Z_MOVE.seconds;
		float vis = Math.min(BattleSpectacle.smooth(t / 0.25f), 1f - BattleSpectacle.smooth((t - (total - 0.6f)) / 0.5f));
		float release = BattleSpectacle.Z_RELEASE;

		Tris tris = Tris.begin(g, false);
		vignette(tris, w, h, 0x1A0E00, 0.6f * vis, h * 0.3f);
		tris.end();

		Tris light = Tris.begin(g, true);
		float pulse = 0.75f + 0.25f * Mth.sin(t * 10f);
		vignette(light, w, h, 0xFFB000, 0.45f * vis * pulse, h * 0.28f);
		if (t >= 0.35f && t < release) {
			// Speed lines rushing to the centre.
			for (int i = 0; i < 70; i++) {
				float angle = hash(i * 3 + 900) * Mth.TWO_PI;
				float far = w * 0.75f;
				float phase = (hash(i * 3 + 901) + t * (1.2f + hash(i * 3 + 902))) % 1f;
				float outer = far * (1f - phase * 0.55f);
				float inner = outer - far * 0.18f;
				light.wedge(cx, cy, angle, 0.008f, inner, outer, argb(0f, 0xFFE070), argb(0.6f, 0xFFF4C0));
			}
		}
		if (t >= 0.6f && t < release + 0.1f) {
			float k = BattleSpectacle.easeOut((t - 0.6f) / 0.4f);
			// The Z-Crystal's emblem: two diamonds turning opposite ways, rings expanding out of it.
			rhombus(light, cx, cy, h * 0.34f * k, t * 1.2f, 3f, argb(0.85f, 0xFFD040));
			rhombus(light, cx, cy, h * 0.27f * k, -t * 1.7f, 2f, argb(0.7f, 0xFFF0A0));
			for (int i = 0; i < 3; i++) {
				float phase = ((t - 0.6f) / 0.8f + i / 3f) % 1f;
				ring(light, cx, cy, h * 0.6f * phase, h * 0.012f, argb(0.6f * (1f - phase), 0xFFE070), 48);
			}
		}
		if (t >= 1.0f && t < release + 0.1f) {
			float pop = elastic((t - 1.0f) / 0.45f);
			float size = h * 0.11f * pop;
			float x = cx;
			float y = cy - h * 0.3f;
			light.disc(x, y, size * 2.2f, argb(0.55f, 0xFFC020), argb(0f, 0xFF8000), 32);
			zGlyph(light, x, y, size, argb(1f, 0xFFF6C8), argb(1f, 0xFFB000));
		}
		if (t >= release) {
			float s = t - release;
			if (s < 0.7f) {
				ring(light, cx, cy, w * 1.1f * BattleSpectacle.easeOut(s / 0.7f), h * 0.05f, argb(0.9f * (1f - s / 0.7f), 0xFFF0B0), 64);
			}
			if (s < 1.0f) {
				float fade = 1f - s;
				for (int i = 0; i < 28; i++) {
					float angle = i * Mth.TWO_PI / 28 + hash(i + 50) * 0.2f;
					light.wedge(cx, cy, angle, 0.05f, 0f, w * (0.5f + 0.5f * hash(i + 60)), argb(0.7f * fade, 0xFFE070), argb(0f, 0xFFFFFF));
				}
			}
		}
		light.end();
		if (t >= release && t - release < 0.4f) {
			g.fill(0, 0, w, h, (Math.round(230 * (1f - (t - release) / 0.4f)) << 24) | 0xFFE8A0);
		}
	}

	/** A thick, slanted Z. */
	private static void zGlyph(Tris tris, float x, float y, float size, int light, int dark) {
		float half = size;
		float bar = size * 0.32f;
		float slant = size * 0.18f;
		tris.quad(x - half + slant, y - half, light, x + half + slant, y - half, light,
				x + half + slant * 0.6f, y - half + bar, dark, x - half + slant * 0.6f, y - half + bar, dark);
		tris.quad(x - half - slant * 0.6f, y + half - bar, light, x + half - slant * 0.6f, y + half - bar, light,
				x + half - slant, y + half, dark, x - half - slant, y + half, dark);
		tris.quad(x + half + slant * 0.6f - bar * 1.3f, y - half + bar, light, x + half + slant * 0.6f, y - half + bar, light,
				x - half - slant * 0.6f + bar * 1.3f, y + half - bar, dark, x - half - slant * 0.6f, y + half - bar, dark);
	}

	// =====================================================================
	// Terastallization
	// =====================================================================

	private static void tera(GuiGraphics g, float t) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		float cx = w / 2f;
		float cy = h / 2f;
		float total = Kind.TERA.seconds;
		float vis = Math.min(BattleSpectacle.smooth(t / 0.4f), 1f - BattleSpectacle.smooth((t - (total - 0.8f)) / 0.6f));
		int col = BattleSpectacle.color();
		int dark = BattleSpectacle.mix(col, 0x000000, 0.8f);
		float shatter = BattleSpectacle.TERA_SHATTER;

		Tris tris = Tris.begin(g, false);
		vignette(tris, w, h, dark, 0.8f * vis, h * (t < shatter ? 0.22f : 0.3f));
		if (t >= 0.6f && t < shatter) {
			// The crystal: a hexagon of facets with pointed facets round it, translucent.
			float radius = h * 0.23f * BattleSpectacle.easeOut((t - 0.6f) / 1.0f);
			for (int i = 0; i < 6; i++) {
				float a0 = i * Mth.TWO_PI / 6 - Mth.HALF_PI;
				float a1 = (i + 1) * Mth.TWO_PI / 6 - Mth.HALF_PI;
				float am = (a0 + a1) / 2;
				float shine = 0.5f + 0.5f * Mth.sin(t * 6f + i * 1.3f);
				int facet = BattleSpectacle.mix(col, 0xFFFFFF, 0.15f + 0.35f * shine);
				tris.tri(cx, cy, argb(0.3f, 0xFFFFFF), cx + Mth.cos(a0) * radius, cy + Mth.sin(a0) * radius, argb(0.55f, facet),
						cx + Mth.cos(a1) * radius, cy + Mth.sin(a1) * radius, argb(0.55f, facet));
				int outer = BattleSpectacle.mix(col, 0x000000, 0.2f * shine);
				tris.tri(cx + Mth.cos(a0) * radius, cy + Mth.sin(a0) * radius, argb(0.6f, outer),
						cx + Mth.cos(am) * radius * 1.32f, cy + Mth.sin(am) * radius * 1.32f, argb(0.7f, facet),
						cx + Mth.cos(a1) * radius, cy + Mth.sin(a1) * radius, argb(0.6f, outer));
			}
		}
		tris.end();

		Tris light = Tris.begin(g, true);
		if (t >= 0.4f && t < 1.9f) {
			// Shards rushing in.
			for (int i = 0; i < 28; i++) {
				float delay = hash(i * 3 + 600) * 0.6f;
				float p = Mth.clamp((t - 0.4f - delay) / 0.8f, 0f, 1f);
				if (p <= 0f || p >= 1f) {
					continue;
				}
				float angle = hash(i * 3 + 601) * Mth.TWO_PI + p;
				float r = w * 0.7f * (1f - BattleSpectacle.easeOut(p)) + h * 0.05f;
				light.shard(cx + Mth.cos(angle) * r, cy + Mth.sin(angle) * r, h * (0.025f + 0.03f * hash(i + 602)), t * 6f + i,
						argb(0.9f, i % 3 == 0 ? 0xFFFFFF : col));
			}
		}
		if (t >= 0.6f && t < shatter) {
			float radius = h * 0.23f * BattleSpectacle.easeOut((t - 0.6f) / 1.0f);
			light.disc(cx, cy, radius * 1.8f, argb(0.35f, col), argb(0f, col), 36);
			// Facet edges.
			for (int i = 0; i < 6; i++) {
				float a0 = i * Mth.TWO_PI / 6 - Mth.HALF_PI;
				float a1 = (i + 1) * Mth.TWO_PI / 6 - Mth.HALF_PI;
				float am = (a0 + a1) / 2;
				int edge = argb(0.8f, 0xFFFFFF);
				light.line(cx + Mth.cos(a0) * radius, cy + Mth.sin(a0) * radius, cx + Mth.cos(a1) * radius, cy + Mth.sin(a1) * radius, 1.5f, edge);
				light.line(cx, cy, cx + Mth.cos(a0) * radius, cy + Mth.sin(a0) * radius, 1f, argb(0.4f, 0xFFFFFF));
				light.line(cx + Mth.cos(a0) * radius, cy + Mth.sin(a0) * radius, cx + Mth.cos(am) * radius * 1.32f, cy + Mth.sin(am) * radius * 1.32f, 1.5f, edge);
				light.line(cx + Mth.cos(am) * radius * 1.32f, cy + Mth.sin(am) * radius * 1.32f, cx + Mth.cos(a1) * radius, cy + Mth.sin(a1) * radius, 1.5f, edge);
			}
			// A glint sweeping across.
			float sweep = ((t - 0.6f) % 0.9f) / 0.9f;
			float gx = cx - radius * 1.3f + sweep * radius * 2.6f;
			light.quad(gx - radius * 0.1f, cy - radius * 1.3f, argb(0f, 0xFFFFFF), gx + radius * 0.1f, cy - radius * 1.3f, argb(0.5f, 0xFFFFFF),
					gx - radius * 0.3f, cy + radius * 1.3f, argb(0.5f, 0xFFFFFF), gx - radius * 0.5f, cy + radius * 1.3f, argb(0f, 0xFFFFFF));
			if (t >= 2.0f) {
				float k = (t - 2.0f) / (shatter - 2.0f);
				for (int i = 0; i < 8; i++) {
					float angle = hash(i * 9 + 700) * Mth.TWO_PI;
					float x = cx;
					float y = cy;
					for (int seg = 1; seg <= 3; seg++) {
						float rr = radius * 1.2f * k * seg / 3f;
						float jitter = (hash(i * 17 + seg) - 0.5f) * 0.6f;
						float nx = cx + Mth.cos(angle + jitter) * rr;
						float ny = cy + Mth.sin(angle + jitter) * rr;
						light.line(x, y, nx, ny, 2f, argb(1f, 0xFFFFFF));
						x = nx;
						y = ny;
					}
				}
			}
		}
		if (t >= shatter) {
			float s = t - shatter;
			if (s < 1.0f) {
				for (int i = 0; i < 40; i++) {
					float angle = hash(i * 5 + 800) * Mth.TWO_PI;
					float d = h * (0.1f + (0.5f + 0.9f * hash(i * 5 + 801)) * s);
					light.shard(cx + Mth.cos(angle) * d, cy + Mth.sin(angle) * d, h * (0.025f + 0.04f * hash(i * 5 + 802)), s * 7f + i,
							argb(1f - s, i % 4 == 0 ? 0xFFFFFF : col));
				}
				ring(light, cx, cy, w * 0.9f * BattleSpectacle.easeOut(s / 0.6f), h * 0.03f, argb(0.8f * Math.max(0f, 1f - s / 0.6f), col), 56);
			}
			if (s >= 0.1f && s < 1.5f) {
				jewel(light, cx, cy - h * 0.3f, h * 0.07f, s - 0.1f, col);
			}
		}
		light.end();
		if (t >= shatter && t - shatter < 0.4f) {
			g.fill(0, 0, w, h, (Math.round(230 * (1f - (t - shatter) / 0.4f)) << 24) | BattleSpectacle.mix(col, 0xFFFFFF, 0.6f));
		}
	}

	/** The Tera jewel over the Pokémon's head: a cut gem in the type's colour, with sparkles. */
	private static void jewel(Tris tris, float x, float y, float size, float s, int col) {
		float pop = elastic(s / 0.45f);
		float fade = 1f - BattleSpectacle.smooth((s - 1.0f) / 0.4f);
		float r = size * pop;
		tris.disc(x, y, r * 2.4f, argb(0.5f * fade, col), argb(0f, col), 32);
		float[][] p = { { -1f, -0.35f }, { -0.5f, -0.8f }, { 0.5f, -0.8f }, { 1f, -0.35f }, { 0f, 1f } };
		int light = argb(fade, BattleSpectacle.mix(col, 0xFFFFFF, 0.55f));
		int mid = argb(fade, col);
		int deep = argb(fade, BattleSpectacle.mix(col, 0x000000, 0.35f));
		tris.tri(x + p[0][0] * r, y + p[0][1] * r, light, x + p[1][0] * r, y + p[1][1] * r, light, x, y - 0.35f * r, mid);
		tris.tri(x + p[1][0] * r, y + p[1][1] * r, light, x + p[2][0] * r, y + p[2][1] * r, light, x, y - 0.35f * r, light);
		tris.tri(x + p[2][0] * r, y + p[2][1] * r, light, x + p[3][0] * r, y + p[3][1] * r, mid, x, y - 0.35f * r, mid);
		tris.tri(x + p[0][0] * r, y + p[0][1] * r, mid, x, y - 0.35f * r, light, x + p[4][0] * r, y + p[4][1] * r, deep);
		tris.tri(x, y - 0.35f * r, light, x + p[3][0] * r, y + p[3][1] * r, mid, x + p[4][0] * r, y + p[4][1] * r, deep);
		for (int i = 0; i < 5; i++) {
			float twinkle = Math.max(0f, Mth.sin(s * 9f + i * 2.1f));
			float angle = i * Mth.TWO_PI / 5 + 0.3f;
			float sx = x + Mth.cos(angle) * r * 1.9f;
			float sy = y + Mth.sin(angle) * r * 1.6f;
			float len = r * 0.55f * twinkle;
			int c = argb(fade * twinkle, 0xFFFFFF);
			tris.quad(sx, sy - len, c, sx + len * 0.15f, sy, c, sx, sy + len, c, sx - len * 0.15f, sy, c);
			tris.quad(sx - len, sy, c, sx, sy - len * 0.15f, c, sx + len, sy, c, sx, sy + len * 0.15f, c);
		}
	}

	// =====================================================================
	// Shapes
	// =====================================================================

	/** Darkens (or, additive, lights) the screen's edges, clear within {@code clear} px of the centre. */
	private static void vignette(Tris tris, int w, int h, int rgb, float alpha, float clear) {
		if (alpha <= 0f) {
			return;
		}
		float outer = (float) Math.sqrt(w * w + h * h) / 2f + 2f;
		ringGradient(tris, w / 2f, h / 2f, clear, outer, argb(0f, rgb), argb(alpha, rgb), 48);
	}

	private static void ring(Tris tris, float cx, float cy, float radius, float thickness, int color, int segments) {
		int edge = color & 0x00FFFFFF;
		ringGradient(tris, cx, cy, Math.max(0f, radius - thickness), radius, edge, color, segments);
		ringGradient(tris, cx, cy, radius, radius + thickness, color, edge, segments);
	}

	private static void ringGradient(Tris tris, float cx, float cy, float r1, float r2, int c1, int c2, int segments) {
		for (int i = 0; i < segments; i++) {
			float a0 = i * Mth.TWO_PI / segments;
			float a1 = (i + 1) * Mth.TWO_PI / segments;
			tris.quad(cx + Mth.cos(a0) * r1, cy + Mth.sin(a0) * r1, c1, cx + Mth.cos(a0) * r2, cy + Mth.sin(a0) * r2, c2,
					cx + Mth.cos(a1) * r2, cy + Mth.sin(a1) * r2, c2, cx + Mth.cos(a1) * r1, cy + Mth.sin(a1) * r1, c1);
		}
	}

	private static void rhombus(Tris tris, float cx, float cy, float r, float rotation, float width, int color) {
		for (int i = 0; i < 4; i++) {
			float a0 = rotation + i * Mth.HALF_PI;
			float a1 = rotation + (i + 1) * Mth.HALF_PI;
			float k0 = i % 2 == 0 ? 1f : 0.62f;
			float k1 = i % 2 == 0 ? 0.62f : 1f;
			tris.line(cx + Mth.cos(a0) * r * k0, cy + Mth.sin(a0) * r * k0, cx + Mth.cos(a1) * r * k1, cy + Mth.sin(a1) * r * k1, width, color);
		}
	}

	private static void arc(Tris tris, float cx, float cy, float r, float from, float to, float width, int color) {
		int segments = 16;
		for (int i = 0; i < segments; i++) {
			float a0 = from + (to - from) * i / segments;
			float a1 = from + (to - from) * (i + 1) / segments;
			tris.line(cx + Mth.cos(a0) * r, cy + Mth.sin(a0) * r, cx + Mth.cos(a1) * r, cy + Mth.sin(a1) * r, width, color);
		}
	}

	/** A point on an ellipse ({@code rx}, {@code ry}) turned by {@code tilt}, at parameter {@code a}. */
	private static float[] ellipse(float cx, float cy, float rx, float ry, float tilt, float a) {
		float x = Mth.cos(a) * rx;
		float y = Mth.sin(a) * ry;
		return new float[] { cx + x * Mth.cos(tilt) - y * Mth.sin(tilt), cy + x * Mth.sin(tilt) + y * Mth.cos(tilt) };
	}

	private static float elastic(float k) {
		k = Mth.clamp(k, 0f, 1f);
		if (k == 0f || k == 1f) {
			return k;
		}
		return (float) (Math.pow(2, -10 * k) * Math.sin((k * 10 - 0.75) * (2 * Math.PI / 3)) + 1);
	}

	static int argb(float alpha, int rgb) {
		return (Math.round(Mth.clamp(alpha, 0f, 1f) * 255) << 24) | (rgb & 0xFFFFFF);
	}

	/** Stable pseudo-random value in [0, 1). */
	private static float hash(int seed) {
		int x = seed * 0x9E3779B1;
		x ^= x >>> 15;
		x *= 0x85EBCA77;
		x ^= x >>> 13;
		return (x & 0xFFFFFF) / (float) 0x1000000;
	}

	/** A batch of coloured triangles on the GUI, normal or additive blending, no depth test. */
	static final class Tris {
		private final BufferBuilder buffer;
		private final Matrix4f matrix;

		private Tris(BufferBuilder buffer, Matrix4f matrix) {
			this.buffer = buffer;
			this.matrix = matrix;
		}

		static Tris begin(GuiGraphics g, boolean additive) {
			g.flush();
			RenderSystem.enableBlend();
			if (additive) {
				RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
			} else {
				RenderSystem.defaultBlendFunc();
			}
			RenderSystem.disableDepthTest();
			RenderSystem.disableCull();
			RenderSystem.setShader(GameRenderer::getPositionColorShader);
			return new Tris(Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR),
					new Matrix4f(g.pose().last().pose()));
		}

		void end() {
			MeshData mesh = buffer.build();
			if (mesh != null) {
				BufferUploader.drawWithShader(mesh);
			}
			RenderSystem.defaultBlendFunc();
			RenderSystem.enableCull();
			RenderSystem.enableDepthTest();
		}

		void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
			buffer.addVertex(matrix, x1, y1, 0).setColor(c1);
			buffer.addVertex(matrix, x2, y2, 0).setColor(c2);
			buffer.addVertex(matrix, x3, y3, 0).setColor(c3);
		}

		void quad(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3, float x4, float y4, int c4) {
			tri(x1, y1, c1, x2, y2, c2, x3, y3, c3);
			tri(x1, y1, c1, x3, y3, c3, x4, y4, c4);
		}

		/** A disc, {@code inner} colour at the centre fading to {@code outer} at the rim. */
		void disc(float cx, float cy, float r, int inner, int outer, int segments) {
			for (int i = 0; i < segments; i++) {
				float a0 = i * Mth.TWO_PI / segments;
				float a1 = (i + 1) * Mth.TWO_PI / segments;
				tri(cx, cy, inner, cx + Mth.cos(a0) * r, cy + Mth.sin(a0) * r, outer, cx + Mth.cos(a1) * r, cy + Mth.sin(a1) * r, outer);
			}
		}

		/** A ray: a thin wedge {@code spread} radians wide, from {@code r1} to {@code r2}. */
		void wedge(float cx, float cy, float angle, float spread, float r1, float r2, int inner, int outer) {
			float a0 = angle - spread / 2;
			float a1 = angle + spread / 2;
			quad(cx + Mth.cos(a0) * r1, cy + Mth.sin(a0) * r1, inner, cx + Mth.cos(a0) * r2, cy + Mth.sin(a0) * r2, outer,
					cx + Mth.cos(a1) * r2, cy + Mth.sin(a1) * r2, outer, cx + Mth.cos(a1) * r1, cy + Mth.sin(a1) * r1, inner);
		}

		void line(float x1, float y1, float x2, float y2, float width, int color) {
			float dx = x2 - x1;
			float dy = y2 - y1;
			float length = Mth.sqrt(dx * dx + dy * dy);
			if (length < 1.0E-3f) {
				return;
			}
			float nx = -dy / length * width / 2;
			float ny = dx / length * width / 2;
			quad(x1 + nx, y1 + ny, color, x2 + nx, y2 + ny, color, x2 - nx, y2 - ny, color, x1 - nx, y1 - ny, color);
		}

		/** A crystal shard: a thin triangle turned by {@code rotation}. */
		void shard(float x, float y, float size, float rotation, int color) {
			int tip = (color & 0x00FFFFFF) | ((((color >>> 24) & 0xFF) / 3) << 24);
			tri(x + Mth.cos(rotation) * size, y + Mth.sin(rotation) * size, color,
					x + Mth.cos(rotation + 2.4f) * size * 0.45f, y + Mth.sin(rotation + 2.4f) * size * 0.45f, tip,
					x + Mth.cos(rotation - 2.4f) * size * 0.45f, y + Mth.sin(rotation - 2.4f) * size * 0.45f, tip);
		}
	}
}
