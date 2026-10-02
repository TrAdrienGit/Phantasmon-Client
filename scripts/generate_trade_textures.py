#!/usr/bin/env python3
"""Generates the trade screen textures (assets/phantasmon/textures/gui/trade/).

Every value below is copied from the reference mock-up's CSS
(handoff `phantasmon_trade_ui.html` / SPEC_ECRAN_ECHANGE.md §4-5): Minecraft's
GuiGraphics can't draw diagonal/radial gradients or glows, so those become
plain PNGs, blitted stretched (not nine-sliced) by PhantasmonTradeScreen.

Smooth gradients are rendered at half resolution and flagged `"blur": true`
in a .png.mcmeta (bilinear upscaling in game, no banding, small files); the
viewport keeps full resolution because of its 1px grid lines.

Usage: python scripts/generate_trade_textures.py   (needs Pillow only)
"""
import json
import math
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "phantasmon", "textures", "gui", "trade")


def rgba(r, g, b, a):
    return (r, g, b, a)


def mix(c0, c1, t):
    """CSS interpolates gradients in premultiplied-alpha space."""
    t = max(0.0, min(1.0, t))
    a = c0[3] + (c1[3] - c0[3]) * t
    if a <= 0:
        return (0.0, 0.0, 0.0, 0.0)
    pm = [c0[i] * c0[3] + (c1[i] * c1[3] - c0[i] * c0[3]) * t for i in range(3)]
    return (pm[0] / a, pm[1] / a, pm[2] / a, a)


def stops_at(stops, t):
    if t <= stops[0][0]:
        return stops[0][1]
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        if t <= p1:
            return mix(c0, c1, 0 if p1 == p0 else (t - p0) / (p1 - p0))
    return stops[-1][1]


def over(top, bottom):
    """Source-over compositing of two straight-alpha colors (alpha in 0..1)."""
    a = top[3] + bottom[3] * (1 - top[3])
    if a <= 0:
        return (0.0, 0.0, 0.0, 0.0)
    return tuple((top[i] * top[3] + bottom[i] * bottom[3] * (1 - top[3])) / a for i in range(3)) + (a,)


def linear(w, h, angle_deg, stops):
    """CSS linear-gradient(angle, ...) — returns a function (x, y) -> color for a w×h box."""
    a = math.radians(angle_deg)
    dx, dy = math.sin(a), -math.cos(a)
    length = abs(w * dx) + abs(h * dy)
    return lambda x, y: stops_at(stops, 0.5 + ((x - w / 2) * dx + (y - h / 2) * dy) / length)


def radial(w, h, cx, cy, stops):
    """CSS radial-gradient(circle at cx cy, ...) with the default farthest-corner size."""
    r = max(math.hypot(cx - px, cy - py) for px in (0, w) for py in (0, h))
    return lambda x, y: stops_at(stops, math.hypot(x - cx, y - cy) / r)


def c(hexstr, alpha=1.0):
    n = int(hexstr.lstrip("#"), 16)
    return ((n >> 16) & 255, (n >> 8) & 255, n & 255, alpha)


def render(name, w, h, layers, scale=1.0, blur=False):
    """layers: list of (x, y) -> color functions in CSS order (first = topmost), in w×h px space."""
    iw, ih = max(1, round(w * scale)), max(1, round(h * scale))
    img = Image.new("RGBA", (iw, ih))
    px = img.load()
    for j in range(ih):
        for i in range(iw):
            x, y = (i + 0.5) / scale, (j + 0.5) / scale
            col = (0.0, 0.0, 0.0, 0.0)
            for layer in reversed(layers):
                col = over(layer(x, y), col)
            px[i, j] = tuple(int(round(v)) for v in col[:3]) + (int(round(col[3] * 255)),)
    path = os.path.join(OUT, name + ".png")
    img.save(path, optimize=True)
    meta = path + ".mcmeta"
    if blur:
        with open(meta, "w", encoding="utf-8") as f:
            json.dump({"texture": {"blur": True, "clamp": True}}, f, indent=2)
            f.write("\n")
    elif os.path.exists(meta):
        os.remove(meta)
    print("wrote", os.path.relpath(path), img.size)


