#!/usr/bin/env python3
"""Génère phantasmon_trade_ui.html en HTML/CSS pur (aucun JavaScript)."""
import html

SPR = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/"
def mon(i): return dict(sprite=f"{SPR}pokemon/other/showdown/{i}.gif",
                        spriteShiny=f"{SPR}pokemon/other/showdown/shiny/{i}.gif",
                        icon=f"{SPR}pokemon/{i}.png")
def item(s): return f"{SPR}items/{s}.png"
def st(*v): return dict(zip(["pv","attaque","defense","atqSpe","defSpe","vitesse"],
                            [dict(iv=v[i], ev=v[i+1]) for i in range(0, 12, 2)]))

def P(name, gender, types, tera, held, icon, dex, nature, ability, moves, stats, shiny=False):
    return dict(name=name, level=100, gender=gender, types=types, teraType=tera,
                heldItem=held, itemIcon=item(icon), nature=nature, ability=ability,
                moves=moves, stats=stats, shiny=shiny, **mon(dex))

PLAYERS = {
 "p1": ("Joueur 1", [
  P("Airmure","♀",["acier","vol"],"eau","Restes","leftovers",227,"Rigide","Fermeté",
    ["Tête de Fer","Roc-Boulet","Atterrissage","Piège de Roc"],st(31,252,31,252,31,4,12,0,31,0,31,0)),
  P("Tyranocif","♂",["roche","tenebres"],"fee","Veste de Combat","assault-vest",248,"Prudent","Sable Volant",
    ["Lame de Roc","Mâchouille","Séisme","Poursuite"],st(31,252,31,4,31,0,10,0,31,252,31,0)),
  P("Cizayox","♂",["insecte","acier"],"acier","Orbe Vie","life-orb",212,"Rigide","Technicien",
    ["Pisto-Poing","Demi-Tour","Surpuissance","Danse Lames"],st(31,248,31,252,31,0,5,0,31,8,31,0)),
  P("Dracaufeu","♂",["feu","vol"],"feu","Grosses Bottes","heavy-duty-boots",6,"Timide","Force Soleil",
    ["Déflagration","Lame d'Air","Exploforce","Atterrissage"],st(31,0,0,0,31,4,31,252,31,0,31,252),shiny=True),
  P("Ectoplasma","♀",["spectre","poison"],"spectre","Mouchoir Choix","choice-scarf",94,"Modeste","Corps Maudit",
    ["Ball'Ombre","Bombe Beurk","Exploforce","Tour de Magie"],st(31,0,0,0,31,0,31,252,31,4,31,252)),
  P("Voltali","♂",["electrik"],"glace","Lunettes Choix","choice-specs",135,"Timide","Absorb-Volt",
    ["Tonnerre","Change-Éclair","Ball'Météo","Puissance Cachée"],st(31,0,0,0,31,4,31,252,31,0,31,252)),
 ]),
 "p2": ("Jason", [
  P("Carchacrok","♂",["dragon","sol"],"acier","Orbe Vie","life-orb",445,"Jovial","Peau Dure",
    ["Séisme","Colère","Lame de Roc","Danse Lames"],st(31,4,31,252,31,0,0,0,31,0,31,252)),
  P("Rayquaza","—",["dragon","vol"],"normal","Baie Sitrus","sitrus-berry",384,"Naïf","Air Lock",
    ["Draco-Ascension","Vitesse Extrême","Séisme","Danse Draco"],st(31,0,31,252,31,0,31,4,31,0,31,252)),
  P("Léviator","♂",["eau","vol"],"sol","Restes","leftovers",130,"Rigide","Intimidation",
    ["Cascade","Mâchouille","Rebond","Danse Draco"],st(31,88,31,220,31,0,15,0,31,0,31,200)),
  P("Ohmassacre","♂",["electrik"],"electrik","Ceinture Force","focus-sash",604,"Pudique","Lévitation",
    ["Zénith","Cage Éclair","Poing Éclair","Demi-Tour"],st(20,3,30,252,8,0,9,3,26,2,24,250)),
  P("Gaulet","♀",["plante","poison"],"eau","Boue Noire","black-sludge",591,"Calme","Régé-Force",
    ["Spore","Giga-Sangsue","Bain de Toxines","Poudre Dodo"],st(31,252,0,0,31,180,31,0,31,76,31,0)),
  P("Simiabraz","♂",["feu","combat"],"feu","Orbe Vie","life-orb",392,"Naïf","Brasier",
    ["Boutefeu","Close Combat","Surchauffe","Demi-Tour"],st(31,0,31,168,31,0,31,88,31,0,31,252)),
 ]),
}
DEFAULT = {"p1": 0, "p2": 3}

