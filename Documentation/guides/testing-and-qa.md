# Tests et recette manuelle

## 1. Tests automatisés

```bash
./gradlew test
```

Seule la **logique pure** (sans Minecraft) est testée automatiquement ; le rendu et les interactions avec le monde
relèvent de la recette manuelle ci-dessous (choix assumé du CAD Partie 4).

| Classe de test | Couvre |
|---|---|
| `pokemon/showdown/ShowdownParserTest` | Analyse du texte Showdown, plusieurs Pokémon, valeurs par défaut |
| `pokemon/showdown/ShowdownImportMapperTest` | Conversion vers la requête de création (IV 31 / EV 0 par défaut) |
| `pokemon/showdown/CobblemonIdentifiersTest` | Normalisation des identifiants, exceptions à tiret |
| `pokemon/HiddenPowerCalculatorTest` | Type de Puissance Cachée |
| `pokemon/NatureModifiersTest` | Bonus/malus des natures |
| `pokemon/PokemonGenderTest` | Sexe stocké ou imposé par l'espèce |
| `version/VersionCompatibilityTest` | Comparaison de versions |
| `trade/LiveTradeStateTest` | Miroir de l'état d'échange en direct |
| `network/GsonUuidSanityTest` | Un `UUID` est sérialisé en chaîne par Gson (garde-fou permanent) |

Le jeu de sources `client` est branché sur les tests dans `build.gradle`. Toute nouvelle logique sans dépendance à
Minecraft doit être extraite dans une classe testable et couverte.

## 2. Préparer une recette

