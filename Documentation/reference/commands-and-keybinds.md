# Commandes et touches

> Source : `command/PhantasmonCommands.java`, `PhantasmonKeybinds.java`, `wheel/GhostWheelOptions.java`.
> Vérifié le 2026-10-03. Toutes les commandes sont **côté client** (Brigadier Fabric) : elles fonctionnent sur
> n'importe quel serveur, sans permission.

## 1. Touches

Catégorie « Phantasmon » dans *Options → Commandes*. Les choix du joueur sont conservés par Minecraft
(`options.txt`) tant que les identifiants ne changent pas.

| Identifiant | Touche par défaut | Action |
|---|---|---|
| `key.phantasmon.music_menu` | **N** | Ouvre le menu des musiques (cocher les morceaux du pack « Phantasmon Music » à jouer, les écouter) |
| `key.phantasmon.open_pc` | **P** | Ouvrir le PC |

**Touche « Cacher l'équipe » de Cobblemon** (`key.cobblemon.hideparty`, **O** par défaut) : fait défiler équipe
Cobblemon → équipe Phantasm (overlay Phantasmon à gauche de l'écran, Ghost sorti en surbrillance) → rien → équipe
Cobblemon. Quand l'équipe Phantasm est affichée, les touches d'équipe de Cobblemon agissent sur elle : **haut / bas**
choisissent un Ghost (case mise en avant), **R** le sort (en rappelant celui qui est dehors) ou le rappelle s'il est
déjà sorti (Poké Ball ouverte). En visant un joueur ou un vrai Pokémon, ou en chevauchant une monture, R reste à
Cobblemon (roue d'interaction, défi, descente).

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

### PC et Pokémon

L'écran PC s'ouvre uniquement avec sa touche (**P** par défaut, TODO-22).

| Commande | Effet |
|---|---|
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

Aucune commande ni touche dédiée (Adrien 2026-10-05) : touches d'équipe de Cobblemon sur l'overlay Ghost (haut / bas
pour choisir, R pour sortir / rappeler). Caméra de combat, musique suivante et chrono : boutons de l'écran de combat.

### Échange en direct

Pas de commande pour inviter (TODO-22) : l'invitation passe par la roue d'interaction de Cobblemon (**R** sur un
joueur → « Échange Ghost »). Ces commandes ne servent qu'aux boutons du chat.

| Commande | Effet |
|---|---|
| `/phantasmon trade join` | Accepte l'invitation reçue (bouton [Accepter] du chat) |
| `/phantasmon trade decline` | Refuse (bouton [Refuser]) |

### Combat

Pas de commande pour inviter (TODO-22) : roue d'interaction de Cobblemon (**R** sur un joueur → « Combat Ghost »).
Ces commandes ne servent qu'aux boutons du chat.

| Commande | Effet |
|---|---|
| `/phantasmon battle join [ghost\|cobblemon]` | Accepte (bouton [Accepter]) et ouvre le lobby ; `cobblemon` présélectionne votre équipe Cobblemon (modifiable dans le lobby) |
| `/phantasmon battle decline` | Refuse (bouton [Refuser]) |
| (roue) « Regarder le combat Ghost » | **R** sur un joueur (ou un avatar du Hub) en combat Ghost : regarder ce combat, comme dans Cobblemon ; bouton Retour de l'écran de combat pour arrêter (D-31) |
| `/phantasmon battle timer` | Dans le lobby : active son timer de 150 s (bouton Timer). En combat : active le chrono de 90 s pour les deux joueurs (bouton [Activer le chrono]), définitivement pour ce combat |

### Global Hub (Phantasmon Network)

Voir le [guide du joueur](../guides/user-guide.md#7-le-global-hub-phantasmon-network). Rien de tout cela n'est
envoyé au serveur Minecraft. Échange et combat avec un joueur d'un autre serveur : **R** en visant son avatar
(roue réduite à « Échange Ghost » et « Combat Ghost »).

| Commande | Effet |
|---|---|
| `/phantasmon hub anchor create <nom>` | Crée votre Anchor (un seul ; les joueurs de ce serveur le voient et peuvent l'utiliser, D-30) : cube de 21 blocs centré sur vous, posé à vos pieds, orienté selon votre regard (arrondi au quart de tour). Le cube (21 blocs de côté, de vos pieds vers le haut) doit être **entièrement vide** : la construction du Hub s'y bâtit (D-34). Nom : 3 à 32 lettres, chiffres, espaces, `-`, `_` |
| `/phantasmon hub anchor info` | Votre Anchor : nom, dimension, position |
| `/phantasmon hub anchor delete` | Supprime votre Anchor |
| `/phantasmon hub anchor delete here` | Supprime l'Anchor où vous vous trouvez (son créateur ou un admin) |
| `/phantasmon hub join` | Accepte l'invitation (bouton [Oui]) |
| `/phantasmon hub decline` | Refuse (bouton [Non]) ; ressortir puis revenir dans l'Anchor invite de nouveau |
| `/phantasmon hub always` | Accepte et ne demande plus pour cet Anchor (bouton [Toujours ici]) |
| `/phantasmon hub autojoin on\|off` | Active ou retire l'entrée sans invitation pour l'Anchor où vous êtes (`config/phantasmon-hub-autojoin.txt`) |
| `/phantasmon hub leave` | Quitte le Hub sans sortir de l'Anchor |
| `/phantasmon hub chat <message>`, `/hc <message>` | Message dans le chat du Hub (256 caractères, un par seconde) |

### Administration (TODO-25)

Réservées aux joueurs listés dans le fichier `admins.txt` du backend (un pseudo Minecraft par ligne, comme les ops
d'un serveur) ; invisibles pour les autres. Le backend vérifie chaque demande lui-même.

| Commande | Effet |
|---|---|
| `/phantasmon admin pc <joueur>` | Ouvre le PC de ce joueur et permet tout comme si c'était le sien (déplacer, éditer, importer, exporter, supprimer) |
| `/phantasmon admin stopbattle <joueur>` | Arrête le combat de ce joueur (match nul, aucun vainqueur) ou annule son lobby |
| `/phantasmon admin battle solo` | Combat Ghost contre un miroir de votre équipe Ghost, joué par l'IA de Cobblemon (format Libre, sans lobby) ; regardable par les autres, non enregistré (D-32) |
| `/phantasmon admin reboot` | Redémarre le backend, après un clic sur [Confirmer] (combats en cours : match nul ; les clients se reconnectent seuls) |
| `/phantasmon admin ping` | Active/désactive l'affichage de `GET /health` toutes les 30 s dans le chat (ancien `toggle-ping`) |
| `/phantasmon admin debug fingerprint <valeur>` | Force l'empreinte de serveur (tests « Ouvrir au LAN »), persistée dans `config/phantasmon-fingerprint-override.txt` ; **à retirer** avant publication (TODO-2) |
| `/phantasmon admin debug fingerprint` | Supprime la surcharge |
| `/phantasmon admin debug spectacle <mega\|primal\|zmove\|tera> [type]` | Joue la mise en scène de Méga-Évolution, Retour primal, capacité Z ou Téracristallisation (type : fire, water…, stellar) sur le Pokémon le plus proche, sans le modifier |
| `/phantasmon admin debug intro <xy\|sword_shield\|diamond_pearl\|emerald\|black_white>` | Joue une des intros de combat seule, contre le joueur le plus proche (ou soi-même), sans combat derrière (TODO-26) |
