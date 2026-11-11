package com.mystaria.phantasmon.client.gui.menu;

import com.cobblemon.mod.common.client.render.RenderHelperKt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

import com.mystaria.phantasmon.client.gui.PhantasmonTheme;

/**
 * Phantasmon's menu toolkit (redesign, 2026-11-10), the same build as Cobblemon Battle Showtime's menu, the quality bar:
 * Cobblemon's {@code uniform} font, relief everywhere (lit and shaded edges, sunken rows, raised buttons), real switches,
 * chips, a dotted background, bezels with accent corners. Two palettes with the same shapes: {@link Palette#COBBLEMON}
 * (Cobblemon's greys and turquoise, the default) and {@link Palette#V1} (Phantasmon's first cyan on navy). GUI pixels,
 * client thread.
 */
public final class MenuUi {

	/** The colours of one look; every shape takes its colours from the current one ({@link #p()}). */
	public record Palette(int dark, int panel, int raised, int edge, int light, int accent, int accentDim, int accentHot,
			int white, int muted, int black, int dots, int danger, int dangerDim, int good, int star) {

		public static final Palette COBBLEMON = new Palette(0xFF2F2F2F, 0xFF3D3D3D, 0xFF4B4B4B, 0xFF676767, 0xFF8D8D8D,
				0xFF38C4BC, 0xFF1F7F7A, 0xFF7FF0E8, 0xFFFFFFFF, 0xFFC6C6C6, 0xFF1A1A1A, 0xFF3A3A3A, 0xFFD9534F, 0xFF8A2A27,
				0xFF76FFB0, 0xFFFFD84A);
		public static final Palette V1 = new Palette(0xFF0B1426, 0xFF13203A, 0xFF1B2D4F, 0xFF2A4470, 0xFF4A6FA8,
				0xFF50E6FF, 0xFF1E7A96, 0xFFA8F4FF, 0xFFFFFFFF, 0xFFB8CCE6, 0xFF050A14, 0xFF17264A, 0xFFFF5C7A, 0xFF8A1F38,
				0xFF76FFB0, 0xFFFFD84A);
	}

	public static final int SHINE = 0x30FFFFFF;
	public static final int SHADE = 0x50000000;
	public static final int VEIL = 0xA0000000;
	private static final ResourceLocation UNIFORM = ResourceLocation.parse("uniform");

	private MenuUi() {
	}

	/** The palette of the look chosen in the settings. */
	public static Palette p() {
		return PhantasmonTheme.cobblemon() ? Palette.COBBLEMON : Palette.V1;
	}

	// ---------------------------------------------------------------- relief

	/** Light on top, shade below: the shape stands out of the panel. */
	public static void raised(GuiGraphics g, int x1, int y1, int x2, int y2) {
		g.fill(x1, y1, x2, y1 + 1, SHINE);
		g.fill(x1, y1 + 1, x1 + 1, y2, SHINE);
		g.fill(x1, y2 - 1, x2, y2, SHADE);
		g.fill(x2 - 1, y1, x2, y2 - 1, SHADE);
	}

	/** Shade on top, light below: the shape sinks into the panel. */
	public static void sunken(GuiGraphics g, int x1, int y1, int x2, int y2) {
		g.fill(x1, y1, x2, y1 + 1, SHADE);
		g.fill(x1, y1 + 1, x1 + 1, y2, SHADE);
		g.fill(x1, y2 - 1, x2, y2, SHINE);
		g.fill(x2 - 1, y1, x2, y2 - 1, SHINE);
	}

	public static void outline(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
		g.fill(x1, y1, x2, y1 + 1, color);
		g.fill(x1, y2 - 1, x2, y2, color);
		g.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
		g.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
	}

	/** A left-to-right gradient, in thin bands (GuiGraphics only has vertical ones). */
	public static void gradientRight(GuiGraphics g, int x1, int y1, int x2, int y2, int from, int to) {
		int steps = Math.max(1, (x2 - x1) / 4);
		for (int i = 0; i < steps; i++) {
			int color = FastColor.ARGB32.lerp(i / (float) steps, from, to);
			g.fill(x1 + (x2 - x1) * i / steps, y1, x1 + (x2 - x1) * (i + 1) / steps, y2, color);
		}
	}

