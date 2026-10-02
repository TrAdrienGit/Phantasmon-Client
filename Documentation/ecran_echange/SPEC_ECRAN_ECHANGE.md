# Spécification — Écran d'échange Phantasmon (portage Minecraft / Cobblemon)

Ce document décrit précisément l'écran d'échange de Pokémon à recréer en tant que `Screen` client d'un mod Minecraft basé sur Cobblemon. La maquette HTML fournie est la **référence visuelle unique** : en cas de doute, c'est elle qui fait foi, pas l'ancienne interface grise de Cobblemon.

## 0. Contenu du dossier

| Fichier | Rôle |
|---|---|
| `SPEC_ECRAN_ECHANGE.md` | Ce document. |
| `source/phantasmon_trade_ui.html` | Maquette de référence, HTML + CSS pur, sans JavaScript. S'ouvre dans un navigateur. |
| `source/gen.py` | Générateur de la maquette. Contient les données de démo et toute la logique d'affichage sous forme lisible (fonctions `slot()` et `card()`). |
| `reference/ref_00_zones.png` | Capture 1600×900 avec chaque zone numérotée (Z01 à Z29). |
| `reference/ref_01_ecran.png` | Capture de l'état par défaut. |
| `reference/ref_02_survol.png` | Recadrage d'un slot survolé. |
| `reference/ref_03_echange.png` | Fenêtre « Échange en cours ». |
| `reference/ref_04_quitter.png` | Fenêtre de confirmation « Quitter ». |
| `reference/measures.json` | Mesures brutes relevées dans le navigateur (x, y, largeur, hauteur en px, relatives au panneau). |

Dans les captures, les cadres jaunes pointillés marquent l'emplacement des images (sprites Pokémon, icônes d'objet). Les vraies images n'ont pas pu être chargées lors de la capture. En jeu, ces zones reçoivent le rendu Cobblemon (voir §6).

## 1. Paramètres à confirmer avant de coder

Ces points ne sont pas fixés par la maquette. L'agent doit les vérifier dans le projet existant ou les demander :

