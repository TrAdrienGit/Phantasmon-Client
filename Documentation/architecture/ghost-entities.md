# Ghost dans le monde

> Classes : `ghost/GhostSession` (connexion et cycle de vie), `ghost/GhostEntityManager` (entités et mouvement).
> Protocole : `Phantasmon-Backend/Documentation/reference/websocket-protocol.md` §2-3. Vérifié le 2026-10-03.

## 1. Principe

Un Ghost n'existe **sur aucun serveur**. Chaque client qui doit le voir crée sa propre entité, localement, à partir
des messages WebSocket du backend. Un joueur sans le mod ne reçoit rien et ne voit rien.

L'entité est une vraie `PokemonEntity` de Cobblemon (donc son modèle, ses animations et son rendu), construite par
le client et ajoutée directement au monde local avec `ClientLevel.addEntity`. Le chemin normal de Cobblemon
(`Pokemon.sendOut`) n'est pas utilisable : il exige un `ServerLevel`.

## 2. Session de présence (`GhostSession`)

| Étape | Détail |
|---|---|
| Démarrage | Après une connexion réussie (il faut le JWT) : ouverture de `/ws?token=…`. Le drapeau « connecté » ne passe à vrai qu'une fois le handshake terminé ; un échec affiche `phantasmon.ghost.connection_failed`. |
| Groupe | Premier tick connecté : `JoinServerGroup` (empreinte + dimension), une seule fois par connexion (drapeau `joinedGroup`, distinct de la détection de dimension). |
| Toutes les secondes | `PositionUpdate` (x, y, z, dimension) et `Heartbeat` |
| Chaque tick client | Si le joueur meurt ou change de dimension alors qu'un Ghost est sorti : rappel automatique |
| Arrêt | Déconnexion du monde : fermeture du WebSocket, suppression de tous les Ghost locaux |

**Empreinte de serveur** : `"singleplayer"` si le client héberge le monde (`Minecraft.isLocalServer()`), sinon
SHA-256 de `Minecraft.getCurrentServer().ip`. Pour les tests « Ouvrir au LAN », `/phantasmon debug fingerprint
<valeur>` force une valeur (identique sur les deux clients), enregistrée dans
`config/phantasmon-fingerprint-override.txt` et appliquée immédiatement (nouveau `JoinServerGroup`). Sans
argument, la commande supprime la surcharge. **Commande de test à retirer** quand un vrai serveur dédié sera utilisé.

**Sortie et rappel** : sur l'overlay de l'équipe Phantasm (touche O de Cobblemon), haut / bas et R (touches de
Cobblemon) choisissent et sortent / rappellent un Ghost (`GhostPartyHud`). Plus de touche dédiée ni de commande. Le chat affiche « Envoi en cours… », puis la confirmation quand le
serveur renvoie l'événement au propriétaire.

## 3. Création de l'entité (`GhostEntityManager.spawn`)

À la réception de `GhostEntitySpawn` (pour soi ou pour un autre joueur) :

