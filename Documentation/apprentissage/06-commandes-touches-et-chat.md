# 6. Commandes, touches et chat

## 6.1 Brigadier : l'arbre des commandes

Minecraft analyse les commandes avec **Brigadier**, une bibliothèque de Mojang. Une commande est un **arbre** :
des nœuds littéraux (mots fixes) et des nœuds arguments (valeurs typées), chaque feuille ayant une action
(`executes`).

```text
/phantasmon
├── login
├── pc
├── sendout
├── pokemon
│   ├── list
│   ├── pc <box:1..16>
│   │   └── move <uuid> <box> <slot>
│   └── team
│       └── set <uuid> <slot:1..6>
├── trade
│   └── invite <player>  (suggestions : joueurs du serveur)
└── battle
    ├── invite <player>
    └── timer
```

Avec Fabric API, une commande **client** (exécutée sans jamais contacter le serveur Minecraft) s'enregistre ainsi :

```java
// command/PhantasmonCommands.java (raccourci)
ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
    var battleNode = ClientCommandManager.literal("battle")
            .then(ClientCommandManager.literal("invite")
                    .then(ClientCommandManager.argument("player", StringArgumentType.word())
                            .suggests((context, builder) ->
                                    SharedSuggestionProvider.suggest(LiveTradeController.onlinePlayerNames(), builder))
                            .executes(context -> {
                                liveBattle.inviteByName(StringArgumentType.getString(context, "player"));
                                return Command.SINGLE_SUCCESS;
                            })))
            .then(ClientCommandManager.literal("timer").executes(context -> {
                liveBattle.enableTimer();
                return Command.SINGLE_SUCCESS;
            }));

    dispatcher.register(ClientCommandManager.literal("phantasmon")
            .then(ClientCommandManager.literal("login").executes(context -> { authService.login(); return Command.SINGLE_SUCCESS; }))
            .then(battleNode)
            …);
});
```

| Élément | Rôle |
|---|---|
| `literal("battle")` | Mot fixe |
| `argument("player", StringArgumentType.word())` | Valeur ; autres types : `IntegerArgumentType.integer(1, 16)` (bornes vérifiées par Brigadier), `UuidArgument.uuid()` |
| `.suggests(...)` | Propositions de complétion (touche Tab) |
| `.executes(context -> …)` | Action ; `context.getSource()` permet de répondre au joueur |
| `Command.SINGLE_SUCCESS` | Valeur de retour conventionnelle (1) |

Convention du projet : la méthode `executes` ne fait **qu'appeler** un gestionnaire (`liveBattle`,
`pokemonCommands`…). La logique n'est jamais dans l'arbre de commandes.

## 6.2 Les touches

```java
// PhantasmonKeybinds.java (raccourci)
private static final String CATEGORY = "key.categories.phantasmon";
private static final KeyMapping OPEN_PC = new KeyMapping("key.phantasmon.open_pc", GLFW.GLFW_KEY_P, CATEGORY);
private static final KeyMapping BATTLE_WITH_TARGET = new KeyMapping("key.phantasmon.battle", GLFW.GLFW_KEY_B, CATEGORY);

public static void register() {
    KeyBindingHelper.registerKeyBinding(OPEN_PC);          // apparaît dans Options → Commandes
    KeyBindingHelper.registerKeyBinding(BATTLE_WITH_TARGET);
}

public static void tick(…) {                               // appelé à chaque tick client
    while (OPEN_PC.consumeClick()) {
        pokemonCommands.openPc();
    }
    while (BATTLE_WITH_TARGET.consumeClick()) {
        liveBattle.inviteTargetedPlayer();
    }
}
```

- Une `KeyMapping` est identifiée par une **clé de traduction** (`key.phantasmon.open_pc`) qui sert aussi de nom
  affiché. Le joueur peut changer la touche ; Minecraft enregistre son choix dans `options.txt`, **tant que
  l'identifiant ne change pas**.
- `consumeClick()` renvoie vrai une fois par appui (y compris un appui survenu entre deux ticks) : l'action n'est
  pas répétée tant que la touche reste enfoncée.
- « Viser un joueur » : `Minecraft.getInstance().crosshairPickEntity` donne l'entité sous le réticule.

## 6.3 Le texte : `Component`

Tout texte affiché par Minecraft est un `Component` (texte + style + événements), pas une simple chaîne.

```java
Component.literal("texte brut")                                   // à éviter pour le joueur (non traduit)
Component.translatable("phantasmon.auth.success")                 // clé de traduction
Component.translatable("phantasmon.ghost.sendout_confirmed", species)   // avec paramètre %s
```

Styles et clics : un message peut contenir un lien cliquable ou un bouton qui exécute une commande.

```java
// auth/AuthService.java
MutableComponent link = Component.translatable("phantasmon.auth.download_link")
        .withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
        .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, DOWNLOAD_URL_PLACEHOLDER)));
```

| Action de clic | Effet | Usage dans Phantasmon |
|---|---|---|
| `OPEN_URL` | Ouvre un lien | Mettre à jour le mod |
| `RUN_COMMAND` | Exécute une commande | Boutons [Accepter] / [Refuser] (`/phantasmon trade join`) |
| `SUGGEST_COMMAND` | Pré-remplit la barre de chat | Cliquer sur un UUID affiché |
| `COPY_TO_CLIPBOARD` | Copie un texte | — |

Afficher dans le chat : `player.displayClientMessage(component, false)` (le `true` l'afficherait au-dessus de la
barre d'action, utilisé pour le compte à rebours du chrono de combat). Dans une commande :
`context.getSource().sendFeedback(...)` ou `sendError(...)`.

## 6.4 Les traductions

```json
// assets/phantasmon/lang/fr_fr.json (extrait)
{
  "phantasmon.ghost.sendout_confirmed": "[Phantasmon] %s est sorti.",
  "key.phantasmon.open_pc": "Ouvrir le PC",
  "key.categories.phantasmon": "Phantasmon"
}
```

Minecraft choisit le fichier selon la langue du jeu (`fr_fr`, `en_us`), avec repli sur l'anglais. Règles du
projet : aucune chaîne visible en dur, toute clé ajoutée dans les deux fichiers, et les erreurs du backend
(`ERROR_…`) traduites par une table (`network/BackendErrorMessages`) au lieu d'être affichées brutes.

Les noms venant de Cobblemon (espèces, talents…) ont leurs propres traductions dans l'espace `cobblemon`. Attention
aux API qui renvoient une **clé** et non un texte : il faut alors passer par `Component.translatable(cle)`.

## À retenir

- Brigadier : un arbre de littéraux et d'arguments typés ; l'action délègue à un gestionnaire.
- `KeyMapping` + `consumeClick()` dans le tick ; l'identifiant de la touche ne doit jamais changer.
- Tout texte est un `Component` traduisible, avec styles et actions de clic.
