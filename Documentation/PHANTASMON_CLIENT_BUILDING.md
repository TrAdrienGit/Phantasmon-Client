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

> Depuis le 2026-10-02, l'échange courant se fait plutôt via l'**écran d'échange en direct** (§4.33 :
> invitation par pseudo ou touche G, sans aucun UUID). Le flux par commandes ci-dessous reste disponible.

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

### 4.24 Talent/Objet/Capacités : sélecteurs au lieu de texte libre (2026-09-27)

- **Problème adressé** : un joueur ne connaît pas forcément l'id Cobblemon exact d'un talent, d'un objet ou
  d'une capacité — les champs `Talent`/`Objet tenu`/`Capacités ×4` de l'écran d'édition étaient de simples
  `EditBox` en texte libre. Ils deviennent des menus déroulants (même mécanisme que Nature/Type téra déjà en
  place).
- **Talent** : liste simple (pas de recherche, un Pokémon a au plus ~4 talents), chargée depuis les vraies
  données Cobblemon de l'espèce (`Species.getAbilities()` → `AbilityPool`, dédupliquée — le même talent peut
  apparaître à plusieurs priorités, ex. normal + caché). La valeur actuelle du Pokémon reste toujours
  sélectionnable même si elle n'apparaît pas dans la liste de l'espèce (mismatch de données).
- **Objet tenu** : menu déroulant avec **champ de recherche**, liste tous les objets `cobblemon:*` du
  registre d'objets (`BuiltInRegistries.ITEM`, filtré par namespace — mêmes conventions que la résolution
  d'icône d'objet du §4.23). Volontairement limité aux objets Cobblemon : le reste du code (stockage,
  résolution d'icône) suppose déjà qu'un id nu est toujours dans ce namespace.
- **Capacités (×4)** : menu déroulant avec **champ de recherche** par emplacement, liste toutes les capacités
  du jeu (`Moves.names()`). Une capacité déjà choisie dans un *autre* des 4 emplacements est automatiquement
  exclue de la liste de celui en cours d'édition — impossible d'avoir deux fois la même capacité.
- **Recherche** : pas un vrai `EditBox` — un simple buffer de texte (`searchQuery`) géré via
  `charTyped`/`keyPressed` (Retour arrière, Échap) pendant qu'un menu ITEM/MOVE est ouvert, pour rester
  cohérent avec le fait que le menu déroulant est déjà un overlay entièrement dessiné à la main plutôt qu'un
  vrai widget. Une ligne de recherche supplémentaire s'affiche au-dessus de la liste pour ces deux menus
  seulement (Talent/Nature/Type téra n'en ont pas besoin).
- **Changement de build.gradle** : ajout de `compileOnly "org.jetbrains.kotlin:kotlin-stdlib:2.0.21"`. Sans
  ça, `javac` refuse de compiler `for (PotentialAbility p : species.getAbilities())` avec
  `cannot access KMappedMarker` — l'API Cobblemon `AbilityPool` implémente `Iterable<T>` via une interface
  marqueur Kotlin, et `kotlin-stdlib` n'était présent qu'à l'exécution (fourni par `fabric-language-kotlin`,
  une dépendance de Cobblemon), jamais sur le classpath de compilation de ce projet. Aucun impact runtime :
  c'est une dépendance de compilation uniquement, `fabric-language-kotlin` reste la seule copie chargée en
  jeu.
- **Non testé par Claude** — `./gradlew build` passe (compilation + tests unitaires), mais tout le flux visuel
  (ouverture des menus, recherche, exclusion des doublons de capacités, sauvegarde) reste à valider en jeu par
  Adrien.

---

### 4.25 Filtrage des objets « stratégiques » et noms localisés (2026-09-29)

- **Problème 1 — trop d'objets listés** : le menu déroulant « Objet tenu » listait tout objet
  `cobblemon:*`, y compris des blocs/objets utilitaires du mod (pas des objets de combat). Corrigé en
  filtrant sur la présence du data component `HeldItemEffectComponent` de Cobblemon
  (`CobblemonItemComponents.HELD_ITEM_EFFECT`, lu via `Item.components()`) — c'est exactement le
  composant que le moteur de combat de Cobblemon lit pour savoir qu'un objet a un effet en combat
  (Leftovers, Choice Band, Vive-Poteau, baies, etc.) ; un bloc/objet utilitaire ne le porte jamais. Cette
  logique vit dans une nouvelle classe partagée, `CobblemonHeldItems` (paquet `pokemon`, côté client),
  utilisée à la fois par le sélecteur d'édition et par l'affichage en lecture seule
  (`PhantasmonPcScreen.resolveHeldItemStack`, qui utilisait auparavant `BuiltInRegistries.ITEM` +
  namespace `cobblemon:` directement).
  - Clé de stockage changée : l'id **Showdown** de l'objet (`HeldItemEffectComponent.showdownId`, ex.
    `leftovers`) plutôt que le chemin d'id d'enregistrement Minecraft de l'objet (qui peut différer, ex.
    `choice_band` avec underscore) — cohérent avec le format déjà produit par l'import Showdown pour
    `data.heldItem`.
- **Problème 2 — ids en anglais peu lisibles** : les menus Talent/Objet/Capacités affichaient l'id brut
  Cobblemon (ex. `stance-change`, `leftovers`). Ils affichent maintenant le **nom localisé réel** — pour
  les talents, `AbilityTemplate.getDisplayName()` (capturé une fois par espèce dans `init()` avec l'id,
  car le talent lui-même n'est pas conservé) ; pour les objets, le nom d'objet vanilla localisé
  (`Item.getDescription()`, la même API que pour n'importe quel objet Minecraft) ; pour les capacités,
  `MoveTemplate.getDisplayName()`. Ces trois-là respectent automatiquement la langue du client (repli sur
  l'anglais si aucune traduction FR n'existe pour cette clé côté Cobblemon) — l'id brut reste stocké/utilisé
  en interne, seul l'affichage change. La recherche (Objet/Capacités) filtre maintenant sur l'id **ou** le
  nom affiché, pour retrouver un objet aussi bien en tapant son id anglais que son nom localisé.
- **Non testé par Claude** — à confirmer par Adrien : que le filtre `HeldItemEffectComponent` exclut bien
  les objets non-stratégiques attendus sans exclure un vrai objet de combat par erreur, et que les noms
  affichés sont corrects en jeu.
- **⚠️ Correction** : les deux points ci-dessus se sont révélés faux à l'usage — voir §4.26.

---

### 4.26 Correctifs suite au test d'Adrien : plus aucun objet trouvé, talents non traduits (2026-09-29)

- **Objets — le filtre `HeldItemEffectComponent` du §4.25 ne renvoyait rien du tout** (testé par Adrien, en
  français comme en anglais). Ce composant n'est en fait pas posé comme composant par défaut sur les
  `Item` (`Item.components()` renvoyait toujours `null` pour lui) — mauvaise piste. La vraie source de
  vérité, trouvée en décompilant le jar Cobblemon : le tag d'objet vanilla **`#cobblemon:held/is_held_item`**
  — c'est la liste que Cobblemon lui-même maintient pour distinguer un objet de combat réel (Leftovers,
  Choice Band, baies, etc.) d'un objet utilitaire. `CobblemonHeldItems` (paquet `pokemon`) a été réécrite
  pour lire ce tag via `Registry.getTagOrEmpty(TagKey<Item>)` plutôt que le data component. Au passage, la
  clé de stockage revient au chemin d'id Minecraft de l'objet (ex. `choice_band`, avec underscore) — pas un
  id "Showdown" séparé comme tenté au §4.25, qui n'existait nulle part ailleurs dans le code : c'est
  exactement le format que `CobblemonIdentifiers.slugUnderscore` produit déjà pour `data.heldItem` à
  l'import Showdown, donc les deux chemins (import Showdown / sélection manuelle dans l'éditeur) restent
  cohérents entre eux.
