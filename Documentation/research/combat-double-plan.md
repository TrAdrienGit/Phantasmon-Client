# Combat double : plan d'intégration

Statut : **étapes 1 à 5 développées et déployées** (2026-11-27), en attente de l'essai en jeu à deux comptes
(étape 6). Mise en scène : simplement correcte (placement côte à côte, caméra qui cadre le milieu des deux Pokémon).

## Ce qui est fait (2026-11-27)

- **Backend** : chaque format garde le `gameType` de Showdown ; formats doubles proposés : Doubles OU, Doubles
  Ubers, Doubles UU, National Dex Doubles, Champions Doubles. Les tiers doubles (DUber, DOU, DBL, DUU) sont lus et
  vérifiés, ainsi que la Gravity Sleep Clause. Le lobby fait choisir les Pokémon dans l'ordre (message `BattleLobbyPick`,
  `own_picks`, `battle_size`) : 1 et 2 mènent (gauche, droite), les suivants attendent ; format Champions VGC 2026
  Reg M-C (Flat Rules : 4 parmi 6, niveau 50, ni Mythical ni Restricted Legendary, bannis par tag d'espèce). Le format envoyé à l'hôte
  porte `game_type`. Un PNJ d'Events peut être réglé sur un format double (ajouté le jour même, pour les essais) : ses deux premiers Pokémon mènent, son IA joue chacun ; les IA expert et Reborn raisonnent contre le premier adversaire encore debout et le visent.
- **Client** : `GhostBattles` démarre en `GEN_9_DOUBLES` quand `game_type` vaut `doubles` ; les Pokémon d'un même
  côté sont posés côte à côte (`BattleVisuals.besideOnSlot`, 3,2 blocs d'écart, la place a à gauche de son
  dresseur), aussi dans les combats vus de loin. Lobby : clic dans l'ordre 1, 2, 3, 4 (numéro et côté affichés sur
  chaque Pokémon choisi), les deux leads affichés dans la carte ; l'équipe d'un PNJ est coupée à `picked_team_size`
  après que son IA l'a ordonnée. Panneau Showtime (musique, caméra, timer) descendu sous la deuxième tuile adverse en
  double ; écart des Pokémon selon la largeur de leur modèle.
