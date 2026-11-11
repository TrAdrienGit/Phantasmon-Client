# Interfaces graphiques et « design system »

> Classes : `gui/PhantasmonCanvasScreen` (socle), `gui/PhantasmonPcScreen`, `gui/PhantasmonPcEditScreen`, `gui/menu/*` (menu Phantasmon),
> `gui/PhantasmonTradeScreen`, `gui/PokemonGuiRendering`. Maquettes : [`design/`](../design/README.md).
> Vérifié le 2026-10-03.

## 1. Les trois écrans

| Écran | Ouverture | Disposition |
|---|---|---|
| **PC** (`PhantasmonPcScreen`) | Touche **P** | Rail Équipe à gauche · fiche Pokémon au centre · grille 6×5 de la boîte à droite (◀ BOÎTE n / 16 ▶). En-tête : « PC · joueur », IMPORTER, compteurs. Pied : ligne d'état, ÉDITER / SUPPRIMER. Bouton ✕ pour fermer. |
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

## 6. Comportements des écrans

**PC**

- Clic = sélection (la sélection suit le Pokémon quand on change de boîte).
- Glisser-déposer entre deux cases quelconques (équipe ou PC) : le backend déplace ou échange (décision D-04) ;
  le modèle suit la souris ; molette, flèches ← → ou ◀ ▶ changent de boîte, **même pendant un glisser**.
- IMPORTER : texte Showdown du presse-papiers, un ou plusieurs Pokémon créés dans les premières cases libres.
- EXPORTER (à droite d'IMPORTER) : copie l'équipe active au format Showdown dans le presse-papiers.
- En-tête du PC et de l'éditeur : la paire IMPORTER / EXPORTER (170 px chacun, 10 px d'écart) est centrée sur x = 800 ;
  les deux bandeaux d'en-tête sont raccourcis d'autant (598 px au lieu de 688), même marge de 12 px qu'autour du
  bouton unique de l'écran d'échange (`renderHeaderPlates(g, largeur)`, constantes `HEADER_*`).
- SUPPRIMER (ou touche Suppr) : fenêtre de confirmation.
- Types de boîte (TODO-36, 2026-11-11) : bouton BOÎTE VRAC / BOÎTE ÉQUIPES dans l'en-tête de la boîte. Une boîte
  équipes montre ses 5 lignes séparées (bandeaux en relief, cases un peu moins hautes), chaque ligne = une équipe ; un
  double-clic sur un Pokémon de la ligne échange la ligne et l'équipe, colonne par colonne (le 1er de la ligne devient
  le 1er de l'équipe et inversement), par les déplacements habituels du backend (D-04) envoyés l'un après l'autre.
  Le type de chaque boîte n'est gardé que côté client (`pokemon/PcBoxTypes`, `config/phantasmon-pc-boxes.json`, par
  propriétaire du PC) : rien sur le backend.
- Recherche (TODO-38, 2026-11-11) : champ dans le bandeau droit de l'en-tête (clic ou Ctrl+F ; ✕ l'efface ; Échap /
  Entrée rendent le clavier). Chaque mot doit se retrouver parmi les noms (surnom, espèce en français et en anglais),
  les types et le type Téra, « shiny » / « chromatique », l'objet, le talent, la nature et les attaques, sans tenir
  compte des majuscules ni des accents. Les Pokémon qui ne correspondent pas restent en place, grisés (équipe et
  boîte) ; le pied de l'écran donne le nombre trouvé et les boîtes où ils sont. Pas de tri (Adrien).
- Apparence (TODO-37, étape 1, 2026-11-11) : la fiche montre des badges en bas à gauche de la vue 3D (filtre,
  aura, taille, scellée). Dans l'éditeur, le bouton ✦ APPARENCE ouvre une fenêtre : filtre, aura, taille (−/+ par
  10 %, de 50 à 200 %) ; un joueur n'y voit que ce que ses récompenses couvrent pour cette espèce (`GET /rewards/mine`),
  un admin tout, plus SCELLÉE. Enregistré avec le reste (`data.look`). Le rendu des effets vient à l'étape 2.
- Noms de boîtes (TODO-38) : clic sur le titre de la boîte pour la nommer (24 caractères ; vide = plus de nom ;
  Entrée garde, Échap annule) ; le titre devient « nom · n/16 ». Gardés avec les types de boîte (`PcBoxTypes`).
- Après chaque action, la liste complète des Pokémon du joueur est rechargée (480 au maximum).

**Éditeur**

- Champs : surnom, sexe (selon l'espèce : bascule Aléatoire → ♂ → ♀ pour une espèce mixte, valeur grisée sinon),
  niveau, chromatique, talent (talents de l'espèce), objet (objets de combat du tag
  `#cobblemon:held/is_held_item`, recherche), nature, Téracristal (« Type d'origine » = aucun), IV et EV (total en
  rouge au-delà de 510, Puissance Cachée recalculée en direct), 4 attaques (recherche, sans doublon).
- Recherche par mots, dans n'importe quel ordre, sur l'identifiant **et** le nom traduit.
- IMPORTER remplit le formulaire depuis un texte Showdown sans enregistrer et sans changer l'espèce.
- EXPORTER copie le Pokémon tel qu'affiché (modifications non enregistrées comprises) au format Showdown.
- Clavier : Tab / Maj+Tab, Entrée = enregistrer, Ctrl+V, Ctrl+Retour arrière, ↑↓ ou molette sur un nombre = ±1
  (Maj : ±10), Échap ferme la liste, le champ, puis l'éditeur (confirmation s'il y a des modifications).