- **Talents affichés sous forme `cobblemon.ability.<id>`** : `AbilityTemplate.getDisplayName()` ne renvoie
  pas un nom résolu mais la **clé de traduction brute** (confirmé par décompilation + par ce qu'Adrien a vu
  à l'écran). Corrigé en enveloppant cette clé dans `Component.translatable(...).getString()` avant de
  l'afficher — exactement ce que Minecraft fait pour n'importe quelle traduction. Les capacités
  (`MoveTemplate.getDisplayName()`) n'avaient pas ce problème : cette méthode-là renvoie directement un
  `Component` déjà traduit, pas une clé brute — d'où le retour d'Adrien confirmant que les capacités
  fonctionnaient bien dès le premier essai.
- **Non testé par Claude** — à confirmer par Adrien en jeu : la liste d'objets n'est plus vide et ne contient
  que des objets de combat réels, et les talents s'affichent maintenant en toutes lettres.

---

### 4.27 Couverture de la liste d'objets + traduction des talents oubliée dans le panneau de détail (2026-09-29)

- **Objets manquants signalés (Énergie Basique, Méga-Gemmes/pierres Méga, Cristaux Z)** : vérifié par
  décompilation du registre d'objets Cobblemon 1.8.1 — **aucun de ces trois n'existe comme objet dans cette
  version de Cobblemon** (pas de Méga-Évolution, pas de capacités Z implémentées). Ce n'est donc pas un bug
  de filtrage de notre côté : le tag `#cobblemon:held/is_held_item` utilisé depuis le §4.26 est la liste
  exhaustive que Cobblemon lui-même expose. En revanche, les **Gemmes de type** (Gemme Feu, Gemme Eau, etc. —
  18 objets, `cobblemon:fire_gem` et consorts) existent bel et bien et sont bien listées dans ce tag via une
  référence imbriquée (`#cobblemon:type_gems`), tout comme les graines de terrain (`#cobblemon:held/terrain_seeds`
  — Graine Électrique, etc.). Comme le picker était entièrement vide au moment du test précédent (§4.26, avant
  correction), Adrien n'a pas pu les voir non plus — elles devraient maintenant apparaître avec le reste. Si
  jamais Cobblemon ajoute la Méga-Évolution/les capacités Z dans une future version, il suffira de mettre à
  jour la version de Cobblemon utilisée : `CobblemonHeldItems` suit automatiquement ce tag, aucun changement de
  code nécessaire.
- **Talent toujours en `cobblemon.ability.<id>` dans le panneau de détail (lecture seule)** : le §4.26 n'avait
  corrigé la traduction que dans le formulaire d'édition. Le panneau d'affichage du Pokémon sélectionné
  (`PhantasmonPcScreen`) construisait encore la ligne Talent directement depuis `dto.ability()` brut. Même
  correctif appliqué ici : nouvelle méthode `abilityLabel(String)` qui résout l'`AbilityTemplate` via
  `Abilities.get(id)` puis traduit sa clé brute avec `Component.translatable(...)`, à l'identique de ce qui se
  fait déjà pour les capacités dans ce même panneau (`Moves.INSTANCE.getByName` + `MoveTemplate.getDisplayName()`,
  qui lui renvoie directement un `Component` déjà traduit).
- **Non testé par Claude** — à confirmer par Adrien : le talent s'affiche bien traduit sur l'écran de détail, et
  les Gemmes/graines apparaissent désormais dans le sélecteur d'objet.

---

### 4.28 Recherche Objet/Capacité moins stricte : mots-clés dans n'importe quel ordre (2026-09-29)

- **Problème** : la recherche des menus Objet/Capacités utilisait un simple `String.contains(query)` sur la
  chaîne entière — il fallait donc taper le nom exact dans le bon ordre. Exemple concret d'Adrien : l'objet
  s'appelle « Energy Booster » (l'ordre réel du nom anglais), donc taper « Booster Energy » (l'ordre le plus
  naturel en français) ne trouvait rien.
- **Correctif** : nouvelle méthode `matchesSearch(id, displayName)` — la requête est découpée en mots
  (séparés par des espaces), et chaque mot doit apparaître *quelque part* dans « id + nom affiché », dans
  n'importe quel ordre. Donc « Booster Energy », « Energy Booster » ou juste « Boo » trouvent tous le même
  objet. S'applique aux deux seuls menus qui ont une recherche (Objet, Capacités) — Talent/Nature/Type téra
  n'en ont pas besoin (listes courtes).
- **Non testé par Claude** — à confirmer par Adrien en jeu.

---

### 4.29 Nature/nom/objet non traduits dans le panneau de détail ; confirmation objets manquants (2026-09-29)

- **Nature non traduite** : même bug que les talents (§4.26/§4.27) — `Nature.getDisplayName()` (Cobblemon)
  renvoie aussi la clé de traduction brute, pas le texte résolu. Corrigé une fois pour toutes dans
  `NatureModifiers.displayName(String)` (nouvelle méthode partagée, paquet `pokemon`), qui résout la
  `Nature` via `Natures.getNature(id)` puis traduit sa clé — utilisée à la fois par le panneau de détail
  (`natureLabel`) et par le menu déroulant Nature de l'éditeur (`natureOptionLabel`), qui avaient chacun leur
  propre `capitalize(natureId)` avant.
- **Nom du Pokémon non traduit dans le panneau de détail** : affichait l'id brut capitalisé (ex. "Dialga" ça
  tombait bien en anglais, mais un nom moins évident resterait faux). Utilise maintenant
  `Species.getTranslatedName()` (Cobblemon) quand l'espèce se résout, avec repli sur l'id capitalisé sinon.
  Le suffixe de forme entre parenthèses (ex. « (mega-x) ») reste tel quel — Cobblemon n'expose pas de nom
  traduit par forme aussi simplement que par espèce.
- **Nom de l'objet tenu non traduit dans le panneau de détail** : l'icône à côté était déjà la bonne (réelle,
  résolue), mais le texte à côté restait l'id brut. Corrigé en utilisant `ItemStack.getHoverName()` une fois
  l'objet résolu (repli sur l'id brut si l'objet ne résout pas).
- **Méga-Gemmes / Cristaux Z / Energy Booster toujours absents — confirmé, ce n'est pas un bug** : nouvelle
  vérification par décompilation, cette fois sur la totalité des ~750 champs statiques de `CobblemonItems`
  (pas seulement ceux de type `CobblemonItem`) : aucune trace de Méga-Pierre, Cristal Z ou Energy Booster,
  sous quelque nom que ce soit. Point notable : le talent Vigueur (Protosynthesis) existe et sa description
  mentionne bien l'Energy Booster comme déclencheur possible (texte copié depuis les jeux officiels), mais
  l'objet lui-même n'a jamais été implémenté dans cette version de Cobblemon (1.8.1, id Modrinth `gBW3vLC7`
  épinglé dans `gradle.properties`) — c'est une limite connue de Cobblemon (pas de Méga-Évolution ni de
  capacités Z à ce jour), pas quelque chose que `CobblemonHeldItems`/le tag `is_held_item` filtrerait par
  erreur. Si une version plus récente de Cobblemon les ajoute un jour, ils apparaîtront automatiquement dans
  le sélecteur sans changement de code (le tag est suivi dynamiquement) — seule une mise à jour de
  `cobblemon_version` dans `gradle.properties` serait nécessaire, à discuter avec Adrien le moment venu (ça
  touche la compatibilité de version Minecraft/Cobblemon globale du mod, pas juste ce menu).
- **Non testé par Claude** — à confirmer par Adrien en jeu pour les 3 traductions.

---

### 4.30 Premier test multijoueur réel : URL du backend pointée sur la machine dev (2026-09-29)

- **Contexte** : premier vrai test à deux clients. Setup actuel (temporaire, pas propre — assumé) :
  machine dev (`100.116.43.32` en IP Tailscale) fait tourner le backend + Postgres + un client
  Minecraft (`MystAria_`) ; machine "production-server" (`100.106.248.73`, tunnel SSH/Tailscale, voir
  `Phantasmon-Backend/Documentation/SERVER_AGENT_BRIEFING.md`) fait tourner un 2e client Minecraft
  (`TheMashen`) et possède elle aussi les repos + un service NSSM pour le backend, **mais ce backend-là
  n'est volontairement pas utilisé pour l'instant** — les deux clients doivent parler au même backend
  (celui de la machine dev) pour partager les mêmes joueurs/échanges/présence.
- **`BackendConfig.BASE_URL`** changé de `http://localhost:8080` vers `http://100.116.43.32:8080` —
  sinon le client tournant sur la machine serveur aurait tenté de parler à *son propre* localhost (donc
  au backend NSSM, avec une base Postgres complètement séparée), rendant tout partage de données
  impossible entre les deux joueurs. Toujours une seule constante en dur (pas encore configurable côté
  utilisateur — noté comme limitation connue dans le fichier lui-même), donc à réviser avant tout vrai
  déploiement.
- **Connexion entre les deux clients** : pas de serveur dédié buildé exprès (inutile et long à
  maintenir en synchro avec les ~230 mods du modpack) — un simple **"Ouvrir au LAN"** depuis une partie
  solo sur le modpack `Cobblemon Academy 2.0` suffit, le serveur intégré tournant déjà avec tous les
  mods nécessaires. L'autre client se connecte en direct via l'IP Tailscale de l'hôte + le port annoncé
  dans le chat (la découverte automatique "Parties locales" ne fonctionne **pas** à travers Tailscale,
  qui ne relaie pas le broadcast UDP du LAN — connexion directe uniquement).
- **Déploiement** : `scripts/deploy-to-prod-server.sh` build une seule fois puis copie le jar dans les
  deux dossiers `mods/` (local `Cobblemon Academy 2.0`, serveur `Cobblemon Academy 2.0 - Copie`),
  nettoyant l'ancien jar à chaque fois. Un redémarrage du jeu déjà lancé est nécessaire pour charger le
  nouveau jar.
