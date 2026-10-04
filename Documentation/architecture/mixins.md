# Mixins

> Configuration : `src/client/resources/phantasmon.client.mixins.json` (`"required": true`,
> `"defaultRequire": 1`). `src/main/resources/phantasmon.mixins.json` est vide. Vérifié le 2026-10-03 contre
> Minecraft 1.21.1 et Cobblemon 1.8.1.

## 1. Politique

- Un Mixin n'est ajouté que lorsqu'aucune API publique (Fabric, Cobblemon) ne permet d'obtenir le comportement.
- Les Mixins restent **minces** : ils interceptent et délèguent ; la logique est dans une classe normale.
- `defaultRequire = 1` : si une cible disparaît ou change (mise à jour de Minecraft ou de Cobblemon), le jeu
  **refuse de démarrer** avec une erreur Mixin explicite, au lieu de se comporter faussement en silence.
- Les Mixins visant Cobblemon utilisent `remap = false` (Cobblemon n'est pas obfusqué par Minecraft).
- Chaque Mixin n'agit **que** sur les combats ou écrans Phantasmon ; tout le reste (combats Cobblemon normaux,
  autres paquets) passe sans changement.

## 2. Inventaire

| Mixin | Cible | Point d'injection | Rôle | Délègue à |
|---|---|---|---|---|
| `ClientCommonPacketListenerImplMixin` | `net.minecraft…ClientCommonPacketListenerImpl.send` (vanilla) | `@Inject` HEAD, annulable | Arrête le `BattleSelectActionsPacket` d'un combat Ghost avant qu'il parte vers le serveur Minecraft et le donne au moteur local | `GhostBattles.onLocalChoice` |
| `DistributionUtilsMixin` | `com.cobblemon.mod.common.util.DistributionUtilsKt.runOnServer` | `@Inject` HEAD, annulable | Sur un client pur, `runOnServer` jette en silence chaque message Showdown (pas de `MinecraftServer`). Uniquement pour les appels venant du thread `phantasmon-battle`, le bloc est remis en file sur ce thread | `BattleThread` |
| `ActionEffectTimelineMixin` | `com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline.run` | `@Inject` HEAD, annulable | Pour un combat Ghost hébergé ici, n'exécute pas l'animation côté serveur (pas d'entités serveur) et la confie au relais d'animations | `GhostActionEffects.intercept` |
| `ActionEffectInstructionsMixin` | `setFuture` de 7 instructions Cobblemon (`Move`, `Damage`, `Boost`, `Activate`, `Cant`, `Prepare`, `Start`) | `@Inject` HEAD | Relie l'animation interceptée à son instruction : lanceur, cibles, cibles ratées, nombre de coups | `GhostActionEffects.bind` |
| `MoveInstructionMixin` | `MoveInstruction.invoke$lambda$1` (Cobblemon 1.8.1) | `@Redirect` de l'écriture du champ `future` | `MoveInstruction` écrit `future` directement (PUTFIELD) sans passer par `setFuture` : la redirection transforme cette écriture en appel `setFuture`, vu par le Mixin précédent | — |
| `InteractWheelGuiFactoryMixin` | `com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelGuiFactoryKt.createPlayerInteractGui` | `@Inject` RETURN | Ajoute « Échange Ghost » et « Combat Ghost » à la roue d'interaction avec un joueur | `wheel.GhostWheelOptions` |
| `InteractWheelGuiAccessor` | `InteractWheelGUI.options` (champ privé) | `@Accessor` | Accès à la `Multimap` d'options que `init()` transforme en boutons | — |
| `PokemonEntityMixin` | `com.cobblemon.mod.common.entity.pokemon.PokemonEntity.canBattle` (Cobblemon 1.8.1) | `@Inject` HEAD, annulable | Renvoie `false` pour les entités Phantasmon : Cobblemon les prenait pour des Pokémon sauvages (pas de propriétaire côté serveur), d'où la ligne « Appuyez sur R pour lancer le combat » sous leur étiquette, et la touche R qui aurait défié une entité inexistante côté serveur | `ghost.PhantasmonEntities` |
| `PokemonRendererMixin` | `com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer.resolveBaseLabel` (privée, Cobblemon 1.8.1) | `@Inject` HEAD, annulable | Les entités Phantasmon (Ghost dans le monde, Pokémon de combat Ghost) affichent toujours leur nom, même si l'espèce n'est pas au Pokédex du joueur (sinon « ??? ») ; les vrais Pokémon gardent la règle du Pokédex. Le nom est lu sur le `Pokemon` (surnom, sinon nom de l'espèce), **jamais** via `PokemonEntity.getName()` / `getTitledName()` : d'autres mods s'y branchent (catchindicator, présent dans le modpack, y remet « ??? » + suffixes) | `ghost.PhantasmonEntities` |

## 3. Après une mise à jour de Cobblemon ou de Minecraft

1. Mettre à jour `cobblemon_version` (identifiant de version **Modrinth**, pas le numéro) ou `minecraft_version`
   dans `gradle.properties`.
2. Lancer le jeu (`./gradlew runClient`) : une erreur Mixin au démarrage désigne la cible modifiée.
3. Décompiler la nouvelle classe cible (`javap -c -p`) et vérifier pour chaque ligne du tableau :
   - la signature de `createPlayerInteractGui`, le champ `options` et l'enum `Orientation` ;
   - le nom de la lambda `invoke$lambda$1` de `MoveInstruction` (généré par le compilateur Kotlin, **le plus
     fragile**) et l'écriture du champ `future` ;
   - la liste des instructions qui appellent `setFuture` (en ajouter si de nouvelles instructions lancent des
     animations) ;
   - `ActionEffectTimeline.run` et `DistributionUtilsKt.runOnServer`.
4. Rejouer la recette de [`guides/testing-and-qa.md`](../guides/testing-and-qa.md) : roue d'interaction, combat
   hébergé par un hôte LAN **et** par un client pur, animations d'attaque, boosts et statuts.

Symptôme utile : un effet d'animation non relié à son instruction laisse un `WARN … was not claimed by any
instruction` dans le log.