TYPES = {
 "normal":("Normal","#A8A77A"),"feu":("Feu","#EE8130"),"eau":("Eau","#6390F0"),"plante":("Plante","#7AC74C"),
 "electrik":("Électrik","#F7D02C"),"glace":("Glace","#96D9D6"),"combat":("Combat","#C22E28"),"poison":("Poison","#A33EA1"),
 "sol":("Sol","#E2BF65"),"vol":("Vol","#A98FF3"),"psy":("Psy","#F95587"),"insecte":("Insecte","#A6B91A"),
 "roche":("Roche","#B6A136"),"spectre":("Spectre","#735797"),"dragon":("Dragon","#6F35FC"),"tenebres":("Ténèbres","#705746"),
 "acier":("Acier","#B7B7CE"),"fee":("Fée","#D685AD"),"stellaire":("Stellaire","#40B5A5"),
}
STAT_NAMES = [("pv","PV"),("attaque","Attaque"),("defense","Défense"),
              ("atqSpe","Atq. Spé."),("defSpe","Déf. Spé."),("vitesse","Vitesse")]
e = html.escape

def lum(h):
    n = int(h[1:], 16)
    return .299*(n>>16&255)/255 + .587*(n>>8&255)/255 + .114*(n&255)/255

def badge(t):
    label, col = TYPES.get(t, (t, "#666666"))
    fg = "#000" if lum(col) > .6 else "#fff"
    return f'<span class="type" style="background:{col};color:{fg}">{e(label)}</span>'

def gender(g):
    return {"♂":'<span class="gender m">♂</span>', "♀":'<span class="gender f">♀</span>'}.get(g, "")

def slot(pl, i, p):
    star = "<span class='star-sm'>★</span> " if p["shiny"] else ""
    return f'''<label class="slot" for="{pl}-{i}">
          <div class="slot-top">{gender(p["gender"]) or "<span></span>"}<span>N.{p["level"]}</span></div>
          <img class="mon" src="{p["icon"]}" alt="{e(p["name"])}">
          <div class="slot-name">{star}{e(p["name"])}</div>
          <img class="slot-item" src="{p["itemIcon"]}" title="{e(p["heldItem"])}" alt="">
        </label>'''

def card(pl, i, p, owner):
    side = "left" if pl == "p1" else "right"
    rows = "".join(f'<div class="stat-row"><span>{n}</span><span class="iv">{p["stats"][k]["iv"]}</span>'
                   f'<span class="ev">{p["stats"][k]["ev"]}</span></div>' for k, n in STAT_NAMES)
    total = sum(p["stats"][k]["ev"] for k, _ in STAT_NAMES)
    src = p["spriteShiny"] if p["shiny"] else p["sprite"]
    star = '<span class="shiny-star" title="Chromatique">★</span>' if p["shiny"] else ""
    moves = "".join(f'<div class="move">{e(m)}</div>' for m in p["moves"])
    return f'''<div class="pokemon-card {side} c-{pl}-{i}">
        <div class="card-head">
          <span class="name">{e(p["name"])} {gender(p["gender"])}</span>
          <span class="owner-tag">{e(owner)}</span>
          <span class="level">Nv. {p["level"]}</span>
        </div>
        <div class="viewport">
          <div class="types">{"".join(badge(t) for t in p["types"])}</div>
          {star}
          <img src="{src}" alt="{e(p["name"])}">
        </div>
        <div class="info">
          <div class="meta-grid">
            <div class="info-box"><div class="label">Objet tenu</div><div class="value"><img src="{p["itemIcon"]}" alt="">{e(p["heldItem"])}</div></div>
            <div class="info-box"><div class="label">Nature</div><div class="value">{e(p["nature"])}</div></div>
            <div class="info-box"><div class="label">Talent</div><div class="value">{e(p["ability"])}</div></div>
            <div class="info-box"><div class="label">Téracristal</div><div class="value">{badge(p["teraType"])}</div></div>
          </div>
          <div class="moves-stats">
            <div class="info-box"><div class="label">Capacités</div><div class="moves">{moves}</div></div>
            <div class="info-box"><div class="label">IV / EV</div>
              <div class="stat-row stat-head"><span></span><span>IV</span><span>EV</span></div>
              <div class="stats">{rows}<div class="stat-row stat-total"><span>Total EV</span><span></span><span class="ev">{total}</span></div></div>
            </div>
          </div>
        </div>
      </div>'''

