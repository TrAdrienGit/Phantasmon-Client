# 2. Le Java utile côté client

Le chapitre 2 du parcours backend couvre les bases de Java utilisées partout (records, annotations, `Optional`,
lambdas, streams, exceptions). Ce chapitre ajoute ce qui est propre au client.

## 2.1 Le thread de rendu est sacré

Minecraft fait presque tout sur **un seul thread**, le *thread client* (ou thread de rendu) : lire les entrées,
mettre à jour le monde local, dessiner l'image. Deux règles en découlent :

1. **Ne jamais bloquer ce thread.** Une requête réseau de 2 secondes sur ce thread = un jeu figé 2 secondes.
   Toute opération réseau est donc **asynchrone**, sur un autre thread.
2. **Ne jamais toucher au jeu depuis un autre thread.** Le monde, les entités, l'écran, le chat ne sont pas prévus
   pour un accès concurrent. Le résultat d'une opération réseau doit **revenir** sur le thread client avant d'agir.

L'outil pour revenir : `Minecraft.getInstance().execute(...)`, qui met une tâche en file pour le prochain passage
du thread client.

```java
// auth/AuthService.java
private static void reportComponent(Component component) {
    Minecraft.getInstance().execute(() -> {                 // exécuté plus tard, sur le thread client
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(component, false);
        }
    });
}
```

## 2.2 `CompletableFuture` : un résultat qui arrivera plus tard

Un `CompletableFuture<T>` représente une valeur **pas encore disponible**. On enchaîne ce qu'il faudra faire quand
elle arrivera, sans attendre :

```java
// auth/AuthService.java — la connexion, étape par étape, sans jamais bloquer
httpClient.get(BackendConfig.BASE_URL.resolve("/version"), VersionResponseDto.class)   // 1. requête lancée
        .thenComposeAsync(version -> handleVersion(version, user, client))             // 2. quand la réponse arrive
        .exceptionally(this::reportFailure);                                            // 3. si une étape échoue
```

| Méthode | Usage |
|---|---|
| `thenApply(f)` | Transformer le résultat (`T` → `U`) |
| `thenAccept(f)` | Consommer le résultat, sans rien produire |
| `thenRun(r)` | Faire quelque chose après, sans le résultat |
| `thenCompose(f)` | Enchaîner une **autre** opération asynchrone (`f` renvoie un `CompletableFuture`) |
| `exceptionally(f)` | Récupérer une erreur survenue à n'importe quelle étape précédente |
| `supplyAsync(s)` | Lancer un calcul sur un autre thread |
| `completedFuture(x)` | Un futur déjà terminé (pour sortir tôt d'une chaîne) |

Une erreur dans une étape « saute » les étapes suivantes jusqu'au premier `exceptionally`. Elle arrive souvent
enveloppée dans une `CompletionException` : on regarde `getCause()` pour l'erreur d'origine.

## 2.3 Les exécuteurs planifiés

Pour faire une action **à intervalle régulier** sans bloquer le jeu :

```java
// ghost/GhostSession.java
scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
    Thread thread = new Thread(runnable, "phantasmon-ghost-session");   // un nom : lisible dans les logs et le débogueur
    thread.setDaemon(true);                                             // ne retient pas la fermeture du jeu
    return thread;
});
scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);      // tick() chaque seconde
…
scheduler.shutdownNow();                                                // arrêt propre à la déconnexion
```

Un exécuteur créé doit toujours être arrêté, sinon il continue de tourner (et d'envoyer des messages) après la
déconnexion.

## 2.4 Partager des données entre threads

| Outil | Exemple | Garantie |
|---|---|---|
| `synchronized` sur les méthodes | `AuthSession` | Un seul thread à la fois lit/écrit le jeton |
| `volatile` | `GhostSession.joinedGroup`, `PhantasmonWebSocketClient.webSocket` | Une écriture faite par un thread est **visible** immédiatement par les autres (sans garantir l'atomicité d'opérations composées) |
| `CopyOnWriteArrayList` | `CobblemonPackets.DELIVERY_LISTENERS` | Liste lue souvent, modifiée rarement, sans verrou à la lecture |
| Copie locale avant usage | `WebSocket ws = webSocket; if (ws == null) return; ws.sendText(…)` | Évite qu'un autre thread remette le champ à `null` entre le test et l'usage |

## 2.5 La réflexion

La **réflexion** permet de manipuler une classe à l'exécution : lister ses méthodes, en appeler une par son nom,
accéder à un membre privé. Elle contourne les vérifications du compilateur : à n'utiliser que quand l'appel normal
est impossible. Phantasmon y recourt deux fois, pour des raisons expliquées au chapitre 11 :

```java
// battle/BattleThread.java — appeler une méthode Kotlin « internal » au nom modifié
for (Method method : PokemonSpecies.class.getDeclaredMethods()) {
    if (method.getName().startsWith("allShowdownSpecies") && method.getParameterCount() == 0) {
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(PokemonSpecies.INSTANCE);
    }
}
```

Bonne pratique : chercher la méthode **une seule fois** et la garder en cache (`PokemonGuiRendering` le fait), car
la recherche est lente.

## 2.6 Lire du Kotlin depuis Java

Cobblemon est en Kotlin, qui compile vers le même bytecode que Java. On l'appelle depuis Java, avec quelques
correspondances à connaître :

| En Kotlin | Vu depuis Java |
|---|---|
| `object PokemonSpecies` (singleton) | `PokemonSpecies.INSTANCE.getByName(...)` |
| Fonction de fichier `fun runOnServer()` dans `DistributionUtils.kt` | Méthode statique de la classe `DistributionUtilsKt` |
| `companion object { val MOVING }` | `PokemonEntity.Companion.getMOVING()` (ou statique si annoté) |
| Propriété `val name` | `getName()` |
| Lambda `() -> T` | `kotlin.jvm.functions.Function0<T>`, appelée par `invoke()` |
| `Unit` (rien) | `kotlin.Unit.INSTANCE` à renvoyer |
| Paramètres avec valeur par défaut | Méthode `nom$default` générée, souvent invisible depuis Java |
| Visibilité `internal` | Méthode publique dont le nom reçoit un suffixe `$<module>` : introuvable par son nom simple |

Pour compiler contre ces classes, la bibliothèque standard Kotlin doit être sur le classpath de compilation
(`compileOnly "org.jetbrains.kotlin:kotlin-stdlib:2.0.21"` dans `build.gradle`) ; à l'exécution elle est fournie par
`fabric-language-kotlin`, embarqué dans Cobblemon.

## 2.7 Gson

Côté client, la conversion JSON utilise **Gson** (déjà présent dans Minecraft). Même idée que Jackson côté backend :

```java
// network/BackendJsonClient.java
private static final Gson GSON = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)   // ownerUuid ↔ owner_uuid
        .create();
```

Les DTO sont des records Java en camelCase ; Gson s'occupe du snake_case. Comme Jackson, il ne renomme pas les clés
d'une `Map` : les clés du champ `data` des Pokémon sont donc écrites directement en snake_case (`held_item`,
`tera_type`).

## À retenir

- Thread client : ne jamais le bloquer, ne jamais toucher au jeu depuis un autre thread ; revenir avec
  `Minecraft.execute`.
- `CompletableFuture` enchaîne des étapes asynchrones ; `exceptionally` récupère les erreurs.
- Exécuteurs planifiés pour les tâches périodiques, toujours arrêtés.
- Réflexion en dernier recours, mise en cache ; connaître les correspondances Kotlin ↔ Java.
