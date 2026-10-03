# Interfaces graphiques et « design system »

> Classes : `gui/PhantasmonCanvasScreen` (socle), `gui/PhantasmonPcScreen`, `gui/PhantasmonPcEditScreen`,
> `gui/PhantasmonTradeScreen`, `gui/PokemonGuiRendering`. Maquettes : [`design/`](../design/README.md).
> Vérifié le 2026-10-03.

## 1. Les trois écrans

| Écran | Ouverture | Disposition |
|---|---|---|
| **PC** (`PhantasmonPcScreen`) | Touche **P**, `/phantasmon pc` | Rail Équipe à gauche · fiche Pokémon au centre · grille 6×5 de la boîte à droite (◀ BOÎTE n / 16 ▶). En-tête : « PC · joueur », IMPORTER, compteurs. Pied : ligne d'état, ÉDITER / SUPPRIMER. Bouton ✕ pour fermer. |
| **Éditeur** (`PhantasmonPcEditScreen`) | Bouton ÉDITER du PC | Fiche en aperçu en direct à gauche · formulaire à droite. En-tête : IMPORTER, indicateur « modifications non enregistrées ». Pied : ANNULER / ENREGISTRER. |
| **Échange** (`PhantasmonTradeScreen`) | Acceptation d'une invitation d'échange | Rail équipe et fiche de chaque joueur, bouton ⇄ ÉCHANGER / PRÊT ✓, QUITTER, fenêtres « échange en cours » et « quitter ? » |

