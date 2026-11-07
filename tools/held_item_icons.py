"""
Phantasmon's own icons for the battle items Cobblemon has no item for, drawn here from shapes and palettes: nothing is
extracted from a game or another mod. Writes 32x32 PNGs to assets/phantasmon/textures/gui/held_items/<showdown id>.png,
and the id -> name table of VirtualItems.java, between its GENERATED markers.

    python tools/held_item_icons.py [contact_sheet.png]
"""
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/phantasmon/textures/gui/held_items"
JAVA = ROOT / "src/client/java/com/mystaria/phantasmon/client/pokemon/VirtualItems.java"
S = 32

TYPES = {
    "normal": "A8A77A", "fire": "EE8130", "water": "6390F0", "electric": "F7D02C", "grass": "7AC74C", "ice": "96D9D6",
    "fighting": "C22E28", "poison": "A33EA1", "ground": "E2BF65", "flying": "A98FF3", "psychic": "F95587", "bug": "A6B91A",
    "rock": "B6A136", "ghost": "735797", "dragon": "6F35FC", "dark": "705746", "steel": "B7B7CE", "fairy": "D685AD",
}

MEGA = {
    "Abomasite": ("7FC8D8", "F4F7F7"), "Absolite": ("3A3F58", "E8ECF2"), "Aerodactylite": ("9C8AB8", "D9D3E6"),
    "Aggronite": ("6A7078", "D8DCE0"), "Alakazite": ("D8A030", "8A5A20"), "Altarianite": ("6FB8E8", "F4F7FA"),
    "Ampharosite": ("F4C430", "F4F7FA"), "Audinite": ("F0A0B8", "F8F0E0"), "Banettite": ("4A4A5A", "E8C840"),
    "Beedrillite": ("E8C030", "2A2A2A"), "Blastoisinite": ("3878C8", "B88A50"), "Blazikenite": ("D84820", "F0D040"),
    "Cameruptite": ("A83028", "6A4A30"), "Charizardite X": ("2A2A38", "3090E0"), "Charizardite Y": ("E87820", "F8D848"),
    "Diancite": ("E878B0", "F0F0F8"), "Galladite": ("48A868", "E8E8F0"), "Garchompite": ("3A4A88", "D83838"),
    "Gardevoirite": ("58B878", "F4F7FA"), "Gengarite": ("6A4890", "D83050"), "Glalitite": ("88C8E8", "384878"),
    "Gyaradosite": ("3070C8", "E8E0B0"), "Heracronite": ("3050A8", "E8C030"), "Houndoominite": ("383038", "E85828"),
    "Kangaskhanite": ("A88858", "E8D8A8"), "Latiasite": ("D85868", "F4F7FA"), "Latiosite": ("4878D8", "F4F7FA"),
    "Lopunnite": ("B88858", "F0E0B8"), "Lucarionite": ("3878C0", "E8C040"), "Manectite": ("48B0C0", "F0D840"),
    "Mawilite": ("E060A0", "2A2A38"), "Medichamite": ("D85878", "F0F0F0"), "Metagrossite": ("4878B0", "C8D0D8"),
    "Mewtwonite X": ("A890C8", "3060C0"), "Mewtwonite Y": ("A890C8", "D85090"), "Pidgeotite": ("C89858", "E85838"),
    "Pinsirite": ("A08870", "E8D8C0"), "Sablenite": ("483878", "58D8D0"), "Salamencite": ("3890B8", "D84848"),
    "Sceptilite": ("389850", "D84848"), "Scizorite": ("C83030", "C8C8D0"), "Sharpedonite": ("304878", "E8E0B0"),
    "Slowbronite": ("E890A8", "C8C8D0"), "Steelixite": ("90A0B0", "E8D040"), "Swampertite": ("3880D0", "E88830"),
    "Tyranitarite": ("688858", "384030"), "Venusaurite": ("48A080", "E85878"),
}