	/** A fine grid of dots, so a background isn't flat. */
	public static void dots(GuiGraphics g, int x1, int y1, int x2, int y2) {
		int color = p().dots();
		for (int y = y1 + 6; y < y2 - 2; y += 12) {
			for (int x = x1 + 6; x < x2 - 2; x += 12) {
				g.fill(x, y, x + 1, y + 1, color);
			}
		}
	}

	/** A raised metal frame with accent corner brackets (previews, dialogs). */
	public static void bezel(GuiGraphics g, int x1, int y1, int x2, int y2) {
		Palette c = p();
		g.fill(x1 - 3, y1 - 3, x2 + 3, y1, c.raised());
		g.fill(x1 - 3, y2, x2 + 3, y2 + 3, c.panel());
		g.fill(x1 - 3, y1, x1, y2, c.raised());
		g.fill(x2, y1, x2 + 3, y2, c.panel());
		g.fill(x1 - 3, y1 - 3, x2 + 3, y1 - 2, c.light());
		g.fill(x1 - 3, y1 - 2, x1 - 2, y2 + 3, c.edge());
		g.fill(x1 - 3, y2 + 2, x2 + 3, y2 + 3, c.black());
		g.fill(x2 + 2, y1 - 2, x2 + 3, y2 + 3, c.black());
		outline(g, x1 - 1, y1 - 1, x2 + 1, y2 + 1, c.black());
		int arm = 10;
		int a = c.accent();
		g.fill(x1 - 3, y1 - 3, x1 - 3 + arm, y1 - 1, a);
		g.fill(x1 - 3, y1 - 3, x1 - 1, y1 - 3 + arm, a);
		g.fill(x2 + 3 - arm, y1 - 3, x2 + 3, y1 - 1, a);
		g.fill(x2 + 1, y1 - 3, x2 + 3, y1 - 3 + arm, a);
		g.fill(x1 - 3, y2 + 1, x1 - 3 + arm, y2 + 3, a);
		g.fill(x1 - 3, y2 + 3 - arm, x1 - 1, y2 + 3, a);
		g.fill(x2 + 3 - arm, y2 + 1, x2 + 3, y2 + 3, a);
		g.fill(x2 + 1, y2 + 3 - arm, x2 + 3, y2 + 3, a);
	}

	/** The outer frame of a window: corners trimmed, lit from the top left. */
	public static void frame(GuiGraphics g, int left, int top, int right, int bottom) {
		Palette c = p();
		g.fill(left - 2, top - 1, left - 1, bottom + 1, c.black());
		g.fill(right + 1, top - 1, right + 2, bottom + 1, c.black());
		g.fill(left - 1, top - 2, right + 1, top - 1, c.black());
		g.fill(left - 1, bottom + 1, right + 1, bottom + 2, c.black());
		g.fill(left - 1, top - 1, right + 1, top, c.light());
		g.fill(left - 1, top, left, bottom + 1, c.light());
		g.fill(right, top, right + 1, bottom + 1, c.black());
		g.fill(left, bottom, right + 1, bottom + 1, c.black());
	}

	/** A window's header bar: gradient, hatching on the right, accent line below, a small gem before the title. */
	public static void header(GuiGraphics g, int left, int top, int right, int height) {
		Palette c = p();
		int bottom = top + height;
		g.fillGradient(left, top, right, bottom, c.edge(), c.raised());
		g.fill(left, top, right, top + 1, c.light());
		for (int i = 0; i < 14; i++) {
			int x = right - 44 - i * 6;
			for (int step = 0; step < 8; step++) {
				g.fill(x - step, top + 8 + step, x - step + 2, top + 9 + step, SHADE);
			}
		}
		g.fill(left, bottom - 2, right, bottom - 1, c.black());
		g.fillGradient(left, bottom - 1, right, bottom, c.accent(), c.accentDim());
		g.fill(left + 9, top + 8, left + 16, top + 15, c.black());
		g.fillGradient(left + 10, top + 9, left + 15, top + 14, c.accentHot(), c.accentDim());
	}

	/** A page title with its accent underline. */
	public static void title(GuiGraphics g, Component text, int x, int y, int right) {
		text(g, text.copy().withStyle(s -> s.withBold(true)), x, y, right - x, p().white());
		g.fill(x, y + 11, right, y + 12, p().accent());
		g.fill(x, y + 12, right, y + 13, p().black());
	}

