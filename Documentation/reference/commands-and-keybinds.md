# Commandes et touches

> Source : `command/PhantasmonCommands.java`, `PhantasmonKeybinds.java`, `wheel/GhostWheelOptions.java`.
> Vérifié le 2026-11-25. Toutes les commandes sont **côté client** (Brigadier Fabric) : elles fonctionnent sur
> n'importe quel serveur, sans permission.

## 1. Touches

Catégorie « Phantasmon » dans *Options → Commandes*. Les choix du joueur sont conservés par Minecraft
(`options.txt`) tant que les identifiants ne changent pas.

| Identifiant | Touche par défaut | Action |
|---|---|---|
| `key.cobblemon_battle_showtime.music_menu` (touche de Cobblemon Battle Showtime depuis D-37) | **N** | Ouvre le menu des musiques de Phantasmon (cocher les morceaux du pack à jouer, les écouter) |
| `key.phantasmon.open_pc` | **P** (**U** quand LeagueWay est installé, 2026-11-19) | Ouvrir le PC ; rappuyer le referme (2026-11-28) |
| `key.phantasmon.hub_manager` | **H** | Menu Phantasmon, ouvert sur les hubs (placer, supprimer ses ancres ; gestion complète pour un admin). Sans connexion au backend, il s'ouvre quand même, sur les Réglages (adresse du backend), sans les pages qui ont besoin du backend (2026-11-18) |

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

Aucune commande (D-39) : tout se fait dans l'écran PC, ouvert par sa touche (**P** par défaut, **U** quand LeagueWay
est installé) : import Showdown (fenêtre de collage avec vérification en direct), export, édition, déplacements,
clonage, suppression, recherche et boîtes d'équipe.

### Ghost

Aucune commande ni touche dédiée (Adrien 2026-10-05) : touches d'équipe de Cobblemon sur l'overlay Ghost (haut / bas
pour choisir, R pour sortir / rappeler). Caméra de combat, musique suivante et chrono : boutons de l'écran de combat.

### Échange en direct

Pas de commande (TODO-22, D-39) : l'invitation passe par la roue d'interaction de Cobblemon (**R** sur un
joueur → « Échange Ghost »). Le joueur invité répond avec les boutons [Accepter] / [Refuser] du chat (actions de chat,
`command/ChatActions`, plus des commandes depuis le 2026-10-30).

### Combat

Pas de commande (TODO-22, D-39) : roue d'interaction de Cobblemon (**R** sur un joueur → « Combat Ghost »). Les
réponses passent par les boutons du chat (actions de chat, plus des commandes depuis le 2026-10-30) :

| Bouton | Effet |
|---|---|
| [Accepter] | Accepte et ouvre le lobby (au choix avec l'équipe Ghost ou l'équipe Cobblemon, modifiable dans le lobby) |
| [Refuser] | Refuse |
| [Activer le chrono] | En combat : active le chrono de 90 s pour les deux joueurs, définitivement pour ce combat. Dans le lobby, le bouton Timer active son timer de 150 s |
| (roue) « Regarder le combat Ghost » | **R** sur un joueur (ou un avatar du Hub) en combat Ghost : regarder ce combat, comme dans Cobblemon ; bouton Retour de l'écran de combat pour arrêter (D-31) |

### Global Hub (Phantasmon Network)

Voir le [guide du joueur](../guides/user-guide.md#7-le-global-hub-phantasmon-network). Rien de tout cela n'est
envoyé au serveur Minecraft. Échange et combat avec un joueur d'un autre serveur : **R** en visant son avatar
(roue réduite à « Échange Ghost » et « Combat Ghost »).

Hubs et ancres se gèrent dans le **gestionnaire des hubs** (touche **H**, 2026-11-10) : les commandes `hub list`,
`hub anchor create / info / delete` et `admin hub create / delete / reload` n'existent plus.

| Commande | Effet |
|---|---|
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
| `/phantasmon admin hub mute <joueur> <durée>` | Rend ce joueur muet dans le chat des hubs pour la durée donnée (`30m`, `2h`, `7d` ; un nombre seul = minutes ; 365 jours au plus). Gardé au redémarrage du backend (TODO-31) |
| `/phantasmon admin hub unmute <joueur>` | Lève ce silence |
| `/phantasmon admin hub debug <hub>` | Affiche ou masque les bordures des ancres de ce hub (champ de force violet) |
| `/phantasmon admin hub lock` | Verrouille ou déverrouille le chat des hubs : seuls les admins y écrivent |
| `/hc !!<message>` | **Admin** : annonce, en surbrillance dans le chat et en grand à l'écran de chaque membre, avec un son |
| `/phantasmon admin ping` | Active/désactive l'affichage de `GET /health` toutes les 30 s dans le chat (ancien `toggle-ping`) |

Les aperçus de mise en scène appartiennent à Cobblemon Battle Showtime (D-37), qui n'a aucune commande : les intros,
ambiances et stades se prévisualisent dans son menu (touche **N**).
