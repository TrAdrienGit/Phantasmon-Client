# Commandes et touches

> Source : `command/PhantasmonCommands.java`, `PhantasmonKeybinds.java`, `wheel/GhostWheelOptions.java`.
> Vérifié le 2026-10-03. Toutes les commandes sont **côté client** (Brigadier Fabric) : elles fonctionnent sur
> n'importe quel serveur, sans permission.

## 1. Touches

Catégorie « Phantasmon » dans *Options → Commandes*. Les choix du joueur sont conservés par Minecraft
(`options.txt`) tant que les identifiants ne changent pas.

| Identifiant | Touche par défaut | Action |
|---|---|---|
| `key.phantasmon.open_pc` | **P** | Ouvrir le PC |
| `key.phantasmon.sendout` | **H** | Sortir / rappeler le Pokémon de l'emplacement 1 de l'équipe |
| `key.phantasmon.trade` | **G** | Inviter à un échange le joueur visé |
| `key.phantasmon.battle` | **B** | Inviter à un combat le joueur visé |

**Touche « Cacher l'équipe » de Cobblemon** (`key.cobblemon.hideparty`, **O** par défaut) : fait défiler équipe
Cobblemon → équipe Ghost (overlay Phantasmon à gauche de l'écran, Ghost sorti en surbrillance) → rien → équipe
Cobblemon. (`key.phantasmon.sendout` est passé de **O** à **H** par défaut pour ne plus la partager ; une touche déjà enregistrée dans `options.txt` est conservée.)

**Roue d'interaction de Cobblemon** (touche **R** de Cobblemon sur un autre joueur) : deux entrées ajoutées,
« Échange Ghost » (Est, cyan) et « Combat Ghost » (Nord-Ouest, rose-rouge), qui invitent le joueur sur lequel la
roue est ouverte. Les entrées d'échange et de combat de Cobblemon (vrais Pokémon) restent inchangées.

## 2. Commandes

Les commandes qui touchent aux données exigent d'être connecté (sinon `phantasmon.error.not_authenticated`). Les
UUID doivent être **complets** (36 caractères) : ils sont affichés en entier dans le chat et un clic les insère
dans la barre de saisie.

### Connexion et diagnostic

| Commande | Effet |
|---|---|
| `/phantasmon login` | Connexion manuelle (normalement automatique à l'entrée dans un monde) : version, preuve Mojang, JWT |
| `/phantasmon toggle-ping` | Active/désactive l'affichage de `GET /health` toutes les 30 s dans le chat (désactivé par défaut, non persisté) |

### PC et Pokémon

| Commande | Effet |
|---|---|
| `/phantasmon pc` | Ouvre l'écran PC |
| `/phantasmon pokemon import` | Crée un ou plusieurs Pokémon depuis le texte Showdown du presse-papiers |
| `/phantasmon pokemon export [uuid]` | Copie l'équipe active (ou le Pokémon donné) au format Showdown dans le presse-papiers |
| `/phantasmon pokemon list` | Liste tous ses Pokémon (espèce, niveau, UUID) |
| `/phantasmon pokemon pc <boîte 1-16>` | Contenu d'une boîte |
| `/phantasmon pokemon pc move <uuid> <boîte 1-16> <case 1-30>` | Déplace vers une case du PC (échange si occupée) |
| `/phantasmon pokemon team` | Affiche l'équipe active |
| `/phantasmon pokemon team set <uuid> <emplacement 1-6>` | Place dans l'équipe (échange si occupé) |
| `/phantasmon pokemon team clear <uuid> <boîte 1-16> <case 1-30>` | Retire de l'équipe vers une case précise du PC |
| `/phantasmon pokemon edit <uuid> level <1-100>` | Change le niveau |
| `/phantasmon pokemon clone <uuid>` | Clone (première case libre) |
| `/phantasmon pokemon delete <uuid>` | Supprime définitivement (sans confirmation, contrairement à l'écran PC) |

### Ghost

| Commande | Effet |
|---|---|
| `/phantasmon sendout` | Bascule : rappelle le Ghost sorti, sinon sort le Pokémon de l'emplacement 1 |
| `/phantasmon recall` | Rappelle le Ghost sorti |

### Échange en direct

| Commande | Effet |
|---|---|
| `/phantasmon trade invite <joueur>` | Invite (suggestions : joueurs du serveur) |
| `/phantasmon trade join` | Accepte l'invitation reçue (bouton [Accepter] du chat) |
| `/phantasmon trade decline` | Refuse (bouton [Refuser]) |

### Échange asynchrone

| Commande | Effet |
|---|---|
| `/phantasmon trade propose <uuid-joueur> <uuid-offert> <uuid-demandé>` | Propose un échange |
| `/phantasmon trade accept <uuid-échange>` | Accepte (destinataire) |
| `/phantasmon trade cancel <uuid-échange>` | Annule (initiateur ou destinataire) |
| `/phantasmon trade view <uuid-échange>` | Détail |
| `/phantasmon trade list` | Échanges initiés ou reçus |

### Combat

| Commande | Effet |
|---|---|
| `/phantasmon battle invite <joueur> [ghost\|cobblemon]` | Invite à un combat ; `cobblemon` présélectionne une copie de votre équipe Cobblemon dans le lobby (Ghost par défaut) |
| `/phantasmon battle join [ghost\|cobblemon]` | Accepte (bouton [Accepter]) et ouvre le lobby ; `cobblemon` présélectionne votre équipe Cobblemon (modifiable dans le lobby) |
| `/phantasmon battle decline` | Refuse (bouton [Refuser]) |
| `/phantasmon battle timer` | Dans le lobby : active son timer de 150 s (bouton Timer). En combat : active le chrono de 90 s pour les deux joueurs (bouton [Activer le chrono]), définitivement pour ce combat |

### Test uniquement

| Commande | Effet |
|---|---|
| `/phantasmon debug fingerprint <valeur>` | Force l'empreinte de serveur (tests « Ouvrir au LAN »), persistée dans `config/phantasmon-fingerprint-override.txt` |
| `/phantasmon debug fingerprint` | Supprime la surcharge |

**À retirer** avant publication (voir `project/status.md`).