- **Non testé par Claude** — à confirmer par Adrien : connexion effective entre les deux comptes,
  visibilité mutuelle des Ghost Pokémon, échanges.

---

### 4.31 Bug réel trouvé au 1er test : le sendout ne se propage pas à l'autre joueur (2026-09-29)

- **Symptôme observé par Adrien** : après `/phantasmon sendout`, le log backend affiche
  `broadcasting to 0 group member(s) + self` — l'autre joueur, pourtant dans le même monde, ne voit rien.
- **Cause réelle, confirmée par les logs** : les deux joueurs rejoignent avec un `server_fingerprint`
  différent. Celui qui héberge la partie (`Minecraft.isLocalServer() == true`, c'est lui qui a fait
  "Ouvrir au LAN") calcule toujours `"singleplayer"` ; celui qui se connecte en tant qu'invité calcule un
  hash de l'adresse tapée pour se connecter (`Minecraft.getCurrentServer().ip`, ici l'IP Tailscale de
  l'hôte). Ces deux valeurs ne peuvent **jamais** coïncider, même si les deux joueurs sont bel et bien
  dans la même session — `PresenceService` les place donc dans deux groupes différents.
- **Ce n'est pas un bug de "vraie prod"** : sur un vrai serveur dédié, personne n'est "l'hôte local" —
  tous les joueurs se connectent de la même façon et calculent donc le même hash. Ce problème est
  spécifiquement un artefact de la méthode de test choisie ("Ouvrir au LAN" pour éviter de monter un
  serveur dédié avec les ~230 mods du modpack, jugé trop long).
- **Solution temporaire de test** : nouvelle commande `/phantasmon debug fingerprint <valeur>` — force
  manuellement le `server_fingerprint` envoyé au backend à une valeur choisie, identique sur les deux
  clients, au lieu de la valeur calculée automatiquement. `/phantasmon debug fingerprint` sans argument
  retire l'override. **Même logique que l'ancienne commande de debug `iconanchor`** (§4.21/§4.22) : un
  hook temporaire pour débloquer un test, pas une vraie fonctionnalité, à retirer une fois les tests
  multijoueur terminés.
- **⚠️ Important pour la suite du test** : l'override ne s'applique qu'au **prochain** envoi de
  `JoinServerGroup`, qui n'a lieu qu'une fois par connexion. Comme les deux joueurs étaient déjà connectés
  avec le mauvais fingerprint au moment où ce correctif arrive, il faut, dans l'ordre : 1) chaque joueur
  tape `/phantasmon debug fingerprint memetest` (la **même** valeur des deux côtés), 2) **quitter le monde
  et le rejoindre** (pas juste re-taper `/phantasmon login`, qui ne fait rien si déjà connecté) pour
  déclencher un nouveau cycle de connexion qui enverra le fingerprint forcé.
- **Non testé par Claude** — correctif écrit et déployé (build+tests verts) suite au retour d'Adrien, mais
  pas encore confirmé en jeu.
- **Mise à jour (2026-09-29) — override persisté sur disque** : Adrien a demandé que la valeur forcée
  s'applique automatiquement après chaque login, sans avoir à retaper la commande à chaque reconnexion.
  L'override est maintenant écrit dans un simple fichier texte du dossier de config du mod
  (`config/phantasmon-fingerprint-override.txt`, une valeur brute, pas de JSON — c'est un réglage de test
  jetable, pas un vrai paramètre utilisateur) et relu au lancement du jeu. Reste **entièrement opt-in** :
  rien n'est jamais écrit tant que la commande n'a pas été tapée au moins une fois, et
  `/phantasmon debug fingerprint` sans argument efface à la fois la valeur en mémoire et le fichier,
  remettant le calcul normal en place pour de bon. Il faut donc toujours taper la commande une première
  fois sur chaque client (avec la même valeur des deux côtés) — c'est seulement les fois *suivantes*
  (relance du jeu, reconnexion) qui deviennent automatiques.

---

### 4.32 Raccourcis clavier PC/sendout, et sendout simplifié (2026-09-29)

- **Contexte** : suite au retour d'Adrien sur la lourdeur des UUID en trade, deux améliorations
  d'ergonomie indépendantes du sujet trade lui-même.
- **`/phantasmon sendout` ne prend plus d'UUID** — il sort systématiquement le Pokémon actuellement à
  l'**emplacement 1 de l'équipe** (`PokemonCommandHandler.sendOutTeamLead`). Pour sortir un autre
  Pokémon, il faut d'abord `/phantasmon pokemon team set <uuid> 1`. Message dédié
  (`phantasmon.ghost.error.no_team_lead`) si l'emplacement 1 est vide.
- **Deux nouveaux raccourcis clavier** (`PhantasmonKeybinds`, nouvelle classe) : un pour ouvrir le PC
  (touche P par défaut), un pour le sendout équipe (touche O par défaut) — les deux apparaissent dans
  Options > Contrôles sous la catégorie "Phantasmon" et sont librement rebindables. Réutilisent le même
  code que les commandes chat correspondantes (`PokemonCommandHandler.openPc()`/`sendOutTeamLead(...)`,
  nouvelles surcharges sans `FabricClientCommandSource` puisqu'un raccourci clavier n'a pas de contexte
  de commande — le retour se fait directement dans le chat).
- **Persistance des touches assignées entre sessions et mises à jour du mod** : c'est du comportement
  Minecraft natif, rien codé spécifiquement — tant que les identifiants (`key.phantasmon.open_pc`,
  `key.phantasmon.sendout`) ne changent pas d'une version à l'autre, Minecraft retrouve et réapplique
  tout seul la touche choisie par le joueur via `options.txt`, exactement comme pour n'importe quel autre
  mod avec des raccourcis.
- **Piste explorée puis écartée pour le "Ghost Trade" dans la roue Cobblemon (touche R)** : Cobblemon
  n'expose aucune API pour ajouter une option à sa roue d'interaction joueur — c'est un enum Java fermé
  (`PlayerInteractOptionsPacket.Options`) rempli côté serveur Cobblemon, jamais extensible par un autre
  mod. L'ajouter de force nécessiterait un Mixin dans les classes internes de Cobblemon
  (`InteractWheelGuiFactoryKt`/`InteractWheelGUI`) — techniquement possible (vérifié par décompilation)
  mais fragile (peut casser silencieusement à chaque mise à jour de Cobblemon) et ce serait le tout
  premier Mixin de ce mod. **Idée mise de côté, intéressante pour Adrien, mais pas implémentée** — le
  raccourci clavier dédié ci-dessus est le choix retenu pour l'instant à la place d'une option dans la
  roue Cobblemon. Le vrai menu "Ghost Trade" (interface où les deux joueurs choisissent leurs Pokémon et
  valident ensemble) reste à concevoir — c'est une fonctionnalité bien plus grosse qu'un raccourci
  (négociation en temps réel entre deux clients via WebSocket), pas encore commencée.
- Build + tests verts, déployé sur les deux machines.
- **Non testé par Claude** — à confirmer par Adrien en jeu.
- **Mise à jour (2026-09-29) — sendout devient un toggle** : après test, Adrien a demandé que
  `/phantasmon sendout` (commande **et** touche) bascule entre sortir et rappeler, plutôt que de
  toujours sortir. Nouveau `PhantasmonCommands.toggleSendOut(...)` (public static, partagé par la
  commande et la touche) : si un Ghost est déjà dehors (`GhostSession.hasActiveGhost()`), rappelle ;
  sinon, sort le Pokémon de l'emplacement 1 comme avant. Aucun changement côté `/phantasmon recall`
  (reste disponible séparément).
- **Touche "trade" absente — clarification** : ce n'est pas un oubli/bug, elle n'a jamais été
  implémentée. Le message précédent mentionnait vouloir « un raccourci clavier indépendant » pour
  Ghost Trade à la place d'une option dans la roue Cobblemon, mais seuls les raccourcis PC et sendout
  ont été codés à ce stade — le vrai menu Ghost Trade (choix des Pokémon + validation à deux) n'existe
  pas encore, donc une touche pour l'ouvrir n'aurait rien à ouvrir pour l'instant.

### 4.33 Écran d'échange en direct (2026-10-02)

Recréation de la maquette fournie par Adrien (`Documentation/ecran_echange/` : `SPEC_ECRAN_ECHANGE.md`,
`phantasmon_trade_ui.html`, captures de référence et `measures.json`), rendue fonctionnelle de bout en
bout. Les deux joueurs voient l'équipe de l'autre, choisissent chacun leur offre, se déclarent prêts, et
l'échange s'exécute quand les deux le sont.

