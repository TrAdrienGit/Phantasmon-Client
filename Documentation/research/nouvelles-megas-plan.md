# Nouvelles Méga-Évolutions (Légendes Z-A / Champions) : plan d'intégration

Statut : **plan validé** le 2026-11-26 avec les 4 choix recommandés ; **développé et déployé le 2026-11-26, validé en
jeu le 2026-11-27** (voir « Où en est-on » en fin de document). Décision D-42.

## But

Rendre jouables, dans les combats Phantasmon (combats « Ghost » simulés par le client hôte), les 49 nouvelles formes
Méga apparues avec Légendes Pokémon Z-A et Pokémon Champions : Méga-Mélodelfe, Méga-Dracolosse, Méga-Absol Z,
Méga-Raichu X/Y, etc. (liste complète en annexe).

Choix retenu après l'étude du 2026-11-26 (option A) : **tout passe par le moteur Phantasmon**, rien côté serveur
Minecraft. Aucun mod serveur, aucun datapack. Les Pokémon du jeu normal (hors Phantasmon) n'y ont pas accès.

## Ce qui existe déjà

- Le client hôte fait tourner le moteur Showdown de Cobblemon. Phantasmon y injecte déjà du code par
  `ShowdownExtensions` (utilisé par League Reborn dans Events) : c'est la porte d'entrée.
- Les Méga Gemmes sont des **objets virtuels** (`VirtualItems`) : tenus dans l'éditeur, donnés au moteur sans objet
  Minecraft. Il suffit d'en ajouter.
- À la Méga-Évolution, l'hôte donne au Pokémon un **aspect** (`mega`, `mega_x`, `mega_y`) que le pack de ressources
  utilise pour changer le modèle, et Showtime joue la mise en scène.
- Le backend télécharge les données Showdown à jour : il connaît déjà les nouvelles Méga et leurs gemmes, marquées
  « Future » (bannies sauf si le format les autorise).

## Ce qui manque

| Manque | Pourquoi |
|---|---|
| Les 49 formes dans le moteur | Le Showdown embarqué par Cobblemon 1.8.1 n'a que les anciennes Méga. |
| Les 45 gemmes dans le moteur | Idem. Certaines gemmes donnent plusieurs formes (Miaousse M/F, Tatsugiri ×3, Magearna ×2) : le moteur embarqué ne sait pas faire, il faut le corriger. |
| 7 nouveaux talents | Mega Sol, Dragonize, Aura Guard, Piercing Drill, Eelevate, Fire Mane, Spicy Spray (noms anglais, les français restent à trouver) : leur code est à reprendre du source Showdown. Les 35 autres talents existent déjà. |
| Les modèles 3D | Aucun pack actuel ne les a. Le mod Mega Showdown en a pour 40 des 49 formes. |
| L'aspect `mega_z` | Absol, Carchacrok et Lucario Z. |
| Noms, icônes, traductions | Gemmes, talents, formes, dans l'éditeur et le chat de combat. |

## Plan

### Étape 1 — Moteur (Client)

- `tools/new_megas.py` lit Showdown en amont (licence MIT) à un commit figé et écrit
  `assets/phantasmon/showdown/new_megas.js` : les 49 formes, les 45 gemmes, le code des 7 talents. Fichier généré,
  jamais modifié à la main, commit d'origine noté en tête.
- Une extension `ShowdownExtensions` « new_megas » dans le cœur de Phantasmon l'installe : formes et talents dans les
  registres du moteur, gemmes au format du moteur embarqué, correction de `canMegaEvo` pour les gemmes à plusieurs
  formes, marque « Future » retirée.
- Test automatique avec Node : un combat réel dans le moteur de Cobblemon où chaque nouvelle forme Méga-évolue
  (types, stats, talent vérifiés), et chaque nouveau talent agit.

### Étape 2 — Objets et éditeur (Client)

- Les 45 gemmes entrent dans `VirtualItems` (liste générée) avec une icône (`tools/held_item_icons.py`).
- L'import Showdown reconnaît les nouvelles formes (`Mélodelfe-Méga` importé comme Mélodelfe + Mélodelfite, comme
  aujourd'hui).
