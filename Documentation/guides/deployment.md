# Déployer pour les tests

> Le mod n'est pas encore publié (Phase 10). Ce guide décrit l'environnement de test actuel (2026-10-03).

## 1. Script de déploiement

`scripts/deploy-to-prod-server.sh` (Bash, Git Bash sous Windows) :

1. compile une fois (`./gradlew build -q`) ;
2. copie le jar dans l'instance locale CurseForge « Cobblemon Academy 2.0 » (en supprimant l'ancien
   `phantasmon-client-*.jar`, et rien d'autre) ;
3. si la machine serveur répond en SSH (alias `production-server`, délai de 8 s), copie le jar dans son instance
   « Cobblemon Academy 2.0 - Copie » ; **sinon**, le copie dans l'instance locale de secours « Cobblemon 2 ».

```bash
./scripts/deploy-to-prod-server.sh
```

Les jeux déjà lancés doivent être redémarrés. **Ce script n'est pas versionné** (il est dans `.gitignore`, car il
contient des chemins propres aux machines d'Adrien) : il n'existe que sur la machine de développement.

## 2. Environnement de test à deux comptes

| Machine | Compte | Instance | Rôle |
|---|---|---|---|
| Développement (`100.116.43.32` sur Tailscale) | `MystAria_` | Cobblemon Academy 2.0 | Backend + PostgreSQL + client |
| « production-server » (`100.106.248.73`) | `TheMashen` | Cobblemon Academy 2.0 - Copie | Second client |
| Développement (secours) | — | Cobblemon 2 | Reçoit le jar si la machine serveur est injoignable |

Les deux clients doivent viser **le même backend** (celui de la machine de dev) : `backend_url` de
`config/phantasmon.json` dans chaque instance (valeur par défaut = machine de dev, rien à faire).

### Rejoindre la même partie

Pas de serveur dédié (le modpack compte environ 230 mods) : l'un des joueurs ouvre son monde solo en LAN, l'autre
s'y connecte **directement** avec l'IP Tailscale de l'hôte et le port annoncé dans le chat (Tailscale ne relaie pas
la découverte LAN).

### Empreinte de serveur

L'hôte LAN calcule `"singleplayer"`, l'invité un hash de l'adresse : le backend ne les regroupe pas. Sur **chaque**
client, taper une fois la même valeur :

```text
/phantasmon debug fingerprint test
```

La valeur est enregistrée (`config/phantasmon-fingerprint-override.txt`) et appliquée immédiatement, puis à chaque
connexion. `/phantasmon debug fingerprint` sans argument revient au calcul normal. Sur un vrai serveur dédié, cette
manipulation est inutile.

## 3. Avant une publication

- URL du backend configurable (aujourd'hui en dur).
- Retirer `/phantasmon debug fingerprint`.
- Renseigner les liens de `fabric.mod.json` et le lien de mise à jour (`AuthService`).
- Traiter les points de priorité haute de [`project/known-issues.md`](../project/known-issues.md).
- Aligner `version` (`gradle.properties`) et `phantasmon.version.current` / `min-supported` du backend.
