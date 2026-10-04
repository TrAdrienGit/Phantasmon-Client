# Architecture du client

> Vérifié contre le code le 2026-10-03. Vue d'ensemble du système : [`system-overview.md`](system-overview.md).
> Sous-systèmes détaillés : [`ghost-entities.md`](ghost-entities.md), [`gui-design-system.md`](gui-design-system.md),
> [`live-trade.md`](live-trade.md), [`battle-engine.md`](battle-engine.md), [`mixins.md`](mixins.md),
> [`showdown-import.md`](showdown-import.md).

## 1. Pile technique

| Élément | Valeur | Source |
|---|---|---|
| Minecraft | 1.21.1, **mappings Mojang officiels** (`Minecraft`, `Component`, `GuiGraphics`… pas les noms Yarn) | `gradle.properties`, `build.gradle` |
| Fabric Loader | ≥ 0.18.1 (plancher bas volontaire, pour les modpacks) | `fabric.mod.json` |
| Fabric API | 0.116.17+1.21.1 | `gradle.properties` |
| Cobblemon | 1.8.1 Fabric, résolu par l'**identifiant de version Modrinth** `gBW3vLC7` | `gradle.properties` |
| Kotlin stdlib | 2.0.21 en `compileOnly` (nécessaire pour compiler contre l'API Kotlin de Cobblemon) | `build.gradle` |
| Langage | Java 21 (bytecode) ; Gradle / Loom 1.18 exige un **JDK 25** pour s'exécuter | `build.gradle` |
| Environnement | `"environment": "client"` : le mod ne se charge jamais sur un serveur | `fabric.mod.json` |
| Tests | JUnit 5 (logique pure uniquement) | `build.gradle` |

Le projet utilise `splitEnvironmentSourceSets()` : presque tout le code est dans `src/client/`. `src/main/`
ne contient que l'initialiseur commun (`Phantasmon`, journalisation), les ressources (`fabric.mod.json`, langues,
textures) et une configuration Mixin vide.

## 2. Paquets

Racine : `com.mystaria.phantasmon.client`.

| Paquet | Responsabilité | Classes principales |
|---|---|---|
| (racine) | Point d'entrée, câblage des événements, touches | `PhantasmonClient`, `PhantasmonKeybinds` |
| `auth` | Handshake de version, preuve Mojang, JWT en mémoire, renouvellement | `AuthService`, `AuthSession`, `SessionRefreshScheduler` |
| `network` | Client REST JSON, client WebSocket, DTO communs, traduction des codes d'erreur, ping de santé | `BackendConfig`, `BackendJsonClient`, `PhantasmonWebSocketClient`, `BackendErrorMessages`, `BackendHealthPinger`, `PingToggle` |
| `command` | Arbre Brigadier `/phantasmon …` | `PhantasmonCommands` |
| `pokemon` | REST Pokémon, logique des commandes, natures, Puissance Cachée, sexe, objets de combat | `PokemonClient`, `PokemonCommandHandler`, `NatureModifiers`, `HiddenPowerCalculator`, `PokemonGender`, `CobblemonHeldItems` |
| `pokemon.showdown` | Analyse du format Showdown et conversion en identifiants Cobblemon ; export Showdown (noms anglais) | `ShowdownParser`, `ShowdownImportMapper`, `CobblemonIdentifiers` |
| `ghost` | Session WebSocket de présence, entités Ghost locales | `GhostSession`, `GhostEntityManager` |
| `gui` | Écrans PC, éditeur, échange et leur socle commun | `PhantasmonCanvasScreen`, `PhantasmonPcScreen`, `PhantasmonPcEditScreen`, `PhantasmonTradeScreen`, `PokemonGuiRendering` |
| `trade` | Échange en direct (état + contrôleur) et échange asynchrone (commandes) | `LiveTradeController`, `LiveTradeState`, `TradeCommandHandler`, `TradeClient` |
| `battle` | Combat Ghost : moteur sur l'hôte, relais, visuels, animations d'attaque | `LiveBattleController`, `GhostBattles`, `BattleThread`, `GhostBattleActor`, `GhostBattlePokemonFactory`, `CobblemonPackets`, `BattleVisuals`, `GhostActionEffects`, `ActionEffectPlayer`, `ClientActionEffects` |
| `wheel` | Entrées Phantasmon dans la roue d'interaction Cobblemon | `GhostWheelOptions` |
| `mixin` | 7 Mixins (voir [`mixins.md`](mixins.md)) | |
| `version` | Comparaison de versions (logique pure) | `VersionCompatibility` |

Convention : la logique métier vit dans des classes « handler » ou « controller », séparées du câblage Brigadier
ou de l'écran (`PokemonCommandHandler`, `LiveTradeController`…). Les DTO sont des `record` Java en camelCase.

## 3. Cycle de vie

`PhantasmonClient.onInitializeClient()` construit tous les services une fois, puis :

