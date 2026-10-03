# 14. Études de cas : bugs réels

Chaque cas : **symptôme**, **enquête**, **cause**, **correction**, **leçon**. Tous viennent de l'histoire réelle du
mod (détail daté dans `../project/development-journal.md`). Les études de cas côté serveur sont dans le parcours du
backend (chapitre 15).

---

## Cas 1 — L'écran PC qui ne s'ouvre pas

- **Symptôme** : `/phantasmon pc` ne fait rien. Pourtant le log montre que la requête réseau a réussi, et un autre
  mod du modpack signale même que l'écran a été enregistré.
- **Enquête** : l'écran est donc bien créé… puis disparaît. En décompilant `ChatScreen.keyPressed` : après avoir
  exécuté la commande tapée, Minecraft appelle `setScreen(null)` **sans condition**.
- **Cause** : la commande ouvrait l'écran pendant son exécution, sur le thread client ; la fermeture du chat
  passait juste après et l'écrasait, dans le même appel.
- **Correction** : la commande pose un drapeau ; l'écran est ouvert au tick suivant (chapitre 5.4).
- **Leçon** : quand une action « ne fait rien », vérifier si elle est **annulée** par ce qui suit. Décompiler le
  code du jeu donne la réponse exacte.

## Cas 2 — Des UUID refusés

- **Symptôme** : `delete`, `clone`, `sendout` échouent avec « UUID non valide ».
- **Cause** : le chat affichait un UUID tronqué à 8 caractères (le complet n'était accessible que par un clic
  peu visible) ; le joueur recopiait la version tronquée, que l'argument `UUID` de Brigadier rejette à raison.
- **Correction** : afficher l'UUID **complet** ; un clic pré-remplit la barre de chat.
- **Leçon** : tout ce que le joueur doit retaper doit être affiché en entier. Mieux encore : éviter de faire taper
  des UUID (touches et invitations par pseudo sont arrivées ensuite).

## Cas 3 — « Envoi en cours… » et puis rien

- **Symptôme** : après correction côté serveur (le propriétaire reçoit maintenant son propre Ghost), le chat affiche
  « Envoi en cours… » mais jamais la confirmation, et aucun Ghost.
- **Cause n°1** : `GhostSession.start()` mettait `connected = true` **immédiatement**, avant la fin du handshake
  WebSocket, sans gérer l'échec. Si la connexion échouait ou n'était pas finie, chaque envoi était ignoré en
  silence, et le message « Envoi en cours… » s'affichait quand même.
- **Correction** : `connected = true` seulement dans `.thenRun(...)`, message d'erreur dans `.exceptionally(...)`,
  et des logs à chaque étape (connexion, envoi ignoré).
- **Leçon** : un état « connecté » doit refléter un fait confirmé, pas une intention. Et un message de progression
  affiché sans condition masque les échecs.

## Cas 4 — Le groupe jamais rejoint

- **Symptôme** : toujours pas de Ghost. Les nouveaux logs du serveur montrent `WebSocket connected`, puis
  `broadcasting to 0 group member(s)`, mais **aucune** ligne `joined group`.
- **Enquête** : `JoinServerGroup` n'est donc jamais envoyé. Dans `GhostSession`, le planificateur (1 fois par
  seconde) envoyait `JoinServerGroup` si `lastKnownDimension == null`. Mais `onClientTick()` (20 fois par seconde)
  remplissait cette même variable pour détecter les changements de dimension, en une fraction de seconde.
- **Cause** : une variable servant à **deux usages** ; le plus rapide écrasait la condition du plus lent. Sans
  groupe, le serveur ignorait en silence positions et sorties de Ghost (`computeIfPresent`), et le Ghost partait
  avec une position nulle, que le client ignore.
- **Correction** : un drapeau dédié `joinedGroup`, avec un commentaire qui explique pourquoi ne pas réutiliser
  l'autre variable.
- **Leçons** : une variable = un usage. L'**absence** d'une ligne de log attendue est un indice décisif. Ce bug
  était invisible pour les tests du serveur, qui envoient toujours `JoinServerGroup` eux-mêmes.

## Cas 5 — Les modèles 3D décentrés

- **Symptôme** : dans l'écran PC, les modèles débordent, sont trop hauts, trop bas… plusieurs corrections à
  l'aveugle échouent.
- **Enquête** : comparer avec le **bytecode** de l'écran PC de Cobblemon (`StorageSlot.renderSlot`) : il applique
  une translation en z = 0 et une **mise à l'échelle extérieure** (`pose.scale(2.5f, 2.5f, 1f)`) avant d'appeler
  `drawProfilePokemon`, et ne change jamais l'échelle interne (4.5).
- **Cause** : on agrandissait via le paramètre interne, qui n'est pas linéaire et décale le modèle.
- **Correction** : reproduire exactement l'enchaînement de Cobblemon, régler la taille par l'échelle extérieure
  seulement. Pour le cadrage fin, une commande de debug temporaire a permis à Adrien de régler la valeur en jeu, puis
  elle a été retirée et la valeur figée.
- **Leçon** : quand on reproduit le comportement d'un code existant, copier **tout** l'enchaînement observé, pas
  seulement l'appel principal. Pour un réglage visuel, un outil de réglage en direct vaut mieux que des allers-retours.

## Cas 6 — La liste déroulante sous les boutons

