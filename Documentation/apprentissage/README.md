# Apprentissage — créer un mod Minecraft comme Phantasmon

Ce parcours explique **tout le mod client Phantasmon** à un développeur qui sait programmer mais n'a jamais fait de
mod Minecraft. À la fin, vous devez comprendre chaque partie du dépôt et savoir construire un mod client similaire :
qui parle à un serveur externe, affiche ses propres entités et écrans, et s'appuie sur un autre mod (Cobblemon).

**Lisez d'abord le parcours du backend** (`Phantasmon-Backend/Documentation/apprentissage/`), au moins les
chapitres 1 à 3, 8 et 10 : ce mod est un client de ce backend, et les notions de HTTP, JSON, JWT et WebSocket y
sont expliquées. Elles ne sont que rappelées ici.

## Ce que le parcours suppose

- Vous savez programmer ; les bases (variable, fonction, classe…) ne sont pas réexpliquées.
- Vous connaissez peu Java : le chapitre 2 du backend présente le Java courant, le chapitre 2 ici le Java plus
  avancé utilisé côté client (threads, `CompletableFuture`, réflexion, interopérabilité Kotlin).
- Vous n'avez jamais fait de mod, ni utilisé Fabric, Mixin ou Cobblemon.

## Comment lire

Chapitres **dans l'ordre**. Chaque chapitre explique le concept général du modding, montre le vrai code (chemin
du fichier indiqué, relatif à `src/client/java/com/mystaria/phantasmon/client/` sauf mention contraire), puis
résume « À retenir ». Les extraits sont parfois raccourcis ; le code complet fait foi.

## Sommaire

| N° | Chapitre | Vous apprendrez |
|---|---|---|
| 1 | [Le modding Minecraft](01-le-modding-minecraft.md) | Client, serveur, serveur intégré, loaders, ce qu'un mod « client uniquement » peut faire |
| 2 | [Le Java utile côté client](02-java-utile-cote-client.md) | Threads, `CompletableFuture`, exécuteurs, `volatile`, réflexion, Kotlin |
| 3 | [Gradle, Loom et les mappings](03-gradle-loom-et-mappings.md) | Obfuscation, mappings, Fabric Loom, dépendances de mods, JDK 25 |
| 4 | [Anatomie d'un mod Fabric](04-anatomie-d-un-mod-fabric.md) | `fabric.mod.json`, points d'entrée, ressources, langues, textures |
| 5 | [La boucle de jeu et les événements](05-boucle-de-jeu-et-evenements.md) | Ticks, thread de rendu, événements Fabric, cycle de vie du mod |
| 6 | [Commandes, touches et chat](06-commandes-touches-et-chat.md) | Brigadier, touches configurables, composants de texte, traductions |
| 7 | [Parler au backend](07-parler-au-backend.md) | HTTP asynchrone, Gson, preuve Mojang, JWT, WebSocket, aiguillage des messages |
| 8 | [Entités et Ghost](08-entites-et-ghost.md) | Entités, monde client, données synchronisées, mouvement, animations |
| 9 | [Les interfaces graphiques](09-interfaces-graphiques.md) | `Screen`, `GuiGraphics`, `PoseStack`, textures, modèles 3D, saisie |
| 10 | [Les Mixins](10-mixins.md) | Modifier le code d'un autre programme : `@Inject`, `@Redirect`, `@Accessor` |
| 11 | [Intégrer un mod tiers : Cobblemon](11-integrer-un-mod-tiers.md) | Lire un jar, décompiler, Kotlin depuis Java, API publique contre internes |
| 12 | [Le moteur de combat](12-moteur-de-combat.md) | Faire tourner du code serveur sur un client, paquets, relais, animations |
| 13 | [Logique pure et tests](13-logique-pure-et-tests.md) | Isoler ce qui se teste, import Showdown, état d'échange, JUnit |
| 14 | [Études de cas : bugs réels](14-etudes-de-cas.md) | Enquêtes sur les vrais bugs du mod |
| 15 | [Glossaire](15-glossaire.md) | Tous les termes |

## Pour aller plus loin

Documentation de référence : `../architecture/` (détail de chaque sous-système), `../reference/` (commandes,
configuration, traductions), `../guides/` (compiler, tester), `../project/development-journal.md` (l'histoire de
chaque fonctionnalité, passe par passe).