- Le Pokémon ne peut tenir que sa propre gemme (règle du moteur, déjà là pour les anciennes).

### Étape 3 — Aspect et modèles (Client + pack)

- L'aspect vient de la gemme réellement tenue, **virtuelle comprise** : `…ite x/y/z` → `mega_x/y/z`, sinon `mega`.
  À vérifier au passage : aujourd'hui l'aspect lit l'objet Minecraft, une gemme virtuelle X/Y donnerait peut-être
  `mega` au lieu de `mega_x`.
- `tools/mega_pack.py` construit un pack « Phantasmon Megas » à partir des modèles de Mega Showdown (commit figé) :
  modèles, animations, posers, résolveurs, textures (shiny compris) des seules nouvelles formes, avec les crédits et la
  licence. Leurs résolveurs utilisent déjà nos aspects (`mega`, `mega_x`, `mega_z`).
- **6 espèces sans modèle** chez Mega Showdown : Heatran, Darkrai, Zeraora, Tatsugiri, Magearna, Zygarde. Elles
  gardent leur modèle normal ; la mise en scène Showtime joue quand même.
- Showtime : vérifier que la mise en scène Méga se déclenche pour `mega_z` (elle accepte tout aspect qui n'est ni
  Z ni Téra, a priori rien à changer).
- L'invité voit les modèles seulement s'il a aussi le pack.

### Étape 4 — Backend