| Événement Fabric | Action |
|---|---|
| Initialisation | `AuthService.setOnAuthenticated(GhostSession::start)` ; enregistrement des écouteurs WebSocket (échanges asynchrones, échange en direct, combat) ; roue Cobblemon ; touches ; commandes |
| `ClientPlayConnectionEvents.JOIN` (entrée dans un monde) | Ping de santé si activé ; démarrage du renouvellement de jeton ; `autoLoginIfBackendHealthy()` (un `GET /health`, connexion seulement si `UP`/`UP`, silencieux sinon) |
| Connexion réussie | `GhostSession.start()` : ouverture du WebSocket, puis `JoinServerGroup` |
| `END_CLIENT_TICK` (20 fois par seconde) | `GhostSession.onClientTick()` (détection dimension/mort, déplacement des Ghost), ouverture différée des écrans, touches |
| `ClientPlayConnectionEvents.DISCONNECT` | Arrêt du ping et du renouvellement, fermeture du WebSocket, nettoyage des visuels de combat, effacement du JWT |

## 4. Fils d'exécution

| Fil | Utilisé pour |
|---|---|
| Thread client (rendu) | Toute manipulation de Minecraft : écrans, entités, chat. Les callbacks réseau y reviennent par `Minecraft.execute(...)`. |
| `HttpClient` asynchrone (JDK) | Appels REST et WebSocket (`CompletableFuture`) |
| Planificateurs dédiés | `GhostSession` (position + heartbeat chaque seconde), `SessionRefreshScheduler` (60 s), `BackendHealthPinger` (30 s) |
| Thread de combat | Moteur Cobblemon de l'hôte : thread du serveur intégré s'il existe, sinon thread privé `phantasmon-battle` (voir [`battle-engine.md`](battle-engine.md)) |

## 5. Réseau

- **URL du backend** : `BackendConfig.BASE_URL`, lue au lancement dans `config/phantasmon.json` (`"backend_url"`) par
  `BackendUrlFile` (défaut `http://100.116.43.32:8080`, IP Tailscale de la machine de dev ; voir
  [`reference/configuration.md`](../reference/configuration.md)).
- **REST** : `BackendJsonClient` (Gson, `LOWER_CASE_WITH_UNDERSCORES`) : les DTO restent en camelCase. En-tête
  `Authorization: Bearer` ajouté pour les routes protégées. Une erreur structurée devient `BackendApiException`
  (dont le code est traduit, jamais affiché tel quel). `GsonUuidSanityTest` garantit qu'un `UUID` est sérialisé en
  chaîne.
- **WebSocket** : `PhantasmonWebSocketClient` (WebSocket du JDK, sans dépendance), enveloppe
  `{"type", "data"}`, reconstitution des messages fragmentés. **Une seule connexion**, détenue par `GhostSession`,
  qui répartit les messages vers les Ghost, les échanges et les combats.
- **Authentification** : `AuthService` → `GET /version` → `MinecraftSessionService.joinServer(UUID, accessToken,
  serverId)` → `POST /auth/session`. Les comptes hors-ligne (`User.Type.LEGACY`) sont refusés avant tout appel.
  Le JWT vit dans `AuthSession` (mémoire, jamais sur disque) ; renouvellement si l'expiration est à moins de 30 s.

## 6. Internationalisation

- Toutes les chaînes affichées sont des clés de `assets/phantasmon/lang/fr_fr.json` et `en_us.json` (~224 clés,
  préfixe `phantasmon.`). Aucune chaîne en dur.
- Les codes d'erreur du backend sont traduits par `BackendErrorMessages` (repli : `phantasmon.error.unknown`).
- Les noms Cobblemon (espèces, talents, natures, attaques, objets) sont traduits par Cobblemon lui-même ; attention,
  `AbilityTemplate.getDisplayName()` et `Nature.getDisplayName()` renvoient une **clé** à passer dans
  `Component.translatable(...)`, alors que `MoveTemplate.getDisplayName()` renvoie un composant déjà traduit.

Règles et inventaire : [`reference/translations.md`](../reference/translations.md).

## 7. Tests

`./gradlew test` couvre uniquement la logique pure, sans Minecraft : analyse et conversion Showdown, natures,
Puissance Cachée, sexe, compatibilité de version, état de l'échange en direct, sérialisation des UUID (40 tests).
Le rendu et les interactions avec le monde sont validés manuellement : [`guides/testing-and-qa.md`](../guides/testing-and-qa.md).

## 8. Pièges connus

| Piège | Règle |
|---|---|
| Une commande qui ouvre un écran pendant son exécution est aussitôt refermée par `ChatScreen` (`setScreen(null)` inconditionnel) | Poser un drapeau et ouvrir l'écran au tick suivant (`PokemonCommandHandler.tick()`, `LiveTradeController.tick()`) |
| API Minecraft/Cobblemon inconnue | Décompiler le jar réel (`javap`) avant d'écrire le code ; ne pas se fier à des tutoriels Yarn ou d'une autre version |
| Fonctions Kotlin à paramètres par défaut (`drawProfilePokemon`) : le pont `$default` est `@JvmSynthetic`, invisible pour `javac` | Appel par réflexion (`PokemonGuiRendering`) |
| Variable d'état réutilisée pour deux usages (bug `lastKnownDimension` / envoi de `JoinServerGroup`) | Un drapeau par usage (`joinedGroup`) |
| Drapeau « connecté » positionné avant la fin du handshake WebSocket | Ne le passer à vrai que dans `.thenRun(...)`, et gérer `.exceptionally(...)` |
| Police du modpack : le gras est plus large que `font.width()` | Éviter le gras pour le texte aligné à droite ou encadré |
| Record Java : une méthode statique ne peut pas porter le nom d'un composant | Nommer les fabriques différemment (`clearingTeamSlot` et non `clearTeamSlot`) |
