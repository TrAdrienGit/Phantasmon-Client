# 7. Parler au backend

Rappel : HTTP, JSON, JWT et WebSocket sont expliqués dans le parcours du backend (chapitres 3, 8 et 10). Ici, on
voit comment le **client** les utilise.

## 7.1 Où est le backend

```java
// network/BackendConfig.java
public static final URI BASE_URL = BackendUrlFile.load(FabricLoader.getInstance().getConfigDir().resolve("phantasmon.json"));
```

Une seule constante pour tout le mod, lue une fois au lancement dans `config/phantasmon.json` (`"backend_url"`).
`BackendUrlFile` crée le fichier avec la valeur par défaut s'il manque et retombe sur cette valeur si l'adresse est
invalide ; il ne dépend pas de Minecraft, ce qui permet de le tester (`BackendUrlFileTest`).
`BASE_URL.resolve("/pokemon")` construit chaque adresse.

## 7.2 Un client REST générique

`BackendJsonClient` enveloppe le client HTTP du JDK (`java.net.http.HttpClient`) : chaque appel renvoie un
`CompletableFuture` (chapitre 2.2), le JSON est converti par Gson, et toute réponse d'erreur devient une exception
typée.

```java
// network/BackendJsonClient.java (raccourci)
public <T> CompletableFuture<T> post(URI uri, Object requestBody, String bearerToken, Class<T> responseType) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(5))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)));      // objet → JSON
    if (bearerToken != null) builder.header("Authorization", "Bearer " + bearerToken);
    return httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString()) // asynchrone
            .thenApply(response -> {
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return GSON.fromJson(response.body(), responseType);                // JSON → objet
                }
                throw toApiException(response);                                          // {error_code, details} → exception
            });
}
```

Par-dessus, chaque domaine a une petite classe d'appels (`PokemonClient`, `TradeClient`) et des DTO en records qui
reproduisent les records du backend (`PokemonDto`, `PokemonCreateRequestDto`…).

```java
// Utilisation typique (gui/PhantasmonPcScreen.java)
pokemonClient.listForOwner(session.accessToken(), session.playerUuid())
        .thenAccept(pokemons -> Minecraft.getInstance().execute(() -> {   // retour sur le thread client
            this.pokemons = pokemons;                                     // mettre à jour l'écran
        }))
        .exceptionally(ex -> { /* message d'erreur traduit */ return null; });
```

Erreurs : `BackendApiException.errorCode()` donne le code (`ERROR_POKEMON_PC_FULL`), traduit par
`BackendErrorMessages.translationKey(code)` en clé de langue.

## 7.3 Se connecter : la preuve Mojang côté client

```java
// auth/AuthService.java (raccourci)
public void login() {
    if (session.isAuthenticated()) { report("phantasmon.auth.already_connected"); return; }
    User user = Minecraft.getInstance().getUser();
    if (user.getType() == User.Type.LEGACY) { report("phantasmon.auth.offline_unsupported"); return; }   // compte hors-ligne
    httpClient.get(BackendConfig.BASE_URL.resolve("/version"), VersionResponseDto.class)              // 1. version
            .thenComposeAsync(version -> handleVersion(version, user, client))
            .exceptionally(this::reportFailure);
}

private CompletableFuture<Void> handleVersion(VersionResponseDto version, User user, Minecraft client) {
    VersionCompatibility.Status status = VersionCompatibility.evaluate(modVersion(), version.minSupportedVersion(), version.currentVersion());
    if (status == VersionCompatibility.Status.INCOMPATIBLE) { reportVersionIncompatible(…); return CompletableFuture.completedFuture(null); }
    return httpClient.postNoBody(BackendConfig.BASE_URL.resolve("/auth/challenge"), null, AuthChallengeResponseDto.class)
            .thenApplyAsync(challenge -> joinMojangServer(user, client, challenge.challenge()))        // 2. preuve Mojang
            .thenCompose(serverId -> httpClient.post(BackendConfig.BASE_URL.resolve("/auth/session"),  // 3. jetons
                    new AuthSessionRequestDto(user.getProfileId(), user.getName(), serverId), AuthSessionResponseDto.class))
            .thenAccept(response -> {
                session.update(user.getProfileId(), user.getName(), response);                        // 4. en mémoire
                report("phantasmon.auth.success");
                onAuthenticated.run();                                                                 // 5. ouvrir le WebSocket
            });
}

private static String joinMojangServer(User user, Minecraft client, String serverId) {    // serverId = défi du backend
    client.getMinecraftSessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
    return serverId;
}
```

- `joinServer` est l'appel que le jeu fait en rejoignant n'importe quel serveur en ligne : il prouve à Mojang que ce
  client détient le compte. Il est **bloquant** (requête réseau) : d'où `thenApplyAsync`, hors du thread client.
- Le `serverId` n'est **pas** tiré au hasard par le client : c'est un défi à usage unique fourni par le backend
  (`POST /auth/challenge`). Sinon, n'importe quel serveur Minecraft rejoint par le joueur, qui reçoit le même genre
  de preuve pour son propre `serverId`, pourrait la rejouer chez nous et se connecter à sa place (audit de sécurité,
  SEC-1).
- Le jeton d'accès Minecraft (`getAccessToken()`) part chez Mojang, **jamais** chez notre backend.
- `onAuthenticated` est un `Runnable` fourni par `PhantasmonClient` : `AuthService` ne connaît pas `GhostSession`,
  il prévient simplement « c'est fait ». Ce découplage évite que les classes dépendent toutes les unes des autres.

