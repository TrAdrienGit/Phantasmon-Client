#!/usr/bin/env python3
"""Build the "Phantasmon Megas" resource pack from Mega Showdown assets.

Takes ONLY the new Mega Evolutions (Legends Z-A / Champions) from the Mega
Showdown mod at a pinned commit: resolvers, posers, models, animations and
textures. Resolver files are renamed with a high, distinct order and a
"phantasmon" filename so they ADD variations next to other packs (CCC...)
and never replace one of their files.

Usage:
    python tools/mega_pack.py              # folder pack at build/phantasmon-megas/
    python tools/mega_pack.py --zip        # also build/phantasmon-megas.zip
    python tools/mega_pack.py --install    # copy into the game instance
    python tools/mega_pack.py --no-conflict-check

Standard library only; downloads use `gh api` (tree) and raw.githubusercontent.com.
The pack is CC BY-NC-SA 4.0 material: never publish it on Modrinth/CurseForge.
"""
import argparse
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor

REPO = "yajatkaul/CobblemonMegaShowdown"
COMMIT = "415c8898032ab47accdf8ce723bd0ea5f4aabe07"
RAW = f"https://raw.githubusercontent.com/{REPO}/{COMMIT}/"
ASSETS = "common/src/main/resources/assets/cobblemon/"
BEDROCK = ASSETS + "bedrock/pokemon/"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUILD = os.path.join(ROOT, "build")
CACHE = os.path.join(BUILD, "mega-pack-cache")
OUT = os.path.join(BUILD, "phantasmon-megas")
OUT_ZIP = os.path.join(BUILD, "phantasmon-megas.zip")
INSTANCE_PACKS = "C:/Users/adrie/Minecraft/Phantasmon/resourcepacks"
INSTALL_NAME = "Phantasmon Megas"

# Distinct, high order: our variations come after everything else, and they
# only match when the mega aspect is present.
RESOLVER_ORDER = 900