1. Chargeur et versions : Fabric ou NeoForge, version de Minecraft, version de Cobblemon.
2. Le mod **remplace-t-il** l'écran d'échange de Cobblemon (mixin ou remplacement à l'ouverture), ou ajoute-t-il un écran séparé ?
3. Comportement du côté droit (voir §7.2) : dans la maquette, on peut cliquer sur les slots du partenaire pour examiner ses Pokémon. Dans un vrai échange, la fiche droite affiche normalement l'offre choisie par le partenaire, envoyée par le serveur.
4. Textures existantes : la DA indique des textures nine-slice dans `textures/gui/sprites/pc/`. Réutiliser celles du PC quand elles correspondent, en créer de nouvelles sinon (liste §5).

## 2. Système de coordonnées

La maquette est conçue sur un panneau de **1600 × 900 px**. En jeu, utiliser un **canevas logique de 800 × 450 unités GUI**, soit exactement les px de la maquette divisés par 2.

- À 1080p avec une échelle GUI de 2, une unité vaut 2 px écran : le panneau occupe alors 1600×900 px, comme la maquette.
- Pour toute autre échelle GUI, calculer `s = min(1, width / 800, height / 450)` (marge éventuelle de quelques unités), centrer le canevas, appliquer `s` via la matrice de pose au rendu, et diviser les coordonnées souris par `s` avant tout test de survol ou de clic.
- Contrainte du brief : ne jamais dépasser 1920×1080 px écran.

## 3. Cotes de chaque zone

Origine : coin supérieur gauche du panneau racine (Z01). Les colonnes de gauche sont en px maquette, celles de droite en unités GUI (÷2, arrondi). Une erreur d'arrondi de ±1 unité est acceptable. Voir `reference/ref_00_zones.png` pour situer chaque zone.

| Zone | Élément | x px | y px | l px | h px | x u | y u | l u | h u |
|---|---|--:|--:|--:|--:|--:|--:|--:|--:|
| Z01 | Panneau racine | 0 | 0 | 1600 | 900 | 0 | 0 | 800 | 450 |
| Z02 | En-tête joueur gauche | 15 | 15 | 688 | 48 | 8 | 8 | 344 | 24 |
| Z03 | Bouton ÉCHANGER | 715 | 15 | 170 | 48 | 358 | 8 | 85 | 24 |
| Z04 | En-tête joueur droit | 897 | 15 | 688 | 48 | 448 | 8 | 344 | 24 |
| Z05 | Rail équipe gauche | 15 | 72 | 194 | 758 | 8 | 36 | 97 | 379 |
| Z06 | Titre du rail | 24 | 81 | 176 | 28 | 12 | 40 | 88 | 14 |
| Z07 | Grille des 6 slots | 24 | 117 | 176 | 656 | 12 | 58 | 88 | 328 |
| Z08 | Slot (1er) | 24 | 117 | 85 | 214 | 12 | 58 | 42 | 107 |
| Z09 | Icône Pokémon du slot | 30 | 182 | 72 | 72 | 15 | 91 | 36 | 36 |
| Z10 | Icône objet du slot | 83 | 305 | 20 | 20 | 42 | 152 | 10 | 10 |
| Z11 | Pied du rail | 24 | 781 | 176 | 40 | 12 | 390 | 88 | 20 |
| Z12 | Fiche gauche | 219 | 72 | 577 | 758 | 110 | 36 | 288 | 379 |
| Z13 | Bandeau nom / propriétaire / niveau | 220 | 75 | 575 | 42 | 110 | 38 | 288 | 21 |
| Z14 | Zone d'aperçu 3D | 220 | 117 | 575 | 235 | 110 | 58 | 288 | 118 |
| Z15 | Modèle Pokémon (boîte englobante) | 392 | 131 | 231 | 206 | 196 | 66 | 116 | 103 |
| Z16 | Badges de type | 229 | 126 | 96 | 19 | 114 | 63 | 48 | 10 |
| Z17 | Bloc infos | 220 | 352 | 575 | 477 | 110 | 176 | 288 | 238 |
| Z18 | Objet tenu | 228 | 361 | 276 | 49 | 114 | 180 | 138 | 24 |
| Z19 | Nature | 510 | 361 | 276 | 49 | 255 | 180 | 138 | 24 |
| Z20 | Talent | 228 | 416 | 276 | 49 | 114 | 208 | 138 | 24 |
| Z21 | Téracristal | 510 | 416 | 276 | 49 | 255 | 208 | 138 | 24 |
| Z22 | Capacités | 228 | 472 | 303 | 349 | 114 | 236 | 152 | 174 |
| Z23 | 1re capacité | 237 | 494 | 285 | 28 | 118 | 247 | 142 | 14 |
| Z24 | IV / EV | 538 | 472 | 248 | 349 | 269 | 236 | 124 | 174 |
| Z25 | 1re ligne de stat | 547 | 511 | 230 | 18 | 274 | 256 | 115 | 9 |
| Z26 | Fiche droite (miroir de Z12) | 805 | 72 | 577 | 758 | 402 | 36 | 288 | 379 |
| Z27 | Rail équipe droit (miroir de Z05) | 1391 | 72 | 194 | 758 | 696 | 36 | 97 | 379 |
| Z28 | Pied de page | 15 | 839 | 1570 | 47 | 8 | 420 | 785 | 24 |
| Z29 | Bouton QUITTER | 1475 | 849 | 97 | 28 | 738 | 424 | 48 | 14 |

### Règles de répétition

- **Slots** : grille 2 colonnes × 3 lignes, slot de 85×214 px, écart 7 px. Slot *i* (0 à 5) : colonne `i % 2`, ligne `i / 2`, position `x = Z07.x + col × 92`, `y = Z07.y + ligne × 221`.
- **Côté droit** : la fiche Z26 est la fiche Z12 décalée de **+586 px (+293 u)** en x, avec un contenu identique. Le rail Z27 est le rail Z05 décalé de **+1376 px (+688 u)**.
- **Capacités** : 4 lignes de 28 px, pas de 32 px (écart 4 px), à partir de Z23.
- **Stats** : 6 lignes de 18 px à partir de Z25, puis un séparateur de 1 px et la ligne « Total EV ». La ligne d'en-tête « IV / EV » se trouve juste au-dessus de Z25.
- **Z15** : le modèle flotte (§8), sa position varie donc de quelques px.

## 4. Couleurs

Toutes les couleurs viennent de la DA Phantasmon. L'alpha est indiqué entre parenthèses (0 à 1).

### Fonds et cadres

| Élément | Fond | Bordure |
|---|---|---|
| Arrière-plan derrière le panneau | Dégradé diagonal `#02070D` → `#071523` → `#02060B` + halo radial `rgba(24,84,112,.22)` | — |
| Panneau racine Z01 | `#081422` (0,41) | 2 px `#50E6FF` (0,75) + filet intérieur 1 px `#50E6FF` (0,12) |
| Liserés néon du haut de Z01 | 2 barres de 4 px, `#50E6FF` plein + lueur, chacune sur 34 % de la largeur, alignées à gauche et à droite | — |
| En-têtes joueur Z02 / Z04 | Dégradé `rgba(12,54,80,.92)` → `rgba(3,10,20,.96)`, orienté vers l'extérieur (Z04 en miroir) | 1 px `#2FB7C9` |
| Rails Z05 / Z27 | Dégradé diagonal `rgba(12,32,54,.96)` → `rgba(3,10,20,.98)` | 1 px `#50E6FF` (0,48) |
| Séparateur sous Z06 | — | 1 px `#50E6FF` (0,25) |
| Pied du rail Z11 | `#000000` (0,2) | 1 px `#50E6FF` (0,25) |
| Fiches Z12 / Z26 | Dégradé diagonal `rgba(12,32,54,.98)` → `rgba(3,10,20,.98)` | 1 px `#2FB7C9` ; bord **haut 3 px** `#50BFFF` à gauche, `#FF6688` à droite |
| Bandeau Z13 | `#02080F` (0,42) | bas 1 px `#50E6FF` (0,28) |
| Aperçu Z14 | Halo radial `rgba(80,230,255,.08)` au centre + vignettage sombre `rgba(0,0,0,.43)` sur les bords | — |
| Disque grille dans Z14 | Cercle de 210 px, grille de 22 px en `#50E6FF` (0,07), tournée de 45°, contour `#50E6FF` (0,09) | — |
| Cases d'info Z18 à Z24 | `#00080F` (0,38) | 1 px `#50E6FF` (0,2) |
| Lignes de capacité Z23 | `#07131F` | 1 px `#50E6FF` (0,12) |
| Lignes de stat paires | `#FFFFFF` (0,03) | — |
| Pied de page Z28 | `#030A14` (0,82) | 1 px `#50E6FF` (0,3) |

### Slots (Z08)

| État | Fond | Bordure |
|---|---|---|
| Normal | `#0E3C4E` (0,51) | 1 px `#50E6FF` (0,35) |
| Survol | `#50E6FF` (0,35) | 1 px `#50E6FF` plein |
| Sélectionné | `#50E6FF` (0,45) | 1 px `#FFFFFF` + lueur intérieure `#50E6FF` (0,35) |

### Boutons

| Bouton | Dégradé haut → bas | Bordure | Texte |
|---|---|---|---|
| ÉCHANGER (Z03), repos | `#18466B` → `#0D2A42` | `#50E6FF` + lueur (0,18) | `#50E6FF` |
| ÉCHANGER, survol | `#205C8D` → `#123859` | `#50E6FF` | `#50E6FF` |
| ÉCHANGER, état « PRÊT ✓ » | inchangé | `#76FFB0` + lueur (0,3) | `#BAFFD2` |
| Standard (FERMER, ANNULER) | `#18466B` → `#0D2A42` ; survol `#205C8D` → `#123859` | `#50E6FF` | `#FFFFFF` |
| Danger (QUITTER) | `#6B182A` → `#420D18` ; survol `#8D2038` → `#591222` | `#FF5078` | `#FFFFFF` |

### Badges de type et Téracristal

Fond = couleur primaire du type fournie par Cobblemon (`getPrimaryColor()` selon la DA). Texte en `#000000` si la luminance du fond dépasse 0,6, sinon en `#FFFFFF`. Contour 1 px `#000000` à 33 %, coins arrondis de 3 px. La maquette utilise une table de couleurs de secours visible dans `gen.py` (`TYPES`), à remplacer par celle de Cobblemon.

### Étoile chromatique

Remplissage `#FFDD33`, contour 1 px `#785000`.

## 5. Textures à prévoir

Minecraft ne sait pas dessiner nativement les dégradés diagonaux, les halos radiaux ni les lueurs. Ces effets doivent être des textures PNG en nine-slice, générées comme les autres textures du PC Phantasmon.

| Texture | Usage | Remarque |
|---|---|---|
| `panel_root` | Z01 | Cadre cyan translucide + fond plat. Les deux liserés néon peuvent être une texture séparée. |
| `panel_sub` | Z05, Z27, Z12, Z26 | Dégradé diagonal. Déjà prévu dans la DA (« sous-panneaux »). |
| `card_accent_left` / `card_accent_right` | Bord haut 3 px des fiches | Ou simple `fill` de couleur. |
| `player_head_left` / `player_head_right` | Z02, Z04 | Dégradé orienté vers l'extérieur. |
| `slot`, `slot_hover`, `slot_selected` | Z08 | Probablement déjà présentes pour le PC. |
| `button`, `button_hover`, `button_danger`, `button_danger_hover` | Boutons | Déjà décrites par la DA. |
| `button_ready` | Z03 en état prêt | Variante verte. |
| `viewport_bg` | Z14 | Halo + vignettage + disque grille. Texture étirée, pas nine-slice. |
| `info_box` | Z18 à Z24 | Un simple `fill` + contour suffit. |

Les éléments unis (cases d'info, lignes de capacité, séparateurs) peuvent être dessinés avec `fill` et des contours de 1 unité plutôt qu'avec des textures.

## 6. Contenu et données

### 6.1 En-tête

- **Z02** : nom du joueur local, aligné à gauche, majuscules, gras, espacement des lettres large.
- **Z04** : nom du partenaire, aligné à droite, même style.
- **Z03** : bouton « ⇄ ÉCHANGER ». En état prêt, le texte devient « PRÊT ✓ ».

Il n'y a **pas** de « VS », pas de rôle (« Échangeur », « Adversaire »), pas de voyant de connexion, pas de bouton Chromatique ni Réinitialiser. Ces éléments ont été retirés volontairement.

### 6.2 Rails (Z05, Z27)

- **Titre Z06** : « ÉQUIPE · {nom du joueur} », majuscules, `#9FD9E6`.
- **6 slots**, toujours affichés même si l'équipe est incomplète (slot vide = état normal sans contenu). Chaque slot contient :
  - en haut à gauche : symbole de sexe, ♂ `#50BFFF`, ♀ `#FF7594`, rien si asexué ;
  - en haut à droite : « N.{niveau} », `#CBEAF0` ;
  - au centre : icône du Pokémon, 72×72 px (Z09) ;
  - sous l'icône : nom, gras, `#FFFFFF`, tronqué avec « … » s'il est trop long. Une étoile `#FFDD33` le précède si le Pokémon est chromatique ;
  - en bas à droite : icône de l'objet tenu, 20×20 px (Z10), absente si aucun objet.
- **Pied Z11** : « Cliquez sur un Pokémon pour l'examiner », `#999999`, centré, sur deux lignes.

### 6.3 Fiches (Z12, Z26)

**Bandeau Z13** : nom + symbole de sexe à gauche ; nom du propriétaire au centre (majuscules, `#9FD9E6`) ; « Nv. {niveau} » à droite.

**Aperçu Z14** :
- badges de type en haut à gauche (Z16) ;
- étoile chromatique en haut à droite si le Pokémon est chromatique ;
- modèle 3D du Pokémon centré, environ 230×205 px, avec son animation d'attente et une ombre portée sous lui. Utiliser le rendu de modèle de Cobblemon (la fonction employée par l'écran de résumé, `drawProfilePokemon` ou équivalent selon la version), dans sa variante chromatique si besoin.

**Bloc infos Z17**, grille 2×2 puis 2 colonnes :

| Case | Libellé | Contenu |
|---|---|---|
| Z18 | OBJET TENU | Icône de l'objet (rendu d'`ItemStack`, 20×20 px) + nom traduit. « Aucun » si vide. |
| Z19 | NATURE | Nom traduit de la nature. |
| Z20 | TALENT | Nom traduit du talent. |
| Z21 | TÉRACRISTAL | Badge du type Téra, même style que les badges de type. |
| Z22 | CAPACITÉS | 4 lignes centrées, une capacité chacune. Ligne vide si moins de 4 capacités. |
| Z24 | IV / EV | En-tête « IV · EV » en `#6C8C96`, puis 6 lignes (PV, Attaque, Défense, Atq. Spé., Déf. Spé., Vitesse), puis une ligne « Total EV ». |

