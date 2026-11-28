"""Pictures of the battle lobby's choice cards: python tools/lobby_previews.py [--skip-capture] [--only id1,id2]

The cards of the lobby's choice windows (scene presets, stadiums, ambiances, League Reborn fields, intros) show a
picture of their choice behind them (gui/LobbyPreviews). This script makes those pictures and puts them in the mod
(src/main/resources/assets/phantasmon/textures/gui/previews/<kind>/<path>.png):

  1. League Reborn fields: each field's Showtime ambiance and stadium, read from the Events add-on
     (reborn/RebornAmbiance.java), make a list of extra scenes;
  2. Showtime's scene capture (tools/scene_capture.py in Cobblemon-Battle-Showtime) starts a dev game and pictures
     every preset, stadium and ambiance, and those fields (about 15 minutes);
  3. every picture is cut to the cards' 16:9, shrunk to 480x270 and reduced to 256 colours (a light jar);
  4. intros: Showtime's art screenshots (art/intros/screenshots/NN_<intro>.png).

--skip-capture only redoes steps 3-4 from the last capture. Needs Pillow.
"""
import argparse
import glob
import os
import re
import subprocess
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GITHUB = os.path.dirname(ROOT)
SHOWTIME = os.path.join(GITHUB, "Cobblemon-Battle-Showtime")
EVENTS = os.path.join(GITHUB, "Phantasmon-Events")
REBORN = os.path.join(EVENTS, "src", "main", "java", "com", "mystaria", "events", "reborn", "RebornAmbiance.java")
SCENES = os.path.join(SHOWTIME, "run-capture", "scenes")
INTROS = os.path.join(SHOWTIME, "art", "intros", "screenshots")
PREVIEWS = os.path.join(ROOT, "src", "main", "resources", "assets", "phantasmon", "textures", "gui", "previews")
NS = "cobblemon_battle_showtime:"
SIZE = (480, 270)


def field_scenes():
    """Field id -> "<ambiance>|<stadium>" (each "none" when the field has none), from the Events add-on."""
    source = open(REBORN, encoding="utf-8").read()

    def table(name):
        block = source[source.index(name + " = Map.ofEntries("):]
        block = block[:block.index(");")]
        return dict(re.findall(r'Map\.entry\("([^"]+)",\s*"([^"]+)"\)', block))

    ambiances = table("BY_FIELD")
    stadiums = table("STADIUM_BY_FIELD")
    scenes = {}
    for field in sorted(set(ambiances) | set(stadiums)):
        ambiance = NS + ambiances[field] if field in ambiances else "none"
        stadium = NS + stadiums[field] if field in stadiums else "none"
        scenes[field] = ambiance + "|" + stadium
    return scenes


def card(image):
    """Cut to 16:9 around the middle, then shrunk to the card's picture size, 256 colours."""
    image = image.convert("RGB")
    w, h = image.size
    if w * 9 > h * 16:
        cut = h * 16 // 9
        image = image.crop(((w - cut) // 2, 0, (w - cut) // 2 + cut, h))
    else:
        cut = w * 9 // 16
        image = image.crop((0, (h - cut) // 2, w, (h - cut) // 2 + cut))
    image = image.resize(SIZE, Image.LANCZOS)
    return image.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--skip-capture", action="store_true")
    p.add_argument("--only", default="", help="capture only these ids (comma separated)")
    p.add_argument("--kinds", default="preset,stadium,ambiance", help="Showtime's own kinds to capture")
    a = p.parse_args()

    if not a.skip_capture:
        extra = os.path.join(SHOWTIME, "run-capture", "lobby-fields.tsv")
        os.makedirs(os.path.dirname(extra), exist_ok=True)
        with open(extra, "w", encoding="utf-8") as f:
            for field, scene in field_scenes().items():
                f.write(f"field\t{field}\t{scene}\n")
        subprocess.run([sys.executable, os.path.join(SHOWTIME, "tools", "scene_capture.py"), "--kinds", a.kinds,
                        "--only", a.only, "--extra", extra], check=True)

    written = 0
    for png in glob.glob(os.path.join(SCENES, "**", "*.png"), recursive=True):
        target = os.path.join(PREVIEWS, os.path.relpath(png, SCENES))
        os.makedirs(os.path.dirname(target), exist_ok=True)
        card(Image.open(png)).save(target, optimize=True)
        written += 1
    for png in glob.glob(os.path.join(INTROS, "*.png")):
        name = re.sub(r"^\d+_", "", os.path.basename(png))
        target = os.path.join(PREVIEWS, "intro", name)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        card(Image.open(png)).save(target, optimize=True)
        written += 1
    total = sum(os.path.getsize(f) for f in glob.glob(os.path.join(PREVIEWS, "**", "*.png"), recursive=True))
    print(f"{written} pictures in {PREVIEWS} ({total / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