# Showdown id -> resolver path relative to bedrock/pokemon/resolvers/ (None =
# no resolver known up front; the tree is searched to confirm).
FORMS = {
    "raichumegax": "0026_raichu/2_raichu_mega_x.json",
    "raichumegay": "0026_raichu/3_raichu_mega_y.json",
    "clefablemega": "0036_clefable/7_clefable_mega.json",
    "victreebelmega": "0071_victreebel/1_victreebel_mega.json",
    "starmiemega": "0121_starmie/1_starmie_mega.json",
    "dragonitemega": "0149_dragonite/1_dragonite_mega.json",
    "meganiummega": "0154_meganium/1_meganium_mega.json",
    "feraligatrmega": "0160_feraligatr/1_feraligatr_mega.json",
    "skarmorymega": "0227_skarmory/1_skarmory_mega.json",
    "chimechomega": "0358_chimecho/1_chimecho_mega.json",
    "absolmegaz": "0359_absol/2_absol_mega_z.json",
    "staraptormega": "0398_staraptor/1_staraptor_mega.json",
    "garchompmegaz": "0445_garchomp/3_garchomp_mega_z.json",
    "lucariomegaz": "0448_lucario/2_lucario_mega_z.json",
    "froslassmega": "0478_froslass/1_froslass_mega.json",
    "heatranmega": None,
    "darkraimega": None,
    "emboarmega": "0500_emboar/1_emboar_mega.json",
    "excadrillmega": "0530_excadrill/1_excadrill_mega.json",
    "scolipedemega": "0545_scolipede/1_scolipede_mega.json",
    "scraftymega": "0560_scrafty/1_scrafty_mega.json",
    "eelektrossmega": "0604_eelektross/1_eelektross_mega.json",
    "chandeluremega": "0609_chandelure/1_chandelure_mega.json",
    "golurkmega": "0623_golurk/1_golurk_mega.json",
    "chesnaughtmega": "0652_chesnaught/1_chesnaught_mega.json",
    "delphoxmega": "0655_delphox/2_delphox_mega.json",
    "greninjamega": "0658_greninja/2_greninja_mega.json",
    "pyroarmega": "0668_pyroar/2_pyroar_mega.json",
    "floettemega": "0670_floette/2_floette_eternal_mega.json",
    # One resolver (aspect "mega") serves both genders.
    "meowsticmmega": "0678_meowstic/3_meowstic_mega.json",
    "meowsticfmega": "0678_meowstic/3_meowstic_mega.json",
    "malamarmega": "0687_malamar/1_malamar_mega.json",
    "barbaraclemega": "0689_barbaracle/1_barbaracle_mega.json",
    "dragalgemega": "0691_dragalge/1_dragalge_mega.json",
    "hawluchamega": "0701_hawlucha/1_hawlucha_mega.json",
    "zygardemega": None,
    "crabominablemega": "0740_crabominable/1_crabominable_mega.json",
    "golisopodmega": "0768_golisopod/1_golisopod_mega.json",
    "drampamega": "0780_drampa/2_drampa_mega.json",
    "magearnamega": None,
    "magearnaoriginalmega": None,
    "zeraoramega": None,
    "falinksmega": "0870_falinks/1_falinks_mega.json",
    "scovillainmega": "0952_scovillain/1_scovillain_mega.json",
    "glimmoramega": "0970_glimmora/1_glimmora_mega.json",
    "tatsugiricurlymega": None,
    "tatsugiridroopymega": None,
    "tatsugiristretchymega": None,
    "baxcaliburmega": "0998_baxcalibur/1_baxcalibur_mega.json",
}
# Species searched in the whole tree when a form has no known resolver.
SEARCH_SPECIES = {
    "heatranmega": "heatran", "darkraimega": "darkrai", "zygardemega": "zygarde",
    "magearnamega": "magearna", "magearnaoriginalmega": "magearna",
    "zeraoramega": "zeraora", "tatsugiricurlymega": "tatsugiri",
    "tatsugiridroopymega": "tatsugiri", "tatsugiristretchymega": "tatsugiri",
}

CREDITS = """Phantasmon Megas - credits and license
======================================

This resource pack ORIGINATES FROM MEGA SHOWDOWN.
The models, animations, posers, resolvers and textures in it were taken
unchanged (except for renamed resolver files and their "order" value) from the
Mega Showdown mod:

  Mod:     Mega Showdown (Cobblemon Mega Showdown)
  Author:  Yajat Kaul (yajatkaul) and the individual asset authors
  Repo:    https://github.com/yajatkaul/CobblemonMegaShowdown
  Commit:  {commit}
  License: https://github.com/yajatkaul/CobblemonMegaShowdown/blob/{commit}/LICENSE.md
           (an intact copy is included next to this file as LICENSE.md)

Per-model credits (the artists of each model and texture) are listed by the
Mega Showdown author here:
  https://docs.google.com/spreadsheets/d/1VPRia2W_7wqhbwSGv1845qmXw_FtjZ4FDhr58fRZFSM/edit?usp=sharing

License
-------
Under section 1.4 of the Mega Showdown License, the textures, models,
animations and other assets original to Mega Showdown are licensed under the
Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International
license (CC BY-NC-SA 4.0): https://creativecommons.org/licenses/by-nc-sa/4.0/

  * No commercial use of any kind (no paywall, donation reward, crate key).
  * You must credit the original authors (see above).
  * Anything built on these assets must be shared under the same license.

This pack is a private, non-commercial companion for Phantasmon. It must not
be published on public platforms (Modrinth, CurseForge...) without the prior
written approval of the Mega Showdown author.

Forms covered ({n_covered}):
{covered}

Forms without a Mega Showdown model ({n_missing}):
{missing}
"""


# --------------------------------------------------------------------------
# Download helpers
# --------------------------------------------------------------------------

