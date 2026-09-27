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

**Volontairement absent de cette V1** (à ajouter plus tard si besoin) : écran/bloc PC visuel, édition des
autres champs qu'un Pokémon (nickname/IVs/EVs/moves/objet tenu — le backend ne permet de toute façon
patcher que `level`/`teamSlot`/`data` en bloc pour l'instant), export Showdown.

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
