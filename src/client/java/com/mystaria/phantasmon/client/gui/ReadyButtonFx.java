package com.mystaria.phantasmon.client.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The battle lobby's READY button effects (2026-11-28), in canvas coordinates.
 * <ul>
 * <li>Hovered: a soft glow breathing around the button, a light streak sweeping across it, and sparks rising from
 * its edges.</li>
 * <li>Pressed: a flash, two shock rings growing out of the button, and a burst of sparks — green when getting ready,
 * the accent colour when backing out.</li>
 * </ul>
 */
final class ReadyButtonFx {

	private static final int MAX_PARTICLES = 260;

	private record Ring(long start, int color, float delay) {
	}

	private static final class Spark {
		float x;
		float y;
		float vx;
		float vy;
		float life;
		final float maxLife;
		final float size;
		final int color;

		Spark(float x, float y, float vx, float vy, float maxLife, float size, int color) {
			this.x = x;
			this.y = y;
			this.vx = vx;
			this.vy = vy;
			this.maxLife = maxLife;
			this.life = maxLife;
			this.size = size;
			this.color = color;
		}
	}

	private final List<Spark> sparks = new ArrayList<>();
	private final List<Ring> rings = new ArrayList<>();
	private final Random random = new Random();
	private long lastFrame;
	private float hover;
	private float spawnDebt;
	private long flashStart;
	private int flashColor;
	private float time;

	/** One frame: advances everything, then draws it around the button {@code x, y, w, h}. */
	void render(GuiGraphics g, int x, int y, int w, int h, boolean hovered, int color) {
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		time += dt;
		hover += (hovered ? 1f : -1f) * dt * 6f;
		hover = Math.max(0f, Math.min(1f, hover));

		if (hover > 0f) {
			renderGlow(g, x, y, w, h, color);
			renderSweep(g, x, y, w, h);
		}
		if (hovered) {
			// Sparks rising off the edges, ~40 a second.
			spawnDebt += dt * 40f;
			while (spawnDebt >= 1f) {
				spawnDebt -= 1f;
				spawnEdgeSpark(x, y, w, h, color);
			}
		}
		renderFlash(g, x, y, w, h, now);
		renderRings(g, x, y, w, h, now);
		updateAndDrawSparks(g, dt);
	}

	/** The button was pressed: {@code ready} true when the player is getting ready. */
	void press(int x, int y, int w, int h, int color) {
		long now = System.nanoTime();
		flashStart = now;
		flashColor = color;
		rings.add(new Ring(now, color, 0f));
		rings.add(new Ring(now, 0xFFFFFFFF, 0.12f));
		float cx = x + w / 2f;
		float cy = y + h / 2f;
		for (int i = 0; i < 70; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			float speed = 120f + random.nextFloat() * 380f;
			float px = cx + (float) Math.cos(angle) * w * 0.35f * random.nextFloat();
			float py = cy + (float) Math.sin(angle) * h * 0.35f * random.nextFloat();
			int sparkColor = random.nextFloat() < 0.3f ? 0xFFFFFFFF : color;
			add(new Spark(px, py, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed * 0.6f,
					0.5f + random.nextFloat() * 0.6f, 2f + random.nextFloat() * 3f, sparkColor));
		}
	}

	private void renderGlow(GuiGraphics g, int x, int y, int w, int h, int color) {
		float breath = 0.65f + 0.35f * (float) Math.sin(time * 4f);
		for (int i = 1; i <= 8; i++) {
			float alpha = hover * breath * 0.22f * (1f - i / 9f);
			int c = PhantasmonCanvasScreen.withAlpha(color | 0xFF000000, alpha);
			g.fill(x - i, y - i, x + w + i, y - i + 1, c);
			g.fill(x - i, y + h + i - 1, x + w + i, y + h + i, c);
			g.fill(x - i, y - i + 1, x - i + 1, y + h + i - 1, c);
			g.fill(x + w + i - 1, y - i + 1, x + w + i, y + h + i - 1, c);
		}
	}

	/** A slanted light streak crossing the button every 1.6 s while hovered. */
	private void renderSweep(GuiGraphics g, int x, int y, int w, int h) {
		float phase = (time % 1.6f) / 1.6f;
		float center = x - h + phase * (w + 2 * h);
		for (int row = 0; row < h; row++) {
			float rowCenter = center + (h / 2f - row) * 0.6f;
			for (int band = -3; band <= 3; band++) {
				float alpha = hover * 0.10f * (1f - Math.abs(band) / 4f);
				int left = Math.round(rowCenter + band * 4);
				int l = Math.max(x, left);
				int r = Math.min(x + w, left + 4);
				if (r > l) {
					g.fill(l, y + row, r, y + row + 1, PhantasmonCanvasScreen.withAlpha(0xFFFFFFFF, alpha));
				}
			}
		}
	}