def get_tree():
    cache = os.path.join(CACHE, "tree.json")
    if not os.path.exists(cache):
        os.makedirs(CACHE, exist_ok=True)
        out = subprocess.run(
            ["gh", "api", f"repos/{REPO}/git/trees/{COMMIT}?recursive=1"],
            check=True, capture_output=True).stdout
        with open(cache, "wb") as f:
            f.write(out)
    with open(cache, "rb") as f:
        tree = json.load(f)
    if tree.get("truncated"):
        sys.exit("git tree is truncated, cannot continue")
    return {x["path"] for x in tree["tree"] if x["type"] == "blob"}


def fetch(path):
    """Return the bytes of a repo file at the pinned commit (disk cached)."""
    cache = os.path.join(CACHE, "files", path)
    if os.path.exists(cache):
        with open(cache, "rb") as f:
            return f.read()
    url = RAW + urllib.request.quote(path)
    last = None
    for _ in range(3):
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                data = r.read()
            os.makedirs(os.path.dirname(cache), exist_ok=True)
            with open(cache, "wb") as f:
                f.write(data)
            return data
        except Exception as e:  # noqa: BLE001
            last = e
    raise RuntimeError(f"download failed: {path}: {last}")


def fetch_many(paths):
    with ThreadPoolExecutor(8) as ex:
        list(ex.map(fetch, paths))


def load_json(path):
    return json.loads(fetch(path).decode("utf-8"))


# --------------------------------------------------------------------------
# Reference corpus: what Cobblemon's own jar already provides
# --------------------------------------------------------------------------

def find_cobblemon_jar():
    pat = os.path.expanduser(
        "~/.gradle/caches/modules-2/files-2.1/maven.modrinth/cobblemon/*/*/cobblemon-*.jar")
    jars = [j for j in glob.glob(pat) if not j.endswith("-sources.jar")]
    return jars[0] if jars else None


def basename_key(path, suffix):
    return os.path.basename(path)[: -len(suffix)]


class Corpus:
    """Names of animations / models / posers present in the Cobblemon jar."""

    def __init__(self, jar):
        self.animations, self.models, self.posers = set(), set(), set()
        self.molang = set()
        self.particles = set()
        self.jar = jar
        if not jar:
            return
        z = zipfile.ZipFile(jar)
        for n in z.namelist():
            if n.startswith("assets/cobblemon/bedrock/particles/") and n.endswith(".particle.json"):
                try:
                    self.particles.add(json.loads(z.read(n))["particle_effect"]["description"]["identifier"])
                except Exception:  # noqa: BLE001
                    pass
                continue
            if not n.startswith("assets/cobblemon/bedrock/pokemon/"):
                continue
            if n.endswith(".animation.json"):
                self.animations.add(basename_key(n, ".animation.json"))
            elif n.endswith(".geo.json"):
                self.models.add(basename_key(n, ".geo.json"))
            elif "/posers/" in n and n.endswith(".json"):
                self.posers.add(basename_key(n, ".json"))
                try:
                    self.molang |= molang_funcs(z.read(n).decode("utf-8", "replace"))
                except Exception:  # noqa: BLE001
                    pass
            elif n.endswith(".animation.json") is False and "/animations/" in n:
                pass
        # Animation files also carry Molang.
        for n in z.namelist():
            if n.startswith("assets/cobblemon/bedrock/pokemon/animations/") and n.endswith(".json"):
                self.molang |= molang_funcs(z.read(n).decode("utf-8", "replace"))


MOLANG_RE = re.compile(r"\b((?:q|query|math|v|variable|t|temp|c|context)\.[A-Za-z_][\w]*)", re.I)


def molang_funcs(text):
    return {m.group(1).lower() for m in MOLANG_RE.finditer(text)}


# --------------------------------------------------------------------------
# Build
# --------------------------------------------------------------------------

BEDROCK_REF_RE = re.compile(r"bedrock\w*\(\s*'([^']+)'")
COBBLE_ID_RE = re.compile(r'"cobblemon:([^"]+)"')