- Backend lancé (voir `Phantasmon-Backend/Documentation/guides/running.md`) et joignable à l'adresse de
  `backend_url` (`config/phantasmon.json` de l'instance).
- Mod compilé et installé (voir [`deployment.md`](deployment.md) pour les instances de test).
- Un compte Microsoft ; **deux comptes** pour les échanges, la visibilité mutuelle et les combats.
- Garder le log du jeu (`logs/latest.log`) et la console du backend ouverts.

## 3. Recette par fonctionnalité

### Connexion

- [ ] Entrée dans un monde, backend actif : connexion automatique, aucune action requise.
- [ ] Backend arrêté : aucun message parasite ; `/phantasmon login` affiche une erreur réseau traduite.
- [ ] `phantasmon.version.min-supported` du backend au-dessus de la version du mod : refus avec lien de mise à jour.
- [ ] `phantasmon.jwt.access-ttl=PT3M` : après 3 minutes, le log du backend montre un `POST /auth/refresh -> 200`.
- [ ] Jeu en anglais puis en français : aucun texte sous forme de clé brute.

### PC et éditeur

- [ ] **P** ouvre le PC ; équipe, fiche et grille affichées ; modèles 3D centrés dans les cases.
- [ ] Glisser PC → équipe vide, PC → équipe occupée (échange), équipe ↔ équipe, PC ↔ PC, vers une autre boîte.
- [ ] IMPORTER avec plusieurs Pokémon Showdown ; texte invalide → message d'erreur.
- [ ] SUPPRIMER → confirmation → Pokémon disparu.
- [ ] Éditeur : chaque champ, recherche d'objet et d'attaque, pas de doublon d'attaque, total EV > 510 en rouge,
      IMPORTER dans l'éditeur, ENREGISTRER, Échap avec modifications (confirmation).
- [ ] Sexe : espèce mixte (bascule), espèce à sexe fixe ou asexuée (grisé), import avec `(M)` / `(F)`.
- [ ] Formes (Arceus Fée, Motisma Lavage, Ogerpon Fontaine…) et chromatiques correctement rendus.
- [ ] Échelles d'interface 1 à 4 et fenêtre 1280×720 : écrans entiers, centrés, cliquables.

### Ghost

- [ ] Overlay Ghost (O), **R** : animation de sortie, le Ghost suit ; **R** à nouveau : rappel animé.
- [ ] Emplacement 1 vide : message d'erreur.
- [ ] Tourner la caméra sur place : le Ghost ne bouge pas. Immobile 5 s : il se promène.
- [ ] Gros Pokémon (Arceus, Rayquaza) : ne pousse pas le joueur, en marchant, en sprintant, en demi-tour.
- [ ] Espèces volantes : vol stationnaire.
- [ ] Mort, changement de dimension, déconnexion : le Ghost disparaît (aussi chez l'autre joueur).
- [ ] Second joueur arrivé après la sortie : il voit le Ghost sans que le propriétaire bouge.

### Échange en direct (deux comptes)

- [ ] Invitation par la roue **R** (« Échange Ghost ») ; [Accepter] ouvre l'écran chez les deux.
- [ ] Changer d'offre met à jour la fiche de l'autre et remet « prêt » à faux des deux côtés.
- [ ] Les deux prêts : animation, « échange terminé », fermeture automatique ; le Pokémon reçu est à
      l'emplacement d'équipe du Pokémon donné.
- [ ] QUITTER (ou Échap) → confirmation → l'autre écran se ferme avec un message.
- [ ] Invitation laissée sans réponse 60 s : expirée.

### Combat (deux comptes)

- [ ] Invitation par la roue **R** (« Combat Ghost ») ; [Accepter] lance l'interface de combat Cobblemon
      chez les deux.
- [ ] Les deux sens d'hébergement : hôte sur serveur intégré (LAN) **et** hôte client pur (l'hôte alterne d'un
      combat à l'autre).
- [ ] Attaques, changements, K.O., objets, boosts, statuts ; animations de sortie, de rappel et d'attaque.
- [ ] Chrono activé : compte à rebours, action automatique à l'expiration.
- [ ] Abandon : victoire de l'autre. Déconnexion : combat annulé.

### Spectateurs et combat solo (D-31, D-32)

- [ ] Admin : `/phantasmon admin battle solo` : intro, combat contre « pseudo (miroir) », l'IA joue seule ; victoire
      ou défaite annoncée ; rien dans l'historique.
- [ ] Un second joueur vise l'admin en combat, **R** → « Regarder le combat Ghost » : écran de combat Cobblemon en mode
      spectateur, Pokémon actuels affichés, puis chaque tour ; l'admin lit « X regarde le combat ».
- [ ] Méga / Z / Téra pendant que l'on regarde : mêmes effets que chez les joueurs.
- [ ] Bouton Retour : le spectateur quitte, l'écran se ferme, la scène disparaît ; il peut regarder de nouveau.
- [ ] Fin du combat : « Combat terminé : X l'emporte » chez le spectateur, écran fermé.
- [ ] Regarder un joueur qui n'est pas en combat : « Ce joueur n'est pas en combat Ghost ».
- [ ] Combat entre deux joueurs, un troisième regarde ; aussi depuis un autre serveur via un avatar du Global Hub.

### Terrain vu sans regarder (D-33)

- [ ] Même serveur : J1 combat J2, J3 à côté sans regarder voit les Pokémon devant chacun, les sorties, rappels,
      K.O., animations d'attaque, Méga / Z / Téra (apogée seulement), sans écran ni caméra ni musique.
- [ ] Global Hub : J1 (S1) combat J3 (S2) ; J4 dans le Hub depuis S3 voit les Pokémon devant les avatars.
- [ ] J2 sur S1 hors Hub voit les Pokémon de J1 et, à 7 blocs devant J1, ceux de J3.
- [ ] Arriver en plein combat (entrer dans le Hub, changer de dimension et revenir) : Pokémon actuels affichés.
- [ ] Le témoin se met à regarder : la scène de loin disparaît sans rappel, l'écran spectateur prend le relais ;
      Retour : la scène de loin revient en une seconde environ.
- [ ] Fin du combat, sortie du Hub, changement de serveur : rappel des Pokémon chez le témoin.

### Construction du Hub (D-34)

- [ ] Backend lancé avec l'arène par défaut, puis avec la vraie construction (`.schem` et `.litematic`) ; deux fichiers
      ou une mauvaise taille : le backend refuse de démarrer avec un message clair.
- [ ] `/phantasmon hub anchor create` sur un terrain encombré : refus, nombre de blocs gênants ; sur un terrain dégagé :
      la construction apparaît, le joueur se retrouve sur le sol.
- [ ] Orientation : poser face au nord, à l'est, au sud, à l'ouest — l'entrée suit le regard.
- [ ] Rendu identique à de vrais blocs, avec et sans Iris (ombres, lumière des lanternes).
- [ ] Collisions : marcher sur le sol, se cogner aux murs ; un second joueur Phantasmon du serveur voit la salle.
- [ ] Incassable (survie et créatif) ; rien ne se pose contre un faux bloc ; la porte s'ouvre et se ferme.
- [ ] S'éloigner au-delà de la distance de rendu puis revenir : la salle est toujours là.
- [ ] `/phantasmon hub anchor delete` : la salle disparaît, le terrain d'origine revient.
- [ ] Serveur avec `allow-flight=false` : noter si le joueur est expulsé en restant sur le faux sol (LIM-14).

### Plusieurs hubs (D-35)

- [ ] Admin : `/phantasmon admin hub create arene 41 31 12` : message, dossier `hub_schematics/hub_arene/` créé ; un
      joueur voit « arene » dans la complétion de `/phantasmon hub anchor create` sans relancer le jeu.
- [ ] Hub sans construction : l'Anchor se pose, le contour (rectangle) suit l'orientation, on peut entrer dans le hub.
- [ ] Déposer un schematic de 31 × 12 × 41 puis `/phantasmon admin hub reload arene` : la construction apparaît chez
      tous ; un fichier de mauvaise taille : refus avec la raison, l'ancienne construction reste.
- [ ] Un Anchor par hub : un second dans `arene` refusé, un dans `global` accepté ; deux Anchors qui se touchent :
      refus « chevaucherait ».
- [ ] Espaces séparés : J1 dans `global`, J2 dans `arene` ne se voient pas, `/hc` ne passe pas de l'un à l'autre.
- [ ] `/phantasmon admin hub delete arene` : constructions disparues, membres sortis, dossier renommé `.deleted-…`.

### Global Hub (Phantasmon Network, N3)

Deux serveurs (ou `/phantasmon admin debug fingerprint` avec deux valeurs différentes) et deux comptes.

- [ ] `/phantasmon hub anchor create Test` : message de confirmation, carré violet au sol, nom flottant au centre ;
      un second `create` répond « vous avez déjà un Anchor ».
- [ ] Partage (D-30) : un second joueur **du même serveur** voit le carré et le nom (rechargement toutes les 30 s au
      plus) et peut entrer dans le Hub par cet Anchor ; un joueur d'un **autre** serveur (autre empreinte) ne le voit pas.
- [ ] Entrer dans le carré : invitation [Oui] [Non] [Toujours ici] ; [Non] puis ressortir et revenir : nouvelle invitation.
- [ ] [Oui] sur deux serveurs différents : chacun reçoit « … est arrivé dans le Hub » ; `/hc salut` arrive chez
      l'autre ; deux messages en moins d'une seconde : le second est refusé.
- [ ] Sortir du carré : « Vous avez quitté le Global Hub », l'autre voit le départ.
- [ ] [Toujours ici] : sortir puis revenir fait rentrer sans invitation ; `/phantasmon hub autojoin off` l'annule.
- [ ] Rester près d'un Anchor avec le jeu en pause (Échap, monde solo) une minute puis reprendre : pas de rafale de
      particules ni de saccade.
- [ ] `/phantasmon hub anchor delete` en étant dans le Hub : « l'Anchor … a été supprimé », sortie du Hub.
- [ ] Redémarrer le backend en étant dans le Hub : après la reconnexion, retour dans le Hub sans nouvelle invitation.

### Global Hub — avatars (Phantasmon Network, N4)

- [ ] Deux comptes sur deux empreintes, chacun dans son Anchor et dans le Hub : chacun voit l'avatar de l'autre, à
      la même place relative au centre de l'Anchor, avec son skin (skin par défaut une ou deux secondes au plus) et
      « Pseudo [Hub] » au-dessus.
- [ ] Marche, course, saut, accroupissement, rotation de la tête : fluides, sans téléportation.
- [ ] Anchors orientés différemment (créés en regardant dans deux directions) : « devant » l'Anchor reste « devant ».
- [ ] Relief différent d'un Anchor à l'autre : l'avatar marche sur le sol local, sans flotter ni s'enfoncer.
- [ ] Couche supérieure du skin (chapeau, veste, manches, jambes) et cape visibles ; décocher une partie dans
      Options → Personnalisation du skin la retire aussi sur l'avatar chez l'autre.
- [ ] Collision comme entre deux joueurs : on se pousse l'un l'autre en se rentrant dedans (pas de mur) ; l'avatar ne se
      frappe pas ; aucun avertissement du serveur Minecraft.
- [ ] Sortie du Hub, déconnexion ou changement de dimension de l'un : son avatar disparaît chez l'autre.
- [ ] Deux joueurs du même serveur dans le même Anchor : ils se voient pour de vrai, aucun avatar en double. Par
      deux Anchors différents du serveur : chacun voit l'avatar de l'autre.
- [ ] Modpack : pas d'erreur de catchindicator, DeltaClient ou ShoulderSurfing ; l'avatar n'apparaît pas dans la
      liste des joueurs (Tab).

### Global Hub — Ghost (Phantasmon Network, N5)

> **Empreintes** : deux mondes solo distincts sont deux « serveurs » : leur donner **deux** empreintes différentes
> (`/phantasmon admin debug fingerprint mondeA` / `mondeB`). Avec la même empreinte forcée, Phantasmon les croit sur
> le même serveur alors que le vrai joueur n'est pas dans le monde de l'autre : le groupe serveur du Core y affiche
> son Ghost à ses coordonnées relayées, en plus du Ghost du Hub (fausse impression de doublon, 2026-10-07). Le cas
> « même serveur » se teste avec les deux comptes dans le **même** monde (ouverture au LAN).

- [ ] Dans le Hub, sortir un Ghost (overlay Ghost, R) : chez l'autre joueur, le Ghost apparaît en sortant de sa
      Poké Ball à côté de l'avatar, le suit, se balade quand l'avatar reste immobile.
- [ ] Rappeler le Ghost : animation de rappel vers l'avatar chez l'autre.
- [ ] Entrer dans le Hub avec un Ghost déjà sorti : il apparaît aussitôt avec l'avatar chez l'autre ; rejoindre le
      Hub alors que l'autre a déjà un Ghost sorti : son Ghost est là aussi.
- [ ] Quitter le Hub, se déconnecter, lancer un combat Ghost ou échanger le Ghost : il disparaît chez l'autre.
- [ ] Deux joueurs du même serveur dans le même Anchor : un seul Ghost visible par joueur (le vrai), pas de doublon.

### Global Hub — échange et combat entre serveurs (Phantasmon Network, jalon 2)

Deux mondes, deux empreintes différentes, chacun dans son Anchor et dans le Hub.

- [ ] Viser l'avatar de l'autre et appuyer sur **R** : roue avec seulement « Échange Ghost » et « Combat Ghost », titre =
      pseudo du joueur ; viser un avatar derrière un mur n'ouvre rien.
- [ ] Échange Ghost : l'autre reçoit l'invitation dans le chat, [Accepter] ouvre l'écran d'échange chez les deux,
      l'échange se termine normalement.
- [ ] Combat Ghost : lobby avec le modèle 3D de l'avatar, intro avec son skin, Pokémon placés face à l'avatar, caméra
      cadrant les deux dresseurs ; combat jusqu'au bout, résultat enregistré.
- [ ] Les Ghost des deux joueurs sont rappelés au début du combat, chez les deux.

### Roue d'interaction

- [ ] **R** sur un joueur : entrées Cobblemon intactes + « Échange Ghost » et « Combat Ghost ».

## 4. Signaler un problème

Joindre : la commande ou l'action, le message affiché, les lignes pertinentes de `logs/latest.log`
(recherche : `Phantasmon`, `Ghost`, `Cannot`, `WARN`) et de la console du backend (requêtes `->` avec leur statut,
`WebSocket connected`, `joined group`, `sent out Ghost`).
