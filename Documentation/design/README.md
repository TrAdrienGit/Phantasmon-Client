# Design et maquettes

Références visuelles des interfaces. Implémentation : [`architecture/gui-design-system.md`](../architecture/gui-design-system.md).

| Dossier | Contenu | Statut |
|---|---|---|
| [`trade-screen/`](trade-screen/SPEC_ECRAN_ECHANGE.md) | Maquette de l'écran d'échange (livrée par Adrien) : spécification cotée Z01-Z29, HTML de référence et son générateur Python, captures et mesures | **Référence active** : sa direction artistique (canevas 1600×900, couleurs, typographie) sert aux trois écrans (PC, éditeur, échange) |
| [`pc-prototype/`](pc-prototype/prototype_pc.html) | Prototype HTML/CSS du PC au style « wisp » (flou, lueurs, fond animé) | **Historique** : base du premier PC (2026-09-27), remplacé le 2026-10-02 par la direction artistique de l'écran d'échange |

## Contenu de `trade-screen/`

| Fichier | Rôle |
|---|---|
| `SPEC_ECRAN_ECHANGE.md` | Spécification : coordonnées, couleurs, textures, comportements, critères de validation (nom d'origine conservé, cité dans le code) |
| `source/phantasmon_trade_ui.html` | Maquette HTML + CSS sans JavaScript, à ouvrir dans un navigateur |
| `source/gen.py` | Générateur de la maquette (données de démonstration) |
| `reference/ref_0*.png` | Captures : zones numérotées, état par défaut, survol, échange en cours, quitter |
| `reference/measures.json` | Mesures relevées dans le navigateur |

## Palette de base

| Rôle | Couleur |
|---|---|
| Cyan principal (cadres, accents) | `#50E6FF` |
| Titres de section | `#5FE6F2` |
| Texte secondaire | `#9FD9E6` |
| IV / EV | `#88CCFF` / `#FFCC88` |
| Mâle / Femelle | `#50BFFF` / `#FF7594` |
| Étoile chromatique | `#FFDD33` |
| Prêt (bordure / texte) | `#76FFB0` / `#BAFFD2` |
| Danger | `#FF5078` |

Les couleurs de type ne suivent pas la maquette mais la palette officielle des jeux actuels
(`OFFICIAL_TYPE_COLORS` dans `PhantasmonCanvasScreen`).

## Modifier une texture

Les textures de l'écran d'échange sont **générées** : modifier les valeurs dans `scripts/generate_trade_textures.py`,
puis relancer le script ([`guides/building.md`](../guides/building.md) §5). Ne pas retoucher les PNG à la main.
