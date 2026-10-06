---
sources: copie de Pokémon Showdown embarquée par Cobblemon 1.8.1 (instance « Cobblemon Academy 2.0 », dossier showdown/),
  https://play.pokemonshowdown.com/data/formats.js, https://play.pokemonshowdown.com/data/formats-data.js,
  bytecode de com.cobblemon.mod.common.battles.BattleFormat
consulté le: 2026-10-06
nature: étude (TODO-24), aucune implémentation
---

# Étude — Régulations Smogon et clauses pour les combats Ghost (TODO-24)

> Note de recherche (2026-10-06), dépôt client. Question d'Adrien : existe-t-il une API qui donne les régulations
> Smogon, et peut-on intégrer les clauses (Sleep Clause, etc.) ?

## 1. Y a-t-il une API Smogon ?

**Pas d'API officielle Smogon.** La référence de fait est **Pokémon Showdown** (projet du même groupe, licence MIT),
qui implémente toutes les régulations Smogon et VGC :

| Source | Contenu | Fraîcheur |
|---|---|---|
| `https://play.pokemonshowdown.com/data/formats.js` | Tous les formats (`[Gen 9] OU`, `Ubers`, `UU`, `Monotype`, `1v1`, VGC…) avec leur `ruleset` (règles et clauses) et leur `banlist` | À jour (on y trouve « [Gen 9 Champions] VGC 2026 Reg M-C ») |
| `https://play.pokemonshowdown.com/data/formats-data.js` | Tier de chaque Pokémon (`tier`, `doublesTier`, `natDexTier`) | À jour (Carchacrok : `UUBL`) |
| Dépôt GitHub `smogon/pokemon-showdown` | Sources complètes : `config/formats.ts`, `data/rulesets.ts` (définition de chaque règle), `sim/team-validator.ts` | À jour |
| Projet pkmn (`data.pkmn.cc`, bibliothèques `@pkmn/*`) | Mêmes données en JSON, plus statistiques d'usage Smogon | Non vérifié en détail |

Ce sont des fichiers JavaScript publics, pas une API documentée : le format peut changer, il faut les lire avec
tolérance et garder une copie locale de secours.

## 2. Ce que Cobblemon embarque déjà

Cobblemon 1.8.1 contient une copie complète de Showdown (`showdown/` de l'instance) :

- `data/rulesets.js` : **141 règles**, dont `Sleep Clause Mod`, `Species Clause`, `OHKO Clause`, `Evasion Moves
  Clause`, `Evasion Items Clause`, `Evasion Abilities Clause`, `Endless Battle Clause`, `Freeze Clause Mod`,
  `Item Clause`, `Baton Pass Clause`, `Moody Clause`, `Swagger Clause`, `Terastal Clause`, `Dynamax Clause`,
  `Z-Move Clause`, `Mega Rayquaza Clause`, `HP Percentage Mod`…
- `config/formats.js` : **280 formats**, ex. `[Gen 9] OU` = `Standard` + `Sleep Moves Clause` + `!Sleep Clause Mod`,
  banlist `Uber`, `AG`, `Arena Trap`, `Moody`, `Sand Veil`, `Shadow Tag`, `Snow Cloak`, `King's Rock`, `Razor Fang`,
  `Baton Pass`, `Last Respects`, `Shed Tail`.
- `data/formats-data.js` : tiers.

**Mais cette copie date d'environ mi-2024** (« BSS Reg F », « VGC 2024 Reg F » ; Carchacrok encore `UU`).

Côté Java, `BattleFormat(mod, battleType, ruleSet, gen, adjustLevel)` transmet son `ruleSet` à Showdown au démarrage
du combat. Le format actuel de nos combats (`GEN_9_SINGLES`) n'a que `Obtainable`, `+Past`, `+Unobtainable`.

## 3. Deux familles de clauses

Showdown distingue deux sortes de règles, et c'est le point clé :

| Famille | Exemples | Où elle agit | Pour nous |
|---|---|---|---|
| **Règles de combat** (crochets dans le moteur) | Sleep Clause Mod, Freeze Clause Mod, Endless Battle Clause, HP Percentage Mod, Switch Priority Clause Mod | Pendant le combat | **Gratuites** : il suffit de les ajouter au `ruleSet` du `BattleFormat` passé à `startHostedBattle`. |
| **Règles d'équipe** (validateur) | Species Clause, Item Clause, OHKO Clause, Evasion Moves / Items / Abilities Clause, banlists, tiers (`Uber`…), Baton Pass Clause | Avant le combat, par le `TeamValidator` de Showdown | **À faire nous-mêmes** : Cobblemon ne lance jamais ce validateur pour un combat ; il faut valider les équipes au moment du « Prêt » dans le lobby. |

## 4. Où valider les équipes

| Option | Avantages | Inconvénients |
|---|---|---|
| **A. Backend (Java), jeu de clauses réimplémenté** | Autorité unique (le backend voit les deux équipes au lobby) ; message d'erreur clair (`ERROR_BATTLE_TEAM_NOT_ALLOWED` + raison) ; pas de dépendance JS | Réécrire les ~15 clauses utiles (simples : doublons d'espèce / d'objet, listes d'attaques OHKO / évasion, talents, tiers, banlist) ; ne refait pas la légalité complète des movesets (déjà couverte par `PokemonLegalityService`) |
| **B. Client hôte, `TeamValidator` de Showdown via GraalJS** | Couverture complète, identique à Showdown | Repose sur le client hôte (modifiable) ; validateur lourd ; copie Showdown datée de 2024 ; erreurs en anglais brut |

**Recommandation : A.** Le backend télécharge `formats.js` et `formats-data.js` (au démarrage puis une fois par jour),
en garde une copie locale (et une copie de secours livrée avec le backend si le site est injoignable), et en extrait
pour chaque format proposé : la liste des règles et la banlist. Les règles de combat sont transmises au client hôte
dans `BattleSessionStarted`, qui les ajoute au `BattleFormat`.

## 5. Proposition de mise en œuvre (à valider avec Adrien)

1. **Choix du format dans le lobby** (par l'inviteur, visible des deux, modifiable tant que personne n'est prêt) :
   « Libre » (comportement actuel), puis quelques formats Smogon simples en simple : OU, Ubers, UU, Monotype, 1v1,
   Anything Goes. Les formats doubles / VGC attendent les combats en double.
2. **Validation au « Prêt »** côté backend : refus avec la règle en cause (« Species Clause : deux Carchacrok »,
   « Banni en OU : Mewtwo »…), affichée dans le pied de page du lobby.
3. **Règles de combat** ajoutées au `BattleFormat` de l'hôte (Sleep Clause Mod, Endless Battle Clause…).
4. **Niveau** : les formats Smogon jouent au niveau 100, VGC au niveau 50 ; `BattleFormat.adjustLevel` peut forcer
   le niveau. À décider : forcer le niveau du format, ou garder le niveau des Ghost.
5. **Méga-Évolution / Cristaux Z** : interdits dans les formats Gen 9 Smogon standards (banlist) ; à garder en tête
   avec TODO-28.

## 6. Points ouverts

- Liste exacte des formats à proposer, et si « Libre » reste le format par défaut.
- Niveau forcé ou non (point 4).
- Équipe Cobblemon (copie de l'équipe réelle, D-22) : mêmes règles que les Ghost.
- Droits : données de Pokémon Showdown sous licence MIT (réutilisation possible, avec mention).