Dans Z24 : libellé à gauche, IV aligné à droite dans une colonne de 30 px en `#88CCFF`, EV aligné à droite dans une colonne de 34 px en `#FFCC88`. La ligne « Total EV » a son libellé en `#999999` et sa valeur en `#FFCC88`.

Sources de données Cobblemon probables (noms à vérifier sur la version utilisée) : `pokemon.species`, `.level`, `.gender`, `.shiny`, `.heldItem()`, `.nature`, `.ability`, `.teraType`, `.moveSet`, `.ivs`, `.evs`, `.types`. Afficher les noms via leurs clés de traduction, pas en texte brut.

### 6.4 Pied de page (Z28)

Uniquement le bouton **QUITTER** (Z29), style danger, aligné à droite.

## 7. Comportement

### 7.1 Sélection

- Un clic sur un slot du rail gauche le sélectionne (style « sélectionné ») et affiche ce Pokémon dans la fiche gauche. Dans un vrai échange, cela correspond à **choisir son offre**.
- Par défaut, le slot 0 est sélectionné à gauche. Dans la maquette, le slot 3 est sélectionné à droite.
- Survol d'un slot : style « survol ».

### 7.2 Côté droit (point à trancher, voir §1)

- Maquette : clic libre sur les slots de droite pour examiner l'équipe du partenaire.
- Option recommandée en jeu : la fiche droite affiche l'offre du partenaire, mise à jour par le serveur, et le slot correspondant passe en style sélectionné. Les clics sur le rail droit sont alors soit désactivés, soit réservés à un examen temporaire, au choix du propriétaire du mod.