Z_TYPES = {
    "Buginium Z": "bug", "Darkinium Z": "dark", "Dragonium Z": "dragon", "Electrium Z": "electric", "Fairium Z": "fairy",
    "Fightinium Z": "fighting", "Firium Z": "fire", "Flyinium Z": "flying", "Ghostium Z": "ghost", "Grassium Z": "grass",
    "Groundium Z": "ground", "Icium Z": "ice", "Normalium Z": "normal", "Poisonium Z": "poison", "Psychium Z": "psychic",
    "Rockium Z": "rock", "Steelium Z": "steel", "Waterium Z": "water",
}
Z_OWN = {
    "Aloraichium Z": ("F0C030", "B07830"), "Decidium Z": ("6A9A58", "6A4A38"), "Eevium Z": ("B88858", "F0E0C0"),
    "Incinium Z": ("D84830", "303030"), "Kommonium Z": ("B8A060", "E8D040"), "Lunalium Z": ("6A58C0", "D8D8F0"),
    "Lycanium Z": ("C89868", "F0E8D8"), "Marshadium Z": ("505050", "58D088"), "Mewnium Z": ("F0A8C8", "F8F0F8"),
    "Mimikium Z": ("E8D088", "584030"), "Pikanium Z": ("F7D02C", "D84830"), "Pikashunium Z": ("F7D02C", "303030"),
    "Primarium Z": ("58A8D8", "F0F4F8"), "Snorlium Z": ("386878", "F0E0C0"), "Solganium Z": ("F0A030", "F8F0D0"),
    "Tapunium Z": ("E8C840", "383838"), "Ultranecrozium Z": ("F0E060", "F8F8F0"),
}
PLATES = {
    "Draco Plate": "dragon", "Dread Plate": "dark", "Earth Plate": "ground", "Fist Plate": "fighting", "Flame Plate": "fire",
    "Icicle Plate": "ice", "Insect Plate": "bug", "Iron Plate": "steel", "Meadow Plate": "grass", "Mind Plate": "psychic",
    "Pixie Plate": "fairy", "Sky Plate": "flying", "Splash Plate": "water", "Spooky Plate": "ghost", "Stone Plate": "rock",
    "Toxic Plate": "poison", "Zap Plate": "electric",
}
MEMORIES = {t.capitalize() + " Memory": t for t in TYPES if t != "normal"}
DRIVES = {"Burn Drive": "fire", "Chill Drive": "ice", "Douse Drive": "water", "Shock Drive": "electric"}
ORBS = {
    "Adamant Orb": ("70A0D0", "D0D8E0"), "Lustrous Orb": ("E0A0C0", "F4E4EC"), "Griseous Orb": ("D8B040", "504870"),
    "Red Orb": ("D03838", "F0A050"), "Blue Orb": ("3058C8", "70D0E8"), "Soul Dew": ("D8D0F0", "80A0E0"),
    "Adrenaline Orb": ("E87830", "F8E048"),
}


def rgb(value, factor=1.0):
    return tuple(min(255, int(int(value[i:i + 2], 16) * factor)) for i in (0, 2, 4)) + (255,)


def canvas():
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def ball(d, main, cx=16, cy=16, r=13):
    d.ellipse((cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1), fill=rgb(main, 0.35))
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=rgb(main, 0.72))
    d.ellipse((cx - r + 1, cy - r + 1, cx + r - 3, cy + r - 3), fill=rgb(main))
    d.ellipse((cx - r + 4, cy - r + 3, cx - r + 10, cy - r + 7), fill=rgb(main, 1.55))


def mega(a, b):
    img, d = canvas()
    ball(d, a)
    # An original mark: two strands twisting up the stone.
    for y in range(8, 25):
        off = round(4.2 * __import__("math").sin((y - 8) / 16 * 6.283))
        d.rectangle((16 + off - 1, y, 16 + off, y), fill=rgb(b))
        d.rectangle((16 - off - 1, y, 16 - off, y), fill=rgb(b, 0.75))
    return img