- Décider quels formats acceptent les nouvelles Méga (voir décisions) et l'appliquer dans la validation d'équipe
  (`TeamValidator`, aujourd'hui « Future » = banni).
- Tests de validation : Mélodelfe + Mélodelfite accepté/refusé selon le format.

### Étape 5 — Textes

- Noms français et anglais des gemmes, des 7 talents (nom + description) et des formes, dans les fichiers de langue.
- Le chat de combat annonce la Méga-Évolution et le talent comme pour les anciennes.

### Étape 6 — Essai en jeu, déploiement, docs

- Combat Ghost entre deux joueurs : Méga-Évolution d'une forme simple (Mélodelfe), d'une X/Y (Raichu), d'une Z
  (Absol), d'une forme multiple (Miaousse F), d'une sans modèle (Darkrai) ; un nouveau talent en action.
- Mise à jour : `architecture/battle-engine.md`, `architecture/decisions.md` (nouvelle décision, miroir backend),
  `guides/user-guide.md`, `guides/installing.md` (le pack), journal.

## Anticiper une mise à jour d'Academy

Une future version d'Academy (ou de Cobblemon, ou de CCC) apportera sans doute ces Méga elle-même. Validé le
2026-11-26 : on l'anticipe seulement là où ça ne coûte rien, le reste se réglera le moment venu.

- L'extension moteur n'ajoute **que ce que le moteur ne connaît pas** : une forme, une gemme ou un talent déjà présent
  est laissé tel quel. Si Academy les ajoute, ce sont les siens qui jouent, sans conflit.
- Le nom de l'aspect se décide à un seul endroit (`FormeChangeVisual.megaAspect`) : si Academy choisit d'autres noms,
  on ne change que là.
- Le pack « Phantasmon Megas » est à part : il suffira de le retirer si Academy/CCC fournit ses propres modèles.
- Une gemme qui deviendrait un vrai objet Minecraft est déjà reconnue par son nom (comme les anciennes gemmes).

## Organisation du développement

Je garde la main sur le moteur (étape 1), la partie la plus délicate. Des sous-agents Sonnet, briefés et relus par
moi, prennent les morceaux indépendants : le générateur du pack (étape 3), les icônes et l'éditeur (étape 2), le
backend (étape 4). Rien n'est commité avant ta validation en jeu.

## Décisions à prendre

Coche ou corrige ; la case cochée est ma recommandation.

1. **Formats où les nouvelles Méga sont permises**
   - [x] Comme Showdown : formats Champions et formats libres de Phantasmon oui, formats Smogon (OU, NatDex…) non.
   - [ ] Partout où les Méga sont permises.
2. **Diffusion du pack de modèles** (licence CC BY-NC-SA, et Mega Showdown demande son accord pour toute
   publication sur Modrinth/CurseForge)
   - [x] Pack privé, déposé sur le Nextcloud comme les jars ; jamais publié sur Modrinth/CurseForge. Le dépôt Git ne
     garde que le script qui le construit et les crédits.
   - [ ] Demander l'accord de Mega Showdown sur leur Discord pour pouvoir le publier.
3. **Les 6 espèces sans modèle**
   - [x] Jouables tout de suite avec leur modèle normal.
   - [ ] Bloquées tant qu'il n'y a pas de modèle.
4. **Règles Champions** (le jeu Champions a ses propres règles : stats, objets…)
   - [x] Plus tard, séparément. Ici seulement les Méga.
   - [ ] Dans la foulée.

## Risques

- Le code des 7 talents peut dépendre de fonctions du Showdown récent absentes du moteur embarqué : on l'adapte, le
  test Node le dira tout de suite.
- Une mise à jour de Cobblemon peut changer le moteur embarqué : l'extension vérifie à chaque combat qu'elle est
  installée et se réinstalle, et le test Node se relance.
- Les modèles de Mega Showdown peuvent attendre des animations ou des posers propres à leur mod : à voir en jeu.

## Annexe : les 49 formes

Raichu X, Raichu Y, Mélodelfe, Empiflor, Staross, Dracolosse, Méganium, Aligatueur, Airmure, Éoko, Absol Z, Étouraptor,
Carchacrok Z, Lucario Z, Momartik, Heatran, Darkrai, Roitiflam, Minotaupe, Brutapode, Baggaïd, Ohmassacre, Lugulabre,
Golemastoc, Blindépique, Goupelin, Amphinobi, Némélios, Floette Éternelle, Miaousse ♂, Miaousse ♀, Sepiatroce,
Golgopathe, Kravarech, Brutalibré, Zygarde, Crabominable, Sarmuraï, Draïeul, Magearna, Magearna Couleur du Passé,
Zeraora, Hexadron, Scovilain, Floréclat, Tatsugiri (×3), Glaivodo.

## Où en est-on (2026-11-27)

| Étape | État |
|---|---|
| 1. Moteur | Fait : `tools/new_megas.py` (Showdown `c7fa6f1`), `new_megas_data.js` + `new_megas.js`, `NewMegas.java`. `node tools/new_megas_test.js <showdown extrait>` : les 49 formes Méga-évoluent, les 7 talents testés. Ajouts au vieux moteur : gemmes à plusieurs formes, Mega Sol, Eelevate, Piercing Drill, Zygarde Parfait qui Méga-évolue. |
| 2. Objets, éditeur | Fait : 45 gemmes et icônes, import Showdown (Floette-Mega → Floette Éternelle, Zygarde-Mega → Zygarde 50 % Rassemblement). L'éditeur laisse toute gemme à tout Pokémon (comme avant) ; le backend refuse la mauvaise espèce. |
| 3. Aspect, modèles | Fait : aspect lu sur la gemme virtuelle aussi (`mega_z`), pack « Phantasmon Megas » (40 formes) installé et activé dans les deux instances. |
| 4. Backend | Fait : Champions et « Libre » acceptent, formats Smogon refusent ; mauvaise espèce refusée. Formats Champions OU, UU et National Dex proposés dans le lobby depuis le 2026-11-27 (règles de Champions, combat en Gen 9). |
| 5. Textes | Talents en anglais dans les deux langues, noms français à trouver. |
| 6. Essai en jeu | **Validé** (2026-11-27) : Méga-Mélodelfe, Méga-Absol Z et Méga-Raichu X validées. Méga-Raichu X faisait planter le jeu : son animation utilise des particules de Mega Showdown absentes du pack, Cobblemon rejetait l'animation et le poser plantait en la demandant. `tools/mega_pack.py` embarque désormais chaque effet de particules (et sa texture) ou s'arrête ; revérifié en jeu, corrigé. À surveiller : Scolipède (clé de poser propre à Mega Showdown), Hexadron (animations propres). |
