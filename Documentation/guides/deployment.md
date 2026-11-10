# Déployer pour les tests

> Le mod n'est pas encore publié (Phase 10). Ce guide décrit l'environnement de test actuel (2026-11-10).

## 1. Script de déploiement

`scripts/deploy-to-prod-server.sh` (Bash, Git Bash sous Windows) :

1. compile Showtime et Phantasmon (et Events avec `--events`) ;
2. copie leurs jars dans chaque instance CurseForge locale « Cobblemon Academy 2.0 - … » dont le dossier `mods/`
   contient déjà Phantasmon (« … - MystAria_ » et « … - TheMashen »), en supprimant seulement leurs anciens jars ;
3. sans `--events`, retire le jar de Phantasmon Events de ces instances : elles ont exactement ce que le déploiement
   installe. Events (D-41) demande aussi son module dans le backend (`deploy-ubuntu.sh`, sans `--no-events`).

```bash
./scripts/deploy-to-prod-server.sh
```

Depuis le 2026-11-10, les tests se font **sans Events** (Adrien) : Phantasmon seul, sur notre serveur Minecraft, avec
le backend lancé par `deploy-ubuntu.sh --no-events`. Les instances « Phantasmon Test » ne servent plus.

Les jeux déjà lancés doivent être redémarrés. **Ce script n'est pas versionné** (il est dans `.gitignore`, car il
contient des chemins propres aux machines d'Adrien) : il n'existe que sur la machine de développement.

## 2. Environnement de test à deux comptes

| Machine | Compte | Instance | Rôle |
|---|---|---|---|
| Développement (`100.116.43.32` sur Tailscale) | `MystAria_` | Cobblemon Academy 2.0 - MystAria_ | Premier client |
| Développement | `TheMashen` | Cobblemon Academy 2.0 - TheMashen | Second client |
| Serveur Ubuntu (`100.101.1.79`) | — | — | Backend + PostgreSQL (`guides/deploying-ubuntu.md` du dépôt Backend), et notre serveur Minecraft Cobblemon Academy (port 25565) où jouent les deux clients |

Les deux clients doivent viser **le même backend** (celui du serveur Ubuntu) : `backend_url` de
`config/phantasmon.json` dans chaque instance.

### Rejoindre la même partie

Les tests se font sur **notre serveur Minecraft dédié** (server-linux, Cobblemon Academy, `100.101.1.79:25565`,
Tailscale seulement ; allumé et éteint depuis la supervision) avec les deux comptes (Adrien 2026-11-03). Tout le monde
y tape la même adresse, donc a la même empreinte de serveur : plus de LAN ni d'empreinte forcée (la commande de test
`/phantasmon admin debug fingerprint` a été retirée le 2026-11-03).

## 3. Avant une publication

- URL du backend configurable (aujourd'hui en dur).
- Lien de mise à jour (`AuthService`) : aujourd'hui les versions GitHub du projet, à remplacer par la page
  Modrinth ou CurseForge.
- Traiter les points de priorité haute de [`project/known-issues.md`](../project/known-issues.md).
- Aligner `version` (`gradle.properties`) et `phantasmon.version.current` / `min-supported` du backend.