def crystal(a, b):
    img, d = canvas()
    outline = [(16, 2), (27, 11), (27, 21), (16, 30), (5, 21), (5, 11)]
    d.polygon(outline, fill=rgb(a, 0.4))
    d.polygon([(16, 4), (25, 12), (25, 20), (16, 28), (7, 20), (7, 12)], fill=rgb(a))
    d.polygon([(16, 4), (7, 12), (16, 15)], fill=rgb(a, 1.4))
    d.polygon([(16, 4), (25, 12), (16, 15)], fill=rgb(a, 1.15))
    d.polygon([(7, 20), (16, 28), (16, 19)], fill=rgb(a, 0.8))
    d.polygon([(25, 20), (16, 28), (16, 19)], fill=rgb(a, 0.65))
    d.rectangle((11, 12, 21, 13), fill=rgb(b))
    d.polygon([(19, 14), (21, 14), (13, 19), (11, 19)], fill=rgb(b))
    d.rectangle((11, 19, 21, 20), fill=rgb(b))
    return img


def plate(t):
    c = TYPES[t]
    img, d = canvas()
    d.rounded_rectangle((4, 5, 27, 27), 3, fill=rgb(c, 0.4))
    d.rounded_rectangle((5, 6, 26, 26), 3, fill=rgb(c))
    d.rectangle((7, 8, 24, 9), fill=rgb(c, 1.4))
    d.polygon([(16, 11), (21, 16), (16, 22), (11, 16)], fill=rgb(c, 0.6))
    d.polygon([(16, 13), (19, 16), (16, 20), (13, 16)], fill=rgb(c, 1.35))
    return img


def memory(t):
    c = TYPES[t]
    img, d = canvas()
    d.ellipse((3, 3, 28, 28), fill=(70, 74, 88, 255))
    d.ellipse((4, 4, 27, 27), fill=(205, 210, 222, 255))
    d.pieslice((6, 6, 25, 25), 200, 340, fill=rgb(c))
    d.pieslice((6, 6, 25, 25), 20, 160, fill=rgb(c, 0.75))
    d.ellipse((12, 12, 19, 19), fill=(70, 74, 88, 255))
    d.ellipse((14, 14, 17, 17), fill=(0, 0, 0, 0))
    return img


def drive(t):
    c = TYPES[t]
    img, d = canvas()
    d.rectangle((6, 4, 25, 27), fill=(52, 55, 68, 255))
    d.rectangle((7, 5, 24, 26), fill=(112, 118, 136, 255))
    d.rectangle((9, 7, 22, 16), fill=rgb(c))
    d.rectangle((9, 7, 22, 8), fill=rgb(c, 1.4))
    for x in range(9, 23, 3):
        d.rectangle((x, 20, x + 1, 25), fill=(226, 196, 92, 255))
    return img


def orb(a, b):
    img, d = canvas()
    ball(d, a)
    d.ellipse((11, 13, 22, 24), fill=rgb(b))
    d.ellipse((13, 15, 20, 22), fill=rgb(b, 1.3))
    return img


def booster():
    img, d = canvas()
    d.rounded_rectangle((10, 3, 21, 28), 5, fill=(50, 54, 66, 255))
    d.rounded_rectangle((11, 4, 20, 27), 4, fill=(226, 230, 238, 255))
    d.rectangle((11, 12, 20, 19), fill=(236, 188, 50, 255))
    d.polygon([(17, 11), (12, 16), (15, 16), (14, 21), (19, 15), (16, 15)], fill=(255, 248, 190, 255))
    return img


def mask(main, accent):
    img, d = canvas()
    d.polygon([(6, 8), (11, 3), (21, 3), (26, 8), (26, 19), (16, 29), (6, 19)], fill=rgb(main, 0.4))
    d.polygon([(7, 8), (12, 4), (20, 4), (25, 8), (25, 18), (16, 27), (7, 18)], fill=rgb(main))
    d.polygon([(10, 11), (14, 12), (14, 15), (10, 14)], fill=(24, 24, 30, 255))
    d.polygon([(22, 11), (18, 12), (18, 15), (22, 14)], fill=(24, 24, 30, 255))
    d.polygon([(16, 5), (19, 9), (16, 11), (13, 9)], fill=rgb(accent))
    d.rectangle((13, 19, 19, 20), fill=rgb(accent))
    return img