**Lancer un échange** (choix d'Adrien : invitation + touche) :
- `/phantasmon trade invite <pseudo>` — les pseudos sont auto-complétés depuis la liste des joueurs du
  serveur (plus aucun UUID à taper, c'était le point « très chiant » du test du 2026-09-29) ;
- ou la touche **G** (« Échanger avec le joueur visé », modifiable dans Options → Commandes → Phantasmon)
  en visant l'autre joueur ;
- l'invité reçoit un message avec **[Accepter]** / **[Refuser]** cliquables (équivalents :
  `/phantasmon trade join` / `/phantasmon trade decline`). L'invitation expire après 60 s.
- L'écran s'ouvre chez les deux joueurs dès l'acceptation. Le premier Pokémon de l'équipe est proposé
  par défaut (spec §7.1).

**Dans l'écran** :
- clic sur un slot du rail **gauche** = changer son offre ; le rail **droit** est en lecture seule
  (choix d'Adrien) — la fiche droite montre toujours l'offre actuelle du partenaire, mise à jour en
  direct, et son slot passe en style « sélectionné » ;
- **⇄ ÉCHANGER** → « PRÊT ✓ » (re-cliquer retire l'accord). Tout changement d'offre, d'un côté ou de
  l'autre, remet les deux joueurs à « non prêt » (règle appliquée par le backend) ;
- quand les deux sont prêts, le backend exécute l'échange puis la fenêtre « ÉCHANGE EN COURS » s'ouvre
  (sur confirmation du serveur, pas en simulation comme dans la maquette) ; **FERMER** ferme l'écran ;
- **QUITTER** ou **Échap** ouvrent la confirmation « QUITTER L'ÉCHANGE ? » ; confirmer annule l'échange
  pour les deux (l'autre joueur voit son écran se fermer avec un message dans le chat).
- Le Pokémon reçu prend **l'emplacement d'équipe** du Pokémon donné (choix d'Adrien, comme un échange
  Cobblemon). Si l'un des deux était sorti en Ghost, il est rappelé automatiquement.

**Écarts assumés par rapport à la maquette** (fonctionnellement nécessaires dans un vrai échange) :
- un « PRÊT ✓ » vert apparaît à gauche du nom du partenaire quand il est prêt (la maquette simule le
  partenaire et n'en a pas besoin) ;
- le pied de page affiche à gauche, seulement quand c'est utile, une ligne d'état (« En attente de X… »,
  « X est prêt. », ou l'erreur traduite renvoyée par le backend) ;
- textes des pieds de rail : « Cliquez sur un Pokémon pour le proposer » à gauche, « Offre de X en
  surbrillance » à droite (le texte « pour l'examiner » de la maquette ne correspond plus au
  comportement, le rail droit n'étant pas cliquable) ;
- la Poké Ball de la fenêtre d'échange fait son aller-retour de 180 px **centré** sur la bande (dans la
  maquette elle partait du centre et chevauchait le nom de droite).

**Implémentation** :
- `gui/PhantasmonTradeScreen` dessine tout dans l'espace **1600×900 px de la maquette**, avec les cotes
  Z01–Z29 de la spec utilisées telles quelles, puis applique une seule échelle `s/2`
  (`s = min(1, largeur/800, hauteur/450)`, spec §2) : à 1080p en échelle GUI 2, 1 px maquette = 1 pixel
  écran. Textes en échelle entière (×2 pour les titres 16 px, ×1 sinon) pour rester nets.
- Textures générées par `scripts/generate_trade_textures.py` (Pillow, valeurs CSS exactes de la maquette)
  dans `textures/gui/trade/` : fond, rails, fiches, en-têtes joueur, viewport (halo + vignettage +
  disque quadrillé), fenêtre modale, ombre sous le modèle. Les dégradés sont générés en demi-résolution
  avec `"blur": true` (filtrage bilinéaire, pas de « dallage » — cf. §4.13). Relancer le script après
  toute retouche de couleur.
- **Piège de rendu évité** : en 1.21.1, `fill`/`drawString` sont différés alors que `blit` dessine
  immédiatement — une texture dessinée après un remplissage finissait *sous* lui. Chaque blit de l'écran
  fait d'abord un `flush()`, ce qui garantit que l'ordre du code = l'ordre visuel. Les fenêtres modales
  sont dessinées à z=2000, au-dessus des icônes d'objet et des modèles 3D.
- Le rendu des modèles 3D (`drawProfilePokemon` par réflexion) et des icônes d'objet a été extrait de
  `PhantasmonPcScreen` dans `gui/PokemonGuiRendering`, partagé par les deux écrans (aucun changement de
  comportement pour le PC).
- `trade/LiveTradeController` (commandes, touche, messages WebSocket, ouverture différée de l'écran au
  tick suivant — même piège de fermeture du chat que pour le PC, §4.6) et `trade/LiveTradeState`
  (miroir de l'état envoyé par le serveur, logique pure testée par `LiveTradeStateTest`). Tout passe par
  le WebSocket de présence existant (`GhostSession`), aucune seconde connexion. Le client n'applique
  **jamais** un changement d'offre/prêt localement : il attend l'écho du serveur.
- Sexe affiché : `data.gender` (`M`/`F`, écrit par l'import Showdown), sinon déduit du ratio de l'espèce
  (espèces asexuées / mono-genre) — jamais deviné pour une espèce mixte (`pokemon/PokemonGender`, testé).
  Il n'existe pas encore de champ « sexe » dans l'éditeur du PC.
- Backend : protocole WebSocket `Trade*` documenté dans `PHANTASMON_API_REFERENCE.md` (section
  « Échange en direct ») ; correction au passage du bug qui rendait impossible la suppression d'un
  Pokémon déjà échangé (migration `V7`, voir `PHANTASMON_DB_SCHEMA.md` §5.5).
- Le flux par commandes `/phantasmon trade propose|accept|cancel|view|list` reste disponible tel quel.

**Test manuel (2 comptes, comme le 2026-09-29)** :
1. Relancer le backend (applique la migration `V7`), déployer le nouveau jar sur les deux machines.
2. Les deux joueurs se connectent (auto-login) et ont au moins un Pokémon dans l'équipe.
3. Joueur A : `/phantasmon trade invite <B>` (ou G en visant B) → B clique **[Accepter]**.
4. Vérifier : les deux écrans s'ouvrent, chaque fiche gauche montre son propre 1er Pokémon, chaque fiche
   droite celui de l'autre ; changer d'offre côté A met à jour la fiche droite de B.
5. A clique ÉCHANGER → « PRÊT ✓ » chez A, « PRÊT ✓ » vert à côté du nom de A chez B ; A change d'offre →
   les deux repassent à « ⇄ ÉCHANGER ».
6. Les deux prêts → fenêtre « ÉCHANGE EN COURS » chez les deux, puis `/phantasmon pc` : le Pokémon reçu
   est au même emplacement d'équipe que celui donné.
7. Nouvel échange puis QUITTER (ou Échap) → confirmation → QUITTER : l'écran de l'autre se ferme avec
   « A a quitté l'échange ».
8. Critères visuels de la spec §10 : comparer une capture 1080p / échelle GUI 2 avec
   `Documentation/ecran_echange/reference/ref_01_ecran.png`, puis essayer les échelles GUI 1 à 4 et une
   fenêtre 1280×720.

- Build + tests verts (client 40 tests dont 11 nouveaux, backend 110 dont 16 nouveaux).
- **Non testé visuellement par Claude** (aucun affichage du jeu ici) — à confirmer par Adrien. Points
  les plus incertains : taille/ancrage des modèles 3D dans les slots et la fiche
  (`SLOT_MODEL_SCALE`/`CARD_MODEL_SCALE`/`*_MODEL_ANCHOR` en tête de `PhantasmonTradeScreen`, réglages
  empiriques comme pour le PC), rendu des glyphes ⇄ ♂ ♀ ★ ✓ (police de secours Unicode de Minecraft),
  et la Poké Ball animée.

### 4.34 Écran d'échange — retours du premier test à deux comptes (2026-10-02)

Échange réussi entre les deux comptes de test. Corrections demandées par Adrien :
- **Fenêtre « ÉCHANGE EN COURS » infinie** : la Poké Ball fait maintenant 2 allers-retours (3,6 s), la
  fenêtre passe en « ÉCHANGE TERMINÉ » (« X a été échangé contre Y ! »), puis l'écran se ferme tout seul
  ~1,8 s plus tard. FERMER / Échap ferment immédiatement.
- **Menu réduit à 80 %** de sa taille précédente (`MENU_SCALE`), toujours centré ; le jeu (flouté) est
  visible autour au lieu du fond opaque plein écran.
- **Lisibilité** : toutes les valeurs de la fiche (objet, nature, talent, capacités, IV/EV, total) passent
  en taille ×2 (`VALUE_SCALE`), ainsi que le niveau, le bouton ÉCHANGER, la ligne d'état du pied de page
  et les textes des fenêtres. Les cases sont redistribuées sur toute la hauteur pour occuper l'espace vide
  sous capacités et IV/EV (lignes de capacités de 68 px, lignes de stats de 36 px).
- **Type à côté de chaque capacité** (badge Cobblemon à droite du nom). Seuls autres ajouts, jugés utiles
  et non superflus : le bonus/malus de la nature à côté de son nom (« +Atk / -SpA »), et le libellé de la
  stat augmentée en rouge / diminuée en bleu dans IV/EV.
- **Modèles 3D de l'équipe ×1,5** ; nom du Pokémon descendu dans le slot pour leur laisser la place.
- **Modèles trop hauts** : l'ancre est maintenant calculée depuis le centre de la zone
  (`modelAnchorY`, reste centré si l'échelle change) + un décalage vers le bas (20 px équipe, 40 px fiche).
  La capture annoncée n'est pas arrivée dans la conversation, donc ce décalage est estimé :
  **commande temporaire** `/phantasmon debug tradeoffset slot <px>` / `card <px>` pour l'ajuster en direct
  (valeurs plus grandes = plus bas), à retirer une fois les bonnes valeurs figées dans le code.
- **Non testé visuellement par Claude.**

### 4.35 PC refait avec la DA de l'écran d'échange (2026-10-02)

Adrien : le PC était bien organisé mais « cheap » à côté de l'écran d'échange. Il est reconstruit sur la
même base graphique :
- **Socle commun** `gui/PhantasmonCanvasScreen` : tout ce qui était générique dans l'écran d'échange
  (canevas 1600×900 à 80 % centré, textures, couleurs, textes à échelle entière, rails, slots, fiche
  Pokémon complète, boutons, pied de page, fenêtres modales, piège `flush()` avant chaque blit) en a été
  extrait tel quel. `PhantasmonTradeScreen` en hérite **sans changement de rendu ni de comportement**.
- **Disposition du PC** : rail **Équipe** à gauche (identique au rail d'échange), **fiche Pokémon** au
  centre (identique à celle de l'échange, plus le type de Puissance Cachée à côté du Téracristal, déjà
  présent dans l'ancien PC), **grille 6×5 de la boîte** à droite avec ◀ BOÎTE n / 16 ▶. En-tête : « PC ·
  joueur » à gauche, **IMPORTER** au centre (à la place d'ÉCHANGER — import Showdown depuis le
  presse-papiers), compteurs PC/équipe à droite. Pied de page : ligne d'état (chargement, erreurs,
  confirmation d'import, sinon aide) et **ÉDITER / SUPPRIMER** quand un Pokémon est sélectionné.
- **Comportement conservé** : clic = sélection, glisser-déposer entre n'importe quels slots (équipe ou PC)
  = déplacement ou échange de place (règle backend inchangée), ÉDITER ouvre l'éditeur existant.
- **Ajouts** : la sélection suit le Pokémon (plus le slot) quand on change de boîte ; le modèle 3D suit la
  souris pendant un glisser-déposer ; molette / flèches ← → / ◀ ▶ changent de boîte, **y compris pendant un
  glisser-déposer** (on peut donc déposer un Pokémon dans une autre boîte) ; **SUPPRIMER demande
  confirmation** (fenêtre identique à « Quitter l'échange ? », touche Suppr aussi).
- Taille des modèles dans la grille : même réglage que les slots d'équipe (centrage confirmé), réduit au
  prorata de la zone plus petite (`GRID_SLOT`).
- L'éditeur (`PhantasmonPcEditScreen`) n'a **pas** encore été refait dans cette DA — prochaine étape
  possible.
- Build vert. **Non testé visuellement par Claude.**
- **Retours sur capture (2026-10-02), corrigés dans le socle commun (profite aussi à l'écran d'échange)** :
  - *Arêtes manquantes / slots mal délimités* : le canevas est rendu à ~0,8-1 pixel écran par pixel de
    maquette, donc un trait de 1 px tombait parfois entre deux pixels et disparaissait. Tous les traits
    (cadres, séparateurs, bordures de slots et de badges, lueurs) passent par `outline()`/`hairline()`,
    qui garantit au moins 1 pixel écran.
  - *Textes pixelisés* : la taille de police est maintenant calée sur un nombre entier de pixels écran et
    la position sur la grille de pixels (`snapTextScale`, `drawText`) — les petits textes restent nets.
  - *Boutons ÉDITER / SUPPRIMER* (et ceux de la confirmation) agrandis, libellés en taille ×2.
  - *Nature* : « +Spe » (rouge) / « -SpA » (bleu) à la même taille que la valeur.
  - *Types* : palette **officielle** des jeux actuels (Écarlate/Violet, Pokémon HOME, reprise par
    Bulbapedia) au lieu des couleurs de Cobblemon, pour les types du Pokémon, des capacités, Téracristal
    et P. cachée (`OFFICIAL_TYPE_COLORS`). Badges de types du Pokémon et des capacités en taille ×2 ; le
    type de chaque capacité est sous son nom.

### 4.36 Finitions PC + éditeur refait dans la même DA (2026-10-02)

- **Texte gras qui débordait** (niveau « Nv. 100 » hors de la fiche, texte des badges de type hors de
  leur cadre) : la police du modpack dessine le gras plus large que ce que Minecraft mesure. Niveau et
  badges passent en non-gras, et la mesure de tout texte gras garde une marge d'un pixel de police par
  caractère.
- **Modèles de la grille PC ×2.**
- **Éditeur (`PhantasmonPcEditScreen`) reconstruit sur `PhantasmonCanvasScreen`** :
  - à gauche, **la même fiche Pokémon que le PC, en aperçu en direct** (recalculée à chaque image depuis
    le formulaire : types, nature colorée, capacités et leurs types, IV/EV, P. cachée) ;
  - à droite, le formulaire en grandes cases : Surnom, Niveau, Chromatique (clic = bascule), Talent,
    Objet tenu, Nature (avec +/- colorés), Téracristal (« Type d'origine » = aucun type forcé), tableau
    IV / EV (un champ par stat, libellés colorés selon la nature, total EV en direct en rouge au-delà de
    510, Puissance Cachée recalculée en direct) et les 4 capacités (nom + badge de type) ;
  - listes déroulantes dans le même style (texte ×2, icônes d'objets, badges de types, recherche par
    mots pour objets et capacités, barre de défilement), dessinées au-dessus de tout ;
  - en-tête : IMPORTER (export Showdown du presse-papiers → remplit le formulaire, rien n'est enregistré)
    et indicateur « ● Modifications non enregistrées » ; pied de page : aide/erreurs + ANNULER /
    ENREGISTRER (vert quand il y a des modifications) ; au retour dans le PC, « Modifications
    enregistrées. » ;
  - clavier/souris : Tab / Maj+Tab entre les champs, Entrée = enregistrer, Ctrl+V, Ctrl+Retour arrière,
    flèches ↑↓ ou molette sur un nombre = ±1 (Maj : ±10), Échap ferme la liste / le champ / l'éditeur,
    avec **confirmation « Quitter sans enregistrer ? »** s'il y a des modifications ;
  - plus aucun widget vanilla (ils ne peuvent pas s'afficher dans le canevas mis à l'échelle) : champs et
    listes sont dessinés par l'écran lui-même. Règles métier inchangées (talents de l'espèce seulement,
    objets de combat seulement, pas de capacité en double, un seul PATCH à l'enregistrement).
- Build vert. **Non testé visuellement par Claude.**

### 4.37 Bouton ✕ du PC, modèles 3D des formes (2026-10-03)

- **Bouton ✕ rouge** en haut à droite du PC (bout de la plaque d'en-tête droite) pour le fermer.
- **Formes alternatives rendues avec le modèle de base** (Arceus Fée affiché en Arceus Normal, Ogerpon
  sans son masque, Motisma…) : Cobblemon choisit le modèle d'une forme via ses **aspects** (`fairy-plate`,
  `wellspring-mask`…), pas via son nom. On envoyait le nom de forme brut (`fairy`) — inconnu, donc forme
  de base. Corrigé dans `PokemonGuiRendering.resolveForm/formAspects` (correspondance tolérante nom /
  identifiant Showdown, ex. `wellspringtera` ↔ `Wellspring-Tera`), utilisé par les écrans (slots, fiche,
  types de la forme) **et** par le Ghost en jeu (`setForm` + aspects forcés). L'ancien
  `species.getFormByName` du Ghost renvoyait silencieusement la forme de base pour un nom inconnu.
- Build vert. **Non testé visuellement par Claude.**

### 4.38 Phase 9 — prototype du moteur de combat local (2026-10-03)

**Choix d'Adrien** : interface de combat **native de Cobblemon** ; équipe active telle quelle (niveaux
stockés, règles Gen 9 de Cobblemon) ; lancement comme l'échange (commande + touche + [Accepter] dans le
chat) ; chrono **désactivé par défaut**, activable par l'un ou l'autre joueur pour les deux, puis plus
désactivable (comme Showdown) — 90 s, puis action par défaut automatique.

**Architecture retenue (CAD Partie 2 §9.1)** : le client hôte fait tourner **la pile de combat serveur de
Cobblemon elle-même** (`PokemonBattle` + Showdown via GraalJS + son interpréteur), sans rien réécrire :
- `battle/BattleThread` : tout le combat s'exécute sur un seul thread — le thread du serveur intégré s'il
  existe (solo / hôte LAN : Showdown y est déjà démarré et `BattleRegistry` déjà « tické »), sinon un thread
  privé qui démarre Showdown une fois (données d'espèces synchronisées + scripts JS du jar Cobblemon) et
  tick `BattleRegistry` toutes les 50 ms ;
- `battle/GhostBattlePokemonFactory` : un `Pokemon` Cobblemon jetable construit depuis les données du Ghost
  (forme et aspects, niveau, nature, talent, IV/EV, capacités, objet, sexe, Téra, surnom) — l'état de combat
  vit sur cette copie, le Ghost n'est jamais modifié (CAD Partie 1 §25) ;
- `battle/GhostBattleActor` : un joueur du combat ; son uuid = uuid Mojang (c'est ainsi que l'UI Cobblemon
  reconnaît « mon camp »), mais sans « joueur serveur », pour que le moteur n'envoie jamais rien par le
  serveur Minecraft ; tous ses paquets vont à un « puits » : l'UI Cobblemon locale ou (étape suivante) le
  relais backend vers l'autre joueur ;
- `battle/CobblemonPackets` : remet un paquet au gestionnaire client enregistré par Cobblemon pour ce
  paquet, comme s'il venait du serveur ; encode/décode avec le codec Cobblemon pour le relais ;
- `mixin/ClientCommonPacketListenerImplMixin` (premier Mixin du mod, sur une méthode vanilla ; un second,
  `DistributionUtilsMixin`, est arrivé au §4.39) :
  intercepte `BattleSelectActionsPacket` (le seul paquet que l'UI de combat renvoie : capacité, changement,
  abandon) pour un combat Ghost et le donne au moteur au lieu du serveur.

**Prototype à tester** : `/phantasmon debug battle` — ton équipe active contre une copie pilotée par l'IA
aléatoire de Cobblemon, entièrement en local, avec l'interface de combat Cobblemon. Chaque paquet passe par
un aller-retour encodage/décodage avant d'arriver à l'UI (valide d'avance le relais vers l'autre joueur).
À tester dans les deux cas : **monde solo** (chemin « serveur intégré ») et **en invité** sur une partie
LAN ou un serveur (chemin « client pur » : le premier lancement démarre Showdown, quelques secondes).
Commande temporaire, retirée quand les vrais combats seront livrés.

**Retours de test du prototype (2026-10-03)** — trois correctifs, puis combat complet validé par Adrien
(attaques, changements, K.O., objets activés, dégâts cohérents, fin de combat, aucun bug vu) :
1. l'écran ne s'ouvrait pas : Cobblemon n'envoie `BattleInitializePacket` qu'à sa classe `PlayerBattleActor`
   (finale) — `GhostBattleActor` l'envoie lui-même juste avant l'équipe ;
2. aucun Pokémon actif : au départ Cobblemon ne remplit les emplacements actifs qu'à travers l'entité qu'il
   fait sortir dans le monde — `placeStartingPokemon` les remplit depuis la requête Showdown, ou à défaut avec
   les premiers de l'équipe (ordre Showdown sans aperçu d'équipe) ;
3. côté client pur, les délais d'animation tournent sur les `ServerTaskTracker` de Cobblemon — avancés par
   `BattleThread`.

**Mise en scène (demande d'Adrien)** : `battle/BattleVisuals` fait apparaître le Pokémon actif de chaque camp
devant son dresseur (entité client-only, orientée vers l'adversaire, à 3 blocs max sur la ligne entre les
deux joueurs), le remplace lors d'un changement, le retire au K.O. et en fin de combat. Piloté uniquement par
les paquets de combat reçus par ce client : l'hôte et l'autre joueur construisent la même scène, sans trafic
réseau en plus.

**Animations de sortie / rappel identiques à Cobblemon (2026-10-03)** : le rendu Cobblemon dessine ces effets
côté client à partir de données d'entité synchronisées (`BEAM_MODE` 1 = sortie, 3 = rappel ;
`PHASING_TARGET_ID` = dresseur d'où part / où revient la balle ; `SPAWN_DIRECTION`). `BattleVisuals` pose ces
mêmes valeurs sur nos entités locales avec les durées exactes de Cobblemon (lancer 0,5 s, sortie 1,5 s) : geste
du dresseur + son de lancer, rayon de sortie, puis cri et éclat chromatique (paquets Cobblemon remis à ses
propres gestionnaires) ; au changement et au K.O., rayon de rappel vers le dresseur puis sortie du suivant
une fois le rappel fini (ordre de Cobblemon pour un dresseur). Ordre décidé avec Adrien : animations
d'attaques en étape dédiée **après** le combat à deux joueurs (il faut réécrire côté client le lecteur de
chorégraphies d'attaque de Cobblemon, qui ne tourne que sur serveur).

- Build vert. **Non testé par Claude** (aucun jeu ici).

### 4.39 Phase 9 — combat à deux joueurs (2026-10-03)

Le moteur validé au §4.38 est maintenant branché sur le backend (voir la section « Combat en direct » de
`PHANTASMON_API_REFERENCE.md`).

**Côté joueur :**
- `/phantasmon battle invite <joueur>` (suggestions = joueurs du serveur), ou la touche **B** en visant un
  joueur (rebindable, « Combattre le joueur visé »).
- Le joueur invité reçoit [Accepter]/[Refuser] dans le chat → `/phantasmon battle join` / `decline`.
- Au démarrage, un bouton [Activer le chrono] dans le chat → `/phantasmon battle timer`. Une fois activé
  par l'un des deux, il l'est pour les deux jusqu'à la fin du combat (90 s par tour, compte à rebours dans
  la barre d'action, rouge sous 10 s). À expiration, une action automatique est jouée (IA aléatoire de
  Cobblemon, comme le joueur IA du prototype).
- L'interface est celle de Cobblemon, avec les mêmes animations de lancer/rappel qu'au §4.38.

**Architecture (`client/battle/LiveBattleController`) :**
- Le backend désigne l'**hôte** (alternance entre les deux joueurs). L'hôte lance
  `GhostBattles.startHostedBattle` avec les deux équipes reçues dans `BattleSessionStarted`.
- L'acteur de l'hôte alimente son UI Cobblemon locale. L'acteur de l'invité a pour « sink » un encodeur :
  chaque paquet Cobblemon part en `BattlePacket` (id + payload base64, codec Cobblemon via
  `CobblemonPackets.encode`).
- L'invité décode ces paquets et les joue dans sa propre UI Cobblemon (`CobblemonPackets.dispatchLocally`),
  ce qui déclenche aussi les mêmes visuels (`BattleVisuals`). Ses choix (`BattleSelectActionsPacket`,
  interceptés par le mixin) repartent en `BattleChoice` ; l'hôte les applique via `GhostBattles.applyChoice`.
- Chrono : l'hôte applique les délais pour les deux joueurs (`GhostBattles.whenMustChoose` +
  `forceAutomaticChoice`), avec 3 s de marge réseau pour l'invité. Chaque client affiche son propre
  compte à rebours : `CobblemonPackets.addDeliveryListener` détecte les demandes d'action et
  `GhostBattles.setLocalChoiceListener` détecte le choix envoyé.
- Fin : l'hôte envoie `BattleResult` (vainqueur ou nul) ; quitter = abandon (`BattleLeave`) ; une
  déconnexion annule le combat sans vainqueur. Les messages `Battle*` passent par le WebSocket de
  `GhostSession`, qui les transmet sur le thread client (`LiveBattleListener`).
- Le backend accepte des messages WebSocket jusqu'à 1 Mio (les paquets d'équipe Cobblemon dépassent la
  limite de 8 Kio par défaut).

**Retours du premier test à deux comptes (2026-10-03)**, partie LAN ouverte par MystAria_ (serveur
intégré), TheMashen connecté en client pur :
1. *Combat 1, MystAria_ hôte* : le combat se déroule, mais TheMashen (invité) ne voit aucun Pokémon sorti.
   Log : `Unknown MovesetBuilder id: cobblemon:wild` dans `BattleVisuals.sendOut`.
   `PokemonProperties.create()` initialise un moveset par défaut via les « moveset builders » de
   Cobblemon, un registre de datapack **serveur**, vide chez un client pur. Correctif :
   `new Pokemon()` + `properties.apply(pokemon)` (seulement les propriétés visuelles, comme les Ghosts).
2. *Combat 2, TheMashen hôte* : Showdown démarre et le combat est créé, puis plus rien des deux côtés.
   Cause : Cobblemon passe chaque message Showdown par `runOnServer` (`ShowdownInterpreter.interpretMessage`),
   qui échoue **en silence** sans `MinecraftServer`. Le chemin « client pur » n'avait jamais été testé.
   Correctif : `mixin/DistributionUtilsMixin` intercepte `DistributionUtilsKt.runOnServer`, mais
   **uniquement** quand l'appel vient de notre thread `phantasmon-battle`, et y remet le bloc en file
   (équivalent de `server.execute`). Les autres appels gardent le comportement de Cobblemon.
3. « Aucun combat en cours » affiché au vainqueur juste après « Victoire ! » : les derniers paquets du
   moteur arrivaient au backend après la fin du combat. `ERROR_BATTLE_NOT_IN_BATTLE` venant du backend est
   maintenant ignoré : les commandes vérifient déjà localement qu'un combat est en cours.

Bruit connu dans le log de l'hôte : `NoSuchElementException: List is empty` dans
`EntityParticlesActionEffectKeyframe`. Les animations d'attaque côté serveur cherchent des entités qui
n'existent pas. C'est sans effet sur le combat, et ce sera traité par l'étape des animations d'attaque.

`/phantasmon debug battle` (combat contre l'IA, §4.38) a été retiré le 2026-10-03, après validation des
animations d'attaque par Adrien (« c'est vraiment propre »). `GhostAiBattleActor` et l'aller-retour
encode/decode de test ont été supprimés avec lui. `/phantasmon debug fingerprint` reste.

### 4.40 Phase 9 — animations d'attaque identiques à Cobblemon (2026-10-03)

Adrien a validé les deux sens du combat à deux joueurs (hôte LAN et hôte client pur). Étape suivante :
les animations d'attaque.

**Comment Cobblemon fait :** chaque attaque (et boost, statut, dégâts de statut...) a une « action
effect » : une timeline JSON (`data/<ns>/action_effects/**.json`, ~150 fichiers dans Cobblemon) de
keyframes `animation`, `entity_particles`, `entity_sound`, `entity_molang`, `pause`, `add_holds`/
`remove_holds`, `sequence`, `parallel`... Le **serveur** la joue contre les entités serveur des Pokémon
et envoie des paquets aux joueurs proches. Un combat Ghost n'a pas d'entités serveur : chaque client a
les siennes (`BattleVisuals`). C'est la cause des erreurs `List is empty` vues dans le log de l'hôte.

**Ce qu'on fait (`client/battle/`) :**
- `mixin/ActionEffectTimelineMixin` : sur l'hôte, pour un combat Ghost hébergé ici
  (`GhostBattles.effectRoute`), `ActionEffectTimeline.run` ne joue plus côté serveur et confie l'effet
  à `GhostActionEffects.intercept`. Un combat Cobblemon normal sur le serveur intégré n'est pas touché.
- `mixin/ActionEffectInstructionsMixin` : les 7 instructions qui lancent un effet (Move, Damage, Boost,
  Activate, Cant, Prepare, Start) font toutes `this.future = effect.run(context)`. L'injection sur
  `setFuture` relie l'effet à son instruction : qui attaque, qui est visé, cibles ratées/touchées, nombre
  de coups.
- `ActionEffectEvent` : id de la timeline + positions Showdown (`p1a`, `p2a`) + ces infos. Il est relayé
  à l'invité dans un `BattlePacket` d'id `phantasmon:action_effect`, sur le même canal que les paquets
  Cobblemon, donc dans le bon ordre.
- `ActionEffectPlayer` : un lecteur de timeline côté client, qui joue les mêmes fichiers JSON (parsés par
  Cobblemon) avec la même sémantique et les mêmes requêtes MoLang (`q.move.*`, `q.missed(q.entity.uuid)`,
  `q.entity.is_user`...). Au lieu d'envoyer des paquets aux joueurs, il les joue directement dans le
  client contre les entités de `BattleVisuals`. `move_to_target`/`return_to_position` deviennent un
  glissement en ligne droite, puisque les entités client n'ont pas d'IA.
- Rythme du combat : comme dans Cobblemon, le moteur attend le hold `effects` (les PV ne bougent qu'à
  l'impact) et la fin de l'effet (l'attaque suivante attend l'animation précédente). Ces deux signaux
  suivent maintenant la lecture **sur le client hôte**, recopiée dans le contexte du moteur. Une sécurité
  libère le combat au bout de 20 s si une lecture ne se termine jamais.
- `ClientActionEffects` : les action effects sont des données de datapack serveur, jamais synchronisées.
  Sur un client pur, le registre est rempli depuis les jars des mods (Cobblemon et addons), avec le parser
  de Cobblemon et les mêmes ids (namespace + nom de fichier). C'est nécessaire au lecteur, mais aussi au
  moteur de l'hôte : `BoostInstruction` déréférence `boost` avec `!!`, ce qui aurait planté au premier boost
  dans un combat hébergé par un client pur.

**Retour du premier test (2026-10-03)** : boosts, statuts et rythme OK, mais aucune animation d'attaque.
`MoveInstruction` (classe Kotlin finale) écrit `future = effect.run(context)` directement dans son champ,
sans passer par `setFuture`. L'effet n'était donc jamais relié à son instruction et se jouait sans
attaquant ni cible. `mixin/MoveInstructionMixin` redirige cette écriture (le `PUTFIELD` dans
`invoke$lambda$1`, nom propre à Cobblemon 1.8.1) vers `setFuture`. Un effet non relié laisse désormais un
`WARN` dans le log (« was not claimed by any instruction »).

Limite connue : les datapacks du monde (pas ceux des mods) ne sont pas lus sur un client pur. Chez un hôte
LAN, le vrai registre du serveur intégré est utilisé.

---

### 4.41 Ghost : suivi fluide du propriétaire (2026-10-03)

Remplace la téléportation sur la position du joueur toutes les secondes (niveau « simple » choisi par
Adrien ; la balade aléatoire et le vrai pathfinding Cobblemon restent des pistes non faites).

- `GhostEntityManager.tick()` (appelé à chaque tick client depuis `GhostSession.onClientTick()`) déplace
  chaque Ghost vers un point **derrière et à droite** de son propriétaire (1,5 bloc derrière, 1,2 de côté).
- **Position du propriétaire** : lue sur son entité `Player` côté client (`level.getPlayerByUUID`) quand elle
  est chargée — position et orientation en direct, sans latence réseau, aussi pour les autres joueurs. Sinon
  repli sur la dernière position relayée par le backend (`GhostEntityMove`, ~1 s).
- **Mouvement** : vitesse adoucie (0,06 à 0,40 bloc/tick selon la distance), `Entity.move` avec collisions,
  gravité maison, saut d'un bloc quand il est bloqué, hystérésis (repart à 1,3 bloc, s'arrête à 0,4) pour
  éviter le tremblement. Se téléporte si > 20 blocs, ou s'il reste bloqué ~3 s à plus de 3 blocs.
- **Orientation** : regarde sa direction de marche, ou son propriétaire à l'arrêt (rotation limitée à
  25°/tick).
- **Animations** : l'entité n'a pas de delegate serveur Cobblemon, donc les drapeaux synchronisés
  `PokemonEntity.MOVING` et `POSE_TYPE` sont posés à la main (WALK/STAND ; FLY/HOVER pour les espèces qui
  `getCanFly()`, qui flottent 1,2 bloc au-dessus de leur point).
- Non testé par Claude (aucun écran). À vérifier en jeu : marche fluide, bonne animation de marche, saut
  des marches, comportement des volants (ex. Rayquaza), Ghost de l'autre joueur, pas de décalage visible à
  la sortie.

---

### 4.42 Ghost : roaming, caméra sans effet, animations de sortie/rappel, formes et shiny (2026-10-03)

Suite au premier test du suivi fluide (§4.41), tout dans `GhostEntityManager`.

- **Caméra** : le point de suivi n'est plus calculé sur l'orientation de la caméra du joueur mais sur son
  **cap de déplacement** (déduit de sa position), figé quand il s'arrête. Tourner la caméra sur place ne
  bouge donc plus le Ghost. « Le joueur ne bouge pas » = déplacement horizontal < 0,02 bloc/tick et
  vertical < 0,08 ; seule la position compte.
- **Roaming** : après 5 s (100 ticks) sans mouvement du propriétaire, le Ghost se promène : de temps en
  temps (pause de 3 à 10 s, 1 à 4 s la première fois) il choisit un point au hasard dans un cercle de
  **10 blocs** autour du propriétaire, y va au pas (0,12 bloc/tick, animation de marche), puis s'arrête et
  regarde son propriétaire. Dès que le propriétaire rebouge, il revient à son point de suivi. Un obstacle
  qui le bloque pendant ~3 s abandonne simplement la destination de balade (pas de téléportation).
- **Sortie / rappel** : mêmes animations que le combat — lancer de la Poké Ball (le propriétaire fait le
  geste), faisceau de sortie, cri, anneau chromatique ; faisceau de rappel vers le propriétaire. Mêmes
  mécanismes et durées que `BattleVisuals` (`BEAM_MODE` 1/3, `PHASING_TARGET_ID`, 0,5 s / 1,5 s), avec un
  minuteur propre au Ghost. Le Ghost reste immobile pendant le faisceau de sortie. Rappel instantané si le
  propriétaire n'est pas chargé chez nous, ou à la déconnexion / changement de dimension. Le rattrapage à
  l'arrivée dans un groupe rejoue la sortie des Ghost déjà dehors (même message `GhostEntitySpawn`).
- **Bug formes spéciales (Arceus Fée, Rotom Lavage, Ogerpon Fontaine…) et shiny** : le moteur de rendu lit
  les `ASPECTS` **synchronisés** de l'entité, que le delegate serveur de Cobblemon remplit normalement ;
  une entité côté client seule n'en a pas, d'où le modèle de base non chromatique. Les aspects (ceux de la
  forme + `shiny`) sont maintenant écrits à la main dans la donnée synchronisée (`PokemonEntity.ASPECTS`).
  Au passage, `setShiny` n'est plus appelé *après* le forçage des aspects de la forme (il retombait sur
  les aspects forcés, sans `shiny`).
- Non testé par Claude. À vérifier : les formes et le shiny sur les Ghost sortis (les cas cités), la
  balade, l'immobilité à la rotation de caméra, les animations de sortie/rappel (pour soi et pour l'autre
  joueur).
- **Retour de test (2026-10-03)** : tout est correct, sauf que les gros Pokémon (Arceus, Rayquaza…)
  **poussaient le joueur** en le suivant (OK pour les petits/moyens : Ogerpon, Lucario, Aegislash,
  Mimikyu…). Cause : poussée entre entités (`Entity.push`) quand les hitbox se chevauchent, dans les deux
  sens. Solution sans toucher aux hitbox : le Ghost est en **`noPhysics = true`** en permanence —
  `Entity.push` ne fait rien si l'un des deux a `noPhysics`, donc il ne pousse ni n'est poussé. Comme
  `noPhysics` désactive aussi les collisions avec les blocs dans `Entity.move`, il n'est remis à `false`
  que le temps de **notre propre appel** `entity.move(...)` dans `tickGhost` (même tick client, rien ne
  peut le pousser pendant ce temps), puis à `true`. Pour l'aspect visuel, le point de suivi est écarté en
  fonction de la largeur de la hitbox (`followScaleFor` : demi-largeur du Ghost + 0,3 du joueur + 0,5 de
  marge ; les petits/moyens gardent le point d'origine), pour qu'un gros Ghost au repos ne se tienne pas
  dans son propriétaire. Conséquence assumée : le Ghost traverse le joueur s'il marche dessus.
- Non testé par Claude : à vérifier avec Arceus/Rayquaza en marchant, en sprintant et en faisant demi-tour.
- **Confirmé en jeu par Adrien** : plus de poussée, comportement parfait.

---

### 4.43 Éditeur : champ sexe (2026-10-03)

Dans `PhantasmonPcEditScreen`, ligne 1 : le surnom passe à 320 px de large et une case **SEXE** occupe la
place libérée jusqu'au bord de la colonne (la case Chromatique et le niveau ne bougent pas).

- **Valeurs possibles dictées par l'espèce** (même règle que `Look.of` : ratio de la forme si elle en a un,
  sinon celui de l'espèce, via `PokemonGender.resolve`) :
  - espèce **mixte** (ex. Pikachu) : la case est cliquable et fait tourner **Aléatoire → ♂ Mâle → ♀
    Femelle → Aléatoire** ;
  - espèce **à sexe fixe** (100 % mâle, 100 % femelle) ou **asexuée** : valeur affichée en grisé, case non
    cliquable, et la sauvegarde ne touche pas à `data.gender`.
- **Stockage** : `data.gender` = `"M"` ou `"F"` (le format que l'import Showdown écrit déjà et que
  `GhostBattlePokemonFactory` lit pour le combat) ; « Aléatoire » supprime la clé, comme Showdown quand le
  sexe n'est pas précisé. Aucun changement backend (`data` est un JSONB libre).
- L'import Showdown depuis l'éditeur remplit aussi le sexe (si l'espèce le permet) ; l'aperçu de la carte à
  gauche affiche le ♂/♀ en direct.
- **Limite connue** : le sexe n'influence pas encore le modèle 3D (Meowstic, Pikachu femelle…) ni côté PC
  ni côté Ghost sorti — le message `GhostEntitySpawn` ne transporte pas le sexe, seul le combat l'utilise.
- Non testé par Claude : à vérifier en jeu (espèce mixte, sexe fixe, asexuée, import Showdown avec `(M)`/`(F)`).
- **Confirmé en jeu par Adrien** : le champ sexe fonctionne.

---

### 4.44 Le sexe change le modèle 3D (2026-10-03)

Les modèles qui diffèrent selon le sexe (Meowstic, Pikachu…) lisent l'aspect Cobblemon `male` / `female` /
`genderless`. Les rendus qui passent leurs aspects à la main (GUI, entité Ghost) n'ont pas le fournisseur
d'aspects de Cobblemon : l'aspect est donc ajouté explicitement.

- `PokemonGuiRendering.genderAspect(species, form, storedGender)` : le sexe stocké (`data.gender`), sinon ce
  que le ratio de l'espèce impose (mâle seul, femelle seule, asexué) ; `null` pour une espèce mixte sans sexe
  stocké (modèle de base). Utilisé par `renderModel` (cartes PC / échange / éditeur, slot glissé) et par
  `GhostEntityManager.spawn` (aspects forcés de la forme + aspects synchronisés de l'entité + `setGender`).
- **Backend** : `GhostEntitySpawn` transporte maintenant `"gender": "M" | "F" | null` (`data.gender` tel que
  stocké). Test d'intégration ajouté en premier (`ghostSpawnCarriesTheStoredGenderSoTheModelCanBeGenderSpecific`,
  vu en échec puis au vert), suite complète au vert, `PHANTASMON_API_REFERENCE.md` à jour. **Le backend doit
  être redémarré** pour que le Ghost d'un autre joueur reçoive le sexe ; sans cela le client retombe
  simplement sur le ratio de l'espèce (aucune erreur).
- Le combat n'est pas concerné : il utilisait déjà le sexe (`GhostBattlePokemonFactory`).
- Non testé par Claude : à vérifier avec un Meowstic ♂ puis ♀ (carte du PC et Ghost sorti, chez soi et chez
  l'autre joueur).

---

## 5. Dépannage courant

| Symptôme | Cause probable |
|---|---|
| `Dependency requires at least JVM runtime version 25` | JDK utilisé pour Gradle < 25 — voir §1.1 |
| Le mod n'apparaît pas dans le jeu | Mauvais dossier `mods/`, Fabric API manquante/incompatible, version Minecraft ≠ 1.21.1 |
| Toujours `Backend injoignable` dans le chat | Backend non lancé, mauvais port, pare-feu local |
| Crash au lancement mentionnant un mixin | Mixin `phantasmon.client.mixins.json` devenu incompatible après une mise à jour de Cobblemon (cible `DistributionUtilsKt.runOnServer`) ou de Minecraft — signaler avec le log |
| `/phantasmon login` répond "comptes hors-ligne non supportés" | Compte de lancement en mode hors-ligne/cracké (`User.Type.LEGACY`) — utiliser un vrai compte Microsoft |
| `/phantasmon login` échoue à la vérification Mojang | Jeu lancé hors mode premium, ou API Mojang temporairement indisponible |
| Crash au lancement mentionnant `cobblemon`/Kotlin | Version de Cobblemon absente/incompatible (doit être 1.8.1 pour Fabric 1.21.1) |
| `UUID non valide à la position N` sur `delete`/`clone`/`edit`/`sendout` | UUID incomplet/tronqué tapé à la main — il faut l'UUID entier (36 caractères), affiché en clair par `/phantasmon pokemon list`/`pc` depuis le correctif du 2026-09-26 |
| `/phantasmon trade invite` : « Ce joueur n'est pas connecté à Phantasmon » | L'autre joueur n'a pas (encore) de session WebSocket : il n'est pas connecté au backend (auto-login raté, `/phantasmon login`), ou les deux clients ne pointent pas vers le même backend |
| L'écran d'échange ne s'ouvre pas après [Accepter] | Invitation expirée (60 s) ou l'inviteur a quitté/est déjà en échange — le message d'erreur est dans le chat ; relancer l'invitation |
| `/phantasmon battle invite` : « Ce joueur n'est pas connecté à Phantasmon » | Même cause que pour l'échange : l'autre joueur n'a pas de session WebSocket au backend |
| Le combat ne démarre pas chez l'invité | Regarder les logs de l'hôte pour `Cannot` / `engine` (moteur Showdown non démarré) ; le message « Le moteur de combat n'a pas pu démarrer » s'affiche alors dans le chat |
| Le Ghost d'un autre joueur n'apparaît jamais | Vérifier les logs client pour `Cannot render Ghost: unresolved species` (espèce/forme non reconnue par Cobblemon côté receveur) ; sinon vérifier que les deux joueurs sont bien dans la même dimension et que le backend tourne |
