# Configuration du client

> Vérifié le 2026-10-03. Le mod n'a **pas encore d'écran ni de fichier de paramètres** pour le joueur : les
> réglages ci-dessous sont dans le code ou les fichiers de build.

## 1. Constantes du code

| Réglage | Valeur | Où |
|---|---|---|
| URL du backend | `http://100.116.43.32:8080` (IP Tailscale de la machine de dev) | `network/BackendConfig.BASE_URL` |
| Version Cobblemon enregistrée sur les Pokémon créés | `1.8.1` | `pokemon/PokemonCommandHandler.COBBLEMON_DATA_VERSION` |
| Lien de mise à jour proposé si la version est refusée | `https://modrinth.com/mod/phantasmon` (factice) | `auth/AuthService` |
| Position + heartbeat WebSocket | toutes les 1 s | `ghost/GhostSession` |
| Vérification du renouvellement du JWT | toutes les 60 s, marge de 30 s avant expiration | `auth/SessionRefreshScheduler`, `auth/AuthSession` |
| Ping de santé (si activé) | toutes les 30 s, délai de 2 s | `network/BackendHealthPinger` |
| Échelle des menus | 80 % de la fenêtre | `gui/PhantasmonCanvasScreen.MENU_SCALE` |
| Suivi et balade des Ghost | voir [`architecture/ghost-entities.md`](../architecture/ghost-entities.md) | `ghost/GhostEntityManager` |

Changer l'URL du backend impose de recompiler et de redéployer le mod sur toutes les machines de test.

## 2. Fichiers créés sur la machine du joueur

| Fichier | Contenu | Quand |
|---|---|---|
| `config/phantasmon-fingerprint-override.txt` | Valeur brute de l'empreinte forcée | Uniquement après `/phantasmon debug fingerprint <valeur>` ; supprimé par la même commande sans argument |
| `options.txt` (Minecraft) | Touches choisies (`key.phantasmon.*`) | Géré par Minecraft |
| `logs/latest.log` (Minecraft) | Journaux du mod (logger `phantasmon`, classes `GhostSession`, `PhantasmonWebSocketClient`…) | À chaque partie |

Le JWT n'est **jamais** écrit sur disque.

## 3. Build

| Fichier | Réglages |
|---|---|
| `gradle.properties` | `minecraft_version=1.21.1`, `loader_version=0.18.1`, `loom_version=1.18-SNAPSHOT`, `fabric_api_version=0.116.17+1.21.1`, `cobblemon_version=gBW3vLC7` (identifiant de version **Modrinth** du build Fabric 1.8.1 ; ne pas le remplacer par `1.8.1`, ambigu avec le build NeoForge), `version=1.0.0` |
| `build.gradle` | Loom (`splitEnvironmentSourceSets`, mappings officiels), dépôt Maven Modrinth, `kotlin-stdlib` en `compileOnly`, JUnit 5, `options.release = 21`, le jeu de sources `client` branché sur les tests |
| `src/main/resources/fabric.mod.json` | Identifiant `phantasmon`, `environment: client`, points d'entrée, dépendances (`fabricloader >= 0.18.1`, `minecraft ~1.21.1`, `java >= 21`, `fabric-api`), licence déclarée `CC0-1.0` alors que le projet est en GPL 3.0 (BUG-1, `project/known-issues.md`), liens `homepage`/`sources` factices |
| `src/client/resources/phantasmon.client.mixins.json` | 7 Mixins client, `defaultRequire = 1` |
| `.env` / `.env.template` | Non utilisés par le mod (reliquat aligné sur le backend : clés `BDD_*`) |

Cobblemon n'est pas déclaré dans `depends` de `fabric.mod.json` : il est requis en pratique (le mod en utilise les
classes), mais son absence ne serait pas signalée proprement par Fabric Loader.
