"""
The Cobblemon look of Phantasmon's screen textures (Adrien 2026-11-08): textures/gui/trade/*.png, the V1 look (cyan
on navy), recoloured into textures/gui/trade_cobblemon/ — the navy becomes Cobblemon's greys, the cyan its teal.
Same sizes and alpha, so every screen lays them out alike.

    python tools/theme_textures.py
"""
import colorsys
import shutil
from pathlib import Path

from PIL import Image

GUI = Path(__file__).resolve().parent.parent / "src/main/resources/assets/phantasmon/textures/gui"
SRC = GUI / "trade"
DST = GUI / "trade_cobblemon"
TEAL = (0x38, 0xC4, 0xBC)


def recolour(r, g, b):
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    cyan = 0.45 < h < 0.6 and s > 0.35
    if cyan and v > 0.45:
        # Cyan light: Cobblemon's teal, as bright as it was.
        k = v / 0.95
        return tuple(min(255, int(c * k)) for c in TEAL)
    # Navy and dim cyan: a neutral grey, lifted (Cobblemon's panels sit around #2F-#4B).
    grey = int(0x22 + v * 255 * 0.75)
    return (min(255, grey),) * 3


DST.mkdir(exist_ok=True)
for source in sorted(SRC.glob("*.png")):
    image = Image.open(source).convert("RGBA")
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            if a:
                pixels[x, y] = recolour(r, g, b) + (a,)
    image.save(DST / source.name)
    meta = source.with_suffix(".png.mcmeta")
    if meta.exists():
        shutil.copy(meta, DST / meta.name)
    print(source.name)