def texture_paths(node):
    """All texture identifiers (cobblemon:textures/...) found in a resolver,
    including animated textures given as {"frames": [...]}."""
    out = []
    if isinstance(node, str):
        if node.startswith("cobblemon:textures/"):
            out.append(node)
    elif isinstance(node, dict):
        for v in node.values():
            out += texture_paths(v)
    elif isinstance(node, list):
        for v in node:
            out += texture_paths(v)
    return out


def build(args):
    tree = get_tree()
    corpus = Corpus(find_cobblemon_jar())
    if not corpus.jar:
        print("WARNING: Cobblemon jar not found, cannot tell which references are already provided")

    # name -> repo path indexes of the Mega Showdown tree.
    anim_idx = {basename_key(p, ".animation.json"): p for p in tree
                if p.startswith(BEDROCK + "animations/") and p.endswith(".animation.json")}
    model_idx = {basename_key(p, ".geo.json"): p for p in tree
                 if p.startswith(BEDROCK + "models/") and p.endswith(".geo.json")}
    poser_idx = {basename_key(p, ".json"): p for p in tree
                 if p.startswith(BEDROCK + "posers/") and p.endswith(".json")}

    # Confirm missing forms by scanning every resolver of the tree.
    resolver_paths = sorted(p for p in tree if p.startswith(BEDROCK + "resolvers/") and p.endswith(".json"))
    wanted_species = set(SEARCH_SPECIES.values())
    fetch_many(resolver_paths)
    found_elsewhere = {}
    for rp in resolver_paths:
        try:
            r = json.loads(fetch(rp).decode("utf-8"))
        except Exception:  # noqa: BLE001
            continue
        sp = str(r.get("species", "")).split(":")[-1]
        if sp in wanted_species:
            for v in r.get("variations", []):
                if any(a.startswith("mega") for a in v.get("aspects", [])) or "mega" in str(v.get("model", "")):
                    found_elsewhere.setdefault(sp, []).append(rp)

    covered, missing = [], []
    needed = {}   # published path -> (kind, source repo path or bytes)
    risks = {}    # form file -> set of molang functions not in Cobblemon jar
    notes = []

    resolver_cache = {}
    for form, rel in FORMS.items():
        if rel is None:
            sp = SEARCH_SPECIES[form]
            if sp in found_elsewhere:
                notes.append(f"{form}: mega resolver found by search: {found_elsewhere[sp]} (add it to FORMS)")
            missing.append(form)
            continue
        rpath = BEDROCK + "resolvers/" + rel
        if rpath not in tree:
            missing.append(form)
            notes.append(f"{form}: resolver {rel} not found in tree")
            continue
        covered.append(form)
        if rpath in resolver_cache:
            continue
        resolver = load_json(rpath)
        resolver_cache[rpath] = resolver

    for rpath, resolver in resolver_cache.items():
        rel = rpath[len(BEDROCK + "resolvers/"):]
        folder, fname = rel.split("/")
        stem = re.sub(r"^\d+_", "", fname[:-5])
        new_name = f"{RESOLVER_ORDER}_phantasmon_{stem}.json"
        out_resolver = dict(resolver)
        out_resolver["order"] = RESOLVER_ORDER
        # Keep key order: species, order, variations.
        out_resolver = {"species": resolver["species"], "order": RESOLVER_ORDER,
                        **{k: v for k, v in resolver.items() if k not in ("species", "order")}}
        needed[f"assets/cobblemon/bedrock/pokemon/resolvers/{folder}/{new_name}"] = (
            "bytes", (json.dumps(out_resolver, indent=2, ensure_ascii=False) + "\n").encode("utf-8"))

        # Model, poser.
        model_names, poser_names = set(), set()
        for v in resolver.get("variations", []):
            if "model" in v:
                model_names.add(v["model"].split(":")[-1].removesuffix(".geo"))
            if "poser" in v:
                poser_names.add(v["poser"].split(":")[-1])
        for m in model_names:
            if m in model_idx:
                needed["assets/cobblemon/" + model_idx[m][len(ASSETS):]] = ("copy", model_idx[m])
            elif m not in corpus.models:
                notes.append(f"{new_name}: model {m} found nowhere")
        # Textures.
        for t in texture_paths(resolver):
            src = ASSETS + t.split(":", 1)[1]
            if src in tree:
                needed["assets/cobblemon/" + t.split(":", 1)[1]] = ("copy", src)
            else:
                notes.append(f"{new_name}: texture {t} missing in repo")
        # Posers and their animation references.
        pending = list(poser_names)
        seen_poser = set()
        while pending:
            pn = pending.pop()
            if pn in seen_poser:
                continue
            seen_poser.add(pn)
            if pn not in poser_idx:
                if pn not in corpus.posers:
                    notes.append(f"{new_name}: poser {pn} found nowhere")
                continue
            ppath = poser_idx[pn]
            needed["assets/cobblemon/" + ppath[len(ASSETS):]] = ("copy", ppath)
            ptext = fetch(ppath).decode("utf-8")
            funcs = molang_funcs(ptext)
            for anim in set(BEDROCK_REF_RE.findall(ptext)):
                collect_animation(anim, anim_idx, corpus, needed, risks, notes, new_name)
            risks.setdefault(new_name, set()).update(funcs - corpus.molang)
            # A poser may point at other cobblemon ids (particles, models).
            for ref in set(COBBLE_ID_RE.findall(ptext)):
                if ref.startswith("textures/") or ref.endswith(".geo"):
                    notes.append(f"{new_name}: poser {pn} references {ref}")

    return covered, missing, needed, risks, notes, corpus