1. Résolution de l'espèce par `PokemonSpecies.getByName`. Espèce inconnue → `WARN Cannot render Ghost: unresolved
   species '…'` dans le log et aucun Ghost (aucune donnée n'est modifiée).
2. Construction d'un `Pokemon` Cobblemon : espèce, chromatique, sexe, forme, niveau, et surnom
   `[Ghost] <surnom ou espèce>` (`phantasmon.ghost.nameplate`, `data.nickname` de `GhostEntitySpawn`) : l'étiquette
   native de Cobblemon (nom + niveau, visible quand on regarde le Pokémon) porte ainsi l'indicateur `[Ghost]`
   (CAD Partie 1 §5).
3. **Aspects forcés** : le modèle d'une forme (plaques d'Arceus, appareils de Motisma, masques d'Ogerpon…) et la
   variante chromatique ou sexuée sont choisis par les **aspects**. Sur une entité purement cliente, personne ne
   les synchronise : ils sont écrits à la main dans `PokemonEntity.ASPECTS` (forme + `shiny` + `male`/`female`).
   L'entité est enregistrée dans `PhantasmonEntities` : son étiquette (`[Ghost] <nom>`) s'affiche même si l'espèce
   n'est pas au Pokédex du joueur (`PokemonRendererMixin`, TODO-13). Le niveau de l'étiquette est lui aussi une donnée
   synchronisée (`PokemonEntity.LABEL_LEVEL`, 1 par défaut) : écrit à la main, comme les aspects.
4. Entité : `setNoAi(true)` (aucune IA Cobblemon), `setInvulnerable(true)`, `noPhysics = true` (ne pousse pas et
   n'est pas poussée ; remis à faux uniquement pendant notre propre `Entity.move` pour garder les collisions avec
   les blocs).
5. Animation de sortie identique à Cobblemon : geste du propriétaire, lancer de Poké Ball (0,5 s), faisceau de
   sortie (1,5 s, `BEAM_MODE = 1`, `PHASING_TARGET_ID` = propriétaire), cri, anneau chromatique. Le Ghost reste
   immobile pendant le faisceau.

Le **sexe** vient de `data.gender` (transmis par `GhostEntitySpawn`), sinon de ce que le ratio de l'espèce impose
(100 % mâle, 100 % femelle, asexué) ; une espèce mixte sans sexe stocké garde le modèle de base.

## 4. Mouvement

Exécuté à chaque tick client (`GhostEntityManager.tick()`), indépendamment sur chaque client.

| Comportement | Règle |
|---|---|
| Source de la position du propriétaire | Son entité `Player` locale si elle est chargée (sans latence) ; sinon la dernière position reçue (`GhostEntityMove`, ~1 s) |
| Point de suivi | 1,5 bloc derrière et 1,2 bloc à droite, calculé sur le **cap de déplacement** du propriétaire (figé à l'arrêt : tourner la caméra ne déplace pas le Ghost) ; écarté pour les gros Pokémon selon la largeur de leur hitbox |
| Vitesse | 0,06 à 0,40 bloc/tick selon la distance ; démarre au-delà de 1,3 bloc, s'arrête sous 0,4 bloc |
| Physique | `Entity.move` avec collisions, gravité, saut d'un bloc si bloqué |
| Rattrapage | Téléportation si plus de 20 blocs, ou bloqué ~3 s à plus de 3 blocs |
| Orientation | Direction de marche, ou vers le propriétaire à l'arrêt (25° par tick maximum) |
| Balade | Après 5 s d'immobilité du propriétaire : points aléatoires dans un rayon de 10 blocs, au pas (0,12 bloc/tick), pauses de 3 à 10 s ; revient dès que le propriétaire bouge |
| Animations | `PokemonEntity.MOVING` et `POSE_TYPE` posés à la main (marche / arrêt ; vol stationnaire 1,2 bloc plus haut pour les espèces volantes) |

Les constantes sont en tête de `GhostEntityManager`.

## 5. Disparition

| Cause | Effet |
|---|---|
| Rappel (touche H ou R sur l'overlay Ghost), Ghost échangé, mort du propriétaire | `GhostEntityDespawn` : faisceau de rappel vers le propriétaire (`BEAM_MODE = 3`) s'il est chargé chez ce client, sinon suppression immédiate |
| Déconnexion du propriétaire, changement de dimension | Suppression immédiate |
| Déconnexion locale | Suppression immédiate de tous les Ghost |

## 6. Limites connues

- Pas d'indicateur `[Ghost]` au-dessus de l'entité (CAD Partie 1 §5).
- Pas de pathfinding : le Ghost avance en ligne droite et peut se bloquer (rattrapage par téléportation).
- Deux clients peuvent voir le même Ghost à des positions légèrement différentes (calcul local, aucune autorité).
- Le Ghost traverse son propriétaire s'il lui marche dessus (conséquence de `noPhysics`).
- Pas de disparition liée à la distance de rendu.