### 7.3 Bouton ÉCHANGER

1. Clic : le bouton passe en état « PRÊT ✓ » (bordure et texte verts). Le joueur local envoie son acceptation.
2. Un nouveau clic retire l'acceptation (retour à « ⇄ ÉCHANGER »).
3. Changer d'offre, d'un côté ou de l'autre, doit annuler l'état prêt des deux joueurs. Cobblemon gère normalement cette règle côté serveur.
4. Quand les deux joueurs sont prêts : afficher la fenêtre « ÉCHANGE EN COURS » (§7.5).

Dans la maquette, la fenêtre s'ouvre 0,7 s après le clic, sans attendre le partenaire : c'est une simulation. En jeu, ouvrir la fenêtre sur confirmation du serveur.

### 7.4 Bouton QUITTER

Ouvre une confirmation (`reference/ref_04_quitter.png`) :
- titre « QUITTER L'ÉCHANGE ? » en `#5FE6F2` ;
- texte « L'échange en cours sera annulé. » en `#CCCCCC` ;
- boutons ANNULER (standard) et QUITTER (danger).

QUITTER ferme l'écran et annule l'échange. La touche Échap doit avoir le même effet que le bouton QUITTER (ouvrir la confirmation), ou fermer directement : à confirmer.

### 7.5 Fenêtre « ÉCHANGE EN COURS »