def sword():
    img, d = canvas()
    d.polygon([(25, 3), (28, 6), (13, 21), (10, 18)], fill=(142, 96, 62, 255))
    d.polygon([(25, 3), (28, 3), (28, 6), (12, 20)], fill=(186, 128, 84, 255))
    d.line((7, 17, 14, 24), fill=(214, 178, 76, 255), width=3)
    d.line((8, 22, 4, 26), fill=(92, 66, 48, 255), width=3)
    return img


def shield():
    img, d = canvas()
    d.polygon([(5, 5), (16, 3), (27, 5), (27, 17), (16, 29), (5, 17)], fill=(92, 62, 42, 255))
    d.polygon([(7, 7), (16, 5), (25, 7), (25, 16), (16, 26), (7, 16)], fill=(160, 104, 66, 255))
    d.line((16, 6, 16, 25), fill=(214, 178, 76, 255), width=2)
    d.line((8, 12, 24, 12), fill=(214, 178, 76, 255), width=2)
    return img


def globe(main, accent):
    img, d = canvas()
    ball(d, main, r=11, cy=14)
    d.arc((5, 3, 27, 25), 20, 160, fill=rgb(accent), width=2)
    d.polygon([(10, 25), (22, 25), (24, 29), (8, 29)], fill=rgb(accent))
    return img


def glove():
    img, d = canvas()
    d.rounded_rectangle((8, 6, 24, 21), 5, fill=(150, 30, 34, 255))
    d.rounded_rectangle((9, 7, 23, 20), 4, fill=(216, 56, 56, 255))
    d.rectangle((10, 21, 22, 27), fill=(238, 232, 214, 255))
    d.line((13, 8, 13, 14), fill=(150, 30, 34, 255))
    d.line((18, 8, 18, 14), fill=(150, 30, 34, 255))
    return img


ICONS = {}
for name, (a, b) in MEGA.items():
    ICONS[name] = mega(a, b)
for name, t in Z_TYPES.items():
    ICONS[name] = crystal(TYPES[t], "F8F8F8")
for name, (a, b) in Z_OWN.items():
    ICONS[name] = crystal(a, b)
for name, t in PLATES.items():
    ICONS[name] = plate(t)
for name, t in MEMORIES.items():
    ICONS[name] = memory(t)
for name, t in DRIVES.items():
    ICONS[name] = drive(t)
for name, (a, b) in ORBS.items():
    ICONS[name] = orb(a, b)
ICONS["Booster Energy"] = booster()
ICONS["Cornerstone Mask"] = mask("8A8E96", "D8C070")
ICONS["Hearthflame Mask"] = mask("B83828", "F0B040")
ICONS["Wellspring Mask"] = mask("2F78B8", "90D8E8")
ICONS["Rusted Sword"] = sword()
ICONS["Rusted Shield"] = shield()
ICONS["Adamant Crystal"] = crystal("70A0D0", "D0D8E0")
ICONS["Lustrous Globe"] = globe("E0A0C0", "A07890")
ICONS["Griseous Core"] = globe("D8B040", "504870")
ICONS["Lucky Punch"] = glove()


def ident(name):
    return re.sub(r"[^a-z0-9]", "", name.lower())


OUT.mkdir(parents=True, exist_ok=True)
for name, img in ICONS.items():
    img.save(OUT / (ident(name) + ".png"))

entries = ",\n".join(f'\t\t\tMap.entry("{ident(n)}", "{n}")' for n in sorted(ICONS, key=ident))
java = JAVA.read_text(encoding="utf-8")
java = re.sub(r"(// GENERATED BEGIN[^\n]*\n).*?(\t*// GENERATED END)", lambda m: m.group(1) + entries + ");\n" + m.group(2), java, flags=re.S)
JAVA.write_text(java, encoding="utf-8", newline="\n")
print(len(ICONS), "icons")

if len(sys.argv) > 1:
    cols = 16
    rows = (len(ICONS) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * 66, rows * 66), (58, 62, 72, 255))
    for i, name in enumerate(sorted(ICONS, key=ident)):
        icon = ICONS[name].resize((64, 64), Image.NEAREST)
        sheet.paste(icon, (i % cols * 66, i // cols * 66), icon)
    sheet.save(sys.argv[1])