	// ---------------------------------------------------------------- controls

	/** A raised button; {@code danger}: red; {@code enabled} false: dimmed, no hover. */
	public static void button(GuiGraphics g, int x, int y, int w, int h, Component label, boolean hovered, boolean danger,
			boolean enabled) {
		Palette c = p();
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, c.black());
		if (!enabled) {
			g.fill(x, y, x + w, y + h, c.panel());
		} else if (danger) {
			g.fillGradient(x, y, x + w, y + h, hovered ? c.danger() : FastColor.ARGB32.lerp(0.5f, c.danger(), c.dangerDim()),
					c.dangerDim());
		} else {
			g.fillGradient(x, y, x + w, y + h, hovered ? c.light() : c.edge(), hovered ? c.edge() : c.raised());
		}
		raised(g, x, y, x + w, y + h);
		centered(g, label, x + w / 2, y + (h - 8) / 2 + 1, w - 6, enabled ? c.white() : c.edge());
	}

	/** An accent button, the main action of a page or dialog. */
	public static void primary(GuiGraphics g, int x, int y, int w, int h, Component label, boolean hovered, boolean enabled) {
		Palette c = p();
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, c.black());
		if (enabled) {
			g.fillGradient(x, y, x + w, y + h, hovered ? c.accentHot() : c.accent(), c.accentDim());
		} else {
			g.fill(x, y, x + w, y + h, c.panel());
		}
		raised(g, x, y, x + w, y + h);
		centered(g, label, x + w / 2, y + (h - 8) / 2 + 1, w - 6, enabled ? c.white() : c.edge());
	}

	/** A section chip (tabs of a page): selected in accent. */
	public static void chip(GuiGraphics g, int x, int y, int w, int h, Component label, boolean selected, boolean hovered) {
		Palette c = p();
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, c.black());
		if (selected) {
			g.fillGradient(x, y, x + w, y + h, c.accent(), c.accentDim());
		} else {
			g.fillGradient(x, y, x + w, y + h, hovered ? c.light() : c.edge(), hovered ? c.edge() : c.raised());
		}
		raised(g, x, y, x + w, y + h);
		centered(g, label, x + w / 2, y + (h - 8) / 2 + 1, w - 6, c.white());
	}

	/** A filter chip, flatter than a section chip, underlined when selected. */
	public static void filter(GuiGraphics g, int x, int y, int w, int h, Component label, boolean selected, boolean hovered) {
		Palette c = p();
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, c.black());
		g.fill(x, y, x + w, y + h, selected ? c.accentDim() : hovered ? c.raised() : c.panel());
		if (selected) {
			g.fill(x, y + h - 2, x + w, y + h, c.accent());
		}
		centered(g, label, x + w / 2, y + (h - 8) / 2 + 1, w - 4, selected ? c.white() : c.muted());
	}

	/** A sunken row's background, lighter under the mouse. */
	public static void row(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
		g.fill(x, y, x + w, y + h, hovered ? p().raised() : p().panel());
		sunken(g, x, y, x + w, y + h);
	}

	/** A selected row: accent bar on the left and a gradient. */
	public static void selectedRow(GuiGraphics g, int x, int y, int w, int h) {
		gradientRight(g, x + 1, y + 1, x + w - 1, y + h - 1, p().accentDim(), p().panel());
		g.fill(x, y, x + 3, y + h, p().accent());
	}

	/** A rounded switch, 20 × 12, its top-left at {@code (x, y)}. */
	public static void toggle(GuiGraphics g, int x, int y, boolean on) {
		Palette c = p();
		g.fill(x + 1, y, x + 19, y + 12, c.black());
		g.fill(x, y + 1, x + 20, y + 11, c.black());
		g.fillGradient(x + 1, y + 1, x + 19, y + 11, on ? c.accentDim() : c.dark(), on ? c.accent() : c.panel());
		int knob = on ? x + 11 : x + 2;
		g.fill(knob - 1, y + 1, knob + 8, y + 11, c.black());
		g.fillGradient(knob, y + 2, knob + 7, y + 10, c.white(), c.muted());
	}

	/** A small square button inside a row (listen, star, delete), 18 × 14. */
	public static void mini(GuiGraphics g, int x, int y, Component label, boolean hovered, boolean danger, int labelColor) {
		Palette c = p();
		if (danger) {
			g.fillGradient(x, y, x + 18, y + 14, hovered ? c.danger() : c.edge(), hovered ? c.dangerDim() : c.raised());
		} else {
			g.fillGradient(x, y, x + 18, y + 14, hovered ? c.accent() : c.edge(), hovered ? c.accentDim() : c.raised());
		}
		raised(g, x, y, x + 18, y + 14);
		centered(g, label, x + 9, y + 3, 16, labelColor);
	}

	/** A text field: sunken, its label above it, a blinking caret when focused; {@code placeholder} when empty. */
	public static void field(GuiGraphics g, int x, int y, int w, int h, Component label, String value, String placeholder,
			boolean focused, boolean invalid) {
		Palette c = p();
		if (label != null) {
			text(g, label, x, y - 10, w, c.muted());
		}
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, focused ? c.accent() : c.black());
		g.fill(x, y, x + w, y + h, c.dark());
		sunken(g, x, y, x + w, y + h);
		int textY = y + (h - 8) / 2 + 1;
		if (value.isEmpty() && placeholder != null && !focused) {
			text(g, Component.literal(placeholder), x + 4, textY, w - 8, c.edge());
		} else {
			String shown = fit(value, w - 14);
			text(g, Component.literal(shown), x + 4, textY, w - 8, invalid ? c.danger() : c.white());
			if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
				int caret = x + 4 + Minecraft.getInstance().font.width(shown) + 1;
				g.fill(caret, textY - 1, caret + 1, textY + 8, c.accent());
			}
		}
	}

	/** The end of {@code value} that fits in {@code width} (the caret stays visible while typing). */
	private static String fit(String value, int width) {
		var font = Minecraft.getInstance().font;
		String shown = value;
		while (!shown.isEmpty() && font.width(shown) > width) {
			shown = shown.substring(1);
		}
		return shown;
	}

	/** A scrollbar for a list of {@code total} pixels shown in {@code view}, scrolled by {@code scroll}. */
	public static void scrollbar(GuiGraphics g, int x, int y, int view, int total, int scroll, boolean hot) {
		if (total <= view) {
			return;
		}
		Palette c = p();
		int max = total - view;
		int height = Math.max(16, view * view / total);
		int top = y + Math.round((view - height) * (scroll / (float) max));
		g.fill(x, y, x + 5, y + view, c.black());
		g.fillGradient(x, top, x + 5, top + height, hot ? c.accentHot() : c.accent(), c.accentDim());
		raised(g, x, top, x + 5, top + height);
	}

	/** The thumb's top and height for {@link #scrollbar}'s geometry, null when everything fits. */
	public static int[] thumb(int y, int view, int total, int scroll) {
		if (total <= view) {
			return null;
		}
		int max = total - view;
		int height = Math.max(16, view * view / total);
		return new int[] { y + Math.round((view - height) * (scroll / (float) max)), height };
	}

	// ---------------------------------------------------------------- text

	public static void text(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
		draw(g, text, x, y, maxWidth, color, false, 1f);
	}

	/** Secondary text: same size (a smaller uniform font is blurred and unreadable, Adrien 2026-11-11), its colour tells it apart. */
	public static void small(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
		draw(g, text, x, y, maxWidth, color, false, 1f);
	}

	public static void centered(GuiGraphics g, Component text, int centerX, int y, int maxWidth, int color) {
		draw(g, text, centerX, y, maxWidth, color, true, 1f);
	}

	/** Cobblemon's own text drawing, in its font; a text too long for its place shrinks to fit. */
	private static void draw(GuiGraphics g, Component text, int x, int y, int maxWidth, int color, boolean center, float scale) {
		RenderHelperKt.drawScaledText(g, UNIFORM, text.copy(), x, y, scale, 1f, Math.max(1, Math.round(maxWidth / scale)), color,
				center, true, null, null);
	}

	public static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	/** Cobblemon's PC click. */
	public static void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
				com.cobblemon.mod.common.CobblemonSounds.PC_CLICK, 1f));
	}
}