Voir `reference/ref_03_echange.png`.
- Voile plein écran `#00050A` (0,82) avec flou de l'arrière-plan si possible, sinon voile seul.
- Carte de 520 px de large, padding 25 px, dégradé `#0C2036` → `#030A14`, bordure 1 px `#50E6FF`, lueur.
- Titre « ÉCHANGE EN COURS », `#5FE6F2`, gras, espacé.
- Bande de transfert de 90 px de haut : « J1 » à gauche, nom du partenaire à droite, ligne de 3 px en dégradé `#50E6FF` → `#FFFFFF` → `#FF5078`, et une Poké Ball qui fait l'aller-retour en tournant (cycle de 1,8 s).
- Texte « Échange de {offre gauche} contre {offre droite}… » en `#CCCCCC`.
- Bouton FERMER (standard).

## 8. Animations

| Animation | Détail |
|---|---|
| Flottement du modèle | Cycle de 3,5 s, ease-in-out : monte de 5 px et grossit de 1,5 % à mi-cycle. |
| Poké Ball de transfert | Cycle de 1,8 s, linéaire : translation de 180 px aller-retour avec un tour complet. |
| Apparition de la fenêtre d'échange | Fondu de 0,2 s. |
| Survol des slots et boutons | Transition de 0,12 s. Peut être instantanée en jeu. |

