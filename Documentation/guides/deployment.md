# Déployer pour les tests

> Le mod n'est pas encore publié (Phase 10). Ce guide décrit l'environnement de test actuel (2026-11-01).

## 1. Script de déploiement

`scripts/deploy-to-prod-server.sh` (Bash, Git Bash sous Windows) :

1. compile Spectacle, Phantasmon et Events ;
2. copie leurs jars dans chaque instance CurseForge locale dont le dossier `mods/` contient déjà Phantasmon ou
   Spectacle (aujourd'hui « Cobblemon Academy 2.0 - MystAria_ » et « Cobblemon Academy 2.0 - TheMashen »), en
   supprimant seulement leurs anciens jars.

```bash
./scripts/deploy-to-prod-server.sh
```

Les jeux déjà lancés doivent être redémarrés. **Ce script n'est pas versionné** (il est dans `.gitignore`, car il
contient des chemins propres aux machines d'Adrien) : il n'existe que sur la machine de développement.

## 2. Environnement de test à deux comptes

| Machine | Compte | Instance | Rôle |
|---|---|---|---|
| Développement (`100.116.43.32` sur Tailscale) | `MystAria_` | Cobblemon Academy 2.0 - MystAria_ | Premier client |
| Développement | `TheMashen` | Cobblemon Academy 2.0 - TheMashen | Second client |
| Serveur Ubuntu (`100.101.1.79`) | — | — | Backend + PostgreSQL (`guides/deploying-ubuntu.md` du dépôt Backend) |

Les deux clients doivent viser **le même backend** (celui du serveur Ubuntu) : `backend_url` de
`config/phantasmon.json` dans chaque instance.

### Rejoindre la même partie

Pas de serveur dédié (le modpack compte environ 230 mods) : l'un des joueurs ouvre son monde solo en LAN, l'autre
s'y connecte **directement** avec l'IP Tailscale de l'hôte et le port annoncé dans le chat (Tailscale ne relaie pas
la découverte LAN).

### Empreinte de serveur

L'hôte LAN calcule `"singleplayer"`, l'invité un hash de l'adresse : le backend ne les regroupe pas. Sur **chaque**
client, taper une fois la même valeur :

```text
/phantasmon admin debug fingerprint test
```

La valeur est enregistrée (`config/phantasmon-fingerprint-override.txt`) et appliquée immédiatement, puis à chaque
connexion. `/phantasmon admin debug fingerprint` sans argument revient au calcul normal. Sur un vrai serveur dédié, cette
manipulation est inutile.

## 3. Avant une publication

- URL du backend configurable (aujourd'hui en dur).
- Retirer `/phantasmon admin debug fingerprint`.
- Renseigner les liens de `fabric.mod.json` et le lien de mise à jour (`AuthService`).
- Traiter les points de priorité haute de [`project/known-issues.md`](../project/known-issues.md).
- Aligner `version` (`gradle.properties`) et `phantasmon.version.current` / `min-supported` du backend.