Les trois partagent la **même fiche Pokémon** : nom et sexe, propriétaire, niveau, modèle 3D animé, types,
chromatique, objet (icône + nom), nature (avec +/- colorés), talent, Téracristal (et Puissance Cachée dans le PC et
l'éditeur), 4 attaques avec leur type, tableau IV/EV et total EV.

## 2. Canevas

- Toute la mise en page est écrite dans l'espace **1600 × 900 px** de la maquette d'échange (cotes reprises de
  `design/trade-screen/SPEC_ECRAN_ECHANGE.md`).
- Une seule mise à l'échelle : `s/2 × MENU_SCALE` unités GUI par pixel, avec `s = min(1, largeur/800,
  hauteur/450)` et `MENU_SCALE = 0.8` ; le canevas est centré, le jeu flouté reste visible autour.
- Les coordonnées de la souris sont reconverties (`toCanvasX`/`toCanvasY`) avant tout test de survol ou de clic.
- **Aucun widget vanilla** : ils ne s'affichent pas correctement dans un canevas mis à l'échelle. Champs de saisie,
  listes déroulantes (avec recherche et barre de défilement) et boutons sont dessinés par l'écran.

## 3. Rendu : règles à respecter

| Règle | Raison |
|---|---|
| `blitTexture` fait toujours `flush()` avant de dessiner | En 1.21.1, `fill`/`drawString` sont différés alors que `blit` dessine immédiatement : sans flush, une texture passe **sous** un remplissage dessiné avant elle |
| Fenêtres modales et listes déroulantes à z = 2000 | Au-dessus des icônes d'objet et des modèles 3D |
| Traits via `outline()` / `hairline()` | Garantit au moins 1 pixel écran (sinon un trait de 1 px de canevas peut disparaître) |
| Texte à échelle entière, calé sur la grille de pixels (`snapTextScale`, `drawText`) | La police Minecraft n'est nette qu'à des échelles entières |
| Pas de gras pour le texte aligné à droite ou encadré | La police du modpack dessine le gras plus large que `font.width()` ; la mesure du gras ajoute une marge d'un pixel de police par caractère |
| Couleurs de type : palette officielle (`OFFICIAL_TYPE_COLORS`) | Couleurs des jeux actuels plutôt que celles de Cobblemon ; texte noir ou blanc selon la luminance |

## 4. Modèles 3D et icônes (`PokemonGuiRendering`)

- Miniatures animées par l'API de Cobblemon `PokemonGuiUtilsKt.drawProfilePokemon`, appelée **par réflexion** :
  son pont Kotlin `drawProfilePokemon$default` est `@JvmSynthetic`, donc invisible pour `javac`. Les paramètres
  (masque de valeurs par défaut, rotation, `FloatingState`) reproduisent ceux de l'écran PC de Cobblemon,
  relevés par décompilation.
- La taille se règle par un `PoseStack.scale` **autour** de l'appel, jamais par le paramètre `scale` interne
  (non linéaire, il décentre le modèle). Le cadrage (`*_MODEL_SCALE`, `*_MODEL_ANCHOR`) est figé dans les
  constantes en tête de chaque écran, après validation en jeu.
- `push`/`pop` du `PoseStack` dans un `finally` : un échec de rendu d'une icône ne doit pas corrompre le reste de
  l'écran.
- Formes : `resolveForm`/`formAspects` font la correspondance tolérante nom de forme ↔ aspects Cobblemon
  (`fairy` → plaque Fée, `wellspringtera` ↔ `Wellspring-Tera`). Sexe : `genderAspect`.
- Icônes d'objet : rendu d'`ItemStack` mis à l'échelle de la police.

## 5. Textures

| Dossier | Contenu | Origine |
|---|---|---|
| `assets/phantasmon/textures/gui/trade/` | Fond, rails, fiches, en-têtes joueur, zone d'aperçu, fenêtre modale, ombre | Générées par `scripts/generate_trade_textures.py` (Pillow) à partir des valeurs CSS exactes de la maquette. Dégradés en demi-résolution avec `"blur": true` dans le `.mcmeta`. Relancer le script après toute retouche de couleur. |
| `assets/phantasmon/textures/gui/sprites/pc/star.png` | Étoile chromatique | Pixel art généré |
| `assets/phantasmon/textures/gui/sprites/pc/*` (autres) | Panneaux, cases et boutons nine-slice de l'ancien PC | **Plus utilisées** depuis la refonte du 2026-10-02 (voir `project/status.md`) |

## 6. Comportements des écrans

**PC**

- Clic = sélection (la sélection suit le Pokémon quand on change de boîte).
- Glisser-déposer entre deux cases quelconques (équipe ou PC) : le backend déplace ou échange (décision D-04) ;
  le modèle suit la souris ; molette, flèches ← → ou ◀ ▶ changent de boîte, **même pendant un glisser**.
- IMPORTER : texte Showdown du presse-papiers, un ou plusieurs Pokémon créés dans les premières cases libres.
- SUPPRIMER (ou touche Suppr) : fenêtre de confirmation.
- Après chaque action, la liste complète des Pokémon du joueur est rechargée (480 au maximum).

**Éditeur**

- Champs : surnom, sexe (selon l'espèce : bascule Aléatoire → ♂ → ♀ pour une espèce mixte, valeur grisée sinon),
  niveau, chromatique, talent (talents de l'espèce), objet (objets de combat du tag
  `#cobblemon:held/is_held_item`, recherche), nature, Téracristal (« Type d'origine » = aucun), IV et EV (total en
  rouge au-delà de 510, Puissance Cachée recalculée en direct), 4 attaques (recherche, sans doublon).
- Recherche par mots, dans n'importe quel ordre, sur l'identifiant **et** le nom traduit.
- IMPORTER remplit le formulaire depuis un texte Showdown sans enregistrer et sans changer l'espèce.
- Clavier : Tab / Maj+Tab, Entrée = enregistrer, Ctrl+V, Ctrl+Retour arrière, ↑↓ ou molette sur un nombre = ±1
  (Maj : ±10), Échap ferme la liste, le champ, puis l'éditeur (confirmation s'il y a des modifications).
- ENREGISTRER envoie un seul `PATCH /pokemon/{uuid}` avec `data` complet (clés inconnues conservées), `level`,
  `nature`, `ability`, `is_shiny`.

**Échange** : voir [`live-trade.md`](live-trade.md).