def viewport_disc(w, h):
    """`.viewport:before`: 210px circle, 1px border, 22px grid of 1px lines, the whole square rotated 45°."""
    cx, cy, radius = w / 2, h / 2, 105.0
    cos45 = math.cos(math.radians(-45))
    sin45 = math.sin(math.radians(-45))
    line = c("#50E6FF", 0x12 / 255)
    border = c("#50E6FF", 0.09)
    samples = [(sx + 0.5) / 4 - 0.5 for sx in range(4)]

    def layer(x, y):
        hits_line = hits_border = 0
        for ox in samples:
            for oy in samples:
                rx, ry = x + ox - cx, y + oy - cy
                d = math.hypot(rx, ry)
                if d > radius:
                    continue
                if d > radius - 1:
                    hits_border += 1
                    continue
                lx = rx * cos45 - ry * sin45 + radius
                ly = rx * sin45 + ry * cos45 + radius
                if (lx % 22) < 1 or (ly % 22) < 1:
                    hits_line += 1
        n = len(samples) ** 2
        col = (0.0, 0.0, 0.0, 0.0)
        if hits_line:
            col = over((line[0], line[1], line[2], line[3] * hits_line / n), col)
        if hits_border:
            col = over((border[0], border[1], border[2], border[3] * hits_border / n), col)
        return col

    return layer


def main():
    os.makedirs(OUT, exist_ok=True)

    # body background behind the root panel (stretched to the whole screen)
    render("background", 1600, 900, [
        radial(1600, 900, 800, 900 * 0.42, [(0, c("#185470", 0.22)), (0.43, c("#185470", 0.0))]),
        linear(1600, 900, 135, [(0, c("#02070D")), (0.55, c("#071523")), (1, c("#02060B"))]),
    ], scale=0.25, blur=True)

    # .team-rail (Z05/Z27)
    render("rail", 194, 758, [
        linear(194, 758, 145, [(0, c("#0C2036", 0.96)), (1, c("#030A14", 0.98))]),
    ], scale=0.5, blur=True)

    # .pokemon-card (Z12/Z26)
    render("card", 577, 758, [
        linear(577, 758, 145, [(0, c("#0C2036", 0.98)), (1, c("#030A14", 0.98))]),
    ], scale=0.5, blur=True)

    # .player-head (Z02) and .player-head.right (Z04, mirrored gradient)
    for name, angle in (("player_head_left", 110), ("player_head_right", 250)):
        render(name, 688, 48, [
            linear(688, 48, angle, [(0, c("#0C3650", 0.92)), (1, c("#030A14", 0.96))]),
        ], scale=0.5, blur=True)

    # .modal-card
    render("modal", 520, 266, [
        linear(520, 266, 145, [(0, c("#0C2036")), (1, c("#030A14"))]),
    ], scale=0.5, blur=True)

    # .viewport (Z14): halo, vignette, then the grid disc drawn by :before on top
    render("viewport", 575, 235, [
        viewport_disc(575, 235),
        radial(575, 235, 575 / 2, 235 * 0.53, [(0, c("#50E6FF", 0.08)), (0.38, c("#50E6FF", 0.0))]),
        radial(575, 235, 575 / 2, 235 / 2, [(0, c("#000000", 0.0)), (0.35, c("#000000", 0.0)), (1, c("#000000", 0.43))]),
    ])

    # soft elliptical shadow under the 3D model (stands in for the sprite's CSS drop-shadow)
    render("shadow", 128, 32, [
        lambda x, y: stops_at([(0, c("#000000", 0.55)), (1, c("#000000", 0.0))],
                              math.hypot((x - 64) / 64, (y - 16) / 16)),
    ], scale=0.5, blur=True)


if __name__ == "__main__":
    main()