- **Symptôme** : dans l'éditeur, le texte des boutons s'affichait **par-dessus** la liste déroulante ouverte.
- **Cause** : en 1.21.1, le texte et les rectangles sont dessinés par lots, plus tard, alors que les textures sont
  dessinées immédiatement : l'ordre du code n'est pas l'ordre à l'écran.
- **Correction** : ne plus dessiner les éléments recouverts par la liste, et `flush()` explicite ; par la suite, le
  socle commun fait un `flush()` avant chaque texture et place les fenêtres à z = 2000.
- **Leçon** : connaître le modèle de rendu de la version utilisée.

## Cas 7 — Aucun objet dans le sélecteur, talents non traduits

- **Symptôme** : le sélecteur d'objets est vide ; les talents s'affichent `cobblemon.ability.torrent`.
- **Cause** : (1) le filtre utilisait un composant de données qui n'est pas posé par défaut sur les objets ; la
  vraie source est le tag `#cobblemon:held/is_held_item`. (2) `AbilityTemplate.getDisplayName()` renvoie une **clé**
  de traduction, pas un texte (alors que `MoveTemplate.getDisplayName()` renvoie un texte traduit).
- **Correction** : lire le tag ; envelopper les clés dans `Component.translatable`.
- **Leçon** : vérifier le **type réel** de ce que renvoie une API (clé ou texte), et chercher la source de vérité
  que le mod tiers utilise lui-même.

## Cas 8 — Les formes affichées en forme de base

- **Symptôme** : Arceus Fée apparaît en Arceus Normal, Ogerpon sans masque, les chromatiques ne brillent pas.
- **Cause** : Cobblemon choisit le modèle par les **aspects** (`fairy-plate`), pas par le nom de forme. Et sur une
  entité locale, personne ne remplit les aspects synchronisés que le renderer lit.
- **Correction** : retrouver la forme par correspondance tolérante, écrire ses aspects (+ `shiny`, + sexe) dans les
  données synchronisées de l'entité.
- **Leçon** : comprendre **quelle donnée** le système de rendu lit réellement, et qui est censé la fournir.

## Cas 9 — Les deux joueurs ne se voient pas en LAN

- **Symptôme** : premier test à deux comptes. Chacun voit son Ghost, pas celui de l'autre.
- **Cause** : l'hôte de la partie LAN calcule l'empreinte `"singleplayer"`, l'invité un hash de l'adresse : jamais
  le même groupe.
- **Correction** : une commande de test qui force la même empreinte des deux côtés (persistée, appliquée
  immédiatement). Pas de changement en production : sur un serveur dédié, tous calculent la même valeur.
- **Leçon** : distinguer un bug de production d'un artefact de la méthode de test.

## Cas 10 — Le gros Pokémon qui pousse son dresseur

- **Symptôme** : Arceus ou Rayquaza poussent le joueur en le suivant.
- **Cause** : la poussée entre entités (`Entity.push`) quand les hitbox se chevauchent.
- **Correction** : `noPhysics = true` en permanence (aucune poussée), désactivé seulement pendant notre propre
  `Entity.move` pour garder les collisions avec les blocs ; point de suivi écarté selon la taille.
- **Leçon** : lire ce qu'un drapeau désactive exactement (ici collisions **et** poussée) pour l'utiliser au bon
  moment.

## Cas 11 — Un combat qui plante uniquement chez le client pur

- **Symptôme** : combat à deux comptes. Hébergé par le joueur qui a ouvert la partie LAN : l'invité ne voit aucun
  Pokémon sortir. Hébergé par l'autre joueur (client pur) : plus rien ne se passe après la création du combat.
- **Causes** : (1) `PokemonProperties.create()` initialise un moveset avec des données **de serveur** (« moveset
  builders »), absentes chez un client pur. (2) Cobblemon envoie chaque message de Showdown par `runOnServer`, qui
  sans serveur ne fait **rien**, en silence.
- **Corrections** : (1) `new Pokemon()` + seulement les propriétés visuelles ; (2) `DistributionUtilsMixin`
  redirige `runOnServer` vers notre thread de combat, seulement pour nos appels.
- **Leçon** : tester les deux situations (serveur intégré et client pur) ; les hypothèses « il y a un serveur »
  sont cachées partout dans un mod pensé pour le serveur.

## Cas 12 — Les attaques sans animation

- **Symptôme** : boosts et statuts animés, attaques non.
- **Enquête** : chaque instruction stocke son animation via `setFuture`, sur lequel un Mixin est accroché. Le
  bytecode de `MoveInstruction` montre une écriture **directe** du champ (`PUTFIELD future`).
- **Correction** : `MoveInstructionMixin` redirige cette écriture vers `setFuture`. Un `WARN` signale désormais toute
  animation non reliée.
- **Leçon** : en Kotlin, une affectation de propriété peut compiler en accès direct au champ et contourner le
  setter. Lire le bytecode quand une interception ne se déclenche pas.

---

## Ce que ces cas ont en commun

- Les bugs les plus coûteux étaient **silencieux** : rien ne plante, « rien ne se passe ». Logs aux étapes clés et
  messages d'erreur explicites sont indispensables.
- La réponse était presque toujours **dans le code réel** (décompilé) de Minecraft ou de Cobblemon, pas dans une
  supposition.
- Plusieurs bugs n'existaient que dans une configuration (client pur, LAN, deux comptes) : il faut tester toutes les
  configurations réelles.
