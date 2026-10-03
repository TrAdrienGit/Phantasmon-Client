# 11. Intégrer un mod tiers : Cobblemon

## 11.1 Pourquoi s'appuyer sur un autre mod

Réécrire 1 000 espèces, leurs modèles, leurs animations et un moteur de combat serait irréaliste. Phantasmon
**réutilise** Cobblemon :

| Besoin | Ce qu'on prend dans Cobblemon |
|---|---|
| Données (espèces, formes, talents, natures, attaques, objets, types) | `PokemonSpecies`, `Abilities`, `Natures`, `Moves`, `ElementalTypes`, registre d'objets |
| Afficher un Pokémon dans le monde | `PokemonEntity` et son renderer |
| Afficher un Pokémon dans un écran | `drawProfilePokemon` |
| Combattre | `PokemonBattle`, `BattleRegistry`, le service Showdown, l'interface de combat |
| Animations | Faisceaux de Poké Ball, cris, timelines d'attaque |
| Traductions des noms | Fichiers de langue de Cobblemon |

Le backend, lui, ne stocke que des **identifiants** (`samurott`, `aquatail`, `assault_vest`) ; chaque client les
résout avec son Cobblemon.

## 11.2 API publique ou internes

Un mod expose rarement une API stable documentée. On distingue :

| | Exemple | Risque à la mise à jour |
|---|---|---|
| **API publique** prévue pour les autres mods | Événements Cobblemon, registres, `PokemonSpecies.getByName` | Faible |
| **Classes publiques internes** | `PokemonEntity`, `CobblemonNetwork`, `BattleRegistry` | Moyen |
| **Détails d'implémentation** | Champ privé `options` de la roue, lambda `invoke$lambda$1` | Élevé (Mixins) |

Règle du projet : préférer l'API ; utiliser les internes en le sachant ; documenter chaque dépendance fragile
(`../architecture/mixins.md`).

## 11.3 Lire le code réel : décompiler

Le seul moyen fiable de savoir ce qui existe dans **la version utilisée** est de lire le jar. Méthode employée tout
au long du projet :

1. Loom a téléchargé les jars dans son cache (`~/.gradle/caches/fabric-loom/…`), déjà remappés avec nos noms.
2. Lister les membres d'une classe :

```bash
javap -p -cp cobblemon.jar com.cobblemon.mod.common.entity.pokemon.PokemonEntity
```

3. Lire le bytecode d'une méthode (pour un Mixin, pour reproduire un appel) :

```bash
javap -c -p -cp cobblemon.jar com.cobblemon.mod.common.client.gui.pc.StorageSlot
```

4. Un décompilateur (celui de l'IDE, Vineflower, CFR) reconstitue un code lisible.
5. Le dépôt source de Cobblemon (cloné en référence, en lecture seule) aide à comprendre l'intention, mais **le jar
   fait foi** (le dépôt peut être sur une autre version).

Exemples de découvertes faites ainsi :

- la signature exacte de `MinecraftSessionService.joinServer(UUID, String, String)` dans cette version d'authlib ;
- les paramètres exacts passés par l'écran PC de Cobblemon à `drawProfilePokemon` (rotation, échelle, masque) ;
- que `MoveInstruction` écrit directement dans son champ `future` (cause des animations absentes, chapitre 14) ;
- que les options de la roue d'interaction viennent d'un `enum` fermé, sans point d'extension.

## 11.4 Les données de Cobblemon

```java
Species species = PokemonSpecies.INSTANCE.getByName("samurott");          // null si inconnue
FormData form = PokemonGuiRendering.resolveForm(species, "hisui");          // formes de l'espèce
ElementalType type = species.getPrimaryType();                              // types et leurs couleurs
MoveTemplate move = Moves.INSTANCE.getByName("aquatail");                   // attaque : type, nom traduit
```

Identifiants : espèces, formes et attaques en minuscules sans séparateur (`mimejr`, `bodyslam`) ; talents, objets et
natures en minuscules avec `_` (`flash_fire`, `choice_band`). Vérifié sur les noms de fichiers des données de
Cobblemon (`data/cobblemon/species/…`) ; détail au chapitre 13.

### Les tags

Un **tag** Minecraft est une liste nommée d'objets (ou de blocs…) définie par les données. Pour savoir quels objets
ont un effet en combat, la bonne source est le tag de Cobblemon `#cobblemon:held/is_held_item`, et non une liste
écrite à la main :

```java
// pokemon/CobblemonHeldItems.java (principe)
TagKey<Item> HELD = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("cobblemon", "held/is_held_item"));
BuiltInRegistries.ITEM.getTagOrEmpty(HELD)   // tous les objets du tag
```

Si une version future de Cobblemon ajoute des objets, le sélecteur les proposera sans changement de code.

## 11.5 Les pièges de l'interopérabilité Kotlin

| Situation rencontrée | Solution |
|---|---|
| `javac` refuse `for (PotentialAbility p : species.getAbilities())` : `cannot access KMappedMarker` | Ajouter `kotlin-stdlib` en `compileOnly` : les interfaces Kotlin doivent être visibles à la compilation |
| `drawProfilePokemon$default` (paramètres par défaut) invisible depuis Java (`@JvmSynthetic`) | Réflexion, méthode mise en cache |
| `PokemonSpecies.allShowdownSpecies()` est `internal` : nom modifié | Réflexion, recherche par préfixe du nom |
| `AbilityTemplate.getDisplayName()` renvoie une clé, pas un texte | `Component.translatable(cle).getString()` |
| `Pokemon.sendOut(...)` exige un `ServerLevel` | Créer l'entité soi-même (chapitre 8) |
| `PokemonProperties.create()` utilise des données de serveur absentes chez un client pur | `new Pokemon()` puis appliquer seulement les propriétés visuelles |

## 11.6 Les données de serveur sur un client

Certaines données de Cobblemon sont des **données de datapack serveur** : elles n'existent que sur un serveur et ne
sont pas envoyées aux clients (les timelines d'animation d'attaque, les « moveset builders », les scripts d'objets
pour Showdown). Avec un serveur intégré (solo, hôte LAN), elles sont là. Sur un client pur, elles manquent. Le mod
les recharge lui-même depuis les jars des mods quand il en a besoin (`ClientActionEffects`,
`BattleThread.jarScripts`), avec les parseurs de Cobblemon.

Leçon générale : toujours tester un mod client **dans les deux situations** : solo/hôte LAN (serveur intégré) et
client connecté à un serveur distant. Plusieurs bugs n'apparaissaient que dans la seconde (chapitre 14).

## À retenir

- Réutiliser un mod existant économise des mois, au prix d'une dépendance à sa version.
- Lire le jar réel (décompiler) au lieu de deviner ou de suivre un tutoriel d'une autre version.
- Préférer API et données (tags, registres) aux listes écrites à la main.
- Connaître les pièges Kotlin ↔ Java et la différence entre données client et données serveur.