- **Showtime** : `BattleStage.activePokemons` / `sidePosition` (méthodes par défaut, l'API reste compatible) ; les
  caméras v1 et v2 cadrent le milieu des Pokémon actifs de chaque côté (inchangé en simple).

## But

Permettre les combats Phantasmon en **double** (deux Pokémon par côté), avec leurs formats (Doubles OU, VGC,
Champions Doubles…), dans le lobby comme dans les tournois. C'est la première étape vers les autres types de combat
demandés : triple, et plus tard des combats « custom » (par exemple un Pokémon contre quatre).

## Ce qui existe déjà

- **Le moteur sait faire le double.** Le combat Ghost est un vrai combat Cobblemon hébergé par le client hôte
  (`GhostBattles`). Cobblemon a déjà le double et le triple (`BattleFormat.GEN_9_DOUBLES`, `GEN_9_TRIPLES`), avec
  son interface de combat : choix de l'attaque pour chacun des deux Pokémon, choix de la cible. Aujourd'hui,
  `GhostBattles` démarre toujours en simple (`GEN_9_SINGLES`).
- **Les positions sont déjà génériques.** Tout le code de rendu parle en positions Showdown (`p1a`, `p1b`, `p2a`…) :
  `BattleVisuals.entityAt`, les effets d'attaques, les changements de forme, l'API de Showtime (`BattleStage`).
- **Les IA de Cobblemon jouent Pokémon par Pokémon.** `safeChoice` est appelé pour chaque Pokémon actif : une IA de
  Cobblemon répond déjà pour deux Pokémon.
- **Les formats doubles sont dans les données Showdown** que lit le backend : Doubles OU, Ubers, UU, LC, VGC 2025
  Reg I, National Dex Doubles, Champions Doubles, Champions VGC 2026 Reg M-C…

## Ce qui manque

| Domaine | Manque | Où |
|---|---|---|
| Moteur | Démarrer en double quand le format le demande | `GhostBattles` (client) |
| Placement | Les deux Pokémon d'un côté sont posés **au même endroit** (une place par dresseur, pas par position) | `BattleVisuals.placement`, `BattleFieldScenes` |
| Lobby | Un seul lead ; il en faut deux. Pas de « choisir 4 sur 6 » (VGC) | `PhantasmonBattleLobbyScreen`, `BattleLobbyState`, `LiveBattleService` (backend) |
| Formats | Les formats n'ont pas de type de combat ; aucun format double proposé | `BattleFormats`, `TeamValidator` (backend), liste du lobby |
| Mise en scène | Showtime est pensé pour un Pokémon par côté : plans de caméra, intro (envoi de deux Pokémon), séquence Méga, attaques qui touchent deux cibles | Cobblemon Battle Showtime |
| IA des PNJ | Les IA d'Events (expert, Reborn) ne savent jouer qu'en simple | Phantasmon Events |
| Tournois | Les formats doubles ne sont pas dans le formulaire | Phantasmon Events |

## Plan

1. **Formats avec un type de combat (backend).** Chaque format garde son `gameType` de Showdown (`singles`,
   `doubles`). Formats doubles proposés (à confirmer, voir Décisions) : Doubles OU, Doubles Ubers, Doubles UU,
   National Dex Doubles, Champions Doubles. Le lobby transmet le type au client hôte au démarrage du combat. Les
   règles « Standard Doubles » sont vérifiées comme les autres (clauses déjà gérées, plus la Gravity Sleep Clause
   jouée par le moteur).
2. **Le moteur en double (client).** `GhostBattles` démarre en `DOUBLES` selon le type reçu ; vérifier le
   chronomètre, l'abandon, le match nul, le choix automatique (une réponse par Pokémon actif), les spectateurs et le
   combat entre deux serveurs.
3. **Le placement.** Une place par position et plus par dresseur : les deux Pokémon d'un côté côte à côte, écartés
   latéralement, face à l'adversaire. Pareil pour les envois, rappels et remplacements, et pour le terrain vu de loin.
4. **Le lobby.** Choix de deux leads en double (cliquer deux Pokémon, ordre gauche / droite). Le reste du lobby ne
   change pas.
5. **La mise en scène Showtime.** Plans de caméra qui cadrent deux Pokémon par côté ; intro qui envoie les deux
   Pokémon ; séquence Méga ; effets des attaques à plusieurs cibles (Séisme, Éboulement…) joués sur chaque cible.
   C'est la partie la plus longue ; elle peut suivre en plusieurs fois une fois le double jouable.
6. **Essai en jeu à deux comptes**, puis les ajouts qui en dépendent :
   - **« Choisir 4 sur 6 »** pour le VGC (et en simple pour le BSS de Champions, laissé de côté aujourd'hui) ;
   - **les tournois en double** (formulaire d'Events) ;
   - **les PNJ en double** : d'abord avec l'IA de Cobblemon, les IA expert et Reborn restant en simple.

## Après le double

- **Triple** : Cobblemon le gère aussi (`GEN_9_TRIPLES`) ; une fois le placement et la mise en scène pensés pour
  « n Pokémon par côté », c'est surtout du réglage. Showdown n'a plus de format triple officiel : on proposerait un
  format « Triple libre ».
- **Combats custom** (un Pokémon contre quatre, etc.) : Showdown ne fait pas de côtés inégaux en nombre de Pokémon
  actifs. Il faudra une étude à part (variante de règles dans le moteur, ou format « boss » à la manière des raids).

## Décisions

- [x] **Formats doubles proposés** : Doubles OU, Doubles Ubers, Doubles UU, National Dex Doubles, Champions Doubles.
  Le VGC (Reg I, Champions Reg M-C) attend le « choisir 4 sur 6 » de l'étape 6. *(recommandé)*
- [x] **Mise en scène** : le double est jouable et validé d'abord avec une mise en scène simplement correcte (placement
  et caméra qui cadre les deux), les plans soignés viennent ensuite. *(recommandé)*
- [x] **Ordre** : le double avant la rediffusion des combats, pour que la rediffusion soit faite d'emblée pour le
  simple et le double. *(recommandé)*
- [x] **PNJ** : pas de PNJ en double dans un premier temps. *(recommandé)* Revu le 2026-11-27 : les PNJ d'Events acceptent les formats doubles, pour pouvoir tester seul ; les IA expert et Reborn ne pensent pas encore le double (une cible à la fois, pas de jeu d'équipe).

## Risques

- **Showtime** est un mod à part ; les retouches de caméra et d'intro y touchent beaucoup de code pensé pour le simple.
- **L'interface de combat de Cobblemon en double** n'a jamais été vue dans un combat Ghost : choix des cibles, attaques
  bloquées, ordre des demandes. À vérifier tôt (étape 2).
- **Les packs du modpack** (DeltaClient, Battle Extras…) modifient l'interface ou le rendu des combats : un doublon
  d'affichage en double est possible.