def collect_animation(name, anim_idx, corpus, needed, risks, notes, owner):
    if name in corpus.animations:
        return
    if name not in anim_idx:
        notes.append(f"{owner}: animation group {name} found nowhere")
        return
    apath = anim_idx[name]
    pub = "assets/cobblemon/" + apath[len(ASSETS):]
    if pub in needed:
        return
    needed[pub] = ("copy", apath)
    text = fetch(apath).decode("utf-8")
    risks.setdefault(owner, set()).update(molang_funcs(text) - corpus.molang)
    for ref in set(COBBLE_ID_RE.findall(text)):
        notes.append(f"{owner}: animation {name} references cobblemon:{ref} (particle/sound?)")
    # Particle effects: Cobblemon refuses the whole animation group when one is unknown, and a poser that then asks
    # for it crashes the game (Raichu-Mega-X, 2026-11-27). Each effect is shipped with its texture, or the build stops.
    for effect in particle_effects(json.loads(text)):
        pid = effect if ":" in effect else "cobblemon:" + effect
        if pid in corpus.particles:
            continue
        src = particle_index().get(pid)
        if src is None:
            sys.exit(f"{owner}: animation {name} uses particle effect {pid}, found nowhere")
        collect_particle(src, needed)


def particle_effects(node):
    """Every "effect" named under a "particle_effects" timeline of an animation file."""
    out = set()
    if isinstance(node, dict):
        for k, v in node.items():
            if k == "particle_effects" and isinstance(v, dict):
                for keyframe in v.values():
                    for e in (keyframe if isinstance(keyframe, list) else [keyframe]):
                        if isinstance(e, dict) and "effect" in e:
                            out.add(e["effect"])
            else:
                out |= particle_effects(v)
    elif isinstance(node, list):
        for v in node:
            out |= particle_effects(v)
    return out


_PARTICLES = None


def particle_index():
    """Particle identifier -> repo path, for every particle file of the Mega Showdown tree."""
    global _PARTICLES
    if _PARTICLES is None:
        paths = sorted(p for p in get_tree()
                       if p.startswith(ASSETS + "bedrock/particles/") and p.endswith(".particle.json"))
        fetch_many(paths)
        _PARTICLES = {}
        for p in paths:
            try:
                ident = load_json(p)["particle_effect"]["description"]["identifier"]
            except Exception:  # noqa: BLE001
                continue
            _PARTICLES.setdefault(ident, p)
    return _PARTICLES