- ENREGISTRER envoie un seul `PATCH /pokemon/{uuid}` avec `data` complet (clés inconnues conservées), `level`,
  `nature`, `ability`, `is_shiny`.

**Échange** : voir [`live-trade.md`](live-trade.md).

## 7. Menu Phantasmon (refonte, lot 1, 2026-11-10)

Refonte des menus au niveau du menu de Cobblemon Battle Showtime (référence de qualité) : `gui/menu/`.

- `MenuUi` : boîte à outils commune, mêmes formes que Showtime (police `uniform` de Cobblemon via `drawScaledText`,
  reliefs éclairés / ombrés, lignes enfoncées, boutons en relief, interrupteurs, puces, fond pointillé, cadre à coins
  d'accent, barre de défilement). Deux palettes pour les mêmes formes : **Cobblemon** (gris et turquoise, par défaut)
  et **V1** (cyan sur fond marine), choisies dans les paramètres (`PhantasmonTheme`).
- `PhantasmonMenuScreen` : en-tête, barre latérale des catégories, page, ligne d'état, dialogues. En pixels GUI (pas
  le canevas 1600 × 900 des écrans PC / échange). Ouvert par **H** (Hubs), Mod Menu (Paramètres), le lobby de combat
  et la touche N de Showtime (Musiques). Hors connexion au backend : pas de page Hubs.
- `RowList` : liste à lignes de Showtime (note, sous-titre, choix, interrupteur avec écoute et étoile, rangée de
  boutons, champ texte), défilement à la molette ou à la barre.
- `MenuDialog` : fenêtre de saisie ou de confirmation (rouge si destructive), Tab / Entrée / Échap.
- `RewardsPage` (admins) : charger un joueur par son pseudo, voir ses récompenses (✕ pour en retirer une), + DONNER : type
  (filtre, aura, taille), lequel, espèce (nom français, anglais ou identifiant ; vide = toutes), note.
- Pages : `SettingsPage` (adresse du backend : Tester `GET /health`, Par défaut, Enregistrer pour le prochain
  lancement ; apparence), `MusicPage` (comme Showtime : dossiers, génération, favoris, écoute, textes de Showtime),
  `HubsPage` (le gestionnaire des hubs, ci-dessous).

**Gestionnaire des hubs** (`HubsPage`)

- **Joueur** : les hubs classiques de Phantasmon seuls (jamais ceux d'un add-on ni leurs ancres), avec taille,
  construction, date de création (sans l'auteur) et nombre d'ancres ; pour le hub choisi, **ses** ancres (✕ pour
  chacune) et PLACER UNE ANCRE : nom facultatif, puis le menu se ferme et `hub/HubPlacement` montre la zone du hub à
  la position du joueur, tournée au quart de tour selon son regard, verte si libre, rouge sinon ; boutons du chat
  [Accepter] / [Annuler] ; H abandonne aussi. Route : `GET /hubs/overview`.
- **Admin** : puces d'onglets « Hubs », ceux des add-ons (`PhantasmonApi.registerHubManagerTab`, interface
  `api/HubManagerTab` : hubs montrés, lignes de détail, boutons en plus, bouton de création ; un bouton peut déclarer
  des champs `Field`, demandés dans un `MenuDialog` ; Phantasmon Events y ajoute « Hubs Event »), puis « Autres »
  s'il reste des hubs qu'aucun onglet ne montre (add-on non installé, D-41). Toutes les ancres de tous les serveurs.
  Boutons : placer une ancre, SCHEMATIC, relire la construction (hubs classiques), renommer, ceux de l'onglet,
  supprimer ; + CRÉER en bas de la liste.
- **SCHEMATIC** : les `.schem` / `.litematic` du dossier `schematics/` de l'instance (sous-dossiers compris, les plus
  récents d'abord), taille attendue et construction actuelle, OUVRIR LE DOSSIER, COPIER LA ZONE DU HUB
  (`hub/HubCapture` : ce qui se trouve dans la zone de l'ancre de ce hub où est l'admin, ou la plus proche, devient
  sa construction), RETIRER LA CONSTRUCTION ; ENVOYER (`PUT /admin/hubs/{name}/schematic`, refus du backend affiché
  avec sa raison).
- Routes : `GET /admin/hubs`, `GET /admin/hubs/{name}/anchors`, `POST /admin/hubs/{name}/rename`, création,
  suppression, relecture, schematic (`rest-api.md` du backend).

**Lot 2 (2026-11-11) : PC, éditeur, échange, lobby de combat en V2.** Ces écrans gardent leur canevas 1600 × 900 et
leur disposition (réglée en jeu), mais leur socle `PhantasmonCanvasScreen` dessine maintenant comme le menu : couleurs
de la palette de `MenuUi` (Cobblemon ou V1), police `uniform`, relief (`raisedEdge`, `sunkenEdge`, `blackBorder`), fond
pointillé (`dotGrid`), crochets d'accent (`cornerBrackets`) sur la vue 3D et les fenêtres, puces d'onglet en accent.
`blitTexture` ne colle plus les textures de la première version pour le fond, les en-têtes, les rails, les fiches, la
vue 3D et les fenêtres : il les dessine (`drawPanel`) ; seule l'ombre sous le modèle reste une texture. Plus de lueurs
néon (`glowOutside` / `glowInside` ne gardent que l'ombre portée sombre).
