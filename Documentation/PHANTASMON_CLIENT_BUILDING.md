# Phantasmon Client — Compiler et installer le mod

Ce document explique comment compiler le mod Phantasmon en `.jar` et l'installer dans une instance
Minecraft, indépendamment du backend (voir `Documentation/PHANTASMON_BACKEND_RUNNING.md` dans le repo
`Phantasmon-Backend` pour le lancer).

---

## 1. Prérequis

- **JDK 25** — obligatoire pour exécuter Gradle/Fabric Loom sur ce projet, **même si** le mod compile
  lui-même en bytecode Java 21 (`sourceCompatibility`/`targetCompatibility` dans `build.gradle`). C'est
  une exigence de l'outillage de build (Loom 1.18), pas du mod lui-même. Sans JDK 25, `./gradlew build`
  échoue à l'étape de configuration avec une erreur du type :
  ```
  Dependency requires at least JVM runtime version 25. This build uses a Java 21 JVM.
  ```
- Pour **jouer** avec le mod une fois compilé : Minecraft **1.21.1**, Fabric Loader **≥ 0.18.1** (plancher
  volontairement bas pour rester compatible avec les modpacks encore sur cette version — voir
  `fabric.mod.json`), Fabric API **0.116.17+1.21.1** (ou une version compatible plus récente, qui
  n'exige elle-même que Fabric Loader ≥ 0.15.11) — ceux-ci tournent normalement sous un Java 21
  classique (le runtime de jeu, différent du JDK utilisé pour builder).
- **Depuis la Phase 7** : **Cobblemon 1.8.1** (build Fabric pour 1.21.1) doit aussi être présent dans
  `mods/` — Phantasmon en dépend pour le rendu des Ghost Pokémon (réutilise les modèles/animations/
  renderer de Cobblemon). Pas besoin d'ajouter `fabric-language-kotlin` séparément : le jar Cobblemon
  l'embarque déjà (jar-in-jar, `META-INF/jars/`), Fabric Loader l'extrait automatiquement.

### 1.1 Installer un JDK 25 (si absent)

```powershell
winget install -e --id Microsoft.OpenJDK.25
```

Si ce JDK n'est pas votre JDK système par défaut, il n'est pas nécessaire de changer `JAVA_HOME`
globalement : indiquez-le juste pour la commande de build (voir §2).

---

## 2. Compiler le mod

Depuis la racine du repo `Phantasmon-Client` :

```bash
./gradlew build
```

Si votre JDK par défaut n'est pas la version 25, précisez-le pour cette seule commande plutôt que de
changer votre configuration système (exemple avec un JDK Microsoft installé sous Windows, à adapter au
chemin réel) :

```bash
JAVA_HOME="C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot" \
PATH="/c/Program Files/Microsoft/jdk-25.0.4.101-hotspot/bin:$PATH" \
./gradlew build
```

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat build
```

### Résultat

Le jar du mod est produit dans `build/libs/` :

```
build/libs/phantasmon-client-<version>.jar
```

⚠️ Ignorez `phantasmon-client-<version>-sources.jar` (jar des sources, pas le mod lui-même) — c'est
`phantasmon-client-<version>.jar` (sans suffixe) qu'il faut installer.

---

## 3. Installer le mod dans Minecraft

1. Installer **Fabric Loader** pour Minecraft 1.21.1 (via le launcher officiel Fabric, ou un launcher de
   modpack compatible comme Modrinth App/CurseForge).
2. Télécharger et placer **Fabric API** (version `0.116.17+1.21.1` ou compatible) dans le dossier
   `mods/` de l'instance — Phantasmon en dépend.
3. Copier `phantasmon-client-<version>.jar` dans ce même dossier `mods/`.
4. Lancer Minecraft avec le profil Fabric 1.21.1.

Phantasmon est un mod **client-only** : il ne nécessite rien côté serveur Minecraft, et peut être utilisé
en solo comme sur n'importe quel serveur vanilla/Fabric classique.

---

## 4. Tester les fonctionnalités actuelles

### 4.1 Heartbeat backend (Phase 0 groundwork)

**Désactivé par défaut** (Adrien : 2026-09-26, pour ne pas spammer le chat par défaut). Taper
`/phantasmon toggle-ping` pour l'activer/désactiver pour la session de jeu en cours (pas persisté). Une
fois activé, le mod envoie un appel `GET /health` au backend **toutes les 30 secondes** et affiche le
résultat dans le chat du joueur (préfixe `[Phantasmon]`). Le ping s'arrête aussi automatiquement à la
déconnexion.

Pour le voir fonctionner :

1. Démarrer le backend en local sur le port 8080 (voir `PHANTASMON_BACKEND_RUNNING.md` dans le repo
   backend).
2. Lancer Minecraft avec le mod installé et rejoindre un monde (solo ou serveur).
3. Taper `/phantasmon toggle-ping` → message de confirmation d'activation.
4. Observer le chat : un message `[Phantasmon] Backend 200 {"status":"UP",...}` doit apparaître toutes
   les 30 secondes. Si le backend n'est pas joignable, le message affiche `Backend injoignable (...)` à
   la place — c'est le comportement attendu, pas un bug.

### 4.2 Connexion (Phase 5) — automatique depuis le polissage du 2026-09-26

**Depuis le polissage post-Phase 8** : plus besoin de taper `/phantasmon login` manuellement dans le cas
courant. À chaque entrée dans un monde/serveur, le mod fait un **unique** `GET /health` ; si la réponse
est `{"status":"UP","database":"UP"}`, il lance automatiquement le même flux que `/phantasmon login`
(silencieusement si le backend est down/injoignable — pas de message parasite si le joueur n'a juste pas
de backend à portée, ex. singleplayer classique). La commande manuelle reste disponible (utile si le
backend était down à l'entrée dans le monde, pour relancer sans quitter/rejoindre).

Taper `/phantasmon login` dans le chat une fois connecté à un monde (ou laisser l'auto-login agir). Le mod, dans l'ordre :

1. Appelle `GET /version` (avant toute authentification, CAD Partie 3 §E).
2. Si la version du mod est sous `min_supported_version` : affiche un message d'incompatibilité et
   s'arrête là (pas de tentative d'auth).
3. Si compatible mais sous `current_version` : affiche un avertissement non bloquant et continue.
4. Effectue le flux Mojang `joinServer` (nécessite un **vrai compte Microsoft/Mojang** — les comptes
   hors-ligne/crackés sont explicitement non supportés et rejetés avant même l'appel réseau) puis
   `POST /auth/session`.
5. En cas de succès, le JWT (access + refresh) est conservé **en mémoire uniquement** le temps de la
   session de jeu (jamais écrit sur disque) et un message de confirmation s'affiche.

Tous les messages (succès, erreurs, avertissements) passent par `lang/fr_fr.json`/`lang/en_us.json` — le
jeu doit être en français ou en anglais pour voir la traduction correspondante, aucun texte brut n'est
affiché.

**Notes** :
- L'URL du backend est actuellement codée en dur sur `http://localhost:8080`
  (`BackendConfig.BASE_URL`) — le client et le backend doivent tourner sur la même machine pour ce test.
  Ce sera rendu configurable avant toute utilisation réelle multi-machines.
- Pour tester le cas "version incompatible", changer temporairement
  `phantasmon.version.min-supported` côté backend (`application.properties`) à une valeur supérieure à
  `1.0.0` (version actuelle du mod, `gradle.properties`), relancer le backend, puis réessayer
  `/phantasmon login`. Le message affiche un lien cliquable (actuellement un placeholder
  `https://modrinth.com/mod/phantasmon`, à remplacer une fois le mod réellement publié).
- Pour tester le renouvellement automatique du token (`SessionRefreshScheduler`, vérifie toutes les 60s
  si un renouvellement est nécessaire), baisser temporairement `phantasmon.jwt.access-ttl` côté backend
  à une valeur courte mais **pas trop courte** (ex. `PT3M` — avec un TTL de 1 min pile, la marge de
  sécurité de 30s combinée à l'intervalle de vérification de 60s peut faire manquer la fenêtre). Rester
  connecté plus longtemps que le TTL choisi et vérifier dans les logs backend qu'un `POST /auth/refresh`
  apparaît tout seul, sans relancer `/phantasmon login`. Remettre le TTL par défaut ensuite.

### 4.3 Pokémon — création (import Showdown), PC, édition (Phase 6)

**Approche "tout en commandes" (Adrien, 2026-09-26)** : pas d'écran graphique pour l'instant (PC/éditeur
visuel), tout passe par `/phantasmon pokemon *`. Nécessite d'être connecté (`/phantasmon login`).

- `/phantasmon pokemon import` — lit le **presse-papiers** (pas un argument de commande, le chat Minecraft
  ne supporte pas le texte multi-lignes) et y cherche un ou plusieurs sets au format Pokémon Showdown
  (blocs séparés par une ligne vide). Copier un export Showdown (depuis le site Showdown, un calculateur
  de dégâts, etc.) presse-papiers, puis taper la commande en jeu.
- `/phantasmon pokemon list` — liste tous les Pokémon du joueur (espèce, niveau, **UUID complet affiché en
  clair** — les commandes ci-dessous exigent l'UUID entier, une version tronquée est rejetée par
  Minecraft avec `UUID non valide`. Cliquer sur l'UUID le pré-remplit dans la barre de chat comme
  raccourci copier-coller, en plus du texte visible, pas à la place).
- `/phantasmon pokemon pc <1-16>` — contenu d'une boîte du PC.
- `/phantasmon pokemon delete <uuid>` — `<uuid>` = UUID complet (ex. `a1b2c3d4-...-...-...-............`), jamais une forme raccourcie.
- `/phantasmon pokemon clone <uuid>`
- `/phantasmon pokemon edit <uuid> level <1-100>`
- `/phantasmon pokemon team` — affiche l'équipe active (1 à 6 Pokémon, triés par emplacement).
- `/phantasmon pokemon team set <uuid> <1-6>` — place un Pokémon à cet emplacement d'équipe.
- `/phantasmon pokemon team clear <uuid> <box 1-16> <slot 1-30>` — retire un Pokémon de l'équipe vers le PC.
- `/phantasmon pokemon pc move <uuid> <box 1-16> <slot 1-30>` — déplace un Pokémon (du PC ou de l'équipe)
  vers cet emplacement PC précis — même mécanisme que `team clear`, en plus général (utile aussi pour
  réorganiser le PC lui-même).

**PC et équipe sont mutuellement exclusifs** (corrigé 2026-09-27, vrai bug de duplication avant ce
correctif) : n'importe lequel des trois déplacements ci-dessus efface automatiquement l'ancien type
d'emplacement du Pokémon. **Sémantique glisser-déposer uniforme**, quelle que soit la combinaison
PC/équipe des deux côtés : si l'emplacement visé est **vide**, simple déplacement ; s'il est **occupé**
par un autre Pokémon du joueur, les deux **échangent** leurs emplacements (PC↔PC, PC↔équipe,
équipe↔équipe — tous les cas, pas seulement équipe↔équipe). Le joueur nomme toujours la destination
exacte, jamais de premier-emplacement-libre automatique ici (à la différence de la création).

**Table de correspondance Showdown → Cobblemon** (`CobblemonIdentifiers`) : plutôt qu'une table figée de
1000+ entrées, une normalisation algorithmique vérifiée contre les vrais fichiers du repo de référence
`cobblemon` (2026-09-26) — espèces/formes/attaques : minuscules, tout séparateur supprimé (`"Body Slam"` →
`bodyslam`, `"Samurott-Hisui"` → espèce `samurott` + forme `hisui`) ; capacités/objets tenus : minuscules
avec underscore (`"Assault Vest"` → `assault_vest`). Une petite liste d'exceptions couvre les espèces dont
le nom contient un tiret sans que ce soit un séparateur de forme (Ho-Oh, Porygon-Z, Nidoran-M/F, etc.).
Couvert par `CobblemonIdentifiersTest`/`ShowdownParserTest`/`ShowdownImportMapperTest`.

**Volontairement absent de cette V1** (à ajouter plus tard si besoin) : édition des autres champs qu'un
Pokémon (nickname/IVs/EVs/moves/objet tenu — le backend ne permet de toute façon patcher que
`level`/`teamSlot`/`data` en bloc pour l'instant), export Showdown. Un écran PC graphique existe désormais
en parallèle des commandes ci-dessus — voir §4.6.

**Pour tester** : backend lancé, `/phantasmon login`, copier le bloc d'exemple du CAD (Partie 1 §8,
`Bichou (Samurott-Hisui) @ Assault Vest / Ability: Torrent / ...`) dans le presse-papiers, puis
`/phantasmon pokemon import`. Vérifier dans les logs backend un `POST /pokemon -> 201`, puis
`/phantasmon pokemon list` pour voir le Pokémon créé.

### 4.4 Ghost Entity — rendu, envoi/rappel (Phase 7)

**⚠️ Phase la plus technique à ce jour, jamais testée visuellement de mon côté** (pas d'affichage dans
cet environnement de dev) — la logique a été construite en vérifiant chaque signature Cobblemon/Mojmap
réelle par décompilation avant écriture (voir mémoire projet), et tout compile, mais le rendu en jeu
n'a été validé par personne. À tester en priorité, avec deux clients (deux comptes/deux instances) sur
le **même serveur/monde**.

**Correctif du 2026-09-26** : `/phantasmon sendout`/`recall` ne donnaient **aucun retour dans le chat**,
et le backend diffusait le spawn/despawn à tous les membres du groupe **sauf l'expéditeur lui-même** — un
test en solo (un seul compte connecté) ne pouvait donc rien montrer, ce qui ressemblait exactement à
"la commande ne fait rien". Les deux sont corrigés : le joueur voit maintenant son propre Ghost lui
aussi (spawn/move/despawn), et chaque commande donne un retour dans le chat (`Envoi en cours...` puis
`<espèce> est sorti.` une fois confirmé par le serveur, ou un message d'erreur explicite si la connexion
WebSocket n'est pas encore prête). **Ce test redevient possible avec un seul client** — deux clients
restent utiles pour vérifier la visibilité entre joueurs, mais ne sont plus indispensables pour un
premier test.

**Prérequis** : Cobblemon 1.8.1 installé (voir §1), backend lancé, le joueur connecté
(`/phantasmon login`) et avec au moins un Pokémon **placé dans l'équipe active**
(`/phantasmon pokemon team set <uuid> <1-6>`, voir §4.3 — depuis le polissage du 2026-09-26, seul un
Pokémon de l'équipe peut être sorti ; en tenter un du PC échoue avec `ERROR_POKEMON_NOT_IN_TEAM`).

1. `/phantasmon sendout <uuid>` avec l'**UUID complet** d'un des Pokémon de l'équipe (récupéré via
   `/phantasmon pokemon team`, voir §4.3 — une version tronquée donne `UUID non valide`). Le chat doit
   afficher `Envoi en cours...` puis `<espèce> est sorti.` — si rien n'apparaît après le deuxième
   message, c'est un vrai bug cette fois (le Ghost devrait être visible juste à côté du joueur).
1bis. Tester le cas rejeté : tenter `/phantasmon sendout` avec l'UUID d'un Pokémon **resté dans le PC**
   (`/phantasmon pokemon list` en montre, `/phantasmon pokemon team` non) → doit afficher le message
   "seuls les Pokémon de l'équipe active peuvent être sortis", aucun Ghost ne doit apparaître.
2. (Avec un second joueur B, dans la même dimension, à portée de rendu) : B doit voir apparaître le
   Pokémon de A, suivant sa position au fil de ses déplacements (mise à jour ~1×/seconde, pas
   d'interpolation lissée en V1 — un léger effet "saccadé" entre deux mises à jour est attendu, pas un
   bug).
3. `/phantasmon recall` → le Pokémon doit disparaître immédiatement (chat : `Ghost rappelé.`), pour soi
   et pour B le cas échéant.
4. Renvoyer, puis faire déconnecter A brutalement (fermer le jeu sans `/phantasmon recall`) → le Pokémon
   doit quand même disparaître chez B (despawn automatique côté backend à la fermeture de connexion).
5. Renvoyer, puis faire changer A de dimension (End/Nether/Overworld) → despawn automatique attendu.
6. Renvoyer, puis tuer le joueur A (`/kill` ou dégâts réels) → despawn automatique attendu à la mort.
7. Joueur B qui rejoint le monde/serveur **après** que A a déjà sorti son Ghost → doit voir le Ghost de A
   apparaître dès sa connexion (rattrapage `GhostEntitySpawn`), sans que A n'ait besoin de bouger.

**Points de vigilance particuliers, à signaler si observés** (limites connues, pas forcément des bugs,
mais pas garanties sans test réel) :
- Le Ghost pourrait tenter de se déplacer tout seul (IA de vagabondage) si `setNoAi(true)` ne suffit pas
  à bloquer tout le comportement de `PokemonEntity` — en théorie il ne devrait **jamais** bouger de
  lui-même, seulement suivre les mises à jour de position reçues.
- Species/forme non reconnue (`species null`/mismatch de `cobblemonDataVersion`) → le Ghost n'apparaîtra
  pas du tout côté receveur, avec un warning dans les logs client (`Cannot render Ghost: unresolved
  species ...`) plutôt qu'un crash — comportement voulu (cf. hard rule sur `cobblemon_data_version`),
  mais à vérifier que ça ne crashe pas.
- Aucune limite de portée de rendu vérifiée manuellement — un Ghost très loin du joueur B pourrait
  rester chargé indéfiniment (pas de despawn par distance en V1).

**Amélioration prévue, pas encore faite** : le Ghost se téléporte exactement sur les coordonnées du
propriétaire à chaque mise à jour (~1×/s), donc il apparaît superposé au joueur plutôt que de se
déplacer naturellement à côté. Une vraie IA de vagabondage autour du propriétaire est prévue plus tard
(demande d'Adrien, 2026-09-26) — pas dans le périmètre de cette passe.

### 4.5 Trade — proposer/accepter/annuler (Phase 8)

Nécessite deux joueurs connectés (`/phantasmon login`), chacun avec au moins un Pokémon
(`/phantasmon pokemon list` pour récupérer les UUID — joueur et Pokémon).

1. Joueur A : `/phantasmon trade propose <uuid-joueur-B> <uuid-pokemon-A> <uuid-pokemon-B>` — **limite
   connue** : aucune commande ne liste encore les joueurs connectés avec leur UUID (pas dans le périmètre
   de cette passe) ; pour tester, récupérer l'UUID Mojang du joueur B autrement (ex. il apparaît dans les
   logs backend au moment de sa connexion — `AuthController`/`WebSocket connected: player ...` — ou via
   NameMC/le pseudo).
2. Joueur B doit recevoir en temps réel (WebSocket, pas besoin de rafraîchir) : `Nouvel échange proposé
   (<uuid>) — /phantasmon trade accept ou cancel.`
3. Joueur B : `/phantasmon trade accept <uuid-échange>` → message `Échange accepté — Pokémon échangés.`
   chez B, et `Échange <uuid> accepté.` chez A (notification temps réel).
4. Vérifier avec `/phantasmon pokemon list` des deux côtés que les deux Pokémon ont bien changé de
   propriétaire.
5. Refaire un `propose` puis `/phantasmon trade cancel <uuid-échange>` (par A ou B) → les deux doivent
   recevoir `Échange <uuid> annulé.`, aucun Pokémon ne doit avoir changé de main.
6. `/phantasmon trade list` — doit lister les échanges initiés ou reçus, avec leur statut.

### 4.6 HUD graphique — écran PC (2026-09-27, première passe)

**Premier écran graphique du mod** (pas une commande) : ouvert via `/phantasmon pc`, modelé sur
`Documentation/prototype_pc.html`. Périmètre volontaire de cette passe (choix Adrien) : grille PC 6×5 +
équipe + panneau de détail + glisser-déposer (déplacer/échanger) + suppression. **La modale d'édition
complète (formulaire IVs/EVs/capacités, ré-import Showdown) est explicitement reportée à une passe
suivante** — le bouton "Éditer" affiche pour l'instant un message indiquant que ce n'est pas encore
disponible.

**Fidélité visuelle** : approximation assumée, pas une réplique pixel-perfect du prototype HTML/CSS.
`GuiGraphics` (rendu Minecraft vanilla) n'a pas d'équivalent natif au flou/glow/`clip-path`/fond animé
"lava lamp" du prototype (esthétique "wisp" glassmorphism) — remplacé ici par des panneaux plats avec
bordure cyan. Accepté explicitement par Adrien avant l'implémentation (question posée en amont).

**Icônes Pokémon réelles** : pas des sprites plats, mais le vrai mini-modèle 3D animé de Cobblemon
(`PokemonGuiUtilsKt.drawProfilePokemon`), la même API utilisée par l'écran PC natif de Cobblemon
lui-même. Point technique notable : cette fonction Kotlin a 9 paramètres avec valeur par défaut ; le pont
généré `drawProfilePokemon$default` qui permet de les omettre est marqué `@JvmSynthetic`, ce que le
compilateur Java masque explicitement même si la méthode est publique en bytecode — contournement via
réflexion (`PhantasmonPcScreen.drawProfilePokemonDefault()`), sans changer le comportement réel.

**Pour tester** :
1. `/phantasmon login`, puis `/phantasmon pc`.
2. Vérifier que le PC s'ouvre sans mettre le jeu en pause (solo) et sans planter.
3. Vérifier que la grille affiche les Pokémon de la boîte 1 avec un modèle 3D visible dans chaque case
   occupée, et que l'équipe (colonne de droite) affiche les Pokémon en équipe (`/phantasmon pokemon team`
   pour comparer).
4. Cliquer un Pokémon → le panneau de gauche doit afficher espèce/niveau/nature/talent/objet/type
   tera/IVs/EVs/capacités.
5. Glisser-déposer un Pokémon du PC vers un emplacement d'équipe vide → doit se déplacer (vérifier avec
   `/phantasmon pokemon team`) ; vers un emplacement d'équipe occupé → doit **échanger** les deux (pas de
   duplication, comparer avec `/phantasmon pokemon list`) ; PC→PC et équipe→équipe idem.
6. Flèches `<`/`>` en haut de la grille → changent de boîte (1 à 16), la sélection doit se réinitialiser si
   le Pokémon sélectionné n'est plus dans la boîte affichée.
7. Bouton "Supprimer" sur un Pokémon sélectionné → doit disparaître de la grille/équipe après confirmation
   côté backend (comparer avec `/phantasmon pokemon list`).
8. Bouton "Éditer" → doit juste afficher un message "arrivera dans une passe suivante", sans planter.

**Non testé par Claude** (pas d'accès visuel/in-game) : rendu effectif des modèles 3D dans les slots
(taille/position peuvent nécessiter un ajustement après un premier retour visuel d'Adrien), lisibilité du
texte dans le panneau de détail selon la résolution d'écran, comportement du glisser-déposer au clic très
rapide. À valider en jeu avant de considérer cette passe définitivement close.

**Bug réel trouvé et corrigé lors du premier test d'Adrien (2026-09-27)** : `/phantasmon pc` n'affichait
rigoureusement rien (jeu inchangé), alors que le log confirmait bien l'exécution de la commande et un appel
réseau réussi (`GET /players/.../pokemon -> 200`), et que FancyMenu loggait même l'enregistrement de
l'écran. Cause réelle, confirmée en décompilant `ChatScreen.keyPressed` : après avoir exécuté la commande
tapée dans le chat, Minecraft appelle **inconditionnellement** `minecraft.setScreen(null)` juste après, sans
vérifier si l'écran courant a changé entre-temps — comme le premier code ouvrait l'écran PC de façon
synchrone pendant l'exécution de la commande, il était rouvert puis immédiatement écrasé par cette fermeture
du chat, dans le même appel. Corrigé en différant l'ouverture d'un tick client (`PokemonCommandHandler.tick()`,
appelé depuis `PhantasmonClient`'s `ClientTickEvents.END_CLIENT_TICK`, déjà utilisé pour `GhostSession`) plutôt
que d'appeler `setScreen` directement dans le handler de commande — piège classique Fabric pour toute commande
qui ouvre un écran graphique, à garder en tête pour toute future commande de ce type.

**Premier retour visuel d'Adrien (2026-09-27, écran fonctionnel mais "très moche et brut")** : capture d'écran
en jeu comparée au template a révélé de vrais défauts corrigés dans la foulée, pas juste la limite CSS déjà
actée :
- **Icône 3D qui débordait de la case** — cause réelle trouvée en comparant avec le bytecode décompilé de
  `StorageSlot.renderSlot` de Cobblemon : celui-ci utilise une échelle `4.5f` pour un slot d'environ 28-30px,
  contre `10-24f` dans la première version ici (2 à 5× trop grand). Corrigé (`ICON_SCALE_SMALL = 4.5f`) et le
  point d'ancrage déplacé du centre de la case vers le haut (comme le fait Cobblemon lui-même), le modèle
  s'étendant naturellement vers le bas depuis ce point plutôt que de déborder par le haut.
- **Panneau grille mal aligné** — centré verticalement au lieu d'être aligné en haut comme les deux autres
  panneaux ; corrigé pour un alignement cohérent.
- **Boutons Éditer/Supprimer en largeur fixe (60px)** — ne s'adaptait pas à l'échelle GUI/résolution ; devenus
  proportionnels à la largeur du panneau de détail.
- **Aucune couleur de type** — ajout de vrais badges de type (fond + texte) via l'API Cobblemon
  `Species.getPrimaryType()`/`getSecondaryType()`/`ElementalType.getPrimaryColor()`/`getDisplayName()` (couleur
  et nom traduit réels, pas une table couleur maison), avec un choix automatique texte noir/blanc selon la
  luminosité du fond pour rester lisible. En-têtes de section (IVs/EVs/Capacités) recolorés en cyan clair au
  lieu du blanc plat.
- **Non revérifié visuellement par Claude** (toujours pas d'accès au jeu) — à reconfirmer par Adrien.

**Enrichissement du panneau de détail (2026-09-27)**, à la demande d'Adrien : type téra, Hidden Power, nature
avec bonus/malus, et types des capacités colorés.
- **Aucune API externe nécessaire** — Cobblemon expose déjà tout ça localement : `Moves.getByName(id)` →
  `MoveTemplate.getElementalType()` pour le type d'une capacité (Cobblemon doit forcément connaître cette
  donnée pour ses propres combats), et `ElementalTypes.get(id)` pour retrouver un type par son nom (utilisé
  pour le type téra et le Hidden Power). Les badges de type des capacités réutilisent le même rendu que les
  badges de type d'espèce (couleur + nom traduit réels).
- **Hidden Power n'est stocké nulle part** (ni ici, ni dans Cobblemon, ni dans Pokémon Showdown lui-même) : il
  se **calcule** à partir des 6 IVs via la formule standard Gen 2+ (`HiddenPowerCalculator`, nouvelle classe
  pure testée : `IVs 31 partout → Ténèbres`, `IVs 0 partout → Combat`, deux faits de référence bien connus qui
  confirment la formule/l'ordre des types).
- **Nature avec bonus/malus** (`NatureModifiers`, nouvelle classe pure testée) : table standard des 25
  natures, les 5 neutres (Hardy/Docile/Serious/Bashful/Quirky) n'affichent aucun bonus/malus. Affiché comme
  `Adamant (+Atk / -SpA)`.
- 5 nouveaux tests client (`HiddenPowerCalculatorTest`, `NatureModifiersTest`).
- **Pour tester** : le Hidden Power s'affiche toujours (calculé depuis les IVs, jamais absent). Le type téra
  s'affiche aussi toujours — si `data.teraType` n'est pas défini, la ligne retombe sur le type primaire de
  l'espèce (comme dans les vrais jeux, chaque Pokémon a un type téra par défaut avant d'utiliser un Téra-Éclat).
  Chaque capacité affiche son nom à gauche et un badge de couleur correspondant à son type **aligné à droite**
  du panneau. La nature doit toujours afficher son bonus/malus sauf pour les 5 natures neutres.

### 4.7 HUD graphique — éditeur complet + import (2026-09-27, deuxième passe)

**Éditeur** : cliquer "Éditer" sur un Pokémon sélectionné dans le PC ouvre désormais un vrai formulaire
(`PhantasmonPcEditScreen`, premier écran du mod à utiliser de vrais widgets Minecraft — `EditBox`/`Button` —
plutôt que le clic manuel du PC principal, plus adapté ici vu le nombre de petits champs indépendants) :
surnom, niveau, talent, objet tenu, nature (flèches `<`/`>` pour cycler parmi les 25 natures), chromatique
(bouton bascule), type téra (flèches `<`/`>` parmi les 18 types + "Aucun"), IVs et EVs (6 champs numériques
chacun), 4 capacités (id Cobblemon brut, ex. `shadowball`). Bouton "Importer Showdown (presse-papiers)" à
l'intérieur du formulaire : préremplit tous ces champs depuis un export Showdown copié, sans sauvegarder —
l'espèce/forme du Pokémon n'est jamais modifiée par ce biais (c'est une édition, pas un remplacement).
Enregistrer envoie un seul `PATCH /pokemon/{uuid}` avec `data` (ivs/evs/moves/objet/type téra/surnom
reconstruits, le reste de `data` préservé), `level`, `nature`, `ability`, `is_shiny`.

**Nécessitait une extension backend** : `PATCH /pokemon/{uuid}` ne permettait de modifier que `data`/`level`/
l'emplacement — `nature`/`ability`/`is_shiny` ont été ajoutés à `PokemonUpdateRequest`/`PokemonService.update()`
(94 tests backend désormais, `PokemonUpdateRequest`/`PHANTASMON_API_REFERENCE.md` mis à jour côté backend).

**Import (créer un nouveau Pokémon depuis le HUD)** : nouveau bouton "Importer (presse-papiers)" dans le
pied de la grille du PC principal — lit le presse-papiers et crée un ou plusieurs Pokémon (export Showdown
multi-mon supporté, blocs séparés par une ligne vide), exactement comme `/phantasmon pokemon import` (même
parseur/mapper, même auto-attribution du premier emplacement PC libre), juste déclenché depuis le HUD.

**Hidden Power reste non éditable** (volontaire) — toujours calculé depuis les IVs, jamais stocké, comme
dans les vrais jeux et sur Pokémon Showdown lui-même.

**Pour tester** :
1. `/phantasmon pc`, sélectionner un Pokémon, cliquer "Éditer" → le formulaire doit s'ouvrir avec les
   valeurs actuelles préremplies.
2. Changer quelques champs (niveau, nature via les flèches, une capacité) et cliquer "Enregistrer" → retour
   au PC, les changements doivent apparaître immédiatement dans le panneau de détail.
3. "Annuler" (ou Échap) → retour au PC sans rien changer.
4. Copier un export Showdown dans le presse-papiers, cliquer "Importer Showdown" dans le formulaire → tous
   les champs doivent se remplir depuis l'export (espèce du Pokémon inchangée), puis "Enregistrer" pour
   confirmer.
5. Depuis l'écran PC principal (pas dans l'éditeur), copier un ou plusieurs exports Showdown, cliquer
   "Importer (presse-papiers)" en bas de la grille → un ou plusieurs nouveaux Pokémon doivent apparaître
   dans le premier emplacement PC libre.
6. Vérifier qu'un talent vide au moment d'enregistrer affiche bien un message d'erreur sans planter.

**Non testé par Claude** (pas d'accès visuel) : disposition exacte du formulaire (chevauchements possibles
selon la résolution), lisibilité des flèches de cycle nature/téra, ergonomie générale du formulaire à
valider par Adrien avant de considérer cette passe close.

**Corrections d'ergonomie suite au premier test (2026-09-27)** : le formulaire débordait sous l'écran
(bouton Enregistrer inaccessible), et les flèches `<`/`>` pour nature/type téra n'étaient pas ergonomiques.
- **Layout repensé en deux colonnes** (identité/comportement à gauche, IVs/EVs/capacités/import à droite)
  au lieu d'une seule colonne empilée — divise à peu près par deux la hauteur totale du formulaire, qui
  devrait désormais tenir dans la quasi-totalité des résolutions/échelles GUI. Le panneau est aussi
  maintenant centré verticalement selon la hauteur réelle de la fenêtre plutôt que positionné en absolu.
- **Nature et type téra sont maintenant de vrais menus déroulants** : cliquer sur le champ affiche une
  liste flottante scrollable (molette) par-dessus tout le reste, cliquer une option la sélectionne et
  referme la liste, cliquer ailleurs referme sans rien changer. Premier widget "menu déroulant" du mod —
  Minecraft n'a pas de widget combo-box natif, implémenté ici comme une petite liste custom dessinée en
  dernier (donc par-dessus) plutôt qu'un vrai widget dans la liste de rendu de l'écran.
- **Non retesté visuellement** — à confirmer par Adrien avant de passer réellement aux textures.

### 4.8 Vraies textures pour le menu PC (2026-09-27, premier passage)

**Approche retenue** (après discussion) : `prototype_pc.html` ne contient aucune image exportable — tout son
rendu (dégradés, lueurs, flou) est du CSS procédural, pas des PNG statiques. Solution : extraire les
**valeurs de couleur exactes** du CSS (`.wisp-chassis`, `.grid-slot`, `.btn-spectral`/`-red`) et générer de
vraies textures PNG avec ces couleurs précises (script Python/Pillow, pas d'IA générative d'image), plutôt
que d'improviser une palette.

**Système "nine-slice" natif de Minecraft** (découvert en creusant l'API, pas une réinvention) :
`GuiGraphics.blitSprite(ResourceLocation, x, y, w, h)` étire une texture en respectant une bordure fixe
définie dans un fichier `.png.mcmeta` à côté (`{"gui":{"scaling":{"type":"nine_slice","width":...,"border":...}}}`)
— la bordure cyan reste nette à n'importe quelle taille de panneau au lieu de s'étirer avec le centre. Textures
posées sous `assets/phantasmon/textures/gui/sprites/pc/` : `panel.png` (dégradé diagonal + bordure cyan,
remplace tous les `fill()+renderOutline()` des 3 panneaux), `slot`/`slot_hover`/`slot_selected`/`slot_drag.png`
(les 4 états de case, couleurs exactes de `.grid-slot`), `button`/`button_hover.png` (bouton cyan) et
`button_red`/`button_red_hover.png` (bouton rouge, utilisé pour "Supprimer" — n'existait pas avant, tous les
boutons étaient cyan).

**Périmètre de cette passe** : uniquement l'écran PC principal (`PhantasmonPcScreen`), à la demande d'Adrien
("on commence par le menu PC"). L'écran d'édition (`PhantasmonPcEditScreen`) garde son style plat pour
l'instant — passe suivante si demandé.

**Non fait dans cette passe** (pistes pour la suite) : la police pixel "Press Start 2P" utilisée par le
template (vraie police Google Fonts, embarquable comme police custom Minecraft via un `font provider` dans
le resource pack) — nécessiterait de récupérer le fichier de police ; le halo/glow qui déborde visuellement
hors du panneau (CSS `box-shadow` externe, non reproductible dans une texture nine-slice qui ne peut dessiner
que dans ses propres limites) ; le fond animé "lava lamp" teinté par type.

**Non testé par Claude** (pas d'accès visuel) — à valider par Adrien : le rendu réel des dégradés/bordures en
jeu, la lisibilité du texte par-dessus les nouvelles textures, et si `blitSprite` applique bien le nine-slice
comme attendu (comportement vérifié uniquement par lecture de l'API, jamais vu s'exécuter).

### 4.9 Refonte du layout — panneau unique + boîte carrée + sous-boîtes (2026-09-27)

**Retour de test d'Adrien après les textures** : le menu devait tenir dans **un seul panneau englobant**
(au lieu de 3 panneaux séparés) contenant 3 zones (détail à gauche | boîte PC au milieu | équipe à droite) ;
la boîte PC doit garder des **slots toujours carrés** (le ratio grille ne varie jamais) et être centrée ;
en largeur, la grille prend au moins 50% du panneau, le détail 35%, l'équipe le reste ; les slots d'équipe
touchaient les bords de leur boîte (pas de padding) et n'avaient pas la même taille que ceux du PC ; la boîte
équipe devait s'adapter à son contenu au lieu de laisser du vide ; le panneau de détail devait séparer le
rendu du modèle 3D (« écran »), les stats (niveau/nature/talent/objet/téra/P.cachée/IVs/EVs), et les
capacités en 3 sous-boîtes distinctes, sans qu'aucune stat ne déborde/soit tronquée/scrolle, à n'importe
quelle résolution.

**Layout entièrement recalculé** dans `layoutPanels()` :
- Un seul `fillPanel` pour le chassis englobant (`rootX/Y/W/H`) ; les 3 zones sont des secteurs à l'intérieur
  (35% / 50% / reste, calculés sur la largeur de contenu disponible après marges).
- **Taille de slot dynamique** (`slotSize`, remplace l'ancienne constante fixe `SLOT_SIZE`) : calculée comme
  le minimum entre ce qui rentre en largeur dans le secteur du milieu et ce qui rentre en hauteur dans tout
  le contenu — garantit des slots carrés quelle que soit la résolution, jamais étirés. La boîte PC réelle
  (grille + en-tête + pied) est ensuite centrée dans son secteur horizontalement et dans tout le contenu
  verticalement.
- **Boîte équipe recalculée en "shrink-wrap"** : sa hauteur est directement `en-tête + 6×(slotSize+marge)`,
  plus de hauteur fixe qui laissait du vide. Padding de 8px ajouté entre les lignes d'équipe et les bords de
  leur boîte (avant : les lignes touchaient les bords). Les lignes d'équipe utilisent désormais `slotSize`
  (la même valeur que la grille PC) au lieu de leur propre constante `TEAM_ROW_HEIGHT` (supprimée).
- **Panneau de détail découpé en 3 sous-boîtes** (chacune avec son propre `fillPanel`, donc sa propre
  bordure visible) : boîte "écran" (juste le modèle 3D), boîte "stats" (niveau/nature/talent/objet en 2
  mini-colonnes côte à côte, puis téra/P.cachée en 2 mini-colonnes, puis IVs/EVs en pleine largeur — ce
  découpage en mini-colonnes réduit le nombre de lignes nécessaires d'environ moitié, précisément pour
  garantir que ça tienne sans déborder), boîte "capacités" (nom à gauche + badge de type à droite, comme
  avant, juste dans sa propre boîte séparée maintenant). Les hauteurs des 3 sous-boîtes sont calculées comme
  des proportions (34% / 38% / reste) de l'espace vertical réellement disponible dans la colonne de gauche,
  donc s'adaptent à la résolution plutôt que d'être des pixels fixes.

**Non testé par Claude** (pas d'accès visuel) — en particulier si les proportions de sous-boîtes calculées
suffisent réellement à éviter tout débordement de texte à toutes les résolutions/échelles GUI réalistes ; à
confirmer par Adrien, avec des ajustements de proportions probables si un débordement persiste quelque part.

### 4.10 Deuxième round de rectifications détaillées (2026-09-27)

- **Boîte PC** : la marge entre la bordure de la boîte et le premier/dernier slot est désormais au moins le
  double de l'espacement entre deux slots (`GRID_EDGE_PAD = SLOT_GAP × 2`), au lieu d'utiliser le même
  espacement pour les deux (bordure et inter-slots confondus, comme avant).
- **Équipe** : chaque slot d'équipe est maintenant un **carré de taille exactement identique** à un slot de
  la boîte PC (`slotSize`, valeur partagée) — avant, le fond/bordure du slot s'étirait sur toute la largeur
  de la ligne (rectangle large, pas carré). Le nom+niveau du Pokémon s'affiche maintenant à côté de ce carré,
  pas dedans. La zone cliquable reste toute la largeur de la ligne (plus facile à cliquer), seul le carré
  visuel a changé de taille. Le libellé "Équipe X/6" est redevenu simplement "Équipe" (plus de compteur).
- **Panneau de présentation** : le cadre autour du modèle 3D a été retiré (zone "écran" sans bordure). Niveau
  affiché en haut à gauche du modèle, objet tenu en bas à droite (juste son nom, ou "Pas d'objet" — plus de
  préfixe "Objet :"). Nom+forme du Pokémon écrit à gauche de son (ou ses) badge(s) de type sur une même
  ligne. Les sous-boîtes "Stats" et "Capacités" ont été fusionnées en une seule. Les IVs s'affichent comme
  "IVs parfaits (31)" si les 6 stats valent 31 ; les EVs omettent silencieusement toute stat à 0 (et
  affichent "Aucun EV investi" si les 6 sont à 0).
- **Non testé par Claude** — à reconfirmer par Adrien, notamment le rendu exact du carré d'équipe et le
  positionnement niveau/objet sur le modèle 3D.

### 4.11 Troisième round de rectifications détaillées (2026-09-27)

- **Équipe** : le texte "-- vide --" pour un emplacement libre a été retiré (rien ne s'affiche à la place).
- **Boîte PC** : marge des flèches `<`/`>` et du bouton "Importer" doublée (8px au lieu de 4px) ; le titre
  "Boîte X/16" est maintenant vertically aligné avec les flèches à cette même marge, plutôt que collé en haut.
- **Présentation** :
  - Boutons Éditer/Supprimer masqués tant qu'aucun Pokémon n'est sélectionné (avant : toujours affichés,
    inertes).
  - Le nom + type(s) du Pokémon a été déplacé **au-dessus** de l'affichage 3D (avant : dans la boîte fusionnée,
    en dessous).
  - Modèle 3D réduit de 10% (`ICON_SCALE_DETAIL = 14f × 0.9`).
  - Padding de la boîte de stats augmenté (6px → 9px).
  - **Talent/Nature/Type téra/P.Cachée** repassés d'un agencement 2 colonnes à 4 lignes empilées, dans cet
    ordre précis (avant : Nature+Talent puis Téra+P.Cachée, 2 par ligne).
  - Les libellés "IVs"/"EVs" sont maintenant sur la **même ligne** que leurs valeurs (avant : leur propre
    ligne au-dessus).
- **Non testé par Claude** — à reconfirmer par Adrien.

### 4.12 Quatrième round + fond teinté par type + transparence du panneau racine (2026-09-27)

- **Boîte de stats** : marge haut/bas désormais calculée dynamiquement pour être toujours égale, quelle que
  soit l'espèce/le nombre de capacités (`(mergedBoxH - hauteurContenuRéelle) / 2` — la hauteur de contenu
  réelle est recalculée par Pokémon, pas une valeur fixe, donc la symétrie tient même avec 1 seule capacité).
- **Équipe** : titre descendu de 2px ; l'espace entre le carré et le texte du nom est désormais identique à
  l'espace entre le bord gauche de la boîte et le carré (les deux utilisent `teamPad`, avant l'un valait 8px
  et l'autre 6px).
- **Boîte PC** : bouton "Importer" remonté de 2px.
- **Fond teinté par type** (nouveau, demandé par Adrien) : la zone "écran" du modèle 3D est maintenant
  teintée avec la ou les couleurs de type réelles de l'espèce (`Species.getPrimaryType()`/`getSecondaryType()`,
  déjà utilisées pour les badges) — un type unique donne un fond uni, deux types donnent un **dégradé
  diagonal**. Minecraft n'a pas de dégradé diagonal natif (`fillGradient` ne fait que haut→bas) ; contourné en
  tournant le pose stack de 45° autour du centre de la zone (même technique que la rotation déjà utilisée pour
  le rendu du modèle 3D), en dessinant un dégradé vertical surdimensionné dans ce repère tourné, découpé au
  scissor pour rester dans les limites de la zone.
- **Transparence du panneau racine** : nouvelle texture `panel_root.png` (mêmes couleurs/bordure que
  `panel.png` mais opacité de remplissage quasiment divisée par deux) utilisée uniquement pour le grand
  panneau englobant — les sous-boîtes (stats, écran, grille, équipe) gardent la texture plus opaque pour que
  le texte reste lisible.
- **Non testé par Claude** — en particulier la technique de dégradé diagonal (jamais vue s'exécuter, risque
  réel si la rotation du pose stack se comporte différemment de ce qui est attendu) et le niveau de
  transparence du panneau racine (à ajuster si trop/pas assez transparent).

### 4.13 Retrait du dégradé, panneau racine uni, recentrage des modèles 3D (2026-09-27)

- **Fond dégradé par type retiré** — jugé mauvaise idée par Adrien après coup. `renderTypeTintedBackground`/
  `fillDiagonalGradient` supprimés entièrement, la zone "écran" n'a plus aucun fond propre (transparente sur
  le panneau racine en dessous).
- **"Dallage" sur le panneau racine corrigé** — cause probable : `panel_root.png` contenait un dégradé
  diagonal interne (même sur sa zone centrale), et le nine-slice de Minecraft **étire** cette zone centrale
  (vérifié en décompilant `GuiGraphics.blitSprite` — chaque région, y compris le centre, est bien étirée et
  jamais carrelée) sur toute la largeur/hauteur du panneau ; avec un filtrage au plus proche voisin
  (pixelisé), un dégradé interne à une texture source de seulement 32×32 pixels étiré sur une très grande
  surface produit de larges bandes/blocs visibles ressemblant à un dallage. Remplacé par une texture à
  **couleur de remplissage parfaitement unie** (aucun dégradé interne, juste la bordure cyan), qui reste
  uniforme quel que soit l'étirement. Transparence également augmentée un peu plus au passage.
- **Modèles 3D recentrés** — dans les slots de la grille, les slots d'équipe, et l'écran du panneau de
  présentation, l'ancrage du modèle est repassé du "haut de la zone" (technique reprise du code natif de
  Cobblemon, mais qui laissait le modèle visuellement décentré vers le haut une fois l'échelle corrigée) au
  **centre vertical réel** de la zone. Ajustement fait sans retour visuel — à confirmer, un réglage fin
  reste possible si le centrage n'est pas encore parfait.

### 4.14 Modèles 3D agrandis ×3, refonte visuelle de l'éditeur (2026-09-27)

- **Modèles 3D agrandis** : échelle ×3 partout (grille, équipe, écran de présentation), en gardant le
  centrage du point précédent. Le découpage au scissor (déjà en place sur chaque slot/écran) protège contre
  tout débordement visuel hors de sa case.
- **Éditeur (`PhantasmonPcEditScreen`) reskinné pour matcher la DA du menu PC** : le panneau utilise
  désormais la même texture nine-slice (`SPRITE_PANEL`) que le PC, et tous les boutons (nature, type téra,
  chromatique, importer, enregistrer, annuler) utilisent la même texture cyan (`SPRITE_BUTTON`/`_HOVER`) via
  une nouvelle classe `SpectralButton` (un `Button` vanilla dont le rendu est surchargé — Minecraft n'a pas
  de moyen public de changer la texture d'un bouton natif autrement). Les `EditBox` gardent leur apparence
  vanilla (les reskinner demanderait de réimplémenter le rendu du curseur/de la sélection de texte, hors
  périmètre ici). Ces deux constantes de sprite ont été rendues visibles au niveau du package dans
  `PhantasmonPcScreen` pour être réutilisées ici sans dupliquer les fichiers texture.
- **Libellés en ligne avec leur champ** : Surnom/Niveau/Talent/Objet/Nature/Type téra ont maintenant leur
  intitulé sur la **même ligne** que leur champ (avant : au-dessus). La largeur de la colonne de libellés est
  mesurée dynamiquement à partir du plus long des 6 intitulés traduits, pour que ça reste aligné aussi bien
  en français qu'en anglais.
- **Menus déroulants toujours au-dessus** : déjà garanti par l'ordre de dessin (le rendu du menu déroulant a
  toujours lieu en tout dernier, après `super.render(...)` qui dessine tous les widgets) — vérifié et
  conservé tel quel dans la réécriture.
- **Non testé par Claude** — à confirmer par Adrien, en particulier le rendu des boutons reskinnés et
  l'alignement des libellés inline.

### 4.15 Correctifs suite au premier test avec captures (2026-09-27)

- **Modèles 3D décentrés par l'agrandissement ×3** : la cause réelle était d'avoir multiplié directement le
  paramètre `scale` passé à `drawProfilePokemon` — ce paramètre ne se comporte pas de façon purement linéaire
  en interne côté Cobblemon (positionnement non proportionnel à cette valeur), donc l'agrandir décale
  visuellement le modèle. Corrigé en revenant à la valeur de référence de Cobblemon pour `scale`
  (4.5f/12.6f, déjà bien centrée) et en appliquant l'agrandissement ×3 **après coup**, via
  `PoseStack.scale(3,3,1)` autour du point déjà centré — un simple zoom visuel qui ne peut pas introduire de
  décalage, contrairement à un paramètre interne dont on ne maîtrise pas la géométrie exacte.
- **Chevauchement "EVs"/"Capacités" sous les champs au-dessus** (visible sur la capture d'Adrien) : la
  cause était un calcul d'espacement qui ne correspondait pas à la vraie hauteur des champs (16px) — l'ancien
  code avançait d'une quantité fixe (`ROW_H`) au lieu de dériver la position du label suivant de la position
  et hauteur réelles du champ précédent. Corrigé en calculant chaque étape à partir de la précédente
  (label → +10px → champ → +hauteur du champ → +6px → label suivant), ce qui élimine tout chevauchement peu
  importe les valeurs exactes.
- **Bonus/malus de nature affiché** : le bouton de nature et chaque ligne de la liste déroulante affichent
  maintenant "Adamant (+Atk / -SpA)" (même format que le panneau de détail en lecture seule), pas juste le
  nom brut. La liste déroulante de nature est aussi élargie (190px) pour laisser la place à ce texte plus
  long.
- **Liste déroulante qui passait derrière les boutons** : cause probable — `GuiGraphics` regroupe ses
  dessins par type de rendu (remplissages unis vs textures des boutons) et peut soumettre ces lots dans un
  ordre différent de l'ordre d'appel si rien ne force une frontière entre eux ; les boutons (texturés) et le
  fond du menu déroulant (couleur unie) utilisaient deux pipelines différents. Corrigé en appelant
  `graphics.flush()` juste avant de dessiner le menu déroulant, forçant tout ce qui précède à être
  réellement soumis avant.
- **Non testé par Claude** — à reconfirmer par Adrien, en particulier si `flush()` résout bien le problème
  d'ordre d'affichage (technique jamais vue s'exécuter).

### 4.16 Vraie cause du décentrage 3D trouvée, chevauchement IVs corrigé (2026-09-27)

- **Chevauchement "IVs"/"HP"** (capture d'Adrien) : le titre "IVs (0-31)" et le libellé "HP" de la première
  colonne étaient dessinés strictement à la même position (`y` identique, `x` identique pour HP puisque
  index 0) — collision totale, DEF/SPA/SPD/SPE apparaissant ensuite sur la même ligne que le titre au lieu
  d'une ligne en dessous. Corrigé en mettant les libellés de colonnes (HP/ATK/DEF/SPA/SPD/SPE) sur leur
  propre ligne, sous le titre "IVs".
- **Cause réelle du décentrage 3D, trouvée après deux échecs précédents** : les tentatives précédentes
  (ancrage en haut, puis centré, puis multiplier le paramètre `scale`) corrigeaient le symptôme sans traiter
  la cause. En recomparant plus précisément avec le bytecode décompilé de `StorageSlot.renderSlot`, il
  manquait deux choses que Cobblemon fait toujours avant d'appeler `drawProfilePokemon` : une translation en
  **z=0** (on utilisait z=100) et surtout un **`pose.scale(2.5f, 2.5f, 1f)` externe** appliqué juste avant —
  jamais reproduit jusqu'ici. Sans cet encadrement exact, le modèle rendait avec un décalage réel et
  reproductible, amplifié à chaque tentative d'agrandissement. Corrigé en reproduisant exactement cette
  chaîne (translate z=0 → scale externe 2.5 → même rotation qu'avant → `drawProfilePokemon` avec le même
  paramètre interne 4.5f qu'avant, jamais modifié) ; la taille finale se règle uniquement via un multiplicateur
  appliqué à ce 2.5f externe (`SLOT_ENLARGE=4`, `DETAIL_ENLARGE=10` — remontés par rapport au round précédent,
  "encore un peu plus grand").
- **Non testé par Claude** — cette fois la correction est motivée par une réplication fidèle du code source
  réel de Cobblemon (pas une nouvelle supposition), donc la confiance sur le centrage est plus élevée qu'avant,
  mais reste à confirmer ; la taille absolue (`SLOT_ENLARGE`/`DETAIL_ENLARGE`) est un réglage arbitraire à
  ajuster selon le retour d'Adrien.

### 4.17 Deux bugs persistants, corrigés différemment (2026-09-27)

- **Modèles 3D en bas au lieu d'être centrés** : information précieuse d'Adrien ("ils sont bien en bas
  maintenant") — ça confirme que le modèle s'étend **vers le bas** depuis son point d'ancrage, pas
  symétriquement. Ancrer au centre vertical de la case pousse donc mécaniquement le modèle sous le centre.
  Remplacé par un ratio ajustable `ICON_ANCHOR_RATIO` (0.2 = 20% depuis le haut de la case, au lieu de 0.5 =
  centre) — une seule constante à ajuster si le prochain retour dit encore "trop bas"/"trop haut", plutôt que
  de redériver toute la géométrie à chaque fois.
- **Menu déroulant toujours derrière le texte des boutons/champs, même après `flush()`** : le `flush()`
  n'a pas suffi (confirmé par le retour d'Adrien), signe que le système de lots de rendu de `GuiGraphics` ne
  respecte pas un simple ordre d'appel + flush pour le texte dans ce cas précis. Solution plus robuste :
  les widgets (`EditBox`/boutons) sont maintenant enregistrés via `addWidget` (saisie/focus uniquement, pas de
  rendu automatique) au lieu de `addRenderableWidget`, et rendus **manuellement** dans une seule boucle
  Java, immédiatement suivie du dessin du menu déroulant — ça élimine toute dépendance au comportement interne
  de traitement par lots de Minecraft, l'ordre est garanti par une seule boucle séquentielle plutôt que par
  des hypothèses sur le pipeline de rendu.
- **Non testé par Claude** — ces deux corrections sont les plus robustes tentées jusqu'ici (l'une basée sur
  un retour très informatif d'Adrien sur la direction du décalage, l'autre en éliminant complètement la
  dépendance au pipeline de rendu interne plutôt que d'essayer de le contraindre), mais restent à confirmer.

### 4.18 Diagnostic précis grâce aux captures d'Adrien (2026-09-27)

Deux captures très utiles (grille du PC avec modèles visibles, menu déroulant Nature ouvert) ont permis un
diagnostic exact au lieu de deviner :
- **Modèles 3D** : la capture montre les modèles positionnés trop haut dans leurs slots (espace vide visible
  en dessous) — direction inverse de ce qu'on pensait au round précédent. `ICON_ANCHOR_RATIO` remonté de 0.2
  à 0.3. Zoom aussi réduit (`SLOT_ENLARGE` 4→3, `DETAIL_ENLARGE` 10→7).
- **Menu déroulant** : la capture a permis d'isoler le bug précisément — le **fond** des boutons recouverts
  disparaît bien derrière le menu déroulant (la texture est correctement masquée), mais leur **texte**
  ("Chromatique : Non", "Enregistrer") continuait de s'afficher par-dessus le texte du menu déroulant. Ça
  confirme que le texte suit un pipeline de rendu différé séparé des remplissages/textures, qui ne respecte
  pas un simple ordre d'appel. Cette fois, double protection : (1) tout widget dont la zone chevauche le menu
  déroulant ouvert n'est **plus rendu du tout** (ni fond ni texte, impossible qu'il apparaisse par-dessus,
  quelle que soit la cause exacte du pipeline de texte), et (2) un `flush()` explicite après la boucle de
  rendu des widgets, avant de dessiner le menu déroulant.
- **Non testé par Claude** — la protection (1) ci-dessus élimine complètement la possibilité du bug par
  construction (le widget concerné n'est simplement plus dessiné), donc la confiance est nettement plus
  élevée cette fois pour le menu déroulant ; le centrage 3D reste un réglage empirique à confirmer.

### 4.19 Menu déroulant confirmé résolu ; vraie direction du ratio d'ancrage trouvée (2026-09-27)

- **Menu déroulant** : confirmé résolu par Adrien.
- **Ratio d'ancrage 3D** : à 1.0 (suivant une suggestion d'Adrien), les modèles disparaissent complètement du
  cadre — donnée précieuse qui confirme sans ambiguïté le sens de la relation : **augmenter** le ratio
  déplace le modèle **vers le bas** (cohérent avec 0.5 qui donnait déjà "tout en bas"). Le ratio 0.2 allait
  donc dans le bon sens mais pas assez loin. Recalé sur la vraie référence Cobblemon décompilée plutôt que de
  continuer à deviner : `StorageSlot.renderSlot` ancre à `y+1` sur un slot dont le scissor fait ~29px de haut,
  soit un ratio d'environ 1/29 ≈ **0.03** (quasiment le bord haut) — nouvelle valeur appliquée.
- **Nom du Pokémon retiré du menu Équipe** : n'affiche plus que le modèle 3D dans son slot carré, plus de
  texte "espèce Nv.X" à droite.
- **Non testé par Claude** — la nouvelle valeur du ratio est motivée par la référence réelle de Cobblemon
  (pas une nouvelle supposition arbitraire), donc plus fiable a priori, mais reste à confirmer par Adrien.

### 4.20 Boîte équipe redimensionnée, panneau racine compacté, nouvel ajustement 3D (2026-09-27)

- **Boîte équipe redimensionnée** : elle réservait toute une portion de largeur (~15% du panneau) alors
  qu'elle n'a plus besoin que de la place pour un carré (le nom du Pokémon ayant été retiré) — ce qui laissait
  un grand vide asymétrique à droite du slot. Elle s'ajuste maintenant exactement à la taille du carré
  (padding égal des deux côtés). Comme elle ne réclame plus une portion fixe de largeur, **tout le panneau
  racine est désormais dimensionné à la taille réelle de son contenu** (colonne détail + grille + boîte
  équipe empaquetées) plutôt qu'étiré à la largeur de l'écran — le panneau entier est donc plus compact,
  comme demandé.
- **Modèles 3D toujours pas centrés au ratio 0.03** : retour très informatif d'Adrien — à ce ratio (quasiment
  le bord haut), le **haut du modèle est rogné par le bord du slot**, et la partie visible restante paraît
  quand même basse. Ça révèle que le modèle s'étend à la fois au-dessus et en dessous de son point d'ancrage
  (pas uniquement vers le bas comme supposé), donc ancrer pile au bord le fait déborder. Ratio remonté à
  0.25 (entre les deux extrêmes testés), combiné à une **réduction de la taille** des modèles
  (`SLOT_ENLARGE` 3→2, `DETAIL_ENLARGE` 7→5) pour laisser plus de marge avant qu'un bord ne rogne quoi que ce
  soit.
- **Non testé par Claude** — à confirmer par Adrien.

### 4.21 Commande de debug temporaire pour le ratio d'ancrage 3D (2026-09-27)

Plusieurs rounds de devinette à l'aveugle sur le centrage vertical des modèles 3D n'ont pas convergé. Adrien
a demandé une commande in-game pour régler la valeur lui-même en direct plutôt que de continuer les
allers-retours capture d'écran/ajustement de code.

- **`/phantasmon debug iconanchor <ratio>`** (nombre décimal, ex. `0.4`) — modifie en direct le ratio
  d'ancrage vertical des modèles 3D du menu PC. S'applique **immédiatement** à un écran PC déjà ouvert (pas
  besoin de le refermer/rouvrir), puisque le rendu relit ce champ à chaque frame.
- **`/phantasmon debug iconanchor`** (sans argument) — affiche la valeur actuelle.
- `PhantasmonPcScreen.ICON_ANCHOR_RATIO` (constante `final`) est devenu `iconAnchorRatio` (champ statique
  modifiable), avec `setIconAnchorRatio(float)`/`getIconAnchorRatio()` publics.
- **Cette commande est temporaire** — dès qu'Adrien communique la valeur idéale trouvée en jeu, elle sera
  supprimée (`PhantasmonCommands`) et le champ redeviendra une constante `final` avec cette valeur.

### 4.22 Ratio final trouvé, commande retirée, finitions du panneau de présentation (2026-09-27)

- **Ratio d'ancrage final** : `-0.1`, trouvé par Adrien lui-même en jeu via la commande de debug. Champ
  redevenu une constante `final` (`ICON_ANCHOR_RATIO`), commande `/phantasmon debug iconanchor` supprimée.
- **Le modèle 3D peut désormais déborder de sa boîte** dans le panneau de présentation (plus de `scissor`
  autour de lui) — contrairement aux slots de la grille/équipe qui gardent le leur pour ne pas déborder sur
  les slots voisins.
- **Nom du Pokémon** : mis en majuscule automatiquement (les identifiants Cobblemon sont en minuscules, ex.
  "dialga" → "Dialga") et affiché en gras.
- **Indicateur chromatique** : le suffixe "*" dans le nom est retiré, remplacé par une **petite icône étoile**
  (nouvelle texture `star.png`, un pentagone doré généré en pixel art) affichée dans le coin supérieur droit
  de la zone du modèle 3D.
- **Non testé par Claude** — à confirmer par Adrien.

### 4.23 Badges à droite, ratio 3D indépendant pour l'écran, icône d'objet réelle (2026-09-27)

- **Badges de type** : déplacés à l'extrémité droite de la ligne nom+type (avant : juste après le nom).
  Empaquetés depuis le bord droit vers l'intérieur (type secondaire collé au bord, primaire juste à gauche).
- **Ratio d'ancrage 3D désormais indépendant par contexte** : la grille/l'équipe gardent `-0.1`
  (`SLOT_ICON_ANCHOR_RATIO`), l'écran de présentation a sa propre valeur `-0.15`
  (`DETAIL_ICON_ANCHOR_RATIO`) — les proportions très différentes entre un petit slot et le grand écran de
  présentation justifient des réglages séparés plutôt qu'une seule constante partagée.
- **Icône réelle de l'objet tenu** : en plus du nom, une vraie icône d'objet Cobblemon s'affiche maintenant à
  côté (résolue via `BuiltInRegistries.ITEM` avec le namespace `cobblemon:`, exactement comme les
  espèces/capacités sont déjà résolues ailleurs dans ce fichier — pas une icône générique). Les icônes
  d'objets Minecraft se rendent toujours en 16×16 fixe ; mise à l'échelle ici via le pose stack pour
  correspondre à la hauteur de la police du nom de l'objet, comme demandé.
- **Non testé par Claude** — à confirmer par Adrien, en particulier si les objets tenus utilisent bien le
  namespace `cobblemon:` (sinon l'icône restera absente, silencieusement, pour ces objets-là).

---

## 5. Dépannage courant

| Symptôme | Cause probable |
|---|---|
| `Dependency requires at least JVM runtime version 25` | JDK utilisé pour Gradle < 25 — voir §1.1 |
| Le mod n'apparaît pas dans le jeu | Mauvais dossier `mods/`, Fabric API manquante/incompatible, version Minecraft ≠ 1.21.1 |
| Toujours `Backend injoignable` dans le chat | Backend non lancé, mauvais port, pare-feu local |
| Crash au lancement mentionnant un mixin | Ne devrait pas arriver (mixins actuellement vides) — signaler si observé |
| `/phantasmon login` répond "comptes hors-ligne non supportés" | Compte de lancement en mode hors-ligne/cracké (`User.Type.LEGACY`) — utiliser un vrai compte Microsoft |
| `/phantasmon login` échoue à la vérification Mojang | Jeu lancé hors mode premium, ou API Mojang temporairement indisponible |
| Crash au lancement mentionnant `cobblemon`/Kotlin | Version de Cobblemon absente/incompatible (doit être 1.8.1 pour Fabric 1.21.1) |
| `UUID non valide à la position N` sur `delete`/`clone`/`edit`/`sendout` | UUID incomplet/tronqué tapé à la main — il faut l'UUID entier (36 caractères), affiché en clair par `/phantasmon pokemon list`/`pc` depuis le correctif du 2026-09-26 |
| Le Ghost d'un autre joueur n'apparaît jamais | Vérifier les logs client pour `Cannot render Ghost: unresolved species` (espèce/forme non reconnue par Cobblemon côté receveur) ; sinon vérifier que les deux joueurs sont bien dans la même dimension et que le backend tourne |