# ---------- Blocs générés ----------
inputs, slots, cards, names, sel_css = [], {}, {}, {}, []
for pl, (owner, team) in PLAYERS.items():
    slots[pl] = "\n        ".join(slot(pl, i, p) for i, p in enumerate(team))
    cards[pl] = "\n      ".join(card(pl, i, p, owner) for i, p in enumerate(team))
    names[pl] = "".join(f'<span class="n-{pl}-{i}">{e(p["name"])}</span>' for i, p in enumerate(team))
    for i in range(len(team)):
        chk = " checked" if DEFAULT[pl] == i else ""
        inputs.append(f'<input class="st" type="radio" name="{pl}" id="{pl}-{i}"{chk}>')
        sel_css.append(f'#{pl}-{i}:checked~.trade-ui label[for="{pl}-{i}"]')
card_sel = [s.replace(' label[for="', ' .c-').replace('"]', '') for s in sel_css]
name_sel = [s.replace('.trade-ui label[for="', '#modal .n-').replace('"]', '') for s in sel_css]

CSS = r"""
:root{
  --cyan:#50E6FF; --cyan2:#2FB7C9;
  --white:#FFF; --muted:#9FD9E6; --dim:#999;
  --title:#5FE6F2; --text2:#CCC;
  --iv:#88CCFF; --ev:#FFCC88; --danger:#FF5078;
}
*{box-sizing:border-box}
html,body{margin:0;width:100%;height:100%;overflow:hidden}
body{
  background:
    radial-gradient(circle at 50% 42%,rgba(24,84,112,.22),transparent 43%),
    linear-gradient(135deg,#02070d,#071523 55%,#02060b);
  color:var(--white);
  font-family:"Trebuchet MS",Arial,sans-serif;
  display:flex;align-items:center;justify-content:center;
}
.st{display:none}
label{cursor:pointer;user-select:none}

/* Panneau racine (max 1080p) */
.trade-ui{
  width:min(1600px,100vw);height:min(900px,100vh);
  max-width:1920px;max-height:1080px;
  padding:13px;position:relative;
  background:rgba(8,20,34,.41);
  border:2px solid rgba(80,230,255,.75);
  box-shadow:0 0 0 1px rgba(80,230,255,.12) inset,0 0 35px rgba(0,0,0,.7);
}
.trade-ui:before,.trade-ui:after{
  content:"";position:absolute;top:-2px;width:34%;height:4px;
  background:var(--cyan);box-shadow:0 0 13px var(--cyan)
}
.trade-ui:before{left:0}.trade-ui:after{right:0}

/* En-tête */
.header{height:48px;display:grid;grid-template-columns:1fr 170px 1fr;align-items:center;gap:12px;margin-bottom:9px}
.player-head{
  height:100%;display:flex;align-items:center;padding:0 16px;border:1px solid var(--cyan2);
  background:linear-gradient(110deg,rgba(12,54,80,.92),rgba(3,10,20,.96));
  box-shadow:inset 0 0 18px rgba(80,230,255,.06)
}
.player-head.right{justify-content:flex-end;background:linear-gradient(250deg,rgba(12,54,80,.92),rgba(3,10,20,.96))}
.player-name{font-weight:800;letter-spacing:1.5px}
.trade-btn{
  height:100%;width:100%;display:flex;align-items:center;justify-content:center;
  border:1px solid var(--cyan);color:var(--cyan);
  background:linear-gradient(#18466b,#0d2a42);font-size:13px;font-weight:900;letter-spacing:2px;
  box-shadow:0 0 12px rgba(80,230,255,.18);transition:.12s
}
.trade-btn:hover{background:linear-gradient(#205c8d,#123859)}
.trade-btn .t-idle{display:flex;align-items:center;gap:8px}
.trade-btn .t-ready{display:none}
.trade-btn .arrows{font-size:20px;line-height:1}
#trade:checked~.trade-ui .trade-btn{border-color:#76ffb0;box-shadow:0 0 15px rgba(118,255,176,.3);color:#baffd2}
#trade:checked~.trade-ui .t-idle{display:none}
#trade:checked~.trade-ui .t-ready{display:inline}

/* Corps */
.main{height:calc(100% - 112px);display:grid;grid-template-columns:194px minmax(0,1fr) 194px;gap:10px}
.team-rail{
  min-width:0;padding:8px;border:1px solid rgba(80,230,255,.48);
  background:linear-gradient(145deg,rgba(12,32,54,.96),rgba(3,10,20,.98));
  display:flex;flex-direction:column
}
.rail-title{
  font-size:12px;color:var(--muted);letter-spacing:2px;text-transform:uppercase;
  padding:5px 4px 8px;border-bottom:1px solid rgba(80,230,255,.25);margin-bottom:8px
}
.team{display:grid;grid-template-columns:1fr 1fr;grid-auto-rows:1fr;gap:7px;flex:1}
.slot{
  min-height:112px;position:relative;padding:5px;
  border:1px solid rgba(80,230,255,.35);background:rgba(14,60,78,.51);
  display:flex;flex-direction:column;align-items:center;justify-content:center;transition:.12s
}
.slot:hover{background:rgba(80,230,255,.35);border-color:var(--cyan)}
.slot-top{position:absolute;top:4px;left:6px;right:6px;display:flex;justify-content:space-between;font-size:10px;color:#cbeaf0}
.slot img.mon{width:72px;height:72px;object-fit:contain;filter:drop-shadow(0 7px 7px rgba(0,0,0,.65))}
.slot-name{font-size:11px;font-weight:800;white-space:nowrap;max-width:100%;overflow:hidden;text-overflow:ellipsis}
.star-sm{color:#FFDD33}
.slot-item{position:absolute;bottom:5px;right:5px;width:20px;height:20px;object-fit:contain}
.rail-foot{
  margin-top:8px;padding:7px;text-align:center;border:1px solid rgba(80,230,255,.25);
  color:var(--dim);font-size:11px;background:rgba(0,0,0,.2)
}

/* Fiches Pokémon */
.center{min-width:0;min-height:0;display:grid;grid-template-columns:1fr 1fr;gap:9px}
.side{min-width:0;min-height:0;display:grid}
.pokemon-card{
  min-width:0;min-height:0;display:none;grid-template-rows:42px 235px 1fr;
  border:1px solid var(--cyan2);background:linear-gradient(145deg,rgba(12,32,54,.98),rgba(3,10,20,.98));
  box-shadow:inset 0 0 24px rgba(80,230,255,.04)
}
.pokemon-card.left{border-top:3px solid #50bfff}
.pokemon-card.right{border-top:3px solid #ff6688}
.card-head{
  display:flex;align-items:center;justify-content:space-between;gap:8px;padding:0 11px;
  border-bottom:1px solid rgba(80,230,255,.28);background:rgba(2,8,15,.42)
}
.card-head .name{font-weight:900;letter-spacing:.6px;font-size:16px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.card-head .owner-tag{font-size:10px;color:var(--muted);letter-spacing:1.5px;text-transform:uppercase}
.level{font-size:13px;font-weight:800;white-space:nowrap}
.gender.m{color:#50bfff}.gender.f{color:#ff7594}
.viewport{
  position:relative;display:flex;align-items:center;justify-content:center;overflow:hidden;
  background:
    radial-gradient(circle at 50% 53%,rgba(80,230,255,.08),transparent 38%),
    radial-gradient(circle at center,transparent 35%,rgba(0,0,0,.43) 100%);
}
.viewport:before{
  content:"";position:absolute;width:210px;height:210px;border-radius:50%;
  border:1px solid rgba(80,230,255,.09);
  background:linear-gradient(#50e6ff12 1px,transparent 1px),linear-gradient(90deg,#50e6ff12 1px,transparent 1px);
  background-size:22px 22px;transform:rotate(45deg)
}
.viewport img{
  width:230px;height:205px;object-fit:contain;position:relative;z-index:1;
  filter:drop-shadow(0 14px 13px rgba(0,0,0,.85));animation:float 3.5s ease-in-out infinite
}
@keyframes float{50%{transform:translateY(-5px) scale(1.015)}}
.types{position:absolute;top:9px;left:9px;display:flex;gap:4px;z-index:2}
.type{
  display:inline-block;padding:3px 8px;border-radius:3px;font-size:10px;font-weight:900;text-transform:uppercase;
  border:1px solid rgba(0,0,0,.33);letter-spacing:.5px
}
.shiny-star{position:absolute;top:8px;right:10px;z-index:2;font-size:18px;color:#FFDD33;
  text-shadow:-1px 0 #785000,1px 0 #785000,0 -1px #785000,0 1px #785000}

/* Infos */
.info{min-height:0;display:grid;grid-template-rows:auto 1fr;gap:7px;padding:8px;border-top:1px solid rgba(80,230,255,.2)}
.meta-grid{display:grid;grid-template-columns:1fr 1fr;gap:6px}
.info-box{border:1px solid rgba(80,230,255,.2);background:rgba(0,8,15,.38);padding:6px 8px;min-width:0;min-height:0}
.label{font-size:10px;text-transform:uppercase;color:var(--title);letter-spacing:1.2px;margin-bottom:4px;font-weight:700}
.value{font-size:13px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;display:flex;align-items:center;gap:6px;min-height:20px}
.value img{width:20px;height:20px;object-fit:contain;flex:none}
.moves-stats{display:grid;grid-template-columns:1.1fr .9fr;gap:7px;min-height:0}
.moves{display:grid;gap:4px;align-content:start}
.move{padding:6px;text-align:center;font-size:12px;color:var(--text2);border:1px solid rgba(80,230,255,.12);background:#07131f;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.stat-row{display:grid;grid-template-columns:1fr 30px 34px;padding:3px;font-size:11px}
.stats .stat-row:nth-child(even){background:rgba(255,255,255,.03)}
.stat-row .iv{color:var(--iv);text-align:right}.stat-row .ev{color:var(--ev);text-align:right}
.stat-head{color:#6c8c96;font-size:10px}
.stat-head span:nth-child(n+2){text-align:right}
.stat-total{border-top:1px solid rgba(80,230,255,.2);margin-top:3px;color:var(--dim)}

/* Pied */
.footer{
  height:47px;margin-top:9px;display:flex;align-items:center;justify-content:flex-end;
  padding:0 12px;border:1px solid rgba(80,230,255,.3);background:rgba(3,10,20,.82)
}
.danger-btn,.std-btn{display:inline-block;padding:7px 18px;font-size:11px;font-weight:900;letter-spacing:1.5px}
.danger-btn{border:1px solid var(--danger);background:linear-gradient(#6B182A,#420D18)}
.danger-btn:hover{background:linear-gradient(#8D2038,#591222)}
.std-btn{border:1px solid var(--cyan);background:linear-gradient(#18466b,#0d2a42)}
.std-btn:hover{background:linear-gradient(#205c8d,#123859)}

/* Modales */
.modal{position:fixed;inset:0;background:rgba(0,5,10,.82);backdrop-filter:blur(5px);display:none;align-items:center;justify-content:center;z-index:50}
#trade:checked~#modal{display:flex;opacity:0;animation:appear .2s .7s forwards}
#quit:checked~#quitModal{display:flex}
@keyframes appear{to{opacity:1}}
.modal-card{width:520px;padding:25px;border:1px solid var(--cyan);background:linear-gradient(145deg,#0c2036,#030a14);box-shadow:0 0 45px rgba(80,230,255,.18);text-align:center}
.modal-title{color:var(--title);letter-spacing:2px;font-weight:900;margin-bottom:22px}
.transfer{height:90px;position:relative;display:flex;align-items:center;justify-content:space-between;padding:0 30px;border:1px solid rgba(80,230,255,.3);overflow:hidden}
.transfer:before{content:"";position:absolute;left:8%;right:8%;height:3px;background:linear-gradient(90deg,#50e6ff,#fff,#ff5078);box-shadow:0 0 13px #50e6ff}
.transfer b{position:relative;z-index:1;background:#081422;padding:0 6px}
.ball{position:relative;z-index:1;font-size:28px;animation:transfer 1.8s linear infinite}
@keyframes transfer{50%{transform:translateX(180px) rotate(360deg)}}
.modal p{color:var(--text2)}
.modal-actions{margin-top:22px;display:flex;justify-content:center;gap:10px}
#modal [class^="n-"]{display:none}

/* Quitter : masque toute l'interface */
#quitted:checked~*{display:none!important}

@media(max-width:1100px){.trade-ui{transform:scale(.88);transform-origin:center}}
"""