### Connexion automatique et renouvellement

- À l'entrée dans un monde, `autoLoginIfBackendHealthy()` appelle `GET /health` et ne lance `login()` que si la
  réponse est `UP`/`UP`. En cas d'échec : **silence**. La plupart des mondes n'ont rien à voir avec Phantasmon ; un
  contrôle automatique ne doit pas déranger le joueur.
- `SessionRefreshScheduler` vérifie toutes les 60 s si le jeton expire dans moins de 30 s, et appelle
  `POST /auth/refresh`.
- Le JWT vit dans `AuthSession` (mémoire, méthodes `synchronized`), jamais sur disque, effacé à la déconnexion.

## 7.4 Le WebSocket côté client

```java
// network/PhantasmonWebSocketClient.java (raccourci)
public CompletableFuture<Void> connect(URI baseUrl, String jwt, Listener listener) {
    URI wsUri = toWebSocketUri(baseUrl).resolve("/ws?token=" + jwt);          // http → ws, https → wss
    return httpClient.newWebSocketBuilder()
            .buildAsync(wsUri, new EnvelopeListener(listener))
            .thenAccept(ws -> this.webSocket = ws);
}

public void send(String type, Map<String, Object> data) {
    WebSocket ws = webSocket;
    if (ws == null) { LOG.warn("Dropped WebSocket message '{}' — not connected yet", type); return; }
    ws.sendText(GSON.toJson(new Envelope(type, data)), true);
}

private static final class EnvelopeListener implements WebSocket.Listener {
    private final StringBuilder buffer = new StringBuilder();
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        buffer.append(data);                       // un message peut arriver en plusieurs morceaux
        if (last) { dispatch(buffer.toString()); buffer.setLength(0); }
        webSocket.request(1);                      // « je suis prêt pour le morceau suivant »
        return null;
    }
}
```

Deux détails d'API qui comptent : un long message (paquet de combat) arrive en **plusieurs fragments** qu'il faut
réassembler (`last`), et le WebSocket du JDK n'envoie le fragment suivant qu'après `request(1)`.

## 7.5 Une connexion, plusieurs fonctionnalités : `GhostSession`

`GhostSession` possède **l'unique** WebSocket. Elle gère la présence et les Ghost, et **aiguille** les autres
messages vers les fonctionnalités qui empruntent la connexion :

```java
// ghost/GhostSession.java (raccourci)
private void handleMessage(String type, Map<String, Object> data) {
    switch (type) {
        case "GhostEntitySpawn" -> Minecraft.getInstance().execute(() -> entityManager.spawn(…));
        case "GhostEntityMove" -> Minecraft.getInstance().execute(() -> entityManager.move(…));
        case "GhostEntityDespawn" -> Minecraft.getInstance().execute(() -> entityManager.despawn(…));
        case "Error" -> report(BackendErrorMessages.translationKey(String.valueOf(data.get("error_code"))));
        case "TradeProposed" -> Minecraft.getInstance().execute(() -> tradeListener.onTradeProposed(data));
        default -> {
            if (type.startsWith("Battle")) {
                Minecraft.getInstance().execute(() -> liveBattleListener.onLiveBattleMessage(type, data));
            } else if (type.startsWith("TradeInvite") || type.startsWith("TradeSession")) {
                Minecraft.getInstance().execute(() -> liveTradeListener.onLiveTradeMessage(type, data));
            }
        }
    }
}
```

Les fonctionnalités s'enregistrent comme **écouteurs** (`setLiveTradeListener`, `setLiveBattleListener`) : le motif
« observateur ». `GhostSession` n'a pas à connaître le détail des échanges ou des combats.

### Le cycle de la session

```java
public synchronized void start() {
    webSocketClient.connect(BackendConfig.BASE_URL, authSession.accessToken(), listener)
            .thenRun(() -> connected = true)                               // seulement quand le handshake a réussi
            .exceptionally(ex -> { report("phantasmon.ghost.connection_failed"); connected = false; return null; });
    scheduler = Executors.newSingleThreadScheduledExecutor(…);
    scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
}

private void tick() {                                                      // chaque seconde
    if (!joinedGroup) { joinServerGroup(player); joinedGroup = true; }     // une fois par connexion
    webSocketClient.send("PositionUpdate", Map.of("x", …, "y", …, "z", …, "dimension", …));
    webSocketClient.send("Heartbeat", Map.of());
}
```

L'**empreinte de serveur** envoyée dans `JoinServerGroup` : `"singleplayer"` si `Minecraft.isLocalServer()`, sinon
le SHA-256 (hexadécimal) de l'adresse du serveur. Deux joueurs sur le même serveur calculent la même valeur ; le
backend les regroupe ainsi sans rien savoir du serveur Minecraft.

## À retenir

- Un client REST générique asynchrone ; une classe d'appels et des DTO par domaine ; erreurs typées puis traduites.
- Connexion : version → défi (`POST /auth/challenge`) → `joinServer` chez Mojang avec ce défi → `POST /auth/session`
  → JWT en mémoire → WebSocket.
- Les contrôles automatiques sont silencieux ; les actions explicites du joueur affichent leurs erreurs.
- Une seule connexion WebSocket, un aiguillage, des écouteurs par fonctionnalité.