## 9. Typographie

La maquette utilise « Trebuchet MS ». En jeu, utiliser la police de Minecraft ou celle du mod. Privilégier les échelles 1 et 0,5 pour un rendu net. Correspondance indicative :

| Usage | Taille maquette | Graisse | Couleur | Casse |
|---|---|---|---|---|
| Noms des joueurs (Z02, Z04) | 16 px | gras | `#FFFFFF` | majuscules |
| Bouton ÉCHANGER | 13 px | gras | `#50E6FF` | majuscules |
| Nom du Pokémon (Z13) | 16 px | gras | `#FFFFFF` | — |
| Niveau (Z13) | 13 px | gras | `#FFFFFF` | — |
| Propriétaire (Z13) | 10 px | normal | `#9FD9E6` | majuscules |
| Libellés de section (OBJET TENU, CAPACITÉS…) | 10 px | gras | `#5FE6F2` | majuscules |
| Valeurs (objet, nature, talent) | 13 px | normal | `#FFFFFF` | — |
| Capacités | 12 px | normal | `#CCCCCC` | — |
| Lignes de stats | 11 px | normal | libellé `#FFFFFF`, IV `#88CCFF`, EV `#FFCC88` | — |
| Titre du rail | 12 px | normal | `#9FD9E6` | majuscules |
| Nom dans un slot | 11 px | gras | `#FFFFFF` | — |
| « N.100 » et sexe dans un slot | 10 px | normal | `#CBEAF0` | — |
| Badges de type | 10 px | gras | noir ou blanc (§4) | majuscules |
| Pied du rail | 11 px | normal | `#999999` | — |
| Boutons QUITTER, FERMER, ANNULER | 11 px | gras | `#FFFFFF` | majuscules |

## 10. Critères de validation

L'écran est considéré fidèle quand :

1. Une capture en jeu à 1080p (échelle GUI 2) se superpose à `reference/ref_01_ecran.png` avec un écart inférieur à 2 px sur les cadres des zones Z01 à Z29.
2. Les couleurs relevées à la pipette correspondent au §4.
3. Les états normal, survol et sélectionné des slots, ainsi que les états repos, survol et prêt du bouton ÉCHANGER, sont tous reproduits.
4. Les deux fenêtres (échange, quitter) correspondent à leurs captures.
5. Avec une équipe incomplète, un Pokémon sans objet, un Pokémon asexué et un Pokémon chromatique, l'affichage reste correct (pas de texte qui déborde, pas d'icône manquante qui casse la mise en page).
6. Aux échelles GUI 1 à 4 et en 1280×720, l'écran reste entier, centré et cliquable correctement.
