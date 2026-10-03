# Installer le mod (joueur)

## 1. Prérequis

| Élément | Version |
|---|---|
| Minecraft | 1.21.1 |
| Fabric Loader | 0.18.1 ou plus récent |
| Fabric API | 0.116.17+1.21.1 ou compatible |
| Cobblemon | **1.8.1**, build Fabric (embarque `fabric-language-kotlin`, rien à ajouter) |
| Java (pour jouer) | 21 |
| Compte | **Compte Microsoft / Minecraft premium** : les comptes hors-ligne sont refusés |
| Backend | Un backend Phantasmon joignable à l'adresse compilée dans le mod |

Le mod est **client uniquement** : rien à installer sur le serveur Minecraft. Il fonctionne en solo, en LAN et sur
n'importe quel serveur. Les autres joueurs ne voient vos Ghost que s'ils ont aussi le mod.

## 2. Installation

1. Installer Fabric Loader pour 1.21.1 (installateur officiel Fabric, ou un lanceur comme Modrinth App / CurseForge).
2. Placer Fabric API et Cobblemon 1.8.1 (Fabric) dans le dossier `mods/` de l'instance.
3. Placer `phantasmon-client-<version>.jar` dans ce même dossier `mods/` (pas le `-sources.jar`).
4. Lancer le jeu. Les mods ne sont chargés qu'au démarrage : relancer le jeu après chaque mise à jour du jar.

Le mod n'est pas encore publié sur Modrinth ni CurseForge.

## 3. Premier lancement

En entrant dans un monde, le mod vérifie que le backend répond, puis vous connecte automatiquement (rien ne
s'affiche si le backend est injoignable). En cas de besoin : `/phantasmon login`.

Suite : [`user-guide.md`](user-guide.md).