def collect_particle(src, needed):
    """A particle file, and its texture: "textures/particles/x" is read by Cobblemon as textures/particle/x.png."""
    needed["assets/cobblemon/" + src[len(ASSETS):]] = ("copy", src)
    texture = load_json(src)["particle_effect"]["description"]["basic_render_parameters"]["texture"]
    ns, _, path = texture.rpartition(":")
    if ns not in ("", "cobblemon"):
        sys.exit(f"{src}: particle texture {texture} outside the cobblemon namespace")
    png = re.sub(r"^textures/particles/", "textures/particle/", path) + ".png"
    if ASSETS + png not in get_tree():
        sys.exit(f"{src}: particle texture {png} missing in repo")
    needed["assets/cobblemon/" + png] = ("copy", ASSETS + png)


def write_pack(covered, missing, needed):
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    copies = [src for kind, src in needed.values() if kind == "copy"]
    fetch_many(copies)
    for pub, (kind, src) in needed.items():
        dest = os.path.join(OUT, pub)
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        with open(dest, "wb") as f:
            f.write(src if kind == "bytes" else fetch(src))
    with open(os.path.join(OUT, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"pack": {"pack_format": 34,
                            "description": "Phantasmon Megas v1 \u2014 models from Mega Showdown (CC BY-NC-SA 4.0)"}},
                  f, indent=2, ensure_ascii=False)
        f.write("\n")
    with open(os.path.join(OUT, "LICENSE.md"), "wb") as f:
        f.write(fetch("LICENSE.md"))
    with open(os.path.join(OUT, "CREDITS.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(CREDITS.format(
            commit=COMMIT, n_covered=len(covered), n_missing=len(missing),
            covered="\n".join("  " + c for c in covered),
            missing="\n".join("  " + m for m in missing)))


def folder_size(path):
    return sum(os.path.getsize(os.path.join(r, f)) for r, _, fs in os.walk(path) for f in fs)


# --------------------------------------------------------------------------
# Conflict check against the packs of the game instance
# --------------------------------------------------------------------------

def other_packs():
    """Yield (pack name, set of file paths, reader) for each pack of the instance."""
    if not os.path.isdir(INSTANCE_PACKS):
        return
    for name in sorted(os.listdir(INSTANCE_PACKS)):
        full = os.path.join(INSTANCE_PACKS, name)
        if name == INSTALL_NAME:
            continue
        if os.path.isdir(full):
            files = {}
            for r, _, fs in os.walk(full):
                for f in fs:
                    rel = os.path.relpath(os.path.join(r, f), full).replace("\\", "/")
                    files[rel] = os.path.join(r, f)
            yield name, set(files), lambda p, files=files: open(files[p], "rb").read()
        elif name.lower().endswith(".zip"):
            z = zipfile.ZipFile(full)
            yield name, {n for n in z.namelist() if not n.endswith("/")}, lambda p, z=z: z.read(p)