	private void renderFlash(GuiGraphics g, int x, int y, int w, int h, long now) {
		if (flashStart == 0) {
			return;
		}
		float t = (now - flashStart) / 1_000_000_000f;
		if (t > 0.35f) {
			flashStart = 0;
			return;
		}
		float alpha = 0.55f * (1f - t / 0.35f);
		g.fill(x, y, x + w, y + h, PhantasmonCanvasScreen.withAlpha(0xFFFFFFFF, alpha));
		g.fill(x - 2, y - 2, x + w + 2, y + h + 2, PhantasmonCanvasScreen.withAlpha(flashColor | 0xFF000000, alpha * 0.4f));
	}

	/** Shock rings: rectangles growing out of the button and fading. */
	private void renderRings(GuiGraphics g, int x, int y, int w, int h, long now) {
		Iterator<Ring> it = rings.iterator();
		while (it.hasNext()) {
			Ring ring = it.next();
			float t = (now - ring.start()) / 1_000_000_000f - ring.delay();
			if (t < 0) {
				continue;
			}
			float duration = 0.55f;
			if (t > duration) {
				it.remove();
				continue;
			}
			float k = t / duration;
			float eased = 1f - (1f - k) * (1f - k);
			int grow = Math.round(eased * 46);
			int thick = Math.max(1, Math.round(3 * (1f - k)));
			int c = PhantasmonCanvasScreen.withAlpha(ring.color() | 0xFF000000, 0.9f * (1f - k));
			int l = x - grow;
			int t0 = y - Math.round(grow * 0.6f);
			int r = x + w + grow;
			int b = y + h + Math.round(grow * 0.6f);
			g.fill(l, t0, r, t0 + thick, c);
			g.fill(l, b - thick, r, b, c);
			g.fill(l, t0 + thick, l + thick, b - thick, c);
			g.fill(r - thick, t0 + thick, r, b - thick, c);
		}
	}

	private void spawnEdgeSpark(int x, int y, int w, int h, int color) {
		float perimeter = 2f * (w + h);
		float p = random.nextFloat() * perimeter;
		float px;
		float py;
		float nx;
		float ny;
		if (p < w) {
			px = x + p;
			py = y;
			nx = 0;
			ny = -1;
		} else if (p < w + h) {
			px = x + w;
			py = y + (p - w);
			nx = 1;
			ny = 0;
		} else if (p < 2 * w + h) {
			px = x + (p - w - h);
			py = y + h;
			nx = 0;
			ny = 1;
		} else {
			px = x;
			py = y + (p - 2 * w - h);
			nx = -1;
			ny = 0;
		}
		float speed = 20f + random.nextFloat() * 40f;
		int sparkColor = random.nextFloat() < 0.25f ? 0xFFFFFFFF : color;
		add(new Spark(px, py, nx * speed + (random.nextFloat() - 0.5f) * 20f, ny * speed - 25f, 0.5f + random.nextFloat() * 0.5f,
				1.5f + random.nextFloat() * 2f, sparkColor));
	}

	private void add(Spark spark) {
		if (sparks.size() < MAX_PARTICLES) {
			sparks.add(spark);
		}
	}

	private void updateAndDrawSparks(GuiGraphics g, float dt) {
		Iterator<Spark> it = sparks.iterator();
		while (it.hasNext()) {
			Spark s = it.next();
			s.life -= dt;
			if (s.life <= 0) {
				it.remove();
				continue;
			}
			float drag = (float) Math.pow(0.12, dt);
			s.vx *= drag;
			s.vy = s.vy * drag + 60f * dt; // a little gravity
			s.x += s.vx * dt;
			s.y += s.vy * dt;
			float k = s.life / s.maxLife;
			float size = s.size * (0.4f + 0.6f * k);
			int half = Math.max(1, Math.round(size / 2f));
			int px = Math.round(s.x);
			int py = Math.round(s.y);
			// A soft halo, then the bright core.
			g.fill(px - half - 1, py - half - 1, px + half + 1, py + half + 1, PhantasmonCanvasScreen.withAlpha(s.color | 0xFF000000, 0.25f * k));
			g.fill(px - half, py - half, px + half, py + half, PhantasmonCanvasScreen.withAlpha(s.color | 0xFF000000, k));
		}
	}
}