SEL_CSS = (
    "/* Sélection (générée) */\n"
    + ",\n".join(sel_css) + "{background:rgba(80,230,255,.45);border-color:#fff;box-shadow:0 0 13px rgba(80,230,255,.35) inset}\n"
    + ",\n".join(card_sel) + "{display:grid}\n"
    + ",\n".join(name_sel) + "{display:inline}\n"
)

PAGE = f"""<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Phantasmon — Échange Pokémon</title>
<style>{CSS}
{SEL_CSS}</style>
</head>
<body>
<!-- États de l'interface (aucun JavaScript) -->
{chr(10).join(inputs)}
<input class="st" type="checkbox" id="trade">
<input class="st" type="checkbox" id="quit">
<input class="st" type="checkbox" id="quitted">

<div class="trade-ui">
  <div class="header">
    <div class="player-head"><span class="player-name">JOUEUR 1</span></div>
    <label for="trade" class="trade-btn"><span class="t-idle"><span class="arrows">⇄</span>ÉCHANGER</span><span class="t-ready">PRÊT ✓</span></label>
    <div class="player-head right"><span class="player-name">JASON</span></div>
  </div>

  <div class="main">
    <aside class="team-rail">
      <div class="rail-title">Équipe · Joueur 1</div>
      <div class="team">
        {slots["p1"]}
      </div>
      <div class="rail-foot">Cliquez sur un Pokémon pour l'examiner</div>
    </aside>

    <section class="center">
      <div class="side">
      {cards["p1"]}
      </div>
      <div class="side">
      {cards["p2"]}
      </div>
    </section>

    <aside class="team-rail">
      <div class="rail-title">Équipe · Jason</div>
      <div class="team">
        {slots["p2"]}
      </div>
      <div class="rail-foot">Cliquez sur un Pokémon pour l'examiner</div>
    </aside>
  </div>

  <div class="footer">
    <label for="quit" class="danger-btn">QUITTER</label>
  </div>
</div>

<div id="modal" class="modal">
  <div class="modal-card">
    <div class="modal-title">ÉCHANGE EN COURS</div>
    <div class="transfer"><b>J1</b><span class="ball">◉</span><b>JASON</b></div>
    <p>Échange de {names["p1"]} contre {names["p2"]}…</p>
    <div class="modal-actions"><label for="trade" class="std-btn">FERMER</label></div>
  </div>
</div>

<div id="quitModal" class="modal">
  <div class="modal-card">
    <div class="modal-title">QUITTER L'ÉCHANGE ?</div>
    <p>L'échange en cours sera annulé.</p>
    <div class="modal-actions">
      <label for="quit" class="std-btn">ANNULER</label>
      <label for="quitted" class="danger-btn">QUITTER</label>
    </div>
  </div>
</div>
</body>
</html>
"""

with open("phantasmon_trade_ui.html", "w", encoding="utf-8") as f:
    f.write(PAGE)
print("ok", len(PAGE))