def conflict_check(needed, jar):
    print("\n== Conflict check against instance packs and the Cobblemon jar ==")
    ours = set(needed)
    sources = list(other_packs())
    if jar:
        zj = zipfile.ZipFile(jar)
        sources.append(("cobblemon jar", {n for n in zj.namelist() if not n.endswith("/")},
                        lambda p, zj=zj: zj.read(p)))
    # Basename identity matters too: models/posers/animations are looked up by name.
    kinds = {"/models/": ".geo.json", "/posers/": ".json", "/animations/": ".animation.json"}
    for name, files, read in sources:
        exact = sorted(ours & files)
        same_name = []
        theirs_by_base = {}
        for f in files:
            if f.startswith("assets/cobblemon/bedrock/pokemon/"):
                for k in kinds:
                    if k in f and f.endswith(".json"):
                        theirs_by_base.setdefault((k, os.path.basename(f)), []).append(f)
        for o in ours:
            for k in kinds:
                if k in o and o.endswith(".json"):
                    for f in theirs_by_base.get((k, os.path.basename(o)), []):
                        if f != o:
                            same_name.append((o, f))
        print(f"- {name}: {len(exact)} same-path file(s), {len(same_name)} same-name/other-path file(s)")
        for e in exact:
            diff = "identical" if read(e) == (needed[e][1] if needed[e][0] == "bytes" else fetch(needed[e][1])) else "DIFFERENT"
            print(f"    same path ({diff}): {e}")
        for o, f in same_name[:20]:
            print(f"    same name: ours {o}  <->  {f}")
        # Species with resolvers there: report overlapping aspects.
        for o in sorted(ours):
            if "/resolvers/" not in o:
                continue
            ours_res = json.loads(needed[o][1].decode("utf-8"))
            sp = ours_res["species"]
            our_aspects = {tuple(sorted(v.get("aspects", []))) for v in ours_res["variations"]}
            for f in sorted(files):
                if "/bedrock/pokemon/resolvers/" in f and f.endswith(".json"):
                    try:
                        r = json.loads(read(f).decode("utf-8"))
                    except Exception:  # noqa: BLE001
                        continue
                    if r.get("species") != sp:
                        continue
                    theirs = {tuple(sorted(v.get("aspects", []))) for v in r.get("variations", [])}
                    shared = our_aspects & theirs
                    if shared and name != "cobblemon jar":
                        print(f"    aspects overlap on {sp}: {f} (order {r.get('order')}) shares {sorted(shared)}")


# --------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--zip", action="store_true", help="also write build/phantasmon-megas.zip")
    ap.add_argument("--install", action="store_true",
                    help=f"copy the pack folder into {INSTANCE_PACKS}/{INSTALL_NAME}")
    ap.add_argument("--no-conflict-check", action="store_true")
    args = ap.parse_args()

    covered, missing, needed, risks, notes, corpus = build(args)
    write_pack(covered, missing, needed)

    print(f"Pinned commit: {COMMIT}")
    print(f"Forms covered ({len(covered)}): {' '.join(covered)}")
    print(f"Forms missing a model ({len(missing)}): {' '.join(missing)}")
    counts = {}
    for pub in needed:
        k = ("resolvers" if "/resolvers/" in pub else "posers" if "/posers/" in pub
             else "models" if "/models/" in pub else "animations" if "/animations/" in pub else "textures")
        counts[k] = counts.get(k, 0) + 1
    print("Files:", ", ".join(f"{v} {k}" for k, v in counts.items()),
          f"- {folder_size(OUT) / 1024 / 1024:.1f} MiB")
    if notes:
        print("\nNotes:")
        for n in sorted(set(notes)):
            print("  " + n)
    print("\n== Molang functions used by our posers/animations but absent from the Cobblemon jar ==")
    all_risk = set()
    for owner, funcs in sorted(risks.items()):
        if funcs:
            print(f"  {owner}: {', '.join(sorted(funcs))}")
            all_risk |= funcs
    if not all_risk:
        print("  none")

    if not args.no_conflict_check:
        conflict_check(needed, corpus.jar)

    if args.zip:
        if os.path.exists(OUT_ZIP):
            os.remove(OUT_ZIP)
        with zipfile.ZipFile(OUT_ZIP, "w", zipfile.ZIP_DEFLATED) as z:
            for r, _, fs in os.walk(OUT):
                for f in fs:
                    full = os.path.join(r, f)
                    z.write(full, os.path.relpath(full, OUT).replace("\\", "/"))
        print(f"\nWrote {OUT_ZIP}")
    if args.install:
        dest = os.path.join(INSTANCE_PACKS, INSTALL_NAME)
        if os.path.isdir(dest):
            shutil.rmtree(dest)
        shutil.copytree(OUT, dest)
        print(f"\nInstalled into {dest}")


if __name__ == "__main__":
    main()
